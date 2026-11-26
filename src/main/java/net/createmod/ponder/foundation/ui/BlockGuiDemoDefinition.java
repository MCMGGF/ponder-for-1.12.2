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
import com.google.gson.JsonParser;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import net.createmod.ponder.foundation.ui.GuiDemoDefinition.Pointer;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.SnapshotProvider;
import net.createmod.ponder.foundation.ui.sandbox.SandboxRecordings;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/** Screen, slot and property keyframes on a detached native block GUI. */
public final class BlockGuiDemoDefinition implements GuiDemoTimeline {
    private static final String MAIN_SCREEN = "main";
    private static final int MAX_DEMO_TICK = 23_999;
    private static final int MAX_SLOT = 4095;
    private static final int MAX_POINTER_COORDINATE = 4095;
    private static final int MAX_CAPTURE_MARGIN = 512;
    private static final int MAX_NBT_LENGTH = 8192;
    private static final int MAX_NBT_DEPTH = 64;

    public record Insets(int left, int top, int right, int bottom) {
        private static final Insets NONE = new Insets(0, 0, 0, 0);
    }

    public record Stack(ResourceLocation item, int count, int meta, NBTTagCompound nbt) {
        @Override
        public NBTTagCompound nbt() {
            return nbt == null ? null : nbt.copy();
        }
    }

    /** An absent slot keeps its initial value. A slot mapped to null is explicitly empty. */
    record State(Map<Integer, Stack> slots, Map<Integer, Integer> properties) {
    }

    record Screen(String name, Integer guiId, String guiModId, ResourceLocation factory, Insets capture) {
    }

    private static final class ScreenTracks {
        private final Screen screen;
        private final Map<Integer, NavigableMap<Integer, Stack>> slots = new TreeMap<>();
        private final Map<Integer, NavigableMap<Integer, Integer>> properties = new TreeMap<>();
        private final NavigableMap<Integer, PointerTarget> pointers = new TreeMap<>();

        private ScreenTracks(Screen screen) {
            this.screen = screen;
        }

        private void readUpdates(JsonObject frame, int tick) {
            readTrackUpdates(frame, "slots", MAX_SLOT, tick, slots, BlockGuiDemoDefinition::readStack);
            readTrackUpdates(frame, "properties", Integer.MAX_VALUE, tick, properties,
                value -> integer(value, "property value", 0, Integer.MIN_VALUE, Integer.MAX_VALUE));
            if (frame.has("pointer")) {
                pointers.put(tick, readPointer(frame.get("pointer"), screen.capture()));
            }
        }
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
    private final ScreenTracks main;
    private final NavigableMap<Integer, ScreenTracks> screenChanges = new TreeMap<>();
    private final int lastKeyframeTick;
    private final SandboxGuiScript sandbox;
    private final boolean preview;
    private final String rawJson;

    private BlockGuiDemoDefinition(JsonObject root, JsonObject source, NavigableMap<Integer, JsonObject> frames) {
        block = requiredResource(source, "block");
        meta = integer(source, "meta", 0, 0, 15);
        tileNbt = nbt(source, "tileNbt");
        Screen defaults = new Screen(MAIN_SCREEN, null, block.getNamespace(), null, Insets.NONE);
        Screen mainScreen = readScreen(source, MAIN_SCREEN, "source", defaults);
        preview = root.has("preview");
        if (preview) {
            JsonObject previewSpec = object(root.get("preview"), "preview");
            fields(previewSpec, Set.of("guiId", "mod"));
            mainScreen = readScreen(previewSpec, MAIN_SCREEN, "preview", mainScreen);
        }
        main = new ScreenTracks(mainScreen);
        sandbox = root.has("sandbox") ? SandboxGuiScript.parse(object(root.get("sandbox"), "sandbox"), main.screen.capture()) : null;
        rawJson = root.toString();
        Map<String, ScreenTracks> tracksByScreen = readScreens(root.get("screens"), main);

        ScreenTracks active = main;
        for (Map.Entry<Integer, JsonObject> entry : frames.entrySet()) {
            int tick = entry.getKey();
            JsonObject frame = entry.getValue();
            try {
                String name = string(frame, "screen", active.screen.name());
                ScreenTracks selected = tracksByScreen.get(name);
                if (selected == null) {
                    throw new IllegalArgumentException("Unknown screen: " + name);
                }
                if (selected != active) {
                    screenChanges.put(tick, selected);
                    active = selected;
                }
                active.readUpdates(frame, tick);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Keyframe " + tick + ": " + invalid.getMessage(), invalid);
            }
        }
        lastKeyframeTick = sandbox != null ? sandbox.actions().getLast().tick() : frames.isEmpty() ? 0 : frames.lastKey();
    }

