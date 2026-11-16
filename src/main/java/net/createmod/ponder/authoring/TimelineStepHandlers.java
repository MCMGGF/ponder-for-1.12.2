package net.createmod.ponder.authoring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import com.google.gson.JsonObject;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.ActorEvent;
import net.createmod.ponder.foundation.PonderScene.ActorEventType;
import net.createmod.ponder.foundation.PonderScene.ActorKind;
import net.createmod.ponder.foundation.PreviewActorTimeline;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Converts declarative cues and bounded actor NBT into immutable preview events. */
public final class TimelineStepHandlers {
    private static final String OUTPUT_BUDGET = "ponder.actorNbtOutputBytes";
    private TimelineStepHandlers() { }

    public static AuthoringCompiler register(AuthoringCompiler compiler) {
        compiler.registerHandler("play_sound", new Handler("play_sound"));
        compiler.registerHandler("modify_entities_nbt", new Handler("modify_entities_nbt"));
        compiler.registerHandler("modify_item_entities_nbt", new Handler("modify_item_entities_nbt"));
        return compiler;
    }

    private static final class Handler implements AuthoringCompiler.StepHandler {
        final String type;
        Handler(String type) { this.type = type; }
        @Override public void validate(AuthoringDocument document, JsonObject step, String path, AuthoringValidation.Builder issues) {
            try {
                if ("play_sound".equals(type)) validateSound(document, step);
                else {
                    boolean item = "modify_item_entities_nbt".equals(type);
                    if (item && !step.has("nbt")) throw new IllegalArgumentException("Item NBT patch is required");
                    ActorNbtAdapter.validate(document, step, item);
                    Vec3d offset = AuthoringCompiler.vector(step, "offset", Vec3d.ZERO);
                    if (!item && !step.has("nbt") && offset.lengthSquared() == 0)
                        throw new IllegalArgumentException("Entity NBT or a non-zero offset is required");
                    if (item && step.has("offset")) throw new IllegalArgumentException("Item movement uses explicit NBT Motion/Pos, not the non-item offset field");
                    validateSelection(step); filter(document, step, item);
                    AuthoringCompiler.duration(step, 20); AuthoringCompiler.bool(step, "walkAnimation", false);
                    if (offset.lengthSquared() != 0 && ActorNbtAdapter.read(step).hasKey("Motion"))
                        throw new IllegalArgumentException("Use either offset or NBT Motion, not both");
                }
            } catch (IllegalArgumentException invalid) { issues.error("timeline.step", path, invalid.getMessage()); }
        }
        @Override public void apply(SceneBuilder scene, SceneBuildingUtil util, JsonObject step, AuthoringCompiler.StepContext context) {
            if ("play_sound".equals(type)) {
                scene.getScene().recordSound(sound(context.getDocument(), step), category(step),
                    (float)AuthoringCompiler.number(step, "soundVolume", 1), (float)AuthoringCompiler.number(step, "pitch", 1));
                return;
            }
            boolean item = "modify_item_entities_nbt".equals(type);
            PonderScene target = scene.getScene();
            String filter = filter(context.getDocument(), step, item);
            String link = optionalString(step, "linkId", "").trim();
            if (!link.isEmpty() && !context.getActorLinkIds().containsValue(link))
                throw new IllegalArgumentException("Unknown preview actor linkId: " + link);
            List<Pending> pending = new ArrayList<Pending>();
            Set<BlockPos> positions = step.has("positions") ? new HashSet<BlockPos>(AuthoringCompiler.positions(step)) : null;
            long bytes = 0;
            for (PreviewActorTimeline.State actor : PreviewActorTimeline.evaluate(target.getActorEvents(), target.getTotalIdleTicks())) {
                if (!actor.alive || item != (actor.kind == ActorKind.ITEM)) continue;
                if (!link.isEmpty() && !link.equals(context.getActorLinkIds().get(Integer.valueOf(actor.actorId)))) continue;
                if (!filter.isEmpty() && !filter.equals(item ? itemId(actor.getItemStack()) : entityId(actor.kind))) continue;
                if (!(link.isEmpty() && AuthoringCompiler.bool(step, "fullScene", false)) && !inside(step, actor.position, positions)) continue;
                if (AuthoringCompiler.bool(step, "walkAnimation", false) && actor.kind != ActorKind.PLAYER)
                    throw new IllegalArgumentException("walkAnimation requires the player preview model adapter");
                ActorNbtAdapter.Patch patch = ActorNbtAdapter.apply(context.getDocument(), step, item, actor.getItemStack());
                Vec3d offset = AuthoringCompiler.vector(step, "offset", Vec3d.ZERO);
                int duration = AuthoringCompiler.duration(step, 20);
                if (patch.motion.lengthSquared() != 0) {
                    if (duration == 0) throw new IllegalArgumentException("A preview Motion requires a positive bounded duration");
                    offset = patch.motion.scale(duration);
                }
                NBTTagCompound data = patch.getData();
                rejectActiveTransform(target, actor.actorId, data.hasKey("Pos") || offset.lengthSquared() != 0, data.hasKey("Rotation"));
                Vec3d start = data.hasKey("Pos", 9) ? PreviewActorTimeline.vector(data.getTagList("Pos", 6)) : actor.position;
                checkPosition(start.add(offset));
                bytes = AuthoringNbtBudget.checkedAdd(bytes, AuthoringNbtBudget.encodedBytes(data));
                if (item) {
                    NBTTagCompound full = new NBTTagCompound(); patch.getItem().writeToNBT(full);
                    bytes = AuthoringNbtBudget.checkedAdd(bytes, AuthoringNbtBudget.encodedBytes(full));
                }
                pending.add(new Pending(actor.actorId, actor.kind, data, patch.getItem(), offset, duration,
                    AuthoringCompiler.bool(step, "walkAnimation", false)));
            }
            long previous = context.getState().containsKey(OUTPUT_BUDGET) ? (Long)context.getState().get(OUTPUT_BUDGET) : 0;
            long total = AuthoringNbtBudget.checkedAdd(previous, bytes);
            context.getState().put(OUTPUT_BUDGET, Long.valueOf(total));
            for (Pending event : pending) event.record(target);
        }
    }

