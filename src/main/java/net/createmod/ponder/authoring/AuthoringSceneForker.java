package net.createmod.ponder.authoring;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.createmod.ponder.authoring.assets.BlueprintCapture;
import net.createmod.ponder.authoring.assets.StructurePool;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSchematic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Explicit, loss-aware copy of an already compiled scene. Never called by startup or registration. */
public final class AuthoringSceneForker {
    public interface AssetWriter { void write(String assetId, PonderSchematic structure) throws IOException; }
    private AuthoringSceneForker() { }
    private static final class Timed {
        final int tick; final JsonObject step;
        Timed(int tick, JsonObject step) { this.tick = tick; this.step = step; }
    }

    public static AuthoringDocument fork(PonderScene source, String newId, StructurePool pool) throws IOException {
        if (pool == null) throw new IOException("An explicit local structure pool is required");
        return fork(source, newId, (id, structure) -> pool.saveNew(id, structure, BlueprintCapture.NbtPolicy.FULL_NBT_EXPLICIT_OPT_IN));
    }

    public static AuthoringDocument fork(PonderScene source, String newId, AssetWriter writer) throws IOException {
        if (source == null || writer == null || !AuthoringDocument.validId(newId)) throw new IOException("Scene, safe new id and explicit asset writer are required");
        JsonObject root = new JsonObject(); root.addProperty("id", newId); root.addProperty("title", source.getTitle());
        JsonArray items = new JsonArray(); items.add(source.getComponent() == null ? "minecraft:book" : source.getComponent().toString()); root.add("items", items);
        JsonObject provenance = new JsonObject(); provenance.addProperty("dialect", "compiled-1.12.2-scene");
        provenance.addProperty("originalSceneId", source.getSceneId().toString()); provenance.addProperty("originalSchematic", source.getSchematicLocation().toString());
        root.add("source", provenance);
        JsonObject segment = new JsonObject(); segment.addProperty("id", "main"); segment.addProperty("title", source.getTitle()); segment.addProperty("autoReveal", false); segment.addProperty("autoKeyframes", false);
        JsonObject view = new JsonObject(); view.addProperty("scale", source.getScaleFactor()); view.addProperty("offsetY", source.getSceneOffsetY());
        view.addProperty("baseX", source.getBasePlateOffsetX()); view.addProperty("baseZ", source.getBasePlateOffsetZ()); view.addProperty("baseSize", source.getBasePlateSize());
        view.addProperty("hideShadow", source.isHidePlatformShadow()); view.addProperty("nextUp", source.isNextUpEnabled()); view.addProperty("finished", source.isFinished()); segment.add("view", view);
        List<Timed> timeline = new ArrayList<Timed>();
        if (source.getFramingBounds() != null) {
            JsonObject bounds = step("encapsulate_bounds"); bounds.add("bounds", vec(source.getFramingBounds()));
            timeline.add(new Timed(0, bounds));
        }
        for (Integer tick : source.getKeyframeTicks()) timeline.add(new Timed(tick, step("keyframe")));
        for (PonderScene.WorldEvent event : source.getWorldEvents()) world(event, timeline);
        int viewIndex = 0;
        for (int overlayIndex = 0; overlayIndex <= source.getOverlayEvents().size(); overlayIndex++) {
            while (viewIndex < source.getCameraViewEvents().size()
                && source.getCameraViewEvents().get(viewIndex).getOverlayCutoff() <= overlayIndex) {
                PonderScene.CameraViewEvent event = source.getCameraViewEvents().get(viewIndex++);
                JsonObject zoom = step("zoom_scene");
                if (event.getCenter() != null) zoom.add("point", vec(event.getCenter()));
                zoom.addProperty("scale", event.getMultiplier()); zoom.addProperty("duration", event.getDuration());
                zoom.addProperty("waitForCompletion", false); timeline.add(new Timed(event.getTick(), zoom));
            }
            if (overlayIndex < source.getOverlayEvents().size()) overlay(source.getOverlayEvents().get(overlayIndex), timeline);
        }
        Map<Integer, Integer> actorIds = new LinkedHashMap<Integer, Integer>();
        for (PonderScene.ActorEvent event : source.getActorEvents()) {
            Integer id = actorIds.get(Integer.valueOf(event.getActorId()));
            if (id == null) { id = Integer.valueOf(actorIds.size() + 1); actorIds.put(Integer.valueOf(event.getActorId()), id); }
            actor(event, id.intValue(), timeline);
        }
        for (PonderScene.SoundEvent event : source.getSoundEvents()) {
            JsonObject sound = step("play_sound"); sound.addProperty("sound", event.getSoundId().toString());
            sound.addProperty("source", event.getCategory().getName()); sound.addProperty("soundVolume", event.getVolume());
            sound.addProperty("pitch", event.getPitch()); timeline.add(new Timed(event.getTick(), sound));
        }
        for (PonderScene.CameraEvent event : source.getCameraEvents()) {
            JsonObject step = step("rotate_camera_y"); step.addProperty("degrees", event.getYawDegrees());
            if (event.affectsPitch()) step.addProperty("degreesX", event.getPitchDegrees()); step.addProperty("duration", event.getDuration());
            step.addProperty("instant", event.getDuration() == 0); step.addProperty("legacyAdditive", !event.isTargetBased());
            step.addProperty("waitForCompletion", false); timeline.add(new Timed(event.getTick(), step));
        }
        for (PonderScene.ParticleEffectEvent event : source.getParticleEvents()) {
            JsonObject raw = step("spawn_particles"); raw.addProperty("particle", event.getParticleName()); raw.addProperty("count", event.getAmountPerCycle());
            raw.addProperty("duration", event.getCycles()); raw.add("pos", vec(event.getLocation())); raw.add("motion", vec(event.getMotion()));
            raw.addProperty("withinBlockSpace", event.isWithinBlockSpace());
            if (event.getDistribution() != null) {
                raw.remove("withinBlockSpace"); raw.addProperty("distribution", event.getDistribution()); raw.add("spread", vec(event.getSpread()));
            }
            if (event.isWithinBlockSpace() || event.getAmountPerCycle() != Math.rint(event.getAmountPerCycle()))
                raw = unsupported("Particle emitter shape/fractional amount has no lossless point-emitter conversion", raw);
            timeline.add(new Timed(event.getTick(), raw));
        }
        for (PonderScene.PoiEvent event : source.getPointOfInterestEvents()) {
            JsonObject point = step("native_point_of_interest"); point.add("point", vec(event.getLocation()));
            timeline.add(new Timed(event.getTick(), point));
        }
        JsonArray operations = new JsonArray();
        for (PonderScene.RecordedOperation operation : source.getRecordedOperations()) {
            JsonObject raw = new JsonObject(); raw.addProperty("tick", operation.getTick()); raw.addProperty("description", operation.getDescription()); operations.add(raw);
            String description = operation.getDescription();
            if ("scene.addLazyKeyframe()".equals(description)) timeline.add(new Timed(operation.getTick(), step("lazy_keyframe")));
            if (description.startsWith("world.createEntity(") || description.startsWith("world.modifyEntit") || description.startsWith("debug.addInstructionInstance("))
                timeline.add(new Timed(operation.getTick(), unsupported("Original callback has no recorded deterministic preview equivalent", raw)));
        }
        provenance.add("recordedOperations", operations);
        String assetId = "ponder_authoring:" + newId.replace(':', '/') + "/fork_initial";
        boolean hasAsset = !source.getSchematic().isEmpty();
        if (hasAsset) {
            try { StructurePool.validateSource(source.getSchematic()); segment.addProperty("structure", assetId); }
            catch (IOException invalid) {
                hasAsset = false;
                JsonObject raw = new JsonObject(); raw.addProperty("originalSchematic", source.getSchematicLocation().toString()); raw.addProperty("blocks", source.getSchematic().getBlocks().size());
                timeline.add(new Timed(0, unsupported("Initial structure cannot be copied into a bounded local asset: " + invalid.getMessage(), raw)));
            }
        }
        timeline.sort(Comparator.comparingInt(event -> event.tick));
        JsonArray steps = new JsonArray(); int current = 0, ordinal = 0;
        for (Timed event : timeline) {
            if (event.tick > current) { JsonObject idle = step("idle"); idle.addProperty("duration", event.tick - current); steps.add(idle); current = event.tick; }
            event.step.addProperty("sourceTick", event.tick); event.step.addProperty("sourceOrdinal", ordinal++); steps.add(event.step);
        }
        if (source.getTotalIdleTicks() > current) { JsonObject idle = step("idle"); idle.addProperty("duration", source.getTotalIdleTicks() - current); steps.add(idle); }
        segment.add("steps", steps); JsonArray scenes = new JsonArray(); scenes.add(segment); root.add("scenes", scenes);
        AuthoringDocument document = AuthoringDocument.fromJson(root);
        if (hasAsset) writer.write(assetId, source.getSchematic());
        return document;
    }

