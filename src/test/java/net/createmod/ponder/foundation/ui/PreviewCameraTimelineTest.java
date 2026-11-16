package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Collections;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSceneBuilder;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PreviewCameraTimelineTest {
    private static final Vec3d DEFAULT = new Vec3d(2.5, 1.5, 2.5);
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void movesBeforeZoomingAndRestoresTheDefaultWithoutAccumulatingScale() {
        PonderScene scene = scene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        builder.zoomScene(new Vec3d(6, 2, 3.25), 2F, 20);
        builder.zoomScene(new Vec3d(9, 2, 4), 3F, 20);
        builder.zoomScene(null, null, 20);
        assertView(scene, 0, DEFAULT, 1F);
        assertView(scene, 10, new Vec3d(6.5, 2, 3.25), 1F);
        assertView(scene, 20, new Vec3d(6.5, 2, 3.25), 2F);
        assertView(scene, 40, new Vec3d(9.5, 2, 4.5), 3F);
        assertView(scene, 60, DEFAULT, 1F);
        assertEquals(60, scene.getTotalIdleTicks());
        assertEquals(1F, scene.getScaleFactor(), "authored view does not mutate static fit hints");
    }

    @Test void reverseAndFractionalSeeksAreDeterministicIncludingOverlappingEvents() {
        java.util.List<PonderScene.CameraViewEvent> events = Arrays.asList(
            new PonderScene.CameraViewEvent(5, 21, new Vec3d(6, 2, 3), 2F, 0),
            new PonderScene.CameraViewEvent(15, 7, null, 0.5F, 0));
        PreviewCameraTimeline.View expected = PreviewCameraTimeline.evaluate(events, 17.5F, DEFAULT);
        for (float tick : new float[] {100, 0, 21, 5, 17.5F, 9, 17.5F}) {
            PreviewCameraTimeline.View view = PreviewCameraTimeline.evaluate(events, tick, DEFAULT);
            assertTrue(Float.isFinite(view.multiplier));
        }
        PreviewCameraTimeline.View actual = PreviewCameraTimeline.evaluate(events, 17.5F, DEFAULT);
        assertEquals(expected.center, actual.center);
        assertEquals(expected.multiplier, actual.multiplier);
        assertEquals(0.5F, PreviewCameraTimeline.evaluate(events, 22F, DEFAULT).multiplier);
        assertEquals(DEFAULT, PreviewCameraTimeline.evaluate(events, 22F, DEFAULT).center);
    }

    @Test void zeroAndOneTickEventsReachExactTargetsWithoutDivisionByZero() {
        for (int duration : new int[] {0, 1, 3, 72000}) {
            PonderScene.CameraViewEvent event = new PonderScene.CameraViewEvent(4, duration, Vec3d.ZERO, 64F, 0);
            PreviewCameraTimeline.View end = PreviewCameraTimeline.evaluate(Collections.singletonList(event), 4 + duration, DEFAULT);
            assertEquals(Vec3d.ZERO, end.center);
            assertEquals(64F, end.multiplier);
            assertEquals(1F, PreviewCameraTimeline.evaluate(Collections.singletonList(event), 3F, DEFAULT).multiplier);
        }
    }

    @Test void zoomClearsOnlyPrecedingTextAndOutlinesEvenAtTheSameTickAndIsSeekable() {
        PonderScene scene = scene();
        PonderScene.OverlayEvent old = scene.createOverlayEvent(100).text("old");
        scene.addIdleTicks(5);
        PonderScene.OverlayEvent justBefore = scene.createOverlayEvent(100).text("before");
        scene.recordCameraView(null, null, 20);
        PonderScene.OverlayEvent after = scene.createOverlayEvent(100).text("after");
        assertFalse(scene.isOverlayCleared(old, 4));
        assertTrue(scene.isOverlayCleared(old, 5));
        assertTrue(scene.isOverlayCleared(justBefore, 5));
        assertFalse(scene.isOverlayCleared(after, 5));
        assertEquals("after", PreviewCalloutContent.collect(scene, 5, key -> key).get(0).text());
        assertEquals(1, PreviewCalloutContent.collect(scene, 4, key -> key).size());
        assertFalse(scene.isOverlayCleared(old, 4));
    }

    @Test void inclusiveBoundsAreFixedFromTheBeginningAndDoNotInventAirGeometry() {
        PonderScene scene = scene();
        scene.addIdleTicks(100);
        scene.encapsulateBounds(new BlockPos(10, 8, 6));
        scene.encapsulateBounds(new BlockPos(3, 12, 4));
        PonderScenePreview.PreviewBounds bounds = PonderSceneRuntime.computeBounds(scene);
        assertEquals(0, bounds.minX);
        assertEquals(10, bounds.maxX);
        assertEquals(12, bounds.maxY);
        assertEquals(6, bounds.maxZ);
        assertTrue(scene.getWorldEvents().isEmpty());
        assertTrue(scene.getSchematic().isEmpty());
    }

    @Test void compactDockAndCameraRemainIdenticalAcrossContentAndPageChanges() {
        PreviewExplanationLayout layout = PreviewExplanationLayout.showcase(320, 240);
        assertTrue(layout.model.right() < layout.explanation.x);
        PreviewModelFraming framing = new PreviewModelFraming();
        framing.initialize(0, 0, 0, 8, 5, 8);
        float scale = framing.fitScale(layout.model.width, layout.model.height, true, 1F);
        for (int ignored = 0; ignored < 30; ignored++) {
            PreviewExplanationLayout changedText = layout.withExplanation(layout.explanation);
            assertEquals(layout.model, changedText.model);
            assertEquals(scale, framing.fitScale(changedText.model.width, changedText.model.height, true, 1F));
        }
    }

    @Test void productionManualTakeoverFreezesTheAuthoredViewAndResetResumesTheTimeline() throws Exception {
        PonderScene scene = scene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        builder.zoomScene(new Vec3d(6, 2, 3), 2F, 20);
        builder.zoomScene(null, null, 20);
        Viewer screen = new Viewer(scene);
        PonderScenePreview.PreviewBounds bounds = PonderSceneRuntime.computeBounds(scene);
        java.lang.reflect.Method viewMethod = PonderDebugScreen.class.getDeclaredMethod("getPreviewView",
            PonderScene.class, PonderScenePreview.PreviewBounds.class, float.class);
        viewMethod.setAccessible(true);
        PreviewCameraTimeline.View at20 = (PreviewCameraTimeline.View) viewMethod.invoke(screen, scene, bounds, 20F);
        java.lang.reflect.Field tick = PonderDebugScreen.class.getDeclaredField("playbackTick");
        tick.setAccessible(true);
        tick.setInt(screen, 20);
        java.lang.reflect.Method take = PonderDebugScreen.class.getDeclaredMethod("takeManualCamera");
        take.setAccessible(true);
        take.invoke(screen);
        PreviewCameraTimeline.View manual = (PreviewCameraTimeline.View) viewMethod.invoke(screen, scene, bounds, 40F);
        assertEquals(at20.center, manual.center);
        assertEquals(at20.multiplier, manual.multiplier);
        screen.resetPreviewCamera();
        PreviewCameraTimeline.View resumed = (PreviewCameraTimeline.View) viewMethod.invoke(screen, scene, bounds, 40F);
        assertEquals(1F, resumed.multiplier);
        assertNotEquals(manual.center, resumed.center);
        assertEquals(40, screen.getSceneEndTick(scene));
    }

    @Test void resetFilteringDoesNotRemoveGuiSnapshotsOrControls() {
        PonderScene scene = scene();
        PonderScene.OverlayEvent controls = scene.createOverlayEvent(100).controls(Vec3d.ZERO,
            net.createmod.catnip.math.Pointing.RIGHT);
        PonderScene.OverlayEvent gui = scene.createOverlayEvent(100).guiTexture(new ResourceLocation("camera_test", "menu"),
            0, 0, 16, 16, 16, 16, 16, 16, 0, 0, true).pointAt(Vec3d.ZERO);
        scene.recordCameraView(null, null, 0);
        assertFalse(scene.isOverlayCleared(controls, 0));
        assertFalse(scene.isOverlayCleared(gui, 0));
        assertEquals(1, PreviewCalloutContent.collect(scene, 0, key -> key).size());
        assertEquals(2, PreviewCalloutContent.collect(scene, 0, key -> key).get(0).events.size());
    }

    @Test void resetCannotReviveAnOldOutlineAsThePredecessorOfANewChase() {
        PonderScene scene = scene();
        scene.createOverlayEvent(100).sceneOutline(new net.minecraft.util.math.AxisAlignedBB(0, 0, 0, 1, 1, 1))
            .overlayId("same-slot");
        scene.addIdleTicks(5);
        scene.recordCameraView(null, null, 20);
        scene.createOverlayEvent(100).sceneOutline(new net.minecraft.util.math.AxisAlignedBB(10, 0, 0, 11, 1, 1))
            .overlayId("same-slot").outlineChasing(true);
        PonderSceneRuntime.RuntimeState state = PonderSceneRuntime.buildState(scene, 5);
        PreviewOutlineGeometry geometry = new PreviewOutlineGeometry();
        assertEquals(1, geometry.frame(scene, state, 5, null).size());
        for (PreviewOutlineGeometry.Edge edge : geometry.frame(scene, state, 5, null).get(0).edges) {
            assertTrue(edge.start.x >= 10 && edge.end.x >= 10);
        }
        assertEquals(1, geometry.frame(scene, state, 4, null).size());
        assertEquals(0D, geometry.frame(scene, state, 4, null).get(0).edges.get(0).start.x);
    }

    private static void assertView(PonderScene scene, float tick, Vec3d center, float scale) {
        PreviewCameraTimeline.View view = PreviewCameraTimeline.evaluate(scene.getCameraViewEvents(), tick, DEFAULT);
        assertEquals(center, view.center);
        assertEquals(scale, view.multiplier);
    }

    private static PonderScene scene() {
        PonderStoryBoardEntry entry = new PonderStoryBoardEntry((s, u) -> { }, "camera_test",
            new ResourceLocation("camera_test", "main"), new ResourceLocation("minecraft", "stone"));
        return new PonderScene(entry, new PonderLocalization());
    }

    private static final class Viewer extends PonderDebugScreen {
        private final PonderScene scene;
        Viewer(PonderScene scene) { super(null, 0, true, null); this.scene = scene; }
        @Override protected PonderScene getSelectedScene() { return scene; }
    }
}
