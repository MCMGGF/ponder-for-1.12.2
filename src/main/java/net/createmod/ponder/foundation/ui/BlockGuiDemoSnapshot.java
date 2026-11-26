package net.createmod.ponder.foundation.ui;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.client.PonderNativeGuiScreens;
import net.createmod.ponder.foundation.content.mmce.MmcePreviewSafety;
import net.createmod.ponder.foundation.ui.BlockGuiDemoDefinition.Insets;
import net.createmod.ponder.foundation.ui.BlockGuiDemoDefinition.Screen;
import net.createmod.ponder.foundation.ui.BlockGuiDemoDefinition.Stack;
import net.createmod.ponder.foundation.ui.BlockGuiDemoDefinition.State;
import net.createmod.ponder.foundation.ui.GuiDemoDefinition.Pointer;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.ResettableSnapshotProvider;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.Snapshot;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.SnapshotProvider;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.SnapshotRenderer;
import net.createmod.ponder.foundation.ui.sandbox.RecordedGuiPlayback.PendingPreview;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IInteractionObject;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import org.lwjgl.opengl.GL11;

/** Renders a detached block screen, with optional scene-authored container updates. */
final class BlockGuiDemoSnapshot implements ResettableSnapshotProvider, SnapshotRenderer, PendingPreview {
    private static final BlockPos POS = new BlockPos(0, 64, 0);
    private static final int OUTSIDE_GUI = -4096;
    private static final int PREVIEW_FOOTER_HEIGHT = 26;

    private final BlockGuiDemoDefinition definition;
    private final int startTick;
    private final boolean pendingPreview;
    private final Map<Screen, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Set<Screen> unavailable = new HashSet<>();
    private Screen active;
    private DetachedPonderWorld world;
    private EntityPlayerSP player;
    private GuiContainer gui;
    private Snapshot pendingSnapshot;
    private String pendingStatusKey;
    private State applied;
    private float appliedTick;

    private BlockGuiDemoSnapshot(BlockGuiDemoDefinition definition, int startTick, boolean pendingPreview) {
        this.definition = definition;
        this.startTick = startTick;
        this.pendingPreview = pendingPreview;
    }

    static SnapshotProvider provider(BlockGuiDemoDefinition definition, int startTick) {
        return new BlockGuiDemoSnapshot(definition, startTick, false);
    }

    static BlockGuiDemoSnapshot preview(BlockGuiDemoDefinition definition, int startTick) {
        return new BlockGuiDemoSnapshot(definition, startTick, true);
    }

    @Override
    public Snapshot previewSnapshot(float tick, String statusKey) {
        if (!visibleAt(tick)) return null;
        prepare(startTick);
        Screen main = definition.screenAt(0F);
        if (!main.equals(active)) return null;

        if (pendingSnapshot == null || !statusKey.equals(pendingStatusKey)) {
            Snapshot nativeSnapshot = snapshotFor(main);
            pendingStatusKey = statusKey;
            pendingSnapshot = Snapshot.liveRenderer(nativeSnapshot.displayWidth,
                nativeSnapshot.displayHeight + PREVIEW_FOOTER_HEIGHT, true,
                (x, y, width, height, ignored, fade) -> renderPending(main, statusKey, x, y, width, height, fade));
        }
        return pendingSnapshot;
    }

    private void renderPending(Screen main, String statusKey, int x, int y, int width, int height, float fade) {
        int alpha = Math.min(255, Math.round(255 * fade));
        if (alpha < 4 || width <= 0 || height <= 0 || !main.equals(active)) return;
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        int footerHeight = Math.min(height, Math.max(PREVIEW_FOOTER_HEIGHT, 2 * font.FONT_HEIGHT + 6));
        int footerY = y + height - footerHeight;
        if (footerY > y) {
            float itemZ = mc.getRenderItem().zLevel;
            try {
                PreviewNativeRenderGuard.run(() -> {
                    try (NativeGuiPreviewScope scope = new NativeGuiPreviewScope(mc, player)) {
                        drawScreen(main, x, y, width, footerY - y, 0F, fade);
                    }
                });
            } catch (RuntimeException | LinkageError failure) {
                markUnavailable(main, failure);
            } finally {
                mc.getRenderItem().zLevel = itemZ;
            }
        }
        Gui.drawRect(x, footerY, x + width, y + height, alpha << 24 | 0x151D23);
        drawPendingLabel(font, "ponder.sandbox.preview", x, footerY + 2, width, y + height, alpha);
        drawPendingLabel(font, statusKey, x, footerY + font.FONT_HEIGHT + 4, width, y + height, alpha);
    }

    private static void drawPendingLabel(FontRenderer font, String key, int x, int y, int width, int bottom, int alpha) {
        if (width <= 8 || y + font.FONT_HEIGHT > bottom) return;
        PreviewTextLayout label = PreviewTextLayout.centered(font, I18n.format(key), x + 4, x + width - 4);
        font.drawStringWithShadow(label.text, label.x, y, alpha << 24 | 0xFFFFFF);
    }

