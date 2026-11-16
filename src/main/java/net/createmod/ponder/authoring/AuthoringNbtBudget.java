package net.createmod.ponder.authoring;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.createmod.ponder.foundation.PonderSchematic;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;

/** Bounds retained/expanded metadata before any SceneBuilder target loop executes. */
final class AuthoringNbtBudget {
    static final long MAX_TILE_BYTES = 64L * 1024;
    static final long MAX_TOTAL_BYTES = 16L * 1024 * 1024;
    private static final long MAX_DOCUMENT_POSITIONS = 1048576;

    static final class LimitException extends IllegalArgumentException {
        final String path;
        LimitException(String path, String message) { super(message); this.path = path; }
    }
    static final class Tile {
        final NBTTagCompound data;
        final long bytes;
        Tile(NBTTagCompound data) { this.data = data; bytes = encodedBytes(data); }
    }
    static final class AssetStats {
        static final AssetStats EMPTY = new AssetStats(Collections.<BlockPos, Tile>emptyMap(), 0);
        final Map<BlockPos, Tile> tiles;
        final long bytes;
        AssetStats(Map<BlockPos, Tile> tiles, long bytes) { this.tiles = tiles; this.bytes = bytes; }
    }

    private long totalBytes, documentPositions;
    private long actorEvents;
    private final List<Long> itemPayloads = new ArrayList<Long>();
    private final List<Long> entityPayloads = new ArrayList<Long>();
    private String path = "/scenes";
    private final Map<BlockPos, NBTTagCompound> currentNbt = new LinkedHashMap<BlockPos, NBTTagCompound>();
    private final Map<BlockPos, Block> currentBlocks = new LinkedHashMap<BlockPos, Block>();
    private final Set<BlockPos> known = new LinkedHashSet<BlockPos>();
    private PonderSchematic primary;
    private AssetStats original;

    static AssetStats measureAsset(PonderSchematic schematic) {
        Map<BlockPos, Tile> tiles = new LinkedHashMap<BlockPos, Tile>(); long total = 0;
        for (BlockPos pos : schematic.getBlocks().keySet()) {
            NBTTagCompound data = schematic.getBlockEntityNbt(pos);
            if (data == null) continue;
            Tile tile = new Tile(data); total = checkedAdd(total, tile.bytes);
            tiles.put(new BlockPos(pos), tile);
        }
        return new AssetStats(Collections.unmodifiableMap(tiles), total);
    }

    static long checkedAdd(long current, long amount) {
        if (amount < 0 || current < 0 || amount > MAX_TOTAL_BYTES - current)
            throw new LimitException("/scenes", "Expanded NBT exceeds the 16 MiB UTF-8 document budget");
        return current + amount;
    }

    static void checkDeclaredNbt(AuthoringDocument document) {
        AuthoringNbtBudget budget = new AuthoringNbtBudget(); int sceneIndex = 0;
        budget.bindingNbt(document);
        for (JsonObject segment : document.getScenes()) {
            int stepIndex = 0;
            for (JsonElement step : segment.getAsJsonArray("steps")) {
                budget.path = "/scenes/" + sceneIndex + "/steps/" + stepIndex++;
                try { budget.nestedNbt(step); }
                catch (LimitException limit) { throw new LimitException(budget.path, limit.getMessage()); }
            }
            sceneIndex++;
        }
    }