    public static BlockGuiDemoDefinition parse(JsonObject root) {
        boolean recorded = root.has("sandbox");
        fields(root, recorded ? Set.of("source", "sandbox", "preview") : Set.of("source", "screens", "keyframes"));
        JsonObject source = object(root.get("source"), "source");
        fields(source, recorded ? Set.of("type", "block", "meta", "tileNbt", "capture")
            : Set.of("type", "block", "meta", "tileNbt", "guiId", "mod", "factory", "capture"));
        if (!"block_gui".equals(string(source, "type", ""))) {
            throw new IllegalArgumentException("GUI demo source.type must be block_gui");
        }

        return new BlockGuiDemoDefinition(root, source,
            keyframes(root, Set.of("tick", "screen", "slots", "properties", "pointer"), MAX_DEMO_TICK));
    }

    @Override
    public int lastKeyframeTick() {
        return lastKeyframeTick;
    }

    @Override
    public SnapshotProvider provider(int startTick) {
        if (sandbox() != null) {
            return SandboxRecordings.onView(rawJson(), startTick,
                preview ? BlockGuiDemoSnapshot.preview(this, startTick) : null);
        }
        return BlockGuiDemoSnapshot.provider(this, startTick);
    }

    public SandboxGuiScript sandbox() {
        return sandbox;
    }

    public JsonObject rawJson() {
        return new JsonParser().parse(rawJson).getAsJsonObject();
    }

    public ResourceLocation block() {
        return block;
    }

    public int meta() {
        return meta;
    }

    public NBTTagCompound tileNbt() {
        return tileNbt == null ? null : tileNbt.copy();
    }

    Integer guiId() {
        return main.screen.guiId();
    }

    String guiModId() {
        return main.screen.guiModId();
    }

    public Insets capture() {
        return main.screen.capture();
    }

    Screen screenAt(float tick) {
        return tracksAt((int) Math.floor(tick)).screen;
    }

    State stateAt(float tick) {
        int wholeTick = (int) Math.floor(tick);
        ScreenTracks tracks = tracksAt(wholeTick);
        return new State(valuesAt(tracks.slots, wholeTick), valuesAt(tracks.properties, wholeTick));
    }

    Pointer pointerAt(float tick, Function<Integer, Pointer> slotCenter) {
        int wholeTick = (int) Math.floor(tick);
        Map.Entry<Integer, ScreenTracks> visit = screenChanges.floorEntry(wholeTick);
        NavigableMap<Integer, PointerTarget> pointers = (visit == null ? main : visit.getValue()).pointers;
        Map.Entry<Integer, PointerTarget> before = pointers.floorEntry(wholeTick);
        Pointer start = resolvePointer(before, slotCenter);
        if (start == null) {
            return null;
        }
        Map.Entry<Integer, PointerTarget> after = pointers.higherEntry(wholeTick);
        Integer nextSwitch = screenChanges.higherKey(wholeTick);
        // A returning screen keeps its pointer, and motion starts with targets authored in this visit.
        if (after == null || (visit != null && before.getKey() < visit.getKey())
            || (nextSwitch != null && after.getKey() >= nextSwitch)) {
            return start;
        }
        Pointer end = resolvePointer(after, slotCenter);
        if (end == null) {
            return start;
        }
        float fraction = (tick - before.getKey()) / (after.getKey() - before.getKey());
        return GuiDemoDefinition.interpolatePointer(start, end, fraction);
    }

    private ScreenTracks tracksAt(int tick) {
        Map.Entry<Integer, ScreenTracks> entry = screenChanges.floorEntry(tick);
        return entry == null ? main : entry.getValue();
    }

