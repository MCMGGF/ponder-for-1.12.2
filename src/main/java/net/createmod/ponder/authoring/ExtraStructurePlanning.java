package net.createmod.ponder.authoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.createmod.ponder.foundation.PonderSchematic;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;

/**
 * Detached 1.12.2 placement/reveal plan adapted from Ponderer's ExtraStructurePlanner (MIT).
 * Copyright (c) 2026 Nobodiiiii; see META-INF/PONDERER-MIGRATION-NOTICE.txt.
 */
public final class ExtraStructurePlanning {
    private static final int MAX_TICKS = 72000;
    private ExtraStructurePlanning() { }

    public static final class PlacedBlock {
        private final BlockPos pos;
        private final IBlockState state;
        private final NBTTagCompound nbt;
        private PlacedBlock(BlockPos pos, IBlockState state, NBTTagCompound nbt) {
            this.pos = pos.toImmutable(); this.state = state; this.nbt = nbt == null ? null : nbt.copy();
        }
        public BlockPos getPos() { return pos; }
        public IBlockState getState() { return state; }
        public NBTTagCompound getNbtCopy() { return nbt == null ? null : nbt.copy(); }
    }

    public static final class EntranceGroup {
        private final List<BlockPos> positions;
        private final int startOffset, duration;
        private final EnumFacing direction;
        private EntranceGroup(List<BlockPos> positions, int startOffset, int duration, EnumFacing direction) {
            this.positions = Collections.unmodifiableList(new ArrayList<>(positions));
            this.startOffset = startOffset; this.duration = duration; this.direction = direction;
        }
        public List<BlockPos> getPositions() { return positions; }
        public int getStartOffset() { return startOffset; }
        public int getDuration() { return duration; }
        public EnumFacing getDirection() { return direction; }
    }

    public static final class Plan {
        private final List<PlacedBlock> placements;
        private final List<BlockPos> positions;
        private final List<EntranceGroup> entranceGroups;
        private final String entranceAnimation;
        private final boolean visible, particles, smartDisplay;
        private final int timelineAdvance;
        private Plan(List<PlacedBlock> placements, List<EntranceGroup> groups, String animation,
            boolean visible, boolean particles, boolean smartDisplay, int timelineAdvance) {
            this.placements = Collections.unmodifiableList(new ArrayList<>(placements));
            List<BlockPos> positions = new ArrayList<>();
            for (PlacedBlock block : placements) positions.add(block.pos);
            this.positions = Collections.unmodifiableList(positions);
            this.entranceGroups = Collections.unmodifiableList(new ArrayList<>(groups));
            this.entranceAnimation = animation; this.visible = visible; this.particles = particles;
            this.smartDisplay = smartDisplay; this.timelineAdvance = timelineAdvance;
        }
        public List<PlacedBlock> getPlacements() { return placements; }
        public List<BlockPos> getPositions() { return positions; }
        public List<EntranceGroup> getEntranceGroups() { return entranceGroups; }
        public String getEntranceAnimation() { return entranceAnimation; }
        public boolean isAnimated() { return !"none".equals(entranceAnimation); }
        public boolean isPlaceVisible() { return visible; }
        public boolean shouldSpawnParticles() { return particles; }
        public boolean isSmartDisplay() { return smartDisplay; }
        /** Directional reveal advances after every strip, including the final strip, like upstream. */
        public int getTimelineAdvance() { return timelineAdvance; }
    }

    public static Plan plan(PonderSchematic source, JsonObject step) {
        if (source == null || step == null) throw new IllegalArgumentException("Extra structure and step are required");
        BlockPos base = AuthoringCompiler.blockPos(step, "blockPos");
        Rotation rotation = rotation(AuthoringCompiler.number(step, "rotation", 0));
        boolean replaceAir = AuthoringCompiler.bool(step, "replaceAir", false);
        String animation = normalizeEntrance(string(step, "entranceAnimation", "none"));
        boolean animated = !"none".equals(animation);
        boolean requestedVisible = AuthoringCompiler.bool(step, "immediateDisplay", true);
        boolean requestedParticles = AuthoringCompiler.bool(step, "spawnParticles", true);
        boolean visible = !animated && requestedVisible;
        boolean particles = visible && requestedParticles;
        boolean smartDisplay = AuthoringCompiler.bool(step, "smartDisplay", true);
        int duration = AuthoringCompiler.integer(step, "entranceDuration", 20, 0, MAX_TICKS);
        int requestedInterval = AuthoringCompiler.integer(step, "entranceInterval", 1, 0, MAX_TICKS);
        int interval = "simultaneous".equals(animation) ? 0 : requestedInterval;
        EnumFacing direction = direction(string(step, "direction", "down"));
        validateSource(source);

        List<PlacedBlock> rotated = new ArrayList<>();
        if (replaceAir) {
            BlockPos size = source.getSize();
            for (int x = 0; x < size.getX(); x++) for (int y = 0; y < size.getY(); y++) for (int z = 0; z < size.getZ(); z++) {
                BlockPos pos = new BlockPos(x, y, z);
                IBlockState state = source.getBlocks().get(pos);
                boolean empty = state == null || skipped(state);
                addRotated(rotated, pos, empty ? Blocks.AIR.getDefaultState() : state,
                    empty ? null : source.getBlockEntityNbt(pos), rotation);
            }
        } else {
            for (Map.Entry<BlockPos, IBlockState> entry : source.getBlocks().entrySet())
                if (!skipped(entry.getValue())) addRotated(rotated, entry.getKey(), entry.getValue(),
                    source.getBlockEntityNbt(entry.getKey()), rotation);
        }
        List<PlacedBlock> placements = normalize(rotated, base);
        List<EntranceGroup> groups = animated ? groups(placements, animation, duration, interval, direction)
            : Collections.emptyList();
        long advance = animated ? (long) groups.size() * interval : 0;
        long lastEnd = groups.isEmpty() ? 0 : (long) groups.get(groups.size() - 1).startOffset + duration;
        if (advance > MAX_TICKS || lastEnd > MAX_TICKS)
            throw new IllegalArgumentException("Extra structure entrance exceeds 72000 ticks");
        return new Plan(placements, groups, animation, visible, particles, smartDisplay, (int) advance);
    }

