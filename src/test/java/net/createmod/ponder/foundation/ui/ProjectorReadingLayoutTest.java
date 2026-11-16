package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectorReadingLayoutTest {
    @Test void onlyNativeGuiOneReceivesTheExtraReadableScale() {
        assertEquals(2, new ProjectorReadingLayout(1280,720,1280,720).scale);
        assertEquals(2, new ProjectorReadingLayout(1920,1080,1920,1080).scale);
        assertEquals(2, new ProjectorReadingLayout(640,480,640,480).scale);
        assertEquals(1, new ProjectorReadingLayout(640,360,1280,720).scale);
        assertEquals(1, new ProjectorReadingLayout(320,240,1280,960).scale);
        assertEquals(1, new ProjectorReadingLayout(639,480,639,480).scale);
        assertEquals(1, new ProjectorReadingLayout(640,479,640,479).scale);
        ProjectorReadingLayout one = new ProjectorReadingLayout(1280,720,1280,720);
        ProjectorReadingLayout two = new ProjectorReadingLayout(640,360,1280,720);
        assertEquals(one.width, two.width);
        assertEquals(one.height, two.height);
        assertEquals(18D, 9D * one.displayHeight / one.height);
        assertEquals(18D, 9D * two.displayHeight / two.height);
    }

    @Test void oddDimensionsKeepTheLastPixelAndAllCanvasContentInBounds() {
        for (int[] size : new int[][] {{1280,720,1280,720}, {1920,1080,1920,1080},
            {1001,701,1001,701}, {640,360,1280,720}, {320,240,1280,960}, {1,1,1,1}}) {
            ProjectorReadingLayout layout = new ProjectorReadingLayout(size[0],size[1],size[2],size[3]);
            assertEquals(0, layout.localX(0)); assertEquals(0, layout.localY(0));
            assertEquals(layout.width - 1, layout.localX(size[0] - 1));
            assertEquals(layout.height - 1, layout.localY(size[1] - 1));
            assertEquals(-1, layout.localX(-1)); assertEquals(-1, layout.localY(-1));
            assertEquals(layout.width, layout.localX(size[0]));
            assertEquals(layout.height, layout.localY(size[1]));
            PreviewDebugLayout.Rect content = layout.content();
            assertTrue(content.x >= 0 && content.y >= 0 && content.width > 0 && content.height > 0);
            assertTrue(content.right() <= layout.width && content.bottom() <= layout.height);
            assertArrayEquals(new int[] {0,0,size[2],size[3]}, layout.framebufferScissor(
                new PreviewDebugLayout.Rect(0,0,layout.width,layout.height)));
        }
        ProjectorReadingLayout odd = new ProjectorReadingLayout(1001,701,1001,701);
        assertEquals(501, odd.width); assertEquals(351, odd.height);
    }

    @Test void displayedPagingTargetsAndPointerCoordinatesUseTheSameCanvas() {
        for (ProjectorReadingLayout layout : new ProjectorReadingLayout[] {
            new ProjectorReadingLayout(1280,720,1280,720), new ProjectorReadingLayout(1001,701,1001,701),
            new ProjectorReadingLayout(640,360,1280,720)}) {
            PreviewDebugLayout.Rect button = new PreviewDebugLayout.Rect(17,31,22,14);
            int left = screenBoundary(button.x,layout.screenWidth,layout.width);
            int right = screenBoundary(button.right(),layout.screenWidth,layout.width);
            int top = screenBoundary(button.y,layout.screenHeight,layout.height);
            int bottom = screenBoundary(button.bottom(),layout.screenHeight,layout.height);
            assertTrue(button.contains(layout.localX(left),layout.localY(top)));
            assertTrue(button.contains(layout.localX(right-1),layout.localY(bottom-1)));
            assertFalse(button.contains(layout.localX(left-1),layout.localY(top)));
            assertFalse(button.contains(layout.localX(right),layout.localY(top)));
            assertFalse(button.contains(layout.localX(left),layout.localY(bottom)));
        }
    }

    @Test void clipsUseFramebufferRatiosRatherThanTheUnscaledGuiFactor() {
        PreviewDebugLayout.Rect panel = new PreviewDebugLayout.Rect(8,24,200,100);
        assertArrayEquals(new int[] {16,472,400,200},
            new ProjectorReadingLayout(1280,720,1280,720).framebufferScissor(panel));
        assertArrayEquals(new int[] {16,472,400,200},
            new ProjectorReadingLayout(640,360,1280,720).framebufferScissor(panel));
        ProjectorReadingLayout odd = new ProjectorReadingLayout(1001,701,1001,701);
        assertArrayEquals(new int[] {15,453,401,201}, odd.framebufferScissor(panel));
        assertArrayEquals(new int[] {0,661,40,40}, odd.framebufferScissor(new PreviewDebugLayout.Rect(-10,-20,30,40)));
        assertArrayEquals(new int[] {1001,0,0,0}, odd.framebufferScissor(
            new PreviewDebugLayout.Rect(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE)));
    }

    @Test void unicodeTextAndMenuPagesStayReachableAtTheSameReadableScale() {
        PreviewTextLayoutTest.MetricsFont font = new PreviewTextLayoutTest.MetricsFont();
        font.setUnicodeFlag(true);
        font.FONT_HEIGHT = 18;
        String text = String.join("",Collections.nCopies(250,"\u673a\u5668"));
        for (ProjectorReadingLayout layout : new ProjectorReadingLayout[] {
            new ProjectorReadingLayout(640,480,640,480), new ProjectorReadingLayout(1280,720,1280,720),
            new ProjectorReadingLayout(320,240,1280,960)}) {
            int textWidth = Math.min(220,layout.content().width) - 12;
            List<String> lines = PreviewTextLayout.wrap(font,text,textWidth);
            assertEquals(text,String.join("",lines));
            for (String line : lines) assertTrue(font.getStringWidth(line) <= textWidth);
            PreviewCaptionPaging paging = new PreviewCaptionPaging();
            paging.configure(text,lines.size(),layout.content().height,1,Math.max(10,font.FONT_HEIGHT));
            List<String> read = new ArrayList<>(); int images = 0;
            for (int page = 0; page < paging.pageCount(); page++) {
                if (paging.imageIndex() >= 0) images++;
                else read.addAll(lines.subList(paging.firstLine(),paging.endLine()));
                paging.turn(1);
            }
            assertEquals(lines,read); assertEquals(1,images);
        }
    }

    @Test void resizingOrFramebufferChangesInvalidateThePreviousMapping() {
        ProjectorReadingLayout layout = new ProjectorReadingLayout(1280,720,1280,720);
        assertTrue(layout.matches(1280,720,1280,720));
        assertFalse(layout.matches(640,360,1280,720));
        assertFalse(layout.matches(1280,720,1920,1080));
        assertThrows(IllegalArgumentException.class, () -> new ProjectorReadingLayout(0,240,1280,960));
        assertThrows(IllegalArgumentException.class, () -> new ProjectorReadingLayout(320,240,-1,960));
    }

    private static int screenBoundary(int local, int screen, int canvas) {
        return (int) (((long)local*screen+canvas-1)/canvas);
    }
}
