package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderTag;
import net.minecraft.client.gui.FontRenderer;
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

/** Exercises production popup geometry and callbacks without a Minecraft or GL context. */
class PreviewGroupInputTest {

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
        if (localeField != null) {
            localeField.set(null, previousLocale);
        }
    }

    @Test
    void everyComponentIsReachableWithMatchingBoundedHitTargetsAtMinimumSize() throws Exception {
        for (int count : new int[] { 53, 100 }) {
            Viewer screen = open(count);
            PreviewGroupLayout layout = layout(screen);
            Set<ResourceLocation> reached = new LinkedHashSet<>();
            int popupHeight = layout.bounds.height;
            for (int page = 0; page < layout.pageCount(); page++) {
                prepare(screen);
                assertEquals(page, layout.page());
                assertEquals(popupHeight, layout.bounds.height);
                assertTrue(layout.bounds.x >= 0 && layout.bounds.right() <= screen.width);
                assertTrue(layout.bounds.y >= 76 && layout.bounds.bottom() <= screen.height - 90);
                List<PonderDebugScreen.ShowcaseGroupIcon> icons = icons(screen);
                assertEquals(layout.endIndex() - layout.firstIndex(), icons.size());
                for (int local = 0; local < icons.size(); local++) {
                    PonderDebugScreen.ShowcaseGroupIcon icon = icons.get(local);
                    assertEquals(screen.components.get(layout.firstIndex() + local), icon.componentId);
                    assertTrue(reached.add(icon.componentId), "each component must appear exactly once");
                    assertTrue(layout.bounds.contains(icon.x, icon.y));
                    assertTrue(layout.bounds.contains(icon.x + icon.width - 1, icon.y + icon.height - 1));
                    assertTrue(icon.y + icon.height <= layout.previous.y);
                    assertSame(icon, screen.getShowcaseGroupIconAt(icon.x, icon.y));
                    assertSame(icon, screen.getShowcaseGroupIconAt(icon.x + icon.width - 1,
                        icon.y + icon.height - 1));
                    assertNull(screen.getShowcaseGroupIconAt(icon.x + icon.width, icon.y));
                    assertNull(screen.getShowcaseGroupIconAt(icon.x, icon.y + icon.height));
                }
                assertTrue(layout.bounds.contains(layout.previous.x, layout.previous.y));
                assertTrue(layout.bounds.contains(layout.next.right() - 1, layout.next.bottom() - 1));
                PonderDebugScreen.ShowcaseGroupIcon first = icons.get(0);
                screen.mouseClicked(layout.next.x + 5, layout.next.y + 5, 0);
                if (page + 1 < layout.pageCount()) {
                    assertNotEquals(first.componentId,
                        screen.getShowcaseGroupIconAt(first.x, first.y).componentId,
                        "paging must replace hit targets before another frame is drawn");
                }
            }
            assertEquals(new LinkedHashSet<>(screen.components), reached);
            assertEquals(layout.pageCount() - 1, layout.page());
            List<PonderDebugScreen.ShowcaseGroupIcon> lastPage = icons(screen);
            PonderDebugScreen.ShowcaseGroupIcon last = lastPage.get(lastPage.size() - 1);
            assertNull(screen.getShowcaseGroupIconAt(last.x + last.width + 4, last.y),
                "unused slots on the last page must not select stale components");
        }
    }

    @Test
    void selectingAnIconOnTheLastPageSelectsThatComponent() throws Exception {
        for (int count : new int[] { 53, 100 }) {
            Viewer screen = open(count);
            screen.keyTyped('\0', Keyboard.KEY_END);
            PonderDebugScreen.ShowcaseGroupIcon icon = icons(screen).get(0);
            screen.mouseClicked(icon.x + 2, icon.y + 2, 0);
            assertEquals(icon.componentId, screen.getSelectedComponentId());
            assertFalse(field("showcaseGroupSelectorOpen").getBoolean(screen));
            assertTrue(icons(screen).isEmpty());
            assertNull(layout(screen).bounds);
        }
    }

    @Test
    void popupClicksTakePriorityOverCaptionTimelineModelAndGuiButtons() throws Exception {
        Viewer screen = open(53);
        PreviewGroupLayout layout = layout(screen);
        PreviewDebugLayout.Rect next = layout.next;
        field("captionBounds").set(screen, new PreviewDebugLayout.Rect(0, 0, 320, 240));
        PreviewCaptionPaging caption = (PreviewCaptionPaging) field("captionPaging").get(screen);
        caption.configure(new Object(), 30, 58);
        field("captionFocused").setBoolean(screen, true);
        field("modelControls").set(screen, new PreviewModelControls(next.x, next.y));
        PreviewModelMode<Object> model = model(screen);
        model.chooseAppearance(Object::new, value -> true, value -> "test model");
        screen.setPlaybackBarBounds(next.x, next.y, next.width, next.height);
        screen.buttonList.add(new GuiButton(PonderDebugScreen.BUTTON_PLAY_PAUSE, next.x, next.y,
            next.width, next.height, "Pause"));
        field("playbackTick").setInt(screen, 37);
        screen.playing = true;
        pointer(screen).beginTimelineDrag();

        screen.mouseClicked(next.x + 4, next.y + 4, 0);

        assertEquals(1, layout.page());
        assertEquals(37, screen.getPlaybackTickValue());
        assertTrue(screen.playing);
        assertTrue(model.isAppearance());
        assertEquals(0, caption.page());
        assertFalse(field("captionFocused").getBoolean(screen));
        assertFalse(pointer(screen).isTimelineDragging());
        assertEquals(0, screen.buttonActions);

        pointer(screen).beginTimelineDrag();
        screen.mouseClickMove(next.x + 10, next.y, 0, 100L);
        assertFalse(pointer(screen).isTimelineDragging());
        assertEquals(37, screen.getPlaybackTickValue());

        screen.mouseClicked(12, 220, 0);
        assertFalse(field("showcaseGroupSelectorOpen").getBoolean(screen));
        assertFalse(field("captionFocused").getBoolean(screen),
            "the click dismissing the popup must not focus the caption underneath");
        assertEquals(37, screen.getPlaybackTickValue());
        assertTrue(screen.playing);
        assertEquals(0, screen.buttonActions);
    }

    @Test
    void wheelPagesOnlyInsideThePopupAndConsumesOutsideWheelEvents() throws Exception {
        Viewer screen = open(100);
        PreviewGroupLayout layout = layout(screen);
        field("playbackTick").setInt(screen, 37);
        field("previewZoom").setFloat(screen, 1.2F);
        pointer(screen).beginPreviewDrag();
        assertTrue(wheel(screen, -120, layout.bounds.x + 2, layout.bounds.y + 2));
        assertEquals(1, layout.page());
        assertFalse(pointer(screen).isPreviewDragging());
        assertTrue(wheel(screen, -120, 10, 220));
        assertEquals(1, layout.page());
        assertTrue(wheel(screen, 120, layout.bounds.x + 2, layout.bounds.y + 2));
        assertEquals(0, layout.page());
        assertTrue(wheel(screen, 120, layout.bounds.x + 2, layout.bounds.y + 2));
        assertEquals(0, layout.page());
        assertFalse(wheel(screen, 0, layout.bounds.x + 2, layout.bounds.y + 2),
            "non-wheel events must still dispatch clicks and releases");
        assertEquals(37, screen.getPlaybackTickValue());
        assertEquals(1.2F, field("previewZoom").getFloat(screen));
    }

    @Test
    void popupKeyboardOwnsPagingAndEscapeWithoutChangingUnderlyingState() throws Exception {
        Viewer screen = open(100);
        PreviewGroupLayout layout = layout(screen);
        field("playbackTick").setInt(screen, 37);
        field("previewYaw").setFloat(screen, 12F);
        field("previewPitch").setFloat(screen, 35F);
        field("previewZoom").setFloat(screen, 0.62F);
        field("captionBounds").set(screen, new PreviewDebugLayout.Rect(20, 20, 120, 58));
        field("captionFocused").setBoolean(screen, true);
        screen.playing = true;
        pointer(screen).beginTimelineDrag();
        for (int key : new int[] { Keyboard.KEY_RIGHT, Keyboard.KEY_DOWN, Keyboard.KEY_NEXT }) {
            screen.keyTyped('\0', key);
        }
        assertEquals(3, layout.page());
        for (int key : new int[] { Keyboard.KEY_LEFT, Keyboard.KEY_UP, Keyboard.KEY_PRIOR }) {
            screen.keyTyped('\0', key);
        }
        assertEquals(0, layout.page());
        screen.keyTyped('\0', Keyboard.KEY_END);
        assertEquals(layout.pageCount() - 1, layout.page());
        screen.keyTyped('\0', Keyboard.KEY_HOME);
        assertEquals(0, layout.page());
        for (int key : new int[] { Keyboard.KEY_SPACE, Keyboard.KEY_R, Keyboard.KEY_C, Keyboard.KEY_D }) {
            screen.keyTyped('\0', key);
        }
        assertEquals(37, screen.getPlaybackTickValue());
        assertTrue(screen.playing);
        assertEquals(12F, field("previewYaw").getFloat(screen));
        assertEquals(35F, field("previewPitch").getFloat(screen));
        assertEquals(0.62F, field("previewZoom").getFloat(screen));
        assertFalse(pointer(screen).isTimelineDragging());
        screen.keyTyped('\0', Keyboard.KEY_ESCAPE);
        assertFalse(field("showcaseGroupSelectorOpen").getBoolean(screen));
        assertNull(layout.bounds);
        assertTrue(icons(screen).isEmpty());
    }

    @Test
    void resizingRetainsTheVisibleComponentAndShrinkingTheGroupClampsThePage() throws Exception {
        Viewer screen = open(100);
        PreviewGroupLayout layout = layout(screen);
        for (int i = 0; i < 8; i++) {
            screen.keyTyped('\0', Keyboard.KEY_NEXT);
        }
        int first = layout.firstIndex();
        screen.width = 640;
        screen.height = 360;
        screen.setPlaybackBarBounds(160, 288, 320, 3);
        prepare(screen);
        assertTrue(layout.firstIndex() <= first && layout.endIndex() > first);
        assertTrue(layout.bounds.right() <= screen.width && layout.bounds.bottom() <= screen.height - 90);
        screen.components = new ArrayList<>(screen.components.subList(0, 7));
        prepare(screen);
        assertEquals(0, layout.page());
        assertEquals(7, icons(screen).size());
        assertNull(layout.previous);
        assertNull(layout.next);
        screen.components = Collections.singletonList(screen.components.get(0));
        assertFalse(prepare(screen));
        assertFalse(field("showcaseGroupSelectorOpen").getBoolean(screen));
        assertNull(layout.bounds);
        assertTrue(icons(screen).isEmpty());
    }

    @Test
    void initGuiInvalidatesOldHitTargetsBeforeTheNextFrameButRetainsPagination() throws Exception {
        for (boolean clickBeforeNextFrame : new boolean[] { false, true }) {
            Viewer screen = open(100);
            assertTrue(screen.synchronizeIndex());
            field("showcaseGroupSelectorOpen").setBoolean(screen, true);
            assertTrue(prepare(screen));
            for (int i = 0; i < 8; i++) {
                screen.keyTyped('\0', Keyboard.KEY_NEXT);
            }
            PreviewGroupLayout layout = layout(screen);
            int previousPage = layout.page();
            int previousFirst = layout.firstIndex();
            PonderDebugScreen.ShowcaseGroupIcon oldIcon = icons(screen).get(0);
            ResourceLocation selected = screen.getSelectedComponentId();
            ((GuiScreen) screen).width = 640;
            ((GuiScreen) screen).height = 360;

            screen.initGui();

            assertEquals(640, screen.width);
            assertEquals(360, screen.height);
            assertTrue(field("showcaseGroupSelectorOpen").getBoolean(screen));
            assertEquals(previousPage, layout.page());
            assertEquals(previousFirst, layout.firstIndex());
            assertNull(layout.bounds);
            assertNull(layout.previous);
            assertNull(layout.next);
            assertNull(field("lastShowcaseGroupState").get(screen));
            assertTrue(icons(screen).isEmpty());
            assertNull(screen.getShowcaseGroupIconAt(oldIcon.x, oldIcon.y));
            if (clickBeforeNextFrame) {
                screen.mouseClicked(oldIcon.x + 1, oldIcon.y + 1, 0);
                assertEquals(selected, screen.getSelectedComponentId());
                assertFalse(field("showcaseGroupSelectorOpen").getBoolean(screen));
                assertEquals(0, screen.buttonActions);
            } else {
                screen.setPlaybackBarBounds(160, 288, 320, 3);
                assertTrue(prepare(screen));
                assertTrue(layout.firstIndex() <= previousFirst && layout.endIndex() > previousFirst);
                assertFalse(icons(screen).isEmpty());
            }
        }
    }

    @Test
    void indexReloadDropsTheOldPopupPageAndHitTargets() throws Exception {
        Viewer screen = open(100);
        assertTrue(screen.synchronizeIndex());
        field("showcaseGroupSelectorOpen").setBoolean(screen, true);
        assertTrue(prepare(screen));
        screen.keyTyped('\0', Keyboard.KEY_END);
        assertTrue(layout(screen).page() > 0);
        assertEquals(layout(screen).pageCount() - 1, layout(screen).page());
        PonderDebugScreen.ShowcaseGroupIcon previous = icons(screen).get(0);
        screen.components = Collections.emptyList();
        screen.generation++;

        assertTrue(screen.synchronizeIndex());

        assertFalse(field("showcaseGroupSelectorOpen").getBoolean(screen));
        assertNull(layout(screen).bounds);
        assertEquals(0, layout(screen).page());
        assertTrue(icons(screen).isEmpty());
        assertNull(screen.getShowcaseGroupIconAt(previous.x, previous.y));
    }

    @Test
    void groupHoverUsesTheBoundedProductionTextPathForFormattedChinese() throws Exception {
        Viewer screen = open(53);
        screen.fontRenderer = new PreviewTextLayoutTest.MetricsFont();
        screen.tag = new PonderTag(new ResourceLocation("group_test", "long_title"), null, null, null) {
            @Override
            public String getTitle() {
                return "\u00a7a\u00a7l" + PreviewTextLayoutTest.longChinese();
            }
        };
        prepare(screen);
        PonderDebugScreen.ShowcaseGroupIcon icon = icons(screen).get(0);
        screen.drawShowcaseHoverHints(icon.x + 1, icon.y + 1);
        assertNotNull(screen.drawnText);
        assertTrue(screen.drawnText.startsWith(icon.componentId.toString() + "\u00a7r"));
        assertTrue(screen.drawnText.contains("\u00a7a\u00a7l"));
        assertTrue(screen.fontRenderer.getStringWidth(screen.drawnText) <= screen.width - 36);
        assertTrue(screen.drawnX >= 18);
        assertTrue(screen.drawnX + screen.fontRenderer.getStringWidth(screen.drawnText) <= screen.width - 18);
        String firstHint = screen.drawnText;
        PonderDebugScreen.ShowcaseGroupIcon next = icons(screen).get(1);
        screen.drawShowcaseHoverHints(next.x + 1, next.y + 1);
        assertTrue(screen.drawnText.startsWith(next.componentId.toString() + "\u00a7r"));
        assertNotEquals(firstHint, screen.drawnText, "the machine must remain identifiable under a long group name");
        screen.drawnText = null;
        screen.drawShowcaseHoverHints(10, 220);
        assertNull(screen.drawnText, "an open popup must not display the underlying controls' hints");
    }

    private static Viewer open(int count) throws Exception {
        Viewer screen = new Viewer();
        for (int i = 0; i < count; i++) {
            screen.components.add(new ResourceLocation("group_test", "machine_" + i));
        }
        field("componentIds").set(screen, screen.components);
        field("showcaseGroupSelectorOpen").setBoolean(screen, true);
        screen.setPlaybackBarBounds(34, 168, 252, 3);
        assertTrue(prepare(screen));
        return screen;
    }

    private static boolean prepare(Viewer screen) throws Exception {
        Constructor<PonderDebugScreen.ShowcaseGroupState> constructor =
            PonderDebugScreen.ShowcaseGroupState.class.getDeclaredConstructor(PonderTag.class, List.class);
        constructor.setAccessible(true);
        Method prepare = PonderDebugScreen.class.getDeclaredMethod("layoutShowcaseGroupPopup",
            PonderDebugScreen.ShowcaseGroupState.class, int.class, int.class, int.class);
        prepare.setAccessible(true);
        return (Boolean) prepare.invoke(screen, constructor.newInstance(screen.tag, screen.components),
            28, 76, screen.width - 124);
    }

    private static boolean wheel(Viewer screen, int wheel, int x, int y) throws Exception {
        Method route = PonderDebugScreen.class.getDeclaredMethod("handleShowcaseGroupWheel",
            int.class, int.class, int.class);
        route.setAccessible(true);
        return (Boolean) route.invoke(screen, wheel, x, y);
    }

    private static PreviewGroupLayout layout(Viewer screen) throws Exception {
        return (PreviewGroupLayout) field("groupLayout").get(screen);
    }

    @SuppressWarnings("unchecked")
    private static List<PonderDebugScreen.ShowcaseGroupIcon> icons(Viewer screen) throws Exception {
        return (List<PonderDebugScreen.ShowcaseGroupIcon>) field("lastShowcaseGroupIcons").get(screen);
    }

    @SuppressWarnings("unchecked")
    private static PreviewModelMode<Object> model(Viewer screen) throws Exception {
        return (PreviewModelMode<Object>) field("modelMode").get(screen);
    }

    private static PreviewPointerInput pointer(Viewer screen) throws Exception {
        return (PreviewPointerInput) field("pointerInput").get(screen);
    }

    private static Field field(String name) throws Exception {
        Field field = PonderDebugScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class Viewer extends PonderUI {
        private List<ResourceLocation> components = new ArrayList<>();
        private long generation;
        private PonderTag tag;
        private int buttonActions;
        private String drawnText;
        private int drawnX;

        Viewer() {
            super(null, 0, null);
            width = 320;
            height = 240;
        }

        @Override
        protected long readIndexGeneration() {
            return generation;
        }

        @Override
        protected List<ResourceLocation> readIndexComponents() {
            return components;
        }

        @Override
        protected List<PonderScene> compileIndexScenes(ResourceLocation component) {
            return Collections.emptyList();
        }

        @Override
        protected ItemStack createComponentStack(ResourceLocation component) {
            return ItemStack.EMPTY;
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            buttonActions++;
        }

        @Override
        public void drawString(FontRenderer font, String text, int x, int y, int color) {
            drawnText = text;
            drawnX = x;
        }
    }
}
