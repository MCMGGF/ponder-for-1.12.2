package net.createmod.ponder.authoring.trigger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.createmod.ponder.authoring.AuthoringCompiler;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.AuthoringValidation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.DimensionManager;

/** An explicit conversion, not a guess at a modern world-generation structure ID. */
public final class TriggerDefinition {
    public static final int MAX_RULES = 64;
    public static final int MAX_PATTERN_BLOCKS = 128;
    public enum Frequency { ALWAYS, FIRST_TIME, UNTIL_READ }

    public static final class ExpectedBlock {
        public final BlockPos position;
        public final IBlockState state;
        ExpectedBlock(BlockPos position, IBlockState state) {
            this.position = position.toImmutable(); this.state = state;
        }
    }

    private final String documentId, text;
    private final ResourceLocation componentId, runtimeSceneId;
    private final int segmentIndex, dimension, radius, cooldown;
    private final BlockPos min, max;
    private final List<ExpectedBlock> blocks;
    private final Frequency frequency;

    private TriggerDefinition(AuthoringDocument document, int segment, int dimension, int radius, int cooldown,
        BlockPos min, BlockPos max, List<ExpectedBlock> blocks, String text, Frequency frequency) {
        documentId = document.getId(); segmentIndex = segment; this.dimension = dimension;
        this.radius = radius; this.cooldown = cooldown; this.min = min; this.max = max;
        this.blocks = Collections.unmodifiableList(new ArrayList<ExpectedBlock>(blocks));
        this.text = text; this.frequency = frequency;
        componentId = new ResourceLocation(AuthoringCompiler.mappedId(document, document.getItems().get(0)));
        runtimeSceneId = document.getRuntimeSceneId(segment);
    }

    public static TriggerDefinition parse(AuthoringDocument document) {
        return parse(document, DimensionManager::isDimensionRegistered);
    }