    static void check(AuthoringDocument document, AuthoringCompiler.CachedAssets assets, Set<String> handlers) {
        AuthoringNbtBudget budget = new AuthoringNbtBudget(); int sceneIndex = 0;
        budget.bindingNbt(document);
        for (JsonObject segment : document.getScenes()) {
            budget.path = "/scenes/" + sceneIndex++ + "/structure";
            budget.primary = AuthoringCompiler.primary(document, segment, assets);
            budget.original = assets.stats(budget.primary);
            budget.known.clear(); budget.currentNbt.clear(); budget.currentBlocks.clear();
            budget.itemPayloads.clear(); budget.entityPayloads.clear();
            budget.known.addAll(budget.primary.getBlocks().keySet());
            budget.chargePositions(budget.known.size());
            budget.charge(budget.original.bytes);
            for (Map.Entry<BlockPos, IBlockState> block : budget.primary.getBlocks().entrySet()) budget.currentBlocks.put(block.getKey(), block.getValue().getBlock());
            for (Map.Entry<BlockPos, Tile> tile : budget.original.tiles.entrySet()) budget.currentNbt.put(tile.getKey(), tile.getValue().data.copy());
            int stepIndex = 0;
            for (JsonElement value : segment.getAsJsonArray("steps")) {
                JsonObject step = value.getAsJsonObject(); budget.path = "/scenes/" + (sceneIndex - 1) + "/steps/" + stepIndex++;
                try { budget.step(document, segment, step, assets, handlers); }
                catch (LimitException limit) { throw new LimitException(budget.path, limit.getMessage()); }
            }
        }
    }

