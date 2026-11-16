package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import net.createmod.ponder.foundation.PonderScene.ActorEvent;
import net.createmod.ponder.foundation.PonderScene.ActorEventType;
import net.createmod.ponder.foundation.PonderScene.ActorKind;
import net.createmod.ponder.foundation.PreviewActorTimeline;
import net.minecraft.util.math.Vec3d;

final class PreviewActorBounds {

    private static final double ACTOR_RADIUS = 0.55D;

    private PreviewActorBounds() {
    }

    static PreviewMotionBounds.Bounds compute(List<ActorEvent> events) {
        Map<Integer, Lifetime> actors = new LinkedHashMap<Integer, Lifetime>();
        PreviewMotionBounds.Bounds result = null;
        for (ActorEvent event : events) {
            Integer id = Integer.valueOf(event.getActorId());
            Lifetime lifetime = actors.get(id);
            if (event.getType() == ActorEventType.SPAWN) {
                if (lifetime != null) {
                    result = include(result, lifetime.compute((float) event.getTick(), false));
                }
                actors.put(id, new Lifetime(event));
            } else if (lifetime != null && (event.getType() == ActorEventType.HIDE
                || event.getType() == ActorEventType.NBT_PATCH
                || (event.getType() == ActorEventType.MOVE && event.getOffset() != null))) {
                lifetime.events.add(event);
            }
        }
        for (Lifetime lifetime : actors.values()) {
            result = include(result, lifetime.compute(Double.POSITIVE_INFINITY, true));
        }
        return result;
    }

    private static PreviewMotionBounds.Bounds include(PreviewMotionBounds.Bounds target,
        PreviewMotionBounds.Bounds addition) {
        if (addition == null) {
            return target;
        }
        if (target == null) {
            return addition;
        }
        target.include(addition);
        return target;
    }

    private static final class Lifetime {
        private final ActorEvent spawn;
        private final List<ActorEvent> events = new ArrayList<ActorEvent>();

        private Lifetime(ActorEvent spawn) {
            this.spawn = spawn;
            events.add(spawn);
        }

        private PreviewMotionBounds.Bounds compute(double stop, boolean includeFinalEndpoint) {
            double first = (float) spawn.getTick();
            if (stop <= first) {
                return null;
            }
            TreeSet<Double> boundaries = new TreeSet<Double>();
            boundaries.add(Double.valueOf(first));
            for (ActorEvent event : events) {
                double start = (float) event.getTick();
                double end = start + (double) duration(event) - (event.isEntranceMotion() ? 0D : 1D);
                if (start >= first && start < stop) {
                    boundaries.add(Double.valueOf(start));
                }
                if (end >= first && end < stop) {
                    boundaries.add(Double.valueOf(end));
                }
            }
            if (Double.isFinite(stop)) {
                boundaries.add(Double.valueOf(stop));
            }
            List<Double> times = new ArrayList<Double>(boundaries);
            PreviewMotionBounds.Bounds result = null;
            for (int index = 0; index < times.size(); index++) {
                double from = times.get(index).doubleValue();
                boolean finalEndpoint = index + 1 == times.size();
                if (finalEndpoint && !includeFinalEndpoint) {
                    break;
                }
                double to = finalEndpoint ? from : times.get(index + 1).doubleValue();
                result = include(result, interval(from, to));
            }
            return result;
        }

