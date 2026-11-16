package net.createmod.ponder.authoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.content.VanillaTutorialRecipes.RecipePreview;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.Snapshot;
import net.createmod.ponder.foundation.ui.VanillaLogisticsSnapshot;
import net.createmod.ponder.foundation.ui.VanillaRecipeSnapshot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

/** Explicit native event data, never a callback, an opening packet, or a real container. */
public final class NativeOverlayStepHandlers {
    private NativeOverlayStepHandlers() { }

    public static void register(AuthoringCompiler compiler) {
        compiler.registerHandler("native_overlay", new Handler(false));
        compiler.registerHandler("native_point_of_interest", new Handler(true));
    }

    private static final class Handler implements AuthoringCompiler.StepHandler {
        private final boolean poi;
        Handler(boolean poi) { this.poi = poi; }
        @Override public void validate(AuthoringDocument document, JsonObject step, String path, AuthoringValidation.Builder issues) {
            try {
                if (poi) vector(step, "point", true);
                else {
                    validateOverlay(document, payload(step));
                    issues.warning("native.snapshot_only", path,
                        "Recorded draw-only overlay: no interactive container, recipe execution, or proof of a currently registered recipe");
                }
            } catch (RuntimeException invalid) { issues.error("native.overlay", path, invalid.getMessage()); }
        }
        @Override public void apply(SceneBuilder scene, SceneBuildingUtil util, JsonObject step, AuthoringCompiler.StepContext context) {
            if (poi) scene.getScene().recordPointOfInterest(vector(step, "point", true));
            else applyOverlay(scene.getScene(), context.getDocument(), payload(step));
        }
    }

    /** Returns null for an unknown provider/renderer without executing any of its code. */
    static JsonObject snapshotDeclaration(PonderScene.OverlayEvent event) {
        Snapshot snapshot = PonderGuiSnapshotRegistry.declaredSnapshotFor(event);
        if (snapshot == null) return null;
        JsonObject result = new JsonObject();
        result.addProperty("presentation", "recorded_static");
        result.addProperty("displayWidth", snapshot.displayWidth); result.addProperty("displayHeight", snapshot.displayHeight);
        result.addProperty("framed", snapshot.framed);
        if (snapshot.renderer == null && snapshot.texture != null) {
            result.addProperty("kind", "texture"); result.addProperty("texture", snapshot.texture.toString());
            result.addProperty("u", snapshot.u); result.addProperty("v", snapshot.v);
            result.addProperty("regionWidth", snapshot.regionWidth); result.addProperty("regionHeight", snapshot.regionHeight);
            result.addProperty("textureWidth", snapshot.textureWidth); result.addProperty("textureHeight", snapshot.textureHeight);
        } else if (snapshot.renderer instanceof VanillaRecipeSnapshot) {
            VanillaRecipeSnapshot renderer = (VanillaRecipeSnapshot) snapshot.renderer;
            RecipePreview recipe = renderer.getRecipePreview();
            result.addProperty("kind", "vanilla_recipe"); result.addProperty("furnace", renderer.isFurnace());
            result.addProperty("showResult", renderer.isShowingResult()); result.addProperty("fuelBurnTicks", recipe.getFuelBurnTicks());
            if (recipe.getUnavailableReason() != null) result.addProperty("unavailableReason", recipe.getUnavailableReason());
            JsonArray inputs = new JsonArray(); for (ItemStack input : recipe.getInputs()) inputs.add(stackData(input));
            result.add("inputs", inputs); result.add("fuel", stackData(recipe.getFuel())); result.add("output", stackData(recipe.getOutput()));
        } else if (snapshot.renderer instanceof VanillaLogisticsSnapshot) {
            result.addProperty("kind", "vanilla_logistics");
            result.addProperty("stage", ((VanillaLogisticsSnapshot) snapshot.renderer).getStage().name());
        } else return null;
        return result;
    }

