package net.createmod.ponder.authoring.capture;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.gson.JsonObject;
import net.createmod.ponder.authoring.practice.PracticeDefinition;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Menu;
import net.createmod.ponder.authoring.practice.PracticeJson;
import net.createmod.ponder.authoring.AuthoringEdits;
import net.createmod.ponder.authoring.AuthoringSession;
import net.createmod.ponder.foundation.ui.DetachedPonderWorld;
import net.minecraft.client.player.inventory.ContainerLocalMenu;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.inventory.ContainerHopper;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VanillaMenuCaptureTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    private InventoryPlayer player() { return new InventoryPlayer(null); }
    private InventoryBasic chest(int size) { return new InventoryBasic("captured", false, size); }

    @Test void singleAndDoubleChestsCaptureAllMenuSlotsWithoutExportingThePlayerTail() {
        for (int size : Arrays.asList(27, 54)) {
            InventoryPlayer player = player(); InventoryBasic chest = chest(size);
            chest.setInventorySlotContents(size - 1, new ItemStack(Items.DIAMOND, 7));
            player.mainInventory.set(0, new ItemStack(Items.EMERALD, 63));
            CaptureDraft captured = VanillaMenuCapture.capture(new ContainerChest(player, chest, null), player);
            assertEquals(size, captured.getSlots().size()); assertEquals(7, captured.getSlots().get(size - 1).count);
            PracticeDefinition definition = PracticeJson.fromStep(captured.toStep());
            assertEquals(size + 36, definition.getSlotCount()); assertTrue(definition.getInitialSlots().get(size).isEmpty());
            assertFalse(captured.toStep().toString().contains("emerald"));
            assertEquals(size == 54 ? Menu.CHEST_LARGE : Menu.CHEST, definition.getMenu());
            assertEquals("minecraft:container", captured.toStep().getAsJsonObject("capture").get("sourceGuiId").getAsString());
        }
    }
    @Test void actualClientLocalSingleAndDoubleChestsKeepTheirNativeGuiIdAndFilterItemNbt() {
        for (int size : Arrays.asList(27, 54)) {
            InventoryPlayer player = player();
            ContainerLocalMenu inventory = new ContainerLocalMenu("minecraft:chest", new TextComponentString("chest"), size);
            ItemStack source = new ItemStack(Blocks.WOOL, 7, 14); NBTTagCompound tag = new NBTTagCompound();
            tag.setString("OwnerUUID", "not-exported"); source.setTagCompound(tag);
            inventory.setInventorySlotContents(size - 1, source); player.mainInventory.set(0, new ItemStack(Items.EMERALD, 63));
            CaptureDraft capture = VanillaMenuCapture.capture(new ContainerChest(player, inventory, null), player);
            assertEquals(size, capture.getSlots().size()); assertEquals(7, capture.getSlots().get(size - 1).count);
            assertEquals(14, capture.getSlots().get(size - 1).meta); assertEquals(1, capture.getFilteredTagCount());
            assertEquals("minecraft:chest", capture.getSourceGuiId());
            JsonObject step = capture.toStep(), metadata = step.getAsJsonObject("capture");
            assertEquals("minecraft:chest", metadata.get("sourceGuiId").getAsString());
            assertEquals(size == 54 ? "minecraft:generic_9x6" : "minecraft:generic_9x3", metadata.get("virtualMenuAdapter").getAsString());
            assertFalse(step.toString().contains("not-exported")); assertFalse(step.toString().contains("emerald"));
            PracticeDefinition definition = PracticeJson.fromStep(step);
            assertEquals(size + 36, definition.getSlotCount()); assertTrue(definition.getInitialSlots().get(size).isEmpty());
            assertFalse(capture.getSlots().get(size - 1).toStack().hasTagCompound()); assertEquals(tag, source.getTagCompound());
            source.setCount(1); assertEquals(7, capture.getSlots().get(size - 1).count);
        }
    }
    @Test void localChestRejectsUnknownGenericAndCrossMenuIdsAndCustomInventoryCallbacks() {
        InventoryPlayer player = player();
        for (int size : Arrays.asList(27, 54)) {
            for (String id : Arrays.asList("thirdparty:machine", "minecraft:container", "minecraft:furnace", "minecraft:hopper",
                "minecraft:generic_9x3", "minecraft:generic_9x6", "minecraft:CHEST")) {
                ContainerLocalMenu unknown = new ContainerLocalMenu(id, new TextComponentString("unknown"), size);
                assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(new ContainerChest(player, unknown, null), player), id);
            }
        }
        AtomicInteger reads = new AtomicInteger();
        ContainerLocalMenu custom = new ContainerLocalMenu("minecraft:chest", new TextComponentString("custom"), 54) {
            @Override public String getGuiID() { reads.incrementAndGet(); throw new AssertionError("Custom GUI callback"); }
            @Override public ItemStack getStackInSlot(int index) { reads.incrementAndGet(); throw new AssertionError("Custom stack callback"); }
        };
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(new ContainerChest(player, custom, null), player));
        assertEquals(0, reads.get());
    }
    @Test void localInventorySizeCannotBeHiddenByRemovingPublicContainerSlots() {
        InventoryPlayer player = player();
        ContainerLocalMenu inventory = new ContainerLocalMenu("minecraft:chest", new TextComponentString("chest"), 54);
        ContainerChest disguised = new ContainerChest(player, inventory, null);
        disguised.inventorySlots.subList(27, 54).clear();
        assertEquals(27 + 36, disguised.inventorySlots.size());
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(disguised, player));
        ContainerLocalMenu oversizedFurnace = new ContainerLocalMenu("minecraft:furnace", new TextComponentString("furnace"), 4);
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(new ContainerFurnace(player, oversizedFurnace), player));
    }
    @Test void explicitSourceGuiIdsAreMenuSpecificWhileTheOriginalConstructorRemainsCompatible() {
        for (Menu menu : Arrays.asList(Menu.WORKBENCH, Menu.FURNACE, Menu.CHEST, Menu.CHEST_LARGE, Menu.HOPPER)) {
            java.util.List<ItemStack> empty = Collections.nCopies(menu.slots, ItemStack.EMPTY);
            CaptureDraft compatible = new CaptureDraft(menu, empty, Collections.emptyMap());
            String expected = menu == Menu.WORKBENCH ? "minecraft:crafting_table" : menu == Menu.FURNACE ? "minecraft:furnace"
                : menu == Menu.HOPPER ? "minecraft:hopper" : "minecraft:container";
            assertEquals(expected, compatible.getSourceGuiId());
            assertEquals(expected, compatible.toStep().getAsJsonObject("capture").get("sourceGuiId").getAsString());
            assertThrows(IllegalArgumentException.class, () -> new CaptureDraft(menu, empty, Collections.emptyMap(), "thirdparty:menu"));
            assertThrows(IllegalArgumentException.class, () -> new CaptureDraft(menu, empty, Collections.emptyMap(), "minecraft:generic_9x6"));
            assertThrows(IllegalArgumentException.class, () -> new CaptureDraft(menu, empty, Collections.emptyMap(), null));
        }
        assertThrows(IllegalArgumentException.class, () -> new CaptureDraft(Menu.FURNACE, Collections.nCopies(3, ItemStack.EMPTY), Collections.emptyMap(), "minecraft:chest"));
    }
    @Test void furnaceCopiesExactlyTheFourAlreadySynchronizedClientFields() {
        InventoryPlayer player = player();
        ContainerLocalMenu inventory = new ContainerLocalMenu("minecraft:furnace", new TextComponentString("furnace"), 3);
        inventory.setInventorySlotContents(0, new ItemStack(Blocks.IRON_ORE, 3));
        inventory.setInventorySlotContents(1, new ItemStack(Items.COAL));
        for (int index = 0; index < 4; index++) inventory.setField(index, new int[] {123, 1600, 42, 200}[index]);
        CaptureDraft draft = VanillaMenuCapture.capture(new ContainerFurnace(player, inventory), player);
        assertEquals(Menu.FURNACE, draft.getMenu()); assertEquals(4, draft.getData().size());
        assertEquals(123, draft.getData().get("burnTime")); assertEquals(200, draft.getData().get("cookTotal"));
        inventory.setField(0, 0); inventory.getStackInSlot(0).setCount(1);
        assertEquals(123, draft.getData().get("burnTime")); assertEquals(3, draft.getSlots().get(0).count);
    }
    @Test void workbenchAndHopperUseTheirKnownFullSlotLayouts() {
        InventoryPlayer player = player();
        ContainerWorkbench workbench = new ContainerWorkbench(player, new DetachedPonderWorld(), BlockPos.ORIGIN);
        workbench.craftResult.setInventorySlotContents(0, new ItemStack(Blocks.CRAFTING_TABLE));
        CaptureDraft crafting = VanillaMenuCapture.capture(workbench, player);
        assertEquals(Menu.WORKBENCH, crafting.getMenu()); assertEquals(10, crafting.getSlots().size());
        assertEquals("minecraft:crafting_table", crafting.getSlots().get(0).itemId);
        ContainerLocalMenu hopper = new ContainerLocalMenu("minecraft:hopper", new TextComponentString("hopper"), 5);
        hopper.setInventorySlotContents(4, new ItemStack(Items.IRON_INGOT));
        CaptureDraft captured = VanillaMenuCapture.capture(new ContainerHopper(player, hopper, null), player);
        assertEquals(Menu.HOPPER, captured.getMenu()); assertEquals(5, captured.getSlots().size());
        assertTrue(captured.getData().isEmpty(), "Do not invent redstone lock state which this native menu does not synchronize");
    }
    @Test void craftingRequiresTheCompleteExactThreeByThreeMatrixAndSingleResult() {
        InventoryPlayer player = player();
        ContainerWorkbench malformed = new ContainerWorkbench(player, new DetachedPonderWorld(), BlockPos.ORIGIN);
        malformed.craftMatrix = new InventoryCrafting(malformed, 2, 5);
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(malformed, player));
        ContainerWorkbench missingResult = new ContainerWorkbench(player, new DetachedPonderWorld(), BlockPos.ORIGIN);
        missingResult.craftResult = null;
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(missingResult, player));
    }
    @Test void unknownCraftingInventoryCallbacksAreRejectedBeforeAnyRead() {
        AtomicInteger reads = new AtomicInteger(); InventoryPlayer player = player();
        ContainerWorkbench customMatrix = new ContainerWorkbench(player, new DetachedPonderWorld(), BlockPos.ORIGIN);
        customMatrix.craftMatrix = new InventoryCrafting(customMatrix, 3, 3) {
            @Override public ItemStack getStackInSlot(int index) { reads.incrementAndGet(); throw new AssertionError("Unknown crafting matrix"); }
        };
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(customMatrix, player));
        ContainerWorkbench customResult = new ContainerWorkbench(player, new DetachedPonderWorld(), BlockPos.ORIGIN);
        customResult.craftResult = new InventoryCraftResult() {
            @Override public ItemStack getStackInSlot(int index) { reads.incrementAndGet(); throw new AssertionError("Unknown crafting result"); }
        };
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(customResult, player));
        assertEquals(0, reads.get());
    }
    @Test void noItemNbtOrIdentityIsReadOrExportedAndTheOriginalStackIsUntouched() {
        InventoryPlayer player = player(); InventoryBasic inventory = chest(27);
        ItemStack source = new ItemStack(Blocks.WOOL, 3, 14); NBTTagCompound tag = new NBTTagCompound();
        tag.setString("OwnerUUID", "private-identity"); NBTTagCompound nested = new NBTTagCompound(); nested.setString("password", "not-exported");
        tag.setTag("BlockEntityTag", nested); source.setTagCompound(tag); inventory.setInventorySlotContents(0, source);
        CaptureDraft draft = VanillaMenuCapture.capture(new ContainerChest(player, inventory, null), player);
        String json = draft.toStep().toString(); assertFalse(json.contains("private-identity")); assertFalse(json.contains("password"));
        assertEquals(1, draft.getFilteredTagCount()); assertFalse(draft.getSlots().get(0).toStack().hasTagCompound());
        assertEquals(14, draft.getSlots().get(0).meta); assertEquals(tag, source.getTagCompound());
        source.setCount(1); assertEquals(3, draft.getSlots().get(0).count);
        assertThrows(UnsupportedOperationException.class, () -> draft.getSlots().clear());
        assertThrows(UnsupportedOperationException.class, () -> draft.getData().put("secret", 1));
    }
    @Test void evenPlayerInventoryGettersAreNeverInvoked() {
        AtomicInteger reads = new AtomicInteger();
        InventoryPlayer player = new InventoryPlayer(null) {
            @Override public ItemStack getStackInSlot(int index) { reads.incrementAndGet(); throw new AssertionError("Player inventory must not be read"); }
        };
        CaptureDraft draft = VanillaMenuCapture.capture(new ContainerChest(player, chest(54), null), player);
        assertEquals(54, draft.getSlots().size()); assertEquals(0, reads.get());
    }
    @Test void unknownInventoryAndSlotCallbacksAreRejectedWithoutExecutingThem() {
        AtomicInteger reads = new AtomicInteger(); InventoryPlayer player = player();
        InventoryBasic unknown = new InventoryBasic("unknown", false, 27) {
            @Override public ItemStack getStackInSlot(int index) { reads.incrementAndGet(); throw new AssertionError("Unknown callback"); }
        };
        ContainerChest customInventory = new ContainerChest(player, unknown, null);
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(customInventory, player));
        InventoryBasic original = chest(27); ContainerChest customSlot = new ContainerChest(player, original, null);
        customSlot.inventorySlots.set(0, new Slot(original, 0, 0, 0) {
            @Override public ItemStack getStack() { reads.incrementAndGet(); throw new AssertionError("Unknown slot"); }
        });
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(customSlot, player)); assertEquals(0, reads.get());
    }
    @Test void nonVanillaContainerSubclassesAndIncompleteSizesAreRejected() {
        InventoryPlayer player = player();
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(new ContainerChest(player, chest(27), null) {}, player));
        for (int count : Arrays.asList(9, 18, 36, 45, 63))
            assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(new ContainerChest(player, chest(count), null), player));
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(new Container() {
            @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) { return false; }
        }, player));
    }
    @Test void wrongPlayerSlotIdentityAndUnknownFurnaceMenuIdFailBeforeExport() {
        InventoryPlayer player = player(); ContainerChest chest = new ContainerChest(player, chest(27), null);
        chest.inventorySlots.set(27, new Slot(chest(36), 9, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(chest, player));
        ContainerLocalMenu unknown = new ContainerLocalMenu("thirdparty:machine", new TextComponentString("machine"), 3);
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(new ContainerFurnace(player, unknown), player));
    }
    @Test void oversizedStacksMalformedDataAndWrongSlotCountsCannotBecomeValidDrafts() {
        assertThrows(IllegalArgumentException.class, () -> new CaptureDraft(Menu.HOPPER, Collections.emptyList(), Collections.emptyMap()));
        assertThrows(IllegalArgumentException.class, () -> new CaptureDraft(Menu.HOPPER,
            Arrays.asList(new ItemStack(Items.DIAMOND, 65), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY), Collections.emptyMap()));
        InventoryPlayer player = player(); ContainerLocalMenu furnace = new ContainerLocalMenu("minecraft:furnace", new TextComponentString("furnace"), 3);
        furnace.setField(0, -1);
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(new ContainerFurnace(player, furnace), player));
    }
    @Test void returnedStepIsAnIndependentMutableCopyNotTheCapturedValues() {
        InventoryPlayer player = player(); InventoryBasic chest = chest(27); chest.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 4));
        CaptureDraft capture = VanillaMenuCapture.capture(new ContainerChest(player, chest, null), player);
        JsonObject first = capture.toStep(); first.getAsJsonObject("interface").getAsJsonArray("slots").get(0).getAsJsonObject().remove("item");
        assertEquals(4, capture.toStep().getAsJsonObject("interface").getAsJsonArray("slots").get(0).getAsJsonObject().getAsJsonObject("item").get("count").getAsInt());
    }
    @Test void onlyAnExplicitCommitChangesTheOriginalSessionAndUndoRedoRemainAvailable() {
        AuthoringSession session = new AuthoringSession(AuthoringEdits.empty("local:captured_menu"));
        String initial = session.getDocument().sha256(); InventoryPlayer player = player();
        CaptureDraft capture = VanillaMenuCapture.capture(new ContainerChest(player, chest(54), null), player);
        assertEquals(initial, session.getDocument().sha256()); assertFalse(session.canUndo());
        capture.appendTo(session, 0, initial);
        assertEquals(2, session.getDocument().getScenes().get(0).getAsJsonArray("steps").size()); assertTrue(session.canUndo());
        assertNull(session.getDraftHash()); assertNull(session.getPublishedHash());
        assertTrue(session.undo()); assertEquals(initial, session.getDocument().sha256());
        assertTrue(session.redo()); assertEquals("show_interface", session.getDocument().getScenes().get(0).getAsJsonArray("steps").get(1).getAsJsonObject().get("type").getAsString());
        String changed = session.getDocument().sha256();
        assertThrows(IllegalStateException.class, () -> capture.appendTo(session, 0, initial)); assertEquals(changed, session.getDocument().sha256());
        assertThrows(IllegalStateException.class, () -> capture.appendTo(session, 99, changed)); assertEquals(changed, session.getDocument().sha256());
    }
    @Test void screenAwareVanillaFurnaceCaptureMatchesTheOriginalContainerOnlyApi() {
        InventoryPlayer player = player(); ContainerLocalMenu inventory = new ContainerLocalMenu("minecraft:furnace", new TextComponentString("furnace"), 3);
        inventory.setInventorySlotContents(0, new ItemStack(Blocks.IRON_ORE, 3)); inventory.setField(3, 200);
        GuiFurnace gui = new GuiFurnace(player, inventory);
        CaptureDraft bound = VanillaMenuCapture.capture(gui, gui.inventorySlots, player);
        CaptureDraft legacy = VanillaMenuCapture.capture(gui.inventorySlots, player);
        assertEquals(legacy.toStep(), bound.toStep()); assertTrue(bound.getSourceMetadata().isEmpty());
        assertEquals("minecraft:furnace", bound.getSourceGuiId()); assertEquals(3, bound.getSlots().get(0).count);
    }
    @Test void supportedGuiCannotCaptureADifferentContainersData() {
        InventoryPlayer player = player(); ContainerLocalMenu inventory = new ContainerLocalMenu("minecraft:furnace", new TextComponentString("furnace"), 3);
        GuiFurnace gui = new GuiFurnace(player, inventory); ContainerFurnace other = new ContainerFurnace(player, inventory);
        assertTrue(VanillaMenuCapture.supports(gui, other));
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(gui, other, player));
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(null, gui.inventorySlots, player));
    }
    @Test void unknownGuiSubclassIsRejectedBeforeAnyInventoryRead() {
        InventoryPlayer player = player(); AtomicInteger reads = new AtomicInteger();
        ContainerLocalMenu inventory = new ContainerLocalMenu("minecraft:furnace", new TextComponentString("furnace"), 3) {
            @Override public ItemStack getStackInSlot(int index) { reads.incrementAndGet(); throw new AssertionError("Unknown inventory callback"); }
        };
        GuiFurnace custom = new GuiFurnace(player, inventory) { };
        assertFalse(VanillaMenuCapture.supports(custom, custom.inventorySlots));
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(custom, custom.inventorySlots, player));
        assertEquals(0, reads.get());
    }
    @Test void newGuiAwarePathStillRejectsCustomFurnaceInventoryAndSlotCallbacks() {
        InventoryPlayer player = player(); AtomicInteger reads = new AtomicInteger();
        ContainerLocalMenu customInventory = new ContainerLocalMenu("minecraft:furnace", new TextComponentString("furnace"), 3) {
            @Override public ItemStack getStackInSlot(int index) { reads.incrementAndGet(); throw new AssertionError("Unknown inventory callback"); }
        };
        GuiFurnace gui = new GuiFurnace(player, customInventory);
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(gui, gui.inventorySlots, player));
        ContainerLocalMenu safe = new ContainerLocalMenu("minecraft:furnace", new TextComponentString("furnace"), 3); GuiFurnace badSlot = new GuiFurnace(player, safe);
        badSlot.inventorySlots.inventorySlots.set(0, new Slot(safe, 0, 0, 0) {
            @Override public ItemStack getStack() { reads.incrementAndGet(); throw new AssertionError("Unknown slot callback"); }
        });
        assertThrows(IllegalArgumentException.class, () -> VanillaMenuCapture.capture(badSlot, badSlot.inventorySlots, player)); assertEquals(0, reads.get());
    }
}
