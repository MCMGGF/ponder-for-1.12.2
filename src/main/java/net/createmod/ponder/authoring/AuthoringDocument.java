package net.createmod.ponder.authoring;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.io.StringReader;
import java.io.IOException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.util.ResourceLocation;

/** A lossless, copy-on-read authoring envelope, including fields this backend cannot execute. */
public final class AuthoringDocument {
    public static final int FORMAT_VERSION = 1;
    public static final String TARGET_VERSION = "1.12.2";
    public static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    private final JsonObject json;
    private final String hash;

    private AuthoringDocument(JsonObject input) {
        json = copy(input);
        if (!json.has("formatVersion")) json.addProperty("formatVersion", FORMAT_VERSION);
        if (!json.has("targetVersion")) json.addProperty("targetVersion", TARGET_VERSION);
        if (!json.has("source")) {
            JsonObject source = new JsonObject();
            source.addProperty("dialect", input.has("formatVersion") ? "ponder-authoring" : "ponderer");
            source.addProperty("originalId", string(input, "id", ""));
            json.add("source", source);
        }
        if (!json.has("scenes") && json.has("steps")) {
            JsonObject segment = new JsonObject();
            segment.addProperty("id", "main");
            if (json.has("title")) segment.add("title", copyElement(json.get("title")));
            segment.add("steps", copyElement(json.get("steps")));
            JsonArray scenes = new JsonArray();
            scenes.add(segment);
            json.add("scenes", scenes);
        }
        byte[] normalized = json.toString().getBytes(StandardCharsets.UTF_8);
        if (normalized.length > MAX_JSON_BYTES) throw new IllegalArgumentException("Normalized document exceeds the 2 MiB JSON budget");
        hash = hash(normalized);
    }

    public static AuthoringDocument fromJson(JsonObject json) {
        if (json == null) throw new IllegalArgumentException("Document is required");
        checkTreeBudget(json);
        if (json.toString().getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES)
            throw new IllegalArgumentException("Document exceeds the 2 MiB JSON budget");
        return new AuthoringDocument(json);
    }

    public static AuthoringDocument parse(String json) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES)
            throw new IllegalArgumentException("Document exceeds the 2 MiB JSON budget");
        checkTextBudget(json);
        JsonElement parsed = new JsonParser().parse(json);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("Document root must be an object");
        return fromJson(parsed.getAsJsonObject());
    }

    public JsonObject toJson() { return copy(json); }
    JsonObject internalJson() { return json; }
    public String getId() { return string(json, "id", ""); }

    public List<JsonObject> getScenes() {
        List<JsonObject> result = new ArrayList<JsonObject>();
        if (json.has("scenes") && json.get("scenes").isJsonArray()) {
            for (JsonElement entry : json.getAsJsonArray("scenes")) {
                if (entry.isJsonObject()) result.add(copy(entry.getAsJsonObject()));
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<String> getItems() {
        List<String> result = new ArrayList<String>();
        if (json.has("items") && json.get("items").isJsonArray()) {
            for (JsonElement entry : json.getAsJsonArray("items")) {
                if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) result.add(entry.getAsString());
            }
        }
        return Collections.unmodifiableList(result);
    }

    public ResourceLocation getRuntimeSceneId(int sceneIndex) {
        if (!validId(getId())) throw new IllegalArgumentException("Invalid authoring document id");
        List<JsonObject> scenes = getScenes();
        JsonObject segment = scenes.get(sceneIndex);
        String suffix = string(segment, "id", "scene_" + (sceneIndex + 1));
        if (!validPath(suffix)) throw new IllegalArgumentException("Invalid segment id: " + suffix);
        ResourceLocation id = new ResourceLocation(getId());
        return new ResourceLocation("ponder_authoring", id.getNamespace() + "/" + id.getPath() + "/" + suffix);
    }

    public String sha256() { return hash; }

    public static boolean validId(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return false;
        if (!validPath(id.substring(0, id.indexOf(':')))) return false;
        return validPath(id.substring(id.indexOf(':') + 1));
    }

    public static boolean validPath(String path) {
        if (path == null || path.isEmpty() || !path.matches("[a-z0-9_./-]+") || path.startsWith("/")) return false;
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part) || part.endsWith(".")) return false;
            String base = part.indexOf('.') < 0 ? part : part.substring(0, part.indexOf('.'));
            if (base.matches("con|prn|aux|nul|com[1-9]|lpt[1-9]")) return false;
        }
        return true;
    }

    public static JsonObject copy(JsonObject object) {
        checkTreeBudget(object);
        return new JsonParser().parse(object.toString()).getAsJsonObject();
    }

    public static JsonElement copyElement(JsonElement element) {
        checkTreeBudget(element);
        return new JsonParser().parse(element.toString());
    }

    public static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
            ? value.getAsString() : fallback;
    }

    public static String hash(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder();
            for (byte b : digest) value.append(String.format(java.util.Locale.ROOT, "%02X", b & 255));
            return value.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void checkTextBudget(String text) {
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            int depth = 0, nodes = 0;
            while (true) {
                JsonToken token = reader.peek();
                if (token == JsonToken.END_DOCUMENT) break;
                if (++nodes > 524288) throw new IllegalArgumentException("Document exceeds the JSON node budget");
                switch (token) {
                    case BEGIN_OBJECT: reader.beginObject(); depth++; break;
                    case BEGIN_ARRAY: reader.beginArray(); depth++; break;
                    case END_OBJECT: reader.endObject(); depth--; break;
                    case END_ARRAY: reader.endArray(); depth--; break;
                    case NAME: reader.nextName(); break;
                    case BOOLEAN: reader.nextBoolean(); break;
                    case NULL: reader.nextNull(); break;
                    default: reader.nextString(); break;
                }
                if (depth > 64) throw new IllegalArgumentException("Document exceeds 64 JSON nesting levels");
            }
        } catch (IOException invalid) { throw new IllegalArgumentException("Invalid authoring JSON", invalid); }
    }

    private static void checkTreeBudget(JsonElement root) {
        Deque<JsonElement> nodes = new ArrayDeque<JsonElement>();
        Deque<Integer> depths = new ArrayDeque<Integer>();
        nodes.push(root); depths.push(1);
        int count = 0;
        while (!nodes.isEmpty()) {
            JsonElement current = nodes.pop(); int depth = depths.pop();
            if (++count > 524288 || depth > 64) throw new IllegalArgumentException("Document exceeds the JSON node/depth budget");
            if (current.isJsonObject()) for (Map.Entry<String, JsonElement> entry : current.getAsJsonObject().entrySet()) {
                nodes.push(entry.getValue()); depths.push(depth + 1);
            }
            else if (current.isJsonArray()) for (JsonElement entry : current.getAsJsonArray()) {
                nodes.push(entry); depths.push(depth + 1);
            }
        }
    }
}
