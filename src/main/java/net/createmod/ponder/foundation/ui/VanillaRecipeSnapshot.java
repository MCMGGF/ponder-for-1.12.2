package net.createmod.ponder.foundation.ui;

import java.util.List;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.content.VanillaTutorialRecipes.RecipePreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** Draw-only vanilla slots, never a native GUI, inventory, tile entity or screen. */
public final class VanillaRecipeSnapshot extends TextureGuiSnapshot {

    private final RecipePreview recipe;
    private final boolean furnace;
    private final boolean showResult;

    private VanillaRecipeSnapshot(RecipePreview recipe, boolean furnace, boolean showResult) {
        super(new ResourceLocation("minecraft", "textures/gui/container/" + (furnace ? "furnace" : "crafting_table")
            + ".png"), 176, 76, 256, 256);
        this.recipe = recipe;
        this.furnace = furnace;
        this.showResult = showResult;
    }

    public static ResourceLocation register(RecipePreview recipe, boolean furnace, boolean showResult) {
        ResourceLocation id = Ponder.asResource("gui_snapshot/vanilla/" + (furnace ? "furnace" : "crafting")
            + (showResult ? "_result" : "_input"));
        // Exactly four slots, replaced on recompilation; no per-scene or per-frame registry growth.
        PonderGuiSnapshotRegistry.register(id, declaredSnapshot(recipe, furnace, showResult));
        return id;
    }

    public RecipePreview getRecipePreview() { return recipe; }
    public boolean isFurnace() { return furnace; }
    public boolean isShowingResult() { return showResult; }

    public static PonderGuiSnapshotRegistry.Snapshot declaredSnapshot(RecipePreview recipe, boolean furnace, boolean showResult) {
        if (recipe == null) throw new IllegalArgumentException("A detached recipe snapshot is required");
        return PonderGuiSnapshotRegistry.Snapshot.liveRenderer(176, 76, false, new VanillaRecipeSnapshot(recipe, furnace, showResult));
    }

    @Override
    protected void drawContents(Minecraft mc, float currentTick) {
        List<ItemStack> inputs = recipe.getInputs();
        ItemStack output = displayedOutput();
        if (furnace && showResult && recipe.isAvailable()) {
            drawRegion(79, 34, 176, 14, 24, 16);
            if (recipe.hasRemainingFuel()) {
                drawRegion(56, 37, 176, 1, 14, 12);
            }
        }
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        try {
            if (furnace) {
                if (!showResult && !inputs.isEmpty()) {
                    drawItem(mc, inputs.get(0), 56, 17);
                }
                if (!showResult) {
                    drawItem(mc, recipe.getFuel(), 56, 53);
                }
                drawItem(mc, output, 116, 35);
            } else {
                for (int slot = 0; slot < Math.min(9, inputs.size()); slot++) {
                    drawItem(mc, inputs.get(slot), 30 + slot % 3 * 18, 17 + slot / 3 * 18);
                }
                drawItem(mc, output, 124, 35);
            }
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
        }
    }

    ItemStack displayedOutput() {
        return recipe.isAvailable() && (!furnace || showResult) ? recipe.getOutput() : ItemStack.EMPTY;
    }

    private static void drawItem(Minecraft mc, ItemStack stack, int x, int y) {
        if (!stack.isEmpty()) {
            mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
            mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, stack, x, y, null);
        }
    }
}
