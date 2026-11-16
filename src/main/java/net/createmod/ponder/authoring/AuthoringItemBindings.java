package net.createmod.ponder.authoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;

/**
 * Ponderer's authoring filters, separate from legacy component matcher's list-prefix policy.
 * Adapted from SceneItemBindings/NbtSceneFilter, Copyright (c) 2026 Nobodiiiii (MIT).
 * See META-INF/PONDERER-MIGRATION-NOTICE.txt and licenses/ponderer-MIT.txt.
 */
public final class AuthoringItemBindings {
    private AuthoringItemBindings() { }

    public static final class Binding implements Predicate<ItemStack> {
        private final ResourceLocation itemId;
        private final int metadata;
        private final NBTTagCompound filter;

        private Binding(ResourceLocation itemId, int metadata, NBTTagCompound filter) {
            this.itemId = itemId;
            this.metadata = metadata;
            this.filter = filter == null ? null : filter.copy();
        }

        public ResourceLocation getItemId() { return itemId; }
        public int getMetadata() { return metadata; }
        public NBTTagCompound getFilter() { return filter == null ? null : filter.copy(); }

        @Override public boolean test(ItemStack stack) {
            if (stack == null || stack.isEmpty() || !itemId.equals(stack.getItem().getRegistryName())
                || metadata >= 0 && metadata != stack.getMetadata()) return false;
            return matchesNbt(stack, filter);
        }
    }

    /** Registry-independent validation; invalid filters never become unfiltered bindings. */
    public static void validate(AuthoringDocument document, AuthoringValidation.Builder result) {
        JsonObject root = document.internalJson();
        validateFilter(root.get("nbtFilter"), "/nbtFilter", result);
        JsonElement filters = root.get("itemNbtFilters");
        if (filters == null || filters.isJsonNull()) return;
        if (!filters.isJsonArray()) {
            result.error("binding.nbt.array", "/itemNbtFilters", "itemNbtFilters must be an array");
            return;
        }
        int index = 0;
        for (JsonElement filter : filters.getAsJsonArray())
            validateFilter(filter, "/itemNbtFilters/" + index++, result);
    }

