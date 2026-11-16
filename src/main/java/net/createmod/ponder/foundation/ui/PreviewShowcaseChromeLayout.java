package net.createmod.ponder.foundation.ui;

/** Shared bottom bands for drawing, hit testing and model framing. */
final class PreviewShowcaseChromeLayout {
    final int fontHeight;
    final int hoverTextY;
    final int footerTextY;
    final int modelControlsY;
    final int modelControlsHeight;
    final int toolbarY;
    final int toolbarHeight;
    final int playbackY;
    final int playbackLabelY;
    final int contentBottom;
    final PreviewDebugLayout.Rect hoverBand;
    final PreviewDebugLayout.Rect footerBand;
    final PreviewDebugLayout.Rect footerTextBand;
    final PreviewDebugLayout.Rect modelFooterTextBand;
    final PreviewDebugLayout.Rect modelControlsBand;
    final PreviewDebugLayout.Rect toolbarBand;
    final PreviewDebugLayout.Rect playbackBand;
    final PreviewDebugLayout.Rect playbackLabelBand;

    static PreviewShowcaseChromeLayout layout(int width, int height) {
        return layout(width, height, 9);
    }

    static PreviewShowcaseChromeLayout layout(int width, int height, int fontHeight) {
        return new PreviewShowcaseChromeLayout(Math.max(0, width), Math.max(0, height),
            Math.max(1, Math.min(256, fontHeight)));
    }

    private PreviewShowcaseChromeLayout(int width, int height, int fontHeight) {
        this.fontHeight = fontHeight;
        modelControlsHeight = Math.max(16, fontHeight + 7);
        toolbarHeight = Math.max(20, Math.max(fontHeight + 8, 2 * fontHeight - 6));
        hoverTextY = height - Math.max(12, fontHeight + 3);
        footerTextY = hoverTextY - Math.max(fontHeight + 3, modelControlsHeight - 4);
        modelControlsY = footerTextY - 4;
        toolbarY = modelControlsY - 4 - toolbarHeight;
        int defaultPlaybackY = height - (PreviewExplanationLayout.isCompact(width, height) ? 58 : 72);
        playbackY = Math.min(defaultPlaybackY, toolbarY - 6);
        playbackLabelY = playbackY - fontHeight - 2;
        contentBottom = Math.max(0, Math.min(height, playbackLabelY - 4));

        hoverBand = clip(18, hoverTextY, width - 35, fontHeight + 1, width, height);
        footerBand = clip(18, modelControlsY, width - 36,
            Math.max(modelControlsHeight, fontHeight + 5), width, height);
        footerTextBand = clip(28, footerTextY, width - 55, fontHeight + 1, width, height);
        modelControlsBand = clip(28, modelControlsY, 80, modelControlsHeight, width, height);
        modelFooterTextBand = clip(120, footerTextY, width - 147, fontHeight + 1, width, height);
        toolbarBand = clip(0, toolbarY, width, toolbarHeight, width, height);
        playbackBand = clip(0, playbackY - 1, width, 5, width, height);
        playbackLabelBand = clip(0, playbackLabelY, width, fontHeight + 1, width, height);
    }

    // Unrepresentably small viewports collapse offscreen bands instead of creating invalid rectangles.
    private static PreviewDebugLayout.Rect clip(int x, int y, int width, int height,
        int screenWidth, int screenHeight) {
        int left = Math.max(0, Math.min(screenWidth, x));
        int top = Math.max(0, Math.min(screenHeight, y));
        int right = Math.max(left, Math.min(screenWidth, x + Math.max(0, width)));
        int bottom = Math.max(top, Math.min(screenHeight, y + Math.max(0, height)));
        return new PreviewDebugLayout.Rect(left, top, right - left, bottom - top);
    }
}
