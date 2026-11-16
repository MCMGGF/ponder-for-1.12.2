package net.createmod.ponder.authoring;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Bounded conversions of Ponderer's view steps; no render callbacks or cross-scene navigation. */
public final class CameraStepHandlers {
    private CameraStepHandlers() { }

    public static void register(AuthoringCompiler compiler) {
        for (String type : new String[] {"zoom_scene", "encapsulate_bounds", "next_scene"})
            compiler.registerHandler(type, new Handler(type));
    }

    private static final class Handler implements AuthoringCompiler.StepHandler {
        private final String type;
        Handler(String type) { this.type = type; }

        @Override
        public void validate(AuthoringDocument document, JsonObject step, String path,
            AuthoringValidation.Builder issues) {
            try {
                if ("zoom_scene".equals(type)) {
                    center(step);
                    multiplier(step);
                    duration(step);
                    AuthoringCompiler.bool(step, "waitForCompletion", true);
                    issues.warning("camera.overlay_reset", path,
                        "Ponderer zoom clears preceding text and outlines; later prompts and GUI snapshots remain visible");
                } else if ("encapsulate_bounds".equals(type)) {
                    bounds(step);
                } else {
                    issues.warning("camera.scene_boundary", path,
                        "next_scene is Ponderer's no-op boundary marker; use scenes[] for separately selectable scenes");
                }
            } catch (RuntimeException invalid) {
                issues.error("camera.invalid", path, invalid.getMessage());
            }
        }

        @Override
        public void apply(SceneBuilder scene, SceneBuildingUtil util, JsonObject step,
            AuthoringCompiler.StepContext context) {
            if ("zoom_scene".equals(type)) scene.zoomScene(center(step), multiplier(step), duration(step),
                AuthoringCompiler.bool(step, "waitForCompletion", true));
            else if ("encapsulate_bounds".equals(type)) scene.encapsulateBounds(bounds(step));
            else scene.getScene().recordOperation("scene.next_scene(boundary_marker)");
        }
    }

    private static Vec3d center(JsonObject step) {
        if (!step.has("point") || step.get("point").isJsonNull()) return null;
        JsonArray point = triple(step.get("point"), "point");
        double[] values = new double[3];
        for (int i = 0; i < 3; i++) {
            values[i] = number(point.get(i), "point");
            if (Math.abs(values[i]) > 30000000D) throw new IllegalArgumentException("Camera point exceeds the coordinate budget");
        }
        return new Vec3d(values[0], values[1], values[2]);
    }

    private static Float multiplier(JsonObject step) {
        if (!step.has("scale") || step.get("scale").isJsonNull()) return null;
        double scale = number(step.get("scale"), "scale");
        if (scale <= 0D || scale > 64D || (float) scale <= 0F)
            throw new IllegalArgumentException("Camera scale must be representable as a positive float in (0, 64]");
        if ((float) scale <= 0F) throw new IllegalArgumentException("Camera scale is too small for the preview renderer");
        return Float.valueOf((float) scale);
    }

    private static int duration(JsonObject step) {
        if (!step.has("duration")) return 20;
        double duration = number(step.get("duration"), "duration");
        if (duration != Math.rint(duration) || duration < 0 || duration > 72000)
            throw new IllegalArgumentException("Camera duration must be an integer in [0, 72000]");
        return (int) duration;
    }

    private static BlockPos bounds(JsonObject step) {
        if (!step.has("bounds")) throw new IllegalArgumentException("encapsulate_bounds requires bounds");
        JsonArray bounds = triple(step.get("bounds"), "bounds");
        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            double value = number(bounds.get(i), "bounds");
            if (value != Math.rint(value) || value < 1 || value > 256)
                throw new IllegalArgumentException("Bounds endpoints must be integers in [1, 256]");
            values[i] = (int) value;
        }
        return new BlockPos(values[0], values[1], values[2]);
    }

    private static JsonArray triple(JsonElement value, String field) {
        if (!value.isJsonArray() || value.getAsJsonArray().size() != 3)
            throw new IllegalArgumentException(field + " requires exactly three coordinates");
        return value.getAsJsonArray();
    }

    private static double number(JsonElement value, String field) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
            || !Double.isFinite(value.getAsDouble())) throw new IllegalArgumentException(field + " must be finite numeric data");
        return value.getAsDouble();
    }
}
