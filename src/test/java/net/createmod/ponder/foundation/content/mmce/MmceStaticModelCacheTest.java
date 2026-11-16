package net.createmod.ponder.foundation.content.mmce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MmceStaticModelCacheTest {

    @Test
    void reloadInvalidatesSuccessfulAndFailedEntries() {
        MmceStaticModelPreviews cache = new MmceStaticModelPreviews();
        cache.remember("missing", missing());
        cache.remember("model", withMesh(MmceStaticModelMeshTest.quad()));
        long before = cache.getResourceGeneration();
        assertEquals(2, cache.getCachedCount());
        assertEquals(128, cache.getCachedBytes());
        cache.onResourceManagerReload(null);
        assertEquals(0, cache.getCachedCount());
        assertEquals(0, cache.getCachedBytes());
        assertTrue(cache.getResourceGeneration() > before);
    }

    @Test
    void limitsFailureEntriesAndCorrectlyReplacesSuccessWeight() {
        MmceStaticModelPreviews cache = new MmceStaticModelPreviews();
        cache.remember("model", withMesh(MmceStaticModelMeshTest.quad()));
        cache.remember("model", missing());
        assertEquals(0, cache.getCachedBytes());
        for (int i = 0; i < 30; i++) {
            cache.remember("missing" + i, missing());
        }
        assertEquals(MmceStaticModelPreviews.MAX_ENTRIES, cache.getCachedCount());
    }

    @Test
    void boundsRetainedMeshBytes() {
        MmceStaticModelPreviews cache = new MmceStaticModelPreviews();
        float[] data = new float[MmceStaticModelMesh.MAX_VERTICES * MmceStaticModelMesh.STRIDE];
        for (int i = 0; i < 8; i++) {
            cache.remember("large" + i, withMesh(data));
        }
        assertTrue(cache.getCachedBytes() <= MmceStaticModelPreviews.MAX_BYTES);
        assertEquals(5, cache.getCachedCount());
        cache.clear();
        assertEquals(0, cache.getCachedBytes());
    }

    private static MmceStaticModelPreviews.Preview missing() {
        return new MmceStaticModelPreviews.Preview(MmceStaticModelSpec.unavailable("没有默认绑定"),
            null, "没有默认绑定", 0, 0);
    }

    private static MmceStaticModelPreviews.Preview withMesh(float[] data) {
        return new MmceStaticModelPreviews.Preview(
            MmceStaticModelSpec.bound("test", "test:model.geo.json", "test:model.png", ""),
            new MmceStaticModelMesh(data, data.length, 1, 1), MmceStaticModelSpec.STATIC_NOTE, 0, 0);
    }
}
