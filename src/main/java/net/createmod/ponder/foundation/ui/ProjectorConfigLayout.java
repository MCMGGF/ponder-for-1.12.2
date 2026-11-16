package net.createmod.ponder.foundation.ui;

/** One local coordinate system for the projector form and its input. */
final class ProjectorConfigLayout {
    final int scale;
    final int width;
    final int height;
    final int left;
    final int top = 36;
    final int formWidth;

    ProjectorConfigLayout(int screenWidth, int screenHeight, int displayWidth, int displayHeight) {
        if (screenWidth <= 0 || screenHeight <= 0 || displayWidth <= 0 || displayHeight <= 0) {
            throw new IllegalArgumentException("Positive projector screen dimensions are required");
        }
        boolean nativeScaleOne = screenWidth == displayWidth && screenHeight == displayHeight;
        scale = nativeScaleOne && screenWidth >= 640 && screenHeight >= 480 ? 2 : 1;
        width = screenWidth / scale;
        height = screenHeight / scale;
        formWidth = Math.max(1, Math.min(360, width - 24));
        left = (width - formWidth) / 2;
    }

    int localX(int screenX) { return Math.floorDiv(screenX, scale); }
    int localY(int screenY) { return Math.floorDiv(screenY, scale); }

    PreviewDebugLayout.Rect field(int id) {
        if (id == 0) return rect(left, top, formWidth, 18);
        int column = formWidth / 3;
        if (id == 1 || id == 2) return rect(left + (id - 1) * column, top + 38, column - 4, 18);
        if (id == 3) return rect(left + 2 * column, top + 38, formWidth - 2 * column, 18);
        throw new IllegalArgumentException("Unknown projector field");
    }

    PreviewDebugLayout.Rect button(int id) {
        int half = formWidth / 2;
        int third = formWidth / 3;
        switch (id) {
            case 0: return rect(left, top + 64, half - 3, 20);
            case 1: return rect(left + half, top + 64, formWidth - half, 20);
            case 2: return rect(left, top + 88, formWidth, 20);
            case 3: return rect(left, top + 112, third - 3, 20);
            case 4: return rect(left + third, top + 112, third - 3, 20);
            case 7: return rect(left + 2 * third, top + 112, formWidth - 2 * third, 20);
            case 5: return rect(left, top + 140, half - 3, 20);
            case 6: return rect(left + half, top + 140, formWidth - half, 20);
            default: throw new IllegalArgumentException("Unknown projector button");
        }
    }

    private static PreviewDebugLayout.Rect rect(int x, int y, int width, int height) {
        return new PreviewDebugLayout.Rect(x, y, width, height);
    }
}
