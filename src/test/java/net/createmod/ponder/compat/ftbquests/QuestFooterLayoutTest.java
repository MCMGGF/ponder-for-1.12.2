package net.createmod.ponder.compat.ftbquests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuestFooterLayoutTest {

    @Test
    void reservesNonOverlappingFooterOnSmallAndLargeGuiScales() {
        int[][] screens = {{320, 240}, {427, 240}, {640, 360}, {960, 540}, {1920, 1080}};
        for (int[] screen : screens) {
            for (int panelWidth : new int[] {200, screen[0] - 10}) {
                int panelX = (screen[0] - panelWidth) / 2;
                int panelHeight = screen[1] - 10;
                QuestFooterLayout layout = QuestFooterLayout.create(panelX, 5, panelWidth, panelHeight,
                    16, screen[0], screen[1]);
                assertNotNull(layout);
                assertTrue(5 + 16 + layout.contentHeight < layout.y);
                assertTrue(layout.x >= 0 && layout.x + layout.width <= screen[0]);
                assertTrue(layout.y >= 0 && layout.y + layout.height <= screen[1]);
                assertEquals(panelHeight - 17 - 26, layout.contentHeight);
            }
        }
    }

    @Test
    void refusesImpossibleBoundsInsteadOfCoveringTaskButtons() {
        assertNull(QuestFooterLayout.create(10, 10, 40, 200, 16, 320, 240));
        assertNull(QuestFooterLayout.create(10, 2, 200, 60, 16, 320, 64));
        assertNull(QuestFooterLayout.create(10, 230, 200, 200, 16, 320, 240));
    }

    @Test
    void shortNativeQuestGrowsAroundItsCenterWithoutShrinkingItsContents() {
        QuestFooterLayout layout = QuestFooterLayout.create(60, 90, 201, 60, 16, 320, 240);
        assertNotNull(layout);
        assertEquals(86, layout.panelHeight);
        assertEquals(77, layout.panelY);
        assertEquals(43, layout.contentHeight);
        assertEquals(90 + 30, layout.panelY + layout.panelHeight / 2);
    }

    @Test
    void growthUsesOnlyAvailableScreenSpaceAndKeepsTheMinimumContentHeight() {
        QuestFooterLayout layout = QuestFooterLayout.create(60, 90, 201, 60, 16, 320, 150);
        assertNotNull(layout);
        assertEquals(86, layout.panelHeight);
        assertTrue(layout.panelY >= 5 && layout.panelY + layout.panelHeight <= 145);
        QuestFooterLayout constrained = QuestFooterLayout.create(60, 5, 201, 60, 16, 320, 75);
        assertNotNull(constrained);
        assertEquals(65, constrained.panelHeight);
        assertTrue(constrained.contentHeight >= 20);
    }

    @Test
    void mouseBoundsMatchVisibleFooterExactly() {
        QuestFooterLayout layout = QuestFooterLayout.create(20, 20, 200, 180, 16, 320, 240);
        assertNotNull(layout);
        assertTrue(layout.contains(layout.x, layout.y));
        assertTrue(layout.contains(layout.x + layout.width - 1, layout.y + layout.height - 1));
        assertFalse(layout.contains(layout.x + layout.width, layout.y));
        assertFalse(layout.contains(layout.x, layout.y - 1));
    }
}
