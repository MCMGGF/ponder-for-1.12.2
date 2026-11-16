package net.createmod.ponder.foundation.content.mmce;

import java.util.Arrays;

/** Owned quad vertices in NORTH-facing controller-local block units. */
public final class MmceStaticModelMesh {

    static final int STRIDE = 8;
    public static final int MAX_VERTICES = 4096 * 6 * 4;

    public interface VertexConsumer {
        void accept(float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ);
    }

    public static final class Bounds {
        public final double minX;
        public final double minY;
        public final double minZ;
        public final double maxX;
        public final double maxY;
        public final double maxZ;

        private Bounds(float[] data) {
            double x0 = Double.POSITIVE_INFINITY;
            double y0 = Double.POSITIVE_INFINITY;
            double z0 = Double.POSITIVE_INFINITY;
            double x1 = Double.NEGATIVE_INFINITY;
            double y1 = Double.NEGATIVE_INFINITY;
            double z1 = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < data.length; i += STRIDE) {
                x0 = Math.min(x0, data[i]);
                y0 = Math.min(y0, data[i + 1]);
                z0 = Math.min(z0, data[i + 2]);
                x1 = Math.max(x1, data[i]);
                y1 = Math.max(y1, data[i + 1]);
                z1 = Math.max(z1, data[i + 2]);
            }
            minX = x0;
            minY = y0;
            minZ = z0;
            maxX = x1;
            maxY = y1;
            maxZ = z1;
        }
    }

    private final float[] vertices;
    private final Bounds bounds;
    private final int boneCount;
    private final int cubeCount;

    MmceStaticModelMesh(float[] data, int used, int boneCount, int cubeCount) {
        if (used <= 0 || used > data.length || used % (STRIDE * 4) != 0
            || used / STRIDE > MAX_VERTICES) {
            throw new IllegalArgumentException("模型顶点数量无效或超过预览限制");
        }
        vertices = Arrays.copyOf(data, used);
        for (float value : vertices) {
            if (Float.isNaN(value) || Float.isInfinite(value) || Math.abs(value) > 1048576F) {
                throw new IllegalArgumentException("模型包含无效或过大的顶点、纹理或法线坐标");
            }
        }
        bounds = new Bounds(vertices);
        this.boneCount = boneCount;
        this.cubeCount = cubeCount;
    }

    public int getVertexCount() {
        return vertices.length / STRIDE;
    }

    public long getByteSize() {
        return vertices.length * 4L;
    }

    public int getBoneCount() {
        return boneCount;
    }

    public int getCubeCount() {
        return cubeCount;
    }

    public Bounds getBounds() {
        return bounds;
    }

    public void forEachVertex(VertexConsumer consumer) {
        for (int i = 0; i < vertices.length; i += STRIDE) {
            consumer.accept(vertices[i], vertices[i + 1], vertices[i + 2], vertices[i + 3],
                vertices[i + 4], vertices[i + 5], vertices[i + 6], vertices[i + 7]);
        }
    }
}
