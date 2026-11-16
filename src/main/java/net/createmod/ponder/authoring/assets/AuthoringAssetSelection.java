package net.createmod.ponder.authoring.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
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
import net.createmod.ponder.foundation.PonderSchematic;

/** Explicit document references only, never an automatic scan/load of the entire asset library. */
public final class AuthoringAssetSelection {
    public static final int MAX_CONTEXT_ASSETS = 64;
    public static final long MAX_CONTEXT_BYTES = 32L * 1024 * 1024;
    public static final int MAX_CONTEXT_BLOCKS = 1048576;
    private AuthoringAssetSelection() { }

    public static List<String> collectReferencedIds(AuthoringDocument document) throws IOException {
        if (document == null) throw new IOException("A document is required");
        Set<String> ids = new LinkedHashSet<>(); JsonObject root = document.toJson();
        addProperty(ids, root, "structure");
        JsonArray rootPool = pool(root);
        if (rootPool != null && rootPool.size() > 0) add(ids, literal(rootPool.get(0), "First root structure"));
        for (JsonObject segment : document.getScenes()) {
            addProperty(ids, segment, "structure"); JsonArray segmentPool = pool(segment);
            if (!segment.has("steps") || !segment.get("steps").isJsonArray()) continue;
            for (JsonElement element : segment.getAsJsonArray("steps")) {
                if (!element.isJsonObject()) continue;
                JsonObject step = element.getAsJsonObject(); String type = AuthoringDocument.string(step, "type", "");
                if (!"show_structure".equals(type) && !"show_extra_structure".equals(type)) continue;
                String reference = step.has("structure") ? literal(step.get("structure"), "Step structure reference")
                    : AuthoringDocument.string(segment, "structure", AuthoringDocument.string(root, "structure", ""));
                if (reference.matches("[0-9]+") && (segmentPool != null || rootPool != null)) {
                    JsonArray active = segmentPool == null ? rootPool : segmentPool;
                    long number;
                    try { number = Long.parseLong(reference); } catch (NumberFormatException invalid) { throw new IOException("Structure pool index exceeds the numeric budget", invalid); }
                    long index = number == 0 ? 0 : number - 1;
                    if (index >= active.size()) throw new IOException("Referenced structure pool index is out of range");
                    reference = literal(active.get((int) index), "Referenced pool structure");
                }
                if (!reference.isEmpty()) add(ids, reference);
                else if ("show_extra_structure".equals(type)) throw new IOException("Extra structure has no explicit resolvable local reference");
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(ids));
    }

    public static Map<String, PonderSchematic> loadContext(StructurePool pool, AuthoringDocument document) throws IOException {
        if (pool == null) throw new IOException("An explicit structure pool is required");
        List<String> ids = collectReferencedIds(document); AuthoringAssetPaths paths = new AuthoringAssetPaths(pool.root());
        long totalBytes = 0; Map<String, Long> expectedSizes = new LinkedHashMap<>();
        // Preflight every encoded size before parsing even the first NBT file.
        for (String id : ids) {
            Path file = paths.resolve(StructurePool.relativePath(id));
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Referenced structure is missing: " + id);
            long size = Files.size(file); totalBytes += size;
            if (size < 1 || totalBytes > MAX_CONTEXT_BYTES) throw new IOException("Referenced structure context exceeds 32 MiB encoded bytes");
            expectedSizes.put(id, size);
        }
        Map<String, PonderSchematic> result = new LinkedHashMap<>(); long totalBlocks = 0;
        PonderSchematic.DecodeBudget decodeBudget = new PonderSchematic.DecodeBudget(256L * 1024 * 1024, MAX_CONTEXT_BLOCKS);
        for (String id : ids) {
            PonderSchematic source = pool.resolve(id, decodeBudget); totalBlocks += source.getBlocks().size();
            if (totalBlocks > MAX_CONTEXT_BLOCKS) throw new IOException("Referenced structure context exceeds 1048576 decoded blocks");
            Path file = paths.resolve(StructurePool.relativePath(id));
            if (Files.size(file) != expectedSizes.get(id)) throw new IOException("Structure changed while loading context: " + id);
            result.put(id, source);
        }
        return Collections.unmodifiableMap(result);
    }
    private static JsonArray pool(JsonObject value) throws IOException {
        if (!value.has("structures")) return null;
        if (!value.get("structures").isJsonArray()) throw new IOException("Structure pool must be an explicit local ID array");
        if (value.getAsJsonArray("structures").size() > 4096) throw new IOException("Structure pool declaration exceeds 4096 IDs");
        return value.getAsJsonArray("structures");
    }
    private static void addProperty(Set<String> ids, JsonObject value, String key) throws IOException {
        if (value.has(key)) { String reference = literal(value.get(key), key); if (!reference.isEmpty()) add(ids, reference); }
    }
    private static String literal(JsonElement value, String name) throws IOException {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() && !value.getAsJsonPrimitive().isNumber())
            throw new IOException(name + " must be a literal local structure ID/index");
        return value.getAsString();
    }
    private static void add(Set<String> ids, String id) throws IOException {
        ids.add(StructurePool.normalizeId(id));
        if (ids.size() > MAX_CONTEXT_ASSETS) throw new IOException("At most 64 explicitly referenced structures may be exported as context");
    }
}