    private Map<String, ScreenTracks> readScreens(JsonElement value, ScreenTracks main) {
        Map<String, ScreenTracks> screens = new HashMap<>();
        screens.put(MAIN_SCREEN, main);
        if (value == null) {
            return screens;
        }
        for (Map.Entry<String, JsonElement> entry : object(value, "screens").entrySet()) {
            String name = entry.getKey();
            if (name.isBlank() || name.length() > 512 || MAIN_SCREEN.equals(name)) {
                throw new IllegalArgumentException("Invalid screen name: " + name);
            }
            String path = "screens." + name;
            JsonObject definition = object(entry.getValue(), path);
            try {
                fields(definition, Set.of("guiId", "mod", "factory", "capture"));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException(path + ": " + invalid.getMessage(), invalid);
            }
            screens.put(name, new ScreenTracks(readScreen(definition, name, path, main.screen)));
        }
        return screens;
    }

    private Screen readScreen(JsonObject definition, String name, String path, Screen defaults) {
        Insets capture = definition.has("capture")
            ? readCapture(definition.get("capture"), path + ".capture") : defaults.capture();
        if (definition.has("factory")) {
            if (definition.has("guiId") || definition.has("mod")) {
                throw new IllegalArgumentException(path + ".factory cannot be combined with guiId or mod");
            }
            ResourceLocation factory = requiredResource(definition, "factory");
            return new Screen(name, null, null, factory, capture);
        }
        if (defaults.factory() != null && !definition.has("guiId") && !definition.has("mod")) {
            return new Screen(name, null, null, defaults.factory(), capture);
        }
        Integer guiId = defaults.guiId();
        if (definition.has("guiId")) {
            guiId = integer(definition.get("guiId"), path + ".guiId", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        if (guiId == null && definition.has("mod")) {
            throw new IllegalArgumentException(path + ".mod requires " + path + ".guiId");
        }
        String mod = string(definition, "mod",
            defaults.guiModId() == null ? block.getNamespace() : defaults.guiModId());
        if (!mod.matches("[a-z0-9_.-]+") || mod.equals(".") || mod.equals("..")) {
            throw new IllegalArgumentException(path + ".mod must be a mod id");
        }
        return new Screen(name, guiId, mod, null, capture);
    }

    private static Pointer resolvePointer(Map.Entry<Integer, PointerTarget> entry,
        Function<Integer, Pointer> slotCenter) {
        return entry == null || entry.getValue() == null ? null : entry.getValue().resolve(slotCenter);
    }

    private static Insets readCapture(JsonElement value, String path) {
        JsonObject capture = object(value, path);
        fields(capture, Set.of("left", "top", "right", "bottom"));
        return new Insets(
            integer(capture.get("left"), path + ".left", 0, 0, MAX_CAPTURE_MARGIN),
            integer(capture.get("top"), path + ".top", 0, 0, MAX_CAPTURE_MARGIN),
            integer(capture.get("right"), path + ".right", 0, 0, MAX_CAPTURE_MARGIN),
            integer(capture.get("bottom"), path + ".bottom", 0, 0, MAX_CAPTURE_MARGIN));
    }

    private static PointerTarget readPointer(JsonElement value, Insets capture) {
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
        return new PointerTarget(null, integer(pointer, "x", -capture.left() - 1, -capture.left(), MAX_POINTER_COORDINATE),
            integer(pointer, "y", -capture.top() - 1, -capture.top(), MAX_POINTER_COORDINATE), pressed);
    }

    static Stack readStack(JsonElement value) {
        if (value.isJsonNull()) {
            return null;
        }
        JsonObject stack = object(value, "slot stack");
        fields(stack, Set.of("item", "count", "meta", "nbt"));
        return new Stack(requiredResource(stack, "item"), integer(stack, "count", 1, 1, 127),
            integer(stack, "meta", 0, 0, 32767), nbt(stack, "nbt"));
    }

    static ResourceLocation requiredResource(JsonObject data, String key) {
        ResourceLocation id = resource(data, key, null);
        if (id == null || id.getNamespace().equals(".") || id.getNamespace().equals("..")) {
            throw new IllegalArgumentException(key + " must be a resource ID with a namespace");
        }
        return id;
    }

    static NBTTagCompound nbt(JsonObject data, String key) {
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
