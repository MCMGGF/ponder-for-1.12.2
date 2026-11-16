package net.createmod.ponder.authoring.assets;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.createmod.ponder.foundation.PonderSchematic;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/** Reads only already-loaded chunks. No real-world placement, entity capture or player inventory API exists here. */
public final class BlueprintCapture {
    public enum NbtPolicy { SAFE_VISUAL_ID_ONLY, FULL_NBT_EXPLICIT_OPT_IN }

    public interface LoadedWorldView {
        void checkReadThread() throws IOException;
        boolean isChunkLoaded(int x, int z);
        IBlockState readLoadedBlock(BlockPos position) throws IOException;
        NBTTagCompound readExistingBlockEntity(BlockPos position) throws IOException;
        default NBTTagCompound readSafeBlockEntityId(BlockPos position) throws IOException {
            NBTTagCompound value = readExistingBlockEntity(position);
            return value == null ? null : sanitize(value, position, NbtPolicy.SAFE_VISUAL_ID_ONLY);
        }
    }

    private BlueprintCapture() { }

    public static LoadedWorldView fromLoadedWorld(final World world, final Thread owningWorldThread) {
        if (world == null || owningWorldThread == null) throw new IllegalArgumentException("World and owning thread are required");
        return new LoadedWorldView() {
            @Override public void checkReadThread() throws IOException {
                if (Thread.currentThread() != owningWorldThread) throw new IOException("Capture must run on the supplied owning world thread");
            }
            private Chunk chunk(BlockPos pos) throws IOException {
                checkReadThread();
                Chunk chunk = world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk == null) throw new IOException("A capture chunk was unloaded; no chunk will be generated");
                return chunk;
            }
            @Override public boolean isChunkLoaded(int x, int z) { return world.getChunkProvider().getLoadedChunk(x, z) != null; }
            @Override public IBlockState readLoadedBlock(BlockPos pos) throws IOException { return chunk(pos).getBlockState(pos); }
            @Override public NBTTagCompound readExistingBlockEntity(BlockPos pos) throws IOException {
                TileEntity tile = chunk(pos).getTileEntityMap().get(pos);
                return tile == null ? null : tile.writeToNBT(new NBTTagCompound()).copy();
            }
            @Override public NBTTagCompound readSafeBlockEntityId(BlockPos pos) throws IOException {
                TileEntity tile = chunk(pos).getTileEntityMap().get(pos);
                if (tile == null) return null;
                ResourceLocation id = TileEntity.getKey(tile.getClass());
                if (id == null) throw new IOException("Unregistered tile entity cannot be captured safely");
                NBTTagCompound value = new NBTTagCompound(); value.setString("id", id.toString());
                return value;
            }
        };
    }

    public static Result capture(LoadedWorldView world, BlockPos first, BlockPos second) throws IOException {
        return capture(world, first, second, NbtPolicy.SAFE_VISUAL_ID_ONLY);
    }

    public static Result capture(LoadedWorldView world, BlockPos first, BlockPos second, NbtPolicy policy) throws IOException {
        if (world == null || first == null || second == null || policy == null) throw new IOException("Capture inputs are required");
        int x0 = Math.min(first.getX(), second.getX()), y0 = Math.min(first.getY(), second.getY()), z0 = Math.min(first.getZ(), second.getZ());
        int x1 = Math.max(first.getX(), second.getX()), y1 = Math.max(first.getY(), second.getY()), z1 = Math.max(first.getZ(), second.getZ());
        if ((long) x1 - x0 >= 64 || (long) y1 - y0 >= 64 || (long) z1 - z0 >= 64
            || Math.abs((long) x0) > 30000000 || Math.abs((long) x1) > 30000000
            || Math.abs((long) y0) > 30000000 || Math.abs((long) y1) > 30000000
            || Math.abs((long) z0) > 30000000 || Math.abs((long) z1) > 30000000) throw new IOException("Capture exceeds 64 cubed or coordinate limits");
        world.checkReadThread();
        for (int cx = x0 >> 4; cx <= (x1 >> 4); cx++) for (int cz = z0 >> 4; cz <= (z1 >> 4); cz++)
            if (!world.isChunkLoaded(cx, cz)) throw new IOException("All capture chunks must already be loaded");
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<>();
        Map<BlockPos, NBTTagCompound> nbt = new LinkedHashMap<>();
        int stripped = 0;
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            BlockPos pos = new BlockPos(x, y, z), local = new BlockPos(x - x0, y - y0, z - z0);
            IBlockState state = world.readLoadedBlock(pos);
            if (state == null) throw new IOException("A loaded block state was unavailable");
            blocks.put(local, state);
            if (state.getBlock() == Blocks.AIR) continue;
            NBTTagCompound tile = policy == NbtPolicy.SAFE_VISUAL_ID_ONLY
                ? world.readSafeBlockEntityId(pos) : world.readExistingBlockEntity(pos);
            if (tile == null) continue;
            NBTTagCompound safe = sanitize(tile, local, policy);
            if (policy == NbtPolicy.SAFE_VISUAL_ID_ONLY) stripped++;
            nbt.put(local, safe);
        }
        PonderSchematic schematic = PonderSchematic.of(new BlockPos(x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1), blocks, nbt);
        return new Result(schematic, new BlockPos(x0, y0, z0), policy, stripped);
    }

    public static NBTTagCompound sanitize(NBTTagCompound source, BlockPos local, NbtPolicy policy) {
        NBTTagCompound copy = policy == NbtPolicy.FULL_NBT_EXPLICIT_OPT_IN ? source.copy() : new NBTTagCompound();
        if (source.hasKey("id", 8)) copy.setString("id", source.getString("id"));
        copy.setInteger("x", local.getX()); copy.setInteger("y", local.getY()); copy.setInteger("z", local.getZ());
        return copy;
    }

    public static final class Result {
        public final PonderSchematic schematic;
        public final BlockPos origin;
        public final NbtPolicy nbtPolicy;
        /** Number of privacy-limited tile records; safe capture deliberately does not inspect their full payloads. */
        public final int strippedTilePayloads;
        Result(PonderSchematic schematic, BlockPos origin, NbtPolicy policy, int stripped) {
            this.schematic = schematic; this.origin = origin; this.nbtPolicy = policy; this.strippedTilePayloads = stripped;
        }
    }
}
