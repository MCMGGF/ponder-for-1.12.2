package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PreviewCaptionPagingTest {

    @Test
    void everyWrappedLineRemainsReachableWithoutOverrunningTheCaptionArea() {
        for (int height : new int[] { 240, 360, 480, 720, 1080 }) {
            int bottom = PreviewCaptionPaging.bottom(18, height - 36, height - 72);
            int top = PreviewCaptionPaging.top(18, bottom);
            assertTrue(top >= 92, "caption must stay below the header");
            assertTrue(bottom <= height - 90, "caption must stay above timeline labels");
            for (int count : new int[] { 1, 4, 5, 25, 5000 }) {
                PreviewCaptionPaging paging = new PreviewCaptionPaging();
                Object caption = new Object();
                paging.configure(caption, count, bottom - top);
                int nextLine = 0;
                for (int page = 0; page < paging.pageCount(); page++) {
                    assertEquals(nextLine, paging.firstLine());
                    assertTrue(paging.endLine() > paging.firstLine());
                    assertTrue(paging.height() <= bottom - top);
                    assertTrue(paging.height() <= PreviewCaptionPaging.MAX_HEIGHT);
                    nextLine = paging.endLine();
                    paging.turn(1);
                    paging.configure(caption, count, bottom - top);
                }
                assertEquals(count, nextLine);
                assertFalse(paging.turn(1));
                paging.turn(-Integer.MAX_VALUE);
                assertEquals(0, paging.firstLine());
                assertFalse(paging.turn(-1));
            }
        }
    }

    @Test
    void pageHeightIsStableIncludingTheLastPage() {
        PreviewCaptionPaging paging = new PreviewCaptionPaging();
        paging.configure(new Object(), 8, 58);
        assertTrue(paging.isPaged());
        int height = paging.height();
        paging.turn(2);
        assertEquals(2, paging.endLine() - paging.firstLine());
        assertEquals(height, paging.height());
    }

    @Test
    void resizingPreservesTheReadingPositionWithinTheNewPage() {
        PreviewCaptionPaging paging = new PreviewCaptionPaging();
        Object caption = new Object();
        paging.configure(caption, 30, 58);
        paging.turn(4);
        int first = paging.firstLine();
        paging.configure(caption, 30, 108);
        assertTrue(paging.firstLine() <= first);
        assertTrue(paging.endLine() > first);
    }

    @Test
    void changingTheCaptionOrClosingTheScreenResetsThePage() {
        PreviewCaptionPaging paging = new PreviewCaptionPaging();
        Object caption = new Object();
        paging.configure(caption, 30, 58);
        paging.turn(4);
        paging.configure(new Object(), 30, 58);
        assertEquals(0, paging.page());
        paging.turn(4);
        paging.clear();
        paging.configure(caption, 30, 58);
        assertEquals(0, paging.page());
    }

    @Test
    void textAndEveryGuiPageRemainReachableAtTheMinimumExplanationHeight() {
        PreviewCaptionPaging paging = new PreviewCaptionPaging();
        Object content = new Object();
        paging.configure(content, 100, 36, 4, 10);
        assertEquals(104, paging.pageCount());
        for (int line = 0; line < 100; line++) {
            assertEquals(line, paging.firstLine());
            assertEquals(line + 1, paging.endLine());
            assertEquals(-1, paging.imageIndex());
            assertTrue(paging.height() <= 36);
            paging.turn(1);
            paging.configure(content, 100, 36, 4, 10);
        }
        for (int image = 0; image < 4; image++) {
            assertEquals(image, paging.imageIndex());
            assertEquals(100, paging.firstLine());
            assertEquals(100, paging.endLine());
            paging.turn(1);
        }
        assertFalse(paging.turn(1));
    }

    @Test
    void resizingOnAnImageRetainsThatImageAndImageOnlyPagesHaveNoBlankTextPage() {
        PreviewCaptionPaging paging = new PreviewCaptionPaging();
        Object content = new Object();
        paging.configure(content, 12, 36, 3, 10);
        paging.turn(13);
        assertEquals(1, paging.imageIndex());
        paging.configure(content, 12, 156, 3, 10);
        assertEquals(1, paging.imageIndex());
        paging.configure(new Object(), 0, 74, 1, 10);
        assertEquals(1, paging.pageCount());
        assertEquals(0, paging.imageIndex());
        assertFalse(paging.isPaged());
    }

    @Test
    void tallerFontsStillLeaveOneLineAndPaginationInsideTheSmallestDock() {
        for (int fontHeight : new int[] { 9, 10, 12, 16, 40 }) {
            PreviewCaptionPaging paging = new PreviewCaptionPaging();
            paging.configure(new Object(), 2, 36, 0, fontHeight);
            assertTrue(paging.height() <= 36);
            assertTrue(paging.endLine() > paging.firstLine());
            assertTrue(6 + paging.lineHeight() <= 36 - PreviewCaptionPaging.FOOTER_HEIGHT);
        }
    }

    @Test
    void fixedExplanationPanelsUseTheirFullHeightWithoutLegacyBubbleLimits() {
        PreviewCaptionPaging paging = new PreviewCaptionPaging();
        Object content = new Object();
        paging.configure(content, 40, 500, 0, 10);
        assertFalse(paging.isPaged());
        assertEquals(1, paging.pageCount());
        assertEquals(40, paging.endLine());
        assertTrue(paging.height() > PreviewCaptionPaging.MAX_HEIGHT);
        assertTrue(paging.height() <= 500);

        paging.configure(content, 100, 500, 2, 10);
        assertEquals(47, paging.endLine());
        assertEquals(5, paging.pageCount());
        paging.turn(4);
        assertEquals(1, paging.imageIndex());
        paging.configure(content, 100, 74, 2, 10);
        assertEquals(1, paging.imageIndex(), "shrinking the dock must preserve the selected GUI page");
    }
}
