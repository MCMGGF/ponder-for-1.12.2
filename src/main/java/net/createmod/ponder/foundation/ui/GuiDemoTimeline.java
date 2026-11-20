package net.createmod.ponder.foundation.ui;

import com.google.gson.JsonObject;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.SnapshotProvider;

/** A GUI overlay definition measured in ticks from the start of the overlay. */
public sealed interface GuiDemoTimeline permits GuiDemoDefinition, BlockGuiDemoDefinition {
    static GuiDemoTimeline parse(JsonObject root) {
        return root.has("source") ? BlockGuiDemoDefinition.parse(root) : GuiDemoDefinition.parse(root);
    }

    int lastKeyframeTick();

    SnapshotProvider provider(int startTick);
}
