package net.createmod.ponder.foundation.ui;

import java.io.IOException;
import java.util.Objects;
import net.createmod.ponder.foundation.ui.GuiDemoDefinition.Bounds;
import net.createmod.ponder.foundation.ui.GuiDemoDefinition.Pointer;
import net.createmod.ponder.foundation.ui.GuiDemoDefinition.State;
import net.createmod.ponder.foundation.ui.GuiDemoDefinition.Widget;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.ResettableSnapshotProvider;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.Snapshot;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.SnapshotProvider;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.SnapshotRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Draws the resource-pack definition as part of the scene's existing GUI overlay pass. */
public final class GuiDemoSnapshot implements ResettableSnapshotProvider, SnapshotRenderer {
    private static final int INK = 0xFF192126;
    private static final int PANEL = 0xFFD9DDDE;
    private static final int ACCENT = 0xFF38644A;
    private static final int HIGHLIGHT = 0xFFFFC34D;

    private final GuiDemoDefinition definition;
    private final int startTick;
    private final Snapshot snapshot;
    private IResourceManager checkedResources;
    private boolean hasBackground;

    private GuiDemoSnapshot(GuiDemoDefinition definition, int startTick) {
        this.definition = Objects.requireNonNull(definition);
        this.startTick = startTick;
        snapshot = Snapshot.liveRenderer(definition.width(), definition.height(), false, this);
    }

    public static SnapshotProvider provider(GuiDemoDefinition definition, int startTick) {
        return new GuiDemoSnapshot(definition, startTick);
    }

    @Override
    public Snapshot provide(float tick) {
        return Float.isFinite(tick) && tick >= startTick ? snapshot : null;
    }

    @Override
    public void reset() {
        checkedResources = null;
        hasBackground = false;
    }

