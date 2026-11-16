package net.createmod.ponder.foundation.ui;

/** Stable, exclusive visual bounds and camera compensation after a resource reload. */
final class PreviewModelFraming {

    private double minX;
    private double minY;
    private double minZ;
    private double maxX;
    private double maxY;
    private double maxZ;
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private float scaleMultiplier = 1F;
    private boolean initialized;

    void initialize(double x0, double y0, double z0, double x1, double y1, double z1) {
        if (initialized) {
            return;
        }
        minX = x0;
        minY = y0;
        minZ = z0;
        maxX = x1;
        maxY = y1;
        maxZ = z1;
        initialized = true;
    }

    void include(double x0, double y0, double z0, double x1, double y1, double z1,
        boolean initialFit, int width, int height, boolean showcase, float authoredScale) {
        double previousX = centerX();
        double previousY = centerY();
        double previousZ = centerZ();
        float previousScale = fitScale(width, height, showcase, authoredScale);
        minX = Math.min(minX, x0);
        minY = Math.min(minY, y0);
        minZ = Math.min(minZ, z0);
        maxX = Math.max(maxX, x1);
        maxY = Math.max(maxY, y1);
        maxZ = Math.max(maxZ, z1);
        if (initialFit) {
            resetCamera();
        } else {
            offsetX = previousX - (minX + maxX) / 2;
            offsetY = previousY - (minY + maxY) / 2;
            offsetZ = previousZ - (minZ + maxZ) / 2;
            scaleMultiplier = previousScale / baseScale(width, height, showcase, authoredScale);
        }
    }

    double centerX() {
        return (minX + maxX) / 2 + offsetX;
    }

    double centerY() {
        return (minY + maxY) / 2 + offsetY;
    }

    double centerZ() {
        return (minZ + maxZ) / 2 + offsetZ;
    }

    float fitScale(int width, int height, boolean showcase, float authoredScale) {
        return baseScale(width, height, showcase, authoredScale) * scaleMultiplier;
    }

    private float baseScale(int width, int height, boolean showcase, float authoredScale) {
        return PreviewCameraMath.fitScale(maxX - minX, maxY - minY, maxZ - minZ,
            width, height, showcase, authoredScale);
    }

    void resetCamera() {
        offsetX = 0;
        offsetY = 0;
        offsetZ = 0;
        scaleMultiplier = 1F;
    }

    PreviewModelFraming copy() {
        PreviewModelFraming copy = new PreviewModelFraming();
        copy.copyFrom(this);
        return copy;
    }

    void copyFrom(PreviewModelFraming source) {
        minX = source.minX;
        minY = source.minY;
        minZ = source.minZ;
        maxX = source.maxX;
        maxY = source.maxY;
        maxZ = source.maxZ;
        offsetX = source.offsetX;
        offsetY = source.offsetY;
        offsetZ = source.offsetZ;
        scaleMultiplier = source.scaleMultiplier;
        initialized = source.initialized;
    }

    void clear() {
        initialized = false;
        resetCamera();
    }
}
