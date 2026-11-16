package net.createmod.ponder.authoring.practice;

import net.createmod.ponder.authoring.practice.PracticeDefinition.Menu;

/** Shared canonical coordinates for drawing and authored normalized click locations. */
public final class PracticeLayout {
    public static final int WIDTH = 180;
    public static final int HEIGHT = 152;
    private PracticeLayout() { }
    public static final class Viewport {
        public final int left;
        public final int top;
        public final float scale;
        private Viewport(int left, int top, float scale) { this.left = left; this.top = top; this.scale = scale; }
    }
    public static Viewport fit(int width, int height) {
        return fit(width, height, Menu.CHEST);
    }
    public static int height(Menu menu) { return HEIGHT + (menu == Menu.CHEST_LARGE ? 54 : 0); }
    public static Viewport fit(int width, int height, Menu menu) {
        float scale = Math.min(1.5F, Math.min(Math.max(1, width - 24) / (float) WIDTH,
            Math.max(1, height - 112) / (float) height(menu)));
        return new Viewport(Math.round((width - WIDTH * scale) / 2), 52, scale);
    }
    public static int x(Menu menu, int slot) {
        if (slot >= menu.slots) return 9 + (slot - menu.slots) % 9 * 18;
        if (menu == Menu.WORKBENCH) return slot == 0 ? 135 : 27 + (slot - 1) % 3 * 18;
        if (menu == Menu.FURNACE) return slot == 2 ? 117 : 45;
        return 9 + slot % 9 * 18;
    }
    public static int y(Menu menu, int slot) {
        if (slot >= menu.slots) return 77 + (menu == Menu.CHEST_LARGE ? 54 : 0) + (slot - menu.slots) / 9 * 18;
        if (menu == Menu.WORKBENCH) return slot == 0 ? 27 : 9 + (slot - 1) / 3 * 18;
        if (menu == Menu.FURNACE) return slot == 0 ? 9 : slot == 1 ? 45 : 27;
        if (menu == Menu.HOPPER) return 27;
        return 9 + slot / 9 * 18;
    }
    public static int hit(Menu menu, double x, double y) {
        for (int slot = 0; slot < menu.slots + 36; slot++) {
            int sx = x(menu, slot), sy = y(menu, slot);
            if (x >= sx && x < sx + 18 && y >= sy && y < sy + 18) return slot;
        }
        return -1;
    }
}
