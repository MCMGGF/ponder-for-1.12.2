package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class ProjectorConfigLayoutTest {
    @Test void readablePresentationOnlyAppliesToNativeScaleOneWithEnoughRoom() {
        assertEquals(2, new ProjectorConfigLayout(1280, 720, 1280, 720).scale);
        assertEquals(2, new ProjectorConfigLayout(1920, 1080, 1920, 1080).scale);
        assertEquals(2, new ProjectorConfigLayout(640, 480, 640, 480).scale);
        assertEquals(1, new ProjectorConfigLayout(640, 360, 1280, 720).scale);
        assertEquals(1, new ProjectorConfigLayout(320, 240, 1280, 960).scale);
        assertEquals(1, new ProjectorConfigLayout(639, 480, 639, 480).scale);
        assertEquals(1, new ProjectorConfigLayout(640, 479, 640, 479).scale);
        assertEquals(1, new ProjectorConfigLayout(320, 240, 320, 240).scale);
    }

    @Test void formAndControlRectanglesStayInsideEverySupportedViewport() {
        for (int[] size : new int[][] {{320,240,1280,960}, {640,360,1280,720}, {1280,720,1280,720},
            {1920,1080,1920,1080}, {640,480,640,480}, {960,540,1920,1080}}) {
            ProjectorConfigLayout layout = new ProjectorConfigLayout(size[0], size[1], size[2], size[3]);
            for (int id = 0; id < 4; id++) inside(layout, layout.field(id));
            for (int id = 0; id < 8; id++) inside(layout, layout.button(id));
            assertTrue(layout.button(5).bottom() < layout.height - 20);
            assertTrue(layout.button(3).width >= 90);
            for (int a = 0; a < 8; a++) {
                for (int b = a + 1; b < 8; b++) assertFalse(overlap(layout.button(a), layout.button(b)));
            }
            assertFalse(overlap(layout.field(1), layout.field(2)));
            assertFalse(overlap(layout.field(2), layout.field(3)));
        }
    }

    @Test void displayedButtonBoundsAndPointerBoundsShareExactlyOneTransform() {
        for (ProjectorConfigLayout layout : new ProjectorConfigLayout[] {
            new ProjectorConfigLayout(1280,720,1280,720), new ProjectorConfigLayout(320,240,1280,960)}) {
            for (int id = 0; id < 8; id++) {
                PreviewDebugLayout.Rect button = layout.button(id);
                assertTrue(button.contains(layout.localX(button.x * layout.scale), layout.localY(button.y * layout.scale)));
                assertTrue(button.contains(layout.localX(button.right() * layout.scale - 1),
                    layout.localY(button.bottom() * layout.scale - 1)));
                assertFalse(button.contains(layout.localX(button.x * layout.scale - 1), layout.localY(button.y * layout.scale)));
                assertFalse(button.contains(layout.localX(button.right() * layout.scale), layout.localY(button.y * layout.scale)));
            }
            assertEquals(-1, layout.localX(-1));
            assertEquals(-1, layout.localY(-1));
        }
    }

    @Test void textFieldAndDragCoordinatesUseTheSameTransformAsButtons() throws Exception {
        String source = source();
        assertTrue(source.contains("f.mouseClicked(localX,localY,b)"));
        assertTrue(source.contains("super.mouseClicked(localX,localY,b)"));
        assertTrue(source.contains("super.mouseClickMove(layout.localX(x),layout.localY(y),b,elapsed)"));
        assertTrue(source.contains("super.mouseReleased(layout.localX(x),layout.localY(y),b)"));
        assertTrue(source.contains("drawForm(layout.localX(x),layout.localY(y),partial)"));
        assertFalse(source.contains("gameSettings.guiScale"));
        assertFalse(source.contains("Mouse.set"));
    }

    @Test void scaledRenderingRestoresTheMatrixAndKeepsFullLocalizedButtonNames() throws Exception {
        String source = source();
        assertTrue(source.contains("PreviewRenderState.runOverlay("));
        int push = source.indexOf("GlStateManager.pushMatrix()"), draw = source.indexOf("drawForm(layout.localX(x)");
        int restore = source.indexOf("} finally {", draw), pop = source.indexOf("GlStateManager.popMatrix()", restore);
        assertTrue(push >= 0 && push < draw && draw < restore && restore < pop);
        assertTrue(source.contains("GlStateManager.matrixMode(GL11.GL_MODELVIEW);\n                GlStateManager.popMatrix()")
            || source.contains("GlStateManager.matrixMode(GL11.GL_MODELVIEW);\r\n                GlStateManager.popMatrix()"));
        assertTrue(source.contains("buttonLabels.put(id,label)"));
        assertTrue(source.contains("fontRenderer.trimStringToWidth(label,Math.max(1,bounds.width-8))"));
        assertTrue(source.contains("GuiUtils.drawHoveringText(Collections.singletonList(label)"));
        assertTrue(source.contains("addButton(3,tr(\"projection_choose\"))"));
        assertTrue(source.contains("addButton(4,tr(\"projection_restart\"))"));
    }

    @Test void invalidDimensionsAndUnknownControlsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectorConfigLayout(0,240,1280,960));
        assertThrows(IllegalArgumentException.class, () -> new ProjectorConfigLayout(320,240,0,960));
        ProjectorConfigLayout layout = new ProjectorConfigLayout(320,240,1280,960);
        assertThrows(IllegalArgumentException.class, () -> layout.field(4));
        assertThrows(IllegalArgumentException.class, () -> layout.button(8));
    }

    private static void inside(ProjectorConfigLayout layout, PreviewDebugLayout.Rect rect) {
        assertTrue(rect.width > 0 && rect.height > 0);
        assertTrue(rect.x >= 0 && rect.y >= 0 && rect.right() <= layout.width && rect.bottom() <= layout.height);
    }

    private static boolean overlap(PreviewDebugLayout.Rect a, PreviewDebugLayout.Rect b) {
        return a.x < b.right() && b.x < a.right() && a.y < b.bottom() && b.y < a.bottom();
    }

    private static String source() throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/net/createmod/ponder/foundation/ui/ProjectorConfigScreen.java")), StandardCharsets.UTF_8);
    }
}
