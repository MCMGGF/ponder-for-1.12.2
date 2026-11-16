package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSceneBuilder;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.SelectionImpl;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PreviewOutlineGeometryTest {

    private static final BlockPos ORIGIN = BlockPos.ORIGIN;

    @BeforeAll
    static void bootstrap() { Bootstrap.register(); }

    @Test
    void singleBlockHasTwelveWorldSpaceEdgesAndEightTargetCorners() {
        PonderScene scene = scene();
        select(scene, Collections.singletonList(ORIGIN));
        PonderSceneRuntime.RuntimeState state = blocks(ORIGIN);
        PreviewOutlineGeometry.Outline outline = geometry(scene, state, 10).get(0);
        assertEquals(12, outline.edges.size());
        for (PreviewOutlineGeometry.Edge edge : outline.edges) {
            assertEquals(1D, edge.start.distanceTo(edge.end), 1E-8);
        }
        assertEquals(8, PreviewOutlineGeometry.targetPoints(Collections.singletonList(ORIGIN), state,
            new Access(state)).size());
    }

    @Test
    void authorPickMarkerUsesTheSameShapeAndTransformsWithoutAddingSceneEvents() {
        PonderScene scene=scene();
        PonderSceneRuntime.RuntimeState state=blocks(ORIGIN);
        state.blocksByPosition.get(ORIGIN).transforms.add(PonderSceneRuntime.TransformStep.move(new Vec3d(3,2,1)));
        PreviewOutlineGeometry.Outline marker=PreviewOutlineGeometry.selection(ORIGIN,state,new Access(state)).get(0);
        assertEquals(12,marker.edges.size()); assertEquals(3D,minX(points(marker.edges)),1E-8);
        assertEquals(0x78DBBD,marker.color); assertTrue(marker.wide); assertNull(marker.event);
        assertTrue(scene.getOverlayEvents().isEmpty());
        state.blocksByPosition.get(ORIGIN).visible=false;
        assertTrue(PreviewOutlineGeometry.selection(ORIGIN,state,new Access(state)).isEmpty());
    }

    @Test
    void filledCuboidHasOnlyOuterEdgesWithoutInternalGrid() {
        List<BlockPos> selection = Arrays.asList(ORIGIN, new BlockPos(1, 0, 0), new BlockPos(0, 1, 0),
            new BlockPos(1, 1, 0));
        PonderScene scene = scene();
        select(scene, selection);
        List<PreviewOutlineGeometry.Edge> edges = geometry(scene, blocks(selection.toArray(new BlockPos[0])), 10)
            .get(0).edges;
        assertEquals(12, edges.size());
        for (Vec3d point : points(edges)) {
            assertTrue(point.x == 0 || point.x == 2);
            assertTrue(point.y == 0 || point.y == 2);
        }
    }

    @Test
    void disjointSelectionAndSelectedAirNeverCreateBoxAcrossGap() {
        for (List<BlockPos> selected : Arrays.asList(Arrays.asList(ORIGIN, new BlockPos(2, 0, 0)),
            Arrays.asList(ORIGIN, new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)))) {
            PonderScene scene = scene();
            select(scene, selected);
            List<PreviewOutlineGeometry.Edge> edges = geometry(scene, blocks(ORIGIN, new BlockPos(2, 0, 0)), 10)
                .get(0).edges;
            assertEquals(24, edges.size());
            for (PreviewOutlineGeometry.Edge edge : edges) {
                assertEquals(1D, edge.start.distanceTo(edge.end), 1E-8);
                assertFalse(edge.start.x < 2 && edge.end.x > 1);
            }
        }
    }

    @Test
    void irregularAdjacentBlocksDeduplicateSharedEdges() {
        List<BlockPos> selected = Arrays.asList(ORIGIN, new BlockPos(1, 0, 0), new BlockPos(0, 0, 1));
        PonderScene scene = scene();
        select(scene, selected);
        List<PreviewOutlineGeometry.Edge> edges = geometry(scene, blocks(selected.toArray(new BlockPos[0])), 10)
            .get(0).edges;
        assertTrue(edges.size() > 12);
        assertTrue(edges.size() < 36);
        for (PreviewOutlineGeometry.Edge edge : edges) {
            assertEquals(1D, edge.start.distanceTo(edge.end), 1E-8);
        }
    }

    @Test
    void railAndSlabUseTheirVanillaSelectionHeights() {
        for (IBlockState block : Arrays.asList(Blocks.RAIL.getDefaultState(), Blocks.RAIL.getStateFromMeta(2),
            Blocks.STONE_SLAB.getDefaultState())) {
            PonderScene scene = scene();
            select(scene, Collections.singletonList(ORIGIN));
            PonderSceneRuntime.RuntimeState state = blocks(ORIGIN);
            state.blocksByPosition.get(ORIGIN).currentState = block;
            List<PreviewOutlineGeometry.Edge> edges = geometry(scene, state, 10).get(0).edges;
            AxisAlignedBB vanilla = block.getBoundingBox(new Access(state), ORIGIN);
            assertEquals(vanilla.maxY, maxY(points(edges)), 1E-8);
            assertTrue(vanilla.maxY < 1D);
        }
    }

    @Test
    void invalidOrThrowingSelectionShapeFallsBackToUnitBlock() {
        List<Block> unusual = Arrays.asList(new Block(Material.ROCK) {
            @Override
            public AxisAlignedBB getBoundingBox(IBlockState blockState, IBlockAccess source, BlockPos pos) {
                throw new IllegalStateException("shape unavailable");
            }
        }, new Block(Material.ROCK) {
            @Override
            public AxisAlignedBB getBoundingBox(IBlockState blockState, IBlockAccess source, BlockPos pos) {
                return new AxisAlignedBB(0, 0, 0, Double.NaN, 1, 1);
            }
        });
        for (Block block : unusual) {
            PonderScene scene = scene();
            select(scene, Collections.singletonList(ORIGIN));
            PonderSceneRuntime.RuntimeState state = blocks(ORIGIN);
            state.blocksByPosition.get(ORIGIN).currentState = block.getDefaultState();
            List<PreviewOutlineGeometry.Edge> edges = geometry(scene, state, 10).get(0).edges;
            assertEquals(12, edges.size());
            for (PreviewOutlineGeometry.Edge edge : edges) {
                assertEquals(1D, edge.start.distanceTo(edge.end), 1E-8);
            }
        }
    }

    @Test
    void neighboringSectionsUseTheirOwnExactTransformsAndFadeOffsets() {
        BlockPos adjacent = new BlockPos(1, 0, 0);
        PonderScene scene = scene();
        select(scene, Arrays.asList(ORIGIN, adjacent));
        PonderSceneRuntime.RuntimeState state = blocks(ORIGIN, adjacent);
        PonderSceneRuntime.RuntimeBlockState moved = state.blocksByPosition.get(ORIGIN);
        moved.transforms.add(PonderSceneRuntime.TransformStep.rotate(Vec3d.ZERO, new Vec3d(0, 90, 0)));
        moved.transforms.add(PonderSceneRuntime.TransformStep.move(new Vec3d(5, 2, 1)));
        moved.fadeOffsetY = .25F;
        moved.fade = .5F;
        List<PreviewOutlineGeometry.Edge> edges = geometry(scene, state, 10).get(0).edges;
        assertEquals(24, edges.size());
        Vec3d expected = PonderSceneRuntime.transformPoint(moved, Vec3d.ZERO);
        assertTrue(points(edges).stream().anyMatch(point -> point.distanceTo(expected) < 1E-8));
        assertTrue(points(edges).stream().anyMatch(point -> point.distanceTo(new Vec3d(1, 0, 0)) < 1E-8));
        assertTrue(edges.stream().anyMatch(edge -> edge.alpha == .5F));
        List<Vec3d> targets = PreviewOutlineGeometry.targetPoints(Collections.singletonList(ORIGIN), state,
            new Access(state));
        assertEquals(8, targets.size());
        assertEquals(expected.y, targets.get(0).y, 1E-8);
        moved.visible = false;
        assertTrue(PreviewOutlineGeometry.targetPoints(Collections.singletonList(ORIGIN), state, new Access(state))
            .isEmpty());
        assertEquals(12, geometry(scene, state, 11).get(0).edges.size());
    }

    @Test
    void equivalentTransformHistoriesCanStillUseOneCuboid() {
        BlockPos adjacent = new BlockPos(1, 0, 0);
        PonderScene scene = scene();
        select(scene, Arrays.asList(ORIGIN, adjacent));
        PonderSceneRuntime.RuntimeState state = blocks(ORIGIN, adjacent);
        state.blocksByPosition.get(ORIGIN).transforms.add(PonderSceneRuntime.TransformStep.move(new Vec3d(1, 0, 0)));
        state.blocksByPosition.get(adjacent).transforms.add(PonderSceneRuntime.TransformStep.move(new Vec3d(.5, 0, 0)));
        state.blocksByPosition.get(adjacent).transforms.add(PonderSceneRuntime.TransformStep.move(new Vec3d(.5, 0, 0)));
        assertEquals(12, geometry(scene, state, 10).get(0).edges.size());
    }

    @Test
    void chaseUsesOneSlotAndReevaluatesDeterministicallyWhenSeeking() {
        PonderScene scene = scene();
        scene.createOverlayEvent(80).sceneOutline(new AxisAlignedBB(0, 0, 0, 1, 1, 1)).overlayId("slot");
        scene.addIdleTicks(10);
        scene.createOverlayEvent(50).sceneOutline(new AxisAlignedBB(10, 0, 0, 11, 1, 1)).overlayId("slot")
            .outlineChasing(true);
        PreviewOutlineGeometry geometry = new PreviewOutlineGeometry();
        PonderSceneRuntime.RuntimeState state = blocks();
        assertEquals(0D, minX(points(geometry.frame(scene, state, 10, new Access(state)).get(0).edges)), 1E-8);
        assertEquals(5D, minX(points(geometry.frame(scene, state, 13, new Access(state)).get(0).edges)), 1E-8);
        assertEquals(10D, minX(points(geometry.frame(scene, state, 16, new Access(state)).get(0).edges)), 1E-8);
        assertEquals(5D, minX(points(geometry.frame(scene, state, 13, new Access(state)).get(0).edges)), 1E-8);
        assertEquals(0D, minX(points(geometry.frame(scene, state, 9, new Access(state)).get(0).edges)), 1E-8);
        assertTrue(geometry.frame(scene, state, 61, new Access(state)).isEmpty(),
            "an expired replacement must not resurrect the previous slot target");
    }

    @Test
    void selectionChaseUsesRuntimeBlockTransformsAndRemainsSeekable() {
        PonderScene scene = scene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        BlockPos next = new BlockPos(4, 0, 0);
        Object slot = new Object();
        builder.overlay().chaseOutline(PonderPalette.RED, slot, SelectionImpl.fromTo(ORIGIN, ORIGIN), 40);
        builder.idle(10);
        builder.overlay().chaseOutline(PonderPalette.RED, slot, SelectionImpl.fromTo(next, next), 40);
        PonderSceneRuntime.RuntimeState state = blocks(ORIGIN, next);
        state.blocksByPosition.get(next).transforms.add(PonderSceneRuntime.TransformStep.move(new Vec3d(2, 0, 0)));
        PreviewOutlineGeometry geometry = new PreviewOutlineGeometry();
        assertEquals(1, geometry.frame(scene, state, 13, new Access(state)).size());
        assertEquals(3D, minX(points(geometry.frame(scene, state, 13, new Access(state)).get(0).edges)), 1E-8);
        assertEquals(6D, minX(points(geometry.frame(scene, state, 16, new Access(state)).get(0).edges)), 1E-8);
        assertEquals(3D, minX(points(geometry.frame(scene, state, 13, new Access(state)).get(0).edges)), 1E-8);
        assertEquals(0D, minX(points(geometry.frame(scene, state, 9, new Access(state)).get(0).edges)), 1E-8);
    }

    @Test
    void explicitBoundsValueBoxAndSceneLineRemainThreeDimensional() {
        PonderScene scene = scene();
        scene.createOverlayEvent(80).sceneOutline(new AxisAlignedBB(-2, 1, 3, -1, 3, 7));
        scene.createOverlayEvent(80).valueBox(new Vec3d(10, 4, 0), new Vec3d(2, 1, .5));
        scene.createOverlayEvent(80).sceneLine(new Vec3d(1, 2, 3), new Vec3d(4, 5, 6), true);
        List<PreviewOutlineGeometry.Outline> outlines = geometry(scene, blocks(), 10);
        assertEquals(3, outlines.size());
        assertEquals(12, outlines.get(0).edges.size());
        assertEquals(12, outlines.get(1).edges.size());
        assertEquals(8D, minX(points(outlines.get(1).edges)), 1E-8);
        assertEquals(1, outlines.get(2).edges.size());
        assertEquals(3D, outlines.get(2).edges.get(0).start.z, 1E-8);
        assertEquals(6D, outlines.get(2).edges.get(0).end.z, 1E-8);
    }

    @Test
    void explicitlyBoundMarkersFollowRecordedOwnerAndDisappearWhenHidden() {
        PonderScene scene = scene();
        scene.createOverlayEvent(80).sceneOutline(new AxisAlignedBB(0, 0, 0, 1, 1, 1))
            .targetPositions(Collections.singletonList(ORIGIN));
        scene.createOverlayEvent(80).valueBox(new Vec3d(.5, .5, .5), new Vec3d(.5, .5, .5));
        scene.createOverlayEvent(80).sceneLine(Vec3d.ZERO, new Vec3d(1, 1, 1), false)
            .targetPositions(Collections.singletonList(ORIGIN));
        PonderSceneRuntime.RuntimeState state = blocks(ORIGIN);
        PonderSceneRuntime.RuntimeBlockState block = state.blocksByPosition.get(ORIGIN);
        block.transforms.add(PonderSceneRuntime.TransformStep.move(new Vec3d(4, 0, 0)));
        List<PreviewOutlineGeometry.Outline> moved = geometry(scene, state, 10);
        assertEquals(3, moved.size());
        for (PreviewOutlineGeometry.Outline outline : moved) {
            assertEquals(4D, minX(points(outline.edges)), 1E-8);
        }
        block.visible = false;
        assertTrue(geometry(scene, state, 11).isEmpty());
        assertEquals(3, geometry(scene, blocks(), 11).size(),
            "authored empty-space coordinates remain compatible when no block ever existed there");
    }

    @Test
    void cacheRetainsOnlyCurrentFrameAndCanBeExplicitlyCleared() {
        PonderScene scene = scene();
        select(scene, Collections.singletonList(ORIGIN));
        PonderSceneRuntime.RuntimeState state = blocks(ORIGIN);
        PreviewOutlineGeometry geometry = new PreviewOutlineGeometry();
        List<PreviewOutlineGeometry.Outline> first = geometry.frame(scene, state, 10, new Access(state));
        assertSame(first, geometry.frame(scene, state, 10, new Access(state)));
        assertNotSame(first, geometry.frame(scene, state, 11, new Access(state)));
        geometry.clear();
        assertNotSame(first, geometry.frame(scene, state, 10, new Access(state)));
    }

    private static PonderScene scene() {
        ResourceLocation id = new ResourceLocation("ponder_test", "outlines");
        return new PonderScene(new PonderStoryBoardEntry(null, "ponder_test", id, id), new PonderLocalization());
    }

    private static void select(PonderScene scene, List<BlockPos> positions) {
        scene.createOverlayEvent(100).sceneOutline(new AxisAlignedBB(-100, -100, -100, 100, 100, 100), positions);
    }

    private static List<PreviewOutlineGeometry.Outline> geometry(PonderScene scene,
        PonderSceneRuntime.RuntimeState state, float tick) {
        return new PreviewOutlineGeometry().frame(scene, state, tick, new Access(state));
    }

    private static PonderSceneRuntime.RuntimeState blocks(BlockPos... positions) {
        Map<BlockPos, PonderSceneRuntime.RuntimeBlockState> blocks = new LinkedHashMap<>();
        for (BlockPos position : positions) {
            PonderSceneRuntime.RuntimeBlockState block = new PonderSceneRuntime.RuntimeBlockState(position);
            block.visible = true;
            block.fade = 1F;
            block.currentState = Blocks.STONE.getDefaultState();
            blocks.put(position, block);
        }
        return new PonderSceneRuntime.RuntimeState(blocks, Collections.emptyMap(), blocks.size(), blocks.size());
    }

    private static List<Vec3d> points(List<PreviewOutlineGeometry.Edge> edges) {
        List<Vec3d> result = new ArrayList<>();
        for (PreviewOutlineGeometry.Edge edge : edges) {
            result.add(edge.start);
            result.add(edge.end);
        }
        return result;
    }

    private static double minX(List<Vec3d> points) {
        return points.stream().mapToDouble(point -> point.x).min().getAsDouble();
    }

    private static double maxY(List<Vec3d> points) {
        return points.stream().mapToDouble(point -> point.y).max().getAsDouble();
    }

    private static final class Access implements IBlockAccess {
        private final PonderSceneRuntime.RuntimeState state;
        private Access(PonderSceneRuntime.RuntimeState state) { this.state = state; }
        public IBlockState getBlockState(BlockPos pos) {
            return PreviewBlockRendering.neighborState(state, null, pos);
        }
        public TileEntity getTileEntity(BlockPos pos) { return null; }
        public int getCombinedLight(BlockPos pos, int lightValue) { return 0xF000F0; }
        public boolean isAirBlock(BlockPos pos) { return getBlockState(pos).getBlock() == Blocks.AIR; }
        public Biome getBiome(BlockPos pos) { return Biomes.PLAINS; }
        public WorldType getWorldType() { return WorldType.FLAT; }
        public int getStrongPower(BlockPos pos, EnumFacing direction) { return 0; }
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean defaultValue) { return !isAirBlock(pos); }
    }
}
