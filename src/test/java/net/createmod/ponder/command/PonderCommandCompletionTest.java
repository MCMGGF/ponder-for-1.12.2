package net.createmod.ponder.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.createmod.ponder.api.registration.SceneRegistryAccess;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderComponentMatcher;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

class PonderCommandCompletionTest {

    @Test
    void dumpCompletionNeverCompilesOrReadsSceneResources() {
        PonderSceneRegistry registry = nonCompilingRegistry();
        ResourceLocation component = id("machine");
        registry.addStoryBoard(unreadableEntry(component));
        registry.addStoryBoard(unreadableEntry(component));

        assertEquals(Arrays.asList("0", "1"), complete(registry, component, ""));
        assertEquals(Collections.singletonList("1"), complete(registry, component, "1"));
    }

    @Test
    void completionIncludesItemAliasesWithoutDuplicatingDirectEntries() {
        PonderSceneRegistry registry = nonCompilingRegistry();
        ResourceLocation item = id("controller");
        ResourceLocation machine = id("machine");
        registry.addStoryBoard(unreadableEntry(item));
        registry.addStoryBoard(unreadableEntry(machine));
        registry.registerComponentMatcher(machine, PonderComponentMatcher.simple(item));

        assertEquals(Arrays.asList("0", "1"), complete(registry, item, ""));
        assertEquals(Collections.singletonList("0"), complete(registry, machine, ""));
    }

    @Test
    void absentScenesAndNonmatchingPrefixesHaveNoCompletion() {
        PonderSceneRegistry registry = nonCompilingRegistry();
        ResourceLocation component = id("machine");
        registry.addStoryBoard(unreadableEntry(component));

        assertTrue(complete(registry, id("missing"), "").isEmpty());
        assertTrue(complete(registry, component, "7").isEmpty());
        assertEquals(0, registry.getSceneCount(null));
    }

    @Test
    void completionReadsTheCurrentRegistryAfterReload() {
        PonderSceneRegistry before = nonCompilingRegistry();
        PonderSceneRegistry after = nonCompilingRegistry();
        ResourceLocation component = id("machine");
        before.addStoryBoard(unreadableEntry(component));
        after.addStoryBoard(unreadableEntry(component));
        after.addStoryBoard(unreadableEntry(component));
        final SceneRegistryAccess[] active = { before };
        PonderCommand command = new PonderCommand() {
            @Override
            SceneRegistryAccess getSceneAccess() {
                return active[0];
            }
        };

        String[] arguments = { "dump", component.toString(), "" };
        assertEquals(Collections.singletonList("0"), command.getTabCompletions(null, null, arguments, null));
        active[0] = after;
        assertEquals(Arrays.asList("0", "1"), command.getTabCompletions(null, null, arguments, null));
    }

    @Test
    void countUsesTheSameResolvedEntriesAsCompilation() {
        final List<StoryBoardEntry> captured = new ArrayList<StoryBoardEntry>();
        PonderSceneRegistry registry = new PonderSceneRegistry(new PonderLocalization()) {
            @Override
            public List<PonderScene> compile(Collection<StoryBoardEntry> entries) {
                captured.clear();
                captured.addAll(entries);
                return Collections.emptyList();
            }
        };
        ResourceLocation item = id("controller");
        ResourceLocation machine = id("machine");
        StoryBoardEntry direct = unreadableEntry(item);
        StoryBoardEntry aliased = unreadableEntry(machine);
        registry.addStoryBoard(direct);
        registry.addStoryBoard(aliased);
        registry.addStoryBoard(aliased);
        registry.registerComponentMatcher(machine, PonderComponentMatcher.simple(item));

        registry.compile(item);
        assertEquals(Arrays.asList(direct, aliased), captured);
        assertEquals(captured.size(), registry.getSceneCount(item));
    }

    private static List<String> complete(final SceneRegistryAccess registry, ResourceLocation component, String prefix) {
        PonderCommand command = new PonderCommand() {
            @Override
            SceneRegistryAccess getSceneAccess() {
                return registry;
            }
        };
        return command.getTabCompletions(null, null, new String[] { "dump", component.toString(), prefix }, null);
    }

    private static PonderSceneRegistry nonCompilingRegistry() {
        return new PonderSceneRegistry(new PonderLocalization()) {
            @Override
            public List<PonderScene> compile(ResourceLocation id) {
                throw new AssertionError("Completion attempted to compile a scene");
            }

            @Override
            public List<PonderScene> compile(Collection<StoryBoardEntry> entries) {
                throw new AssertionError("Completion attempted to compile storyboards");
            }
        };
    }

    private static StoryBoardEntry unreadableEntry(ResourceLocation component) {
        return new PonderStoryBoardEntry((scene, util) -> {
            throw new AssertionError("Completion executed a storyboard");
        }, "completion_test", id("missing_schematic"), component) {
            @Override
            public ResourceLocation getSchematicLocation() {
                throw new AssertionError("Completion read a schematic location");
            }
        };
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("completion_test", path);
    }
}