    public static Rotation rotation(double degrees) {
        if (!Double.isFinite(degrees) || Math.abs(degrees) > 1000000 || degrees % 90 != 0)
            throw new IllegalArgumentException("Extra structure rotation must be a finite multiple of 90 degrees within +/-1000000");
        int normalized = ((int) degrees % 360 + 360) % 360;
        if (normalized == 90) return Rotation.CLOCKWISE_90;
        if (normalized == 180) return Rotation.CLOCKWISE_180;
        if (normalized == 270) return Rotation.COUNTERCLOCKWISE_90;
        return Rotation.NONE;
    }

    private static void validateSource(PonderSchematic source) {
        BlockPos size = source.getSize();
        if (size.getX() < 0 || size.getY() < 0 || size.getZ() < 0 || size.getX() > PonderSchematic.MAX_EXTERNAL_AXIS
            || size.getY() > PonderSchematic.MAX_EXTERNAL_AXIS || size.getZ() > PonderSchematic.MAX_EXTERNAL_AXIS
            || (long) size.getX() * size.getY() * size.getZ() > PonderSchematic.MAX_BLOCKS)
            throw new IllegalArgumentException("Extra structure dimensions must be within 64x64x64");
        for (BlockPos pos : source.getBlocks().keySet())
            if (pos.getX() < 0 || pos.getY() < 0 || pos.getZ() < 0 || pos.getX() >= size.getX()
                || pos.getY() >= size.getY() || pos.getZ() >= size.getZ())
                throw new IllegalArgumentException("Extra structure block is outside its declared size: " + pos);
    }

    private static boolean skipped(IBlockState state) {
        String id = String.valueOf(state.getBlock().getRegistryName());
        return state.getBlock() == Blocks.AIR || "minecraft:cave_air".equals(id)
            || "minecraft:void_air".equals(id) || "minecraft:structure_void".equals(id);
    }

    private static void addRotated(List<PlacedBlock> blocks, BlockPos source, IBlockState state,
        NBTTagCompound nbt, Rotation rotation) {
        BlockPos pos;
        if (rotation == Rotation.CLOCKWISE_90) pos = new BlockPos(-source.getZ(), source.getY(), source.getX());
        else if (rotation == Rotation.CLOCKWISE_180) pos = new BlockPos(-source.getX(), source.getY(), -source.getZ());
        else if (rotation == Rotation.COUNTERCLOCKWISE_90) pos = new BlockPos(source.getZ(), source.getY(), -source.getX());
        else pos = source;
        blocks.add(new PlacedBlock(pos, state.withRotation(rotation), nbt));
    }

    private static List<PlacedBlock> normalize(List<PlacedBlock> rotated, BlockPos base) {
        if (rotated.isEmpty()) return Collections.emptyList();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (PlacedBlock block : rotated) {
            minX = Math.min(minX, block.pos.getX()); minY = Math.min(minY, block.pos.getY()); minZ = Math.min(minZ, block.pos.getZ());
        }
        List<PlacedBlock> normalized = new ArrayList<>(rotated.size());
        for (PlacedBlock block : rotated) {
            long x = (long) base.getX() + block.pos.getX() - minX;
            long y = (long) base.getY() + block.pos.getY() - minY;
            long z = (long) base.getZ() + block.pos.getZ() - minZ;
            if (Math.abs(x) > PonderSchematic.MAX_COORDINATE || Math.abs(y) > PonderSchematic.MAX_COORDINATE
                || Math.abs(z) > PonderSchematic.MAX_COORDINATE)
                throw new IllegalArgumentException("Rotated extra structure exceeds the preview coordinate budget");
            BlockPos pos = new BlockPos((int) x, (int) y, (int) z);
            NBTTagCompound nbt = block.getNbtCopy();
            if (nbt != null) {
                nbt.setInteger("x", pos.getX()); nbt.setInteger("y", pos.getY()); nbt.setInteger("z", pos.getZ());
            }
            normalized.add(new PlacedBlock(pos, block.state, nbt));
        }
        return normalized;
    }

