package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PreviewModelModeTest {

    @Test
    void structureIsTheDefaultAndOnlyAnExplicitChoiceCallsTheLoader() {
        PreviewModelMode<String> mode = new PreviewModelMode<>();
        AtomicInteger calls = new AtomicInteger();
        assertFalse(mode.isAppearance());
        assertFalse(mode.wasAttempted());
        mode.chooseStructure();
        mode.invalidate(false);
        assertEquals(0, calls.get());
        assertTrue(mode.chooseAppearance(() -> {
            calls.incrementAndGet();
            return "mesh";
        }, "mesh"::equals, value -> "static"));
        mode.chooseStructure();
        assertTrue(mode.chooseAppearance(() -> {
            calls.incrementAndGet();
            return "unexpected";
        }, "mesh"::equals, value -> "static"));
        assertEquals(1, calls.get());
    }

    @Test
    void aMissingModelKeepsItsReasonAndNeverRetriesWhileCurrent() {
        PreviewModelMode<String> mode = new PreviewModelMode<>();
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 100; i++) {
            assertFalse(mode.chooseAppearance(() -> {
                calls.incrementAndGet();
                return "missing texture";
            }, value -> false, value -> value));
            assertFalse(mode.invalidate(true));
        }
        assertEquals(1, calls.get());
        assertEquals("missing texture", mode.getNote());
        assertFalse(mode.claimInitialFit());
    }

    @Test
    void staleSuccessAndFailureWaitForAnotherExplicitRequest() {
        for (boolean success : new boolean[] { true, false }) {
            PreviewModelMode<String> mode = new PreviewModelMode<>();
            AtomicInteger calls = new AtomicInteger();
            mode.chooseAppearance(() -> {
                calls.incrementAndGet();
                return "first";
            }, value -> success, value -> value);
            assertTrue(mode.invalidate(false));
            assertNull(mode.getPreview());
            assertFalse(mode.isAppearance());
            assertEquals(1, calls.get());
            mode.chooseAppearance(() -> {
                calls.incrementAndGet();
                return "second";
            }, value -> true, value -> value);
            assertEquals(2, calls.get());
        }
    }

    @Test
    void onlyTheFirstSuccessfulChoiceFitsUntilTheSceneIsReleased() {
        PreviewModelMode<String> mode = new PreviewModelMode<>();
        mode.chooseAppearance(() -> "mesh", value -> true, value -> value);
        assertTrue(mode.claimInitialFit());
        mode.chooseStructure();
        mode.chooseAppearance(() -> fail("must reuse the preview"), value -> true, value -> value);
        assertFalse(mode.claimInitialFit());
        mode.invalidate(false);
        mode.chooseAppearance(() -> "replacement", value -> true, value -> value);
        assertFalse(mode.claimInitialFit());
        mode.clear();
        assertNull(mode.getPreview());
        mode.chooseAppearance(() -> "new scene", value -> true, value -> value);
        assertTrue(mode.claimInitialFit());
    }

    @Test
    void aRenderFailureCannotReenterTheSameBrokenMesh() {
        PreviewModelMode<String> mode = new PreviewModelMode<>();
        mode.chooseAppearance(() -> "mesh", value -> true, value -> value);
        mode.fail("specific render failure");
        assertFalse(mode.chooseAppearance(() -> fail("must not retry"), value -> true, value -> value));
        assertEquals("specific render failure", mode.getNote());
        mode.clear();
        assertNull(mode.getPreview());
        assertFalse(mode.wasAttempted());
        assertTrue(mode.getNote().isEmpty());
    }
}
