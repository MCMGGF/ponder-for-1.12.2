package net.createmod.ponder.foundation.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import net.minecraft.util.ResourceLocation;

/** A draw-only layout and its tracks, measured in ticks from the GUI overlay's start. */
public final class GuiDemoDefinition implements GuiDemoTimeline {
    private static final int MAX_SIZE = 1024;
    private static final int MAX_TICK = 1_000_000;
    private static final Pattern ID = Pattern.compile("[a-zA-Z0-9_-]{1,64}");
    private static final Pattern RESOURCE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final State DEFAULT = new State(true, false, false, "", 0, null, 1, 0);

    enum Kind {
        SLOT(Set.of("item", "count", "meta")),
        BUTTON(Set.of("text", "active")),
        TAB(Set.of("text", "active")),
        PROGRESS(Set.of("value")),
        STATUS(Set.of("text")),
        HIGHLIGHT(Set.of());

        private final Set<String> stateFields;

        Kind(Set<String> stateFields) {
            this.stateFields = stateFields;
        }
    }

    record State(boolean visible, boolean highlighted, boolean active, String text, float value,
        ResourceLocation item, int count, int meta) {
    }

    record Bounds(int x, int y, int width, int height) {
    }

    record Widget(Kind kind, Bounds bounds, int color, NavigableMap<Integer, State> track) {
        State at(float tick) {
            return track.floorEntry((int) Math.floor(tick)).getValue();
        }
    }

    record Pointer(float x, float y, boolean pressed) {
    }

    private final int width;
    private final int height;
    private final ResourceLocation background;
    private final List<Widget> widgets;
    private final NavigableMap<Integer, Pointer> pointers;
    private final int lastKeyframeTick;

    private GuiDemoDefinition(int width, int height, ResourceLocation background, List<Widget> widgets,
        NavigableMap<Integer, Pointer> pointers, int lastKeyframeTick) {
        this.width = width;
        this.height = height;
        this.background = background;
        this.widgets = List.copyOf(widgets);
        this.pointers = Collections.unmodifiableNavigableMap(pointers);
        this.lastKeyframeTick = lastKeyframeTick;
    }

