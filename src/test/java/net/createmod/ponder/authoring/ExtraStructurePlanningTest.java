package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.createmod.ponder.foundation.PonderSchematic;
import net.minecraft.block.BlockRail;
import net.minecraft.block.BlockRailBase.EnumRailDirection;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExtraStructurePlanningTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void quarterTurnsRotatePositionsAndNormalizeActualMinimumToRequestedBase() {
        PonderSchematic source = source(new BlockPos(4, 3, 4), new BlockPos(1, 1, 1), new BlockPos(3, 2, 2));
        List<List<BlockPos>> expected = Arrays.asList(
            Arrays.asList(new BlockPos(10, 5, 20), new BlockPos(12, 6, 21)),
            Arrays.asList(new BlockPos(11, 5, 20), new BlockPos(10, 6, 22)),
            Arrays.asList(new BlockPos(12, 5, 21), new BlockPos(10, 6, 20)),
            Arrays.asList(new BlockPos(10, 5, 22), new BlockPos(11, 6, 20)));
        for (int turn = 0; turn < 4; turn++) {
            JsonObject step = step(new BlockPos(10, 5, 20)); step.addProperty("rotation", turn * 90);
            assertEquals(expected.get(turn), ExtraStructurePlanning.plan(source, step).getPositions());
        }
    }

    @Test void rotationNormalizesNegativeAndRepeatedTurnsButNeverSilentlyDropsAnUnknownAngle() {
        assertEquals(Rotation.COUNTERCLOCKWISE_90, ExtraStructurePlanning.rotation(-90));
        assertEquals(Rotation.COUNTERCLOCKWISE_90, ExtraStructurePlanning.rotation(-450));
        assertEquals(Rotation.CLOCKWISE_90, ExtraStructurePlanning.rotation(810));
        assertEquals(Rotation.NONE, ExtraStructurePlanning.rotation(720));
        for (double angle : new double[] {45, 90.5, Double.NaN, Double.POSITIVE_INFINITY, 1000080})
            assertThrows(IllegalArgumentException.class, () -> ExtraStructurePlanning.rotation(angle));
    }

    @Test void nativeBlockStateRotationUpdatesStairsAndStraightCurvedAndAscendingRails() {
        IBlockState stair = Blocks.STONE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.NORTH);
        assertEquals(EnumFacing.EAST, rotated(stair).getValue(BlockStairs.FACING));
        assertEquals(EnumRailDirection.EAST_WEST,
            rotated(Blocks.RAIL.getDefaultState().withProperty(BlockRail.SHAPE, EnumRailDirection.NORTH_SOUTH)).getValue(BlockRail.SHAPE));
        assertEquals(EnumRailDirection.SOUTH_WEST,
            rotated(Blocks.RAIL.getDefaultState().withProperty(BlockRail.SHAPE, EnumRailDirection.SOUTH_EAST)).getValue(BlockRail.SHAPE));
        assertEquals(EnumRailDirection.ASCENDING_SOUTH,
            rotated(Blocks.RAIL.getDefaultState().withProperty(BlockRail.SHAPE, EnumRailDirection.ASCENDING_EAST)).getValue(BlockRail.SHAPE));
    }

    @Test void defaultPlacementSkipsAirAndStructureVoidBeforeComputingMinimum() {
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<>();
        blocks.put(BlockPos.ORIGIN, Blocks.AIR.getDefaultState());
        blocks.put(new BlockPos(1, 0, 0), Blocks.STRUCTURE_VOID.getDefaultState());
        blocks.put(new BlockPos(2, 1, 2), Blocks.STONE.getDefaultState());
        PonderSchematic source = PonderSchematic.of(new BlockPos(3, 2, 3), blocks, Collections.emptyMap());
        ExtraStructurePlanning.Plan plan = ExtraStructurePlanning.plan(source, step(new BlockPos(7, 8, 9)));
        assertEquals(Collections.singletonList(new BlockPos(7, 8, 9)), plan.getPositions());
        assertSame(Blocks.STONE, plan.getPlacements().get(0).getState().getBlock());
        assertTrue(plan.isPlaceVisible()); assertTrue(plan.shouldSpawnParticles());
    }

    @Test void replaceAirExpandsWholeDeclaredSizeIncludingMissingCellsAndStructureVoid() {
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<>();
        blocks.put(new BlockPos(1, 0, 1), Blocks.STONE.getDefaultState());
        blocks.put(new BlockPos(2, 0, 0), Blocks.STRUCTURE_VOID.getDefaultState());
        PonderSchematic source = PonderSchematic.of(new BlockPos(3, 1, 2), blocks, Collections.emptyMap());
        JsonObject step = step(new BlockPos(10, 3, 20)); step.addProperty("replaceAir", true);
        ExtraStructurePlanning.Plan plan = ExtraStructurePlanning.plan(source, step);
        assertEquals(6, plan.getPlacements().size());
        assertEquals(5, plan.getPlacements().stream().filter(block -> block.getState().getBlock() == Blocks.AIR).count());
        assertSame(Blocks.STONE, at(plan, new BlockPos(11, 3, 21)).getState().getBlock());
        assertSame(Blocks.AIR, at(plan, new BlockPos(12, 3, 20)).getState().getBlock());
    }

    @Test void replaceAirUsesRotatedDeclaredBoxNotOnlyNonAirMinimum() {
        PonderSchematic source = source(new BlockPos(3, 1, 2), new BlockPos(1, 0, 1));
        JsonObject step = step(BlockPos.ORIGIN); step.addProperty("replaceAir", true); step.addProperty("rotation", 90);
        ExtraStructurePlanning.Plan plan = ExtraStructurePlanning.plan(source, step);
        assertEquals(6, plan.getPositions().size());
        assertSame(Blocks.STONE, at(plan, new BlockPos(0, 0, 1)).getState().getBlock());
        assertTrue(plan.getPositions().contains(new BlockPos(1, 0, 2)));
    }

    @Test void blockEntityCoordinatesAreRelocatedOnIndependentDeepCopies() {
        BlockPos old = new BlockPos(2, 1, 3); NBTTagCompound tile = new NBTTagCompound();
        tile.setString("id", "minecraft:chest"); tile.setInteger("x", 999); tile.setInteger("y", 888); tile.setInteger("z", 777);
        NBTTagCompound nested = new NBTTagCompound(); nested.setString("name", "original"); tile.setTag("custom", nested);
        PonderSchematic source = PonderSchematic.of(new BlockPos(4, 2, 4),
            Collections.singletonMap(old, Blocks.CHEST.getDefaultState()), Collections.singletonMap(old, tile));
        JsonObject step = step(new BlockPos(10, 5, 20)); step.addProperty("rotation", 90);
        ExtraStructurePlanning.PlacedBlock block = ExtraStructurePlanning.plan(source, step).getPlacements().get(0);
        NBTTagCompound copied = block.getNbtCopy();
        assertEquals(10, copied.getInteger("x")); assertEquals(5, copied.getInteger("y")); assertEquals(20, copied.getInteger("z"));
        assertEquals("minecraft:chest", copied.getString("id"));
        copied.getCompoundTag("custom").setString("name", "changed"); copied.setInteger("x", -1);
        assertEquals("original", block.getNbtCopy().getCompoundTag("custom").getString("name"));
        assertEquals(10, block.getNbtCopy().getInteger("x"));
        assertEquals(999, source.getBlockEntityNbt(old).getInteger("x"));
        assertEquals("original", tile.getCompoundTag("custom").getString("name"));
    }

    @Test void replacingAirDoesNotResurrectTileNbtFromAirCells() {
        NBTTagCompound stale = new NBTTagCompound(); stale.setString("id", "minecraft:chest");
        PonderSchematic source = PonderSchematic.of(new BlockPos(1, 1, 1),
            Collections.singletonMap(BlockPos.ORIGIN, Blocks.AIR.getDefaultState()), Collections.singletonMap(BlockPos.ORIGIN, stale));
        JsonObject step = step(BlockPos.ORIGIN); step.addProperty("replaceAir", true);
        assertNull(ExtraStructurePlanning.plan(source, step).getPlacements().get(0).getNbtCopy());
    }

    @Test void noneHonorsImmediateDisplayAndParticlesWithoutInventingEntranceGroups() {
        PonderSchematic source = source(new BlockPos(1, 1, 1), BlockPos.ORIGIN);
        JsonObject step = step(BlockPos.ORIGIN); step.addProperty("immediateDisplay", false);
        ExtraStructurePlanning.Plan hidden = ExtraStructurePlanning.plan(source, step);
        assertFalse(hidden.isAnimated()); assertFalse(hidden.isPlaceVisible()); assertFalse(hidden.shouldSpawnParticles());
        assertTrue(hidden.getEntranceGroups().isEmpty()); assertEquals(0, hidden.getTimelineAdvance());
        step.addProperty("immediateDisplay", true); step.addProperty("spawnParticles", false);
        assertTrue(ExtraStructurePlanning.plan(source, step).isPlaceVisible());
        assertFalse(ExtraStructurePlanning.plan(source, step).shouldSpawnParticles());
    }

    @Test void simultaneousStartsAllIndependentStripsAtZeroAndDoesNotBridgeAirHoles() {
        PonderSchematic source = source(new BlockPos(4, 2, 2),
            new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(3, 0, 0), new BlockPos(0, 1, 1));
        JsonObject step = step(BlockPos.ORIGIN); step.addProperty("entranceAnimation", "simultaneous");
        step.addProperty("entranceDuration", 7); step.addProperty("entranceInterval", 999); step.addProperty("direction", "west");
        ExtraStructurePlanning.Plan plan = ExtraStructurePlanning.plan(source, step);
        assertTrue(plan.isAnimated()); assertFalse(plan.isPlaceVisible()); assertFalse(plan.shouldSpawnParticles());
        assertEquals(3, plan.getEntranceGroups().size()); assertEquals(0, plan.getTimelineAdvance());
        for (ExtraStructurePlanning.EntranceGroup group : plan.getEntranceGroups()) {
            assertEquals(0, group.getStartOffset()); assertEquals(7, group.getDuration()); assertEquals(EnumFacing.WEST, group.getDirection());
        }
        assertEquals(Arrays.asList(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)), plan.getEntranceGroups().get(0).getPositions());
        assertPartition(plan);
    }

    @Test void allSixDirectionalEntrancesHaveStableLayerOrderAndTimedStripOffsets() {
        PonderSchematic source = cube();
        for (String entrance : new String[] {"up", "down", "north", "south", "east", "west"}) {
            JsonObject step = step(BlockPos.ORIGIN); step.addProperty("entranceAnimation", entrance);
            step.addProperty("entranceDuration", 3); step.addProperty("entranceInterval", 2);
            ExtraStructurePlanning.Plan plan = ExtraStructurePlanning.plan(source, step);
            assertEquals(4, plan.getEntranceGroups().size()); assertEquals(8, plan.getTimelineAdvance());
            int axis = entrance.equals("east") || entrance.equals("west") ? 0 : entrance.equals("north") || entrance.equals("south") ? 2 : 1;
            boolean reverse = entrance.equals("west") || entrance.equals("north") || entrance.equals("down");
            assertEquals(reverse ? 1 : 0, axis(plan.getEntranceGroups().get(0).getPositions().get(0), axis));
            for (int i = 0; i < 4; i++) {
                assertEquals(i * 2, plan.getEntranceGroups().get(i).getStartOffset());
                assertEquals(3, plan.getEntranceGroups().get(i).getDuration());
                assertEquals(EnumFacing.DOWN, plan.getEntranceGroups().get(i).getDirection());
            }
            assertPartition(plan);
        }
    }

    @Test void entranceLayerOrderAndPerStripSlideDirectionAreIndependent() {
        JsonObject step = step(BlockPos.ORIGIN); step.addProperty("entranceAnimation", "east"); step.addProperty("direction", "north");
        ExtraStructurePlanning.Plan plan = ExtraStructurePlanning.plan(cube(), step);
        assertEquals(0, plan.getEntranceGroups().get(0).getPositions().get(0).getX());
        assertEquals(EnumFacing.NORTH, plan.getEntranceGroups().get(0).getDirection());
    }

    @Test void sparseGroupsExactlyPartitionSelectedPositionsAndNeverSpanMissingCells() {
        PonderSchematic source = source(new BlockPos(4, 2, 2),
            new BlockPos(0, 0, 0), new BlockPos(2, 0, 0), new BlockPos(3, 0, 0), new BlockPos(1, 1, 1));
        JsonObject step = step(BlockPos.ORIGIN); step.addProperty("entranceAnimation", "up");
        ExtraStructurePlanning.Plan plan = ExtraStructurePlanning.plan(source, step); assertPartition(plan);
        assertEquals(3, plan.getEntranceGroups().size());
        assertEquals(Collections.singletonList(BlockPos.ORIGIN), plan.getEntranceGroups().get(0).getPositions());
        assertEquals(Arrays.asList(new BlockPos(2, 0, 0), new BlockPos(3, 0, 0)), plan.getEntranceGroups().get(1).getPositions());
    }

    @Test void explicitUpstreamAliasesNormalizeButUnknownAnimationsFailClosed() {
        assertEquals("none", ExtraStructurePlanning.normalizeEntrance(null));
        assertEquals("none", ExtraStructurePlanning.normalizeEntrance(" (\u65e0) "));
        assertEquals("simultaneous", ExtraStructurePlanning.normalizeEntrance("\u540c\u65f6"));
        assertEquals("down", ExtraStructurePlanning.normalizeEntrance("top_to_bottom"));
        assertEquals("up", ExtraStructurePlanning.normalizeEntrance("bottom-up"));
        assertEquals("north", ExtraStructurePlanning.normalizeEntrance("south_to_north"));
        assertEquals("south", ExtraStructurePlanning.normalizeEntrance("\u4ece\u5317\u5230\u5357"));
        assertEquals("east", ExtraStructurePlanning.normalizeEntrance("west-east"));
        assertEquals("west", ExtraStructurePlanning.normalizeEntrance("east_to_west"));
        assertThrows(IllegalArgumentException.class, () -> ExtraStructurePlanning.normalizeEntrance("random"));
    }

    @Test void declaredAxisAndCoordinateBudgetsAreCheckedBeforeExpansion() {
        JsonObject step = step(BlockPos.ORIGIN); step.addProperty("replaceAir", true);
        assertThrows(IllegalArgumentException.class, () -> ExtraStructurePlanning.plan(source(new BlockPos(65, 1, 1), BlockPos.ORIGIN), step));
        assertThrows(IllegalArgumentException.class, () -> ExtraStructurePlanning.plan(source(new BlockPos(1, 1, 1), new BlockPos(-1, 0, 0)), step));
        assertThrows(IllegalArgumentException.class, () -> ExtraStructurePlanning.plan(source(new BlockPos(1, 1, 1), new BlockPos(1, 0, 0)), step));
        JsonObject edge = step(new BlockPos(30000000, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ExtraStructurePlanning.plan(source(new BlockPos(2, 1, 1), BlockPos.ORIGIN, new BlockPos(1, 0, 0)), edge));
        assertEquals(1, ExtraStructurePlanning.plan(source(new BlockPos(2, 1, 1), new BlockPos(1, 0, 0)), edge).getPlacements().size());
    }

    @Test void fullDeclaredAxisBoundaryRemainsSupportedAndRotatesWithoutTruncation() {
        PonderSchematic source = source(new BlockPos(64, 1, 1), new BlockPos(63, 0, 0));
        JsonObject step = step(BlockPos.ORIGIN); step.addProperty("replaceAir", true); step.addProperty("rotation", 90);
        ExtraStructurePlanning.Plan plan = ExtraStructurePlanning.plan(source, step);
        assertEquals(64, plan.getPlacements().size()); assertTrue(plan.getPositions().contains(new BlockPos(0, 0, 63)));
        assertSame(Blocks.STONE, at(plan, new BlockPos(0, 0, 63)).getState().getBlock());
    }

    @Test void entranceTimingUsesLongArithmeticAndCountsFinalUpstreamInterval() {
        PonderSchematic source = source(new BlockPos(1, 2, 1), BlockPos.ORIGIN, new BlockPos(0, 1, 0));
        JsonObject step = step(BlockPos.ORIGIN); step.addProperty("entranceAnimation", "up"); step.addProperty("entranceInterval", 72000);
        assertThrows(IllegalArgumentException.class, () -> ExtraStructurePlanning.plan(source, step));
        step.addProperty("entranceDuration", 0); step.addProperty("entranceInterval", 36000);
        ExtraStructurePlanning.Plan plan = ExtraStructurePlanning.plan(source, step);
        assertEquals(72000, plan.getTimelineAdvance()); assertEquals(36000, plan.getEntranceGroups().get(1).getStartOffset());
        step.addProperty("entranceInterval", 36001);
        assertThrows(IllegalArgumentException.class, () -> ExtraStructurePlanning.plan(source, step));
    }

    @Test void invalidFlagsDoNotHideBehindInactiveAnimatedOrInvisibleBranches() {
        PonderSchematic source = source(new BlockPos(1, 1, 1), BlockPos.ORIGIN);
        JsonObject step = step(BlockPos.ORIGIN); step.addProperty("entranceAnimation", "up"); step.addProperty("immediateDisplay", "yes");
        assertThrows(IllegalArgumentException.class, () -> ExtraStructurePlanning.plan(source, step));
        step.addProperty("immediateDisplay", false); step.addProperty("spawnParticles", "yes");
        assertThrows(IllegalArgumentException.class, () -> ExtraStructurePlanning.plan(source, step));
        step.addProperty("spawnParticles", true); step.addProperty("direction", "invalid");
        assertThrows(IllegalArgumentException.class, () -> ExtraStructurePlanning.plan(source, step));
        step.addProperty("direction", "down"); step.addProperty("entranceDuration", -1);
        assertThrows(IllegalArgumentException.class, () -> ExtraStructurePlanning.plan(source, step));
    }

    @Test void emptySourcesProduceNoPlacementsOrAnimationWork() {
        JsonObject step = step(BlockPos.ORIGIN); step.addProperty("replaceAir", true); step.addProperty("entranceAnimation", "up");
        ExtraStructurePlanning.Plan plan = ExtraStructurePlanning.plan(PonderSchematic.empty(), step);
        assertTrue(plan.getPlacements().isEmpty()); assertTrue(plan.getPositions().isEmpty());
        assertTrue(plan.getEntranceGroups().isEmpty()); assertEquals(0, plan.getTimelineAdvance());
    }

    @Test void returnedPlanCollectionsAndCoordinatesAreImmutableAndSmartDisplayRemainsExplicit() {
        JsonObject step = step(BlockPos.ORIGIN); step.addProperty("entranceAnimation", "up"); step.addProperty("smartDisplay", false);
        ExtraStructurePlanning.Plan plan = ExtraStructurePlanning.plan(cube(), step);
        assertFalse(plan.isSmartDisplay());
        assertThrows(UnsupportedOperationException.class, () -> plan.getPlacements().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.getPositions().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.getEntranceGroups().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.getEntranceGroups().get(0).getPositions().clear());
    }

    private static IBlockState rotated(IBlockState state) {
        PonderSchematic source = PonderSchematic.of(new BlockPos(1, 1, 1), Collections.singletonMap(BlockPos.ORIGIN, state), Collections.emptyMap());
        JsonObject step = step(BlockPos.ORIGIN); step.addProperty("rotation", 90);
        return ExtraStructurePlanning.plan(source, step).getPlacements().get(0).getState();
    }
    private static ExtraStructurePlanning.PlacedBlock at(ExtraStructurePlanning.Plan plan, BlockPos pos) {
        return plan.getPlacements().stream().filter(block -> block.getPos().equals(pos)).findFirst().orElseThrow(() -> new AssertionError("Missing " + pos));
    }
    private static PonderSchematic source(BlockPos size, BlockPos... positions) {
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<>(); for (BlockPos pos : positions) blocks.put(pos, Blocks.STONE.getDefaultState());
        return PonderSchematic.of(size, blocks, Collections.emptyMap());
    }
    private static PonderSchematic cube() {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) positions.add(new BlockPos(x, y, z));
        return source(new BlockPos(2, 2, 2), positions.toArray(new BlockPos[0]));
    }
    private static JsonObject step(BlockPos base) {
        JsonObject step = new JsonObject(); JsonArray pos = new JsonArray();
        pos.add(base.getX()); pos.add(base.getY()); pos.add(base.getZ()); step.add("blockPos", pos); return step;
    }
    private static int axis(BlockPos pos, int axis) { return axis == 0 ? pos.getX() : axis == 1 ? pos.getY() : pos.getZ(); }
    private static void assertPartition(ExtraStructurePlanning.Plan plan) {
        Set<BlockPos> unique = new HashSet<>();
        for (ExtraStructurePlanning.EntranceGroup group : plan.getEntranceGroups()) for (BlockPos pos : group.getPositions()) assertTrue(unique.add(pos));
        assertEquals(new HashSet<>(plan.getPositions()), unique);
    }
}
