package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.SimpleResource;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

class PreviewLosslessTextWrapTest {

    @Test
    void exactRedstoneCaptionKeepsTheMissingRepeaterCharacter() {
        assertConserved("\u6253\u5f00\u62c9\u6746\uff0c\u4fe1\u53f7\u6cbf\u7ea2\u77f3\u7ebf\u8fdb\u5165\u4e2d\u7ee7\u5668\uff0c\u518d\u4f20\u5411\u7ea2\u77f3\u706f\u548c\u6d3b\u585e\u3002");
    }

    @Test
    void exactRailCaptionKeepsTheMissingRailAndStraightCharacters() {
        assertConserved("\u628a\u94c1\u8f68\u94fa\u5728\u652f\u6491\u65b9\u5757\u4e0a\u3002\u666e\u901a\u94c1\u8f68\u53ef\u5f62\u6210\u5f2f\u8f68\uff0c\u52a8\u529b\u94c1\u8f68\u53ea\u80fd\u76f4\u884c\u6216\u722c\u5761\u3002");
    }

    @Test
    void exactMissingSmeltingRecipeCaptionKeepsTheFinalRecipeCharacter() {
        assertConserved("\u5f53\u524d\u5b9e\u4f8b\u6ca1\u6709\u94c1\u77ff\u77f3\u7684\u7194\u7089\u914d\u65b9\u3002");
    }

    @Test
    void activeFontMeasurementNeverUsesItsInconsistentLegacyWrapOrTrim() {
        InconsistentFont font = new InconsistentFont();
        String text = "\u4e2d\u7ee7\u5668 \u666e\u901a\u94c1\u8f68 \ud83d\ude00";
        List<String> lines = PreviewTextLayout.wrap(font, text, 28);
        assertEquals(text, String.join("", lines));
        assertTrue(font.measureCalls > 0);
        assertEquals(0, font.wrapCalls);
        assertEquals(0, font.trimCalls);
        assertNotEquals(text, font.trimStringToWidth(text, 1000), "fixture deliberately loses content");
    }

    @Test
    void normalLinesLeaveOneLogicalPixelForFontRounding() {
        List<String> lines = PreviewTextLayout.wrap("ABCDEF", 19, PreviewLosslessTextWrapTest::measure);
        assertEquals(Arrays.asList("ABC", "DEF"), lines);
        for (String line : lines) assertTrue(measure(line) <= 18);
    }

    @Test
    void englishWordBreakKeepsTheOriginalSpaces() {
        List<String> lines = PreviewTextLayout.wrap("one two three", 37, PreviewLosslessTextWrapTest::measure);
        assertEquals(Arrays.asList("one ", "two ", "three"), lines);
        assertEquals("one two three", String.join("", lines));
    }

    @Test
    void multipleSpacesTabsAndLongWordsAreNotDiscarded() {
        String text = "  one  \ttwo supercalifragilistic";
        List<String> lines = PreviewTextLayout.wrap(text, 37, PreviewLosslessTextWrapTest::measure);
        assertEquals(text, String.join("", lines));
        assertTrue(lines.size() > 2);
        for (String line : lines) assertTrue(measure(line) <= 36);
    }

    @Test
    void hardBreaksKeepEmptyAndTrailingLinesAndNormalizeCrLfOnce() {
        assertEquals(Arrays.asList("A", "", "B", "C", ""),
            PreviewTextLayout.wrap("A\r\n\r\nB\rC\n", 100, PreviewLosslessTextWrapTest::measure));
    }

    @Test
    void emptyNullAndInvalidBudgetsAlwaysTerminate() {
        assertEquals(Collections.singletonList(""), PreviewTextLayout.wrap("", 0, s -> 0));
        assertEquals(Collections.singletonList(""), PreviewTextLayout.wrap((String) null, -1, s -> 0));
        for (int width : new int[] {Integer.MIN_VALUE, -1, 0, 1, 2}) {
            List<String> lines = PreviewTextLayout.wrap("\u4e2d\ud83d\ude00A", width, PreviewLosslessTextWrapTest::measure);
            assertEquals(Arrays.asList("\u4e2d", "\ud83d\ude00", "A"), lines);
        }
    }

    @Test
    void supplementaryCodePointsNeverSplitTheirSurrogatePairs() {
        String text = "\ud83d\ude00\u4e2d\ud83e\uddf1\ud83d\ude80";
        List<String> lines = PreviewTextLayout.wrap(text, 20, PreviewLosslessTextWrapTest::measure);
        assertEquals(text, String.join("", lines));
        for (String line : lines) {
            assertFalse(Character.isLowSurrogate(line.charAt(0)));
            assertFalse(Character.isHighSurrogate(line.charAt(line.length() - 1)));
        }
    }

