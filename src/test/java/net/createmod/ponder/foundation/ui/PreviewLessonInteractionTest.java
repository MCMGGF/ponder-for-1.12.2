package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.util.Collections;
import net.createmod.ponder.foundation.*;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

class PreviewLessonInteractionTest {
    @Test void stepsAndSkinChangesKeepCameraAndSceneSelection() throws Exception {
        PonderScene scene = new PonderScene(new PonderStoryBoardEntry((builder, util) -> {},
            "test", new ResourceLocation("test", "scene"), new ResourceLocation("test", "item")),
            new PonderLocalization());
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        builder.special().createPlayer(Vec3d.ZERO, 0);
        builder.addKeyframe();
        builder.idle(20);
        builder.addKeyframe();
        builder.idle(20);
        PonderUI screen = PonderUI.showcase(null, 0);
        field("compiledScenes").set(screen, Collections.singletonList(scene));
        field("previewYaw").setFloat(screen, 25F);
        field("previewPitch").setFloat(screen, 18F);
        field("previewZoom").setFloat(screen, 1.3F);
        GuiButton previous = new GuiButton(PonderDebugScreen.BUTTON_PREV_STEP, 0, 0, 20, 20, "|<");
        screen.buttonList.add(previous);
        screen.updateButtonState();
        assertFalse(previous.enabled);
        screen.playing = true;
        screen.updateScreen();
        assertEquals(1, screen.getPlaybackTickValue());
        assertTrue(previous.enabled, "autoplay must enable previous-step without another click");
        screen.stepLesson(1);
        assertEquals(20, screen.getPlaybackTickValue());
        assertFalse(screen.isPlaybackRunning());
        for (int id = PonderDebugScreen.BUTTON_SKIN_CURRENT; id <= PonderDebugScreen.BUTTON_SKIN_ALEX; id++) {
            screen.actionPerformed(new GuiButton(id, 0, 0, 20, 20, "skin"));
            assertEquals(20, screen.getPlaybackTickValue());
            assertSame(scene, screen.getSelectedScene());
            assertEquals(25F, field("previewYaw").getFloat(screen));
            assertEquals(18F, field("previewPitch").getFloat(screen));
            assertEquals(1.3F, field("previewZoom").getFloat(screen));
        }
        screen.stepLesson(-1);
        assertEquals(0, screen.getPlaybackTickValue());
        assertFalse(screen.isPlaybackRunning());
    }
    private static Field field(String name) throws Exception {
        Field field = PonderDebugScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
