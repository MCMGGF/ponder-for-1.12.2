package net.createmod.ponder.compat.ftbquests;

import static net.createmod.ponder.compat.ftbquests.FtbQuestFooterLifecycleTest.field;
import static net.createmod.ponder.compat.ftbquests.FtbQuestFooterLifecycleTest.setField;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.createmod.ponder.compat.ftbquests.FtbApiFixture.Chapter;
import net.createmod.ponder.compat.ftbquests.FtbApiFixture.ClientQuestFile;
import net.createmod.ponder.compat.ftbquests.FtbApiFixture.GuiQuestTree;
import net.createmod.ponder.compat.ftbquests.FtbApiFixture.GuiWrapper;
import net.createmod.ponder.compat.ftbquests.FtbApiFixture.Panel;
import net.createmod.ponder.compat.ftbquests.FtbApiFixture.Quest;
import net.createmod.ponder.compat.ftbquests.FtbApiFixture.ScrollBar;
import net.createmod.ponder.compat.ftbquests.FtbQuestReflection.ReturnView;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FtbQuestReturnLifecycleTest {

    private FtbApiFixture fixture;
    private FtbQuestReflection reflection;
    private FtbQuestsCompat compat;
    private ReturnView saved;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @BeforeEach
    void setUp() throws Exception {
        fixture = new FtbApiFixture();
        reflection = FtbApiFixture.reflection();
        compat = new FtbQuestsCompat(reflection,
            new File("C:\\CODEX\\Ponder-MMCE-Integration\\validation\\unused-return-config.json"), null);
        fixture.tree.questPanel.setScrollX(-120.25);
        fixture.tree.questPanel.setScrollY(450.75);
        fixture.tree.viewQuestPanel.panelContent.setScrollY(300.5);
        fixture.tree.viewQuestPanel.panelTasks.setScrollY(12.75);
        fixture.tree.viewQuestPanel.panelRewards.setScrollX(22.25);
        fixture.tree.viewQuestPanel.panelText.setScrollY(88.5);
        capture();
    }

    @Test
    void unchangedReturnKeepsNativeSelectionAndRestoresAllScrollableChildren() throws Exception {
        int selections = fixture.tree.selections;
        GuiOpenEvent event = returnEvent();
        assertSame(fixture.screen, event.getGui());
        assertSame(saved, returning());
        assertTrue(saved.returning);
        assertEquals(selections, fixture.tree.selections);
        fixture.tree.rebuild(5, 230);
        fixture.tree.questPanel.setScrollX(0);
        fixture.tree.questPanel.setScrollY(0);
        Panel content = fixture.tree.viewQuestPanel.panelContent;
        content.contentHeight = 400;
        content.attachedScrollbar = new ScrollBar(true);
        Panel rewards = fixture.tree.viewQuestPanel.panelRewards;
        rewards.attachedScrollbar = new ScrollBar(false);

        assertTrue(reflection.restoreReturnView(event.getGui(), saved));

        assertEquals(-120.25, fixture.tree.questPanel.getScrollX());
        assertEquals(450.75, fixture.tree.questPanel.getScrollY());
        assertEquals(187.0, content.getScrollY());
        assertEquals(187, content.attachedScrollbar.value);
        assertEquals(12.75, fixture.tree.viewQuestPanel.panelTasks.getScrollY());
        assertEquals(22.25, rewards.getScrollX());
        assertEquals(22, rewards.attachedScrollbar.value);
        assertEquals(88.5, fixture.tree.viewQuestPanel.panelText.getScrollY());
    }

    @Test
    void fullSyncReturnsTheAuthoritativeTreeAndRebindsByStableIds() throws Exception {
        fixture.invalidateFile();
        FtbApiFixture replacement = new FtbApiFixture();
        replacement.tree.selectChapter(null);
        GuiOpenEvent event = returnEvent();
        ReturnView rebound = returning();

        assertNotSame(fixture.screen, event.getGui());
        assertSame(replacement.tree, ((GuiWrapper) event.getGui()).getGui());
        assertSame(replacement.file, rebound.file);
        assertSame(replacement.quest, rebound.quest);
        assertSame(replacement.chapter, rebound.chapter);
        assertNotSame(saved.positions, rebound.positions);
        assertSame(saved.positions[1], rebound.positions[1]);
        assertTrue(rebound.returning);
        replacement.tree.rebuild(90, 60);
        assertTrue(reflection.restoreReturnView(event.getGui(), rebound));
        assertEquals(-120.25, replacement.tree.questPanel.getScrollX());
        assertEquals(450.75, replacement.tree.questPanel.getScrollY());
    }

    @Test
    void sameFileTreeRefreshRebindsWhenNoNewSelectionExists() throws Exception {
        GuiQuestTree refreshed = new GuiQuestTree(fixture.file);
        fixture.file.questTreeGui = refreshed;
        GuiOpenEvent event = returnEvent();
        ReturnView rebound = returning();
        assertSame(refreshed, ((GuiWrapper) event.getGui()).getGui());
        assertSame(fixture.quest, refreshed.getViewedQuest());
        assertSame(fixture.chapter, refreshed.selectedChapter);
        assertSame(fixture.quest, rebound.quest);
        assertSame(saved.positions[1], rebound.positions[1]);
    }

    @Test
    void nativeSameFileChapterMigrationStillRestoresTheOriginalTask() throws Exception {
        GuiQuestTree refreshed = new GuiQuestTree(fixture.file);
        fixture.file.questTreeGui = refreshed;
        refreshed.selectChapter(fixture.chapter);

        GuiOpenEvent event = returnEvent();

        assertSame(refreshed, ((GuiWrapper) event.getGui()).getGui());
        assertSame(fixture.quest, refreshed.getViewedQuest());
        assertSame(fixture.chapter, refreshed.selectedChapter);
        assertNotNull(returning());
        assertSame(saved.positions[1], returning().positions[1]);
    }

    @Test
    void deletingAndReusingAQuestUidInTheSameFileNeverReopensItAsTheOldQuest() throws Exception {
        Quest unrelated = new Quest(fixture.quest.id, fixture.chapter);
        fixture.quest.invalid = true;
        fixture.file.quests.put(unrelated.id, unrelated);
        returnEvent();
        assertNull(fixture.tree.getViewedQuest());
        assertSame(fixture.chapter, fixture.tree.selectedChapter);
        assertNull(returning());
    }

    @Test
    void anAlreadySelectedReplacementWithAReusedUidIsLeftAloneWithoutOldScroll() throws Exception {
        Quest unrelated = new Quest(fixture.quest.id, fixture.chapter);
        fixture.quest.invalid = true;
        fixture.file.quests.put(unrelated.id, unrelated);
        fixture.tree.viewQuest(unrelated);
        fixture.tree.questPanel.setScrollY(17);
        returnEvent();
        assertSame(unrelated, fixture.tree.getViewedQuest());
        assertEquals(17.0, fixture.tree.questPanel.getScrollY());
        assertNull(returning());
    }

    @Test
    void removingQuestFallsBackToItsVisibleChapterWithoutInvalidObjects() throws Exception {
        fixture.file.quests.remove(fixture.quest.id);
        fixture.quest.invalid = true;
        GuiOpenEvent event = returnEvent();
        assertNotNull(event.getGui());
        assertNull(fixture.tree.getViewedQuest());
        assertSame(fixture.chapter, fixture.tree.selectedChapter);
        assertNull(returning());
    }

    @Test
    void hiddenQuestReturnsToDirectoryAndHiddenChapterIsNotSelected() throws Exception {
        fixture.quest.visible = false;
        returnEvent();
        assertNull(fixture.tree.getViewedQuest());
        assertSame(fixture.chapter, fixture.tree.selectedChapter);
        assertNull(returning());

        setUp();
        fixture.chapter.visible = false;
        returnEvent();
        assertNull(fixture.tree.getViewedQuest());
        assertNull(fixture.tree.selectedChapter);
        assertNull(returning());
    }

    @Test
    void removedOrReusedChapterDoesNotBecomeTheFallbackSelection() throws Exception {
        for (boolean reused : new boolean[] {false, true}) {
            setUp();
            fixture.file.quests.clear();
            fixture.file.chapters.remove(fixture.chapter.id);
            fixture.chapter.invalid = true;
            if (reused) {
                fixture.file.chapters.put(fixture.chapter.id, new Chapter(fixture.chapter.id));
            }
            returnEvent();
            assertNull(fixture.tree.selectedChapter);
            assertNull(fixture.tree.getViewedQuest());
        }
    }

    @Test
    void movingQuestToAnotherChapterDropsOldChapterCanvasOffsets() throws Exception {
        Chapter moved = new Chapter(101);
        fixture.file.chapters.put(moved.id, moved);
        fixture.quest.chapter = moved;
        returnEvent();
        assertSame(moved, fixture.tree.selectedChapter);
        assertSame(fixture.quest, fixture.tree.getViewedQuest());
        for (Object position : returning().positions) {
            assertNull(position);
        }
    }

    @Test
    void nativePlayerVisibilityAndEditingRulesAreRespected() throws Exception {
        fixture.file.disableGui = true;
        assertNull(returnEvent().getGui());
        assertNull(returning());

        setUp();
        fixture.file.disableGui = true;
        fixture.file.editing = true;
        fixture.quest.visible = false;
        fixture.chapter.visible = false;
        assertSame(fixture.screen, returnEvent().getGui());
        assertSame(saved, returning());
    }

    @Test
    void absentFileTeamOrAuthoritativeTreeClosesInsteadOfOpeningObsoleteData() throws Exception {
        for (int problem = 0; problem < 5; problem++) {
            setUp();
            switch (problem) {
                case 0: ClientQuestFile.INSTANCE = null; break;
                case 1: fixture.file.invalid = true; break;
                case 2: fixture.file.self = null; break;
                case 3: fixture.file.questTreeGui = null; break;
                default: fixture.file.questTreeGui = new GuiQuestTree(new ClientQuestFile()); break;
            }
            assertNull(returnEvent().getGui());
            assertNull(returning());
            assertFalse((Boolean) field(compat, "failed"));
        }
    }

    @Test
    void aDifferentCurrentTaskIsNeverReplacedOrScrolledToTheSavedTask() throws Exception {
        Quest different = new Quest(201, fixture.chapter);
        fixture.file.quests.put(different.id, different);
        fixture.tree.viewQuest(different);
        fixture.tree.questPanel.setScrollY(29);
        assertSame(fixture.screen, returnEvent().getGui());
        assertSame(different, fixture.tree.getViewedQuest());
        assertEquals(29.0, fixture.tree.questPanel.getScrollY());
        assertNull(returning());
    }

    @Test
    void aCurrentTaskSelectionOnAReplacementFileWinsEvenWhenItsUidMatches() throws Exception {
        FtbApiFixture replacement = new FtbApiFixture();
        replacement.tree.questPanel.setScrollY(39);
        GuiOpenEvent event = returnEvent();
        assertSame(replacement.tree, ((GuiWrapper) event.getGui()).getGui());
        assertSame(replacement.quest, replacement.tree.getViewedQuest());
        assertEquals(39.0, replacement.tree.questPanel.getScrollY());
        assertNull(returning());
    }

    @Test
    void anExplicitDirectorySelectionSurvivesBothSameTreeAndNewTreeReturn() throws Exception {
        for (boolean refreshed : new boolean[] {false, true}) {
            setUp();
            Chapter other = new Chapter(102);
            fixture.file.chapters.put(other.id, other);
            GuiQuestTree tree = refreshed ? new GuiQuestTree(fixture.file) : fixture.tree;
            fixture.file.questTreeGui = tree;
            tree.selectChapter(other);
            tree.questPanel.setScrollY(34);
            GuiOpenEvent event = returnEvent();
            assertSame(tree, ((GuiWrapper) event.getGui()).getGui());
            assertNull(tree.getViewedQuest());
            assertSame(other, tree.selectedChapter);
            assertEquals(34.0, tree.questPanel.getScrollY());
            assertNull(returning());
        }
    }

    @Test
    void anExplicitTopLevelDirectoryOnTheSameTreeRemainsOpen() throws Exception {
        fixture.tree.selectChapter(null);
        assertSame(fixture.screen, returnEvent().getGui());
        assertNull(fixture.tree.getViewedQuest());
        assertNull(fixture.tree.selectedChapter);
        assertNull(returning());
    }

    @Test
    void cancelledAndUnrelatedGuiEventsDoNotResurrectTheSavedTask() throws Exception {
        // Forge supplies this override from @Cancelable during launch, outside this unit-test JVM.
        GuiOpenEvent canceled = new GuiOpenEvent(fixture.screen) {
            @Override
            public boolean isCancelable() {
                return true;
            }
        };
        canceled.setCanceled(true);
        fixture.file.failLookup = true;
        compat.onGuiOpen(canceled);
        assertSame(fixture.screen, canceled.getGui());
        assertSame(saved, returning());
        assertFalse(saved.returning);
        assertFalse((Boolean) field(compat, "failed"));

        GuiScreen unrelated = new GuiScreen() {};
        GuiOpenEvent other = new GuiOpenEvent(unrelated);
        compat.onGuiOpen(other);
        assertSame(unrelated, other.getGui());
        assertNull(returning());
    }

    @Test
    void aReflectionFailureClosesTheStaleParentAndDisablesOnlyTheBridge() throws Exception {
        fixture.file.failLookup = true;
        assertNull(returnEvent().getGui());
        assertNull(returning());
        assertTrue((Boolean) field(compat, "failed"));
    }

    @Test
    void syncingDuringPonderCannotMakeNativeBackReopenTheClosedViewer() throws Exception {
        GuiScreen originalPrevious = new GuiScreen() {};
        fixture.tree.previousScreen(originalPrevious);
        capture();
        QuestPonderUI viewer = new QuestPonderUI(new ResourceLocation("test", "machine"), "test:scene",
            Collections.emptyList(), 0, fixture.screen);
        compat.onGuiOpen(new GuiOpenEvent(viewer));
        assertSame(viewer, saved.viewerScreen);
        fixture.invalidateFile();
        FtbApiFixture replacement = new FtbApiFixture();
        replacement.tree.selectChapter(null);
        replacement.tree.previousScreen(viewer);

        returnEvent();

        assertSame(originalPrevious, replacement.tree.getPrevScreen());
        replacement.tree.closeQuest();
        assertSame(originalPrevious, replacement.tree.getPrevScreen());
    }

    @Test
    void oldWrapperBackReferenceIsRepairedButUnrelatedNewBackTargetsArePreserved() throws Exception {
        for (boolean stale : new boolean[] {false, true}) {
            setUp();
            GuiScreen originalPrevious = new GuiScreen() {};
            fixture.tree.previousScreen(originalPrevious);
            capture();
            GuiQuestTree refreshed = new GuiQuestTree(fixture.file);
            fixture.file.questTreeGui = refreshed;
            GuiScreen unrelatedPrevious = new GuiScreen() {};
            refreshed.previousScreen(stale ? fixture.screen : unrelatedPrevious);
            returnEvent();
            assertSame(stale ? originalPrevious : unrelatedPrevious, refreshed.getPrevScreen());
        }
    }

    @Test
    void aCapturedPreviousQuestWrapperThatIsNowObsoleteIsCleared() throws Exception {
        GuiQuestTree obsolete = new GuiQuestTree(fixture.file);
        fixture.tree.previousScreen(obsolete.getWrapper());
        capture();
        GuiQuestTree refreshed = new GuiQuestTree(fixture.file);
        fixture.file.questTreeGui = refreshed;
        refreshed.previousScreen(fixture.screen);
        returnEvent();
        assertNull(refreshed.getPrevScreen());
    }

    @Test
    void restoringScrollDoesNotChangeProgressOrRewardClaims() throws Exception {
        fixture.file.self.progress.put(1, 42L);
        fixture.file.self.rewards.put(2, Boolean.TRUE);
        Map<Integer, Long> progress = new LinkedHashMap<>(fixture.file.self.progress);
        Map<Integer, Boolean> rewards = new LinkedHashMap<>(fixture.file.self.rewards);
        returnEvent();
        fixture.tree.rebuild(90, 60);
        reflection.restoreReturnView(fixture.screen, saved);
        assertEquals(progress, fixture.file.self.progress);
        assertEquals(rewards, fixture.file.self.rewards);
    }

    @Test
    void lateSyncOrNavigationCancelsOldScrollRestoration() throws Exception {
        returnEvent();
        fixture.tree.closeQuest();
        assertFalse(reflection.restoreReturnView(fixture.screen, saved));
        assertTrue(saved.cancelled);

        setUp();
        returnEvent();
        new FtbApiFixture();
        assertFalse(reflection.restoreReturnView(fixture.screen, saved));
        assertTrue(saved.cancelled);
    }

    @Test
    void replacedAuthoritativeTreeCancelsRestorationEvenWhenOldWrapperRemainsOpen() throws Exception {
        returnEvent();
        fixture.file.questTreeGui = new GuiQuestTree(fixture.file);
        fixture.tree.questPanel.setScrollY(17);

        assertFalse(reflection.restoreReturnView(fixture.screen, saved));
        assertTrue(saved.cancelled);
        assertEquals(17.0, fixture.tree.questPanel.getScrollY());
    }

    @Test
    void restorationStillFinishesWhenNoFooterCanBeRendered() throws Exception {
        returnEvent();
        for (int frame = 0; frame < 3; frame++) {
            compat.onDrawPost(new GuiScreenEvent.DrawScreenEvent.Post(fixture.screen, 0, 0, 0));
        }
        assertNull(returning());
        assertEquals(5, saved.remainingDraws);
    }

    private void capture() throws Exception {
        saved = reflection.captureReturnView(fixture.screen);
        assertNotNull(saved);
        setField(compat, "returnView", saved);
    }

    private GuiOpenEvent returnEvent() {
        GuiOpenEvent event = new GuiOpenEvent(saved.screen);
        compat.onGuiOpen(event);
        return event;
    }

    private ReturnView returning() throws Exception {
        return (ReturnView) field(compat, "returnView");
    }
}
