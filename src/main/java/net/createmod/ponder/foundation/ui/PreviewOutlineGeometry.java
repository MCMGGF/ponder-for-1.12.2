package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;

/** Immutable world-space line geometry; cache ownership never outlives one preview frame. */
final class PreviewOutlineGeometry {

    static final int CHASE_TICKS = 6;
    private static final double EPSILON = 1.0E-6D;
    private static final AxisAlignedBB UNIT = new AxisAlignedBB(0, 0, 0, 1, 1, 1);
    private PonderScene cachedScene;
    private PonderSceneRuntime.RuntimeState cachedState;
    private float cachedTick = Float.NaN;
    private List<Outline> cachedOutlines = Collections.emptyList();

    List<Outline> frame(PonderScene scene, PonderSceneRuntime.RuntimeState state, float tick, IBlockAccess access) {
        if (scene == cachedScene && state == cachedState && Float.compare(tick, cachedTick) == 0) {
            return cachedOutlines;
        }
        clear();
        if (scene == null || state == null || !Float.isFinite(tick)) {
            return cachedOutlines;
        }

        Map<String, PonderScene.OverlayEvent> latestBySlot = new LinkedHashMap<String, PonderScene.OverlayEvent>();
        Map<PonderScene.OverlayEvent, PonderScene.OverlayEvent> predecessors =
            new LinkedHashMap<PonderScene.OverlayEvent, PonderScene.OverlayEvent>();
        for (PonderScene.OverlayEvent event : scene.getOverlayEvents()) {
            if (event.getTick() > tick || !isGeometry(event) || scene.isOverlayCleared(event, tick)) {
                continue;
            }
            String slot = slot(event);
            if (slot != null) {
                PonderScene.OverlayEvent previous = latestBySlot.put(slot, event);
                if (previous != null) {
                    predecessors.put(event, previous);
                }
            }
        }

        Map<BlockPos, Shape> shapes = new LinkedHashMap<BlockPos, Shape>();
        List<Outline> outlines = new ArrayList<Outline>();
        for (PonderScene.OverlayEvent event : scene.getOverlayEvents()) {
            if (!active(event, tick) || !isGeometry(event) || scene.isOverlayCleared(event, tick)) {
                continue;
            }
            String slot = slot(event);
            if (slot != null && latestBySlot.get(slot) != event) {
                continue;
            }
            List<Edge> lines = lines(event, state, access, shapes);
            PonderScene.OverlayEvent previous = predecessors.get(event);
            if (event.isOutlineChasing() && previous != null && tick - event.getTick() < CHASE_TICKS
                && previous.getTick() + previous.getDuration() >= event.getTick()) {
                List<Edge> from = lines(previous, state, access, shapes);
                lines = chase(from, lines, Math.max(0F, (tick - event.getTick()) / CHASE_TICKS));
            }
            if (!lines.isEmpty()) {
                outlines.add(new Outline(event, lines, opacity(event, tick)));
            }
        }
        cachedScene = scene;
        cachedState = state;
        cachedTick = tick;
        cachedOutlines = Collections.unmodifiableList(outlines);
        return cachedOutlines;
    }

    void clear() {
        cachedScene = null;
        cachedState = null;
        cachedTick = Float.NaN;
        cachedOutlines = Collections.emptyList();
    }

    private static String slot(PonderScene.OverlayEvent event) {
        String id = event.getOverlayId();
        return id == null || id.isEmpty() ? null : event.getType().name() + ':' + id;
    }

    private static boolean active(PonderScene.OverlayEvent event, float tick) {
        return event.getTick() <= tick && tick <= event.getTick() + Math.max(0, event.getDuration());
    }

    private static boolean isGeometry(PonderScene.OverlayEvent event) {
        return event.getType() == PonderScene.OverlayEventType.SCENE_OUTLINE
            || event.getType() == PonderScene.OverlayEventType.SCENE_LINE
            || event.getType() == PonderScene.OverlayEventType.VALUE_BOX;
    }

    static float opacity(PonderScene.OverlayEvent event, float tick) {
        float duration = Math.max(1, event.getDuration());
        float fadeTicks = Math.min(6F, duration / 2F);
        return Math.max(0F, Math.min(1F,
            Math.min((tick - event.getTick() + 1F) / fadeTicks,
                (event.getTick() + duration - tick + 1F) / fadeTicks)));
    }

