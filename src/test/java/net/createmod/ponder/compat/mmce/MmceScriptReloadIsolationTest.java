package net.createmod.ponder.compat.mmce;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class MmceScriptReloadIsolationTest {

    @Test
    void optionalClassesNeverAppearInImportsOrTypeDescriptors() throws IOException {
        for (String filename : new String[] {"MmceScriptReloadCompat.java", "MmceScriptReloadApi.java",
            "MmceReloadRefreshQueue.java"}) {
            String source = read(filename);
            for (String forbidden : new String[] {"import youyihj.", "import hellfirepvp.",
                "import crafttweaker.", "ScriptReloadEvent event", "ScriptStatus status",
                "@Mod.EventBusSubscriber", "getRequester(", "getWorld(", "getPlayer("}) {
                assertFalse(source.contains(forbidden), filename + ": " + forbidden);
            }
        }
    }

    @Test
    void bridgeDoesNotExecuteScriptsCompileScenesOrBakeModels() throws IOException {
        for (String filename : new String[] {"MmceScriptReloadCompat.java", "MmceScriptReloadApi.java",
            "MmceReloadRefreshQueue.java"}) {
            String source = read(filename);
            for (String forbidden : new String[] {"ScriptReloader", "ReloadCommand", "loadScript(",
                "reloadScripts(", "setScriptStatus(", "MachineRegistry", "ScriptRunEvent",
                "MmceMachineIndex", "compileScene", "bake(", "GeoModelRender", "TileEntity",
                "Thread.sleep", "addScheduledTask", "getDeclaredField", "setAccessible",
                "InternalUtils", "getScriptStatus", ".invoke("}) {
                assertFalse(source.contains(forbidden), filename + ": " + forbidden);
            }
        }
    }

    @Test
    void subscribersUseAuditedPrioritiesAndOneBusRegistration() throws IOException {
        String source = read("MmceScriptReloadCompat.java");
        assertTrue(source.contains("@SubscribeEvent(priority = EventPriority.HIGHEST)\n"
            + "    public void onReloadStarted(Event event)"));
        assertTrue(source.contains("@SubscribeEvent(priority = EventPriority.LOWEST)\n"
            + "    public void onReloadCompleted(Event event)"));
        assertTrue(source.contains("FMLCommonHandler.instance().getSide().isClient()"));
        assertTrue(source.contains("if (instance != null)"));
        assertTrue(source.contains("MmceRegistryReadGuard.beginReload()"));
        assertTrue(source.contains("MmceRegistryReadGuard.completeReload()"));
        String registration = "MinecraftForge.EVENT_BUS.register(candidate)";
        assertTrue(source.indexOf(registration) >= 0);
        assertTrue(source.indexOf(registration) == source.lastIndexOf(registration));
        assertFalse(source.contains("bus().register("));
    }

    @Test
    void tickUsesStableGuardEpochOnlyOnClientMainThreadAndNeverLocksTheWholeCallback() throws IOException {
        String source = read("MmceScriptReloadCompat.java");
        String tick = source.substring(source.indexOf("public void onClientTick("));
        int idleGuard = tick.indexOf("event.phase != TickEvent.Phase.END || !queue.hasPending()");
        int threadGuard = tick.indexOf("!minecraft.isCallingFromMinecraftThread()");
        int refresh = tick.indexOf("queue.refreshIfReady(true, MmceRegistryReadGuard::currentEpoch, refresh)");
        assertTrue(idleGuard >= 0 && idleGuard < threadGuard);
        assertTrue(threadGuard < refresh);
        assertFalse(tick.contains("loadedVersion("));
        assertFalse(tick.contains("Class.forName("));
        assertFalse(tick.contains("MmceRegistryReadGuard.read("));
        assertFalse(tick.contains("synchronized"));
        String notifications = source.substring(source.indexOf("public void onReloadStarted("),
            source.indexOf("public void onClientTick("));
        assertFalse(notifications.contains("refresh.run("));
        assertFalse(notifications.contains("refreshIfReady("));
        assertFalse(notifications.contains("Minecraft.getMinecraft()"));
    }

    private static String read(String filename) throws IOException {
        Path path = Paths.get("src/main/java/net/createmod/ponder/compat/mmce", filename);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
