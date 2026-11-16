package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.createmod.ponder.foundation.PonderScene.ActorEvent;
import net.createmod.ponder.foundation.PonderScene.ActorEventType;
import net.createmod.ponder.foundation.PonderScene.ActorKind;
import net.createmod.ponder.foundation.PreviewActorTimeline;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Evaluates directly from scene time, so seeking never replays entity side effects. */
final class PreviewPlayerTimeline {
    private PreviewPlayerTimeline() {
    }

    static List<State> evaluate(List<ActorEvent> events, float tick) {
        Map<Integer, Track> tracks = new LinkedHashMap<Integer, Track>();
        for (ActorEvent event : events) {
            if (event.getActorKind() != ActorKind.PLAYER || event.getTick() > tick) {
                continue;
            }
            Integer id = Integer.valueOf(event.getActorId());
            Track track = tracks.get(id);
            if (event.getType() == ActorEventType.SPAWN) {
                track = new Track(event);
                tracks.put(id, track);
            } else if (track != null) {
                track.events.add(event);
            }
        }
        List<State> result = new ArrayList<State>();
        for (Track track : tracks.values()) {
            result.add(track.evaluate(tick));
        }
        return result;
    }

    static float progress(ActorEvent event, float tick) {
        return MathHelper.clamp((tick - event.getTick() + 1.0F) / Math.max(1, event.getDuration()), 0.0F, 1.0F);
    }

    static final class State {
        final int actorId;
        final Vec3d position;
        final float yaw;
        final float pitch;
        final float fade;
        final boolean sneaking;
        final ItemStack heldItem;
        final float swingProgress;
        final float limbSwing;
        final float limbSwingAmount;

        private State(int actorId, Vec3d position, float yaw, float pitch, float fade, boolean sneaking,
            ItemStack heldItem, float swingProgress, float limbSwing, float limbSwingAmount) {
            this.actorId = actorId;
            this.position = position;
            this.yaw = yaw;
            this.pitch = pitch;
            this.fade = fade;
            this.sneaking = sneaking;
            this.heldItem = heldItem.copy();
            this.swingProgress = swingProgress;
            this.limbSwing = limbSwing;
            this.limbSwingAmount = limbSwingAmount;
        }
    }

    private static final class Track {
        final ActorEvent spawn;
        final List<ActorEvent> events = new ArrayList<ActorEvent>();

        Track(ActorEvent spawn) {
            this.spawn = spawn;
        }

        Vec3d positionAt(float tick) {
            Vec3d position = spawn.getLocation() == null ? Vec3d.ZERO : spawn.getLocation();
            for (ActorEvent event : events) {
                if (event.getType() == ActorEventType.MOVE && event.getOffset() != null && event.getTick() <= tick) {
                    position = position.add(event.getOffset().scale(PreviewActorTimeline.movementProgress(event, tick)));
                } else if (event.getType() == ActorEventType.NBT_PATCH && event.getTick() <= tick) {
                    NBTTagCompound patch = event.getActorNbt();
                    if (patch.hasKey("Pos", 9)) {
                        NBTTagList values = patch.getTagList("Pos", 6);
                        position = new Vec3d(values.getDoubleAt(0), values.getDoubleAt(1), values.getDoubleAt(2));
                    }
                }
            }
            return position;
        }

        State evaluate(float tick) {
            float fade = progress(spawn, tick);
            boolean sneak = false;
            boolean invisible = false;
            ItemStack held = ItemStack.EMPTY;
            float swing = 0.0F;
            float distance = 0.0F;
            Vec3d velocity = Vec3d.ZERO;
            Look look = new Look(spawn.getAngle(), 0.0F);
            for (ActorEvent event : events) {
                switch (event.getType()) {
                    case HIDE:
                        fade = 1.0F - progress(event, tick);
                        break;
                    case HELD_ITEM:
                        held = event.getItemStack();
                        break;
                    case SNEAK:
                        sneak = event.getAngle() != 0.0F;
                        break;
                    case SWING:
                        float phase = (tick - event.getTick()) / Math.max(1, event.getDuration());
                        swing = phase >= 0.0F && phase < 1.0F ? phase : 0.0F;
                        break;
                    case MOVE:
                        if (event.getOffset() != null && event.hasWalkAnimation()) {
                            distance += length(event.getOffset()) * progress(event, tick);
                            if (progress(event, tick) < 1.0F) {
                                velocity = velocity.add(event.getOffset().scale(1.0D / Math.max(1, event.getDuration())));
                            }
                        }
                        break;
                    case ROTATE:
                        if (event.getRotation() != null) look = new Look(event, look.yaw(event.getTick()),
                            look.pitch(event.getTick()), look.yaw(event.getTick()) + (float)event.getRotation().y,
                            look.pitch(event.getTick()) + (float)event.getRotation().x);
                        break;
                    case NBT_PATCH:
                        NBTTagCompound patch = event.getActorNbt();
                        if (patch.hasKey("Invisible")) invisible = patch.getBoolean("Invisible");
                        if (patch.hasKey("Rotation", 9)) {
                            NBTTagList values = patch.getTagList("Rotation", 5);
                            look = new Look(values.getFloatAt(0), values.getFloatAt(1));
                        }
                        break;
                    case LOOK_AT:
                        if (event.getLocation() != null) {
                            Vec3d eye = positionAt(event.getTick()).add(0.0D, sneak ? 1.54D : 1.62D, 0.0D);
                            Vec3d direction = event.getLocation().subtract(eye);
                            double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
                            float yaw = horizontal < 0.0001D ? look.yaw(event.getTick())
                                : (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
                            float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, horizontal));
                            look = new Look(event, look.yaw(event.getTick()), look.pitch(event.getTick()), yaw,
                                MathHelper.clamp(pitch, -80.0F, 80.0F));
                        }
                        break;
                    default:
                        break;
                }
            }
            return new State(spawn.getActorId(), positionAt(tick), look.yaw(tick), look.pitch(tick), invisible ? 0 : fade, sneak,
                held, swing, distance * 4.0F,
                MathHelper.clamp((float) length(velocity) * 8.0F, 0.0F, 1.0F));
        }
    }

    private static double length(Vec3d vector) {
        return Math.sqrt(vector.x * vector.x + vector.y * vector.y + vector.z * vector.z);
    }

    private static final class Look {
        final ActorEvent event;
        final float fromYaw;
        final float fromPitch;
        final float toYaw;
        final float toPitch;

        Look(float yaw, float pitch) {
            this(null, yaw, pitch, yaw, pitch);
        }

        Look(ActorEvent event, float fromYaw, float fromPitch, float toYaw, float toPitch) {
            this.event = event;
            this.fromYaw = fromYaw;
            this.fromPitch = fromPitch;
            this.toYaw = fromYaw + MathHelper.wrapDegrees(toYaw - fromYaw);
            this.toPitch = toPitch;
        }

        float yaw(float tick) {
            return fromYaw + (toYaw - fromYaw) * (event == null ? 1.0F : progress(event, tick));
        }

        float pitch(float tick) {
            return fromPitch + (toPitch - fromPitch) * (event == null ? 1.0F : progress(event, tick));
        }
    }
}
