package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.createmod.ponder.foundation.ui.OilLineControlLayout.Rect;

class OilLineControlLayoutTest {
    @Test void fixedBandsDoNotOverlapAcrossScreenAndFontSizes() {
        for (int[] viewport : new int[][] {{320, 240}, {427, 240}, {640, 360}, {1280, 720}, {1920, 1080}}) {
            for (int font : new int[] {9, 18, 24, 32}) {
                OilLineControlLayout layout = new OilLineControlLayout(viewport[0], viewport[1], font);
                List<Rect> bands = new ArrayList<>();
                bands.add(layout.title); bands.add(layout.marker); bands.add(layout.body); bands.add(layout.feedback);
                java.util.Collections.addAll(bands, layout.stations);
                java.util.Collections.addAll(bands, layout.transport);
                for (Rect band : bands) assertInside(band, new Rect(0, 0, layout.width, layout.height));
                for (int i = 0; i < bands.size(); i++) for (int j = i + 1; j < bands.size(); j++)
                    assertFalse(bands.get(i).intersects(bands.get(j)), viewport[0] + "x" + viewport[1] + " font=" + font + " bands=" + i + "," + j);
            }
        }
    }

    @Test void smallestSupportedViewportRetainsAFullActionAndReadableText() {
        for (int font : new int[] {9, 18, 24, 32}) {
            OilLineControlLayout layout = new OilLineControlLayout(320, 240, font);
            assertTrue(layout.compact);
            Rect content = layout.textArea(layout.actions);
            assertTrue(content.height >= layout.controlHeight, "font " + font);
            assertTrue(content.height >= layout.lineHeight, "font " + font);
            for (Rect cell : layout.actionCells(5, 0)) assertEquals(layout.controlHeight, cell.height);
            assertTrue(layout.feedback.height == 0 || layout.feedbackText().height >= font);
        }
        assertEquals(0, new OilLineControlLayout(320, 240, 24).marker.height);
    }

    @Test void everyStationActionIsReachableOnEveryPageWithoutPagerCollision() {
        for (int font : new int[] {9, 18, 24, 32}) for (int width : new int[] {320, 1280}) {
            OilLineControlLayout layout = new OilLineControlLayout(width, width == 320 ? 240 : 720, font);
            for (int count : new int[] {3, 4, 5}) {
                int total = 0;
                for (int page = 0; page < OilLineControlLayout.pageCount(count, layout.actionsPerPage()); page++) {
                    List<Rect> cells = layout.actionCells(count, page);
                    total += cells.size();
                    for (Rect cell : cells) {
                        assertInside(cell, layout.textArea(layout.actions));
                        assertEquals(layout.controlHeight, cell.height);
                        for (Rect pager : layout.pager(layout.actions, 2)) assertFalse(cell.intersects(pager));
                    }
                    for (int i = 0; i < cells.size(); i++) for (int j = i + 1; j < cells.size(); j++)
                        assertFalse(cells.get(i).intersects(cells.get(j)));
                }
                assertEquals(count, total);
            }
        }
    }

    @Test void wideScreenShowsSeparateStateGoalsAndOperations() {
        OilLineControlLayout layout = new OilLineControlLayout(1280, 720, 18);
        assertFalse(layout.compact);
        assertEquals(0, layout.views.length);
        assertFalse(layout.stats.intersects(layout.goals));
        assertFalse(layout.stats.intersects(layout.actions));
        assertFalse(layout.goals.intersects(layout.actions));
        for (Rect panel : new Rect[] {layout.stats, layout.goals, layout.actions}) {
            assertInside(panel, layout.body);
            assertTrue(layout.textArea(panel).height >= layout.lineHeight);
        }
    }

    @Test void compactViewTabsAndFeedbackControlsHaveIndependentHitAreas() {
        for (int font : new int[] {9, 18, 24, 32}) {
            OilLineControlLayout layout = new OilLineControlLayout(320, 240, font);
            for (Rect view : layout.views) assertFalse(view.intersects(layout.actions));
            Rect feedbackText = layout.feedbackText();
            assertInside(feedbackText, layout.feedback);
            for (Rect pager : layout.feedbackPager()) {
                assertInside(pager, layout.feedback);
                assertFalse(feedbackText.intersects(pager));
            }
            for (Rect pager : layout.pager(layout.goals, 4)) assertInside(pager, layout.goals);
        }
    }

    @Test void pageBoundsAreClampedAndNeverLoseFinalAction() {
        assertEquals(1, OilLineControlLayout.pageCount(0, 0));
        assertEquals(0, OilLineControlLayout.pageStart(-4, 5, 2));
        assertEquals(4, OilLineControlLayout.pageStart(99, 5, 2));
        OilLineControlLayout layout = new OilLineControlLayout(320, 240, 24);
        assertEquals(1, layout.actionCells(5, 99).size());
        assertThrows(IllegalArgumentException.class, () -> layout.actionCells(-1, 0));
    }

    @Test void longChineseTextSurvivesWrappingAndPagination() {
        String text = "当前抽油机的内部蒸汽不足，请检查北侧供汽、原油输出和分馏塔的连接，再关闭供汽并观察内部缓存。";
        OilLineControlLayout layout = new OilLineControlLayout(320, 240, 18);
        List<String> lines = PreviewTextLayout.wrap(text, layout.textArea(layout.stats).width,
            value -> value.codePointCount(0, value.length()) * 18);
        StringBuilder restored = new StringBuilder();
        for (int page = 0; page < OilLineControlLayout.pageCount(lines.size(), layout.textRows(layout.stats)); page++) {
            int start = OilLineControlLayout.pageStart(page, lines.size(), layout.textRows(layout.stats));
            for (int i = start; i < Math.min(lines.size(), start + layout.textRows(layout.stats)); i++) restored.append(lines.get(i));
        }
        assertEquals(text, restored.toString());
        assertTrue(lines.size() > 1);
    }

    private static void assertInside(Rect inner, Rect outer) {
        assertTrue(inner.x >= outer.x && inner.y >= outer.y && inner.right() <= outer.right() && inner.bottom() <= outer.bottom(),
            "inner=" + inner.x + "," + inner.y + "," + inner.width + "," + inner.height
                + " outer=" + outer.x + "," + outer.y + "," + outer.width + "," + outer.height);
    }
}
