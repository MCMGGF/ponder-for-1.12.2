package net.createmod.ponder.foundation.ui;

/** Pagination of already wrapped caption lines; no line is discarded. */
final class PreviewCaptionPaging {

    static final int LINE_HEIGHT = 10;
    static final int PADDING = 12;
    static final int FOOTER_HEIGHT = 14;
    static final int MAX_HEIGHT = 156;
    private Object caption;
    private int lineCount;
    private int pageSize = 1;
    private int page;
    private boolean paged;
    private int textPages;
    private int imagePages;
    private int lineHeight = LINE_HEIGHT;
    private int padding = PADDING;

    static int bottom(int previewY, int previewHeight, int playbackBarY) {
        return Math.min(previewY + previewHeight - 12, playbackBarY - 18);
    }

    static int top(int previewY, int bottom) {
        return Math.max(previewY + 4, Math.min(previewY + 74, bottom - 36));
    }

    void configure(Object caption, int lineCount, int availableHeight) {
        configure(caption, lineCount, Math.min(MAX_HEIGHT, availableHeight), 0, LINE_HEIGHT);
    }

    void configure(Object caption, int lineCount, int availableHeight, int imagePages, int lineHeight) {
        availableHeight = Math.max(0, availableHeight);
        int firstLine = this.caption == caption ? firstLine() : 0;
        int previousImage = this.caption == caption ? imageIndex() : -1;
        this.caption = caption;
        this.lineCount = Math.max(0, lineCount);
        this.imagePages = Math.max(0, imagePages);
        this.lineHeight = Math.min(Math.max(1, lineHeight), Math.max(1, availableHeight - FOOTER_HEIGHT - 6));
        int linesWithoutFooter = Math.max(1, (availableHeight - PADDING) / this.lineHeight);
        paged = lineCount > linesWithoutFooter || this.imagePages > (lineCount > 0 ? 0 : 1);
        padding = Math.min(PADDING, Math.max(0, availableHeight - this.lineHeight - (paged ? FOOTER_HEIGHT : 0)));
        pageSize = paged ? Math.max(1, (availableHeight - padding - FOOTER_HEIGHT) / this.lineHeight)
            : Math.max(1, lineCount);
        textPages = (this.lineCount + pageSize - 1) / pageSize;
        page = Math.min(pageCount() - 1,
            previousImage >= 0 ? textPages + previousImage : firstLine / pageSize);
    }

    boolean turn(int amount) {
        int next = Math.max(0, Math.min(pageCount() - 1, page + amount));
        boolean changed = next != page;
        page = next;
        return changed;
    }

    int firstLine() {
        return Math.min(lineCount, page * pageSize);
    }

    int endLine() {
        return Math.min(lineCount, firstLine() + pageSize);
    }

    int page() {
        return page;
    }

    int pageCount() {
        return Math.max(1, textPages + imagePages);
    }

    int height() {
        return padding + Math.min(lineCount, pageSize) * lineHeight + (paged ? FOOTER_HEIGHT : 0);
    }

    int imageIndex() {
        return page >= textPages && imagePages > 0 ? page - textPages : -1;
    }

    int lineHeight() {
        return lineHeight;
    }

    boolean isPaged() {
        return paged;
    }

    void clear() {
        caption = null;
        page = 0;
        lineCount = 0;
        textPages = 0;
        imagePages = 0;
        paged = false;
    }
}
