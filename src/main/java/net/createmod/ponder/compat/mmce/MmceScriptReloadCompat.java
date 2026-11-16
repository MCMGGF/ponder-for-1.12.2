package net.createmod.ponder.compat.mmce;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.content.mmce.MmceRegistryReadGuard;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Client-only, read-only bridge from completed native script reloads to Ponder.
 * Register from ClientProxy, never from common startup or a server event hook.
 */
public final class MmceScriptReloadCompat {

    private static MmceScriptReloadCompat instance;

    private final MmceScriptReloadApi api;
    private final Runnable refresh;
    private final BooleanSupplier registryReady;
    private final MmceReloadRefreshQueue queue = new MmceReloadRefreshQueue();
    private volatile boolean disabled;

    private MmceScriptReloadCompat(MmceScriptReloadApi api, Runnable refresh, BooleanSupplier registryReady) {
        if (!api.usesEventBase(Event.class)) {
            throw new IllegalArgumentException("ZenUtils reload notifications must be Forge events");
        }
        this.api = api;
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.registryReady = Objects.requireNonNull(registryReady, "registryReady");
    }

    public static synchronized void register(Runnable refresh, BooleanSupplier registryReady) {
        if (instance != null) {
            return;
        }
        MmceScriptReloadCompat candidate = null;
        try {
            MmceScriptReloadApi api = MmceScriptReloadApi.resolve(
                FMLCommonHandler.instance().getSide().isClient(),
                MmceScriptReloadCompat::loadedVersion, MmceScriptReloadCompat.class.getClassLoader());
            if (api == null) {
                return;
            }
            candidate = new MmceScriptReloadCompat(api, refresh, registryReady);
            // FMLCommonHandler.bus() aliases this bus in Forge 1.12.2.
            MinecraftForge.EVENT_BUS.register(candidate);
            instance = candidate;
            Ponder.LOGGER.info("Enabled deferred Ponder refresh for MMCE 2.3.2 / ZenUtils 1.28.7 script reloads");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (candidate != null) {
                candidate.disabled = true;
                MinecraftForge.EVENT_BUS.unregister(candidate);
            }
            Ponder.LOGGER.warn("Optional MMCE script reload notifications are unavailable", failure);
        }
    }

    private static String loadedVersion(String modId) {
        if (!Loader.isModLoaded(modId)) {
            return null;
        }
        ModContainer mod = Loader.instance().getIndexedModList().get(modId);
        return mod == null ? null : mod.getVersion();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onReloadStarted(Event event) {
        if (!disabled && api.isPre(event)) {
            queue.reloadStarted();
            MmceRegistryReadGuard.beginReload();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onReloadCompleted(Event event) {
        if (disabled || !api.isPost(event)) {
            return;
        }
        long completedEpoch = MmceRegistryReadGuard.completeReload();
        if (completedEpoch == MmceRegistryReadGuard.NO_COMPLETION) {
            return;
        }
        try {
            // MMCE's NORMAL listener has published all audited native registry data.
            queue.reloadCompleted(completedEpoch, registryReady.getAsBoolean());
        } catch (RuntimeException | LinkageError failure) {
            queue.clear();
            Ponder.LOGGER.warn("Could not queue Ponder refresh after the MMCE script reload notification", failure);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (disabled || event.phase != TickEvent.Phase.END || !queue.hasPending()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || !minecraft.isCallingFromMinecraftThread()) {
            return;
        }
        try {
            queue.refreshIfReady(true, MmceRegistryReadGuard::currentEpoch, refresh);
        } catch (RuntimeException | LinkageError failure) {
            // The consumed attempt stays consumed; a later reload can request another.
            Ponder.LOGGER.warn("Ponder refresh failed after an MMCE script reload notification", failure);
        }
    }
}
