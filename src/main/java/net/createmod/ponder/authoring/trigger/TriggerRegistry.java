package net.createmod.ponder.authoring.trigger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reload commits immutable metadata only; no world, assets or timeline compilation. */
public final class TriggerRegistry {
    public static final class Snapshot {
        public final long revision;
        public final List<TriggerDefinition> definitions;
        private Snapshot(long revision, List<TriggerDefinition> definitions) {
            this.revision = revision; this.definitions = definitions;
        }
    }
    private static volatile Snapshot current = new Snapshot(0, Collections.<TriggerDefinition>emptyList());
    private TriggerRegistry() { }
    public static Snapshot snapshot() { return current; }
    public static synchronized void replacePublished(List<TriggerDefinition> definitions) {
        if (definitions == null || definitions.size() > TriggerDefinition.MAX_RULES)
            throw new IllegalArgumentException("At most 64 published trigger rules are allowed");
        Set<String> ids = new HashSet<String>();
        for (TriggerDefinition definition : definitions) {
            if (definition == null || !ids.add(definition.getRuntimeSceneId().toString()))
                throw new IllegalArgumentException("Trigger definitions must be non-null with unique scene IDs");
        }
        current = new Snapshot(current.revision + 1,
            Collections.unmodifiableList(new ArrayList<TriggerDefinition>(definitions)));
    }
}
