package net.createmod.ponder.foundation.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.createmod.ponder.foundation.ui.DetachedPonderWorld;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.world.World;

/** Captures the active recipe result without opening or mutating a real container. */
public final class VanillaTutorialRecipes {

    private VanillaTutorialRecipes() {
    }

    public static RecipePreview crafting() {
        try {
            return crafting(CraftingManager.REGISTRY, new DetachedPonderWorld());
        } catch (RuntimeException | LinkageError failure) {
            return RecipePreview.unavailable(craftingInputs(), ItemStack.EMPTY,
                "当前合成配方无法安全读取，未演示产物。");
        }
    }

    static RecipePreview crafting(Iterable<IRecipe> recipes, World previewWorld) {
        List<ItemStack> inputs = craftingInputs();
        InventoryCrafting inventory = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return false;
            }
        }, 3, 3);
        for (int slot = 0; slot < inputs.size(); slot++) {
            inventory.setInventorySlotContents(slot, inputs.get(slot).copy());
        }
        try {
            // Match the registry's order, including pack overrides; never synthesize a fallback recipe.
            for (IRecipe recipe : recipes) {
                if (recipe.matches(inventory, previewWorld)) {
                    ItemStack result = recipe.getCraftingResult(inventory);
                    if (result == null || result.isEmpty()) {
                        break;
                    }
                    return RecipePreview.available(inputs, ItemStack.EMPTY, result);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            return RecipePreview.unavailable(inputs, ItemStack.EMPTY,
                "当前配方查询失败，未演示未经确认的产物。");
        }
        return RecipePreview.unavailable(inputs, ItemStack.EMPTY,
            "当前实例没有与这四块橡木木板匹配的合成配方。");
    }

    public static RecipePreview smelting() {
        List<ItemStack> input = Collections.singletonList(new ItemStack(Blocks.IRON_ORE));
        ItemStack fuel = new ItemStack(Items.COAL);
        try {
            return smelting(input.get(0), fuel, FurnaceRecipes.instance().getSmeltingResult(input.get(0)),
                TileEntityFurnace.getItemBurnTime(fuel));
        } catch (RuntimeException | LinkageError failure) {
            return RecipePreview.unavailable(input, fuel, "当前熔炼配方无法安全读取，未演示产物。");
        }
    }

    static RecipePreview smelting(ItemStack input, ItemStack fuel, ItemStack output, int burnTicks) {
        List<ItemStack> inputs = Collections.singletonList(input);
        if (input == null || input.isEmpty() || output == null || output.isEmpty()) {
            return RecipePreview.unavailable(inputs, fuel, "当前实例没有铁矿石的熔炉配方。");
        }
        if (fuel == null || fuel.isEmpty() || burnTicks < 200) {
            return RecipePreview.unavailable(inputs, fuel, "当前燃料不足以完成一次原版熔炼，未演示产物。");
        }
        return new RecipePreview(inputs, fuel, output, null, burnTicks);
    }

    private static List<ItemStack> craftingInputs() {
        List<ItemStack> input = new ArrayList<ItemStack>(9);
        for (int slot = 0; slot < 9; slot++) {
            input.add(slot == 0 || slot == 1 || slot == 3 || slot == 4
                ? new ItemStack(Blocks.PLANKS, 1, 0) : ItemStack.EMPTY);
        }
        return input;
    }

    public static final class RecipePreview {
        private final List<ItemStack> inputs;
        private final ItemStack fuel;
        private final ItemStack output;
        private final String unavailableReason;
        private final int fuelBurnTicks;

        private RecipePreview(List<ItemStack> inputs, ItemStack fuel, ItemStack output, String reason, int fuelBurnTicks) {
            this.inputs = copyInputs(inputs);
            this.fuel = copy(fuel);
            this.output = copy(output);
            this.unavailableReason = reason;
            this.fuelBurnTicks = Math.max(0, fuelBurnTicks);
        }

        static RecipePreview available(List<ItemStack> inputs, ItemStack fuel, ItemStack output) {
            return new RecipePreview(inputs, fuel, output, null, 0);
        }

        static RecipePreview unavailable(List<ItemStack> inputs, ItemStack fuel, String reason) {
            return new RecipePreview(inputs, fuel, ItemStack.EMPTY, reason, 0);
        }

        /** Restores recorded draw-only slot data, not a recipe or a real crafting operation. */
        public static RecipePreview fromSnapshot(List<ItemStack> inputs, ItemStack fuel, ItemStack output,
            String reason, int fuelBurnTicks) {
            if (inputs == null || inputs.size() > 9 || fuelBurnTicks < 0 || reason != null && reason.length() > 4096)
                throw new IllegalArgumentException("Invalid bounded recipe snapshot data");
            return new RecipePreview(inputs, fuel, output, reason, fuelBurnTicks);
        }

        public boolean isAvailable() {
            return unavailableReason == null && !output.isEmpty();
        }

        public List<ItemStack> getInputs() {
            return copyInputs(inputs);
        }

        public ItemStack getFuel() {
            return fuel.copy();
        }

        public ItemStack getOutput() {
            return output.copy();
        }

        public String getUnavailableReason() {
            return unavailableReason;
        }

        public boolean hasRemainingFuel() {
            return isAvailable() && fuelBurnTicks > 200;
        }

        public int getFuelBurnTicks() { return fuelBurnTicks; }

        private static List<ItemStack> copyInputs(List<ItemStack> source) {
            List<ItemStack> result = new ArrayList<ItemStack>(source.size());
            for (ItemStack item : source) {
                result.add(copy(item));
            }
            return Collections.unmodifiableList(result);
        }

        private static ItemStack copy(ItemStack stack) {
            return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
    }
}