    private static List<EntranceGroup> groups(List<PlacedBlock> blocks, String animation, int duration,
        int interval, EnumFacing direction) {
        List<BlockPos> positions = new ArrayList<>(); for (PlacedBlock block : blocks) positions.add(block.pos);
        return groupsForPositions(positions, animation, duration, interval, direction);
    }

    public static List<EntranceGroup> groupsForPositions(Iterable<BlockPos> selected, String animation, int duration,
        int interval, EnumFacing direction) {
        animation = normalizeEntrance(animation);
        if (duration < 0 || duration > MAX_TICKS || interval < 0 || interval > MAX_TICKS)
            throw new IllegalArgumentException("Section entrance timing exceeds 72000 ticks");
        if ("none".equals(animation)) return Collections.emptyList();
        if ("simultaneous".equals(animation)) interval = 0;
        String ordering = "simultaneous".equals(animation) ? "up" : animation;
        int layer = "east".equals(ordering) || "west".equals(ordering) ? 0
            : "north".equals(ordering) || "south".equals(ordering) ? 2 : 1;
        int row = layer == 1 ? 2 : 1, strip = layer == 0 ? 2 : 0;
        boolean reverse = "down".equals(ordering) || "north".equals(ordering) || "west".equals(ordering);
        Comparator<BlockPos> byLayer = Comparator.comparingInt(pos -> axis(pos, layer));
        if (reverse) byLayer = byLayer.reversed();
        Comparator<BlockPos> comparator = byLayer.thenComparingInt(pos -> axis(pos, row)).thenComparingInt(pos -> axis(pos, strip));
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : selected) {
            if (positions.size() >= PonderSchematic.MAX_BLOCKS) throw new IllegalArgumentException("Section entrance exceeds 262144 positions");
            positions.add(pos.toImmutable());
        }
        positions.sort(comparator);
        List<List<BlockPos>> strips = new ArrayList<>(); List<BlockPos> current = null; BlockPos previous = null;
        for (BlockPos pos : positions) {
            if (previous == null || axis(pos, layer) != axis(previous, layer) || axis(pos, row) != axis(previous, row)
                || axis(pos, strip) != axis(previous, strip) + 1) {
                current = new ArrayList<>(); strips.add(current);
            }
            current.add(pos); previous = pos;
        }
        List<EntranceGroup> result = new ArrayList<>();
        for (int index = 0; index < strips.size(); index++) {
            long offset = (long) index * interval;
            if (offset + duration > MAX_TICKS || (long) strips.size() * interval > MAX_TICKS)
                throw new IllegalArgumentException("Section entrance exceeds 72000 ticks");
            result.add(new EntranceGroup(strips.get(index), (int) offset, duration, direction));
        }
        return result;
    }

    private static int axis(BlockPos pos, int axis) { return axis == 0 ? pos.getX() : axis == 1 ? pos.getY() : pos.getZ(); }

    private static EnumFacing direction(String value) {
        if (value.trim().isEmpty()) return EnumFacing.DOWN;
        try { return EnumFacing.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Unknown extra structure direction: " + value); }
    }

    private static String string(JsonObject step, String key, String fallback) {
        JsonElement value = step.get(key);
        if (value == null || value.isJsonNull()) return fallback;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException(key + " must be a string");
        return value.getAsString();
    }

    public static String normalizeEntrance(String raw) {
        String value = raw == null ? "none" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || "none".equals(value) || "\u65e0".equals(value) || "(\u65e0)".equals(value)) return "none";
        if ("simultaneous".equals(value) || "\u540c\u65f6".equals(value)) return "simultaneous";
        if ("down".equals(value) || "top_to_bottom".equals(value) || "top-down".equals(value)
            || "\u4ece\u4e0a\u5230\u4e0b".equals(value) || "\u4e0a\u5230\u4e0b".equals(value)) return "down";
        if ("up".equals(value) || "bottom_to_top".equals(value) || "bottom-up".equals(value)
            || "\u4ece\u4e0b\u5230\u4e0a".equals(value) || "\u4e0b\u5230\u4e0a".equals(value)) return "up";
        if ("south".equals(value) || "north_to_south".equals(value) || "north-south".equals(value)
            || "\u4ece\u5317\u5230\u5357".equals(value) || "\u5317\u5230\u5357".equals(value)) return "south";
        if ("north".equals(value) || "south_to_north".equals(value) || "south-north".equals(value)
            || "\u4ece\u5357\u5230\u5317".equals(value) || "\u5357\u5230\u5317".equals(value)) return "north";
        if ("east".equals(value) || "west_to_east".equals(value) || "west-east".equals(value)
            || "\u4ece\u897f\u5230\u4e1c".equals(value) || "\u897f\u5230\u4e1c".equals(value)) return "east";
        if ("west".equals(value) || "east_to_west".equals(value) || "east-west".equals(value)
            || "\u4ece\u4e1c\u5230\u897f".equals(value) || "\u4e1c\u5230\u897f".equals(value)) return "west";
        throw new IllegalArgumentException("Unknown extra structure entranceAnimation: " + raw);
    }
}