    @Test
    void colorAndStylesContinueAcrossSoftAndHardBreaks() {
        String source = "\u00a7a\u00a7lABCD\nEF";
        assertEquals(Arrays.asList("\u00a7a\u00a7lAB", "\u00a7a\u00a7lCD", "\u00a7a\u00a7lEF"),
            PreviewTextLayout.wrap(source, 13, PreviewLosslessTextWrapTest::measure));
        assertEquals("ABCDEF", plain(String.join("", PreviewTextLayout.wrap(source, 13,
            PreviewLosslessTextWrapTest::measure))));
    }

    @Test
    void colorChangeAndResetDoNotLeakPreviousBoldStyle() {
        List<String> lines = PreviewTextLayout.wrap("\u00a7a\u00a7lAB\u00a7cCD\u00a7rEFGH", 13,
            PreviewLosslessTextWrapTest::measure);
        assertEquals(Arrays.asList("\u00a7a\u00a7lAB\u00a7c", "\u00a7cCD\u00a7r", "EF", "GH"), lines);
    }

    @Test
    void formattingOnlyAndOversizedFirstGlyphDoNotProduceEndlessEmptyLines() {
        assertEquals(Collections.singletonList("\u00a7a\u00a7l"),
            PreviewTextLayout.wrap("\u00a7a\u00a7l", 0, PreviewLosslessTextWrapTest::measure));
        assertEquals(Arrays.asList("\u00a7a\u00a7l\u4e2d", "\u00a7a\u00a7l\u6587"),
            PreviewTextLayout.wrap("\u00a7a\u00a7l\u4e2d\u6587", 0, PreviewLosslessTextWrapTest::measure));
    }

    @Test
    void formatPairsStayTogetherAtContinuationBoundaries() {
        String text = "\u00a7aA\u00a7LB\u00a7rC\u00a7qD";
        List<String> lines = PreviewTextLayout.wrap(text, 7, PreviewLosslessTextWrapTest::measure);
        assertEquals(plain(text), plain(String.join("", lines)));
        for (String line : lines) assertFalse(line.endsWith("\u00a7"));
    }

    @Test
    void unknownFormatCodeContinuesWithVanillaWhiteFallbackAndNoOldStyles() {
        String source = "\u00a7a\u00a7l\u00a7lAB\u00a7qCDEF";
        List<String> lines = PreviewTextLayout.wrap(source, 13, PreviewLosslessTextWrapTest::measure);
        assertEquals(Arrays.asList("\u00a7a\u00a7l\u00a7lAB\u00a7q", "\u00a7fCD", "\u00a7fEF"), lines);
        assertEquals(plain(source), plain(String.join("", lines)));
        assertEquals(Arrays.asList("\u00a7a\u00a7lA\u00a7q", "\u00a7fBC"),
            PreviewTextLayout.wrap("\u00a7a\u00a7lA\u00a7q\nBC", 100, PreviewLosslessTextWrapTest::measure));
    }

    @Test
    void longZeroWidthInputUsesBoundedMeasurementSearch() {
        char[] chars = new char[20000];
        Arrays.fill(chars, 'A');
        String text = new String(chars);
        AtomicInteger calls = new AtomicInteger();
        assertEquals(Collections.singletonList(text), PreviewTextLayout.wrap(text, 10, s -> {
            calls.incrementAndGet();
            return 0;
        }));
        assertTrue(calls.get() < 40, "do not measure every growing prefix of a long line");
    }

    @Test
    void allNineProductionCaptionAndActorWrapEntrypointsUseTheSharedLosslessWrapper() throws Exception {
        Path ui = Paths.get("src/main/java/net/createmod/ponder/foundation/ui");
        int wrappers = 0;
        for (String file : new String[] {"AuthoringImageScreen.java", "AuthoringImportScreen.java",
            "AuthoringReviewScreen.java", "PonderDebugScreen.java", "PonderTagScreen.java",
            "SandboxTriggeredBlockGuiSnapshot.java"}) {
            String source = read(ui.resolve(file));
            wrappers += source.split("PreviewTextLayout\\.wrap\\(", -1).length - 1;
        }
        assertEquals(9, wrappers);
        assertTrue(read(ui.resolve("PonderDebugScreen.java")).contains("PreviewTextLayout.wrap(fontRenderer, actor.displayName,"));
        try (Stream<Path> sources = Files.walk(Paths.get("src/main/java"))) {
            for (Path file : (Iterable<Path>) sources.filter(p -> p.toString().endsWith(".java"))::iterator) {
                assertFalse(read(file).contains(".listFormattedStringToWidth("), file.toString());
            }
        }
    }

