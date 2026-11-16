package net.createmod.ponder.foundation.ui;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** A screen-space pass that does not inherit another mod's item, clip or coordinate state. */
public final class PonderScreenOverlay {

    private PonderScreenOverlay() {
    }

    public static void run(int width, int height, int displayWidth, int displayHeight, Runnable render) {
        run(new NativeAccess(), width, height, displayWidth, displayHeight, render);
    }

    static void run(Access access, int width, int height, int displayWidth, int displayHeight, Runnable render) {
        if (width <= 0 || height <= 0 || displayWidth <= 0 || displayHeight <= 0) {
            throw new IllegalArgumentException("Positive overlay and framebuffer dimensions are required");
        }
        access.isolate(() -> {
            access.pushAttributes();
            try {
                access.coordinates(width, height, () -> {
                    access.viewport(displayWidth, displayHeight);
                    access.scissor(false);
                    access.colorMask(true);
                    access.activeTexture(true);
                    access.texture(false);
                    access.activeTexture(false);
                    access.texture(true);
                    access.cull(false);
                    access.alpha();
                    access.blend();
                    access.white();
                    render.run();
                });
            } finally {
                access.popAttributes();
            }
        });
    }

    /** Called only for the first successful footer frame or an explicit diagnostic request. */
    public static Map<String, Object> snapshot() {
        PreviewGlQueries queries = PreviewGlQueries.runtime();
        Map<String, Object> state = new LinkedHashMap<String, Object>();
        state.put("depthTest", GL11.glIsEnabled(GL11.GL_DEPTH_TEST));
        state.put("depthWrite", GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK) != 0);
        state.put("depthFunction", GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        state.put("scissorTest", GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));
        state.put("scissor", integers(queries, GL11.GL_SCISSOR_BOX));
        state.put("viewport", integers(queries, GL11.GL_VIEWPORT));
        state.put("colorWrite", integers(queries, GL11.GL_COLOR_WRITEMASK));
        state.put("matrixMode", GL11.glGetInteger(GL11.GL_MATRIX_MODE));
        state.put("projection", floats(queries, GL11.GL_PROJECTION_MATRIX));
        state.put("modelView", floats(queries, GL11.GL_MODELVIEW_MATRIX));
        state.put("activeTexture", GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        state.put("activeTexture2d", GL11.glIsEnabled(GL11.GL_TEXTURE_2D));
        state.put("textureBinding", GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        state.put("lighting", GL11.glIsEnabled(GL11.GL_LIGHTING));
        state.put("fog", GL11.glIsEnabled(GL11.GL_FOG));
        state.put("blend", GL11.glIsEnabled(GL11.GL_BLEND));
        state.put("alphaTest", GL11.glIsEnabled(GL11.GL_ALPHA_TEST));
        return state;
    }

    private static List<Integer> integers(PreviewGlQueries queries, int name) {
        IntBuffer buffer = BufferUtils.createIntBuffer(PreviewGlQueries.VECTOR_CAPACITY);
        queries.integers(name, buffer);
        List<Integer> result = new ArrayList<Integer>();
        for (int index = 0; index < 4; index++) result.add(buffer.get(index));
        return result;
    }

    private static List<Float> floats(PreviewGlQueries queries, int name) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(PreviewGlQueries.VECTOR_CAPACITY);
        queries.floats(name, buffer);
        List<Float> result = new ArrayList<Float>();
        for (int index = 0; index < 16; index++) result.add(buffer.get(index));
        return result;
    }

    interface Access {
        void isolate(Runnable render);
        void pushAttributes();
        void popAttributes();
        void coordinates(int width, int height, Runnable render);
        void viewport(int width, int height);
        void scissor(boolean enabled);
        void colorMask(boolean enabled);
        void activeTexture(boolean lightmap);
        void texture(boolean enabled);
        void cull(boolean enabled);
        void alpha();
        void blend();
        void white();
    }

    private static final class NativeAccess implements Access {
        public void isolate(Runnable render) { PreviewRenderState.runOverlay(render); }

        public void pushAttributes() {
            GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_VIEWPORT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        }

        public void popAttributes() { GL11.glPopAttrib(); }

        public void coordinates(int width, int height, Runnable render) {
            try (PreviewModelViewGuard projection = new PreviewModelViewGuard(GL11.GL_PROJECTION);
                PreviewModelViewGuard modelView = new PreviewModelViewGuard()) {
                GlStateManager.matrixMode(GL11.GL_PROJECTION);
                GlStateManager.loadIdentity();
                GlStateManager.ortho(0, width, height, 0, 1000, 3000);
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                GlStateManager.loadIdentity();
                GlStateManager.translate(0, 0, -2000);
                render.run();
            }
        }

        public void viewport(int width, int height) { GL11.glViewport(0, 0, width, height); }

        public void scissor(boolean enabled) {
            if (enabled) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        public void colorMask(boolean enabled) { GL11.glColorMask(enabled, enabled, enabled, enabled); }

        public void activeTexture(boolean lightmap) {
            int unit = lightmap ? OpenGlHelper.lightmapTexUnit : OpenGlHelper.defaultTexUnit;
            GlStateManager.setActiveTexture(unit);
            OpenGlHelper.setActiveTexture(unit);
        }

        public void texture(boolean enabled) {
            if (enabled) {
                GlStateManager.enableTexture2D();
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            } else {
                GlStateManager.disableTexture2D();
                GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
        }

        public void cull(boolean enabled) {
            if (enabled) {
                GlStateManager.enableCull();
                GL11.glEnable(GL11.GL_CULL_FACE);
            } else {
                GlStateManager.disableCull();
                GL11.glDisable(GL11.GL_CULL_FACE);
            }
        }

        public void alpha() {
            GlStateManager.enableAlpha();
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        }

        public void blend() {
            GlStateManager.enableBlend();
            GL11.glEnable(GL11.GL_BLEND);
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        }

        public void white() {
            GlStateManager.resetColor();
            GlStateManager.color(1, 1, 1, 1);
            GL11.glColor4f(1, 1, 1, 1);
        }
    }
}
