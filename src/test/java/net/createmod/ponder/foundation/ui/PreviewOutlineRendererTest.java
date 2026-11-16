package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

class PreviewOutlineRendererTest {

    @Test
    void hiddenEdgesAreFaintVisibleEdgesAreClearAndOriginalStateIsRestored() {
        Access access = new Access();
        PreviewOutlineRenderer.render(access, Collections.emptyList());
        assertEquals(Arrays.asList("save", "configure", "draw:" + GL11.GL_GREATER + ":0.2",
            "draw:" + GL11.GL_LEQUAL + ":0.95", "restore"), access.calls);
    }

    @Test
    void exceptionalConfigurationOrUploadsStillRestoreStateAndWidth() {
        for (String failure : Arrays.asList("configure", "draw:" + GL11.GL_GREATER + ":0.2",
            "draw:" + GL11.GL_LEQUAL + ":0.95")) {
            Access access = new Access();
            access.failAt = failure;
            assertThrows(IllegalStateException.class,
                () -> PreviewOutlineRenderer.render(access, Collections.emptyList()));
            assertEquals("restore", access.calls.get(access.calls.size() - 1));
        }
    }

    private static final class Access implements PreviewOutlineRenderer.Access {
        private final Object token = new Object();
        private final List<String> calls = new ArrayList<>();
        private String failAt;

        public Object save() { record("save"); return token; }
        public void configure() { record("configure"); }
        public void draw(List<PreviewOutlineGeometry.Outline> outlines, int function, float alpha) {
            record("draw:" + function + ':' + alpha);
        }
        public void restore(Object previous) {
            assertSame(token, previous);
            record("restore");
        }
        private void record(String call) {
            calls.add(call);
            if (call.equals(failAt)) {
                throw new IllegalStateException("simulated " + call);
            }
        }
    }
}
