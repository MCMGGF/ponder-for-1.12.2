package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.content.VanillaPonderPlugin;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

class PonderTutorialSceneCountTest {
    private final ResourceLocation crafting = new ResourceLocation("minecraft", "crafting_table");
    private final ResourceLocation furnace = new ResourceLocation("minecraft", "furnace");

    @Test
    void tutorialCardsCountExactlyTheEntriesTheirViewerWillCompile() {
        List<Map.Entry<ResourceLocation, StoryBoardEntry>> registrations = fixtures();
        Map<ResourceLocation, Integer> counts = AbstractPonderBrowserScreen.countScenesByComponent(
            VanillaPonderPlugin.TUTORIAL_TAG, registrations);
        assertEquals(PonderUI.tutorialEntries(crafting, registrations).size(), counts.get(crafting));
        assertEquals(PonderUI.tutorialEntries(furnace, registrations).size(), counts.get(furnace));
        assertEquals(1, counts.get(crafting));
        assertEquals(1, counts.get(furnace));
    }

    @Test
    void unfilteredBrowserStillCountsLegacyAndDiagnosticEntrypoints() {
        Map<ResourceLocation, Integer> counts = AbstractPonderBrowserScreen.countScenesByComponent(null, fixtures());
        assertEquals(3, counts.get(crafting));
        assertEquals(2, counts.get(furnace));
    }

    @Test
    void unrelatedTagsDoNotLeakIntoTutorialCounts() {
        assertTrue(AbstractPonderBrowserScreen.countScenesByComponent(
            new ResourceLocation("test", "absent"), fixtures()).isEmpty());
    }

    @Test
    void filteredCardDoesNotMutateTheAllScenesCard() {
        PonderComponentEntry all = new PonderComponentEntry(crafting, ItemStack.EMPTY, "Crafting", 3,
            Collections.emptySet());
        PonderComponentEntry filtered = all.withSceneCount(1);
        assertEquals(3, all.sceneCount);
        assertEquals(1, filtered.sceneCount);
        assertEquals(all.componentId, filtered.componentId);
        assertEquals(all.label, filtered.label);
        assertSame(all.tags, filtered.tags);
        assertSame(all, all.withSceneCount(3));
    }

    private List<Map.Entry<ResourceLocation, StoryBoardEntry>> fixtures() {
        return Arrays.asList(entry(crafting, false), entry(crafting, true), entry(crafting, false),
            entry(furnace, true), entry(furnace, false));
    }

    private Map.Entry<ResourceLocation, StoryBoardEntry> entry(ResourceLocation component, boolean tutorial) {
        StoryBoardEntry entry = new PonderStoryBoardEntry((scene, util) -> {
            fail("Counting cards must not compile legacy, diagnostic or tutorial scenes");
        }, "test", new ResourceLocation("test", "unreadable"), component);
        if (tutorial) entry.highlightTag(VanillaPonderPlugin.TUTORIAL_TAG);
        return new AbstractMap.SimpleImmutableEntry<>(component, entry);
    }
}
