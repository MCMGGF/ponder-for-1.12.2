package net.createmod.ponder.compat.ftbquests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.createmod.ponder.compat.ftbquests.FtbQuestReflection.ReturnView;
import net.createmod.ponder.compat.ftbquests.FtbQuestReflection.ScrollPosition;
import org.junit.jupiter.api.Test;

class QuestReturnViewTest {

    @Test
    void canvasOffsetsRetainNegativeAndFractionalCoordinates() {
        ScrollPosition scroll = new ScrollPosition(-124.25, 450.75);
        assertEquals(-124.25, scroll.x);
        assertEquals(450.75, scroll.y);
    }

    @Test
    void contentOffsetsClampToRelayoutDimensions() {
        ScrollPosition scroll = new ScrollPosition(-124.25, 450.75).clamp(240, 300, 600, 320);
        assertEquals(0.0, scroll.x);
        assertEquals(280.0, scroll.y);
        assertEquals(0.0, scroll.clamp(0, 300, 0, 320).y);
        assertEquals(450.75, new ScrollPosition(0, 450.75).clamp(10, 5, 900, 200).y);
    }

    @Test
    void invalidAndExtremeOffsetsRemainBounded() {
        ScrollPosition invalid = new ScrollPosition(Double.NaN, Double.POSITIVE_INFINITY);
        assertEquals(0.0, invalid.x);
        assertEquals(0.0, invalid.y);
        ScrollPosition large = new ScrollPosition(Double.MAX_VALUE, 32)
            .clamp(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
        assertEquals((double) Integer.MAX_VALUE, large.x);
        assertEquals(0.0, large.y);
    }

    @Test
    void restorationStopsAfterTwoUnchangedCompletedDraws() {
        ReturnView view = emptyView();
        view.restored = true;
        view.changedSinceDraw = true;
        assertFalse(view.finishDraw());
        assertFalse(view.finishDraw());
        assertTrue(view.finishDraw());
    }

    @Test
    void aDeferredPanelRebuildRestartsStabilityConfirmation() {
        ReturnView view = emptyView();
        view.restored = true;
        assertFalse(view.finishDraw());
        view.changedSinceDraw = true;
        assertFalse(view.finishDraw());
        assertFalse(view.finishDraw());
        assertTrue(view.finishDraw());
    }

    @Test
    void absentOrContinuallyRebuildingPanelsCannotRestoreForever() {
        ReturnView absent = emptyView();
        ReturnView rebuilding = emptyView();
        rebuilding.restored = true;
        for (int frame = 0; frame < 8; frame++) {
            rebuilding.changedSinceDraw = true;
            assertEquals(frame == 7, absent.finishDraw());
            assertEquals(frame == 7, rebuilding.finishDraw());
        }
    }

    private static ReturnView emptyView() {
        return new ReturnView(null, new Object(), new Object(), new Object(), new Object(), 1, 2, null,
            new ScrollPosition[0]);
    }
}
