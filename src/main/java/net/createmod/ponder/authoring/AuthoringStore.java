package net.createmod.ponder.authoring;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Local author-owned files only. Null expectedHash means create, never unconditional overwrite. */
public final class AuthoringStore {
    public static final int HISTORY_LIMIT = 20;
    private static final int LISTING_LIMIT = 8192;

    public static final class SavedDocument {
        private final AuthoringDocument document;
        private final String hash;
        SavedDocument(AuthoringDocument document, String hash) { this.document = document; this.hash = hash; }
        public AuthoringDocument getDocument() { return document; }
        public String getHash() { return hash; }
    }

    public static final class ConflictException extends IOException {
        public ConflictException(String message) { super(message); }
    }

    private final Path configDirectory, root;
    private final AuthoringCompiler compiler;

    public AuthoringStore(Path minecraftConfigDirectory) {
        this(minecraftConfigDirectory, new AuthoringCompiler());
    }

    public AuthoringStore(Path minecraftConfigDirectory, AuthoringCompiler compiler) {
        if (minecraftConfigDirectory == null || compiler == null) throw new IllegalArgumentException("Config directory and compiler are required");
        configDirectory = minecraftConfigDirectory.toAbsolutePath().normalize();
        root = configDirectory.resolve("ponder_legacy").resolve("authoring");
        this.compiler = compiler;
    }

    public Path getRoot() { return root; }
    public List<String> listDraftIds() throws IOException { return listIds("drafts"); }
    public List<String> listPublishedIds() throws IOException { return listIds("published"); }
    public SavedDocument loadDraft(String id) throws IOException { return load("drafts", id); }
    public SavedDocument loadPublished(String id) throws IOException { return load("published", id); }

