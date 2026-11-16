package net.createmod.ponder.authoring.capture;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaptureReviewLayoutTest {
    @Test void logical320By240KeepsUnscaledTextAndEveryControlSeparateAtAllSupportedFontHeights() {
        for (int font : new int[] {9, 14, 18, 28}) {
            CaptureReviewLayout layout = new CaptureReviewLayout(320, 240, font);
            assertTrue(layout.readable, "font=" + font); assertTrue(layout.rows >= 1);
            assertTrue(layout.titleY + font <= layout.menuY);
            assertTrue(layout.menuY + font <= layout.tabsY);
            assertTrue(layout.tabsY + layout.buttonHeight <= layout.bodyY);
            assertTrue(layout.bodyY + layout.rows * layout.lineHeight <= layout.bodyBottom);
            assertTrue(layout.bodyBottom <= layout.pagerY);
            assertTrue(layout.pagerY + layout.buttonHeight <= layout.buttonsY);
            assertTrue(layout.buttonsY + layout.buttonHeight <= 240);
            assertTrue(layout.buttonHeight >= font + 6);
        }
    }
    @Test void allFiftyFourSlotsTheirWrappedContinuationsAndAllFourDataFieldsRemainReachableExactlyOnce() {
        for (int font : new int[] {9, 14, 18, 28}) for (int lines : new int[] {6, 54, 108, 4}) {
            CaptureReviewLayout layout = new CaptureReviewLayout(320, 240, font);
            List<Integer> read = new ArrayList<>();
            for (int page = 0; page < layout.pageCount(lines); page++) {
                assertTrue(layout.endLine(page, lines) - layout.firstLine(page, lines) <= layout.rows);
                for (int index = layout.firstLine(page, lines); index < layout.endLine(page, lines); index++) read.add(index);
            }
            assertEquals(lines, read.size());
            for (int index = 0; index < lines; index++) assertEquals(index, read.get(index));
        }
    }
    @Test void extraordinaryFontSizeIsNotSilentlyScaledDownOrTreatedAsReadable() {
        CaptureReviewLayout layout = new CaptureReviewLayout(320, 240, 64);
        assertFalse(layout.readable); assertEquals(0, layout.rows); assertEquals(66, layout.lineHeight);
        assertThrows(IllegalArgumentException.class, () -> new CaptureReviewLayout(0, 240, 18));
    }
}
