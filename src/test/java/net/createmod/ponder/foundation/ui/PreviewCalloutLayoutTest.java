package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import net.createmod.ponder.foundation.ui.PreviewCalloutLayout.Placement;
import net.createmod.ponder.foundation.ui.PreviewCalloutLayout.Side;
import net.createmod.ponder.foundation.ui.PreviewDebugLayout.Rect;

class PreviewCalloutLayoutTest {

    private static final Rect AREA = new Rect(18, 76, 764, 284);

    @Test
    void triesAboveThenRightThenLeftThenBelowWhileRespectingObstacles() {
        Rect target = new Rect(330, 190, 80, 60);
        Placement above = PreviewCalloutLayout.place(AREA, target, 100, 60, Collections.emptyList(), null);
        assertEquals(Side.ABOVE, above.side);
        assertEquals(target.y - PreviewCalloutLayout.GAP, above.panel.bottom());
        Placement right = PreviewCalloutLayout.place(AREA, target, 100, 60,
            Collections.singletonList(above.panel), null);
        assertEquals(Side.RIGHT, right.side);
        Placement left = PreviewCalloutLayout.place(AREA, target, 100, 60,
            Arrays.asList(above.panel, right.panel), null);
        assertEquals(Side.LEFT, left.side);
        Placement below = PreviewCalloutLayout.place(AREA, target, 100, 60,
            Arrays.asList(above.panel, right.panel, left.panel), null);
        assertEquals(Side.BELOW, below.side);
        assertEquals(0, PreviewCalloutLayout.intersectionArea(target, below.panel));
    }

    @Test
    void aSlightlyMovedTargetKeepsTheLastUsablePanelPosition() {
        Placement before = PreviewCalloutLayout.place(AREA, new Rect(330, 190, 80, 60),
            100, 60, Collections.emptyList(), null);
        Placement after = PreviewCalloutLayout.place(AREA, new Rect(332, 191, 80, 60),
            100, 60, Collections.emptyList(), before);
        assertSame(before, after);
    }

    @Test
    void aFarMovingTargetKeepsItsSideButBringsThePanelAlong() {
        Rect target = new Rect(330, 190, 80, 60);
        Placement before = PreviewCalloutLayout.place(AREA, target, 100, 60,
            Collections.emptyList(), null);
        Rect moved = new Rect(480, 210, 80, 60);
        Placement after = PreviewCalloutLayout.place(AREA, moved, 100, 60,
            Collections.emptyList(), before);
        assertEquals(before.side, after.side);
        assertEquals(before.panel.x + 150, after.panel.x);
        assertEquals(before.panel.y + 20, after.panel.y);
        assertEquals(moved.y - PreviewCalloutLayout.GAP, after.panel.bottom());
    }

    @Test
    void anInvalidPreviousPositionRelocatesWithoutChangingTheModelArea() {
        Rect target = new Rect(330, 190, 80, 60);
        Placement before = PreviewCalloutLayout.place(AREA, target, 100, 60, Collections.emptyList(), null);
        Placement after = PreviewCalloutLayout.place(AREA, target, 100, 60,
            Collections.singletonList(before.panel), before);
        assertNotSame(before, after);
        assertEquals(Side.RIGHT, after.side);
        assertEquals(18, AREA.x);
        assertEquals(764, AREA.width);
        assertEquals(0, PreviewCalloutLayout.intersectionArea(before.panel, after.panel));
    }

    @Test
    void multipleTargetPanelsRemainSeparateAndAvoidEachOther() {
        Rect firstTarget = new Rect(240, 190, 70, 50);
        Rect secondTarget = new Rect(340, 190, 70, 50);
        Placement first = PreviewCalloutLayout.place(AREA, firstTarget, 150, 70,
            Collections.singletonList(secondTarget), null);
        Placement second = PreviewCalloutLayout.place(AREA, secondTarget, 150, 70,
            Arrays.asList(firstTarget, first.panel), null);
        assertInside(first.panel, AREA);
        assertInside(second.panel, AREA);
        assertEquals(0, PreviewCalloutLayout.intersectionArea(first.panel, second.panel));
        assertEquals(0, PreviewCalloutLayout.intersectionArea(first.panel, secondTarget));
        assertEquals(0, PreviewCalloutLayout.intersectionArea(second.panel, firstTarget));
    }

