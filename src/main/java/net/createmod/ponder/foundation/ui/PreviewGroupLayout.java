package net.createmod.ponder.foundation.ui;

/** Page geometry shared by the group popup's drawing and input. */
final class PreviewGroupLayout {

    static final int ICON_SIZE = 20;
    private static final int GAP = 4;
    private static final int HEADER_HEIGHT = 22;
    private static final int BOTTOM_PADDING = 8;
    private static final int FOOTER_HEIGHT = 16;
    private int count;
    private int columns = 1;
    private int pageSize = 1;
    private int page;
    PreviewDebugLayout.Rect bounds;
    PreviewDebugLayout.Rect previous;
    PreviewDebugLayout.Rect next;

    boolean configure(int count, int x, int y, int maxWidth, int bottom) {
        int first = firstIndex();
        invalidate();
        this.count = Math.max(0, count);
        columns = Math.min(Math.max(1, count), Math.min(6, Math.max(1, (maxWidth - 12) / (ICON_SIZE + GAP))));
        int availableHeight = bottom - y;
        int rows = (availableHeight - HEADER_HEIGHT - BOTTOM_PADDING + GAP) / (ICON_SIZE + GAP);
        boolean paged = count > rows * columns;
        if (paged) {
            rows = (availableHeight - HEADER_HEIGHT - BOTTOM_PADDING - FOOTER_HEIGHT + GAP) / (ICON_SIZE + GAP);
        }
        if (count == 0 || rows < 1 || maxWidth < ICON_SIZE + 16) {
            return false;
        }
        rows = Math.min(rows, (count + columns - 1) / columns);
        pageSize = rows * columns;
        page = Math.min(pageCount() - 1, first / pageSize);
        int width = 16 + columns * ICON_SIZE + (columns - 1) * GAP;
        int height = HEADER_HEIGHT + rows * ICON_SIZE + (rows - 1) * GAP + BOTTOM_PADDING
            + (paged ? FOOTER_HEIGHT : 0);
        bounds = new PreviewDebugLayout.Rect(x, y, width, height);
        if (paged) {
            int footerY = bounds.bottom() - BOTTOM_PADDING - FOOTER_HEIGHT;
            previous = new PreviewDebugLayout.Rect(x + 3, footerY, 22, FOOTER_HEIGHT);
            next = new PreviewDebugLayout.Rect(bounds.right() - 25, footerY, 22, FOOTER_HEIGHT);
        }
        return true;
    }

    PreviewDebugLayout.Rect icon(int index) {
        if (bounds == null || index < firstIndex() || index >= endIndex()) {
            return null;
        }
        int local = index - firstIndex();
        return new PreviewDebugLayout.Rect(bounds.x + 8 + local % columns * (ICON_SIZE + GAP),
            bounds.y + HEADER_HEIGHT + local / columns * (ICON_SIZE + GAP), ICON_SIZE, ICON_SIZE);
    }

    boolean turn(int amount) {
        int nextPage = (int) Math.max(0L, Math.min(pageCount() - 1L, (long) page + amount));
        boolean changed = page != nextPage;
        page = nextPage;
        return changed;
    }

    int firstIndex() {
        return page * pageSize;
    }

    int endIndex() {
        return Math.min(count, firstIndex() + pageSize);
    }

    int page() {
        return page;
    }

    int pageCount() {
        return Math.max(1, (count + pageSize - 1) / pageSize);
    }

    void invalidate() {
        bounds = null;
        previous = null;
        next = null;
    }

    void clear() {
        invalidate();
        count = 0;
        pageSize = 1;
        page = 0;
    }
}
