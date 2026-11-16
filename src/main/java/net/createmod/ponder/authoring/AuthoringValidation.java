package net.createmod.ponder.authoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class AuthoringValidation {
    public enum Severity { ERROR, WARNING }
    public enum Status { IMPLEMENTED, CONVERTED, UNSUPPORTED }

    public static final class Issue {
        private final Severity severity;
        private final String code, path, message;
        public Issue(Severity severity, String code, String path, String message) {
            this.severity = severity; this.code = code; this.path = path; this.message = message;
        }
        public Severity getSeverity() { return severity; }
        public String getCode() { return code; }
        public String getPath() { return path; }
        public String getMessage() { return message; }
        @Override public String toString() { return severity + " " + path + " [" + code + "] " + message; }
    }

    public static final class Capability {
        private final String type, reason;
        private final Status status;
        Capability(String type, Status status, String reason) {
            this.type = type; this.status = status; this.reason = reason;
        }
        public String getType() { return type; }
        public Status getStatus() { return status; }
        public String getReason() { return reason; }
    }

    public static final class Builder {
        private final List<Issue> issues = new ArrayList<Issue>();
        public Builder error(String code, String path, String message) {
            issues.add(new Issue(Severity.ERROR, code, path, message)); return this;
        }
        public Builder warning(String code, String path, String message) {
            issues.add(new Issue(Severity.WARNING, code, path, message)); return this;
        }
        public Builder addAll(AuthoringValidation validation) { issues.addAll(validation.issues); return this; }
        public AuthoringValidation build() { return new AuthoringValidation(issues); }
    }

    private static final Map<String, Capability> CAPABILITIES = createCapabilities();
    private static final Map<String, Capability> NATIVE_CAPABILITIES = createNativeCapabilities();
    private final List<Issue> issues;
    private AuthoringValidation(List<Issue> issues) {
        this.issues = Collections.unmodifiableList(new ArrayList<Issue>(issues));
    }
    public List<Issue> getIssues() { return issues; }
    public boolean hasErrors() {
        for (Issue issue : issues) if (issue.severity == Severity.ERROR) return true;
        return false;
    }
    public boolean canPublish() { return !hasErrors(); }
    public boolean canPreview() { return !hasErrors(); }
    public static Map<String, Capability> capabilities() { return CAPABILITIES; }
    public static Map<String, Capability> nativeCapabilities() { return NATIVE_CAPABILITIES; }
    public static AuthoringValidation validate(AuthoringDocument document) {
        return validate(document, Collections.<String>emptySet());
    }

    static AuthoringValidation validate(AuthoringDocument document, Set<String> extensions) {
        Builder result = new Builder();
        JsonObject root = document.internalJson();
        if (!numberEquals(root.get("formatVersion"), AuthoringDocument.FORMAT_VERSION))
            result.error("format.version", "/formatVersion", "Only authoring formatVersion 1 is executable");
        if (!AuthoringDocument.TARGET_VERSION.equals(AuthoringDocument.string(root, "targetVersion", "")))
            result.error("target.version", "/targetVersion", "Explicit conversion to Minecraft 1.12.2 is required");
        if (!AuthoringDocument.validId(document.getId()))
            result.error("document.id", "/id", "Use a namespaced id without traversal segments");
        if (!root.has("source") || !root.get("source").isJsonObject())
            result.error("source.object", "/source", "Source provenance must be an object");
        if (root.has("idMappings") && !root.get("idMappings").isJsonObject())
            result.error("mapping.object", "/idMappings", "Explicit idMappings must be an object");
        net.createmod.ponder.authoring.trigger.TriggerDefinition.validate(document, result);
        AuthoringItemBindings.validate(document, result);
        if (!root.has("items") || !root.get("items").isJsonArray() || root.getAsJsonArray("items").size() == 0)
            result.error("items.array", "/items", "At least one explicit item binding is required");
        else {
            int i = 0;
            for (JsonElement item : root.getAsJsonArray("items")) {
                if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()
                    || !AuthoringDocument.validId(item.getAsString()))
                    result.error("item.id", "/items/" + i, "Item tags are not silently mapped; use explicit 1.12.2 ids");
                i++;
            }
        }
        if (!root.has("scenes") || !root.get("scenes").isJsonArray() || root.getAsJsonArray("scenes").size() == 0) {
            result.error("scenes.array", "/scenes", "At least one scene segment is required");
            return result.build();
        }
        if (root.getAsJsonArray("scenes").size() > 64)
            result.error("budget.scenes", "/scenes", "At most 64 segments are allowed");
        int sceneIndex = 0, count = 0;
        Set<String> ids = new HashSet<String>();
        for (JsonElement element : root.getAsJsonArray("scenes")) {
            String path = "/scenes/" + sceneIndex;
            if (!element.isJsonObject()) {
                result.error("scene.object", path, "Scene segment must be an object"); sceneIndex++; continue;
            }
            JsonObject scene = element.getAsJsonObject();
            String id = AuthoringDocument.string(scene, "id", "scene_" + (sceneIndex + 1));
            if (!AuthoringDocument.validPath(id) || !ids.add(id))
                result.error("scene.id", path + "/id", "Scene ids must be safe and unique within the document");
            if (!scene.has("steps") || !scene.get("steps").isJsonArray())
                result.error("steps.array", path + "/steps", "Scene steps must be an array");
            else {
                int stepIndex = 0;
                for (JsonElement stepElement : scene.getAsJsonArray("steps")) {
                    String stepPath = path + "/steps/" + stepIndex++;
                    count++;
                    if (!stepElement.isJsonObject()) { result.error("step.object", stepPath, "Step must be an object"); continue; }
                    JsonObject step = stepElement.getAsJsonObject();
                    String type = AuthoringDocument.string(step, "type", "").toLowerCase(java.util.Locale.ROOT);
                    Capability capability = CAPABILITIES.get(type);
                    if (capability == null) capability = NATIVE_CAPABILITIES.get(type);
                    if (extensions.contains(type) && (capability == null || capability.status == Status.UNSUPPORTED)) {
                        result.warning("step.extension", stepPath + "/type", "Registered offline handler validates and converts this step");
                        continue;
                    }
                    if ("unsupported_builtin_event".equals(type))
                        result.error("fork.unsupported", stepPath, AuthoringDocument.string(step, "reason", "Original built-in event requires an explicit adapter"));
                    else if (capability == null) result.error("step.unknown", stepPath + "/type", "Unknown step retained as a draft: " + type);
                    else if (capability.status == Status.UNSUPPORTED)
                        result.error("step.unsupported", stepPath + "/type", capability.reason);
                    else if (capability.status == Status.CONVERTED)
                        result.warning("step.converted", stepPath + "/type", capability.reason);
                }
            }
            sceneIndex++;
        }
        if (count > 16384) result.error("budget.steps", "/scenes", "At most 16384 total steps are executable");
        return result.build();
    }

    private static boolean numberEquals(JsonElement element, int expected) {
        try { return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
            && element.getAsDouble() == expected; } catch (RuntimeException invalid) { return false; }
    }

    private static Map<String, Capability> createCapabilities() {
        Map<String, Capability> values = new LinkedHashMap<String, Capability>();
        add(values, Status.IMPLEMENTED, "Uses the existing deterministic preview timeline", "idle", "text",
            "highlight_section", "show_controls", "set_block", "destroy_block", "replace_blocks", "hide_section",
            "rotate_section", "move_section", "toggle_redstone_power", "modify_block_entity_nbt", "indicate_redstone", "indicate_success");
        add(values, Status.CONVERTED, "Local assets with quarter-turn state rotation, copied NBT, explicit ownership release and grouped directional entrances",
            "show_structure", "show_extra_structure");
        add(values, Status.CONVERTED, "Deterministic section identities, repeated-link append and directional groups; temporary entry inherits the target translation before final merge", "show_section_and_merge");
        add(values, Status.CONVERTED, "Player, parrot and rideable minecart adapters with bounded directional entrance and declared visual NBT; entrance/motion conflicts need separate steps", "create_entity");
        add(values, Status.CONVERTED, "Isolated item stacks and explicit motion; declared entity wrappers are adapted, without live gravity or pickup", "create_item_entity");
        add(values, Status.CONVERTED, "Authored pitch/yaw targets, instant zero-wait rotation and fixed preview animation; no live camera mutation", "rotate_camera_y");
        add(values, Status.CONVERTED, "Registered 1.12.2 particle IDs; deterministic point/box/ellipsoid preview markers, capped at 12 visible samples per emitter", "spawn_particles");
        add(values, Status.CONVERTED, "Clears only actors created by this compiled segment", "clear_entities", "clear_item_entities");
        add(values, Status.CONVERTED, "Requires an explicit local sharedTexts entry or literal text; no remote language lookup", "shared_text");
        add(values, Status.CONVERTED, "Deterministic default-relative view: move then zoom, with timeline overlay reset; manual camera remains independent", "zoom_scene");
        add(values, Status.CONVERTED, "Inclusive preview framing bounds are precomputed; no live world bounds or blocks are mutated", "encapsulate_bounds");
        add(values, Status.CONVERTED, "Registered 1.12.2 sound cues on forward screen playback only; seeking and world projection stay silent", "play_sound");
        add(values, Status.CONVERTED, "Explicit preview actor/NBT adapter fields only; item-tag deep merge is bounded and identity/lifecycle payloads are rejected", "modify_entities_nbt", "modify_item_entities_nbt");
        add(values, Status.IMPLEMENTED, "Matches the upstream explicit no-op scene boundary marker; does not wait or automatically switch scenes", "next_scene");
        add(values, Status.UNSUPPORTED, "Requires a registered offline interface step handler; no real GUI/container interaction is performed",
            "show_interface", "click_interface", "change_interface_slot", "set_interface_slots", "set_interface_data",
            "set_interface_context", "interface_action", "hide_interface", "await_interface");
        return Collections.unmodifiableMap(values);
    }

    private static void add(Map<String, Capability> values, Status status, String reason, String... types) {
        for (String type : types) values.put(type, new Capability(type, status, reason));
    }

    private static Map<String, Capability> createNativeCapabilities() {
        Map<String, Capability> values = new LinkedHashMap<String, Capability>();
        add(values, Status.CONVERTED, "1.12.2 preview-only actor event; fixed PLAYER/BIRB/CART/ITEM kinds, no live entity callbacks", "actor_action");
        add(values, Status.CONVERTED, "Recorded declarative texture, line, value-box, caption or known vanilla snapshot only; arbitrary providers are never executed", "native_overlay");
        add(values, Status.CONVERTED, "Recorded static preview point of interest; not a live entity tracking callback", "native_point_of_interest");
        add(values, Status.CONVERTED, "Bounded declarative section identity and ownership history; never a live world or executable callback", "native_section");
        add(values, Status.IMPLEMENTED, "Restores only recorded preview data and navigation timing", "restore_blocks", "break_progress", "keyframe", "lazy_keyframe");
        return Collections.unmodifiableMap(values);
    }
}