    static boolean supports(PonderScene.OverlayEvent event, JsonObject raw) {
        switch (event.getType()) {
            case GUI_TEXTURE:
                if (event.getGuiSnapshotId() != null || event.getGuiSnapshotProvider() != null) {
                    JsonObject snapshot = snapshotDeclaration(event);
                    if (snapshot == null) return false;
                    raw.add("snapshotData", snapshot);
                    return true;
                }
                return event.getTextureLocation() != null;
            case GUI_HIGHLIGHT: return event.getParentOverlayId() != null;
            case SCENE_LINE: return event.getLineStart() != null && event.getLineEnd() != null;
            case SCENE_OUTLINE: return event.getSourcePositions().isEmpty() && event.getSceneBounds() != null;
            case VALUE_BOX: return event.getValueBoxCenter() != null && event.getValueBoxExpand() != null;
            case TEXT: return event.getParentOverlayId() != null && !event.getText().isEmpty();
            default: return false;
        }
    }

    private static JsonObject payload(JsonObject step) {
        if (AuthoringCompiler.integer(step, "nativeVersion", 1, 1, 1) != 1 || !step.has("overlay") || !step.get("overlay").isJsonObject())
            throw new IllegalArgumentException("native_overlay requires a version 1 overlay object");
        return step.getAsJsonObject("overlay");
    }

    private static void validateOverlay(AuthoringDocument document, JsonObject data) {
        String kind = string(data, "originalType", null);
        AuthoringCompiler.duration(data, 20);
        palette(data); text(data, "text", "", 32768); text(data, "overlayId", null, 512); text(data, "parentOverlayId", null, 512);
        vector(data, "point", false); positions(data, "sourcePositions"); positions(data, "targetPositions");
        AuthoringPointSpace.validateNative(data);
        AuthoringCompiler.integer(data, "independentY", -1, -1, 32767);
        if (AuthoringCompiler.integer(data, "independentY", -1, -1, 32767) >= 0 && data.has("point"))
            throw new IllegalArgumentException("An independent native caption cannot also target a 3D point");
        if (data.has("bounds") && !"SCENE_OUTLINE".equals(kind) || data.has("controlDirection")
            || AuthoringCompiler.bool(data, "chasing", false) && !"SCENE_OUTLINE".equals(kind))
            throw new IllegalArgumentException("This native overlay contains unsupported outline/control metadata");
        for (String key : new String[] {"controlLeftClick", "controlRightClick", "controlScroll", "controlSneaking", "controlCtrl"})
            if (AuthoringCompiler.bool(data, key, false)) throw new IllegalArgumentException("Control actions require the controls adapter");
        if (data.has("controlItem") && (!data.get("controlItem").isJsonObject() || !data.getAsJsonObject("controlItem").entrySet().isEmpty()))
            throw new IllegalArgumentException("Control items require the controls adapter");
        for (String key : new String[] {"connectorVisible", "placeNearTarget", "framed", "scaleToParent", "stretchTexture", "lineWide"})
            AuthoringCompiler.bool(data, key, false);
        for (String key : new String[] {"offsetX", "offsetY", "captionOffsetX", "captionOffsetY", "guiX", "guiY"})
            AuthoringCompiler.integer(data, key, 0, -32768, 32767);
        for (String key : new String[] {"captionX", "captionY"})
            if (data.has(key) && AuthoringCompiler.number(data, key, Integer.MIN_VALUE) != Integer.MIN_VALUE)
                AuthoringCompiler.integer(data, key, 0, -32768, 32767);
        AuthoringCompiler.integer(data, "stretchBorder", 4, 1, 4096);
        if ("GUI_TEXTURE".equals(kind)) {
            if (data.has("texture")) {
                texture(data, "texture"); textureGeometry(data, "textureU", "textureV");
            }
            if (data.has("snapshotData")) snapshot(document, object(data, "snapshotData"));
            else if (!data.has("texture")) throw new IllegalArgumentException("Native GUI texture has no declarative texture or known snapshot");
            if (data.has("snapshot")) resource(data, "snapshot");
        } else if ("GUI_HIGHLIGHT".equals(kind)) {
            requiredText(data, "parentOverlayId", 512);
            positive(data, "guiWidth"); positive(data, "guiHeight");
        } else if ("SCENE_LINE".equals(kind)) {
            vector(data, "lineStart", true); vector(data, "lineEnd", true);
        } else if ("SCENE_OUTLINE".equals(kind)) {
            bounds(data);
            if (!positions(data, "sourcePositions").isEmpty()) throw new IllegalArgumentException("A static AABB must not invent a block selection");
        } else if ("VALUE_BOX".equals(kind)) {
            vector(data, "valueBoxCenter", true); Vec3d expand = vector(data, "valueBoxExpand", true);
            if (expand.x < 0 || expand.y < 0 || expand.z < 0 || expand.x > 256 || expand.y > 256 || expand.z > 256)
                throw new IllegalArgumentException("Value-box expansion must be within 0..256");
        } else if ("TEXT".equals(kind)) {
            requiredText(data, "parentOverlayId", 512); requiredText(data, "text", 32768);
        } else throw new IllegalArgumentException("Unsupported native overlay kind: " + kind);
    }