    private static List<Edge> lines(PonderScene.OverlayEvent event, PonderSceneRuntime.RuntimeState state,
        IBlockAccess access, Map<BlockPos, Shape> shapes) {
        if (event.getSourcePositions().isEmpty() && hasUnavailableTarget(event.getTargetPositions(), state)) {
            return Collections.emptyList();
        }
        if (event.getType() == PonderScene.OverlayEventType.SCENE_LINE) {
            Vec3d start = event.getLineStart();
            Vec3d end = event.getLineEnd();
            if (!finite(start) || !finite(end)) {
                return Collections.emptyList();
            }
            PonderSceneRuntime.RuntimeBlockState owner = owner(event.getTargetPositions(), state);
            return Collections.singletonList(new Edge(PonderSceneRuntime.transformPoint(owner, start),
                PonderSceneRuntime.transformPoint(owner, end), owner == null ? 1F : owner.fade));
        }
        if (event.getType() == PonderScene.OverlayEventType.VALUE_BOX) {
            Vec3d center = event.getValueBoxCenter();
            Vec3d size = event.getValueBoxExpand();
            if (!finite(center) || !finite(size)) {
                return Collections.emptyList();
            }
            return box(new AxisAlignedBB(center.x - Math.abs(size.x), center.y - Math.abs(size.y),
                center.z - Math.abs(size.z), center.x + Math.abs(size.x), center.y + Math.abs(size.y),
                center.z + Math.abs(size.z)), owner(event.getTargetPositions(), state));
        }

        if (event.getSourcePositions().isEmpty()) {
            return valid(event.getSceneBounds()) ? box(event.getSceneBounds(), owner(event.getTargetPositions(), state))
                : Collections.<Edge>emptyList();
        }

        List<Shape> selected = new ArrayList<Shape>();
        for (BlockPos position : new LinkedHashSet<BlockPos>(event.getSourcePositions())) {
            Shape shape = shape(position, state, access, shapes);
            if (shape != null) {
                selected.add(shape);
            }
        }
        if (selected.isEmpty()) {
            return Collections.emptyList();
        }
        AxisAlignedBB solid = solidCuboid(selected);
        if (solid != null) {
            return box(solid, selected.get(0).block);
        }
        Map<EdgeKey, Edge> unique = new LinkedHashMap<EdgeKey, Edge>();
        for (Shape shape : selected) {
            for (Edge edge : box(shape.worldBounds, shape.block)) {
                EdgeKey key = new EdgeKey(edge);
                Edge existing = unique.get(key);
                if (existing == null || existing.alpha < edge.alpha) {
                    unique.put(key, edge);
                }
            }
        }
        return new ArrayList<Edge>(unique.values());
    }

    private static List<Edge> chase(List<Edge> previous, List<Edge> current, float progress) {
        if (previous.size() != current.size() || previous.isEmpty()) {
            return current;
        }
        float eased = progress * progress * (3F - 2F * progress);
        List<Edge> result = new ArrayList<Edge>(current.size());
        for (int i = 0; i < current.size(); i++) {
            Edge a = previous.get(i);
            Edge b = current.get(i);
            result.add(new Edge(interpolate(a.start, b.start, eased), interpolate(a.end, b.end, eased),
                b.alpha));
        }
        return result;
    }

    private static Vec3d interpolate(Vec3d from, Vec3d to, double amount) {
        return from.add(to.subtract(from).scale(amount));
    }

    static List<Vec3d> targetPoints(List<BlockPos> positions, PonderSceneRuntime.RuntimeState state,
        IBlockAccess access) {
        if (state == null || positions == null || positions.isEmpty()) {
            return Collections.emptyList();
        }
        List<Vec3d> points = new ArrayList<Vec3d>();
        for (BlockPos position : new LinkedHashSet<BlockPos>(positions)) {
            Shape shape = shape(position, state, access, null);
            if (shape == null) {
                continue;
            }
            for (Vec3d corner : corners(shape.worldBounds)) {
                points.add(PonderSceneRuntime.transformPoint(shape.block, corner));
            }
        }
        return Collections.unmodifiableList(points);
    }

