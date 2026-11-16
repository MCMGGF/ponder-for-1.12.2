package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class PonderIndexViewStateTest {

    @Test
    void refreshesOncePerPublishedGenerationAndAgainAfterReopening() {
        PonderIndexViewState state = new PonderIndexViewState();
        AtomicInteger refreshes = new AtomicInteger();
        assertTrue(state.refresh(3, refreshes::incrementAndGet));
        for (int frame = 0; frame < 100; frame++) {
            assertFalse(state.refresh(3, refreshes::incrementAndGet));
        }
        assertTrue(state.refresh(4, refreshes::incrementAndGet));
        state.invalidate();
        assertTrue(state.refresh(4, refreshes::incrementAndGet));
        assertEquals(3, refreshes.get());
    }

    @Test
    void aFailedViewPreparationIsNotRetriedEveryFrame() {
        PonderIndexViewState state = new PonderIndexViewState();
        assertThrows(IllegalStateException.class, () -> state.refresh(1, () -> {
            throw new IllegalStateException("invalid component");
        }));
        assertFalse(state.refresh(1, () -> {
            throw new AssertionError("must not retry the same generation");
        }));
        assertTrue(state.refresh(2, () -> {}));
    }

    @Test
    void selectionFollowsIdentityAcrossInsertionReorderAndDeletion() {
        assertEquals(2, retain("b", 1, "new", "a", "b", "c"));
        assertEquals(0, retain("b", 1, "b", "a", "c"));
        assertEquals(1, retain("removed", 1, "a", "c"));
        assertEquals(0, retain("removed", 8, "only"));
        assertEquals(0, retain(null, -1, "first", "second"));
        assertEquals(0, PonderIndexViewState.retain(Collections.<String>emptyList(), "b", 2, value -> value));
    }

    @Test
    void duplicateSceneIdsPreferThePreviousPosition() {
        assertEquals(2, retain("same", 2, "same", "other", "same"));
        assertEquals(0, retain("same", 1, "same", "other", "same"));
    }

    private static int retain(String selected, int fallback, String... values) {
        return PonderIndexViewState.retain(Arrays.asList(values), selected, fallback, value -> value);
    }
}
