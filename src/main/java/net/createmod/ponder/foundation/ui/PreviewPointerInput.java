package net.createmod.ponder.foundation.ui;

import java.io.IOException;
import java.util.function.IntConsumer;

/** Drag ownership and wheel dispatch, independent of the native input backend. */
final class PreviewPointerInput {

    private boolean previewDragging;
    private boolean timelineDragging;

    void beginPreviewDrag() {
        cancelDrag();
        previewDragging = true;
    }

    void beginTimelineDrag() {
        cancelDrag();
        timelineDragging = true;
    }

    boolean isPreviewDragging() {
        return previewDragging;
    }

    boolean isTimelineDragging() {
        return timelineDragging;
    }

    void cancelDrag() {
        previewDragging = false;
        timelineDragging = false;
    }

    boolean routeWheel(int wheel, boolean overCaption, IntConsumer turnPage, DefaultInput defaultInput)
        throws IOException {
        if (wheel != 0 && overCaption) {
            cancelDrag();
            turnPage.accept(wheel > 0 ? -1 : 1);
            return true;
        }
        defaultInput.handle();
        return false;
    }

    interface DefaultInput {
        void handle() throws IOException;
    }
}