    private static Shape shape(BlockPos position, PonderSceneRuntime.RuntimeState state, IBlockAccess access,
        Map<BlockPos, Shape> cache) {
        if (cache != null && cache.containsKey(position)) {
            return cache.get(position);
        }
        PonderSceneRuntime.RuntimeBlockState block = state.blocksByPosition.get(position);
        Shape result = null;
        if (block != null && block.visible && block.fade > 0F && block.currentState != null
            && block.currentState.getBlock() != Blocks.AIR) {
            IBlockState actual = block.currentState;
            IBlockAccess shapeAccess = access == null ? null : new ShapeAccess(access, state, block);
            if (shapeAccess != null && !(actual.getBlock() instanceof BlockRailBase)) {
                try {
                    actual = actual.getActualState(shapeAccess, position);
                } catch (RuntimeException | LinkageError ignored) {
                    actual = block.currentState;
                }
            }
            AxisAlignedBB bounds;
            try {
                bounds = actual.getBoundingBox(shapeAccess, position);
            } catch (RuntimeException | LinkageError ignored) {
                bounds = UNIT;
            }
            if (!valid(bounds)) {
                bounds = UNIT;
            }
            result = new Shape(block, bounds.offset(position), fullCube(bounds));
        }
        if (cache != null) {
            cache.put(position, result);
        }
        return result;
    }

    static AxisAlignedBB selectionBounds(BlockPos position, PonderSceneRuntime.RuntimeState state, IBlockAccess access) {
        Shape selected = shape(position, state, access, null);
        return selected == null ? null : selected.worldBounds;
    }

    static List<Outline> selection(BlockPos position, PonderSceneRuntime.RuntimeState state, IBlockAccess access) {
        Shape selected = shape(position, state, access, null);
        return selected == null ? Collections.<Outline>emptyList() : Collections.singletonList(
            new Outline(null, box(selected.worldBounds, selected.block), 1F, 0x78DBBD, true));
    }

    private static PonderSceneRuntime.RuntimeBlockState owner(List<BlockPos> positions,
        PonderSceneRuntime.RuntimeState state) {
        PonderSceneRuntime.RuntimeBlockState first = null;
        for (BlockPos position : positions) {
            PonderSceneRuntime.RuntimeBlockState block = state.blocksByPosition.get(position);
            if (block == null || !block.visible || block.fade <= 0F) {
                return null;
            }
            if (first == null) {
                first = block;
            } else if (!sameTransform(first, block)) {
                return null;
            }
        }
        return first;
    }

    private static boolean hasUnavailableTarget(List<BlockPos> positions, PonderSceneRuntime.RuntimeState state) {
        for (BlockPos position : positions) {
            PonderSceneRuntime.RuntimeBlockState block = state.blocksByPosition.get(position);
            // Legacy coordinates may intentionally point at empty space, but a hidden
            // recorded block must not leave a bright untransformed marker behind.
            if (block != null && (!block.visible || block.destroyed || block.fade <= 0F || block.currentState == null
                || block.currentState.getBlock() == Blocks.AIR)) {
                return true;
            }
        }
        return false;
    }

    static boolean sameTransform(PonderSceneRuntime.RuntimeBlockState a, PonderSceneRuntime.RuntimeBlockState b) {
        if (Math.abs(a.fade - b.fade) > EPSILON) {
            return false;
        }
        // Affine basis comparison accepts equivalent transform histories without guessing section ownership.
        for (Vec3d basis : new Vec3d[] { Vec3d.ZERO, new Vec3d(1, 0, 0), new Vec3d(0, 1, 0), new Vec3d(0, 0, 1) }) {
            Vec3d left = PonderSceneRuntime.transformPoint(a, basis);
            Vec3d right = PonderSceneRuntime.transformPoint(b, basis);
            if (left.squareDistanceTo(right) > EPSILON * EPSILON) {
                return false;
            }
        }
        return true;
    }

    private static AxisAlignedBB solidCuboid(List<Shape> selected) {
        Shape first = selected.get(0);
        int minX = first.block.pos.getX();
        int minY = first.block.pos.getY();
        int minZ = first.block.pos.getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;
        for (Shape shape : selected) {
            if (!shape.fullCube || !sameTransform(first.block, shape.block)) {
                return null;
            }
            BlockPos pos = shape.block.pos;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        long width = (long) maxX - minX + 1;
        long height = (long) maxY - minY + 1;
        long depth = (long) maxZ - minZ + 1;
        if (width > selected.size() || height > selected.size() || depth > selected.size()
            || width * height > selected.size() || width * height * depth != selected.size()) {
            return null;
        }
        return new AxisAlignedBB(minX, minY, minZ, (double) maxX + 1, (double) maxY + 1, (double) maxZ + 1);
    }

    private static boolean fullCube(AxisAlignedBB bounds) {
        return Math.abs(bounds.minX) < EPSILON && Math.abs(bounds.minY) < EPSILON
            && Math.abs(bounds.minZ) < EPSILON && Math.abs(bounds.maxX - 1) < EPSILON
            && Math.abs(bounds.maxY - 1) < EPSILON && Math.abs(bounds.maxZ - 1) < EPSILON;
    }

    private static boolean valid(AxisAlignedBB box) {
        return box != null && Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
            && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ)
            && box.maxX > box.minX && box.maxY > box.minY && box.maxZ > box.minZ;
    }