    public static TriggerDefinition parse(AuthoringDocument document, IntPredicate knownDimension) {
        JsonObject root = document.toJson();
        String mode = string(root, "triggerMode", "none");
        if (mode.isEmpty() || "none".equals(mode)) return null;
        if (!"coordinate".equals(mode) && !"structure".equals(mode))
            throw new IllegalArgumentException("Unknown triggerMode; keep this document as a draft");
        JsonObject mapping = object(root, "legacyTrigger");
        if (integer(mapping, "version", -1, 1, 1) != 1)
            throw new IllegalArgumentException("legacyTrigger.version must be 1");
        int dimension = integer(mapping, "dimension", Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
        if (!mapping.has("dimension") || !knownDimension.test(dimension))
            throw new IllegalArgumentException("legacyTrigger.dimension must name a registered 1.12 dimension");
        if (mapping.has("autoOpen") && (!mapping.get("autoOpen").isJsonPrimitive()
            || !mapping.get("autoOpen").getAsJsonPrimitive().isBoolean() || mapping.get("autoOpen").getAsBoolean()))
            throw new IllegalArgumentException("Triggers only show hints; legacyTrigger.autoOpen must be false");
        if (hasText(root, "hintAuto") && (!mapping.has("autoOpen") || mapping.get("autoOpen").getAsBoolean()))
            throw new IllegalArgumentException("Imported hintAuto requires explicit legacyTrigger.autoOpen:false conversion");
        String adapter = string(mapping, "adapter", "");
        if (!("coordinate".equals(mode) ? "coordinate_aabb" : "block_pattern").equals(adapter))
            throw new IllegalArgumentException("legacyTrigger.adapter must explicitly match coordinate_aabb or block_pattern");
        int segment = findSegment(document, string(mapping, "scene", ""));
        int radius = integer(mapping, "radius", 16, 0, "coordinate".equals(mode) ? 32 : 64);
        int cooldown = integer(mapping, "cooldown", 200, 40, 72000);
        String text = string(mapping, "text", string(root, "hintSubtitleText", string(root, "hintTitleText", "")));
        if (text.isEmpty()) text = AuthoringCompiler.text(document.getScenes().get(segment).get("title"), documentId(document));
        if (text.length() > 512 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Trigger hint must be a single line of at most 512 characters");
        String rawFrequency = string(mapping, "frequency", sourceFrequency(root));
        Frequency frequency;
        if ("always".equals(rawFrequency)) frequency = Frequency.ALWAYS;
        else if ("first_time".equals(rawFrequency)) frequency = Frequency.FIRST_TIME;
        else if ("until_read".equals(rawFrequency)) frequency = Frequency.UNTIL_READ;
        else throw new IllegalArgumentException("Unsupported trigger frequency: " + rawFrequency);
        List<ExpectedBlock> blocks = new ArrayList<ExpectedBlock>();
        BlockPos min, max;
        if ("coordinate".equals(mode)) {
            BlockPos first = worldPos(root, "triggerCoord1"), second = worldPos(root, "triggerCoord2");
            min = minimum(first, second); max = maximum(first, second);
            if ((long) max.getX() - min.getX() > 128 || (long) max.getY() - min.getY() > 128
                || (long) max.getZ() - min.getZ() > 128)
                throw new IllegalArgumentException("Coordinate trigger bounds may span at most 128 blocks per axis");
        } else {
            String source = string(root, "triggerStructure", "");
            if (!AuthoringDocument.validId(source) || !source.equals(string(mapping, "sourceStructure", "")))
                throw new IllegalArgumentException("block_pattern must explicitly map the exact imported triggerStructure ID");
            BlockPos origin = worldPos(mapping, "origin");
            JsonArray pattern = array(mapping, "blocks");
            if (pattern.size() == 0 || pattern.size() > MAX_PATTERN_BLOCKS)
                throw new IllegalArgumentException("A trigger pattern needs 1..128 explicit block states");
            Set<BlockPos> unique = new HashSet<BlockPos>(); boolean solid = false;
            min = null; max = null;
            for (JsonElement element : pattern) {
                if (!element.isJsonObject()) throw new IllegalArgumentException("Pattern entry must be an object");
                JsonObject entry = element.getAsJsonObject();
                BlockPos offset = vector(entry, "offset", -32, 32);
                BlockPos position = origin.add(offset); checkWorldPosition(position);
                if (!unique.add(position)) throw new IllegalArgumentException("Duplicate trigger pattern position");
                IBlockState state = AuthoringCompiler.resolveBlock(document, entry);
                IBlockState stored = state.getBlock().getStateFromMeta(state.getBlock().getMetaFromState(state));
                if (!state.equals(stored))
                    throw new IllegalArgumentException("Pattern state requires unstored actual-state properties; only exact 1.12 metadata states are supported");
                solid |= state.getBlock() != Blocks.AIR;
                blocks.add(new ExpectedBlock(position, state));
                min = min == null ? position : minimum(min, position);
                max = max == null ? position : maximum(max, position);
            }
            if (!solid) throw new IllegalArgumentException("A trigger pattern must include at least one non-air block");
        }
        return new TriggerDefinition(document, segment, dimension, radius, cooldown, min, max, blocks, text, frequency);
    }

    public static void validate(AuthoringDocument document, AuthoringValidation.Builder issues) {
        JsonObject root = document.toJson();
        try {
            TriggerDefinition result = parse(document);
            if (result != null) issues.warning("trigger.converted", "/legacyTrigger",
                "Explicit singleplayer loaded-block hint conversion; no automatic opening or world-generation structure lookup; frequencies are scoped to this world session");
            else for (String key : new String[] {"hintAuto", "hintTitle", "hintSubtitle", "hintFrequency", "hintStyle"})
                if (hasText(root, key)) issues.warning("hint.metadata", "/" + key, "Hint metadata is retained without an enabled trigger");
        } catch (RuntimeException invalid) {
            issues.error("trigger.unsupported", "/legacyTrigger", invalid.getMessage());
        }
    }

    private static String documentId(AuthoringDocument document) { return document.getId(); }
    private static String sourceFrequency(JsonObject root) {
        for (String key : new String[] {"hintSubtitle", "hintTitle", "hintFrequency"})
            if (hasText(root, key)) return string(root, key, "always");
        if (root.has("onlyFirstTime")) {
            JsonElement first = root.get("onlyFirstTime");
            if (!first.isJsonPrimitive() || !first.getAsJsonPrimitive().isBoolean())
                throw new IllegalArgumentException("onlyFirstTime must be boolean");
            if (first.getAsBoolean()) return "first_time";
        }
        return "always";
    }
    private static int findSegment(AuthoringDocument document, String id) {
        if (id.isEmpty()) throw new IllegalArgumentException("legacyTrigger.scene must name an explicit segment");
        List<JsonObject> scenes = document.getScenes();
        for (int i = 0; i < scenes.size(); i++)
            if (id.equals(string(scenes.get(i), "id", "scene_" + (i + 1)))) return i;
        throw new IllegalArgumentException("Trigger segment does not exist: " + id);
    }
    private static BlockPos minimum(BlockPos a, BlockPos b) { return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())); }
    private static BlockPos maximum(BlockPos a, BlockPos b) { return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())); }
    private static BlockPos worldPos(JsonObject root, String key) {
        BlockPos value = vector(root, key, -30000000, 30000000); checkWorldPosition(value); return value;
    }
    private static void checkWorldPosition(BlockPos value) {
        if (Math.abs((long) value.getX()) > 30000000 || Math.abs((long) value.getZ()) > 30000000
            || value.getY() < 0 || value.getY() > 255) throw new IllegalArgumentException("Trigger position is outside the 1.12 world bounds");
    }
    private static BlockPos vector(JsonObject root, String key, int min, int max) {
        JsonArray values = array(root, key);
        if (values.size() != 3) throw new IllegalArgumentException(key + " must have three integer coordinates");
        int[] result = new int[3];
        for (int i = 0; i < 3; i++) {
            JsonObject value = new JsonObject(); value.add("coordinate", values.get(i));
            result[i] = integer(value, "coordinate", 0, min, max);
        }
        return new BlockPos(result[0], result[1], result[2]);
    }
    private static JsonObject object(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonObject()) throw new IllegalArgumentException(key + " requires an explicit object mapping");
        return root.getAsJsonObject(key);
    }
    private static JsonArray array(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        return root.getAsJsonArray(key);
    }
    private static String string(JsonObject root, String key, String fallback) {
        if (!root.has(key) || root.get(key).isJsonNull()) return fallback;
        JsonElement value = root.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key + " must be a string");
        return value.getAsString();
    }
    private static boolean hasText(JsonObject root, String key) { return !string(root, key, "").isEmpty(); }
    private static int integer(JsonObject root, String key, int fallback, int min, int max) {
        return AuthoringCompiler.integer(root, key, fallback, min, max);
    }
    public String getDocumentId() { return documentId; }
    public String getText() { return text; }
    public ResourceLocation getComponentId() { return componentId; }
    public ResourceLocation getRuntimeSceneId() { return runtimeSceneId; }
    public int getSegmentIndex() { return segmentIndex; }
    public int getDimension() { return dimension; }
    public int getRadius() { return radius; }
    public int getCooldown() { return cooldown; }
    public BlockPos getMin() { return min; }
    public BlockPos getMax() { return max; }
    public List<ExpectedBlock> getBlocks() { return blocks; }
    public Frequency getFrequency() { return frequency; }
}
