package net.createmod.ponder.foundation.content.oil;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

/**
 * Builds detached display snapshots of the installed Ender IO 5.2.66 tile format.
 * Replace the returned top-level fields on a preview tag, rather than recursively merging
 * them, so an empty snapshot cannot retain fluid contents from an earlier timeline step.
 */
public final class OilLinePreviewNbt {

    public static final int IN_OUT = 0;
    public static final int EXTRACT = 1;
    public static final int INSERT = 2;
    public static final int DISABLED = 3;
    public static final int NOT_SET = 4;
    public static final int TANK_CAPACITY = 16000;

    private static final String FLUID_UUID = "0bedff66-2699-3854-8a2f-feca13935aa5";
    private static final String ENERGY_UUID = "7e9c38f2-9a3a-3091-bea9-d53e75afc515";
    private static final int FLUID_PER_CONNECTION = 250;

    private OilLinePreviewNbt() {
    }

    /** Modes are conduit-relative: EXTRACT takes from a device; INSERT sends to one. */
    public static NBTTagCompound fluidConduit(Set<EnumFacing> internal,
        Map<EnumFacing, Integer> externalModes, String fluid, int amount) {
        Connections connections = new Connections(internal, externalModes);
        validateFluid(fluid, amount,
            (connections.internal.size() + connections.external.size()) * FLUID_PER_CONNECTION);
        NBTTagCompound conduit = connections.conduit(FLUID_UUID);
        conduit.setBoolean("fluidLocked", false);
        if (amount > 0) {
            conduit.setTag("tank", fluidContents(fluid, amount));
        }
        for (EnumFacing side : connections.external.keySet()) {
            conduit.setShort("extRM." + side.name(), (short) 0);
        }
        return bundle(conduit);
    }

    public static NBTTagCompound energyConduit(Set<EnumFacing> internal,
        Map<EnumFacing, Integer> externalModes) {
        Connections connections = new Connections(internal, externalModes);
        NBTTagCompound conduit = connections.conduit(ENERGY_UUID);
        conduit.setShort("subtype", (short) 0);
        conduit.setInteger("energyStoredRF", 0);
        for (EnumFacing side : connections.external.keySet()) {
            conduit.setShort("pRsMode." + side.name(), (short) 0);
        }
        return bundle(conduit);
    }

    public static NBTTagCompound tank(String fluid, int amount) {
        validateFluid(fluid, amount, TANK_CAPACITY);
        NBTTagCompound contents = amount > 0 ? fluidContents(fluid, amount) : new NBTTagCompound();
        contents.setInteger("Capacity", TANK_CAPACITY);
        NBTTagCompound result = new NBTTagCompound();
        result.setTag("tank", contents);
        return result;
    }

    /** Combines the two supported conduit kinds without constructing a live conduit bundle. */
    public static NBTTagCompound combineConduits(NBTTagCompound... snapshots) {
        if (snapshots == null || snapshots.length == 0 || snapshots.length > 2) {
            throw new IllegalArgumentException("One or two distinct preview conduit snapshots are required");
        }
        Set<String> memberIds = new HashSet<>();
        NBTTagCompound entries = new NBTTagCompound();
        for (int index = 0; index < snapshots.length; index++) {
            NBTTagCompound snapshot = snapshots[index];
            if (snapshot == null || snapshot.getKeySet().size() != 1 || !snapshot.hasKey("conduits", 10)) {
                throw new IllegalArgumentException("Expected a single-conduit preview snapshot");
            }
            NBTTagCompound source = snapshot.getCompoundTag("conduits");
            if (source.getInteger("size") != 1 || source.getKeySet().size() != 2 || !source.hasKey("0", 10)) {
                throw new IllegalArgumentException("Expected a single-conduit preview snapshot");
            }
            NBTTagCompound conduit = source.getCompoundTag("0");
            String uuid = conduit.getString("UUID");
            if ((!FLUID_UUID.equals(uuid) && !ENERGY_UUID.equals(uuid)) || !memberIds.add(uuid)) {
                throw new IllegalArgumentException("Unsupported or duplicate preview conduit kind");
            }
            entries.setTag(Integer.toString(index), conduit.copy());
        }
        entries.setInteger("size", snapshots.length);
        NBTTagCompound result = new NBTTagCompound();
        result.setTag("conduits", entries);
        return result;
    }

