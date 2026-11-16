package net.createmod.ponder.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.createmod.ponder.api.registration.SceneRegistryAccess;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.command.CommandBase;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

class ClientCommandCompletionTest {
    @Test
    void bothClientCommandsReadCountsWithoutCompilation() {
        PonderSceneRegistry registry = new PonderSceneRegistry(new PonderLocalization()) {
            @Override public List<PonderScene> compile(ResourceLocation id) {
                throw new AssertionError("Tab completion compiled a scene");
            }
            @Override public List<PonderScene> compile(Collection<StoryBoardEntry> entries) {
                throw new AssertionError("Tab completion compiled storyboards");
            }
        };
        ResourceLocation id = new ResourceLocation("test", "component");
        for (int i = 0; i < 12; i++) {
            registry.addStoryBoard(new PonderStoryBoardEntry((scene, util) -> {
                throw new AssertionError("Tab completion ran a storyboard");
            }, "test", new ResourceLocation("test", "missing_" + i), id));
        }
        CommandBase[] commands = {
            new PonderClientCommand() {
                @Override SceneRegistryAccess getSceneAccess() { return registry; }
            },
            new PonderDebugClientCommand() {
                @Override SceneRegistryAccess getSceneAccess() { return registry; }
            }
        };
        for (CommandBase command : commands) {
            assertEquals(12, complete(command, id.toString(), "").size());
            assertEquals(Arrays.asList("1", "10", "11"), complete(command, id.toString(), "1"));
            assertTrue(complete(command, "test:absent", "").isEmpty());
            assertTrue(complete(command, id.toString(), "20").isEmpty());
            registry.clearRegistry();
            assertTrue(complete(command, id.toString(), "").isEmpty());
            // Restore registration for the second command, retaining the throwing compiler.
            for (int i = 0; i < 12; i++) {
                registry.addStoryBoard(new PonderStoryBoardEntry((scene, util) -> {
                    throw new AssertionError("Tab completion ran a storyboard");
                }, "test", new ResourceLocation("test", "missing_" + i), id));
            }
        }
    }

    private List<String> complete(CommandBase command, String id, String prefix) {
        return command.getTabCompletions(null, null, new String[] {id, prefix}, null);
    }
}
