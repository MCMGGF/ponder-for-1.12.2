package net.createmod.ponder.foundation.ui;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Shared by block uploads and native TESRs, including exceptional uploads. */
final class PreviewClientArrayGuard {

    private PreviewClientArrayGuard() {
    }

    static void run(Runnable render) {
        run(new NativeAccess(), render);
    }

    static void run(Access access, Runnable render) {
        access.push();
        try {
            render.run();
        } finally {
            try {
                access.pop();
            } finally {
                access.resetColor();
            }
        }
    }

    interface Access {
        void push();
        void pop();
        void resetColor();
    }

    private static final class NativeAccess implements Access {
        public void push() { GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT); }
        public void pop() { GL11.glPopClientAttrib(); }
        public void resetColor() { GlStateManager.resetColor(); }
    }
}
