package net.createmod.ponder.authoring;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Explicit author edits only: never interacts with a world, renderer, inventory or network. */
public final class AuthoringCoordinateEdits {
    private AuthoringCoordinateEdits() {}

    private static boolean overlayTarget(JsonObject step) {
        return Arrays.asList("text", "shared_text", "show_controls", "show_image", "show_interface")
            .contains(AuthoringDocument.string(step, "type", ""));
    }

    public static List<String> fields(JsonObject step) {
        String type = AuthoringDocument.string(step, "type", "");
        List<String> result = new ArrayList<>();
        boolean linkedMotion = ("move_section".equals(type) || "rotate_section".equals(type))
            && !AuthoringDocument.string(step, "linkId", "").isEmpty();
        if (!linkedMotion && Arrays.asList("show_structure", "hide_section", "highlight_section", "toggle_redstone_power",
            "modify_block_entity_nbt", "move_section", "rotate_section", "show_section_and_merge",
            "restore_blocks", "replace_blocks", "text", "shared_text").contains(type)) {
            result.add("blockPos");
            if (step.has("blockPos")) result.add("blockPos2");
            result.add("selection");
        }
        else if (Arrays.asList("set_block", "destroy_block", "break_progress", "indicate_redstone", "indicate_success",
            "show_extra_structure", "show_image", "show_interface").contains(type)) result.add("blockPos");
        if (Arrays.asList("text", "shared_text", "show_controls", "show_image", "show_interface", "zoom_scene").contains(type)) result.add("point");
        if (Arrays.asList("create_entity", "create_item_entity", "spawn_particles").contains(type)) result.add("pos");
        if ("rotate_section".equals(type)) result.add("pivot");
        if ("create_entity".equals(type) && "minecraft:player".equals(AuthoringDocument.string(step, "entity", ""))
            && !step.has("yaw") && !step.has("pitch") && !hasRotationNbt(step)) result.add("lookAt");
        return Collections.unmodifiableList(result);
    }

    private static boolean hasRotationNbt(JsonObject step) {
        try { return ActorNbtAdapter.read(step).hasKey("Rotation"); }
        catch (RuntimeException invalidDraft) { return true; }
    }

    public static AuthoringDocument point(AuthoringDocument document, int scene, int step, String key, Vec3d point) {
        if (!Arrays.asList("point", "pos", "pivot", "lookAt").contains(key))
            throw new IllegalArgumentException("Unsupported absolute point field: " + key);
        JsonObject replacement = selected(document, scene, step);
        replacement.add(key, vector(point));
        if ("point".equals(key) && overlayTarget(replacement)) {
            replacement.remove("blockPos"); replacement.remove("blockPos2"); replacement.remove("positions"); replacement.remove("fullScene");
            replacement.remove("independentY");
            replacement.addProperty("pointSpace", "scene");
        }
        AuthoringCompiler.vector(replacement, key, Vec3d.ZERO);
        return AuthoringEdits.replaceStep(document, scene, step, replacement);
    }

    public static AuthoringDocument block(AuthoringDocument document, int scene, int step, String key, BlockPos position) {
        if (position == null || !("blockPos".equals(key) || "blockPos2".equals(key)))
            throw new IllegalArgumentException("An explicit block coordinate field is required");
        JsonObject replacement = selected(document, scene, step);
        replacement.add(key, vector(new Vec3d(position)));
        replacement.remove("fullScene"); replacement.remove("positions");
        replacement.remove("pointSpace");
        if (overlayTarget(replacement)) { replacement.remove("point"); replacement.remove("independentY"); }
        if ("blockPos".equals(key)) replacement.remove("blockPos2");
        AuthoringCompiler.blockPos(replacement, key);
        return AuthoringEdits.replaceStep(document, scene, step, replacement);
    }

    public static AuthoringDocument selection(AuthoringDocument document, int scene, int step, BlockPos first, BlockPos last) {
        if (first == null || last == null) throw new IllegalArgumentException("Two selection corners are required");
        JsonObject replacement = selected(document, scene, step);
        replacement.add("blockPos", vector(new Vec3d(first)));
        replacement.add("blockPos2", vector(new Vec3d(last)));
        replacement.remove("fullScene"); replacement.remove("positions");
        replacement.remove("pointSpace");
        if (overlayTarget(replacement)) { replacement.remove("point"); replacement.remove("independentY"); }
        AuthoringCompiler.volume(replacement);
        return AuthoringEdits.replaceStep(document, scene, step, replacement);
    }

    private static JsonObject selected(AuthoringDocument document, int scene, int step) {
        return AuthoringDocument.copy(document.getScenes().get(scene).getAsJsonArray("steps").get(step).getAsJsonObject());
    }
    private static JsonArray vector(Vec3d value) {
        if (value == null) throw new IllegalArgumentException("A point is required");
        JsonArray result = new JsonArray(); result.add(value.x); result.add(value.y); result.add(value.z); return result;
    }
}
