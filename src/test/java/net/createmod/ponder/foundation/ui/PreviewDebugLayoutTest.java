package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PreviewDebugLayoutTest {

    @Test
    void compactAndWideLayoutsKeepPanelsAndToolbarInsideTheScreen() {
        for (int width : new int[] { 320, 427, 640, 719, 720, 960, 1280, 1920 }) {
            for (int height : new int[] { 240, 360, 419, 420, 480, 720, 1080 }) {
                PreviewDebugLayout layout = new PreviewDebugLayout(width, height);
                assertInside(layout.components, width, height);
                assertInside(layout.componentRows, width, height);
                assertInside(layout.content, width, height);
                assertInside(layout.preview, width, height);
                assertInside(layout.summaryRows, width, height);
                assertInside(layout.operationRows, width, height);
                assertFalse(overlap(layout.components, layout.content));
                assertFalse(overlap(layout.componentRows, layout.preview));
                assertTrue(layout.preview.width >= 178);
                assertTrue(layout.preview.height >= 140);
                if (layout.compact) {
                    for (PreviewDebugLayout.Rect tab : layout.tabs) {
                        assertInside(tab, width, height);
                        assertTrue(tab.bottom() <= layout.preview.y);
                    }
                } else {
                    assertFalse(overlap(layout.preview, layout.summaryRows));
                    assertFalse(overlap(layout.preview, layout.operationRows));
                    assertFalse(overlap(layout.summaryRows, layout.operationRows));
                }
                for (int i = 0; i < layout.toolbar.length; i++) {
                    PreviewDebugLayout.Rect button = layout.toolbar[i];
                    assertInside(button, width, height);
                    assertTrue(button.width >= 38);
                    assertTrue(layout.content.bottom() < button.y);
                    assertTrue(layout.components.bottom() < button.y);
                    if (i > 0) {
                        assertFalse(overlap(layout.toolbar[i - 1], button));
                    }
                }
            }
        }
    }

    @Test
    void listHitRegionsMatchExactlyTheDrawnRows() {
        PreviewDebugLayout layout = new PreviewDebugLayout(320, 240);
        for (PreviewDebugLayout.Rect rows : new PreviewDebugLayout.Rect[] {
            layout.componentRows, layout.summaryRows, layout.operationRows
        }) {
            assertTrue(rows.lines() > 0);
            assertEquals(0, rows.height % PreviewDebugLayout.LINE_HEIGHT);
            assertTrue(rows.contains(rows.x, rows.y));
            assertTrue(rows.contains(rows.right() - 1, rows.bottom() - 1));
            assertFalse(rows.contains(rows.x, rows.bottom()));
            assertFalse(rows.contains(rows.right(), rows.y));
            assertFalse(rows.contains(rows.x - 1, rows.y));
        }
    }

    private static void assertInside(PreviewDebugLayout.Rect rect, int width, int height) {
        assertTrue(rect.width > 0 && rect.height > 0);
        assertTrue(rect.x >= 0 && rect.y >= 0 && rect.right() <= width && rect.bottom() <= height);
    }

    private static boolean overlap(PreviewDebugLayout.Rect left, PreviewDebugLayout.Rect right) {
        return left.x < right.right() && left.right() > right.x && left.y < right.bottom() && left.bottom() > right.y;
    }
}
