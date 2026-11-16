package net.createmod.ponder.api.scene;

import net.minecraft.util.math.Vec3d;

public interface GuiSnapshotBuilder {

    GuiSnapshotBuilder pointAt(Vec3d vec);

    default GuiSnapshotBuilder pointAt(Selection selection) {
        return pointAt(selection == null || !selection.iterator().hasNext() ? (Vec3d) null : selection.getCenter());
    }

    GuiSnapshotBuilder independent(int y);

    default GuiSnapshotBuilder independent() {
        return independent(0);
    }

    GuiSnapshotBuilder placeNearTarget();

    GuiSnapshotBuilder offset(int x, int y);
}
