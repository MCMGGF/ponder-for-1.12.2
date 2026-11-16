package net.createmod.ponder.foundation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.content.ComplexTestScenes;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockRedstoneRepeater;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RedstonePreviewStateTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void repeaterPairRoundTripsEveryFacingDelayAndLockedProperty() {
        for (Block block : Arrays.asList(Blocks.UNPOWERED_REPEATER, Blocks.POWERED_REPEATER)) {
            for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
                for (int delay = 1; delay <= 4; delay++) {
                    for (boolean locked : new boolean[] {false, true}) {
                        IBlockState original = block.getDefaultState()
                            .withProperty(BlockRedstoneRepeater.FACING, facing)
                            .withProperty(BlockRedstoneRepeater.DELAY, delay)
                            .withProperty(BlockRedstoneRepeater.LOCKED, locked);
                        IBlockState[] result = toggleTwice(original);
                        assertSame(block == Blocks.UNPOWERED_REPEATER ? Blocks.POWERED_REPEATER : Blocks.UNPOWERED_REPEATER,
                            result[0].getBlock());
                        assertEquals(facing, result[0].getValue(BlockRedstoneRepeater.FACING));
                        assertEquals(delay, result[0].getValue(BlockRedstoneRepeater.DELAY).intValue());
                        assertEquals(locked, result[0].getValue(BlockRedstoneRepeater.LOCKED).booleanValue());
                        assertEquals(original, result[1]);
                    }
                }
            }
        }
    }

    @Test void lampPairChangesTheBlockAndRoundTripsInBothDirections() {
        for (Block block : Arrays.asList(Blocks.REDSTONE_LAMP, Blocks.LIT_REDSTONE_LAMP)) {
            IBlockState[] result = toggleTwice(block.getDefaultState());
            assertSame(block == Blocks.REDSTONE_LAMP ? Blocks.LIT_REDSTONE_LAMP : Blocks.REDSTONE_LAMP,
                result[0].getBlock());
            assertEquals(block.getDefaultState(), result[1]);
        }
    }

    @Test void existingLeverAndWirePropertyTogglesStillRoundTrip() {
        IBlockState lever = Blocks.LEVER.getDefaultState().withProperty(BlockLever.POWERED, false);
        IBlockState[] leverResult = toggleTwice(lever);
        assertTrue(leverResult[0].getValue(BlockLever.POWERED));
        assertEquals(lever, leverResult[1]);
        IBlockState wire = Blocks.REDSTONE_WIRE.getDefaultState().withProperty(BlockRedstoneWire.POWER, 0);
        IBlockState[] wireResult = toggleTwice(wire);
        assertEquals(15, wireResult[0].getValue(BlockRedstoneWire.POWER).intValue());
        assertEquals(wire, wireResult[1]);
    }

    @Test void unrelatedFurnacesPistonsAndOrdinaryBlocksDoNotBecomeInventedRedstonePairs() {
        for (Block block : Arrays.asList(Blocks.STONE, Blocks.CHEST, Blocks.FURNACE, Blocks.LIT_FURNACE,
            Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.GLASS)) {
            IBlockState original = block.getDefaultState();
            IBlockState[] result = toggleTwice(original);
            assertEquals(original, result[0]);
            assertEquals(original, result[1]);
        }
    }

    @Test void complexMatrixInitializesWestInputWithoutChangingTemplateDelayOrStableId() {
        PonderScene scene = matrix();
        assertEquals(Ponder.asResource("complex_redstone_matrix"), scene.getSceneId());
        for (int z : new int[] {2, 4, 6}) {
            BlockPos pos = new BlockPos(4, 1, z);
            IBlockState original = scene.getSchematic().getBlocks().get(pos);
            IBlockState initialized = stateAt(scene, pos, 0);
            assertSame(Blocks.UNPOWERED_REPEATER, initialized.getBlock());
            assertEquals(EnumFacing.WEST, initialized.getValue(BlockRedstoneRepeater.FACING));
            assertEquals(pos.west(), pos.offset(initialized.getValue(BlockRedstoneRepeater.FACING)),
                "1.12 calculateInputStrength reads the neighboring block in FACING, toward the lever");
            assertEquals(original.getValue(BlockRedstoneRepeater.DELAY), initialized.getValue(BlockRedstoneRepeater.DELAY));
        }
    }

    @Test void allThreeMatrixRepeatersPowerOnAndOffWithTheirWireAndLamp() {
        PonderScene scene = matrix();
        List<Integer> starts = scene.getKeyframeTicks();
        assertEquals(3, starts.size());
        for (int index = 0; index < 3; index++) {
            int z = 2 + index * 2;
            int on = starts.get(index);
            int off = scene.getTotalIdleTicks() - 24 + index * 8;
            BlockPos repeater = new BlockPos(4, 1, z);
            BlockPos wire = new BlockPos(3, 1, z);
            BlockPos lamp = new BlockPos(7, 1, z);
            assertSame(Blocks.UNPOWERED_REPEATER, stateAt(scene, repeater, on - 1).getBlock());
            assertSame(Blocks.POWERED_REPEATER, stateAt(scene, repeater, on).getBlock());
            assertEquals(15, stateAt(scene, wire, on).getValue(BlockRedstoneWire.POWER).intValue());
            assertSame(Blocks.LIT_REDSTONE_LAMP, stateAt(scene, lamp, on).getBlock());
            assertSame(Blocks.POWERED_REPEATER, stateAt(scene, repeater, off - 1).getBlock());
            assertSame(Blocks.UNPOWERED_REPEATER, stateAt(scene, repeater, off).getBlock());
            assertEquals(0, stateAt(scene, wire, off).getValue(BlockRedstoneWire.POWER).intValue());
            assertSame(Blocks.REDSTONE_LAMP, stateAt(scene, lamp, off).getBlock());
        }
    }

    @Test void matrixDelayCyclesPreservePowerAndTheFinalValueSurvivesSwitchingOff() {
        PonderScene scene = matrix();
        BlockPos center = new BlockPos(4, 1, 4);
        int start = scene.getKeyframeTicks().get(2) + 24;
        int initial = scene.getSchematic().getBlocks().get(center).getValue(BlockRedstoneRepeater.DELAY);
        for (int cycle = 0; cycle < 3; cycle++) {
            IBlockState state = stateAt(scene, center, start + cycle * 12);
            assertSame(Blocks.POWERED_REPEATER, state.getBlock());
            assertEquals((initial + cycle) % 4 + 1, state.getValue(BlockRedstoneRepeater.DELAY).intValue());
            assertEquals(EnumFacing.WEST, state.getValue(BlockRedstoneRepeater.FACING));
        }
        IBlockState finalState = stateAt(scene, center, scene.getTotalIdleTicks());
        assertSame(Blocks.UNPOWERED_REPEATER, finalState.getBlock());
        assertEquals((initial + 2) % 4 + 1, finalState.getValue(BlockRedstoneRepeater.DELAY).intValue());
    }

    private static IBlockState[] toggleTwice(IBlockState original) {
        ResourceLocation id = new ResourceLocation("ponder_test", "redstone_preview");
        PonderScene scene = new PonderScene(new PonderStoryBoardEntry(null, "ponder_test", id, id), new PonderLocalization());
        scene.setSchematic(PonderSchematic.of(new BlockPos(1, 1, 1),
            Collections.singletonMap(BlockPos.ORIGIN, original), Collections.emptyMap()));
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        IBlockState[] states = new IBlockState[2];
        for (int index = 0; index < 2; index++) {
            builder.world().toggleRedstonePower(SelectionImpl.fromTo(BlockPos.ORIGIN, BlockPos.ORIGIN));
            final int target = index;
            builder.world().modifyBlock(BlockPos.ORIGIN, state -> { states[target] = state; return state; }, false);
        }
        assertEquals(original, scene.getSchematic().getBlocks().get(BlockPos.ORIGIN), "The original structure remains immutable");
        return states;
    }

    private static PonderScene matrix() {
        return PonderSceneRegistry.compileScene(new PonderLocalization(), new PonderStoryBoardEntry(
            ComplexTestScenes::redstoneMatrix, Ponder.MOD_ID, Ponder.asResource("complex/redstone_matrix"),
            new ResourceLocation("minecraft", "redstone")));
    }

    private static IBlockState stateAt(PonderScene scene, BlockPos position, int tick) {
        IBlockState result = scene.getSchematic().getBlocks().get(position);
        for (PonderScene.WorldEvent event : scene.getWorldEvents()) {
            if (event.getTick() > tick || !event.getPositions().contains(position)) continue;
            if (event.getType() == PonderScene.WorldEventType.SET_BLOCK
                || event.getType() == PonderScene.WorldEventType.SET_BLOCKS
                || event.getType() == PonderScene.WorldEventType.REPLACE_BLOCKS) {
                String[] encoded = event.getStateDescription().split("#", 2);
                Block block = Block.REGISTRY.getObject(new ResourceLocation(encoded[0]));
                assertNotNull(block);
                result = block.getStateFromMeta(encoded.length == 2 ? Integer.parseInt(encoded[1]) : 0);
            }
        }
        return result;
    }
}
