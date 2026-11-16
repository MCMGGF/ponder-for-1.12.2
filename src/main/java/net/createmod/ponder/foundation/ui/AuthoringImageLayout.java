package net.createmod.ponder.foundation.ui;

/** Fixed thumbnail tracks keep images, labels and input controls stable at compact logical sizes. */
public final class AuthoringImageLayout {
    public final int columns, rows, slots, left, top, cellWidth, cellHeight;
    public AuthoringImageLayout(int width, int height) {
        int inner = Math.max(64, width - 24); columns = Math.max(1, Math.min(5, inner / 112));
        cellWidth = inner / columns; cellHeight = 86; top = 64;
        rows = Math.max(1, Math.min(Math.min(4, 16 / columns), (height - 118) / cellHeight)); slots = rows * columns;
        left = (width - cellWidth * columns) / 2;
    }
    public int x(int slot) { return left + slot % columns * cellWidth; }
    public int y(int slot) { return top + slot / columns * cellHeight; }
    public int pageCount(int items) { return Math.max(1, (items + slots - 1) / slots); }
    public int clampPage(int page, int items) { return Math.max(0, Math.min(page, pageCount(items) - 1)); }
}
