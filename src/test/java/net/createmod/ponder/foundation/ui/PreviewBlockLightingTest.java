package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.junit.jupiter.api.Test;

class PreviewBlockLightingTest {

    @Test
    void blockFormatHasBakedColorAndLightmapButNoFixedFunctionLightingNormals() {
        assertTrue(DefaultVertexFormats.BLOCK.hasColor());
        assertTrue(DefaultVertexFormats.BLOCK.hasUvOffset(0));
        assertTrue(DefaultVertexFormats.BLOCK.hasUvOffset(1));
        assertFalse(DefaultVertexFormats.BLOCK.hasNormal());
    }

    @Test
    void bakedBlockUploadDisablesLightingWithoutChangingOtherPassSettings() {
        FakeLighting gl = new FakeLighting(true);
        PreviewBlockLighting.runUnlit(gl, () -> {
            assertFalse(gl.lighting);
            assertTrue(gl.diffuse);
            assertTrue(gl.lightmap);
            assertTrue(gl.depthTest);
            assertTrue(gl.depthWrite);
            assertEquals("translucent", gl.blend);
        });
        assertTrue(gl.lighting);
        assertEquals(Arrays.asList(false, true), gl.writes);
    }

    @Test
    void alreadyUnlitCallersRemainUnlitAfterTheUpload() {
        FakeLighting gl = new FakeLighting(false);
        PreviewBlockLighting.runUnlit(gl, () -> assertFalse(gl.lighting));
        assertFalse(gl.lighting);
        assertEquals(Arrays.asList(false, false), gl.writes);
    }

    @Test
    void exceptionalUploadRestoresTheLightingNeededByFollowingNativeRenderers() {
        FakeLighting gl = new FakeLighting(true);
        IllegalStateException failure = new IllegalStateException("upload failed");
        assertSame(failure, assertThrows(IllegalStateException.class, () ->
            PreviewBlockLighting.runUnlit(gl, () -> {
                assertFalse(gl.lighting);
                throw failure;
            })));
        assertTrue(gl.lighting);
    }

    @Test
    void linkageErrorAlsoRestoresTheCallerLighting() {
        FakeLighting gl = new FakeLighting(true);
        NoSuchMethodError failure = new NoSuchMethodError("legacy uploader");
        assertSame(failure, assertThrows(NoSuchMethodError.class, () ->
            PreviewBlockLighting.runUnlit(gl, () -> { throw failure; })));
        assertTrue(gl.lighting);
    }

    @Test
    void nestedUploadsRestoreTheirImmediateParent() {
        FakeLighting gl = new FakeLighting(true);
        PreviewBlockLighting.runUnlit(gl, () -> {
            PreviewBlockLighting.runUnlit(gl, () -> assertFalse(gl.lighting));
            assertFalse(gl.lighting);
        });
        assertTrue(gl.lighting);
        assertEquals(Arrays.asList(false, false, false, true), gl.writes);
    }

    @Test
    void failedDisableStillAttemptsToRestoreTheCallerLighting() {
        FakeLighting gl = new FakeLighting(true);
        gl.disableFailure = new IllegalStateException("disable failed");
        assertSame(gl.disableFailure, assertThrows(IllegalStateException.class, () ->
            PreviewBlockLighting.runUnlit(gl, () -> { throw new AssertionError("must not upload"); })));
        assertTrue(gl.lighting);
        assertEquals(Arrays.asList(false, true), gl.writes);
    }

    private static final class FakeLighting implements PreviewBlockLighting.Access {
        final List<Boolean> writes = new ArrayList<Boolean>();
        final boolean diffuse = true, lightmap = true, depthTest = true, depthWrite = true;
        final String blend = "translucent";
        boolean lighting;
        RuntimeException disableFailure;

        FakeLighting(boolean lighting) { this.lighting = lighting; }
        public boolean enabled() { return lighting; }
        public void enabled(boolean value) {
            writes.add(value);
            lighting = value;
            if (!value && disableFailure != null) throw disableFailure;
        }
    }
}
