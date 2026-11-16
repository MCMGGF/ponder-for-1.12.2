package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PonderScreenOverlayTest {

    @Test
    void foregroundUsesFullScreenCoordinatesAndReadableGuiState() {
        FakeAccess gl = new FakeAccess();
        Map<String, Object> before = new LinkedHashMap<String, Object>(gl.state);
        PonderScreenOverlay.run(gl, 750, 400, 1500, 800, () -> {
            assertEquals("ortho:750x400", gl.state.get("projection"));
            assertEquals("identity:0,0,-2000", gl.state.get("modelView"));
            assertEquals("0,0,1500,800", gl.state.get("viewport"));
            assertEquals(false, gl.state.get("scissor"));
            assertEquals(true, gl.state.get("colorMask"));
            assertEquals(false, gl.state.get("depth"));
            assertEquals(false, gl.state.get("depthWrite"));
            assertEquals(false, gl.state.get("lighting"));
            assertEquals(false, gl.state.get("fog"));
            assertEquals(false, gl.state.get("cull"));
            assertEquals("diffuse", gl.state.get("activeTexture"));
            assertEquals(true, gl.state.get("texture:diffuse"));
            assertEquals(false, gl.state.get("texture:lightmap"));
            assertEquals("greater:0.1", gl.state.get("alpha"));
            assertEquals("src-alpha,one-minus-src-alpha,one,zero", gl.state.get("blend"));
            assertEquals("white", gl.state.get("color"));
        });
        assertEquals(before, gl.state);
        assertEquals(Arrays.asList("isolate", "attributes+", "coordinates+", "coordinates-", "attributes-", "restore"), gl.events);
    }

    @Test
    void exceptionalFooterRestoresMatricesClipViewportTexturesAndOuterRenderState() {
        FakeAccess gl = new FakeAccess();
        Map<String, Object> before = new LinkedHashMap<String, Object>(gl.state);
        IllegalStateException failure = new IllegalStateException("broken footer");
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> PonderScreenOverlay.run(gl, 320, 240, 1280, 960, () -> {
                gl.poison();
                throw failure;
            })));
        assertEquals(before, gl.state);
        assertTrue(gl.events.indexOf("coordinates-") < gl.events.indexOf("attributes-"));
        assertTrue(gl.events.indexOf("attributes-") < gl.events.indexOf("restore"));
    }

    @Test
    void linkageErrorCannotLeakTheForegroundPass() {
        FakeAccess gl = new FakeAccess();
        Map<String, Object> before = new LinkedHashMap<String, Object>(gl.state);
        NoSuchMethodError failure = new NoSuchMethodError("old gui renderer");
        assertSame(failure, assertThrows(NoSuchMethodError.class,
            () -> PonderScreenOverlay.run(gl, 320, 240, 1280, 960, () -> { throw failure; })));
        assertEquals(before, gl.state);
    }

    @Test
    void nestedOverlayReturnsToItsImmediateParentsCoordinatesAndState() {
        FakeAccess gl = new FakeAccess();
        Map<String, Object> before = new LinkedHashMap<String, Object>(gl.state);
        PonderScreenOverlay.run(gl, 750, 400, 1500, 800, () -> {
            Map<String, Object> parent = new LinkedHashMap<String, Object>(gl.state);
            PonderScreenOverlay.run(gl, 320, 240, 1280, 960, () -> {
                assertEquals("ortho:320x240", gl.state.get("projection"));
                gl.poison();
            });
            assertEquals(parent, gl.state);
        });
        assertEquals(before, gl.state);
    }

    @Test
    void invalidDimensionsNeverEnterAnOpenGlScope() {
        for (int[] size : new int[][] {{0, 240, 1280, 960}, {320, -1, 1280, 960}, {320, 240, 0, 960}, {320, 240, 1280, -1}}) {
            FakeAccess gl = new FakeAccess();
            assertThrows(IllegalArgumentException.class,
                () -> PonderScreenOverlay.run(gl, size[0], size[1], size[2], size[3], () -> { }));
            assertTrue(gl.events.isEmpty());
        }
    }

    @Test
    void foregroundBodyCannotRunBeforeCoordinatesAreEstablished() {
        FakeAccess gl = new FakeAccess();
        PonderScreenOverlay.run(gl, 750, 400, 1500, 800, () -> {
            assertTrue(gl.events.contains("coordinates+"));
            assertFalse(gl.events.contains("coordinates-"));
            assertFalse(gl.events.contains("attributes-"));
        });
    }

    private static final class FakeAccess implements PonderScreenOverlay.Access {
        final Map<String, Object> state = new LinkedHashMap<String, Object>();
        final List<String> events = new ArrayList<String>();
        final List<Map<String, Object>> attributes = new ArrayList<Map<String, Object>>();

        FakeAccess() {
            state.put("projection", "foreign-projection");
            state.put("modelView", "foreign-item-translation");
            state.put("viewport", "10,20,900,500");
            state.put("scissor", true);
            state.put("colorMask", false);
            state.put("depth", true);
            state.put("depthWrite", true);
            state.put("lighting", true);
            state.put("fog", true);
            state.put("cull", true);
            state.put("activeTexture", "lightmap");
            state.put("texture:diffuse", false);
            state.put("texture:lightmap", true);
            state.put("alpha", "foreign-alpha");
            state.put("blend", "foreign-blend");
            state.put("color", "transparent-black");
        }

        public void isolate(Runnable render) {
            Map<String, Object> before = new LinkedHashMap<String, Object>(state);
            events.add("isolate");
            state.put("depth", false);
            state.put("depthWrite", false);
            state.put("lighting", false);
            state.put("fog", false);
            try { render.run(); }
            finally { state.clear(); state.putAll(before); events.add("restore"); }
        }

        public void pushAttributes() { attributes.add(new LinkedHashMap<String, Object>(state)); events.add("attributes+"); }
        public void popAttributes() {
            Map<String, Object> saved = attributes.remove(attributes.size() - 1);
            for (String key : new String[] {"viewport", "scissor", "colorMask", "alpha", "blend"}) state.put(key, saved.get(key));
            events.add("attributes-");
        }
        public void coordinates(int width, int height, Runnable render) {
            Object projection = state.get("projection"), modelView = state.get("modelView");
            state.put("projection", "ortho:" + width + "x" + height);
            state.put("modelView", "identity:0,0,-2000");
            events.add("coordinates+");
            try { render.run(); }
            finally { state.put("projection", projection); state.put("modelView", modelView); events.add("coordinates-"); }
        }
        public void viewport(int width, int height) { state.put("viewport", "0,0," + width + "," + height); }
        public void scissor(boolean enabled) { state.put("scissor", enabled); }
        public void colorMask(boolean enabled) { state.put("colorMask", enabled); }
        public void activeTexture(boolean lightmap) { state.put("activeTexture", lightmap ? "lightmap" : "diffuse"); }
        public void texture(boolean enabled) { state.put("texture:" + state.get("activeTexture"), enabled); }
        public void cull(boolean enabled) { state.put("cull", enabled); }
        public void alpha() { state.put("alpha", "greater:0.1"); }
        public void blend() { state.put("blend", "src-alpha,one-minus-src-alpha,one,zero"); }
        public void white() { state.put("color", "white"); }
        void poison() { for (String key : new ArrayList<String>(state.keySet())) state.put(key, "poisoned:" + key); }
    }
}
