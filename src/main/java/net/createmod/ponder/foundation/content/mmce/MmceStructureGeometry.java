package net.createmod.ponder.foundation.content.mmce;

/** Coordinate-only transformation, kept independent of Minecraft for regression tests. */
public final class MmceStructureGeometry {

    private MmceStructureGeometry() {
    }

    public static Bounds bounds(Iterable<int[]> positions) {
        int minX = 0;
        int minY = 0;
        int minZ = 0;
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        long count = 0L;
        for (int[] position : positions) {
            if (position == null || position.length != 3) {
                throw new IllegalArgumentException("结构坐标必须包含 X、Y、Z");
            }
            MmcePreviewLimits.checkBlockCount(++count);
            minX = Math.min(minX, position[0]);
            minY = Math.min(minY, position[1]);
            minZ = Math.min(minZ, position[2]);
            maxX = Math.max(maxX, position[0]);
            maxY = Math.max(maxY, position[1]);
            maxZ = Math.max(maxZ, position[2]);
        }
        int sizeX = MmcePreviewLimits.checkedExtent(minX, maxX);
        int sizeY = MmcePreviewLimits.checkedExtent(minY, maxY);
        int sizeZ = MmcePreviewLimits.checkedExtent(minZ, maxZ);
        return new Bounds(-minX, -minY, -minZ, sizeX, sizeY, sizeZ);
    }

    public static final class Bounds {
        public final int originX;
        public final int originY;
        public final int originZ;
        public final int sizeX;
        public final int sizeY;
        public final int sizeZ;

        private Bounds(int originX, int originY, int originZ, int sizeX, int sizeY, int sizeZ) {
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
        }

        public int[] toPreview(int x, int y, int z) {
            return new int[] { Math.addExact(x, originX), Math.addExact(y, originY), Math.addExact(z, originZ) };
        }

        public int[] toOriginal(int x, int y, int z) {
            return new int[] { Math.subtractExact(x, originX), Math.subtractExact(y, originY),
                Math.subtractExact(z, originZ) };
        }
    }
}