    @Test
    void wrappedCaptionDrawingBypassesTrimButSingleLineLabelsStillFit() throws Exception {
        String source = read(Paths.get("src/main/java/net/createmod/ponder/foundation/ui/PonderDebugScreen.java"));
        String body = method(source, "private void drawCaptionText(");
        assertTrue(body.contains("drawWrappedExplanationText(lines.get(i)"));
        assertFalse(body.contains("trimStringToWidth"));
        String draw = method(source, "private void drawWrappedExplanationText(");
        assertTrue(draw.contains("drawString(fontRenderer, text,"));
        assertFalse(draw.contains("trimStringToWidth"));
        assertTrue(method(source, "private void drawExplanationText(").contains("trimStringToWidth"));
    }

    @Test
    void wrappedTagDescriptionBypassesTrimButTitleAndCountsStillFit() throws Exception {
        String source = read(Paths.get("src/main/java/net/createmod/ponder/foundation/ui/PonderTagScreen.java"));
        String summary = method(source, "private void drawSummary(");
        assertTrue(summary.contains("drawString(fontRenderer, descriptionLines.get(line),"));
        assertFalse(summary.contains("drawClippedString(descriptionLines.get(line)"));
        assertTrue(summary.contains("drawClippedString(tag.getTitle(),"));
        assertTrue(summary.contains("drawClippedString(tr(\"tag.component_count\""));
        assertTrue(summary.contains("drawClippedString(tr(\"tag.scene_count\""));
        InconsistentFont font = new InconsistentFont();
        String text = "\u4e2d\u7ee7\u5668 \u666e\u901a\u94c1\u8f68";
        List<String> lines = PreviewTextLayout.wrap(font, text, 28);
        assertEquals(text, String.join("", lines));
        StringBuilder trimmed = new StringBuilder();
        for (String line : lines) trimmed.append(font.trimStringToWidth(line, 28));
        assertNotEquals(text, trimmed.toString(), "A second legacy trim would reproduce lost description characters");
    }

    @Test
    void reopeningAfterFontSettingsChangeInvalidatesAllCachedWraps() throws Exception {
        String source = read(Paths.get("src/main/java/net/createmod/ponder/foundation/ui/PonderDebugScreen.java"));
        String init = method(source, "public void initGui(");
        assertTrue(init.contains("captionText = null;"));
        assertTrue(init.contains("calloutViews.values()"));
        assertTrue(init.contains("view.wrappedWidth = -1;"));
    }

    private static void assertConserved(String text) {
        for (int width : new int[] {19, 28, 100, 118, 130, 220, 960}) {
            List<String> lines = PreviewTextLayout.wrap(text, width, PreviewLosslessTextWrapTest::measure);
            assertEquals(text, String.join("", lines), "width=" + width);
            for (String line : lines) assertTrue(measure(line) <= width - 1);
        }
    }

    private static int measure(String text) {
        int width = 0;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == '\u00a7' && i < text.length()) {
                i += Character.charCount(text.codePointAt(i));
            } else {
                width += Character.isWhitespace(codePoint) ? 3 : codePoint > 0xFFFF ? 10 : codePoint > 127 ? 9 : 6;
            }
        }
        return width;
    }

    private static String plain(String text) {
        return text.replaceAll("\u00a7.", "");
    }

    private static String read(Path file) throws Exception {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature), open = source.indexOf('{', start), depth = 1, end = open + 1;
        assertTrue(start >= 0 && open >= 0, signature);
        while (depth > 0 && end < source.length()) {
            char c = source.charAt(end++);
            if (c == '{') depth++;
            else if (c == '}') depth--;
        }
        assertEquals(0, depth);
        return source.substring(open + 1, end - 1);
    }

    private static final class InconsistentFont extends FontRenderer {
        int measureCalls, wrapCalls, trimCalls;

        InconsistentFont() {
            super(new GameSettings(), new ResourceLocation("minecraft", "textures/font/ascii.png"), null, false);
        }

        @Override
        public int getStringWidth(String text) {
            measureCalls++;
            return measure(text);
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int width) {
            wrapCalls++;
            return Collections.singletonList(text.substring(1));
        }

        @Override
        public String trimStringToWidth(String text, int width) {
            trimCalls++;
            return text.substring(0, Math.max(0, text.length() - 1));
        }

        @Override
        protected void bindTexture(ResourceLocation location) {
        }

        @Override
        protected IResource getResource(ResourceLocation location) {
            return new SimpleResource("lossless-font-test", location,
                new ByteArrayInputStream(new byte[65536]), null, null);
        }
    }
}
