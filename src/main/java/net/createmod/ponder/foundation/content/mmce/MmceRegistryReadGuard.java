package net.createmod.ponder.foundation.content.mmce;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Excludes audited native MMCE reads from mutations after the reload Pre event.
 * Readers must be bounded data copies, never plugin callbacks or game tasks.
 */
public final class MmceRegistryReadGuard {

    public static final long NO_COMPLETION = -1L;

    private static final MmceRegistryReadGuard SHARED = new MmceRegistryReadGuard();

    private final ThreadLocal<Integer> readDepth = new ThreadLocal<Integer>();
    private final ThreadLocal<Integer> reloadDepth = new ThreadLocal<Integer>();
    private int activeReloads;
    private long epoch;
    private boolean invalidated;

    MmceRegistryReadGuard() {
    }

    public static <T> T read(Supplier<T> reader) {
        return SHARED.readLocked(reader);
    }

    public static <T, E extends Exception> T readChecked(CheckedSupplier<T, E> reader) throws E {
        return SHARED.readCheckedLocked(reader);
    }

    public static void beginReload() {
        SHARED.beginLocked();
    }

    /**
     * Returns a new stable epoch only for the final matching completion.
     * Unmatched or duplicate Post events never unlock another thread's reload.
     */
    public static long completeReload() {
        return SHARED.completeLocked();
    }

    public static long currentEpoch() {
        return SHARED.currentEpochLocked();
    }

    <T> T readLocked(Supplier<T> reader) {
        Objects.requireNonNull(reader, "reader");
        return readCheckedLocked(reader::get);
    }

    synchronized <T, E extends Exception> T readCheckedLocked(CheckedSupplier<T, E> reader) throws E {
        Objects.requireNonNull(reader, "reader");
        requireReadable();
        int depth = depth(readDepth);
        readDepth.set(Integer.valueOf(depth + 1));
        long startedAt = epoch;
        try {
            T result = reader.get();
            // A same-thread reload attempt must not be hidden by a reader's catch.
            requireReadable();
            if (epoch != startedAt) {
                throw new ReloadInProgressException("MMCE registry changed during a guarded read");
            }
            return result;
        } finally {
            restoreDepth(readDepth, depth);
        }
    }

    synchronized void beginLocked() {
        epoch++;
        if (depth(readDepth) != 0) {
            invalidated = true;
            throw new ReloadInProgressException("Cannot begin a native reload inside an MMCE registry read");
        }
        activeReloads++;
        reloadDepth.set(Integer.valueOf(depth(reloadDepth) + 1));
    }

    synchronized long completeLocked() {
        int depth = depth(reloadDepth);
        if (depth == 0) {
            return NO_COMPLETION;
        }
        restoreDepth(reloadDepth, depth - 1);
        activeReloads--;
        if (activeReloads != 0 || invalidated) {
            return NO_COMPLETION;
        }
        return ++epoch;
    }

    synchronized long currentEpochLocked() {
        return activeReloads != 0 || invalidated ? NO_COMPLETION : epoch;
    }

    private void requireReadable() {
        if (activeReloads != 0 || invalidated) {
            throw new ReloadInProgressException("MMCE registry reload has not completed");
        }
    }

    private static int depth(ThreadLocal<Integer> local) {
        Integer depth = local.get();
        return depth == null ? 0 : depth.intValue();
    }

    private static void restoreDepth(ThreadLocal<Integer> local, int depth) {
        if (depth == 0) {
            local.remove();
        } else {
            local.set(Integer.valueOf(depth));
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T, E extends Exception> {
        T get() throws E;
    }

    public static final class ReloadInProgressException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private ReloadInProgressException(String message) {
            super(message);
        }
    }
}
