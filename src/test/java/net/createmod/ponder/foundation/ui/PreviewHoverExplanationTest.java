package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.Locale;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

class PreviewHoverExplanationTest {

    private static Field localeField;
    private static Object previousLocale;

    @BeforeAll
    static void initializeClientFixtures() throws Exception {
        Bootstrap.register();
        localeField = I18n.class.getDeclaredField("i18nLocale");
        localeField.setAccessible(true);
        previousLocale = localeField.get(null);
        localeField.set(null, new Locale());
    }

    @AfterAll
    static void restoreLocale() throws Exception {
        localeField.set(null, previousLocale);
    }

    @Test
    void longModelHintStaysInTheDockAndEveryPageRemainsReadableAfterLeavingItsSource() throws Exception {
        PonderDebugScreen screen = screen(true);
        PonderScene scene = scene();
        String note = String.join("\n", Collections.nCopies(4, PreviewTextLayoutTest.longChinese()));
        ((PreviewModelMode<?>) field("modelMode").get(screen)).fail(note);
        field("modelControls").set(screen, new PreviewModelControls(28, 212));
        PonderDebugScreen.PreviewLayout viewport = screen.lastPreviewLayout;
        float scale = scale(screen, scene);

        screen.updateExplanationHover(30, 214);
        prepare(screen, scene);
        assertEquals(note, field("captionText").get(screen));
        PreviewDebugLayout.Rect dock = (PreviewDebugLayout.Rect) field("captionBounds").get(screen);
        PreviewDebugLayout.Rect reserved = ((PreviewExplanationLayout) field("explanationLayout").get(screen)).explanation;
        assertTrue(dock.x >= reserved.x && dock.right() <= reserved.right());
        assertTrue(dock.y >= reserved.y && dock.bottom() <= reserved.bottom());
        assertEquals(0, PreviewCalloutLayout.intersectionArea(dock, new PreviewDebugLayout.Rect(
            viewport.originX, viewport.originY, viewport.width, viewport.height)));
        PreviewCaptionPaging paging = (PreviewCaptionPaging) field("captionPaging").get(screen);
        assertTrue(((List<?>) field("captionLines").get(screen)).size() >= 12,
            "the fixture must overflow the taller compact explanation pane");
        assertTrue(paging.pageCount() > 1);
        screen.updateExplanationHover(160, 200);
        assertEquals(note, field("explanationHoverText").get(screen), "the gap must not dismiss the note");
        field("explanationHoverTicks").setInt(screen, 0);
        screen.updateExplanationHover(dock.x + 5, dock.y + 5);
        assertEquals(note, field("explanationHoverText").get(screen));
        screen.playing = true;
        ((PreviewPointerInput) field("pointerInput").get(screen)).beginPreviewDrag();
        screen.mouseClicked(dock.x + 5, dock.y + 5, 0);
        assertFalse(screen.playing);
        assertFalse(((PreviewPointerInput) field("pointerInput").get(screen)).isPreviewDragging());

        int nextLine = 0;
        int lineCount = ((List<?>) field("captionLines").get(screen)).size();
        for (int page = 0; page < paging.pageCount(); page++) {
            field("explanationHoverTicks").setInt(screen, 0);
            screen.updateExplanationHover(0, 0);
            prepare(screen, scene);
            assertEquals(note, field("captionText").get(screen));
            assertEquals(page, paging.page(), "reading must not reset the page on redraw");
            assertEquals(nextLine, paging.firstLine());
            nextLine = paging.endLine();
            assertSame(viewport, screen.lastPreviewLayout);
            assertEquals(scale, scale(screen, scene));
            screen.keyTyped('\0', Keyboard.KEY_NEXT);
        }
        assertEquals(lineCount, nextLine);
    }

