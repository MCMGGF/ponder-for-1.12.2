package net.createmod.ponder.foundation.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.createmod.ponder.foundation.PonderScene.ActorEvent;
import net.createmod.ponder.foundation.PonderScene.ActorEventType;

/** Actors own their visibility even when there are no visible scene blocks. */
final class PreviewActorVisibility {

    private PreviewActorVisibility() {
    }

    static boolean anyVisible(List<ActorEvent> events, float tick) {
        if (events == null || !Float.isFinite(tick)) {
            return false;
        }
        Map<Integer, Boolean> visible = new LinkedHashMap<Integer, Boolean>();
        Map<Integer, Boolean> invisible = new LinkedHashMap<Integer, Boolean>();
        for (ActorEvent event : events) {
            if (event.getTick() > tick) {
                break;
            }
            Integer id = Integer.valueOf(event.getActorId());
            if (event.getType() == ActorEventType.SPAWN) {
                visible.put(id, Boolean.valueOf(progress(event, tick) > 0F));
                invisible.put(id, Boolean.FALSE);
            } else if (event.getType() == ActorEventType.HIDE && visible.containsKey(id)) {
                visible.put(id, Boolean.valueOf(progress(event, tick) < 1F));
            } else if (event.getType() == ActorEventType.NBT_PATCH && visible.containsKey(id)
                && event.getActorNbt().hasKey("Invisible")) {
                invisible.put(id, Boolean.valueOf(event.getActorNbt().getBoolean("Invisible")));
            }
        }
        for (Map.Entry<Integer, Boolean> entry : visible.entrySet()) if (entry.getValue().booleanValue()
            && !Boolean.TRUE.equals(invisible.get(entry.getKey()))) return true;
        return false;
    }

    private static float progress(ActorEvent event, float tick) {
        return Math.max(0F, Math.min(1F, (tick - event.getTick() + 1F) / Math.max(1, event.getDuration())));
    }
}
