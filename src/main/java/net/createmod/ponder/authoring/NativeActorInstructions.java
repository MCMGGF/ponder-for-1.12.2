package net.createmod.ponder.authoring;

import java.util.Locale;
import java.util.Map;
import com.google.gson.JsonObject;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.Vec3d;

final class NativeActorInstructions {
    private NativeActorInstructions() { }
    static void validate(AuthoringDocument document, JsonObject step, Map<Integer, PonderScene.ActorKind> actors) {
        int id = AuthoringCompiler.integer(step, "actorId", 0, 1, 65535);
        PonderScene.ActorKind kind = kind(step);
        PonderScene.ActorEventType action = action(step);
        for (String coordinate : new String[] {"pos", "offset", "lookAt", "rotation"})
            if (step.has(coordinate)) AuthoringCompiler.vector(step, coordinate, Vec3d.ZERO);
        if (action == PonderScene.ActorEventType.SPAWN) {
            if (actors.containsKey(id)) throw new IllegalArgumentException("Duplicate native actor id");
            AuthoringCompiler.vector(step, "pos", null);
            if (!step.has("pos")) throw new IllegalArgumentException("Actor spawn requires pos");
            actors.put(id, kind);
        } else if (actors.get(id) != kind) throw new IllegalArgumentException("Native actor action must follow a matching spawn");
        if (action == PonderScene.ActorEventType.MOVE && !step.has("offset")) throw new IllegalArgumentException("Actor move requires offset");
        if (action == PonderScene.ActorEventType.ROTATE && !step.has("rotation") && kind != PonderScene.ActorKind.CART) throw new IllegalArgumentException("Actor rotation requires rotation");
        if (action == PonderScene.ActorEventType.LOOK_AT && !step.has("lookAt")) throw new IllegalArgumentException("Actor look requires lookAt");
        if (action == PonderScene.ActorEventType.LOOK_AT || action == PonderScene.ActorEventType.HELD_ITEM
            || action == PonderScene.ActorEventType.SWING || action == PonderScene.ActorEventType.SNEAK)
            if (kind != PonderScene.ActorKind.PLAYER) throw new IllegalArgumentException("This action requires a PLAYER preview actor");
        if (action == PonderScene.ActorEventType.POSE && kind != PonderScene.ActorKind.BIRB) throw new IllegalArgumentException("Pose requires a BIRB preview actor");
        if (action == PonderScene.ActorEventType.SNEAK) AuthoringCompiler.bool(step, "sneaking", false);
        if (action == PonderScene.ActorEventType.MOVE) {
            boolean entrance = AuthoringCompiler.bool(step, "entranceMotion", false);
            boolean walk = AuthoringCompiler.bool(step, "walkAnimation", !entrance);
            if (entrance && (walk || AuthoringCompiler.duration(step, 0) <= 0))
                throw new IllegalArgumentException("Entrance MOVE requires positive duration and no walk animation");
        } else if (step.has("entranceMotion"))
            throw new IllegalArgumentException("entranceMotion applies only to a native MOVE");
        if (action == PonderScene.ActorEventType.NBT_PATCH) {
            AuthoringCompiler.requiredString(step, "nbt");
            ActorNbtAdapter.validate(document, step, kind == PonderScene.ActorKind.ITEM);
            if (ActorNbtAdapter.read(step).hasKey("Motion"))
                throw new IllegalArgumentException("Native NBT_PATCH requires an explicit MOVE for motion");
        }
        double angle = AuthoringCompiler.number(step, "angle", 0);
        if (Math.abs(angle) > 1000000) throw new IllegalArgumentException("Actor angle exceeds numeric budget");
        if (kind == PonderScene.ActorKind.ITEM && action == PonderScene.ActorEventType.SPAWN
            || action == PonderScene.ActorEventType.HELD_ITEM) stack(document, step, kind != PonderScene.ActorKind.ITEM);
    }
    static void apply(PonderScene scene, AuthoringDocument document, JsonObject step) {
        int id = 1000000 + AuthoringCompiler.integer(step, "actorId", 0, 1, 65535);
        PonderScene.ActorKind kind = kind(step);
        int duration = AuthoringCompiler.duration(step, 0);
        switch (action(step)) {
            case SPAWN:
                Vec3d pos = AuthoringCompiler.vector(step, "pos", Vec3d.ZERO);
                if (kind == PonderScene.ActorKind.ITEM) scene.recordItemActorSpawn(id, pos, duration,
                    AuthoringDocument.string(step, "displayName", null), stack(document, step, false));
                else scene.recordActorSpawn(id, kind, pos, duration, (float) AuthoringCompiler.number(step, "angle", 0), AuthoringDocument.string(step, "pose", null));
                break;
            case MOVE:
                if (AuthoringCompiler.bool(step, "entranceMotion", false))
                    scene.recordActorEntranceMove(id, kind, AuthoringCompiler.vector(step, "offset", Vec3d.ZERO), duration);
                else scene.recordActorMove(id, kind, AuthoringCompiler.vector(step, "offset", Vec3d.ZERO), duration,
                    AuthoringCompiler.bool(step, "walkAnimation", true));
                break;
            case ROTATE:
                if (kind == PonderScene.ActorKind.CART && !step.has("rotation")) scene.recordActorCartRotation(id, (float) AuthoringCompiler.number(step, "angle", 0), duration);
                else scene.recordActorRotation(id, kind, AuthoringCompiler.vector(step, "rotation", Vec3d.ZERO), duration);
                break;
            case HIDE: scene.recordActorHide(id, kind, duration); break;
            case POSE: scene.recordActorPose(id, AuthoringCompiler.requiredString(step, "pose")); break;
            case LOOK_AT: scene.recordPlayerLook(id, AuthoringCompiler.vector(step, "lookAt", Vec3d.ZERO), duration); break;
            case HELD_ITEM: scene.recordPlayerItem(id, stack(document, step, true)); break;
            case SWING: scene.recordPlayerSwing(id, duration); break;
            case SNEAK: scene.recordPlayerSneak(id, AuthoringCompiler.bool(step, "sneaking", false)); break;
            case NBT_PATCH:
                ItemStack current = ItemStack.EMPTY;
                for (net.createmod.ponder.foundation.PreviewActorTimeline.State state
                    : net.createmod.ponder.foundation.PreviewActorTimeline.evaluate(scene.getActorEvents(), scene.getTotalIdleTicks()))
                    if (state.actorId == id) current = state.getItemStack();
                ActorNbtAdapter.Patch patch = ActorNbtAdapter.apply(document, step, kind == PonderScene.ActorKind.ITEM, current);
                scene.recordActorNbtPatch(id, kind, patch.getData(), patch.getItem());
                break;
            default: throw new IllegalArgumentException("Unsupported native actor action");
        }
    }
    private static PonderScene.ActorKind kind(JsonObject step) {
        return PonderScene.ActorKind.valueOf(AuthoringCompiler.requiredString(step, "kind").toUpperCase(Locale.ROOT));
    }
    private static PonderScene.ActorEventType action(JsonObject step) {
        return PonderScene.ActorEventType.valueOf(AuthoringCompiler.requiredString(step, "action").toUpperCase(Locale.ROOT));
    }
    private static ItemStack stack(AuthoringDocument document, JsonObject step, boolean allowEmpty) {
        String id = AuthoringDocument.string(step, "item", "");
        if (allowEmpty && id.isEmpty()) return ItemStack.EMPTY;
        try {
            NBTTagCompound nbt = step.has("nbt") ? AuthoringCompiler.nbt(AuthoringCompiler.requiredString(step, "nbt")) : null;
            ItemStack stack = AuthoringCompiler.resolveItem(document, id, AuthoringCompiler.integer(step, "count", 1, 1, 64), nbt);
            stack.setItemDamage(AuthoringCompiler.integer(step, "meta", stack.getMetadata(), 0, 32767));
            return stack;
        } catch (Exception invalid) { throw new IllegalArgumentException("Invalid native actor item: " + invalid.getMessage(), invalid); }
    }
}
