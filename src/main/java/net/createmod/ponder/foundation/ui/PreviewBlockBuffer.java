package net.createmod.ponder.foundation.ui;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.lwjgl.opengl.GL11;

/** A failed preview model must never poison Minecraft's shared Tessellator. */
final class PreviewBlockBuffer {

    private BufferBuilder buffer;

    void draw(float brightness, float alpha, Consumer<BufferBuilder> emit, Consumer<BufferBuilder> upload) {
        if (buffer == null) {
            buffer = new BufferBuilder(4096);
        }
        boolean complete = false;
        try {
            buffer.setTranslation(0D, 0D, 0D);
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            emit.accept(buffer);
            multiplyColor(buffer, brightness, alpha);
            buffer.finishDrawing();
            upload.accept(buffer);
            complete = true;
        } finally {
            if (complete) {
                buffer.reset();
                buffer.setTranslation(0D, 0D, 0D);
            } else {
                BufferBuilder failed = buffer;
                buffer = null;
                discard(failed);
            }
        }
    }

    void clear() {
        buffer = null;
    }

    static void discard(BufferBuilder buffer) {
        try {
            // reset() alone leaves isDrawing set after an emitter throws.
            buffer.finishDrawing();
        } catch (IllegalStateException alreadyFinished) {
            // Uploaders may fail after finishDrawing() has already completed.
        } finally {
            buffer.reset();
            buffer.setTranslation(0D, 0D, 0D);
        }
    }

    static void multiplyColor(BufferBuilder buffer, float brightness, float alpha) {
        VertexFormat format = buffer.getVertexFormat();
        if (!format.hasColor()) {
            return;
        }
        float rgb = Math.max(0F, Math.min(1F, brightness));
        float opacity = Math.max(0F, Math.min(1F, alpha));
        if (rgb == 1F && opacity == 1F) {
            return;
        }
        ByteBuffer bytes = buffer.getByteBuffer();
        int stride = format.getIntegerSize() * Integer.BYTES;
        int color = format.getColorOffset();
        for (int vertex = 0; vertex < buffer.getVertexCount(); vertex++) {
            int offset = vertex * stride + color;
            for (int channel = 0; channel < 4; channel++) {
                int original = bytes.get(offset + channel) & 255;
                // GL_UNSIGNED_BYTE color arrays are RGBA bytes, independent of integer order.
                float multiplier = channel == 3 ? opacity : rgb;
                bytes.put(offset + channel, (byte) Math.round(original * multiplier));
            }
        }
    }
}
