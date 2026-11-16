package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import net.createmod.ponder.foundation.PonderScene.WorldEvent;
import net.createmod.ponder.foundation.PonderScene.WorldEventType;
import net.createmod.ponder.foundation.Vec3iAccessor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class PreviewMotionBounds {

    private static final int TIME_SUBDIVISIONS = 16;
    private static final double FULL_TURN = 360.0D;

    private PreviewMotionBounds() {
    }

    static boolean isMotion(WorldEvent event) {
        if (event.getType() == WorldEventType.MOVE_SECTION && event.getOffset() != null) {
            requireFinite(event.getOffset());
            return !nearZero(event.getOffset());
        }
        if (event.getType() == WorldEventType.ROTATE_SECTION
            && event.getRotation() != null && event.getPivot() != null) {
            requireFinite(event.getRotation());
            requireFinite(event.getPivot());
            return !nearZero(event.getRotation());
        }
        return false;
    }

    static Bounds compute(BlockPos position, List<WorldEvent> events) {
        return compute(Bounds.block(position), events);
    }

    static Bounds compute(Bounds geometry, List<WorldEvent> events) {
        Bounds result = new Bounds(geometry.minX, geometry.minY, geometry.minZ,
            geometry.maxX, geometry.maxY, geometry.maxZ);
        TreeSet<Float> boundaries = new TreeSet<Float>();
        for (WorldEvent event : events) {
            if (!isMotion(event)) {
                continue;
            }
            float start = event.getTick();
            double completion = (double) start + event.getDuration();
            float end = (float) completion;
            if (end < completion) {
                end = Math.nextUp(end);
            }
            boundaries.add(Float.valueOf(start));
            boundaries.add(Float.valueOf(end));
        }
        if (boundaries.isEmpty()) {
            return result;
        }

        double first = boundaries.first().doubleValue();
        double last = boundaries.last().doubleValue();
        for (int i = 1; i < TIME_SUBDIVISIONS; i++) {
            boundaries.add(Float.valueOf((float) (first + (last - first) * i / TIME_SUBDIVISIONS)));
        }
        List<Float> times = new ArrayList<Float>(boundaries);
        // Each interval encloses all progress values, rather than sampling frames.
        // Event boundaries keep instant steps discrete and completed transforms fixed.
        for (int index = 0; index < times.size(); index++) {
            float start = times.get(index).floatValue();
            float end = index + 1 < times.size() ? times.get(index + 1).floatValue() : start;
            boolean endpoint = start == end;
            List<MotionStep> steps = new ArrayList<MotionStep>();
            for (WorldEvent event : events) {
                if (isMotion(event) && !(endpoint ? event.getTick() > start : event.getTick() >= end)) {
                    double from = progressAt(event, start);
                    double to = progressAt(event, end);
                    if (from > 0.0D || to > 0.0D) {
                        steps.add(new MotionStep(event, from, to));
                    }
                }
            }
            for (int x = 0; x <= 1; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = 0; z <= 1; z++) {
                        Bounds corner = Bounds.point(x == 0 ? geometry.minX : geometry.maxX,
                            y == 0 ? geometry.minY : geometry.maxY, z == 0 ? geometry.minZ : geometry.maxZ);
                        for (MotionStep step : steps) {
                            step.apply(corner);
                        }
                        result.include(corner);
                    }
                }
            }
        }
        return result;
    }

    /** Encloses every ordered subsequence, including membership releases and later transfers. */
    static Bounds computeOwnership(Bounds geometry, List<WorldEvent> events) {
        Bounds result = new Bounds(geometry.minX, geometry.minY, geometry.minZ, geometry.maxX, geometry.maxY, geometry.maxZ);
        for (WorldEvent event : events) if (isMotion(event)) {
            Bounds next = new Bounds(result.minX, result.minY, result.minZ, result.maxX, result.maxY, result.maxZ);
            new MotionStep(event, 0, 1).apply(next);
            result.include(next);
        }
        return result;
    }

    private static double progressAt(WorldEvent event, float tick) {
        if (event.getTick() > tick) {
            return 0.0D;
        }
        double progress = PonderSceneRuntime.transformProgress(tick - event.getTick(), event.getDuration());
        Vec3d amount = event.getType() == WorldEventType.MOVE_SECTION ? event.getOffset() : event.getRotation();
        return nearZero(amount.scale(progress)) ? 0.0D : progress;
    }

    private static boolean nearZero(Vec3d vector) {
        return Math.abs(vector.x) < 1.0E-4D && Math.abs(vector.y) < 1.0E-4D
            && Math.abs(vector.z) < 1.0E-4D;
    }

    private static void requireFinite(Vec3d vector) {
        requireFinite(vector.x);
        requireFinite(vector.y);
        requireFinite(vector.z);
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Non-finite Ponder section motion bounds");
        }
    }

    private static final class MotionStep {
        private final Vec3d offset;
        private final Vec3d pivot;
        private final double from;
        private final double to;
        private final AxisSweep[] axes;

        private MotionStep(WorldEvent event, double from, double to) {
            this.from = from;
            this.to = to;
            offset = event.getType() == WorldEventType.MOVE_SECTION ? event.getOffset() : null;
            pivot = event.getPivot();
            Vec3d rotation = event.getType() == WorldEventType.ROTATE_SECTION ? event.getRotation() : null;
            axes = rotation == null ? null : new AxisSweep[] {
                new AxisSweep(rotation.x * from, rotation.x * to),
                new AxisSweep(rotation.y * from, rotation.y * to),
                new AxisSweep(rotation.z * from, rotation.z * to)
            };
        }

        private void apply(Bounds bounds) {
            if (axes == null) {
                bounds.move(offset, from, to);
            } else {
                bounds.rotate(pivot, axes);
            }
        }
    }

    static final class Bounds {
        double minX;
        double minY;
        double minZ;
        double maxX;
        double maxY;
        double maxZ;

        Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            checkFinite();
        }

        private static Bounds point(double x, double y, double z) {
            return new Bounds(x, y, z, x, y, z);
        }

        static Bounds block(BlockPos position) {
            return new Bounds(Vec3iAccessor.x(position), Vec3iAccessor.y(position), Vec3iAccessor.z(position),
                (double) Vec3iAccessor.x(position) + 1, (double) Vec3iAccessor.y(position) + 1,
                (double) Vec3iAccessor.z(position) + 1);
        }

        void include(Bounds other) {
            minX = Math.min(minX, other.minX);
            minY = Math.min(minY, other.minY);
            minZ = Math.min(minZ, other.minZ);
            maxX = Math.max(maxX, other.maxX);
            maxY = Math.max(maxY, other.maxY);
            maxZ = Math.max(maxZ, other.maxZ);
        }

        private void move(Vec3d offset, double from, double to) {
            minX += Math.min(offset.x * from, offset.x * to);
            minY += Math.min(offset.y * from, offset.y * to);
            minZ += Math.min(offset.z * from, offset.z * to);
            maxX += Math.max(offset.x * from, offset.x * to);
            maxY += Math.max(offset.y * from, offset.y * to);
            maxZ += Math.max(offset.z * from, offset.z * to);
            checkFinite();
        }

        private void rotate(Vec3d pivot, AxisSweep[] axes) {
            minX -= pivot.x;
            minY -= pivot.y;
            minZ -= pivot.z;
            maxX -= pivot.x;
            maxY -= pivot.y;
            maxZ -= pivot.z;
            checkFinite();
            rotateAxis(0, axes[0]);
            rotateAxis(1, axes[1]);
            rotateAxis(2, axes[2]);
            minX += pivot.x;
            minY += pivot.y;
            minZ += pivot.z;
            maxX += pivot.x;
            maxY += pivot.y;
            maxZ += pivot.z;
            checkFinite();
        }

        private void rotateAxis(int axis, AxisSweep sweep) {
            if (sweep.identity) {
                return;
            }
            double lowA = axis == 0 ? minY : minX;
            double highA = axis == 0 ? maxY : maxX;
            double lowB = axis == 2 ? minY : minZ;
            double highB = axis == 2 ? maxY : maxZ;
            Range a = new Range();
            Range b = new Range();
            for (int i = 0; i <= 1; i++) {
                for (int j = 0; j <= 1; j++) {
                    double valueA = i == 0 ? lowA : highA;
                    double valueB = j == 0 ? lowB : highB;
                    a.includeSinusoid(valueA, axis == 1 ? valueB : -valueB, sweep);
                    b.includeSinusoid(valueB, axis == 1 ? -valueA : valueA, sweep);
                }
            }
            if (axis == 0) {
                minY = a.min;
                maxY = a.max;
                minZ = b.min;
                maxZ = b.max;
            } else if (axis == 1) {
                minX = a.min;
                maxX = a.max;
                minZ = b.min;
                maxZ = b.max;
            } else {
                minX = a.min;
                maxX = a.max;
                minY = b.min;
                maxY = b.max;
            }
        }

        void includeFade(EnumFacing direction, double distance) {
            minX += Math.min(0.0D, direction.getXOffset() * distance);
            minY += Math.min(0.0D, direction.getYOffset() * distance);
            minZ += Math.min(0.0D, direction.getZOffset() * distance);
            maxX += Math.max(0.0D, direction.getXOffset() * distance);
            maxY += Math.max(0.0D, direction.getYOffset() * distance);
            maxZ += Math.max(0.0D, direction.getZOffset() * distance);
            checkFinite();
        }

        private void checkFinite() {
            requireFinite(minX);
            requireFinite(minY);
            requireFinite(minZ);
            requireFinite(maxX);
            requireFinite(maxY);
            requireFinite(maxZ);
        }
    }

    private static final class Range {
        private double min = Double.POSITIVE_INFINITY;
        private double max = Double.NEGATIVE_INFINITY;

        private void include(double value) {
            requireFinite(value);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        private void includeSinusoid(double cosineFactor, double sineFactor, AxisSweep sweep) {
            if (sweep.fullTurn) {
                double radius = Math.hypot(cosineFactor, sineFactor);
                include(-radius);
                include(radius);
                return;
            }
            include(cosineFactor * sweep.cosineFrom + sineFactor * sweep.sineFrom);
            if (sweep.constant) {
                return;
            }
            include(cosineFactor * sweep.cosineTo + sineFactor * sweep.sineTo);
            double radius = Math.hypot(cosineFactor, sineFactor);
            double phase = Math.atan2(sineFactor, cosineFactor);
            if (containsPhase(sweep.phaseFrom, sweep.phaseTo, phase)) {
                include(radius);
            }
            if (containsPhase(sweep.phaseFrom, sweep.phaseTo, phase + Math.PI)) {
                include(-radius);
            }
        }

        private static boolean containsPhase(double from, double to, double phase) {
            double fullTurn = Math.PI * 2.0D;
            return Math.ceil((from - phase) / fullTurn) <= Math.floor((to - phase) / fullTurn);
        }
    }

    private static final class AxisSweep {
        private final boolean identity;
        private final boolean constant;
        private final boolean fullTurn;
        private final double sineFrom;
        private final double cosineFrom;
        private final double sineTo;
        private final double cosineTo;
        private final double phaseFrom;
        private final double phaseTo;

        private AxisSweep(double first, double last) {
            requireFinite(first);
            requireFinite(last);
            identity = first == 0.0D && last == 0.0D;
            constant = first == last;
            double from = Math.min(first, last);
            double to = Math.max(first, last);
            double radians = Math.toRadians(to) - Math.toRadians(from);
            fullTurn = to - from >= FULL_TURN || radians >= Math.PI * 2.0D;
            sineFrom = fullTurn ? 0.0D : sine(from);
            cosineFrom = fullTurn ? 0.0D : cosine(from);
            sineTo = fullTurn ? 0.0D : sine(to);
            cosineTo = fullTurn ? 0.0D : cosine(to);
            // Retain the span before reducing its phase; use the same radian
            // conversion as the runtime even for very large turn counts.
            phaseFrom = fullTurn ? 0.0D : Math.atan2(sineFrom, cosineFrom);
            phaseTo = phaseFrom + radians;
        }

        private static double sine(double degrees) {
            if (Math.abs(degrees) <= FULL_TURN) {
                if (degrees == 0.0D || Math.abs(degrees) == 180.0D || Math.abs(degrees) == FULL_TURN) {
                    return 0.0D;
                }
                if (degrees == 90.0D || degrees == -270.0D) {
                    return 1.0D;
                }
                if (degrees == -90.0D || degrees == 270.0D) {
                    return -1.0D;
                }
            }
            return Math.sin(Math.toRadians(degrees));
        }

        private static double cosine(double degrees) {
            if (Math.abs(degrees) <= FULL_TURN) {
                if (degrees == 0.0D || Math.abs(degrees) == FULL_TURN) {
                    return 1.0D;
                }
                if (Math.abs(degrees) == 180.0D) {
                    return -1.0D;
                }
                if (Math.abs(degrees) == 90.0D || Math.abs(degrees) == 270.0D) {
                    return 0.0D;
                }
            }
            return Math.cos(Math.toRadians(degrees));
        }
    }
}
