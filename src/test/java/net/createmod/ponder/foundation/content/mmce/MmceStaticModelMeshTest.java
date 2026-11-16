package net.createmod.ponder.foundation.content.mmce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class MmceStaticModelMeshTest {

    @Test
    void ownsItsVertexDataAndBounds() {
        float[] data = quad();
        MmceStaticModelMesh mesh = new MmceStaticModelMesh(data, data.length, 1, 1);
        data[0] = 999F;
        assertEquals(4, mesh.getVertexCount());
        assertEquals(128, mesh.getByteSize());
        assertEquals(0D, mesh.getBounds().minX);
        assertEquals(1D, mesh.getBounds().maxX);
        AtomicInteger count = new AtomicInteger();
        mesh.forEachVertex((x, y, z, u, v, nx, ny, nz) -> {
            count.incrementAndGet();
            assertEquals(0F, y);
            assertEquals(1F, ny);
        });
        assertEquals(4, count.get());
    }

    @Test
    void rejectsNonFiniteAndIncompleteQuads() {
        float[] data = quad();
        data[0] = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> new MmceStaticModelMesh(data, data.length, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MmceStaticModelMesh(quad(), 24, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MmceStaticModelMesh(quad(), 0, 1, 1));
    }

    @Test
    void absentBindingNeverGuessesResources() {
        MmceStaticModelSpec spec = MmceStaticModelSpec.unavailable("没有默认绑定");
        assertEquals("", spec.getGeometry());
        assertEquals("", spec.getTexture());
        assertEquals("", spec.getAnimation());
        assertEquals(false, spec.isBound());
    }

    static float[] quad() {
        return new float[] {
            0, 0, 0, 0, 0, 0, 1, 0,
            1, 0, 0, 1, 0, 0, 1, 0,
            1, 0, 1, 1, 1, 0, 1, 0,
            0, 0, 1, 0, 1, 0, 1, 0
        };
    }
}
