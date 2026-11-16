package net.createmod.ponder.foundation.ui;

import java.io.IOException;
import java.util.Locale;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;

/** Reads an installed texture and draws explicitly schematic panels without opening a container. */
final class ThermalGuiSnapshot implements PonderGuiSnapshotRegistry.SnapshotRenderer {
    static final int BASE_WIDTH = 198;
    static final int BASE_HEIGHT = 166;
    static final int PANEL_HEIGHT = 92;
    final ResourceLocation texture;
    final String panel;
    final int logicalWidth;
    final int logicalHeight;
    final boolean machineVisible;
    final int panelX;
    final int panelY;
    final int panelWidth;
    final int panelHeight;
    private final TextureGuiSnapshot machine;
    private Boolean available;

    static ThermalGuiSnapshot base(String machine) {
        return new ThermalGuiSnapshot(machine, null, BASE_WIDTH, BASE_HEIGHT, true);
    }

    static ThermalGuiSnapshot panel(String machine, String panel, int width, int height) {
        return new ThermalGuiSnapshot(machine, panel, width, height, false);
    }

    static ThermalGuiSnapshot composite(String machine, String panel, int width) {
        return new ThermalGuiSnapshot(machine, panel, width, BASE_HEIGHT, true);
    }

    static String machineFromGuiClass(String name) {
        String prefix = "cofh.thermalexpansion.gui.client.machine.Gui";
        if (name == null || !name.startsWith(prefix)) return null;
        String machine = name.substring(prefix.length()).toLowerCase(Locale.ROOT);
        return validMachine(machine) ? machine : null;
    }

    private ThermalGuiSnapshot(String machineName, String panelName, int width, int height,
        boolean machineVisible) {
        if (!validMachine(machineName)) throw new IllegalArgumentException("Invalid Thermal machine texture name");
        if (panelName != null && !validPanel(panelName)) throw new IllegalArgumentException("Unknown Thermal schematic panel");
        if (width <= 0 || height <= 0 || (!machineVisible && panelName == null))
            throw new IllegalArgumentException("A Thermal snapshot requires a positive display area");
        texture = new ResourceLocation("thermalexpansion", "textures/gui/machine/" + machineName + ".png");
        panel = panelName;
        logicalWidth = width;
        logicalHeight = height;
        this.machineVisible = machineVisible;
        panelWidth = panelName == null ? 0 : machineVisible ? ("redstone".equals(panelName) ? 112 : 100) : width;
        panelHeight = panelName == null ? 0 : machineVisible ? PANEL_HEIGHT : height;
        if (machineVisible && (width < BASE_WIDTH || height < BASE_HEIGHT
            || panelName != null && width < BASE_WIDTH - 22 + panelWidth)
            || !machineVisible && (width < 48 || height < 48))
            throw new IllegalArgumentException("Thermal snapshot content must fit its declared area");
        panelX = machineVisible ? width - panelWidth : 0;
        panelY = machineVisible ? 30 : 0;
        machine = new TextureGuiSnapshot(texture, BASE_WIDTH, BASE_HEIGHT, 256, 256);
    }

    private static boolean validMachine(String name) {
        return name != null && name.matches("[a-z0-9_]+");
    }

    private static boolean validPanel(String name) {
        return "augment".equals(name) || "config".equals(name) || "redstone".equals(name);
    }

    @Override
    public void render(int x, int y, int width, int height, float currentTick, float fade) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || width <= 0 || height <= 0 || !(fade > 0)) return;
        if (!available(mc)) {
            SandboxTriggeredBlockGuiSnapshot.renderUnavailable(x, y, width, height, texture.toString());
            return;
        }
        PreviewRenderState.runOverlay(() -> {
            try (PreviewModelViewGuard matrix = new PreviewModelViewGuard()) {
                GlStateManager.translate(x, y, 0);
                GlStateManager.scale((float) width / logicalWidth, (float) height / logicalHeight, 1);
                if (machineVisible) machine.render(0, 0, BASE_WIDTH, BASE_HEIGHT, currentTick, fade);
                if (panel != null) drawPanel(mc.fontRenderer, fade);
            }
        });
    }

    private boolean available(Minecraft mc) {
        return available(location -> {
            try (IResource ignored = mc.getResourceManager().getResource(location)) {
                return true;
            } catch (IOException failure) {
                return false;
            }
        });
    }

    boolean available(Predicate<ResourceLocation> lookup) {
        if (available == null) {
            available = Boolean.valueOf(lookup.test(texture));
        }
        return available.booleanValue();
    }

    private void drawPanel(FontRenderer font, float fade) {
        if (font == null) return;
        int x = panelX, y = panelY, width = panelWidth, height = panelHeight;
        Gui.drawRect(x, y, x + width, y + height, color(0x95A3AA, fade));
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, color(0x20292F, fade));
        int lineHeight = Math.max(1, font.FONT_HEIGHT) + 2;
        drawLabel(font, I18n.format("ponder.snapshot.thermal.panel." + panel), x, y + 4, width, fade);
        drawLabel(font, I18n.format("ponder.snapshot.thermal.schematic"), x, y + 4 + lineHeight, width, fade);
        int top = y + 6 + 2 * lineHeight;
        int bottom = y + height - lineHeight - 5;
        if (bottom - top >= 8) drawDiagram(x, top, width, bottom - top, fade);
        drawLabel(font, I18n.format("ponder.snapshot.thermal.not_live"), x, y + height - lineHeight - 2, width, fade);
    }

    private void drawDiagram(int x, int y, int width, int height, float fade) {
        if ("augment".equals(panel)) {
            int size = Math.min(16, Math.min((width - 16) / 2, (height - 4) / 2));
            int startX = x + (width - 2 * size - 4) / 2;
            int startY = y + (height - 2 * size - 4) / 2;
            for (int row = 0; row < 2; row++) for (int column = 0; column < 2; column++)
                drawSlot(startX + column * (size + 4), startY + row * (size + 4), size, 0xBBC7CC, fade);
        } else if ("config".equals(panel)) {
            int size = Math.max(1, Math.min(11, Math.min((width - 16) / 3, (height - 4) / 3)));
            int startX = x + (width - 3 * size - 4) / 2;
            int startY = y + (height - 3 * size - 4) / 2;
            for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++)
                drawSlot(startX + column * (size + 2), startY + row * (size + 2), size,
                    row == 1 && column == 1 ? 0xC3C9CC : 0x698691, fade);
        } else {
            int size = Math.max(2, Math.min(16, Math.min((width - 16) / 3, height - 4)));
            int startX = x + (width - 3 * size - 8) / 2;
            int startY = y + (height - size) / 2;
            for (int column = 0; column < 3; column++)
                drawSlot(startX + column * (size + 4), startY, size, 0xBA6D73, fade);
        }
    }

    private static void drawSlot(int x, int y, int size, int border, float fade) {
        Gui.drawRect(x, y, x + size, y + size, color(border, fade));
        if (size > 2) Gui.drawRect(x + 1, y + 1, x + size - 1, y + size - 1, color(0x36434A, fade));
    }

    private static void drawLabel(FontRenderer font, String text, int x, int y, int width, float fade) {
        PreviewTextLayout layout = PreviewTextLayout.centered(font, text, x + 4, x + width - 4);
        font.drawString(layout.text, layout.x, y, color(0xE2E8EB, fade));
    }

    private static int color(int rgb, float fade) {
        return Math.max(4, Math.round(Math.max(0, Math.min(1, fade)) * 255)) << 24 | rgb;
    }

    @Override
    public void reset() {
        available = null;
        machine.reset();
    }
}
