package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Point;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

class PreviewWorldProjectionTest {
    @Test void readerRemappingCopiesOnlyLogicalCoordinatesAndPreservesLetterboxing() {
        PreviewWorldProjection original = new PreviewWorldProjection(identity(),identity(),
            new int[] {80,40,1120,640},1280,720,1280,720);
        PreviewWorldProjection reader = original.withLogicalSize(640,360);
        assertEquals(new Point(640,360),original.project(Vec3d.ZERO));
        assertEquals(new Point(320,180),reader.project(Vec3d.ZERO));
        assertEquals(new Point(80,40),original.project(new Vec3d(-1,1,0)));
        assertEquals(new Point(40,20),reader.project(new Vec3d(-1,1,0)));
        assertEquals(new Point(1200,680),original.project(new Vec3d(1,-1,0)));
        assertEquals(new Point(600,340),reader.project(new Vec3d(1,-1,0)));
        assertNull(original.project(new Vec3d(2,0,0)));
        assertNull(reader.project(new Vec3d(2,0,0)));
        assertEquals(new Point(640,360),original.project(Vec3d.ZERO),"The captured world projection remains immutable");
    }
    @Test void oddFramebufferReaderMappingRetainsTheCapturedViewportAndRejectsInvalidSizes() {
        PreviewWorldProjection original = new PreviewWorldProjection(identity(),identity(),
            new int[] {31,17,937,661},1001,701,1001,701);
        PreviewWorldProjection reader = original.withLogicalSize(501,351);
        assertEquals(new Point(500,354),original.project(Vec3d.ZERO));
        assertEquals(new Point(250,177),reader.project(Vec3d.ZERO));
        assertEquals(new Point(16,12),reader.project(new Vec3d(-1,1,0)));
        assertThrows(IllegalArgumentException.class, () -> original.withLogicalSize(0,351));
        assertThrows(IllegalArgumentException.class, () -> original.withLogicalSize(501,-1));
    }
    private static float[] identity() {
        return new float[] {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
    }
    private static PreviewWorldProjection full(float[] model, float[] projection) {
        return new PreviewWorldProjection(model, projection, new int[] {0,0,1280,720}, 640,360,1280,720);
    }
    @Test void identityMapsTheCenterAndFlipsOpenGlYIntoTopLeftGuiCoordinates() {
        PreviewWorldProjection view = full(identity(), identity());
        assertEquals(new Point(320,180), view.project(Vec3d.ZERO));
        assertEquals(new Point(480,90), view.project(new Vec3d(0.5,0.5,0)));
        assertEquals(new Point(160,270), view.project(new Vec3d(-0.5,-0.5,0)));
    }
    @Test void viewportOriginAndIndependentLogicalScaleAreAppliedAfterProjection() {
        PreviewWorldProjection view = new PreviewWorldProjection(identity(), identity(), new int[] {320,180,640,360}, 320,180,1280,720);
        assertEquals(new Point(160,90), view.project(Vec3d.ZERO));
        assertEquals(new Point(80,45), view.project(new Vec3d(-1,1,0)));
        assertEquals(new Point(240,135), view.project(new Vec3d(1,-1,0)));
    }
    @Test void columnMajorModelViewRunsBeforeProjectionRatherThanUsingTheWrongMatrixOrder() {
        float[] model = identity(); model[12] = 0.5F; model[13] = 0.25F;
        float[] projection = identity(); projection[0] = 0.5F;
        PreviewWorldProjection view = full(model, projection);
        assertEquals(new Point(400,135), view.project(Vec3d.ZERO));
        model = identity(); model[0] = 0; model[1] = 1; model[4] = -1; model[5] = 0;
        assertEquals(new Point(320,90), full(model, identity()).project(new Vec3d(0.5,0,0)));
    }
    @Test void perspectiveDividesByPositiveHomogeneousWAndRejectsPointsBehindTheCamera() {
        // Symmetric ninety-degree perspective, near 1 and far 3; all coefficients are exact.
        float[] perspective = {1,0,0,0, 0,1,0,0, 0,0,-2,-1, 0,0,-3,0};
        PreviewWorldProjection view = full(identity(), perspective);
        assertEquals(new Point(400,180), view.project(new Vec3d(0.5,0,-2)));
        assertEquals(new Point(320,180), view.project(new Vec3d(0,0,-1)));
        assertEquals(new Point(320,180), view.project(new Vec3d(0,0,-3)));
        assertNull(view.project(new Vec3d(0,0,1))); assertNull(view.project(Vec3d.ZERO));
        assertNull(view.project(new Vec3d(0,0,-0.99))); assertNull(view.project(new Vec3d(0,0,-3.01)));
    }
    @Test void clipBoundaryIsAcceptedButPointsOutsideAnyOfTheSixPlanesAreRejected() {
        PreviewWorldProjection view = full(identity(), identity());
        assertEquals(new Point(0,0), view.project(new Vec3d(-1,1,-1)));
        assertEquals(new Point(639,359), view.project(new Vec3d(1,-1,1)));
        for (Vec3d outside : new Vec3d[] {new Vec3d(-1.001,0,0),new Vec3d(1.001,0,0),
            new Vec3d(0,-1.001,0),new Vec3d(0,1.001,0),new Vec3d(0,0,-1.001),new Vec3d(0,0,1.001)})
            assertNull(view.project(outside));
    }
    @Test void partiallyOffscreenViewportDoesNotClampInvisibleTargetsOntoTheScreen() {
        PreviewWorldProjection view = new PreviewWorldProjection(identity(),identity(),new int[] {-200,0,800,720},640,360,1280,720);
        assertNull(view.project(new Vec3d(-1,0,0)));
        assertEquals(new Point(100,180),view.project(Vec3d.ZERO));
        view = new PreviewWorldProjection(identity(),identity(),new int[] {0,600,1280,720},640,360,1280,720);
        assertNull(view.project(Vec3d.ZERO));
    }
    @Test void snapshotIsDefensivelyCopiedAndNativeCaptureIsNotNeededByTheMathPath() {
        float[] model = identity(), projection = identity(); int[] viewport = {0,0,1280,720};
        PreviewWorldProjection view = new PreviewWorldProjection(model,projection,viewport,640,360,1280,720);
        model[12] = 50; projection[0] = 0; viewport[0] = 8000;
        assertEquals(new Point(320,180),view.project(Vec3d.ZERO));
    }
    @Test void nonfiniteInputsAndInvalidDimensionsCannotCreateVisibleTargets() {
        PreviewWorldProjection view = full(identity(),identity());
        assertNull(view.project(null)); assertNull(view.project(new Vec3d(Double.NaN,0,0)));
        assertNull(view.project(new Vec3d(0,Double.POSITIVE_INFINITY,0)));
        float[] bad = identity(); bad[0] = Float.NaN;
        assertThrows(IllegalArgumentException.class,() -> full(bad,identity()));
        assertThrows(IllegalArgumentException.class,() -> full(new float[15],identity()));
        assertThrows(IllegalArgumentException.class,() -> new PreviewWorldProjection(identity(),identity(),new int[] {0,0,0,720},640,360,1280,720));
        assertThrows(IllegalArgumentException.class,() -> new PreviewWorldProjection(identity(),identity(),new int[] {0,0,1280,720},0,360,1280,720));
    }
    @Test void centerScorePrioritizesTheVisibleCenterAndRejectsInvisiblePoints() {
        PreviewWorldProjection view = full(identity(),identity());
        assertEquals(0,view.centerScore(Vec3d.ZERO));
        assertTrue(view.centerScore(new Vec3d(0.1,0,0)) < view.centerScore(new Vec3d(0.8,0,0)));
        assertEquals(Double.POSITIVE_INFINITY,view.centerScore(new Vec3d(2,0,0)));
    }
}
