package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.createmod.ponder.foundation.PonderScene.SoundEvent;

/** Only contiguous, explicitly advancing playback can cross a cue. Rendering and seeking cannot. */
final class PreviewSoundTimeline {
    private Object scene;
    private int cursor;

    void reset(Object value) { scene = value; cursor = -1; }
    void seek(Object value, int tick) { scene = value; cursor = tick; }
    List<SoundEvent> advance(Object value, List<SoundEvent> events, int before, int after) {
        if (value == null) { seek(null, after); return Collections.emptyList(); }
        if (scene == value && cursor == after && after == before + 1) return Collections.emptyList();
        if (scene != value) { scene = value; cursor = before == 0 ? -1 : before; }
        if (cursor != before && !(before == 0 && cursor == -1)) cursor = before;
        int previous = cursor; cursor = after;
        if (after != before + 1) return Collections.emptyList();
        List<SoundEvent> crossed = new ArrayList<SoundEvent>();
        for (SoundEvent event : events) if (event.getTick() > previous && event.getTick() <= after) crossed.add(event);
        return crossed;
    }
}
