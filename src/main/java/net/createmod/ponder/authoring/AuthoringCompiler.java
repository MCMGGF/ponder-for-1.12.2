package net.createmod.ponder.authoring;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.AnimatedSceneElement;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.InputElementBuilder;
import net.createmod.ponder.api.element.MinecartElement;
import net.createmod.ponder.api.element.ParrotElement;
import net.createmod.ponder.api.element.ParrotPose;
import net.createmod.ponder.api.element.PlayerElement;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSceneBuilder;
import net.createmod.ponder.foundation.PonderSchematic;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Offline compilation only: no client screens, live world callbacks, packets or registry mutation. */
public final class AuthoringCompiler {
    public interface AssetResolver { PonderSchematic resolve(String assetId) throws IOException; }
    public interface BudgetedAssetResolver extends AssetResolver {
        PonderSchematic resolve(String assetId, PonderSchematic.DecodeBudget budget) throws IOException;
    }
    public interface StepHandler {
        void validate(AuthoringDocument document, JsonObject step, String path, AuthoringValidation.Builder issues);
        void apply(SceneBuilder scene, SceneBuildingUtil util, JsonObject step, StepContext context);
    }

    public static final class CompilationException extends IllegalArgumentException {
        private final AuthoringValidation validation;
        CompilationException(AuthoringValidation validation) {
            super(validation.getIssues().toString()); this.validation = validation;
        }
        public AuthoringValidation getValidation() { return validation; }
    }

    public static final class StepContext {
        private final AuthoringDocument document;
        private final JsonObject segment;
        private final AssetResolver resolver;
        private final Map<String, Object> state = new LinkedHashMap<String, Object>();
        private final AuthoringSectionOwnership ownership;
        private final Map<Integer, String> actorLinkIds = new LinkedHashMap<Integer, String>();
        private final Set<BlockPos> knownPositions = new java.util.LinkedHashSet<BlockPos>();
        private int worldEventCursor;
        private int actorEventCursor;
        private String path;
        StepContext(AuthoringDocument document, JsonObject segment, AssetResolver resolver) {
            this.document = document; this.segment = AuthoringDocument.copy(segment); this.resolver = resolver;
            this.ownership = new AuthoringSectionOwnership(this.segment);
        }
        public AuthoringDocument getDocument() { return document; }
        public JsonObject getSegment() { return AuthoringDocument.copy(segment); }
        public AssetResolver getAssetResolver() { return resolver; }
        public String getPath() { return path; }
        public Map<String, Object> getState() { return state; }
        public Map<Integer, String> getActorLinkIds() {
            return Collections.unmodifiableMap(new LinkedHashMap<Integer, String>(actorLinkIds));
        }
        private void captureWorldPositions(PonderScene scene) {
            List<PonderScene.WorldEvent> events = scene.getWorldEvents();
            while (worldEventCursor < events.size()) {
                for (BlockPos pos : events.get(worldEventCursor++).getPositions()) knownPositions.add(pos);
            }
        }
        private void captureActorLinks(PonderScene scene, JsonObject step) {
            String linkId = AuthoringDocument.string(step, "linkId", "").trim();
            List<PonderScene.ActorEvent> events = scene.getActorEvents();
            while (actorEventCursor < events.size()) {
                PonderScene.ActorEvent event = events.get(actorEventCursor++);
                if (event.getType() == PonderScene.ActorEventType.SPAWN && !linkId.isEmpty())
                    actorLinkIds.put(event.getActorId(), linkId);
            }
        }
    }

    private final Map<String, StepHandler> handlers = new LinkedHashMap<String, StepHandler>();
    private boolean installingCoreHandlers;

    public AuthoringCompiler() {
        installingCoreHandlers = true;
        try {
            CameraStepHandlers.register(this);
            TimelineStepHandlers.register(this);
            NativeOverlayStepHandlers.register(this);
            NativeSectionInstructions.register(this);
        } finally {
            installingCoreHandlers = false;
        }
    }

    static final class CachedAssets implements AssetResolver {
        private final AssetResolver delegate;
        private final PonderSchematic.DecodeBudget decodeBudget = new PonderSchematic.DecodeBudget(64L * 1024 * 1024, 1048576);
        private final Map<String, PonderSchematic> values = new LinkedHashMap<String, PonderSchematic>();
        private final Map<PonderSchematic, AuthoringNbtBudget.AssetStats> stats = new java.util.IdentityHashMap<PonderSchematic, AuthoringNbtBudget.AssetStats>();
        private long uniqueNbtBytes;
        CachedAssets(AssetResolver delegate) { this.delegate = delegate; }
        @Override public PonderSchematic resolve(String id) throws IOException {
            if (values.containsKey(id)) return values.get(id);
            if (delegate == null) throw new IOException("No local structure resolver");
            PonderSchematic value = delegate instanceof BudgetedAssetResolver
                ? ((BudgetedAssetResolver) delegate).resolve(id, decodeBudget) : delegate.resolve(id);
            if (value == null) throw new IOException("Structure asset not found: " + id);
            if (!stats.containsKey(value)) {
                AuthoringNbtBudget.AssetStats measured = AuthoringNbtBudget.measureAsset(value);
                uniqueNbtBytes = AuthoringNbtBudget.checkedAdd(uniqueNbtBytes, measured.bytes);
                stats.put(value, measured);
            }
            values.put(id, value);
            return value;
        }
        AuthoringNbtBudget.AssetStats stats(PonderSchematic value) {
            AuthoringNbtBudget.AssetStats measured = stats.get(value);
            return measured == null ? AuthoringNbtBudget.AssetStats.EMPTY : measured;
        }
    }

    private static CachedAssets cached(AssetResolver resolver) {
        return resolver instanceof CachedAssets ? (CachedAssets) resolver : new CachedAssets(resolver);
    }

    public AuthoringCompiler registerHandler(String type, StepHandler handler) {
        String key = type == null ? "" : type.toLowerCase(Locale.ROOT);
        AuthoringValidation.Capability capability = AuthoringValidation.capabilities().get(key);
        if (capability == null) capability = AuthoringValidation.nativeCapabilities().get(key);
        if (key.isEmpty() || handler == null || handlers.containsKey(key)
            || !installingCoreHandlers && capability != null && capability.getStatus() != AuthoringValidation.Status.UNSUPPORTED)
            throw new IllegalArgumentException("Cannot replace an existing authoring step handler: " + key);
        handlers.put(key, handler);
        return this;
    }

    public Map<String, AuthoringValidation.Capability> getCapabilities() {
        Map<String, AuthoringValidation.Capability> values = new LinkedHashMap<String, AuthoringValidation.Capability>(AuthoringValidation.capabilities());
        values.putAll(AuthoringValidation.nativeCapabilities());
        for (String type : handlers.keySet()) {
            AuthoringValidation.Capability existing = values.get(type);
            if (existing == null || existing.getStatus() == AuthoringValidation.Status.UNSUPPORTED)
                values.put(type, new AuthoringValidation.Capability(type, AuthoringValidation.Status.CONVERTED,
                    "set_interface_data".equals(type)
                        ? "Isolated integer data timeline: duration/from/to interpolation, IDs 0..3 or local keys; legacy key/value is instant; no real menu writes"
                        : "Registered offline extension; handler validation defines the supported subset"));
        }
        return Collections.unmodifiableMap(values);
    }

    public AuthoringValidation validate(AuthoringDocument document, AssetResolver resolver) {
        return validateCached(document, cached(resolver));
    }

