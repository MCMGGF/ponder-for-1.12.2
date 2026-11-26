package net.createmod.ponder.foundation.ui;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.Vec3iAccessor;
import net.createmod.ponder.foundation.content.mmce.MmcePreviewSafety;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.SaveHandlerMP;
import net.minecraft.world.storage.WorldInfo;

/**
 * Display-only world with no save, server, client connection or ticking lifecycle.
 * Only the explicit population methods may change its local display data.
 */
public final class DetachedPonderWorld extends World {

    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int MAX_CACHED_CHUNKS = 16;
    private static final Set<String> WARNED_TILES = Collections.synchronizedSet(new HashSet<String>());

    private final boolean mergeTileDefaults;
    private final Map<BlockPos, IBlockState> states = new LinkedHashMap<BlockPos, IBlockState>();
    private final Map<BlockPos, NBTTagCompound> tileData = new LinkedHashMap<BlockPos, NBTTagCompound>();
    private final Map<BlockPos, TileEntity> tiles = new LinkedHashMap<BlockPos, TileEntity>();
    private final Set<BlockPos> creatingTiles = new HashSet<BlockPos>();
    private final Set<BlockPos> failedTiles = new HashSet<BlockPos>();

    public DetachedPonderWorld() {
        this(false);
    }

    DetachedPonderWorld(boolean mergeTileDefaults) {
        super(new SaveHandlerMP(), new WorldInfo(
            new WorldSettings(0L, GameType.CREATIVE, false, false, WorldType.FLAT), "Ponder Preview"),
            new WorldProviderSurface(), new Profiler(), true);
        this.mergeTileDefaults = mergeTileDefaults;
        provider.setWorld(this);
        chunkProvider = createChunkProvider();
        mapStorage = new MapStorage(null);
        eventListeners.clear();
        setWorldTime(6000L);
    }

    public EntityPlayer createPlayer() {
        return new EntityPlayer(this, new GameProfile(
            UUID.nameUUIDFromBytes("ponder:detached-preview".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "PonderPreview")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
    }

    public void putBlock(BlockPos pos, IBlockState state, @Nullable NBTTagCompound nbt) {
        BlockPos key = new BlockPos(pos);
        IBlockState previousState = states.get(key);
        NBTTagCompound previousNbt = tileData.get(key);
        boolean sameNbt = previousNbt == null ? nbt == null : previousNbt.equals(nbt);
        if (previousState != state || !sameNbt) {
            tiles.remove(key);
            failedTiles.remove(key);
        }
        if (state == null || state.getBlock() == Blocks.AIR) {
            states.remove(key);
            tileData.remove(key);
            return;
        }
        states.put(key, state);
        if (nbt == null) {
            tileData.remove(key);
        } else {
            tileData.put(key, nbt.copy());
        }
    }

    public void setBlocks(Map<BlockPos, IBlockState> blocks, Map<BlockPos, NBTTagCompound> nbt) {
        states.keySet().retainAll(blocks.keySet());
        tileData.keySet().retainAll(blocks.keySet());
        tiles.keySet().retainAll(blocks.keySet());
        failedTiles.retainAll(blocks.keySet());
        for (Map.Entry<BlockPos, IBlockState> entry : blocks.entrySet()) {
            putBlock(entry.getKey(), entry.getValue(), nbt.get(entry.getKey()));
        }
    }

    public void clear() {
        states.clear();
        tileData.clear();
        tiles.clear();
        creatingTiles.clear();
        failedTiles.clear();
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        IBlockState state = states.get(pos);
        return state == null ? Blocks.AIR.getDefaultState() : state;
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        TileEntity cached = tiles.get(pos);
        if (cached != null || failedTiles.contains(pos) || creatingTiles.contains(pos)) {
            return cached;
        }
        IBlockState state = states.get(pos);
        if (state == null) {
            return null;
        }
        // Native controller NBT and TESRs reach MMCE's global scheduling/render queues.
        if (MmcePreviewSafety.isNativeControllerBlock(state.getBlock().getClass())) {
            return null;
        }
        if (!state.getBlock().hasTileEntity(state)) {
            return null;
        }
        BlockPos key = new BlockPos(pos);
        creatingTiles.add(key);
        try {
            TileEntity tile = state.getBlock().createTileEntity(this, state);
            if (tile == null) {
                failedTiles.add(key);
                return null;
            }
            tile.setWorld(this);
            tile.setPos(key);
            // Publish before deserialization: some addon tiles query their own position.
            tiles.put(key, tile);
            NBTTagCompound nbt = tileData.get(key);
            if (nbt != null) {
                NBTTagCompound displayNbt;
                if (mergeTileDefaults) {
                    // Native GUI fixtures describe overrides of a freshly placed tile.
                    displayNbt = tile.writeToNBT(new NBTTagCompound());
                    String id = displayNbt.getString("id");
                    displayNbt.merge(nbt);
                    displayNbt.setString("id", id);
                } else {
                    displayNbt = nbt.copy();
                }
                displayNbt.setInteger("x", Vec3iAccessor.x(pos));
                displayNbt.setInteger("y", Vec3iAccessor.y(pos));
                displayNbt.setInteger("z", Vec3iAccessor.z(pos));
                tile.readFromNBT(displayNbt);
                tile.setWorld(this);
                tile.setPos(key);
            }
            return tile;
        } catch (RuntimeException | LinkageError failure) {
            tiles.remove(key);
            failedTiles.add(key);
            String id = String.valueOf(state.getBlock().getRegistryName());
            if (WARNED_TILES.add(id)) {
                Ponder.LOGGER.warn("Ponder could not create detached preview tile " + id + " at " + pos, failure);
            }
            return null;
        } finally {
            creatingTiles.remove(key);
        }
    }

    @Override
    protected IChunkProvider createChunkProvider() {
        return new IChunkProvider() {
            private final Map<Long, Chunk> chunks = new LinkedHashMap<Long, Chunk>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Chunk> eldest) {
                    return size() > MAX_CACHED_CHUNKS;
                }
            };

            @Override
            public Chunk getLoadedChunk(int x, int z) {
                return provideChunk(x, z);
            }

            @Override
            public Chunk provideChunk(int x, int z) {
                long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
                Chunk chunk = chunks.get(Long.valueOf(key));
                if (chunk == null) {
                    chunk = new EmptyChunk(DetachedPonderWorld.this, x, z) {
                        @Override
                        public IBlockState getBlockState(BlockPos pos) {
                            return DetachedPonderWorld.this.getBlockState(pos);
                        }

                        @Override
                        public TileEntity getTileEntity(BlockPos pos, Chunk.EnumCreateEntityType type) {
                            return DetachedPonderWorld.this.getTileEntity(pos);
                        }
                    };
                    chunks.put(Long.valueOf(key), chunk);
                }
                return chunk;
            }

            @Override
            public boolean tick() {
                return false;
            }

            @Override
            public String makeString() {
                return "Ponder detached display";
            }

            @Override
            public boolean isChunkGeneratedAt(int x, int z) {
                return true;
            }
        };
    }

