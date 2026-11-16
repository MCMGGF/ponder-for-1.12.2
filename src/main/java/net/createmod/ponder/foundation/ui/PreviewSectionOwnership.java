package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.WorldEvent;
import net.createmod.ponder.foundation.SectionOwnershipEvent;
import net.createmod.ponder.foundation.SectionOwnershipEvent.Action;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Replay-local section state. A later release invalidates pending merges by owner identity. */
final class PreviewSectionOwnership {
    private final Map<BlockPos, PonderSceneRuntime.RuntimeBlockState> blocks;
    private final Map<BlockPos, Group> owners = new LinkedHashMap<>();
    private final Map<BlockPos, Boolean> baseVisible = new LinkedHashMap<>();
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final Map<String, Set<BlockPos>> reveals = new LinkedHashMap<>();
    private final Set<BlockPos> managed = new LinkedHashSet<>();
    private final Map<BlockPos, List<WorldEvent>> localMotions = new LinkedHashMap<>();
    private final Map<BlockPos, WorldEvent> localVisibility = new LinkedHashMap<>();
    private final Map<WorldEvent, Integer> eventOrder = new IdentityHashMap<>();
    private final PriorityQueue<Merge> merges = new PriorityQueue<>((a, b) -> {
        int tick = Integer.compare(a.tick, b.tick);
        return tick == 0 ? Integer.compare(a.order, b.order) : tick;
    });
    private int sequence;

    PreviewSectionOwnership(Map<BlockPos, PonderSceneRuntime.RuntimeBlockState> blocks) { this.blocks = blocks; }

    void before(float tick) {
        while (!merges.isEmpty() && merges.peek().tick <= tick) {
            Merge merge = merges.remove();
            for (BlockPos pos : merge.source.positions)
                if (owners.get(pos) == merge.source) owners.put(pos, merge.target);
        }
    }

    void apply(WorldEvent event) {
        eventOrder.put(event, eventOrder.size());
        SectionOwnershipEvent data = event.getSectionOwnership();
        before(event.getTick());
        switch (data.getAction()) {
            case PREPARE:
                if (reveals.containsKey(data.getRevealId())) throw new IllegalArgumentException("Duplicate reveal identity");
                Set<BlockPos> pending = new LinkedHashSet<>();
                for (BlockPos pos : event.getPositions()) {
                    Group owner = owners.get(pos);
                    boolean registered = owner == null ? baseVisible.containsKey(pos) : !owner.hiding;
                    boolean visible = visibleAt(pos, owner, event.getTick());
                    if (!data.isSmartDisplay() || !registered || !visible || !renderable(blocks.get(pos))) pending.add(pos);
                }
                reveals.put(data.getRevealId(), pending);
                release(pending, false);
                break;
            case STAGE:
                Set<BlockPos> staged = reveal(data.getRevealId());
                for (BlockPos pos : staged) baseVisible.put(pos, false);
                for (BlockPos pos : event.getPositions()) localVisibility.remove(pos);
                break;
            case PLACE:
                release(event.getPositions(), data.isVisible());
                break;
            case SHOW:
            case HIDE:
                if (groups.containsKey(data.getSectionId())) throw new IllegalArgumentException("Section identities cannot be reused");
                Group target = data.getTargetId().isEmpty() ? null : group(data.getTargetId());
                Set<BlockPos> allowed = data.getRevealId().isEmpty() ? null : reveal(data.getRevealId());
                Set<BlockPos> shown = new LinkedHashSet<>();
                for (BlockPos pos : event.getPositions())
                    if ((allowed == null || allowed.contains(pos)) && renderable(blocks.get(pos))) shown.add(pos);
                release(shown, false);
                Group created = new Group(event, shown, data.getAction() == Action.HIDE);
                // Ponder 1.0.91 copies only the merge target's current animated offset on first tick.
                // Rotation is deliberately inherited only at merge completion, without rebasing coordinates.
                if (target != null) created.initialOffset = offsetAt(target, event.getTick());
                groups.put(data.getSectionId(), created);
                for (BlockPos pos : shown) owners.put(pos, created);
                for (BlockPos pos : shown) blocks.get(pos).breakingProgress = 0;
                if (target != null) merges.add(new Merge(event.getTick() + Math.max(1, event.getDuration()) - 1,
                    sequence++, created, target));
                before(event.getTick());
                break;
            case MOVE:
            case ROTATE:
                group(data.getSectionId()).motions.add(event);
                break;
            default: throw new IllegalArgumentException("Unknown section ownership action");
        }
    }

