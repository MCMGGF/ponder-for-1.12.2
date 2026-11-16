package net.createmod.ponder.authoring;

import java.util.Arrays;
import com.google.gson.JsonObject;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.util.math.Vec3d;

/** Explicit scene coordinates do not acquire a block owner by flooring the point. */
public final class AuthoringPointSpace {
    private AuthoringPointSpace() { }
    public static boolean isScene(JsonObject data) {
        if (!data.has("pointSpace")) return false;
        String value = AuthoringCompiler.requiredString(data, "pointSpace");
        if (!"source".equals(value) && !"scene".equals(value)) throw new IllegalArgumentException("pointSpace must be source or scene");
        return "scene".equals(value);
    }
    public static void validate(JsonObject step) {
        if (!step.has("pointSpace")) return;
        if (!Arrays.asList("text", "shared_text", "show_controls", "show_image", "show_interface").contains(AuthoringDocument.string(step,"type","")))
            throw new IllegalArgumentException("pointSpace only applies to an overlay's explicit three-coordinate point");
        validateNative(step);
        if (isScene(step) && (step.has("blockPos") || step.has("blockPos2") || step.has("positions") || step.has("fullScene") || step.has("independentY")))
            throw new IllegalArgumentException("A scene point cannot also carry a source selection or independent caption position");
    }
    public static void validateNative(JsonObject data) {
        if (!data.has("pointSpace")) return;
        isScene(data);
        if (!data.has("point") || !data.get("point").isJsonArray() || data.getAsJsonArray("point").size() != 3)
            throw new IllegalArgumentException("pointSpace requires exactly three point coordinates");
        AuthoringCompiler.vector(data,"point",Vec3d.ZERO);
        if (isScene(data) && data.has("targetPositions") && (!data.get("targetPositions").isJsonArray() || data.getAsJsonArray("targetPositions").size() != 0))
            throw new IllegalArgumentException("A scene point cannot acquire source target positions");
    }
    public static void apply(PonderScene.OverlayEvent event, JsonObject step) {
        if (isScene(step)) event.pointAtScene(AuthoringCompiler.vector(step,"point",Vec3d.ZERO));
    }
}