    /** Review-stage shape/capability checks only; never invokes handlers, resolves assets or establishes publishability. */
    public AuthoringValidation validateSchema(AuthoringDocument document) {
        return AuthoringValidation.validate(document, handlers.keySet());
    }

    private AuthoringValidation validateCached(AuthoringDocument document, CachedAssets resolver) {
        AuthoringValidation base = AuthoringValidation.validate(document, handlers.keySet());
        AuthoringValidation.Builder issues = new AuthoringValidation.Builder().addAll(base);
        if (base.hasErrors()) return issues.build();
        try { AuthoringNbtBudget.checkDeclaredNbt(document); }
        catch (AuthoringNbtBudget.LimitException limit) { return issues.error("budget.nbt", limit.path, limit.getMessage()).build(); }
        catch (RuntimeException invalid) { return issues.error("step.nbt", "/scenes", invalid.getMessage()).build(); }
        int binding = 0;
        for (String item : document.getItems()) {
            try { resolveItem(document, item, 1, null); }
            catch (RuntimeException invalid) { issues.error("registry.item", "/items/" + binding, invalid.getMessage()); }
            binding++;
        }
        int sceneIndex = 0;
        for (JsonObject segment : document.getScenes()) {
            String scenePath = "/scenes/" + sceneIndex++;
            long totalPositions = 0;
            try { validateView(segment); }
            catch (RuntimeException invalid) { issues.error("view.invalid", scenePath + "/view", invalid.getMessage()); }
            Set<String> sectionIds = new HashSet<String>();
            Map<String, Object> nativeSections = new LinkedHashMap<String, Object>();
            Map<Integer, PonderScene.ActorKind> nativeActors = new LinkedHashMap<Integer, PonderScene.ActorKind>();
            try { totalPositions += primary(document, segment, resolver).getBlocks().size(); }
            catch (AuthoringNbtBudget.LimitException limit) { return issues.error("budget.nbt", scenePath + "/structure", limit.getMessage()).build(); }
            catch (RuntimeException invalid) { issues.error("asset.structure", scenePath + "/structure", invalid.getMessage()); }
            int stepIndex = 0, actorCreations = 0, particleEvents = 0;
            long particleEmissions = 0;
            for (JsonElement element : segment.getAsJsonArray("steps")) {
                JsonObject step = element.getAsJsonObject();
                String path = scenePath + "/steps/" + stepIndex++;
                try {
                    if ("create_entity".equals(type(step)) || "create_item_entity".equals(type(step))
                        || "actor_action".equals(type(step)) && "SPAWN".equalsIgnoreCase(AuthoringDocument.string(step, "action", ""))) {
                        if (++actorCreations > 256) return issues.error("budget.actors", path, "A segment may create at most 256 preview actors").build();
                    }
                    validateStep(document, step, segment, resolver);
                    if ("spawn_particles".equals(type(step)) || "indicate_redstone".equals(type(step)) || "indicate_success".equals(type(step))) {
                        if (++particleEvents > 256) throw new IllegalArgumentException("A segment may contain at most 256 particle emitters");
                        particleEmissions += "spawn_particles".equals(type(step)) ? (long) integer(step, "count", 10, 0, 256) * duration(step, 1) : 20;
                        if (particleEmissions > 65536) throw new IllegalArgumentException("Segment particle emissions exceed 65536");
                    }
                    if ("actor_action".equals(type(step))) NativeActorInstructions.validate(document, step, nativeActors);
                    if ("native_section".equals(type(step))) NativeSectionInstructions.validateReferenceOrder(step, nativeSections);
                    String linkId = AuthoringDocument.string(step, "linkId", "");
                    if (!linkId.isEmpty() && ("show_structure".equals(type(step)) || "show_section_and_merge".equals(type(step)) || "show_extra_structure".equals(type(step))
                        || "set_block".equals(type(step)) && !"none".equals(ExtraStructurePlanning.normalizeEntrance(AuthoringDocument.string(step,"entranceAnimation","none"))))) {
                        sectionIds.add(linkId);
                    }
                    if (!linkId.isEmpty() && ("move_section".equals(type(step)) || "rotate_section".equals(type(step))) && !sectionIds.contains(linkId))
                        throw new IllegalArgumentException("Unknown section link: " + linkId);
                    if (step.has("blockPos") && !"show_extra_structure".equals(type(step))) totalPositions += volume(step);
                    if (step.has("positions")) totalPositions += "native_section".equals(type(step))
                        ? NativeSectionInstructions.positions(step).size() : positions(step).size();
                    if ("native_overlay".equals(type(step)) && step.has("overlay") && step.get("overlay").isJsonObject()) {
                        JsonObject overlay = step.getAsJsonObject("overlay");
                        for (String key : new String[] {"sourcePositions", "targetPositions"})
                            if (overlay.has(key) && overlay.get(key).isJsonArray()) totalPositions += overlay.getAsJsonArray(key).size();
                    }
                    if ("show_extra_structure".equals(type(step))) totalPositions += ExtraStructurePlanning.plan(
                        resolveAsset(resolver, structureReference(document, segment, step)), step).getPlacements().size();
                    StepHandler handler = handlers.get(type(step));
                    if (handler != null) handler.validate(document, AuthoringDocument.copy(step), path, issues);
                } catch (AuthoringNbtBudget.LimitException limit) { return issues.error("budget.nbt", path, limit.getMessage()).build(); }
                catch (RuntimeException invalid) { issues.error("step.invalid", path, invalid.getMessage()); }
            }
            if (totalPositions > 262144) issues.error("budget.positions", scenePath, "Total selected/asset positions exceed 262144");
        }
        if (!issues.build().hasErrors()) {
            try { AuthoringNbtBudget.check(document, resolver, handlers.keySet()); }
            catch (AuthoringNbtBudget.LimitException limit) { issues.error("budget.nbt", limit.path, limit.getMessage()); }
            catch (RuntimeException invalid) { issues.error("budget.preflight", "/scenes", invalid.getMessage()); }
        }
        return issues.build();
    }

    public List<PonderScene> compile(AuthoringDocument document, AssetResolver resolver) {
        final CachedAssets assets = cached(resolver);
        requireValid(document, assets);
        List<PonderScene> scenes = new ArrayList<PonderScene>();
        ResourceLocation component = new ResourceLocation(mappedId(document, document.getItems().get(0)));
        for (int index = 0; index < document.getScenes().size(); index++) {
            final int sceneIndex = index;
            PonderStoryBoard board = (builder, util) -> applyValidated(builder, util, document, document.getScenes().get(sceneIndex), assets);
            PonderStoryBoardEntry entry = new PonderStoryBoardEntry(board, "ponder_authoring", document.getRuntimeSceneId(index), component);
            PonderScene scene = new PonderScene(entry, new PonderLocalization());
            scene.program(new PonderSceneBuilder(scene), scene.getSceneBuildingUtil());
            scenes.add(scene);
        }
        return Collections.unmodifiableList(scenes);
    }

    public PonderStoryBoard storyBoard(AuthoringDocument document, int sceneIndex, AssetResolver resolver) {
        final CachedAssets assets = cached(resolver);
        requireValid(document, assets);
        final JsonObject segment = document.getScenes().get(sceneIndex);
        return (scene, util) -> applyValidated(scene, util, document, segment, assets);
    }

    public void apply(SceneBuilder scene, SceneBuildingUtil util, AuthoringDocument document,
        JsonObject segment, AssetResolver resolver) {
        CachedAssets assets = cached(resolver);
        requireValid(document, assets);
        applyValidated(scene, util, document, segment, assets);
    }

