package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

class PreviewNativeRenderGuardTest {

    @Test
    void nativeCallbackRestoresProjectionModelViewClipAndViewportWithoutChangingThe3dPass() {
        FakeGeometry gl = new FakeGeometry();
        String before = gl.snapshot();
        PreviewNativeRenderGuard.run(gl, () -> {
            assertEquals(before, gl.snapshot());
            assertTrue(gl.depthTest);
            assertTrue(gl.depthWrite);
            gl.poison();
        });
        assertEquals(before, gl.snapshot());
        assertEquals(Arrays.asList("pushRaster", "guardProjection", "guardModelView",
            "restoreModelView", "restoreProjection", "popRaster"), gl.calls);
    }

    @Test
    void failingNativeCallbackRestoresEveryGeometryFieldBeforePropagatingTheOriginalFailure() {
        FakeGeometry gl = new FakeGeometry();
        String before = gl.snapshot();
        IllegalStateException failure = new IllegalStateException("broken tile renderer");
        assertSame(failure, assertThrows(IllegalStateException.class, () ->
            PreviewNativeRenderGuard.run(gl, () -> {
                gl.poison();
                throw failure;
            })));
        assertEquals(before, gl.snapshot());
        assertEquals(0, gl.raster.size());
    }

    @Test
    void linkageFailureUnwindsProjectionAndRasterStateAsWell() {
        FakeGeometry gl = new FakeGeometry();
        String before = gl.snapshot();
        NoSuchMethodError failure = new NoSuchMethodError("legacy renderer");
        assertSame(failure, assertThrows(NoSuchMethodError.class, () ->
            PreviewNativeRenderGuard.run(gl, () -> {
                gl.poison();
                throw failure;
            })));
        assertEquals(before, gl.snapshot());
        assertEquals(0, gl.raster.size());
    }

    @Test
    void failedModelViewCaptureStillReleasesTheProjectionGuardAndRasterSnapshot() {
        FakeGeometry gl = new FakeGeometry();
        String before = gl.snapshot();
        gl.captureFailure = new IllegalStateException("model-view capture");
        assertSame(gl.captureFailure, assertThrows(IllegalStateException.class, () ->
            PreviewNativeRenderGuard.run(gl, () -> { throw new AssertionError("callback must not run"); })));
        assertEquals(before, gl.snapshot());
        assertEquals(Arrays.asList("pushRaster", "guardProjection", "guardModelView",
            "restoreProjection", "popRaster"), gl.calls);
    }

    @Test
    void failedMatrixRecoveryStillRestoresProjectionClipAndViewport() {
        FakeGeometry gl = new FakeGeometry();
        String before = gl.snapshot();
        gl.recoveryFailure = new IllegalStateException("model-view stack damaged");
        assertSame(gl.recoveryFailure, assertThrows(IllegalStateException.class, () ->
            PreviewNativeRenderGuard.run(gl, gl::poison)));
        assertEquals(before, gl.snapshot());
        assertEquals(0, gl.raster.size());
        assertEquals("popRaster", gl.calls.get(gl.calls.size() - 1));
    }

    @Test
    void nestedCallbacksRestoreTheirImmediateParentInsteadOfTheOriginalWindow() {
        FakeGeometry gl = new FakeGeometry();
        String before = gl.snapshot();
        PreviewNativeRenderGuard.run(gl, () -> {
            gl.poison();
            String outer = gl.snapshot();
            PreviewNativeRenderGuard.run(gl, () -> {
                gl.projection = "nested projection";
                gl.modelView = "nested model-view";
                gl.clip = "nested clip";
                gl.viewport = "nested viewport";
            });
            assertEquals(outer, gl.snapshot());
            assertEquals(1, gl.raster.size());
        });
        assertEquals(before, gl.snapshot());
        assertEquals(0, gl.raster.size());
    }

    private static final class FakeGeometry implements PreviewNativeRenderGuard.Access {
        final List<String> calls = new ArrayList<String>();
        final Deque<String[]> raster = new ArrayDeque<String[]>();
        String projection = "orthographic GUI";
        String modelView = "scene camera";
        String clip = "model clipping rectangle";
        String viewport = "full window viewport";
        boolean depthTest = true;
        boolean depthWrite = true;
        RuntimeException captureFailure;
        RuntimeException recoveryFailure;

        public void pushRaster() {
            calls.add("pushRaster");
            raster.push(new String[] { clip, viewport });
        }

        public void popRaster() {
            calls.add("popRaster");
            String[] saved = raster.pop();
            clip = saved[0];
            viewport = saved[1];
        }

        public PreviewNativeRenderGuard.Scope guardProjection() {
            calls.add("guardProjection");
            String saved = projection;
            return () -> {
                calls.add("restoreProjection");
                projection = saved;
            };
        }

        public PreviewNativeRenderGuard.Scope guardModelView() {
            calls.add("guardModelView");
            if (captureFailure != null) throw captureFailure;
            String saved = modelView;
            return () -> {
                calls.add("restoreModelView");
                modelView = saved;
                if (recoveryFailure != null) throw recoveryFailure;
            };
        }

        void poison() {
            projection = "tile projection";
            modelView = "tile model-view";
            clip = "tile clipping rectangle";
            viewport = "tile viewport";
        }

        String snapshot() {
            return projection + ";" + modelView + ";" + clip + ";" + viewport;
        }
    }
}