    public static List<Binding> resolve(AuthoringDocument document) {
        AuthoringValidation.Builder validation = new AuthoringValidation.Builder();
        validate(document, validation);
        AuthoringValidation checked = validation.build();
        if (checked.hasErrors()) throw new IllegalArgumentException("Invalid item bindings: " + checked.getIssues());
        List<Binding> bindings = new ArrayList<>();
        JsonObject root = document.internalJson();
        JsonElement items = root.get("items");
        if (items == null || !items.isJsonArray() || items.getAsJsonArray().size() == 0)
            throw new IllegalArgumentException("At least one explicit item binding is required");
        for (JsonElement item : items.getAsJsonArray())
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()
                || !AuthoringDocument.validId(item.getAsString()))
                throw new IllegalArgumentException("Item bindings require explicit namespaced 1.12.2 ids");
        int index = 0;
        for (String item : document.getItems()) {
            String mapped = AuthoringCompiler.mappedId(document, item);
            ResourceLocation id = new ResourceLocation(mapped);
            if (!Item.REGISTRY.containsKey(id))
                throw new IllegalArgumentException("Unknown 1.12.2 item binding: " + mapped);
            AuthoringCompiler.resolveItem(document, item, 1, null);
            JsonObject mapping = mapping(root, item);
            int metadata = mapping.has("meta") ? AuthoringCompiler.integer(mapping, "meta", 0, 0, 32767) : -1;
            // An existing null/blank entry explicitly cancels the document-wide fallback.
            JsonElement filters = root.get("itemNbtFilters");
            JsonElement filter = filters != null && filters.isJsonArray() && index < filters.getAsJsonArray().size()
                ? filters.getAsJsonArray().get(index) : root.get("nbtFilter");
            bindings.add(new Binding(id, metadata, parseFilter(filter)));
            index++;
        }
        return Collections.unmodifiableList(bindings);
    }

    private static JsonObject mapping(JsonObject root, String item) {
        if (root.has("idMappings") && root.get("idMappings").isJsonObject()) {
            JsonElement mapped = root.getAsJsonObject("idMappings").get(item);
            if (mapped != null && mapped.isJsonObject()) return mapped.getAsJsonObject();
        }
        return new JsonObject();
    }

    private static void validateFilter(JsonElement filter, String path, AuthoringValidation.Builder result) {
        try { parseFilter(filter); }
        catch (RuntimeException invalid) {
            result.error("binding.nbt.invalid", path, String.valueOf(invalid.getMessage()));
        }
    }

    private static NBTTagCompound parseFilter(JsonElement filter) {
        if (filter == null || filter.isJsonNull()) return null;
        if (!filter.isJsonPrimitive() || !filter.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Item NBT filter must be an SNBT string or null");
        String source = filter.getAsString();
        if (source.length() > 65536) throw new IllegalArgumentException("NBT exceeds 65536 characters");
        if (source.trim().isEmpty()) return null;
        NBTTagCompound parsed = AuthoringCompiler.nbt(source);
        if (parsed.hasKey("components"))
            throw new IllegalArgumentException("High-version item components require explicit conversion to 1.12.2 tag NBT");
        return parsed.isEmpty() ? null : parsed;
    }

    static boolean matchesNbt(ItemStack stack, NBTTagCompound filter) {
        if (filter == null || filter.isEmpty()) return true;
        try {
            NBTTagCompound custom = stack.getTagCompound();
            if (stack.getItem() == Items.WRITTEN_BOOK) {
                String requiredTitle = bookTitle(filter);
                if (requiredTitle != null && !requiredTitle.isEmpty())
                    return requiredTitle.equals(bookTitle(custom));
            }
            if (custom != null && !custom.isEmpty() && subset(filter, custom, false)) return true;
            if (filter.hasKey("tag", 10)) {
                NBTTagCompound nested = filter.getCompoundTag("tag");
                if (!nested.isEmpty() && custom != null && subset(nested, custom, false)) return true;
            }
            NBTTagCompound saved = stack.writeToNBT(new NBTTagCompound());
            if (subset(filter, saved, true)) return true;
            return filter.hasKey("tag", 10) && saved.hasKey("tag", 10)
                && subset(filter.getCompoundTag("tag"), saved.getCompoundTag("tag"), false);
        } catch (RuntimeException invalidStack) {
            return false;
        }
    }

    private static boolean subset(NBTTagCompound expected, NBTTagCompound actual, boolean ignoreCount) {
        for (String key : expected.getKeySet()) {
            if (ignoreCount && ("Count".equals(key) || "count".equals(key))) continue;
            NBTBase pattern = expected.getTag(key), value = actual.getTag(key);
            if (value == null) return false;
            if (pattern instanceof NBTTagCompound && value instanceof NBTTagCompound) {
                if (!subset((NBTTagCompound) pattern, (NBTTagCompound) value, ignoreCount)) return false;
            } else if (!pattern.equals(value)) return false;
        }
        return true;
    }

    private static String bookTitle(NBTTagCompound tag) { return bookTitle(tag, 0); }

    private static String bookTitle(NBTTagCompound tag, int depth) {
        if (tag == null || tag.isEmpty() || depth >= 64) return null;
        if (tag.hasKey("title")) {
            NBTBase title = tag.getTag("title");
            String raw = null;
            if (title instanceof NBTTagString) raw = ((NBTTagString) title).getString();
            else if (title instanceof NBTTagCompound) {
                NBTTagCompound component = (NBTTagCompound) title;
                for (String key : new String[] { "raw", "text", "translate" })
                    if (component.hasKey(key, 8)) { raw = component.getString(key); break; }
            }
            if (raw != null) {
                String value = raw.trim();
                if ((value.startsWith("{") || value.startsWith("[")) && safeJsonText(value)) {
                    try {
                        String text = textComponent(new JsonParser().parse(value));
                        if (text != null && !text.trim().isEmpty()) return text;
                    } catch (RuntimeException ignored) { }
                }
                if (!value.isEmpty()) return value;
            }
        }
        return tag.hasKey("tag", 10) ? bookTitle(tag.getCompoundTag("tag"), depth + 1) : null;
    }

    private static boolean safeJsonText(String source) {
        if (source.length() > 65536) return false;
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (quoted) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = false;
            } else if (current == '"') quoted = true;
            else if ((current == '{' || current == '[') && ++depth > 64) return false;
            else if (current == '}' || current == ']') depth--;
        }
        return true;
    }

    private static String textComponent(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive()) return element.getAsString();
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String key : new String[] { "text", "raw", "translate" })
                if (object.has(key) && object.get(key).isJsonPrimitive()) return object.get(key).getAsString();
            return object.has("extra") && object.get("extra").isJsonArray() ? textComponent(object.get("extra")) : null;
        }
        StringBuilder joined = new StringBuilder();
        if (element.isJsonArray()) for (JsonElement child : element.getAsJsonArray()) {
            String part = textComponent(child);
            if (part != null) joined.append(part);
        }
        return joined.length() == 0 ? null : joined.toString();
    }
}
