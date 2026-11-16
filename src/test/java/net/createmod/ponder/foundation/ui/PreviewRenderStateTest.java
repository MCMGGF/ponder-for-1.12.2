package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

class PreviewRenderStateTest {

    @Test
    void overlayPassDisablesDepthAndRestoresStateAfterSuccessfulOrFailingCallbacks() {
        for (boolean fail : new boolean[] { false, true }) {
            FakeGl gl = new FakeGl(true);
            Map<String, Object> before = gl.snapshot();
            Runnable callback = () -> {
                assertFalse(gl.enabled(GL11.GL_DEPTH_TEST));
                assertEquals(0, gl.integer(GL11.GL_DEPTH_WRITEMASK));
                gl.poisonLikeNativeRenderer();
                if (fail) throw new IllegalStateException("snapshot failed");
            };
            if (fail) {
                assertThrows(IllegalStateException.class, () -> PreviewRenderState.runOverlay(gl, callback));
            } else {
                PreviewRenderState.runOverlay(gl, callback);
            }
            assertEquals(before, gl.snapshot());
        }
    }

    @Test
    void aSnapshotCannotLeaveDepthEnabledForFollowingCaptionText() {
        FakeGl gl = new FakeGl(true);
        PreviewRenderState.runOverlay(gl, () -> {
            PreviewRenderState.runOverlay(gl, gl::poisonLikeNativeRenderer);
            assertFalse(gl.enabled(GL11.GL_DEPTH_TEST));
            assertEquals(0, gl.integer(GL11.GL_DEPTH_WRITEMASK));
        });
    }

    @Test
    void captureDoesNotChangeTheCallersActiveTexture() {
        FakeGl gl = new FakeGl(true);
        gl.activeTexture(GL13.GL_TEXTURE1);
        Map<String, Object> before = gl.snapshot();
        new PreviewRenderState(gl);
        assertEquals(before, gl.snapshot());
    }

    @Test
    void portalStyleBlendPollutionIsRestoredForBothBlockPasses() {
        for (boolean transparent : new boolean[] { false, true }) {
            FakeGl gl = new FakeGl(transparent);
            Map<String, Object> before = gl.snapshot();
            PreviewRenderState.runIsolated(new PreviewRenderState(gl), gl::poisonLikeNativeRenderer);
            assertEquals(before, gl.snapshot());
        }
    }

