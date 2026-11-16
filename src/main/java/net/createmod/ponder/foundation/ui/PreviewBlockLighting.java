package net.createmod.ponder.foundation.ui;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** BLOCK vertices already contain face shading and lightmap UVs, but no lighting normals. */
final class PreviewBlockLighting {

    private PreviewBlockLighting() {
    }

    static void runUnlit(Runnable render) {
        runUnlit(new NativeAccess(), render);
    }

    static void runUnlit(Access access, Runnable render) {
        boolean previous = access.enabled();
        try {
            access.enabled(false);
            render.run();
        } finally {
            access.enabled(previous);
        }
    }

    interface Access {
        boolean enabled();
        void enabled(boolean value);
    }

    private static final class NativeAccess implements Access {
        public boolean enabled() { return GL11.glIsEnabled(GL11.GL_LIGHTING); }

        public void enabled(boolean value) {
            if (value) {
                GL11.glEnable(GL11.GL_LIGHTING);
                GlStateManager.enableLighting();
            } else {
                GL11.glDisable(GL11.GL_LIGHTING);
                GlStateManager.disableLighting();
            }
        }
    }
}
