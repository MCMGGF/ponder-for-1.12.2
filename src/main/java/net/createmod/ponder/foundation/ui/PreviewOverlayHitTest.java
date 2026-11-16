package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.List;

/** Drawn overlay regions that must not forward pointer input to the camera. */
final class PreviewOverlayHitTest {

    private final List<Area> areas = new ArrayList<Area>();

    void clear() {
        areas.clear();
    }

    void add(int x, int y, int width, int height) {
        if (width > 0 && height > 0) {
            areas.add(new Area(x, y, width, height));
        }
    }

    boolean contains(int x, int y) {
        for (Area area : areas) {
            long relativeX = (long) x - area.x;
            long relativeY = (long) y - area.y;
            if (relativeX >= 0 && relativeX < area.width && relativeY >= 0 && relativeY < area.height) {
                return true;
            }
        }
        return false;
    }

    private static final class Area {
        final int x;
        final int y;
        final int width;
        final int height;

        Area(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
