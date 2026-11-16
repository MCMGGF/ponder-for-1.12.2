package net.createmod.ponder.authoring.assets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.channels.FileChannel;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.Locale;

/** Explicit-root filesystem boundary shared by asset previews and confirmed writes. */
public final class AuthoringAssetPaths {
    private static final Object NEW_FILE_PUBLICATION = new Object();
    private final Path root;

    public AuthoringAssetPaths(Path root) throws IOException {
        if (root == null) throw new IOException("An explicit authoring asset root is required");
        this.root = root.toAbsolutePath().normalize();
        verifyAncestors(this.root);
    }

    public Path root() { return root; }

    /** Creates the explicitly supplied root on demand, never during read-only construction or preview. */
    public void ensureRoot() throws IOException {
        verifyAncestors(root);
        Files.createDirectories(root);
        verifyAncestors(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Asset root is not a directory");
    }

    public Path resolve(String relative) throws IOException {
        String name = portableRelative(relative);
        Path result = root.resolve(name.replace('/', java.io.File.separatorChar)).normalize();
        if (!result.startsWith(root) || result.equals(root)) throw new IOException("Asset path escapes its root");
        verifyAncestors(result);
        return result;
    }

    public void writeNew(String relative, byte[] bytes) throws IOException {
        synchronized (NEW_FILE_PUBLICATION) { writeNewLocked(relative, bytes); }
    }

    private void writeNewLocked(String relative, byte[] bytes) throws IOException {
        if (bytes == null) throw new IOException("Missing asset bytes");
        Path target = resolve(relative);
        createParents(target.getParent());
        verifyAncestors(target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new java.nio.file.FileAlreadyExistsException(target.toString());
        Path temporary = target.resolveSibling(".asset-" + java.util.UUID.randomUUID() + ".tmp");
        Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) { channel.force(true); }
        if (!java.util.Arrays.equals(bytes, Files.readAllBytes(temporary))) throw new IOException("Asset temporary-file verification failed");
        verifyAncestors(target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new java.nio.file.FileAlreadyExistsException(target.toString());
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        verifyAncestors(target);
    }

    public void createParents(Path directory) throws IOException {
        if (directory == null || !directory.toAbsolutePath().normalize().startsWith(root)) throw new IOException("Directory escapes asset root");
        java.util.List<Path> missing = new java.util.ArrayList<>();
        for (Path at = directory; at != null && !Files.exists(at, LinkOption.NOFOLLOW_LINKS); at = at.getParent()) missing.add(at);
        verifyAncestors(directory);
        for (int i = missing.size() - 1; i >= 0; i--) {
            Path at = missing.get(i);
            if (!at.startsWith(root)) throw new IOException("The asset root's parent must already exist");
            Files.createDirectory(at);
            verifyAncestors(at);
        }
    }

    public static String portableRelative(String value) throws IOException {
        if (value == null || value.isEmpty() || value.length() > 240 || !Normalizer.isNormalized(value, Normalizer.Form.NFC)
            || value.startsWith("/") || value.indexOf('\\') >= 0 || value.endsWith("/")) throw new IOException("Invalid portable asset path");
        String[] parts = value.split("/", -1);
        if (parts.length > 32) throw new IOException("Asset path nesting budget exceeded");
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ")
                || part.matches(".*[<>:\"|?*\\x00-\\x1f].*")) throw new IOException("Unsafe asset path segment: " + part);
            String base = part.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
            if (base.matches("CON|PRN|AUX|NUL|CLOCK\\$|CONIN\\$|CONOUT\\$|COM[1-9]|LPT[1-9]"))
                throw new IOException("Reserved portable asset filename: " + part);
        }
        return value;
    }

    public static String comparisonKey(String relative) throws IOException {
        return portableRelative(relative).toLowerCase(Locale.ROOT);
    }

    public static void verifyAncestors(Path value) throws IOException {
        for (Path at = value.toAbsolutePath().normalize(); at != null; at = at.getParent()) {
            if (!Files.exists(at, LinkOption.NOFOLLOW_LINKS)) continue;
            BasicFileAttributes attributes = Files.readAttributes(at, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther()) throw new IOException("Symbolic/reparse asset paths are not allowed: " + at);
            Path real = at.toRealPath();
            boolean same = java.io.File.separatorChar == '\\' ? real.toString().equalsIgnoreCase(at.toString()) : real.equals(at);
            if (!same) throw new IOException("Reparse or aliased asset path is not allowed: " + at);
        }
    }
}