    @Test
    void minimumViewportUsesAnEdgeFallbackAndKeepsItsTargetAccessible() {
        Rect area = new Rect(18, 76, 284, 74);
        Rect target = new Rect(80, 82, 160, 62);
        Placement placement = PreviewCalloutLayout.place(area, target, 180, 74, Collections.emptyList(), null);
        assertTrue(placement.fallback);
        assertEquals(Side.EDGE, placement.side);
        assertInside(placement.panel, area);
        assertEquals(180, placement.panel.width);
        assertEquals(74, placement.panel.height);
    }

    @Test
    void fallbackShrinksIntoMenuFreeSpaceRatherThanCoveringTheMenu() {
        Rect area = new Rect(18, 76, 284, 74);
        Rect menu = new Rect(18, 76, 150, 74);
        Placement placement = PreviewCalloutLayout.place(area, new Rect(18, 76, 284, 74), 180, 74,
            Collections.singletonList(menu), null);
        assertTrue(placement.fallback);
        assertInside(placement.panel, area);
        assertEquals(134, placement.panel.width);
        assertEquals(0, PreviewCalloutLayout.intersectionArea(menu, placement.panel));
    }

    @Test
    void untargetedAndOffscreenDescriptionsStayWithinScreenEdges() {
        for (Rect target : Arrays.asList(null, new Rect(-1000, -1000, 30, 30),
            new Rect(2000, 2000, 40, 40), new Rect(18, 76, 1, 1), new Rect(781, 359, 1, 1))) {
            Placement placement = PreviewCalloutLayout.place(AREA, target, 200, 80,
                Collections.emptyList(), null);
            assertInside(placement.panel, AREA);
            assertTrue(placement.panel.width > 0 && placement.panel.height > 0);
        }
    }

    @Test
    void menuCoveringAllAvailableSpaceProducesAnEmptyNonInteractivePanel() {
        Placement placement = PreviewCalloutLayout.place(AREA, null, 200, 80,
            Collections.singletonList(AREA), null);
        assertEquals(0, placement.panel.width);
        assertEquals(0, placement.panel.height);
    }

    @Test
    void aTinyTargetUsesTheWholeVisibleModelEnvelopeInsteadOfCoveringItsNeighbours() {
        Rect area = new Rect(18, 76, 714, 234);
        Rect model = new Rect(282, 164, 190, 94);
        Rect tinyTarget = new Rect(350, 196, 8, 8);
        Placement oldTargetOnly = PreviewCalloutLayout.place(area, tinyTarget, 220, 52, Collections.emptyList(), null);
        assertTrue(PreviewCalloutLayout.intersectionArea(oldTargetOnly.panel, model) > 0);
        Placement first = PreviewCalloutLayout.place(area, tinyTarget, 220, 52, Collections.emptyList(), null, model);
        Placement second = PreviewCalloutLayout.place(area, new Rect(385, 205, 8, 8), 220, 52,
            Collections.singletonList(first.panel), null, model);
        assertFalse(first.fallback);
        assertFalse(second.fallback);
        assertEquals(Side.ABOVE, first.side);
        assertEquals(model.y - PreviewCalloutLayout.GAP, first.panel.bottom());
        assertEquals(0, PreviewCalloutLayout.intersectionArea(first.panel, model));
        assertEquals(0, PreviewCalloutLayout.intersectionArea(second.panel, model));
        assertEquals(0, PreviewCalloutLayout.intersectionArea(first.panel, second.panel));
        assertInside(first.panel, area);
        assertInside(second.panel, area);
    }

    @Test
    void aPreviousTargetOnlyPlacementCannotRemainAcrossNewlyVisibleModelGeometry() {
        Rect target = new Rect(350, 210, 8, 8);
        Placement previous = PreviewCalloutLayout.place(AREA, target, 180, 60, Collections.emptyList(), null);
        Rect model = new Rect(280, 170, 200, 120);
        assertTrue(PreviewCalloutLayout.intersectionArea(previous.panel, model) > 0);
        Placement current = PreviewCalloutLayout.place(AREA, target, 180, 60, Collections.emptyList(), previous, model);
        assertNotSame(previous, current);
        assertEquals(0, PreviewCalloutLayout.intersectionArea(current.panel, model));
        assertEquals(18, AREA.x);
        assertEquals(764, AREA.width);
    }

    private static void assertInside(Rect actual, Rect area) {
        assertTrue(actual.x >= area.x && actual.y >= area.y);
        assertTrue(actual.right() <= area.right() && actual.bottom() <= area.bottom());
    }
}