    /** Replaces only owned display fields and retains the preview tile's identity and position. */
    public static void applySnapshot(NBTTagCompound target, NBTTagCompound snapshot) {
        if (target == null || snapshot == null) {
            throw new IllegalArgumentException("Preview snapshot and target must not be null");
        }
        for (String key : snapshot.getKeySet()) {
            if ((!"conduits".equals(key) && !"tank".equals(key)) || !snapshot.hasKey(key, 10)) {
                throw new IllegalArgumentException("Unexpected preview snapshot field");
            }
        }
        for (String key : snapshot.getKeySet()) {
            target.setTag(key, snapshot.getTag(key).copy());
        }
    }

    private static NBTTagCompound fluidContents(String fluid, int amount) {
        NBTTagCompound contents = new NBTTagCompound();
        contents.setString("FluidName", fluid);
        contents.setInteger("Amount", amount);
        return contents;
    }

    private static void validateFluid(String fluid, int amount, int capacity) {
        if (amount < 0 || amount > capacity) {
            throw new IllegalArgumentException("Preview fluid amount exceeds the displayed container capacity");
        }
        if (fluid == null || fluid.isEmpty()) {
            if (amount != 0) {
                throw new IllegalArgumentException("A nonempty fluid requires a registered fluid name");
            }
            return;
        }
        if (fluid.length() > 256 || !fluid.equals(fluid.trim())) {
            throw new IllegalArgumentException("Invalid preview fluid name");
        }
        for (int index = 0; index < fluid.length(); index++) {
            if (Character.isISOControl(fluid.charAt(index))) {
                throw new IllegalArgumentException("Invalid preview fluid name");
            }
        }
    }

    private static NBTTagCompound bundle(NBTTagCompound conduit) {
        // AutoSave uses a counted compound, not NBTTagList. No live network is created here.
        NBTTagCompound entries = new NBTTagCompound();
        entries.setInteger("size", 1);
        entries.setTag("0", conduit);
        NBTTagCompound result = new NBTTagCompound();
        result.setTag("conduits", entries);
        return result;
    }

    private static final class Connections {
        private final EnumSet<EnumFacing> internal = EnumSet.noneOf(EnumFacing.class);
        private final EnumMap<EnumFacing, Integer> external = new EnumMap<>(EnumFacing.class);

        private Connections(Set<EnumFacing> internal, Map<EnumFacing, Integer> externalModes) {
            if (internal == null || externalModes == null) {
                throw new IllegalArgumentException("Preview connections must not be null");
            }
            for (EnumFacing side : internal) {
                if (side == null) {
                    throw new IllegalArgumentException("Preview connection side must not be null");
                }
                this.internal.add(side);
            }
            for (Map.Entry<EnumFacing, Integer> entry : externalModes.entrySet()) {
                EnumFacing side = entry.getKey();
                Integer mode = entry.getValue();
                if (side == null || mode == null || mode < IN_OUT || mode > NOT_SET) {
                    throw new IllegalArgumentException("Invalid preview external connection mode");
                }
                if (this.internal.contains(side)) {
                    throw new IllegalArgumentException("A preview side cannot connect to both a conduit and a device");
                }
                external.put(side, mode);
            }
        }

        private NBTTagCompound conduit(String uuid) {
            NBTTagCompound result = new NBTTagCompound();
            result.setString("UUID", uuid);
            result.setIntArray("connections", ordinals(internal));
            result.setIntArray("externalConnections", ordinals(external.keySet()));
            byte[] modes = new byte[EnumFacing.VALUES.length];
            for (Map.Entry<EnumFacing, Integer> entry : external.entrySet()) {
                modes[entry.getKey().ordinal()] = entry.getValue().byteValue();
            }
            result.setByteArray("conModes", modes);
            result.setBoolean("signalActive", false);
            // A static rendering flag only, not proof of connectivity or actual transport.
            result.setBoolean("hasNetwork", true);
            return result;
        }

        private static int[] ordinals(Set<EnumFacing> sides) {
            int[] result = new int[sides.size()];
            int index = 0;
            for (EnumFacing side : EnumFacing.VALUES) {
                if (sides.contains(side)) {
                    result[index++] = side.ordinal();
                }
            }
            return result;
        }
    }
}
