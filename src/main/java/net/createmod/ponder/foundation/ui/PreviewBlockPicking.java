package net.createmod.ponder.foundation.ui;

import java.util.List;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Orthographic surface picking in the same camera and per-block affine basis as the preview. */
final class PreviewBlockPicking {
    private static final int[][] TRIANGLES = {
        {0, 2, 6}, {0, 6, 4}, {1, 5, 7}, {1, 7, 3},
        {0, 4, 5}, {0, 5, 1}, {2, 3, 7}, {2, 7, 6},
        {0, 1, 3}, {0, 3, 2}, {4, 6, 7}, {4, 7, 5}
    };
    private PreviewBlockPicking() {}

    static final class Camera {
        final Vec3d center;
        final double yaw, pitch, scale, originX, anchorY;
        Camera(Vec3d center, double yaw, double pitch, double scale, double originX, double anchorY) {
            if (!finite(center) || !Double.isFinite(yaw) || !Double.isFinite(pitch)
                || !Double.isFinite(scale) || scale <= 0 || !Double.isFinite(originX) || !Double.isFinite(anchorY))
                throw new IllegalArgumentException("Finite preview camera with positive scale required");
            this.center = center; this.yaw = Math.toRadians(yaw); this.pitch = Math.toRadians(pitch);
            this.scale = scale; this.originX = originX; this.anchorY = anchorY;
        }
        Vec3d project(Vec3d point) {
            Vec3d v = point.subtract(center);
            double x = v.x * Math.cos(yaw) + v.z * Math.sin(yaw);
            double z = v.z * Math.cos(yaw) - v.x * Math.sin(yaw);
            double y = v.y * Math.cos(pitch) - z * Math.sin(pitch);
            double depth = v.y * Math.sin(pitch) + z * Math.cos(pitch);
            return new Vec3d(originX + x * scale, anchorY - y * scale, depth);
        }
    }
    static final class Candidate {
        final PonderSceneRuntime.RuntimeBlockState block;
        final AxisAlignedBB bounds;
        Candidate(PonderSceneRuntime.RuntimeBlockState block, AxisAlignedBB bounds) {
            this.block = block; this.bounds = bounds;
        }
    }
    static final class Hit {
        final BlockPos block;
        final Vec3d point;
        final Vec3d displayedPoint;
        final double depth;
        Hit(BlockPos block, Vec3d point, Vec3d displayedPoint, double depth) {
            this.block = block.toImmutable(); this.point = point; this.displayedPoint = displayedPoint; this.depth = depth;
        }
    }
    static Hit pick(List<Candidate> candidates, Camera camera, double x, double y) {
        if (camera == null || candidates == null || !Double.isFinite(x) || !Double.isFinite(y)) return null;
        if (candidates.size() > 262144) throw new IllegalArgumentException("Preview picking geometry budget exceeded");
        Hit nearest = null;
        for (Candidate candidate : candidates) {
            if (candidate == null || candidate.block == null || candidate.bounds == null
                || !candidate.block.visible || candidate.block.destroyed || candidate.block.fade <= 0) continue;
            Vec3d[] source = corners(candidate.bounds), display = new Vec3d[8], screen = new Vec3d[8];
            boolean valid = true;
            for (int i = 0; i < 8; i++) {
                display[i] = PonderSceneRuntime.transformPoint(candidate.block, source[i]);
                screen[i] = camera.project(display[i]); valid &= finite(screen[i]);
            }
            if (!valid) continue;
            for (int[] triangle : TRIANGLES) {
                Vec3d a = screen[triangle[0]], b = screen[triangle[1]], c = screen[triangle[2]];
                double denominator = (b.y-c.y)*(a.x-c.x)+(c.x-b.x)*(a.y-c.y);
                if (Math.abs(denominator) < 1E-10) continue;
                double wa = ((b.y-c.y)*(x-c.x)+(c.x-b.x)*(y-c.y))/denominator;
                double wb = ((c.y-a.y)*(x-c.x)+(a.x-c.x)*(y-c.y))/denominator, wc = 1-wa-wb;
                if (wa < -1E-7 || wb < -1E-7 || wc < -1E-7) continue;
                double depth = a.z*wa+b.z*wb+c.z*wc;
                // GUI's orthographic projection looks towards negative Z: larger model Z is nearer.
                if (nearest == null || depth > nearest.depth + 1E-7) nearest = new Hit(candidate.block.pos,
                    weighted(source, triangle, wa, wb, wc), weighted(display, triangle, wa, wb, wc), depth);
            }
        }
        return nearest;
    }
    private static Vec3d weighted(Vec3d[] values, int[] triangle, double a, double b, double c) {
        Vec3d x = values[triangle[0]], y = values[triangle[1]], z = values[triangle[2]];
        return new Vec3d(x.x*a+y.x*b+z.x*c, x.y*a+y.y*b+z.y*c, x.z*a+y.z*b+z.z*c);
    }
    private static Vec3d[] corners(AxisAlignedBB b) {
        Vec3d[] result = new Vec3d[8];
        for (int i = 0; i < 8; i++) result[i] = new Vec3d((i&1)==0?b.minX:b.maxX,
            (i&2)==0?b.minY:b.maxY, (i&4)==0?b.minZ:b.maxZ);
        return result;
    }
    private static boolean finite(Vec3d v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}