    public List<String> listHistory(String id) throws IOException {
        Path directory = historyDirectory(id);
        checkPath(directory);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.collect(Collectors.toList())) {
                checkPath(file);
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && validHistoryName(file.getFileName().toString()))
                    result.add(file.getFileName().toString());
            }
        }
        Collections.sort(result, Collections.reverseOrder());
        return Collections.unmodifiableList(result);
    }

    public SavedDocument loadHistory(String id, String version) throws IOException {
        if (!validHistoryName(version)) throw new IOException("Unsafe authoring history version");
        Path path = historyDirectory(id).resolve(version);
        checkPath(path);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        byte[] bytes = readBytes(path);
        try {
            AuthoringDocument document = AuthoringDocument.parse(new String(bytes, StandardCharsets.UTF_8));
            if (!id.equals(document.getId())) throw new IOException("History document id does not match its path");
            return new SavedDocument(document, AuthoringDocument.hash(bytes));
        } catch (RuntimeException invalid) { throw new IOException("Invalid authoring history document", invalid); }
    }

    public SavedDocument saveDraft(AuthoringDocument document, String expectedHash) throws IOException {
        return save("drafts", document, expectedHash);
    }

    public SavedDocument publish(AuthoringDocument document, String expectedHash, AuthoringCompiler.AssetResolver resolver) throws IOException {
        return publish(document, expectedHash, resolver, compiler);
    }

    public SavedDocument publish(AuthoringDocument document, String expectedHash, AuthoringCompiler.AssetResolver resolver,
        AuthoringCompiler activeCompiler) throws IOException {
        // Compilation is all-or-nothing before any published file is written.
        activeCompiler.compile(document, resolver);
        return save("published", document, expectedHash);
    }

    private synchronized SavedDocument save(String channel, AuthoringDocument document, String expectedHash) throws IOException {
        Path target = documentPath(channel, document.getId());
        ensureDirectories(root);
        Path lockPath = root.resolve(".store.lock");
        checkPath(lockPath);
        try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = lockChannel.lock()) {
            checkPath(target);
            byte[] old = Files.exists(target, LinkOption.NOFOLLOW_LINKS) ? readBytes(target) : null;
            String actual = old == null ? null : AuthoringDocument.hash(old);
            if (expectedHash == null ? actual != null : !expectedHash.equalsIgnoreCase(actual))
                throw new ConflictException("Authoring file changed since it was loaded: " + document.getId());
            byte[] bytes = document.toJson().toString().getBytes(StandardCharsets.UTF_8);
            if (old != null && java.util.Arrays.equals(old, bytes)) return new SavedDocument(document, actual);
            if (old != null) {
                Path history = historyDirectory(document.getId());
                ensureDirectories(history);
                String name = String.format(java.util.Locale.ROOT, "%019d", System.currentTimeMillis())
                    + "-" + channel + "-" + UUID.randomUUID().toString() + ".json";
                atomicWrite(history.resolve(name), old);
            }
            ensureDirectories(target.getParent());
            atomicWrite(target, bytes);
            pruneHistory(document.getId());
            return new SavedDocument(document, AuthoringDocument.hash(bytes));
        } catch (java.nio.channels.OverlappingFileLockException busy) {
            throw new ConflictException("Another authoring transaction is in progress");
        }
    }

    private SavedDocument load(String channel, String id) throws IOException {
        Path path = documentPath(channel, id);
        checkPath(path);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        byte[] bytes = readBytes(path);
        AuthoringDocument document;
        try { document = AuthoringDocument.parse(new String(bytes, StandardCharsets.UTF_8)); }
        catch (RuntimeException invalid) { throw new IOException("Invalid authoring file: " + path, invalid); }
        if (!id.equals(document.getId())) throw new IOException("Authoring file id does not match its safe path");
        return new SavedDocument(document, AuthoringDocument.hash(bytes));
    }

    private List<String> listIds(String channel) throws IOException {
        Path directory = root.resolve(channel);
        checkPath(directory);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        try (Stream<Path> walk = Files.walk(directory)) {
            java.util.Iterator<Path> iterator = walk.iterator(); int visited = 0;
            while (iterator.hasNext()) {
                Path file = iterator.next();
                checkPath(file);
                if (++visited > LISTING_LIMIT) throw new IOException("Authoring listing exceeds its 8192-entry budget");
                if (!file.getFileName().toString().endsWith(".json") || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                Path relative = directory.relativize(file);
                if (relative.getNameCount() < 2) throw new IOException("Unexpected authoring file path: " + file);
                String value = relative.toString().replace('\\', '/');
                int separator = value.indexOf('/');
                String id = value.substring(0, separator) + ":" + value.substring(separator + 1, value.length() - 5);
                if (load(channel, id) != null) result.add(id);
            }
        }
        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }

    private Path documentPath(String channel, String id) throws IOException {
        if (!AuthoringDocument.validId(id)) throw new IOException("Unsafe authoring document id: " + id);
        if (id.length() > 200 || id.split("/").length > 16) throw new IOException("Authoring document id exceeds the storage path budget");
        int separator = id.indexOf(':');
        Path path = root.resolve(channel).resolve(id.substring(0, separator)).resolve(id.substring(separator + 1) + ".json").normalize();
        if (!path.startsWith(root.resolve(channel))) throw new IOException("Authoring path escaped its channel");
        return path;
    }

    private Path historyDirectory(String id) throws IOException {
        Path file = documentPath("history", id);
        return file.resolveSibling(file.getFileName().toString().substring(0, file.getFileName().toString().length() - 5));
    }

    private static boolean validHistoryName(String version) {
        return version != null && version.matches("[0-9]{19}-(drafts|published)-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.json");
    }

    private void pruneHistory(String id) throws IOException {
        Path directory = historyDirectory(id);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        checkPath(directory);
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> snapshots = files.filter(path -> validHistoryName(path.getFileName().toString()))
                .sorted(Comparator.comparing(path -> path.getFileName().toString())).collect(Collectors.toList());
            for (int index = 0; index < snapshots.size() - HISTORY_LIMIT; index++) {
                checkPath(snapshots.get(index));
                Files.delete(snapshots.get(index));
            }
        }
    }

    private byte[] readBytes(Path path) throws IOException {
        checkPath(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Not a regular authoring file: " + path);
        if (Files.size(path) > AuthoringDocument.MAX_JSON_BYTES) throw new IOException("Authoring file exceeds JSON budget");
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length > AuthoringDocument.MAX_JSON_BYTES) throw new IOException("Authoring file exceeded JSON budget during read");
        return bytes;
    }

    private void ensureDirectories(Path directory) throws IOException {
        if (!directory.normalize().startsWith(configDirectory)) throw new IOException("Directory is outside the configured root");
        Path cursor = configDirectory;
        if (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(cursor);
        checkPath(cursor);
        for (Path part : configDirectory.relativize(directory.normalize())) {
            cursor = cursor.resolve(part);
            checkPath(cursor);
            if (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(cursor);
            checkPath(cursor);
        }
    }

    private void checkPath(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        if (!absolute.startsWith(configDirectory)) throw new IOException("Path is outside the configured config directory");
        Path cursor = configDirectory;
        if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(cursor) || !cursor.toRealPath().equals(cursor)) throw new IOException("Config directory must not redirect through a link");
        }
        for (Path part : configDirectory.relativize(absolute)) {
            cursor = cursor.resolve(part);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(cursor) || !cursor.toRealPath().equals(cursor)))
                throw new IOException("Authoring path must not redirect through a link: " + cursor);
        }
    }

    private void atomicWrite(Path target, byte[] bytes) throws IOException {
        checkPath(target);
        Path temporary = target.resolveSibling("." + target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer data = ByteBuffer.wrap(bytes);
                while (data.hasRemaining()) channel.write(data);
                channel.force(true);
            }
            checkPath(target);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
