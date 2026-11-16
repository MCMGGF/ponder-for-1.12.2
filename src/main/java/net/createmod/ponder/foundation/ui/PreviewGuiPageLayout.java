package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.createmod.ponder.foundation.PonderScene.OverlayEvent;

/** Each root GUI and its children share one bounded page and one transform. */
final class PreviewGuiPageLayout {

    static List<List<OverlayEvent>> pages(List<OverlayEvent> events) {
        Map<String, OverlayEvent> byId = new LinkedHashMap<>();
        for (OverlayEvent event : events) {
            if (event.getOverlayId() != null && !event.getOverlayId().isEmpty()) {
                byId.put(event.getOverlayId(), event);
            }
        }
        List<OverlayEvent> nodes = new ArrayList<>();
        Map<OverlayEvent, OverlayEvent> parents = new IdentityHashMap<>();
        for (OverlayEvent event : events) {
            if (!parents.containsKey(event)) {
                nodes.add(event);
                parents.put(event, byId.get(event.getParentOverlayId()));
            }
        }

        // False marks the current walk. Break only an edge inside its cycle,
        // so descendants encountered before that cycle stay with their parent.
        Map<OverlayEvent, Boolean> completed = new IdentityHashMap<>();
        for (OverlayEvent event : nodes) {
            if (completed.containsKey(event)) {
                continue;
            }
            List<OverlayEvent> path = new ArrayList<>();
            OverlayEvent cursor = event;
            while (cursor != null && !completed.containsKey(cursor)) {
                completed.put(cursor, false);
                path.add(cursor);
                cursor = parents.get(cursor);
            }
            if (cursor != null && !completed.get(cursor)) {
                parents.put(cursor, null);
            }
            for (OverlayEvent visited : path) {
                completed.put(visited, true);
            }
        }

        Map<OverlayEvent, List<OverlayEvent>> children = new IdentityHashMap<>();
        for (OverlayEvent event : nodes) {
            OverlayEvent parent = parents.get(event);
            if (parent != null) {
                children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(event);
            }
        }
        List<List<OverlayEvent>> pages = new ArrayList<>();
        for (OverlayEvent event : nodes) {
            if (parents.get(event) != null) {
                continue;
            }
            List<OverlayEvent> page = new ArrayList<>();
            page.add(event);
            for (int i = 0; i < page.size(); i++) {
                List<OverlayEvent> descendants = children.get(page.get(i));
                if (descendants != null) {
                    page.addAll(descendants);
                }
            }
            pages.add(page);
        }
        return pages;
    }

    static List<Placement> layout(List<OverlayEvent> page, PreviewDebugLayout.Rect area) {
        if (page.isEmpty() || area.width < 1 || area.height < 1) {
            return Collections.emptyList();
        }
        List<Box> boxes = new ArrayList<>();
        Map<String, Box> byId = new LinkedHashMap<>();
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (OverlayEvent event : page) {
            Box parent = byId.get(event.getParentOverlayId());
            double x = 0;
            double y = 0;
            double width = Math.max(1, event.getDisplayWidth());
            double height = Math.max(1, event.getDisplayHeight());
            if (parent != null) {
                double scaleX = parent.width / Math.max(1, parent.event.getRegionWidth());
                double scaleY = parent.height / Math.max(1, parent.event.getRegionHeight());
                x = parent.x + event.getGuiX() * scaleX + event.getOffsetX();
                y = parent.y + event.getGuiY() * scaleY + event.getOffsetY();
                if (event.isScaleToParent()) {
                    width = Math.max(1, event.getRegionWidth()) * scaleX;
                    height = Math.max(1, event.getRegionHeight()) * scaleY;
                }
            }
            Box box = new Box(event, x, y, width, height);
            boxes.add(box);
            if (event.getOverlayId() != null) {
                byId.put(event.getOverlayId(), box);
            }
            int padding = event.isFramed() ? 4 : 0;
            minX = Math.min(minX, x - padding);
            minY = Math.min(minY, y - padding);
            maxX = Math.max(maxX, x + width + padding);
            maxY = Math.max(maxY, y + height + padding);
        }
        double scale = Math.min(1, Math.min(area.width / (maxX - minX), area.height / (maxY - minY)));
        double originX = area.x + (area.width - (maxX - minX) * scale) / 2;
        double originY = area.y + (area.height - (maxY - minY) * scale) / 2;
        List<Placement> placements = new ArrayList<>();
        for (Box box : boxes) {
            int padding = box.event.isFramed() ? 4 : 0;
            PreviewDebugLayout.Rect draw = rect(area, originX + (box.x - minX) * scale,
                originY + (box.y - minY) * scale, box.width * scale, box.height * scale);
            PreviewDebugLayout.Rect panel = rect(area, originX + (box.x - padding - minX) * scale,
                originY + (box.y - padding - minY) * scale,
                (box.width + padding * 2) * scale, (box.height + padding * 2) * scale);
            placements.add(new Placement(box.event, draw, panel));
        }
        return placements;
    }

    static int preferredHeight(List<List<OverlayEvent>> pages, int contentWidth) {
        int height = 0;
        for (List<OverlayEvent> page : pages) {
            int top = Integer.MAX_VALUE, bottom = Integer.MIN_VALUE;
            for (Placement placement : layout(page, new PreviewDebugLayout.Rect(0, 0, Math.max(1, contentWidth), 240))) {
                top = Math.min(top, placement.panel.y);
                bottom = Math.max(bottom, placement.panel.bottom());
            }
            if (top != Integer.MAX_VALUE) height = Math.max(height, bottom - top);
        }
        return Math.max(1, Math.min(240, height));
    }

    static int minimumReadableHeight(int desiredHeight, int tabs) {
        return Math.max(36 + tabs, Math.min(96 + tabs, desiredHeight));
    }

    private static PreviewDebugLayout.Rect rect(PreviewDebugLayout.Rect area,
        double x, double y, double width, double height) {
        int left = Math.max(area.x, Math.min(area.right() - 1, (int) Math.round(x)));
        int top = Math.max(area.y, Math.min(area.bottom() - 1, (int) Math.round(y)));
        int right = Math.max(left + 1, Math.min(area.right(), (int) Math.round(x + width)));
        int bottom = Math.max(top + 1, Math.min(area.bottom(), (int) Math.round(y + height)));
        return new PreviewDebugLayout.Rect(left, top, right - left, bottom - top);
    }

    static final class Placement {
        final OverlayEvent event;
        final PreviewDebugLayout.Rect draw;
        final PreviewDebugLayout.Rect panel;

        Placement(OverlayEvent event, PreviewDebugLayout.Rect draw, PreviewDebugLayout.Rect panel) {
            this.event = event;
            this.draw = draw;
            this.panel = panel;
        }
    }

    private static final class Box {
        final OverlayEvent event;
        final double x;
        final double y;
        final double width;
        final double height;

        Box(OverlayEvent event, double x, double y, double width, double height) {
            this.event = event;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
