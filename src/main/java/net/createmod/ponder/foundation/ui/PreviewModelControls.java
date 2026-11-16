package net.createmod.ponder.foundation.ui;

/** These controls occupy the existing footer, never the preview or caption bands. */
final class PreviewModelControls {

    static final int WIDTH = 80;
    static final int HEIGHT = 16;
    final PreviewDebugLayout.Rect structure;
    final PreviewDebugLayout.Rect appearance;

    PreviewModelControls(int x, int y) {
        this(x, y, HEIGHT);
    }

    PreviewModelControls(int x, int y, int height) {
        structure = new PreviewDebugLayout.Rect(x, y, WIDTH / 2, Math.max(HEIGHT, height));
        appearance = new PreviewDebugLayout.Rect(x + WIDTH / 2, y, WIDTH / 2, Math.max(HEIGHT, height));
    }

    boolean contains(int x, int y) {
        return structure.contains(x, y) || appearance.contains(x, y);
    }
}