    public static void validateSpawn(AuthoringDocument document, JsonObject step, boolean item) {
        ActorNbtAdapter.validate(document, step, item);
        Vec3d motion = AuthoringCompiler.vector(step, "motion", Vec3d.ZERO);
        NBTTagCompound nbt = ActorNbtAdapter.read(step);
        if (motion.lengthSquared() != 0 && nbt.hasKey("Motion")) throw new IllegalArgumentException("Use either motion or NBT Motion at spawn");
        AuthoringCompiler.duration(step, 20);
        Entrance entrance = entrance(step, item);
        AuthoringCompiler.bool(step, "walkAnimation", false);
        if (AuthoringCompiler.bool(step, "walkAnimation", false) && (item
            || !"minecraft:player".equals(AuthoringCompiler.mappedId(document, AuthoringCompiler.requiredString(step, "entity")))))
            throw new IllegalArgumentException("walkAnimation requires the player preview model adapter");
        if (entrance.active()) {
            if (motion.lengthSquared() != 0 || nbt.hasKey("Motion") || nbt.hasKey("Pos"))
                throw new IllegalArgumentException("Entity entrance cannot overlap motion or NBT Pos/Motion; use separate preview steps");
            if (AuthoringCompiler.bool(step, "walkAnimation", false))
                throw new IllegalArgumentException("Entity entrance is a non-walking glide; use a later MOVE for walk animation");
            Vec3d target = AuthoringCompiler.vector(step, "pos", AuthoringCompiler.vector(step, "point", Vec3d.ZERO));
            checkPosition(target.add(entrance.offset));
        }
        double pitch = AuthoringCompiler.number(step, "pitch", 0);
        if (Math.abs(pitch) > 90) throw new IllegalArgumentException("Spawn pitch must be between -90 and 90 degrees");
        if ((step.has("pitch") || step.has("yaw")) && nbt.hasKey("Rotation")) throw new IllegalArgumentException("Use either yaw/pitch or NBT Rotation at spawn");
        if (item) spawnItem(document, step);
    }

