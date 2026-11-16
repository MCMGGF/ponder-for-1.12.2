package net.createmod.ponder.foundation.content.mmce;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class MmceModelResourcesTest {

    @Test
    void enforcesExactReadLimit() throws IOException {
        byte[] bytes = new byte[16];
        assertArrayEquals(bytes, MmceModelResources.readLimited(new ByteArrayInputStream(bytes), 16));
        assertThrows(IOException.class, () ->
            MmceModelResources.readLimited(new ByteArrayInputStream(bytes), 15));
    }

    @Test
    void acceptsOpaqueAndCutoutTextures() throws IOException {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF4080C0);
        image.setRGB(1, 0, 0x004080C0);
        MmceModelResources.validateTexture(png(image));
    }

    @Test
    void rejectsPartialAlphaRatherThanMisrepresentingTransparency() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x804080C0);
        byte[] bytes = png(image);
        assertThrows(IOException.class, () -> MmceModelResources.validateTexture(bytes));
    }

    @Test
    void rejectsMalformedAndOversizedTextureDimensions() throws IOException {
        assertThrows(IOException.class, () -> MmceModelResources.validateTexture(new byte[16]));
        byte[] bytes = png(new BufferedImage(1025, 1, BufferedImage.TYPE_INT_ARGB));
        assertThrows(IOException.class, () -> MmceModelResources.validateTexture(bytes));
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        image.flush();
        return output.toByteArray();
    }
}