    private static void applyOverlay(PonderScene scene, AuthoringDocument document, JsonObject data) {
        validateOverlay(document, data);
        String kind = string(data, "originalType", null);
        PonderScene.OverlayEvent event = scene.createOverlayEvent(AuthoringCompiler.duration(data, 20));
        if ("GUI_TEXTURE".equals(kind)) {
            if (data.has("texture")) event.guiTexture(resource(data, "texture"), integer(data, "textureU", 0), integer(data, "textureV", 0),
                positive(data, "regionWidth"), positive(data, "regionHeight"), positive(data, "textureWidth"), positive(data, "textureHeight"),
                positive(data, "displayWidth"), positive(data, "displayHeight"), integer(data, "offsetX", 0), integer(data, "offsetY", 0),
                AuthoringCompiler.bool(data, "framed", true));
            if (data.has("snapshotData")) {
                event.guiSnapshot(data.has("snapshot") ? resource(data, "snapshot") : null);
                event.setGuiSnapshotProvider(PonderGuiSnapshotRegistry.declaredProvider(snapshot(document, object(data, "snapshotData"))));
            }
        } else if ("GUI_HIGHLIGHT".equals(kind)) {
            event.guiHighlight(string(data, "parentOverlayId", null), integer(data, "guiX", 0), integer(data, "guiY", 0),
                positive(data, "guiWidth"), positive(data, "guiHeight"));
        } else if ("SCENE_LINE".equals(kind)) event.sceneLine(vector(data, "lineStart", true), vector(data, "lineEnd", true), AuthoringCompiler.bool(data, "lineWide", false));
        else if ("SCENE_OUTLINE".equals(kind)) event.sceneOutline(bounds(data));
        else if ("VALUE_BOX".equals(kind)) event.valueBox(vector(data, "valueBoxCenter", true), vector(data, "valueBoxExpand", true));
        event.text(string(data, "text", "")).palette(palette(data));
        if ("SCENE_OUTLINE".equals(kind)) event.outlineChasing(AuthoringCompiler.bool(data, "chasing", false));
        if (data.has("point")) event.pointAt(vector(data, "point", true));
        AuthoringPointSpace.apply(event, data);
        int independentY = AuthoringCompiler.integer(data, "independentY", -1, -1, 32767);
        if (independentY >= 0) event.independent(independentY);
        event.sourcePositions(positions(data, "sourcePositions")).targetPositions(positions(data, "targetPositions"));
        event.overlayId(string(data, "overlayId", null)).parentOverlayId(string(data, "parentOverlayId", null))
            .placeNearTarget(AuthoringCompiler.bool(data, "placeNearTarget", false)).connectorVisible(AuthoringCompiler.bool(data, "connectorVisible", true))
            .scaleToParent(AuthoringCompiler.bool(data, "scaleToParent", false))
            .stretchTexture(AuthoringCompiler.bool(data, "stretchTexture", false), AuthoringCompiler.integer(data, "stretchBorder", 4, 1, 4096))
            .offset(integer(data, "offsetX", 0), integer(data, "offsetY", 0)).guiOrigin(integer(data, "guiX", 0), integer(data, "guiY", 0))
            .captionPosition((int) AuthoringCompiler.number(data, "captionX", Integer.MIN_VALUE), (int) AuthoringCompiler.number(data, "captionY", Integer.MIN_VALUE))
            .captionOffset(integer(data, "captionOffsetX", 0), integer(data, "captionOffsetY", 0));
    }

