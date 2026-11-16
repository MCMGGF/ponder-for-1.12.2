package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Collections;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

class PreviewBlockPickingTest {
    private static final AxisAlignedBB UNIT = new AxisAlignedBB(0, 0, 0, 1, 1, 1);
    private PreviewBlockPicking.Candidate block(BlockPos position) {
        PonderSceneRuntime.RuntimeBlockState state = new PonderSceneRuntime.RuntimeBlockState(position);
        state.visible = true; state.fade = 1;
        return new PreviewBlockPicking.Candidate(state, UNIT.offset(position));
    }
    private PreviewBlockPicking.Camera camera(double yaw, double pitch, double scale) {
        return new PreviewBlockPicking.Camera(Vec3d.ZERO, yaw, pitch, scale, 100, 100);
    }
    @Test void hitsVisibleFrontSurfaceAndMissesEmptySpace() {
        PreviewBlockPicking.Hit hit = PreviewBlockPicking.pick(Collections.singletonList(block(BlockPos.ORIGIN)), camera(0, 0, 20), 110, 90);
        assertNotNull(hit); assertEquals(BlockPos.ORIGIN, hit.block);
        assertEquals(0.5, hit.point.x, 1E-9); assertEquals(0.5, hit.point.y, 1E-9); assertEquals(1, hit.point.z, 1E-9);
        assertNull(PreviewBlockPicking.pick(Collections.singletonList(block(BlockPos.ORIGIN)), camera(0, 0, 20), 130, 90));
    }
    @Test void depthSelectionDoesNotDependOnIterationOrder() {
        PreviewBlockPicking.Candidate near = block(new BlockPos(0, 0, 2)), far = block(BlockPos.ORIGIN);
        for (java.util.List<PreviewBlockPicking.Candidate> order : Arrays.asList(Arrays.asList(near, far), Arrays.asList(far, near))) {
            PreviewBlockPicking.Hit hit = PreviewBlockPicking.pick(order, camera(0, 0, 20), 110, 90);
            assertEquals(near.block.pos, hit.block); assertEquals(3, hit.point.z, 1E-9);
        }
    }
    @Test void affineMotionRotationAndFadeReturnOriginalAuthorCoordinates() {
        PreviewBlockPicking.Candidate target = block(new BlockPos(2, 1, -1));
        target.block.transforms.add(PonderSceneRuntime.TransformStep.rotate(new Vec3d(2, 1, -1), new Vec3d(31, 47, 19)));
        target.block.transforms.add(PonderSceneRuntime.TransformStep.move(new Vec3d(4, 2, 3)));
        target.block.fadeOffsetX = 0.25F; target.block.fadeOffsetY = -0.1F;
        PreviewBlockPicking.Camera view = camera(32, 30, 7);
        Vec3d center = PonderSceneRuntime.transformPoint(target.block, new Vec3d(2.5, 1.5, -0.5));
        Vec3d projected = view.project(center);
        PreviewBlockPicking.Hit hit = PreviewBlockPicking.pick(Collections.singletonList(target), view, projected.x, projected.y);
        assertNotNull(hit); assertEquals(target.block.pos, hit.block);
        assertTrue(target.bounds.grow(1E-7).contains(hit.point));
        assertEquals(0, hit.displayedPoint.squareDistanceTo(PonderSceneRuntime.transformPoint(target.block, hit.point)), 1E-10);
        assertEquals(projected.x, view.project(hit.displayedPoint).x, 1E-8);
        assertEquals(projected.y, view.project(hit.displayedPoint).y, 1E-8);
    }
    @Test void railAndSlabBoundsDoNotBecomeUnitCubeHitAreas() {
        PreviewBlockPicking.Candidate base = block(BlockPos.ORIGIN);
        for (double height : new double[] {0.125, 0.5}) {
            PreviewBlockPicking.Candidate shaped = new PreviewBlockPicking.Candidate(base.block, new AxisAlignedBB(0,0,0,1,height,1));
            assertNotNull(PreviewBlockPicking.pick(Collections.singletonList(shaped), camera(0,0,20), 110,100-height*10));
            assertNull(PreviewBlockPicking.pick(Collections.singletonList(shaped), camera(0,0,20), 110,100-(height+0.1)*20));
        }
    }
    @Test void hiddenDestroyedAndZeroOpacityGeometryCannotBePicked() {
        PreviewBlockPicking.Candidate candidate = block(BlockPos.ORIGIN);
        candidate.block.visible = false;
        assertNull(PreviewBlockPicking.pick(Collections.singletonList(candidate),camera(0,0,20),110,90));
        candidate.block.visible = true; candidate.block.destroyed = true;
        assertNull(PreviewBlockPicking.pick(Collections.singletonList(candidate),camera(0,0,20),110,90));
        candidate.block.destroyed = false; candidate.block.fade = 0;
        assertNull(PreviewBlockPicking.pick(Collections.singletonList(candidate),camera(0,0,20),110,90));
    }
    @Test void compactCameraAndZoomUseExactLogicalCoordinates() {
        for (double scale : new double[] {0.25, 1, 4, 29}) {
            PreviewBlockPicking.Camera view = new PreviewBlockPicking.Camera(new Vec3d(0.5,0.5,0.5),35,30,scale,80,112);
            assertNotNull(PreviewBlockPicking.pick(Collections.singletonList(block(BlockPos.ORIGIN)),view,80,112));
        }
    }
    @Test void invalidCameraOrPointerCannotProduceCoordinates() {
        assertThrows(IllegalArgumentException.class,()->camera(0,0,0));
        assertThrows(IllegalArgumentException.class,()->camera(Double.NaN,0,1));
        assertNull(PreviewBlockPicking.pick(Collections.singletonList(block(BlockPos.ORIGIN)),camera(0,0,1),Double.NaN,100));
        assertNull(PreviewBlockPicking.pick(Collections.emptyList(),camera(0,0,1),100,100));
    }
}
