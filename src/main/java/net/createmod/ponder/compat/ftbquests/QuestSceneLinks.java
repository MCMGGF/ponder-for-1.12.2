package net.createmod.ponder.compat.ftbquests;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Client-side links only. IDs are FTB Quests 1.12's unsigned 32-bit hex IDs,
 * not the 64-bit IDs used by newer FTB Quests versions.
 */
public final class QuestSceneLinks {

    private static final Pattern ID = Pattern.compile("[0-9a-fA-F]{1,8}");
    private static final Pattern COMPONENT = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern MMCE_COMPONENT =
        Pattern.compile("ponder_legacy:mmce/[a-z0-9_.-]+/[a-z0-9_./-]+");
    private static final Pattern MMCE_BINDING_SUFFIX = Pattern.compile(".*/binding_[0-9]+");
    private static final int MAX_LINKS_PER_OBJECT = 128;
    private static final int MAX_OBJECTS = 16384;

    public final boolean enabled;
    public final boolean autoLinkItems;
    private final Map<String, List<Target>> quests;
    private final Map<String, List<Target>> tasks;

    private QuestSceneLinks(boolean enabled, boolean autoLinkItems, Map<String, List<Target>> quests,
        Map<String, List<Target>> tasks) {
        this.enabled = enabled;
        this.autoLinkItems = autoLinkItems;
        this.quests = quests;
        this.tasks = tasks;
    }

    public static QuestSceneLinks defaults() {
        return new QuestSceneLinks(true, true, Collections.<String, List<Target>>emptyMap(),
            Collections.<String, List<Target>>emptyMap());
    }

    public static QuestSceneLinks parse(Reader reader) {
        JsonElement element = new JsonParser().parse(reader);
        if (!element.isJsonObject()) {
            throw new JsonParseException("Quest links must be a JSON object");
        }
        JsonObject root = element.getAsJsonObject();
        if (root.has("version") && integer(root.get("version"), "version") != 1) {
            throw new JsonParseException("Only quest link version 1 is supported");
        }
        return new QuestSceneLinks(bool(root, "enabled", true), bool(root, "autoLinkItems", true),
            readLinks(root, "quests"), readLinks(root, "tasks"));
    }

    public List<Target> forQuest(String id) {
        return targets(quests, id);
    }

    public List<Target> forTask(String id) {
        return targets(tasks, id);
    }

    public List<Target> resolve(String questId, Map<String, List<String>> taskComponents, String iconComponent,
        Map<String, Integer> registeredSceneCounts) {
        if (!enabled) {
            return Collections.emptyList();
        }
        Map<Target, Target> result = new LinkedHashMap<Target, Target>();
        Set<String> explicitComponents = new LinkedHashSet<String>();
        addExplicit(result, explicitComponents, forQuest(questId));
        for (String taskId : taskComponents.keySet()) {
            addExplicit(result, explicitComponents, forTask(taskId));
        }
        // A missing explicit link must remain visible, never turn into an automatic fallback.
        if (!autoLinkItems) {
            return Collections.unmodifiableList(new ArrayList<Target>(result.values()));
        }
        for (Map.Entry<String, List<String>> task : taskComponents.entrySet()) {
            for (String component : task.getValue()) {
                addAutomatic(result, explicitComponents, component, registeredSceneCounts);
            }
        }
        if (iconComponent != null) {
            addAutomatic(result, explicitComponents, iconComponent, registeredSceneCounts);
        }
        return Collections.unmodifiableList(new ArrayList<Target>(result.values()));
    }

    private static void addExplicit(Map<Target, Target> result, Set<String> components, List<Target> candidates) {
        for (Target target : candidates) {
            result.put(target, target);
            components.add(target.component);
        }
    }

    private static void addAutomatic(Map<Target, Target> result, Set<String> explicitComponents, String component,
        Map<String, Integer> counts) {
        Integer count = counts.get(component);
        if (!explicitComponents.contains(component) && count != null && count.intValue() > 0) {
            Target target = Target.automatic(component);
            result.put(target, target);
        }
    }

