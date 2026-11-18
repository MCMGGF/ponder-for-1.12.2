package net.createmod.ponder.foundation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.PonderBackportMod;
import net.createmod.ponder.api.registration.LangRegistryAccess;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.SceneRegistryAccess;
import net.createmod.ponder.api.registration.TagRegistryAccess;
import net.createmod.ponder.foundation.registration.DefaultPonderSceneRegistrationHelper;
import net.createmod.ponder.foundation.registration.DefaultPonderTagRegistrationHelper;
import net.createmod.ponder.foundation.registration.PonderComponentMatcher;
import net.createmod.ponder.foundation.registration.DefaultSharedTextRegistrationHelper;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.createmod.ponder.foundation.registration.PonderTagRegistry;
import net.minecraft.util.ResourceLocation;

public final class PonderIndex {

    private static volatile Registries active = new Registries();
    private static final ThreadLocal<Registries> BUILDING = new ThreadLocal<Registries>();
    private static volatile long generation;
    private static boolean reloading;
    private static final List<PonderPlugin> PLUGINS = new ArrayList<PonderPlugin>();
    private static final Comparator<PonderPlugin> PLUGIN_COMPARATOR = new Comparator<PonderPlugin>() {
        @Override
        public int compare(PonderPlugin left, PonderPlugin right) {
            boolean leftIsPonder = Ponder.MOD_ID.equals(left.getModId());
            boolean rightIsPonder = Ponder.MOD_ID.equals(right.getModId());
            if (leftIsPonder != rightIsPonder) {
                return leftIsPonder ? -1 : 1;
            }
            return left.getModId().compareTo(right.getModId());
        }
    };

    private PonderIndex() {
    }

    public static void addPlugin(PonderPlugin plugin) {
        synchronized (PLUGINS) {
            int index = Collections.binarySearch(PLUGINS, plugin, PLUGIN_COMPARATOR);
            int insertionPoint = index >= 0 ? index : -index - 1;
            PLUGINS.add(insertionPoint, plugin);
        }
    }

    public static void forEachPlugin(Consumer<PonderPlugin> action) {
        for (PonderPlugin plugin : plugins()) {
            action.accept(plugin);
        }
    }

    private static List<PonderPlugin> plugins() {
        synchronized (PLUGINS) {
            return new ArrayList<PonderPlugin>(PLUGINS);
        }
    }

    public static synchronized void reload() {
        if (reloading) {
            throw new IllegalStateException("A Ponder plugin attempted a recursive registry reload");
        }
        long started = System.nanoTime();
        Ponder.LOGGER.info("Reloading Ponder plugin registry");
        Registries next = new Registries();
        List<PonderPlugin> participants = plugins();
        List<PonderPlugin> startedPlugins = new ArrayList<PonderPlugin>();
        boolean successful = false;
        Throwable pendingFailure = null;
        reloading = true;
        try {
            BUILDING.set(next);
            for (PonderPlugin plugin : participants) {
                startedPlugins.add(plugin);
                plugin.onPonderReloadStart();
            }
            registerAll(participants);
            gatherSharedText(participants);
            // Readers retain the previous complete index until registration succeeds.
            active = next;
            generation++;
            successful = true;
            Ponder.LOGGER.info("Ponder registry generation {} contains {} scene entries and {} listed tags ({} ms)",
                Long.valueOf(generation), Integer.valueOf(next.scenes.getRegisteredEntryCount()),
                Integer.valueOf(next.tags.getListedTagCount()),
                Long.valueOf((System.nanoTime() - started) / 1000000L));
        } catch (RuntimeException | LinkageError failure) {
            Ponder.LOGGER.error("Ponder registry reload failed; retaining the previous complete index", failure);
        } catch (Error failure) {
            // Attempt every rollback before propagating a serious failure.
            pendingFailure = failure;
        } finally {
            BUILDING.remove();
            try {
                for (int i = startedPlugins.size() - 1; i >= 0; i--) {
                    PonderPlugin plugin = startedPlugins.get(i);
                    try {
                        plugin.onPonderReloadEnd(successful);
                    } catch (RuntimeException | LinkageError failure) {
                        if (pendingFailure != null) {
                            pendingFailure = retainFailure(pendingFailure, failure);
                        }
                        Ponder.LOGGER.error("Ponder plugin {} failed to finish its reload",
                            plugin.getClass().getName(), failure);
                    } catch (Error failure) {
                        pendingFailure = retainFailure(pendingFailure, failure);
                    }
                }
                // A failed plugin may still have changed optional resource registries.
                if (PonderBackportMod.proxy != null) {
                    try {
                        PonderBackportMod.proxy.onPonderReload();
                    } catch (RuntimeException | Error failure) {
                        pendingFailure = retainFailure(pendingFailure, failure);
                    }
                }
            } finally {
                reloading = false;
            }
        }
        if (pendingFailure instanceof Error) {
            throw (Error) pendingFailure;
        }
        if (pendingFailure != null) {
            throw (RuntimeException) pendingFailure;
        }
    }

    private static Throwable retainFailure(Throwable original, Throwable failure) {
        if (original == null) {
            return failure;
        }
        if (original != failure) {
            original.addSuppressed(failure);
        }
        return original;
    }

    public static void registerAll() {
        registerAll(plugins());
    }

    private static void registerAll(List<PonderPlugin> plugins) {
        for (PonderPlugin plugin : plugins) {
            Registries registries = registries();
            String namespace = plugin.getRegistrationNamespace();
            plugin.registerScenes(new DefaultPonderSceneRegistrationHelper(namespace, registries.scenes));
            plugin.registerTags(new DefaultPonderTagRegistrationHelper(namespace, registries.tags,
                registries.localization));
        }
    }

    public static void gatherSharedText() {
        gatherSharedText(plugins());
    }

    private static void gatherSharedText(List<PonderPlugin> plugins) {
        for (PonderPlugin plugin : plugins) {
            plugin.registerSharedText(new DefaultSharedTextRegistrationHelper(plugin.getRegistrationNamespace(),
                registries().localization));
        }
    }

    public static SceneRegistryAccess getSceneAccess() {
        return registries().scenes;
    }

    public static void registerComponentMatcher(ResourceLocation componentId, PonderComponentMatcher matcher) {
        registries().scenes.registerComponentMatcher(componentId, matcher);
    }

    public static TagRegistryAccess getTagAccess() {
        return registries().tags;
    }

    public static LangRegistryAccess getLangAccess() {
        return registries().localization;
    }

    public static int getPluginCount() {
        synchronized (PLUGINS) {
            return PLUGINS.size();
        }
    }

    public static long getGeneration() {
        return generation;
    }

    private static Registries registries() {
        Registries building = BUILDING.get();
        return building == null ? active : building;
    }

    private static final class Registries {
        private final PonderLocalization localization = new PonderLocalization();
        private final PonderSceneRegistry scenes = new PonderSceneRegistry(localization);
        private final PonderTagRegistry tags = new PonderTagRegistry(localization);
    }
}
