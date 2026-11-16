package net.createmod.ponder.authoring;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

/** Explicit 1.12 preview adapters, never Entity.readFromNBT or a callback against a live world. */
public final class ActorNbtAdapter {
    private static final Set<String> COMMON = keys("Pos", "Rotation", "Motion", "Invisible", "CustomName");
    private static final Set<String> ENTITY_MARKERS = keys("Item", "Age", "PickupDelay", "Health", "Motion", "Pos",
        "Rotation", "NoGravity", "Glowing", "Invulnerable", "UUID", "UUIDMost", "UUIDLeast", "Tags", "Owner",
        "OwnerUUID", "CustomName", "CustomNameVisible", "Invisible", "Passengers", "Riding", "ForgeCaps", "id");
    private static final Set<String> UNSAFE_ITEM_TAGS = keys("SkullOwner", "BlockEntityTag", "ForgeCaps");
    private ActorNbtAdapter() { }

    public static final class Patch {
        private final NBTTagCompound data;
        private final ItemStack item;
        public final Vec3d motion;
        private Patch(NBTTagCompound data, ItemStack item, Vec3d motion) {
            this.data = data.copy(); this.item = item.copy(); this.motion = motion;
        }
        public NBTTagCompound getData() { return data.copy(); }
        public ItemStack getItem() { return item.copy(); }
    }

    static NBTTagCompound read(JsonObject step) {
        return step.has("nbt") ? AuthoringCompiler.nbt(AuthoringCompiler.requiredString(step, "nbt")) : new NBTTagCompound();
    }

    public static void validate(AuthoringDocument document, JsonObject step, boolean item) {
        NBTTagCompound patch = read(step);
        boolean entityStyle = !item || entityStyle(patch);
        if (entityStyle) {
            for (String key : patch.getKeySet()) {
                if (item && "Item".equals(key)) { validateItemContainer(document, patch.getTag(key)); continue; }
                if (!COMMON.contains(key)) throw new IllegalArgumentException("No safe preview NBT adapter for " + key);
                validateCommon(key, patch.getTag(key));
            }
        } else validateItemTag(patch);
        AuthoringNbtBudget.encodedBytes(patch);
    }

    public static Patch apply(AuthoringDocument document, JsonObject step, boolean isItem, ItemStack base) {
        validate(document, step, isItem);
        NBTTagCompound patch = read(step), result = new NBTTagCompound();
        Vec3d motion = Vec3d.ZERO;
        boolean entityStyle = !isItem || entityStyle(patch);
        ItemStack item = base == null ? ItemStack.EMPTY : base.copy();
        if (entityStyle) {
            for (String key : patch.getKeySet()) {
                if ("Item".equals(key)) continue;
                if ("Motion".equals(key)) { motion = vector((NBTTagList)patch.getTag(key)); continue; }
                result.setTag(key, canonical(key, patch.getTag(key)));
            }
            if (isItem && patch.hasKey("Item", 10)) item = patchItem(document, item, patch.getCompoundTag("Item"));
        } else item = mergeItemTag(item, patch);
        if (isItem && (!entityStyle || patch.hasKey("Item", 10))) {
            NBTTagCompound snapshot = new NBTTagCompound(); item.writeToNBT(snapshot); result.setTag("Item", snapshot);
            if (!item.isEmpty()) AuthoringNbtBudget.encodedBytes(snapshot);
        }
        return new Patch(result, item, motion);
    }

    static boolean entityStyle(NBTTagCompound patch) {
        for (String key : patch.getKeySet()) if (ENTITY_MARKERS.contains(key)) return true;
        return false;
    }

