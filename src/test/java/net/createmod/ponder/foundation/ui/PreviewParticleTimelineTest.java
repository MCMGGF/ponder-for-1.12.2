package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Locale;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.ParticleEffectEvent;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

class PreviewParticleTimelineTest {
    private ParticleEffectEvent event(float amount, String name, Vec3d motion, boolean within) {
        return new ParticleEffectEvent(10, new Vec3d(2, 3, 4), amount, 20, name, motion, within);
    }
    @Test void eventWindowAndSixTickFadeKeepTheExistingGuiAlphaBytes() {
        ParticleEffectEvent event = event(3, "FLAME", Vec3d.ZERO, false);
        assertTrue(PreviewParticleTimeline.evaluate(event, 9.99F, 1).isEmpty());
        assertEquals(36, PreviewParticleTimeline.evaluate(event, 10, 1).get(0).argb() >>> 24);
        assertEquals(220, PreviewParticleTimeline.evaluate(event, 15, 1).get(0).argb() >>> 24);
        assertEquals(36, PreviewParticleTimeline.evaluate(event, 29, 1).get(0).argb() >>> 24);
        assertEquals(18, PreviewParticleTimeline.evaluate(event, 29.5F, 1).get(0).argb() >>> 24);
        assertTrue(PreviewParticleTimeline.evaluate(event, 30, 1).isEmpty());
        assertTrue(PreviewParticleTimeline.evaluate(event, 31, 1).isEmpty());
        assertEquals(110, PreviewParticleTimeline.evaluate(event, 15, 0.5F).get(0).argb() >>> 24);
    }
    @Test void deterministicMotionRewindsWithoutMutableStateOrNativeParticles() {
        ParticleEffectEvent event = event(5, "PORTAL", new Vec3d(0.1, 0.2, -0.3), true);
        List<PreviewParticleTimeline.Sample> before = PreviewParticleTimeline.evaluate(event, 15, 1);
        List<PreviewParticleTimeline.Sample> after = PreviewParticleTimeline.evaluate(event, 18, 1);
        List<PreviewParticleTimeline.Sample> replay = PreviewParticleTimeline.evaluate(event, 15, 1);
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i).position, replay.get(i).position);
            assertEquals(0.3, after.get(i).position.x - before.get(i).position.x, 1E-7);
            assertEquals(0.6, after.get(i).position.y - before.get(i).position.y, 1E-7);
            assertEquals(-0.9, after.get(i).position.z - before.get(i).position.z, 1E-7);
            assertEquals(i % 3 == 0 ? 2 : 1, before.get(i).radius);
        }
        assertThrows(UnsupportedOperationException.class, () -> before.clear());
    }
    @Test void countSpreadAndAlphaMatchTheExistingGuiMarkerRules() {
        assertEquals(1, PreviewParticleTimeline.evaluate(event(-5, null, Vec3d.ZERO, false), 15, 1).size());
        assertEquals(3, PreviewParticleTimeline.evaluate(event(2.6F, null, Vec3d.ZERO, false), 15, 1).size());
        assertEquals(12, PreviewParticleTimeline.evaluate(event(1000, null, Vec3d.ZERO, false), 15, 1).size());
        Vec3d point = PreviewParticleTimeline.evaluate(event(1, null, Vec3d.ZERO, false), 15, 1).get(0).position;
        double angle = Math.toRadians((10 * 37) % 360);
        assertEquals(2 + Math.cos(angle) * 0.28 * 0.35, point.x, 1E-12);
        assertEquals(3 - 1.5 * 0.08, point.y, 1E-12);
        assertEquals(4 + Math.sin(angle) * 0.28 * 0.35, point.z, 1E-12);
        PreviewParticleTimeline.Sample larger = PreviewParticleTimeline.evaluate(event(1, null, Vec3d.ZERO, true), 15, 1).get(0);
        assertEquals((point.x - 2) * 0.55 / 0.28, larger.position.x - 2, 1E-12);
        assertEquals(220 / 255.0F, larger.alpha, 1E-7);
        assertEquals(0xDCF2D28C, larger.argb());
    }
    @Test void namedAndCustomRedstoneColorsPreserveTheExistingPalette() {
        assertEquals(0xF2D28C, PreviewParticleTimeline.color(null));
        assertEquals(0xFFB347, PreviewParticleTimeline.color("FLAME"));
        assertEquals(0xFFB347, PreviewParticleTimeline.color("drip_lava"));
        assertEquals(0xB084F5, PreviewParticleTimeline.color("portal"));
        assertEquals(0x8FD3FF, PreviewParticleTimeline.color("water_bubble"));
        assertEquals(0x123ABC, PreviewParticleTimeline.color("redstone:123abc"));
        assertEquals(0xFF6B6B, PreviewParticleTimeline.color("REDSTONE:bad hex"));
        Locale old = Locale.getDefault();
        try { Locale.setDefault(new Locale("tr", "TR")); assertEquals(0xFFB347, PreviewParticleTimeline.color("flame")); }
        finally { Locale.setDefault(old); }
    }
    @Test void invalidInputsProduceNoPartialSampleSet() {
        assertTrue(PreviewParticleTimeline.evaluate(event(1, null, Vec3d.ZERO, false), Float.NaN, 1).isEmpty());
        assertTrue(PreviewParticleTimeline.evaluate(event(Float.NaN, null, Vec3d.ZERO, false), 15, 1).isEmpty());
        assertTrue(PreviewParticleTimeline.evaluate(event(1, null, new Vec3d(Double.POSITIVE_INFINITY, 0, 0), false), 15, 1).isEmpty());
        assertTrue(PreviewParticleTimeline.evaluate(event(1, null, Vec3d.ZERO, false), 15, Float.NaN).isEmpty());
        assertTrue(PreviewParticleTimeline.evaluate(event(1, null, Vec3d.ZERO, false), 15, 0).isEmpty());
    }
    @Test void sceneBudgetIsExplicitAndNeverSilentlyDropsValidEvents() {
        ResourceLocation id = new ResourceLocation("test", "particles");
        PonderScene scene = new PonderScene(new PonderStoryBoardEntry(null, "test", id, id), new PonderLocalization());
        for (int i = 0; i < 256; i++) scene.recordParticleEffect(Vec3d.ZERO, 12, 20, "FLAME", Vec3d.ZERO, false);
        List<PreviewParticleTimeline.Sample> samples = PreviewParticleTimeline.evaluate(scene, 6, 1);
        assertEquals(3072, samples.size()); assertThrows(UnsupportedOperationException.class, () -> samples.clear());
        scene.recordParticleEffect(Vec3d.ZERO, 1, 20, "FLAME", Vec3d.ZERO, false);
        assertThrows(IllegalArgumentException.class, () -> PreviewParticleTimeline.evaluate(scene, 6, 1));
    }
}
