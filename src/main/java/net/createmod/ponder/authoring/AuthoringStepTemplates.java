package net.createmod.ponder.authoring;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/** Adds editable defaults without discarding existing or future draft fields. */
public final class AuthoringStepTemplates {
    private AuthoringStepTemplates() { }

    public static JsonObject withType(JsonObject original, String type) {
        JsonObject result = original == null ? new JsonObject() : AuthoringDocument.copy(original);
        result.addProperty("type", type);
        JsonObject defaults = new JsonObject();
        if ("idle".equals(type)) defaults.addProperty("duration", 20);
        else if ("text".equals(type) || "shared_text".equals(type)) {
            if (!"shared_text".equals(type) || !result.has("key"))
                defaults.addProperty("text", "\u8bf7\u586b\u5199\u64cd\u4f5c\u5bf9\u8c61\u3001\u64cd\u4f5c\u4e0e\u53ef\u89c2\u5bdf\u7ed3\u679c\u3002");
            defaults.addProperty("duration", 60); defaults.addProperty("color", "white");
            if (!hasSelection(result)) defaults.add("point", triple(0, 1, 0));
        } else if ("show_controls".equals(type)) {
            defaults.add("point", triple(0, 1, 0)); defaults.addProperty("direction", "down");
            if (!result.has("leftClick") && !result.has("rightClick") && !result.has("scroll")) defaults.addProperty("action", "right");
            defaults.addProperty("duration", 60);
        } else if ("set_block".equals(type)) {
            defaults.addProperty("block", "minecraft:stone"); defaults.add("blockPos", triple(0, 0, 0));
            defaults.addProperty("spawnParticles", false); defaults.addProperty("immediateDisplay", true);
            defaults.addProperty("entranceAnimation", "none"); defaults.addProperty("duration", 20);
        } else if ("show_extra_structure".equals(type)) {
            defaults.add("blockPos", triple(0, 0, 0)); defaults.addProperty("rotation", 0);
            defaults.addProperty("replaceAir", false); defaults.addProperty("spawnParticles", false);
            defaults.addProperty("immediateDisplay", true); defaults.addProperty("entranceAnimation", "none");
            defaults.addProperty("entranceDuration", 20); defaults.addProperty("entranceInterval", 1);
            defaults.addProperty("direction", "down"); defaults.addProperty("smartDisplay", true);
        } else if ("show_structure".equals(type) || "show_section_and_merge".equals(type)) {
            target(defaults, result); defaults.addProperty("duration", 20);
            defaults.addProperty("direction", "show_structure".equals(type) ? "up" : "down");
            if ("show_structure".equals(type)) {
                if (!result.has("entranceAnimation")) {
                    fade(defaults, result); defaults.addProperty("directionless", false);
                }
            } else {
                defaults.addProperty("entranceAnimation", "none"); defaults.addProperty("entranceDuration", 20);
                defaults.addProperty("entranceInterval", 1); defaults.addProperty("smartDisplay", true);
            }
        } else if ("hide_section".equals(type)) {
            target(defaults, result); defaults.addProperty("duration", 20); fade(defaults, result);
            defaults.addProperty("direction", "down"); defaults.addProperty("directionless", false);
        } else if ("highlight_section".equals(type)) {
            target(defaults, result); defaults.addProperty("duration", 40);
            defaults.addProperty("color", "blue"); defaults.addProperty("chasing", true);
        } else if ("move_section".equals(type) || "rotate_section".equals(type)) {
            if (!result.has("linkId")) target(defaults, result);
            defaults.addProperty("duration", 20);
            if ("move_section".equals(type)) defaults.add("offset", triple(1, 0, 0));
            else {
                defaults.addProperty("rotX", 0); defaults.addProperty("rotY", 90); defaults.addProperty("rotZ", 0);
                if (result.has("degrees")) defaults.add("rotY", result.get("degrees"));
            }
        } else if ("replace_blocks".equals(type)) {
            target(defaults, result); defaults.addProperty("block", "minecraft:stone");
            defaults.addProperty("spawnParticles", false); defaults.addProperty("duration", 20);
        } else if ("modify_block_entity_nbt".equals(type)) {
            target(defaults, result); defaults.addProperty("duration", 20);
            if (!result.has("blockProperties")) defaults.addProperty("nbt", "{}");
        } else if ("toggle_redstone_power".equals(type) || "restore_blocks".equals(type)) {
            target(defaults, result); defaults.addProperty("duration", 20);
        } else if ("destroy_block".equals(type) || "break_progress".equals(type)
            || "indicate_redstone".equals(type) || "indicate_success".equals(type)) {
            defaults.add("blockPos", JsonNull.INSTANCE); defaults.addProperty("duration", 20);
        } else if ("show_interface".equals(type)) {
            if (!hasMenu(result)) defaults.addProperty("uiId", "minecraft:chest");
            defaults.addProperty("title", "\u5bb9\u5668\u6f14\u793a"); defaults.addProperty("duration", 200);
            if (!result.has("interface") && !result.has("slots")) defaults.add("interface", emptyMenu());
        } else if ("click_interface".equals(type) || "interface_action".equals(type)) {
            defaults.addProperty("action", "left"); defaults.addProperty("duration", 10);
            if (!result.has("target") && !result.has("pos")) defaults.addProperty("slot", 0);
        } else if ("set_interface_slots".equals(type) || "change_interface_slot".equals(type)) {
            defaults.add("set_interface_slots".equals(type) ? "slots" : "interfaceSlots", new JsonArray());
            defaults.addProperty("replace", false);
        } else if ("set_interface_data".equals(type)) defaults.add("data", new JsonArray());
        else if ("set_interface_context".equals(type)) defaults.add("player", emptyPlayer());
        else if ("hide_interface".equals(type)) defaults.addProperty("duration", 0);
        else if ("await_interface".equals(type)) {
            defaults.add("goal", JsonNull.INSTANCE);
            defaults.addProperty("hint", "\u8bf7\u8bbe\u7f6e\u58f0\u660e\u5f0f\u7ec3\u4e60\u76ee\u6807\u3002");
        } else if ("zoom_scene".equals(type)) {
            defaults.add("point", JsonNull.INSTANCE); defaults.addProperty("scale", 1); defaults.addProperty("duration", 20);
        } else if ("encapsulate_bounds".equals(type)) defaults.add("bounds", triple(5, 3, 5));
        else if ("play_sound".equals(type)) {
            defaults.addProperty("sound", "minecraft:block.lever.click"); defaults.addProperty("source", "blocks");
            defaults.addProperty("soundVolume", 1); defaults.addProperty("pitch", 1);
        } else if ("modify_entities_nbt".equals(type) || "modify_item_entities_nbt".equals(type)) {
            defaults.addProperty("fullScene", true); defaults.addProperty("nbt", "{CustomName:\"Preview\"}");
            defaults.addProperty("duration", 20);
        } else if ("rotate_camera_y".equals(type)) {
            defaults.addProperty("degrees", 90); defaults.addProperty("degreesX", 0); defaults.addProperty("duration", 20);
        } else if ("spawn_particles".equals(type)) {
            defaults.addProperty("particle", "FLAME"); defaults.add("pos", triple(0, 1, 0));
            defaults.addProperty("distribution", "point"); defaults.add("spread", triple(0, 0, 0));
            defaults.add("motion", triple(0, 0, 0)); defaults.addProperty("count", 10); defaults.addProperty("duration", 20);
        } else if ("create_entity".equals(type)) {
            defaults.addProperty("entity", "minecraft:player"); defaults.add("pos", triple(0, 1, 0));
            defaults.addProperty("entranceAnimation", "none"); defaults.addProperty("entranceDuration", 20);
            defaults.addProperty("direction", "down"); defaults.add("motion", triple(0, 0, 0)); defaults.addProperty("walkAnimation", false);
        } else if ("create_item_entity".equals(type)) {
            defaults.addProperty("item", "minecraft:stone"); defaults.add("pos", triple(0, 1, 0));
            defaults.add("motion", triple(0, 0, 0)); defaults.addProperty("count", 1);
        }
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : defaults.entrySet())
            if (!result.has(entry.getKey())) result.add(entry.getKey(), entry.getValue());
        return result;
    }

    private static boolean hasSelection(JsonObject step) {
        return step.has("blockPos") || step.has("positions") || step.has("fullScene");
    }

    private static void target(JsonObject defaults, JsonObject step) {
        // Target-dependent actions stay unpublishable until the author explicitly chooses a block or section.
        if (!hasSelection(step)) defaults.add("blockPos", JsonNull.INSTANCE);
    }

    private static void fade(JsonObject defaults, JsonObject step) {
        defaults.addProperty("fadeDuration", 20);
        if (step.has("duration")) defaults.add("fadeDuration", step.get("duration"));
    }

    private static boolean hasMenu(JsonObject step) {
        if (step.has("uiId") || step.has("block")) return true;
        if (!step.has("interface")) return false;
        if (!step.get("interface").isJsonObject()) return true;
        JsonObject descriptor = step.getAsJsonObject("interface");
        return descriptor.has("menuType") || descriptor.has("target");
    }

    private static JsonObject emptyMenu() {
        JsonObject descriptor = new JsonObject(); descriptor.add("slots", new JsonArray());
        descriptor.add("player", emptyPlayer()); return descriptor;
    }

    private static JsonObject emptyPlayer() {
        JsonObject inventory = new JsonObject(); inventory.addProperty("mode", "empty");
        JsonObject player = new JsonObject(); player.add("inventory", inventory); return player;
    }

    private static JsonArray triple(int x, int y, int z) {
        JsonArray result = new JsonArray(); result.add(x); result.add(y); result.add(z); return result;
    }
}