    private void step(AuthoringDocument document, JsonObject segment, JsonObject step,
        AuthoringCompiler.CachedAssets assets, Set<String> handlers) {
        String type = AuthoringDocument.string(step, "type", "").toLowerCase(java.util.Locale.ROOT);
        if ("modify_entities_nbt".equals(type) || "modify_item_entities_nbt".equals(type)) {
            long patchBytes = step.has("nbt") ? encodedBytes(AuthoringCompiler.nbt(AuthoringCompiler.requiredString(step, "nbt"))) : 0;
            chargeActorSnapshots(itemPayloads, patchBytes, true);
            if ("modify_entities_nbt".equals(type)) chargeActorSnapshots(entityPayloads, patchBytes, false);
            return;
        }
        if (handlers.contains(type)) {
            if ("native_section".equals(type)) {
                java.util.List<BlockPos> selected = NativeSectionInstructions.positions(step);
                chargePositions(selected.size()); known.addAll(selected);
            }
            if ("native_overlay".equals(type) && step.has("overlay") && step.get("overlay").isJsonObject()) {
                JsonObject overlay = step.getAsJsonObject("overlay");
                for (String key : new String[] {"sourcePositions", "targetPositions"})
                    if (overlay.has(key) && overlay.get(key).isJsonArray()) chargePositions(overlay.getAsJsonArray(key).size());
            }
            nestedNbt(step); return;
        }
        if ("highlight_section".equals(type) || ("text".equals(type) || "shared_text".equals(type))
            && (step.has("blockPos") || step.has("positions"))) {
            chargePositions(targets(step, false).size()); return;
        }
        if ("actor_action".equals(type) || "create_entity".equals(type) || "create_item_entity".equals(type) || "show_controls".equals(type)) {
            long bytes = step.has("nbt") ? encodedBytes(AuthoringCompiler.nbt(AuthoringCompiler.requiredString(step, "nbt"))) : 0;
            if ("actor_action".equals(type) && "NBT_PATCH".equalsIgnoreCase(AuthoringDocument.string(step, "action", ""))) {
                List<Long> payloads = "ITEM".equalsIgnoreCase(AuthoringDocument.string(step, "kind", "")) ? itemPayloads : entityPayloads;
                long upper = bytes;
                for (long previous : payloads) upper = Math.max(upper, Math.min(MAX_TILE_BYTES, previous + bytes));
                charge(upper);
                if (payloads == itemPayloads) charge(upper);
                for (int index = 0; index < payloads.size(); index++) payloads.set(index, Math.max(payloads.get(index), upper));
                chargeActorEvents(1);
                return;
            }
            charge(bytes);
            if ("create_entity".equals(type) || "create_item_entity".equals(type)
                || "actor_action".equals(type) && "SPAWN".equalsIgnoreCase(AuthoringDocument.string(step, "action", ""))) {
                boolean item = "create_item_entity".equals(type) || "ITEM".equalsIgnoreCase(AuthoringDocument.string(step, "kind", ""));
                long payload = Math.min(MAX_TILE_BYTES, bytes + 128);
                (item ? itemPayloads : entityPayloads).add(payload);
                charge(payload);
                chargeActorEvents(8);
            } else if ("actor_action".equals(type)) chargeActorEvents(1);
            return;
        }
        if ("clear_entities".equals(type) || "clear_item_entities".equals(type)) {
            chargeActorEvents(itemPayloads.size() + ("clear_entities".equals(type) ? entityPayloads.size() : 0));
            return;
        }
        if ("show_extra_structure".equals(type)) {
            PonderSchematic extra = AuthoringCompiler.resolveAsset(assets, AuthoringCompiler.structureReference(document, segment, step));
            AssetStats stats = assets.stats(extra);
            ExtraStructurePlanning.Plan plan = ExtraStructurePlanning.plan(extra, step);
            chargePositions(plan.getPlacements().size());
            // Even a repeated reference contributes its full expanded output again.
            ensureFits(stats.bytes);
            for (ExtraStructurePlanning.PlacedBlock block : plan.getPlacements()) {
                BlockPos pos = block.getPos(); currentNbt.remove(pos); setBlock(pos, block.getState().getBlock()); known.add(pos);
                NBTTagCompound data = block.getNbtCopy();
                if (data != null) merge(pos, data);
            }
            return;
        }
        if ("set_block".equals(type) || "replace_blocks".equals(type)) {
            NBTTagCompound patch = step.has("nbt") ? AuthoringCompiler.nbt(AuthoringCompiler.requiredString(step, "nbt")) : null;
            if (patch != null) ensurePatchFits(step, "set_block".equals(type) && !step.has("blockPos2"), patch);
            Set<BlockPos> selected = targets(step, "set_block".equals(type) && !step.has("blockPos2")); chargePositions(selected.size());
            Block block = AuthoringCompiler.resolveBlock(document, step).getBlock();
            for (BlockPos pos : selected) {
                if ("set_block".equals(type) || AuthoringCompiler.bool(step, "clearBlockEntityNbt", false)) currentNbt.remove(pos);
                setBlock(pos, block);
            }
            known.addAll(selected);
            if (patch != null) patch(selected, patch);
            return;
        }
        if ("modify_block_entity_nbt".equals(type)) {
            NBTTagCompound patch = step.has("nbt") ? AuthoringCompiler.nbt(AuthoringCompiler.requiredString(step, "nbt")) : null;
            if (patch != null) ensurePatchFits(step, false, patch);
            Set<BlockPos> selected = targets(step, false); chargePositions(selected.size()); known.addAll(selected);
            if (patch != null) patch(selected, patch);
            return;
        }
        if ("restore_blocks".equals(type)) {
            Set<BlockPos> selected = targets(step, false); chargePositions(selected.size()); known.addAll(selected);
            for (BlockPos pos : selected) {
                currentNbt.remove(pos); IBlockState block = primary.getBlocks().get(pos);
                currentBlocks.put(pos, block == null ? null : block.getBlock());
                Tile tile = original.tiles.get(pos);
                if (tile != null) { charge(tile.bytes); currentNbt.put(pos, tile.data.copy()); }
            }
            return;
        }
        if ("destroy_block".equals(type)) {
            BlockPos pos = AuthoringCompiler.blockPos(step, "blockPos"); chargePositions(1); setBlock(pos, Blocks.AIR); known.add(pos); return;
        }
        if ("show_structure".equals(type) || "hide_section".equals(type) || "show_section_and_merge".equals(type)
            || "move_section".equals(type) || "rotate_section".equals(type) || "toggle_redstone_power".equals(type) || "break_progress".equals(type)) {
            Set<BlockPos> selected = targets(step, "break_progress".equals(type)); chargePositions(selected.size()); known.addAll(selected);
        }
    }

