package net.createmod.ponder.foundation.ui;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/**
 * Legacy adapter name retained for scene registration. Thermal native GUI
 * initialization writes the live container, so previews read installed textures only.
 */
final class EmbeddedReflectiveGuiSnapshot implements PonderGuiSnapshotRegistry.SnapshotRenderer {

    private final ThermalGuiSnapshot texture;
    private final String identifier;

    EmbeddedReflectiveGuiSnapshot(String guiClassName, String tileClassName) {
        identifier = tileClassName;
        String machine = ThermalGuiSnapshot.machineFromGuiClass(guiClassName);
        texture = machine == null ? null : ThermalGuiSnapshot.base(machine);
    }

    EmbeddedReflectiveGuiSnapshot(ResourceLocation blockId, int meta, @Nullable NBTTagCompound tileNbt) {
        identifier = blockId + "#" + meta;
        // Only this metadata binding has an explicit legacy alias. Other native
        // block GUI variants require their own reviewed display adapter.
        texture = new ResourceLocation("thermalexpansion", "machine").equals(blockId) && meta == 0
            && (tileNbt == null || tileNbt.getKeySet().isEmpty()) ? ThermalGuiSnapshot.base("furnace") : null;
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
