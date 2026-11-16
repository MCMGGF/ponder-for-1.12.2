package net.createmod.ponder.authoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.SectionOwnershipEvent;
import net.createmod.ponder.foundation.SectionOwnershipEvent.Action;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Emits owned-section data; runtime selection and merge completion are evaluated during replay. */
final class AuthoringSectionOwnership {
    static final class Handle {
        final String id;
        final Vec3d pivot;
        final Set<BlockPos> potentialPositions = new LinkedHashSet<>();
        Handle(String id, Selection selection) { this.id = id; pivot = selection.getCenter(); selection.forEach(pos -> potentialPositions.add(pos.toImmutable())); }
    }
    private final Map<String, Handle> links = new LinkedHashMap<>();
    private final Set<String> reserved = new LinkedHashSet<>();
    private int sequence;
    AuthoringSectionOwnership(com.google.gson.JsonObject segment) {
        for (com.google.gson.JsonElement value : segment.getAsJsonArray("steps")) {
            com.google.gson.JsonObject step = value.getAsJsonObject();
            if (!"native_section".equals(AuthoringDocument.string(step, "type", ""))) continue;
            for (String key : new String[] {"sectionId", "targetId", "revealId"}) reserved.add(AuthoringDocument.string(step, key, ""));
        }
    }
    private String next(String kind) {
        String id; do { id = "authoring/" + kind + "/" + sequence++; } while (!reserved.add(id));
        return id;
    }

    String prepare(SceneBuilder scene, Iterable<BlockPos> positions, boolean smart) {
        String reveal = next("reveal");
        record(scene, new SectionOwnershipEvent(Action.PREPARE, "", "", reveal, smart, false), positions, null, 0, null, null, null);
        return reveal;
    }
    void stage(SceneBuilder scene, Iterable<BlockPos> positions, String reveal) {
        record(scene, new SectionOwnershipEvent(Action.STAGE, "", "", reveal, false, false), positions, null, 0, null, null, null);
    }
    void place(SceneBuilder scene, Iterable<BlockPos> positions, boolean visible) {
        record(scene, new SectionOwnershipEvent(Action.PLACE, "", "", "", false, visible), positions, null, 0, null, null, null);
    }
    void hide(SceneBuilder scene, Selection selection, int duration, EnumFacing direction) {
        record(scene, new SectionOwnershipEvent(Action.HIDE, next("hidden"), "", "", false, false), selection,
            duration == 0 ? null : direction, Math.max(1, duration), null, null, null);
    }
    void show(SceneBuilder scene, Selection selection, String linkId, String reveal, int duration, EnumFacing direction) {
        show(scene, selection, linkId, reveal, duration, direction, null);
    }
    private Handle show(SceneBuilder scene, Selection selection, String linkId, String reveal, int duration,
        EnumFacing direction, Handle working) {
        Handle target = working != null ? working : links.get(linkId);
        String id = next("section");
        record(scene, new SectionOwnershipEvent(Action.SHOW, id, target == null ? "" : target.id, reveal, false, true),
            selection, duration == 0 ? null : direction, Math.max(1, duration), null, null, null);
        if (target == null) {
            target = new Handle(id, selection);
            if (!linkId.isEmpty()) links.put(linkId, target);
        } else for (BlockPos pos : selection) target.potentialPositions.add(pos.toImmutable());
        return target;
    }
    void showGroups(SceneBuilder scene, net.createmod.ponder.api.scene.SceneBuildingUtil util, Selection entire,
        String linkId, String reveal, List<ExtraStructurePlanning.EntranceGroup> groups, int advance) {
        Handle working = links.get(linkId);
        int elapsed = 0;
        for (ExtraStructurePlanning.EntranceGroup group : groups) {
            if (group.getStartOffset() > elapsed) scene.idle(group.getStartOffset() - elapsed);
            elapsed = group.getStartOffset();
            Selection selected = util.select().position(BlockPos.ORIGIN).substract(util.select().position(BlockPos.ORIGIN));
            for (BlockPos pos : group.getPositions()) selected.add(util.select().position(pos));
            working = show(scene, selected, linkId, reveal, group.getDuration(), group.getDirection(), working);
        }
        if (working == null && !linkId.isEmpty()) show(scene, entire, linkId, reveal, 0, null);
        if (advance > elapsed) scene.idle(advance - elapsed);
    }
    void move(SceneBuilder scene, String linkId, Vec3d offset, int duration) {
        Handle handle = require(linkId);
        record(scene, new SectionOwnershipEvent(Action.MOVE, handle.id, "", "", false, true), handle.potentialPositions,
            null, duration, offset, null, null);
    }
    void rotate(SceneBuilder scene, String linkId, Vec3d rotation, Vec3d pivot, int duration) {
        Handle handle = require(linkId);
        record(scene, new SectionOwnershipEvent(Action.ROTATE, handle.id, "", "", false, true), handle.potentialPositions,
            null, duration, null, rotation, pivot == null ? handle.pivot : pivot);
    }
    private Handle require(String link) {
        Handle handle = links.get(link);
        if (handle == null) throw new IllegalArgumentException("Unknown section link: " + link);
        return handle;
    }
    private static void record(SceneBuilder scene, SectionOwnershipEvent event, Iterable<BlockPos> positions,
        EnumFacing direction, int duration, Vec3d offset, Vec3d rotation, Vec3d pivot) {
        scene.getScene().recordSectionOwnership(event, positions, direction, duration, offset, rotation, pivot);
    }
}
