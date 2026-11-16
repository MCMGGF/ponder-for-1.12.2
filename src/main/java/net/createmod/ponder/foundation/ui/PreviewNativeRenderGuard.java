package net.createmod.ponder.foundation.ui;

import org.lwjgl.opengl.GL11;

/** Native 3D callbacks keep the caller's pass, matrices, clip and viewport intact. */
final class PreviewNativeRenderGuard {

    private PreviewNativeRenderGuard() {
    }

    static void run(Runnable render) {
        PreviewRenderState.runIsolated(() -> PreviewClientArrayGuard.run(() ->
            run(new NativeAccess(), render)));
    }

    static void run(Access access, Runnable render) {
        access.pushRaster();
        try (Scope projection = access.guardProjection(); Scope modelView = access.guardModelView()) {
            render.run();
        } finally {
            access.popRaster();
        }
    }

    interface Access {
        void pushRaster();
        void popRaster();
        Scope guardProjection();
        Scope guardModelView();
    }

    interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    private static final class NativeAccess implements Access {
        public void pushRaster() { GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_VIEWPORT_BIT); }
        public void popRaster() { GL11.glPopAttrib(); }

        public Scope guardProjection() {
            PreviewModelViewGuard guard = new PreviewModelViewGuard(GL11.GL_PROJECTION);
            return guard::close;
        }

        public Scope guardModelView() {
            PreviewModelViewGuard guard = new PreviewModelViewGuard();
            return guard::close;
        }
    }
}
