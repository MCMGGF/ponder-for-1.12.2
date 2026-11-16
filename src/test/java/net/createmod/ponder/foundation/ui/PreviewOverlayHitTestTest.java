package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PreviewOverlayHitTestTest {

    @Test
    void centralCaptionsAndSnapshotPanelsBlockTheCameraOnlyInsideTheirBounds() {
        PreviewOverlayHitTest hitTest = new PreviewOverlayHitTest();
        hitTest.add(150, 90, 220, 54);
        hitTest.add(30, 160, 176, 120);
        assertTrue(hitTest.contains(150, 90));
        assertTrue(hitTest.contains(369, 143));
        assertTrue(hitTest.contains(80, 200));
        assertFalse(hitTest.contains(370, 110));
        assertFalse(hitTest.contains(300, 150));
        assertFalse(hitTest.contains(149, 100));
    }

    @Test
    void theNextFrameDoesNotRetainExpiredOrMovedOverlays() {
        PreviewOverlayHitTest hitTest = new PreviewOverlayHitTest();
        hitTest.add(10, 10, 20, 20);
        hitTest.clear();
        assertFalse(hitTest.contains(15, 15));
        hitTest.add(90, 90, 20, 20);
        assertFalse(hitTest.contains(15, 15));
        assertTrue(hitTest.contains(95, 95));
    }

    @Test
    void zeroSizedAndInvalidRegionsNeverBlockInput() {
        PreviewOverlayHitTest hitTest = new PreviewOverlayHitTest();
        hitTest.add(0, 0, 0, 40);
        hitTest.add(0, 0, 40, -1);
        assertFalse(hitTest.contains(0, 0));
    }

    @Test
    void largeCoordinatesDoNotOverflowIntoTheOppositeSideOfTheScreen() {
        PreviewOverlayHitTest hitTest = new PreviewOverlayHitTest();
        hitTest.add(Integer.MAX_VALUE - 10, 20, 20, 10);
        assertTrue(hitTest.contains(Integer.MAX_VALUE, 25));
        assertFalse(hitTest.contains(Integer.MIN_VALUE, 25));
    }
}
