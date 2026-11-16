package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class PreviewPointerInputTest {

    @Test
    void captionWheelCancelsTimelineDragBeforeAnyDefaultMouseDispatch() throws IOException {
        PreviewPointerInput input = new PreviewPointerInput();
        input.beginTimelineDrag();
        AtomicInteger defaultCalls = new AtomicInteger();
        AtomicInteger page = new AtomicInteger();
        assertTrue(input.routeWheel(-120, true, direction -> {
            assertFalse(input.isTimelineDragging());
            assertFalse(input.isPreviewDragging());
            page.addAndGet(direction);
        }, () -> defaultCalls.incrementAndGet()));
        assertEquals(1, page.get());
        assertEquals(0, defaultCalls.get());
        assertFalse(input.isTimelineDragging(), "subsequent motion must not seek the timeline");
    }

    @Test
    void captionWheelCancelsCameraDragAndMapsWheelDirection() throws IOException {
        PreviewPointerInput input = new PreviewPointerInput();
        input.beginPreviewDrag();
        AtomicInteger page = new AtomicInteger(2);
        assertTrue(input.routeWheel(120, true, page::addAndGet, () -> {
            throw new AssertionError("caption wheel leaked to the default drag callback");
        }));
        assertEquals(1, page.get());
        assertFalse(input.isPreviewDragging());
    }

    @Test
    void wheelOutsideTheCaptionKeepsNormalDragDispatch() throws IOException {
        PreviewPointerInput input = new PreviewPointerInput();
        input.beginTimelineDrag();
        AtomicInteger defaultCalls = new AtomicInteger();
        assertFalse(input.routeWheel(-120, false, direction -> {
            throw new AssertionError("wheel outside caption paged the caption");
        }, () -> {
            assertTrue(input.isTimelineDragging());
            defaultCalls.incrementAndGet();
        }));
        assertEquals(1, defaultCalls.get());
    }

    @Test
    void nonWheelEventsStillReachDefaultDispatchIncludingMouseRelease() throws IOException {
        PreviewPointerInput input = new PreviewPointerInput();
        input.beginPreviewDrag();
        AtomicInteger defaultCalls = new AtomicInteger();
        assertFalse(input.routeWheel(0, true, direction -> {
            throw new AssertionError("a non-wheel event paged the caption");
        }, () -> {
            defaultCalls.incrementAndGet();
            input.cancelDrag();
        }));
        assertEquals(1, defaultCalls.get());
        assertFalse(input.isPreviewDragging());
        assertFalse(input.isTimelineDragging());
    }
}
