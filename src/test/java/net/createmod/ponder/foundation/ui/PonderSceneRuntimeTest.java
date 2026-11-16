package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSceneBuilder;
import net.createmod.ponder.foundation.PonderScene.WorldEventType;
import net.createmod.ponder.foundation.PonderSchematic;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.SelectionImpl;
import net.createmod.ponder.foundation.content.MmcePonderPlugin;
import net.createmod.ponder.foundation.content.mmce.MmceMachineDescriptor;
import net.createmod.ponder.foundation.content.mmce.MmceStructureSnapshot;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PonderSceneRuntimeTest {

    @BeforeAll
    static void registerBlocks() {
        Bootstrap.register();
    }

    @Test
    void initialTileDataAndFirstBuilderPatchAreRetainedWithoutMutatingTheStructure() throws Exception {
        PonderScene scene = chestScene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        builder.idle(1);
        builder.world().modifyBlockEntityNBT(SelectionImpl.fromTo(BlockPos.ORIGIN, BlockPos.ORIGIN),
            TileEntityChest.class, tag -> tag.setString("Patched", "yes"));

        NBTTagCompound before = tileData(blockAt(scene, 0));
        NBTTagCompound after = tileData(blockAt(scene, 1));
        assertEquals("Original chest", before.getString("CustomName"));
        assertFalse(before.hasKey("Patched"));
        assertEquals("Original chest", after.getString("CustomName"));
        assertEquals("yes", after.getString("Patched"));
        after.setString("CustomName", "Local copy");
        assertEquals("Original chest", tileData(blockAt(scene, 1)).getString("CustomName"));
        assertFalse(scene.getSchematic().getBlockEntityNbt(BlockPos.ORIGIN).hasKey("Patched"));
    }

    @Test
    void explicitSetsAndDestructionClearTileDataEvenForTheSameBlockType() {
        for (WorldEventType type : new WorldEventType[] { WorldEventType.SET_BLOCK,
            WorldEventType.SET_BLOCKS, WorldEventType.DESTROY_BLOCK, WorldEventType.REPLACE_BLOCKS }) {
            PonderScene scene = chestScene();
            scene.recordWorldEvent(type, BlockPos.ORIGIN,
                type == WorldEventType.REPLACE_BLOCKS ? "minecraft:air#0" : "minecraft:chest#0");
            assertNull(blockAt(scene, 0).tileNbt, type.toString());
        }
    }

    @Test
    void sameBlockReplacementKeepsTileDataButChangingItsBlockIdentityDoesNot() throws Exception {
        PonderScene scene = chestScene();
        scene.recordWorldEvent(WorldEventType.REPLACE_BLOCKS, BlockPos.ORIGIN, "minecraft:chest#3");
        assertEquals("Original chest", tileData(blockAt(scene, 0)).getString("CustomName"));
        scene.addIdleTicks(1);
        scene.recordWorldEvent(WorldEventType.REPLACE_BLOCKS, BlockPos.ORIGIN, "minecraft:furnace#2");
        assertNull(blockAt(scene, 1).tileNbt);
    }

    @Test
    void restoreRecoversOriginalTileDataAndRewindKeepsTheOriginalPatchSequence() throws Exception {
        PonderScene scene = chestScene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        builder.idle(1);
        builder.world().modifyBlockEntityNBT(SelectionImpl.fromTo(BlockPos.ORIGIN, BlockPos.ORIGIN),
            TileEntityChest.class, tag -> tag.setString("Patched", "discard"));
        builder.idle(1);
        builder.world().destroyBlock(BlockPos.ORIGIN);
        builder.idle(1);
        builder.world().restoreBlocks(SelectionImpl.fromTo(BlockPos.ORIGIN, BlockPos.ORIGIN));
        assertEquals("Original chest", tileData(blockAt(scene, 3)).getString("CustomName"));
        assertFalse(tileData(blockAt(scene, 3)).hasKey("Patched"));
        assertNull(blockAt(scene, 2).tileNbt);
        assertEquals("discard", tileData(blockAt(scene, 1)).getString("Patched"));
        assertFalse(tileData(blockAt(scene, 0)).hasKey("Patched"));
        assertFalse(scene.getSchematic().getBlockEntityNbt(BlockPos.ORIGIN).hasKey("Patched"));
    }

    @Test
    void restoringAnAddedBlockOrAnAirOriginalClearsTileData() {
        for (boolean airOriginal : new boolean[] { false, true }) {
            PonderScene scene = emptyScene();
            if (airOriginal) {
                scene.setSchematic(PonderSchematic.of(new BlockPos(1, 1, 1),
                    Collections.singletonMap(BlockPos.ORIGIN, Blocks.AIR.getDefaultState()),
                    Collections.<BlockPos, NBTTagCompound>emptyMap()));
            }
            scene.recordWorldEvent(WorldEventType.SET_BLOCK, BlockPos.ORIGIN, "minecraft:chest#0");
            scene.recordWorldEvent(WorldEventType.APPLY_NBT, BlockPos.ORIGIN, "{CustomName:\"Temporary\"}");
            scene.addIdleTicks(1);
            scene.recordWorldEvent(WorldEventType.RESTORE_BLOCKS, BlockPos.ORIGIN, null);
            assertNull(blockAt(scene, 1).tileNbt);
            assertFalse(blockAt(scene, 1).visible);
        }
    }

    private static PonderScene chestScene() {
        PonderScene scene = emptyScene();
        NBTTagCompound data = new NBTTagCompound();
        data.setString("id", "minecraft:chest");
        data.setString("CustomName", "Original chest");
        scene.setSchematic(PonderSchematic.of(new BlockPos(1, 1, 1),
            Collections.singletonMap(BlockPos.ORIGIN, Blocks.CHEST.getDefaultState()),
            Collections.singletonMap(BlockPos.ORIGIN, data)));
        return scene;
    }

    private static NBTTagCompound tileData(PonderSceneRuntime.RuntimeBlockState state) throws Exception {
        return JsonToNBT.getTagFromJson(state.tileNbt);
    }

    @Test
    void dynamicallyCreatedBlocksSurviveHiddenLayerReveal() {
        for (WorldEventType type : blockWrites()) {
            for (EnumFacing direction : new EnumFacing[] { null, EnumFacing.UP }) {
                PonderScene scene = emptyScene();
                scene.recordWorldEvent(type, BlockPos.ORIGIN, "minecraft:stone#0");
                hideThenShow(scene, direction);

                assertFalse(blockAt(scene, 16).visible);
                PonderSceneRuntime.RuntimeBlockState revealing = blockAt(scene, 17);
                assertNull(revealing.originalState);
                assertSame(Blocks.STONE.getDefaultState(), revealing.currentState);
                assertTrue(revealing.visible);
                assertTrue(revealing.fade > 0.0F);

                PonderSceneRuntime.RuntimeState completed = PonderSceneRuntime.buildState(scene, 32);
                assertEquals(1, completed.visibleBlocks);
                assertEquals(1.0F, completed.blocksByPosition.get(BlockPos.ORIGIN).fade);
            }
        }
    }

    @Test
    void replacedSchematicBlockKeepsItsReplacementWhenShown() throws Exception {
        PonderScene scene = stoneScene();
        scene.recordWorldEvent(WorldEventType.REPLACE_BLOCKS, BlockPos.ORIGIN, "minecraft:gold_block#0");
        hideThenShow(scene, EnumFacing.NORTH);

        PonderSceneRuntime.RuntimeBlockState block = blockAt(scene, 32);
        assertSame(Blocks.STONE.getDefaultState(), block.originalState);
        assertSame(Blocks.GOLD_BLOCK.getDefaultState(), block.currentState);
        assertEquals("minecraft:gold_block#0", block.stateDescription);
        assertTrue(block.visible);
    }

    @Test
    void explicitRestoreRecoversTheOriginalAfterReplacementAndDestruction() throws Exception {
        PonderScene scene = stoneScene();
        scene.recordWorldEvent(WorldEventType.REPLACE_BLOCKS, BlockPos.ORIGIN, "minecraft:gold_block#0");
        scene.addIdleTicks(1);
        scene.recordWorldEvent(WorldEventType.DESTROY_BLOCK, BlockPos.ORIGIN, null);
        scene.addIdleTicks(1);
        scene.recordWorldEvent(WorldEventType.RESTORE_BLOCKS, BlockPos.ORIGIN, null);

        PonderSceneRuntime.RuntimeBlockState block = blockAt(scene, 2);
        assertFalse(block.destroyed);
        assertTrue(block.visible);
        assertSame(Blocks.STONE.getDefaultState(), block.currentState);
        assertEquals(1.0F, block.fade);
    }

    @Test
    void restoringDynamicallyCreatedBlockClearsItInsteadOfMakingAnOriginal() {
        PonderScene scene = emptyScene();
        scene.recordWorldEvent(WorldEventType.SET_BLOCK, BlockPos.ORIGIN, "minecraft:stone#0");
        scene.addIdleTicks(1);
        scene.recordWorldEvent(WorldEventType.RESTORE_BLOCKS, BlockPos.ORIGIN, null);
        scene.addIdleTicks(1);
        scene.recordWorldEvent(WorldEventType.SHOW_SECTION, BlockPos.ORIGIN, null, EnumFacing.UP);

        PonderSceneRuntime.RuntimeBlockState block = blockAt(scene, 20);
        assertNull(block.originalState);
        assertNull(block.currentState);
        assertFalse(block.visible);
        assertEquals(0.0F, block.fade);
    }

    @Test
    void showDoesNotResurrectDestroyedBlocks() throws Exception {
        PonderScene scene = stoneScene();
        scene.recordWorldEvent(WorldEventType.DESTROY_BLOCK, BlockPos.ORIGIN, null);
        scene.addIdleTicks(1);
        scene.recordWorldEvent(WorldEventType.SHOW_SECTION, BlockPos.ORIGIN, null, EnumFacing.UP);

        PonderSceneRuntime.RuntimeBlockState block = blockAt(scene, 20);
        assertTrue(block.destroyed);
        assertFalse(block.visible);
        assertEquals(0.0F, block.fade);
        assertEquals(0, PonderSceneRuntime.buildState(scene, 20).visibleBlocks);
    }

    @Test
    void showingAnAirReplacementDoesNotRestoreTheSchematic() throws Exception {
        PonderScene scene = stoneScene();
        scene.recordWorldEvent(WorldEventType.REPLACE_BLOCKS, BlockPos.ORIGIN, "minecraft:air#0");
        hideThenShow(scene, EnumFacing.UP);

        PonderSceneRuntime.RuntimeBlockState block = blockAt(scene, 32);
        assertSame(Blocks.AIR.getDefaultState(), block.currentState);
        assertFalse(block.visible);
        assertEquals(0.0F, block.fade);
    }

    @Test
    void nonGeometrySelectionsDoNotExpandBounds() throws Exception {
        for (WorldEventType type : new WorldEventType[] {
            WorldEventType.SHOW_SECTION, WorldEventType.HIDE_SECTION,
            WorldEventType.DESTROY_BLOCK, WorldEventType.BREAK_PROGRESS,
            WorldEventType.APPLY_NBT, WorldEventType.RESTORE_BLOCKS
        }) {
            PonderScene scene = stoneScene();
            scene.recordWorldEvent(type, Arrays.asList(BlockPos.ORIGIN,
                new BlockPos(-100, 20, 100), new BlockPos(100, -20, -100)), null);

            assertBounds(PonderSceneRuntime.computeBounds(scene), 0, 0, 0, 0, 0, 0);
        }
    }

    @Test
    void airAndUnavailableBlocksDoNotExpandBounds() throws Exception {
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<BlockPos, IBlockState>();
        blocks.put(BlockPos.ORIGIN, Blocks.STONE.getDefaultState());
        blocks.put(new BlockPos(100, 100, 100), Blocks.AIR.getDefaultState());
        PonderScene scene = sceneWithBlocks(blocks);
        for (WorldEventType type : blockWrites()) {
            scene.recordWorldEvent(type, new BlockPos(-100, -100, -100), "minecraft:air#0");
            scene.recordWorldEvent(type, new BlockPos(200, 200, 200), "ponder_missing:test");
        }

        assertBounds(PonderSceneRuntime.computeBounds(scene), 0, 0, 0, 0, 0, 0);
    }

    @Test
    void futureDynamicBlocksRemainInBoundsEvenAfterBeingRemoved() throws Exception {
        for (WorldEventType type : blockWrites()) {
            PonderScene scene = stoneScene();
            BlockPos later = new BlockPos(10, 6, -5);
            scene.addIdleTicks(50);
            scene.recordWorldEvent(type, later, "minecraft:gold_block#0");
            scene.addIdleTicks(50);
            scene.recordWorldEvent(type, later, "minecraft:air#0");

            PonderScenePreview.PreviewBounds bounds = PonderSceneRuntime.computeBounds(scene);
            assertBounds(bounds, 0, 10, 0, 6, -5, 0);
            assertSame(bounds, PonderSceneRuntime.computeBounds(scene));
        }
    }

    @Test
    void movementBoundsIncludeRealBlocksButNotEmptySelectionCells() throws Exception {
        PonderScene scene = stoneScene();
        scene.recordSectionMove(Arrays.asList(BlockPos.ORIGIN, new BlockPos(100, 100, 100)),
            new Vec3d(4, 2, -3), 20);

        assertBounds(PonderSceneRuntime.computeBounds(scene), 0, 4, 0, 2, -3, 0);
    }

    @Test
    void rotationBoundsIncludeRealCornersButNotEmptySelectionCells() throws Exception {
        PonderScene scene = stoneScene();
        scene.recordSectionRotation(Arrays.asList(BlockPos.ORIGIN, new BlockPos(100, 100, 100)),
            new Vec3d(0.5D, 0.5D, 0.5D), new Vec3d(0, 90, 0), 20);

        assertBounds(PonderSceneRuntime.computeBounds(scene), -1, 1, 0, 0, -1, 1);
    }

    @Test
    void fullRotationBoundsContainEveryRenderedCenterBetweenTheOldSamples() throws Exception {
        BlockPos rotating = new BlockPos(10, 0, 0);
        PonderScene scene = sceneWithBlocks(Collections.singletonMap(rotating, Blocks.STONE.getDefaultState()));
        scene.recordSectionRotation(Collections.singletonList(rotating), Vec3d.ZERO, new Vec3d(0, 360, 0), 360);

        PonderScenePreview.PreviewBounds bounds = PonderSceneRuntime.computeBounds(scene);
        for (float tick = 0; tick <= 360; tick += 0.25F) {
            PonderSceneRuntime.RuntimeBlockState block =
                PonderSceneRuntime.buildState(scene, tick).blocksByPosition.get(rotating);
            assertTrue(block.renderCenterX >= bounds.minX && block.renderCenterX <= bounds.maxX + 1,
                "x outside fixed bounds at tick " + tick + ": " + block.renderCenterX);
            assertTrue(block.renderCenterY >= bounds.minY && block.renderCenterY <= bounds.maxY + 1,
                "y outside fixed bounds at tick " + tick + ": " + block.renderCenterY);
            assertTrue(block.renderCenterZ >= bounds.minZ && block.renderCenterZ <= bounds.maxZ + 1,
                "z outside fixed bounds at tick " + tick + ": " + block.renderCenterZ);
        }
    }

    @Test
    void emptyScenesUseBaseplateBoundsInsteadOfEmptySelections() {
        PonderScene scene = emptyScene();
        scene.setBasePlateOffsetX(-3);
        scene.setBasePlateOffsetZ(8);
        scene.setBasePlateSize(7);
        BlockPos nonexistent = new BlockPos(100, 100, 100);
        scene.recordWorldEvent(WorldEventType.SHOW_SECTION, nonexistent, null);
        scene.recordWorldEvent(WorldEventType.SET_BLOCK, nonexistent, "minecraft:air#0");
        scene.recordSectionMove(Collections.singletonList(nonexistent), new Vec3d(10, 10, 10), 20);

        assertBounds(PonderSceneRuntime.computeBounds(scene), -3, 3, 0, 0, 8, 14);
    }

    @Test
    @SuppressWarnings("unchecked")
    void controllerSelectionBoundsDoNotShrinkTheStructureView() throws Exception {
        Constructor<MmceMachineDescriptor> descriptorConstructor =
            MmceMachineDescriptor.class.getDeclaredConstructor(ResourceLocation.class, String.class,
                int.class, List.class, boolean.class, AxisAlignedBB.class, List.class);
        descriptorConstructor.setAccessible(true);
        MmceMachineDescriptor descriptor = descriptorConstructor.newInstance(
            new ResourceLocation("ponder_test", "large_controller"), "Large selection box", 1,
            Collections.emptyList(), false, new AxisAlignedBB(-128, -128, -128, 128, 128, 128),
            Collections.emptyList());
        Constructor<MmceStructureSnapshot> snapshotConstructor =
            MmceStructureSnapshot.class.getDeclaredConstructor(MmceMachineDescriptor.class,
                MmceStructureSnapshot.Variant.class, List.class, BlockPos.class, BlockPos.class,
                List.class, long.class, long.class);
        snapshotConstructor.setAccessible(true);
        MmceStructureSnapshot snapshot = snapshotConstructor.newInstance(descriptor,
            MmceStructureSnapshot.Variant.MINIMUM, Collections.emptyList(), BlockPos.ORIGIN,
            new BlockPos(1, 1, 1), Collections.emptyList(), 0L, 0L);
        Field snapshotsField = MmcePonderPlugin.class.getDeclaredField("SCENE_SNAPSHOTS");
        snapshotsField.setAccessible(true);
        Map<PonderScene, MmceStructureSnapshot> snapshots =
            (Map<PonderScene, MmceStructureSnapshot>) snapshotsField.get(null);
        PonderScene scene = stoneScene();
        snapshots.put(scene, snapshot);
        try {
            assertSame(snapshot, MmcePonderPlugin.getSceneSnapshot(scene));
            assertBounds(PonderSceneRuntime.computeBounds(scene), 0, 0, 0, 0, 0, 0);
        } finally {
            snapshots.remove(scene);
        }
    }

    private static WorldEventType[] blockWrites() {
        return new WorldEventType[] {
            WorldEventType.SET_BLOCK, WorldEventType.SET_BLOCKS, WorldEventType.REPLACE_BLOCKS
        };
    }

    private static void hideThenShow(PonderScene scene, EnumFacing direction) {
        scene.addIdleTicks(1);
        scene.recordWorldEvent(WorldEventType.HIDE_SECTION, BlockPos.ORIGIN, null, EnumFacing.DOWN);
        scene.addIdleTicks(16);
        scene.recordWorldEvent(WorldEventType.SHOW_SECTION, BlockPos.ORIGIN, null, direction);
    }

    private static PonderSceneRuntime.RuntimeBlockState blockAt(PonderScene scene, int tick) {
        return PonderSceneRuntime.buildState(scene, tick).blocksByPosition.get(BlockPos.ORIGIN);
    }

    private static PonderScene emptyScene() {
        ResourceLocation id = new ResourceLocation("ponder_test", "runtime");
        return new PonderScene(new PonderStoryBoardEntry(null, "ponder_test", id, id), new PonderLocalization());
    }

    private static PonderScene stoneScene() throws Exception {
        return sceneWithBlocks(Collections.singletonMap(BlockPos.ORIGIN, Blocks.STONE.getDefaultState()));
    }

    private static PonderScene sceneWithBlocks(Map<BlockPos, IBlockState> blocks) throws Exception {
        Constructor<PonderSchematic> constructor = PonderSchematic.class.getDeclaredConstructor(BlockPos.class,
            Map.class);
        constructor.setAccessible(true);
        PonderScene scene = emptyScene();
        scene.setSchematic(constructor.newInstance(new BlockPos(1, 1, 1),
            new LinkedHashMap<BlockPos, IBlockState>(blocks)));
        return scene;
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
