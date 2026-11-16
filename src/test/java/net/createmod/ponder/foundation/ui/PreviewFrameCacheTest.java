package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class PreviewFrameCacheTest {

    @Test
    void pausedFramesReuseTheHeavyStateAndPreparedBlockLists() {
        PreviewFrameCache<List<Integer>> cache = new PreviewFrameCache<List<Integer>>();
        Object scene = new Object();
        AtomicInteger builds = new AtomicInteger();
        Supplier<List<Integer>> factory = () -> {
            builds.incrementAndGet();
            List<Integer> blocks = new ArrayList<Integer>();
            for (int i = 0; i < 8192; i++) {
                blocks.add(i);
            }
            return blocks;
        };
        List<Integer> first = cache.get(scene, 140.0F, factory);
        for (int frame = 0; frame < 600; frame++) {
            assertSame(first, cache.get(scene, 140.0F, factory));
        }
        assertEquals(1, builds.get());
        assertEquals(8192, first.size());
    }

    @Test
    void fractionalPlaybackAndSeekingBothInvalidateTheFrame() {
        PreviewFrameCache<Object> cache = new PreviewFrameCache<Object>();
        Object scene = new Object();
        Object paused = cache.get(scene, 5.0F, Object::new);
        Object partial = cache.get(scene, 5.25F, Object::new);
        Object rewound = cache.get(scene, 2.0F, Object::new);
        assertNotSame(paused, partial);
        assertNotSame(partial, rewound);
        assertSame(rewound, cache.get(scene, 2.0F, Object::new));
    }

    @Test
    void sceneIdentityAndExplicitReleaseInvalidateTheFrame() {
        PreviewFrameCache<Object> cache = new PreviewFrameCache<Object>();
        String firstScene = new String("same-id");
        String replacementScene = new String("same-id");
        Object first = cache.get(firstScene, 0, Object::new);
        Object replacement = cache.get(replacementScene, 0, Object::new);
        assertNotSame(first, replacement);
        cache.clear();
        assertNotSame(replacement, cache.get(replacementScene, 0, Object::new));
    }

    @Test
    void failedBuildsDoNotPublishOrCorruptThePreviousFrame() {
        PreviewFrameCache<Object> cache = new PreviewFrameCache<Object>();
        Object scene = new Object();
        Object first = cache.get(scene, 1, Object::new);
        assertThrows(IllegalStateException.class, () -> cache.get(scene, 2, () -> {
            throw new IllegalStateException("test failure");
        }));
        assertSame(first, cache.get(scene, 1, Object::new));
        assertNotSame(first, cache.get(scene, 2, Object::new));
    }
}
