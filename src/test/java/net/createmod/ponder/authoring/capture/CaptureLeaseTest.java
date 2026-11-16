package net.createmod.ponder.authoring.capture;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CaptureLeaseTest {
    private final Object world = new Object(), player = new Object(), container = new Object(), screen = new Object(), playerContainer = new Object();
    @Test void captureOnlyReachesReviewAfterTheSameMenuIsNormallyClosed() {
        CaptureLease lease = new CaptureLease(world, player, 100, 1200);
        assertTrue(lease.bind(world, player, container, screen)); assertTrue(lease.capture(container, screen));
        assertEquals(CaptureLease.Observation.WAIT, lease.observe(world, player, container, screen, playerContainer, 101));
        assertEquals(CaptureLease.Observation.REVIEW_READY, lease.observe(world, player, playerContainer, null, playerContainer, 102));
        Object review = new Object(); lease.reviewing(review);
        assertEquals(CaptureLease.Observation.WAIT, lease.observe(world, player, playerContainer, review, playerContainer, 103));
    }
    @Test void worldPlayerContainerAndScreenSubstitutionAllCancel() {
        for (int change = 0; change < 4; change++) {
            CaptureLease lease = new CaptureLease(world, player, 0, 1200); lease.bind(world, player, container, screen); lease.capture(container, screen);
            assertEquals(CaptureLease.Observation.CANCEL, lease.observe(change == 0 ? new Object() : world, change == 1 ? new Object() : player,
                change == 2 ? new Object() : container, change == 3 ? new Object() : screen, playerContainer, 2));
        }
    }
    @Test void closingWithoutCaptureCancelsAndNeverInventsASnapshot() {
        CaptureLease lease = new CaptureLease(world, player, 0, 1200); lease.bind(world, player, container, screen);
        assertEquals(CaptureLease.Observation.CANCEL, lease.observe(world, player, playerContainer, null, playerContainer, 1));
        assertThrows(IllegalStateException.class, () -> lease.reviewing(new Object()));
    }
    @Test void timeoutBackwardClockAndReviewReplacementStopSafely() {
        CaptureLease lease = new CaptureLease(world, player, 10, 20);
        assertEquals(CaptureLease.Observation.WAIT, lease.observe(world, player, playerContainer, null, playerContainer, 30));
        assertEquals(CaptureLease.Observation.CANCEL, lease.observe(world, player, playerContainer, null, playerContainer, 31));
        assertEquals(CaptureLease.Observation.CANCEL, lease.observe(world, player, playerContainer, null, playerContainer, 9));
        lease.bind(world, player, container, screen); lease.capture(container, screen);
        assertThrows(IllegalStateException.class, () -> lease.reviewing(new Object()));
        lease.observe(world, player, playerContainer, null, playerContainer, 11); lease.reviewing(new Object());
        assertEquals(CaptureLease.Observation.CANCEL, lease.observe(world, player, playerContainer, new Object(), playerContainer, 11));
    }
    @Test void noDuplicateCaptureRebindOrFinishedWorldReferencesRemain() {
        CaptureLease lease = new CaptureLease(world, player, 0, 1200);
        assertTrue(lease.ownsWorld(world)); assertFalse(lease.bind(new Object(), player, container, screen));
        assertTrue(lease.bind(world, player, container, screen)); assertFalse(lease.bind(world, player, container, screen));
        assertFalse(lease.capture(container, new Object())); assertTrue(lease.capture(container, screen)); assertFalse(lease.capture(container, screen));
        lease.finish(); assertFalse(lease.owns(world, player)); assertFalse(lease.ownsWorld(world));
        assertEquals(CaptureLease.State.FINISHED, lease.getState());
    }
    @Test void explicitPagedReviewHasItsOwnFiniteReadingBudgetNotTheMenuOpeningDeadline() {
        CaptureLease lease = new CaptureLease(world, player, 0, 1200); lease.bind(world, player, container, screen); lease.capture(container, screen);
        lease.observe(world, player, playerContainer, null, playerContainer, 1190);
        Object review = new Object(); lease.reviewing(review);
        assertEquals(CaptureLease.Observation.WAIT, lease.observe(world, player, playerContainer, review, playerContainer, 2390));
        assertEquals(CaptureLease.Observation.WAIT, lease.observe(world, player, playerContainer, review, playerContainer, 13190));
        assertEquals(CaptureLease.Observation.CANCEL, lease.observe(world, player, playerContainer, review, playerContainer, 13191));
    }

    @Test void capturedMenuMayFinishItsNormalClosingAnimationBeforeReview() {
        CaptureLease lease = captured();
        assertEquals(CaptureLease.Observation.WAIT, lease.observe(world, player, playerContainer, screen, playerContainer, 10));
        assertEquals(CaptureLease.State.CAPTURED, lease.getState());
        assertThrows(IllegalStateException.class, () -> lease.reviewing(new Object()));
        assertEquals(CaptureLease.Observation.WAIT, lease.observe(world, player, playerContainer, screen, playerContainer, 12));
        assertEquals(CaptureLease.Observation.REVIEW_READY, lease.observe(world, player, playerContainer, null, playerContainer, 13));
        Object review = new Object(); lease.reviewing(review);
        assertEquals(CaptureLease.Observation.WAIT, lease.observe(world, player, playerContainer, review, playerContainer, 14));
    }

    @Test void closingAnimationHasOneFixedBudgetAndNeverResetsOnRepeatedObservations() {
        CaptureLease lease = captured();
        assertEquals(CaptureLease.Observation.WAIT, lease.observe(world, player, playerContainer, screen, playerContainer, 10));
        assertEquals(CaptureLease.Observation.WAIT, lease.observe(world, player, playerContainer, screen, playerContainer, 49));
        assertEquals(CaptureLease.Observation.WAIT, lease.observe(world, player, playerContainer, screen, playerContainer, 50));
        assertEquals(CaptureLease.Observation.CANCEL, lease.observe(world, player, playerContainer, screen, playerContainer, 51));
        assertThrows(IllegalStateException.class, () -> lease.reviewing(new Object()));
    }

    @Test void completingAnimationAfterItsBudgetCannotBypassTheDeadline() {
        CaptureLease lease = captured();
        lease.observe(world, player, playerContainer, screen, playerContainer, 10);
        assertEquals(CaptureLease.Observation.CANCEL, lease.observe(world, player, playerContainer, null, playerContainer, 51));
        assertThrows(IllegalStateException.class, () -> lease.reviewing(new Object()));
    }

    @Test void closingAnimationDoesNotExtendTheOriginalCaptureDeadline() {
        CaptureLease lease = captured();
        assertEquals(CaptureLease.Observation.WAIT, lease.observe(world, player, playerContainer, screen, playerContainer, 1199));
        assertEquals(CaptureLease.Observation.CANCEL, lease.observe(world, player, playerContainer, null, playerContainer, 1201));
    }

    @Test void closingAnimationRejectsReopenedContainersAndUnrelatedScreensOrIdentities() {
        for (int change = 0; change < 5; change++) {
            CaptureLease lease = captured();
            lease.observe(world, player, playerContainer, screen, playerContainer, 10);
            assertEquals(CaptureLease.Observation.CANCEL, lease.observe(change == 0 ? new Object() : world,
                change == 1 ? new Object() : player, change == 2 ? container : change == 3 ? new Object() : playerContainer,
                change == 4 ? new Object() : screen, playerContainer, 11));
        }
    }

    @Test void animationGraceRequiresACapturedDraftAndAMonotonicClosingClock() {
        CaptureLease lease = new CaptureLease(world, player, 0, 1200);
        lease.bind(world, player, container, screen);
        assertEquals(CaptureLease.Observation.CANCEL, lease.observe(world, player, playerContainer, screen, playerContainer, 10));
        lease = captured();
        lease.observe(world, player, playerContainer, screen, playerContainer, 10);
        lease.observe(world, player, playerContainer, screen, playerContainer, 12);
        assertEquals(CaptureLease.Observation.CANCEL, lease.observe(world, player, playerContainer, null, playerContainer, 11));
    }

    private CaptureLease captured() {
        CaptureLease lease = new CaptureLease(world, player, 0, 1200);
        assertTrue(lease.bind(world, player, container, screen));
        assertTrue(lease.capture(container, screen));
        return lease;
    }
}
