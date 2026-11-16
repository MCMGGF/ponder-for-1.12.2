package net.createmod.ponder.authoring.practice;

import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.createmod.ponder.authoring.AuthoringCompiler;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Action;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Builder;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Goal;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Menu;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Operation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public final class PracticeJson {
    private PracticeJson() { }

    public static PracticeDefinition fromStep(JsonObject step) {
        return start(null, step).build();
    }

    static Builder start(AuthoringDocument document, JsonObject step) {
        JsonObject descriptor = object(step, "interface");
        rejectOpeningData(descriptor);
        JsonObject target = object(descriptor, "target");
        String menu = string(step, "uiId", string(descriptor, "menuType",
            string(target, "block", string(step, "block", "unknown:menu"))));
        if (document != null && !"unknown:menu".equals(menu)) menu = AuthoringCompiler.mappedId(document, menu);
        Builder builder = PracticeDefinition.builder(menu).title(AuthoringCompiler.text(step.get("title"), ""))
            .duration(integer(step, "duration", 200, 0, 24000));
        initialSlots(builder, document, array(descriptor, "slots"));
        initialSlots(builder, document, array(step, "slots"));
        data(builder, array(descriptor, "data"), 0, true, null);
        player(builder, document, object(descriptor, "player"), Menu.fromId(menu), 0, true);
        if (step.has("goal")) goal(builder, document, object(step, "goal"), string(step, "hint", ""));
        return builder;
    }

    static void append(Builder builder, AuthoringDocument document, JsonObject step, int tick) {
        rejectOpeningData(object(step, "interface"));
        String type = string(step, "type", "").toLowerCase(Locale.ROOT);
        Menu menu = builder.menu();
        if ("hide_interface".equals(type)) { builder.action(action(tick, Operation.HIDE, -1)); return; }
        if ("await_interface".equals(type)) { goal(builder, document, object(step, "goal"), AuthoringCompiler.text(step.get("hint"), "")); return; }
        if ("set_interface_data".equals(type)) { data(builder, array(step, "data"), tick, false, step); return; }
        if ("set_interface_context".equals(type)) { player(builder, document, object(step, "player"), menu, tick, false); return; }
        if ("change_interface_slot".equals(type) || "set_interface_slots".equals(type)) {
            if (step.has("ghosts") && array(step, "ghosts").size() != 0)
                throw new IllegalArgumentException("Non-item ghost ingredients are not supported by vanilla practice adapters");
            JsonArray writes = array(step, "change_interface_slot".equals(type) ? "interfaceSlots" : "slots");
            if (AuthoringCompiler.bool(step, "replace", "change_interface_slot".equals(type))) {
                for (int i = 0; i < menu.slots + 36; i++) builder.action(new Action(tick, Operation.SET_SLOT, i, ItemStack.EMPTY, "", 0));
            }
            for (JsonElement element : writes) {
                JsonObject write = requireObject(element, "slot write");
                int slot = integer(write, "index", integer(write, "slotIndex", -1, -1, 98), 0, 98);
                ItemStack stack = write.has("ingredientId") ? item(document, write) : item(document, write.get("item"));
                builder.action(new Action(tick, Operation.SET_SLOT, slot, stack, "", 0));
            }
            return;
        }
        if ("click_interface".equals(type) || "interface_action".equals(type)) {
            String action = string(step, "action", "click").toLowerCase(Locale.ROOT);
            Operation operation;
            if ("right".equals(action)) operation = Operation.CLICK_RIGHT;
            else if ("left".equals(action) || "click".equals(action)) operation = "right".equals(string(step, "button", "left")) ? Operation.CLICK_RIGHT : Operation.CLICK_LEFT;
            else if ("quick_move".equals(action) || "transfer".equals(action)) operation = Operation.TRANSFER;
            else throw new IllegalArgumentException("Unsupported isolated interface action: " + action);
            int slot = integer(step, "slot", integer(object(step, "target"), "slot", -1, -1, 98), -1, 98);
            if (slot < 0 && step.has("pos")) {
                JsonArray pos = array(step, "pos");
                if (pos.size() != 2) throw new IllegalArgumentException("Click position must have two coordinates");
                for (JsonElement coordinate : pos) if (!coordinate.isJsonPrimitive()
                    || !coordinate.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Click coordinates must be numeric");
                double x = pos.get(0).getAsDouble(), y = pos.get(1).getAsDouble();
                if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1)
                    throw new IllegalArgumentException("Click position outside normalized interface");
                slot = PracticeLayout.hit(menu, x * PracticeLayout.WIDTH, y * PracticeLayout.height(menu));
            }
            if (slot < 0) throw new IllegalArgumentException("Action needs a virtual slot target");
            builder.action(action(tick, operation, slot));
            return;
        }
        throw new IllegalArgumentException("Unknown interface step: " + type);
    }

    private static Action action(int tick, Operation operation, int slot) { return new Action(tick, operation, slot, ItemStack.EMPTY, "", 0); }
    private static void rejectOpeningData(JsonObject descriptor) {
        if (!descriptor.has("extraData") || descriptor.get("extraData").isJsonNull()) return;
        JsonElement data = descriptor.get("extraData");
        if (!data.isJsonPrimitive() || !data.getAsJsonPrimitive().isString() || !data.getAsString().isEmpty())
            throw new IllegalArgumentException("interface.extraData opening payload is unsupported in 1.12; retain it in a draft and replace it with an explicit isolated vanilla descriptor before publishing");
    }
    private static void initialSlots(Builder builder, AuthoringDocument document, JsonArray array) {
        if (array.size() > 98) throw new IllegalArgumentException("Too many initial slots");
        for (JsonElement element : array) {
            JsonObject slot = requireObject(element, "initial slot");
            builder.slot(integer(slot, "index", -1, 0, 98), item(document, slot.get("item")));
        }
    }
    private static void data(Builder builder, JsonArray array, int tick, boolean initial, JsonObject step) {
        for (JsonElement element : array) {
            JsonObject value = requireObject(element, "data");
            for (String field : value.keySet()) if (!"key".equals(field) && !"id".equals(field) && !"value".equals(field)
                && !"to".equals(field) && (!"from".equals(field) || initial))
                throw new IllegalArgumentException("Unsupported virtual data field: " + field);
            if (value.has("key") && value.get("key").isJsonNull()) throw new IllegalArgumentException("Data key must be a string");
            String key = string(value, "key", "");
            if (key.isEmpty() && !value.has("id")) throw new IllegalArgumentException("Data key or numeric id required");
            if (value.has("id") || key.isEmpty()) {
                int id = integer(value, "id", -1, 0, 3);
                String mapped = new String[] {"burnTime", "fuelTime", "cookTime", "cookTotal"}[id];
                if (!key.isEmpty() && !key.equals(mapped)) throw new IllegalArgumentException("Data key and numeric id disagree");
                key = mapped;
            }
            if (!"burnTime".equals(key) && !"fuelTime".equals(key) && !"cookTime".equals(key)
                && !"cookTotal".equals(key) && !"locked".equals(key)) throw new IllegalArgumentException("Unsupported virtual data key: " + key);
            boolean tween = !initial && (value.has("id") || value.has("from") || value.has("to"));
            if (tween) {
                if (!value.has("to") && !value.has("value")) throw new IllegalArgumentException("Data tween requires to or value");
                int number = value.has("to") ? integer(value, "to", 0, 0, 100000) : integer(value, "value", 0, 0, 100000);
                if (value.has("value")) integer(value, "value", 0, 0, 100000);
                int from = integer(value, "from", number, 0, 100000);
                int duration = integer(step, "duration", 1, 0, 24000);
                builder.action(Action.dataTween(tick, key, from, number, duration));
            } else {
                int number = integer(value, "value", integer(value, "to", 0, 0, 100000), 0, 100000);
                if (initial) builder.data(key, number);
                else builder.action(new Action(tick, Operation.SET_DATA, -1, ItemStack.EMPTY, key, number));
            }
        }
    }
    private static void player(Builder builder, AuthoringDocument document, JsonObject player, Menu menu, int tick, boolean initial) {
        JsonObject inventory = object(player, "inventory");
        if (inventory.has("armor") || inventory.has("offhand")) throw new IllegalArgumentException("Armor/offhand practice context is not supported");
        if ("empty".equals(string(inventory, "mode", ""))) {
            for (int i = 0; i < 36; i++) {
                if (initial) builder.slot(menu.slots + i, ItemStack.EMPTY);
                else builder.action(action(tick, Operation.SET_SLOT, menu.slots + i));
            }
        }
        for (JsonElement element : array(inventory, "items")) {
            JsonObject entry = requireObject(element, "virtual supply");
            int slot = integer(entry, "slot", -1, 0, 35) + menu.slots;
            ItemStack stack = item(document, entry.get("item"));
            if (initial) builder.slot(slot, stack); else builder.action(new Action(tick, Operation.SET_SLOT, slot, stack, "", 0));
        }
    }
    private static void goal(Builder builder, AuthoringDocument document, JsonObject goal, String description) {
        String kind = string(goal, "kind", "slot_contains").toUpperCase(Locale.ROOT);
        Goal.Kind goalKind;
        try { goalKind = Goal.Kind.valueOf(kind); }
        catch (IllegalArgumentException failure) { throw new IllegalArgumentException("Unsupported declarative goal: " + kind); }
        int count = integer(goal, "minCount", integer(goal, "count", 1, 0, 64), 0, 64);
        if (count == 0 && goalKind == Goal.Kind.SLOT_CONTAINS) goalKind = Goal.Kind.SLOT_EMPTY;
        ItemStack expected = ItemStack.EMPTY;
        if (goalKind == Goal.Kind.SLOT_CONTAINS || goalKind == Goal.Kind.SLOT_EXACT) {
            expected = item(document, goal.get("item"));
            if (expected.isEmpty()) throw new IllegalArgumentException("Item goal needs an explicit registered item");
            expected.setCount(count);
        }
        builder.goal(new Goal(goalKind, integer(goal, "slot", -1, -1, 98), expected, string(goal, "key", ""),
            integer(goal, "value", 0, 0, 100000), description));
    }
    private static ItemStack item(AuthoringDocument document, JsonElement element) {
        if (element == null || element.isJsonNull()) return ItemStack.EMPTY;
        JsonObject spec = element.isJsonPrimitive() ? new JsonObject() : requireObject(element, "item");
        String id = element.isJsonPrimitive() ? element.getAsString() : string(spec, "id", string(spec, "ingredientId", ""));
        if (spec.has("components")) throw new IllegalArgumentException("1.20 item components require explicit 1.12 NBT mapping");
        int count = integer(spec, "count", 1, 1, 64);
        NBTTagCompound tag = null;
        if (spec.has("nbt") || spec.has("legacyNbt")) {
            try { tag = JsonToNBT.getTagFromJson(string(spec, "nbt", string(spec, "legacyNbt", "{}"))); }
            catch (Exception failure) { throw new IllegalArgumentException("Invalid item NBT", failure); }
        }
        ItemStack stack;
        if (document != null) stack = AuthoringCompiler.resolveItem(document, id, count, tag);
        else {
            ResourceLocation key = new ResourceLocation(id);
            if (!Item.REGISTRY.containsKey(key)) throw new IllegalArgumentException("Unknown registered item: " + id);
            stack = new ItemStack(Item.REGISTRY.getObject(key), count, integer(spec, "meta", 0, 0, 32767));
            if (tag != null) stack.setTagCompound(tag);
        }
        if (spec.has("meta")) stack.setItemDamage(integer(spec, "meta", 0, 0, 32767));
        if (stack.isEmpty() || count > stack.getMaxStackSize()) throw new IllegalArgumentException("Invalid virtual stack count");
        return stack;
    }
    private static int integer(JsonObject object, String key, int fallback, int min, int max) {
        return AuthoringCompiler.integer(object, key, fallback, min, max);
    }
    private static String string(JsonObject object, String key, String fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) return fallback;
        if (!object.get(key).isJsonPrimitive() || !object.get(key).getAsJsonPrimitive().isString())
            throw new IllegalArgumentException(key + " must be a string");
        return object.get(key).getAsString();
    }
    private static JsonObject requireObject(JsonElement element, String name) {
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException(name + " must be an object");
        return element.getAsJsonObject();
    }
    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) ? requireObject(parent.get(key), key) : new JsonObject();
    }
    private static JsonArray array(JsonObject parent, String key) {
        if (!parent.has(key)) return new JsonArray();
        if (!parent.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        JsonArray array = parent.getAsJsonArray(key);
        if (array.size() > 256) throw new IllegalArgumentException(key + " exceeds virtual interface budget");
        return array;
    }
}
