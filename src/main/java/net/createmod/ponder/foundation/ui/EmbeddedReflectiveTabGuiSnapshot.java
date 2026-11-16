package net.createmod.ponder.foundation.ui;

/** Existing tab IDs now show explicit schematics without native GUI callbacks. */
final class EmbeddedReflectiveTabGuiSnapshot implements PonderGuiSnapshotRegistry.SnapshotRenderer {

    private final ThermalGuiSnapshot texture;
    private final String identifier;

    EmbeddedReflectiveTabGuiSnapshot(String guiClassName, String tileClassName, String tabFieldName,
        int panelWidth, int panelHeight) {
        identifier = tabFieldName;
        String panel = "augmentTab".equals(tabFieldName) ? "augment"
            : "configTab".equals(tabFieldName) ? "config"
            : "redstoneTab".equals(tabFieldName) ? "redstone" : null;
        String machine = ThermalGuiSnapshot.machineFromGuiClass(guiClassName);
        texture = panel == null || machine == null ? null
            : ThermalGuiSnapshot.panel(machine, panel, panelWidth, panelHeight);
    }

    @Override
    public void render(int x, int y, int width, int height, float currentTick, float fade) {
        if (texture == null) {
            SandboxTriggeredBlockGuiSnapshot.renderUnavailable(x, y, width, height, identifier);
        } else {
            texture.render(x, y, width, height, currentTick, fade);
        }
    }

    @Override
    public void reset() {
        if (texture != null) {
            texture.reset();
        }
    }
}
