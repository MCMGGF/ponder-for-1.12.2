package net.createmod.ponder.foundation.ui;

import net.createmod.ponder.Ponder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** Three readable inventory counters matching the authored display-world NBT. */
public final class VanillaLogisticsSnapshot extends TextureGuiSnapshot {

    public enum Stage {
        LOADED(2, 0, 0, false), IN_HOPPER(1, 1, 0, false), TRANSFERRED(1, 0, 1, false),
        LOCKED(1, 0, 1, true), RESUMED(0, 1, 1, false), COMPLETE(0, 0, 2, false);

        final int[] counts;
        final boolean locked;

        Stage(int input, int hopper, int output, boolean locked) {
            this.counts = new int[] { input, hopper, output };
            this.locked = locked;
        }
    }

    private final Stage stage;

    private VanillaLogisticsSnapshot(Stage stage) {
        super(new ResourceLocation("minecraft", "textures/gui/container/hopper.png"), 176, 76, 256, 256);
        this.stage = stage;
    }

    public static ResourceLocation register(Stage stage) {
        ResourceLocation id = Ponder.asResource("gui_snapshot/vanilla/logistics_"
            + stage.name().toLowerCase(java.util.Locale.ROOT));
        PonderGuiSnapshotRegistry.register(id, declaredSnapshot(stage));
        return id;
    }

    public Stage getStage() { return stage; }

    public static PonderGuiSnapshotRegistry.Snapshot declaredSnapshot(Stage stage) {
        if (stage == null) throw new IllegalArgumentException("A detached logistics stage is required");
        return PonderGuiSnapshotRegistry.Snapshot.liveRenderer(176, 76, false, new VanillaLogisticsSnapshot(stage));
    }

    @Override
    protected void drawContents(Minecraft mc, float tick) {
        Gui.drawRect(0, 0, 176, 76, 0xFF242A2B);
        mc.fontRenderer.drawString(stage.locked ? "漏斗已锁定" : "圆石数量", 8, 5, 0xFFFFFF);
        String[] labels = { "输入箱", "漏斗", "输出箱" };
        for (int i = 0; i < 3; i++) {
            int x = 9 + i * 56;
            Gui.drawRect(x, 22, x + 34, 46, 0xFF515859);
            mc.fontRenderer.drawString(labels[i], x, 57, 0xFFFFFF);
            mc.fontRenderer.drawString(Integer.toString(stage.counts[i]), x + 23, 30, 0xFFFFFF);
        }
        mc.fontRenderer.drawString(stage.locked ? "x" : ">", 47, 30, stage.locked ? 0xFF6868 : 0x79D3AF);
        mc.fontRenderer.drawString(stage.locked ? "x" : ">", 103, 30, stage.locked ? 0xFF6868 : 0x79D3AF);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        try {
            for (int i = 0; i < 3; i++) {
                mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(Blocks.COBBLESTONE), 11 + i * 56, 26);
            }
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
        }
    }
}
