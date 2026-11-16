package net.createmod.ponder.foundation;

import java.io.IOException;
import java.io.InputStream;
import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.PushbackInputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.createmod.ponder.Ponder;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import java.util.zip.GZIPInputStream;

public final class PonderSchematic {
    public static final int MAX_EXTERNAL_AXIS = 64;
    public static final int MAX_BLOCKS = 262144;
    public static final int MAX_COORDINATE = 30000000;
    private static final long MAX_NBT_BYTES = 64L * 1024 * 1024;

    private static final PonderSchematic EMPTY =
        new PonderSchematic(new BlockPos(0, 0, 0), Collections.<BlockPos, IBlockState>emptyMap());
    private static final Set<String> WARNED_LOCATIONS = Collections.synchronizedSet(new HashSet<String>());

    private final BlockPos size;
    private final Map<BlockPos, IBlockState> blocks;
    private final Map<BlockPos, NBTTagCompound> blockEntityNbt;

    /** Shared across a pack/context transaction; counts decoded NBT bytes before retaining another structure. */
    public static final class DecodeBudget {
        private final long byteLimit;
        private final int blockLimit;
        private long decodedBytes;
        private int decodedBlocks;
        public DecodeBudget(long byteLimit, int blockLimit) {
            if (byteLimit < 1 || byteLimit > 256L * 1024 * 1024 || blockLimit < 1 || blockLimit > 1048576)
                throw new IllegalArgumentException("Decode budget exceeds the bounded transaction limits");
            this.byteLimit = byteLimit; this.blockLimit = blockLimit;
        }
        public long getDecodedBytes() { return decodedBytes; }
        public int getDecodedBlocks() { return decodedBlocks; }
        private long remainingBytes() { return byteLimit - decodedBytes; }
        private void consumeBytes(int count) throws IOException {
            if (count > remainingBytes()) throw new IOException("Cumulative decoded NBT byte budget exceeded");
            decodedBytes += count;
        }
        private void consumeBlocks(int count) throws IOException {
            if (count > blockLimit - decodedBlocks) throw new IOException("Cumulative decoded NBT block budget exceeded");
            decodedBlocks += count;
        }
    }

    private PonderSchematic(BlockPos size, Map<BlockPos, IBlockState> blocks) {
        this(size, blocks, Collections.<BlockPos, NBTTagCompound>emptyMap());
    }