    public static String normalizeId(String value) {
        if (value == null || !ID.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("Expected a 1-8 digit FTB Quests 1.12 hex ID: " + value);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return ("00000000" + normalized).substring(normalized.length());
    }

    public static String idFromInt(int value) {
        return normalizeId(Integer.toHexString(value));
    }

    private static List<Target> targets(Map<String, List<Target>> source, String id) {
        if (id == null) {
            return Collections.emptyList();
        }
        List<Target> result = source.get(normalizeId(id));
        return result == null ? Collections.<Target>emptyList() : result;
    }

    private static Map<String, List<Target>> readLinks(JsonObject root, String field) {
        if (!root.has(field)) {
            return Collections.emptyMap();
        }
        if (!root.get(field).isJsonObject()) {
            throw new JsonParseException(field + " must be an object keyed by hex quest/task ID");
        }
        Map<String, List<Target>> result = new LinkedHashMap<String, List<Target>>();
        JsonObject entries = root.getAsJsonObject(field);
        if (entries.entrySet().size() > MAX_OBJECTS) {
            throw new JsonParseException(field + " contains too many objects");
        }
        for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            String id;
            try {
                id = normalizeId(entry.getKey());
            } catch (IllegalArgumentException exception) {
                throw new JsonParseException(exception.getMessage(), exception);
            }
            if (result.containsKey(id)) {
                throw new JsonParseException("Duplicate normalized " + field + " ID " + id);
            }
            if (!entry.getValue().isJsonArray()) {
                throw new JsonParseException(field + "." + id + " must be an array");
            }
            List<Target> links = new ArrayList<Target>();
            for (JsonElement value : entry.getValue().getAsJsonArray()) {
                if (links.size() >= MAX_LINKS_PER_OBJECT) {
                    throw new JsonParseException("Too many links for " + id);
                }
                if (!value.isJsonObject()) {
                    throw new JsonParseException("Each link must be an object with component and optional scene");
                }
                JsonObject target = value.getAsJsonObject();
                String component = resourceId(target, "component");
                int scene = target.has("scene") ? integer(target.get("scene"), "scene") : 0;
                if (scene < 0 || scene > 10000) {
                    throw new JsonParseException("Scene index must be between 0 and 10000");
                }
                String sceneId = target.has("sceneId") ? resourceId(target, "sceneId") : null;
                Target link = new Target(component, scene, sceneId, false);
                if (!links.contains(link)) {
                    links.add(link);
                }
            }
            result.put(id, Collections.unmodifiableList(links));
        }
        return Collections.unmodifiableMap(result);
    }

    private static String resourceId(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()
            || !object.getAsJsonPrimitive(field).isString()) {
            throw new JsonParseException("Link " + field + " must be a namespaced string");
        }
        String value = object.get(field).getAsString();
        if (value.length() > 256 || !COMPONENT.matcher(value).matches()) {
            throw new JsonParseException("Invalid Ponder " + field + " ID " + value);
        }
        return value;
    }

    private static boolean bool(JsonObject object, String field, boolean defaultValue) {
        if (!object.has(field)) {
            return defaultValue;
        }
        JsonElement value = object.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new JsonParseException(field + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static int integer(JsonElement value, String field) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
            || !value.getAsString().matches("-?[0-9]+")) {
            throw new JsonParseException(field + " must be an integer");
        }
        try {
            return Integer.parseInt(value.getAsString());
        } catch (NumberFormatException exception) {
            throw new JsonParseException(field + " is outside the integer range", exception);
        }
    }

    public static final class Target {
        public final String component;
        /** Old-format ordinal, retained for readers of existing manifests; never an ID fallback. */
        public final int scene;
        public final String sceneId;
        private final boolean automatic;

        public Target(String component, int scene) {
            this(component, scene, null, false);
        }

        public Target(String component, String sceneId) {
            this(component, 0, sceneId, false);
        }

        private Target(String component, int scene, String sceneId, boolean automatic) {
            this.component = component;
            this.scene = scene;
            this.sceneId = sceneId;
            this.automatic = automatic;
        }

        static Target automatic(String component) {
            return new Target(component, 0, null, true);
        }

        boolean isAutomatic() {
            return automatic;
        }

        public String stableSceneId() {
            if (sceneId != null) {
                return sceneId;
            }
            // Binding aliases and literal machine paths can both end in /binding_N.
            // Their identity cannot be recovered from the component alone.
            if (!automatic && MMCE_COMPONENT.matcher(component).matches()
                && !MMCE_BINDING_SUFFIX.matcher(component).matches() && (scene == 0 || scene == 1)) {
                return component + (scene == 0 ? "/minimum" : "/maximum");
            }
            return null;
        }

        private String identity() {
            String stable = stableSceneId();
            return stable != null ? stable : automatic ? "#automatic" : "#" + scene;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof Target)) {
                return false;
            }
            Target other = (Target) object;
            return component.equals(other.component) && identity().equals(other.identity());
        }

        @Override
        public int hashCode() {
            return component.hashCode() * 31 + identity().hashCode();
        }
    }
}
