package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PreviewLessonControlsTest {
    @Test void oilPracticeFitsWithoutRemovingAnyPlaybackOrSkinControls() {
        for (int width : new int[] {320, 427, 640, 960, 1920}) {
            for (int font : new int[] {9, 18, 24}) {
                PreviewDebugLayout.Rect[] buttons = PreviewLessonControls.layout(width, 240, font, true);
                assertEquals(12, buttons.length);
                for (int i = 0; i < buttons.length; i++) {
                    assertTrue(buttons[i].x >= 0 && buttons[i].right() <= width);
                    assertEquals(PreviewShowcaseChromeLayout.layout(width, 240, font).toolbarY, buttons[i].y);
                    if (i > 0) assertTrue(buttons[i - 1].right() < buttons[i].x);
                }
            }
        }
    }

    @Test void nonOilToolbarRetainsItsExactPreviousGeometry() {
        PreviewDebugLayout.Rect[] old = PreviewLessonControls.layout(320, 240, 9);
        PreviewDebugLayout.Rect[] ordinary = PreviewLessonControls.layout(320, 240, 9, false);
        for (int i = 0; i < old.length; i++) {
            assertEquals(old[i].x, ordinary[i].x);
            assertEquals(old[i].y, ordinary[i].y);
            assertEquals(old[i].width, ordinary[i].width);
        }
    }
    @Test void toolbarFitsMinimumGuiAndDoesNotTouchExplanationOrFooter() {
        for (int width : new int[] {320, 427, 640, 960, 1920}) {
            for (int height : new int[] {240, 360, 480, 1080}) {
                PreviewDebugLayout.Rect[] buttons = PreviewLessonControls.layout(width, height);
                assertEquals(11, buttons.length);
                for (int i = 0; i < buttons.length; i++) {
                    assertTrue(buttons[i].x >= 0);
                    assertTrue(buttons[i].right() <= width);
                    assertTrue(buttons[i].y > PreviewExplanationLayout.showcase(width, height).model.bottom());
                    assertTrue(buttons[i].bottom() <= height - 20);
                    if (i > 0) assertTrue(buttons[i - 1].right() < buttons[i].x);
                }
            }
        }
    }
    @Test void stepNavigationUsesStrictPreviousAndNextAndClampsEndpoints() {
        java.util.List<Integer> ticks = Arrays.asList(0, 20, 60);
        assertEquals(20, PreviewLessonControls.step(ticks, 40, 80, -1));
        assertEquals(0, PreviewLessonControls.step(ticks, 20, 80, -1));
        assertEquals(60, PreviewLessonControls.step(ticks, 20, 80, 1));
        assertEquals(80, PreviewLessonControls.step(ticks, 60, 80, 1));
        assertEquals(0, PreviewLessonControls.step(ticks, 0, 80, -1));
    }

    @Test void defaultToolbarUsesTheSharedNinePixelGeometry() {
        PreviewDebugLayout.Rect[] implicit = PreviewLessonControls.layout(320, 240);
        PreviewDebugLayout.Rect[] explicit = PreviewLessonControls.layout(320, 240, 9);
        for (int i = 0; i < implicit.length; i++) {
            assertEquals(explicit[i].x, implicit[i].x);
            assertEquals(explicit[i].y, implicit[i].y);
            assertEquals(explicit[i].width, implicit[i].width);
            assertEquals(explicit[i].height, implicit[i].height);
            assertEquals(188, implicit[i].y);
            assertEquals(20, implicit[i].height);
        }
    }

    @Test void fontAwareToolbarKeepsLabelsIndependentAndUsesTheSharedHeight() {
        for (int font : new int[] {9, 12, 18, 24}) {
            PreviewShowcaseChromeLayout chrome = PreviewShowcaseChromeLayout.layout(320, 240, font);
            PreviewDebugLayout.Rect[] buttons = PreviewLessonControls.layout(320, 240, font);
            for (int i = 0; i < buttons.length; i++) {
                assertEquals(chrome.toolbarY, buttons[i].y);
                assertEquals(chrome.toolbarHeight, buttons[i].height);
                assertTrue(buttons[i].bottom() + 4 <= chrome.modelControlsY);
                assertTrue(buttons[i].x >= 0 && buttons[i].right() <= 320);
            }
        }
    }
}