    private static void world(PonderScene.WorldEvent event, List<Timed> timeline) {
        if (event.getSectionOwnership() != null) {
            timeline.add(new Timed(event.getTick(), NativeSectionInstructions.export(event)));
            return;
        }
        JsonObject raw = new JsonObject(); raw.addProperty("originalType", event.getType().name()); raw.addProperty("state", event.getStateDescription());
        raw.add("positions", positions(event.getPositions())); raw.addProperty("duration", event.getDuration());
        if (event.getDirection() != null) raw.addProperty("direction", event.getDirection().name());
        if (event.getOffset() != null) raw.add("offset", vec(event.getOffset()));
        if (event.getRotation() != null) raw.add("rotation", vec(event.getRotation()));
        if (event.getPivot() != null) raw.add("pivot", vec(event.getPivot()));
        JsonObject converted = null;
        switch (event.getType()) {
            case SHOW_SECTION:
                converted = step("show_structure");
                converted.addProperty("legacyOwnership", true);
                converted.addProperty("fadeDuration", event.getDirection() == null ? 0 : event.getDuration() > 0 ? event.getDuration() : 15);
                if (event.getDirection() == null) converted.addProperty("directionless", true);
                break;
            case HIDE_SECTION:
                converted = step("hide_section");
                converted.addProperty("legacyOwnership", true);
                converted.addProperty("fadeDuration", event.getDuration() > 0 ? event.getDuration() : 15);
                if (event.getDirection() == null) converted.addProperty("directionless", true);
                break;
            case RESTORE_BLOCKS: converted = step("restore_blocks"); break;
            case MOVE_SECTION: if (event.getOffset() != null) { converted = step("move_section"); converted.add("offset", vec(event.getOffset())); converted.addProperty("duration", event.getDuration()); } break;
            case ROTATE_SECTION:
                if (event.getRotation() == null) break;
                converted = step("rotate_section"); converted.addProperty("rotX", event.getRotation().x); converted.addProperty("rotY", event.getRotation().y); converted.addProperty("rotZ", event.getRotation().z);
                converted.addProperty("duration", event.getDuration()); if (event.getPivot() != null) converted.add("pivot", vec(event.getPivot())); break;
            case APPLY_NBT:
                converted = step("modify_block_entity_nbt"); converted.addProperty("nbt", event.getStateDescription());
                converted.addProperty("nbtMode", "replace"); break;
            case SET_BLOCK: case SET_BLOCKS: case REPLACE_BLOCKS:
                String state = event.getStateDescription(); int meta = state == null ? -1 : state.lastIndexOf('#');
                String id = meta < 0 ? state : state.substring(0, meta);
                if (AuthoringDocument.validId(id)) {
                    try {
                        converted = step("replace_blocks"); converted.addProperty("block", id);
                        converted.addProperty("legacyOwnership", true);
                        if (meta >= 0) converted.addProperty("meta", Integer.parseInt(state.substring(meta + 1)));
                        if (event.getType() == PonderScene.WorldEventType.SET_BLOCK || event.getType() == PonderScene.WorldEventType.SET_BLOCKS)
                            converted.addProperty("clearBlockEntityNbt", true);
                    }
                    catch (RuntimeException invalid) { converted = null; }
                }
                break;
            case DESTROY_BLOCK: case BREAK_PROGRESS:
                for (BlockPos pos : event.getPositions()) { JsonObject one = step(event.getType() == PonderScene.WorldEventType.DESTROY_BLOCK ? "destroy_block" : "break_progress"); one.add("blockPos", vec(pos)); timeline.add(new Timed(event.getTick(), one)); }
                return;
            default: break;
        }
        if (converted == null || event.getPositions().isEmpty()) converted = unsupported("World event has no lossless supported conversion", raw);
        else { converted.add("positions", positions(event.getPositions())); if (event.getDirection() != null) converted.addProperty("direction", event.getDirection().name().toLowerCase(java.util.Locale.ROOT)); }
        timeline.add(new Timed(event.getTick(), converted));
    }

