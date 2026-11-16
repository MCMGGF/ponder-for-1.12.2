package net.createmod.ponder.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PonderSceneBuilderNbtTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void firstPatchPreservesInitialMetadataWithoutMutatingTheSource() throws Exception {
        PonderScene scene = chestScene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        builder.world().modifyBlockEntityNBT(selection(), TileEntityChest.class, tag -> {
            assertEquals("Original chest", tag.getString("CustomName"));
            assertEquals(1, tag.getTagList("Items", 10).tagCount());
            tag.setString("Added", "first");
            tag.getTagList("Items", 10).getCompoundTagAt(0).setByte("Count", (byte) 2);
        });
        builder.world().modifyBlockEntityNBT(selection(), TileEntityChest.class, tag -> {
            assertEquals("first", tag.getString("Added"));
            tag.setString("Added", "second");
        });

        NBTTagCompound first = eventNbt(scene, 0);
        assertEquals("Original chest", first.getString("CustomName"));
        assertEquals("first", first.getString("Added"));
        assertEquals(2, first.getTagList("Items", 10).getCompoundTagAt(0).getByte("Count"));
        assertEquals("second", eventNbt(scene, 1).getString("Added"));
        NBTTagCompound original = scene.getSchematic().getBlockEntityNbt(BlockPos.ORIGIN);
        assertFalse(original.hasKey("Added"));
        assertEquals(1, original.getTagList("Items", 10).getCompoundTagAt(0).getByte("Count"));
    }

    @Test
    void explicitWritesAndDestructionDoNotReloadOriginalMetadata() throws Exception {
        for (int operation = 0; operation < 4; operation++) {
            PonderScene scene = chestScene();
            PonderSceneBuilder builder = new PonderSceneBuilder(scene);
            if (operation == 0) {
                builder.world().setBlock(BlockPos.ORIGIN, Blocks.CHEST.getDefaultState(), false);
            } else if (operation == 1) {
                builder.world().setBlocks(selection(), Blocks.CHEST.getDefaultState(), false);
            } else if (operation == 2) {
                builder.world().destroyBlock(BlockPos.ORIGIN);
            } else {
                builder.world().replaceBlocks(selection(), Blocks.STONE.getDefaultState(), false);
            }
            builder.world().modifyBlockEntityNBT(selection(), TileEntityChest.class,
                tag -> tag.setString("Fresh", "value"));
            NBTTagCompound updated = lastNbt(scene);
            assertEquals("value", updated.getString("Fresh"));
            assertFalse(updated.hasKey("CustomName"), "operation " + operation);
            assertFalse(updated.hasKey("Items"), "operation " + operation);
        }
    }

    @Test
    void sameBlockStateModificationKeepsInitialMetadata() throws Exception {
        PonderScene scene = chestScene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        builder.world().modifyBlock(BlockPos.ORIGIN, state -> state, false);
        builder.world().modifyBlockEntityNBT(selection(), TileEntityChest.class,
            tag -> tag.setString("Added", "value"));
        assertEquals("Original chest", lastNbt(scene).getString("CustomName"));
        assertTrue(lastNbt(scene).hasKey("Items"));
    }

    @Test
    void restoringUsesTheOriginalMetadataRatherThanThePreviousPatchOrTombstone() throws Exception {
        for (boolean destroy : new boolean[] {false, true}) {
            PonderScene scene = chestScene();
            PonderSceneBuilder builder = new PonderSceneBuilder(scene);
            builder.world().modifyBlockEntityNBT(selection(), TileEntityChest.class,
                tag -> tag.setString("PreviousPatch", "discard"));
            if (destroy) {
                builder.world().destroyBlock(BlockPos.ORIGIN);
            } else {
                builder.world().setBlock(BlockPos.ORIGIN, Blocks.CHEST.getDefaultState(), false);
            }
            builder.world().restoreBlocks(selection());
            builder.world().modifyBlockEntityNBT(selection(), TileEntityChest.class,
                tag -> tag.setString("RestoredPatch", "keep"));
            NBTTagCompound restored = lastNbt(scene);
            assertEquals("Original chest", restored.getString("CustomName"));
            assertTrue(restored.hasKey("Items"));
            assertFalse(restored.hasKey("PreviousPatch"));
            assertEquals("keep", restored.getString("RestoredPatch"));
        }
    }

    @Test
    void restoringAPositionAbsentFromTheSchematicDoesNotInventOriginalMetadata() throws Exception {
        PonderScene scene = chestScene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        BlockPos added = new BlockPos(2, 0, 0);
        builder.world().setBlock(added, Blocks.CHEST.getDefaultState(), false);
        builder.world().modifyBlockEntityNBT(SelectionImpl.fromTo(added, added), TileEntityChest.class,
            tag -> tag.setString("PreviousPatch", "discard"));
        builder.world().restoreBlocks(SelectionImpl.fromTo(added, added));
        builder.world().modifyBlockEntityNBT(SelectionImpl.fromTo(added, added), TileEntityChest.class,
            tag -> tag.setString("Fresh", "value"));
        assertFalse(lastNbt(scene).hasKey("PreviousPatch"));
        assertFalse(lastNbt(scene).hasKey("CustomName"));
    }

    @Test
    void typedTileModificationAlsoStartsWithTheOriginalInventoryAndName() throws Exception {
        PonderScene scene = chestScene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        builder.world().modifyBlockEntity(BlockPos.ORIGIN, TileEntityChest.class, chest -> {
            assertEquals("Original chest", chest.getName());
            assertEquals(Items.APPLE, chest.getStackInSlot(0).getItem());
            chest.setInventorySlotContents(1, new ItemStack(Items.COAL));
        });
        NBTTagCompound updated = lastNbt(scene);
        assertEquals("Original chest", updated.getString("CustomName"));
        assertEquals(2, updated.getTagList("Items", 10).tagCount());
        assertEquals(1, scene.getSchematic().getBlockEntityNbt(BlockPos.ORIGIN)
            .getTagList("Items", 10).tagCount());
    }

    private static Selection selection() {
        return SelectionImpl.fromTo(BlockPos.ORIGIN, BlockPos.ORIGIN);
    }

    private static PonderScene chestScene() {
        ResourceLocation id = new ResourceLocation("ponder_test", "builder_nbt");
        PonderScene scene = new PonderScene(new PonderStoryBoardEntry(null, "ponder_test", id, id),
            new PonderLocalization());
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", "minecraft:chest");
        tag.setString("CustomName", "Original chest");
        NBTTagCompound item = new NBTTagCompound();
        new ItemStack(Items.APPLE).writeToNBT(item);
        item.setByte("Slot", (byte) 0);
        NBTTagList items = new NBTTagList();
        items.appendTag(item);
        tag.setTag("Items", items);
        scene.setSchematic(PonderSchematic.of(new BlockPos(1, 1, 1),
            Collections.singletonMap(BlockPos.ORIGIN, Blocks.CHEST.getDefaultState()),
            Collections.singletonMap(BlockPos.ORIGIN, tag)));
        return scene;
    }

    private static NBTTagCompound lastNbt(PonderScene scene) throws Exception {
        return eventNbt(scene, scene.getWorldEvents().size() - 1);
    }

    private static NBTTagCompound eventNbt(PonderScene scene, int index) throws Exception {
        assertEquals(PonderScene.WorldEventType.APPLY_NBT, scene.getWorldEvents().get(index).getType());
        return JsonToNBT.getTagFromJson(scene.getWorldEvents().get(index).getStateDescription());
    }
}