    private static Snapshot snapshot(AuthoringDocument document, JsonObject data) {
        if (!"recorded_static".equals(string(data, "presentation", null)))
            throw new IllegalArgumentException("Native snapshot data must explicitly declare recorded_static presentation");
        String kind = string(data, "kind", null);
        if ("texture".equals(kind)) {
            texture(data, "texture"); textureGeometry(data, "u", "v");
            return new Snapshot(resource(data, "texture"), integer(data, "u", 0), integer(data, "v", 0), positive(data, "regionWidth"),
                positive(data, "regionHeight"), positive(data, "textureWidth"), positive(data, "textureHeight"), positive(data, "displayWidth"),
                positive(data, "displayHeight"), AuthoringCompiler.bool(data, "framed", true), null);
        }
        if (positive(data, "displayWidth") != 176 || positive(data, "displayHeight") != 76 || AuthoringCompiler.bool(data, "framed", false))
            throw new IllegalArgumentException("Known vanilla slot snapshots require their recorded 176x76 unframed dimensions");
        if ("vanilla_logistics".equals(kind)) {
            VanillaLogisticsSnapshot.Stage stage = VanillaLogisticsSnapshot.Stage.valueOf(requiredText(data, "stage", 64));
            return VanillaLogisticsSnapshot.declaredSnapshot(stage);
        }
        if (!"vanilla_recipe".equals(kind)) throw new IllegalArgumentException("Unknown declarative snapshot kind: " + kind);
        if (!data.has("inputs") || !data.get("inputs").isJsonArray() || data.getAsJsonArray("inputs").size() > 9)
            throw new IllegalArgumentException("Recipe snapshot requires at most nine input slots");
        List<ItemStack> inputs = new ArrayList<>();
        for (JsonElement item : data.getAsJsonArray("inputs")) {
            if (!item.isJsonObject()) throw new IllegalArgumentException("Recipe input must be an item object");
            inputs.add(stack(document, item.getAsJsonObject()));
        }
        RecipePreview recipe = RecipePreview.fromSnapshot(inputs, stack(document, object(data, "fuel")), stack(document, object(data, "output")),
            text(data, "unavailableReason", null, 4096), AuthoringCompiler.integer(data, "fuelBurnTicks", 0, 0, Integer.MAX_VALUE));
        return VanillaRecipeSnapshot.declaredSnapshot(recipe, AuthoringCompiler.bool(data, "furnace", false), AuthoringCompiler.bool(data, "showResult", false));
    }

    private static JsonObject stackData(ItemStack stack) {
        JsonObject result = new JsonObject();
        if (stack == null || stack.isEmpty()) return result;
        result.addProperty("item", Item.REGISTRY.getNameForObject(stack.getItem()).toString()); result.addProperty("count", stack.getCount());
        result.addProperty("meta", stack.getMetadata()); if (stack.hasTagCompound()) result.addProperty("nbt", stack.getTagCompound().toString());
        return result;
    }

    private static ItemStack stack(AuthoringDocument document, JsonObject data) {
        if (data.entrySet().isEmpty()) return ItemStack.EMPTY;
        for (java.util.Map.Entry<String, JsonElement> field : data.entrySet())
            if (!"item".equals(field.getKey()) && !"count".equals(field.getKey()) && !"meta".equals(field.getKey()) && !"nbt".equals(field.getKey()))
                throw new IllegalArgumentException("Unknown snapshot item field: " + field.getKey());
        ItemStack stack = AuthoringCompiler.resolveItem(document, requiredText(data, "item", 256), AuthoringCompiler.integer(data, "count", 1, 1, 64),
            data.has("nbt") ? AuthoringCompiler.nbt(AuthoringCompiler.requiredString(data, "nbt")) : null);
        if (data.has("meta")) stack.setItemDamage(AuthoringCompiler.integer(data, "meta", 0, 0, 32767));
        return stack;
    }

