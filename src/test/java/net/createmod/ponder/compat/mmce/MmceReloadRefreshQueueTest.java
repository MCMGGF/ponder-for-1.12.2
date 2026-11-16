package net.createmod.ponder.compat.mmce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class MmceReloadRefreshQueueTest {

    @Test
    void idleTicksDoNothing() {
        MmceReloadRefreshQueue queue = new MmceReloadRefreshQueue();
        assertFalse(queue.hasPending());
        assertFalse(queue.refreshIfReady(true, () -> 1L, () -> {
            throw new AssertionError("Idle refresh");
        }));
    }

    @Test
    void startupCompletionIsDiscardedInsteadOfReplayed() {
        MmceReloadRefreshQueue queue = new MmceReloadRefreshQueue();
        AtomicInteger calls = new AtomicInteger();
        queue.reloadCompleted(1L, false);
        assertFalse(queue.hasPending());
        assertFalse(queue.refreshIfReady(true, () -> 1L, calls::incrementAndGet));
        queue.reloadCompleted(1L, true);
        assertFalse(queue.hasPending(), "Startup completion must not be replayed after readiness changes");
        queue.reloadCompleted(2L, true);
        assertTrue(queue.refreshIfReady(true, () -> 2L, calls::incrementAndGet));
        assertEquals(1, calls.get());
    }

    @Test
    void repeatedCompletionsCoalesceIntoOneRefresh() {
        MmceReloadRefreshQueue queue = new MmceReloadRefreshQueue();
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 100; i++) {
            queue.reloadCompleted(1L, true);
        }
        assertEquals(0, calls.get());
        assertTrue(queue.refreshIfReady(true, () -> 1L, calls::incrementAndGet));
        assertFalse(queue.refreshIfReady(true, () -> 1L, calls::incrementAndGet));
        assertEquals(1, calls.get());
    }

    @Test
    void startingAnotherReloadSuspendsTheOlderCompletion() {
        MmceReloadRefreshQueue queue = new MmceReloadRefreshQueue();
        AtomicInteger calls = new AtomicInteger();
        queue.reloadCompleted(1L, true);
        queue.reloadStarted();
        assertFalse(queue.refreshIfReady(true, () -> 1L, calls::incrementAndGet));
        queue.reloadCompleted(2L, true);
        assertTrue(queue.refreshIfReady(true, () -> 2L, calls::incrementAndGet));
        assertEquals(1, calls.get());
    }

    @Test
    void callbackRequiresTheClientMainThread() {
        MmceReloadRefreshQueue queue = new MmceReloadRefreshQueue();
        AtomicInteger calls = new AtomicInteger();
        queue.reloadCompleted(1L, true);
        assertFalse(queue.refreshIfReady(false, () -> {
            throw new AssertionError("Worker must not query native readiness");
        }, calls::incrementAndGet));
        assertTrue(queue.hasPending());
        assertTrue(queue.refreshIfReady(true, () -> 1L, calls::incrementAndGet));
        assertEquals(1, calls.get());
    }

    @Test
    void aWorkerCompletionOnlySignalsTheClient() throws InterruptedException {
        MmceReloadRefreshQueue queue = new MmceReloadRefreshQueue();
        AtomicReference<Thread> refreshedOn = new AtomicReference<Thread>();
        Thread worker = new Thread(() -> queue.reloadCompleted(1L, true), "mmce-test-reload-worker");
        worker.start();
        worker.join(5000L);
        assertFalse(worker.isAlive());
        assertEquals(null, refreshedOn.get());
        assertTrue(queue.hasPending());
        assertTrue(queue.refreshIfReady(true, () -> 1L, () -> refreshedOn.set(Thread.currentThread())));
        assertEquals(Thread.currentThread(), refreshedOn.get());
    }

    @Test
    void aCompletionDuringTheCallbackSurvivesWithoutHoldingTheQueueLock() throws InterruptedException {
        MmceReloadRefreshQueue queue = new MmceReloadRefreshQueue();
        queue.reloadCompleted(1L, true);
        AtomicReference<Thread> workerRef = new AtomicReference<Thread>();
        assertTrue(queue.refreshIfReady(true, () -> 1L, () -> {
            CountDownLatch signaled = new CountDownLatch(1);
            Thread worker = new Thread(() -> {
                queue.reloadCompleted(2L, true);
                signaled.countDown();
            }, "mmce-test-next-reload");
            workerRef.set(worker);
            worker.start();
            try {
                assertTrue(signaled.await(5L, TimeUnit.SECONDS), "Callback must not hold the queue lock");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
        }));
        workerRef.get().join(5000L);
        assertFalse(workerRef.get().isAlive());
        assertTrue(queue.hasPending());
        AtomicInteger calls = new AtomicInteger();
        assertTrue(queue.refreshIfReady(true, () -> 2L, calls::incrementAndGet));
        assertEquals(1, calls.get());
        assertFalse(queue.hasPending());
    }

    @Test
    void callbackFailureDoesNotRetryEveryTickButAllowsALaterCompletion() {
        MmceReloadRefreshQueue queue = new MmceReloadRefreshQueue();
        queue.reloadCompleted(1L, true);
        assertThrows(IllegalStateException.class, () -> queue.refreshIfReady(true, () -> 1L, () -> {
            throw new IllegalStateException("Injected callback failure");
        }));
        AtomicInteger calls = new AtomicInteger();
        assertFalse(queue.hasPending());
        assertFalse(queue.refreshIfReady(true, () -> 1L, calls::incrementAndGet));
        queue.reloadCompleted(2L, true);
        assertTrue(queue.refreshIfReady(true, () -> 2L, calls::incrementAndGet));
        assertEquals(1, calls.get());
    }

    @Test
    void callbackFailureDoesNotEraseANewerCompletion() {
        MmceReloadRefreshQueue queue = new MmceReloadRefreshQueue();
        queue.reloadCompleted(1L, true);
        assertThrows(LinkageError.class, () -> queue.refreshIfReady(true, () -> 1L, () -> {
            queue.reloadCompleted(2L, true);
            throw new LinkageError("Injected optional plugin failure");
        }));
        assertTrue(queue.hasPending());
        assertTrue(queue.refreshIfReady(true, () -> 2L, () -> { }));
        assertFalse(queue.hasPending());
    }

    @Test
    void clearingADisabledBridgeRemovesPendingWork() {
        MmceReloadRefreshQueue queue = new MmceReloadRefreshQueue();
        queue.reloadCompleted(1L, true);
        queue.clear();
        assertFalse(queue.hasPending());
        assertFalse(queue.refreshIfReady(true, () -> 1L, () -> {
            throw new AssertionError("Disabled refresh");
        }));
    }

    @Test
    void duplicateAndDelayedOlderPostsCannotReplayAConsumedEpoch() {
        MmceReloadRefreshQueue queue = new MmceReloadRefreshQueue();
        AtomicInteger calls = new AtomicInteger();
        queue.reloadCompleted(2L, true);
        assertTrue(queue.refreshIfReady(true, () -> 2L, calls::incrementAndGet));
        queue.reloadCompleted(2L, true);
        queue.reloadCompleted(1L, true);
        queue.reloadCompleted(-1L, true);
        assertFalse(queue.hasPending());
        assertEquals(1, calls.get());
    }

    @Test
    void aStaleEpochOrReloadInProgressCannotRunTheCallback() {
        MmceReloadRefreshQueue queue = new MmceReloadRefreshQueue();
        AtomicInteger calls = new AtomicInteger();
        queue.reloadCompleted(1L, true);
        assertFalse(queue.refreshIfReady(true, () -> -1L, calls::incrementAndGet));
        queue.reloadCompleted(2L, true);
        assertFalse(queue.refreshIfReady(true, () -> 3L, calls::incrementAndGet));
        assertFalse(queue.hasPending());
        assertEquals(0, calls.get());
    }

    @Test
    void aNewCompletionDuringTheEpochCheckSurvivesTheStaleAttempt() {
        MmceReloadRefreshQueue queue = new MmceReloadRefreshQueue();
        AtomicInteger calls = new AtomicInteger();
        queue.reloadCompleted(1L, true);
        assertFalse(queue.refreshIfReady(true, () -> {
            queue.reloadStarted();
            queue.reloadCompleted(3L, true);
            return 3L;
        }, calls::incrementAndGet));
        assertTrue(queue.hasPending());
        assertTrue(queue.refreshIfReady(true, () -> 3L, calls::incrementAndGet));
        assertEquals(1, calls.get());
    }
}
