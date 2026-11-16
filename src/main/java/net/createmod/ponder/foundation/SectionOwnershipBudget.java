package net.createmod.ponder.foundation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Counts implicit membership as well as explicit selections without expanding coordinates. */
public final class SectionOwnershipBudget {
    private static final int MAX_EVENTS = 4096;
    private static final int MAX_MOTIONS = 128;
    private static final long MAX_MOTION_POSITIONS = 1048576;
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private int events, motions;
    private long motionPositions;

    public void add(SectionOwnershipEvent event, int selectedPositions) {
        if (++events > MAX_EVENTS) throw new IllegalArgumentException("Section ownership exceeds 4096 events");
        switch (event.getAction()) {
            case SHOW: case HIDE:
                if (groups.containsKey(event.getSectionId())) throw new IllegalArgumentException("Duplicate section identity");
                Group target = event.getTargetId().isEmpty() ? null : require(event.getTargetId());
                Group created = new Group(target);
                groups.put(event.getSectionId(), created);
                int depth = 0;
                for (Group current = created; current != null; current = current.target) {
                    if (++depth > 64) throw new IllegalArgumentException("Section merge ancestry exceeds 64");
                    current.positions += selectedPositions;
                    charge((long) selectedPositions * current.motions);
                }
                break;
            case MOVE: case ROTATE:
                Group group = require(event.getSectionId());
                motion();
                group.motions++;
                charge(group.positions);
                break;
            default: break;
        }
    }

    public void legacyMotion(int selectedPositions) { motion(); charge(selectedPositions); }
    private void motion() {
        if (++motions > MAX_MOTIONS) throw new IllegalArgumentException("Owned scenes exceed 128 section motions");
    }
    private void charge(long amount) {
        motionPositions += amount;
        if (motionPositions > MAX_MOTION_POSITIONS)
            throw new IllegalArgumentException("Implicit section motion positions exceed 1048576");
    }
    private Group require(String id) {
        Group group = groups.get(id);
        if (group == null) throw new IllegalArgumentException("Unknown section identity: " + id);
        return group;
    }
    private static final class Group {
        final Group target;
        long positions;
        int motions;
        Group(Group target) { this.target = target; }
    }
}
