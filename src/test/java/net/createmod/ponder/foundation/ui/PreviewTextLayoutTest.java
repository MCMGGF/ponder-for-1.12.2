package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.SimpleResource;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

class PreviewTextLayoutTest {

    @Test
    void fixtureUsesVanillaFontMeasurementAndFormatting() {
        FontRenderer font = new MetricsFont();
        assertEquals(18, font.getStringWidth("\u673a\u5668"));
        assertEquals(20, font.getStringWidth("\u00a7l\u673a\u5668"));
        assertEquals(18, font.getStringWidth("\u00a7a\u673a\u5668"));
        assertEquals(8, font.getStringWidth("iW"));
        assertEquals(360, font.getStringWidth(longChinese()));
    }

    @Test
    void actorLabelsFitBeforeClampingTheirPosition() {
        FontRenderer font = new MetricsFont();
        for (String text : new String[] { longChinese(), "\u00a7a\u00a7l" + longChinese(),
            "iiiiWWWW " + longChinese() + "\u00a7r machine" }) {
            for (int preferredX : new int[] { -500, 230, 5000 }) {
                PreviewTextLayout label = PreviewTextLayout.inside(font, text, 123, 295, preferredX);
                assertEquals(font.trimStringToWidth(text, 172), label.text);
                assertEquals(font.getStringWidth(label.text), label.width);
                assertTrue(label.x >= 123);
                assertTrue(label.x + label.width <= 295);
                assertTrue(label.width <= 172);
                assertFalse(label.text.endsWith("\u00a7"));
            }
        }
    }

    @Test
    void centeredHintsFitTheScreenWithChineseFormattingAndVariableWidthGlyphs() {
        FontRenderer font = new MetricsFont();
        for (String text : new String[] { "\u00a7l" + longChinese(), "\u00a7aMMCE | " + longChinese(),
            "iWiWiW", "" }) {
            PreviewTextLayout label = PreviewTextLayout.centered(font, text, 18, 302);
            assertEquals(font.trimStringToWidth(text, 284), label.text);
            assertTrue(label.x >= 18 && label.x + label.width <= 302);
            assertTrue(Math.abs(label.x * 2 + label.width - 320) <= 1);
        }
    }

    static String longChinese() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            text.append("\u673a\u5668");
        }
        return text.toString();
    }

    /** Keeps vanilla measurement/wrapping code; only texture access and fixture glyph data are replaced. */
    static final class MetricsFont extends FontRenderer {
        MetricsFont() {
            super(new GameSettings(), new ResourceLocation("minecraft", "textures/font/ascii.png"), null, false);
            Arrays.fill(charWidth, 6);
            charWidth['i'] = 2;
            glyphWidth['\u673a'] = 15;
            glyphWidth['\u5668'] = 15;
        }

        @Override
        protected void bindTexture(ResourceLocation location) {
        }

        @Override
        protected IResource getResource(ResourceLocation location) {
            return new SimpleResource("font-test", location, new ByteArrayInputStream(new byte[65536]), null, null);
        }
    }
}