    @Override
    public Snapshot provide(float tick) {
        return visibleAt(tick) ? snapshotFor(definition.screenAt(tick - startTick)) : null;
    }

    private boolean visibleAt(float tick) {
        return Float.isFinite(tick) && tick >= startTick;
    }

    /** Resolves native dimensions before the preview places and clips the GUI. */
    void prepare(float tick) {
        Minecraft mc = Minecraft.getMinecraft();
        if (visibleAt(tick) && mc != null && mc.isCallingFromMinecraftThread()
            && mc.getRenderItem() != null && mc.fontRenderer != null) {
            float localTick = tick - startTick;
            prepareScreen(mc, definition.screenAt(localTick), localTick);
        }
    }

    private void prepareScreen(Minecraft mc, Screen screen, float localTick) {
        if (screen.equals(active) && !needsRewindReset(localTick)) {
            return;
        }
        discardScreen();
        if (unavailable.contains(screen)) {
            return;
        }
        float itemZ = mc.getRenderItem().zLevel;
        try {
            PreviewNativeRenderGuard.run(() -> open(mc, screen));
            active = screen;
        } catch (RuntimeException | LinkageError failure) {
            markUnavailable(screen, failure);
        } finally {
            mc.getRenderItem().zLevel = itemZ;
        }
    }

    private boolean needsRewindReset(float localTick) {
        if (applied == null || localTick >= appliedTick) {
            return false;
        }
        // Recreate the detached container to restore fields with no generic read method.
        State state = definition.stateAt(localTick);
        return !state.slots().keySet().containsAll(applied.slots().keySet())
            || !state.properties().keySet().containsAll(applied.properties().keySet());
    }

