package net.createmod.ponder.foundation.content.mmce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import net.createmod.ponder.foundation.content.mmce.MmceRegistryReadGuard.ReloadInProgressException;
import org.junit.jupiter.api.Test;

class MmceRegistryReadGuardTest {

    @Test
    void readReturnsAValueAndTheSameStableEpoch() {
        MmceRegistryReadGuard guard = new MmceRegistryReadGuard();
        long epoch = guard.currentEpochLocked();
        assertTrue(epoch >= 0L);
        assertEquals("snapshot", guard.readLocked(() -> {
            assertEquals(epoch, guard.currentEpochLocked());
            return "snapshot";
        }));
    }

    @Test
    void anActiveReloadRejectsNativeAccessWithoutCallingTheSupplier() {
        MmceRegistryReadGuard guard = new MmceRegistryReadGuard();
        AtomicBoolean called = new AtomicBoolean();
        guard.beginLocked();
        assertEquals(MmceRegistryReadGuard.NO_COMPLETION, guard.currentEpochLocked());
        assertThrows(ReloadInProgressException.class, () -> guard.readLocked(() -> {
            called.set(true);
            return "partial";
        }));
        assertFalse(called.get());
        assertTrue(guard.completeLocked() >= 0L);
        assertEquals("complete", guard.readLocked(() -> "complete"));
    }

    @Test
    void nativePreWaitsForAnAlreadyRunningBoundedCopy() throws InterruptedException {
        MmceRegistryReadGuard guard = new MmceRegistryReadGuard();
        AtomicBoolean nativeMutation = new AtomicBoolean();
        CountDownLatch readEntered = new CountDownLatch(1);
        CountDownLatch releaseRead = new CountDownLatch(1);
        CountDownLatch preAttempted = new CountDownLatch(1);
        CountDownLatch preReturned = new CountDownLatch(1);
        CountDownLatch releasePost = new CountDownLatch(1);
        Worker reader = new Worker("guarded-native-read", () -> guard.readLocked(() -> {
            readEntered.countDown();
            await(releaseRead);
            assertFalse(nativeMutation.get(), "MMCE must not mutate during the guarded native copy");
            return "snapshot";
        }));
        Worker reloader = new Worker("native-reload", () -> {
            preAttempted.countDown();
            guard.beginLocked();
            nativeMutation.set(true);
            preReturned.countDown();
            await(releasePost);
            guard.completeLocked();
        });
        reader.start();
        try {
            await(readEntered);
            reloader.start();
            await(preAttempted);
            assertFalse(preReturned.await(100L, TimeUnit.MILLISECONDS));
            releaseRead.countDown();
            reader.finish();
            await(preReturned);
            assertThrows(ReloadInProgressException.class, () -> guard.readLocked(() -> "partial"));
        } finally {
            releaseRead.countDown();
            releasePost.countDown();
            reader.finish();
            reloader.finish();
        }
        assertEquals("after", guard.readLocked(() -> "after"));
    }

    @Test
    void nextPreCanRunBetweenCopiesButTheNextCopyIsRejected() {
        MmceRegistryReadGuard guard = new MmceRegistryReadGuard();
        CountDownLatch firstCopyDone = new CountDownLatch(1);
        CountDownLatch preReturned = new CountDownLatch(1);
        CountDownLatch releasePost = new CountDownLatch(1);
        Worker reloader = new Worker("reload-between-plugin-work", () -> {
            await(firstCopyDone);
            guard.beginLocked();
            preReturned.countDown();
            await(releasePost);
            guard.completeLocked();
        });
        reloader.start();
        try {
            assertEquals("first", guard.readLocked(() -> "first"));
            firstCopyDone.countDown();
            await(preReturned);
            assertThrows(ReloadInProgressException.class, () -> guard.readLocked(() -> "half updated"));
        } finally {
            firstCopyDone.countDown();
            releasePost.countDown();
            reloader.finish();
        }
        assertEquals("new generation", guard.readLocked(() -> "new generation"));
    }

    @Test
    void missingPostStaysClosedAndAForeignPostCannotUnlockIt() {
        MmceRegistryReadGuard guard = new MmceRegistryReadGuard();
        guard.beginLocked();
        Worker foreignPost = new Worker("unmatched-post", () ->
            assertEquals(MmceRegistryReadGuard.NO_COMPLETION, guard.completeLocked()));
        foreignPost.start();
        foreignPost.finish();
        assertEquals(MmceRegistryReadGuard.NO_COMPLETION, guard.currentEpochLocked());
        assertThrows(ReloadInProgressException.class, () -> guard.readLocked(() -> "partial"));
    }

    @Test
    void duplicatePostDoesNotAdvanceEpochOrProduceAnotherCompletion() {
        MmceRegistryReadGuard guard = new MmceRegistryReadGuard();
        assertEquals(MmceRegistryReadGuard.NO_COMPLETION, guard.completeLocked());
        guard.beginLocked();
        long completed = guard.completeLocked();
        assertEquals(completed, guard.currentEpochLocked());
        assertEquals(MmceRegistryReadGuard.NO_COMPLETION, guard.completeLocked());
        assertEquals(completed, guard.currentEpochLocked());
    }