    private static void validateCommon(String key, NBTBase value) {
        if ("Pos".equals(key) || "Motion".equals(key)) {
            numericList(value, 3, key, 30000000);
        } else if ("Rotation".equals(key)) {
            numericList(value, 2, key, 1000000);
            if (Math.abs(((NBTPrimitive)((NBTTagList)value).get(1)).getDouble()) > 90)
                throw new IllegalArgumentException("Rotation pitch must be between -90 and 90 degrees");
        }
        else if ("Invisible".equals(key)) {
            if (!(value instanceof NBTPrimitive) || (((NBTPrimitive)value).getDouble() != 0 && ((NBTPrimitive)value).getDouble() != 1))
                throw new IllegalArgumentException("Invisible must be 0b or 1b");
        } else if ("CustomName".equals(key)) {
            if (value.getId() != 8) throw new IllegalArgumentException("CustomName must be a bounded 1.12 literal string");
            String name = ((net.minecraft.nbt.NBTTagString)value).getString();
            if (name.codePointCount(0, name.length()) > 64)
                throw new IllegalArgumentException("CustomName must be a bounded 1.12 literal string");
            for (int i = 0; i < name.length(); i++) if (Character.isISOControl(name.charAt(i)))
                throw new IllegalArgumentException("CustomName cannot contain control characters");
        }
    }

    private static NBTBase canonical(String key, NBTBase value) {
        if ("Pos".equals(key)) return canonicalList((NBTTagList)value, false);
        if ("Rotation".equals(key)) return canonicalList((NBTTagList)value, true);
        return value.copy();
    }

    private static NBTTagList canonicalList(NBTTagList source, boolean floats) {
        NBTTagList values = new NBTTagList();
        for (int i = 0; i < source.tagCount(); i++) {
            double value = ((NBTPrimitive)source.get(i)).getDouble();
            values.appendTag(floats ? new NBTTagFloat((float)value) : new NBTTagDouble(value));
        }
        return values;
    }

    private static void numericList(NBTBase raw, int count, String key, double bound) {
        if (!(raw instanceof NBTTagList) || ((NBTTagList)raw).tagCount() != count)
            throw new IllegalArgumentException(key + " requires exactly " + count + " numeric values");
        NBTTagList values = (NBTTagList)raw;
        for (int i = 0; i < count; i++) {
            if (!(values.get(i) instanceof NBTPrimitive)) throw new IllegalArgumentException(key + " must be numeric");
            double value = ((NBTPrimitive)values.get(i)).getDouble();
            if (!Double.isFinite(value) || Math.abs(value) > bound) throw new IllegalArgumentException(key + " exceeds the finite preview coordinate/angle budget");
        }
    }

    private static void validateItemContainer(AuthoringDocument document, NBTBase raw) {
        if (!(raw instanceof NBTTagCompound)) throw new IllegalArgumentException("Item must be a compound");
        NBTTagCompound item = (NBTTagCompound)raw;
        for (String key : item.getKeySet()) {
            if ("id".equals(key)) {
                if (!item.hasKey(key, 8)) throw new IllegalArgumentException("Item.id must be a registered string ID");
                resolveItem(document, item.getString(key));
            } else if ("Count".equals(key)) integer(item.getTag(key), 0, 64, "Item.Count");
            else if ("Damage".equals(key)) integer(item.getTag(key), 0, 32767, "Item.Damage");
            else if ("tag".equals(key)) {
                if (!item.hasKey(key, 10)) throw new IllegalArgumentException("Item.tag must be a compound");
                validateItemTag(item.getCompoundTag(key));
            } else throw new IllegalArgumentException("No safe preview Item NBT adapter for " + key);
        }
    }