    @Override
    public void render(int x, int y, int width, int height, float tick, float fade) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || !mc.isCallingFromMinecraftThread() || mc.getRenderItem() == null || mc.fontRenderer == null
            || width < 1 || height < 1 || !visibleAt(tick) || !(fade >= 4F / 255)) {
            return;
        }
        float localTick = tick - startTick;
        Screen screen = definition.screenAt(localTick);
        prepareScreen(mc, screen, localTick);
        if (screen.equals(active)) {
            try {
                PreviewNativeRenderGuard.run(() -> draw(mc, screen, x, y, width, height, localTick, fade));
                return;
            } catch (RuntimeException | LinkageError failure) {
                markUnavailable(screen, failure);
            }
        }
        SandboxTriggeredBlockGuiSnapshot.renderUnavailable(x, y, width, height, definition.block().toString());
    }

    private void draw(Minecraft mc, Screen screen, int x, int y, int width, int height, float localTick, float fade) {
        State state = definition.stateAt(localTick);
        float itemZ = mc.getRenderItem().zLevel;
        try (NativeGuiPreviewScope ignored = new NativeGuiPreviewScope(mc, player)) {
            if (!state.equals(applied)) {
                apply(state);
                applied = state;
            }
            appliedTick = localTick;
            drawScreen(screen, x, y, width, height, localTick, fade);
        } finally {
            mc.getRenderItem().zLevel = itemZ;
        }
    }

    private void drawScreen(Screen screen, int x, int y, int width, int height, float localTick, float fade) {
        Snapshot snapshot = snapshotFor(screen);
        int captureWidth = snapshot.displayWidth;
        int captureHeight = snapshot.displayHeight;
        float scale = Math.min(width / (float) captureWidth, height / (float) captureHeight);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.translate(x + (width - captureWidth * scale) / 2F,
            y + (height - captureHeight * scale) / 2F, 0);
        GlStateManager.scale(scale, scale, 1);
        // The scene layout clips to the full capture. Native widgets and pointer targets still use the base GUI.
        Insets capture = screen.capture();
        GlStateManager.translate(capture.left() - gui.getGuiLeft(), capture.top() - gui.getGuiTop(), 0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.color(1, 1, 1, 1);
        gui.drawScreen(OUTSIDE_GUI, OUTSIDE_GUI, localTick - (float) Math.floor(localTick));
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.disableAlpha();
        GlStateManager.translate(gui.getGuiLeft(), gui.getGuiTop(), 0);
        Pointer pointer = definition.pointerAt(localTick, this::slotCenter);
        if (pointer != null) {
            GuiDemoSnapshot.drawPointer(pointer, Math.min(1, fade));
        }
    }

    private void open(Minecraft mc, Screen screen) {
        Block block = Block.REGISTRY.getObject(definition.block());
        if (block == null || !definition.block().equals(block.getRegistryName())
            || MmcePreviewSafety.isNativeControllerBlock(block.getClass())) {
            throw new IllegalArgumentException("Unsupported preview block " + definition.block());
        }
        world = new DetachedPonderWorld(pendingPreview);
        world.putBlock(POS, block.getStateFromMeta(definition.meta()), definition.tileNbt());
        player = NativeGuiPreviewScope.player(mc, world);
        player.setPosition(POS.getX() + 0.5, POS.getY() + 0.5, POS.getZ() + 0.5);
        try (NativeGuiPreviewScope scope = new NativeGuiPreviewScope(mc, player)) {
            if (screen.factory() != null) {
                PonderNativeGuiScreens.Factory factory = PonderNativeGuiScreens.get(screen.factory());
                if (factory == null) {
                    throw new IllegalArgumentException("Unknown native GUI factory " + screen.factory());
                }
                gui = factory.create(world, POS, player);
            } else {
                gui = screen.guiId() == null ? vanillaScreen(scope) : forgeScreen(screen);
            }
            if (gui == null) {
                throw new IllegalArgumentException("Block did not expose a client GuiContainer");
            }
            gui.mc = mc;
            gui.itemRender = mc.getRenderItem();
            gui.fontRenderer = mc.fontRenderer;
            gui.setGuiSize(gui.getXSize(), gui.getYSize());
            gui.initGui();
            int width = gui.getXSize();
            int height = gui.getYSize();
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException("Native GUI has invalid dimensions");
            }
            snapshots.put(screen, captureSnapshot(screen, width, height));
        }
    }

    private Snapshot snapshotFor(Screen screen) {
        return snapshots.computeIfAbsent(screen, key -> captureSnapshot(key, 176, 166));
    }

    private Snapshot captureSnapshot(Screen screen, int guiWidth, int guiHeight) {
        Insets capture = screen.capture();
        int width = Math.addExact(guiWidth, capture.left() + capture.right());
        int height = Math.addExact(guiHeight, capture.top() + capture.bottom());
        return Snapshot.liveRenderer(width, height, false, this);
    }

    private GuiContainer vanillaScreen(NativeGuiPreviewScope scope) {
        TileEntity tile = world.getTileEntity(POS);
        if (tile instanceof IInventory inventory) {
            player.displayGUIChest(inventory);
        } else if (tile instanceof IInteractionObject interaction) {
            player.displayGui(interaction);
        }
        return scope.captured();
    }

    private GuiContainer forgeScreen(Screen screen) {
        ModContainer mod = Loader.instance().getIndexedModList().get(screen.guiModId());
        if (mod == null) {
            return null;
        }
        Object result = NetworkRegistry.INSTANCE.getLocalGuiContainer(mod, player, screen.guiId(), world,
            POS.getX(), POS.getY(), POS.getZ());
        return result instanceof GuiContainer container ? container : null;
    }

    private void apply(State state) {
        for (Map.Entry<Integer, Stack> entry : state.slots().entrySet()) {
            int slot = entry.getKey();
            if (slot >= gui.inventorySlots.inventorySlots.size()) {
                throw new IllegalArgumentException("Native GUI has no slot " + slot);
            }
            ItemStack desired = itemStack(entry.getValue());
            if (!ItemStack.areItemStacksEqual(gui.inventorySlots.getSlot(slot).getStack(), desired)) {
                gui.inventorySlots.putStackInSlot(slot, desired);
            }
        }
        for (Map.Entry<Integer, Integer> property : state.properties().entrySet()) {
            if (applied == null || !property.getValue().equals(applied.properties().get(property.getKey()))) {
                gui.inventorySlots.updateProgressBar(property.getKey(), property.getValue());
            }
        }
    }

    private static ItemStack itemStack(Stack definition) {
        if (definition == null) {
            return ItemStack.EMPTY;
        }
        Item item = Item.REGISTRY.getObject(definition.item());
        if (item == null || !definition.item().equals(item.getRegistryName())) {
            throw new IllegalArgumentException("Unknown native GUI item " + definition.item());
        }
        ItemStack stack = new ItemStack(item, definition.count(), definition.meta());
        stack.setTagCompound(definition.nbt());
        return stack;
    }

    private Pointer slotCenter(int index) {
        if (index >= gui.inventorySlots.inventorySlots.size()) {
            return null;
        }
        Slot slot = gui.inventorySlots.getSlot(index);
        return new Pointer(slot.xPos + 8, slot.yPos + 8, false);
    }

    @Override
    public void reset() {
        discardScreen();
        snapshots.clear();
        unavailable.clear();
    }

    private void markUnavailable(Screen screen, Throwable failure) {
        discardScreen();
        if (unavailable.add(screen)) {
            Ponder.LOGGER.warn("Could not render Ponder native GUI screen {} for {}", screen.name(), definition.block(), failure);
        }
    }

    private void discardScreen() {
        active = null;
        gui = null;
        player = null;
        pendingSnapshot = null;
        pendingStatusKey = null;
        applied = null;
        if (world != null) {
            world.clear();
            world = null;
        }
    }
}
