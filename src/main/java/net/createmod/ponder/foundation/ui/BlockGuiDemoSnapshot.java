package net.createmod.ponder.foundation.ui;

import java.util.Map;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.content.mmce.MmcePreviewSafety;
import net.createmod.ponder.foundation.ui.BlockGuiDemoDefinition.Insets;
import net.createmod.ponder.foundation.ui.BlockGuiDemoDefinition.Stack;
import net.createmod.ponder.foundation.ui.BlockGuiDemoDefinition.State;
import net.createmod.ponder.foundation.ui.GuiDemoDefinition.Pointer;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.ResettableSnapshotProvider;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.Snapshot;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.SnapshotProvider;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.SnapshotRenderer;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
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

/** Renders a captured block screen using only scene-authored container updates. */
final class BlockGuiDemoSnapshot implements ResettableSnapshotProvider, SnapshotRenderer {
    private static final BlockPos POS = new BlockPos(0, 64, 0);
    private static final int OUTSIDE_GUI = -4096;

    private final BlockGuiDemoDefinition definition;
    private final Insets capture;
    private final int startTick;
    private final Snapshot fallback;
    private Snapshot snapshot;
    private DetachedPonderWorld world;
    private EntityPlayerSP player;
    private GuiContainer gui;
    private State applied;
    private boolean unavailable;

    private BlockGuiDemoSnapshot(BlockGuiDemoDefinition definition, int startTick) {
        this.definition = definition;
        this.capture = definition.capture();
        this.startTick = startTick;
        fallback = captureSnapshot(176, 166);
        snapshot = fallback;
    }

    static SnapshotProvider provider(BlockGuiDemoDefinition definition, int startTick) {
        return new BlockGuiDemoSnapshot(definition, startTick);
    }

    @Override
    public Snapshot provide(float tick) {
        return Float.isFinite(tick) && tick >= startTick ? snapshot : null;
    }

    @Override
    public void render(int x, int y, int width, int height, float tick, float fade) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || width < 1 || height < 1 || provide(tick) == null || !(fade >= 4F / 255)) {
            return;
        }
        if (!unavailable) {
            try {
                PreviewNativeRenderGuard.run(() -> draw(mc, x, y, width, height, tick - startTick, fade));
                return;
            } catch (RuntimeException | LinkageError failure) {
                discardScreen();
                snapshot = fallback;
                unavailable = true;
                Ponder.LOGGER.warn("Could not render Ponder native GUI for {}", definition.block(), failure);
            }
        }
        SandboxTriggeredBlockGuiSnapshot.renderUnavailable(x, y, width, height, definition.block().toString());
    }

    private void draw(Minecraft mc, int x, int y, int width, int height, float localTick, float fade) {
        State state = definition.stateAt(localTick);
        if (applied != null && (!state.slots().keySet().containsAll(applied.slots().keySet())
            || !state.properties().keySet().containsAll(applied.properties().keySet()))) {
            // Recreate the original container to restore fields with no generic read method.
            discardScreen();
        }
        if (gui == null) {
            open(mc);
        }
        float itemZ = mc.getRenderItem().zLevel;
        try (NativeGuiPreviewScope ignored = new NativeGuiPreviewScope(mc, player)) {
            if (!state.equals(applied)) {
                apply(state);
                applied = state;
            }
            int captureWidth = snapshot.displayWidth;
            int captureHeight = snapshot.displayHeight;
            float scale = Math.min(width / (float) captureWidth, height / (float) captureHeight);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.translate(x + (width - captureWidth * scale) / 2F,
                y + (height - captureHeight * scale) / 2F, 0);
            GlStateManager.scale(scale, scale, 1);
            // The scene layout clips to the full capture. Native widgets and pointer targets still use the base GUI.
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
        } finally {
            mc.getRenderItem().zLevel = itemZ;
        }
    }

    private void open(Minecraft mc) {
        Block block = Block.REGISTRY.getObject(definition.block());
        if (block == null || !definition.block().equals(block.getRegistryName())
            || MmcePreviewSafety.isNativeControllerBlock(block.getClass())) {
            throw new IllegalArgumentException("Unsupported preview block " + definition.block());
        }
        world = new DetachedPonderWorld();
        world.putBlock(POS, block.getStateFromMeta(definition.meta()), definition.tileNbt());
        player = NativeGuiPreviewScope.player(mc, world);
        player.setPosition(POS.getX() + 0.5, POS.getY() + 0.5, POS.getZ() + 0.5);
        try (NativeGuiPreviewScope scope = new NativeGuiPreviewScope(mc, player)) {
            gui = definition.guiId() == null ? vanillaScreen(scope) : forgeScreen();
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
            snapshot = captureSnapshot(width, height);
        }
    }

    private Snapshot captureSnapshot(int guiWidth, int guiHeight) {
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

    private GuiContainer forgeScreen() {
        ModContainer mod = Loader.instance().getIndexedModList().get(definition.guiModId());
        if (mod == null) {
            return null;
        }
        Object result = NetworkRegistry.INSTANCE.getLocalGuiContainer(mod, player, definition.guiId(), world,
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
        snapshot = fallback;
        unavailable = false;
    }

    private void discardScreen() {
        gui = null;
        player = null;
        applied = null;
        if (world != null) {
            world.clear();
            world = null;
        }
    }
}
