package net.createmod.ponder.authoring.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.createmod.ponder.authoring.AuthoringDocument;

/** Local PNG IDs only. Foreign resource/path references require an explicit imageMappings entry. */
public final class ImageAssetPool {
    public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    public static final long MAX_DECODED_BYTES = 16L * 1024 * 1024;
    public static final int MAX_REFERENCES = 32;
    public static final long MAX_CONTEXT_ENCODED_BYTES = 32L * 1024 * 1024;
    public static final long MAX_CONTEXT_DECODED_BYTES = 64L * 1024 * 1024;
    private final AuthoringAssetPaths authoring;
    private final AuthoringAssetPaths assets;

    public static final class ImageInfo {
        public final String id, sha256;
        public final int width, height, encodedBytes;
        public final long decodedBytes, rgbaBytes;
        private ImageInfo(String id, String hash, int width, int height, int encoded, long decoded) {
            this.id = id; sha256 = hash; this.width = width; this.height = height;
            encodedBytes = encoded; decodedBytes = decoded; rgbaBytes = (long) width * height * 4;
        }
    }
    public static final class ImportPlan {
        public final String inboxName;
        public final ImageInfo image;
        private final ImageAssetPool owner;
        private ImportPlan(ImageAssetPool owner, String name, ImageInfo info) { this.owner = owner; inboxName = name; image = info; }
    }

