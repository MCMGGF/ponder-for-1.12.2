package net.createmod.ponder.compat.ftbquests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;

import net.createmod.ponder.compat.ftbquests.FtbApiFixture.Panel;
import net.createmod.ponder.compat.ftbquests.FtbApiFixture.PanelViewQuest;
import net.createmod.ponder.compat.ftbquests.FtbApiFixture.Quest;
import net.createmod.ponder.compat.ftbquests.FtbQuestReflection.FooterState;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Bootstrap;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FtbQuestFooterLifecycleTest {

    private FtbApiFixture fixture;
    private FtbQuestReflection reflection;
    private FooterState state;
    private PanelViewQuest panel;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @BeforeEach
    void setUp() throws Exception {
        fixture = new FtbApiFixture();
        reflection = FtbApiFixture.reflection();
        panel = fixture.tree.viewQuestPanel;
        state = reflection.captureFooter(reflection.inspect(fixture.screen));
    }

    @Test
    void shortQuestReservesSpaceOnceAndFullyRestoresNativeGeometry() throws Exception {
        for (int frame = 0; frame < 30; frame++) {
            assertNotNull(reserve());
            assertEquals(77, panel.posY);
            assertEquals(86, panel.height);
            assertEquals(43, panel.panelContent.height);
        }
        reflection.restoreFooter(state);
        assertEquals(90, panel.posY);
        assertEquals(60, panel.height);
        assertEquals(43, panel.panelContent.height);
    }

    @Test
    void nativeRelayoutIsCapturedAndDoesNotAccumulateHeight() throws Exception {
        reserve();
        panel.posY = 30;
        panel.height = 180;
        panel.panelContent.height = 163;
        for (int frame = 0; frame < 20; frame++) {
            reserve();
            assertEquals(17, panel.posY);
            assertEquals(206, panel.height);
            assertEquals(163, panel.panelContent.height);
        }
        reflection.restoreFooter(state);
        assertEquals(30, panel.posY);
        assertEquals(180, panel.height);
        assertEquals(163, panel.panelContent.height);
    }

    @Test
    void nativePositionOnlyUpdateDoesNotAdoptTheAlreadyExpandedHeight() throws Exception {
        reserve();
        panel.posY = 80;
        reserve();
        assertEquals(67, panel.posY);
        assertEquals(86, panel.height);
        reflection.restoreFooter(state);
        assertEquals(80, panel.posY);
        assertEquals(60, panel.height);
    }

    @Test
    void fullHeightQuestShrinksOnlyItsContentAndRestoresOnDeparture() throws Exception {
        fixture.tree.rebuild(5, 230);
        state = reflection.captureFooter(reflection.inspect(fixture.screen));
        reserve();
        assertEquals(230, panel.height);
        assertEquals(187, panel.panelContent.height);
        FtbQuestsCompat compat = compatWithFooter();

        compat.onGuiOpen(new GuiOpenEvent(new GuiScreen() {}));

        assertEquals(5, panel.posY);
        assertEquals(230, panel.height);
        assertEquals(213, panel.panelContent.height);
        assertNull(field(compat, "footerState"));
    }

    @Test
    void disabledIntegrationReleasesItsReservation() throws Exception {
        reserve();
        FtbQuestsCompat compat = compatWithFooter();
        setField(compat, "links", QuestSceneLinks.parse(new java.io.StringReader("{\"enabled\":false}")));

        compat.onDrawPre(new GuiScreenEvent.DrawScreenEvent.Pre(fixture.screen, 0, 0, 0));

        assertEquals(90, panel.posY);
        assertEquals(60, panel.height);
        assertNull(field(compat, "footerState"));
    }

    @Test
    void replacingNativeChildrenNeverWritesOldGeometryBackToTheReusedParent() throws Exception {
        reserve();
        fixture.tree.rebuild(40, 140);
        Panel replacement = panel.panelContent;
        reflection.restoreFooter(state);
        assertEquals(40, panel.posY);
        assertEquals(140, panel.height);
        assertEquals(123, replacement.height);
        assertNull(reflection.reserveFooter(reflection.inspect(fixture.screen), state, 320, 240));
    }

    @Test
    void switchingQuestWithoutReplacingChildrenDoesNotRestoreStaleDimensions() throws Exception {
        reserve();
        panel.quest = new Quest(300, fixture.chapter);
        reflection.restoreFooter(state);
        assertEquals(77, panel.posY);
        assertEquals(86, panel.height);
        assertEquals(43, panel.panelContent.height);
    }

    @Test
    void laterNativeDimensionChangesAreNotOverwrittenDuringCleanup() throws Exception {
        reserve();
        panel.posY = 12;
        panel.height = 200;
        panel.panelContent.height = 170;
        reflection.restoreFooter(state);
        assertEquals(12, panel.posY);
        assertEquals(200, panel.height);
        assertEquals(170, panel.panelContent.height);
    }

    @Test
    void screenResizeAndNonzeroParentOriginStayWithinVisibleBounds() throws Exception {
        fixture.tree.posY = 10;
        fixture.screen.height = 150;
        panel.posY = 40;
        state = reflection.captureFooter(reflection.inspect(fixture.screen));
        QuestFooterLayout layout = reserve();
        assertNotNull(layout);
        assertEquals(37, panel.getY());
        assertEquals(27, panel.posY);
        assertTrue(layout.y + layout.height <= 150);
        reflection.restoreFooter(state);
        assertEquals(40, panel.posY);
        fixture.screen.height = 64;
        assertNull(reserve());
    }

    @Test
    void deferredNativeRebuildBetweenDrawEventsSkipsOverlayAndClickInterception() throws Exception {
        reserve();
        FtbQuestsCompat compat = compatWithFooter();
        compat.prepareFooterFrame(fixture.screen);
        assertTrue(compat.footerFrameMatches(fixture.screen));
        fixture.tree.rebuild(90, 60);
        assertFalse(compat.footerFrameMatches(fixture.screen));

        compat.onDrawPost(new GuiScreenEvent.DrawScreenEvent.Post(fixture.screen, 0, 0, 0));

        assertEquals(90, panel.posY);
        assertEquals(60, panel.height);
        assertEquals(43, panel.panelContent.height);
        assertNull(field(compat, "preparedFrame"));
        assertNull(field(compat, "drawnFrame"));
        assertFalse(compat.footerInputReady(fixture.screen));
        assertEquals(60, panel.height);
    }

    @Test
    void geometryChangesWithoutChildReplacementAlsoSkipTheOverlay() throws Exception {
        reserve();
        FtbQuestsCompat compat = compatWithFooter();
        compat.prepareFooterFrame(fixture.screen);
        panel.posY++;
        assertFalse(compat.footerFrameMatches(fixture.screen));
        compat.onDrawPost(new GuiScreenEvent.DrawScreenEvent.Post(fixture.screen, 0, 0, 0));
        assertEquals(78, panel.posY);
        assertFalse(compat.footerInputReady(fixture.screen));
    }

    @Test
    void preparedButNeverDrawnFooterCannotConsumeInput() throws Exception {
        reserve();
        FtbQuestsCompat compat = compatWithFooter();
        compat.prepareFooterFrame(fixture.screen);
        assertFalse(compat.footerInputReady(fixture.screen));
        setField(compat, "drawnFrame", reflection.inspect(fixture.screen));
        panel.panelContent.height++;
        assertFalse(compat.footerInputReady(fixture.screen));
        assertEquals(44, panel.panelContent.height);
    }

    @Test
    void logicalClickRejectsPreparedButUndrawnFooter() throws Exception {
        FtbQuestsCompat compat = compatWithFooter();
        compat.prepareFooterFrame(fixture.screen);
        assertFalse(compat.handleFooterClick(fixture.screen, 20, 100));
        assertNull(field(compat, "pendingTarget"));
    }

    @Test
    void logicalClickRejectsAnotherScreenAndChangedGeometry() throws Exception {
        FtbQuestsCompat compat = clickReadyCompat();
        QuestFooterLayout layout = (QuestFooterLayout) field(compat, "layout");
        assertFalse(compat.handleFooterClick(new GuiScreen() {}, layout.x + 1, layout.y + 1));
        panel.posY++;
        assertFalse(compat.handleFooterClick(fixture.screen, layout.x + 1, layout.y + 1));
        assertNull(field(compat, "pendingTarget"));
    }

    @Test
    void logicalClickOutsideFooterDoesNotConsumeInput() throws Exception {
        FtbQuestsCompat compat = clickReadyCompat();
        QuestFooterLayout layout = (QuestFooterLayout) field(compat, "layout");
        assertFalse(compat.handleFooterClick(fixture.screen, layout.x - 1, layout.y));
        assertNull(field(compat, "pendingTarget"));
    }

    @Test
    void disabledButDrawnFooterConsumesItsOwnAreaWithoutOpening() throws Exception {
        FtbQuestsCompat compat = clickReadyCompat();
        QuestFooterLayout layout = (QuestFooterLayout) field(compat, "layout");
        assertTrue(compat.handleFooterClick(fixture.screen, layout.x + 1, layout.y + 1));
        assertNull(field(compat, "pendingTarget"));
    }

    private FtbQuestsCompat clickReadyCompat() throws Exception {
        QuestFooterLayout layout = reserve();
        FtbQuestsCompat compat = compatWithFooter();
        setField(compat, "lastQuest", fixture.quest);
        setField(compat, "lastContent", panel.panelContent);
        setField(compat, "sceneGeneration", PonderIndex.getGeneration());
        setField(compat, "targets", java.util.Collections.singletonList(new QuestSceneLinks.Target("minecraft:stone", 0)));
        setField(compat, "layout", layout);
        GuiButton button = new GuiButton(0, layout.x, layout.y, layout.width, layout.height, "Test");
        button.enabled = false;
        setField(compat, "viewButton", button);
        compat.prepareFooterFrame(fixture.screen);
        setField(compat, "drawnFrame", reflection.inspect(fixture.screen));
        return compat;
    }

    private QuestFooterLayout reserve() throws ReflectiveOperationException {
        return reflection.reserveFooter(reflection.inspect(fixture.screen), state,
            fixture.screen.width, fixture.screen.height);
    }

    private FtbQuestsCompat compatWithFooter() throws Exception {
        FtbQuestsCompat compat = new FtbQuestsCompat(reflection,
            new File("C:\\CODEX\\Ponder-MMCE-Integration\\validation\\unused-footer-config.json"), null);
        setField(compat, "footerState", state);
        setField(compat, "lastScreen", fixture.screen);
        return compat;
    }

    static Object field(Object target, String name) throws Exception {
        Field field = FtbQuestsCompat.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    static void setField(Object target, String name, Object value) throws Exception {
        Field field = FtbQuestsCompat.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