    @Override
    protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
        return true;
    }

    @Override
    public boolean isBlockLoaded(BlockPos pos, boolean allowEmpty) {
        return true;
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        return getBlockState(pos).getBlock() == Blocks.AIR;
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return Biomes.PLAINS;
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue) {
        return FULL_BRIGHT;
    }

    @Override
    public int getLight(BlockPos pos, boolean checkNeighbors) {
        return 15;
    }

    @Override
    public int getLightFromNeighbors(BlockPos pos) {
        return 15;
    }

    @Override
    public int getLightFor(EnumSkyBlock type, BlockPos pos) {
        return 15;
    }

    @Override
    public float getLightBrightness(BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean fallback) {
        IBlockState state = getBlockState(pos);
        return state.getBlock() != Blocks.AIR && state.isSideSolid(this, pos, side);
    }

    @Override
    public boolean setBlockState(BlockPos pos, IBlockState state, int flags) {
        return false;
    }

    @Override
    public boolean setBlockState(BlockPos pos, IBlockState state) {
        return false;
    }

    @Override
    public boolean setBlockToAir(BlockPos pos) {
        return false;
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean drops) {
        return false;
    }

    @Override
    public void setTileEntity(BlockPos pos, TileEntity tile) {
    }

    @Override
    public void removeTileEntity(BlockPos pos) {
    }

    @Override
    public boolean addTileEntity(TileEntity tile) {
        return false;
    }

    @Override
    public boolean spawnEntity(Entity entity) {
        return false;
    }

    @Override
    public void scheduleUpdate(BlockPos pos, Block block, int delay) {
    }

    @Override
    public void updateBlockTick(BlockPos pos, Block block, int delay, int priority) {
    }

    @Override
    public void scheduleBlockUpdate(BlockPos pos, Block block, int delay, int priority) {
    }

    @Override
    public void addBlockEvent(BlockPos pos, Block block, int eventId, int parameter) {
    }

    @Override
    public void notifyNeighborsOfStateChange(BlockPos pos, Block block, boolean observers) {
    }

    @Override
    public void neighborChanged(BlockPos pos, Block block, BlockPos fromPos) {
    }

    @Override
    public void observedNeighborChanged(BlockPos pos, Block block, BlockPos fromPos) {
    }

    @Override
    public void tick() {
    }

    @Override
    public void updateEntities() {
    }

    @Override
    public void sendPacketToServer(Packet<?> packet) {
    }
}
