package net.createmod.ponder.authoring.capture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.createmod.ponder.authoring.practice.PracticeDefinition.Menu;
import net.minecraft.client.gui.GuiHopper;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.client.player.inventory.ContainerLocalMenu;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.inventory.ContainerHopper;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.inventory.SlotFurnaceFuel;
import net.minecraft.inventory.SlotFurnaceOutput;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityHopper;

/** Exact vanilla types and audited optional adapters; never performs real container interactions. */
public final class VanillaMenuCapture {
    private VanillaMenuCapture() { }
    public static boolean supports(GuiScreen screen, Container container) {
        return screen != null && container != null && (screen.getClass() == GuiCrafting.class && container.getClass() == ContainerWorkbench.class
            || screen.getClass() == GuiFurnace.class && container.getClass() == ContainerFurnace.class
            || screen.getClass() == GuiChest.class && container.getClass() == ContainerChest.class
            || screen.getClass() == GuiHopper.class && container.getClass() == ContainerHopper.class
            || FastWorkbenchCaptureAdapter.supports(screen, container)
            || HammerFurnaceCaptureAdapter.supports(screen, container));
    }
    public static CaptureDraft capture(GuiScreen screen, Container container, InventoryPlayer playerInventory) {
        if (!supports(screen, container) || !(screen instanceof GuiContainer) || ((GuiContainer) screen).inventorySlots != container)
            throw new IllegalArgumentException("Capture requires the exact supported GUI and its already open container");
        return captureVerified(container, playerInventory, HammerFurnaceCaptureAdapter.resolve(screen, container));
    }
    public static CaptureDraft capture(Container container, InventoryPlayer playerInventory) {
        return captureVerified(container, playerInventory, null);
    }
    private static CaptureDraft captureVerified(Container container, InventoryPlayer playerInventory, HammerFurnaceCaptureAdapter.Access hammer) {
        if (container == null || playerInventory == null) throw new IllegalArgumentException("An already open vanilla container is required");
        FastWorkbenchCaptureAdapter.Access fast = FastWorkbenchCaptureAdapter.resolve(container);
        Menu menu;
        if (container.getClass() == ContainerWorkbench.class || fast != null) menu = Menu.WORKBENCH;
        else if (container.getClass() == ContainerFurnace.class) menu = Menu.FURNACE;
        else if (container.getClass() == ContainerHopper.class) menu = Menu.HOPPER;
        else if (container.getClass() == ContainerChest.class) {
            int count = container.inventorySlots.size() - 36;
            if (count != 27 && count != 54) throw new IllegalArgumentException("Only complete 27/54-slot vanilla chests can be captured");
            menu = count == 54 ? Menu.CHEST_LARGE : Menu.CHEST;
        } else throw new IllegalArgumentException("This container has no read-only vanilla capture adapter");
        if (menu == Menu.WORKBENCH) {
            ContainerWorkbench workbench = (ContainerWorkbench) container;
            if (workbench.craftMatrix == null || workbench.craftMatrix.getClass() != InventoryCrafting.class
                || workbench.craftMatrix.getWidth() != 3 || workbench.craftMatrix.getHeight() != 3
                || workbench.craftMatrix.getSizeInventory() != 9 || workbench.craftResult == null
                || workbench.craftResult.getClass() != InventoryCraftResult.class || workbench.craftResult.getSizeInventory() != 1)
                throw new IllegalArgumentException("Only the exact vanilla 3x3 crafting matrix and one result slot can be read");
        }
        if (container.inventorySlots.size() != menu.slots + 36) throw new IllegalArgumentException("Vanilla container slot layout was replaced");
        if (container.inventorySlots.get(0) == null) throw new IllegalArgumentException("Missing vanilla container slot");
        IInventory inventory = container.inventorySlots.get(0).inventory;
        for (int index = 0; index < menu.slots + 36; index++) {
            Slot slot = container.inventorySlots.get(index);
            Class<?> expectedSlot = menu == Menu.WORKBENCH && index == 0 ? SlotCrafting.class
                : menu == Menu.FURNACE && index == 1 ? SlotFurnaceFuel.class
                : menu == Menu.FURNACE && index == 2 ? SlotFurnaceOutput.class : Slot.class;
            boolean knownSlot = slot != null && (fast != null && menu == Menu.WORKBENCH && index == 0
                ? fast.outputSlot(slot) : slot.getClass() == expectedSlot);
            if (!knownSlot) throw new IllegalArgumentException("A custom slot cannot be captured without an explicit adapter");
            if (index >= menu.slots) {
                int playerIndex = index - menu.slots, expected = playerIndex < 27 ? playerIndex + 9 : playerIndex - 27;
                if (slot.inventory != playerInventory || slot.getSlotIndex() != expected)
                    throw new IllegalArgumentException("Player inventory slot identity changed; nothing was exported");
                continue;
            }
            IInventory expected = inventory;
            if (menu == Menu.WORKBENCH) expected = index == 0 ? ((ContainerWorkbench) container).craftResult : ((ContainerWorkbench) container).craftMatrix;
            int expectedIndex = menu == Menu.WORKBENCH && index > 0 ? index - 1 : index;
            if (slot.inventory == playerInventory || slot.inventory != expected || slot.getSlotIndex() != expectedIndex || !safeInventory(slot.inventory, menu))
                throw new IllegalArgumentException("A custom inventory cannot be read by the vanilla capture adapter");
        }
        List<ItemStack> slots = new ArrayList<>();
        for (int index = 0; index < menu.slots; index++) {
            Slot slot = container.inventorySlots.get(index);
            slots.add(slot.inventory.getStackInSlot(slot.getSlotIndex()));
        }
        Map<String, Integer> data = new LinkedHashMap<>();
        if (menu == Menu.FURNACE) {
            String[] names = {"burnTime", "fuelTime", "cookTime", "cookTotal"};
            for (int index = 0; index < names.length; index++) data.put(names[index], inventory.getField(index));
        }
        String sourceGuiId = hammer != null ? HammerFurnaceCaptureAdapter.SOURCE_GUI_ID : fast != null ? FastWorkbenchCaptureAdapter.SOURCE_GUI_ID : menu == Menu.WORKBENCH ? "minecraft:crafting_table"
            : inventory.getClass() == ContainerLocalMenu.class ? ((ContainerLocalMenu) inventory).getGuiID()
            : menu == Menu.CHEST || menu == Menu.CHEST_LARGE
                ? inventory.getClass() == InventoryBasic.class ? "minecraft:container" : "minecraft:chest"
                : menu == Menu.FURNACE ? "minecraft:furnace" : "minecraft:hopper";
        return hammer != null ? new CaptureDraft(menu, slots, data, sourceGuiId, hammer.metadata())
            : fast == null ? new CaptureDraft(menu, slots, data, sourceGuiId)
            : new CaptureDraft(menu, slots, data, sourceGuiId, fast.metadata());
    }
    private static boolean safeInventory(IInventory inventory, Menu menu) {
        if (inventory == null) return false;
        Class<?> type = inventory.getClass();
        if (menu == Menu.WORKBENCH) return type == InventoryCrafting.class || type == InventoryCraftResult.class;
        if (type == ContainerLocalMenu.class) {
            if (inventory.getSizeInventory() != menu.slots) return false;
            String id = ((ContainerLocalMenu) inventory).getGuiID();
            return menu == Menu.FURNACE && "minecraft:furnace".equals(id) || menu == Menu.HOPPER && "minecraft:hopper".equals(id)
                || (menu == Menu.CHEST || menu == Menu.CHEST_LARGE) && "minecraft:chest".equals(id);
        }
        return type == InventoryBasic.class && (menu == Menu.CHEST || menu == Menu.CHEST_LARGE) && inventory.getSizeInventory() == menu.slots
            || type == TileEntityFurnace.class && menu == Menu.FURNACE || type == TileEntityChest.class && menu == Menu.CHEST
            || type == TileEntityHopper.class && menu == Menu.HOPPER;
    }
}
