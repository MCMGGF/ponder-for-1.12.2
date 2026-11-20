package net.createmod.ponder.foundation.ui;

import static net.createmod.ponder.foundation.ui.GuiDemoDefinition.bool;
import static net.createmod.ponder.foundation.ui.GuiDemoDefinition.fields;
import static net.createmod.ponder.foundation.ui.GuiDemoDefinition.integer;
import static net.createmod.ponder.foundation.ui.GuiDemoDefinition.keyframes;
import static net.createmod.ponder.foundation.ui.GuiDemoDefinition.object;
import static net.createmod.ponder.foundation.ui.GuiDemoDefinition.resource;
import static net.createmod.ponder.foundation.ui.GuiDemoDefinition.string;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import net.createmod.ponder.foundation.ui.GuiDemoDefinition.Pointer;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.SnapshotProvider;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/** Slot and property keyframes on a detached native block GUI. */
public final class BlockGuiDemoDefinition implements GuiDemoTimeline {
    private static final int MAX_DEMO_TICK = 23_999;
    private static final int MAX_SLOT = 4095;
    private static final int MAX_POINTER_COORDINATE = 4095;
    private static final int MAX_NBT_LENGTH = 8192;
    private static final int MAX_NBT_DEPTH = 64;

    record Stack(ResourceLocation item, int count, int meta, NBTTagCompound nbt) {
        @Override
        public NBTTagCompound nbt() {
            return nbt == null ? null : nbt.copy();
        }
    }

    /** An absent slot keeps its initial value. A slot mapped to null is explicitly empty. */
    record State(Map<Integer, Stack> slots, Map<Integer, Integer> properties) {
    }

    private record PointerTarget(Integer slot, int x, int y, boolean pressed) {
        Pointer resolve(Function<Integer, Pointer> slotCenter) {
            if (slot == null) {
                return new Pointer(x, y, pressed);
            }
            Pointer center = slotCenter.apply(slot);
            return center == null ? null : new Pointer(center.x(), center.y(), pressed);
        }
    }

    private final ResourceLocation block;
    private final int meta;
    private final NBTTagCompound tileNbt;
    private final Integer guiId;
    private final String guiModId;
    private final NavigableMap<Integer, NavigableMap<Integer, Stack>> slots = new TreeMap<>();
    private final NavigableMap<Integer, NavigableMap<Integer, Integer>> properties = new TreeMap<>();
    private final NavigableMap<Integer, PointerTarget> pointers = new TreeMap<>();
    private final int lastKeyframeTick;

    private BlockGuiDemoDefinition(JsonObject source, NavigableMap<Integer, JsonObject> frames) {
        block = requiredResource(source, "block");
        meta = integer(source, "meta", 0, 0, 15);
        tileNbt = nbt(source, "tileNbt");
        if (!source.has("guiId") && source.has("mod")) {
            throw new IllegalArgumentException("source.mod requires source.guiId");
        }
        guiId = source.has("guiId") ? integer(source, "guiId", 0, Integer.MIN_VALUE, Integer.MAX_VALUE) : null;
        guiModId = string(source, "mod", block.getNamespace());
        if (!guiModId.matches("[a-z0-9_.-]+") || guiModId.equals(".") || guiModId.equals("..")) {
            throw new IllegalArgumentException("source.mod must be a mod id");
        }

        for (Map.Entry<Integer, JsonObject> entry : frames.entrySet()) {
            int tick = entry.getKey();
            JsonObject frame = entry.getValue();
            try {
                readTrackUpdates(frame, "slots", MAX_SLOT, tick, slots, BlockGuiDemoDefinition::readStack);
                readTrackUpdates(frame, "properties", Integer.MAX_VALUE, tick, properties,
                    value -> integer(value, "property value", 0, Integer.MIN_VALUE, Integer.MAX_VALUE));
                if (frame.has("pointer")) {
                    pointers.put(tick, readPointer(frame.get("pointer")));
                }
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Keyframe " + tick + ": " + invalid.getMessage(), invalid);
            }
        }
        lastKeyframeTick = frames.isEmpty() ? 0 : frames.lastKey();
    }

    public static BlockGuiDemoDefinition parse(JsonObject root) {
        fields(root, Set.of("source", "keyframes"));
        JsonObject source = object(root.get("source"), "source");
        fields(source, Set.of("type", "block", "meta", "tileNbt", "guiId", "mod"));
        if (!"block_gui".equals(string(source, "type", ""))) {
            throw new IllegalArgumentException("GUI demo source.type must be block_gui");
        }

        return new BlockGuiDemoDefinition(source,
            keyframes(root, Set.of("tick", "slots", "properties", "pointer"), MAX_DEMO_TICK));
    }

    @Override
    public int lastKeyframeTick() {
        return lastKeyframeTick;
    }

    @Override
    public SnapshotProvider provider(int startTick) {
        return BlockGuiDemoSnapshot.provider(this, startTick);
    }

    ResourceLocation block() {
        return block;
    }

    int meta() {
        return meta;
    }

    NBTTagCompound tileNbt() {
        return tileNbt == null ? null : tileNbt.copy();
    }

    Integer guiId() {
        return guiId;
    }

    String guiModId() {
        return guiModId;
    }

    Set<Integer> slotIds() {
        return Collections.unmodifiableSet(slots.keySet());
    }