    @Test
    void exceptionalTesrRestoresDepthBlendAlphaTexturesLightsColorAndMatrixMode() {
        FakeGl gl = new FakeGl(true);
        Map<String, Object> before = gl.snapshot();
        IllegalStateException failure = new IllegalStateException("broken tile");
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> PreviewRenderState.runIsolated(new PreviewRenderState(gl), () -> {
                gl.poisonLikeNativeRenderer();
                throw failure;
            })));
        assertEquals(before, gl.snapshot());
    }

    @Test
    void linkageFailureAlsoRestoresTheOuterPass() {
        FakeGl gl = new FakeGl(false);
        Map<String, Object> before = gl.snapshot();
        NoSuchMethodError failure = new NoSuchMethodError("old tile renderer");
        assertSame(failure, assertThrows(NoSuchMethodError.class,
            () -> PreviewRenderState.runIsolated(new PreviewRenderState(gl), () -> {
                gl.poisonLikeNativeRenderer();
                throw failure;
            })));
        assertEquals(before, gl.snapshot());
    }

    @Test
    void nestedRendererRestoresItsImmediateParentBeforeRestoringThePreview() {
        FakeGl gl = new FakeGl(true);
        Map<String, Object> before = gl.snapshot();
        PreviewRenderState.runIsolated(new PreviewRenderState(gl), () -> {
            gl.poisonLikeNativeRenderer();
            Map<String, Object> parent = gl.snapshot();
            PreviewRenderState.runIsolated(new PreviewRenderState(gl), () -> {
                gl.blendFunction(2, 3, 4, 5);
                gl.depthFunction(GL11.GL_LESS);
                gl.color(new float[] { 0.1F, 0.2F, 0.3F, 0.4F });
            });
            assertEquals(parent, gl.snapshot());
        });
        assertEquals(before, gl.snapshot());
    }

    @Test
    void rawDriverPollutionIsRepairedEvenWhenEveryCachedValueStillLooksCorrect() {
        FakeGl driver = new FakeGl(true);
        FakeGl cache = new FakeGl(true);
        PreviewRenderState.Writes cached = cachingWrites(driver, cache);
        PreviewRenderState.Access forced = new PreviewRenderState.ForcingAccess(driver, cached);
        Map<String, Object> before = driver.snapshot();
        PreviewRenderState saved = new PreviewRenderState(forced);
        PreviewRenderState.runIsolated(saved, driver::poisonLikeNativeRenderer);
        assertEquals(before, driver.snapshot());
        assertEquals(before, cache.snapshot());

        cached.enabled(GL11.GL_BLEND, false);
        assertEquals(cache.snapshot(), driver.snapshot(), "subsequent cached draws must agree with the driver");
    }

    @Test
    void cachedOnlySettersCannotRepairTheSameRawPollution() {
        FakeGl driver = new FakeGl(true);
        FakeGl cache = new FakeGl(true);
        PreviewRenderState.Writes cached = cachingWrites(driver, cache);
        PreviewRenderState.Access cachedOnly = (PreviewRenderState.Access) Proxy.newProxyInstance(
            PreviewRenderState.Access.class.getClassLoader(), new Class<?>[] { PreviewRenderState.Access.class },
            (proxy, method, arguments) -> method.invoke(
                method.getReturnType() == void.class ? cached : driver, arguments));
        Map<String, Object> before = driver.snapshot();
        PreviewRenderState.runIsolated(new PreviewRenderState(cachedOnly), driver::poisonLikeNativeRenderer);
        assertNotEquals(before, driver.snapshot(), "the old cache-only restore leaves stale native state");
        assertEquals(before, cache.snapshot(), "the cache can look correct while the actual driver is wrong");
    }

    @Test
    void mixedRawAndCachedNativeCallsRestoreBothCopiesAfterAnException() {
        FakeGl driver = new FakeGl(false);
        FakeGl cache = new FakeGl(false);
        PreviewRenderState.Writes cached = cachingWrites(driver, cache);
        PreviewRenderState.Access forced = new PreviewRenderState.ForcingAccess(driver, cached);
        Map<String, Object> before = driver.snapshot();
        assertThrows(IllegalArgumentException.class,
            () -> PreviewRenderState.runIsolated(new PreviewRenderState(forced), () -> {
                cached.blendFunction(1, 1, 1, 1);
                cached.activeTexture(GL13.GL_TEXTURE1);
                cached.bindTexture(901);
                driver.poisonLikeNativeRenderer();
                throw new IllegalArgumentException("native render failure");
            }));
        assertEquals(before, driver.snapshot());
        assertEquals(before, cache.snapshot());
    }

    @Test
    void textureCaptureForcesTheUnitBeforeQueryingOrRestoringItsBinding() {
        FakeGl driver = new FakeGl(true);
        FakeGl cache = new FakeGl(true);
        driver.activeTexture(GL13.GL_TEXTURE1);
        Map<String, Object> before = driver.snapshot();
        PreviewRenderState.Access forced = new PreviewRenderState.ForcingAccess(driver, cachingWrites(driver, cache));
        PreviewRenderState saved = new PreviewRenderState(forced);
        assertEquals(before, driver.snapshot(), "capture must not query texture 1 as if it were texture 0");
        driver.poisonLikeNativeRenderer();
        saved.restore();
        assertEquals(before, driver.snapshot());
        assertEquals(before, cache.snapshot());
    }

    @Test
    void clientArrayCleanupFailureDoesNotSkipTheOuterRenderStateRestore() {
        FakeGl driver = new FakeGl(true);
        FakeGl cache = new FakeGl(true);
        PreviewRenderState.Access forced = new PreviewRenderState.ForcingAccess(driver, cachingWrites(driver, cache));
        Map<String, Object> before = driver.snapshot();
        int[] colorResets = { 0 };
        IllegalStateException failure = new IllegalStateException("client attribute pop failed");
        PreviewClientArrayGuard.Access arrays = new PreviewClientArrayGuard.Access() {
            public void push() { }
            public void pop() { throw failure; }
            public void resetColor() { colorResets[0]++; }
        };
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> PreviewRenderState.runIsolated(new PreviewRenderState(forced),
                () -> PreviewClientArrayGuard.run(arrays, driver::poisonLikeNativeRenderer))));
        assertEquals(1, colorResets[0]);
        assertEquals(before, driver.snapshot());
        assertEquals(before, cache.snapshot());
    }

    private static PreviewRenderState.Writes cachingWrites(FakeGl driver, FakeGl cache) {
        return (PreviewRenderState.Writes) Proxy.newProxyInstance(PreviewRenderState.Writes.class.getClassLoader(),
            new Class<?>[] { PreviewRenderState.Writes.class }, (proxy, method, arguments) -> {
                Map<String, Object> previous = cache.snapshot();
                method.invoke(cache, arguments);
                if (!previous.equals(cache.snapshot())) {
                    method.invoke(driver, arguments);
                }
                return null;
            });
    }

    private static final class FakeGl implements PreviewRenderState.Access {
        private final Map<Integer, Boolean> capabilities = new LinkedHashMap<Integer, Boolean>();
        private final Map<Integer, Integer> integers = new LinkedHashMap<Integer, Integer>();
        private final Map<Integer, Integer> bindings = new LinkedHashMap<Integer, Integer>();
        private final Map<Integer, Boolean> textures = new LinkedHashMap<Integer, Boolean>();
        private float reference = 0.1F;
        private float[] currentColor = { 0.7F, 0.8F, 0.9F, 1F };
        private float lightX = 240F;
        private float lightY = 208F;

        FakeGl(boolean transparent) {
            for (int capability : new int[] {
                GL11.GL_DEPTH_TEST, GL11.GL_ALPHA_TEST, GL11.GL_BLEND, GL11.GL_CULL_FACE,
                GL11.GL_LIGHTING, GL11.GL_LIGHT0, GL11.GL_LIGHT1, GL11.GL_COLOR_MATERIAL,
                GL11.GL_NORMALIZE, 32826, GL11.GL_FOG
            }) {
                capabilities.put(capability, Boolean.FALSE);
            }
            enabled(GL11.GL_DEPTH_TEST, true);
            enabled(GL11.GL_ALPHA_TEST, true);
            enabled(GL11.GL_BLEND, transparent);
            enabled(GL11.GL_LIGHTING, true);
            enabled(GL11.GL_LIGHT0, true);
            enabled(GL11.GL_LIGHT1, true);
            enabled(GL11.GL_COLOR_MATERIAL, true);
            enabled(32826, true);
            depthMask(!transparent);
            depthFunction(GL11.GL_LEQUAL);
            shadeModel(GL11.GL_SMOOTH);
            alphaFunction(GL11.GL_GREATER, reference);
            blendFunction(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            matrixMode(GL11.GL_PROJECTION);
            bindings.put(GL13.GL_TEXTURE0, 17);
            bindings.put(GL13.GL_TEXTURE1, 23);
            textures.put(GL13.GL_TEXTURE0, true);
            textures.put(GL13.GL_TEXTURE1, false);
            activeTexture(GL13.GL_TEXTURE0);
            clientActiveTexture(GL13.GL_TEXTURE0);
        }

        void poisonLikeNativeRenderer() {
            for (Map.Entry<Integer, Boolean> entry : capabilities.entrySet()) {
                entry.setValue(!entry.getValue());
            }
            enabled(GL11.GL_BLEND, false);
            depthMask(true);
            depthFunction(GL11.GL_ALWAYS);
            alphaFunction(GL11.GL_NEVER, 0.75F);
            blendFunction(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
            shadeModel(GL11.GL_FLAT);
            activeTexture(GL13.GL_TEXTURE0);
            bindTexture(101);
            enabled(GL11.GL_TEXTURE_2D, false);
            activeTexture(GL13.GL_TEXTURE1);
            bindTexture(102);
            enabled(GL11.GL_TEXTURE_2D, true);
            clientActiveTexture(GL13.GL_TEXTURE1);
            color(new float[] { 0.2F, 0.1F, 0.4F, 0.3F });
            lightmap(16F, 32F);
            matrixMode(GL11.GL_TEXTURE);
        }

        Map<String, Object> snapshot() {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("capabilities", new LinkedHashMap<Integer, Boolean>(capabilities));
            result.put("integers", new LinkedHashMap<Integer, Integer>(integers));
            result.put("bindings", new LinkedHashMap<Integer, Integer>(bindings));
            result.put("textures", new LinkedHashMap<Integer, Boolean>(textures));
            result.put("alphaReference", reference);
            result.put("color", Arrays.asList(currentColor[0], currentColor[1], currentColor[2], currentColor[3]));
            result.put("lightmap", Arrays.asList(lightX, lightY));
            return result;
        }

        public boolean enabled(int capability) {
            if (capability == GL11.GL_TEXTURE_2D) {
                return textures.get(integer(GL13.GL_ACTIVE_TEXTURE));
            }
            return capabilities.get(capability);
        }

        public void enabled(int capability, boolean value) {
            if (capability == GL11.GL_TEXTURE_2D) {
                textures.put(integer(GL13.GL_ACTIVE_TEXTURE), value);
            } else {
                capabilities.put(capability, value);
            }
        }

        public int integer(int parameter) {
            if (parameter == GL11.GL_TEXTURE_BINDING_2D) {
                return bindings.get(integer(GL13.GL_ACTIVE_TEXTURE));
            }
            return integers.get(parameter);
        }

        public float decimal(int parameter) {
            return reference;
        }

        public float[] color() {
            return currentColor.clone();
        }

        public void color(float[] value) {
            currentColor = value.clone();
        }

        public void depthMask(boolean value) {
            integers.put(GL11.GL_DEPTH_WRITEMASK, value ? 1 : 0);
        }

        public void depthFunction(int value) {
            integers.put(GL11.GL_DEPTH_FUNC, value);
        }

        public void alphaFunction(int function, float value) {
            integers.put(GL11.GL_ALPHA_TEST_FUNC, function);
            reference = value;
        }

        public void blendFunction(int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha) {
            integers.put(32969, sourceRgb);
            integers.put(32968, destinationRgb);
            integers.put(32971, sourceAlpha);
            integers.put(32970, destinationAlpha);
        }

        public void shadeModel(int value) {
            integers.put(GL11.GL_SHADE_MODEL, value);
        }

        public int textureUnit(boolean lightmap) {
            return lightmap ? GL13.GL_TEXTURE1 : GL13.GL_TEXTURE0;
        }

        public void activeTexture(int value) {
            integers.put(GL13.GL_ACTIVE_TEXTURE, value);
        }

        public void clientActiveTexture(int value) {
            integers.put(GL13.GL_CLIENT_ACTIVE_TEXTURE, value);
        }

        public void bindTexture(int value) {
            bindings.put(integer(GL13.GL_ACTIVE_TEXTURE), value);
        }

        public float lightmapCoordinate(boolean y) {
            return y ? lightY : lightX;
        }

        public void lightmap(float x, float y) {
            lightX = x;
            lightY = y;
        }

        public void matrixMode(int value) {
            integers.put(GL11.GL_MATRIX_MODE, value);
        }
    }
}
