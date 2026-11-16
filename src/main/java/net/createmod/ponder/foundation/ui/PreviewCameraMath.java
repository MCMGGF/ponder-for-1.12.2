package net.createmod.ponder.foundation.ui;

final class PreviewCameraMath {

    static final float DEFAULT_PITCH = 26.0F;
    static final float MAX_PITCH = 180.0F;

    private PreviewCameraMath() {
    }

    static float fitScale(double sizeX, double sizeY, double sizeZ, int width, int height,
        boolean showcase, float authoredScale) {
        double horizontalDiameter = Math.hypot(Math.max(1.0D, sizeX), Math.max(1.0D, sizeZ));
        double verticalSize = Math.max(1.0D, sizeY);
        double pitch = Math.atan2(horizontalDiameter, verticalSize);
        double verticalDiameter = verticalSize * Math.cos(pitch)
            + horizontalDiameter * Math.sin(pitch);
        double availableWidth = Math.max(1, width - (showcase ? 24 : 12));
        double availableHeight = Math.max(1, height - 12);
        double fit = Math.min(availableWidth / horizontalDiameter, availableHeight / verticalDiameter);
        // Fit the full yaw and pitch envelope, including authored upside-down views, never the current frame.
        // Values above 1 would violate the initial fit, and very small legacy hints
        // shrink an already fitted preview unnecessarily.
        double hint = Math.max(showcase ? 0.9D : 0.55D, Math.min(1.0D,
            Float.isFinite(authoredScale) ? authoredScale : 1.0D));
        return (float) Math.max(0.0001D, fit * hint);
    }

    static int topClearance(boolean showcase) {
        return 0;
    }

    static int bottomClearance(boolean showcase) {
        return 0;
    }

    static float anchorY(int originY, int height, boolean showcase) {
        int top = topClearance(showcase);
        int bottom = bottomClearance(showcase);
        return originY + top + Math.max(1, height - top - bottom) * 0.5F;
    }

    static boolean usableModelBounds(double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ, double structureCenterX,
        double structureCenterY, double structureCenterZ) {
        return usableModelAxis(minX, maxX, structureCenterX)
            && usableModelAxis(minY, maxY, structureCenterY)
            && usableModelAxis(minZ, maxZ, structureCenterZ);
    }

    private static boolean usableModelAxis(double min, double max, double structureCenter) {
        return Double.isFinite(min) && Double.isFinite(max)
            && min <= max && max - min <= 4096.0D
            && Math.abs(min - structureCenter) <= 4096.0D
            && Math.abs(max - structureCenter) <= 4096.0D;
    }
}