    private void applyValidated(SceneBuilder scene, SceneBuildingUtil util, AuthoringDocument document,
        JsonObject segment, CachedAssets resolver) {
        StepContext context = new StepContext(document, segment, resolver);
        List<JsonObject> all = document.getScenes();
        int index = all.indexOf(segment);
        if (index < 0) throw new IllegalArgumentException("Segment does not belong to the document");
        ResourceLocation runtimeId = document.getRuntimeSceneId(index);
        PonderSchematic initialStructure = primary(document, segment, resolver);
        scene.getScene().setSchematic(initialStructure);
        context.knownPositions.addAll(initialStructure.getBlocks().keySet());
        scene.title(runtimeId.getPath(), text(segment.get("title"), text(document.internalJson().get("title"), runtimeId.getPath())));
        scene.getScene().setSceneId(runtimeId);
        applyView(scene, segment);
        JsonArray steps = segment.getAsJsonArray("steps");
        String firstType = steps.size() == 0 ? "" : type(steps.get(0).getAsJsonObject());
        if (!initialStructure.isEmpty() && bool(segment, "autoReveal", true) && !"show_structure".equals(firstType) && !"show_interface".equals(firstType)) {
            scene.world().showSection(selection(scene, util, new JsonObject(), context), EnumFacing.UP);
            scene.idle(20);
        }
        context.captureWorldPositions(scene.getScene());
        int stepIndex = 0;
        for (JsonElement element : steps) {
            JsonObject step = element.getAsJsonObject();
            context.path = "/scenes/" + index + "/steps/" + stepIndex++;
            if (bool(segment, "autoKeyframes", true)) scene.addKeyframe();
            scene.getScene().recordOperation("authoring.source(" + context.path + ", " + type(step) + ")");
            try {
                StepHandler handler = handlers.get(type(step));
                if (handler == null) applyBuiltin(scene, util, step, context);
                else handler.apply(scene, util, AuthoringDocument.copy(step), context);
                context.captureWorldPositions(scene.getScene());
                context.captureActorLinks(scene.getScene(), step);
            } catch (RuntimeException invalid) {
                throw new CompilationException(new AuthoringValidation.Builder().error("step.compile", context.path,
                    invalid.getMessage() == null ? invalid.getClass().getSimpleName() : invalid.getMessage()).build());
            }
        }
        try { scene.getScene().validateSectionOwnershipBudget(); }
        catch (IllegalArgumentException limit) {
            throw new CompilationException(new AuthoringValidation.Builder().error("budget.sections", "/scenes/" + index, limit.getMessage()).build());
        }
        scene.markAsFinished();
        if (segment.has("view") && segment.get("view").isJsonObject() && !bool(segment.getAsJsonObject("view"), "finished", true)) scene.getScene().setFinished(false);
    }

    private void requireValid(AuthoringDocument document, AssetResolver resolver) {
        AuthoringValidation validation = validate(document, resolver);
        if (validation.hasErrors()) throw new CompilationException(validation);
    }

    private void validateStep(AuthoringDocument doc, JsonObject step, JsonObject segment, AssetResolver resolver) {
        duration(step, 20);
        if (!handlers.containsKey(type(step)) || AuthoringValidation.capabilities().containsKey(type(step))
            || AuthoringValidation.nativeCapabilities().containsKey(type(step))) AuthoringPointSpace.validate(step);
        // Interface extensions own their 2D coordinates, menu fields and virtual-slot validation.
        if (handlers.containsKey(type(step))) return;
        String type = type(step);
        if (step.has("legacyOwnership")) {
            if (!java.util.Arrays.asList("show_structure","hide_section","set_block","replace_blocks").contains(type) || step.has("linkId"))
                throw new IllegalArgumentException("legacyOwnership is only a native unlinked world-event compatibility flag");
            bool(step,"legacyOwnership",false);
            if (bool(step,"legacyOwnership",false) && step.has("entranceAnimation")
                && !"none".equals(ExtraStructurePlanning.normalizeEntrance(AuthoringDocument.string(step,"entranceAnimation","none"))))
                throw new IllegalArgumentException("Native legacy ownership cannot request a new entrance animation");
        }
        if (step.has("nbtMode")) {
            if (!"modify_block_entity_nbt".equals(type) || !step.has("nbt"))
                throw new IllegalArgumentException("nbtMode requires modify_block_entity_nbt with an explicit NBT value");
            nbtMode(step);
        }
        if ("actor_action".equals(type)) return;
        if (step.has("clearBlockEntityNbt")) {
            if (!"replace_blocks".equals(type)) throw new IllegalArgumentException("clearBlockEntityNbt only applies to a recorded replace_blocks conversion");
            bool(step, "clearBlockEntityNbt", false);
        }
        if (step.has("fadeDuration") || step.has("directionless")) {
            if ((!"show_structure".equals(type) && !"hide_section".equals(type)) || !step.has("fadeDuration"))
                throw new IllegalArgumentException("Explicit fadeDuration is required for local show/hide visibility fields");
            integer(step, "fadeDuration", 15, 0, 72000); bool(step, "directionless", false);
        }
        for (String key : new String[] {"point", "pos", "motion", "offset", "lookAt", "pivot"})
            if (step.has(key)) vector(step, key, Vec3d.ZERO);
        if (step.has("blockPos")) blockPos(step, "blockPos");
        if (step.has("blockPos2")) blockPos(step, "blockPos2");
        if (step.has("positions")) positions(step);
        if (step.has("nbt")) nbt(requiredString(step, "nbt"));
        for (String key : new String[] {"rotX", "rotY", "rotZ", "yaw", "pitch", "degrees", "degreesX", "scale", "rotation"})
            if (step.has(key) && Math.abs(number(step, key, 0)) > 1000000) throw new IllegalArgumentException(key + " exceeds numeric budget");
        if ("set_block".equals(type) || "replace_blocks".equals(type)) resolveBlock(doc, step);
        if ("set_block".equals(type) || "destroy_block".equals(type) || "break_progress".equals(type) || "indicate_redstone".equals(type)
            || "indicate_success".equals(type)) blockPos(step, "blockPos");
        if ("replace_blocks".equals(type) || "hide_section".equals(type) || "highlight_section".equals(type)
            || "toggle_redstone_power".equals(type) || "modify_block_entity_nbt".equals(type)
            || "move_section".equals(type) || "rotate_section".equals(type) || "show_section_and_merge".equals(type) || "restore_blocks".equals(type)) {
            boolean linkedTransform = ("move_section".equals(type) || "rotate_section".equals(type)) && !AuthoringDocument.string(step, "linkId", "").isEmpty();
            if (!step.has("blockPos") && !step.has("positions") && !bool(step, "fullScene", false) && !linkedTransform)
                throw new IllegalArgumentException("Explicit blockPos/blockPos2 or fullScene selection is required");
        }
        if ("move_section".equals(type) && !step.has("offset")) throw new IllegalArgumentException("move_section requires offset");
        if ("modify_block_entity_nbt".equals(type)) {
            if (!step.has("nbt") && !step.has("blockProperties")) throw new IllegalArgumentException("NBT or blockProperties patch is required");
            if (step.has("blockProperties") && !step.get("blockProperties").isJsonObject()) throw new IllegalArgumentException("blockProperties must be an object");
        }
        if ("text".equals(type) && text(step.get("text"), "").isEmpty()) throw new IllegalArgumentException("Text must contain a literal/localized value");
        if ("show_extra_structure".equals(type)) {
            ExtraStructurePlanning.plan(resolveAsset(resolver, structureReference(doc, segment, step)), step);
        }
        if ("show_structure".equals(type) && step.has("scale") && number(step, "scale", 1) <= 0)
            throw new IllegalArgumentException("Structure scale must be positive");
        if ("show_section_and_merge".equals(type) || "set_block".equals(type)) {
            String entrance = ExtraStructurePlanning.normalizeEntrance(AuthoringDocument.string(step, "entranceAnimation", "none"));
            int entranceDuration = integer(step, "entranceDuration", 20, 0, 72000), entranceInterval = integer(step, "entranceInterval", 1, 0, 72000);
            bool(step, "smartDisplay", true);
            if ("set_block".equals(type)) bool(step, "immediateDisplay", true);
            if (!"none".equals(entrance) && (step.has("positions") || step.has("blockPos"))) {
                if (step.has("blockPos")) volume(step);
                Iterable<BlockPos> positions = step.has("positions") ? positions(step)
                    : BlockPos.getAllInBox(blockPos(step,"blockPos"), step.has("blockPos2") ? blockPos(step,"blockPos2") : blockPos(step,"blockPos"));
                ExtraStructurePlanning.groupsForPositions(positions, entrance, entranceDuration, entranceInterval, facing(step));
            }
        }
        if ("create_entity".equals(type)) {
            String entity = mappedId(doc, requiredString(step, "entity"));
            if (!"minecraft:player".equals(entity) && !"minecraft:parrot".equals(entity) && !"minecraft:minecart".equals(entity))
                throw new IllegalArgumentException("No safe preview actor for entity " + entity);
            if (step.has("lookAt") && !"minecraft:player".equals(entity))
                throw new IllegalArgumentException("lookAt requires the player adapter; use explicit yaw/pitch for parrot or minecart");
            if (step.has("lookAt") && (step.has("yaw") || step.has("pitch")
                || ActorNbtAdapter.read(step).hasKey("Rotation")))
                throw new IllegalArgumentException("Choose player lookAt or an explicit yaw/pitch/NBT Rotation, not both");
            TimelineStepHandlers.validateSpawn(doc, step, false);
        }
        if ("show_controls".equals(type) && step.has("item"))
            stepItem(doc, step);
        if ("create_item_entity".equals(type)) TimelineStepHandlers.validateSpawn(doc, step, true);
        if ("shared_text".equals(type)) sharedText(doc, step);
        if ("rotate_camera_y".equals(type)) {
            boolean instant = bool(step, "instant", duration(step, 20) == 0);
            bool(step, "waitForCompletion", true);
            if (bool(step, "legacyAdditive", false) && (number(step, "degreesX", 0) != 0 || instant))
                throw new IllegalArgumentException("Legacy additive rotation requires zero pitch and a non-instant animation");
        }
        if ("spawn_particles".equals(type)) {
            particle(doc, step);
            String distribution = AuthoringDocument.string(step, "distribution", "point").toLowerCase(Locale.ROOT);
            if (!"point".equals(distribution) && !"box".equals(distribution) && !"sphere".equals(distribution))
                throw new IllegalArgumentException("Particle distribution must be point, box or sphere");
            Vec3d spread = vector(step, "spread", Vec3d.ZERO);
            if (spread.x < 0 || spread.y < 0 || spread.z < 0) throw new IllegalArgumentException("Particle spread must be non-negative");
            if (duration(step, 1) < 1) throw new IllegalArgumentException("Particle duration must be positive");
            if ((long) integer(step, "count", 10, 0, 256) * duration(step, 1) > 65536)
                throw new IllegalArgumentException("Particle count/duration exceeds the 65536 event budget");
        }
        if (step.has("color")) palette(step);
        if (step.has("direction") && !"show_controls".equals(type)) facing(step);
        if ("show_controls".equals(type)) {
            Pointing.valueOf(AuthoringDocument.string(step, "direction", "down").toUpperCase(Locale.ROOT));
            String action = AuthoringDocument.string(step, "action", "");
            if (!action.isEmpty() && !"left".equals(action) && !"right".equals(action) && !"scroll".equals(action)) throw new IllegalArgumentException("Unknown control action: " + action);
        }
    }

