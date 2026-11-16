package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.createmod.ponder.foundation.ui.PreviewDebugLayout.Rect;

/** Screen-space placement only: this helper never changes model framing. */
final class PreviewCalloutLayout {

    static final int GAP = 8;
    private static final int POSITION_TOLERANCE = 4;

    enum Side { ABOVE, RIGHT, LEFT, BELOW, EDGE }

    static final class Placement {
        final Rect panel;
        final Side side;
        final boolean fallback;

        Placement(Rect panel, Side side, boolean fallback) {
            this.panel = panel;
            this.side = side;
            this.fallback = fallback;
        }
    }

    static Placement place(Rect area, Rect target, int measuredWidth, int measuredHeight,
        List<Rect> obstacles, Placement previous) {
        return place(area, target, measuredWidth, measuredHeight, obstacles, previous, null);
    }

    static Placement place(Rect area, Rect target, int measuredWidth, int measuredHeight,
        List<Rect> obstacles, Placement previous, Rect preferredModelBounds) {
        List<Rect> reserved = obstacles == null ? Collections.emptyList() : obstacles;
        Rect anchor = target;
        if (preferredModelBounds != null && preferredModelBounds.width > 0 && preferredModelBounds.height > 0) {
            reserved = new ArrayList<>(reserved);
            reserved.add(preferredModelBounds);
            anchor = union(target, preferredModelBounds);
        }
        int width = Math.min(area.width, Math.max(0, measuredWidth));
        int height = Math.min(area.height, Math.max(0, measuredHeight));
        if (width == 0 || height == 0) {
            return new Placement(new Rect(area.x, area.y, width, height), Side.EDGE, true);
        }
        if (previous != null && previous.panel.width == width && previous.panel.height == height
            && valid(previous.panel, area, target, reserved)) {
            if (anchor == null || anchor.width <= 0 || anchor.height <= 0) {
                return previous;
            }
            if (previous.side != Side.EDGE) {
                Rect next = candidate(area, anchor, width, height, previous.side);
                if (Math.abs(next.x - previous.panel.x) <= POSITION_TOLERANCE
                    && Math.abs(next.y - previous.panel.y) <= POSITION_TOLERANCE) {
                    return previous;
                }
            }
        }
        if (anchor != null && anchor.width > 0 && anchor.height > 0) {
            if (previous != null && previous.side != Side.EDGE) {
                Rect candidate = candidate(area, anchor, width, height, previous.side);
                if (valid(candidate, area, target, reserved)) {
                    return new Placement(candidate, previous.side, false);
                }
            }
            for (Side side : new Side[] { Side.ABOVE, Side.RIGHT, Side.LEFT, Side.BELOW }) {
                Rect candidate = candidate(area, anchor, width, height, side);
                if (valid(candidate, area, target, reserved)) {
                    return new Placement(candidate, side, false);
                }
            }
        }
        return edge(area, target, width, height, reserved);
    }

    private static Rect union(Rect first, Rect second) {
        if (first == null || first.width <= 0 || first.height <= 0) return second;
        int left = Math.min(first.x, second.x), top = Math.min(first.y, second.y);
        return new Rect(left, top, Math.max(first.right(), second.right()) - left,
            Math.max(first.bottom(), second.bottom()) - top);
    }

    private static Rect candidate(Rect area, Rect target, int width, int height, Side side) {
        int centeredX = clamp(target.x + (target.width - width) / 2, area.x, area.right() - width);
        int centeredY = clamp(target.y + (target.height - height) / 2, area.y, area.bottom() - height);
        switch (side) {
            case ABOVE:
                return new Rect(centeredX, target.y - GAP - height, width, height);
            case RIGHT:
                return new Rect(target.right() + GAP, centeredY, width, height);
            case LEFT:
                return new Rect(target.x - GAP - width, centeredY, width, height);
            case BELOW:
                return new Rect(centeredX, target.bottom() + GAP, width, height);
            default:
                return new Rect(area.right() - width, area.y, width, height);
        }
    }

    private static Placement edge(Rect area, Rect target, int width, int height, List<Rect> obstacles) {
        List<Rect> free = new ArrayList<>();
        free.add(area);
        for (Rect obstacle : obstacles) {
            List<Rect> next = new ArrayList<>();
            for (Rect available : free) {
                subtract(available, obstacle, next);
            }
            free = next;
        }
        Rect best = null;
        long bestScore = Long.MAX_VALUE;
        for (Rect available : free) {
            int actualWidth = Math.min(width, available.width);
            int actualHeight = Math.min(height, available.height);
            if (actualWidth < 1 || actualHeight < 1) {
                continue;
            }
            for (int x : new int[] { available.right() - actualWidth, available.x }) {
                for (int y : new int[] { available.y, available.bottom() - actualHeight }) {
                    Rect panel = new Rect(x, y, actualWidth, actualHeight);
                    // Preserve readable space first, then prefer an edge away from the target.
                    long lostArea = (long) width * height - (long) actualWidth * actualHeight;
                    long score = lostArea * ((long) area.width * area.height + 1)
                        + intersectionArea(panel, target);
                    if (best == null || score < bestScore) {
                        best = panel;
                        bestScore = score;
                    }
                }
            }
        }
        return new Placement(best == null ? new Rect(area.x, area.y, 0, 0) : best, Side.EDGE, true);
    }

    private static void subtract(Rect area, Rect obstacle, List<Rect> result) {
        if (intersectionArea(area, obstacle) == 0) {
            result.add(area);
            return;
        }
        int left = Math.max(area.x, obstacle.x);
        int right = Math.min(area.right(), obstacle.right());
        int top = Math.max(area.y, obstacle.y);
        int bottom = Math.min(area.bottom(), obstacle.bottom());
        add(result, area.x, area.y, area.width, top - area.y);
        add(result, area.x, bottom, area.width, area.bottom() - bottom);
        add(result, area.x, top, left - area.x, bottom - top);
        add(result, right, top, area.right() - right, bottom - top);
    }

    private static void add(List<Rect> result, int x, int y, int width, int height) {
        if (width > 0 && height > 0) {
            result.add(new Rect(x, y, width, height));
        }
    }

    private static boolean valid(Rect panel, Rect area, Rect target, List<Rect> obstacles) {
        if (panel.x < area.x || panel.y < area.y || panel.right() > area.right()
            || panel.bottom() > area.bottom() || intersectionArea(panel, target) > 0) {
            return false;
        }
        for (Rect obstacle : obstacles) {
            if (intersectionArea(panel, obstacle) > 0) {
                return false;
            }
        }
        return true;
    }

    static long intersectionArea(Rect first, Rect second) {
        if (first == null || second == null) {
            return 0;
        }
        return (long) Math.max(0, Math.min(first.right(), second.right()) - Math.max(first.x, second.x))
            * Math.max(0, Math.min(first.bottom(), second.bottom()) - Math.max(first.y, second.y));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
