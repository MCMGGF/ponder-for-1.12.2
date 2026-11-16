package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PreviewShowcaseChromeLayoutTest {
    private static final int[][] SIZES = {
        {320, 240}, {360, 240}, {480, 300}, {500, 267}, {640, 480},
        {960, 540}, {1280, 720}, {1920, 1080}
    };

    @Test
    void compactUnicodeRegressionReservesActualFooterTabsAndHoverAtNinePixelFont() {
        PreviewShowcaseChromeLayout layout = PreviewShowcaseChromeLayout.layout(320, 240, 9);
        assertEquals(228, layout.hoverTextY);
        assertEquals(216, layout.footerTextY);
        assertEquals(212, layout.modelControlsY);
        assertEquals(16, layout.modelControlsHeight);
        assertEquals(188, layout.toolbarY);
        assertEquals(20, layout.toolbarHeight);
        assertEquals(182, layout.playbackY);
        assertEquals(171, layout.playbackLabelY);
        assertEquals(167, layout.contentBottom);
        assertFalse(overlap(layout.toolbarBand, layout.footerTextBand));
        assertFalse(overlap(layout.toolbarBand, layout.modelControlsBand));
        assertFalse(overlap(layout.modelControlsBand, layout.modelFooterTextBand));
        assertFalse(overlap(layout.modelControlsBand, layout.hoverBand));
    }

    @Test
    void regularNinePixelLayoutKeepsItsExistingBottomAnchors() {
        PreviewShowcaseChromeLayout layout = PreviewShowcaseChromeLayout.layout(960, 540, 9);
        assertEquals(528, layout.hoverTextY);
        assertEquals(516, layout.footerTextY);
        assertEquals(512, layout.modelControlsY);
        assertEquals(488, layout.toolbarY);
        assertEquals(468, layout.playbackY);
    }

    @Test
    void oldOverloadIsExactlyTheNinePixelConfiguration() {
        for (int[] size : SIZES) {
            PreviewShowcaseChromeLayout implicit = PreviewShowcaseChromeLayout.layout(size[0], size[1]);
            PreviewShowcaseChromeLayout explicit = PreviewShowcaseChromeLayout.layout(size[0], size[1], 9);
            assertSameBand(explicit.hoverBand, implicit.hoverBand);
            assertSameBand(explicit.footerBand, implicit.footerBand);
            assertSameBand(explicit.modelControlsBand, implicit.modelControlsBand);
            assertSameBand(explicit.toolbarBand, implicit.toolbarBand);
            assertSameBand(explicit.playbackBand, implicit.playbackBand);
            assertSameBand(explicit.playbackLabelBand, implicit.playbackLabelBand);
            assertEquals(explicit.contentBottom, implicit.contentBottom);
        }
    }

    @Test
    void actualToolbarButtonsDoNotIntersectStatusOrEitherModelTab() {
        for (int[] size : SIZES) {
            for (int font : new int[] {1, 9, 12, 18, 24, 32}) {
                PreviewShowcaseChromeLayout chrome = PreviewShowcaseChromeLayout.layout(size[0], size[1], font);
                PreviewModelControls model = new PreviewModelControls(chrome.modelControlsBand.x,
                    chrome.modelControlsY, chrome.modelControlsHeight);
                for (PreviewDebugLayout.Rect button : PreviewLessonControls.layout(size[0], size[1], font)) {
                    assertFalse(overlap(button, chrome.footerTextBand));
                    assertFalse(overlap(button, chrome.modelFooterTextBand));
                    assertFalse(overlap(button, model.structure));
                    assertFalse(overlap(button, model.appearance));
                    assertFalse(overlap(button, chrome.modelControlsBand));
                    assertFalse(overlap(button, chrome.hoverBand));
                }
            }
        }
    }

    @Test
    void allBottomBandsAreDisjointForOrdinaryAndModelFooters() {
        for (int[] size : SIZES) {
            for (int font : new int[] {1, 9, 12, 18, 24, 32}) {
                PreviewShowcaseChromeLayout layout = PreviewShowcaseChromeLayout.layout(size[0], size[1], font);
                assertDisjoint(layout.playbackLabelBand, layout.playbackBand, layout.toolbarBand,
                    layout.footerTextBand, layout.hoverBand);
                assertDisjoint(layout.playbackLabelBand, layout.playbackBand, layout.toolbarBand,
                    layout.modelControlsBand, layout.modelFooterTextBand, layout.hoverBand);
            }
        }
    }

    @Test
    void largerFontsIncreaseControlHeightsAndMovePlaybackBeforeTheToolbar() {
        PreviewShowcaseChromeLayout normal = PreviewShowcaseChromeLayout.layout(320, 240, 9);
        for (int font : new int[] {12, 18, 24, 32}) {
            PreviewShowcaseChromeLayout layout = PreviewShowcaseChromeLayout.layout(320, 240, font);
            assertEquals(Math.max(20, Math.max(font + 8, 2 * font - 6)), layout.toolbarHeight);
            assertEquals(font + 7, layout.modelControlsHeight);
            assertTrue(layout.toolbarY < normal.toolbarY);
            assertTrue(layout.playbackY < normal.playbackY);
            assertTrue(layout.playbackY + 3 + 3 <= layout.toolbarY);
            assertTrue(layout.playbackBand.bottom() + 2 <= layout.toolbarBand.y);
            assertTrue(layout.toolbarBand.bottom() + 4 <= layout.modelControlsBand.y);
            assertTrue(layout.footerTextBand.bottom() + 2 <= layout.hoverBand.y);
        }
    }

    @Test
    void modelAndExplanationsStayBeforeTheActualFontAwarePlaybackLabel() {
        for (int[] size : SIZES) {
            for (int font : new int[] {9, 12, 18, 24, 32}) {
                PreviewShowcaseChromeLayout chrome = PreviewShowcaseChromeLayout.layout(size[0], size[1], font);
                PreviewExplanationLayout content = PreviewExplanationLayout.showcase(size[0], size[1], font);
                assertTrue(content.model.bottom() <= chrome.contentBottom);
                assertTrue(content.explanation.bottom() <= chrome.contentBottom);
                assertTrue(content.model.bottom() + 4 <= chrome.playbackLabelY);
                assertFalse(overlap(content.model, chrome.playbackLabelBand));
                assertFalse(overlap(content.explanation, chrome.toolbarBand));
            }
        }
    }

    @Test
    void tinyAndExtremeConfigurationsKeepClippedBandsInsideTheScreen() {
        for (int width : new int[] {-1, 0, 1, 35, 160, 320}) {
            for (int height : new int[] {-1, 0, 1, 20, 80, 240}) {
                for (int font : new int[] {-1, 0, 1, 9, 36, 256, Integer.MAX_VALUE}) {
                    PreviewShowcaseChromeLayout layout = PreviewShowcaseChromeLayout.layout(width, height, font);
                    for (PreviewDebugLayout.Rect band : new PreviewDebugLayout.Rect[] {
                        layout.hoverBand, layout.footerBand, layout.footerTextBand, layout.modelFooterTextBand,
                        layout.modelControlsBand, layout.toolbarBand, layout.playbackBand, layout.playbackLabelBand
                    }) {
                        assertTrue(band.x >= 0 && band.y >= 0 && band.width >= 0 && band.height >= 0);
                        assertTrue(band.right() <= Math.max(0, width));
                        assertTrue(band.bottom() <= Math.max(0, height));
                    }
                    assertTrue(layout.contentBottom >= 0 && layout.contentBottom <= Math.max(0, height));
                }
            }
        }
    }

    @Test
    void resizingAndFontChangesReturnToExactlyTheSameChrome() {
        PreviewShowcaseChromeLayout before = PreviewShowcaseChromeLayout.layout(320, 240, 9);
        for (int[] size : SIZES) PreviewShowcaseChromeLayout.layout(size[0], size[1], 24);
        PreviewShowcaseChromeLayout after = PreviewShowcaseChromeLayout.layout(320, 240, 9);
        assertSameBand(before.toolbarBand, after.toolbarBand);
        assertSameBand(before.modelControlsBand, after.modelControlsBand);
        assertSameBand(before.footerTextBand, after.footerTextBand);
        assertSameBand(before.playbackLabelBand, after.playbackLabelBand);
        assertEquals(before.contentBottom, after.contentBottom);
    }

    @Test
    void bandsIncludeRealShadowAndPlaybackCursorInk() {
        PreviewShowcaseChromeLayout layout = PreviewShowcaseChromeLayout.layout(320, 240, 9);
        assertEquals(10, layout.footerTextBand.height);
        assertEquals(10, layout.hoverBand.height);
        assertEquals(10, layout.playbackLabelBand.height);
        assertEquals(layout.playbackY - 1, layout.playbackBand.y);
        assertEquals(5, layout.playbackBand.height);
        assertTrue(layout.playbackLabelBand.bottom() <= layout.playbackBand.y);
        assertTrue(layout.playbackBand.bottom() + 2 <= layout.toolbarBand.y);
    }

    @Test
    void standardGuiButtonTextAndShadowFitInsideTheSharedToolbarHeight() {
        for (int font : new int[] {1, 9, 12, 18, 24, 32, 64, 256}) {
            PreviewShowcaseChromeLayout layout = PreviewShowcaseChromeLayout.layout(1920, 2160, font);
            int actualTextY = (layout.toolbarHeight - 8) / 2;
            assertTrue(actualTextY >= 0);
            assertTrue(actualTextY + font + 1 <= layout.toolbarHeight);
            assertTrue(4 + font + 1 <= layout.modelControlsHeight);
        }
    }

    private static void assertSameBand(PreviewDebugLayout.Rect expected, PreviewDebugLayout.Rect actual) {
        assertEquals(expected.x, actual.x);
        assertEquals(expected.y, actual.y);
        assertEquals(expected.width, actual.width);
        assertEquals(expected.height, actual.height);
    }

    private static void assertDisjoint(PreviewDebugLayout.Rect... bands) {
        for (int i = 0; i < bands.length; i++) {
            for (int j = i + 1; j < bands.length; j++) assertFalse(overlap(bands[i], bands[j]));
        }
    }

    private static boolean overlap(PreviewDebugLayout.Rect a, PreviewDebugLayout.Rect b) {
        return a.width > 0 && a.height > 0 && b.width > 0 && b.height > 0
            && a.x < b.right() && a.right() > b.x && a.y < b.bottom() && a.bottom() > b.y;
    }
}
