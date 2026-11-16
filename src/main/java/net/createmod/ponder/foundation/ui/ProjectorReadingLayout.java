package net.createmod.ponder.foundation.ui;

/** Reader-only logical canvas; world geometry keeps its original viewport. */
final class ProjectorReadingLayout {
    final int scale, width, height;
    final int screenWidth, screenHeight, displayWidth, displayHeight;

    ProjectorReadingLayout(int screenWidth, int screenHeight, int displayWidth, int displayHeight) {
        if (screenWidth <= 0 || screenHeight <= 0 || displayWidth <= 0 || displayHeight <= 0)
            throw new IllegalArgumentException("Positive reading screen dimensions are required");
        this.screenWidth = screenWidth; this.screenHeight = screenHeight;
        this.displayWidth = displayWidth; this.displayHeight = displayHeight;
        boolean nativeScaleOne = screenWidth == displayWidth && screenHeight == displayHeight;
        scale = nativeScaleOne && screenWidth >= 640 && screenHeight >= 480 ? 2 : 1;
        width = (int) (((long) screenWidth + scale - 1) / scale);
        height = (int) (((long) screenHeight + scale - 1) / scale);
    }

    boolean matches(int screenWidth, int screenHeight, int displayWidth, int displayHeight) {
        return this.screenWidth == screenWidth && this.screenHeight == screenHeight
            && this.displayWidth == displayWidth && this.displayHeight == displayHeight;
    }

    int localX(int screenX) { return (int) Math.floorDiv((long) screenX * width, screenWidth); }
    int localY(int screenY) { return (int) Math.floorDiv((long) screenY * height, screenHeight); }

    PreviewDebugLayout.Rect content() {
        int left = Math.min(8, width - 1), top = Math.min(24, height - 1);
        int right = Math.max(left + 1, width - 8), bottom = Math.max(top + 1, height - 48);
        return new PreviewDebugLayout.Rect(left, top, right - left, bottom - top);
    }

    int[] framebufferScissor(PreviewDebugLayout.Rect area) {
        int left = clamp(area.x, width), top = clamp(area.y, height);
        int right = Math.max(left, clamp((long) area.x + area.width, width));
        int bottom = Math.max(top, clamp((long) area.y + area.height, height));
        int pixelLeft = (int) ((long) left * displayWidth / width);
        int pixelTop = (int) ((long) top * displayHeight / height);
        int pixelRight = ceil((long) right * displayWidth, width);
        int pixelBottom = ceil((long) bottom * displayHeight, height);
        return new int[] {pixelLeft, displayHeight - pixelBottom,
            pixelRight - pixelLeft, pixelBottom - pixelTop};
    }

    private static int clamp(long value, int maximum) { return (int) Math.max(0, Math.min(maximum, value)); }
    private static int ceil(long value, int divisor) { return (int) ((value + divisor - 1) / divisor); }
}
