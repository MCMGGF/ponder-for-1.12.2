package net.createmod.ponder.foundation.content.oil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;

class OilLinePreviewNbtTest {

    @Test
    void usesTheInstalledAutosaveCountedCompoundAndConduitMemberUuid() {
        NBTTagCompound root = OilLinePreviewNbt.fluidConduit(
            EnumSet.of(EnumFacing.EAST, EnumFacing.WEST), Collections.emptyMap(), "oil", 125);
        assertEquals(10, root.getTagId("conduits"));
        NBTTagCompound entries = root.getCompoundTag("conduits");
        assertEquals(3, entries.getTagId("size"));
        assertEquals(1, entries.getInteger("size"));
        assertEquals(10, entries.getTagId("0"));
        assertEquals(2, entries.getKeySet().size());
        NBTTagCompound conduit = conduit(root);
        assertEquals(UUID.nameUUIDFromBytes("enderio:liquid_conduit".getBytes(StandardCharsets.UTF_8)).toString(),
            conduit.getString("UUID"));
        assertArrayEquals(new int[] {4, 5}, conduit.getIntArray("connections"));
        assertArrayEquals(new int[0], conduit.getIntArray("externalConnections"));
        assertArrayEquals(new byte[6], conduit.getByteArray("conModes"));
        assertEquals(11, conduit.getTagId("connections"));
        assertEquals(11, conduit.getTagId("externalConnections"));
        assertEquals(7, conduit.getTagId("conModes"));
        assertEquals(1, conduit.getTagId("hasNetwork"));
        assertTrue(conduit.getBoolean("hasNetwork"));
        assertFalse(conduit.getBoolean("signalActive"));
        assertFalse(conduit.getBoolean("fluidLocked"));
        assertNoWorldIdentity(root);
    }

    @Test
    void usesConduitRelativeModesAndAlwaysActiveFluidExtraction() {
        Map<EnumFacing, Integer> modes = new EnumMap<>(EnumFacing.class);
        modes.put(EnumFacing.WEST, OilLinePreviewNbt.EXTRACT);
        modes.put(EnumFacing.EAST, OilLinePreviewNbt.INSERT);
        NBTTagCompound conduit = conduit(OilLinePreviewNbt.fluidConduit(
            EnumSet.of(EnumFacing.UP), modes, "oil", 500));
        assertArrayEquals(new int[] {1}, conduit.getIntArray("connections"));
        assertArrayEquals(new int[] {4, 5}, conduit.getIntArray("externalConnections"));
        assertArrayEquals(new byte[] {0, 0, 0, 0, 1, 2}, conduit.getByteArray("conModes"));
        assertEquals(2, conduit.getTagId("extRM.WEST"));
        assertEquals(0, conduit.getShort("extRM.WEST"));
        assertEquals(0, conduit.getShort("extRM.EAST"));
        assertFalse(conduit.hasKey("extRM.UP"));
        assertEquals("oil", conduit.getCompoundTag("tank").getString("FluidName"));
        assertEquals(500, conduit.getCompoundTag("tank").getInteger("Amount"));
        assertEquals(8, conduit.getCompoundTag("tank").getTagId("FluidName"));
        assertEquals(3, conduit.getCompoundTag("tank").getTagId("Amount"));
    }

