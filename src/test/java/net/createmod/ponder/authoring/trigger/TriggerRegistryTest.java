package net.createmod.ponder.authoring.trigger;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.createmod.ponder.authoring.AuthoringPlugin;
import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TriggerRegistryTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void onlySuccessfulPluginReloadCommitsTheStagedTriggerSnapshot() throws Exception {
        List<TriggerDefinition> previous = TriggerRegistry.snapshot().definitions;
        try {
            TriggerDefinition old = TriggerDefinitionTest.parse(TriggerDefinitionTest.coordinate("old_registry"));
            TriggerDefinition next = TriggerDefinitionTest.parse(TriggerDefinitionTest.coordinate("new_registry"));
            TriggerRegistry.replacePublished(Collections.singletonList(old));
            TriggerRegistry.Snapshot before = TriggerRegistry.snapshot();
            AuthoringPlugin plugin = new AuthoringPlugin(); Field staged = AuthoringPlugin.class.getDeclaredField("reloadTriggers"); staged.setAccessible(true);
            staged.set(plugin, Collections.singletonList(next)); plugin.onPonderReloadEnd(false);
            assertSame(before, TriggerRegistry.snapshot());
            staged.set(plugin, Collections.singletonList(next)); plugin.onPonderReloadEnd(true);
            assertEquals(next, TriggerRegistry.snapshot().definitions.get(0)); assertTrue(TriggerRegistry.snapshot().revision > before.revision);
            assertThrows(UnsupportedOperationException.class, () -> TriggerRegistry.snapshot().definitions.clear());
        } finally { TriggerRegistry.replacePublished(previous); }
    }

    @Test void invalidOrOversizedReplacementCannotChangeThePublishedSnapshot() {
        TriggerRegistry.Snapshot before = TriggerRegistry.snapshot();
        TriggerDefinition rule = TriggerDefinitionTest.parse(TriggerDefinitionTest.coordinate("limit"));
        assertThrows(IllegalArgumentException.class, () -> TriggerRegistry.replacePublished(java.util.Arrays.asList(rule, rule)));
        List<TriggerDefinition> many = new ArrayList<TriggerDefinition>(); for (int i = 0; i < 65; i++) many.add(rule);
        assertThrows(IllegalArgumentException.class, () -> TriggerRegistry.replacePublished(many));
        assertSame(before, TriggerRegistry.snapshot());
    }

    @Test void clientAdapterHasNoChunkLoadingWorldMutationPacketOrAutomaticOpeningPath() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/net/createmod/ponder/authoring/trigger/AuthoringTriggerManager.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("getLoadedChunk(")); assertTrue(source.contains("chunk.getBlockState(position)"));
        assertTrue(source.contains("mc.isSingleplayer()")); assertTrue(source.contains("mc.currentScreen != null"));
        assertTrue(source.contains("ENGINE.getActionableHint()"));
        assertTrue(source.contains("TriggerEngine.Hint active = ENGINE.getActiveHint()"));
        for (String denied : new String[] {".getChunk(", ".getChunkFrom", ".provideChunk(", ".loadChunk(", ".getTileEntity(",
            ".setBlockState(", ".setTileEntity(", "sendPacket(", "displayGuiScreen(", "openScene(", "PonderUI"}) assertFalse(source.contains(denied), denied);
    }
}
