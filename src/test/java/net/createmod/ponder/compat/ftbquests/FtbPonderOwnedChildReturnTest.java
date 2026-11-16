package net.createmod.ponder.compat.ftbquests;

import static net.createmod.ponder.compat.ftbquests.FtbQuestFooterLifecycleTest.field;
import static net.createmod.ponder.compat.ftbquests.FtbQuestFooterLifecycleTest.setField;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.createmod.ponder.authoring.practice.PracticeMode;
import net.createmod.ponder.compat.ftbquests.FtbQuestReflection.ReturnView;
import net.createmod.ponder.foundation.ui.PonderOwnedChildScreen;
import net.createmod.ponder.foundation.ui.PracticeScreen;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FtbPonderOwnedChildReturnTest {

    private FtbApiFixture fixture;
    private FtbQuestReflection reflection;
    private FtbQuestsCompat compat;
    private ReturnView saved;
    private QuestPonderUI viewer;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @BeforeEach
    void setUp() throws Exception {
        fixture = new FtbApiFixture();
        reflection = FtbApiFixture.reflection();
        compat = new FtbQuestsCompat(reflection,
            new File("C:\\CODEX\\Ponder-MMCE-Integration\\validation\\unused-owned-child-config.json"), null);
        fixture.tree.questPanel.setScrollX(-120.25);
        fixture.tree.questPanel.setScrollY(450.75);
        fixture.tree.viewQuestPanel.panelContent.setScrollY(300.5);
        saved = reflection.captureReturnView(fixture.screen);
        setField(compat, "returnView", saved);
        viewer = newViewer();
    }

    @Test
    void directOwnedChildAndBackPreserveOriginalReturnAndScrollSnapshot() throws Exception {
        open(viewer);
        OwnedChild child = new OwnedChild(viewer);
        GuiOpenEvent childEvent = open(child);
        assertSame(child, childEvent.getGui());
        assertSame(saved, returning());
        assertSame(viewer, saved.viewerScreen);
        assertFalse(saved.returning);

        open(viewer);
        assertSame(saved, returning());
        fixture.tree.questPanel.setScrollX(0);
        fixture.tree.questPanel.setScrollY(0);
        fixture.tree.viewQuestPanel.panelContent.setScrollY(0);
        GuiOpenEvent returnEvent = open(fixture.screen);
        assertSame(fixture.screen, returnEvent.getGui());
        assertSame(saved, returning());
        assertTrue(saved.returning);
        assertTrue(reflection.restoreReturnView(fixture.screen, saved));
        assertEquals(-120.25, fixture.tree.questPanel.getScrollX());
        assertEquals(450.75, fixture.tree.questPanel.getScrollY());
        assertEquals(300.5, fixture.tree.viewQuestPanel.panelContent.getScrollY());
    }

    @Test
    void sameComponentButDifferentViewerIdentityIsNotAnOwnedChild() throws Exception {
        open(viewer);
        QuestPonderUI foreignViewer = newViewer();
        OwnedChild child = new OwnedChild(foreignViewer);
        assertSame(child, open(child).getGui());
        assertNull(returning());
        assertFalse((Boolean) field(compat, "failed"));
    }

    @Test
    void existingVanillaPracticeScreenUsesTheSameDirectOwnerContract() throws Exception {
        PracticeScreen child = PracticeScreen.openVanilla(viewer, "minecraft:chest", PracticeMode.TRY);
        try {
            assertTrue(PonderOwnedChildScreen.class.isAssignableFrom(PracticeScreen.class));
            assertSame(viewer, child.getPonderParent());
            open(viewer);
            open(child);
            assertSame(saved, returning());
            assertSame(viewer, saved.viewerScreen);
            open(viewer);
            open(fixture.screen);
            assertTrue(reflection.restoreReturnView(fixture.screen, saved));
        } finally {
            child.getSession().close();
        }
    }

    @Test
    void existingPracticeTypeCannotBypassForeignParentIdentity() throws Exception {
        PracticeScreen child = PracticeScreen.openVanilla(new GuiScreen() {}, "minecraft:chest", PracticeMode.TRY);
        try {
            open(viewer);
            open(child);
            assertNull(returning());
        } finally {
            child.getSession().close();
        }
    }

    @Test
    void questScreenAsParentCannotImpersonateThePonderViewer() throws Exception {
        open(viewer);
        open(new OwnedChild(fixture.screen));
        assertNull(returning());
    }

    @Test
    void nullParentDoesNotMatchAnUnregisteredNullViewer() throws Exception {
        assertNull(saved.viewerScreen);
        open(new OwnedChild(null));
        assertNull(returning());
    }

    @Test
    void nestedChildWithoutTheDirectViewerParentIsRejected() throws Exception {
        open(viewer);
        OwnedChild direct = new OwnedChild(viewer);
        open(direct);
        assertSame(saved, returning());
        open(new OwnedChild(direct));
        assertNull(returning());
    }

    @Test
    void closingTheChildClearsReturnAndLaterChildCannotResurrectIt() throws Exception {
        open(viewer);
        open(new OwnedChild(viewer));
        GuiOpenEvent closed = open(null);
        assertNull(closed.getGui());
        assertNull(returning());
        open(new OwnedChild(viewer));
        assertNull(returning());
    }

    @Test
    void unrelatedScreenClearsReturnWithoutRestoringOldQuestPosition() throws Exception {
        open(viewer);
        open(new OwnedChild(viewer));
        GuiScreen unrelated = new GuiScreen() {};
        assertSame(unrelated, open(unrelated).getGui());
        assertNull(returning());
        fixture.tree.questPanel.setScrollY(19);
        open(fixture.screen);
        assertNull(returning());
        assertEquals(19, fixture.tree.questPanel.getScrollY());
    }

    @Test
    void lateChildCannotKeepAViewerReturnAfterQuestRestorationHasStarted() throws Exception {
        open(viewer);
        open(new OwnedChild(viewer));
        open(viewer);
        open(fixture.screen);
        assertSame(saved, returning());
        assertTrue(saved.returning);

        open(new OwnedChild(viewer));
        assertNull(returning());
        assertFalse((Boolean) field(compat, "failed"));
    }

    @Test
    void canceledForeignChildEventDoesNotChangeTheStillOpenViewerReturn() throws Exception {
        open(viewer);
        GuiOpenEvent canceled = new GuiOpenEvent(new OwnedChild(newViewer())) {
            @Override
            public boolean isCancelable() {
                return true;
            }
        };
        canceled.setCanceled(true);
        compat.onGuiOpen(canceled);
        assertSame(saved, returning());
        assertSame(viewer, saved.viewerScreen);
        assertFalse(saved.returning);
    }

    @Test
    void childRoundTripDoesNotChangeProgressOrRewardClaims() throws Exception {
        fixture.file.self.progress.put(1, 42L);
        fixture.file.self.rewards.put(2, Boolean.TRUE);
        Map<Integer, Long> progress = new LinkedHashMap<>(fixture.file.self.progress);
        Map<Integer, Boolean> rewards = new LinkedHashMap<>(fixture.file.self.rewards);
        open(viewer);
        open(new OwnedChild(viewer));
        open(viewer);
        open(fixture.screen);
        assertTrue(reflection.restoreReturnView(fixture.screen, saved));
        assertEquals(progress, fixture.file.self.progress);
        assertEquals(rewards, fixture.file.self.rewards);
    }

    private QuestPonderUI newViewer() {
        return new QuestPonderUI(new ResourceLocation("test", "oil_line"), "test:oil_scene",
            Collections.emptyList(), 0, fixture.screen);
    }

    private GuiOpenEvent open(GuiScreen screen) {
        GuiOpenEvent event = new GuiOpenEvent(screen);
        compat.onGuiOpen(event);
        return event;
    }

    private ReturnView returning() throws Exception {
        return (ReturnView) field(compat, "returnView");
    }

    private static final class OwnedChild extends GuiScreen implements PonderOwnedChildScreen {
        private final GuiScreen parent;

        OwnedChild(GuiScreen parent) {
            this.parent = parent;
        }

        @Override
        public GuiScreen getPonderParent() {
            return parent;
        }
    }
}
