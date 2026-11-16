package net.createmod.ponder.authoring.capture;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonObject;
import net.createmod.ponder.authoring.practice.PracticeDefinition;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Menu;
import net.createmod.ponder.authoring.practice.PracticeJson;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Synthetic detached provenance/privacy fixtures; no HammerLib callbacks or GUI are constructed. */
class HammerFurnaceCaptureDraftTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    private Map<String, String> metadata() {
        Map<String, String> value = new LinkedHashMap<>();
        value.put("sourceAdapter", HammerFurnaceCaptureAdapter.ADAPTER_ID); value.put("sourceModId", "hammercore");
        value.put("sourceModVersion", "12.2.58"); value.put("sourceJarSha256", HammerFurnaceCaptureAdapter.JAR_SHA256);
        value.put("sourceGuiClass", HammerFurnaceCaptureAdapter.GUI); value.put("sourceContainerClass", HammerFurnaceCaptureAdapter.CONTAINER);
        return value;
    }
    private List<ItemStack> slots() { return new ArrayList<>(Collections.nCopies(3, ItemStack.EMPTY)); }
    private CaptureDraft draft(List<ItemStack> values, Map<String, Integer> data, Map<String, String> metadata) {
        return new CaptureDraft(Menu.FURNACE, values, data, HammerFurnaceCaptureAdapter.SOURCE_GUI_ID, metadata);
    }
    @Test void actualReplacementIdentityIsSeparateFromTheDetachedVanillaFurnace() {
        Map<String, Integer> data = new LinkedHashMap<>(); data.put("burnTime", 12); data.put("fuelTime", 1600); data.put("cookTime", 30); data.put("cookTotal", 400);
        CaptureDraft capture = draft(slots(), data, metadata()); JsonObject step = capture.toStep(), source = step.getAsJsonObject("capture");
        assertEquals("hammercore:furnace_smooth", capture.getSourceGuiId()); assertEquals("minecraft:furnace", capture.getMenuId());
        assertEquals("already_open_supported_replacement_menu", source.get("source").getAsString());
        assertEquals("minecraft:furnace", source.get("virtualMenuAdapter").getAsString());
        for (Map.Entry<String, String> entry : metadata().entrySet()) assertEquals(entry.getValue(), source.get(entry.getKey()).getAsString());
        PracticeDefinition definition = PracticeJson.fromStep(step); assertEquals(Menu.FURNACE, definition.getMenu()); assertEquals(39, definition.getSlotCount());
        for (int index = 3; index < 39; index++) assertTrue(definition.getInitialSlots().get(index).isEmpty());
        assertEquals(400, capture.getData().get("cookTotal")); data.put("cookTotal", 1); assertEquals(400, capture.getData().get("cookTotal"));
    }
    @Test void itemNbtIsFilteredWithoutReadingOrChangingItsIdentityValues() {
        List<ItemStack> values = slots(); ItemStack original = new ItemStack(Blocks.WOOL, 7, 14); NBTTagCompound tag = new NBTTagCompound();
        tag.setString("OwnerUUID", "not-exported"); original.setTagCompound(tag); values.set(0, original);
        CaptureDraft capture = draft(values, Collections.emptyMap(), metadata());
        assertEquals(1, capture.getFilteredTagCount()); assertEquals(7, capture.getSlots().get(0).count); assertEquals(14, capture.getSlots().get(0).meta);
        assertFalse(capture.toStep().toString().contains("not-exported")); assertFalse(capture.getSlots().get(0).toStack().hasTagCompound());
        assertEquals(tag, original.getTagCompound()); original.setCount(1); assertEquals(7, capture.getSlots().get(0).count);
    }
    @Test void provenanceIsAnImmutableCopyAndDoesNotExportFilePaths() {
        Map<String, String> values = metadata(); CaptureDraft capture = draft(slots(), Collections.emptyMap(), values); values.put("sourceModVersion", "changed");
        assertEquals("12.2.58", capture.getSourceMetadata().get("sourceModVersion"));
        assertThrows(UnsupportedOperationException.class, () -> capture.getSourceMetadata().put("privatePath", "C:\\private"));
        JsonObject first = capture.toStep(); first.getAsJsonObject("capture").addProperty("sourceGuiClass", "changed");
        assertEquals(HammerFurnaceCaptureAdapter.GUI, capture.toStep().getAsJsonObject("capture").get("sourceGuiClass").getAsString());
        assertFalse(capture.toStep().toString().contains("C:\\"));
    }
    @Test void originalPublicConstructorsCannotClaimAHammerGuiWithoutValidatedMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new CaptureDraft(Menu.FURNACE, slots(), Collections.emptyMap(), HammerFurnaceCaptureAdapter.SOURCE_GUI_ID));
        CaptureDraft old = new CaptureDraft(Menu.FURNACE, slots(), Collections.emptyMap());
        assertEquals("minecraft:furnace", old.getSourceGuiId()); assertTrue(old.getSourceMetadata().isEmpty());
        assertEquals("already_open_vanilla_menu", old.toStep().getAsJsonObject("capture").get("source").getAsString());
    }
    @Test void everyMissingChangedOrExtraProvenanceValueIsRejected() {
        for (String key : metadata().keySet()) {
            Map<String, String> missing = metadata(); missing.remove(key);
            assertThrows(IllegalArgumentException.class, () -> draft(slots(), Collections.emptyMap(), missing));
            Map<String, String> changed = metadata(); changed.put(key, "untrusted");
            assertThrows(IllegalArgumentException.class, () -> draft(slots(), Collections.emptyMap(), changed));
        }
        Map<String, String> extra = metadata(); extra.put("privatePath", "untrusted");
        assertThrows(IllegalArgumentException.class, () -> draft(slots(), Collections.emptyMap(), extra));
        assertThrows(IllegalArgumentException.class, () -> draft(slots(), Collections.emptyMap(), null));
    }
    @Test void hammerMetadataCannotBeMixedWithVanillaFastBenchOrOtherMenus() {
        for (Menu menu : Arrays.asList(Menu.WORKBENCH, Menu.CHEST, Menu.CHEST_LARGE, Menu.HOPPER))
            assertThrows(IllegalArgumentException.class, () -> new CaptureDraft(menu, Collections.nCopies(menu.slots, ItemStack.EMPTY), Collections.emptyMap(), HammerFurnaceCaptureAdapter.SOURCE_GUI_ID, metadata()));
        for (String id : Arrays.asList("minecraft:furnace", FastWorkbenchCaptureAdapter.SOURCE_GUI_ID, "hammercore:brewing_smooth"))
            assertThrows(IllegalArgumentException.class, () -> new CaptureDraft(Menu.FURNACE, slots(), Collections.emptyMap(), id, metadata()));
    }
    @Test void replacementProvenanceCannotBypassSlotAndDataBudgets() {
        assertThrows(IllegalArgumentException.class, () -> draft(Collections.nCopies(4, ItemStack.EMPTY), Collections.emptyMap(), metadata()));
        assertThrows(IllegalArgumentException.class, () -> draft(slots(), Collections.singletonMap("cookTotal", -1), metadata()));
        assertThrows(IllegalArgumentException.class, () -> draft(slots(), Collections.singletonMap("cookTime", 100001), metadata()));
        assertThrows(IllegalArgumentException.class, () -> draft(slots(), Collections.singletonMap("privateValue", 1), metadata()));
    }
}