    public static GuiDemoDefinition parse(JsonObject root) {
        fields(root, Set.of("width", "height", "background", "widgets", "keyframes"));
        int width = integer(root, "width", -1, 1, MAX_SIZE);
        int height = integer(root, "height", -1, 1, MAX_SIZE);
        ResourceLocation background = resource(root, "background", null);
        if (background != null && (!background.getPath().startsWith("textures/")
            || !background.getPath().endsWith(".png"))) {
            throw new IllegalArgumentException("background must be a textures/ PNG resource");
        }

        Map<String, Widget> widgets = new LinkedHashMap<>();
        for (JsonElement value : array(root, "widgets", 256)) {
            JsonObject data = object(value, "widget");
            String id = string(data, "id", "");
            if (!ID.matcher(id).matches() || widgets.containsKey(id)) {
                throw new IllegalArgumentException("Invalid or duplicate widget id: " + id);
            }
            widgets.put(id, widget(data, width, height));
        }

        NavigableMap<Integer, JsonObject> frames = keyframes(root, Set.of("tick", "pointer", "updates"), MAX_TICK);

        NavigableMap<Integer, Pointer> pointers = new TreeMap<>();
        for (Map.Entry<Integer, JsonObject> entry : frames.entrySet()) {
            int tick = entry.getKey();
            JsonObject frame = entry.getValue();
            try {
                if (frame.has("pointer")) {
                    pointers.put(tick, pointer(frame.get("pointer"), widgets, width, height));
                }
                if (frame.has("updates")) {
                    for (Map.Entry<String, JsonElement> update : object(frame.get("updates"), "updates").entrySet()) {
                        Widget widget = getWidget(widgets, update.getKey());
                        JsonObject data = object(update.getValue(), "update for " + update.getKey());
                        stateFields(data, widget.kind(), Set.of());
                        widget.track().put(tick, state(data, widget.track().lastEntry().getValue()));
                    }
                }
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Keyframe " + tick + ": " + invalid.getMessage(), invalid);
            }
        }

        List<Widget> tracks = new ArrayList<>(widgets.size());
        for (Widget widget : widgets.values()) {
            tracks.add(new Widget(widget.kind(), widget.bounds(), widget.color(),
                Collections.unmodifiableNavigableMap(widget.track())));
        }
        return new GuiDemoDefinition(width, height, background, tracks, pointers, frames.isEmpty() ? 0 : frames.lastKey());
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public int lastKeyframeTick() {
        return lastKeyframeTick;
    }

    @Override
    public PonderGuiSnapshotRegistry.SnapshotProvider provider(int startTick) {
        return GuiDemoSnapshot.provider(this, startTick);
    }

    ResourceLocation background() {
        return background;
    }

    List<Widget> widgets() {
        return widgets;
    }

    Pointer pointerAt(float tick) {
        int wholeTick = (int) Math.floor(tick);
        Map.Entry<Integer, Pointer> before = pointers.floorEntry(wholeTick);
        if (before == null || before.getValue() == null) {
            return null;
        }
        Map.Entry<Integer, Pointer> after = pointers.higherEntry(wholeTick);
        Pointer start = before.getValue();
        if (after == null || after.getValue() == null) {
            return start;
        }
        Pointer end = after.getValue();
        float fraction = (tick - before.getKey()) / (after.getKey() - before.getKey());
        return interpolatePointer(start, end, fraction);
    }

    static Pointer interpolatePointer(Pointer start, Pointer end, float fraction) {
        return new Pointer(start.x() + (end.x() - start.x()) * fraction,
            start.y() + (end.y() - start.y()) * fraction, start.pressed());
    }

    static NavigableMap<Integer, JsonObject> keyframes(JsonObject root, Set<String> allowed, int maxTick) {
        NavigableMap<Integer, JsonObject> frames = new TreeMap<>();
        for (JsonElement value : array(root, "keyframes", 4096)) {
            JsonObject frame = object(value, "keyframe");
            fields(frame, allowed);
            int tick = integer(frame, "tick", -1, 0, maxTick);
            if (frames.putIfAbsent(tick, frame) != null) {
                throw new IllegalArgumentException("Duplicate keyframe tick: " + tick);
            }
        }
        return frames;
    }

    private static Widget widget(JsonObject data, int canvasWidth, int canvasHeight) {
        Kind kind;
        String type = string(data, "type", "");
        try {
            kind = Kind.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Unknown widget type: " + type, invalid);
        }
        stateFields(data, kind, Set.of("id", "type", "x", "y", "width", "height", "color"));
        int x = integer(data, "x", -1, 0, canvasWidth - 1);
        int y = integer(data, "y", -1, 0, canvasHeight - 1);
        int min = kind == Kind.SLOT ? 18 : 1;
        int size = kind == Kind.SLOT ? 18 : -1;
        int width = integer(data, "width", size, min, canvasWidth - x);
        int height = integer(data, "height", size, min, canvasHeight - y);
        int color = 0;
        if (data.has("color")) {
            String hex = string(data, "color", "");
            if (!hex.matches("#[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException("color must be #RRGGBB");
            }
            color = 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
        }
        NavigableMap<Integer, State> track = new TreeMap<>();
        track.put(0, state(data, DEFAULT));
        return new Widget(kind, new Bounds(x, y, width, height), color, track);
    }

    private static Pointer pointer(JsonElement value, Map<String, Widget> widgets, int width, int height) {
        if (value.isJsonNull()) {
            return null;
        }
        JsonObject data = object(value, "pointer");
        fields(data, Set.of("target", "x", "y", "pressed"));
        boolean pressed = bool(data, "pressed", false);
        if (data.has("target")) {
            if (data.has("x") || data.has("y")) {
                throw new IllegalArgumentException("A pointer target cannot be combined with x or y");
            }
            Bounds bounds = getWidget(widgets, string(data, "target", "")).bounds();
            return new Pointer(bounds.x() + (bounds.width() - 1) / 2F,
                bounds.y() + (bounds.height() - 1) / 2F, pressed);
        }
        return new Pointer(integer(data, "x", -1, 0, width - 1), integer(data, "y", -1, 0, height - 1), pressed);
    }

    private static Widget getWidget(Map<String, Widget> widgets, String id) {
        Widget widget = widgets.get(id);
        if (widget == null) {
            throw new IllegalArgumentException("Unknown widget: " + id);
        }
        return widget;
    }

    private static State state(JsonObject data, State previous) {
        ResourceLocation item = resource(data, "item", previous.item());
        boolean changedItem = data.has("item") && !java.util.Objects.equals(item, previous.item());
        int count = integer(data, "count", changedItem ? 1 : previous.count(), 1, 127);
        int meta = integer(data, "meta", changedItem ? 0 : previous.meta(), 0, 32767);
        return new State(bool(data, "visible", previous.visible()), bool(data, "highlighted", previous.highlighted()),
            bool(data, "active", previous.active()), string(data, "text", previous.text()),
            progress(data, previous.value()), item, count, meta);
    }

    private static void stateFields(JsonObject data, Kind kind, Set<String> layout) {
        for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
            String key = entry.getKey();
            if (!layout.contains(key) && !kind.stateFields.contains(key)
                && !key.equals("visible") && !key.equals("highlighted")) {
                throw new IllegalArgumentException("Unknown " + kind.name().toLowerCase(Locale.ROOT) + " field: " + key);
            }
        }
    }

    static void fields(JsonObject data, Set<String> allowed) {
        for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                throw new IllegalArgumentException("Unknown field: " + entry.getKey());
            }
        }
    }

    static JsonObject object(JsonElement value, String name) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(name + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject data, String key, int max) {
        JsonElement value = data.get(key);
        if (value == null) {
            return new JsonArray();
        }
        if (!value.isJsonArray() || value.getAsJsonArray().size() > max) {
            throw new IllegalArgumentException(key + " must be an array with at most " + max + " entries");
        }
        return value.getAsJsonArray();
    }

    static String string(JsonObject data, String key, String fallback) {
        JsonElement value = data.get(key);
        if (value == null) {
            return fallback;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().length() > 512) {
            throw new IllegalArgumentException(key + " must be a string of at most 512 characters");
        }
        return value.getAsString();
    }

    static boolean bool(JsonObject data, String key, boolean fallback) {
        JsonElement value = data.get(key);
        if (value == null) {
            return fallback;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return value.getAsBoolean();
    }

    static int integer(JsonObject data, String key, int fallback, int min, int max) {
        return integer(data.get(key), key, fallback, min, max);
    }

    static int integer(JsonElement value, String key, int fallback, int min, int max) {
        int result = fallback;
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            try {
                result = value.getAsBigDecimal().intValueExact();
            } catch (NumberFormatException | ArithmeticException invalid) {
                throw integerError(key, min, max);
            }
        } else if (value != null) {
            throw integerError(key, min, max);
        }
        if (result < min || result > max) {
            throw integerError(key, min, max);
        }
        return result;
    }

    private static IllegalArgumentException integerError(String key, int min, int max) {
        return new IllegalArgumentException(key + " must be an integer between " + min + " and " + max);
    }

    private static float progress(JsonObject data, float fallback) {
        JsonElement value = data.get("value");
        if (value == null) {
            return fallback;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            double number = value.getAsDouble();
            if (number >= 0 && number <= 1) {
                return (float) number;
            }
        }
        throw new IllegalArgumentException("value must be a number between 0 and 1");
    }

    static ResourceLocation resource(JsonObject data, String key, ResourceLocation fallback) {
        JsonElement value = data.get(key);
        if (value == null) {
            return fallback;
        }
        if (value.isJsonNull()) {
            return null;
        }
        String id = string(data, key, "");
        if (!RESOURCE.matcher(id).matches()) {
            throw new IllegalArgumentException(key + " must be a resource ID with a namespace");
        }
        ResourceLocation location = new ResourceLocation(id);
        for (String part : location.getPath().split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException(key + " contains an invalid resource path");
            }
        }
        return location;
    }
}
