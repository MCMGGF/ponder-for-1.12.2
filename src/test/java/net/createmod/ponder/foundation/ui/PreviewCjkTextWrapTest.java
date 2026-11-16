package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class PreviewCjkTextWrapTest {

    @Test
    void finalChinesePeriodMovesWithItsPrecedingCharacter() {
        assertEquals(Arrays.asList("\u7532\u4e59", "\u4e19\u3002"),
            wrap("\u7532\u4e59\u4e19\u3002", 28));
        assertEquals(Arrays.asList("\u7532", "\u4e59\u3002"), wrap("\u7532\u4e59\u3002", 19));
    }

    @Test
    void chineseCommasAndClosingBracketsDoNotStartSoftLines() {
        for (String punctuation : new String[] {"\uff0c", "\u3001", "\uff1a", "\uff1b", "\uff01",
            "\uff1f", "\u300d", "\u300b", "\u201d", "\uff09"}) {
            String source = "\u7532\u4e59\u4e19" + punctuation + "\u4e01";
            List<String> lines = wrap(source, 28);
            assertEquals(source, String.join("", lines));
            assertEquals("\u7532\u4e59", lines.get(0));
            assertEquals("\u4e19" + punctuation + "\u4e01", lines.get(1));
        }
    }

    @Test
    void openingBracketsAndQuotesStayWithTheirFollowingText() {
        for (String[] pair : new String[][] {{"\uff08", "\uff09"}, {"\u300c", "\u300d"},
            {"\u300a", "\u300b"}, {"\u201c", "\u201d"}}) {
            String source = "\u7532\u4e59" + pair[0] + "\u4e19" + pair[1] + "\u4e01";
            assertEquals(Arrays.asList("\u7532\u4e59", pair[0] + "\u4e19" + pair[1], "\u4e01"),
                wrap(source, 28));
        }
    }

    @Test
    void asciiSentencePunctuationAlsoKeepsItsPrecedingLetter() {
        for (String punctuation : new String[] {".", ",", "!", "?", ":", ";", "%", ")", "]", "}"}) {
            assertEquals(Arrays.asList("AB", "C" + punctuation), wrap("ABC" + punctuation, 19));
        }
        assertEquals(Arrays.asList("ABC", "(DE)", "FG"), wrap("ABC(DE)FG", 25));
    }

    @Test
    void consecutivePunctuationMovesTogetherWhenTheBudgetAllowsIt() {
        assertEquals(Arrays.asList("\u7532\u4e59", "\u4e19\uff1f\uff01"),
            wrap("\u7532\u4e59\u4e19\uff1f\uff01", 28));
    }

    @Test
    void punctuationSeparatedByFormattingStillFindsItsVisibleNeighbour() {
        List<String> lines = wrap("\u00a7a\u7532\u4e59\u00a7c\u3002", 19);
        assertEquals(Arrays.asList("\u00a7a\u7532", "\u00a7a\u4e59\u00a7c\u3002"), lines);
        assertEquals("\u7532\u4e59\u3002", plain(String.join("", lines)));
        for (String line : lines) assertFalse(line.endsWith("\u00a7"));
    }

    @Test
    void whitespaceIsKeptEvenWhenItPrecedesPunctuation() {
        String source = "\u7532\u4e59 \u3002\u4e19";
        List<String> lines = wrap(source, 25);
        assertEquals(Arrays.asList("\u7532", "\u4e59 \u3002", "\u4e19"), lines);
        assertEquals(source, String.join("", lines));
        assertEquals(Arrays.asList("one ", "two ", "three"), wrap("one two three", 37));
    }

    @Test
    void explicitHardBreaksAndAuthoredLeadingPunctuationAreNotRewritten() {
        assertEquals(Arrays.asList("\u7532\u4e59", "\u3002", "", "\uff0c\u4e19"),
            wrap("\u7532\u4e59\r\n\u3002\n\n\uff0c\u4e19", 19));
    }

    @Test
    void narrowBudgetsPreferLosslessnessAndTerminationOverImpossibleTypography() {
        for (int width : new int[] {Integer.MIN_VALUE, 0, 1, 2, 7, 10, 19}) {
            String source = "\u7532\uff1f\uff01\uff08\u4e59\uff09\ud83e\uddf1\u3002";
            List<String> lines = wrap(source, width);
            assertEquals(source, String.join("", lines));
            assertTrue(lines.size() <= source.codePointCount(0, source.length()));
            for (String line : lines) {
                assertFalse(line.isEmpty());
                assertFalse(Character.isLowSurrogate(line.charAt(0)));
                assertFalse(Character.isHighSurrogate(line.charAt(line.length() - 1)));
            }
        }
    }

    @Test
    void supplementaryCharactersAndAllWhitespaceRemainIntact() {
        String source = " \ud83e\uddf1\u7532\u3002\t\u00a7b\u4e59\uff0c\ud83d\ude80  ";
        for (int width : new int[] {19, 28, 37, 118, 220}) {
            List<String> lines = wrap(source, width);
            assertEquals(plain(source), plain(String.join("", lines)));
            for (String line : lines) assertTrue(measure(line) <= width - 1);
        }
    }

    @Test
    void typographyDoesNotAddFontMeasurementCallsForLongZeroWidthInput() {
        char[] chars = new char[20000];
        Arrays.fill(chars, '\u3002');
        String text = new String(chars);
        AtomicInteger calls = new AtomicInteger();
        assertEquals(Arrays.asList(text), PreviewTextLayout.wrap(text, 10, value -> {
            calls.incrementAndGet();
            return 0;
        }));
        assertTrue(calls.get() < 40);
    }

    private static List<String> wrap(String source, int width) {
        List<String> lines = PreviewTextLayout.wrap(source, width, PreviewCjkTextWrapTest::measure);
        int budget = width > 1 ? width - 1 : 1;
        for (String line : lines) {
            int visible = plain(line).codePointCount(0, plain(line).length());
            assertTrue(measure(line) <= budget || visible == 1, line);
        }
        return lines;
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
}