        private PreviewMotionBounds.Bounds interval(double from, double to) {
            ActorEvent visibility = spawn;
            for (ActorEvent event : events) {
                if ((float) event.getTick() > from) {
                    break;
                }
                if (event.getType() == ActorEventType.HIDE) {
                    visibility = event;
                }
            }
            if (visibility.getType() == ActorEventType.HIDE && runtimeProgress(visibility, (float) from) >= 1.0F) {
                return null;
            }

            Vec3d origin = spawn.getLocation() == null ? Vec3d.ZERO : spawn.getLocation();
            double[] a = { origin.x, origin.y, origin.z };
            double[] b = { origin.x, origin.y, origin.z };
            double[] error = new double[3];
            for (ActorEvent event : events) {
                if ((float) event.getTick() > from) {
                    break;
                }
                if (event.getType() == ActorEventType.NBT_PATCH && event.getActorNbt().hasKey("Pos", 9)) {
                    Vec3d position = PreviewActorTimeline.vector(event.getActorNbt().getTagList("Pos", 6));
                    a[0] = b[0] = position.x; a[1] = b[1] = position.y; a[2] = b[2] = position.z;
                    error[0] = error[1] = error[2] = 0;
                    continue;
                }
                if (event.getType() != ActorEventType.MOVE || event.getOffset() == null) {
                    continue;
                }
                double startProgress = linearProgress(event, from);
                double endProgress = linearProgress(event, to);
                double progressError = progressError(event, from, to);
                Vec3d offset = event.getOffset();
                double[] movement = { offset.x, offset.y, offset.z };
                for (int axis = 0; axis < 3; axis++) {
                    double magnitude = Math.max(Math.abs(a[axis]), Math.abs(b[axis]))
                        + error[axis] + Math.abs(movement[axis]);
                    error[axis] += Math.abs(movement[axis]) * progressError + Math.ulp(magnitude) * 2.0D;
                    a[axis] += movement[axis] * startProgress;
                    b[axis] += movement[axis] * endProgress;
                }
            }
            // Ordinary moves are affine; entrance curves add a bounded quadratic chord error.
            // Shared envelope for both arm types, swinging tools, sneaking and every heading.
            boolean player = spawn.getActorKind() == ActorKind.PLAYER;
            boolean cart = spawn.getActorKind() == ActorKind.CART;
            double radius = player ? 1.25D : cart ? 0.9D : ACTOR_RADIUS;
            double below = player ? 0.15D : cart ? 0.55D : ACTOR_RADIUS;
            double above = player ? 2.20D : cart ? 1.05D : ACTOR_RADIUS;
            return new PreviewMotionBounds.Bounds(
                Math.min(a[0], b[0]) - error[0] - radius,
                Math.min(a[1], b[1]) - error[1] - below,
                Math.min(a[2], b[2]) - error[2] - radius,
                Math.max(a[0], b[0]) + error[0] + radius,
                Math.max(a[1], b[1]) + error[1] + above,
                Math.max(a[2], b[2]) + error[2] + radius);
        }
    }

    private static float duration(ActorEvent event) {
        return Math.max(1, event.getDuration());
    }

    private static float runtimeProgress(ActorEvent event, float tick) {
        return Math.max(0.0F, Math.min(1.0F, (tick - event.getTick() + 1.0F) / duration(event)));
    }

    private static double linearProgress(ActorEvent event, double tick) {
        if (event.isEntranceMotion()) {
            double phase = Math.max(0D, Math.min(1D, (tick - event.getTick()) / duration(event)));
            return 1D - (1D - phase) * (1D - phase);
        }
        return Math.max(0.0D, Math.min(1.0D,
            (tick - (double) (float) event.getTick() + 1.0D) / duration(event)));
    }

    private static double progressError(ActorEvent event, double from, double to) {
        if (linearProgress(event, from) == 1.0D && runtimeProgress(event, (float) from) == 1.0F) {
            return 0.0D;
        }
        float elapsed = (float) (to - (double) (float) event.getTick());
        // The renderer computes subtraction, addition, and division as floats.
        // Enclose their rounding without turning numerical error into padding
        // on unrelated static block geometry.
        double numerical = (Math.ulp(elapsed) * 0.5D + Math.ulp(elapsed + 1.0F) * 0.5D) / duration(event)
            + Math.ulp(1.0F) * 0.5D;
        if (!event.isEntranceMotion()) return numerical;
        double span = Math.max(0D, Math.min(1D, (to - from) / duration(event)));
        return numerical * 4D + span * span / 4D;
    }
}