    private static void textureGeometry(JsonObject data, String u, String v) {
        int x = AuthoringCompiler.integer(data, u, 0, 0, 4096), y = AuthoringCompiler.integer(data, v, 0, 0, 4096);
        int width = positive(data, "regionWidth"), height = positive(data, "regionHeight");
        if ((long) x + width > positive(data, "textureWidth") || (long) y + height > positive(data, "textureHeight"))
            throw new IllegalArgumentException("Texture region exceeds its declared atlas");
        positive(data, "displayWidth"); positive(data, "displayHeight");
    }
    private static void texture(JsonObject data, String key) {
        ResourceLocation id = resource(data, key);
        if (!id.getPath().startsWith("textures/") || !id.getPath().endsWith(".png"))
            throw new IllegalArgumentException("Native textures require an explicit textures/*.png resource");
    }
    private static ResourceLocation resource(JsonObject data, String key) {
        String value = requiredText(data, key, 256);
        if (!AuthoringDocument.validId(value)) throw new IllegalArgumentException("Invalid native resource: " + value);
        return new ResourceLocation(value);
    }
    private static Vec3d vector(JsonObject data, String key, boolean required) {
        if (!data.has(key)) {
            if (required) throw new IllegalArgumentException(key + " requires a position");
            return null;
        }
        return AuthoringCompiler.vector(data, key, Vec3d.ZERO);
    }
    private static List<BlockPos> positions(JsonObject data, String key) {
        if (!data.has(key)) return java.util.Collections.emptyList();
        if (data.get(key).isJsonArray() && data.getAsJsonArray(key).size() == 0) return java.util.Collections.emptyList();
        JsonObject positions = new JsonObject(); positions.add("positions", data.get(key)); return AuthoringCompiler.positions(positions);
    }
    private static AxisAlignedBB bounds(JsonObject data) {
        if (!data.has("bounds") || !data.get("bounds").isJsonArray() || data.getAsJsonArray("bounds").size() != 6)
            throw new IllegalArgumentException("Static scene bounds require six numeric endpoints");
        double[] values = new double[6];
        for (int index = 0; index < 6; index++) {
            JsonElement value = data.getAsJsonArray("bounds").get(index);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !Double.isFinite(value.getAsDouble())
                || Math.abs(value.getAsDouble()) > 30000000D) throw new IllegalArgumentException("Scene bounds must be finite coordinates within +/-30000000");
            values[index] = value.getAsDouble();
        }
        double volume = 1;
        for (int axis = 0; axis < 3; axis++) {
            double length = values[axis + 3] - values[axis];
            if (length < 0 || length > 256) throw new IllegalArgumentException("Scene bound axes must be ordered and no longer than 256");
            volume *= length;
        }
        if (volume > 262144) throw new IllegalArgumentException("Scene bound volume exceeds 262144");
        return new AxisAlignedBB(values[0], values[1], values[2], values[3], values[4], values[5]);
    }
    private static PonderPalette palette(JsonObject data) {
        return PonderPalette.valueOf(string(data, "palette", "white").toUpperCase(Locale.ROOT));
    }
    private static int integer(JsonObject data, String key, int fallback) { return AuthoringCompiler.integer(data, key, fallback, -32768, 32767); }
    private static int positive(JsonObject data, String key) { return AuthoringCompiler.integer(data, key, 0, 1, 4096); }
    private static JsonObject object(JsonObject data, String key) {
        if (!data.has(key) || !data.get(key).isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        return data.getAsJsonObject(key);
    }
    private static String requiredText(JsonObject data, String key, int limit) {
        String value = text(data, key, null, limit);
        if (value == null || value.isEmpty()) throw new IllegalArgumentException(key + " is required");
        return value;
    }
    private static String text(JsonObject data, String key, String fallback, int limit) {
        String value = string(data, key, fallback);
        if (value != null && value.length() > limit) throw new IllegalArgumentException(key + " exceeds its text budget");
        return value;
    }
    private static String string(JsonObject data, String key, String fallback) {
        if (!data.has(key) || data.get(key).isJsonNull()) return fallback;
        if (!data.get(key).isJsonPrimitive() || !data.getAsJsonPrimitive(key).isString()) throw new IllegalArgumentException(key + " must be a string");
        return data.get(key).getAsString();
    }
}
