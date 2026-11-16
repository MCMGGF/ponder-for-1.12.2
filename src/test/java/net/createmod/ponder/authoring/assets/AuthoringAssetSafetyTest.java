package net.createmod.ponder.authoring.assets;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.createmod.ponder.foundation.PonderSchematic;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthoringAssetSafetyTest {
    Path fixture;
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    @BeforeEach void setup() throws Exception { fixture = Files.createTempDirectory(Files.createDirectories(Paths.get("build/test-fixtures/authoring-assets").toAbsolutePath()), "case-"); }
    @AfterEach void cleanup() throws Exception {
        try (Stream<Path> paths = Files.walk(fixture)) { for (Path path : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.delete(path); }
    }
    static NBTTagCompound privateTile() {
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("id", "minecraft:chest"); tag.setString("owner", "private-player");
        tag.setString("Items", "private-items"); tag.setString("ForgeCaps", "private-capability"); return tag;
    }
    static PonderSchematic schematic() {
        return PonderSchematic.of(new BlockPos(1, 1, 1), Collections.singletonMap(BlockPos.ORIGIN, Blocks.CHEST.getDefaultState()), Collections.singletonMap(BlockPos.ORIGIN, privateTile()));
    }
    static byte[] encoded(NBTTagCompound tag, boolean compressed) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (compressed) CompressedStreamTools.writeCompressed(tag, bytes); else CompressedStreamTools.write(tag, new DataOutputStream(bytes));
        return bytes.toByteArray();
    }
    static PonderSchematic read(NBTTagCompound tag) throws Exception { return PonderSchematic.loadExternal(new ByteArrayInputStream(encoded(tag, true))); }

    @Test void nbtCopiesAreImmutableAcrossFactoryGettersAndExternalRoundtrip() throws Exception {
        NBTTagCompound original = privateTile();
        PonderSchematic source = PonderSchematic.of(new BlockPos(1, 1, 1), Collections.singletonMap(BlockPos.ORIGIN, Blocks.CHEST.getDefaultState()), Collections.singletonMap(BlockPos.ORIGIN, original));
        original.setString("owner", "changed"); source.getBlockEntityNbt(BlockPos.ORIGIN).setString("owner", "changed-again");
        source.getBlockEntityNbtCopies().get(BlockPos.ORIGIN).setString("owner", "third");
        assertEquals("private-player", source.getBlockEntityNbt(BlockPos.ORIGIN).getString("owner"));
        for (boolean compressed : new boolean[] {true, false}) {
            PonderSchematic loaded = PonderSchematic.loadExternal(new ByteArrayInputStream(encoded(source.toNbt(), compressed)));
            assertEquals(Blocks.CHEST, loaded.getBlocks().get(BlockPos.ORIGIN).getBlock());
            assertEquals("private-items", loaded.getBlockEntityNbt(BlockPos.ORIGIN).getString("Items"));
        }
        assertThrows(UnsupportedOperationException.class, () -> source.getBlocks().clear());
    }

    @Test void strictExternalNbtRejectsMalformedPalettePositionsTypesAndTrailingBytes() throws Exception {
        NBTTagCompound invalid = schematic().toNbt(); invalid.getTagList("size", 3).set(0, new NBTTagInt(65));
        final NBTTagCompound dimensions = invalid; assertThrows(IOException.class, () -> read(dimensions));
        invalid = schematic().toNbt(); invalid.getTagList("palette", 10).getCompoundTagAt(0).setString("Name", "missing:no_block");
        final NBTTagCompound missing = invalid; assertThrows(IOException.class, () -> read(missing));
        invalid = schematic().toNbt(); invalid.getTagList("blocks", 10).appendTag(invalid.getTagList("blocks", 10).getCompoundTagAt(0).copy());
        final NBTTagCompound duplicate = invalid; assertThrows(IOException.class, () -> read(duplicate));
        invalid = schematic().toNbt(); invalid.getTagList("blocks", 10).getCompoundTagAt(0).setInteger("state", 9);
        final NBTTagCompound index = invalid; assertThrows(IOException.class, () -> read(index));
        invalid = schematic().toNbt(); invalid.removeTag("blocks"); final NBTTagCompound absent = invalid; assertThrows(IOException.class, () -> read(absent));
        invalid = schematic().toNbt(); NBTTagList wrong = new NBTTagList(); wrong.appendTag(new NBTTagInt(1)); invalid.setTag("blocks", wrong);
        final NBTTagCompound wrongList = invalid; assertThrows(IOException.class, () -> read(wrongList));
        byte[] raw = encoded(schematic().toNbt(), false), trailing = java.util.Arrays.copyOf(raw, raw.length + 1);
        assertThrows(IOException.class, () -> PonderSchematic.loadExternal(new ByteArrayInputStream(trailing)));
    }

    @Test void strictExternalNbtRejectsUnknownPropertiesAndOutOfBounds() throws Exception {
        NBTTagCompound data = schematic().toNbt(), props = new NBTTagCompound(); props.setString("unknown", "yes");
        data.getTagList("palette", 10).getCompoundTagAt(0).setTag("Properties", props);
        assertThrows(IOException.class, () -> read(data));
        NBTTagCompound other = schematic().toNbt(); other.getTagList("blocks", 10).getCompoundTagAt(0).getTagList("pos", 3).set(0, new NBTTagInt(-1));
        assertThrows(IOException.class, () -> read(other));
    }

    @Test void overlaySupportsNegativeCoordinatesAirPolicyAndTileRelocationWithoutMutation() {
        PonderSchematic base = schematic(), extra = schematic();
        PonderSchematic result = base.overlay(extra, new BlockPos(-2, 1, 0), false, false);
        assertEquals(2, result.getBlocks().size()); assertEquals(3, result.getSize().getX());
        assertEquals(-2, result.getBlockEntityNbt(new BlockPos(-2, 1, 0)).getInteger("x"));
        assertFalse(base.getBlockEntityNbt(BlockPos.ORIGIN).hasKey("x"));
        assertThrows(IllegalArgumentException.class, () -> base.overlay(extra, BlockPos.ORIGIN, false, false));
        PonderSchematic air = PonderSchematic.of(new BlockPos(1, 1, 1), Collections.singletonMap(BlockPos.ORIGIN, Blocks.AIR.getDefaultState()), Collections.emptyMap());
        assertEquals(Blocks.CHEST, base.overlay(air, BlockPos.ORIGIN, false, true).getBlocks().get(BlockPos.ORIGIN).getBlock());
        PonderSchematic erased = base.overlay(air, BlockPos.ORIGIN, true, true);
        assertEquals(Blocks.AIR, erased.getBlocks().get(BlockPos.ORIGIN).getBlock()); assertNull(erased.getBlockEntityNbt(BlockPos.ORIGIN));
        assertThrows(IllegalArgumentException.class, () -> base.overlay(extra, new BlockPos(30000001, 0, 0), true, true));
    }

    @Test void poolFirstSaveCreatesOnlyDemandedRootAndDefaultRemovesPrivateData() throws Exception {
        Path root = fixture.resolve("config/ponder_legacy/authoring"); StructurePool pool = new StructurePool(root);
        assertFalse(Files.exists(root)); pool.saveNew("example:blueprint", schematic());
        assertEquals(Collections.singletonList("example:blueprint"), pool.listIds());
        NBTTagCompound safe = pool.resolve("example:blueprint").getBlockEntityNbt(BlockPos.ORIGIN);
        assertEquals(4, safe.getKeySet().size()); assertFalse(safe.hasKey("Items")); assertFalse(safe.hasKey("owner")); assertFalse(safe.hasKey("ForgeCaps"));
        assertThrows(IOException.class, () -> pool.saveNew("example:blueprint", schematic()));
        pool.saveNew("example:full", schematic(), BlueprintCapture.NbtPolicy.FULL_NBT_EXPLICIT_OPT_IN);
        assertEquals("private-items", pool.resolve("example:full").getBlockEntityNbt(BlockPos.ORIGIN).getString("Items"));
    }

    @Test void poolRejectsTraversalAbsoluteReservedAndOutOfBoundsAssets() throws Exception {
        for (String id : new String[] {"../escape", "x:../escape", "C:/escape", "x:a//b", "x:con", "x:a/../b", "/absolute", "x:A"})
            assertThrows(IOException.class, () -> StructurePool.normalizeId(id), id);
        StructurePool pool = new StructurePool(fixture);
        PonderSchematic huge = PonderSchematic.of(new BlockPos(65, 1, 1), Collections.singletonMap(BlockPos.ORIGIN, Blocks.STONE.getDefaultState()), Collections.emptyMap());
        assertThrows(IOException.class, () -> pool.saveNew("x:big", huge));
        assertFalse(Files.exists(fixture.resolve("assets")));
    }

    static class View implements BlueprintCapture.LoadedWorldView {
        int reads, fullReads, safeReads; boolean allLoaded = true, rightThread = true;
        @Override public void checkReadThread() throws IOException { if (!rightThread) throw new IOException("wrong thread"); }
        @Override public boolean isChunkLoaded(int x, int z) { return allLoaded || x == 0; }
        @Override public IBlockState readLoadedBlock(BlockPos p) { reads++; return Blocks.CHEST.getDefaultState(); }
        @Override public NBTTagCompound readExistingBlockEntity(BlockPos p) { fullReads++; return privateTile(); }
        @Override public NBTTagCompound readSafeBlockEntityId(BlockPos p) { safeReads++; NBTTagCompound id = new NBTTagCompound(); id.setString("id", "minecraft:chest"); return id; }
    }
    @Test void capturePreflightsAllChunksAndLimitsBeforeAnyRead() throws Exception {
        View view = new View(); view.allLoaded = false;
        assertThrows(IOException.class, () -> BlueprintCapture.capture(view, BlockPos.ORIGIN, new BlockPos(16, 0, 0))); assertEquals(0, view.reads);
        assertThrows(IOException.class, () -> BlueprintCapture.capture(view, BlockPos.ORIGIN, new BlockPos(64, 0, 0))); assertEquals(0, view.reads);
        view.allLoaded = true; view.rightThread = false;
        assertThrows(IOException.class, () -> BlueprintCapture.capture(view, BlockPos.ORIGIN, BlockPos.ORIGIN)); assertEquals(0, view.reads);
    }
    @Test void safeCaptureDoesNotInvokeFullTileSerializationAndFullRequiresExplicitPolicy() throws Exception {
        View view = new View(); BlockPos world = new BlockPos(-8, 30, 17);
        BlueprintCapture.Result result = BlueprintCapture.capture(view, world, world);
        assertEquals(world, result.origin); assertEquals(0, view.fullReads); assertEquals(1, view.safeReads);
        assertEquals(0, result.schematic.getBlockEntityNbt(BlockPos.ORIGIN).getInteger("x"));
        assertFalse(result.schematic.getBlockEntityNbt(BlockPos.ORIGIN).hasKey("Items"));
        BlueprintCapture.Result full = BlueprintCapture.capture(view, world, world, BlueprintCapture.NbtPolicy.FULL_NBT_EXPLICIT_OPT_IN);
        assertEquals(1, view.fullReads); assertEquals("private-player", full.schematic.getBlockEntityNbt(BlockPos.ORIGIN).getString("owner"));
    }

    @Test void portablePathsAndAtomicNewWritesRefuseAliasesAndOverwrite() throws Exception {
        AuthoringAssetPaths paths = new AuthoringAssetPaths(fixture);
        for (String path : new String[] {"../x", "a\\b", "a//b", "CON.txt", "a/..", "a:b", "a.", "e\u0301/file"})
            assertThrows(IOException.class, () -> paths.resolve(path), path);
        paths.writeNew("safe/a.txt", new byte[] {1, 2, 3});
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(paths.resolve("safe/a.txt")));
        assertThrows(IOException.class, () -> paths.writeNew("safe/a.txt", new byte[] {9}));
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(paths.resolve("safe/a.txt")));
    }

    @Test void externalSharedDecodeBudgetAccumulatesAcrossStructuresAndClosesFailedStreams() throws Exception {
        byte[] raw = encoded(schematic().toNbt(), false); PonderSchematic.DecodeBudget budget = new PonderSchematic.DecodeBudget(raw.length * 2L, 2);
        PonderSchematic.loadExternal(new ByteArrayInputStream(raw), budget); PonderSchematic.loadExternal(new ByteArrayInputStream(raw), budget);
        assertEquals(raw.length * 2L, budget.getDecodedBytes()); assertEquals(2, budget.getDecodedBlocks());
        assertThrows(IOException.class, () -> PonderSchematic.loadExternal(new ByteArrayInputStream(raw), budget));
        class TrackedInput extends ByteArrayInputStream {
            boolean closed; int delivered;
            TrackedInput(byte[] bytes) { super(bytes); }
            @Override public synchronized int read() { int value = super.read(); if (value >= 0) delivered++; return value; }
            @Override public synchronized int read(byte[] b, int p, int n) { int count = super.read(b, p, n); if (count > 0) delivered += count; return count; }
            @Override public void close() { closed = true; }
        }
        TrackedInput limited = new TrackedInput(raw);
        assertThrows(IOException.class, () -> PonderSchematic.loadExternal(limited, new PonderSchematic.DecodeBudget(20, 2)));
        assertTrue(limited.closed); assertTrue(limited.delivered <= 21, "Must stop the stream at the budget, not read all bytes first");
    }
}
