package net.createmod.ponder.foundation.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import net.createmod.ponder.foundation.content.VanillaTutorialRecipes.RecipePreview;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VanillaTutorialRecipesTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void craftingUsesTheActualFirstMatchingRegisteredResult() {
        RecipePreview preview = VanillaTutorialRecipes.crafting(Arrays.asList(
            planksRecipe(new ItemStack(Items.DIAMOND, 2)), planksRecipe(new ItemStack(Blocks.CRAFTING_TABLE))), null);
        assertTrue(preview.isAvailable());
        assertEquals(Items.DIAMOND, preview.getOutput().getItem());
        assertEquals(2, preview.getOutput().getCount());
        assertEquals(9, preview.getInputs().size());
    }

    @Test
    void removedCraftingRecipeNeverInventsAWorkbench() {
        RecipePreview preview = VanillaTutorialRecipes.crafting(Collections.<IRecipe>emptyList(), null);
        assertFalse(preview.isAvailable());
        assertTrue(preview.getOutput().isEmpty());
        assertTrue(preview.getUnavailableReason().contains("没有"));
    }

    @Test
    void throwingFirstRecipeDoesNotSilentlyFallThroughToAnotherResult() {
        IRecipe failing = new ShapedRecipes("", 2, 2, planks(), new ItemStack(Blocks.CRAFTING_TABLE)) {
            @Override
            public boolean matches(InventoryCrafting inventory, World world) {
                throw new IllegalStateException("Broken pack recipe");
            }
        };
        RecipePreview preview = VanillaTutorialRecipes.crafting(Arrays.asList(failing,
            planksRecipe(new ItemStack(Blocks.CRAFTING_TABLE))), null);
        assertFalse(preview.isAvailable());
        assertTrue(preview.getUnavailableReason().contains("失败"));
    }

    @Test
    void recipeSnapshotsDefensivelyCopyInputAndOutputStacks() {
        ItemStack output = new ItemStack(Items.DIAMOND, 2);
        RecipePreview preview = VanillaTutorialRecipes.crafting(Collections.singletonList(planksRecipe(output)), null);
        output.setCount(9);
        preview.getOutput().setCount(7);
        preview.getInputs().get(0).setCount(6);
        assertEquals(2, preview.getOutput().getCount());
        assertEquals(1, preview.getInputs().get(0).getCount());
    }

    @Test
    void smeltingUsesPackResultAndRequiresEnoughRegisteredFuel() {
        RecipePreview preview = VanillaTutorialRecipes.smelting(new ItemStack(Blocks.IRON_ORE),
            new ItemStack(Items.COAL), new ItemStack(Items.GOLD_INGOT, 3), 1600);
        assertTrue(preview.isAvailable());
        assertEquals(Items.GOLD_INGOT, preview.getOutput().getItem());
        assertEquals(3, preview.getOutput().getCount());
        assertFalse(VanillaTutorialRecipes.smelting(new ItemStack(Blocks.IRON_ORE),
            new ItemStack(Items.COAL), new ItemStack(Items.IRON_INGOT), 199).isAvailable());
    }

    @Test
    void missingSmeltingRecipeAndInvalidFuelHaveNoOutput() {
        RecipePreview missing = VanillaTutorialRecipes.smelting(new ItemStack(Blocks.IRON_ORE),
            new ItemStack(Items.COAL), ItemStack.EMPTY, 1600);
        assertFalse(missing.isAvailable());
        assertTrue(missing.getOutput().isEmpty());
        RecipePreview fuel = VanillaTutorialRecipes.smelting(new ItemStack(Blocks.IRON_ORE),
            ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT), 0);
        assertFalse(fuel.isAvailable());
        assertTrue(fuel.getOutput().isEmpty());
    }

    @Test
    void furnaceFlameAfterCompletionDependsOnRegisteredBurnTime() {
        RecipePreview exact = VanillaTutorialRecipes.smelting(new ItemStack(Blocks.IRON_ORE),
            new ItemStack(Items.COAL), new ItemStack(Items.IRON_INGOT), 200);
        assertTrue(exact.isAvailable());
        assertFalse(exact.hasRemainingFuel());
        RecipePreview remaining = VanillaTutorialRecipes.smelting(new ItemStack(Blocks.IRON_ORE),
            new ItemStack(Items.COAL), new ItemStack(Items.IRON_INGOT), 201);
        assertTrue(remaining.isAvailable());
        assertTrue(remaining.hasRemainingFuel());
    }

    private static IRecipe planksRecipe(ItemStack output) {
        return new ShapedRecipes("", 2, 2, planks(), output);
    }

    private static NonNullList<Ingredient> planks() {
        NonNullList<Ingredient> ingredients = NonNullList.withSize(4, Ingredient.EMPTY);
        for (int i = 0; i < 4; i++) {
            ingredients.set(i, Ingredient.fromStacks(new ItemStack(Blocks.PLANKS, 1, 0)));
        }
        return ingredients;
    }
}
