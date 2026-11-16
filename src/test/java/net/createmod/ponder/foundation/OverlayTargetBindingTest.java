package net.createmod.ponder.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderScene.OverlayEvent;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OverlayTargetBindingTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void outlineAndCaptionKeepIndependentImmutableCopiesOfAnIrregularSelection() {
        PonderScene scene = scene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        Selection selection = SelectionImpl.fromTo(BlockPos.ORIGIN, BlockPos.ORIGIN)
            .add(SelectionImpl.fromTo(new BlockPos(3, 1, 2), new BlockPos(3, 1, 2)));
        builder.overlay().showOutlineWithText(selection, 40).colored(PonderPalette.GREEN).text("target");
        selection.add(SelectionImpl.fromTo(new BlockPos(9, 0, 0), new BlockPos(9, 0, 0)));

        OverlayEvent outline = scene.getOverlayEvents().get(0);
        OverlayEvent text = scene.getOverlayEvents().get(1);
        List<BlockPos> expected = Arrays.asList(BlockPos.ORIGIN, new BlockPos(3, 1, 2));
        assertEquals(expected, outline.getSourcePositions());
        assertEquals(expected, text.getTargetPositions());
        assertNotSame(outline.getSourcePositions(), text.getTargetPositions());
        assertThrows(UnsupportedOperationException.class, () -> outline.getSourcePositions().clear());
        assertThrows(UnsupportedOperationException.class, () -> text.getTargetPositions().clear());
        assertEquals(outline.getColor(), text.getColor());
    }

    @Test
    void mutablePositionsAndCallerCollectionsCannotChangeStoredTargets() {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(1, 2, 3);
        List<BlockPos> positions = new ArrayList<BlockPos>(Arrays.asList(mutable, mutable, null));
        OverlayEvent event = scene().createOverlayEvent(20).sourcePositions(positions).targetPositions(positions);
        mutable.setPos(8, 8, 8);
        positions.clear();
        assertEquals(Collections.singletonList(new BlockPos(1, 2, 3)), event.getSourcePositions());
        assertEquals(event.getSourcePositions(), event.getTargetPositions());
    }

    @Test
    void plainOutlinesKeepExactDisconnectedPositionsAndChaseEventsAreMarkedSeparately() {
        PonderScene scene = scene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        Selection selection = SelectionImpl.fromTo(BlockPos.ORIGIN, BlockPos.ORIGIN)
            .add(SelectionImpl.fromTo(new BlockPos(5, 0, 0), new BlockPos(5, 0, 0)));
        builder.overlay().showOutline(PonderPalette.RED, "plain", selection, 20);
        builder.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "chase", new AxisAlignedBB(0, 0, 0, 2, 2, 2), 20);
        assertEquals(2, scene.getOverlayEvents().get(0).getSourcePositions().size());
        assertFalse(scene.getOverlayEvents().get(0).isOutlineChasing());
        assertTrue(scene.getOverlayEvents().get(1).isOutlineChasing());
        assertTrue(scene.getOverlayEvents().get(1).getSourcePositions().isEmpty());
    }

    @Test
    void textAndGuiSelectionOverloadsBindTheSameSourcePositions() {
        PonderScene scene = scene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        Selection selection = SelectionImpl.fromTo(new BlockPos(2, 1, 2), new BlockPos(3, 1, 2));
        builder.overlay().showText(30).pointAt(selection).text("caption");
        builder.overlay().showGuiSnapshot(new ResourceLocation("test", "image"), 30).pointAt(selection);
        OverlayEvent text = scene.getOverlayEvents().get(0);
        OverlayEvent image = scene.getOverlayEvents().get(1);
        assertEquals(selection.getCenter(), text.getPointAt());
        assertEquals(text.getPointAt(), image.getPointAt());
        assertEquals(text.getTargetPositions(), image.getTargetPositions());
        assertEquals(2, image.getTargetPositions().size());
    }

    @Test
    void chasingSelectionKeepsImmutableDisconnectedPositionsAndSlotIdentity() {
        PonderScene scene = scene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        Selection selection = SelectionImpl.fromTo(BlockPos.ORIGIN, BlockPos.ORIGIN)
            .add(SelectionImpl.fromTo(new BlockPos(3, 1, 2), new BlockPos(3, 1, 2)));
        Object slot = new Object();
        builder.overlay().chaseOutline(PonderPalette.GREEN, slot, selection, 20);
        selection.add(SelectionImpl.fromTo(new BlockPos(9, 0, 0), new BlockPos(9, 0, 0)));
        builder.idle(10);
        builder.overlay().chaseOutline(PonderPalette.GREEN, slot,
            SelectionImpl.fromTo(new BlockPos(1, 1, 1), new BlockPos(1, 1, 1)), 20);

        OverlayEvent first = scene.getOverlayEvents().get(0);
        OverlayEvent second = scene.getOverlayEvents().get(1);
        assertTrue(first.isOutlineChasing());
        assertTrue(second.isOutlineChasing());
        assertEquals(first.getOverlayId(), second.getOverlayId());
        assertEquals(Arrays.asList(BlockPos.ORIGIN, new BlockPos(3, 1, 2)), first.getSourcePositions());
        assertThrows(UnsupportedOperationException.class, () -> first.getSourcePositions().clear());
        assertEquals(10, second.getTick());
    }

    @Test
    void topSurfaceCoordinatesShareTheActualBlockWithControlsAndValueBox() {
        PonderScene scene = scene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        BlockPos target = new BlockPos(2, 1, 3);
        Vec3d top = new Vec3d(2.5, 2, 3.5);
        builder.world().setBlock(target, Blocks.STONE.getDefaultState(), false);
        builder.overlay().showText(30).pointAt(top).text("caption");
        builder.overlay().showGuiSnapshot(new ResourceLocation("test", "image"), 30).pointAt(top);
        builder.overlay().showControls(top, Pointing.DOWN, 30).rightClick();
        builder.overlay().showCenteredScrollInput(target, EnumFacing.UP, 30);
        for (OverlayEvent event : scene.getOverlayEvents()) {
            assertEquals(Collections.singletonList(target), event.getTargetPositions());
        }
    }

    @Test
    void independentExplicitlyClearsAnchorsAndPointAtCanBindAgain() {
        PonderScene scene = scene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        Selection selection = SelectionImpl.fromTo(BlockPos.ORIGIN, BlockPos.ORIGIN);
        builder.overlay().showText(30).pointAt(selection).placeNearTarget().independent(12);
        builder.overlay().showGuiSnapshot(new ResourceLocation("test", "image"), 30).pointAt(selection).independent();
        for (OverlayEvent event : scene.getOverlayEvents()) {
            assertNull(event.getPointAt());
            assertTrue(event.getTargetPositions().isEmpty());
            assertFalse(event.isPlaceNearTarget());
        }
        OverlayEvent text = scene.getOverlayEvents().get(0).pointAt(new Vec3d(0.5, 0.5, 0.5));
        assertEquals(-1, text.getIndependentY());
        assertEquals(Collections.singletonList(BlockPos.ORIGIN), text.getTargetPositions());
    }

    @Test
    void emptiedSelectionNeverBecomesAnExplicitBoxOrAnOriginTarget() {
        PonderScene scene = scene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        Selection origin = SelectionImpl.fromTo(BlockPos.ORIGIN, BlockPos.ORIGIN);
        Selection empty = origin.substract(origin);
        builder.overlay().showOutline(PonderPalette.GREEN, "empty", empty, 30);
        builder.overlay().showOutlineWithText(empty, 30).text("no selection");
        builder.overlay().showText(30).pointAt(empty).text("edge caption");
        builder.overlay().showGuiSnapshot(new ResourceLocation("test", "image"), 30).pointAt(empty);
        for (OverlayEvent event : scene.getOverlayEvents()) {
            assertTrue(event.getSourcePositions().isEmpty());
            assertTrue(event.getTargetPositions().isEmpty());
            assertNull(event.getSceneBounds());
            assertNull(event.getPointAt());
        }
        AxisAlignedBB explicit = new AxisAlignedBB(0, 0, 0, 1, 1, 1);
        assertEquals(explicit, scene.createOverlayEvent(30).sceneOutline(explicit).getSceneBounds());
    }

    private static PonderScene scene() {
        ResourceLocation id = new ResourceLocation("ponder_test", "overlay_targets");
        return new PonderScene(new PonderStoryBoardEntry(null, "ponder_test", id, id), new PonderLocalization());
    }
}
