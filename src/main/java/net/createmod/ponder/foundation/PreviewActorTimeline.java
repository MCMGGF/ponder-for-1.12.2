package net.createmod.ponder.foundation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createmod.ponder.foundation.PonderScene.ActorEvent;
import net.createmod.ponder.foundation.PonderScene.ActorEventType;
import net.createmod.ponder.foundation.PonderScene.ActorKind;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Pure preview state shared by target selection and rendering; no Entity or World is created. */
public final class PreviewActorTimeline {
    private PreviewActorTimeline() { }

    public static final class State {
        public final int actorId;
        public final ActorKind kind;
        public final Vec3d position, rotation;
        public final float yaw, fade;
        public final boolean alive, visible;
        public final String poseName, displayName;
        private final ItemStack item;
        private final NBTTagCompound nbt;
        private State(Mutable actor) {
            actorId = actor.id; kind = actor.kind; position = actor.position; rotation = actor.rotation;
            yaw = actor.yaw; fade = actor.fade; alive = actor.fade > 0;
            visible = alive && !actor.nbt.getBoolean("Invisible");
            poseName = actor.pose; displayName = actor.name; item = actor.item.copy(); nbt = actor.nbt.copy();
        }
        public ItemStack getItemStack() { return item.copy(); }
        public NBTTagCompound getNbt() { return nbt.copy(); }
    }

    public static List<State> evaluate(List<ActorEvent> events, float tick) {
        Map<Integer, Mutable> actors = new LinkedHashMap<Integer, Mutable>();
        for (ActorEvent event : events) {
            if (event.getTick() > tick) continue;
            Integer id = Integer.valueOf(event.getActorId());
            if (event.getType() == ActorEventType.SPAWN) {
                actors.put(id, new Mutable(event, tick)); continue;
            }
            Mutable actor = actors.get(id);
            if (actor == null || actor.kind != event.getActorKind()) continue;
            switch (event.getType()) {
                case MOVE:
                    if (event.getOffset() != null) actor.position = actor.position.add(event.getOffset().scale(movementProgress(event, tick)));
                    break;
                case ROTATE:
                    if (event.getRotation() != null) {
                        actor.rotation = actor.rotation.add(event.getRotation().scale(progress(event, tick)));
                        if (actor.kind == ActorKind.CART) actor.yaw += event.getRotation().y * progress(event, tick);
                    }
                    else actor.yaw += event.getAngle() * progress(event, tick);
                    break;
                case HIDE: actor.fade = 1 - progress(event, tick); break;
                case POSE: actor.pose = event.getPoseName(); break;
                case NBT_PATCH:
                    NBTTagCompound patch = event.getActorNbt(); actor.nbt.merge(patch);
                    if (patch.hasKey("Pos", 9)) actor.position = vector(patch.getTagList("Pos", 6));
                    if (patch.hasKey("Rotation", 9)) {
                        NBTTagList rotation = patch.getTagList("Rotation", 5);
                        actor.yaw = rotation.getFloatAt(0);
                        actor.rotation = new Vec3d(rotation.getFloatAt(1), rotation.getFloatAt(0), 0);
                    }
                    if (patch.hasKey("CustomName", 8)) actor.name = patch.getString("CustomName");
                    if (patch.hasKey("Item", 10)) actor.item = event.getItemStack().copy();
                    break;
                default: break;
            }
        }
        List<State> result = new ArrayList<State>();
        for (Mutable actor : actors.values()) result.add(new State(actor));
        return result;
    }

    public static float progress(ActorEvent event, float tick) {
        return MathHelper.clamp((tick - event.getTick() + 1) / Math.max(1, event.getDuration()), 0, 1);
    }

    public static float movementProgress(ActorEvent event, float tick) {
        if (!event.isEntranceMotion()) return progress(event, tick);
        float phase = MathHelper.clamp((tick - event.getTick()) / Math.max(1, event.getDuration()), 0F, 1F);
        float remaining = 1F - phase;
        return 1F - remaining * remaining;
    }

    public static Vec3d vector(NBTTagList values) {
        return new Vec3d(values.getDoubleAt(0), values.getDoubleAt(1), values.getDoubleAt(2));
    }

    private static final class Mutable {
        final int id; final ActorKind kind;
        Vec3d position, rotation = Vec3d.ZERO;
        float yaw, fade;
        String pose, name;
        ItemStack item;
        final NBTTagCompound nbt = new NBTTagCompound();
        Mutable(ActorEvent spawn, float tick) {
            id = spawn.getActorId(); kind = spawn.getActorKind();
            position = spawn.getLocation() == null ? Vec3d.ZERO : spawn.getLocation();
            yaw = spawn.getAngle(); fade = progress(spawn, tick); pose = spawn.getPoseName();
            name = spawn.getDisplayName(); item = spawn.getItemStack().copy();
        }
    }
}
