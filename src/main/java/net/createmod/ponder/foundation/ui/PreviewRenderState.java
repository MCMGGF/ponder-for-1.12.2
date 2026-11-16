package net.createmod.ponder.foundation.ui;

import java.nio.FloatBuffer;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Snapshotted once per preview and around actual native TileEntity calls only. */
final class PreviewRenderState {

    private static final int[] CAPABILITIES = {
        GL11.GL_DEPTH_TEST, GL11.GL_ALPHA_TEST, GL11.GL_BLEND, GL11.GL_CULL_FACE,
        GL11.GL_LIGHTING, GL11.GL_LIGHT0, GL11.GL_LIGHT1, GL11.GL_COLOR_MATERIAL,
        GL11.GL_NORMALIZE, 32826, GL11.GL_FOG
    };
    private final Access access;
    private final boolean[] enabled = new boolean[CAPABILITIES.length];
    private final boolean depthMask;
    private final int depthFunction;
    private final int shadeModel;
    private final int alphaFunction;
    private final float alphaReference;
    private final int sourceRgb;
    private final int destinationRgb;
    private final int sourceAlpha;
    private final int destinationAlpha;
    private final float lightX;
    private final float lightY;
    private final float[] color;
    private final int activeTexture;
    private final int clientActiveTexture;
    private final int matrixMode;
    private final TextureState diffuse;
    private final TextureState lightmap;

    PreviewRenderState() {
        this(new NativeAccess());
    }