    private void applyBuiltin(SceneBuilder scene, SceneBuildingUtil util, JsonObject step, StepContext context) {
        AuthoringDocument doc = context.document;
        String type = type(step);
        int duration = duration(step, "text".equals(type) || "shared_text".equals(type) || "show_controls".equals(type) ? 60
            : "highlight_section".equals(type) ? 40 : "spawn_particles".equals(type) ? 1 : 20);
        if ("idle".equals(type)) { scene.idle(duration); return; }
        if ("keyframe".equals(type)) { scene.addKeyframe(); return; }
        if ("lazy_keyframe".equals(type)) { scene.addLazyKeyframe(); return; }
        if ("actor_action".equals(type)) { NativeActorInstructions.apply(scene.getScene(), doc, step); return; }
        if ("text".equals(type) || "shared_text".equals(type)) {
            PonderScene.OverlayEvent text = scene.getScene().createOverlayEvent(duration).text("shared_text".equals(type)
                ? sharedText(doc, step) : text(step.get("text"), "")).palette(step.has("color") ? palette(step) : PonderPalette.WHITE);
            if (step.has("blockPos") || step.has("positions")) {
                Selection selected = selection(scene, util, step, context); List<BlockPos> positions = new ArrayList<BlockPos>();
                for (BlockPos pos : selected) positions.add(pos);
                text.pointAt(vector(step, "point", selected.getCenter())).targetPositions(positions);
            } else if (step.has("point")) text.pointAt(vector(step, "point", Vec3d.ZERO));
            AuthoringPointSpace.apply(text, step);
            if (step.has("independentY")) text.independent(integer(step, "independentY", 0, 0, 32767));
            text.placeNearTarget(bool(step, "placeNearTarget", false)).connectorVisible(bool(step, "connectorVisible", true));
            if (step.has("overlayId")) text.overlayId(AuthoringDocument.string(step, "overlayId", null));
            return;
        }
        if ("highlight_section".equals(type)) {
            if (bool(step, "chasing", true)) scene.overlay().chaseOutline(palette(step), AuthoringDocument.string(step, "linkId", context.path), selection(scene, util, step, context), duration);
            else scene.overlay().showOutline(palette(step), AuthoringDocument.string(step, "linkId", context.path), selection(scene, util, step, context), duration);
            return;
        }
        if ("show_controls".equals(type)) {
            InputElementBuilder controls = scene.overlay().showControls(vector(step, "point", Vec3d.ZERO),
                Pointing.valueOf(AuthoringDocument.string(step, "direction", "down").toUpperCase(Locale.ROOT)), duration);
            String action = AuthoringDocument.string(step, "action", "");
            if ("left".equals(action)) controls.leftClick(); else if ("right".equals(action)) controls.rightClick(); else if ("scroll".equals(action)) controls.scroll();
            if (bool(step, "leftClick", false)) controls.leftClick();
            if (bool(step, "rightClick", false)) controls.rightClick();
            if (bool(step, "scroll", false)) controls.scroll();
            if (step.has("item")) controls.withItem(stepItem(doc, step));
            if (bool(step, "whileSneaking", false)) controls.whileSneaking();
            if (bool(step, "whileCTRL", false)) controls.whileCTRL();
            AuthoringPointSpace.apply(scene.getScene().getOverlayEvents().get(scene.getScene().getOverlayEvents().size() - 1), step);
            return;
        }
        if ("show_structure".equals(type) || "show_section_and_merge".equals(type)) {
            Selection selected = selection(scene, util, step, context);
            EnumFacing revealDirection = step.has("direction") ? facing(step)
                : "show_section_and_merge".equals(type) ? EnumFacing.DOWN : EnumFacing.UP;
            if ("show_section_and_merge".equals(type) || step.has("linkId") || !bool(step,"legacyOwnership",false)) {
                String linkId = AuthoringDocument.string(step, "linkId", "");
                String entrance = ExtraStructurePlanning.normalizeEntrance(AuthoringDocument.string(step, "entranceAnimation", "none"));
                boolean animated = step.has("entranceAnimation") && !"none".equals(entrance);
                String reveal = context.ownership.prepare(scene, selected, animated && bool(step, "smartDisplay", true));
                if (animated) {
                    List<ExtraStructurePlanning.EntranceGroup> groups = ExtraStructurePlanning.groupsForPositions(selected, entrance,
                        integer(step, "entranceDuration", 20, 0, 72000), integer(step, "entranceInterval", 1, 0, 72000), revealDirection);
                    int interval = "simultaneous".equals(entrance) ? 0 : integer(step, "entranceInterval", 1, 0, 72000);
                    context.ownership.showGroups(scene, util, selected, linkId, reveal, groups, groups.size() * interval);
                } else {
                    int revealDuration = step.has("fadeDuration") ? integer(step, "fadeDuration", 15, 0, 72000)
                        : step.has("entranceAnimation") && !step.get("entranceAnimation").isJsonNull() ? 0 : duration;
                    context.ownership.show(scene, selected, linkId, reveal, revealDuration,
                        bool(step, "directionless", false) ? null : revealDirection);
                }
            } else if (step.has("fadeDuration")) {
                int revealDuration = step.has("fadeDuration") ? integer(step, "fadeDuration", 15, 0, 72000)
                    : step.has("entranceAnimation") && !step.get("entranceAnimation").isJsonNull() ? 0 : duration;
                scene.getScene().recordWorldEvent(PonderScene.WorldEventType.SHOW_SECTION, selected, null,
                    revealDuration == 0 || bool(step, "directionless", false) ? null : step.has("direction") ? facing(step) : EnumFacing.DOWN,
                    Math.max(1, revealDuration), null, null, null);
            } else scene.world().showSection(selected, facing(step));
            if (step.has("scale")) scene.scaleSceneView((float) number(step, "scale", 1));
            if (number(step, "rotation", 0) != 0) scene.rotateCameraY((float) number(step, "rotation", 0));
            return;
        }
        if ("show_extra_structure".equals(type)) {
            ExtraStructurePlanning.Plan plan = ExtraStructurePlanning.plan(resolveAsset(context.resolver, structureReference(doc, context.segment, step)), step);
            Selection placement = selectPositions(util, plan.getPositions());
            String reveal = plan.isAnimated() ? context.ownership.prepare(scene, placement, plan.isSmartDisplay()) : "";
            Map<IBlockState, List<BlockPos>> byState = new LinkedHashMap<IBlockState, List<BlockPos>>();
            for (ExtraStructurePlanning.PlacedBlock entry : plan.getPlacements())
                byState.computeIfAbsent(entry.getState(), state -> new ArrayList<BlockPos>()).add(entry.getPos());
            for (Map.Entry<IBlockState, List<BlockPos>> batch : byState.entrySet())
                scene.world().setBlocks(selectPositions(util, batch.getValue()), batch.getKey(), plan.shouldSpawnParticles());
            for (ExtraStructurePlanning.PlacedBlock entry : plan.getPlacements()) {
                BlockPos pos = entry.getPos(); NBTTagCompound data = entry.getNbtCopy();
                if (data != null) {
                    scene.world().modifyBlockEntityNBT(util.select().position(pos), TileEntity.class, tag -> tag.merge(data.copy()), true);
                }
            }
            String linkId = AuthoringDocument.string(step, "linkId", "");
            if (plan.isAnimated()) {
                context.ownership.stage(scene, placement, reveal);
                context.ownership.showGroups(scene, util, placement, linkId, reveal, plan.getEntranceGroups(), plan.getTimelineAdvance());
            } else {
                context.ownership.place(scene, placement, plan.isPlaceVisible());
                if (!linkId.isEmpty()) context.ownership.show(scene,
                    plan.isPlaceVisible() ? placement : selectPositions(util, Collections.<BlockPos>emptyList()), linkId, "", 0, null);
            }
            return;
        }
        if ("set_block".equals(type)) {
            Selection selected = selection(scene, util, step, context);
            IBlockState state = resolveBlock(doc, step);
            String entrance = ExtraStructurePlanning.normalizeEntrance(AuthoringDocument.string(step, "entranceAnimation", "none"));
            boolean animated = state.getBlock() != Blocks.AIR && !"none".equals(entrance);
            String reveal = animated ? context.ownership.prepare(scene, selected, bool(step, "smartDisplay", true)) : "";
            boolean visible = bool(step, "immediateDisplay", true) && state.getBlock() != Blocks.AIR;
            boolean particles = !animated && visible && bool(step, "spawnParticles", true);
            if (step.has("blockPos2")) scene.world().setBlocks(selected, state, particles);
            else scene.world().setBlock(blockPos(step,"blockPos"), state, particles);
            if (step.has("nbt")) patchNbt(scene, selected, step);
            if (animated) {
                context.ownership.stage(scene, selected, reveal);
                List<ExtraStructurePlanning.EntranceGroup> groups = ExtraStructurePlanning.groupsForPositions(selected, entrance,
                    integer(step, "entranceDuration", 20, 0, 72000), integer(step, "entranceInterval", 1, 0, 72000), facing(step));
                int interval = "simultaneous".equals(entrance) ? 0 : integer(step, "entranceInterval", 1, 0, 72000);
                context.ownership.showGroups(scene, util, selected, AuthoringDocument.string(step, "linkId", ""), reveal, groups, groups.size() * interval);
            } else if (!bool(step,"legacyOwnership",false)) context.ownership.place(scene, selected, visible);
            return;
        }
        if ("replace_blocks".equals(type)) {
            Selection selected = selection(scene, util, step, context);
            if (bool(step, "clearBlockEntityNbt", false)) scene.world().setBlocks(selected, resolveBlock(doc, step), bool(step, "spawnParticles", true));
            else scene.world().replaceBlocks(selected, resolveBlock(doc, step), bool(step, "spawnParticles", true));
            if (!bool(step,"legacyOwnership",false)) context.ownership.place(scene, selected, resolveBlock(doc, step).getBlock() != Blocks.AIR);
            if (step.has("nbt")) patchNbt(scene, selected, step); return;
        }
        if ("destroy_block".equals(type)) { scene.world().destroyBlock(blockPos(step, "blockPos")); return; }
        if ("restore_blocks".equals(type)) { scene.world().restoreBlocks(selection(scene, util, step, context)); return; }
        if ("break_progress".equals(type)) { scene.world().incrementBlockBreakingProgress(blockPos(step, "blockPos")); return; }
        if ("hide_section".equals(type)) {
            int fadeDuration = step.has("fadeDuration") ? integer(step, "fadeDuration", 20, 0, 72000) : duration;
            if (bool(step,"legacyOwnership",false)) scene.getScene().recordWorldEvent(PonderScene.WorldEventType.HIDE_SECTION,
                selection(scene,util,step,context),null,fadeDuration==0 || bool(step,"directionless",false) ? null : step.has("direction") ? facing(step) : EnumFacing.DOWN,
                Math.max(1,fadeDuration),null,null,null);
            else context.ownership.hide(scene, selection(scene, util, step, context), fadeDuration,
                bool(step, "directionless", false) ? null : step.has("direction") ? facing(step) : EnumFacing.DOWN);
            return;
        }
        if ("toggle_redstone_power".equals(type)) { scene.world().toggleRedstonePower(selection(scene, util, step, context)); return; }
        if ("modify_block_entity_nbt".equals(type)) {
            Selection selected = selection(scene, util, step, context);
            if (step.has("blockProperties")) scene.world().modifyBlocks(selected,
                state -> applyProperties(state, step.getAsJsonObject("blockProperties")), false);
            if (step.has("nbt")) patchNbt(scene, selected, step);
            return;
        }
        if ("indicate_redstone".equals(type)) { scene.effects().indicateRedstone(blockPos(step, "blockPos")); return; }
        if ("indicate_success".equals(type)) { scene.effects().indicateSuccess(blockPos(step, "blockPos")); return; }
        if ("rotate_camera_y".equals(type)) {
            if (bool(step, "legacyAdditive", false)) scene.rotateCameraY((float) number(step, "degrees", 90));
            else scene.rotateCamera(step.has("degreesX") ? Float.valueOf((float) number(step, "degreesX", 0)) : null,
                (float) number(step, "degrees", 90), bool(step, "instant", duration == 0));
            if (duration > 0 && bool(step, "waitForCompletion", true)) scene.idle(duration);
            return;
        }
        if ("move_section".equals(type) || "rotate_section".equals(type)) {
            String linkId = AuthoringDocument.string(step, "linkId", "");
            if ("move_section".equals(type)) {
                Vec3d offset = vector(step, "offset", Vec3d.ZERO);
                if (!linkId.isEmpty()) context.ownership.move(scene, linkId, offset, duration);
                else scene.getScene().recordSectionMove(selection(scene, util, step, context), offset, duration);
            } else {
                Vec3d rotation = new Vec3d(number(step, "rotX", 0), number(step, "rotY", number(step, "degrees", 0)), number(step, "rotZ", 0));
                if (!linkId.isEmpty()) context.ownership.rotate(scene, linkId, rotation, step.has("pivot") ? vector(step, "pivot", Vec3d.ZERO) : null, duration);
                else { Selection selected = selection(scene, util, step, context); scene.getScene().recordSectionRotation(selected, vector(step, "pivot", selected.getCenter()), rotation, duration); }
            }
            return;
        }
        if ("create_item_entity".equals(type)) {
            int before = scene.getScene().getActorEvents().size();
            scene.world().createItemEntity(vector(step, "pos", vector(step, "point", Vec3d.ZERO)),
                Vec3d.ZERO, TimelineStepHandlers.spawnItem(doc, step));
            TimelineStepHandlers.applySpawn(scene.getScene(), doc, step, spawnedActorId(scene.getScene(), before), true);
            return;
        }
        if ("create_entity".equals(type)) {
            int before = scene.getScene().getActorEvents().size();
            Vec3d pos = vector(step, "pos", vector(step, "point", Vec3d.ZERO));
            String entity = mappedId(doc, requiredString(step, "entity"));
            ElementLink<? extends AnimatedSceneElement> link;
            if ("minecraft:player".equals(entity)) {
                ElementLink<PlayerElement> player = scene.special().createPlayer(pos, (float) number(step, "yaw", 0));
                if (step.has("lookAt")) scene.special().lookAtPlayer(player, vector(step, "lookAt", Vec3d.ZERO), 0);
                link = player;
            } else if ("minecraft:parrot".equals(entity)) link = scene.special().createBirb(pos, ParrotPose.DancePose::new);
            else link = scene.special().createCart(pos, (float) number(step, "yaw", 0), null);
            TimelineStepHandlers.applySpawn(scene.getScene(), doc, step, spawnedActorId(scene.getScene(), before), false);
            return;
        }
        if ("clear_entities".equals(type) || "clear_item_entities".equals(type)) {
            TimelineStepHandlers.clearActors(scene.getScene(), "clear_item_entities".equals(type));
            return;
        }
        if ("spawn_particles".equals(type)) {
            scene.getScene().recordParticleDistribution(vector(step, "pos", vector(step, "point", Vec3d.ZERO)),
                integer(step, "count", 10, 0, 256), duration, particle(doc, step).name(), vector(step, "motion", Vec3d.ZERO),
                AuthoringDocument.string(step, "distribution", "point").toLowerCase(Locale.ROOT), vector(step, "spread", Vec3d.ZERO)); return;
        }
        throw new IllegalArgumentException("No executable handler for " + type);
    }