    @Test
    void debugComponentOperationAndButtonHintsUseTheSameFullTextPipeline() throws Exception {
        PonderDebugScreen screen = screen(false);
        PonderScene scene = scene();
        field("compiledScenes").set(screen, Collections.singletonList(scene));
        ResourceLocation component = new ResourceLocation("hover_test",
            "long_component_identifier_for_external_explanation_pagination");
        field("componentIds").set(screen, Collections.singletonList(component));
        PreviewDebugLayout layout = new PreviewDebugLayout(screen.width, screen.height);
        screen.updateExplanationHover(layout.componentRows.x + 1, layout.componentRows.y + 1);
        prepare(screen, scene);
        assertEquals(component.toString(), field("captionText").get(screen));

        field("debugTab").setInt(screen, 2);
        scene.recordOperation(PreviewTextLayoutTest.longChinese());
        screen.updateExplanationHover(layout.operationRows.x + 1, layout.operationRows.y + 1);
        prepare(screen, scene);
        assertEquals("T+0 " + PreviewTextLayoutTest.longChinese(), field("captionText").get(screen));
        assertTrue(((PreviewCaptionPaging) field("captionPaging").get(screen)).pageCount() > 1);

        GuiButton button = new GuiButton(PonderDebugScreen.BUTTON_RELOAD, 12, 210, 20, 20, "R");
        screen.buttonList.add(button);
        assertFalse(button.isMouseOver(), "the button has not been drawn in this frame");
        screen.updateExplanationHover(14, 212);
        prepare(screen, scene);
        assertEquals("Reload", field("captionText").get(screen));
    }

    @Test
    void compactDetailsAndTimelineReserveTheDockWithoutStaleListHitTargets() throws Exception {
        PonderDebugScreen screen = screen(false);
        PonderScene scene = scene();
        for (int i = 0; i < 50; i++) {
            scene.recordOperation("operation " + i);
        }
        field("compiledScenes").set(screen, Collections.singletonList(scene));
        PreviewDebugLayout layout = new PreviewDebugLayout(screen.width, screen.height);
        PreviewExplanationLayout dock = (PreviewExplanationLayout) field("explanationLayout").get(screen);
        Method textRows = method("getDebugTextRows", PreviewDebugLayout.Rect.class);
        Method operationAt = method("getOperationIndexAt", int.class, int.class);
        Method overSummary = method("isMouseOverSummary", int.class, int.class);
        PreviewDebugLayout.Rect rows = (PreviewDebugLayout.Rect) textRows.invoke(screen, layout.operationRows);
        assertTrue(rows.lines() > 0);
        assertTrue(rows.bottom() <= dock.explanation.y - 8);
        field("debugTab").setInt(screen, 2);
        assertEquals(0, operationAt.invoke(screen, rows.x + 1, rows.y + 1));
        assertEquals(-1, operationAt.invoke(screen, dock.explanation.x + 1, dock.explanation.y + 1));
        int maximumScroll = (Integer) method("getMaxOperationScroll").invoke(screen);
        field("operationScroll").setInt(screen, maximumScroll);
        assertEquals(49, operationAt.invoke(screen, rows.x + 1, rows.bottom() - 1));
        field("debugTab").setInt(screen, 1);
        assertTrue((Boolean) overSummary.invoke(screen, rows.x + 1, rows.y + 1));
        assertFalse((Boolean) overSummary.invoke(screen, dock.explanation.x + 1, dock.explanation.y + 1));
    }

    @Test
    void unattendedHintsExpireButThePopupSuppressesThemImmediately() throws Exception {
        PonderDebugScreen screen = screen(false);
        PonderScene scene = scene();
        scene.createOverlayEvent(100).text("SCENE EXPLANATION");
        screen.buttonList.add(new GuiButton(PonderDebugScreen.BUTTON_RELOAD, 12, 210, 20, 20, "R"));
        screen.updateExplanationHover(14, 212);
        prepare(screen, scene);
        assertEquals("Reload", field("captionText").get(screen));
        for (int i = 0; i < 40; i++) {
            screen.updateScreen();
        }
        screen.updateExplanationHover(0, 0);
        prepare(screen, scene);
        assertEquals("SCENE EXPLANATION", field("captionText").get(screen));
        screen.updateExplanationHover(14, 212);
        field("showcaseGroupSelectorOpen").setBoolean(screen, true);
        screen.updateExplanationHover(14, 212);
        assertNull(field("explanationHoverText").get(screen));
        prepare(screen, scene);
        assertEquals("SCENE EXPLANATION", field("captionText").get(screen));
    }

