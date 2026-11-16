package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

class PreviewModelViewGuardTest {

    @Test
    void balancedRendererRestoresTheCompleteEntryMatrixAndPreviousMode() {
        FakeMatrix gl = new FakeMatrix();
        float[] original = gl.top();
        try (PreviewModelViewGuard ignored = new PreviewModelViewGuard(gl)) {
            assertEquals(GL11.GL_MODELVIEW, gl.mode());
            assertEquals(4, gl.depth());
            gl.load(matrix(20F));
            gl.push();
            gl.load(matrix(40F));
            gl.pop();
            gl.mode(GL11.GL_TEXTURE);
        }
        assertEquals(3, gl.depth());
        assertArrayEquals(original, gl.top());
        assertArrayEquals(matrix(2F), gl.stack.get(1));
        assertEquals(GL11.GL_PROJECTION, gl.mode());
    }

    @Test
    void unbalancedPushesAreRemovedEvenWhenTheRendererThrows() {
        FakeMatrix gl = new FakeMatrix();
        float[] original = gl.top();
        IllegalArgumentException failure = new IllegalArgumentException("tile failed");
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> {
            try (PreviewModelViewGuard ignored = new PreviewModelViewGuard(gl)) {
                gl.load(matrix(20F));
                gl.push();
                gl.load(matrix(30F));
                gl.push();
                gl.load(matrix(40F));
                gl.mode(GL11.GL_TEXTURE);
                throw failure;
            }
        }));
        assertEquals(3, gl.depth());
        assertEquals(3, gl.popCalls);
        assertArrayEquals(original, gl.top());
        assertEquals(GL11.GL_PROJECTION, gl.mode());
    }

    @Test
    void rendererThatPoppedOnlyTheGuardFrameCanStillBeRepaired() {
        FakeMatrix gl = new FakeMatrix();
        float[] original = gl.top();
        try (PreviewModelViewGuard ignored = new PreviewModelViewGuard(gl)) {
            gl.pop();
            gl.load(matrix(90F));
        }
        assertEquals(3, gl.depth());
        assertArrayEquals(original, gl.top());
        assertEquals(GL11.GL_PROJECTION, gl.mode());
    }

    @Test
    void callerStackUnderflowIsReportedWithoutInventingLostHistory() {
        FakeMatrix gl = new FakeMatrix();
        float[] original = gl.top();
        PreviewModelViewGuard guard = new PreviewModelViewGuard(gl);
        gl.pop();
        gl.pop();
        gl.load(matrix(90F));
        assertThrows(IllegalStateException.class, guard::close);
        assertEquals(2, gl.depth(), "lost caller frames cannot be recreated from a top-matrix snapshot");
        assertArrayEquals(original, gl.top());
        assertArrayEquals(matrix(1F), gl.stack.get(0));
        assertEquals(GL11.GL_PROJECTION, gl.mode());
    }

    @Test
    void recoveryHasAFixedPopBudget() {
        FakeMatrix gl = new FakeMatrix();
        float[] original = gl.top();
        PreviewModelViewGuard guard = new PreviewModelViewGuard(gl);
        for (int i = 0; i < PreviewModelViewGuard.MAX_RECOVERY_POPS + 2; i++) {
            gl.push();
        }
        assertThrows(IllegalStateException.class, guard::close);
        assertEquals(PreviewModelViewGuard.MAX_RECOVERY_POPS, gl.popCalls);
        assertEquals(6, gl.depth());
        assertArrayEquals(original, gl.top());
        assertEquals(GL11.GL_PROJECTION, gl.mode());
    }

    @Test
    void aBrokenPopThatDoesNotChangeDepthCannotCauseAnEndlessCleanup() {
        FakeMatrix gl = new FakeMatrix();
        PreviewModelViewGuard guard = new PreviewModelViewGuard(gl);
        gl.push();
        gl.ignorePops = true;
        assertThrows(IllegalStateException.class, guard::close);
        assertEquals(2, gl.popCalls);
        assertEquals(5, gl.depth());
        assertEquals(GL11.GL_PROJECTION, gl.mode());
    }

    @Test
    void closeIsIdempotentAfterBothSuccessfulAndFailedRecovery() {
        for (boolean underflow : new boolean[] { false, true }) {
            FakeMatrix gl = new FakeMatrix();
            PreviewModelViewGuard guard = new PreviewModelViewGuard(gl);
            if (underflow) {
                gl.pop();
                gl.pop();
                assertThrows(IllegalStateException.class, guard::close);
            } else {
                guard.close();
            }
            int calls = gl.popCalls;
            int loads = gl.loadCalls;
            gl.mode(GL11.GL_TEXTURE);
            guard.close();
            assertEquals(calls, gl.popCalls);
            assertEquals(loads, gl.loadCalls);
            assertEquals(GL11.GL_TEXTURE, gl.mode());
        }
    }

    @Test
    void failedCaptureRestoresModeWithoutPushingAFrame() {
        FakeMatrix gl = new FakeMatrix();
        gl.captureFailure = new IllegalStateException("query failed");
        assertSame(gl.captureFailure,
            assertThrows(IllegalStateException.class, () -> new PreviewModelViewGuard(gl)));
        assertEquals(3, gl.depth());
        assertEquals(0, gl.pushCalls);
        assertEquals(GL11.GL_PROJECTION, gl.mode());
    }

    @Test
    void matrixCleanupFailureStillUnwindsClientArraysAndColorCache() {
        FakeMatrix gl = new FakeMatrix();
        List<String> calls = new ArrayList<String>();
        PreviewClientArrayGuard.Access arrays = new PreviewClientArrayGuard.Access() {
            public void push() { calls.add("push"); }
            public void pop() { calls.add("pop"); }
            public void resetColor() { calls.add("reset"); }
        };
        assertThrows(IllegalStateException.class, () -> PreviewClientArrayGuard.run(arrays, () -> {
            try (PreviewModelViewGuard ignored = new PreviewModelViewGuard(gl)) {
                gl.pop();
                gl.pop();
            }
        }));
        assertEquals(java.util.Arrays.asList("push", "pop", "reset"), calls);
        assertEquals(GL11.GL_PROJECTION, gl.mode());
    }

    @Test
    void aThrowingMatrixPopStillRestoresPreviousMode() {
        FakeMatrix gl = new FakeMatrix();
        PreviewModelViewGuard guard = new PreviewModelViewGuard(gl);
        gl.popFailure = new IllegalStateException("pop failed");
        assertSame(gl.popFailure, assertThrows(IllegalStateException.class, guard::close));
        assertEquals(GL11.GL_PROJECTION, gl.mode());
    }

    private static float[] matrix(float seed) {
        float[] result = new float[16];
        for (int i = 0; i < result.length; i++) {
            result[i] = seed + i;
        }
        return result;
    }

    private static final class FakeMatrix implements PreviewModelViewGuard.Access {
        final List<float[]> stack = new ArrayList<float[]>();
        int currentMode = GL11.GL_PROJECTION;
        int pushCalls;
        int popCalls;
        int loadCalls;
        boolean ignorePops;
        RuntimeException captureFailure;
        RuntimeException popFailure;

        FakeMatrix() {
            stack.add(PreviewModelViewGuardTest.matrix(1F));
            stack.add(PreviewModelViewGuardTest.matrix(2F));
            stack.add(PreviewModelViewGuardTest.matrix(3F));
        }

        public int mode() { return currentMode; }
        public void mode(int mode) { currentMode = mode; }
        public int depth() { return stack.size(); }

        public float[] matrix() {
            assertEquals(GL11.GL_MODELVIEW, currentMode);
            if (captureFailure != null) throw captureFailure;
            return top();
        }

        float[] top() { return stack.get(stack.size() - 1).clone(); }

        public void push() {
            assertEquals(GL11.GL_MODELVIEW, currentMode);
            pushCalls++;
            stack.add(top());
        }

        public void pop() {
            assertEquals(GL11.GL_MODELVIEW, currentMode);
            popCalls++;
            if (popFailure != null) throw popFailure;
            if (!ignorePops) stack.remove(stack.size() - 1);
        }

        public void load(float[] value) {
            assertEquals(GL11.GL_MODELVIEW, currentMode);
            loadCalls++;
            stack.set(stack.size() - 1, value.clone());
        }
    }
}
