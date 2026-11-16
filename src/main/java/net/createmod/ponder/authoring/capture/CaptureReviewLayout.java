package net.createmod.ponder.authoring.capture;

/** Fixed geometry for an unscaled, paged review, including large Unicode font configurations. */
public final class CaptureReviewLayout {
    public final int margin = 12, titleY = 8, menuY, tabsY, bodyY, bodyBottom, pagerY, buttonsY, buttonHeight, lineHeight, rows;
    public final int bodyWidth;
    public final boolean readable;
    public CaptureReviewLayout(int width, int height, int fontHeight) {
        if (width < 1 || height < 1 || fontHeight < 1 || fontHeight > 256) throw new IllegalArgumentException("Invalid review dimensions/font");
        buttonHeight = Math.max(20, fontHeight + 6); lineHeight = fontHeight + 2;
        menuY = titleY + fontHeight + 4; tabsY = menuY + fontHeight + 4;
        bodyY = tabsY + buttonHeight + 6; buttonsY = height - buttonHeight - 4;
        pagerY = buttonsY - buttonHeight - 4; bodyBottom = pagerY - 6;
        bodyWidth = Math.max(1, width - margin * 2);
        rows = Math.max(0, (bodyBottom - bodyY) / lineHeight);
        readable = width >= 160 && rows > 0;
    }
    public int pageCount(int lines) { return Math.max(1, (Math.max(0, lines) + Math.max(1, rows) - 1) / Math.max(1, rows)); }
    public int firstLine(int page, int lines) { return Math.max(0, Math.min(page, pageCount(lines) - 1)) * Math.max(1, rows); }
    public int endLine(int page, int lines) { return Math.min(lines, firstLine(page, lines) + Math.max(1, rows)); }
}
