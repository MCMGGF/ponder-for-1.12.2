package net.createmod.ponder.authoring.practice;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import net.createmod.ponder.authoring.practice.PracticeDefinition.Action;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Goal;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Operation;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistryModifiable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PracticeSessionTest {
    private final List<ResourceLocation> addedRecipes = new ArrayList<ResourceLocation>();
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    @AfterEach void removeTestRecipes() {
        for (ResourceLocation id : addedRecipes)
            ((IForgeRegistryModifiable<IRecipe>) ForgeRegistries.RECIPES).remove(id);
    }

    @Test void definitionsAndReturnedStacksAreDeepCopies() {
        ItemStack original = tagged(Items.PAPER, 7, 1);
        PracticeDefinition definition = PracticeDefinition.builder("minecraft:chest").slot(0, original).build();
        original.setCount(1); original.getTagCompound().setInteger("test", 9);
        definition.getInitialSlots().get(0).setCount(2);
        PracticeSession session = new PracticeSession(definition, PracticeMode.TRY);
        session.getSlot(0).setCount(3);
        session.getSlot(0).getTagCompound().setInteger("test", 8);
        assertEquals(7, session.getSlot(0).getCount());
        assertEquals(1, session.getSlot(0).getTagCompound().getInteger("test"));
        session.click(0, false, false);
        session.getCursor().setCount(1);
        assertEquals(7, session.getCursor().getCount());
        session.reset();
        assertEquals(7, session.getSlot(0).getCount());
        assertTrue(session.getCursor().isEmpty());
    }

    @Test void leftAndRightClicksSplitMergeSwapAndRespectNbt() {
        PracticeSession session = chest().slot(0, tagged(Items.PAPER, 7, 1)).slot(1, tagged(Items.PAPER, 2, 2)).buildSession();
        assertTrue(session.click(0, true, false));
        assertEquals(4, session.getCursor().getCount()); assertEquals(3, session.getSlot(0).getCount());
        assertTrue(session.click(2, true, false));
        assertEquals(1, session.getSlot(2).getCount()); assertEquals(3, session.getCursor().getCount());
        assertFalse(session.click(1, true, false));
        assertTrue(session.click(1, false, false));
        assertEquals(2, session.getCursor().getTagCompound().getInteger("test"));
        assertEquals(1, session.getSlot(1).getTagCompound().getInteger("test"));
    }

    @Test void maximumStackSizeIsPreservedWithoutDeletingCursorRemainder() {
        PracticeSession session = chest().slot(0, new ItemStack(Items.PAPER, 63)).slot(1, new ItemStack(Items.PAPER, 5)).buildSession();
        session.click(1, false, false); session.click(0, false, false);
        assertEquals(64, session.getSlot(0).getCount()); assertEquals(4, session.getCursor().getCount());
        assertFalse(session.click(0, false, false));
        assertEquals(4, session.getCursor().getCount());
    }

    @Test void shiftTransferOnlyTouchesTheVirtualSupplyArea() {
        PracticeSession session = chest().slot(0, new ItemStack(Items.PAPER, 8)).buildSession();
        assertTrue(session.click(0, false, true));
        assertTrue(session.getSlot(0).isEmpty()); assertEquals(8, session.getSlot(62).getCount());
        assertTrue(session.getCursor().isEmpty());
        assertTrue(session.click(62, false, true));
        assertEquals(8, session.getSlot(0).getCount());
    }

    @Test void watchRejectsUserInputButReplaysTheSameScript() {
        PracticeDefinition definition = PracticeDefinition.builder("minecraft:chest").slot(0, new ItemStack(Items.PAPER, 3))
            .action(action(12, Operation.CLICK_LEFT, 0)).action(action(20, Operation.CLICK_RIGHT, 1)).build();
        PracticeSession session = new PracticeSession(definition, PracticeMode.WATCH);
        assertFalse(session.click(0, false, false));
        session.seek(20);
        assertEquals(1, session.getSlot(1).getCount()); assertEquals(2, session.getCursor().getCount());
        session.seek(5); assertTrue(session.getCursor().isEmpty());
        session.seek(20); assertEquals(2, session.getCursor().getCount());
    }

    @Test void practiceKeepsInitialSuppliesButDoesNotExecuteTheAnswer() {
        PracticeDefinition definition = PracticeDefinition.builder("minecraft:chest").slot(0, new ItemStack(Items.PAPER))
            .action(action(10, Operation.CLICK_LEFT, 0)).build();
        PracticeSession session = new PracticeSession(definition, PracticeMode.PRACTICE);
        session.seek(30); assertFalse(session.getSlot(0).isEmpty()); assertTrue(session.getCursor().isEmpty());
        assertTrue(session.click(0, false, false));
        session.setMode(PracticeMode.WATCH);
        assertEquals(0, session.getTick()); assertFalse(session.getSlot(0).isEmpty());
        session.seek(30); assertTrue(session.getSlot(0).isEmpty());
    }

    @Test void arbitrarySeekingResetsUserChangesAndPreservesSameTickInsertionOrder() {
        PracticeDefinition definition = PracticeDefinition.builder("minecraft:chest")
            .action(new Action(10, Operation.SET_SLOT, 0, new ItemStack(Items.PAPER, 4), "", 0))
            .action(action(10, Operation.CLICK_LEFT, 0)).action(action(10, Operation.CLICK_RIGHT, 1)).build();
        PracticeSession session = new PracticeSession(definition, PracticeMode.TRY);
        session.seek(10); assertEquals(3, session.getCursor().getCount());
        session.click(2, false, false); assertTrue(session.getCursor().isEmpty());
        session.seek(10); assertEquals(3, session.getCursor().getCount()); assertTrue(session.getSlot(2).isEmpty());
    }

    @Test void hideAndShowAreDeterministicAndHiddenSlotsCannotBeClicked() {
        PracticeDefinition definition = PracticeDefinition.builder("minecraft:chest").slot(0, new ItemStack(Items.PAPER))
            .action(action(4, Operation.HIDE, -1)).action(action(8, Operation.SHOW, -1)).build();
        PracticeSession session = new PracticeSession(definition, PracticeMode.TRY);
        session.seek(5); assertFalse(session.isVisible()); assertFalse(session.click(0, false, false));
        session.seek(8); assertTrue(session.isVisible()); assertTrue(session.click(0, false, false));
    }

    @Test void unsupportedMenuIsReadOnlyAndNamesItsReason() {
        PracticeSession session = new PracticeSession(PracticeDefinition.builder("thirdparty:machine")
            .slot(0, new ItemStack(Items.PAPER)).build(), PracticeMode.TRY);
        assertTrue(session.isReadOnly()); assertTrue(session.getReadOnlyReason().contains("thirdparty:machine"));
        assertFalse(session.click(0, false, false)); assertFalse(session.toggleLock());
    }

    @Test void hopperMovesOneItemEveryEightVirtualTicksAndHonorsLock() {
        PracticeDefinition definition = PracticeDefinition.builder("minecraft:hopper")
            .slot(0, new ItemStack(Items.PAPER, 4)).data("locked", 1).build();
        PracticeSession session = new PracticeSession(definition, PracticeMode.TRY);
        session.seek(16); assertEquals(4, session.getSlot(0).getCount()); assertTrue(session.getSlot(5).isEmpty());
        assertTrue(session.toggleLock());
        for (int i = 0; i < 8; i++) session.advance();
        assertEquals(3, session.getSlot(0).getCount()); assertEquals(1, session.getSlot(5).getCount());
        session.seek(24); assertEquals(4, session.getSlot(0).getCount());
    }

    @Test void furnaceUsesCurrentSmeltingAndFuelRegistriesAndSeeksIdentically() {
        PracticeDefinition definition = PracticeDefinition.builder("minecraft:furnace")
            .slot(0, new ItemStack(Blocks.IRON_ORE, 2)).slot(1, new ItemStack(Items.COAL)).duration(500).build();
        PracticeSession session = new PracticeSession(definition, PracticeMode.WATCH);
        session.seek(199); assertTrue(session.getSlot(2).isEmpty());
        session.seek(200); assertEquals(Items.IRON_INGOT, session.getSlot(2).getItem());
        assertEquals(1, session.getSlot(0).getCount());
        int fuel = session.getData("burnTime");
        session.seek(400); assertEquals(2, session.getSlot(2).getCount());
        session.seek(200); assertEquals(fuel, session.getData("burnTime"));
        assertEquals(1, session.getSlot(2).getCount());
    }

    @Test void furnaceRejectsInvalidFuelAndCannotOverwriteOutput() {
        PracticeSession session = new PracticeSession(PracticeDefinition.builder("minecraft:furnace")
            .slot(3, new ItemStack(Items.PAPER)).build(), PracticeMode.TRY);
        session.click(3, false, false);
        assertFalse(session.click(1, false, false)); assertFalse(session.click(2, false, false));
        assertEquals(Items.PAPER, session.getCursor().getItem());
    }

    @Test void missingSmeltingRecipeNeverInventsAnOutput() {
        PracticeSession session = new PracticeSession(PracticeDefinition.builder("minecraft:furnace")
            .slot(0, new ItemStack(Items.DIAMOND)).slot(1, new ItemStack(Items.COAL)).duration(400).build(), PracticeMode.WATCH);
        session.seek(400); assertTrue(session.getSlot(2).isEmpty()); assertEquals(1, session.getSlot(1).getCount());
    }

    @Test void workbenchUsesTheActualRegisteredRecipeAndConsumesInputs() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.fromItem(Items.PAPER)); ingredients.add(Ingredient.fromItem(Items.DIAMOND));
        registerRecipe("isolated_practice",
            new ShapelessRecipes("", new ItemStack(Items.EMERALD, 2), ingredients));
        PracticeSession session = new PracticeSession(PracticeDefinition.builder("minecraft:crafting")
            .slot(1, new ItemStack(Items.PAPER, 2)).slot(2, new ItemStack(Items.DIAMOND, 2)).build(), PracticeMode.TRY);
        assertEquals(Items.EMERALD, session.getSlot(0).getItem()); assertEquals(2, session.getSlot(0).getCount());
        assertTrue(session.click(0, false, false)); assertEquals(2, session.getCursor().getCount());
        assertEquals(1, session.getSlot(1).getCount()); assertEquals(1, session.getSlot(2).getCount());
        assertTrue(session.click(0, false, false)); assertEquals(4, session.getCursor().getCount());
        assertTrue(session.getSlot(0).isEmpty());
    }

    @Test void recipeResultIsSnapshottedForDeterministicResetAndSeek() {
        final int[] calls = {0};
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.fromItem(Items.NETHER_STAR)); ingredients.add(Ingredient.fromItem(Items.STICK));
        registerRecipe("practice_snapshot",
            new ShapelessRecipes("", new ItemStack(Items.EMERALD), ingredients) {
                @Override public ItemStack getCraftingResult(net.minecraft.inventory.InventoryCrafting matrix) {
                    return new ItemStack(Items.EMERALD, ++calls[0]);
                }
            });
        PracticeDefinition definition = PracticeDefinition.builder("minecraft:crafting")
            .slot(1, new ItemStack(Items.NETHER_STAR)).slot(2, new ItemStack(Items.STICK))
            .action(action(10, Operation.CLICK_LEFT, 0)).build();
        PracticeSession session = new PracticeSession(definition, PracticeMode.WATCH);
        session.seek(10); assertEquals(1, session.getCursor().getCount());
        session.seek(0); session.seek(10); assertEquals(1, session.getCursor().getCount());
        assertEquals(1, calls[0]);
    }

    @Test void invalidRegisteredRemaindersProduceAReadableFailureWithoutConsumingInputs() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.fromItem(Items.NETHER_STAR)); ingredients.add(Ingredient.fromItem(Items.PAPER));
        registerRecipe("practice_bad_remainder",
            new ShapelessRecipes("", new ItemStack(Items.EMERALD), ingredients) {
                @Override public NonNullList<ItemStack> getRemainingItems(net.minecraft.inventory.InventoryCrafting matrix) {
                    return NonNullList.create();
                }
            });
        PracticeSession session = new PracticeSession(PracticeDefinition.builder("minecraft:crafting")
            .slot(1, new ItemStack(Items.NETHER_STAR)).slot(2, new ItemStack(Items.PAPER)).build(), PracticeMode.TRY);
        assertTrue(session.getSlot(0).isEmpty()); assertTrue(session.getNotice().contains("failed"));
        assertFalse(session.click(0, false, false)); assertFalse(session.getSlot(1).isEmpty());
    }

    @Test void oversizedRecipeOutputOrContainerRemainderIsRejectedWithoutInputLoss() {
        for (boolean oversizedOutput : new boolean[] {false, true}) {
            NonNullList<Ingredient> ingredients = NonNullList.create();
            ingredients.add(Ingredient.fromItem(Items.NETHER_STAR));
            ingredients.add(Ingredient.fromItem(oversizedOutput ? Items.APPLE : Items.CARROT));
            registerRecipe(oversizedOutput ? "oversized_output" : "oversized_remainder",
                new ShapelessRecipes("", new ItemStack(Items.EMERALD), ingredients) {
                    @Override public ItemStack getCraftingResult(net.minecraft.inventory.InventoryCrafting matrix) {
                        return new ItemStack(Items.EMERALD, oversizedOutput ? 65 : 1);
                    }
                    @Override public NonNullList<ItemStack> getRemainingItems(net.minecraft.inventory.InventoryCrafting matrix) {
                        NonNullList<ItemStack> remains = NonNullList.withSize(9, ItemStack.EMPTY);
                        if (!oversizedOutput) remains.set(0, new ItemStack(Items.BUCKET, 17));
                        return remains;
                    }
                });
            PracticeSession session = new PracticeSession(PracticeDefinition.builder("minecraft:crafting")
                .slot(1, new ItemStack(Items.NETHER_STAR))
                .slot(2, new ItemStack(oversizedOutput ? Items.APPLE : Items.CARROT)).build(), PracticeMode.TRY);
            assertTrue(session.getSlot(0).isEmpty()); assertTrue(session.getNotice().contains("failed"));
            assertFalse(session.click(0, false, false)); assertEquals(1, session.getSlot(1).getCount());
            assertTrue(session.getCursor().isEmpty());
        }
    }

    @Test void craftingKeepsContainerRemaindersAndNeverSilentlyDeletesOverflow() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.fromItem(Items.NETHER_STAR)); ingredients.add(Ingredient.fromItem(Items.BLAZE_ROD));
        registerRecipe("practice_remainders",
            new ShapelessRecipes("", new ItemStack(Items.EMERALD), ingredients) {
                @Override public NonNullList<ItemStack> getRemainingItems(net.minecraft.inventory.InventoryCrafting matrix) {
                    NonNullList<ItemStack> remains = NonNullList.withSize(9, ItemStack.EMPTY);
                    remains.set(0, new ItemStack(Items.BUCKET)); return remains;
                }
            });
        PracticeDefinition.Builder builder = PracticeDefinition.builder("minecraft:crafting")
            .slot(1, new ItemStack(Items.NETHER_STAR, 2)).slot(2, new ItemStack(Items.BLAZE_ROD, 2));
        for (int i = 10; i < 46; i++) builder.slot(i, new ItemStack(Items.PAPER, 64));
        PracticeSession session = new PracticeSession(builder.build(), PracticeMode.TRY);
        assertFalse(session.click(0, false, false)); assertTrue(session.getCursor().isEmpty());
        assertEquals(2, session.getSlot(1).getCount()); assertTrue(session.getNotice().contains("remainder"));
        PracticeSession enoughRoom = new PracticeSession(PracticeDefinition.builder("minecraft:crafting")
            .slot(1, new ItemStack(Items.NETHER_STAR)).slot(2, new ItemStack(Items.BLAZE_ROD)).build(), PracticeMode.TRY);
        assertTrue(enoughRoom.click(0, false, false));
        assertEquals(Items.BUCKET, enoughRoom.getSlot(1).getItem());
        assertEquals(Items.EMERALD, enoughRoom.getCursor().getItem());
    }

    @Test void exactGoalsCompareCountMetadataAndFullNbt() {
        ItemStack expected = tagged(Items.PAPER, 4, 2);
        Goal exact = new Goal(Goal.Kind.SLOT_EXACT, 0, expected, "", 0, "Paper");
        Goal contains = new Goal(Goal.Kind.SLOT_CONTAINS, 0, tagged(Items.PAPER, 2, 2), "", 0, "Paper");
        PracticeDefinition definition = PracticeDefinition.builder("minecraft:chest").slot(0, expected)
            .goal(exact).goal(contains).goal(new Goal(Goal.Kind.CURSOR_EMPTY, -1, null, "", 0, "")).build();
        expected.getTagCompound().setInteger("test", 9);
        PracticeSession session = new PracticeSession(definition, PracticeMode.PRACTICE);
        assertTrue(session.isComplete()); assertTrue(session.isGoalSatisfied(exact));
        session.click(0, true, false);
        assertFalse(session.isGoalSatisfied(exact)); assertTrue(session.isGoalSatisfied(contains)); assertFalse(session.isComplete());
        assertFalse(session.isGoalSatisfied(new Goal(Goal.Kind.SLOT_CONTAINS, 0, tagged(Items.PAPER, 1, 3), "", 0, "")));
    }

    @Test void shiftOutputMovesOnlyIntoVirtualSupplyAndPreservesHeldCursor() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.fromItem(Items.NETHER_STAR)); ingredients.add(Ingredient.fromItem(Items.LEATHER));
        registerRecipe("practice_shift", new ShapelessRecipes("", new ItemStack(Items.EMERALD, 2), ingredients));
        PracticeSession session = new PracticeSession(PracticeDefinition.builder("minecraft:crafting")
            .slot(1, new ItemStack(Items.NETHER_STAR)).slot(2, new ItemStack(Items.LEATHER))
            .slot(10, new ItemStack(Items.STICK, 3)).build(), PracticeMode.TRY);
        session.click(10, false, false);
        assertTrue(session.click(0, false, true));
        assertEquals(Items.STICK, session.getCursor().getItem()); assertEquals(3, session.getCursor().getCount());
        assertTrue(session.getSlot(10).isEmpty());
        assertEquals(Items.EMERALD, session.getSlot(45).getItem()); assertEquals(2, session.getSlot(45).getCount());
        assertTrue(session.getSlot(1).isEmpty());
        PracticeDefinition.Builder full = PracticeDefinition.builder("minecraft:crafting")
            .slot(1, new ItemStack(Items.NETHER_STAR)).slot(2, new ItemStack(Items.LEATHER));
        for (int slot = 10; slot < 46; slot++) full.slot(slot, new ItemStack(Items.PAPER, 64));
        PracticeSession noRoom = new PracticeSession(full.build(), PracticeMode.TRY);
        assertFalse(noRoom.click(0, false, true));
        assertTrue(noRoom.getCursor().isEmpty()); assertEquals(1, noRoom.getSlot(1).getCount());
        assertEquals(2, noRoom.getSlot(0).getCount());
        assertEquals(Items.PAPER, noRoom.getSlot(10).getItem()); assertEquals(64, noRoom.getSlot(10).getCount());
    }

    @Test void emptyGoalsDoNotPretendCompletionAndDataMapsAreReadOnly() {
        PracticeSession session = chest().buildSession();
        assertFalse(session.isComplete());
        assertThrows(UnsupportedOperationException.class, () -> session.getData().put("locked", 1));
        assertThrows(IllegalArgumentException.class, () -> session.seek(-1));
        assertThrows(IllegalArgumentException.class, () -> session.seek(24001));
        assertThrows(IllegalArgumentException.class, () -> session.click(999, false, false));
    }

    @Test void invalidIndicesAndBudgetsAreRejectedBeforePlayback() {
        assertThrows(IllegalArgumentException.class, () -> PracticeDefinition.builder("minecraft:furnace").slot(39, new ItemStack(Items.PAPER)).build());
        assertThrows(IllegalArgumentException.class, () -> PracticeDefinition.builder("minecraft:chest").action(action(1, Operation.CLICK_LEFT, 99)).build());
        assertThrows(IllegalArgumentException.class, () -> PracticeDefinition.builder("minecraft:chest").duration(24001));
    }

    @Test void closingDropsVirtualStateAndRejectsFurtherInteraction() {
        PracticeSession session = chest().slot(0, new ItemStack(Items.PAPER)).buildSession();
        session.click(0, false, false); session.close();
        assertTrue(session.isClosed()); assertFalse(session.isVisible());
        assertTrue(session.getCursor().isEmpty()); assertEquals(0, session.getSlotCount());
        assertFalse(session.click(0, false, false)); assertFalse(session.toggleLock()); assertFalse(session.isComplete());
        assertThrows(IllegalStateException.class, session::reset);
        session.close();
    }

    private static ItemStack tagged(net.minecraft.item.Item item, int count, int value) {
        ItemStack stack = new ItemStack(item, count); NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("test", value); stack.setTagCompound(tag); return stack;
    }
    private static Action action(int tick, Operation op, int slot) { return new Action(tick, op, slot, ItemStack.EMPTY, "", 0); }
    private void registerRecipe(String name, IRecipe recipe) {
        ResourceLocation id = new ResourceLocation("ponder_test", name);
        recipe.setRegistryName(id);
        ForgeRegistries.RECIPES.register(recipe);
        addedRecipes.add(id);
    }
    private static ChestBuilder chest() { return new ChestBuilder(); }
    private static final class ChestBuilder {
        final PracticeDefinition.Builder builder = PracticeDefinition.builder("minecraft:chest");
        ChestBuilder slot(int index, ItemStack stack) { builder.slot(index, stack); return this; }
        PracticeSession buildSession() { return new PracticeSession(builder.build(), PracticeMode.TRY); }
    }
}
