package net.createmod.ponder.foundation.ui.sandbox;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.createmod.ponder.authoring.assets.PngAssetValidator;

/** Immutable frame index. Validate the entire generation before publishing any of its frames. */
final class RecordingFrames {
    record Frame(Path path, int width, int height) {}

    private final int[] ticks;
    private final Frame[] frames;
    private final boolean[] hidden;

    private RecordingFrames(int count) {
        ticks = new int[count];
        frames = new Frame[count];
        hidden = new boolean[count];
    }

    static RecordingFrames read(Path directory) throws IOException {
        Path root = directory.toRealPath();
        Path manifest = inside(root, "frames.json");
        if (Files.size(manifest) > 4 * 1024 * 1024) throw new IOException("Oversized recording manifest");
        try {
            JsonObject json = new JsonParser().parse(Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonObject();
            int duration = integer(json, "duration");
            JsonArray entries = json.getAsJsonArray("frames");
            if (duration < 1 || duration > 24000 || entries.size() < 1 || entries.size() > duration)
                throw new IOException("Invalid recording duration or frame count");
            RecordingFrames result = new RecordingFrames(entries.size());
            Map<Path, Frame> validated = new HashMap<>();
            for (int i = 0; i < entries.size(); i++) {
                JsonObject entry = entries.get(i).getAsJsonObject();
                int tick = integer(entry, "tick");
                if ((i == 0 ? tick != 0 : tick <= result.ticks[i - 1]) || tick >= duration)
                    throw new IOException("Invalid recording frame tick " + tick);
                String name = entry.get("file").getAsString();
                if (!name.matches("(?:[A-Za-z0-9_-][A-Za-z0-9_.-]*/)*[A-Za-z0-9_-][A-Za-z0-9_.-]*\\.png"))
                    throw new IOException("Invalid recording frame filename");
                Path path = inside(root, name);
                Frame frame = validated.get(path);
                if (frame == null) {
                    if (Files.size(path) > 64L * 1024 * 1024) throw new IOException("Oversized recording frame");
                    byte[] bytes = Files.readAllBytes(path);
                    PngAssetValidator.validate(bytes, 129L * 1024 * 1024);
                    ByteBuffer header = ByteBuffer.wrap(bytes);
                    frame = new Frame(path, header.getInt(16), header.getInt(20));
                    validated.put(path, frame);
                }
                result.ticks[i] = tick;
                result.frames[i] = frame;
                result.hidden[i] = entry.has("hidden") && entry.get("hidden").getAsBoolean();
            }
            return result;
        } catch (RuntimeException invalid) {
            throw new IOException("Malformed recording manifest " + manifest, invalid);
        }
    }

    private static int integer(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Expected integer " + field);
        return value.getAsBigDecimal().intValueExact();
    }

    private static Path inside(Path root, String name) throws IOException {
        Path file = root.resolve(name).toRealPath();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IOException("Invalid recording file " + name);
        return file;
    }

    int indexAt(int tick) {
        int index = Arrays.binarySearch(ticks, tick);
        return index >= 0 ? index : Math.max(0, -index - 2);
    }

    Frame frame(int index) { return frames[index]; }
    boolean hidden(int index) { return hidden[index]; }
    Frame next(int index) { return index + 1 < frames.length ? frames[index + 1] : null; }
}
