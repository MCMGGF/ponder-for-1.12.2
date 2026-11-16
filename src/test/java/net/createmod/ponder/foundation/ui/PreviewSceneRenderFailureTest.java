package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PreviewSceneRenderFailureTest {
    private List<String> originalWarnings;

    @BeforeEach
    void retainLogDeduplication() throws Exception {
        originalWarnings = new ArrayList<>(warnings());
    }

    @AfterEach
    void restoreLogDeduplication() throws Exception {
        warnings().clear();
        warnings().addAll(originalWarnings);
    }

    @Test
    void aGloballyDeduplicatedErrorIsStillRecordedInEveryScreen() throws Exception {
        PonderUI first = PonderUI.showcase(null, 0);
        PonderUI second = PonderUI.showcase(null, 0);
        report(first, "test:shared-model");
        report(second, "test:shared-model");
        assertEquals(java.util.Collections.singletonList("test:shared-model"), first.getPreviewRenderFailureKeys());
        assertEquals(first.getPreviewRenderFailureKeys(), second.getPreviewRenderFailureKeys());
        assertTrue(warnings().contains("test:shared-model"));
    }

    @Test
    void clearingTheSceneDoesNotPreventItReportingTheSameFailureAgain() throws Exception {
        PonderUI screen = PonderUI.showcase(null, 0);
        report(screen, "test:scene-reset");
        clearScene(screen);
        assertTrue(screen.getPreviewRenderFailureKeys().isEmpty());
        assertTrue(warnings().contains("test:scene-reset"));
        report(screen, "test:scene-reset");
        assertEquals(java.util.Collections.singletonList("test:scene-reset"), screen.getPreviewRenderFailureKeys());
    }

    @Test
    void failureSnapshotsAreImmutableAndDoNotRetainTheSceneCollection() throws Exception {
        PonderUI screen = PonderUI.showcase(null, 0);
        report(screen, "test:snapshot");
        List<String> snapshot = screen.getPreviewRenderFailureKeys();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add("injected"));
        clearScene(screen);
        assertEquals(1, snapshot.size());
        assertTrue(screen.getPreviewRenderFailureKeys().isEmpty());
    }

    @Test
    void perSceneErrorInventoryIsBoundedAndReportsOverflow() throws Exception {
        PonderUI screen = PonderUI.showcase(null, 0);
        for (int i = 0; i < 140; i++) report(screen, "test:bounded:" + i);
        assertEquals(129, screen.getPreviewRenderFailureKeys().size());
        assertTrue(screen.getPreviewRenderFailureKeys().contains("additional-render-failures-truncated"));
    }

    private static void report(PonderDebugScreen screen, String key) throws Exception {
        warnings().add(key);
        Method method = PonderDebugScreen.class.getDeclaredMethod("warnRenderFailure", String.class,
            String.class, Throwable.class);
        method.setAccessible(true);
        method.invoke(screen, key, "Synthetic render failure", new IllegalStateException("test"));
    }

    private static void clearScene(PonderDebugScreen screen) throws Exception {
        Method method = PonderDebugScreen.class.getDeclaredMethod("clearPreviewWorld");
        method.setAccessible(true);
        method.invoke(screen);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> warnings() throws Exception {
        Field field = PonderDebugScreen.class.getDeclaredField("WARNED_RENDER_FAILURES");
        field.setAccessible(true);
        return (Set<String>) field.get(null);
    }
}
