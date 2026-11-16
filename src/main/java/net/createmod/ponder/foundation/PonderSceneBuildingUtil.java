package net.createmod.ponder.foundation;

import net.createmod.ponder.api.scene.PositionUtil;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.api.scene.SelectionUtil;
import net.createmod.ponder.api.scene.VectorUtil;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

public class PonderSceneBuildingUtil implements SceneBuildingUtil {

    private static final int DEFAULT_SCENE_HEIGHT = 5;
    private static final int DEFAULT_SCENE_SPAN = 5;

    private final PonderScene scene;
    private final SelectionUtil selectionUtil;
    private final VectorUtil vectorUtil;
    private final PositionUtil positionUtil;

    public PonderSceneBuildingUtil(PonderScene scene) {
        this.scene = scene;
        this.selectionUtil = new PonderSelectionUtil();
        this.vectorUtil = new PonderVectorUtil();
        this.positionUtil = new PonderPositionUtil();
    }

    @Override
    public SelectionUtil select() {
        return selectionUtil;
    }

    @Override
    public VectorUtil vector() {
        return vectorUtil;
    }

    @Override
    public PositionUtil grid() {
        return positionUtil;
    }

    private BlockPos getSceneMaxPos() {
        int maxX = Math.max(DEFAULT_SCENE_SPAN - 1, scene.getBasePlateOffsetX() + scene.getBasePlateSize() - 1);
        int maxY = DEFAULT_SCENE_HEIGHT - 1;
        int maxZ = Math.max(DEFAULT_SCENE_SPAN - 1, scene.getBasePlateOffsetZ() + scene.getBasePlateSize() - 1);
        BlockPos schematicSize = scene.getSchematic().getSize();
        maxX = Math.max(maxX, Vec3iAccessor.x(schematicSize) - 1);
        maxY = Math.max(maxY, Vec3iAccessor.y(schematicSize) - 1);
        maxZ = Math.max(maxZ, Vec3iAccessor.z(schematicSize) - 1);
        for (BlockPos pos : scene.getSchematic().getBlocks().keySet()) {
            maxX = Math.max(maxX, Vec3iAccessor.x(pos));
            maxY = Math.max(maxY, Vec3iAccessor.y(pos));
            maxZ = Math.max(maxZ, Vec3iAccessor.z(pos));
        }
        return new BlockPos(maxX, maxY, maxZ);
    }

    private BlockPos getSceneMinPos() {
        int minX = Math.min(0, scene.getBasePlateOffsetX());
        int minY = 0;
        int minZ = Math.min(0, scene.getBasePlateOffsetZ());
        for (BlockPos pos : scene.getSchematic().getBlocks().keySet()) {
            minX = Math.min(minX, Vec3iAccessor.x(pos));
            minY = Math.min(minY, Vec3iAccessor.y(pos));
            minZ = Math.min(minZ, Vec3iAccessor.z(pos));
        }
        return new BlockPos(minX, minY, minZ);
    }

    private final class PonderPositionUtil implements PositionUtil {

        @Override
        public BlockPos at(int x, int y, int z) {
            return new BlockPos(x, y, z);
        }

        @Override
        public BlockPos zero() {
            return BlockPos.ORIGIN;
        }
    }

    private final class PonderVectorUtil implements VectorUtil {

        @Override
        public Vec3d centerOf(int x, int y, int z) {
            return centerOf(grid().at(x, y, z));
        }

        @Override
        public Vec3d centerOf(BlockPos pos) {
            return new Vec3d(Vec3iAccessor.x(pos) + 0.5D, Vec3iAccessor.y(pos) + 0.5D,
                Vec3iAccessor.z(pos) + 0.5D);
        }

        @Override
        public Vec3d topOf(int x, int y, int z) {
            return blockSurface(grid().at(x, y, z), EnumFacing.UP);
        }

        @Override
        public Vec3d topOf(BlockPos pos) {
            return blockSurface(pos, EnumFacing.UP);
        }

        @Override
        public Vec3d blockSurface(BlockPos pos, EnumFacing face) {
            return blockSurface(pos, face, 0.0F);
        }

        @Override
        public Vec3d blockSurface(BlockPos pos, EnumFacing face, float margin) {
            Vec3d normal = new Vec3d(face.getDirectionVec()).scale(0.5D + margin);
            return centerOf(pos).add(normal);
        }

        @Override
        public Vec3d of(double x, double y, double z) {
            return new Vec3d(x, y, z);
        }
    }

    private final class PonderSelectionUtil implements SelectionUtil {

        @Override
        public Selection everywhere() {
            return SelectionImpl.fromTo(getSceneMinPos(), getSceneMaxPos());
        }

        @Override
        public Selection position(int x, int y, int z) {
            return position(grid().at(x, y, z));
        }

        @Override
        public Selection position(BlockPos pos) {
            return cuboid(pos, Vec3i.NULL_VECTOR);
        }

        @Override
        public Selection fromTo(int x, int y, int z, int x2, int y2, int z2) {
            return fromTo(new BlockPos(x, y, z), new BlockPos(x2, y2, z2));
        }

        @Override
        public Selection fromTo(BlockPos pos1, BlockPos pos2) {
            return SelectionImpl.fromTo(pos1, pos2);
        }

        @Override
        public Selection column(int x, int z) {
            int minY = Vec3iAccessor.y(getSceneMinPos());
            int maxY = Vec3iAccessor.y(getSceneMaxPos());
            return cuboid(new BlockPos(x, minY, z), new Vec3i(0, maxY - minY, 0));
        }

        @Override
        public Selection layer(int y) {
            return layers(y, 1);
        }

        @Override
        public Selection layersFrom(int y) {
            return layers(y, Vec3iAccessor.y(getSceneMaxPos()) - y + 1);
        }

        @Override
        public Selection layers(int y, int height) {
            BlockPos min = getSceneMinPos();
            BlockPos max = getSceneMaxPos();
            int clampedY = Math.max(Vec3iAccessor.y(min), Math.min(y, Vec3iAccessor.y(max)));
            int clampedHeight = Math.max(1, Math.min(height, Vec3iAccessor.y(max) - clampedY + 1));
            return cuboid(new BlockPos(Vec3iAccessor.x(min), clampedY, Vec3iAccessor.z(min)),
                new Vec3i(Vec3iAccessor.x(max) - Vec3iAccessor.x(min), clampedHeight - 1,
                    Vec3iAccessor.z(max) - Vec3iAccessor.z(min)));
        }

        @Override
        public Selection cuboid(BlockPos origin, Vec3i size) {
            return SelectionImpl.fromTo(origin, origin.add(size));
        }
    }
}
