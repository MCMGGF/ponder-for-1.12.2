package net.createmod.ponder.foundation.ui;

import java.nio.FloatBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** A ground-model ItemStack at its independent actor position, not a GUI icon or world entity. */
final class PreviewItemRenderer {

    private final Access access;

    PreviewItemRenderer() {
        this(new NativeAccess());
    }

    PreviewItemRenderer(Access access) {
        this.access = access;
    }

    void render(ItemStack stack, Vec3d position, Vec3d rotation, float bob, float fade) {
        if (stack == null || stack.isEmpty() || position == null || rotation == null || !Float.isFinite(fade)
            || fade <= 0F || !Float.isFinite(bob) || !finite(position) || !finite(rotation)) {
            return;
        }
        float alpha = Math.min(1F, fade);
        access.isolate(() -> {
            access.prepare(alpha);
            access.translate(position.x, position.y + bob, position.z);
            access.rotate((float) rotation.y, 0F, 1F, 0F);
            access.rotate((float) rotation.x, 1F, 0F, 0F);
            access.rotate((float) rotation.z, 0F, 0F, 1F);
            access.draw(stack.copy(), alpha);
        });
    }

    private static boolean finite(Vec3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    interface Access {
        void isolate(Runnable render);
        void prepare(float alpha);
        void translate(double x, double y, double z);
        void rotate(float degrees, float x, float y, float z);
        void draw(ItemStack stack, float alpha);
    }

    private static final class NativeAccess implements Access {
        public void isolate(Runnable render) {
            PreviewNativeRenderGuard.run(() -> {
                GL11.glPushAttrib(GL11.GL_LIGHTING_BIT | GL11.GL_TEXTURE_BIT);
                try {
                    render.run();
                } finally {
                    GL11.glPopAttrib();
                }
            });
        }

        public void prepare(float alpha) {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(alpha >= 1F);
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.01F * alpha);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.disableCull();
            GlStateManager.disableFog();
            RenderHelper.enableStandardItemLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
            GlStateManager.color(1F, 1F, 1F, 1F);
        }

        public void translate(double x, double y, double z) { GlStateManager.translate(x, y, z); }
        public void rotate(float degrees, float x, float y, float z) { GlStateManager.rotate(degrees, x, y, z); }

        public void draw(ItemStack stack, float alpha) {
            // Item vertex colors override glColor alpha. Modulate texture alpha while
            // retaining tint RGB, and restore the entire texture environment afterward.
            if (alpha < 1F) {
                GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
                GlStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
                GlStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_MODULATE);
                GlStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL11.GL_TEXTURE);
                GlStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_RGB, GL13.GL_PREVIOUS);
                GlStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
                GlStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
                GlStateManager.glTexEnvf(GL11.GL_TEXTURE_ENV, GL13.GL_RGB_SCALE, 1F);
                GlStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_MODULATE);
                GlStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
                GlStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA, GL13.GL_CONSTANT);
                GlStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
                GlStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_ALPHA, GL11.GL_SRC_ALPHA);
                FloatBuffer constant = BufferUtils.createFloatBuffer(4);
                constant.put(new float[] { 1F, 1F, 1F, alpha }).flip();
                GlStateManager.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, constant);
            }
            Minecraft.getMinecraft().getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.GROUND);
        }
    }
}