    public static ItemStack spawnItem(AuthoringDocument document, JsonObject step) {
        ItemStack stack = AuthoringCompiler.resolveItem(document, AuthoringCompiler.requiredString(step, "item"),
            AuthoringCompiler.integer(step, "count", 1, 1, 64), null);
        stack.setItemDamage(AuthoringCompiler.integer(step, "meta", 0, 0, 32767));
        return ActorNbtAdapter.apply(document, step, true, stack).getItem();
    }

    public static void applySpawn(PonderScene scene, AuthoringDocument document, JsonObject step, int actorId, boolean item) {
        PreviewActorTimeline.State actor = null;
        for (PreviewActorTimeline.State candidate : PreviewActorTimeline.evaluate(scene.getActorEvents(), scene.getTotalIdleTicks()))
            if (candidate.actorId == actorId) actor = candidate;
        if (actor == null) throw new IllegalArgumentException("Spawned preview actor is missing");
        // spawnItem already materialized the item tag; only entity transform fields are needed now.
        ActorNbtAdapter.Patch patch = ActorNbtAdapter.apply(document, step, item, actor.getItemStack());
        NBTTagCompound data = patch.getData();
        if (item) data.removeTag("Item");
        if (step.has("pitch") || step.has("yaw")) {
            NBTTagList rotation = new NBTTagList();
            rotation.appendTag(new NBTTagFloat((float)AuthoringCompiler.number(step, "yaw", 0)));
            rotation.appendTag(new NBTTagFloat((float)AuthoringCompiler.number(step, "pitch", 0)));
            data.setTag("Rotation", rotation);
        }
        Entrance entrance = entrance(step, item);
        if (entrance.active()) {
            Vec3d start = actor.position.add(entrance.offset);
            checkPosition(start);
            NBTTagList position = new NBTTagList();
            position.appendTag(new NBTTagDouble(start.x)); position.appendTag(new NBTTagDouble(start.y));
            position.appendTag(new NBTTagDouble(start.z)); data.setTag("Pos", position);
            scene.recordActorNbtPatch(actorId, actor.kind, data);
            scene.recordActorEntranceMove(actorId, actor.kind, entrance.offset.scale(-1D), entrance.duration);
            return;
        }
        int duration = AuthoringCompiler.duration(step, 20);
        Vec3d motion = AuthoringCompiler.vector(step, "motion", Vec3d.ZERO);
        if (motion.lengthSquared() == 0) motion = patch.motion;
        if (motion.lengthSquared() != 0 && duration == 0) throw new IllegalArgumentException("A preview Motion requires a positive bounded duration");
        Vec3d offset = motion.scale(duration);
        Vec3d start = data.hasKey("Pos", 9) ? PreviewActorTimeline.vector(data.getTagList("Pos", 6)) : actor.position;
        checkPosition(start.add(offset));
        if (!data.isEmpty()) scene.recordActorNbtPatch(actorId, actor.kind, data);
        if (offset.lengthSquared() != 0) scene.recordActorMove(actorId, actor.kind, offset, duration,
            AuthoringCompiler.bool(step, "walkAnimation", false));
    }