    Set<Integer> propertyIds() {
        return Collections.unmodifiableSet(properties.keySet());
    }

    State stateAt(float tick) {
        int wholeTick = (int) Math.floor(tick);
        return new State(valuesAt(slots, wholeTick), valuesAt(properties, wholeTick));
    }

    Pointer pointerAt(float tick, Function<Integer, Pointer> slotCenter) {
        int wholeTick = (int) Math.floor(tick);
        Map.Entry<Integer, PointerTarget> before = pointers.floorEntry(wholeTick);
        Pointer start = resolvePointer(before, slotCenter);
        if (start == null) {
            return null;
        }
        Map.Entry<Integer, PointerTarget> after = pointers.higherEntry(wholeTick);
        Pointer end = resolvePointer(after, slotCenter);
        if (end == null) {
            return start;
        }
        float fraction = (tick - before.getKey()) / (after.getKey() - before.getKey());
        return GuiDemoDefinition.interpolatePointer(start, end, fraction);
    }

    private static Pointer resolvePointer(Map.Entry<Integer, PointerTarget> entry,
        Function<Integer, Pointer> slotCenter) {
        return entry == null || entry.getValue() == null ? null : entry.getValue().resolve(slotCenter);
    }

    private static PointerTarget readPointer(JsonElement value) {
        if (value.isJsonNull()) {
            return null;
        }
        JsonObject pointer = object(value, "pointer");
        fields(pointer, Set.of("slot", "x", "y", "pressed"));
        boolean pressed = bool(pointer, "pressed", false);
        if (pointer.has("slot")) {
            if (pointer.has("x") || pointer.has("y")) {
                throw new IllegalArgumentException("A pointer slot cannot be combined with x or y");
            }
            return new PointerTarget(integer(pointer, "slot", -1, 0, MAX_SLOT), 0, 0, pressed);
        }
        return new PointerTarget(null, integer(pointer, "x", -1, 0, MAX_POINTER_COORDINATE),
            integer(pointer, "y", -1, 0, MAX_POINTER_COORDINATE), pressed);
    }

    private static Stack readStack(JsonElement value) {
        if (value.isJsonNull()) {
            return null;
        }
        JsonObject stack = object(value, "slot stack");
        fields(stack, Set.of("item", "count", "meta", "nbt"));
        return new Stack(requiredResource(stack, "item"), integer(stack, "count", 1, 1, 127),
            integer(stack, "meta", 0, 0, 32767), nbt(stack, "nbt"));
    }

    private static ResourceLocation requiredResource(JsonObject data, String key) {
        ResourceLocation id = resource(data, key, null);
        if (id == null || id.getNamespace().equals(".") || id.getNamespace().equals("..")) {
            throw new IllegalArgumentException(key + " must be a resource ID with a namespace");
        }
        return id;
    }

    private static NBTTagCompound nbt(JsonObject data, String key) {
        JsonElement value = data.get(key);
        if (value == null) {
            return null;
        }
        String invalidValue = key + " must be an SNBT compound of at most " + MAX_NBT_LENGTH + " characters";
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(invalidValue);
        }
        String snbt = value.getAsString();
        if (snbt.length() > MAX_NBT_LENGTH) {
            throw new IllegalArgumentException(invalidValue);
        }
        checkNbtDepth(snbt, key);
        try {
            return JsonToNBT.getTagFromJson(snbt);
        } catch (NBTException invalid) {
            throw new IllegalArgumentException("Invalid " + key + ": " + invalid.getMessage(), invalid);
        }
    }

    private static void checkNbtDepth(String snbt, String key) {
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < snbt.length(); i++) {
            char c = snbt.charAt(i);
            if (quoted && c == '\\') {
                i++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (!quoted) {
                if (c == '[' || c == '{') {
                    if (++depth > MAX_NBT_DEPTH) {
                        throw new IllegalArgumentException(key + " must be nested at most " + MAX_NBT_DEPTH + " levels");
                    }
                } else if (c == ']' || c == '}') {
                    depth--;
                }
            }
        }
    }

    private static <T> Map<Integer, T> valuesAt(Map<Integer, NavigableMap<Integer, T>> tracks, int tick) {
        Map<Integer, T> state = new TreeMap<>();
        tracks.forEach((id, values) -> {
            Map.Entry<Integer, T> value = values.floorEntry(tick);
            if (value != null) {
                state.put(id, value.getValue());
            }
        });
        return Collections.unmodifiableMap(state);
    }

    private static <T> void readTrackUpdates(JsonObject frame, String name, int maxId, int tick,
        Map<Integer, NavigableMap<Integer, T>> tracks, Function<JsonElement, T> readValue) {
        if (!frame.has(name)) {
            return;
        }
        for (Map.Entry<String, JsonElement> update : object(frame.get(name), name).entrySet()) {
            String key = update.getKey();
            int id;
            try {
                id = Integer.parseInt(key);
                if (id < 0 || id > maxId || !key.equals(Integer.toString(id))) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException(name + " key " + key + " must be an integer between 0 and " + maxId);
            }
            try {
                T value = readValue.apply(update.getValue());
                tracks.computeIfAbsent(id, ignored -> new TreeMap<>()).put(tick, value);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException(name + "[" + id + "]: " + invalid.getMessage(), invalid);
            }
        }
    }
}
