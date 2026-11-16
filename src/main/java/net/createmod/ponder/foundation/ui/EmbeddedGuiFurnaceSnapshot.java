package net.createmod.ponder.foundation.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

/** Uses the vanilla atlas and authored progress, without a player or container. */
final class EmbeddedGuiFurnaceSnapshot extends TextureGuiSnapshot {

    static final EmbeddedGuiFurnaceSnapshot INSTANCE = new EmbeddedGuiFurnaceSnapshot();

    private EmbeddedGuiFurnaceSnapshot() {
        super(new ResourceLocation("minecraft", "textures/gui/container/furnace.png"),
            176, 166, 256, 256);
    }

    @Override
    protected void drawContents(Minecraft mc, float currentTick) {
        int cycle = Math.floorMod((int) currentTick, 200);
        if (cycle < 160) {
            int flame = (160 - cycle) * 13 / 160;
            drawRegion(56, 48 - flame, 176, 12 - flame, 14, flame + 1);
        }
        drawRegion(79, 34, 176, 14, cycle % 100 * 24 / 100 + 1, 16);
        String title = I18n.format("container.furnace");
        mc.fontRenderer.drawString(title, (176 - mc.fontRenderer.getStringWidth(title)) / 2, 6, 0x404040);
        mc.fontRenderer.drawString(I18n.format("container.inventory"), 8, 72, 0x404040);
    }
}
