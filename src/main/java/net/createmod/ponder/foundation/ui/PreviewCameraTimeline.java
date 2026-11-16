package net.createmod.ponder.foundation.ui;

import java.util.List;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.util.math.Vec3d;

/** Pure seekable view evaluation. Defaults and window geometry never depend on captions. */
final class PreviewCameraTimeline {
    private PreviewCameraTimeline() { }

    static final class View {
        final Vec3d center;
        final float multiplier;
        View(Vec3d center, float multiplier) { this.center = center; this.multiplier = multiplier; }
    }

    static View evaluate(List<PonderScene.CameraViewEvent> events, float tick, Vec3d defaultCenter) {
        View start = new View(defaultCenter, 1F);
        if (!Float.isFinite(tick)) return start;
        PonderScene.CameraViewEvent active = null;
        for (PonderScene.CameraViewEvent event : events) {
            if (event.getTick() > tick) break;
            if (active != null) start = interpolate(start, active, event.getTick(), defaultCenter);
            active = event;
        }
        return active == null ? start : interpolate(start, active, tick, defaultCenter);
    }

    private static View interpolate(View start, PonderScene.CameraViewEvent event, float tick, Vec3d defaultCenter) {
        int moveTicks = event.getDuration() / 2;
        int zoomTicks = event.getDuration() - moveTicks;
        double elapsed = tick - event.getTick();
        double move = ease(elapsed, moveTicks);
        double zoom = ease(elapsed - moveTicks, zoomTicks);
        Vec3d target = event.getCenter() == null ? defaultCenter : event.getCenter();
        Vec3d center = move == 1D ? target : start.center.add(target.subtract(start.center).scale(move));
        float scale = zoom == 1D ? event.getMultiplier()
            : (float) (start.multiplier + (event.getMultiplier() - start.multiplier) * zoom);
        return new View(center, scale);
    }

    private static double ease(double elapsed, int duration) {
        if (duration <= 0 || elapsed >= duration) return 1D;
        if (elapsed <= 0D) return 0D;
        return (1D - Math.pow(0.9D, elapsed)) / (1D - Math.pow(0.9D, duration));
    }
}
