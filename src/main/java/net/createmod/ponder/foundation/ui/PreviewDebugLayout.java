package net.createmod.ponder.foundation.ui;

/** Shared rectangles for debug drawing, scrolling and hit testing. */
final class PreviewDebugLayout {

    static final int LINE_HEIGHT = 12;
    final int screenWidth;
    final int screenHeight;
    final boolean compact;
    final Rect components;
    final Rect componentRows;
    final Rect content;
    final Rect preview;
    final Rect summaryRows;
    final Rect operationRows;
    final Rect[] tabs = new Rect[3];
    final Rect[] toolbar = new Rect[7];

    PreviewDebugLayout(int width, int height) {
        screenWidth = width;
        screenHeight = height;
        compact = width < 720 || height < 420;
        int margin = 12;
        int gap = 8;
        int leftWidth = Math.min(170, Math.max(90, width / 4));
        int bottom = height - 40;
        components = new Rect(margin, 24, leftWidth, Math.max(1, bottom - 24));
        componentRows = rows(margin + 4, 44, leftWidth - 8, bottom - 4);
        int right = components.right() + gap;
        content = new Rect(right, 24, Math.max(1, width - margin - right), Math.max(1, bottom - 24));
        for (int i = 0; i < tabs.length; i++) {
            int x = right + i * content.width / tabs.length;
            int end = right + (i + 1) * content.width / tabs.length;
            tabs[i] = new Rect(x, 27, Math.max(1, end - x - 2), 18);
        }
        if (compact) {
            preview = new Rect(right + 2, 50, content.width - 4, Math.max(40, bottom - 52));
            summaryRows = rows(right + 6, 54, content.width - 12, bottom - 4);
            operationRows = summaryRows;
        } else {
            int previewWidth = Math.min(360, content.width / 2);
            int topHeight = Math.min(220, Math.max(180, (bottom - 44) * 3 / 5));
            preview = new Rect(content.right() - previewWidth - 4, 42, previewWidth, topHeight);
            summaryRows = rows(right + 6, 44, preview.x - right - 14, preview.bottom() - 4);
            operationRows = rows(right + 6, preview.bottom() + 24, content.width - 12, bottom - 4);
        }
        for (int i = 0; i < toolbar.length; i++) {
            int x = margin + i * (width - margin * 2) / toolbar.length;
            int end = margin + (i + 1) * (width - margin * 2) / toolbar.length;
            toolbar[i] = new Rect(x, height - 30, Math.max(1, end - x - 4), 20);
        }
    }

    private static Rect rows(int x, int y, int width, int bottom) {
        return new Rect(x, y, Math.max(1, width), Math.max(0, (bottom - y) / LINE_HEIGHT) * LINE_HEIGHT);
    }

    static final class Rect {
        final int x;
        final int y;
        final int width;
        final int height;

        Rect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
        }

        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        int lines() {
            return height / LINE_HEIGHT;
        }

        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }
    }
}
