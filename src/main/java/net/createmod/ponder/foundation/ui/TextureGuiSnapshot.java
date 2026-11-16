package net.createmod.ponder.foundation.ui;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Draw-only atlas adapter. It never constructs or initializes a native container. */
class TextureGuiSnapshot implements PonderGuiSnapshotRegistry.SnapshotRenderer {

    private final ResourceLocation texture;
    private final int logicalWidth;
    private final int logicalHeight;
    private final int textureWidth;
    private final int textureHeight;
    private Boolean available;

    TextureGuiSnapshot(ResourceLocation texture, int logicalWidth, int logicalHeight,
        int textureWidth, int textureHeight) {
        this.texture = texture;
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    @Override
    public void render(int x, int y, int width, int height, float currentTick, float fade) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || width <= 0 || height <= 0) {
            return;
        }
        if (available == null) {
            try (IResource ignored = mc.getResourceManager().getResource(texture)) {
                available = Boolean.TRUE;
            } catch (IOException failure) {
                available = Boolean.FALSE;
            }
        }
        if (!available.booleanValue()) {
            SandboxTriggeredBlockGuiSnapshot.renderUnavailable(x, y, width, height, texture.getPath());
            return;
        }
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean textured = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 0);
            GlStateManager.scale((float) width / logicalWidth, (float) height / logicalHeight, 1);
            GlStateManager.disableDepth();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1, 1, 1, Math.max(0, Math.min(1, fade)));
            mc.getTextureManager().bindTexture(texture);
            drawRegion(0, 0, 0, 0, logicalWidth, logicalHeight);
            drawContents(mc, currentTick);
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.bindTexture(previousTexture);
            if (depth) { GlStateManager.enableDepth(); } else { GlStateManager.disableDepth(); }
            if (lighting) { GlStateManager.enableLighting(); } else { GlStateManager.disableLighting(); }
            if (blend) { GlStateManager.enableBlend(); } else { GlStateManager.disableBlend(); }
            if (alpha) { GlStateManager.enableAlpha(); } else { GlStateManager.disableAlpha(); }
            if (textured) { GlStateManager.enableTexture2D(); } else { GlStateManager.disableTexture2D(); }
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    protected void drawContents(Minecraft minecraft, float currentTick) {
    }

    protected final void drawRegion(int x, int y, int u, int v, int width, int height) {
        Gui.drawScaledCustomSizeModalRect(x, y, u, v, width, height, width, height,
            textureWidth, textureHeight);
    }

    @Override
    public void reset() {
        available = null;
    }
}
