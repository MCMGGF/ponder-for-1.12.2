package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PonderBrowserLayoutTest {

    @BeforeAll
    static void registerBlocks() {
        Bootstrap.register();
    }

    @Test
    void minimumViewportLeavesRoomForBothColumnsAndTheSearchBox() throws Exception {
        PonderIndexScreen screen = index(320);
        assertEquals(136, screen.getTagPanelWidth());
        int rightWidth = screen.width - screen.getTagPanelWidth() - 48;
        assertEquals(136, rightWidth);
        assertTrue(rightWidth - 20 - 64 > 0);
        assertEquals(3, invoke(screen, "getComponentColumns"));
    }

    @Test
    void desktopKeepsTheExistingTagWidth() throws Exception {
        for (int width : new int[] { 500, 800, 1280, 1920 }) {
            PonderIndexScreen screen = index(width);
            assertEquals(220, screen.getTagPanelWidth());
            int columns = (Integer) invoke(screen, "getComponentColumns");
            int rightX = 32 + screen.getTagPanelWidth();
            assertTrue(rightX + 12 + columns * 32 - 8 <= width - 28);
        }
    }

    @Test
    void pointerRegionsFollowTheResponsivePanelsWithoutOverlapping() throws Exception {
        for (int width : new int[] { 320, 360, 480, 800 }) {
            PonderIndexScreen screen = index(width);
            int split = 16 + screen.getTagPanelWidth();
            assertTrue(hit(screen, "isMouseWithinTags", split - 1, 100));
            assertFalse(hit(screen, "isMouseWithinTags", split, 100));
            assertFalse(hit(screen, "isMouseWithinGrid", split, 100));
            assertTrue(hit(screen, "isMouseWithinGrid", split + 16, 100));
            assertTrue(hit(screen, "isMouseWithinGrid", width - 17, 100));
            assertFalse(hit(screen, "isMouseWithinGrid", width - 16, 100));
            assertFalse(hit(screen, "isMouseWithinGrid", split + 16, 200));
        }
    }

    @Test
    void scrollLimitUsesTheSameColumnsAfterResize() throws Exception {
        PonderIndexScreen screen = index(320);
        Field entries = PonderIndexScreen.class.getDeclaredField("filteredComponents");
        entries.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<PonderComponentEntry> components = (List<PonderComponentEntry>) entries.get(screen);
        components.addAll(Collections.nCopies(100, (PonderComponentEntry) null));
        assertEquals(32, invoke(screen, "getMaxComponentScroll"));
        screen.width = 800;
        assertEquals(16, invoke(screen, "getComponentColumns"));
        assertEquals(5, invoke(screen, "getMaxComponentScroll"));
    }

    @Test
    void longFormattedDescriptionsStayAboveTheComponentSection() {
        DescriptionPage screen = new DescriptionPage();
        for (int lineHeight : new int[] { 9, 12, 16 }) {
            screen.lineHeight(lineHeight);
            List<String> lines = screen.getDescriptionLines(
                "\u00a7a\u00a7l" + PreviewTextLayoutTest.longChinese()
                    + "\n" + PreviewTextLayoutTest.longChinese(), 288);
            assertFalse(lines.isEmpty());
            assertEquals(32 / lineHeight, lines.size());
            assertTrue(68 + lines.size() * lineHeight <= 100);
            for (String line : lines) {
                assertTrue(screen.textWidth(line) <= 256);
            }
        }
    }

    @Test
    void shortAndEmptyDescriptionsDoNotInventExtraRows() {
        DescriptionPage screen = new DescriptionPage();
        assertEquals(Collections.singletonList("machine"), screen.getDescriptionLines("machine", 288));
        assertEquals(Collections.singletonList(""), screen.getDescriptionLines("", 288));
        screen.lineHeight(40);
        assertTrue(screen.getDescriptionLines("machine", 288).isEmpty());
    }

    private static PonderIndexScreen index(int width) {
        PonderIndexScreen screen = new PonderIndexScreen();
        screen.width = width;
        screen.height = 240;
        return screen;
    }

    private static Object invoke(PonderIndexScreen screen, String name) throws Exception {
        Method method = PonderIndexScreen.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(screen);
    }

    private static boolean hit(PonderIndexScreen screen, String name, int x, int y) throws Exception {
        Method method = PonderIndexScreen.class.getDeclaredMethod(name, int.class, int.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(screen, x, y);
    }

    private static final class DescriptionPage extends PonderTagScreen {
        DescriptionPage() {
            super(new ResourceLocation("browser_test", "description"));
            fontRenderer = new PreviewTextLayoutTest.MetricsFont();
        }

        void lineHeight(int value) {
            fontRenderer.FONT_HEIGHT = value;
        }

        int textWidth(String value) {
            return fontRenderer.getStringWidth(value);
        }
    }
}
