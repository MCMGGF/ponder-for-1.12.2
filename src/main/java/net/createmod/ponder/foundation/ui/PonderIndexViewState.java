package net.createmod.ponder.foundation.ui;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** A GUI refreshes once per published index, not once per rendered frame. */
final class PonderIndexViewState {

    private long generation = Long.MIN_VALUE;

    boolean refresh(long current, Runnable update) {
        if (generation == current) {
            return false;
        }
        generation = current;
        update.run();
        return true;
    }

    void invalidate() {
        generation = Long.MIN_VALUE;
    }

    long generation() {
        return generation;
    }

    static <T, K> int retain(List<T> values, K selected, int fallback, Function<T, K> key) {
        if (values.isEmpty()) {
            return 0;
        }
        int clamped = Math.max(0, Math.min(values.size() - 1, fallback));
        if (selected != null) {
            if (Objects.equals(selected, key.apply(values.get(clamped)))) {
                return clamped;
            }
            for (int i = 0; i < values.size(); i++) {
                if (Objects.equals(selected, key.apply(values.get(i)))) {
                    return i;
                }
            }
        }
        return clamped;
    }
}