    private Set<BlockPos> reveal(String id) {
        Set<BlockPos> result = reveals.get(id);
        if (result == null) throw new IllegalArgumentException("Unknown reveal identity: " + id);
        return result;
    }
    private Group group(String id) {
        Group result = groups.get(id);
        if (result == null) throw new IllegalArgumentException("Unknown section identity: " + id);
        return result;
    }
    private void release(Iterable<BlockPos> positions, boolean visible) {
        for (BlockPos pos : positions) {
            managed.add(pos); owners.remove(pos); baseVisible.put(pos, visible); localMotions.remove(pos); localVisibility.remove(pos);
        }
    }

    void observeLegacyMotion(WorldEvent event) {
        eventOrder.put(event, eventOrder.size());
        for (BlockPos pos : event.getPositions()) if (managed.contains(pos)) {
            if (event.getType() == PonderScene.WorldEventType.MOVE_SECTION || event.getType() == PonderScene.WorldEventType.ROTATE_SECTION)
                localMotions.computeIfAbsent(pos, key -> new ArrayList<>()).add(event);
            else if (event.getType() == PonderScene.WorldEventType.SHOW_SECTION || event.getType() == PonderScene.WorldEventType.HIDE_SECTION
                || event.getType() == PonderScene.WorldEventType.SET_BLOCK || event.getType() == PonderScene.WorldEventType.SET_BLOCKS
                || event.getType() == PonderScene.WorldEventType.REPLACE_BLOCKS || event.getType() == PonderScene.WorldEventType.RESTORE_BLOCKS)
                localVisibility.put(pos, event);
        }
    }
    private static boolean renderable(PonderSceneRuntime.RuntimeBlockState block) {
        return block != null && !block.destroyed && block.currentState != null && block.currentState.getBlock() != Blocks.AIR;
    }

    private boolean visibleAt(BlockPos pos, Group owner, float tick) {
        WorldEvent override = localVisibility.get(pos);
        if (override == null) return owner == null ? Boolean.TRUE.equals(baseVisible.get(pos)) : owner.visible(tick);
        if (override.getType() != PonderScene.WorldEventType.HIDE_SECTION) return true;
        return tick < override.getTick() + (override.getDuration() > 0 ? override.getDuration() : 15) - 1;
    }

    void finish(float tick) {
        before(tick);
        for (BlockPos pos : managed) {
            PonderSceneRuntime.RuntimeBlockState state = blocks.get(pos);
            if (state == null) continue;
            state.transforms.clear();
            PonderSceneRuntime.resetFadeOffset(state);
            Group owner = owners.get(pos);
            state.sectionId = owner == null ? "" : owner.event.getSectionOwnership().getSectionId();
            if (!renderable(state)) { state.visible = false; state.fade = 0; continue; }
            if (owner == null) {
                state.visible = Boolean.TRUE.equals(baseVisible.get(pos)); state.fade = state.visible ? 1F : 0F;
            } else {
                float elapsed = tick - owner.event.getTick();
                if (owner.hiding) PonderSceneRuntime.animateHideSection(state, owner.event.getDirection(), elapsed, owner.event.getDuration());
                else PonderSceneRuntime.animateShowSection(state, owner.event.getDirection(), elapsed, owner.event.getDuration());
                if (owner.initialOffset.lengthSquared() > 1E-12) state.transforms.add(PonderSceneRuntime.TransformStep.move(owner.initialOffset));
            }
            List<WorldEvent> motions = new ArrayList<>();
            if (owner != null) motions.addAll(owner.motions);
            if (localMotions.containsKey(pos)) motions.addAll(localMotions.get(pos));
            motions.sort((a, b) -> Integer.compare(eventOrder.get(a), eventOrder.get(b)));
            applyMotions(state, motions, tick);
            WorldEvent visible = localVisibility.get(pos);
            if (visible != null) {
                if (visible.getType() == PonderScene.WorldEventType.SHOW_SECTION)
                    PonderSceneRuntime.animateShowSection(state, visible.getDirection(), tick - visible.getTick(), visible.getDuration());
                else if (visible.getType() == PonderScene.WorldEventType.HIDE_SECTION)
                    PonderSceneRuntime.animateHideSection(state, visible.getDirection(), tick - visible.getTick(), visible.getDuration());
                else { state.visible = true; state.fade = 1F; PonderSceneRuntime.resetFadeOffset(state); }
            }
        }
    }

    private static void applyMotions(PonderSceneRuntime.RuntimeBlockState state, List<WorldEvent> motions, float tick) {
        for (WorldEvent motion : motions) {
                if (motion.getType() == PonderScene.WorldEventType.MOVE_SECTION)
                    PonderSceneRuntime.applySectionMove(state, motion, tick - motion.getTick());
                else PonderSceneRuntime.applySectionRotation(state, motion, tick - motion.getTick());
        }
    }

    private static Vec3d offsetAt(Group group, float tick) {
        Vec3d offset = group.initialOffset;
        for (WorldEvent motion : group.motions) if (motion.getType() == PonderScene.WorldEventType.MOVE_SECTION)
            offset = offset.add(motion.getOffset().scale(PonderSceneRuntime.transformProgress(tick - motion.getTick(), motion.getDuration())));
        return offset;
    }

