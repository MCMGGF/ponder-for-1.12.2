package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

class PreviewBlockBufferTest {

    @Test
    void realBlockVerticesReceiveBothBrightnessAndAlphaBeforeUpload() {
        PreviewBlockBuffer renderer = new PreviewBlockBuffer();
        renderer.draw(0.5F, 0.5F, buffer -> quad(buffer, 200, 100, 50, 128), buffer -> {
            assertEquals(4, buffer.getVertexCount());
            for (int vertex = 0; vertex < 4; vertex++) {
                assertArrayEquals(new int[] { 100, 50, 25, 64 }, color(buffer, vertex));
            }
            assertEquals(1F, buffer.getByteBuffer().getFloat(0));
            assertEquals(2F, buffer.getByteBuffer().getFloat(4));
            assertEquals(3F, buffer.getByteBuffer().getFloat(8));
        });
    }

    @Test
    void fadePreservesExistingFluidAndModelColorAndAlpha() {
        PreviewBlockBuffer renderer = new PreviewBlockBuffer();
        renderer.draw(1F, 0.25F, buffer -> quad(buffer, 21, 77, 133, 64), buffer -> {
            assertArrayEquals(new int[] { 21, 77, 133, 16 }, color(buffer, 0));
        });
    }

    @Test
    void colorBytePositionsDoNotDependOnTheBufferIntegerOrder() {
        for (ByteOrder order : new ByteOrder[] { ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN }) {
            new PreviewBlockBuffer().draw(0.5F, 0.25F, buffer -> {
                quad(buffer, 200, 100, 40, 128);
                buffer.getByteBuffer().order(order);
            }, buffer -> assertArrayEquals(new int[] { 100, 50, 20, 32 }, color(buffer, 0)));
        }
    }

    @Test
    void nearZeroFadeDoesNotKeepTheOldTwentyEightPercentOpacityFloor() {
        PonderSceneRuntime.RuntimeBlockState block = new PonderSceneRuntime.RuntimeBlockState(BlockPos.ORIGIN);
        block.fade = 0.02F;
        assertEquals(0.02F, PreviewBlockRendering.alpha(block));
        new PreviewBlockBuffer().draw(1F, PreviewBlockRendering.alpha(block),
            buffer -> quad(buffer, 255, 255, 255, 255),
            buffer -> assertEquals(5, color(buffer, 0)[3]));
    }

    @Test
    void identityTintLeavesTheCompleteBlockBufferUnchanged() {
        BufferBuilder buffer = new BufferBuilder(256);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        quad(buffer, 11, 73, 185, 92);
        byte[] before = bytes(buffer);
        PreviewBlockBuffer.multiplyColor(buffer, 1F, 1F);
        assertArrayEquals(before, bytes(buffer));
        PreviewBlockBuffer.discard(buffer);
    }

    @Test
    void emptyModelCanFinishAndBeFollowedByARealModel() {
        PreviewBlockBuffer renderer = new PreviewBlockBuffer();
        renderer.draw(1F, 1F, buffer -> { }, buffer -> assertEquals(0, buffer.getVertexCount()));
        renderer.draw(1F, 1F, buffer -> quad(buffer, 255, 255, 255, 255),
            buffer -> assertEquals(4, buffer.getVertexCount()));
    }

    @Test
    void emitterFailureClosesAndDiscardsThePoisonedBuffer() {
        PreviewBlockBuffer renderer = new PreviewBlockBuffer();
        BufferBuilder[] failed = new BufferBuilder[1];
        IllegalStateException failure = new IllegalStateException("bad model");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> renderer.draw(1F, 1F, buffer -> {
            failed[0] = buffer;
            quad(buffer, 255, 255, 255, 255);
            throw failure;
        }, buffer -> {
            throw new AssertionError("failed geometry must not upload");
        })));
        failed[0].begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        PreviewBlockBuffer.discard(failed[0]);
        renderer.draw(1F, 1F, buffer -> {
            assertNotSame(failed[0], buffer);
            quad(buffer, 255, 255, 255, 255);
        }, buffer -> assertEquals(4, buffer.getVertexCount()));
    }

    @Test
    void uploadFailureDoesNotBreakTheFollowingBlock() {
        PreviewBlockBuffer renderer = new PreviewBlockBuffer();
        BufferBuilder[] failed = new BufferBuilder[1];
        assertThrows(NoSuchMethodError.class, () -> renderer.draw(1F, 1F,
            buffer -> quad(buffer, 255, 255, 255, 255), buffer -> {
                failed[0] = buffer;
                throw new NoSuchMethodError("legacy uploader");
            }));
        failed[0].begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        PreviewBlockBuffer.discard(failed[0]);
        renderer.draw(1F, 1F, buffer -> {
            assertNotSame(failed[0], buffer);
            quad(buffer, 255, 255, 255, 255);
        }, buffer -> assertEquals(4, buffer.getVertexCount()));
    }

    @Test
    void nativeTesrFailureCanDiscardASharedBufferThatWasLeftBuilding() {
        BufferBuilder shared = new BufferBuilder(256);
        shared.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        quad(shared, 255, 255, 255, 255);
        PreviewBlockBuffer.discard(shared);
        shared.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        assertEquals(0, shared.getVertexCount());
        PreviewBlockBuffer.discard(shared);
        PreviewBlockBuffer.discard(shared);
    }

    @Test
    void bufferTranslationFromOneEmitterCannotMoveTheFollowingBlock() {
        PreviewBlockBuffer renderer = new PreviewBlockBuffer();
        renderer.draw(1F, 1F, buffer -> {
            buffer.setTranslation(100D, 200D, 300D);
            quad(buffer, 255, 255, 255, 255);
        }, buffer -> assertEquals(101F, buffer.getByteBuffer().getFloat(0)));
        renderer.draw(1F, 1F, buffer -> quad(buffer, 255, 255, 255, 255),
            buffer -> assertEquals(1F, buffer.getByteBuffer().getFloat(0)));
    }

    private static void quad(BufferBuilder buffer, int red, int green, int blue, int alpha) {
        for (int vertex = 0; vertex < 4; vertex++) {
            buffer.pos(1D + vertex, 2D, 3D).color(red, green, blue, alpha).tex(0.25D, 0.5D)
                .lightmap(240, 240).endVertex();
        }
    }

    private static int[] color(BufferBuilder buffer, int vertex) {
        int offset = vertex * buffer.getVertexFormat().getIntegerSize() * Integer.BYTES
            + buffer.getVertexFormat().getColorOffset();
        ByteBuffer bytes = buffer.getByteBuffer();
        return new int[] { bytes.get(offset) & 255, bytes.get(offset + 1) & 255,
            bytes.get(offset + 2) & 255, bytes.get(offset + 3) & 255 };
    }

    private static byte[] bytes(BufferBuilder buffer) {
        ByteBuffer view = buffer.getByteBuffer().duplicate();
        view.position(0);
        byte[] result = new byte[buffer.getVertexCount() * buffer.getVertexFormat().getIntegerSize() * Integer.BYTES];
        view.get(result);
        return result;
    }
}
