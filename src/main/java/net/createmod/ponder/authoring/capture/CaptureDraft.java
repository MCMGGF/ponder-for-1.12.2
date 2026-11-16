package net.createmod.ponder.authoring.capture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Menu;
import net.createmod.ponder.authoring.practice.PracticeJson;
import net.createmod.ponder.authoring.AuthoringEdits;
import net.createmod.ponder.authoring.AuthoringSession;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** Detached privacy-filtered values only: no real inventory, container, player or item NBT. */
public final class CaptureDraft {
    public static final class SlotValue {
        public final String itemId;
        public final int count, meta;
        private SlotValue(ItemStack stack) {
            if (stack == null || stack.isEmpty()) { itemId = ""; count = meta = 0; return; }
            ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
            if (id == null || id.toString().length() > 256 || stack.getCount() < 1 || stack.getCount() > 64 || stack.getMetadata() < 0 || stack.getMetadata() > 32767)
                throw new IllegalArgumentException("Captured stack exceeds the bounded virtual item format");
            itemId = id.toString(); count = stack.getCount(); meta = stack.getMetadata();
        }
        public ItemStack toStack() {
            return itemId.isEmpty() ? ItemStack.EMPTY : new ItemStack(Item.REGISTRY.getObject(new ResourceLocation(itemId)), count, meta);
        }
        JsonObject toJson() {
            if (itemId.isEmpty()) return null;
            JsonObject result = new JsonObject(); result.addProperty("id", itemId); result.addProperty("count", count); result.addProperty("meta", meta);
            return result;
        }
    }
    private final Menu menu;
    private final List<SlotValue> slots;
    private final Map<String, Integer> data;
    private final int filteredTagCount;
    private final String sourceGuiId;
    private final Map<String, String> sourceMetadata;

