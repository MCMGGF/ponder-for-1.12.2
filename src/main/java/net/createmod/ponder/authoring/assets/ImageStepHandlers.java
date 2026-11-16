package net.createmod.ponder.authoring.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;
import net.createmod.ponder.api.scene.GuiSnapshotBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.authoring.AuthoringCompiler;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.AuthoringPointSpace;
import net.createmod.ponder.authoring.AuthoringValidation;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.ImageSnapshotProvider;
import net.minecraft.util.math.Vec3d;

/** Native image extension: one foreground overlay, no commands, external URLs, NBT expansion or world callbacks. */
public final class ImageStepHandlers {
    private ImageStepHandlers() { }
    public static void register(AuthoringCompiler compiler, ImageAssetPool pool) { compiler.registerHandler("show_image", new Handler(pool)); }
    private static final class Context {
        String firstPath;
        Map<String, ImageAssetPool.ImageInfo> images;
        IOException error;
    }
    private static final class Handler implements AuthoringCompiler.StepHandler {
        private final ImageAssetPool pool;
        private final Map<AuthoringDocument, Context> validationContexts = new WeakHashMap<>();
        Handler(ImageAssetPool pool) { if (pool == null) throw new IllegalArgumentException("Image pool is required"); this.pool = pool; }
        private synchronized Context context(AuthoringDocument document, String path) {
            Context cached = validationContexts.get(document);
            if (cached != null && !path.equals(cached.firstPath)) return cached;
            Context result = new Context(); result.firstPath = path;
            try { result.images = pool.inspectContext(document); } catch (IOException invalid) { result.error = invalid; }
            validationContexts.put(document, result); return result;
        }
        private synchronized Map<String, ImageAssetPool.ImageInfo> validatedImages(AuthoringDocument document) throws IOException {
            Context cached = validationContexts.get(document);
            if (cached != null) {
                if (cached.error != null) throw cached.error;
                return cached.images;
            }
            return pool.inspectContext(document);
        }
        @Override public void validate(AuthoringDocument document, JsonObject step, String path, AuthoringValidation.Builder issues) {
            try {
                AuthoringPointSpace.validate(step);
                Context context = context(document, path);
                if (context.error != null) throw context.error;
                if (!context.images.containsKey(ImageAssetPool.resolveReference(document, step))) throw new IOException("Image reference was not preflighted");
                AuthoringCompiler.integer(step, "duration", 80, 1, 24000);
                if (step.has("blockPos")) AuthoringCompiler.blockPos(step, "blockPos");
                if (step.has("point")) {
                    if (!step.get("point").isJsonArray() || step.getAsJsonArray("point").size() != 3)
                        throw new IOException("Image target point must contain exactly three world coordinates");
                    AuthoringCompiler.vector(step, "point", Vec3d.ZERO);
                }
            } catch (IOException | RuntimeException invalid) { issues.error("image.invalid", path, invalid.getMessage()); }
        }
        @SuppressWarnings("unchecked")
        @Override public void apply(SceneBuilder scene, SceneBuildingUtil util, JsonObject step, AuthoringCompiler.StepContext context) {
            try {
                Map<String, ImageAssetPool.ImageInfo> images = (Map<String, ImageAssetPool.ImageInfo>) context.getState().get("ponder.images.context");
                if (images == null) { images = validatedImages(context.getDocument()); context.getState().put("ponder.images.context", images); }
                String id = ImageAssetPool.resolveReference(context.getDocument(), step);
                ImageSnapshotProvider provider = new ImageSnapshotProvider(pool, images.get(id));
                GuiSnapshotBuilder gui = scene.overlay().showGuiSnapshot(provider.register(), AuthoringCompiler.integer(step, "duration", 80, 1, 24000));
                if (step.has("blockPos")) gui.pointAt(util.select().position(AuthoringCompiler.blockPos(step, "blockPos")));
                else if (step.has("point")) gui.pointAt(AuthoringCompiler.vector(step, "point", Vec3d.ZERO));
                PonderScene.OverlayEvent event = scene.getScene().getOverlayEvents().get(scene.getScene().getOverlayEvents().size() - 1);
                AuthoringPointSpace.apply(event, step);
                event.setGuiSnapshotProvider(provider);
            } catch (IOException invalid) { throw new IllegalArgumentException(invalid.getMessage(), invalid); }
        }
    }
    public static java.util.List<Integer> editableSceneIndices(AuthoringDocument document) throws IOException {
        JsonObject root = document.toJson(); java.util.List<Integer> indices = new java.util.ArrayList<>();
        if (!root.has("scenes") || !root.get("scenes").isJsonArray() || root.getAsJsonArray("scenes").size() > 64)
            throw new IOException("Image binding requires a bounded scene array");
        for (int i = 0; i < root.getAsJsonArray("scenes").size(); i++) {
            JsonElement value = root.getAsJsonArray("scenes").get(i);
            if (value.isJsonObject() && value.getAsJsonObject().has("steps") && value.getAsJsonObject().get("steps").isJsonArray()) indices.add(i);
        }
        return java.util.Collections.unmodifiableList(indices);
    }
    public static AuthoringDocument append(AuthoringDocument document, int sceneIndex, String imageId, int duration) throws IOException {
        ImageAssetPool.normalizeId(imageId);
        if (duration < 1 || duration > 24000) throw new IOException("Image duration must be 1 to 24000 ticks");
        JsonObject root = document.toJson();
        if (!root.has("scenes") || !root.get("scenes").isJsonArray() || sceneIndex < 0 || sceneIndex >= root.getAsJsonArray("scenes").size())
            throw new IOException("Choose an existing valid scene");
        JsonElement selected = root.getAsJsonArray("scenes").get(sceneIndex);
        if (!selected.isJsonObject() || !selected.getAsJsonObject().has("steps") || !selected.getAsJsonObject().get("steps").isJsonArray())
            throw new IOException("Selected scene has no editable step array");
        JsonObject step = new JsonObject(); step.addProperty("type", "show_image"); step.addProperty("image", imageId); step.addProperty("duration", duration);
        selected.getAsJsonObject().getAsJsonArray("steps").add(step); return AuthoringDocument.fromJson(root);
    }
}
