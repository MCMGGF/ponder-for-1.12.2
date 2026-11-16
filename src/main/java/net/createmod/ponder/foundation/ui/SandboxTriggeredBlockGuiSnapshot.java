package net.createmod.ponder.foundation.ui;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;

/**
 * Compatibility name for older storyboards. No block activation or GUI interception
 * is performed; only explicitly supported, detached GUI adapters may render.
 */
public final class SandboxTriggeredBlockGuiSnapshot implements PonderGuiSnapshotRegistry.SnapshotRenderer {

    private static final int MAX_CACHED_ADAPTERS = 128;
    private static final Map<String, SandboxTriggeredBlockGuiSnapshot> BY_BLOCK =
        new LinkedHashMap<String, SandboxTriggeredBlockGuiSnapshot>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, SandboxTriggeredBlockGuiSnapshot> eldest) {
                if (size() <= MAX_CACHED_ADAPTERS) {
                    return false;
                }
                eldest.getValue().reset();
                return true;
            }
        };

    public static final SandboxTriggeredBlockGuiSnapshot MINECRAFT_FURNACE =
        getOrCreate(new ResourceLocation("minecraft", "furnace"), 3);
    public static final SandboxTriggeredBlockGuiSnapshot THERMAL_FURNACE =
        getOrCreate(new ResourceLocation("thermalexpansion", "machine"), 0);

    private final ResourceLocation blockId;
    private final PonderGuiSnapshotRegistry.SnapshotRenderer renderer;

    public SandboxTriggeredBlockGuiSnapshot(ResourceLocation blockId, int meta) {
        this(blockId, meta, null);
    }

    public SandboxTriggeredBlockGuiSnapshot(ResourceLocation blockId, int meta,
        @Nullable NBTTagCompound configuredTileNbt) {
        this.blockId = blockId;
        if (new ResourceLocation("minecraft", "furnace").equals(blockId)
            || new ResourceLocation("minecraft", "lit_furnace").equals(blockId)) {
            renderer = EmbeddedGuiFurnaceSnapshot.INSTANCE;
        } else if (new ResourceLocation("thermalexpansion", "machine").equals(blockId)) {
            renderer = new EmbeddedReflectiveGuiSnapshot(blockId, meta, configuredTileNbt);
        } else {
            renderer = null;
        }
    }

    public static synchronized SandboxTriggeredBlockGuiSnapshot getOrCreate(ResourceLocation blockId, int meta) {
        return getOrCreate(blockId, meta, null);
    }

    public static synchronized SandboxTriggeredBlockGuiSnapshot getOrCreate(ResourceLocation blockId, int meta,
        @Nullable NBTTagCompound configuredTileNbt) {
        String key = blockId + "#" + meta + "#" + (configuredTileNbt == null ? "" : configuredTileNbt.toString());
        SandboxTriggeredBlockGuiSnapshot existing = BY_BLOCK.get(key);
        if (existing == null) {
            existing = new SandboxTriggeredBlockGuiSnapshot(blockId, meta, configuredTileNbt);
            BY_BLOCK.put(key, existing);
        }
        return existing;
    }

    @Override
    public void render(int x, int y, int width, int height, float currentTick, float fade) {
        if (renderer != null) {
            renderer.render(x, y, width, height, currentTick, fade);
            return;
        }
        renderUnavailable(x, y, width, height, blockId.toString());
    }

    static void renderUnavailable(int x, int y, int width, int height, String identifier) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null || width < 16 || height < 20) {
            return;
        }
        FontRenderer font = mc.fontRenderer;
        String message = "\u6b64\u754c\u9762\u6682\u65e0\u53ea\u8bfb\u9884\u89c8";
        int lineY = y + 6;
        for (String line : PreviewTextLayout.wrap(font, message, width - 12)) {
            if (lineY + font.FONT_HEIGHT > y + height - 6) {
                return;
            }
            font.drawString(line, x + 6, lineY, 0xEEEEEE);
            lineY += font.FONT_HEIGHT + 2;
        }
        if (lineY + font.FONT_HEIGHT <= y + height - 6) {
            font.drawString(font.trimStringToWidth(identifier, width - 12), x + 6, lineY, 0xAAAAAA);
        }
    }

    @Override
    public void reset() {
        if (renderer != null) {
            renderer.reset();
        }
    }

    public static synchronized void clearCache() {
        for (SandboxTriggeredBlockGuiSnapshot snapshot : BY_BLOCK.values()) {
            snapshot.reset();
        }
        BY_BLOCK.clear();
    }

    /** Retained for binary compatibility; previews never participate in game ticks. */
    @Deprecated
    public static void onClientTickAll() {
    }

    /** Retained for binary compatibility; previews never intercept another screen. */
    @Deprecated
    public static void onGuiOpenAll(GuiOpenEvent event) {
    }
}