    private static void validateItemTag(NBTTagCompound tag) {
        for (String key : tag.getKeySet()) {
            if (UNSAFE_ITEM_TAGS.contains(key)) throw new IllegalArgumentException("Item tag requires a dedicated safe rendering adapter: " + key);
            if ("display".equals(key)) {
                if (!tag.hasKey(key, 10)) throw new IllegalArgumentException("display must be a compound");
                NBTTagCompound display = tag.getCompoundTag(key);
                for (String field : display.getKeySet()) {
                    if ("Name".equals(field) || "LocName".equals(field)) {
                        if (!display.hasKey(field, 8)) throw new IllegalArgumentException("display." + field + " must be a literal string");
                    } else if ("Lore".equals(field)) {
                        if (!display.hasKey(field, 9) || display.getTagList(field, 8).tagCount() != ((NBTTagList)display.getTag(field)).tagCount())
                            throw new IllegalArgumentException("display.Lore must be a string list");
                    } else if ("color".equals(field)) integer(display.getTag(field), 0, 0xFFFFFF, "display.color");
                }
            } else if ("ench".equals(key) || "StoredEnchantments".equals(key)) {
                if (!tag.hasKey(key, 9)) throw new IllegalArgumentException(key + " must be a compound list");
                NBTTagList entries = (NBTTagList)tag.getTag(key);
                if (entries.tagCount() > 256) throw new IllegalArgumentException("Enchantment list exceeds 256 entries");
                for (int i = 0; i < entries.tagCount(); i++) {
                    if (!(entries.get(i) instanceof NBTTagCompound)) throw new IllegalArgumentException("Enchantment entries must be compounds");
                    NBTTagCompound entry = entries.getCompoundTagAt(i);
                    if (!entry.getKeySet().equals(keys("id", "lvl"))) throw new IllegalArgumentException("1.12 enchantments require numeric id/lvl only");
                    integer(entry.getTag("id"), 0, 32767, "enchantment.id"); integer(entry.getTag("lvl"), 0, 32767, "enchantment.lvl");
                }
            } else if ("CanDestroy".equals(key) || "CanPlaceOn".equals(key)) {
                if (!tag.hasKey(key, 9) || tag.getTagList(key, 8).tagCount() != ((NBTTagList)tag.getTag(key)).tagCount())
                    throw new IllegalArgumentException(key + " must be a string list");
            } else if ("Unbreakable".equals(key) || "RepairCost".equals(key) || "HideFlags".equals(key))
                integer(tag.getTag(key), 0, "Unbreakable".equals(key) ? 1 : Integer.MAX_VALUE, key);
        }
        AuthoringNbtBudget.encodedBytes(tag);
    }

    private static ItemStack patchItem(AuthoringDocument document, ItemStack base, NBTTagCompound patch) {
        Item item = patch.hasKey("id", 8) ? resolveItem(document, patch.getString("id")) : base.getItem();
        int count = patch.hasKey("Count") ? patch.getInteger("Count") : base.isEmpty() ? 1 : base.getCount();
        int damage = patch.hasKey("Damage") ? patch.getInteger("Damage") : base.isEmpty() ? 0 : base.getMetadata();
        ItemStack result = new ItemStack(item, count, damage);
        if (base.hasTagCompound()) result.setTagCompound(base.getTagCompound().copy());
        return patch.hasKey("tag", 10) ? mergeItemTag(result, patch.getCompoundTag("tag")) : result;
    }

    private static ItemStack mergeItemTag(ItemStack base, NBTTagCompound patch) {
        ItemStack result = base.copy();
        NBTTagCompound tag = result.hasTagCompound() ? result.getTagCompound().copy() : new NBTTagCompound();
        tag.merge(patch.copy()); result.setTagCompound(tag); return result;
    }

    private static Item resolveItem(AuthoringDocument document, String raw) {
        String mapped = AuthoringCompiler.mappedId(document, raw);
        ResourceLocation id = new ResourceLocation(mapped);
        if (!Item.REGISTRY.containsKey(id)) throw new IllegalArgumentException("Unknown 1.12 item: " + mapped);
        return Item.REGISTRY.getObject(id);
    }

    private static void integer(NBTBase raw, int min, int max, String name) {
        if (!(raw instanceof NBTPrimitive)) throw new IllegalArgumentException(name + " must be an integer");
        double value = ((NBTPrimitive)raw).getDouble();
        if (!Double.isFinite(value) || value != Math.rint(value) || value < min || value > max)
            throw new IllegalArgumentException(name + " is outside " + min + ".." + max);
    }

    private static Vec3d vector(NBTTagList raw) {
        NBTTagList values = canonicalList(raw, false);
        return new Vec3d(values.getDoubleAt(0), values.getDoubleAt(1), values.getDoubleAt(2));
    }
    private static Set<String> keys(String... values) { return new HashSet<String>(Arrays.asList(values)); }
}