    private static int spawnedActorId(PonderScene scene, int startIndex) {
        List<PonderScene.ActorEvent> events = scene.getActorEvents();
        for (int index = startIndex; index < events.size(); index++)
            if (events.get(index).getType() == PonderScene.ActorEventType.SPAWN) return events.get(index).getActorId();
        throw new IllegalStateException("Preview actor creation did not record a spawn event");
    }

    private static void patchNbt(SceneBuilder scene, Selection selection, JsonObject step) {
        if (!step.has("nbt")) throw new IllegalArgumentException("NBT patch is required");
        NBTTagCompound patch = nbt(AuthoringDocument.string(step, "nbt", ""));
        boolean replace = "replace".equals(nbtMode(step));
        scene.world().modifyBlockEntityNBT(selection, TileEntity.class, tag -> {
            if (replace) {
                for (String key : new ArrayList<String>(tag.getKeySet())) tag.removeTag(key);
            }
            tag.merge(patch.copy());
        }, bool(step, "reDrawBlocks", true));
    }

    private static String nbtMode(JsonObject step) {
        if (!step.has("nbtMode")) return "merge";
        JsonElement mode = step.get("nbtMode");
        if (!mode.isJsonPrimitive() || !mode.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("nbtMode must be merge or replace");
        String value = mode.getAsString();
        if (!"merge".equals(value) && !"replace".equals(value))
            throw new IllegalArgumentException("Unknown nbtMode: " + value);
        return value;
    }

    private static void applyView(SceneBuilder scene, JsonObject segment) {
        validateView(segment);
        if (!segment.has("view")) return;
        if (!segment.get("view").isJsonObject()) throw new IllegalArgumentException("view must be an object");
        JsonObject view = segment.getAsJsonObject("view");
        double scale = number(view, "scale", 1), offset = number(view, "offsetY", 0);
        if (scale <= 0 || scale > 1000000 || Math.abs(offset) > 1000000) throw new IllegalArgumentException("View exceeds numeric budget");
        scene.configureBasePlate(integer(view, "baseX", 0, -30000000, 30000000), integer(view, "baseZ", 0, -30000000, 30000000), integer(view, "baseSize", 5, 1, 256));
        scene.scaleSceneView((float) scale); scene.setSceneOffsetY((float) offset);
        scene.getScene().setHidePlatformShadow(bool(view, "hideShadow", true));
        scene.setNextUpEnabled(bool(view, "nextUp", true));
    }

    private static void validateView(JsonObject segment) {
        bool(segment, "autoReveal", true);
        bool(segment, "autoKeyframes", true);
        if (!segment.has("view")) return;
        if (!segment.get("view").isJsonObject()) throw new IllegalArgumentException("view must be an object");
        JsonObject view = segment.getAsJsonObject("view");
        double scale = number(view, "scale", 1), offset = number(view, "offsetY", 0);
        if (scale <= 0 || scale > 1000000 || Math.abs(offset) > 1000000) throw new IllegalArgumentException("View exceeds numeric budget");
        integer(view, "baseX", 0, -30000000, 30000000); integer(view, "baseZ", 0, -30000000, 30000000); integer(view, "baseSize", 5, 1, 256);
        bool(view, "hideShadow", true); bool(view, "nextUp", true); bool(view, "finished", true);
    }

    private static ItemStack stepItem(AuthoringDocument document, JsonObject step) {
        ItemStack stack = resolveItem(document, requiredString(step, "item"), integer(step, "count", 1, 1, 64),
            step.has("nbt") ? nbt(requiredString(step, "nbt")) : null);
        if (step.has("meta")) stack.setItemDamage(integer(step, "meta", 0, 0, 32767));
        return stack;
    }

    static PonderSchematic primary(AuthoringDocument document, JsonObject segment, AssetResolver resolver) {
        JsonObject root = document.internalJson();
        String reference = AuthoringDocument.string(root, "structure", "");
        if (root.has("structures") && root.get("structures").isJsonArray() && root.getAsJsonArray("structures").size() > 0)
            reference = root.getAsJsonArray("structures").get(0).getAsString();
        for (JsonObject candidate : document.getScenes()) {
            reference = AuthoringDocument.string(candidate, "structure", reference);
            for (JsonElement entry : candidate.getAsJsonArray("steps")) {
                JsonObject step = entry.getAsJsonObject();
                if ("show_structure".equals(type(step))) {
                    if (step.has("structure")) reference = structureReference(document, candidate, step);
                    break;
                }
            }
            if (candidate.equals(segment)) break;
        }
        return reference.isEmpty() ? PonderSchematic.empty() : resolveAsset(resolver, reference);
    }

    static String structureReference(AuthoringDocument doc, JsonObject segment, JsonObject step) {
        JsonElement reference = step.get("structure");
        if (reference == null) return AuthoringDocument.string(segment, "structure", AuthoringDocument.string(doc.internalJson(), "structure", ""));
        JsonElement pool = segment.has("structures") ? segment.get("structures") : doc.internalJson().get("structures");
        String text = reference.getAsString();
        if (text.matches("[0-9]+") && pool != null && pool.isJsonArray()) {
            int number = Integer.parseInt(text);
            int index = number == 0 ? 0 : number - 1;
            if (index >= pool.getAsJsonArray().size()) throw new IllegalArgumentException("Structure pool index is out of range");
            return pool.getAsJsonArray().get(index).getAsString();
        }
        return text;
    }

    static PonderSchematic resolveAsset(AssetResolver resolver, String reference) {
        if (reference == null || reference.isEmpty() || resolver == null) throw new IllegalArgumentException("Missing local structure asset/resolver: " + reference);
        try {
            PonderSchematic schematic = resolver.resolve(reference);
            if (schematic == null) throw new IllegalArgumentException("Structure asset not found: " + reference);
            if (schematic.getBlocks().size() > 262144) throw new IllegalArgumentException("Structure exceeds block budget");
            for (BlockPos pos : schematic.getBlocks().keySet()) checkCoordinate(pos.getX(), pos.getY(), pos.getZ());
            return schematic;
        } catch (IOException invalid) { throw new IllegalArgumentException("Cannot load structure " + reference + ": " + invalid.getMessage(), invalid); }
    }

    private static Selection selection(SceneBuilder scene, SceneBuildingUtil util, JsonObject step, StepContext context) {
        if (step.has("positions")) {
            Selection selected = util.select().position(BlockPos.ORIGIN).substract(util.select().position(BlockPos.ORIGIN));
            for (BlockPos pos : positions(step)) selected.add(util.select().position(pos));
            return selected;
        }
        if (step.has("blockPos")) {
            BlockPos first = blockPos(step, "blockPos");
            return util.select().fromTo(first, step.has("blockPos2") ? blockPos(step, "blockPos2") : first);
        }
        Selection result = util.select().position(BlockPos.ORIGIN).substract(util.select().position(BlockPos.ORIGIN));
        for (BlockPos pos : context.knownPositions) result.add(util.select().position(pos));
        return result;
    }

    private static Selection selectPositions(SceneBuildingUtil util, Iterable<BlockPos> positions) {
        Selection selected = util.select().position(BlockPos.ORIGIN).substract(util.select().position(BlockPos.ORIGIN));
        for (BlockPos pos : positions) selected.add(util.select().position(pos));
        return selected;
    }

    public static String mappedId(AuthoringDocument document, String id) {
        JsonObject root = document.internalJson();
        if (root.has("idMappings") && root.get("idMappings").isJsonObject()) {
            JsonElement mapped = root.getAsJsonObject("idMappings").get(id);
            if (mapped != null) {
                if (mapped.isJsonPrimitive() && mapped.getAsJsonPrimitive().isString()) id = mapped.getAsString();
                else if (mapped.isJsonObject()) id = requiredString(mapped.getAsJsonObject(), "id");
                else throw new IllegalArgumentException("Invalid explicit id mapping: " + id);
            }
        }
        if (!AuthoringDocument.validId(id)) throw new IllegalArgumentException("Invalid resource id: " + id);
        return id;
    }

    public static ItemStack resolveItem(AuthoringDocument document, String id, int count, NBTTagCompound tag) {
        String mapped = mappedId(document, id);
        ResourceLocation location = new ResourceLocation(mapped);
        if (!Item.REGISTRY.containsKey(location)) throw new IllegalArgumentException("Unknown 1.12.2 item; add an explicit idMappings entry: " + id);
        JsonObject mapping = mappingObject(document, id);
        int meta = integer(mapping, "meta", 0, 0, 32767);
        ItemStack stack = new ItemStack(Item.REGISTRY.getObject(location), count, meta);
        if (stack.isEmpty()) throw new IllegalArgumentException("Item binding resolves to an empty stack: " + id);
        if (tag != null) stack.setTagCompound(tag.copy());
        return stack;
    }

    public static IBlockState resolveBlock(AuthoringDocument document, JsonObject step) {
        String id = requiredString(step, "block");
        ResourceLocation location = new ResourceLocation(mappedId(document, id));
        if (!Block.REGISTRY.containsKey(location)) throw new IllegalArgumentException("Unknown 1.12.2 block; add an explicit idMappings entry: " + id);
        JsonObject mapping = mappingObject(document, id);
        Block block = Block.REGISTRY.getObject(location);
        int meta = integer(step, "meta", integer(mapping, "meta", 0, 0, 15), 0, 15);
        IBlockState state = block.getStateFromMeta(meta);
        JsonObject properties = mapping.has("properties") && mapping.get("properties").isJsonObject() ? AuthoringDocument.copy(mapping.getAsJsonObject("properties")) : new JsonObject();
        if (step.has("blockProperties")) {
            if (!step.get("blockProperties").isJsonObject()) throw new IllegalArgumentException("blockProperties must be an object");
            for (Map.Entry<String, JsonElement> property : step.getAsJsonObject("blockProperties").entrySet()) properties.add(property.getKey(), property.getValue());
        }
        return applyProperties(state, properties);
    }

    private static IBlockState applyProperties(IBlockState state, JsonObject properties) {
        for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
            IProperty<?> found = null;
            for (IProperty<?> candidate : state.getPropertyKeys()) if (candidate.getName().equals(property.getKey())) found = candidate;
            if (found == null) throw new IllegalArgumentException("Unknown block property " + property.getKey() + " for " + state.getBlock().getRegistryName());
            state = withProperty(state, found, property.getValue().getAsString());
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IBlockState withProperty(IBlockState state, IProperty property, String value) {
        com.google.common.base.Optional parsed = property.parseValue(value);
        if (!parsed.isPresent()) throw new IllegalArgumentException("Invalid value " + value + " for property " + property.getName());
        return state.withProperty(property, (Comparable) parsed.get());
    }

    private static JsonObject mappingObject(AuthoringDocument document, String id) {
        JsonObject root = document.internalJson();
        if (root.has("idMappings") && root.get("idMappings").isJsonObject()) {
            JsonElement value = root.getAsJsonObject("idMappings").get(id);
            if (value != null && value.isJsonObject()) return value.getAsJsonObject();
        }
        return new JsonObject();
    }

    public static String text(JsonElement value, String fallback) {
        if (value == null || value.isJsonNull()) return fallback;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) return value.getAsString();
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            for (String key : new String[] {"zh_cn", "en_us", "text"}) if (object.has(key)) return text(object.get(key), fallback);
        }
        return fallback;
    }

