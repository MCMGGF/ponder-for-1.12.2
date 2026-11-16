package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;
import java.util.Deque;

import org.junit.jupiter.api.Test;

class PreviewClientArrayGuardTest {

    @Test
    void successfulUploadRestoresPointersEnablesAndTheClientTextureUnit() {
        FakeArrays arrays = new FakeArrays();
        int[] original = arrays.state.clone();
        PreviewClientArrayGuard.run(arrays, arrays::poison);
        assertArrayEquals(original, arrays.state);
        assertEquals(0, arrays.stack.size());
        assertEquals(1, arrays.resets);
    }

    @Test
    void failedUploadStillRestoresEverySavedArrayField() {
        FakeArrays arrays = new FakeArrays();
        int[] original = arrays.state.clone();
        IllegalStateException failure = new IllegalStateException("upload failed");
        assertSame(failure, assertThrows(IllegalStateException.class, () ->
            PreviewClientArrayGuard.run(arrays, () -> {
                arrays.poison();
                throw failure;
            })));
        assertArrayEquals(original, arrays.state);
        assertEquals(1, arrays.pops);
        assertEquals(1, arrays.resets);
    }

    @Test
    void linkageErrorAlsoUnwindsTheClientAttributeStack() {
        FakeArrays arrays = new FakeArrays();
        int[] original = arrays.state.clone();
        NoSuchMethodError failure = new NoSuchMethodError("legacy upload");
        assertSame(failure, assertThrows(NoSuchMethodError.class, () ->
            PreviewClientArrayGuard.run(arrays, () -> {
                arrays.poison();
                throw failure;
            })));
        assertArrayEquals(original, arrays.state);
        assertEquals(0, arrays.stack.size());
        assertEquals(1, arrays.resets);
    }

    @Test
    void nestedUploadsRestoreTheirImmediateCallerBeforeTheOuterState() {
        FakeArrays arrays = new FakeArrays();
        int[] original = arrays.state.clone();
        PreviewClientArrayGuard.run(arrays, () -> {
            arrays.poison();
            int[] outer = arrays.state.clone();
            PreviewClientArrayGuard.run(arrays, () -> arrays.state[0] = 99);
            assertArrayEquals(outer, arrays.state);
            assertEquals(1, arrays.stack.size());
        });
        assertArrayEquals(original, arrays.state);
        assertEquals(0, arrays.stack.size());
        assertEquals(2, arrays.resets);
    }

    @Test
    void colorCacheIsInvalidatedEvenWhenClientAttributePopThrows() {
        FakeArrays arrays = new FakeArrays();
        arrays.popFailure = new IllegalStateException("pop failed");
        assertSame(arrays.popFailure, assertThrows(IllegalStateException.class,
            () -> PreviewClientArrayGuard.run(arrays, arrays::poison)));
        assertEquals(1, arrays.pops);
        assertEquals(1, arrays.resets);
    }

    private static final class FakeArrays implements PreviewClientArrayGuard.Access {
        // Vertex/color/texture pointers, enabled flags, and the client texture unit.
        int[] state = { 11, 17, 23, 1, 1, 0, 0 };
        final Deque<int[]> stack = new ArrayDeque<int[]>();
        int pops;
        int resets;
        RuntimeException popFailure;

        public void push() { stack.push(state.clone()); }

        public void pop() {
            pops++;
            if (popFailure != null) throw popFailure;
            state = stack.pop();
        }

        public void resetColor() { resets++; }

        void poison() {
            state = new int[] { 101, 202, 303, 0, 0, 1, 1 };
        }
    }
}
