package net.createmod.ponder.compat.ftbquests;

/** Geometry in scaled GUI pixels; the footer never covers quest content. */
final class QuestFooterLayout {

    static final int FOOTER_HEIGHT = 26;
    final int panelY;
    final int panelHeight;
    final int contentHeight;
    final int x;
    final int y;
    final int width;
    final int height;

    private QuestFooterLayout(int panelY, int panelHeight, int contentHeight, int x, int y, int width, int height) {
        this.panelY = panelY;
        this.panelHeight = panelHeight;
        this.contentHeight = contentHeight;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    static QuestFooterLayout create(int panelX, int panelY, int panelWidth, int panelHeight,
        int contentY, int screenWidth, int screenHeight) {
        if (panelY < 0 || panelHeight <= 0 || (long) panelY + panelHeight > screenHeight || contentY < 0) {
            return null;
        }
        // Native short quests shrink to their contents even on an otherwise empty screen.
        int extraHeight = Math.min(FOOTER_HEIGHT, Math.max(0, screenHeight - 10 - panelHeight));
        if (extraHeight > 0) {
            panelHeight += extraHeight;
            panelY = Math.max(5, Math.min(screenHeight - 5 - panelHeight, panelY - extraHeight / 2));
        }
        int contentHeight = panelHeight - contentY - 1 - FOOTER_HEIGHT;
        if (panelWidth < 80 || contentHeight < 20 || screenWidth < 80 || screenHeight < 64) {
            return null;
        }
        int left = Math.max(2, panelX + 4);
        int right = Math.min(screenWidth - 2, panelX + panelWidth - 4);
        int top = panelY + panelHeight - FOOTER_HEIGHT + 2;
        if (right - left < 72 || top < 0 || top + 20 > screenHeight) {
            return null;
        }
        return new QuestFooterLayout(panelY, panelHeight, contentHeight, left, top, right - left, 20);
    }

    boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