    private static String sharedText(AuthoringDocument document, JsonObject step) {
        if (step.has("text")) return text(step.get("text"), "");
        String key = requiredString(step, "key");
        JsonObject root = document.internalJson();
        if (root.has("sharedTexts") && root.get("sharedTexts").isJsonObject() && root.getAsJsonObject("sharedTexts").has(key))
            return text(root.getAsJsonObject("sharedTexts").get(key), "");
        throw new IllegalArgumentException("Unresolved shared text: " + key);
    }

    private static EnumParticleTypes particle(AuthoringDocument document, JsonObject step) {
        String id = requiredString(step, "particle");
        String mapped = id.contains(":") ? mappedId(document, id) : id;
        for (EnumParticleTypes particle : EnumParticleTypes.values())
            if (particle.name().equalsIgnoreCase(mapped) || particle.getParticleName().equals(mapped)
                || ("minecraft:" + particle.getParticleName()).equals(mapped)) return particle;
        throw new IllegalArgumentException("Unknown 1.12.2 particle (SNBT options are not supported): " + id);
    }

    private static PonderPalette palette(JsonObject step) {
        return PonderPalette.valueOf(AuthoringDocument.string(step, "color", "blue").toUpperCase(Locale.ROOT));
    }
    private static EnumFacing facing(JsonObject step) {
        return EnumFacing.valueOf(AuthoringDocument.string(step, "direction", "up").toUpperCase(Locale.ROOT));
    }
    private static String type(JsonObject step) { return AuthoringDocument.string(step, "type", "").toLowerCase(Locale.ROOT); }
    public static int duration(JsonObject step, int fallback) { return integer(step, "duration", fallback, 0, 72000); }
    public static boolean bool(JsonObject step, String key, boolean fallback) {
        if (!step.has(key)) return fallback;
        if (!step.get(key).isJsonPrimitive() || !step.get(key).getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key + " must be boolean");
        return step.get(key).getAsBoolean();
    }
    public static int integer(JsonObject step, String key, int fallback, int min, int max) {
        double value = number(step, key, fallback);
        if (value != Math.rint(value) || value < min || value > max) throw new IllegalArgumentException(key + " must be an integer in " + min + ".." + max);
        return (int) value;
    }
    public static double number(JsonObject object, String key, double fallback) {
        if (!object.has(key)) return fallback;
        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key + " must be numeric");
        double value = element.getAsDouble();
        if (Double.isNaN(value) || Double.isInfinite(value)) throw new IllegalArgumentException(key + " must be finite");
        return value;
    }
    public static String requiredString(JsonObject object, String key) {
        String value = AuthoringDocument.string(object, key, "");
        if (value.isEmpty()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }
    public static Vec3d vector(JsonObject object, String key, Vec3d fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonArray() || value.getAsJsonArray().size() != 3) throw new IllegalArgumentException(key + " must have exactly three coordinates");
        JsonArray array = value.getAsJsonArray();
        double[] coordinates = new double[3];
        for (int i = 0; i < 3; i++) {
            if (!array.get(i).isJsonPrimitive() || !array.get(i).getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key + " coordinates must be numeric");
            coordinates[i] = array.get(i).getAsDouble();
        }
        checkCoordinate(coordinates[0], coordinates[1], coordinates[2]);
        return new Vec3d(coordinates[0], coordinates[1], coordinates[2]);
    }
    public static BlockPos blockPos(JsonObject object, String key) {
        if (!object.has(key)) throw new IllegalArgumentException("Missing " + key);
        Vec3d value = vector(object, key, Vec3d.ZERO);
        if (value.x != Math.rint(value.x) || value.y != Math.rint(value.y) || value.z != Math.rint(value.z)) throw new IllegalArgumentException(key + " must use integral coordinates");
        return new BlockPos(value.x, value.y, value.z);
    }
    static long volume(JsonObject step) {
        BlockPos a = blockPos(step, "blockPos"), b = step.has("blockPos2") ? blockPos(step, "blockPos2") : a;
        long x = Math.abs((long) a.getX() - b.getX()) + 1, y = Math.abs((long) a.getY() - b.getY()) + 1, z = Math.abs((long) a.getZ() - b.getZ()) + 1;
        if (x > 262144 || y > 262144 || z > 262144 || x * y > 262144 || x * y * z > 262144) throw new IllegalArgumentException("Selection exceeds 262144 blocks");
        return x * y * z;
    }

    static List<BlockPos> positions(JsonObject step) {
        JsonElement element = step.get("positions");
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() == 0 || element.getAsJsonArray().size() > 262144)
            throw new IllegalArgumentException("positions must contain 1..262144 explicit block coordinates");
        List<BlockPos> result = new ArrayList<BlockPos>();
        for (JsonElement entry : element.getAsJsonArray()) {
            JsonObject wrapper = new JsonObject(); wrapper.add("pos", entry);
            result.add(blockPos(wrapper, "pos"));
        }
        return result;
    }
    private static void checkCoordinate(double x, double y, double z) {
        for (double value : new double[] {x, y, z}) if (Double.isNaN(value) || Double.isInfinite(value) || Math.abs(value) > 30000000)
            throw new IllegalArgumentException("Coordinates must be finite and within +/-30000000");
    }
    public static NBTTagCompound nbt(String source) {
        if (source.length() > 65536) throw new IllegalArgumentException("NBT exceeds 65536 characters");
        if (source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > AuthoringNbtBudget.MAX_TILE_BYTES)
            throw new AuthoringNbtBudget.LimitException("/scenes", "SNBT exceeds the 64 KiB UTF-8 tile budget");
        int depth = 0; char quote = 0; boolean escaped = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == quote) quote = 0;
            } else if (current == '\"' || current == '\'') quote = current;
            else if (current == '{' || current == '[') {
                if (++depth > 64) throw new IllegalArgumentException("SNBT exceeds 64 nesting levels");
            } else if (current == '}' || current == ']') depth--;
        }
        try { return JsonToNBT.getTagFromJson(source.isEmpty() ? "{}" : source); }
        catch (Exception invalid) { throw new IllegalArgumentException("Invalid SNBT: " + invalid.getMessage(), invalid); }
    }
}
