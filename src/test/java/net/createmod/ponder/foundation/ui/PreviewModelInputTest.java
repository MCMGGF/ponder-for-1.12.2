package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class PreviewModelInputTest {

    @Test
    void structureSelectionConsumesTheClickCancelsDragsAndKeepsCameraState() throws Exception {
        for (boolean timeline : new boolean[] { true, false }) {
            PonderDebugScreen screen = new PonderDebugScreen(null, 0);
            field("modelControls").set(screen, new PreviewModelControls(20, 200));
            PreviewModelMode<Object> mode = mode(screen);
            mode.chooseAppearance(Object::new, value -> true, value -> "owned mesh");
            PreviewPointerInput pointer = (PreviewPointerInput) field("pointerInput").get(screen);
            if (timeline) pointer.beginTimelineDrag(); else pointer.beginPreviewDrag();
            field("previewYaw").setFloat(screen, 12F);
            field("previewPitch").setFloat(screen, 35F);
            field("previewZoom").setFloat(screen, 0.62F);
            screen.mouseClicked(21, 201, 0);
            assertFalse(mode.isAppearance());
            assertFalse(pointer.isTimelineDragging());
            assertFalse(pointer.isPreviewDragging());
            assertEquals(12F, field("previewYaw").getFloat(screen));
            assertEquals(35F, field("previewPitch").getFloat(screen));
            assertEquals(0.62F, field("previewZoom").getFloat(screen));
        }
    }

    @Test
    void closingTheActualScreenDropsItsCurrentMeshReference() throws Exception {
        PonderDebugScreen screen = new PonderDebugScreen(null, 0);
        PreviewModelMode<Object> mode = mode(screen);
        mode.chooseAppearance(Object::new, value -> true, value -> "owned mesh");
        screen.onGuiClosed();
        assertNull(mode.getPreview());
        assertFalse(mode.wasAttempted());
        assertNull(field("modelControls").get(screen));
        assertNull(field("detachedPreviewWorld").get(screen));
    }

    @SuppressWarnings("unchecked")
    private static PreviewModelMode<Object> mode(PonderDebugScreen screen) throws Exception {
        return (PreviewModelMode<Object>) field("modelMode").get(screen);
    }

    private static Field field(String name) throws Exception {
        Field field = PonderDebugScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
