package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PreviewModelControlsTest {

    @Test
    void segmentsShareAnEdgeWithoutAnOverlappingHitRegion() {
        PreviewModelControls controls = new PreviewModelControls(28, 212);
        assertTrue(controls.structure.contains(67, 212));
        assertFalse(controls.structure.contains(68, 212));
        assertTrue(controls.appearance.contains(68, 212));
        assertFalse(controls.contains(108, 212));
        assertFalse(controls.contains(28, 228));
    }

    @Test
    void debugControlsStayInTheExistingFooterAcrossGuiScales() {
        for (int[] size : new int[][] { { 320, 240 }, { 480, 270 }, { 720, 420 }, { 1920, 1080 } }) {
            PreviewDebugLayout layout = new PreviewDebugLayout(size[0], size[1]);
            PreviewModelControls controls =
                new PreviewModelControls(layout.preview.x + 6, layout.preview.bottom() - 18);
            assertTrue(controls.structure.y >= layout.preview.bottom() - 20);
            assertTrue(controls.appearance.right() <= layout.preview.right() - 6);
            assertTrue(controls.structure.bottom() <= layout.preview.bottom());
            for (PreviewDebugLayout.Rect button : layout.toolbar) {
                assertTrue(controls.structure.bottom() <= button.y);
            }
        }
    }

    @Test
    void showcaseControlsDoNotMoveThePlaybackBarOrCaptionBand() {
        for (int height : new int[] { 240, 270, 480, 1080 }) {
            PreviewModelControls controls = new PreviewModelControls(28, height - 28);
            assertTrue(controls.structure.y > height - 32);
            assertTrue(controls.structure.y > height - 72);
            assertTrue(controls.structure.bottom() <= height - 12);
        }
    }

    @Test
    void actualShowcaseSegmentsMatchTheSharedFontAwareFooter() {
        for (int font : new int[] { 9, 18, 24, 32 }) {
            PreviewShowcaseChromeLayout chrome = PreviewShowcaseChromeLayout.layout(320, 240, font);
            PreviewModelControls controls = new PreviewModelControls(28, chrome.modelControlsY, chrome.modelControlsHeight);
            assertEquals(chrome.modelControlsBand.y, controls.structure.y);
            assertEquals(chrome.modelControlsBand.height, controls.structure.height);
            assertEquals(chrome.modelControlsBand.right(), controls.appearance.right());
            assertTrue(4 + font + 1 <= controls.structure.height, "Model-tab glyph and shadow fit their actual hit box");
        }
    }

    @Test
    void actualShowcaseHitBoxesNeverIntersectLessonToolbar() {
        for (int[] size : new int[][] { { 320, 240 }, { 480, 270 }, { 640, 360 }, { 960, 540 } }) {
            for (int font : new int[] { 9, 18, 24, 32 }) {
                PreviewShowcaseChromeLayout chrome = PreviewShowcaseChromeLayout.layout(size[0], size[1], font);
                PreviewModelControls controls = new PreviewModelControls(28, chrome.modelControlsY, chrome.modelControlsHeight);
                for (PreviewDebugLayout.Rect button : PreviewLessonControls.layout(size[0], size[1], font)) {
                    assertTrue(button.bottom() <= controls.structure.y);
                    assertTrue(button.bottom() <= controls.appearance.y);
                }
                assertTrue(controls.appearance.right() < chrome.modelFooterTextBand.x);
            }
        }
    }

    @Test
    void tallModelControlsPreserveTheirSeparateActualClickTargets() {
        PreviewModelControls controls = new PreviewModelControls(28, 180, 39);
        assertTrue(controls.structure.contains(67, 218));
        assertFalse(controls.structure.contains(68, 218));
        assertTrue(controls.appearance.contains(68, 218));
        assertFalse(controls.contains(28, 219));
        assertEquals(PreviewModelControls.HEIGHT, new PreviewModelControls(0, 0, -1).structure.height);
    }
}
