package net.createmod.ponder.foundation.ui;

import java.nio.FloatBuffer;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Repairs extra native-renderer pushes without pretending to reconstruct popped caller history. */
final class PreviewModelViewGuard implements AutoCloseable {

    static final int MAX_RECOVERY_POPS = 1024;
    private final Access access;
    private final int previousMode;
    private final int guardedMode;
    private final int entryDepth;
    private final float[] entryMatrix;
    private boolean closed;

    PreviewModelViewGuard() {
        this(GL11.GL_MODELVIEW);
    }

    PreviewModelViewGuard(int mode) {
        this(new NativeAccess(mode), mode);
    }

    PreviewModelViewGuard(Access access) {
        this(access, GL11.GL_MODELVIEW);
    }

    private PreviewModelViewGuard(Access access, int mode) {
        this.access = access;
        guardedMode = mode;
        previousMode = access.mode();
        access.mode(guardedMode);
        try {
            entryDepth = access.depth();
            entryMatrix = access.matrix();
            access.push();
        } catch (RuntimeException | Error failure) {
            access.mode(previousMode);
            throw failure;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            access.mode(guardedMode);
            int depth = access.depth();
            int extra = Math.max(0, depth - entryDepth);
            int recovery = Math.min(extra, MAX_RECOVERY_POPS);
            for (int i = 0; i < recovery; i++) {
                access.pop();
            }
            access.load(entryMatrix);
            if (depth < entryDepth || extra > MAX_RECOVERY_POPS || access.depth() != entryDepth) {
                throw new IllegalStateException("Tile renderer damaged the caller's model-view stack: entry="
                    + entryDepth + ", exit=" + depth);
            }
        } finally {
            access.mode(previousMode);
        }
    }

    interface Access {
        int mode();
        void mode(int mode);
        int depth();
        float[] matrix();
        void push();
        void pop();
        void load(float[] matrix);
    }

    private static final class NativeAccess implements Access {
        private static final PreviewGlQueries QUERIES = PreviewGlQueries.runtime();
        private final int mode;

        private NativeAccess(int mode) {
            this.mode = mode;
        }

        public int mode() { return GL11.glGetInteger(GL11.GL_MATRIX_MODE); }
        public void mode(int mode) { GlStateManager.matrixMode(mode); }
        public int depth() {
            return GL11.glGetInteger(mode == GL11.GL_PROJECTION
                ? GL11.GL_PROJECTION_STACK_DEPTH : GL11.GL_MODELVIEW_STACK_DEPTH);
        }

        public float[] matrix() {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(PreviewGlQueries.VECTOR_CAPACITY);
            QUERIES.floats(mode == GL11.GL_PROJECTION ? GL11.GL_PROJECTION_MATRIX : GL11.GL_MODELVIEW_MATRIX, buffer);
            float[] values = new float[16];
            buffer.get(values);
            return values;
        }

        public void push() { GlStateManager.pushMatrix(); }
        public void pop() { GlStateManager.popMatrix(); }

        public void load(float[] matrix) {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            buffer.put(matrix);
            buffer.flip();
            GlStateManager.loadIdentity();
            GlStateManager.multMatrix(buffer);
        }
    }
}
