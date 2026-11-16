package net.createmod.ponder.foundation.ui;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import net.createmod.ponder.foundation.content.mmce.MmceStaticModelMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Ponder-owned immediate submission of immutable cutout geometry, without a native renderer. */
final class PreviewStaticModelRenderer {

    private static final PreviewGlQueries QUERIES = PreviewGlQueries.runtime();
    private Tessellator tessellator;

    void draw(Minecraft minecraft, MmceStaticModelMesh mesh, ResourceLocation texture, Runnable transform) {
        State previous = new State();
        boolean matrixPushed = false;
        boolean clientAttributesPushed = false;
        boolean complete = false;
        try {
            GlStateManager.pushMatrix();
            matrixPushed = true;
            transform.run();
            GlStateManager.enableDepth();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.depthMask(true);
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.disableBlend();
            GlStateManager.disableCull();
            GlStateManager.disableFog();
            GlStateManager.disableLighting();
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D();
            minecraft.getTextureManager().bindTexture(texture);
            GlStateManager.color(1F, 1F, 1F, 1F);
            if (tessellator == null) {
                tessellator = new Tessellator(4096);
            }
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
            mesh.forEachVertex((x, y, z, u, v, nx, ny, nz) -> {
                float shade = Math.max(0.55F, Math.min(1F, 0.78F + ny * 0.16F - nx * 0.08F + nz * 0.05F));
                buffer.pos(x, y, z).tex(u, v).color(shade, shade, shade, 1F).normal(nx, ny, nz).endVertex();
            });
            GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            clientAttributesPushed = true;
            tessellator.draw();
            complete = true;
        } finally {
            if (!complete) {
                tessellator = null;
            }
            try {
                if (clientAttributesPushed) {
                    GL11.glPopClientAttrib();
                }
            } finally {
                try {
                    if (matrixPushed) {
                        GlStateManager.popMatrix();
                    }
                } finally {
                    previous.restore();
                }
            }
        }
    }

    void clear() {
        tessellator = null;
    }

    private static final class State {
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        private final boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        private final int alphaFunction = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        private final float alphaReference = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
        private final boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        private final int shading = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        private final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        private final IntBuffer scissorBox = BufferUtils.createIntBuffer(PreviewGlQueries.VECTOR_CAPACITY);
        private final FloatBuffer color = BufferUtils.createFloatBuffer(PreviewGlQueries.VECTOR_CAPACITY);
        private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final int clientActiveTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        private final TextureState diffuse;
        private final TextureState lightmap;

        private State() {
            QUERIES.integers(GL11.GL_SCISSOR_BOX, scissorBox);
            QUERIES.floats(GL11.GL_CURRENT_COLOR, color);
            diffuse = new TextureState(OpenGlHelper.defaultTexUnit);
            lightmap = new TextureState(OpenGlHelper.lightmapTexUnit);
            GlStateManager.setActiveTexture(activeTexture);
        }

        private void restore() {
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            GlStateManager.depthMask(depthMask);
            GlStateManager.depthFunc(depthFunction);
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            GlStateManager.alphaFunc(alphaFunction, alphaReference);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            if (fog) GlStateManager.enableFog(); else GlStateManager.disableFog();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            GlStateManager.shadeModel(shading);
            GL11.glScissor(scissorBox.get(0), scissorBox.get(1), scissorBox.get(2), scissorBox.get(3));
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            diffuse.restore();
            lightmap.restore();
            GlStateManager.setActiveTexture(activeTexture);
            OpenGlHelper.setClientActiveTexture(clientActiveTexture);
            GlStateManager.resetColor();
            GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
        }
    }

    private static final class TextureState {
        private final int unit;
        private final int binding;
        private final boolean enabled;

        private TextureState(int unit) {
            this.unit = unit;
            GlStateManager.setActiveTexture(unit);
            binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            enabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        }

        private void restore() {
            GlStateManager.setActiveTexture(unit);
            GlStateManager.bindTexture(binding);
            if (enabled) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
        }
    }
}