    private void nestedNbt(JsonElement element) {
        if (element.isJsonObject()) for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if ("nbt".equals(entry.getKey()) && !entry.getValue().isJsonNull()) {
                JsonElement value = entry.getValue(); String text = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : value.toString();
                charge(encodedBytes(AuthoringCompiler.nbt(text)));
            } else nestedNbt(entry.getValue());
        }
        else if (element.isJsonArray()) for (JsonElement child : element.getAsJsonArray()) nestedNbt(child);
    }

    private void bindingNbt(AuthoringDocument document) {
        JsonObject root = document.internalJson();
        path = "/nbtFilter";
        chargeFilter(root.get("nbtFilter"));
        if (root.has("itemNbtFilters") && root.get("itemNbtFilters").isJsonArray()) {
            int index = 0;
            for (JsonElement filter : root.getAsJsonArray("itemNbtFilters")) {
                path = "/itemNbtFilters/" + index++;
                chargeFilter(filter);
            }
        }
    }

    private void chargeFilter(JsonElement filter) {
        if (filter == null || filter.isJsonNull()) return;
        if (!filter.isJsonPrimitive() || !filter.getAsJsonPrimitive().isString())
            throw limit("Item NBT filters must be SNBT strings or null");
        String value = filter.getAsString().trim();
        if (!value.isEmpty()) charge(encodedBytes(AuthoringCompiler.nbt(value)));
    }

    private void chargeActorSnapshots(List<Long> payloads, long patchBytes, boolean item) {
        chargeActorEvents((long) payloads.size() * 16);
        for (int index = 0; index < payloads.size(); index++) {
            // A selector can match any previously created actor. Bound repeated deep-merge snapshots before compiling.
            long upper = Math.min(MAX_TILE_BYTES, payloads.get(index) + patchBytes);
            charge(upper);
            // Item patches retain both serialized Item data and an independent ItemStack snapshot.
            if (item) charge(upper);
            payloads.set(index, upper);
        }
    }

    private void chargeActorEvents(long count) {
        if (count < 0 || count > 65536 - actorEvents)
            throw limit("Expanded actor timeline exceeds 65536 events per document");
        actorEvents += count;
    }

    private void setBlock(BlockPos pos, Block block) {
        if (currentBlocks.get(pos) != block) currentNbt.remove(pos);
        currentBlocks.put(new BlockPos(pos), block);
    }

    private void patch(Set<BlockPos> targets, NBTTagCompound patch) {
        long bytes = encodedBytes(patch);
        if (targets.size() != 0 && bytes > (MAX_TOTAL_BYTES - totalBytes) / targets.size())
            throw limit("NBT bytes multiplied by the actual target count exceed the 16 MiB budget");
        for (BlockPos pos : targets) merge(pos, patch);
    }

    private void ensurePatchFits(JsonObject step, boolean single, NBTTagCompound patch) {
        long count = single ? 1 : step.has("blockPos") ? AuthoringCompiler.volume(step)
            : step.has("positions") ? new LinkedHashSet<BlockPos>(AuthoringCompiler.positions(step)).size() : known.size();
        long bytes = encodedBytes(patch);
        if (count != 0 && bytes > (MAX_TOTAL_BYTES - totalBytes) / count)
            throw limit("NBT bytes multiplied by the actual target count exceed the 16 MiB budget");
    }

    private void merge(BlockPos pos, NBTTagCompound patch) {
        NBTTagCompound old = currentNbt.get(pos);
        NBTTagCompound merged = old == null ? new NBTTagCompound() : old.copy(); merged.merge(patch);
        charge(encodedBytes(merged)); currentNbt.put(new BlockPos(pos), merged);
    }

    private Set<BlockPos> targets(JsonObject step, boolean single) {
        Set<BlockPos> result = new LinkedHashSet<BlockPos>();
        if (step.has("positions") && !single) result.addAll(AuthoringCompiler.positions(step));
        else if (step.has("blockPos")) {
            BlockPos first = AuthoringCompiler.blockPos(step, "blockPos"), last = !single && step.has("blockPos2") ? AuthoringCompiler.blockPos(step, "blockPos2") : first;
            if (!single) AuthoringCompiler.volume(step);
            for (BlockPos pos : BlockPos.getAllInBox(first, last)) result.add(new BlockPos(pos));
        } else result.addAll(known);
        return result;
    }

    private void chargePositions(long count) {
        if (count > MAX_DOCUMENT_POSITIONS - documentPositions) throw limit("Expanded document targets exceed 1048576 positions");
        documentPositions += count;
    }
    private void ensureFits(long bytes) { if (bytes > MAX_TOTAL_BYTES - totalBytes) throw limit("Expanded NBT exceeds the 16 MiB UTF-8 document budget"); }
    private void charge(long bytes) { ensureFits(bytes); totalBytes += bytes; }
    private LimitException limit(String message) { return new LimitException(path, message); }

    static long encodedBytes(NBTTagCompound compound) {
        // A bounded tree walk precedes toString, so a huge scalar/array cannot allocate a huge SNBT string.
        Counter counter = new Counter(); counter.tag(compound, 0);
        long actual = compound.toString().getBytes(StandardCharsets.UTF_8).length;
        if (actual > MAX_TILE_BYTES) throw new LimitException("/scenes", "NBT exceeds the 64 KiB UTF-8 tile/item budget");
        return actual;
    }

    private static final class Counter {
        long size;
        void add(long bytes) {
            if (bytes < 0 || bytes > MAX_TILE_BYTES - size) throw new LimitException("/scenes", "NBT exceeds the bounded 64 KiB serialization budget");
            size += bytes;
        }
        void string(String text) {
            add(2);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\\' || c == '"') add(2);
                else if (c < 32) add(6);
                else if (c < 128) add(1);
                else if (c < 2048) add(2);
                else if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) { add(4); i++; }
                else add(3);
            }
        }
        void tag(NBTBase tag, int depth) {
            if (depth > 64) throw new LimitException("/scenes", "NBT exceeds 64 nesting levels");
            if (tag instanceof NBTTagCompound) {
                add(2);
                for (String key : ((NBTTagCompound) tag).getKeySet()) { string(key); add(2); tag(((NBTTagCompound) tag).getTag(key), depth + 1); }
            } else if (tag instanceof NBTTagList) {
                add(2); NBTTagList list = (NBTTagList) tag;
                for (int index = 0; index < list.tagCount(); index++) { add(12); tag(list.get(index), depth + 1); }
            } else if (tag instanceof NBTTagString) string(((NBTTagString) tag).getString());
            else if (tag instanceof NBTTagByteArray) { add(4); for (byte value : ((NBTTagByteArray) tag).getByteArray()) add(Byte.toString(value).length() + 2); }
            else if (tag instanceof NBTTagIntArray) { add(4); for (int value : ((NBTTagIntArray) tag).getIntArray()) add(Integer.toString(value).length() + 2); }
            else if (tag.getId() == 12) {
                // Long-array accessor names differ between mappings. Binary length times three bounds its SNBT size.
                NBTTagCompound wrapper = new NBTTagCompound(); wrapper.setTag("v", tag);
                final long allowance = (MAX_TILE_BYTES - size) / 3;
                final long[] written = {0};
                try {
                    CompressedStreamTools.write(wrapper, new DataOutputStream(new OutputStream() {
                        @Override public void write(int ignored) throws IOException { if (++written[0] > allowance) throw new IOException("NBT array budget exceeded"); }
                        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                            if (length > allowance - written[0]) throw new IOException("NBT array budget exceeded"); written[0] += length;
                        }
                    }));
                    add(written[0] * 3);
                } catch (IOException tooLarge) { throw new LimitException("/scenes", "NBT long array exceeds the bounded serialization budget"); }
            } else add(tag.toString().getBytes(StandardCharsets.UTF_8).length);
        }
    }
}
