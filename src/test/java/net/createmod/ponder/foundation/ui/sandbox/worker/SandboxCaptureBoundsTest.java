package net.createmod.ponder.foundation.ui.sandbox.worker;

import net.createmod.ponder.foundation.ui.BlockGuiDemoDefinition.Insets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SandboxCaptureBoundsTest {
    @Test
    void rightHandSidebarUsesTheAvailableAreaAfterLoweringGuiScale() {
        Insets tabs = new Insets(24, 0, 112, 0);
        var small = SandboxFrames.Bounds.container(72, 37, 176, 166, tabs);
        assertFalse(small.fits(320, 240));
        assertEquals(new SandboxFrames.Bounds(48, 37, 320, 203), small.visible(320, 240));
        var larger = SandboxFrames.Bounds.container(152, 97, 176, 166, tabs);
        assertTrue(larger.fits(480, 360));
        assertEquals(larger, larger.visible(480, 360));
    }

    @Test
    void oversizedGuiAtMinimumScaleCapturesTheVisiblePixelsIncludingTheOrigin() {
        var offscreen = SandboxFrames.Bounds.container(-20, -8, 400, 300, new Insets(32, 32, 32, 32));
        assertEquals(new SandboxFrames.Bounds(0, 0, 320, 240), offscreen.visible(320, 240));
    }
}
