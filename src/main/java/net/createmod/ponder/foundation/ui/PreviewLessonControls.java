package net.createmod.ponder.foundation.ui;

import java.util.List;

/** Stable toolbar geometry and semantic step navigation, independent of labels. */
final class PreviewLessonControls {
    static final int COUNT = 11;

    static PreviewDebugLayout.Rect[] layout(int width, int height) {
        return layout(width, height, 9);
    }

    static PreviewDebugLayout.Rect[] layout(int width, int height, int fontHeight) {
        return layout(width, height, fontHeight, false);
    }

    static PreviewDebugLayout.Rect[] layout(int width, int height, int fontHeight, boolean practice) {
        PreviewShowcaseChromeLayout chrome = PreviewShowcaseChromeLayout.layout(width, height, fontHeight);
        int gap = 3;
        int count = COUNT + (practice ? 1 : 0);
        int total = (count - 3) * 20 + 3 * 28 + (count - 1) * gap;
        int x = Math.max(0, (width - total) / 2);
        PreviewDebugLayout.Rect[] result = new PreviewDebugLayout.Rect[count];
        for (int i = 0; i < count; i++) {
            int buttonWidth = i >= 7 && i <= 9 ? 28 : 20;
            result[i] = new PreviewDebugLayout.Rect(x, chrome.toolbarY, buttonWidth, chrome.toolbarHeight);
            x += buttonWidth + gap;
        }
        return result;
    }

    static int step(List<Integer> ticks, int current, int end, int direction) {
        if (direction < 0) {
            for (int i = ticks.size() - 1; i >= 0; i--) {
                int tick = ticks.get(i);
                if (tick < current) return Math.max(0, tick);
            }
            return 0;
        }
        for (int tick : ticks) {
            if (tick > current) return Math.min(end, tick);
        }
        return end;
    }
}
