package net.createmod.ponder.authoring.practice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.GuiSnapshotBuilder;
import net.createmod.ponder.authoring.AuthoringCompiler;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.AuthoringValidation;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Builder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PracticeSnapshotRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

/** Registers only a closed, declarative set of interface operations, never external GUI code. */
public final class PracticeStepHandlers {
    private static final String STATE_KEY = "ponder.practice.definition";
    private static final String[] TYPES = {"show_interface", "click_interface", "change_interface_slot",
        "set_interface_slots", "set_interface_data", "set_interface_context", "interface_action",
        "hide_interface", "await_interface"};
    private static final Map<PonderScene, List<PracticeDefinition>> DEFINITIONS =
        Collections.synchronizedMap(new WeakHashMap<PonderScene, List<PracticeDefinition>>());
    private static final Map<AuthoringDocument, Map<String, JsonObject>> VALIDATION_SHOWS =
        Collections.synchronizedMap(new WeakHashMap<AuthoringDocument, Map<String, JsonObject>>());

    private PracticeStepHandlers() { }

    public static void register(AuthoringCompiler compiler) {
        for (String type : TYPES) compiler.registerHandler(type, new Handler(type));
    }

    public static List<PracticeDefinition> getDefinitions(PonderScene scene) {
        List<PracticeDefinition> definitions = DEFINITIONS.get(scene);
        return definitions == null ? Collections.<PracticeDefinition>emptyList()
            : Collections.unmodifiableList(new ArrayList<PracticeDefinition>(definitions));
    }

    private static final class Pending {
        final Builder builder;
        final int start;
        final List<PracticeDefinition> definitions;
        final int index;
        PonderScene.OverlayEvent overlay;
        Pending(Builder builder, int start, List<PracticeDefinition> definitions) {
            this.builder = builder; this.start = start; this.definitions = definitions;
            index = definitions.size(); definitions.add(builder.build());
        }
    }