    @Override
    public void render(int x, int y, int width, int height, float tick, float fade) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || width < 1 || height < 1 || provide(tick) == null || !(fade >= 4F / 255)) {
            return;
        }
        float localTick = tick - startTick;
        int canvasWidth = definition.width();
        int canvasHeight = definition.height();
        float opacity = Math.min(1, fade);
        PreviewNativeRenderGuard.run(() -> {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            try {
                float scale = Math.min(width / (float) canvasWidth, height / (float) canvasHeight);
                GlStateManager.translate(x + (width - canvasWidth * scale) / 2F,
                    y + (height - canvasHeight * scale) / 2F, 0);
                GlStateManager.scale(scale, scale, 1);
                GlStateManager.disableDepth();
                GlStateManager.depthMask(false);
                GlStateManager.disableLighting();
                drawBackground(mc, opacity);
                for (Widget widget : definition.widgets()) {
                    State state = widget.at(localTick);
                    if (state.visible()) {
                        drawWidget(mc, widget, state, opacity);
                    }
                }
                Pointer pointer = definition.pointerAt(localTick);
                if (pointer != null) {
                    drawPointer(pointer, opacity);
                }
            } finally {
                GlStateManager.popMatrix();
            }
        });
    }

    private void drawBackground(Minecraft mc, float opacity) {
        ResourceLocation texture = definition.background();
        if (texture != null && backgroundAvailable(mc, texture)) {
            enableBlend();
            GlStateManager.disableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1, 1, 1, opacity);
            mc.getTextureManager().bindTexture(texture);
            int width = definition.width();
            int height = definition.height();
            Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, width, height, width, height);
            GlStateManager.color(1, 1, 1, 1);
        } else {
            rect(0, 0, definition.width(), definition.height(), PANEL, opacity);
        }
    }

    private boolean backgroundAvailable(Minecraft mc, ResourceLocation texture) {
        IResourceManager resources = mc.getResourceManager();
        if (resources != checkedResources) {
            checkedResources = resources;
            try (IResource ignored = resources.getResource(texture)) {
                hasBackground = true;
            } catch (IOException missing) {
                hasBackground = false;
            }
        }
        return hasBackground;
    }

    private static void drawWidget(Minecraft mc, Widget widget, State state, float opacity) {
        Bounds b = widget.bounds();
        int x = b.x(), y = b.y(), w = b.width(), h = b.height();
        int color = widget.color();
        switch (widget.kind()) {
            case SLOT -> {
                rect(x, y, w, h, INK, opacity);
                rect(x + 1, y + 1, w - 2, h - 2, color == 0 ? 0xFF949D9E : color, opacity);
                if (state.item() != null) {
                    drawItem(mc, state, x + (w - 16) / 2, y + (h - 16) / 2, opacity);
                }
            }
            case BUTTON, TAB -> {
                rect(x, y, w, h, INK, opacity);
                int fill = color == 0 ? 0xFF495D68 : color;
                rect(x + 1, y + 1, w - 2, h - 2, state.active() ? ACCENT : fill, opacity);
                drawText(mc, b, state.text(), 0xFFFFFFFF, true, opacity);
            }
            case PROGRESS -> {
                rect(x, y, w, h, INK, opacity);
                int progressWidth = Math.round(Math.max(0, w - 2) * state.value());
                rect(x + 1, y + 1, progressWidth, h - 2, color == 0 ? 0xFF7CCF90 : color, opacity);
            }
            case STATUS -> drawText(mc, b, state.text(), color == 0 ? INK : color, false, opacity);
            case HIGHLIGHT -> outline(b, color == 0 ? HIGHLIGHT : color, opacity);
        }
        if (state.highlighted() && widget.kind() != GuiDemoDefinition.Kind.HIGHLIGHT) {
            outline(b, HIGHLIGHT, opacity);
        }
    }

    private static void drawText(Minecraft mc, Bounds bounds, String text, int color, boolean centered, float opacity) {
        if (text.isEmpty() || bounds.width() <= 4 || bounds.height() < mc.fontRenderer.FONT_HEIGHT) {
            return;
        }
        String clipped = mc.fontRenderer.trimStringToWidth(text, bounds.width() - 4);
        int offset = centered ? (bounds.width() - mc.fontRenderer.getStringWidth(clipped)) / 2 : 2;
        int y = bounds.y() + (bounds.height() - mc.fontRenderer.FONT_HEIGHT) / 2;
        enableBlend();
        mc.fontRenderer.drawString(clipped, bounds.x() + offset, y, alpha(color, opacity));
    }

    private static void drawItem(Minecraft mc, State state, int x, int y, float opacity) {
        Item item = Item.REGISTRY.getObject(state.item());
        ItemStack stack = new ItemStack(item, state.count(), state.meta());
        if (stack.isEmpty()) {
            enableBlend();
            mc.fontRenderer.drawString("?", x + 5, y + 4, alpha(INK, opacity));
            return;
        }
        float z = mc.getRenderItem().zLevel;
        try {
            PreviewNativeRenderGuard.run(() -> {
                RenderHelper.enableGUIStandardItemLighting();
                GlStateManager.color(1, 1, 1, opacity);
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
                mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, stack, x, y, null);
            });
        } finally {
            mc.getRenderItem().zLevel = z;
        }
    }

    static void drawPointer(Pointer pointer, float opacity) {
        float x = pointer.x(), y = pointer.y();
        if (pointer.pressed()) {
            int left = Math.round(x) - 5;
            int top = Math.round(y) - 5;
            outline(new Bounds(left, top, 11, 11), HIGHLIGHT, opacity);
        }
        enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        triangle(x, y, 13, 9, INK, opacity);
        triangle(x + 1, y + 2, 9, 6, pointer.pressed() ? HIGHLIGHT : 0xFFFFFFFF, opacity);
        GlStateManager.enableTexture2D();
    }

    private static void triangle(float x, float y, int height, int width, int color, float opacity) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        int a = Math.round(255 * opacity);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder vertices = tessellator.getBuffer();
        vertices.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        vertices.pos(x, y, 0).color(r, g, b, a).endVertex();
        vertices.pos(x, y + height, 0).color(r, g, b, a).endVertex();
        vertices.pos(x + width, y + height * .68F, 0).color(r, g, b, a).endVertex();
        tessellator.draw();
    }

    private static void outline(Bounds b, int color, float opacity) {
        rect(b.x(), b.y(), b.width(), 1, color, opacity);
        rect(b.x(), b.y() + b.height() - 1, b.width(), 1, color, opacity);
        rect(b.x(), b.y() + 1, 1, b.height() - 2, color, opacity);
        rect(b.x() + b.width() - 1, b.y() + 1, 1, b.height() - 2, color, opacity);
    }

    private static void rect(int x, int y, int width, int height, int color, float opacity) {
        if (width > 0 && height > 0) {
            Gui.drawRect(x, y, x + width, y + height, alpha(color, opacity));
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    private static int alpha(int color, float opacity) {
        return (color & 0xFFFFFF) | Math.round((color >>> 24) * opacity) << 24;
    }

    private static void enableBlend() {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }
}