    private PonderSchematic(BlockPos size, Map<BlockPos, IBlockState> blocks, Map<BlockPos, NBTTagCompound> nbt) {
        this.size = copyPosition(size);
        this.blocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
        Map<BlockPos, NBTTagCompound> copied = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, NBTTagCompound> entry : nbt.entrySet())
            copied.put(copyPosition(entry.getKey()), entry.getValue().copy());
        this.blockEntityNbt = Collections.unmodifiableMap(copied);
    }

    public BlockPos getSize() {
        return size;
    }

    public Map<BlockPos, IBlockState> getBlocks() {
        return blocks;
    }

    public Map<BlockPos, NBTTagCompound> getBlockEntityNbtCopies() {
        Map<BlockPos, NBTTagCompound> result = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, NBTTagCompound> entry : blockEntityNbt.entrySet())
            result.put(copyPosition(entry.getKey()), entry.getValue().copy());
        return Collections.unmodifiableMap(result);
    }

    public NBTTagCompound getBlockEntityNbt(BlockPos position) {
        NBTTagCompound value = blockEntityNbt.get(position);
        return value == null ? null : value.copy();
    }

    public static PonderSchematic of(BlockPos size, Map<BlockPos, IBlockState> blocks,
        Map<BlockPos, NBTTagCompound> nbt) {
        if (size == null || blocks == null || nbt == null || blocks.size() > MAX_BLOCKS
            || size.getX() < 0 || size.getY() < 0 || size.getZ() < 0) throw new IllegalArgumentException("Invalid schematic bounds or block budget");
        Map<BlockPos, IBlockState> copied = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, IBlockState> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos == null || entry.getValue() == null || !coordinate(pos.getX()) || !coordinate(pos.getY()) || !coordinate(pos.getZ()))
                throw new IllegalArgumentException("Invalid schematic block or coordinate");
            copied.put(copyPosition(pos), entry.getValue());
        }
        for (Map.Entry<BlockPos, NBTTagCompound> entry : nbt.entrySet())
            if (!copied.containsKey(entry.getKey()) || entry.getValue() == null) throw new IllegalArgumentException("Tile NBT must belong to an existing block");
        return new PonderSchematic(size, copied, nbt);
    }

    public PonderSchematic overlay(PonderSchematic extra, BlockPos offset, boolean includeAir, boolean replaceExisting) {
        if (extra == null || offset == null) throw new IllegalArgumentException("Overlay and offset are required");
        Map<BlockPos, IBlockState> merged = new LinkedHashMap<>(blocks);
        Map<BlockPos, NBTTagCompound> nbt = new LinkedHashMap<>(getBlockEntityNbtCopies());
        for (Map.Entry<BlockPos, IBlockState> entry : extra.blocks.entrySet()) {
            if (!includeAir && entry.getValue().getBlock() == Blocks.AIR) continue;
            BlockPos source = entry.getKey();
            long x = (long) source.getX() + offset.getX(), y = (long) source.getY() + offset.getY(), z = (long) source.getZ() + offset.getZ();
            if (Math.abs(x) > MAX_COORDINATE || Math.abs(y) > MAX_COORDINATE || Math.abs(z) > MAX_COORDINATE)
                throw new IllegalArgumentException("Overlay coordinate exceeds the preview budget");
            BlockPos at = new BlockPos((int) x, (int) y, (int) z);
            if (!replaceExisting && merged.containsKey(at)) throw new IllegalArgumentException("Overlay block collision at " + at);
            merged.put(at, entry.getValue()); nbt.remove(at);
            NBTTagCompound tile = extra.getBlockEntityNbt(source);
            if (tile != null) { tile.setInteger("x", at.getX()); tile.setInteger("y", at.getY()); tile.setInteger("z", at.getZ()); nbt.put(at, tile); }
            if (merged.size() > MAX_BLOCKS) throw new IllegalArgumentException("Overlay block budget exceeded");
        }
        int minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
        for (BlockPos pos : merged.keySet()) {
            minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX() + 1); maxY = Math.max(maxY, pos.getY() + 1); maxZ = Math.max(maxZ, pos.getZ() + 1);
        }
        return of(new BlockPos(maxX - minX, maxY - minY, maxZ - minZ), merged, nbt);
    }

    public NBTTagCompound toNbt() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList dimensions = new NBTTagList();
        dimensions.appendTag(new NBTTagInt(size.getX())); dimensions.appendTag(new NBTTagInt(size.getY())); dimensions.appendTag(new NBTTagInt(size.getZ()));
        root.setTag("size", dimensions);
        Map<IBlockState, Integer> palette = new LinkedHashMap<>();
        NBTTagList states = new NBTTagList(), positions = new NBTTagList();
        for (Map.Entry<BlockPos, IBlockState> entry : blocks.entrySet()) {
            Integer index = palette.get(entry.getValue());
            if (index == null) { index = palette.size(); palette.put(entry.getValue(), index); states.appendTag(NBTUtil.writeBlockState(new NBTTagCompound(), entry.getValue())); }
            NBTTagCompound block = new NBTTagCompound(); NBTTagList pos = new NBTTagList();
            pos.appendTag(new NBTTagInt(entry.getKey().getX())); pos.appendTag(new NBTTagInt(entry.getKey().getY())); pos.appendTag(new NBTTagInt(entry.getKey().getZ()));
            block.setTag("pos", pos); block.setInteger("state", index);
            NBTTagCompound tile = getBlockEntityNbt(entry.getKey()); if (tile != null) block.setTag("nbt", tile);
            positions.appendTag(block);
        }
        root.setTag("palette", states); root.setTag("blocks", positions); root.setTag("entities", new NBTTagList());
        return root;
    }

    /** Consumes and closes the supplied stream; both encoded bytes and NBT accounting are bounded. */
    public static PonderSchematic loadExternal(InputStream source) throws IOException {
        return loadExternal(source, new DecodeBudget(MAX_NBT_BYTES, MAX_BLOCKS));
    }

    public static PonderSchematic loadExternal(InputStream source, DecodeBudget budget) throws IOException {
        if (source == null) throw new IOException("Missing external NBT stream");
        try (PushbackInputStream encoded = new PushbackInputStream(new LimitedInput(source, MAX_NBT_BYTES), 2)) {
            if (budget == null || budget.remainingBytes() < 1) throw new IOException("External NBT decode budget is exhausted");
            int first = encoded.read(), second = encoded.read();
            if (second >= 0) encoded.unread(second); if (first >= 0) encoded.unread(first);
            InputStream decoded = first == 0x1f && second == 0x8b ? new GZIPInputStream(encoded) : encoded;
            try (DataInputStream input = new DataInputStream(new LimitedInput(new BudgetInput(decoded, budget), MAX_NBT_BYTES))) {
                long allocationBudget = Math.min(128L * 1024 * 1024, budget.remainingBytes() * 2 + 1024 * 1024);
                NBTTagCompound tag = CompressedStreamTools.read(input, new NBTSizeTracker(allocationBudget));
                if (input.read() != -1) throw new IOException("Trailing bytes after external structure NBT");
                return decode(tag, new ResourceLocation("ponder_authoring", "external"), true, budget);
            }
        } catch (RuntimeException failure) { throw new IOException("Invalid external structure NBT: " + failure.getMessage(), failure); }
    }

    public static PonderSchematic loadExternal(Path file) throws IOException {
        return loadExternal(file, new DecodeBudget(MAX_NBT_BYTES, MAX_BLOCKS));
    }

    public static PonderSchematic loadExternal(Path file, DecodeBudget budget) throws IOException {
        if (file == null || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file))
            throw new IOException("External structure must be a regular non-symbolic file");
        return loadExternal(Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS), budget);
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public static PonderSchematic empty() {
        return EMPTY;
    }

    public static PonderSchematic load(ResourceLocation location) {
        String resourcePath = "assets/" + location.getNamespace() + "/ponder/" + location.getPath() + ".nbt";
        InputStream stream = PonderSchematic.class.getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            warnOnce(location.toString(), "Ponder schematic resource is missing: " + resourcePath, null);
            return empty();
        }

        try (InputStream input = stream) {
            NBTTagCompound compound = CompressedStreamTools.readCompressed(input);
            return fromNbt(compound, location);
        } catch (IOException e) {
            warnOnce(location.toString(), "Failed to read Ponder schematic: " + resourcePath, e);
            return empty();
        } catch (RuntimeException e) {
            warnOnce(location.toString(), "Failed to parse Ponder schematic: " + resourcePath, e);
            return empty();
        }
    }

    private static void warnOnce(String key, String message, Throwable cause) {
        if (!WARNED_LOCATIONS.add(key)) {
            return;
        }
        if (cause == null) {
            Ponder.LOGGER.warn(message);
        } else {
            Ponder.LOGGER.warn(message, cause);
        }
    }

    private static PonderSchematic fromNbt(NBTTagCompound compound, ResourceLocation location) {
        try { return decode(compound, location, false); }
        catch (IOException invalid) { throw new IllegalArgumentException(invalid.getMessage(), invalid); }
    }

    private static PonderSchematic decode(NBTTagCompound compound, ResourceLocation location, boolean strict) throws IOException {
        return decode(compound, location, strict, null);
    }

    private static PonderSchematic decode(NBTTagCompound compound, ResourceLocation location, boolean strict, DecodeBudget budget) throws IOException {
        if (strict && (!compound.hasKey("size", 9) || !compound.hasKey("palette", 9) || !compound.hasKey("blocks", 9)))
            throw new IOException("External structure requires size, palette and blocks lists");
        NBTTagList sizeTag = compound.getTagList("size", 3);
        BlockPos size = sizeTag.tagCount() >= 3
            ? new BlockPos(sizeTag.getIntAt(0), sizeTag.getIntAt(1), sizeTag.getIntAt(2))
            : new BlockPos(0, 0, 0);
        if (strict && (sizeTag.tagCount() != 3 || size.getX() < 1 || size.getY() < 1 || size.getZ() < 1
            || size.getX() > MAX_EXTERNAL_AXIS || size.getY() > MAX_EXTERNAL_AXIS || size.getZ() > MAX_EXTERNAL_AXIS))
            throw new IOException("External structure dimensions must each be 1..64");

        Map<Integer, IBlockState> palette = new LinkedHashMap<Integer, IBlockState>();
        NBTTagList paletteTag = compound.getTagList("palette", 10);
        if (strict && (paletteTag.tagCount() < 1 || paletteTag.tagCount() > MAX_BLOCKS)) throw new IOException("Invalid external palette size");
        for (int index = 0; index < paletteTag.tagCount(); index++) {
            NBTTagCompound stateTag = paletteTag.getCompoundTagAt(index);
            if (strict) validatePaletteState(stateTag);
            IBlockState state = NBTUtil.readBlockState(stateTag);
            String declaredId = stateTag.getString("Name");
            if (state.getBlock() == Blocks.AIR && !"minecraft:air".equals(declaredId)) {
                warnOnce(location + "|block:" + declaredId,
                    "Ponder schematic " + location + " references an unavailable block: " + declaredId, null);
            }
            palette.put(Integer.valueOf(index), state);
        }

        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<BlockPos, IBlockState>();
        Map<BlockPos, NBTTagCompound> nbt = new LinkedHashMap<>();
        NBTTagList blocksTag = compound.getTagList("blocks", 10);
        if (strict) {
            NBTTagList original = (NBTTagList) compound.getTag("blocks");
            if (original.tagCount() != 0 && original.getTagType() != 10) throw new IOException("External blocks list requires compounds");
        }
        if (strict && blocksTag.tagCount() > MAX_BLOCKS) throw new IOException("External structure block budget exceeded");
        if (budget != null) budget.consumeBlocks(blocksTag.tagCount());
        for (int index = 0; index < blocksTag.tagCount(); index++) {
            NBTTagCompound blockTag = blocksTag.getCompoundTagAt(index);
            NBTTagList posTag = blockTag.getTagList("pos", 3);
            if (posTag.tagCount() < 3) {
                if (strict) throw new IOException("External structure block position must have three integers");
                continue;
            }

            IBlockState state = palette.get(Integer.valueOf(blockTag.getInteger("state")));
            if (state == null) {
                if (strict) throw new IOException("External block references an invalid palette index");
                continue;
            }

            BlockPos pos = new BlockPos(posTag.getIntAt(0), posTag.getIntAt(1), posTag.getIntAt(2));
            if (strict && (posTag.tagCount() != 3 || !blockTag.hasKey("state", 3) || pos.getX() < 0 || pos.getY() < 0
                || pos.getZ() < 0 || pos.getX() >= size.getX() || pos.getY() >= size.getY() || pos.getZ() >= size.getZ()
                || blocks.containsKey(pos))) throw new IOException("Duplicate, malformed or out-of-bounds structure block");
            blocks.put(pos, state);
            if (blockTag.hasKey("nbt", 10)) nbt.put(pos, blockTag.getCompoundTag("nbt").copy());
            else if (strict && blockTag.hasKey("nbt")) throw new IOException("Block entity data must be a compound");
        }

        return new PonderSchematic(size, blocks, nbt);
    }

    private static void validatePaletteState(NBTTagCompound tag) throws IOException {
        String name = tag.getString("Name");
        if (!name.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IOException("Invalid block registry ID: " + name);
        ResourceLocation id = new ResourceLocation(name);
        if (!Block.REGISTRY.containsKey(id)) throw new IOException("Unavailable block registry ID: " + name);
        Block block = Block.REGISTRY.getObject(id);
        if (tag.hasKey("Properties") && !tag.hasKey("Properties", 10)) throw new IOException("Block properties must be a compound");
        NBTTagCompound properties = tag.getCompoundTag("Properties");
        for (String key : properties.getKeySet()) {
            IProperty<?> matched = null;
            for (IProperty<?> property : block.getBlockState().getProperties()) if (property.getName().equals(key)) matched = property;
            if (matched == null || !properties.hasKey(key, 8) || !matched.parseValue(properties.getString(key)).isPresent())
                throw new IOException("Invalid block property " + name + ":" + key);
        }
    }

    private static BlockPos copyPosition(BlockPos pos) { return new BlockPos(pos.getX(), pos.getY(), pos.getZ()); }
    private static boolean coordinate(int value) { return Math.abs((long) value) <= MAX_COORDINATE; }

    private static final class BudgetInput extends FilterInputStream {
        private final DecodeBudget budget;
        BudgetInput(InputStream input, DecodeBudget budget) { super(input); this.budget = budget; }
        @Override public int read() throws IOException {
            int value = in.read(); if (value >= 0) budget.consumeBytes(1); return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = in.read(bytes, offset, (int) Math.min(length, Math.max(1L, budget.remainingBytes() + 1)));
            if (read > 0) budget.consumeBytes(read); return read;
        }
    }

    private static final class LimitedInput extends FilterInputStream {
        private final long limit;
        private long count;
        LimitedInput(InputStream input, long limit) { super(input); this.limit = limit; }
        @Override public int read() throws IOException {
            int value = in.read(); if (value >= 0 && ++count > limit) throw new IOException("External NBT byte budget exceeded"); return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = in.read(bytes, offset, (int) Math.min(length, Math.max(1L, limit - count + 1)));
            if (read > 0 && (count += read) > limit) throw new IOException("External NBT byte budget exceeded"); return read;
        }
        @Override public long skip(long requested) throws IOException {
            long skipped = 0; byte[] scratch = new byte[8192];
            while (skipped < requested) { int read = read(scratch, 0, (int) Math.min(scratch.length, requested - skipped)); if (read < 0) break; skipped += read; }
            return skipped;
        }
    }
}
