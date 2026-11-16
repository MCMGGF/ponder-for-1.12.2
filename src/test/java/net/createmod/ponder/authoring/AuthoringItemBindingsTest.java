package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuthoringItemBindingsTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void validFiltersCanPublishButMalformedSnbtFailsClosed() {
        JsonObject root = root("minecraft:compass");
        root.addProperty("nbtFilter", "{mode:1}");
        assertTrue(AuthoringValidation.validate(doc(root)).canPublish());
        root.addProperty("nbtFilter", "{mode:");
        assertFalse(AuthoringValidation.validate(doc(root)).canPublish());
        assertThrows(IllegalArgumentException.class, () -> AuthoringItemBindings.resolve(doc(root)));
    }

    @Test void nullAndBlankPerItemEntriesCancelGlobalWhileMissingIndicesFallBack() {
        JsonObject root = root("minecraft:compass", "minecraft:clock", "minecraft:paper", "minecraft:book");
        root.addProperty("nbtFilter", "{mode:1}");
        JsonArray filters = new JsonArray();
        filters.add(JsonNull.INSTANCE); filters.add("  "); filters.add("{mode:2}");
        root.add("itemNbtFilters", filters);
        List<AuthoringItemBindings.Binding> bindings = AuthoringItemBindings.resolve(doc(root));
        assertTrue(bindings.get(0).test(new ItemStack(Items.COMPASS)));
        assertTrue(bindings.get(1).test(new ItemStack(Items.CLOCK)));
        assertFalse(bindings.get(2).test(stack(Items.PAPER, "{mode:1}")));
        assertTrue(bindings.get(2).test(stack(Items.PAPER, "{mode:2}")));
        assertFalse(bindings.get(3).test(new ItemStack(Items.BOOK)));
        assertTrue(bindings.get(3).test(stack(Items.BOOK, "{mode:1}")));
    }

    @Test void absentAndNullFilterArraysUseGlobalAndEmptyCompoundIsUnfiltered() {
        JsonObject root = root("minecraft:compass"); root.addProperty("nbtFilter", "{mode:1}");
        assertFalse(binding(root).test(new ItemStack(Items.COMPASS)));
        root.add("itemNbtFilters", JsonNull.INSTANCE);
        assertFalse(binding(root).test(new ItemStack(Items.COMPASS)));
        root.addProperty("nbtFilter", "{}");
        ItemStack clean = new ItemStack(Items.COMPASS);
        assertTrue(binding(root).test(clean));
        assertNull(clean.getTagCompound());
    }

    @Test void compoundsUseSubsetButListsUseOrderedFullEquality() {
        AuthoringItemBindings.Binding binding = binding("{nested:{mode:1}, list:[{a:1}]} ");
        assertTrue(binding.test(stack(Items.COMPASS, "{nested:{mode:1,extra:2},list:[{a:1}],extra:3}")));
        assertFalse(binding.test(stack(Items.COMPASS, "{nested:{mode:1},list:[{a:1,b:2}]}")));
        assertFalse(binding.test(stack(Items.COMPASS, "{nested:{mode:1},list:[{a:1},{a:2}]}")));
        assertFalse(binding.test(stack(Items.COMPASS, "{nested:{mode:1},list:[]}")));
    }

    @Test void listOrderAndNumericTagTypesAreNotCoerced() {
        AuthoringItemBindings.Binding binding = binding("{values:[1,2],mode:1b}");
        assertTrue(binding.test(stack(Items.COMPASS, "{values:[1,2],mode:1b}")));
        assertFalse(binding.test(stack(Items.COMPASS, "{values:[2,1],mode:1b}")));
        assertFalse(binding.test(stack(Items.COMPASS, "{values:[1,2],mode:1}")));
    }

    @Test void rawTagAndLegacyTagWrapperAreBothUnderstoodWithoutMutatingStack() {
        ItemStack stack = stack(Items.COMPASS, "{mode:1,nested:{extra:2}}");
        NBTTagCompound before = stack.writeToNBT(new NBTTagCompound());
        assertTrue(binding("{mode:1}").test(stack));
        assertTrue(binding("{tag:{mode:1}}").test(stack));
        assertFalse(binding("{tag:{mode:2}}").test(stack));
        assertEquals(before, stack.writeToNBT(new NBTTagCompound()));
    }

    @Test void serializedStackFiltersIgnoreCountButRetainOtherFields() {
        ItemStack compass = new ItemStack(Items.COMPASS, 12);
        assertTrue(binding("{id:\"minecraft:compass\",Count:1b}").test(compass));
        assertTrue(binding("{id:\"minecraft:compass\",count:99}").test(compass));
        assertFalse(binding("{id:\"minecraft:paper\",Count:12b}").test(compass));
        assertFalse(binding("{Damage:1s}").test(compass));
    }

    @Test void countIsIgnoredRecursivelyOnlyForSerializedStackComparison() {
        ItemStack compass = stack(Items.COMPASS, "{mode:1}");
        assertTrue(binding("{tag:{mode:1,Count:90}}").test(compass));
        assertFalse(binding("{tag:{mode:2,Count:90}}").test(compass));
    }

    @Test void metadataOnlyRestrictsWhenMappingExplicitlySuppliesIt() {
        JsonObject root = root("minecraft:wool");
        assertTrue(binding(root).test(new ItemStack(net.minecraft.item.Item.getByNameOrId("minecraft:wool"), 1, 5)));
        JsonObject mappings = new JsonObject(), mapped = new JsonObject();
        mapped.addProperty("id", "minecraft:wool"); mapped.addProperty("meta", 5);
        mappings.add("minecraft:wool", mapped); root.add("idMappings", mappings);
        assertTrue(binding(root).test(new ItemStack(net.minecraft.item.Item.getByNameOrId("minecraft:wool"), 1, 5)));
        assertFalse(binding(root).test(new ItemStack(net.minecraft.item.Item.getByNameOrId("minecraft:wool"), 1, 0)));
        mapped.addProperty("meta", 5.5);
        assertThrows(IllegalArgumentException.class, () -> AuthoringItemBindings.resolve(doc(root)));
    }

    @Test void explicitMappingChangesItemIdentityAndFilterCannotMatchOtherItems() {
        JsonObject root = root("newversion:clock");
        JsonObject mappings = new JsonObject(); mappings.addProperty("newversion:clock", "minecraft:clock"); root.add("idMappings", mappings);
        root.addProperty("nbtFilter", "{mode:1}");
        AuthoringItemBindings.Binding binding = binding(root);
        assertEquals(new ResourceLocation("minecraft:clock"), binding.getItemId());
        assertTrue(binding.test(stack(Items.CLOCK, "{mode:1}")));
        assertFalse(binding.test(stack(Items.COMPASS, "{mode:1}")));
        assertFalse(binding.test(ItemStack.EMPTY)); assertFalse(binding.test(null));
    }

    @Test void eachResolvedBindingAndFilterSnapshotIsIndependent() {
        JsonObject root = root("minecraft:compass", "minecraft:compass");
        JsonArray filters = new JsonArray(); filters.add("{mode:1}"); filters.add("{mode:2}"); root.add("itemNbtFilters", filters);
        List<AuthoringItemBindings.Binding> bindings = AuthoringItemBindings.resolve(doc(root));
        NBTTagCompound copy = bindings.get(0).getFilter(); copy.setInteger("mode", 2);
        filters.set(0, new com.google.gson.JsonPrimitive("{mode:3}"));
        assertTrue(bindings.get(0).test(stack(Items.COMPASS, "{mode:1}")));
        assertFalse(bindings.get(0).test(stack(Items.COMPASS, "{mode:2}")));
        assertTrue(bindings.get(1).test(stack(Items.COMPASS, "{mode:2}")));
        assertThrows(UnsupportedOperationException.class, () -> bindings.clear());
    }

    @Test void unknownItemsAndEmptyStacksDoNotResolveToAirFallback() {
        assertThrows(IllegalArgumentException.class, () -> AuthoringItemBindings.resolve(doc(root("missing:item"))));
        assertThrows(IllegalArgumentException.class, () -> AuthoringItemBindings.resolve(doc(root("minecraft:air"))));
    }

    @Test void malformedItemSlotsCannotShiftAlignedFilterIndices() {
        JsonObject root = root("minecraft:compass");
        JsonArray items = new JsonArray(); items.add(1); items.add("minecraft:compass"); root.add("items", items);
        JsonArray filters = new JsonArray(); filters.add(JsonNull.INSTANCE); filters.add("{mode:1}"); root.add("itemNbtFilters", filters);
        assertThrows(IllegalArgumentException.class, () -> AuthoringItemBindings.resolve(doc(root)));
    }

    @Test void invalidTypesAndUnusedMalformedOverridesAreRejectedWithExactPaths() {
        JsonObject root = root("minecraft:compass"); root.addProperty("nbtFilter", 1);
        assertIssue(root, "/nbtFilter");
        root.addProperty("nbtFilter", "{}"); root.addProperty("itemNbtFilters", "bad");
        assertIssue(root, "/itemNbtFilters");
        JsonArray filters = new JsonArray(); filters.add(JsonNull.INSTANCE); filters.add("{bad:"); root.add("itemNbtFilters", filters);
        assertIssue(root, "/itemNbtFilters/1");
        root.addProperty("nbtFilter", "{bad:"); filters.remove(1);
        assertIssue(root, "/nbtFilter");
    }

    @Test void highVersionComponentsRequireExplicitConversionNotSilentCustomTagMatching() {
        JsonObject root = root("minecraft:compass"); root.addProperty("nbtFilter", "{components:{custom_data:{mode:1}}}");
        assertFalse(AuthoringValidation.validate(doc(root)).canPublish());
        assertThrows(IllegalArgumentException.class, () -> AuthoringItemBindings.resolve(doc(root)));
    }

    @Test void oversizedOrDeepSnbtIsRejectedBeforeParsing() {
        JsonObject root = root("minecraft:compass");
        String nested = "{}";
        for (int i = 0; i < 65; i++) nested = "{a:" + nested + "}";
        root.addProperty("nbtFilter", nested); assertIssue(root, "/nbtFilter");
        root.addProperty("nbtFilter", "{text:\"" + String.join("", java.util.Collections.nCopies(65537, "x")) + "\"}");
        assertIssue(root, "/nbtFilter");
        root.addProperty("nbtFilter", String.join("", java.util.Collections.nCopies(65537, " ")));
        assertIssue(root, "/nbtFilter");
    }

    @Test void writtenBookTitleRetainsUpstreamTitleOnlyCompatibility() {
        JsonObject root = root("minecraft:written_book");
        root.addProperty("nbtFilter", "{tag:{title:\"Guide\",author:\"Other\"}}");
        ItemStack book = stack(Items.WRITTEN_BOOK, "{title:\"Guide\",author:\"Actual\"}");
        assertTrue(binding(root).test(book));
        book.getTagCompound().setString("title", "Different");
        assertFalse(binding(root).test(book));
    }

    @Test void writtenBookJsonTitlesNormalizeWithoutMutatingTag() {
        JsonObject root = root("minecraft:written_book"); root.addProperty("nbtFilter", "{title:\"Guide\"}");
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK); NBTTagCompound tag = new NBTTagCompound();
        tag.setString("title", "{\"text\":\"Guide\"}"); book.setTagCompound(tag);
        assertTrue(binding(root).test(book));
        assertEquals("{\"text\":\"Guide\"}", tag.getString("title"));
    }

    @Test void deeplyNestedJsonBookTitleDoesNotInvokeUnboundedRecursiveParser() {
        JsonObject root = root("minecraft:written_book"); root.addProperty("nbtFilter", "{title:\"Guide\"}");
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK); NBTTagCompound tag = new NBTTagCompound();
        tag.setString("title", String.join("", java.util.Collections.nCopies(10000, "["))); book.setTagCompound(tag);
        assertFalse(binding(root).test(book));
    }

    private static AuthoringItemBindings.Binding binding(String filter) {
        JsonObject root = root("minecraft:compass"); root.addProperty("nbtFilter", filter); return binding(root);
    }
    private static AuthoringItemBindings.Binding binding(JsonObject root) {
        return AuthoringItemBindings.resolve(doc(root)).get(0);
    }
    private static ItemStack stack(net.minecraft.item.Item item, String tag) {
        ItemStack stack = new ItemStack(item); stack.setTagCompound(AuthoringCompiler.nbt(tag)); return stack;
    }
    private static JsonObject root(String... items) {
        JsonObject root = AuthoringModelTest.document(AuthoringModelTest.step("idle")).toJson();
        JsonArray array = new JsonArray(); for (String item : items) array.add(item); root.add("items", array); return root;
    }
    private static AuthoringDocument doc(JsonObject root) { return AuthoringDocument.fromJson(root); }
    private static void assertIssue(JsonObject root, String path) {
        AuthoringValidation validation = AuthoringValidation.validate(doc(root));
        assertTrue(validation.hasErrors());
        assertTrue(validation.getIssues().stream().anyMatch(issue -> path.equals(issue.getPath())));
    }
}
