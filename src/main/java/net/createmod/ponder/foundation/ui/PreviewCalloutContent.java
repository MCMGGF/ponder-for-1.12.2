package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.OverlayEvent;
import net.createmod.ponder.foundation.PonderScene.OverlayEventType;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Keeps each explanation attached to its own target and GUI descendants. */
final class PreviewCalloutContent {

    static List<Group> collect(PonderScene scene, float tick) {
        return collect(scene, tick, I18n::format);
    }

    static List<Group> collect(PonderScene scene, float tick, Function<String, String> translate) {
        if (scene == null) {
            return Collections.emptyList();
        }
        List<OverlayEvent> active = new ArrayList<>();
        List<OverlayEvent> gui = new ArrayList<>();
        for (OverlayEvent event : scene.getOverlayEvents()) {
            if (scene.isOverlayCleared(event, tick)) continue;
            OverlayEventType type = event.getType();
            if (event.getTick() <= tick && tick <= (long) event.getTick() + event.getDuration()
                && (type == OverlayEventType.TEXT || type == OverlayEventType.CONTROLS
                    || type == OverlayEventType.GUI_TEXTURE || type == OverlayEventType.GUI_HIGHLIGHT)) {
                if (type == OverlayEventType.TEXT && (event.getText() == null || event.getText().isEmpty())) {
                    continue;
                }
                active.add(event);
                if (type == OverlayEventType.GUI_TEXTURE || type == OverlayEventType.GUI_HIGHLIGHT) {
                    gui.add(event);
                }
            }
        }
        Map<OverlayEvent, Target> guiTargets = new IdentityHashMap<>();
        for (List<OverlayEvent> page : PreviewGuiPageLayout.pages(gui)) {
            OverlayEvent root = page.get(0);
            Target target = target(root);
            if (target.key == EdgeKey.INSTANCE) {
                for (OverlayEvent descendant : page) {
                    Target candidate = target(descendant);
                    if (candidate.key != EdgeKey.INSTANCE) {
                        target = candidate;
                        break;
                    }
                }
            }
            if (target.key == EdgeKey.INSTANCE) {
                target = new Target(root, Collections.emptyList(), null, false);
            }
            for (OverlayEvent member : page) {
                guiTargets.put(member, target);
            }
        }
        Map<Object, MutableGroup> groups = new LinkedHashMap<>();
        for (OverlayEvent event : active) {
            Target target = guiTargets.containsKey(event) ? guiTargets.get(event) : target(event);
            MutableGroup group = groups.computeIfAbsent(target.key, ignored -> new MutableGroup(target));
            group.events.add(event);
            if (event.getText() != null && !event.getText().isEmpty()) {
                group.paragraphs.add(event.getText());
            }
            if (event.getType() == OverlayEventType.CONTROLS) {
                group.paragraphs.add(controlDescription(event, translate));
            }
        }
        List<Group> result = new ArrayList<>();
        for (MutableGroup group : groups.values()) {
            result.add(new Group(group));
        }
        return Collections.unmodifiableList(result);
    }

    private static Target target(OverlayEvent event) {
        if (!event.getTargetPositions().isEmpty()) {
            TreeSet<BlockPos> ordered = new TreeSet<>(Comparator.comparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));
            ordered.addAll(event.getTargetPositions());
            List<BlockPos> positions = Collections.unmodifiableList(new ArrayList<>(ordered));
            return new Target(positions, positions, event.getPointAt(), false);
        }
        Vec3d point = event.getPointAt();
        return point == null ? new Target(EdgeKey.INSTANCE, Collections.emptyList(), null, false)
            : new Target(new PointKey(point, event.isScenePoint()), Collections.emptyList(), point, event.isScenePoint());
    }

    static String controlDescription(OverlayEvent event, Function<String, String> translate) {
        List<String> labels = new ArrayList<>();
        if (event.isControlLeftClick()) labels.add(label(translate, "left_click", "\u5de6\u952e"));
        if (event.isControlRightClick()) labels.add(label(translate, "right_click", "\u53f3\u952e"));
        if (event.isControlScroll()) labels.add(label(translate, "scroll", "\u6eda\u8f6e"));
        if (event.isControlSneaking()) labels.add(label(translate, "sneak", "\u6f5c\u884c"));
        if (event.isControlCtrl()) labels.add(label(translate, "ctrl", "Ctrl"));
        if (!event.getControlItem().isEmpty()) labels.add(event.getControlItem().getDisplayName());
        return labels.isEmpty() ? label(translate, "interact", "\u4ea4\u4e92") : String.join("  ", labels);
    }

    private static String label(Function<String, String> translate, String name, String fallback) {
        String key = "ponder_legacy.callout.control." + name;
        String value;
        try {
            value = translate.apply(key);
        } catch (RuntimeException unavailable) {
            return fallback;
        }
        return value == null || value.isEmpty() || key.equals(value) ? fallback : value;
    }

    static final class Group {
        final Object key;
        final List<OverlayEvent> events;
        final List<String> paragraphs;
        final List<BlockPos> targetPositions;
        final Vec3d pointAt;
        final boolean scenePoint;
        final int color;
        final boolean connectorVisible;

        private Group(MutableGroup group) {
            key = group.target.key;
            events = Collections.unmodifiableList(new ArrayList<>(group.events));
            paragraphs = Collections.unmodifiableList(new ArrayList<>(group.paragraphs));
            targetPositions = group.target.positions;
            pointAt = group.target.point;
            scenePoint = group.target.scenePoint;
            OverlayEvent first = group.events.get(0);
            color = first.getColor();
            connectorVisible = first.isConnectorVisible();
        }

        String text() {
            return String.join("\n\n", paragraphs);
        }

        List<OverlayEvent> guiEvents() {
            List<OverlayEvent> result = new ArrayList<>();
            for (OverlayEvent event : events) {
                if (event.getType() == OverlayEventType.GUI_TEXTURE || event.getType() == OverlayEventType.GUI_HIGHLIGHT) {
                    result.add(event);
                }
            }
            return Collections.unmodifiableList(result);
        }
    }

    private enum EdgeKey { INSTANCE }

    private static final class Target {
        final Object key;
        final List<BlockPos> positions;
        final Vec3d point;
        final boolean scenePoint;

        Target(Object key, List<BlockPos> positions, Vec3d point, boolean scenePoint) {
            this.key = key;
            this.positions = positions;
            this.point = point;
            this.scenePoint = scenePoint;
        }
    }

    private static final class MutableGroup {
        final Target target;
        final List<OverlayEvent> events = new ArrayList<>();
        final List<String> paragraphs = new ArrayList<>();

        MutableGroup(Target target) {
            this.target = target;
        }
    }

    private static final class PointKey {
        final double x;
        final double y;
        final double z;
        final boolean scenePoint;

        PointKey(Vec3d point, boolean scenePoint) {
            x = point.x == 0 ? 0 : point.x;
            y = point.y == 0 ? 0 : point.y;
            z = point.z == 0 ? 0 : point.z;
            this.scenePoint = scenePoint;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof PointKey)) return false;
            PointKey key = (PointKey) other;
            return scenePoint == key.scenePoint && Double.compare(x, key.x) == 0 && Double.compare(y, key.y) == 0 && Double.compare(z, key.z) == 0;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(x, y, z, scenePoint);
        }
    }
}
