package net.createmod.ponder.authoring.practice;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistryModifiable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PracticeQuickMoveTest {
    private final List<ResourceLocation> addedRecipes = new ArrayList<ResourceLocation>();
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    @AfterEach void removeRecipes() {
        for (ResourceLocation id : addedRecipes)
            ((IForgeRegistryModifiable<IRecipe>) ForgeRegistries.RECIPES).remove(id);
    }

    @Test void workbenchPlayerSlotsSwapMainAndHotbarWithoutFillingTheGrid() {
        PracticeSession session = session(PracticeDefinition.builder("minecraft:crafting")
            .slot(10, new ItemStack(Items.PAPER, 8)).slot(42, new ItemStack(Items.PAPER, 60)));
        assertTrue(session.click(10, false, true));
        assertEquals(64, session.getSlot(42).getCount());
        assertEquals(4, session.getSlot(37).getCount());
        for (int i = 0; i < 10; i++) assertTrue(session.getSlot(i).isEmpty());
        assertTrue(session.click(37, false, true));
        assertEquals(4, session.getSlot(10).getCount());
        assertTrue(session.getSlot(37).isEmpty());
        assertTrue(session.getCursor().isEmpty());
    }

    @Test void workbenchGridReturnsForwardButMergesBeforeChoosingAnEmptyPlayerSlot() {
        PracticeSession session = session(PracticeDefinition.builder("minecraft:crafting")
            .slot(1, new ItemStack(Items.PAPER, 8)).slot(20, new ItemStack(Items.PAPER, 60)));
        assertTrue(session.click(1, false, true));
        assertTrue(session.getSlot(1).isEmpty());
        assertEquals(64, session.getSlot(20).getCount());
        assertEquals(4, session.getSlot(10).getCount());
        assertTrue(session.getSlot(45).isEmpty());
    }

    @Test void furnaceQuickMoveRoutesCoalToFuelOreToInputAndOtherItemsBetweenPlayerRanges() {
        PracticeSession session = session(PracticeDefinition.builder("minecraft:furnace")
            .slot(3, new ItemStack(Items.COAL, 8)).slot(4, new ItemStack(Blocks.IRON_ORE, 3))
            .slot(5, new ItemStack(Items.DIAMOND, 5)).slot(35, new ItemStack(Items.DIAMOND, 2)));
        assertTrue(session.click(3, false, true));
        assertEquals(Items.COAL, session.getSlot(1).getItem());
        assertEquals(8, session.getSlot(1).getCount());
        assertTrue(session.getSlot(0).isEmpty());
        assertTrue(session.click(4, false, true));
        assertEquals(Blocks.IRON_ORE, net.minecraft.block.Block.getBlockFromItem(session.getSlot(0).getItem()));
        assertTrue(session.click(5, false, true));
        assertEquals(7, session.getSlot(35).getCount());
        assertTrue(session.getSlot(30).isEmpty());
        assertTrue(session.click(35, false, true));
        assertEquals(Items.DIAMOND, session.getSlot(3).getItem());
        assertEquals(7, session.getSlot(3).getCount());
    }

    @Test void furnaceSmeltingTakesPriorityOverFuelAndDoesNotFallbackWhenInputIsFull() {
        PracticeSession session = session(PracticeDefinition.builder("minecraft:furnace")
            .slot(3, new ItemStack(Blocks.LOG, 3)));
        assertTrue(session.click(3, false, true));
        assertEquals(3, session.getSlot(0).getCount());
        assertTrue(session.getSlot(1).isEmpty());
        PracticeSession full = session(PracticeDefinition.builder("minecraft:furnace")
            .slot(0, new ItemStack(Blocks.LOG, 64)).slot(3, new ItemStack(Blocks.LOG, 3)));
        assertFalse(full.click(3, false, true));
        assertEquals(3, full.getSlot(3).getCount());
        assertTrue(full.getSlot(1).isEmpty());
        assertTrue(full.getSlot(30).isEmpty());
    }

    @Test void furnacePlayerRangesMergeBeforeEmptySlotsAndMenuInputsReturnForward() {
        PracticeSession session = session(PracticeDefinition.builder("minecraft:furnace")
            .slot(0, new ItemStack(Items.PAPER, 2)).slot(3, new ItemStack(Items.PAPER, 8))
            .slot(33, new ItemStack(Items.PAPER, 60)));
        assertTrue(session.click(3, false, true));
        assertEquals(64, session.getSlot(33).getCount());
        assertEquals(4, session.getSlot(30).getCount());
        assertTrue(session.click(0, false, true));
        assertEquals(6, session.getSlot(30).getCount());
        assertTrue(session.getSlot(3).isEmpty());
    }

    @Test void hopperUsesForwardInsertionAndReversePlayerExtractionInTwoPasses() {
        PracticeSession session = session(PracticeDefinition.builder("minecraft:hopper")
            .slot(5, new ItemStack(Items.PAPER, 8)).slot(3, new ItemStack(Items.PAPER, 60))
            .slot(40, new ItemStack(Items.PAPER, 63)).data("locked", 1));
        assertTrue(session.click(5, false, true));
        assertEquals(64, session.getSlot(3).getCount());
        assertEquals(4, session.getSlot(0).getCount());
        assertTrue(session.click(0, false, true));
        assertEquals(64, session.getSlot(40).getCount());
        assertEquals(3, session.getSlot(39).getCount());
        assertTrue(session.getSlot(5).isEmpty());
    }

    @Test void furnaceOutputAllowsPartialReverseTransferAndDoesNotTouchTheHeldCursor() {
        PracticeDefinition.Builder builder = PracticeDefinition.builder("minecraft:furnace")
            .slot(1, new ItemStack(Items.STICK, 2)).slot(2, new ItemStack(Items.IRON_INGOT, 4));
        for (int i = 3; i < 39; i++) builder.slot(i, new ItemStack(Items.PAPER, 64));
        builder.slot(38, new ItemStack(Items.IRON_INGOT, 63));
        PracticeSession session = session(builder);
        session.click(1, false, false);
        assertTrue(session.click(2, false, true));
        assertEquals(64, session.getSlot(38).getCount());
        assertEquals(3, session.getSlot(2).getCount());
        assertEquals(Items.STICK, session.getCursor().getItem());
        assertEquals(2, session.getCursor().getCount());
        assertFalse(session.click(2, false, true));
        assertEquals(3, session.getSlot(2).getCount());
    }

    @Test void furnaceOutputPrefersLastHotbarSlotEvenWhenTheFirstPlayerSlotIsEmpty() {
        PracticeSession session = session(PracticeDefinition.builder("minecraft:furnace")
            .slot(2, new ItemStack(Items.IRON_INGOT, 4)));
        assertTrue(session.click(2, false, true));
        assertEquals(4, session.getSlot(38).getCount());
        assertTrue(session.getSlot(3).isEmpty());
    }

    @Test void emptyBucketFuelSlotAcceptsExactlyOneAndRejectsAnOversizedSwap() {
        PracticeSession session = session(PracticeDefinition.builder("minecraft:furnace")
            .slot(3, new ItemStack(Items.BUCKET, 2)));
        session.click(3, false, false);
        assertTrue(session.click(1, false, false));
        assertEquals(1, session.getSlot(1).getCount());
        assertEquals(1, session.getCursor().getCount());
        assertFalse(session.click(1, false, false));
        PracticeSession swap = session(PracticeDefinition.builder("minecraft:furnace")
            .slot(1, new ItemStack(Items.COAL)).slot(3, new ItemStack(Items.BUCKET, 2)));
        swap.click(3, false, false);
        assertFalse(swap.click(1, false, false));
        assertEquals(Items.COAL, swap.getSlot(1).getItem());
        assertEquals(2, swap.getCursor().getCount());
    }

    @Test void quickMovePreservesNbtAndTheItemMaximumStackSize() {
        ItemStack different = new ItemStack(Items.ENDER_PEARL, 15);
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("test", "different"); different.setTagCompound(tag);
        PracticeSession session = session(PracticeDefinition.builder("minecraft:crafting")
            .slot(10, new ItemStack(Items.ENDER_PEARL, 16)).slot(44, new ItemStack(Items.ENDER_PEARL, 15))
            .slot(45, different));
        assertTrue(session.click(10, false, true));
        assertEquals(16, session.getSlot(44).getCount());
        assertEquals(15, session.getSlot(37).getCount());
        assertEquals(15, session.getSlot(45).getCount());
        assertEquals("different", session.getSlot(45).getTagCompound().getString("test"));
    }

    @Test void workbenchShiftOutputRepeatsCraftingAndFillsThePlayerRangeInReverse() {
        register("repeat", recipe(2));
        PracticeSession session = session(PracticeDefinition.builder("minecraft:crafting")
            .slot(1, new ItemStack(Items.NETHER_STAR, 64)).slot(2, new ItemStack(Items.LEATHER, 64)));
        assertTrue(session.click(0, false, true));
        assertEquals(64, session.getSlot(45).getCount());
        assertEquals(64, session.getSlot(44).getCount());
        assertTrue(session.getSlot(43).isEmpty());
        assertTrue(session.getSlot(1).isEmpty());
        assertTrue(session.getSlot(2).isEmpty());
        assertTrue(session.getCursor().isEmpty());
    }

    @Test void workbenchRejectsAnIncompleteOutputBatchWithoutConsumingOrDroppingAnything() {
        register("output_room", recipe(2));
        PracticeDefinition.Builder builder = PracticeDefinition.builder("minecraft:crafting")
            .slot(1, new ItemStack(Items.NETHER_STAR)).slot(2, new ItemStack(Items.LEATHER));
        for (int i = 10; i < 46; i++) builder.slot(i, new ItemStack(Items.PAPER, 64));
        builder.slot(45, new ItemStack(Items.EMERALD, 63));
        PracticeSession session = session(builder);
        assertFalse(session.click(0, false, true));
        assertEquals(63, session.getSlot(45).getCount());
        assertEquals(1, session.getSlot(1).getCount());
        assertEquals(1, session.getSlot(2).getCount());
        assertEquals(2, session.getSlot(0).getCount());
        assertTrue(session.getCursor().isEmpty());
        assertTrue(session.getNotice().contains("complete crafting output"));
    }

    @Test void workbenchStopsAtFullOutputSpaceWithoutRollingBackEarlierCompletedRecipes() {
        register("partial_repeat", recipe(2));
        PracticeDefinition.Builder builder = PracticeDefinition.builder("minecraft:crafting")
            .slot(1, new ItemStack(Items.NETHER_STAR, 3)).slot(2, new ItemStack(Items.LEATHER, 3));
        for (int i = 10; i < 46; i++) builder.slot(i, new ItemStack(Items.PAPER, 64));
        builder.slot(45, new ItemStack(Items.EMERALD, 60));
        PracticeSession session = session(builder);
        assertTrue(session.click(0, false, true));
        assertEquals(64, session.getSlot(45).getCount());
        assertEquals(1, session.getSlot(1).getCount());
        assertEquals(1, session.getSlot(2).getCount());
        assertEquals(2, session.getSlot(0).getCount());
        assertTrue(session.getCursor().isEmpty());
    }

    @Test void workbenchQuickOutputReservesContainerRemaindersBeforeConsumingInputs() {
        register("remainder_room", new ShapelessRecipes("", new ItemStack(Items.EMERALD, 2), ingredients()) {
            @Override public NonNullList<ItemStack> getRemainingItems(net.minecraft.inventory.InventoryCrafting matrix) {
                NonNullList<ItemStack> result = NonNullList.withSize(9, ItemStack.EMPTY);
                result.set(0, new ItemStack(Items.BUCKET)); return result;
            }
        });
        PracticeDefinition.Builder builder = PracticeDefinition.builder("minecraft:crafting")
            .slot(1, new ItemStack(Items.NETHER_STAR, 2)).slot(2, new ItemStack(Items.LEATHER, 2));
        for (int i = 10; i < 46; i++) builder.slot(i, new ItemStack(Items.PAPER, 64));
        builder.slot(45, new ItemStack(Items.EMERALD, 60));
        PracticeSession session = session(builder);
        assertFalse(session.click(0, false, true));
        assertEquals(60, session.getSlot(45).getCount());
        assertEquals(2, session.getSlot(1).getCount());
        assertEquals(2, session.getSlot(2).getCount());
        assertEquals(2, session.getSlot(0).getCount());
        assertTrue(session.getNotice().contains("remainder"));
    }

    @Test void workbenchQuickCraftingHasABoundedBudgetEvenForSelfReturningIngredients() {
        final int[] lookups = {0};
        register("replenish", new ShapelessRecipes("", new ItemStack(Items.EMERALD), ingredients()) {
            @Override public ItemStack getCraftingResult(net.minecraft.inventory.InventoryCrafting matrix) {
                lookups[0]++; return new ItemStack(Items.EMERALD);
            }
            @Override public NonNullList<ItemStack> getRemainingItems(net.minecraft.inventory.InventoryCrafting matrix) {
                NonNullList<ItemStack> result = NonNullList.withSize(9, ItemStack.EMPTY);
                result.set(0, new ItemStack(Items.NETHER_STAR)); result.set(1, new ItemStack(Items.LEATHER)); return result;
            }
        });
        PracticeSession session = session(PracticeDefinition.builder("minecraft:crafting")
            .slot(1, new ItemStack(Items.NETHER_STAR)).slot(2, new ItemStack(Items.LEATHER)));
        assertTrue(session.click(0, false, true));
        assertEquals(64, session.getSlot(45).getCount());
        assertTrue(session.getSlot(44).isEmpty());
        assertEquals(1, session.getSlot(1).getCount());
        assertEquals(1, session.getSlot(2).getCount());
        assertTrue(session.getNotice().contains("64 recipes"));
        assertEquals(1, lookups[0]);
    }

    @Test void scriptedQuickMovesResetAndSeekDeterministicallyForEachVanillaMenu() {
        String[] menus = {"minecraft:chest", "minecraft:generic_9x6", "minecraft:hopper", "minecraft:furnace", "minecraft:crafting"};
        int[] source = {0, 0, 0, 3, 10};
        int[] destination = {62, 89, 40, 30, 37};
        for (int i = 0; i < menus.length; i++) {
            PracticeDefinition definition = PracticeDefinition.builder(menus[i]).duration(40).data("locked", 1)
                .slot(source[i], new ItemStack(Items.PAPER, 8))
                .action(new PracticeDefinition.Action(5, PracticeDefinition.Operation.TRANSFER, source[i], ItemStack.EMPTY, "", 0))
                .action(new PracticeDefinition.Action(10, PracticeDefinition.Operation.TRANSFER, destination[i], ItemStack.EMPTY, "", 0)).build();
            PracticeSession session = new PracticeSession(definition, PracticeMode.WATCH);
            session.seek(5); assertEquals(8, session.getSlot(destination[i]).getCount(), menus[i]);
            session.seek(10); String after = snapshot(session);
            assertEquals(8, session.getSlot(source[i]).getCount(), menus[i]);
            session.seek(0); session.seek(10); assertEquals(after, snapshot(session), menus[i]);
            assertTrue(session.getCursor().isEmpty());
        }
    }

    private static PracticeSession session(PracticeDefinition.Builder builder) { return new PracticeSession(builder.build(), PracticeMode.TRY); }
    private static NonNullList<Ingredient> ingredients() {
        NonNullList<Ingredient> result = NonNullList.create();
        result.add(Ingredient.fromItem(Items.NETHER_STAR)); result.add(Ingredient.fromItem(Items.LEATHER)); return result;
    }
    private static IRecipe recipe(int count) { return new ShapelessRecipes("", new ItemStack(Items.EMERALD, count), ingredients()); }
    private void register(String name, IRecipe recipe) {
        ResourceLocation id = new ResourceLocation("ponder_quick_move_test", name); recipe.setRegistryName(id);
        ForgeRegistries.RECIPES.register(recipe); addedRecipes.add(id);
    }
    private static String snapshot(PracticeSession session) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < session.getSlotCount(); i++) result.append(i).append(':').append(session.getSlot(i).serializeNBT());
        return result.append(session.getCursor().serializeNBT()).toString();
    }
}