    @Test
    void nestedOrRepeatedPreRequiresAllMatchingPosts() {
        MmceRegistryReadGuard guard = new MmceRegistryReadGuard();
        guard.beginLocked();
        guard.beginLocked();
        assertEquals(MmceRegistryReadGuard.NO_COMPLETION, guard.completeLocked());
        assertThrows(ReloadInProgressException.class, () -> guard.readLocked(() -> "inner only"));
        assertTrue(guard.completeLocked() >= 0L);
        assertEquals("outer complete", guard.readLocked(() -> "outer complete"));
    }

    @Test
    void overlappingThreadsMustEachCompleteTheirOwnReload() {
        MmceRegistryReadGuard guard = new MmceRegistryReadGuard();
        CountDownLatch secondPre = new CountDownLatch(1);
        CountDownLatch secondPost = new CountDownLatch(1);
        AtomicLong completed = new AtomicLong(MmceRegistryReadGuard.NO_COMPLETION);
        guard.beginLocked();
        Worker other = new Worker("second-native-reload", () -> {
            guard.beginLocked();
            secondPre.countDown();
            await(secondPost);
            completed.set(guard.completeLocked());
        });
        other.start();
        try {
            await(secondPre);
            assertEquals(MmceRegistryReadGuard.NO_COMPLETION, guard.completeLocked());
            assertEquals(MmceRegistryReadGuard.NO_COMPLETION, guard.completeLocked(),
                "Repeated Post from the first thread must not finish the second thread");
            assertThrows(ReloadInProgressException.class, () -> guard.readLocked(() -> "partial"));
        } finally {
            secondPost.countDown();
            other.finish();
        }
        assertTrue(completed.get() >= 0L);
        assertEquals(completed.get(), guard.currentEpochLocked());
    }

    @Test
    void reentrantReloadInsideAReadFailsClosedEvenWhenTheReaderCatchesIt() {
        MmceRegistryReadGuard guard = new MmceRegistryReadGuard();
        assertThrows(ReloadInProgressException.class, () -> guard.readLocked(() -> {
            assertThrows(ReloadInProgressException.class, guard::beginLocked);
            return "must not escape";
        }));
        assertEquals(MmceRegistryReadGuard.NO_COMPLETION, guard.completeLocked());
        assertThrows(ReloadInProgressException.class, () -> guard.readLocked(() -> "still unsafe"));
    }

    @Test
    void ordinaryNestedReadsAreAllowedAtTheSameEpoch() {
        MmceRegistryReadGuard guard = new MmceRegistryReadGuard();
        long expected = guard.currentEpochLocked();
        long actual = guard.readLocked(() -> guard.readLocked(guard::currentEpochLocked));
        assertEquals(expected, actual);
    }

    @Test
    void readerExceptionsReleaseTheMonitorAndReadDepth() {
        MmceRegistryReadGuard guard = new MmceRegistryReadGuard();
        IllegalArgumentException failure = new IllegalArgumentException("Injected read failure");
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> guard.readLocked(() -> {
            throw failure;
        })));
        guard.beginLocked();
        assertTrue(guard.completeLocked() >= 0L);
        assertEquals("recovered", guard.readLocked(() -> "recovered"));
    }

    @Test
    void checkedReadPreservesTheOriginalException() {
        MmceRegistryReadGuard guard = new MmceRegistryReadGuard();
        IOException failure = new IOException("Injected checked failure");
        assertSame(failure, assertThrows(IOException.class, () -> guard.readCheckedLocked(() -> {
            throw failure;
        })));
        guard.beginLocked();
        assertTrue(guard.completeLocked() >= 0L);
    }

    @Test
    void fatalReaderErrorsPropagateAndStillReleaseTheMonitor() {
        MmceRegistryReadGuard guard = new MmceRegistryReadGuard();
        OutOfMemoryError vmFailure = new OutOfMemoryError("Injected VM failure");
        assertSame(vmFailure, assertThrows(OutOfMemoryError.class, () -> guard.readLocked(() -> {
            throw vmFailure;
        })));
        ThreadDeath threadDeath = new ThreadDeath();
        assertSame(threadDeath, assertThrows(ThreadDeath.class, () -> guard.readLocked(() -> {
            throw threadDeath;
        })));
        guard.beginLocked();
        assertTrue(guard.completeLocked() >= 0L);
    }

    @Test
    void newPreInvalidatesOldEpochAndPostPublishesANewOne() {
        MmceRegistryReadGuard guard = new MmceRegistryReadGuard();
        long initial = guard.currentEpochLocked();
        guard.beginLocked();
        assertEquals(MmceRegistryReadGuard.NO_COMPLETION, guard.currentEpochLocked());
        long next = guard.completeLocked();
        assertTrue(next > initial);
        assertEquals(next, guard.readLocked(guard::currentEpochLocked).longValue());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5L, TimeUnit.SECONDS), "Timed out waiting for the test handshake");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static final class Worker {
        private final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        private final CountDownLatch finished = new CountDownLatch(1);
        private final Thread thread;

        private Worker(String name, Runnable action) {
            thread = new Thread(() -> {
                try {
                    action.run();
                } catch (Throwable problem) {
                    failure.set(problem);
                } finally {
                    finished.countDown();
                }
            }, name);
            thread.setDaemon(true);
        }

        private void start() {
            thread.start();
        }

        private void finish() {
            if (thread.getState() == Thread.State.NEW) {
                return;
            }
            await(finished);
            if (failure.get() != null) {
                throw new AssertionError("Worker failed", failure.get());
            }
        }
    }
}
