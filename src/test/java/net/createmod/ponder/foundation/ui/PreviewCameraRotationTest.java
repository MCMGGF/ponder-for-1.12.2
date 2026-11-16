package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSceneBuilder;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PreviewCameraRotationTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void legacyYawConstructorAndApiKeepTheirEighteenTickAnimation() {
        PonderScene.CameraEvent event = new PonderScene.CameraEvent(2, 90F);
        assertEquals(0F, event.getPitchDegrees());
        assertEquals(18, event.getDuration());
        PonderScene scene = scene();
        new PonderSceneBuilder(scene).rotateCameraY(90F);
        assertEquals(legacyAngle(90F, 0), PonderSceneRuntime.getCameraYaw(scene, 0), 0.0001F);
        assertEquals(legacyAngle(90F, 9), PonderSceneRuntime.getCameraYaw(scene, 9), 0.0001F);
        assertEquals(90F, PonderSceneRuntime.getCameraYaw(scene, 18));
    }

    @Test void pitchAndYawHaveDeterministicInstantAndAnimatedHistoryWithoutImplicitWait() {
        PonderScene scene = scene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        builder.rotateCamera(30F, 90F, false);
        assertEquals(0, scene.getTotalIdleTicks());
        assertEquals(0F, PonderSceneRuntime.getCameraPitch(scene, 0), "new targets cannot jump on their first frame");
        assertEquals(0F, PonderSceneRuntime.getCameraYaw(scene, 0));
        assertEquals(22.5F, PonderSceneRuntime.getCameraPitch(scene, 9));
        builder.idle(20);
        builder.rotateCamera(-60F, -90F, true);
        assertEquals(-30F, PonderSceneRuntime.getCameraPitch(scene, 20));
        assertEquals(0F, PonderSceneRuntime.getCameraYaw(scene, 20));
        assertEquals(22.5F, PonderSceneRuntime.getCameraPitch(scene, 9));
        assertEquals(67.5F, PonderSceneRuntime.getCameraYaw(scene, 9));
        assertEquals(0, scene.getCameraEvents().get(1).getDuration());
        assertEquals(20, new Viewer(scene).getSceneEndTick(scene));
    }

    @Test void instantInstructionCancelsAnUnfinishedTargetChaseWhileLegacyDeltasRemainAdditive() {
        PonderScene scene = scene();
        scene.recordCameraRotation(30F, 60F, false);
        scene.addIdleTicks(7);
        scene.recordCameraRotation(-30F, -60F, true);
        assertEquals(0F, PonderSceneRuntime.getCameraPitch(scene, 7));
        assertEquals(0F, PonderSceneRuntime.getCameraYaw(scene, 7));
        assertEquals(0F, PonderSceneRuntime.getCameraPitch(scene, 15));
        PonderScene legacy = scene();
        legacy.recordCameraYaw(90F);
        legacy.addIdleTicks(9);
        legacy.recordCameraYaw(90F);
        assertEquals(legacyAngle(90F, 9) + legacyAngle(90F, 0), PonderSceneRuntime.getCameraYaw(legacy, 9), 0.0001F);
        assertEquals(90F + legacyAngle(90F, 9), PonderSceneRuntime.getCameraYaw(legacy, 18), 0.0001F);
    }

    @Test void omittedInstantPitchPreservesItsChaseWhereasExplicitZeroSnapsToItsTarget() {
        PonderScene omitted = scene();
        omitted.recordCameraRotation(30F, 0F, false);
        omitted.addIdleTicks(7);
        omitted.recordCameraRotation((Float) null, 0F, true);
        assertFalse(omitted.getCameraEvents().get(1).affectsPitch());
        assertEquals(22.5F, PonderSceneRuntime.getCameraPitch(omitted, 9));
        PonderScene explicit = scene();
        explicit.recordCameraRotation(30F, 0F, false);
        explicit.addIdleTicks(7);
        explicit.recordCameraRotation(0F, 0F, true);
        assertTrue(explicit.getCameraEvents().get(1).affectsPitch());
        assertEquals(30F, PonderSceneRuntime.getCameraPitch(explicit, 7));
        assertEquals(30F, PonderSceneRuntime.getCameraPitch(explicit, 9));
    }

    @Test void invalidAndCumulativeAnglesFailBeforeAppendingAnEvent() {
        for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, 1000001F, -1000001F}) {
            PonderScene scene = scene();
            assertThrows(IllegalArgumentException.class, () -> scene.recordCameraRotation(invalid, 0, true));
            assertTrue(scene.getCameraEvents().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> scene.recordCameraRotation(0, invalid, true));
            assertTrue(scene.getCameraEvents().isEmpty());
        }
        PonderScene scene = scene();
        scene.recordCameraRotation(1000000F, 1000000F, true);
        assertThrows(IllegalArgumentException.class, () -> scene.recordCameraRotation(1F, 0F, true));
        assertThrows(IllegalArgumentException.class, () -> scene.recordCameraRotation(0F, 1F, true));
        assertEquals(1, scene.getCameraEvents().size());
        scene.recordCameraRotation(-1000000F, -1000000F, true);
        assertEquals(0F, PonderSceneRuntime.getCameraPitch(scene, 0));
        assertEquals(0F, PonderSceneRuntime.getCameraYaw(scene, 0));
    }

    @Test void manualTakeoverDoesNotSnapAnAuthoredPitchBackToTheOldSeventyFiveDegreeLimit() throws Exception {
        PonderScene scene = scene();
        scene.recordCameraRotation(120F, 0F, true);
        scene.addIdleTicks(10);
        scene.recordCameraRotation(-120F, 0F, true);
        Viewer screen = new Viewer(scene);
        java.lang.reflect.Method pitch = PonderDebugScreen.class.getDeclaredMethod("getPreviewPitch", PonderScene.class, float.class);
        pitch.setAccessible(true);
        assertEquals(146F, ((Number) pitch.invoke(screen, scene, 0F)).floatValue());
        java.lang.reflect.Method take = PonderDebugScreen.class.getDeclaredMethod("takeManualCamera");
        take.setAccessible(true);
        take.invoke(screen);
        assertEquals(146F, ((Number) pitch.invoke(screen, scene, 10F)).floatValue());
        screen.resetPreviewCamera();
        assertEquals(26F, ((Number) pitch.invoke(screen, scene, 10F)).floatValue());
    }

    @Test void flatModelsFitAtNinetyDegreesWithoutChangingScaleDuringRotation() {
        float scale = PreviewCameraMath.fitScale(100, 1, 100, 320, 240, true, 1F);
        double widestDepth = Math.hypot(100D, 100D);
        for (int degrees = -180; degrees <= 180; degrees++) {
            double pitch = Math.toRadians(degrees);
            double projected = Math.abs(Math.cos(pitch)) + widestDepth * Math.abs(Math.sin(pitch));
            assertTrue(projected * scale <= 228.001D);
            assertEquals(scale, PreviewCameraMath.fitScale(100, 1, 100, 320, 240, true, 1F));
        }
    }

    private static PonderScene scene() {
        return new PonderScene(new PonderStoryBoardEntry((s, u) -> { }, "rotation_test",
            new ResourceLocation("rotation_test", "main"), new ResourceLocation("minecraft", "stone")), new PonderLocalization());
    }

    private static float legacyAngle(float degrees, int elapsed) {
        float progress = Math.min(1F, (elapsed + 1F) / 18F);
        return degrees * (1F - (1F - progress) * (1F - progress));
    }

    private static final class Viewer extends PonderDebugScreen {
        private final PonderScene scene;
        Viewer(PonderScene scene) { super(null, 0, true, null); this.scene = scene; }
        @Override protected PonderScene getSelectedScene() { return scene; }
    }
}