    private static final class Group {
        final WorldEvent event;
        final Set<BlockPos> positions;
        final List<WorldEvent> motions = new ArrayList<>();
        final boolean hiding;
        Vec3d initialOffset = Vec3d.ZERO;
        Group(WorldEvent event, Set<BlockPos> positions, boolean hiding) { this.event = event; this.positions = positions; this.hiding = hiding; }
        boolean visible(float tick) { return !hiding || tick < event.getTick() + Math.max(1, event.getDuration()) - 1; }
    }
    private static final class Merge {
        final int tick, order;
        final Group source, target;
        Merge(int tick, int order, Group source, Group target) { this.tick = tick; this.order = order; this.source = source; this.target = target; }
    }

    /** Each stable section's full transform history bounds every coordinate that can merge into it. */
    static List<PreviewMotionBounds.Bounds> bounds(PonderScene scene, Set<BlockPos> renderable) {
        Map<WorldEvent, Integer> eventOrder = new IdentityHashMap<>();
        Map<String, Set<BlockPos>> positions = new LinkedHashMap<>();
        Map<String, List<WorldEvent>> motions = new LinkedHashMap<>();
        List<WorldEvent> shows = new ArrayList<>();
        Map<String, Set<EnumFacing>> fades = new LinkedHashMap<>();
        for (WorldEvent event : scene.getWorldEvents()) {
            eventOrder.put(event, eventOrder.size());
            SectionOwnershipEvent data = event.getSectionOwnership();
            if (data == null) continue;
            if (data.getAction() == Action.SHOW || data.getAction() == Action.HIDE) {
                Set<BlockPos> group = positions.computeIfAbsent(data.getSectionId(), key -> new LinkedHashSet<>());
                for (BlockPos pos : event.getPositions()) if (renderable.contains(pos)) group.add(pos);
                shows.add(event);
                if (event.getDirection() != null) fades.computeIfAbsent(data.getSectionId(), key -> new LinkedHashSet<>())
                    .add(data.getAction() == Action.SHOW ? event.getDirection().getOpposite() : event.getDirection());
            } else if (data.getAction() == Action.MOVE || data.getAction() == Action.ROTATE)
                motions.computeIfAbsent(data.getSectionId(), key -> new ArrayList<>()).add(event);
        }
        // Targets predate their unique temporary source; reverse creation order propagates the DAG once.
        for (int i = shows.size() - 1; i >= 0; i--) {
            SectionOwnershipEvent data = shows.get(i).getSectionOwnership();
            if (!data.getTargetId().isEmpty()) positions.computeIfAbsent(data.getTargetId(), key -> new LinkedHashSet<>())
                .addAll(positions.get(data.getSectionId()));
        }
        Map<BlockPos, Set<String>> possibleOwners = new LinkedHashMap<>();
        for (Map.Entry<String, Set<BlockPos>> entry : positions.entrySet())
            for (BlockPos pos : entry.getValue()) possibleOwners.computeIfAbsent(pos, key -> new LinkedHashSet<>()).add(entry.getKey());
        Map<String, Set<WorldEvent>> legacyMotions = new LinkedHashMap<>();
        for (WorldEvent event : scene.getWorldEvents()) if (event.getSectionOwnership() == null && PreviewMotionBounds.isMotion(event))
            for (BlockPos pos : event.getPositions()) if (possibleOwners.containsKey(pos))
                for (String owner : possibleOwners.get(pos)) legacyMotions.computeIfAbsent(owner, key -> new LinkedHashSet<>()).add(event);
        List<PreviewMotionBounds.Bounds> result = new ArrayList<>();
        for (Map.Entry<String, Set<BlockPos>> entry : positions.entrySet()) {
            PreviewMotionBounds.Bounds geometry = null;
            for (BlockPos pos : entry.getValue()) {
                if (geometry == null) geometry = PreviewMotionBounds.Bounds.block(pos);
                else geometry.include(PreviewMotionBounds.Bounds.block(pos));
            }
            if (geometry == null) continue;
            List<WorldEvent> chain = new ArrayList<>(motions.containsKey(entry.getKey()) ? motions.get(entry.getKey()) : Collections.emptyList());
            if (legacyMotions.containsKey(entry.getKey())) chain.addAll(legacyMotions.get(entry.getKey()));
            chain.sort((a, b) -> Integer.compare(eventOrder.get(a), eventOrder.get(b)));
            PreviewMotionBounds.Bounds swept = PreviewMotionBounds.computeOwnership(geometry, chain);
            for (EnumFacing direction : fades.containsKey(entry.getKey()) ? fades.get(entry.getKey()) : Collections.<EnumFacing>emptySet())
                swept.includeFade(direction, 0.5D);
            result.add(swept);
        }
        return result;
    }
}