    PreviewRenderState(Access access) {
        this.access = access;
        for (int i = 0; i < CAPABILITIES.length; i++) {
            enabled[i] = access.enabled(CAPABILITIES[i]);
        }
        depthMask = access.integer(GL11.GL_DEPTH_WRITEMASK) != 0;
        depthFunction = access.integer(GL11.GL_DEPTH_FUNC);
        shadeModel = access.integer(GL11.GL_SHADE_MODEL);
        alphaFunction = access.integer(GL11.GL_ALPHA_TEST_FUNC);
        alphaReference = access.decimal(GL11.GL_ALPHA_TEST_REF);
        sourceRgb = access.integer(32969);
        destinationRgb = access.integer(32968);
        sourceAlpha = access.integer(32971);
        destinationAlpha = access.integer(32970);
        lightX = access.lightmapCoordinate(false);
        lightY = access.lightmapCoordinate(true);
        color = access.color();
        activeTexture = access.integer(GL13.GL_ACTIVE_TEXTURE);
        clientActiveTexture = access.integer(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        matrixMode = access.integer(GL11.GL_MATRIX_MODE);
        try {
            diffuse = new TextureState(access.textureUnit(false));
            lightmap = new TextureState(access.textureUnit(true));
        } finally {
            access.activeTexture(activeTexture);
        }
    }

    static void runIsolated(Runnable render) {
        runIsolated(new PreviewRenderState(), render);
    }

    static void runIsolated(PreviewRenderState previous, Runnable render) {
        try {
            render.run();
        } finally {
            previous.restore();
        }
    }

    static void runOverlay(Runnable render) {
        runOverlay(new NativeAccess(), render);
    }

    static void runOverlay(Access access, Runnable render) {
        runIsolated(new PreviewRenderState(access), () -> {
            access.enabled(GL11.GL_DEPTH_TEST, false);
            access.depthMask(false);
            access.enabled(GL11.GL_LIGHTING, false);
            access.enabled(GL11.GL_FOG, false);
            render.run();
        });
    }

    void restore() {
        for (int i = 0; i < CAPABILITIES.length; i++) {
            access.enabled(CAPABILITIES[i], enabled[i]);
        }
        access.depthMask(depthMask);
        access.depthFunction(depthFunction);
        access.alphaFunction(alphaFunction, alphaReference);
        access.blendFunction(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
        access.shadeModel(shadeModel);
        access.lightmap(lightX, lightY);
        diffuse.restore();
        lightmap.restore();
        access.activeTexture(activeTexture);
        access.clientActiveTexture(clientActiveTexture);
        access.matrixMode(matrixMode);
        access.color(color);
    }

    private final class TextureState {
        private final int unit;
        private final int binding;
        private final boolean enabled;

        private TextureState(int unit) {
            this.unit = unit;
            access.activeTexture(unit);
            binding = access.integer(GL11.GL_TEXTURE_BINDING_2D);
            enabled = access.enabled(GL11.GL_TEXTURE_2D);
        }

        private void restore() {
            access.activeTexture(unit);
            access.bindTexture(binding);
            access.enabled(GL11.GL_TEXTURE_2D, enabled);
        }
    }

    interface Writes {
        void enabled(int capability, boolean value);
        void color(float[] value);
        void depthMask(boolean value);
        void depthFunction(int value);
        void alphaFunction(int function, float reference);
        void blendFunction(int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha);
        void shadeModel(int value);
        void activeTexture(int value);
        void clientActiveTexture(int value);
        void bindTexture(int value);
        void lightmap(float x, float y);
        void matrixMode(int value);
    }

    interface Access extends Writes {
        boolean enabled(int capability);
        int integer(int parameter);
        float decimal(int parameter);
        float[] color();
        int textureUnit(boolean lightmap);
        float lightmapCoordinate(boolean y);
    }

    /** Raw writes repair the driver even when Minecraft's cache already equals the saved value. */
    static class ForcingAccess implements Access {
        private final Access raw;
        private final Writes cached;

        ForcingAccess(Access raw, Writes cached) {
            this.raw = raw;
            this.cached = cached;
        }

        public boolean enabled(int capability) { return raw.enabled(capability); }
        public int integer(int parameter) { return raw.integer(parameter); }
        public float decimal(int parameter) { return raw.decimal(parameter); }
        public float[] color() { return raw.color(); }
        public int textureUnit(boolean lightmap) { return raw.textureUnit(lightmap); }
        public float lightmapCoordinate(boolean y) { return raw.lightmapCoordinate(y); }

        public void enabled(int capability, boolean value) {
            raw.enabled(capability, value);
            cached.enabled(capability, value);
        }

        public void color(float[] value) {
            raw.color(value);
            cached.color(value);
        }

        public void depthMask(boolean value) {
            raw.depthMask(value);
            cached.depthMask(value);
        }

        public void depthFunction(int value) {
            raw.depthFunction(value);
            cached.depthFunction(value);
        }

        public void alphaFunction(int function, float reference) {
            raw.alphaFunction(function, reference);
            cached.alphaFunction(function, reference);
        }

        public void blendFunction(int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha) {
            raw.blendFunction(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            cached.blendFunction(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
        }

        public void shadeModel(int value) {
            raw.shadeModel(value);
            cached.shadeModel(value);
        }

        public void activeTexture(int value) {
            raw.activeTexture(value);
            cached.activeTexture(value);
        }

        public void clientActiveTexture(int value) {
            raw.clientActiveTexture(value);
            cached.clientActiveTexture(value);
        }

        public void bindTexture(int value) {
            raw.bindTexture(value);
            cached.bindTexture(value);
        }

        public void lightmap(float x, float y) {
            raw.lightmap(x, y);
            cached.lightmap(x, y);
        }

        public void matrixMode(int value) {
            raw.matrixMode(value);
            cached.matrixMode(value);
        }
    }

    private static final class NativeAccess extends ForcingAccess {
        private NativeAccess() {
            super(new RawAccess(), new CachedAccess());
        }
    }

    private static final class RawAccess implements Access {
        private static final PreviewGlQueries QUERIES = PreviewGlQueries.runtime();

        public boolean enabled(int capability) {
            return GL11.glIsEnabled(capability);
        }

        public void enabled(int capability, boolean value) {
            if (value) GL11.glEnable(capability); else GL11.glDisable(capability);
        }

        public int integer(int parameter) { return GL11.glGetInteger(parameter); }
        public float decimal(int parameter) { return GL11.glGetFloat(parameter); }

        public float[] color() {
            FloatBuffer color = BufferUtils.createFloatBuffer(PreviewGlQueries.VECTOR_CAPACITY);
            QUERIES.floats(GL11.GL_CURRENT_COLOR, color);
            return new float[] { color.get(0), color.get(1), color.get(2), color.get(3) };
        }

        public void color(float[] value) { GL11.glColor4f(value[0], value[1], value[2], value[3]); }
        public void depthMask(boolean value) { GL11.glDepthMask(value); }
        public void depthFunction(int value) { GL11.glDepthFunc(value); }
        public void alphaFunction(int function, float reference) { GL11.glAlphaFunc(function, reference); }

        public void blendFunction(int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha) {
            OpenGlHelper.glBlendFunc(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
        }

        public void shadeModel(int value) { GL11.glShadeModel(value); }

        public int textureUnit(boolean lightmap) {
            return lightmap ? OpenGlHelper.lightmapTexUnit : OpenGlHelper.defaultTexUnit;
        }

        public void activeTexture(int value) { OpenGlHelper.setActiveTexture(value); }
        public void clientActiveTexture(int value) { OpenGlHelper.setClientActiveTexture(value); }
        public void bindTexture(int value) { GL11.glBindTexture(GL11.GL_TEXTURE_2D, value); }

        public float lightmapCoordinate(boolean y) {
            return y ? OpenGlHelper.lastBrightnessY : OpenGlHelper.lastBrightnessX;
        }

        public void lightmap(float x, float y) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, x, y);
        }

        public void matrixMode(int value) { GL11.glMatrixMode(value); }
    }

    private static final class CachedAccess implements Writes {
        public void enabled(int capability, boolean value) {
            switch (capability) {
                case GL11.GL_DEPTH_TEST:
                    if (value) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
                    break;
                case GL11.GL_ALPHA_TEST:
                    if (value) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
                    break;
                case GL11.GL_BLEND:
                    if (value) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
                    break;
                case GL11.GL_CULL_FACE:
                    if (value) GlStateManager.enableCull(); else GlStateManager.disableCull();
                    break;
                case GL11.GL_LIGHTING:
                    if (value) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
                    break;
                case GL11.GL_LIGHT0:
                case GL11.GL_LIGHT1:
                    if (value) GlStateManager.enableLight(capability - GL11.GL_LIGHT0);
                    else GlStateManager.disableLight(capability - GL11.GL_LIGHT0);
                    break;
                case GL11.GL_COLOR_MATERIAL:
                    if (value) GlStateManager.enableColorMaterial(); else GlStateManager.disableColorMaterial();
                    break;
                case GL11.GL_NORMALIZE:
                    if (value) GlStateManager.enableNormalize(); else GlStateManager.disableNormalize();
                    break;
                case 32826:
                    if (value) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal();
                    break;
                case GL11.GL_FOG:
                    if (value) GlStateManager.enableFog(); else GlStateManager.disableFog();
                    break;
                case GL11.GL_TEXTURE_2D:
                    if (value) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported preview capability: " + capability);
            }
        }

        public void color(float[] value) {
            GlStateManager.resetColor();
            GlStateManager.color(value[0], value[1], value[2], value[3]);
        }

        public void depthMask(boolean value) {
            GlStateManager.depthMask(value);
        }

        public void depthFunction(int value) {
            GlStateManager.depthFunc(value);
        }

        public void alphaFunction(int function, float reference) {
            GlStateManager.alphaFunc(function, reference);
        }

        public void blendFunction(int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha) {
            GlStateManager.tryBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
        }

        public void shadeModel(int value) {
            GlStateManager.shadeModel(value);
        }

        public void activeTexture(int value) {
            GlStateManager.setActiveTexture(value);
        }

        public void clientActiveTexture(int value) {
            OpenGlHelper.setClientActiveTexture(value);
        }

        public void bindTexture(int value) {
            GlStateManager.bindTexture(value);
        }

        public void lightmap(float x, float y) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, x, y);
        }

        public void matrixMode(int value) {
            GlStateManager.matrixMode(value);
        }
    }
}
