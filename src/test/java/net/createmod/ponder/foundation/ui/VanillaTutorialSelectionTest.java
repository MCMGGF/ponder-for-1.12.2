package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.content.VanillaPonderPlugin;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

class VanillaTutorialSelectionTest {
    @Test void tutorialGroupDoesNotFallBackToLargerDiagnosticOrExternalTags() {
        PonderUI screen = PonderUI.showcaseForTag(new ResourceLocation("minecraft", "furnace"),
            VanillaPonderPlugin.TUTORIAL_TAG, null);
        assertTrue(screen.isShowcaseGroupCandidate(new PonderTag(VanillaPonderPlugin.TUTORIAL_TAG, null, null, null)));
        for (String id : new String[] {"ponder_legacy:ui_test", "ponder_legacy:complex_tests", "external:large_group"}) {
            assertFalse(screen.isShowcaseGroupCandidate(new PonderTag(new ResourceLocation(id), null, null, null)));
        }
        assertFalse(screen.isShowcaseGroupCandidate(null));
    }
    @Test void tutorialSelectionExcludesDiagnosticsBeforeCompiling() {
        ResourceLocation component = new ResourceLocation("minecraft", "crafting_table");
        StoryBoardEntry tutorial = entry(component);
        tutorial.highlightTag(VanillaPonderPlugin.TUTORIAL_TAG);
        StoryBoardEntry debug = entry(component);
        ResourceLocation other = new ResourceLocation("minecraft", "furnace");
        StoryBoardEntry otherTutorial = entry(other);
        otherTutorial.highlightTag(VanillaPonderPlugin.TUTORIAL_TAG);
        List<Map.Entry<ResourceLocation, StoryBoardEntry>> registrations = Arrays.asList(
            new AbstractMap.SimpleEntry<>(component, debug),
            new AbstractMap.SimpleEntry<>(component, tutorial),
            new AbstractMap.SimpleEntry<>(other, otherTutorial));
        assertEquals(Collections.singletonList(tutorial), PonderUI.tutorialEntries(component, registrations));
    }
    private StoryBoardEntry entry(ResourceLocation component) {
        return new PonderStoryBoardEntry((scene, util) -> {
            throw new AssertionError("Selection must not compile a scene");
        }, "test", new ResourceLocation("test", "unreadable"), component);
    }
}