    private static void overlay(PonderScene.OverlayEvent event, List<Timed> timeline) {
        JsonObject raw = new JsonObject(); raw.addProperty("originalType", event.getType().name()); raw.addProperty("text", event.getText());
        raw.addProperty("duration", event.getDuration()); raw.addProperty("overlayId", event.getOverlayId()); raw.addProperty("parentOverlayId", event.getParentOverlayId());
        raw.add("sourcePositions", positions(event.getSourcePositions())); raw.add("targetPositions", positions(event.getTargetPositions()));
        if (event.getPointAt() != null) raw.add("point", vec(event.getPointAt()));
        if (event.isScenePoint()) raw.addProperty("pointSpace", "scene");
        if (event.getSceneBounds() != null) {
            JsonArray bounds = new JsonArray();
            bounds.add(event.getSceneBounds().minX); bounds.add(event.getSceneBounds().minY); bounds.add(event.getSceneBounds().minZ);
            bounds.add(event.getSceneBounds().maxX); bounds.add(event.getSceneBounds().maxY); bounds.add(event.getSceneBounds().maxZ);
            raw.add("bounds", bounds);
        }
        if (event.getTextureLocation() != null) raw.addProperty("texture", event.getTextureLocation().toString());
        if (event.getGuiSnapshotId() != null) raw.addProperty("snapshot", event.getGuiSnapshotId().toString());
        raw.addProperty("textureU", event.getTextureU()); raw.addProperty("textureV", event.getTextureV()); raw.addProperty("regionWidth", event.getRegionWidth()); raw.addProperty("regionHeight", event.getRegionHeight());
        raw.addProperty("displayWidth", event.getDisplayWidth()); raw.addProperty("displayHeight", event.getDisplayHeight());
        raw.addProperty("palette", event.getPalette().name()); raw.addProperty("independentY", event.getIndependentY());
        raw.addProperty("connectorVisible", event.isConnectorVisible()); raw.addProperty("placeNearTarget", event.isPlaceNearTarget()); raw.addProperty("chasing", event.isOutlineChasing());
        raw.addProperty("textureWidth", event.getTextureWidth()); raw.addProperty("textureHeight", event.getTextureHeight()); raw.addProperty("offsetX", event.getOffsetX()); raw.addProperty("offsetY", event.getOffsetY());
        raw.addProperty("framed", event.isFramed()); raw.addProperty("scaleToParent", event.isScaleToParent()); raw.addProperty("stretchTexture", event.isStretchTexture()); raw.addProperty("stretchBorder", event.getStretchBorder());
        raw.addProperty("captionX", event.getCaptionX()); raw.addProperty("captionY", event.getCaptionY()); raw.addProperty("captionOffsetX", event.getCaptionOffsetX()); raw.addProperty("captionOffsetY", event.getCaptionOffsetY());
        raw.addProperty("guiX", event.getGuiX()); raw.addProperty("guiY", event.getGuiY()); raw.addProperty("guiWidth", event.getGuiWidth()); raw.addProperty("guiHeight", event.getGuiHeight());
        if (event.getLineStart() != null) raw.add("lineStart", vec(event.getLineStart()));
        if (event.getLineEnd() != null) raw.add("lineEnd", vec(event.getLineEnd())); raw.addProperty("lineWide", event.isLineWide());
        if (event.getValueBoxCenter() != null) raw.add("valueBoxCenter", vec(event.getValueBoxCenter()));
        if (event.getValueBoxExpand() != null) raw.add("valueBoxExpand", vec(event.getValueBoxExpand()));
        raw.addProperty("controlLeftClick", event.isControlLeftClick()); raw.addProperty("controlRightClick", event.isControlRightClick()); raw.addProperty("controlScroll", event.isControlScroll());
        raw.addProperty("controlSneaking", event.isControlSneaking()); raw.addProperty("controlCtrl", event.isControlCtrl());
        if (event.getControlDirection() != null) raw.addProperty("controlDirection", event.getControlDirection().name());
        JsonObject rawItem = new JsonObject(); putItem(rawItem, event.getControlItem()); raw.add("controlItem", rawItem);
        JsonObject converted = null;
        if (event.getType() == PonderScene.OverlayEventType.TEXT && !event.getText().isEmpty() && event.getParentOverlayId() == null) {
            converted = step("text"); converted.addProperty("text", event.getText()); converted.addProperty("overlayId", event.getOverlayId());
            if (!event.getTargetPositions().isEmpty()) converted.add("positions", positions(event.getTargetPositions()));
            if (event.getPointAt() != null) converted.add("point", vec(event.getPointAt()));
            if (event.getIndependentY() >= 0) converted.addProperty("independentY", event.getIndependentY());
            converted.addProperty("placeNearTarget", event.isPlaceNearTarget()); converted.addProperty("connectorVisible", event.isConnectorVisible());
        } else if (event.getType() == PonderScene.OverlayEventType.SCENE_OUTLINE && !event.getSourcePositions().isEmpty()) {
            converted = step("highlight_section"); converted.add("positions", positions(event.getSourcePositions()));
            if (event.getOverlayId() != null) converted.addProperty("linkId", event.getOverlayId());
            converted.addProperty("chasing", event.isOutlineChasing());
        } else if (event.getType() == PonderScene.OverlayEventType.CONTROLS && event.getPointAt() != null) {
            converted = step("show_controls"); converted.add("point", vec(event.getPointAt()));
            if (event.getControlDirection() != null) converted.addProperty("direction", event.getControlDirection().name().toLowerCase(java.util.Locale.ROOT));
            converted.addProperty("leftClick", event.isControlLeftClick()); converted.addProperty("rightClick", event.isControlRightClick()); converted.addProperty("scroll", event.isControlScroll());
            converted.addProperty("whileSneaking", event.isControlSneaking()); converted.addProperty("whileCTRL", event.isControlCtrl());
            putItem(converted, event.getControlItem());
        } else if (NativeOverlayStepHandlers.supports(event, raw)) {
            converted = step("native_overlay"); converted.addProperty("nativeVersion", 1); converted.add("overlay", raw);
        }
        if (converted == null) converted = unsupported("Overlay image/custom callback/abstract geometry needs a dedicated adapter; original target and appearance metadata are retained", raw);
        else {
            if (event.isScenePoint() && !"native_overlay".equals(AuthoringDocument.string(converted,"type",""))) converted.addProperty("pointSpace","scene");
            converted.addProperty("duration", event.getDuration()); converted.addProperty("color", event.getPalette().name().toLowerCase(java.util.Locale.ROOT));
        }
        timeline.add(new Timed(event.getTick(), converted));
    }

