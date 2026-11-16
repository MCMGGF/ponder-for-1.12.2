package net.createmod.ponder.authoring;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Copy-on-edit commands are shared by forms, history and undo/redo. */
public final class AuthoringEdits {
    private AuthoringEdits() {}
    public static AuthoringDocument empty(String id) {
        JsonObject doc = new JsonObject();
        doc.addProperty("id", id); doc.addProperty("title", "\u65b0\u5efa\u6559\u5b66");
        JsonArray items = new JsonArray(); items.add("minecraft:stone"); doc.add("items", items);
        JsonArray scenes = new JsonArray(); scenes.add(segment("main")); doc.add("scenes", scenes);
        return AuthoringDocument.fromJson(doc);
    }
    private static JsonObject segment(String id) {
        JsonObject scene = new JsonObject(); scene.addProperty("id", id); scene.addProperty("title", "\u65b0\u5efa\u7247\u6bb5");
        JsonArray steps = new JsonArray(); JsonObject idle = new JsonObject(); idle.addProperty("type", "idle");
        idle.addProperty("duration", 20); steps.add(idle); scene.add("steps", steps); return scene;
    }
    public static AuthoringDocument addScene(AuthoringDocument doc) {
        JsonObject json = doc.toJson(); JsonArray scenes = json.getAsJsonArray("scenes");
        int next = scenes.size() + 1;
        while (hasId(scenes, "scene_" + next)) next++;
        scenes.add(segment("scene_" + next)); return AuthoringDocument.fromJson(json);
    }
    private static boolean hasId(JsonArray scenes, String id) {
        for (JsonElement e : scenes) if (id.equals(AuthoringDocument.string(e.getAsJsonObject(), "id", ""))) return true;
        return false;
    }
    public static AuthoringDocument removeScene(AuthoringDocument doc, int index) {
        JsonObject json = doc.toJson(); JsonArray scenes = json.getAsJsonArray("scenes");
        if (scenes.size() <= 1) throw new IllegalArgumentException("At least one segment is required");
        scenes.remove(index); return AuthoringDocument.fromJson(json);
    }
    public static AuthoringDocument addStep(AuthoringDocument doc, int scene, JsonObject step) {
        JsonObject json = doc.toJson(); steps(json, scene).add(AuthoringDocument.copy(step));
        return AuthoringDocument.fromJson(json);
    }
    public static AuthoringDocument replaceStep(AuthoringDocument doc, int scene, int step, JsonObject replacement) {
        JsonObject json = doc.toJson(); steps(json, scene).set(step, AuthoringDocument.copy(replacement));
        return AuthoringDocument.fromJson(json);
    }
    public static AuthoringDocument removeStep(AuthoringDocument doc, int scene, int step) {
        JsonObject json = doc.toJson(); steps(json, scene).remove(step); return AuthoringDocument.fromJson(json);
    }
    public static AuthoringDocument move(AuthoringDocument doc, int scene, int index, int direction, boolean segments) {
        JsonObject json = doc.toJson(); JsonArray values = segments ? json.getAsJsonArray("scenes") : steps(json, scene);
        int other = index + direction;
        if (index < 0 || index >= values.size() || other < 0 || other >= values.size()) return doc;
        JsonElement current = values.get(index); values.set(index, values.get(other)); values.set(other, current);
        return AuthoringDocument.fromJson(json);
    }
    private static JsonArray steps(JsonObject json, int scene) {
        return json.getAsJsonArray("scenes").get(scene).getAsJsonObject().getAsJsonArray("steps");
    }
}
