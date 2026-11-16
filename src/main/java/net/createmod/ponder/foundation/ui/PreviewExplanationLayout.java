package net.createmod.ponder.foundation.ui;

/** Content-independent regions for the model and its explanations. */
final class PreviewExplanationLayout {

    private static final int SHOWCASE_MARGIN = 18;
    private static final int SHOWCASE_CONTENT_TOP = 76;
    private static final int SHOWCASE_BOTTOM_RESERVED = 90;
    private static final int REGION_GAP = 8;
    private static final int MIN_EXPLANATION_WIDTH = 124;
    private static final int MAX_EXPLANATION_WIDTH = 240;
    private static final int MIN_EXPLANATION_HEIGHT = 36;
    private static final int MAX_EXPLANATION_HEIGHT = 96;

    final PreviewDebugLayout.Rect model;
    final PreviewDebugLayout.Rect explanation;

    private PreviewExplanationLayout(PreviewDebugLayout.Rect model, PreviewDebugLayout.Rect explanation) {
        this.model = model;
        this.explanation = explanation;
    }

    static PreviewExplanationLayout showcase(int screenWidth, int screenHeight) {
        return showcase(screenWidth, screenHeight, 9);
    }

    static PreviewExplanationLayout showcase(int screenWidth, int screenHeight, int fontHeight) {
        int width = Math.max(0, screenWidth);
        int height = Math.max(0, screenHeight);
        int left = Math.min(SHOWCASE_MARGIN, width / 2);
        int availableWidth = width - left * 2;
        boolean compact = isCompact(width, height);
        int safeFontHeight = Math.max(1, Math.min(256, fontHeight));
        PreviewShowcaseChromeLayout chrome = PreviewShowcaseChromeLayout.layout(width, height, safeFontHeight);
        int headerBottom = compact ? 16 + 2 * Math.max(20, safeFontHeight + 2)
            : Math.max(SHOWCASE_CONTENT_TOP, 40 + 2 * Math.max(12, safeFontHeight + 2));
        int top = Math.min(headerBottom, chrome.contentBottom);
        int reserve = compact ? Math.max(80, 62 + safeFontHeight)
            : Math.max(SHOWCASE_BOTTOM_RESERVED, 76 + safeFontHeight);
        int bottom = Math.min(chrome.contentBottom, Math.max(top, height - reserve));
        int preferredExplanationWidth = Math.max(MIN_EXPLANATION_WIDTH,
            Math.min(MAX_EXPLANATION_WIDTH, availableWidth / 3));
        int explanationWidth = Math.min(preferredExplanationWidth, availableWidth);
        int gap = compact ? Math.min(REGION_GAP, Math.max(0, availableWidth - explanationWidth)) : 0;
        int modelWidth = compact ? Math.max(0, availableWidth - explanationWidth - gap) : availableWidth;
        PreviewDebugLayout.Rect model = new PreviewDebugLayout.Rect(left, top, modelWidth, bottom - top);
        PreviewDebugLayout.Rect explanation = new PreviewDebugLayout.Rect(left + availableWidth - explanationWidth, top,
            explanationWidth, bottom - top);
        return new PreviewExplanationLayout(model, explanation);
    }

    static boolean isCompact(int width, int height) {
        return width <= 480 || height <= 300;
    }

    static int playbackY(int width, int height) {
        return playbackY(width, height, 9);
    }

    static int playbackY(int width, int height, int fontHeight) {
        return Math.max(0, PreviewShowcaseChromeLayout.layout(width, height, fontHeight).playbackY);
    }

    static int toolbarY(int width, int height) {
        return toolbarY(width, height, 9);
    }

    static int toolbarY(int width, int height, int fontHeight) {
        return Math.max(0, PreviewShowcaseChromeLayout.layout(width, height, fontHeight).toolbarY);
    }

    PreviewExplanationLayout withExplanation(PreviewDebugLayout.Rect explanation) {
        return new PreviewExplanationLayout(model, explanation);
    }

    static PreviewExplanationLayout embedded(PreviewDebugLayout.Rect viewport) {
        return new PreviewExplanationLayout(viewport, viewport);
    }

    static PreviewExplanationLayout debug(PreviewDebugLayout.Rect innerViewport) {
        int height = innerViewport.height;
        int gap = Math.min(REGION_GAP, Math.max(0, height - MIN_EXPLANATION_HEIGHT - 1));
        int preferredExplanationHeight = Math.max(MIN_EXPLANATION_HEIGHT,
            Math.min(MAX_EXPLANATION_HEIGHT, height / 3));
        int explanationHeight = Math.min(preferredExplanationHeight, Math.max(0, height - gap - 1));
        int modelHeight = height - gap - explanationHeight;
        PreviewDebugLayout.Rect model = new PreviewDebugLayout.Rect(innerViewport.x, innerViewport.y,
            innerViewport.width, modelHeight);
        PreviewDebugLayout.Rect explanation = new PreviewDebugLayout.Rect(innerViewport.x, model.bottom() + gap,
            innerViewport.width, explanationHeight);
        return new PreviewExplanationLayout(model, explanation);
    }
}
