package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.content.BasePonderPlugin;
import net.createmod.ponder.foundation.content.ComplexTestPonderPlugin;
import net.createmod.ponder.foundation.content.DebugPonderPlugin;
import net.createmod.ponder.foundation.content.ThermalExpansionScenes;
import net.createmod.ponder.foundation.content.UiTestPonderPlugin;
import net.createmod.ponder.foundation.content.VanillaPonderPlugin;
import net.createmod.ponder.foundation.registration.DefaultPonderSceneRegistrationHelper;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

class LegacyGuiTargetCoverageTest {
    @Test
    void everyFiniteBuiltInGuiAnchorResolvesToAnActualVisiblePreviewBlock() throws Exception {
        Bootstrap.register();
        Field active = PonderIndex.class.getDeclaredField("active");
        active.setAccessible(true);
        Object previous = active.get(null);
        Constructor<?> constructor = active.getType().getDeclaredConstructor();
        constructor.setAccessible(true);
        active.set(null, constructor.newInstance());
        try {
            PonderSceneRegistry registry = new PonderSceneRegistry(new PonderLocalization());
            for (PonderPlugin plugin : Arrays.asList(new BasePonderPlugin(), new VanillaPonderPlugin(),
                new DebugPonderPlugin(), new UiTestPonderPlugin(), new ComplexTestPonderPlugin())) {
                plugin.registerScenes(new DefaultPonderSceneRegistrationHelper(plugin.getModId(), registry));
            }
            // Register the optional content without pretending its mod is installed.
            ThermalExpansionScenes.registerAll(new DefaultPonderSceneRegistrationHelper("thermalexpansion", registry));
            assertEquals(50, registry.getRegisteredEntryCount());
            List<String> failures = new ArrayList<>();
            int checked = 0;
            for (Map.Entry<ResourceLocation, StoryBoardEntry> entry : registry.getRegisteredEntries()) {
                PonderScene scene = PonderSceneRegistry.compileScene(new PonderLocalization(), entry.getValue());
                for (PonderScene.OverlayEvent event : scene.getOverlayEvents()) {
                    if (event.getType() != PonderScene.OverlayEventType.GUI_TEXTURE
                        && event.getType() != PonderScene.OverlayEventType.GUI_HIGHLIGHT) continue;
                    checked++;
                    int tick = event.getTick() + Math.min(10, Math.max(0, event.getDuration() / 2));
                    PonderSceneRuntime.RuntimeState runtime = PonderSceneRuntime.buildState(scene, tick);
                    if (event.getTargetPositions().isEmpty()) {
                        failures.add(scene.getSceneId() + " unbound GUI at " + tick);
                    }
                    for (BlockPos target : event.getTargetPositions()) {
                        PonderSceneRuntime.RuntimeBlockState block = runtime.blocksByPosition.get(target);
                        if (block == null || !block.visible || block.fade <= 0 || block.currentState == null
                            || block.currentState.getBlock() == Blocks.AIR) {
                            failures.add(entry.getKey() + " -> " + scene.getSceneId() + " GUI target " + target
                                + " is absent/hidden/air at " + tick);
                        }
                    }
                }
            }
            assertTrue(checked > 100, "Coverage must include the existing optional and legacy GUI content");
            assertTrue(failures.isEmpty(), String.join("\n", failures));
        } finally {
            active.set(null, previous);
        }
    }
}
