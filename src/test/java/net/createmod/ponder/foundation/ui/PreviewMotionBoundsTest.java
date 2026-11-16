package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.WorldEvent;
import net.createmod.ponder.foundation.PonderScene.WorldEventType;
import net.createmod.ponder.foundation.PonderSchematic;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.content.DebugScenes;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PreviewMotionBoundsTest {

    private static final double ROUNDING_TOLERANCE = 1.0E-8D;

    @BeforeAll
    static void registerBlocks() {
        Bootstrap.register();
    }

    @Test
    void completeTurnBoundsUseCornerRadiusInsteadOfThreeSampledFrames() throws Exception {
        BlockPos block = new BlockPos(10, 0, 0);
        PonderScene scene = sceneWithBlocks(block);
        rotate(scene, block, Vec3d.ZERO, new Vec3d(0, 360, 0), 360);

        assertBounds(PonderSceneRuntime.computeBounds(scene), -12, 11, 0, 0, -12, 11);
        assertEnclosesFrames(scene, Collections.singleton(block), 0, 360, 0.125F);
    }

    @Test
    void negativeMultiTurnRotationsAroundEveryAxisContainCompleteCorners() throws Exception {
        BlockPos block = new BlockPos(7, -3, 5);
        Vec3d pivot = new Vec3d(-2.25D, 1.5D, 4.75D);
        for (Vec3d rotation : Arrays.asList(new Vec3d(-1800, 0, 0),
            new Vec3d(0, -1800, 0), new Vec3d(0, 0, -1800))) {
            PonderScene scene = sceneWithBlocks(block);
            rotate(scene, block, pivot, rotation, 43);
            assertEnclosesFrames(scene, Collections.singleton(block), -1, 50, 0.125F);
        }
    }

    @Test
    void overlappingSameAxisRotationsIncludeTheirCombinedIntermediateExtrema() throws Exception {
        BlockPos block = new BlockPos(10, 0, 2);
        PonderScene scene = sceneWithBlocks(block);
        rotate(scene, block, Vec3d.ZERO, new Vec3d(0, 90, 0), 48);
        rotate(scene, block, Vec3d.ZERO, new Vec3d(0, 90, 0), 48);

        assertEnclosesFrames(scene, Collections.singleton(block), 0, 60, 0.125F);
        assertTrue(PonderSceneRuntime.computeBounds(scene).minZ <= -11);
    }

    @Test
    void overlappingEulerAxesAndDisplacedPivotsContainAllIntermediateCorners() throws Exception {
        BlockPos block = new BlockPos(8, -2, 4);
        PonderScene scene = sceneWithBlocks(block);
        rotate(scene, block, new Vec3d(1.5D, -0.5D, 2.5D), new Vec3d(90, 90, 0), 60);
        scene.addIdleTicks(7);
        rotate(scene, block, new Vec3d(-2, 4, -1), new Vec3d(-75, 110, 130), 37);

        assertEnclosesFrames(scene, Collections.singleton(block), 0, 75, 0.125F);
    }

    @Test
    void translatingBeforeOrAfterRotationKeepsChangingRadiusInsideTheEnvelope() throws Exception {
        BlockPos block = new BlockPos(5, 2, -1);
        for (boolean moveFirst : new boolean[] { false, true }) {
            PonderScene scene = sceneWithBlocks(block);
            Vec3d offset = new Vec3d(12, -3, 6);
            Vec3d pivot = new Vec3d(-2, 1, 4);
            Vec3d rotation = new Vec3d(45, 540, -70);
            if (moveFirst) {
                move(scene, block, offset, 45);
                rotate(scene, block, pivot, rotation, 60);
            } else {
                rotate(scene, block, pivot, rotation, 60);
                move(scene, block, offset, 45);
            }
            assertEnclosesFrames(scene, Collections.singleton(block), 0, 75, 0.125F);
        }
    }

    @Test
    void completedRotationsRemainAppliedDuringLaterMotionEvents() throws Exception {
        BlockPos block = new BlockPos(4, 2, -5);
        PonderScene scene = sceneWithBlocks(block);
        rotate(scene, block, new Vec3d(1, 1, 1), new Vec3d(0, 170, 0), 24);
        scene.addIdleTicks(30);
        rotate(scene, block, new Vec3d(-3, 2, 1), new Vec3d(-230, 0, 90), 41);
        scene.addIdleTicks(11);
        move(scene, block, new Vec3d(-4, 8, 3), 20);
        scene.addIdleTicks(41);
        rotate(scene, block, Vec3d.ZERO, new Vec3d(0, -125, -80), 17);

        assertEnclosesFrames(scene, Collections.singleton(block), 0, 110, 0.125F);
    }

    @Test
    void zeroDurationRotationsAreDiscreteStepsNotSweptFullOrbits() throws Exception {
        BlockPos block = new BlockPos(10, 2, 1);
        PonderScene halfTurn = sceneWithBlocks(block);
        halfTurn.addIdleTicks(4);
        rotate(halfTurn, block, Vec3d.ZERO, new Vec3d(0, 180, 0), 0);
        assertBounds(PonderSceneRuntime.computeBounds(halfTurn), -11, 10, 2, 2, -2, 1);
        assertEnclosesFrames(halfTurn, Collections.singleton(block), 0, 10, 0.125F);

        PonderScene fullTurn = sceneWithBlocks(block);
        fullTurn.addIdleTicks(4);
        rotate(fullTurn, block, Vec3d.ZERO, new Vec3d(0, 360, 0), 0);
        assertBounds(PonderSceneRuntime.computeBounds(fullTurn), 10, 10, 2, 2, 1, 1);
        assertEnclosesFrames(fullTurn, Collections.singleton(block), 0, 10, 0.125F);
    }

    @Test
    void zeroDurationEventBetweenAnimationsDoesNotDiscardEarlierTransforms() throws Exception {
        BlockPos block = new BlockPos(6, -1, 3);
        PonderScene scene = sceneWithBlocks(block);
        rotate(scene, block, Vec3d.ZERO, new Vec3d(20, 135, 0), 15);
        scene.addIdleTicks(8);
        rotate(scene, block, new Vec3d(1, 2, -3), new Vec3d(0, 180, 90), 0);
        move(scene, block, new Vec3d(4, -2, 7), 19);

        assertEnclosesFrames(scene, Collections.singleton(block), 0, 40, 0.125F);
    }

    @Test
    void staticGeometryAndPureTranslationsRemainExactWithoutGlobalPadding() throws Exception {
        BlockPos block = new BlockPos(3, -2, 5);
        PonderScene staticScene = sceneWithBlocks(block);
        staticScene.recordWorldEvent(WorldEventType.HIDE_SECTION, block, null);
        staticScene.addIdleTicks(20);
        staticScene.recordWorldEvent(WorldEventType.SHOW_SECTION, block, null);
        assertBounds(PonderSceneRuntime.computeBounds(staticScene), 3, 3, -2, -2, 5, 5);

        PonderScene moving = sceneWithBlocks(BlockPos.ORIGIN);
        move(moving, BlockPos.ORIGIN, new Vec3d(4, 2, -3), 20);
        assertBounds(PonderSceneRuntime.computeBounds(moving), 0, 4, 0, 2, -3, 0);
        assertEnclosesFrames(moving, Collections.singleton(BlockPos.ORIGIN), 0, 30, 0.125F);
    }

    @Test
    void directionOnlyShowsAndHidesContainActualCornersForAllSixDirections() throws Exception {
        for (WorldEventType type : Arrays.asList(WorldEventType.SHOW_SECTION, WorldEventType.HIDE_SECTION)) {
            for (EnumFacing direction : EnumFacing.values()) {
                PonderScene scene = sceneWithBlocks(BlockPos.ORIGIN);
                scene.addIdleTicks(1);
                scene.recordWorldEvent(type, BlockPos.ORIGIN, null, direction);
                EnumFacing motion = type == WorldEventType.SHOW_SECTION ? direction.getOpposite() : direction;
                assertBounds(PonderSceneRuntime.computeBounds(scene),
                    Math.min(0, motion.getXOffset()), Math.max(0, motion.getXOffset()),
                    Math.min(0, motion.getYOffset()), Math.max(0, motion.getYOffset()),
                    Math.min(0, motion.getZOffset()), Math.max(0, motion.getZOffset()));
                assertEnclosesFrames(scene, Collections.singleton(BlockPos.ORIGIN), 0, 30, 0.125F);
            }
        }
    }

    @Test
    void directionOnlyFadesDoNotPadUnrelatedStaticGeometry() throws Exception {
        BlockPos staticBlock = new BlockPos(20, 10, 30);
        PonderScene scene = sceneWithBlocks(BlockPos.ORIGIN, staticBlock);
        scene.recordWorldEvent(WorldEventType.SHOW_SECTION, BlockPos.ORIGIN, null, EnumFacing.WEST);

        assertBounds(PonderSceneRuntime.computeBounds(scene), 0, 20, 0, 10, 0, 30);
        assertEnclosesFrames(scene, new LinkedHashSet<BlockPos>(Arrays.asList(BlockPos.ORIGIN, staticBlock)),
            0, 30, 0.125F);
    }

    @Test
    void oppositeInwardFadesDoNotPadTheOutsideOfAnotherGeometryGroup() throws Exception {
        BlockPos distant = new BlockPos(10, 0, 0);
        PonderScene scene = sceneWithBlocks(BlockPos.ORIGIN, distant);
        scene.recordWorldEvent(WorldEventType.SHOW_SECTION, BlockPos.ORIGIN, null, EnumFacing.WEST);
        scene.recordWorldEvent(WorldEventType.SHOW_SECTION, distant, null, EnumFacing.EAST);

        assertBounds(PonderSceneRuntime.computeBounds(scene), 0, 10, 0, 0, 0, 0);
        assertEnclosesFrames(scene, new LinkedHashSet<BlockPos>(Arrays.asList(BlockPos.ORIGIN, distant)),
            0, 30, 0.125F);
    }

    @Test
    void fiveThousandBlocksShareTheTimelineWorkForOneHundredTwentyEvents() throws Exception {
        List<BlockPos> positions = new ArrayList<BlockPos>();
        for (int x = 0; x < 50; x++) {
            for (int y = 0; y < 10; y++) {
                for (int z = 0; z < 10; z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        PonderScene scene = sceneWithBlocks(positions.toArray(new BlockPos[0]));
        for (int event = 0; event < 120; event++) {
            scene.recordSectionRotation(positions, new Vec3d(24.5D, 4.5D, 4.5D), new Vec3d(0, 90, 0), 8);
            scene.addIdleTicks(12);
        }

        PonderScenePreview.PreviewBounds bounds = assertTimeoutPreemptively(Duration.ofSeconds(5),
            () -> PonderSceneRuntime.computeBounds(scene));
        assertTrue(bounds.maxX - bounds.minX + 1 <= 55, "shared grouping must not inflate the rotation radius");
        assertTrue(bounds.maxZ - bounds.minZ + 1 <= 55, "shared grouping must not inflate the rotation radius");
        assertEquals(0, bounds.minY);
        assertEquals(9, bounds.maxY);
        assertFrame(scene, new LinkedHashSet<BlockPos>(Arrays.asList(BlockPos.ORIGIN, new BlockPos(49, 9, 9))),
            bounds, transformMethod(), 6.0F);
    }

    @Test
    void movingGeometryIncludesItsWorldSpaceShowAndHideOffsets() throws Exception {
        BlockPos block = new BlockPos(4, 2, 0);
        PonderScene scene = sceneWithBlocks(block);
        scene.recordWorldEvent(WorldEventType.SHOW_SECTION, block, null, EnumFacing.DOWN);
        rotate(scene, block, Vec3d.ZERO, new Vec3d(25, -450, 35), 40);
        move(scene, block, new Vec3d(2, 3, -4), 30);
        scene.addIdleTicks(12);
        scene.recordWorldEvent(WorldEventType.HIDE_SECTION, block, null, EnumFacing.EAST);
        scene.addIdleTicks(8);
        scene.recordWorldEvent(WorldEventType.SHOW_SECTION, block, null, EnumFacing.SOUTH);

        assertEnclosesFrames(scene, Collections.singleton(block), 0, 60, 0.125F);
    }

    @Test
    void maximumDurationAndHugeTurnCountsHaveBoundedWorkWithoutIntegerEndTickOverflow() throws Exception {
        BlockPos block = new BlockPos(10, 3, -2);
        PonderScene scene = sceneWithBlocks(block);
        scene.addIdleTicks(2_000_000_000);
        rotate(scene, block, new Vec3d(-2, 4, 1), new Vec3d(-1.0E100D, 1.0E200D, -1.0E300D),
            Integer.MAX_VALUE);
        move(scene, block, new Vec3d(40, -10, 3), Integer.MAX_VALUE);

        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> PonderSceneRuntime.computeBounds(scene));
        Method transform = transformMethod();
        PonderScenePreview.PreviewBounds bounds = PonderSceneRuntime.computeBounds(scene);
        for (int step = 0; step <= 32; step++) {
            float tick = (float) (2_000_000_000.0D + (double) Integer.MAX_VALUE * step / 32.0D);
            assertFrame(scene, Collections.singleton(block), bounds, transform, tick);
        }
    }

    @Test
    void instantHugeAnglesUseTheRuntimePhaseRatherThanAnInaccurateDegreeModulo() throws Exception {
        BlockPos block = new BlockPos(1000, 13, -49);
        PonderScene scene = sceneWithBlocks(block);
        rotate(scene, block, new Vec3d(-2, 4, 1), new Vec3d(1.0E100D, -1.0E200D, 1.0E300D), 0);

        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> PonderSceneRuntime.computeBounds(scene));
        assertEnclosesFrames(scene, Collections.singleton(block), 0, 2, 0.25F);
    }

    @Test
    void nonFiniteMotionInputsAreRejectedInsteadOfBecomingPoisonedCameraBounds() throws Exception {
        for (double value : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            PonderScene translation = sceneWithBlocks(BlockPos.ORIGIN);
            move(translation, BlockPos.ORIGIN, new Vec3d(value, 0, 0), 20);
            assertThrows(IllegalArgumentException.class, () -> PonderSceneRuntime.computeBounds(translation));

            PonderScene rotation = sceneWithBlocks(BlockPos.ORIGIN);
            rotate(rotation, BlockPos.ORIGIN, Vec3d.ZERO, new Vec3d(0, value, 0), 20);
            assertThrows(IllegalArgumentException.class, () -> PonderSceneRuntime.computeBounds(rotation));

            PonderScene pivot = sceneWithBlocks(BlockPos.ORIGIN);
            rotate(pivot, BlockPos.ORIGIN, new Vec3d(0, 0, value), new Vec3d(0, 90, 0), 20);
            assertThrows(IllegalArgumentException.class, () -> PonderSceneRuntime.computeBounds(pivot));
        }
    }

    @Test
    void finiteCoordinatesAreRejectedOnlyWhenArithmeticOrIntegerBoundsOverflow() throws Exception {
        PonderScene valid = sceneWithBlocks(BlockPos.ORIGIN);
        move(valid, BlockPos.ORIGIN, new Vec3d(1_000_000_000, -1_000_000_000, 0), 20);
        assertBounds(PonderSceneRuntime.computeBounds(valid),
            0, 1_000_000_000, -1_000_000_000, 0, 0, 0);

        PonderScene outOfRange = sceneWithBlocks(BlockPos.ORIGIN);
        move(outOfRange, BlockPos.ORIGIN, new Vec3d(Double.MAX_VALUE, 0, 0), 20);
        assertThrows(IllegalArgumentException.class, () -> PonderSceneRuntime.computeBounds(outOfRange));

        PonderScene arithmeticOverflow = sceneWithBlocks(BlockPos.ORIGIN);
        rotate(arithmeticOverflow, BlockPos.ORIGIN,
            new Vec3d(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE), new Vec3d(0, 360, 0), 20);
        assertThrows(IllegalArgumentException.class, () -> PonderSceneRuntime.computeBounds(arithmeticOverflow));
    }

    @Test
    void realSectionsSceneKeepsItsMultiBlockHelicopterInsideTheFixedEnvelope() throws Exception {
        ResourceLocation schematic = Ponder.asResource("ui_test/scene_8");
        PonderStoryBoardEntry entry = new PonderStoryBoardEntry(DebugScenes::sectionsScene, Ponder.MOD_ID,
            schematic, new ResourceLocation("minecraft", "obsidian"));
        PonderScene scene = PonderSceneRegistry.compileScene(new PonderLocalization(), entry);
        assertFalse(scene.getSchematic().isEmpty());
        assertEquals(Ponder.asResource("debug_sections"), scene.getSceneId());
        Set<BlockPos> movingBlocks = new LinkedHashSet<BlockPos>();
        boolean helicopterRotation = false;
        boolean helicopterTranslation = false;
        int lastMotionTick = 0;
        for (WorldEvent event : scene.getWorldEvents()) {
            if (event.getType() != WorldEventType.MOVE_SECTION
                && event.getType() != WorldEventType.ROTATE_SECTION) {
                continue;
            }
            movingBlocks.addAll(event.getPositions());
            lastMotionTick = Math.max(lastMotionTick, event.getTick() + event.getDuration());
            if (event.getRotation() != null) {
                helicopterRotation |= event.getRotation().x == 50.0D && event.getRotation().y == 1800.0D;
            }
            if (event.getOffset() != null) {
                helicopterTranslation |= event.getOffset().y == 4.0D && event.getOffset().z == 5.0D;
            }
        }
        assertTrue(helicopterRotation);
        assertTrue(helicopterTranslation);
        assertTrue(movingBlocks.size() > 1);
        assertEnclosesFrames(scene, movingBlocks, 0,
            Math.max(scene.getTotalIdleTicks(), lastMotionTick) + 20, 0.25F);
    }

    private static void assertEnclosesFrames(PonderScene scene, Set<BlockPos> positions,
        float firstTick, float lastTick, float step) throws Exception {
        PonderScenePreview.PreviewBounds bounds = PonderSceneRuntime.computeBounds(scene);
        Method transform = transformMethod();
        for (float tick = firstTick; tick <= lastTick; tick += step) {
            assertFrame(scene, positions, bounds, transform, tick);
        }
    }

    private static void assertFrame(PonderScene scene, Set<BlockPos> positions,
        PonderScenePreview.PreviewBounds bounds, Method transform, float tick) throws Exception {
        PonderSceneRuntime.RuntimeState runtime = PonderSceneRuntime.buildState(scene, tick);
        for (BlockPos position : positions) {
            PonderSceneRuntime.RuntimeBlockState state = runtime.blocksByPosition.get(position);
            if (state == null) {
                continue;
            }
            for (int x = 0; x <= 1; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = 0; z <= 1; z++) {
                        Vec3d corner = new Vec3d(position).add(x, y, z);
                        Vec3d actual = (Vec3d) transform.invoke(null, state, corner);
                        String message = "tick " + tick + ", block " + position + ", corner " + corner
                            + ", rendered " + actual;
                        assertTrue(actual.x >= bounds.minX - ROUNDING_TOLERANCE
                            && actual.x <= (double) bounds.maxX + 1 + ROUNDING_TOLERANCE, "x: " + message);
                        assertTrue(actual.y >= bounds.minY - ROUNDING_TOLERANCE
                            && actual.y <= (double) bounds.maxY + 1 + ROUNDING_TOLERANCE, "y: " + message);
                        assertTrue(actual.z >= bounds.minZ - ROUNDING_TOLERANCE
                            && actual.z <= (double) bounds.maxZ + 1 + ROUNDING_TOLERANCE, "z: " + message);
                    }
                }
            }
        }
    }

    private static Method transformMethod() throws Exception {
        Method transform = PonderSceneRuntime.class.getDeclaredMethod("applyTransformsToPoint",
            PonderSceneRuntime.RuntimeBlockState.class, Vec3d.class);
        transform.setAccessible(true);
        return transform;
    }

    private static PonderScene sceneWithBlocks(BlockPos... positions) throws Exception {
        ResourceLocation id = new ResourceLocation("ponder_test", "motion_bounds");
        PonderScene scene = new PonderScene(new PonderStoryBoardEntry(null, "ponder_test", id, id),
            new PonderLocalization());
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<BlockPos, IBlockState>();
        for (BlockPos position : positions) {
            blocks.put(position, Blocks.STONE.getDefaultState());
        }
        Constructor<PonderSchematic> constructor = PonderSchematic.class.getDeclaredConstructor(BlockPos.class,
            Map.class);
        constructor.setAccessible(true);
        scene.setSchematic(constructor.newInstance(new BlockPos(1, 1, 1), blocks));
        scene.recordWorldEvent(WorldEventType.SHOW_SECTION, Arrays.asList(positions), null);
        return scene;
    }

    private static void move(PonderScene scene, BlockPos block, Vec3d offset, int duration) {
        scene.recordSectionMove(Collections.singleton(block), offset, duration);
    }

    private static void rotate(PonderScene scene, BlockPos block, Vec3d pivot, Vec3d angles, int duration) {
        scene.recordSectionRotation(Collections.singleton(block), pivot, angles, duration);
    }

    private static void assertBounds(PonderScenePreview.PreviewBounds bounds, int minX, int maxX,
        int minY, int maxY, int minZ, int maxZ) {
        assertEquals(minX, bounds.minX, "minX");
        assertEquals(maxX, bounds.maxX, "maxX");
        assertEquals(minY, bounds.minY, "minY");
        assertEquals(maxY, bounds.maxY, "maxY");
        assertEquals(minZ, bounds.minZ, "minZ");
        assertEquals(maxZ, bounds.maxZ, "maxZ");
    }
}