    @Test
    void changingDebugTabsDropsRetainedHoverTextBeforeAnotherFrame() throws Exception {
        PonderDebugScreen screen = screen(false);
        screen.buttonList.add(new GuiButton(PonderDebugScreen.BUTTON_RELOAD, 12, 210, 20, 20, "R"));
        screen.updateExplanationHover(14, 212);
        assertNotNull(field("explanationHoverText").get(screen));
        screen.actionPerformed(new GuiButton(field("BUTTON_DEBUG_DETAILS").getInt(null), 0, 0, "Details"));
        assertNull(field("explanationHoverText").get(screen));
        assertNull(field("explanationLayout").get(screen));
        assertNull(screen.lastPreviewLayout);
    }

    @Test
    void resumingPlaybackReturnsFromRetainedHoverToTheSceneExplanation() throws Exception {
        for (boolean showcase : new boolean[] { false, true }) {
            for (boolean keyboard : new boolean[] { false, true }) {
                PonderDebugScreen screen = screen(showcase);
                PonderScene scene = scene();
                scene.createOverlayEvent(100).text("SCENE EXPLANATION");
                field("explanationHoverText").set(screen, "RETAINED HINT");
                prepare(screen, scene);
                PreviewDebugLayout.Rect dock = (PreviewDebugLayout.Rect) field("captionBounds").get(screen);
                screen.mouseClicked(dock.x + 5, dock.y + 5, 0);
                assertFalse(screen.playing);
                assertTrue(field("captionFocused").getBoolean(screen));

                if (keyboard) {
                    screen.keyTyped('\0', Keyboard.KEY_SPACE);
                } else {
                    screen.actionPerformed(new GuiButton(PonderDebugScreen.BUTTON_PLAY_PAUSE, 0, 0, "Play"));
                }

                assertTrue(screen.playing);
                assertFalse(field("captionFocused").getBoolean(screen));
                assertNull(field("explanationHoverText").get(screen));
                screen.updateExplanationHover(0, 0);
                prepare(screen, scene);
                assertEquals("SCENE EXPLANATION", field("captionText").get(screen));
            }
        }
    }

    @Test
    void guiImagesAndHighlightsDoNotRequireAVisibleModelOnDetailsOrTimelineTabs() throws Exception {
        PonderDebugScreen screen = screen(false);
        PonderScene scene = scene();
        scene.createOverlayEvent(100).guiTexture(new ResourceLocation("hover_test", "gui"),
            0, 0, 100, 50, 100, 50, 100, 50, 0, 0, true).overlayId("gui");
        scene.createOverlayEvent(100).guiHighlight("gui", 4, 4, 8, 8);
        for (int tab : new int[] { 1, 2 }) {
            field("debugTab").setInt(screen, tab);
            screen.lastPreviewLayout = null;
            prepare(screen, scene);
            List<?> images = (List<?>) method("getActiveGuiOverlayPlacements", PonderScene.class,
                PonderDebugScreen.PreviewLayout.class, float.class).invoke(screen, scene, null, 20F);
            assertEquals(1, images.size());
            List<?> highlights = (List<?>) method("getActiveGuiHighlightPlacements", PonderScene.class,
                PonderDebugScreen.PreviewLayout.class, float.class, List.class).invoke(screen, scene, null, 20F, images);
            assertEquals(1, highlights.size());
            assertNull(screen.lastPreviewLayout);
        }
    }

