package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.gui.GuiButton;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

/** Exercises screen callbacks without initializing Minecraft or an OpenGL context. */
class PreviewCaptionInputTest {

    @Test
    void clickingCaptionBodyPausesPlaybackAndCancelsTheHeldDrag() throws Exception {
        for (boolean timeline : new boolean[] { false, true }) {
            PonderDebugScreen screen = captionScreen();
            PreviewPointerInput input = pointer(screen);
            if (timeline) {
                input.beginTimelineDrag();
            } else {
                input.beginPreviewDrag();
            }
            screen.playing = true;
            screen.mouseClicked(50, 50, 0);
            assertFalse(screen.playing);
            assertTrue(field("captionFocused").getBoolean(screen));
            assertFalse(input.isTimelineDragging());
            assertFalse(input.isPreviewDragging());
        }
    }

    @Test
    void pageKeysOperateOnTheCaptionAndCancelAnOutstandingDrag() throws Exception {
        PonderDebugScreen screen = captionScreen();
        PreviewCaptionPaging paging = (PreviewCaptionPaging) field("captionPaging").get(screen);
        paging.configure(new Object(), 30, 58);
        screen.mouseClicked(50, 50, 0);
        pointer(screen).beginTimelineDrag();
        screen.playing = true;
        screen.keyTyped('\0', Keyboard.KEY_NEXT);
        assertEquals(1, paging.page());
        assertFalse(screen.playing);
        assertFalse(pointer(screen).isTimelineDragging());
        screen.keyTyped('\0', Keyboard.KEY_PRIOR);
        assertEquals(0, paging.page());
        assertEquals(0, screen.getPlaybackTickValue(), "caption page keys must not seek the scene");
    }

    @Test
    void changingDebugTabsImmediatelyDropsTheOldPreviewHitRegion() throws Exception {
        PonderDebugScreen screen = new PonderDebugScreen(null, 0);
        Constructor<PonderDebugScreen.PreviewLayout> constructor =
            PonderDebugScreen.PreviewLayout.class.getDeclaredConstructor(int.class, int.class, int.class, int.class);
        constructor.setAccessible(true);
        screen.lastPreviewLayout = constructor.newInstance(0, 0, 180, 120);
        field("explanationLayout").set(screen, PreviewExplanationLayout.debug(
            new PreviewDebugLayout.Rect(0, 0, 180, 120)));
        field("captionBounds").set(screen, new PreviewDebugLayout.Rect(0, 80, 180, 40));
        field("captionPrevious").set(screen, new PreviewDebugLayout.Rect(3, 106, 22, 14));
        field("captionNext").set(screen, new PreviewDebugLayout.Rect(155, 106, 22, 14));
        field("captionFocused").setBoolean(screen, true);
        pointer(screen).beginPreviewDrag();
        int detailsId = field("BUTTON_DEBUG_DETAILS").getInt(null);
        screen.actionPerformed(new GuiButton(detailsId, 0, 0, "Details"));
        assertNull(screen.lastPreviewLayout);
        assertNull(field("explanationLayout").get(screen));
        assertNull(field("captionBounds").get(screen));
        assertNull(field("captionPrevious").get(screen));
        assertNull(field("captionNext").get(screen));
        assertFalse(field("captionFocused").getBoolean(screen));
        assertFalse(pointer(screen).isPreviewDragging());
        assertEquals(1, field("debugTab").getInt(screen));
    }

    @Test
    void standaloneCaptionsKeepEveryLineReachableOutsideTheModel() throws Exception {
        for (int[] size : new int[][] { { 320, 240 }, { 640, 360 }, { 960, 540 } }) {
            for (int lineCount : new int[] { 1, 20, 100 }) {
                PonderDebugScreen screen = standaloneScreen(size[0], size[1]);
                PreviewCaptionPaging paging = (PreviewCaptionPaging) field("captionPaging").get(screen);
                Object caption = new Object();
                layoutStandaloneCaption(screen, caption, lineCount);
                int nextLine = 0;
                int firstHeight = -1;
                for (int page = 0; page < paging.pageCount(); page++) {
                    layoutStandaloneCaption(screen, caption, lineCount);
                    PreviewDebugLayout.Rect bounds = (PreviewDebugLayout.Rect) field("captionBounds").get(screen);
                    assertNotNull(bounds);
                    PonderDebugScreen.PreviewLayout viewport = screen.lastPreviewLayout;
                    assertOutsideModel(screen, bounds);
                    assertTrue(bounds.bottom() < screen.height - 30, "caption must not cover the debug toolbar");
                    assertEquals(nextLine, paging.firstLine());
                    assertTrue(paging.endLine() > paging.firstLine());
                    if (firstHeight == -1) {
                        firstHeight = bounds.height;
                    }
                    assertEquals(firstHeight, bounds.height);
                    assertTrue(paging.height() <= bounds.height);
                    PreviewDebugLayout.Rect previous =
                        (PreviewDebugLayout.Rect) field("captionPrevious").get(screen);
                    PreviewDebugLayout.Rect next =
                        (PreviewDebugLayout.Rect) field("captionNext").get(screen);
                    if (paging.isPaged()) {
                        assertNotNull(previous);
                        assertNotNull(next);
                        assertTrue(bounds.contains(previous.x, previous.y));
                        assertTrue(bounds.contains(next.right() - 1, next.bottom() - 1));
                        int textBottom = bounds.y + 6
                            + (paging.endLine() - paging.firstLine()) * PreviewCaptionPaging.LINE_HEIGHT;
                        assertTrue(textBottom <= previous.y);
                    } else {
                        assertNull(previous);
                        assertNull(next);
                    }
                    nextLine = paging.endLine();
                    paging.turn(1);
                }
                assertEquals(lineCount, nextLine);
                assertFalse(paging.turn(1));
            }
        }
    }