    private static Entrance entrance(JsonObject step, boolean item) {
        String raw = !step.has("entranceAnimation") || step.get("entranceAnimation").isJsonNull()
            ? "none" : optionalString(step, "entranceAnimation", "none");
        String animation = ExtraStructurePlanning.normalizeEntrance(raw);
        int duration = AuthoringCompiler.integer(step, "entranceDuration", 20, 0, 72000);
        if (item && !"none".equals(animation))
            throw new IllegalArgumentException("Upstream item creation has no entrance animation; use an explicit native MOVE");
        if ("none".equals(animation) || duration == 0) return new Entrance(Vec3d.ZERO, 0);
        String direction = "simultaneous".equals(animation) ? optionalString(step, "direction", "down") : animation;
        EnumFacing facing;
        try { facing = EnumFacing.valueOf(direction.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Unknown entity entrance direction: " + direction); }
        return new Entrance(new Vec3d(facing.getDirectionVec()).scale(-0.5D), duration);
    }

    private static final class Entrance {
        final Vec3d offset;
        final int duration;
        Entrance(Vec3d offset, int duration) { this.offset = offset; this.duration = duration; }
        boolean active() { return duration > 0; }
    }

    public static void clearActors(PonderScene scene, boolean itemsOnly) {
        Map<Integer, ActorKind> actors = new LinkedHashMap<Integer, ActorKind>();
        for (ActorEvent event : scene.getActorEvents()) if (event.getType() == ActorEventType.SPAWN
            && (!itemsOnly || event.getActorKind() == ActorKind.ITEM)) actors.put(Integer.valueOf(event.getActorId()), event.getActorKind());
        for (Map.Entry<Integer, ActorKind> actor : actors.entrySet()) scene.recordActorHide(actor.getKey().intValue(), actor.getValue(), 10);
    }

    private static void validateSound(AuthoringDocument document, JsonObject step) {
        sound(document, step); category(step);
        double volume = AuthoringCompiler.number(step, "soundVolume", 1), pitch = AuthoringCompiler.number(step, "pitch", 1);
        if (volume < 0 || volume > 4 || pitch < 0.01 || pitch > 4)
            throw new IllegalArgumentException("Preview sound volume must be 0..4 and pitch 0.01..4");
    }
    private static ResourceLocation sound(AuthoringDocument document, JsonObject step) {
        String mapped = AuthoringCompiler.mappedId(document, AuthoringCompiler.requiredString(step, "sound"));
        ResourceLocation id = new ResourceLocation(mapped);
        if (!SoundEvent.REGISTRY.containsKey(id)) throw new IllegalArgumentException("Unknown 1.12 sound; add an explicit mapping: " + mapped);
        return id;
    }
    private static SoundCategory category(JsonObject step) {
        String raw = optionalString(step, "source", "master").toLowerCase(Locale.ROOT);
        switch (raw) {
            case "master": return SoundCategory.MASTER;
            case "music": return SoundCategory.MUSIC;
            case "record": case "records": return SoundCategory.RECORDS;
            case "weather": return SoundCategory.WEATHER;
            case "block": case "blocks": return SoundCategory.BLOCKS;
            case "hostile": return SoundCategory.HOSTILE;
            case "neutral": return SoundCategory.NEUTRAL;
            case "player": case "players": return SoundCategory.PLAYERS;
            case "ambient": return SoundCategory.AMBIENT;
            case "voice": return SoundCategory.VOICE;
            default: throw new IllegalArgumentException("Unknown sound category: " + raw);
        }
    }
    private static void validateSelection(JsonObject step) {
        optionalString(step, "linkId", "");
        if (step.has("positions")) AuthoringCompiler.positions(step);
        if (step.has("blockPos")) {
            AuthoringCompiler.blockPos(step, "blockPos");
            if (step.has("blockPos2")) AuthoringCompiler.blockPos(step, "blockPos2");
        }
        if (step.has("blockPos2") && !step.has("blockPos")) throw new IllegalArgumentException("blockPos2 requires blockPos");
        if (!AuthoringCompiler.bool(step, "fullScene", false) && !step.has("blockPos") && !step.has("positions")
            && optionalString(step, "linkId", "").trim().isEmpty())
            throw new IllegalArgumentException("Actor patch requires fullScene, a selection, or linkId");
    }
    private static String filter(AuthoringDocument document, JsonObject step, boolean item) {
        String raw = optionalString(step, item ? "item" : "entity", "").trim();
        if (raw.isEmpty()) return "";
        String mapped = AuthoringCompiler.mappedId(document, raw);
        if (item) {
            if (!Item.REGISTRY.containsKey(new ResourceLocation(mapped))) throw new IllegalArgumentException("Unknown item filter: " + mapped);
        } else if (!"minecraft:player".equals(mapped) && !"minecraft:parrot".equals(mapped) && !"minecraft:minecart".equals(mapped))
            throw new IllegalArgumentException("No safe preview actor for entity filter " + mapped);
        return mapped;
    }
    private static boolean inside(JsonObject step, Vec3d position, Set<BlockPos> positions) {
        BlockPos pos = new BlockPos(position);
        if (positions != null) return positions.contains(pos);
        if (!step.has("blockPos")) return true;
        BlockPos a = AuthoringCompiler.blockPos(step, "blockPos"), b = step.has("blockPos2") ? AuthoringCompiler.blockPos(step, "blockPos2") : a;
        return pos.getX() >= Math.min(a.getX(), b.getX()) && pos.getX() <= Math.max(a.getX(), b.getX())
            && pos.getY() >= Math.min(a.getY(), b.getY()) && pos.getY() <= Math.max(a.getY(), b.getY())
            && pos.getZ() >= Math.min(a.getZ(), b.getZ()) && pos.getZ() <= Math.max(a.getZ(), b.getZ());
    }
    private static String entityId(ActorKind kind) {
        return kind == ActorKind.PLAYER ? "minecraft:player" : kind == ActorKind.BIRB ? "minecraft:parrot" : "minecraft:minecart";
    }
    private static String optionalString(JsonObject step, String key, String fallback) {
        if (!step.has(key)) return fallback;
        if (!step.get(key).isJsonPrimitive() || !step.get(key).getAsJsonPrimitive().isString())
            throw new IllegalArgumentException(key + " must be a string");
        return step.get(key).getAsString();
    }
    private static String itemId(ItemStack item) {
        return item.isEmpty() ? "minecraft:air" : String.valueOf(Item.REGISTRY.getNameForObject(item.getItem()));
    }
    private static void rejectActiveTransform(PonderScene scene, int actor, boolean position, boolean rotation) {
        int now = scene.getTotalIdleTicks();
        for (ActorEvent event : scene.getActorEvents()) if (event.getActorId() == actor
            && event.getTick() <= now
            && (long)event.getTick() + event.getDuration() - (event.isEntranceMotion() ? 0 : 1) > now
            && (position && event.getType() == ActorEventType.MOVE
                || rotation && (event.getType() == ActorEventType.ROTATE || event.getType() == ActorEventType.LOOK_AT)))
            throw new IllegalArgumentException("Overlapping actor transforms require separate keyframes before an NBT patch/move");
    }
    private static void checkPosition(Vec3d position) {
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
            || Math.abs(position.x) > 30000000 || Math.abs(position.y) > 30000000 || Math.abs(position.z) > 30000000)
            throw new IllegalArgumentException("Actor movement exceeds the finite 30000000 coordinate budget");
    }
    private static final class Pending {
        final int id, duration; final ActorKind kind; final NBTTagCompound data;
        final ItemStack item; final Vec3d offset; final boolean walk;
        Pending(int id, ActorKind kind, NBTTagCompound data, ItemStack item, Vec3d offset, int duration, boolean walk) {
            this.id=id; this.kind=kind; this.data=data.copy(); this.item=item.copy(); this.offset=offset; this.duration=duration; this.walk=walk;
        }
        void record(PonderScene scene) {
            if (!data.isEmpty()) scene.recordActorNbtPatch(id, kind, data, item);
            if (offset.lengthSquared() != 0) scene.recordActorMove(id, kind, offset, duration, walk);
        }
    }
}
