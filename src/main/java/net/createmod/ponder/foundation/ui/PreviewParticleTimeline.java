package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.util.math.Vec3d;

/** Pure samples shared by screen markers and world billboards; never creates game particles. */
public final class PreviewParticleTimeline {
    public static final int MAX_SAMPLES_PER_EVENT = 12;
    public static final int MAX_SCENE_EVENTS = 256;
    public static final int MAX_SCENE_SAMPLES = MAX_SCENE_EVENTS * MAX_SAMPLES_PER_EVENT;

    public static final class Sample {
        public final Vec3d position;
        public final int color;
        public final float alpha;
        /** Original screen-marker radius in logical pixels, not a world-space distance. */
        public final int radius;
        private final int alphaByte;
        private Sample(Vec3d position, int color, int alphaByte, int radius) {
            this.position = position; this.color = color; this.alphaByte = alphaByte;
            alpha = alphaByte / 255.0F; this.radius = radius;
        }
        public int argb() { return alphaByte << 24 | color & 0xFFFFFF; }
    }

    private PreviewParticleTimeline() { }

    /** Rejects an over-budget scene explicitly; individual GUI events can be sampled separately. */
    public static List<Sample> evaluate(PonderScene scene, float tick, float sceneFade) {
        if (scene == null) return Collections.emptyList();
        if (scene.getParticleEvents().size() > MAX_SCENE_EVENTS)
            throw new IllegalArgumentException("Particle preview exceeds 256 events; use the screen viewer instead");
        List<Sample> result = new ArrayList<Sample>();
        for (PonderScene.ParticleEffectEvent event : scene.getParticleEvents()) result.addAll(evaluate(event, tick, sceneFade));
        return Collections.unmodifiableList(result);
    }

    public static List<Sample> evaluate(PonderScene.ParticleEffectEvent event, float tick, float sceneFade) {
        if (event == null || !Float.isFinite(tick) || !Float.isFinite(sceneFade)
            || !Float.isFinite(event.getAmountPerCycle()) || !finite(event.getLocation()) || !finite(event.getMotion()) || !finite(event.getSpread())
            || event.getCycles() < 0 || tick < event.getTick() || (double) tick > (double) event.getTick() + event.getCycles())
            return Collections.emptyList();
        float alpha = fade(event.getTick(), Math.max(1, event.getCycles()), tick) * clamp(sceneFade);
        if (alpha <= 0) return Collections.emptyList();
        int alphaByte = Math.max(0, Math.min(255, (int) (alpha * 220.0F)));
        int count = Math.max(1, Math.min(MAX_SAMPLES_PER_EVENT, Math.round(event.getAmountPerCycle())));
        int color = color(event.getParticleName());
        double spread = event.isWithinBlockSpace() ? 0.55D : 0.28D;
        float progress = Math.max(0.0F, tick - event.getTick());
        Vec3d motion = event.getMotion().scale(progress);
        List<Sample> result = new ArrayList<Sample>(count);
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(((long) event.getTick() * 37 + i * (360.0D / count)) % 360);
            double radius = spread * (0.35D + (i % 3) * 0.25D);
            Vec3d offset = new Vec3d(Math.cos(angle) * radius, ((i % 4) - 1.5D) * 0.08D,
                Math.sin(angle) * radius);
            if (event.getDistribution() != null) offset = distributedOffset(event, i);
            Vec3d position = event.getLocation().add(offset).add(motion);
            if (!finite(position)) return Collections.emptyList();
            result.add(new Sample(position, color, alphaByte, i % 3 == 0 ? 2 : 1));
        }
        return Collections.unmodifiableList(result);
    }

    private static Vec3d distributedOffset(PonderScene.ParticleEffectEvent event, int sample) {
        if ("point".equals(event.getDistribution())) return Vec3d.ZERO;
        java.util.Random random = new java.util.Random(0x504F4E444552L ^ ((long) event.getTick() << 32) ^ sample * 7919L);
        Vec3d unit;
        if ("sphere".equals(event.getDistribution())) {
            double vertical = random.nextDouble() * 2 - 1;
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = Math.cbrt(random.nextDouble());
            double horizontal = Math.sqrt(1 - vertical * vertical);
            unit = new Vec3d(horizontal * Math.cos(angle), vertical, horizontal * Math.sin(angle)).scale(radius);
        } else unit = new Vec3d(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1);
        Vec3d spread = event.getSpread();
        return new Vec3d(unit.x * spread.x, unit.y * spread.y, unit.z * spread.z);
    }

    public static int color(String particleName) {
        if (particleName == null) return 0xF2D28C;
        int separator = particleName.indexOf(':');
        if (separator >= 0 && particleName.regionMatches(true, 0, "REDSTONE", 0, "REDSTONE".length())) {
            try { return Integer.parseInt(particleName.substring(separator + 1).trim(), 16) & 0xFFFFFF; }
            catch (NumberFormatException ignored) { }
        }
        String upper = particleName.toUpperCase(Locale.ROOT);
        if (upper.contains("FLAME") || upper.contains("LAVA")) return 0xFFB347;
        if (upper.contains("PORTAL")) return 0xB084F5;
        if (upper.contains("BUBBLE")) return 0x8FD3FF;
        if (upper.contains("REDSTONE")) return 0xFF6B6B;
        return 0xF2D28C;
    }

    private static float fade(int start, int duration, float tick) {
        float in = clamp((tick - start + 1.0F) / 6.0F);
        float out = clamp(((float) start + duration - tick) / 6.0F);
        return Math.min(in, out);
    }
    private static float clamp(float value) { return Math.max(0.0F, Math.min(1.0F, value)); }
    private static boolean finite(Vec3d value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