    public ImageAssetPool(Path authoringRoot) throws IOException {
        authoring = new AuthoringAssetPaths(authoringRoot);
        assets = new AuthoringAssetPaths(authoringRoot.resolve("assets"));
    }
    public Path root() { return assets.root(); }
    public static String normalizeId(String value) throws IOException {
        if (!AuthoringDocument.validId(value) || value.endsWith(".png"))
            throw new IOException("Image requires a namespaced local ID without .png; foreign references require imageMappings");
        AuthoringAssetPaths.portableRelative(value.replace(':', '/'));
        return value;
    }
    public static String relativePath(String id) throws IOException { return "textures/" + normalizeId(id).replace(':', '/') + ".png"; }
    public static String idFromPackPath(String path) throws IOException {
        if (path == null || !path.startsWith("assets/textures/") || !path.endsWith(".png")) throw new IOException("Invalid packed image path");
        String name = path.substring("assets/textures/".length(), path.length() - 4);
        int slash = name.indexOf('/');
        if (slash < 1) throw new IOException("Packed image requires a namespace");
        String id = normalizeId(name.substring(0, slash) + ":" + name.substring(slash + 1));
        if (!path.equals("assets/" + relativePath(id))) throw new IOException("Image path does not match its canonical local ID");
        return id;
    }
    public ImageInfo inspect(String id) throws IOException {
        String key = normalizeId(id); return inspectBytes(key, read(assets.resolve(relativePath(key))));
    }
    public byte[] readChecked(ImageInfo expected) throws IOException {
        if (expected == null) throw new IOException("A reviewed image descriptor is required");
        byte[] bytes = read(assets.resolve(relativePath(expected.id)));
        if (bytes.length != expected.encodedBytes || !AuthoringDocument.hash(bytes).equals(expected.sha256))
            throw new IOException("Image changed since validation: " + expected.id);
        return bytes;
    }
    public static ImageInfo inspectBytes(String id, byte[] bytes) throws IOException {
        normalizeId(id);
        if (bytes == null || bytes.length > MAX_FILE_BYTES) throw new IOException("Authoring images are limited to 8 MiB encoded");
        long decoded = PngAssetValidator.validate(bytes, MAX_DECODED_BYTES);
        int width = intAt(bytes, 16), height = intAt(bytes, 20);
        if (width > 2048 || height > 2048 || (long) width * height * 4 > MAX_DECODED_BYTES)
            throw new IOException("Authoring images must fit 2048 squared and 16 MiB RGBA");
        return new ImageInfo(id, AuthoringDocument.hash(bytes), width, height, bytes.length, decoded);
    }
    private static int intAt(byte[] bytes, int offset) {
        return (bytes[offset] & 255) << 24 | (bytes[offset + 1] & 255) << 16 | (bytes[offset + 2] & 255) << 8 | bytes[offset + 3] & 255;
    }
    private static byte[] read(Path file) throws IOException {
        AuthoringAssetPaths.verifyAncestors(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_FILE_BYTES)
            throw new IOException("Expected a regular PNG within the 8 MiB byte budget");
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192];
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            int count; while ((count = input.read(buffer)) != -1) {
                if (count > MAX_FILE_BYTES - output.size()) throw new IOException("PNG grew beyond its byte budget");
                output.write(buffer, 0, count);
            }
        }
        return output.toByteArray();
    }
    public List<String> listIds() throws IOException {
        Path directory = assets.resolve("textures"); List<String> result = new ArrayList<>();
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return result;
        try (java.util.stream.Stream<Path> entries = Files.walk(directory, 32)) {
            java.util.Iterator<Path> iterator = entries.iterator(); int visited = 0;
            while (iterator.hasNext()) {
                Path file = iterator.next(); if (++visited > 8192) throw new IOException("Image library traversal exceeds 8192 entries");
                AuthoringAssetPaths.verifyAncestors(file);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.toString().endsWith(".png")) continue;
                String relative = directory.relativize(file).toString().replace(java.io.File.separatorChar, '/');
                result.add(idFromPackPath("assets/textures/" + relative));
                if (result.size() > 512) throw new IOException("Image library listing exceeds 512 images");
            }
        }
        Collections.sort(result); return Collections.unmodifiableList(result);
    }
    public List<String> listInboxNames() throws IOException {
        Path directory = authoring.resolve("inbox"); List<String> result = new ArrayList<>();
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return result;
        try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            int visited = 0;
            for (Path file : entries) {
                if (++visited > 4096) throw new IOException("Inbox listing exceeds 4096 entries");
                AuthoringAssetPaths.verifyAncestors(file);
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && file.toString().endsWith(".png")) {
                    result.add(file.getFileName().toString()); if (result.size() > 512) throw new IOException("Inbox exceeds 512 PNG files");
                }
            }
        }
        Collections.sort(result); return Collections.unmodifiableList(result);
    }
    public ImportPlan previewInbox(String name, String id) throws IOException {
        if (name == null || name.contains("/") || !name.endsWith(".png")) throw new IOException("Choose a PNG directly inside the authoring inbox");
        normalizeId(id); Path destination = assets.resolve(relativePath(id));
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Image ID already exists; choose a new ID");
        return new ImportPlan(this, name, inspectBytes(id, read(authoring.resolve("inbox/" + name))));
    }
    public ImageInfo confirmInbox(ImportPlan plan) throws IOException {
        if (plan == null || plan.owner != this) throw new IOException("An image preview from this workspace is required");
        ImportPlan current = previewInbox(plan.inboxName, plan.image.id);
        if (!plan.image.sha256.equals(current.image.sha256)) throw new IOException("Inbox PNG changed after review");
        byte[] bytes = read(authoring.resolve("inbox/" + plan.inboxName));
        if (!plan.image.sha256.equals(AuthoringDocument.hash(bytes))) throw new IOException("Inbox PNG changed while importing");
        authoring.ensureRoot(); assets.writeNew(relativePath(plan.image.id), bytes); return plan.image;
    }
    public static String resolveReference(AuthoringDocument document, JsonObject step) throws IOException {
        String source = AuthoringDocument.string(step, "image", "");
        if (source.isEmpty()) throw new IOException("show_image requires an explicit image reference");
        JsonObject root = document.toJson();
        if (root.has("imageMappings")) {
            if (!root.get("imageMappings").isJsonObject() || root.getAsJsonObject("imageMappings").entrySet().size() > 256)
                throw new IOException("imageMappings must be an object with at most 256 explicit entries");
            JsonElement mapped = root.getAsJsonObject("imageMappings").get(source);
            if (mapped != null) {
                if (!mapped.isJsonPrimitive() || !mapped.getAsJsonPrimitive().isString()) throw new IOException("Image mapping must name a local image ID");
                return normalizeId(mapped.getAsString());
            }
        }
        return normalizeId(source);
    }
    public static List<String> referencedIds(AuthoringDocument document) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonObject scene : document.getScenes()) {
            if (!scene.has("steps") || !scene.get("steps").isJsonArray()) continue;
            for (JsonElement value : scene.getAsJsonArray("steps")) if (value.isJsonObject()) {
                JsonObject step = value.getAsJsonObject();
                if ("show_image".equals(AuthoringDocument.string(step, "type", ""))) ids.add(resolveReference(document, step));
                if (ids.size() > MAX_REFERENCES) throw new IOException("A document may reference at most 32 distinct local images");
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(ids));
    }
    public Map<String, ImageInfo> inspectContext(AuthoringDocument document) throws IOException {
        Map<String, ImageInfo> values = new LinkedHashMap<>(); long encoded = 0, decoded = 0;
        for (String id : referencedIds(document)) {
            ImageInfo info = inspect(id); encoded += info.encodedBytes; decoded += Math.max(info.decodedBytes, info.rgbaBytes);
            if (encoded > MAX_CONTEXT_ENCODED_BYTES || decoded > MAX_CONTEXT_DECODED_BYTES)
                throw new IOException("Referenced images exceed 32 MiB encoded or 64 MiB decoded context");
            values.put(id, info);
        }
        return Collections.unmodifiableMap(values);
    }
}
