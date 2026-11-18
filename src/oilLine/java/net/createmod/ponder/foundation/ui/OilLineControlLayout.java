package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fixed tool bands; text and action pagination never resize their neighbouring controls. */
public final class OilLineControlLayout {
    public final int width, height, fontHeight, lineHeight, controlHeight;
    public final boolean compact;
    public final Rect title, marker, body, stats, goals, actions, feedback;
    public final Rect[] stations, views, transport;
    private static final int GAP = 3;

    public OilLineControlLayout(int width, int height, int fontHeight) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.fontHeight = Math.max(1, Math.min(32, fontHeight));
        lineHeight = this.fontHeight + 3;
        controlHeight = Math.max(20, this.fontHeight + 8);
        int inner = Math.max(0, this.width - 16);
        transport = weighted(new Rect(8, Math.max(0, this.height - 8 - controlHeight), inner, controlHeight),
            new int[] {1, 1, 1, 1, 1, 2});
        int feedbackHeight = Math.max(20, this.fontHeight + 2);
        int viewHeight = Math.max(20, this.fontHeight + 4);
        boolean minimal = this.height < 3 * controlHeight + viewHeight + feedbackHeight + this.fontHeight + 32;
        title = new Rect(8, 6, inner, minimal ? 0 : this.fontHeight + 2);
        // Extremely large fonts keep actual controls; the current feedback remains in station tooltips.
        if (minimal) feedbackHeight = 0;
        feedback = new Rect(8, Math.max(0, transport[0].y - GAP - feedbackHeight), inner, feedbackHeight);
        int minimumBody = viewHeight + 2 * controlHeight + 2 * GAP;
        int normalBodyTop = title.bottom() + GAP + this.fontHeight + 2 + GAP + controlHeight + GAP;
        marker = feedback.y - GAP - normalBodyTop >= minimumBody
            ? new Rect(8, title.bottom() + GAP, inner, this.fontHeight + 2)
            : new Rect(8, title.bottom(), inner, 0);
        int stationWidth = Math.min(700, inner);
        stations = columns(new Rect((this.width - stationWidth) / 2, marker.bottom() + GAP,
            stationWidth, controlHeight), 5);
        body = new Rect(8, stations[0].bottom() + GAP, inner,
            Math.max(0, feedback.y - GAP - stations[0].bottom() - GAP));
        compact = this.width < 600 || body.height < 2 * (lineHeight + controlHeight + this.fontHeight + 2 * GAP);
        if (compact) {
            views = columns(new Rect(body.x, body.y, body.width, Math.min(viewHeight, body.height)), 3);
            Rect content = new Rect(body.x, views[0].bottom() + GAP, body.width,
                Math.max(0, body.bottom() - views[0].bottom() - GAP));
            stats = content;
            goals = content;
            actions = content;
        } else {
            views = new Rect[0];
            int leftWidth = Math.max(0, (body.width - 2 * GAP) / 2);
            int firstHeight = Math.max(0, (body.height - GAP) / 2);
            stats = new Rect(body.x, body.y, leftWidth, firstHeight);
            goals = new Rect(body.x, stats.bottom() + GAP, leftWidth, body.height - firstHeight - GAP);
            actions = new Rect(body.x + leftWidth + 2 * GAP, body.y,
                body.width - leftWidth - 2 * GAP, body.height);
        }
    }

    public Rect textArea(Rect panel) {
        int top = panel.y + (compact ? 0 : lineHeight);
        return new Rect(panel.x + 2, top, Math.max(0, panel.width - 4),
            Math.max(0, panel.bottom() - controlHeight - GAP - top));
    }

    public int textRows(Rect panel) { return Math.max(1, textArea(panel).height / lineHeight); }

    public Rect[] pager(Rect panel, int count) {
        return columns(new Rect(panel.x, Math.max(panel.y, panel.bottom() - controlHeight),
            panel.width, Math.min(controlHeight, panel.height)), count);
    }

    public Rect feedbackText() {
        if (feedback.height == 0) return new Rect(feedback.x, feedback.y, 0, 0);
        int reserved = Math.min(feedback.width, 2 * controlHeight + 2 * GAP);
        return new Rect(feedback.x + 2, feedback.y + (feedback.height - fontHeight) / 2,
            Math.max(0, feedback.width - reserved - 4), fontHeight + 1);
    }

    public Rect[] feedbackPager() {
        if (feedback.height == 0) return new Rect[0];
        int pagerWidth = Math.min(feedback.width, 2 * controlHeight + GAP);
        return columns(new Rect(feedback.right() - pagerWidth, feedback.y, pagerWidth, feedback.height), 2);
    }

    public int actionColumns() { return actions.width >= 270 ? 2 : 1; }

    public int actionsPerPage() {
        Rect content = textArea(actions);
        return Math.max(1, (content.height + GAP) / (controlHeight + GAP)) * actionColumns();
    }

    public List<Rect> actionCells(int count, int page) {
        if (count < 0) throw new IllegalArgumentException("Negative action count");
        int size = actionsPerPage();
        int start = pageStart(page, count, size);
        int visible = Math.min(size, Math.max(0, count - start));
        Rect area = textArea(actions);
        int columns = actionColumns();
        int cellWidth = Math.max(0, (area.width - (columns - 1) * GAP) / columns);
        List<Rect> cells = new ArrayList<>();
        for (int index = 0; index < visible; index++) {
            cells.add(new Rect(area.x + (index % columns) * (cellWidth + GAP),
                area.y + (index / columns) * (controlHeight + GAP), cellWidth,
                Math.min(controlHeight, Math.max(0, area.bottom() - area.y - (index / columns) * (controlHeight + GAP)))));
        }
        return Collections.unmodifiableList(cells);
    }

    public static int pageCount(int count, int pageSize) {
        return Math.max(1, (Math.max(0, count) + Math.max(1, pageSize) - 1) / Math.max(1, pageSize));
    }

    public static int pageStart(int page, int count, int pageSize) {
        int bounded = Math.max(0, Math.min(page, pageCount(count, pageSize) - 1));
        return bounded * Math.max(1, pageSize);
    }

    private static Rect[] columns(Rect band, int count) {
        int[] weights = new int[count];
        java.util.Arrays.fill(weights, 1);
        return weighted(band, weights);
    }

    private static Rect[] weighted(Rect band, int[] weights) {
        int total = 0;
        for (int weight : weights) total += weight;
        int available = Math.max(0, band.width - GAP * (weights.length - 1));
        Rect[] cells = new Rect[weights.length];
        int consumed = 0;
        int x = band.x;
        for (int i = 0; i < weights.length; i++) {
            int previous = available * consumed / Math.max(1, total);
            consumed += weights[i];
            int cellWidth = available * consumed / Math.max(1, total) - previous;
            cells[i] = new Rect(x, band.y, cellWidth, band.height);
            x += cellWidth + GAP;
        }
        return cells;
    }

    public static final class Rect {
        public final int x, y, width, height;
        public Rect(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = Math.max(0, width); this.height = Math.max(0, height);
        }
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(int px, int py) { return px >= x && py >= y && px < right() && py < bottom(); }
        public boolean intersects(Rect other) {
            return width > 0 && height > 0 && other.width > 0 && other.height > 0
                && x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }
    }
}
