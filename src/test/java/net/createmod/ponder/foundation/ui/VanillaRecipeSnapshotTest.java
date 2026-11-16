package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.List;

import net.createmod.ponder.foundation.content.VanillaTutorialRecipes.RecipePreview;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VanillaRecipeSnapshotTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void validCraftingShowsTheActualOutputAsSoonAsInputsAreShown() throws Exception {
        RecipePreview recipe = preview(new ItemStack(Items.DIAMOND, 2), null);
        for (boolean resultStage : new boolean[] {false, true}) {
            ItemStack displayed = snapshot(recipe, false, resultStage).displayedOutput();
            assertEquals(Items.DIAMOND, displayed.getItem());
            assertEquals(2, displayed.getCount());
        }
    }

    @Test
    void unavailableCraftingNeverDrawsAnInventedResultInEitherStage() throws Exception {
        RecipePreview recipe = preview(ItemStack.EMPTY, "no matching recipe");
        for (boolean resultStage : new boolean[] {false, true}) {
            assertTrue(snapshot(recipe, false, resultStage).displayedOutput().isEmpty());
        }
    }

    @Test
    void validFurnaceKeepsItsInputAndCompletionStagesDistinct() throws Exception {
        RecipePreview recipe = preview(new ItemStack(Items.GOLD_INGOT, 3), null);
        assertTrue(snapshot(recipe, true, false).displayedOutput().isEmpty());
        ItemStack displayed = snapshot(recipe, true, true).displayedOutput();
        assertEquals(Items.GOLD_INGOT, displayed.getItem());
        assertEquals(3, displayed.getCount());
    }

    @Test
    void unavailableFurnaceHasNoOutputEvenInTheCompletionStage() throws Exception {
        RecipePreview recipe = preview(ItemStack.EMPTY, "no smelting recipe");
        for (boolean resultStage : new boolean[] {false, true}) {
            assertTrue(snapshot(recipe, true, resultStage).displayedOutput().isEmpty());
        }
    }

    private static VanillaRecipeSnapshot snapshot(RecipePreview recipe, boolean furnace, boolean resultStage) {
        ResourceLocation id = VanillaRecipeSnapshot.register(recipe, furnace, resultStage);
        return (VanillaRecipeSnapshot) PonderGuiSnapshotRegistry.get(id, 0).renderer;
    }

    private static RecipePreview preview(ItemStack output, String reason) throws Exception {
        Constructor<RecipePreview> constructor = RecipePreview.class.getDeclaredConstructor(List.class,
            ItemStack.class, ItemStack.class, String.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(Collections.singletonList(new ItemStack(Blocks.IRON_ORE)),
            new ItemStack(Items.COAL), output, reason, 1600);
    }
}