    private static final class Handler implements AuthoringCompiler.StepHandler {
        private final String type;
        Handler(String type) { this.type = type; }
        @Override
        public void validate(AuthoringDocument document, JsonObject step, String path, AuthoringValidation.Builder issues) {
            try {
                net.createmod.ponder.authoring.AuthoringPointSpace.validate(step);
                if ("show_interface".equals(type)) {
                    if (step.has("blockPos")) AuthoringCompiler.blockPos(step, "blockPos");
                    if (step.has("point")) {
                        if (!step.get("point").isJsonArray() || step.getAsJsonArray("point").size() < 2 || step.getAsJsonArray("point").size() > 3)
                            throw new IllegalArgumentException("Interface point must have two screen or three world coordinates");
                        for (JsonElement coordinate : step.getAsJsonArray("point")) {
                            if (!coordinate.isJsonPrimitive() || !coordinate.getAsJsonPrimitive().isNumber()
                                || !Double.isFinite(coordinate.getAsDouble()) || Math.abs(coordinate.getAsDouble()) > 30000000)
                                throw new IllegalArgumentException("Invalid interface point coordinate");
                        }
                        if (step.getAsJsonArray("point").size() == 2) issues.warning("practice.screen_point", path,
                            "Two-coordinate screen placement is retained as metadata; the preview uses the foreground panel layout, not a 3D target");
                    }
                    PracticeDefinition definition = PracticeJson.start(document, step).build();
                    if (definition.getMenu() == PracticeDefinition.Menu.READ_ONLY)
                        issues.warning("practice.read_only", path, "Unknown menu is a read-only snapshot; no mod GUI will be invoked");
                } else {
                    JsonObject preceding = validationShows(document).get(path);
                    if (preceding == null) throw new IllegalArgumentException("Interface step requires a preceding show_interface");
                    Builder builder = PracticeJson.start(document, preceding);
                    PracticeJson.append(builder, document, step, 0);
                    builder.build();
                }
            } catch (RuntimeException failure) {
                issues.error("practice.invalid", path, failure.getMessage());
            }
        }
        @Override
        public void apply(SceneBuilder scene, SceneBuildingUtil util, JsonObject step, AuthoringCompiler.StepContext context) {
            int current = scene.getScene().getTotalIdleTicks();
            if ("show_interface".equals(type)) {
                Object previous = context.getState().get(STATE_KEY);
                List<PracticeDefinition> definitions = previous instanceof Pending ? ((Pending) previous).definitions : null;
                if (previous instanceof Pending) {
                    Pending old = (Pending) previous;
                    int at = Math.max(0, current - old.start);
                    old.builder.action(new PracticeDefinition.Action(at, PracticeDefinition.Operation.HIDE, -1,
                        net.minecraft.item.ItemStack.EMPTY, "", 0));
                    update(old, at);
                }
                if (definitions == null) {
                    definitions = new ArrayList<PracticeDefinition>();
                    DEFINITIONS.put(scene.getScene(), definitions);
                }
                Pending pending = new Pending(PracticeJson.start(context.getDocument(), step), current, definitions);
                context.getState().put(STATE_KEY, pending);
                PracticeSnapshotRenderer provider = new PracticeSnapshotRenderer(scene.getScene(), pending.index, current);
                ResourceLocation snapshot = provider.register();
                GuiSnapshotBuilder gui = scene.overlay().showGuiSnapshot(snapshot, Math.max(1, pending.builder.duration()));
                if (step.has("blockPos")) gui.pointAt(util.select().position(AuthoringCompiler.blockPos(step, "blockPos")));
                else if (step.has("point") && step.get("point").isJsonArray() && step.getAsJsonArray("point").size() == 3)
                    gui.pointAt(AuthoringCompiler.vector(step, "point", Vec3d.ZERO));
                pending.overlay = scene.getScene().getOverlayEvents().get(scene.getScene().getOverlayEvents().size() - 1);
                net.createmod.ponder.authoring.AuthoringPointSpace.apply(pending.overlay, step);
                pending.overlay.setGuiSnapshotProvider(provider);
                pending.overlay.setGuiDuration(pending.builder.duration());
            } else {
                Object state = context.getState().get(STATE_KEY);
                if (!(state instanceof Pending)) throw new IllegalArgumentException("Interface step requires a preceding show_interface");
                Pending pending = (Pending) state;
                int tick = Math.max(0, current - pending.start);
                int duration = ("interface_action".equals(type) || "click_interface".equals(type))
                    ? AuthoringCompiler.duration(step, 10) : 0;
                PracticeJson.append(pending.builder, context.getDocument(), step, tick + duration);
                update(pending, tick + duration);
                if (duration > 0) scene.idle(duration);
            }
            scene.getScene().recordOperation("practice." + type);
        }
    }

    private static void update(Pending pending, int end) {
        pending.builder.duration(Math.max(pending.builder.duration(), end));
        PracticeDefinition definition = pending.builder.build();
        pending.definitions.set(pending.index, definition);
        if (pending.overlay != null) pending.overlay.setGuiDuration(definition.getDuration());
    }

    private static Map<String, JsonObject> validationShows(AuthoringDocument document) {
        Map<String, JsonObject> cached = VALIDATION_SHOWS.get(document);
        if (cached != null) return cached;
        Map<String, JsonObject> index = new java.util.LinkedHashMap<String, JsonObject>();
        int sceneIndex = 0;
        for (JsonObject segment : document.getScenes()) {
            JsonObject previous = null;
            int stepIndex = 0;
            for (JsonElement element : segment.getAsJsonArray("steps")) {
                JsonObject step = element.getAsJsonObject();
                String path = "/scenes/" + sceneIndex + "/steps/" + stepIndex++;
                if (previous != null) index.put(path, previous);
                if (step.has("type") && "show_interface".equalsIgnoreCase(step.get("type").getAsString())) previous = step;
            }
            sceneIndex++;
        }
        VALIDATION_SHOWS.put(document, index);
        return index;
    }
}