    private static boolean finite(Vec3d value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static Vec3d[] corners(AxisAlignedBB box) {
        Vec3d[] corners = new Vec3d[8];
        for (int i = 0; i < corners.length; i++) {
            corners[i] = new Vec3d((i & 1) == 0 ? box.minX : box.maxX,
                (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ);
        }
        return corners;
    }

    private static List<Edge> box(AxisAlignedBB box, PonderSceneRuntime.RuntimeBlockState owner) {
        Vec3d[] corners = corners(box);
        for (int i = 0; i < corners.length; i++) {
            corners[i] = PonderSceneRuntime.transformPoint(owner, corners[i]);
        }
        List<Edge> edges = new ArrayList<Edge>(12);
        for (int i = 0; i < corners.length; i++) {
            for (int axis : new int[] { 1, 2, 4 }) {
                if ((i & axis) == 0) {
                    edges.add(new Edge(corners[i], corners[i | axis], owner == null ? 1F : owner.fade));
                }
            }
        }
        return edges;
    }

    static final class Outline {
        final PonderScene.OverlayEvent event;
        final List<Edge> edges;
        final float alpha;
        final int color;
        final boolean wide;

        private Outline(PonderScene.OverlayEvent event, List<Edge> edges, float alpha) {
            this(event, edges, alpha, event.getColor(), event.isLineWide());
        }

        private Outline(PonderScene.OverlayEvent event, List<Edge> edges, float alpha, int color, boolean wide) {
            this.event = event;
            this.edges = Collections.unmodifiableList(edges);
            this.alpha = alpha;
            this.color = color;
            this.wide = wide;
        }
    }

    static final class Edge {
        final Vec3d start;
        final Vec3d end;
        final float alpha;

        private Edge(Vec3d start, Vec3d end, float alpha) {
            this.start = start;
            this.end = end;
            this.alpha = alpha;
        }
    }

    private static final class Shape {
        final PonderSceneRuntime.RuntimeBlockState block;
        final AxisAlignedBB worldBounds;
        final boolean fullCube;

        private Shape(PonderSceneRuntime.RuntimeBlockState block, AxisAlignedBB bounds, boolean fullCube) {
            this.block = block;
            this.worldBounds = bounds;
            this.fullCube = fullCube;
        }
    }

    private static final class EdgeKey {
        private final String key;

        private EdgeKey(Edge edge) {
            String start = pointKey(edge.start);
            String end = pointKey(edge.end);
            key = start.compareTo(end) < 0 ? start + ':' + end : end + ':' + start;
        }

        private static String pointKey(Vec3d point) {
            return Math.round(point.x / EPSILON) + "," + Math.round(point.y / EPSILON) + ","
                + Math.round(point.z / EPSILON);
        }

        @Override
        public int hashCode() { return key.hashCode(); }

        @Override
        public boolean equals(Object other) {
            return other instanceof EdgeKey && key.equals(((EdgeKey) other).key);
        }
    }

    private static final class ShapeAccess implements IBlockAccess {
        private final IBlockAccess source;
        private final PonderSceneRuntime.RuntimeState state;
        private final PonderSceneRuntime.RuntimeBlockState block;

        private ShapeAccess(IBlockAccess source, PonderSceneRuntime.RuntimeState state,
            PonderSceneRuntime.RuntimeBlockState block) {
            this.source = source;
            this.state = state;
            this.block = block;
        }

        public IBlockState getBlockState(BlockPos pos) {
            return PreviewBlockRendering.neighborState(state, block, pos);
        }

        public TileEntity getTileEntity(BlockPos pos) {
            return isAirBlock(pos) ? null : source.getTileEntity(pos);
        }

        public int getCombinedLight(BlockPos pos, int lightValue) { return source.getCombinedLight(pos, lightValue); }
        public boolean isAirBlock(BlockPos pos) { return getBlockState(pos).getBlock() == Blocks.AIR; }
        public Biome getBiome(BlockPos pos) { return source.getBiome(pos); }
        public WorldType getWorldType() { return source.getWorldType(); }
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return getBlockState(pos).getStrongPower(this, pos, direction);
        }
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean defaultValue) {
            return !isAirBlock(pos) && getBlockState(pos).isSideSolid(this, pos, side);
        }
    }
}
