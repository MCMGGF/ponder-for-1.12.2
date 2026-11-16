package net.createmod.ponder.foundation.ui;

import java.util.function.Supplier;

/** One display frame, retained while its scene and animation time are unchanged. */
final class PreviewFrameCache<T> {

    private Object scene;
    private int tickBits;
    private T frame;

    T get(Object scene, float tick, Supplier<T> factory) {
        int bits = Float.floatToIntBits(tick);
        if (frame == null || this.scene != scene || tickBits != bits) {
            T next = factory.get();
            this.scene = scene;
            tickBits = bits;
            frame = next;
        }
        return frame;
    }

    void clear() {
        scene = null;
        frame = null;
    }
}