    @Test
    void standaloneCaptionControlsPauseAndOwnTheirInput() throws Exception {
        PonderDebugScreen screen = standaloneScreen(320, 240);
        Object caption = new Object();
        layoutStandaloneCaption(screen, caption, 20);
        PreviewCaptionPaging paging = (PreviewCaptionPaging) field("captionPaging").get(screen);
        PreviewDebugLayout.Rect next = (PreviewDebugLayout.Rect) field("captionNext").get(screen);
        assertNotNull(next);
        field("playbackTick").setInt(screen, 37);
        pointer(screen).beginPreviewDrag();
        screen.playing = true;

        screen.mouseClicked(next.x + 5, next.y + 5, 0);

        assertEquals(1, paging.page());
        assertFalse(screen.playing);
        assertTrue(field("captionFocused").getBoolean(screen));
        assertFalse(pointer(screen).isPreviewDragging());
        layoutStandaloneCaption(screen, caption, 20);
        assertEquals(1, paging.page(), "redrawing the same caption must retain the page");
        pointer(screen).beginTimelineDrag();
        screen.playing = true;
        screen.keyTyped('\0', Keyboard.KEY_PRIOR);
        assertEquals(0, paging.page());
        assertFalse(screen.playing);
        assertFalse(pointer(screen).isTimelineDragging());
        assertEquals(37, screen.getPlaybackTickValue());

        layoutStandaloneCaption(screen, new Object(), 1);
        assertEquals(0, paging.page());
        assertNull(field("captionPrevious").get(screen));
        assertNull(field("captionNext").get(screen));
        PreviewDebugLayout.Rect bounds = (PreviewDebugLayout.Rect) field("captionBounds").get(screen);
        screen.playing = true;
        screen.mouseClicked(bounds.x + 8, bounds.y + 8, 0);
        assertFalse(screen.playing);
    }

    @Test
    void standaloneCaptionSizesAndResizePreserveBoundsAndReadingPosition() throws Exception {
        PonderDebugScreen screen = standaloneScreen(320, 240);
        Object caption = new Object();
        PreviewCaptionPaging paging = (PreviewCaptionPaging) field("captionPaging").get(screen);
        for (int lineCount : new int[] { 1, 20, 100 }) {
            layoutStandaloneCaption(screen, caption, lineCount);
            PreviewDebugLayout.Rect bounds = (PreviewDebugLayout.Rect) field("captionBounds").get(screen);
            assertOutsideModel(screen, bounds);
        }
        paging.turn(3);
        int readingLine = paging.firstLine();
        screen.width = 960;
        screen.height = 540;
        PonderDebugScreen resized = standaloneScreen(960, 540);
        screen.lastPreviewLayout = resized.lastPreviewLayout;
        field("explanationLayout").set(screen, field("explanationLayout").get(resized));
        layoutStandaloneCaption(screen, caption, 100);
        assertTrue(paging.firstLine() <= readingLine);
        assertTrue(paging.endLine() > readingLine);
    }

    private static PonderDebugScreen standaloneScreen(int width, int height) throws Exception {
        PonderDebugScreen screen = new PonderDebugScreen(null, 0);
        screen.width = width;
        screen.height = height;
        PreviewDebugLayout.Rect preview = new PreviewDebugLayout(width, height).preview;
        Constructor<PonderDebugScreen.PreviewLayout> constructor =
            PonderDebugScreen.PreviewLayout.class.getDeclaredConstructor(int.class, int.class, int.class, int.class);
        constructor.setAccessible(true);
        PreviewExplanationLayout layout = PreviewExplanationLayout.debug(new PreviewDebugLayout.Rect(
            preview.x + 7, preview.y + 19, preview.width - 14, preview.height - 40));
        field("explanationLayout").set(screen, layout);
        screen.lastPreviewLayout = constructor.newInstance(layout.model.x, layout.model.y,
            layout.model.width, layout.model.height);
        return screen;
    }

    private static void layoutStandaloneCaption(PonderDebugScreen screen, Object caption, int lineCount)
        throws Exception {
        Method layout = PonderDebugScreen.class.getDeclaredMethod("layoutExplanation",
            Object.class, int.class, int.class);
        layout.setAccessible(true);
        layout.invoke(screen, caption, lineCount, 0);
    }

    private static void assertOutsideModel(PonderDebugScreen screen, PreviewDebugLayout.Rect bounds) throws Exception {
        PreviewExplanationLayout layout = (PreviewExplanationLayout) field("explanationLayout").get(screen);
        assertEquals(layout.explanation.x, bounds.x);
        assertEquals(layout.explanation.y, bounds.y);
        assertEquals(layout.explanation.width, bounds.width);
        assertEquals(layout.explanation.height, bounds.height);
        assertTrue(bounds.x >= layout.model.right() || bounds.right() <= layout.model.x
            || bounds.y >= layout.model.bottom() || bounds.bottom() <= layout.model.y);
    }

    private static PonderDebugScreen captionScreen() throws Exception {
        PonderDebugScreen screen = new PonderDebugScreen(null, 0);
        field("captionBounds").set(screen, new PreviewDebugLayout.Rect(20, 20, 120, 58));
        return screen;
    }

    private static PreviewPointerInput pointer(PonderDebugScreen screen) throws Exception {
        return (PreviewPointerInput) field("pointerInput").get(screen);
    }

    private static Field field(String name) throws Exception {
        Field field = PonderDebugScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
