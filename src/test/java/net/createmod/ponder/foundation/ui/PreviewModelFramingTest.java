package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PreviewModelFramingTest {

    @Test
    void firstExplicitAppearanceUsesTheUnionWithoutChangingItOnModeSwitches() {
        PreviewModelFraming framing = structure();
        float structureScale = framing.fitScale(600, 400, true, 0.92F);
        framing.include(-2, -1, 0, 9, 7, 6, true, 600, 400, true, 0.92F);
        assertEquals(3.5, framing.centerX(), 0.00001);
        assertEquals(3, framing.centerY(), 0.00001);
        assertEquals(3, framing.centerZ(), 0.00001);
        float unionScale = framing.fitScale(600, 400, true, 0.92F);
        assertTrue(unionScale < structureScale);
        framing.initialize(0, 0, 0, 3, 3, 3);
        assertEquals(3.5, framing.centerX(), 0.00001);
        assertEquals(unionScale, framing.fitScale(600, 400, true, 0.92F));
    }

    @Test
    void aReloadedLargerMeshPreservesExistingCenterAndPixelScale() {
        PreviewModelFraming framing = structure();
        framing.include(-2, 0, 0, 5, 4, 6, true, 600, 400, true, 0.92F);
        double x = framing.centerX();
        double y = framing.centerY();
        double z = framing.centerZ();
        float scale = framing.fitScale(600, 400, true, 0.92F);
        framing.include(-10, -7, -4, 20, 25, 17, false, 600, 400, true, 0.92F);
        assertEquals(x, framing.centerX(), 0.00001);
        assertEquals(y, framing.centerY(), 0.00001);
        assertEquals(z, framing.centerZ(), 0.00001);
        assertEquals(scale, framing.fitScale(600, 400, true, 0.92F), 0.00001);
        framing.resetCamera();
        assertEquals(5, framing.centerX(), 0.00001);
        assertTrue(framing.fitScale(600, 400, true, 0.92F) < scale);
    }

    @Test
    void closingAViewDiscardsItsUnionAndCompensation() {
        PreviewModelFraming framing = structure();
        framing.include(-50, -40, -30, 30, 20, 10, true, 320, 240, false, 1F);
        framing.clear();
        framing.initialize(0, 0, 0, 3, 3, 3);
        assertEquals(1.5, framing.centerX());
        assertEquals(1.5, framing.centerY());
        assertEquals(1.5, framing.centerZ());
        assertEquals(PreviewCameraMath.fitScale(3, 3, 3, 320, 240, false, 1F),
            framing.fitScale(320, 240, false, 1F));
    }

    private static PreviewModelFraming structure() {
        PreviewModelFraming framing = new PreviewModelFraming();
        framing.initialize(0, 0, 0, 3, 3, 3);
        return framing;
    }
}