    @Test
    void resizeInvalidatesPlaybackHeaderAndNextSceneHitAreasBeforeTheNextFrame() throws Exception {
        ResizeViewer screen = new ResizeViewer();
        assertTrue(screen.synchronizeIndex());
        screen.setPlaybackBarBounds(20, 160, 100, 3);
        screen.setShowcaseHeaderIconBounds(28, 33, 28);
        screen.setNextUpCardBounds(190, 30, 90, 28);
        assertTrue(screen.isMouseOverPlaybackBar(25, 160));
        assertTrue(screen.isMouseOverShowcaseHeaderIcon(30, 35));
        assertTrue(screen.isMouseOverNextUpCard(200, 40));
        ((GuiScreen) screen).width = 640;
        ((GuiScreen) screen).height = 360;

        screen.initGui();

        assertEquals(640, screen.width);
        assertEquals(360, screen.height);
        assertFalse(screen.isMouseOverPlaybackBar(25, 160));
        assertFalse(screen.isMouseOverShowcaseHeaderIcon(30, 35));
        assertFalse(screen.isMouseOverNextUpCard(200, 40));
        for (String name : new String[] { "lastPlaybackBarWidth", "lastPlaybackBarHeight",
            "lastShowcaseHeaderIconSize", "lastNextUpCardWidth", "lastNextUpCardHeight" }) {
            assertEquals(0, field(name).getInt(screen), name);
        }
    }

    private static PonderDebugScreen screen(boolean showcase) throws Exception {
        PonderDebugScreen screen = showcase ? PonderDebugScreen.showcase(null, 0) : new PonderDebugScreen(null, 0);
        screen.width = 320;
        screen.height = 240;
        screen.fontRenderer = new PreviewTextLayoutTest.MetricsFont();
        PreviewExplanationLayout layout = showcase ? PreviewExplanationLayout.showcase(320, 240)
            : (PreviewExplanationLayout) method("getDebugExplanationLayout").invoke(screen);
        field("explanationLayout").set(screen, layout);
        Constructor<PonderDebugScreen.PreviewLayout> constructor = PonderDebugScreen.PreviewLayout.class
            .getDeclaredConstructor(int.class, int.class, int.class, int.class);
        constructor.setAccessible(true);
        screen.lastPreviewLayout = constructor.newInstance(layout.model.x, layout.model.y,
            layout.model.width, layout.model.height);
        return screen;
    }

    private static PonderScene scene() {
        PonderScene scene = new PonderScene(new PonderStoryBoardEntry((builder, util) -> {},
            "hover_test", new ResourceLocation("hover_test", "scene"), new ResourceLocation("hover_test", "component")),
            new PonderLocalization());
        scene.setBasePlateSize(5);
        return scene;
    }

    private static void prepare(PonderDebugScreen screen, PonderScene scene) throws Exception {
        method("prepareExplanation", PonderScene.class, float.class).invoke(screen, scene, 20F);
    }

    private static float scale(PonderDebugScreen screen, PonderScene scene) throws Exception {
        return (Float) method("computePreviewScale", PonderScene.class, PonderScenePreview.PreviewBounds.class,
            PonderDebugScreen.PreviewLayout.class).invoke(screen, scene, PonderScenePreview.computeBounds(scene),
                screen.lastPreviewLayout);
    }

    private static Method method(String name, Class<?>... types) throws Exception {
        Method method = PonderDebugScreen.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    private static Field field(String name) throws Exception {
        Field field = PonderDebugScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class ResizeViewer extends PonderUI {
        ResizeViewer() {
            super(null, 0, null);
            width = 320;
            height = 240;
        }

        @Override
        protected long readIndexGeneration() {
            return 0;
        }

        @Override
        protected List<ResourceLocation> readIndexComponents() {
            return Collections.emptyList();
        }

        @Override
        protected List<PonderScene> compileIndexScenes(ResourceLocation component) {
            return Collections.emptyList();
        }

        @Override
        protected ItemStack createComponentStack(ResourceLocation component) {
            return ItemStack.EMPTY;
        }
    }
}
