package net.createmod.ponder.authoring.trigger;

import com.google.gson.JsonObject;
import java.util.Collections;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.chunk.Chunk;

/** Client-thread adapter; inspecting an unloaded target never requests or generates its chunk. */
public final class AuthoringTriggerManager {
    private static final TriggerEngine ENGINE = new TriggerEngine();
    private static WorldClient owner;
    private static long revision = -1, tick;
    private AuthoringTriggerManager() { }

    public static void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!available(mc)) { clearSession(); return; }
        if (owner != mc.world) { ENGINE.clearSession(); owner = mc.world; tick = 0; }
        TriggerRegistry.Snapshot snapshot = TriggerRegistry.snapshot();
        if (revision != snapshot.revision) { ENGINE.replaceRules(snapshot.definitions); revision = snapshot.revision; }
        if (mc.currentScreen != null) return;
        final WorldClient world = owner;
        final BlockPos player = mc.player.getPosition().toImmutable();
        TriggerEngine.Hint hint = ENGINE.poll(new TriggerEngine.WorldView() {
            @Override public int dimension() { return world.provider.getDimension(); }
            @Override public BlockPos playerPosition() { return player; }
            @Override public boolean isLoaded(BlockPos position) {
                return world.getChunkProvider().getLoadedChunk(position.getX() >> 4, position.getZ() >> 4) != null;
            }
            @Override public IBlockState blockState(BlockPos position) {
                Chunk chunk = world.getChunkProvider().getLoadedChunk(position.getX() >> 4, position.getZ() >> 4);
                return chunk == null ? null : chunk.getBlockState(position);
            }
        }, ++tick);
        if (hint != null) mc.ingameGUI.setOverlayMessage(new TextComponentString(hint.getText()), false);
    }
    private static boolean available(Minecraft mc) {
        return mc != null && mc.isSingleplayer() && mc.getIntegratedServer() != null && mc.world != null && mc.player != null;
    }
    public static TriggerEngine.Hint getActiveHint() {
        Minecraft mc = Minecraft.getMinecraft();
        return available(mc) && owner == mc.world && mc.currentScreen == null ? ENGINE.getActionableHint() : null;
    }
    public static boolean markOpened(ResourceLocation runtimeSceneId) {
        Minecraft mc = Minecraft.getMinecraft();
        return available(mc) && owner == mc.world && ENGINE.markOpened(runtimeSceneId);
    }
    public static void clearSession() {
        ENGINE.clearSession(); owner = null; revision = -1; tick = 0;
    }
    public static void disable() {
        clearSession();
        ENGINE.replaceRules(Collections.emptyList());
        TriggerRegistry.replacePublished(Collections.emptyList());
    }
    public static JsonObject getDiagnostics() {
        JsonObject result = new JsonObject();
        result.addProperty("worldOwned", owner != null); result.addProperty("tick", tick);
        result.addProperty("registryRevision", revision); result.addProperty("rules", TriggerRegistry.snapshot().definitions.size());
        result.addProperty("evaluations", ENGINE.getEvaluations()); result.addProperty("lastRuleChecks", ENGINE.getLastRuleChecks());
        result.addProperty("lastBlockReads", ENGINE.getLastBlockReads()); result.addProperty("hintCount", ENGINE.getHintCount());
        result.addProperty("openedCount", ENGINE.getOpenedCount());
        TriggerEngine.Hint active = ENGINE.getActiveHint();
        result.addProperty("activeSceneId", active == null ? "" : active.getRuntimeSceneId().toString());
        return result;
    }
}