    public CaptureDraft(Menu menu, List<ItemStack> values, Map<String, Integer> data) {
        this(menu, values, data, defaultSourceGuiId(menu));
    }
    public CaptureDraft(Menu menu, List<ItemStack> values, Map<String, Integer> data, String sourceGuiId) {
        this(menu, values, data, sourceGuiId, Collections.emptyMap());
    }
    CaptureDraft(Menu menu, List<ItemStack> values, Map<String, Integer> data, String sourceGuiId, Map<String, String> metadata) {
        if (menu == null || menu == Menu.READ_ONLY || values == null || values.size() != menu.slots)
            throw new IllegalArgumentException("Capture requires the complete known vanilla menu, without player slots");
        if (metadata == null || (metadata.isEmpty() ? !validSourceGuiId(menu, sourceGuiId)
            : !validFastWorkbenchSource(menu, sourceGuiId, metadata) && !validHammerFurnaceSource(menu, sourceGuiId, metadata)))
            throw new IllegalArgumentException("Capture source does not match a known exact read-only menu adapter");
        this.menu = menu; this.sourceGuiId = sourceGuiId;
        sourceMetadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        List<SlotValue> copy = new ArrayList<>(); int filtered = 0;
        for (ItemStack value : values) {
            if (value != null && !value.isEmpty() && value.hasTagCompound()) filtered++;
            copy.add(new SlotValue(value));
        }
        slots = Collections.unmodifiableList(copy); filteredTagCount = filtered;
        Map<String, Integer> copiedData = new LinkedHashMap<>();
        if (data != null) for (Map.Entry<String, Integer> entry : data.entrySet()) {
            if (menu != Menu.FURNACE || !java.util.Arrays.asList("burnTime", "fuelTime", "cookTime", "cookTotal").contains(entry.getKey())
                || entry.getValue() == null || entry.getValue() < 0 || entry.getValue() > 100000)
                throw new IllegalArgumentException("Unsupported or oversized captured vanilla data");
            copiedData.put(entry.getKey(), entry.getValue());
        }
        this.data = Collections.unmodifiableMap(copiedData);
        PracticeJson.fromStep(toStep());
    }
    public Menu getMenu() { return menu; }
    public List<SlotValue> getSlots() { return slots; }
    public Map<String, Integer> getData() { return data; }
    public int getFilteredTagCount() { return filteredTagCount; }
    public String getSourceGuiId() { return sourceGuiId; }
    public Map<String, String> getSourceMetadata() { return sourceMetadata; }
    private static boolean validFastWorkbenchSource(Menu menu, String id, Map<String, String> values) {
        return menu == Menu.WORKBENCH && FastWorkbenchCaptureAdapter.SOURCE_GUI_ID.equals(id) && values.size() == 6
            && FastWorkbenchCaptureAdapter.ADAPTER_ID.equals(values.get("sourceAdapter")) && "fastbench".equals(values.get("sourceModId"))
            && FastWorkbenchCaptureAdapter.VERSION.equals(values.get("sourceModVersion"))
            && FastWorkbenchCaptureAdapter.JAR_SHA256.equalsIgnoreCase(values.get("sourceJarSha256"))
            && FastWorkbenchCaptureAdapter.GUI.equals(values.get("sourceGuiClass")) && FastWorkbenchCaptureAdapter.CLIENT.equals(values.get("sourceContainerClass"));
    }
    private static boolean validHammerFurnaceSource(Menu menu, String id, Map<String, String> values) {
        return menu == Menu.FURNACE && HammerFurnaceCaptureAdapter.SOURCE_GUI_ID.equals(id) && values.size() == 6
            && HammerFurnaceCaptureAdapter.ADAPTER_ID.equals(values.get("sourceAdapter")) && "hammercore".equals(values.get("sourceModId"))
            && HammerFurnaceCaptureAdapter.VERSION.equals(values.get("sourceModVersion"))
            && HammerFurnaceCaptureAdapter.JAR_SHA256.equalsIgnoreCase(values.get("sourceJarSha256"))
            && HammerFurnaceCaptureAdapter.GUI.equals(values.get("sourceGuiClass")) && HammerFurnaceCaptureAdapter.CONTAINER.equals(values.get("sourceContainerClass"));
    }
    private static String defaultSourceGuiId(Menu menu) {
        if (menu == null || menu == Menu.READ_ONLY) return "";
        switch (menu) {
            case WORKBENCH: return "minecraft:crafting_table";
            case FURNACE: return "minecraft:furnace";
            case HOPPER: return "minecraft:hopper";
            case CHEST:
            case CHEST_LARGE: return "minecraft:container";
            default: return "";
        }
    }
    private static boolean validSourceGuiId(Menu menu, String sourceGuiId) {
        if (menu == Menu.CHEST || menu == Menu.CHEST_LARGE)
            return "minecraft:container".equals(sourceGuiId) || "minecraft:chest".equals(sourceGuiId);
        return (menu == Menu.WORKBENCH || menu == Menu.FURNACE || menu == Menu.HOPPER)
            && defaultSourceGuiId(menu).equals(sourceGuiId);
    }
    public void appendTo(AuthoringSession session, int scene, String expectedDocumentHash) {
        if (session == null || expectedDocumentHash == null || !expectedDocumentHash.equals(session.getDocument().sha256())
            || scene < 0 || scene >= session.getDocument().getScenes().size())
            throw new IllegalStateException("The original authoring session changed; the captured draft was not applied");
        session.replace(AuthoringEdits.addStep(session.getDocument(), scene, toStep()));
    }
    public String getMenuId() {
        switch (menu) {
            case WORKBENCH: return "minecraft:crafting_table";
            case FURNACE: return "minecraft:furnace";
            case CHEST: return "minecraft:generic_9x3";
            case CHEST_LARGE: return "minecraft:generic_9x6";
            case HOPPER: return "minecraft:hopper";
            default: throw new IllegalStateException("Unknown captured menu");
        }
    }
    public JsonObject toStep() {
        JsonObject step = new JsonObject(); step.addProperty("type", "show_interface"); step.addProperty("uiId", getMenuId()); step.addProperty("duration", 200);
        JsonObject descriptor = new JsonObject(); descriptor.addProperty("menuType", getMenuId());
        JsonArray entries = new JsonArray();
        for (int index = 0; index < slots.size(); index++) {
            JsonObject entry = new JsonObject(); entry.addProperty("index", index); entry.add("item", slots.get(index).toJson()); entries.add(entry);
        }
        descriptor.add("slots", entries);
        JsonArray fields = new JsonArray();
        for (Map.Entry<String, Integer> field : data.entrySet()) {
            JsonObject value = new JsonObject(); value.addProperty("key", field.getKey()); value.addProperty("value", field.getValue()); fields.add(value);
        }
        descriptor.add("data", fields);
        JsonObject player = new JsonObject(), inventory = new JsonObject(); inventory.addProperty("mode", "empty"); player.add("inventory", inventory); descriptor.add("player", player);
        step.add("interface", descriptor);
        JsonObject capture = new JsonObject(); capture.addProperty("version", 1); capture.addProperty("privacy", "id_count_meta_only");
        capture.addProperty("playerInventoryExported", false); capture.addProperty("itemNbtExported", false); capture.addProperty("filteredTagCount", filteredTagCount);
        capture.addProperty("sourceGuiId", sourceGuiId);
        capture.addProperty("virtualMenuAdapter", getMenuId());
        for (Map.Entry<String, String> source : sourceMetadata.entrySet()) capture.addProperty(source.getKey(), source.getValue());
        capture.addProperty("source", sourceMetadata.isEmpty() ? "already_open_vanilla_menu" : "already_open_supported_replacement_menu"); step.add("capture", capture);
        return step;
    }
}