    private static void actor(PonderScene.ActorEvent event, int actorId, List<Timed> timeline) {
        JsonObject step = step("actor_action"); step.addProperty("actorId", actorId); step.addProperty("kind", event.getActorKind().name());
        step.addProperty("action", event.getType().name()); step.addProperty("duration", event.getDuration()); step.addProperty("angle", event.getAngle());
        if (event.getLocation() != null) step.add(event.getType() == PonderScene.ActorEventType.LOOK_AT ? "lookAt" : "pos", vec(event.getLocation()));
        if (event.getOffset() != null) step.add("offset", vec(event.getOffset()));
        if (event.getRotation() != null) step.add("rotation", vec(event.getRotation()));
        if (event.getPoseName() != null) step.addProperty("pose", event.getPoseName());
        if (event.getDisplayName() != null) step.addProperty("displayName", event.getDisplayName());
        if (event.getType() == PonderScene.ActorEventType.SNEAK) step.addProperty("sneaking", event.getAngle() != 0);
        if (event.getType() == PonderScene.ActorEventType.MOVE) step.addProperty("walkAnimation", event.hasWalkAnimation());
        if (event.getType() == PonderScene.ActorEventType.MOVE && event.isEntranceMotion()) step.addProperty("entranceMotion", true);
        if (event.getType() == PonderScene.ActorEventType.NBT_PATCH) step.addProperty("nbt", event.getActorNbt().toString());
        else putItem(step, event.getItemStack());
        timeline.add(new Timed(event.getTick(), step));
    }

    private static void putItem(JsonObject target, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        target.addProperty("item", Item.REGISTRY.getNameForObject(stack.getItem()).toString()); target.addProperty("count", stack.getCount()); target.addProperty("meta", stack.getMetadata());
        if (stack.hasTagCompound()) target.addProperty("nbt", stack.getTagCompound().toString());
    }
    private static JsonObject step(String type) { JsonObject result = new JsonObject(); result.addProperty("type", type); return result; }
    private static JsonObject unsupported(String reason, JsonObject original) {
        JsonObject result = step("unsupported_builtin_event"); result.addProperty("reason", reason); result.add("sourceEvent", original); return result;
    }
    private static JsonArray positions(List<BlockPos> values) { JsonArray result = new JsonArray(); for (BlockPos pos : values) result.add(vec(pos)); return result; }
    private static JsonArray vec(BlockPos pos) { JsonArray array = new JsonArray(); array.add(pos.getX()); array.add(pos.getY()); array.add(pos.getZ()); return array; }
    private static JsonArray vec(Vec3d vec) { JsonArray array = new JsonArray(); array.add(vec.x); array.add(vec.y); array.add(vec.z); return array; }
}
