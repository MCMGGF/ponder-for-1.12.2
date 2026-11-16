package net.createmod.ponder.compat.mmce;

import java.util.function.LongSupplier;

/**
 * Coalesces completion signals without retaining native events or game objects.
 */
final class MmceReloadRefreshQueue {

    private static final long NONE = -1L;
    private volatile long pendingEpoch = NONE;
    private long lastRequestedEpoch = NONE;

    synchronized void reloadStarted() {
        pendingEpoch = NONE;
    }

    synchronized void reloadCompleted(long epoch, boolean registryReady) {
        if (epoch < 0L || epoch <= lastRequestedEpoch) {
            return;
        }
        lastRequestedEpoch = epoch;
        // Startup indexing will include these changes; do not replay them later.
        pendingEpoch = registryReady ? epoch : NONE;
    }

    boolean hasPending() {
        return pendingEpoch != NONE;
    }

    synchronized void clear() {
        pendingEpoch = NONE;
    }

    boolean refreshIfReady(boolean clientMainThread, LongSupplier currentEpoch, Runnable refresh) {
        if (!clientMainThread) {
            return false;
        }
        long requested = takePending();
        if (requested == NONE || requested != currentEpoch.getAsLong()) {
            return false;
        }
        // The MMCE adapter guards each native read; never lock arbitrary plugins.
        refresh.run();
        return true;
    }

    private synchronized long takePending() {
        long requested = pendingEpoch;
        pendingEpoch = NONE;
        return requested;
    }
}