    @Test
    void serializesEveryFacingInMinecraftOrdinalOrderAndAcceptsEveryStoredMode() {
        Map<EnumFacing, Integer> modes = new HashMap<>();
        modes.put(EnumFacing.EAST, OilLinePreviewNbt.NOT_SET);
        modes.put(EnumFacing.WEST, OilLinePreviewNbt.DISABLED);
        modes.put(EnumFacing.SOUTH, OilLinePreviewNbt.INSERT);
        modes.put(EnumFacing.NORTH, OilLinePreviewNbt.EXTRACT);
        modes.put(EnumFacing.UP, OilLinePreviewNbt.IN_OUT);
        modes.put(EnumFacing.DOWN, OilLinePreviewNbt.IN_OUT);
        NBTTagCompound conduit = conduit(OilLinePreviewNbt.energyConduit(Collections.emptySet(), modes));
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5}, conduit.getIntArray("externalConnections"));
        assertArrayEquals(new byte[] {0, 0, 1, 2, 3, 4}, conduit.getByteArray("conModes"));
    }

    @Test
    void createsBasicEnergyConduitsWithoutBorrowingFluidOrLiveNetworkData() {
        NBTTagCompound root = OilLinePreviewNbt.energyConduit(EnumSet.of(EnumFacing.NORTH),
            Collections.singletonMap(EnumFacing.DOWN, OilLinePreviewNbt.EXTRACT));
        NBTTagCompound conduit = conduit(root);
        assertEquals(UUID.nameUUIDFromBytes("enderio:power_conduit".getBytes(StandardCharsets.UTF_8)).toString(),
            conduit.getString("UUID"));
        assertEquals(2, conduit.getTagId("subtype"));
        assertEquals(0, conduit.getShort("subtype"));
        assertEquals(3, conduit.getTagId("energyStoredRF"));
        assertEquals(0, conduit.getInteger("energyStoredRF"));
        assertEquals(2, conduit.getTagId("pRsMode.DOWN"));
        assertEquals(0, conduit.getShort("pRsMode.DOWN"));
        assertFalse(conduit.hasKey("extRM.DOWN"));
        assertFalse(conduit.hasKey("tank"));
        assertFalse(conduit.hasKey("network"));
        assertNoWorldIdentity(root);
    }

    @Test
    void createsNormalTankContentsWithTheSmartTankCapacityField() {
        NBTTagCompound root = OilLinePreviewNbt.tank("diesel", 16000);
        assertEquals(Collections.singleton("tank"), root.getKeySet());
        NBTTagCompound tank = root.getCompoundTag("tank");
        assertEquals("diesel", tank.getString("FluidName"));
        assertEquals(16000, tank.getInteger("Amount"));
        assertEquals(16000, tank.getInteger("Capacity"));
        assertEquals(3, tank.getTagId("Capacity"));
        assertNoWorldIdentity(root);
    }

    @Test
    void supportsEmptySnapshotsWithoutInventingAFluid() {
        assertFalse(conduit(OilLinePreviewNbt.fluidConduit(Collections.emptySet(), Collections.emptyMap(),
            null, 0)).hasKey("tank"));
        assertFalse(conduit(OilLinePreviewNbt.fluidConduit(EnumSet.of(EnumFacing.UP), Collections.emptyMap(),
            "oil", 0)).hasKey("tank"));
        NBTTagCompound emptyTank = OilLinePreviewNbt.tank("", 0).getCompoundTag("tank");
        assertEquals(16000, emptyTank.getInteger("Capacity"));
        assertFalse(emptyTank.hasKey("FluidName"));
        assertFalse(emptyTank.hasKey("Amount"));
    }

    @Test
    void validatesTopologyAndRejectsAmbiguousOrInvalidModes() {
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.energyConduit(null,
            Collections.emptyMap()));
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.energyConduit(
            Collections.emptySet(), null));
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.energyConduit(
            EnumSet.of(EnumFacing.WEST), Collections.singletonMap(EnumFacing.WEST, OilLinePreviewNbt.EXTRACT)));
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.energyConduit(
            Collections.emptySet(), Collections.singletonMap(null, OilLinePreviewNbt.EXTRACT)));
        for (Integer mode : new Integer[] {null, -1, 5, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.energyConduit(
                Collections.emptySet(), Collections.singletonMap(EnumFacing.UP, mode)));
        }
        Set<EnumFacing> invalid = new HashSet<>();
        invalid.add(null);
        assertThrows(IllegalArgumentException.class,
            () -> OilLinePreviewNbt.energyConduit(invalid, Collections.emptyMap()));
    }

    @Test
    void boundsOrdinaryConduitFluidByItsActualConnectionVolume() {
        OilLinePreviewNbt.fluidConduit(EnumSet.of(EnumFacing.EAST, EnumFacing.WEST),
            Collections.emptyMap(), "oil", 500);
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.fluidConduit(
            EnumSet.of(EnumFacing.EAST, EnumFacing.WEST), Collections.emptyMap(), "oil", 501));
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.fluidConduit(
            Collections.emptySet(), Collections.emptyMap(), "oil", 1));
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.fluidConduit(
            EnumSet.of(EnumFacing.UP), Collections.emptyMap(), "oil", -1));
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.tank("oil", -1));
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.tank("oil", 16001));
    }

    @Test
    void refusesMalformedNamesWithoutClaimingToValidateTheRuntimeFluidRegistry() {
        for (String invalid : new String[] {null, "", " oil", "oil ", "oil\n", new String(new char[257])}) {
            assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.tank(invalid, 1));
        }
        assertEquals("pack:custom_fluid", OilLinePreviewNbt.tank("pack:custom_fluid", 1)
            .getCompoundTag("tank").getString("FluidName"));
    }

    @Test
    void ownsEveryMutableInputAndReturnedNestedTag() {
        Set<EnumFacing> internal = EnumSet.of(EnumFacing.WEST);
        Map<EnumFacing, Integer> external = new EnumMap<>(EnumFacing.class);
        external.put(EnumFacing.EAST, OilLinePreviewNbt.INSERT);
        NBTTagCompound first = OilLinePreviewNbt.fluidConduit(internal, external, "oil", 250);
        internal.clear();
        external.put(EnumFacing.DOWN, OilLinePreviewNbt.DISABLED);
        assertArrayEquals(new int[] {4}, conduit(first).getIntArray("connections"));
        assertArrayEquals(new int[] {5}, conduit(first).getIntArray("externalConnections"));
        NBTTagCompound second = OilLinePreviewNbt.fluidConduit(EnumSet.of(EnumFacing.WEST),
            Collections.singletonMap(EnumFacing.EAST, OilLinePreviewNbt.INSERT), "oil", 250);
        assertNotSame(first.getCompoundTag("conduits"), second.getCompoundTag("conduits"));
        conduit(first).getByteArray("conModes")[5] = 3;
        conduit(first).getCompoundTag("tank").setInteger("Amount", 1);
        assertEquals(2, conduit(second).getByteArray("conModes")[5]);
        assertEquals(250, conduit(second).getCompoundTag("tank").getInteger("Amount"));
        NBTTagCompound tank = OilLinePreviewNbt.tank("oil", 1000);
        tank.getCompoundTag("tank").setInteger("Capacity", 1);
        assertEquals(16000, OilLinePreviewNbt.tank("oil", 1000).getCompoundTag("tank").getInteger("Capacity"));
    }

    @Test
    void replacingSnapshotFieldsClearsOldContentsAndPreservesTileIdentity() {
        NBTTagCompound tile = new NBTTagCompound();
        tile.setString("id", "enderio:tile_tank");
        tile.setInteger("x", 4);
        OilLinePreviewNbt.applySnapshot(tile, OilLinePreviewNbt.tank("oil", 1000));
        OilLinePreviewNbt.applySnapshot(tile, OilLinePreviewNbt.tank(null, 0));
        assertFalse(tile.getCompoundTag("tank").hasKey("FluidName"));
        assertEquals("enderio:tile_tank", tile.getString("id"));
        assertEquals(4, tile.getInteger("x"));

        OilLinePreviewNbt.applySnapshot(tile, OilLinePreviewNbt.fluidConduit(EnumSet.of(EnumFacing.EAST),
            Collections.emptyMap(), "oil", 100));
        OilLinePreviewNbt.applySnapshot(tile, OilLinePreviewNbt.fluidConduit(EnumSet.of(EnumFacing.EAST),
            Collections.emptyMap(), null, 0));
        assertFalse(conduit(tile).hasKey("tank"));
    }

    @Test
    void appliesAnOwnedCopyAndRejectsUnexpectedFieldsBeforeChangingTheTarget() {
        NBTTagCompound target = new NBTTagCompound();
        NBTTagCompound snapshot = OilLinePreviewNbt.tank("oil", 1000);
        OilLinePreviewNbt.applySnapshot(target, snapshot);
        snapshot.getCompoundTag("tank").setInteger("Amount", 2);
        assertEquals(1000, target.getCompoundTag("tank").getInteger("Amount"));
        NBTTagCompound before = target.copy();
        NBTTagCompound unsafe = OilLinePreviewNbt.tank("diesel", 3000);
        unsafe.setInteger("x", 123);
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.applySnapshot(target, unsafe));
        assertEquals(before, target);
        NBTTagCompound wrongType = new NBTTagCompound();
        wrongType.setString("tank", "not a compound");
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.applySnapshot(target, wrongType));
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.applySnapshot(null, snapshot));
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.applySnapshot(target, null));
    }

    @Test
    void combinesFluidAndEnergyMembersOnTheSameFaceWithoutSharedTags() {
        NBTTagCompound fluid = OilLinePreviewNbt.fluidConduit(EnumSet.of(EnumFacing.WEST),
            Collections.singletonMap(EnumFacing.EAST, OilLinePreviewNbt.INSERT), "oil", 100);
        NBTTagCompound energy = OilLinePreviewNbt.energyConduit(EnumSet.of(EnumFacing.WEST),
            Collections.singletonMap(EnumFacing.EAST, OilLinePreviewNbt.INSERT));
        NBTTagCompound combined = OilLinePreviewNbt.combineConduits(fluid, energy);
        NBTTagCompound members = combined.getCompoundTag("conduits");
        assertEquals(2, members.getInteger("size"));
        assertEquals(3, members.getKeySet().size());
        assertEquals(conduit(fluid), members.getCompoundTag("0"));
        assertEquals(conduit(energy), members.getCompoundTag("1"));
        conduit(fluid).getCompoundTag("tank").setInteger("Amount", 1);
        conduit(energy).setShort("subtype", (short) 2);
        assertEquals(100, members.getCompoundTag("0").getCompoundTag("tank").getInteger("Amount"));
        assertEquals(0, members.getCompoundTag("1").getShort("subtype"));
        NBTTagCompound applied = new NBTTagCompound();
        OilLinePreviewNbt.applySnapshot(applied, combined);
        members.getCompoundTag("0").setString("UUID", "changed");
        assertFalse("changed".equals(conduit(applied).getString("UUID")));
    }

    @Test
    void refusesDuplicateMalformedAndUnsupportedBundleMembers() {
        NBTTagCompound fluid = OilLinePreviewNbt.fluidConduit(Collections.emptySet(),
            Collections.emptyMap(), null, 0);
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.combineConduits(fluid, fluid));
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.combineConduits());
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.combineConduits((NBTTagCompound[]) null));
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.combineConduits((NBTTagCompound) null));
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.combineConduits(fluid, fluid, fluid));
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.combineConduits(OilLinePreviewNbt.tank(null, 0)));
        NBTTagCompound unsupported = fluid.copy();
        conduit(unsupported).setString("UUID", "unsupported-preview-conduit");
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.combineConduits(unsupported));
        NBTTagCompound wrongCount = fluid.copy();
        wrongCount.getCompoundTag("conduits").setInteger("size", 2);
        assertThrows(IllegalArgumentException.class, () -> OilLinePreviewNbt.combineConduits(wrongCount));
    }

    private static NBTTagCompound conduit(NBTTagCompound root) {
        return root.getCompoundTag("conduits").getCompoundTag("0");
    }

    private static void assertNoWorldIdentity(NBTTagCompound root) {
        for (String key : new String[] {"id", "x", "y", "z"}) {
            assertFalse(root.hasKey(key));
        }
    }
}
