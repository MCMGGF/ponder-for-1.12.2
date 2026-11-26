package net.createmod.ponder.foundation.ui;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.PonderScene.OverlayEvent;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public final class PonderGuiSnapshotRegistry {

    private static final int TE_BASE_WIDTH = 198;
    private static final int TE_BASE_HEIGHT = 166;
    private static final int TE_PANEL_WIDTH = 100;
    private static final int TE_PANEL_HEIGHT = 92;
    private static final int TE_REDSTONE_PANEL_WIDTH = 112;
    private static final int TE_COMPOSITE_WIDTH = 276;
    private static final int TE_REDSTONE_COMPOSITE_WIDTH = 288;

    private static final Map<ResourceLocation, SnapshotProvider> SNAPSHOTS =
        new LinkedHashMap<ResourceLocation, SnapshotProvider>();
    private static final Set<ResourceLocation> DYNAMIC_SNAPSHOTS = new LinkedHashSet<ResourceLocation>();
    private static final int MAX_DYNAMIC_SNAPSHOTS = 256;
    private static final Map<SnapshotProvider, Boolean> EVENT_PROVIDERS = new java.util.WeakHashMap<SnapshotProvider, Boolean>();

    static {
        registerDefaults();
    }

    private PonderGuiSnapshotRegistry() {
    }

    public static void register(ResourceLocation id, Snapshot snapshot) {
        if (id == null || snapshot == null) {
            return;
        }
        registerProvider(id, declaredProvider(snapshot));
    }

    public static void registerProvider(ResourceLocation id, SnapshotProvider provider) {
        if (id == null || provider == null) {
            return;
        }
        SNAPSHOTS.put(id, provider);
    }

    public static void registerDynamicProvider(ResourceLocation id, SnapshotProvider provider) {
        if (id == null || provider == null) throw new IllegalArgumentException("Dynamic snapshot ID and provider are required");
        if (SNAPSHOTS.containsKey(id) && !DYNAMIC_SNAPSHOTS.contains(id)) throw new IllegalArgumentException("Cannot replace a built-in snapshot with a dynamic provider");
        resetProvider(SNAPSHOTS.put(id, provider));
        DYNAMIC_SNAPSHOTS.remove(id);
        DYNAMIC_SNAPSHOTS.add(id);
        while (DYNAMIC_SNAPSHOTS.size() > MAX_DYNAMIC_SNAPSHOTS) {
            Iterator<ResourceLocation> iterator = DYNAMIC_SNAPSHOTS.iterator();
            ResourceLocation oldest = iterator.next(); iterator.remove(); resetProvider(SNAPSHOTS.remove(oldest));
        }
    }

    static int dynamicProviderCount() { return DYNAMIC_SNAPSHOTS.size(); }

    public static Snapshot get(ResourceLocation id, float currentTick) {
        SnapshotProvider provider = id == null ? null : SNAPSHOTS.get(id);
        return provider == null ? null : provider.provide(currentTick);
    }

    public static Snapshot resolve(OverlayEvent event, float currentTick) {
        SnapshotProvider provider = providerFor(event);
        if (provider == null) return null;
        EVENT_PROVIDERS.put(provider, Boolean.TRUE);
        // Null means hidden, not a request to revive the last texture or another scene's provider.
        return provider.provide(currentTick);
    }

    /** Select the real provider without opening, decoding, downloading or allocating any snapshot resources. */
    public static SnapshotProvider providerFor(OverlayEvent event) {
        if (event == null) return null;
        SnapshotProvider local = event.getGuiSnapshotProvider();
        return local != null ? local : SNAPSHOTS.get(event.getGuiSnapshotId());
    }

    /** Reads only a registered immutable definition; never invokes an unknown provider or renderer. */
    public static Snapshot declaredSnapshotFor(OverlayEvent event) {
        SnapshotProvider provider = providerFor(event);
        return provider instanceof StaticSnapshotProvider ? ((StaticSnapshotProvider) provider).snapshot : null;
    }

    public static SnapshotProvider declaredProvider(Snapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("A static snapshot definition is required");
        return new StaticSnapshotProvider(snapshot);
    }

    /** Release one consumed provider without invalidating another scene's replacement under the same ID. */
    public static void releaseProvider(SnapshotProvider provider) {
        if (provider == null) return;
        try { resetProvider(provider); }
        finally {
            Iterator<SnapshotProvider> events = EVENT_PROVIDERS.keySet().iterator();
            while (events.hasNext()) if (events.next() == provider) events.remove();
            Iterator<ResourceLocation> ids = DYNAMIC_SNAPSHOTS.iterator();
            while (ids.hasNext()) {
                ResourceLocation id = ids.next();
                if (SNAPSHOTS.get(id) == provider) { ids.remove(); SNAPSHOTS.remove(id); }
            }
        }
    }

    public static ResourceLocation registerBlockGuiSnapshot(ResourceLocation blockId, int meta, int width, int height) {
        return registerBlockGuiSnapshot(blockId, meta, null, width, height);
    }

    public static ResourceLocation registerBlockGuiSnapshot(ResourceLocation blockId, int meta,
        NBTTagCompound tileNbt, int width, int height) {
        String nbtSuffix = tileNbt == null ? "" : "/nbt_" + Integer.toHexString(tileNbt.toString().hashCode());
        ResourceLocation snapshotId = Ponder.asResource("gui_snapshot/block/" + sanitizePath(blockId.getNamespace())
            + "/" + sanitizePath(blockId.getPath()) + "/" + Math.max(0, meta) + "/"
            + Math.max(1, width) + "x" + Math.max(1, height) + nbtSuffix);
        Snapshot snapshot = Snapshot.liveRenderer(Math.max(1, width), Math.max(1, height), true,
            SandboxTriggeredBlockGuiSnapshot.getOrCreate(blockId, Math.max(0, meta), tileNbt));
        registerDynamicProvider(snapshotId, currentTick -> snapshot);
        return snapshotId;
    }

    public static void releaseRuntimeState() {
        for (SnapshotProvider provider : EVENT_PROVIDERS.keySet()) resetProvider(provider);
        EVENT_PROVIDERS.clear();
        for (SnapshotProvider provider : SNAPSHOTS.values()) {
            resetProvider(provider);
        }
        for (ResourceLocation id : DYNAMIC_SNAPSHOTS) {
            SNAPSHOTS.remove(id);
        }
        DYNAMIC_SNAPSHOTS.clear();
        SandboxTriggeredBlockGuiSnapshot.clearCache();
        PlayerInventoryResolver.clearSnapshot();
    }

    private static void resetProvider(SnapshotProvider provider) {
        if (provider == null) {
            return;
        }
        try {
            if (provider instanceof ResettableSnapshotProvider) {
                ((ResettableSnapshotProvider) provider).reset(); return;
            }
            Snapshot snapshot = provider.provide(0.0F);
            if (snapshot != null && snapshot.renderer != null) {
                snapshot.renderer.reset();
            }
        } catch (RuntimeException | LinkageError failure) {
            Ponder.LOGGER.warn("Could not release a Ponder GUI preview", failure);
        }
    }

    private static String sanitizePath(String value) {
        String lower = value == null ? "unknown" : value.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '/') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        return builder.length() == 0 ? "unknown" : builder.toString();
    }

    private static void registerDefaults() {
        registerMinecraftSnapshots();
        registerThermalPanelAliases();
        registerThermalMachine("furnace");
        registerThermalMachine("charger");
        registerThermalMachine("insolator");
        registerThermalMachine("pulverizer");
        registerThermalMachine("sawmill");
        registerThermalMachine("smelter");
        registerThermalLiveOnly("brewer");
        registerThermalLiveOnly("centrifuge");
        registerThermalLiveOnly("compactor");
        registerThermalLiveOnly("crafter");
        registerThermalLiveOnly("crucible");
        registerThermalLiveOnly("enchanter");
        registerThermalLiveOnly("extruder");
        registerThermalLiveOnly("precipitator");
        registerThermalLiveOnly("refinery");
        registerThermalLiveOnly("transposer");
    }

    private static void registerMinecraftSnapshots() {
        register(Ponder.asResource("gui_snapshot/minecraft_furnace"),
            Snapshot.fullTexture(new ResourceLocation("minecraft", "textures/gui/container/furnace.png"), 176, 166));
        register(Ponder.asResource("gui_snapshot/minecraft_furnace_live"),
            Snapshot.liveRenderer(176, 166, true, EmbeddedGuiFurnaceSnapshot.INSTANCE));
        register(Ponder.asResource("gui_snapshot/minecraft_furnace_sandbox_live"),
            Snapshot.liveRenderer(176, 166, true, SandboxTriggeredBlockGuiSnapshot.MINECRAFT_FURNACE));
    }

    private static void registerThermalPanelAliases() {
        Snapshot augmentPanel = thermalPanel("augment", TE_PANEL_WIDTH);
        Snapshot configPanel = thermalPanel("config", TE_PANEL_WIDTH);
        Snapshot redstonePanel = thermalPanel("redstone", TE_REDSTONE_PANEL_WIDTH);

        register(Ponder.asResource("gui_snapshot/te_panel_augment"), augmentPanel);
        register(Ponder.asResource("gui_snapshot/te_panel_config"), configPanel);
        register(Ponder.asResource("gui_snapshot/te_panel_redstone"), redstonePanel);

        register(Ponder.asResource("gui_snapshot/te_furnace_panel_augment"), augmentPanel);
        register(Ponder.asResource("gui_snapshot/te_furnace_panel_config"), configPanel);
        register(Ponder.asResource("gui_snapshot/te_furnace_panel_redstone"), redstonePanel);
    }

    private static void registerThermalMachine(String machineName) {
        ResourceLocation baseId = Ponder.asResource("gui_snapshot/te_" + machineName + "_base");
        ResourceLocation augmentId = Ponder.asResource("gui_snapshot/te_" + machineName + "_augment");
        ResourceLocation configId = Ponder.asResource("gui_snapshot/te_" + machineName + "_config");
        ResourceLocation redstoneId = Ponder.asResource("gui_snapshot/te_" + machineName + "_redstone");

        Snapshot base = thermalBase(machineName);
        Snapshot augment = thermalComposite(machineName, "augment", TE_COMPOSITE_WIDTH);
        Snapshot config = thermalComposite(machineName, "config", TE_COMPOSITE_WIDTH);
        Snapshot redstone = thermalComposite(machineName, "redstone", TE_REDSTONE_COMPOSITE_WIDTH);

        register(baseId, base);
        register(augmentId, augment);
        register(configId, config);
        register(redstoneId, redstone);
        registerThermalLive(machineName);

        registerProvider(Ponder.asResource("gui_snapshot/te_" + machineName + "_cycle"), currentTick -> {
            int frame = ((int) currentTick / 20) % 4;
            if (frame == 1) {
                return config;
            }
            if (frame == 2) {
                return augment;
            }
            if (frame == 3) {
                return redstone;
            }
            return base;
        });
    }

    private static Snapshot thermalBase(String machineName) {
        return Snapshot.liveRenderer(TE_BASE_WIDTH, TE_BASE_HEIGHT, true, ThermalGuiSnapshot.base(machineName));
    }

    private static Snapshot thermalPanel(String panel, int width) {
        return Snapshot.liveRenderer(width, TE_PANEL_HEIGHT, true,
            ThermalGuiSnapshot.panel("furnace", panel, width, TE_PANEL_HEIGHT));
    }

    private static Snapshot thermalComposite(String machineName, String panel, int width) {
        return Snapshot.liveRenderer(width, TE_BASE_HEIGHT, true, ThermalGuiSnapshot.composite(machineName, panel, width));
    }

    private static void registerThermalLive(String machineName) {
        String suffix = Character.toUpperCase(machineName.charAt(0)) + machineName.substring(1);
        String guiClass = "cofh.thermalexpansion.gui.client.machine.Gui" + suffix;
        String tileClass = "cofh.thermalexpansion.block.machine.Tile" + suffix;
        register(Ponder.asResource("gui_snapshot/te_" + machineName + "_live"),
            Snapshot.liveRenderer(TE_BASE_WIDTH, TE_BASE_HEIGHT, true,
                new EmbeddedReflectiveGuiSnapshot(guiClass, tileClass)));
        if ("furnace".equals(machineName)) {
            register(Ponder.asResource("gui_snapshot/te_furnace_sandbox_live"),
                Snapshot.liveRenderer(TE_BASE_WIDTH, TE_BASE_HEIGHT, true,
                    SandboxTriggeredBlockGuiSnapshot.THERMAL_FURNACE));
        }
        registerThermalPanelLive(machineName, guiClass, tileClass, "augmentTab", "augment", TE_PANEL_WIDTH,
            TE_PANEL_HEIGHT);
        registerThermalPanelLive(machineName, guiClass, tileClass, "configTab", "config", TE_PANEL_WIDTH,
            TE_PANEL_HEIGHT);
        registerThermalPanelLive(machineName, guiClass, tileClass, "redstoneTab", "redstone",
            TE_REDSTONE_PANEL_WIDTH, TE_PANEL_HEIGHT);
    }

    private static void registerThermalLiveOnly(String machineName) {
        register(Ponder.asResource("gui_snapshot/te_" + machineName + "_base"), thermalBase(machineName));
        registerThermalLive(machineName);
    }

    private static void registerThermalPanelLive(String machineName, String guiClass, String tileClass,
        String tabFieldName, String panelName, int width, int height) {
        register(Ponder.asResource("gui_snapshot/te_" + machineName + "_panel_" + panelName + "_live"),
            Snapshot.liveRenderer(width, height, true,
                new EmbeddedReflectiveTabGuiSnapshot(guiClass, tileClass, tabFieldName, width, height)));
    }

    public interface SnapshotProvider {
        Snapshot provide(float currentTick);

        default boolean waitingForRecording() { return false; }
    }

    private static final class StaticSnapshotProvider implements SnapshotProvider {
        private final Snapshot snapshot;
        private StaticSnapshotProvider(Snapshot snapshot) { this.snapshot = snapshot; }
        @Override public Snapshot provide(float currentTick) { return snapshot; }
    }

    public interface ResettableSnapshotProvider extends SnapshotProvider {
        void reset();
    }

    public interface SnapshotRenderer {
        void render(int x, int y, int width, int height, float currentTick, float fade);

        default void reset() {
        }
    }

    public static final class Snapshot {
        public final ResourceLocation texture;
        public final int u;
        public final int v;
        public final int regionWidth;
        public final int regionHeight;
        public final int textureWidth;
        public final int textureHeight;
        public final int displayWidth;
        public final int displayHeight;
        public final boolean framed;
        public final SnapshotRenderer renderer;

        public Snapshot(ResourceLocation texture, int u, int v, int regionWidth, int regionHeight, int textureWidth,
            int textureHeight, int displayWidth, int displayHeight, boolean framed, SnapshotRenderer renderer) {
            this.texture = texture;
            this.u = u;
            this.v = v;
            this.regionWidth = regionWidth;
            this.regionHeight = regionHeight;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.displayWidth = displayWidth;
            this.displayHeight = displayHeight;
            this.framed = framed;
            this.renderer = renderer;
        }

        public static Snapshot fullTexture(ResourceLocation texture, int width, int height) {
            return new Snapshot(texture, 0, 0, width, height, width, height, width, height, true, null);
        }

        public static Snapshot liveRenderer(int width, int height, boolean framed, SnapshotRenderer renderer) {
            return new Snapshot(null, 0, 0, width, height, width, height, width, height, framed, renderer);
        }
    }
}
