package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

import net.minecraft.client.gui.FontRenderer;

/** Use the active font for fitted labels and lossless multiline text. */
public final class PreviewTextLayout {

    final String text;
    final int x;
    final int width;

    private PreviewTextLayout(String text, int x, int width) {
        this.text = text;
        this.x = x;
        this.width = width;
    }

    static PreviewTextLayout inside(FontRenderer font, String text, int left, int right, int preferredX) {
        String fitted = font.trimStringToWidth(text, Math.max(0, right - left));
        int width = font.getStringWidth(fitted);
        int x = Math.max(left, Math.min(preferredX, right - width));
        return new PreviewTextLayout(fitted, x, width);
    }

    static PreviewTextLayout centered(FontRenderer font, String text, int left, int right) {
        PreviewTextLayout fitted = inside(font, text, left, right, left);
        return new PreviewTextLayout(fitted.text, left + (Math.max(0, right - left) - fitted.width) / 2,
            fitted.width);
    }

    public static List<String> wrap(FontRenderer font, String text, int width) {
        return wrap(text, width, font::getStringWidth);
    }

    static List<String> wrap(String text, int width, ToIntFunction<String> measure) {
        Objects.requireNonNull(measure, "measure");
        if (text == null || text.isEmpty()) return Collections.singletonList("");
        int budget = width > 1 ? width - 1 : 1;
        List<String> lines = new ArrayList<>();
        Formatting formatting = new Formatting();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\r' && c != '\n') continue;
            wrapParagraph(text.substring(start, i), budget, measure, formatting, lines);
            if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
            start = i + 1;
        }
        wrapParagraph(text.substring(start), budget, measure, formatting, lines);
        return lines;
    }

    private static void wrapParagraph(String text, int budget, ToIntFunction<String> measure,
        Formatting formatting, List<String> lines) {
        if (text.isEmpty()) {
            lines.add(formatting.prefix());
            return;
        }
        List<Integer> boundaries = new ArrayList<>();
        boundaries.add(0);
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == '\u00a7' && i < text.length()) {
                i += Character.charCount(text.codePointAt(i));
            }
            boundaries.add(i);
        }
        int count = boundaries.size() - 1;
        int[] previousVisible = new int[count + 1];
        int[] nextVisible = new int[count + 1];
        previousVisible[0] = -1;
        nextVisible[count] = -1;
        for (int i = 0; i < count; i++) {
            previousVisible[i + 1] = isInvisibleToken(text, boundaries, i) ? previousVisible[i] : i;
        }
        for (int i = count - 1; i >= 0; i--) {
            nextVisible[i] = isInvisibleToken(text, boundaries, i) ? nextVisible[i + 1] : i;
        }
        for (int start = 0; start < count;) {
            String prefix = formatting.prefix();
            int minimum = start;
            while (minimum < count && text.charAt(boundaries.get(minimum)) == '\u00a7'
                && boundaries.get(minimum + 1) - boundaries.get(minimum) > 1) minimum++;
            if (minimum < count) minimum++;
            int end = minimum;
            if (fits(text, boundaries, start, minimum, prefix, budget, measure)) {
                int step = 1;
                while (end < count) {
                    int high = end + Math.min(step, count - end);
                    if (fits(text, boundaries, start, high, prefix, budget, measure)) {
                        end = high;
                        step = Math.min(count, step <= count / 2 ? step * 2 : count);
                    } else {
                        int low = end;
                        while (low + 1 < high) {
                            int middle = low + (high - low) / 2;
                            if (fits(text, boundaries, start, middle, prefix, budget, measure)) low = middle;
                            else high = middle;
                        }
                        end = low;
                        break;
                    }
                }
            }
            if (end < count) {
                int fittedEnd = end;
                for (int i = end - 1; i >= minimum - 1; i--) {
                    if (Character.isWhitespace(text.codePointAt(boundaries.get(i)))) {
                        end = i + 1;
                        break;
                    }
                }
                // Rebalance inside the fitted range; punctuation never justifies overflowing it.
                if (!canBreak(text, boundaries, start, end, previousVisible, nextVisible)) {
                    end = fittedEnd;
                    for (int candidate = fittedEnd; candidate >= minimum; candidate--) {
                        if (canBreak(text, boundaries, start, candidate, previousVisible, nextVisible)) {
                            end = candidate;
                            break;
                        }
                    }
                }
            }
            // Keep original spaces and every code point; only continuation format prefixes are added.
            String part = text.substring(boundaries.get(start), boundaries.get(end));
            lines.add(prefix + part);
            formatting.accept(part);
            start = end;
        }
    }

    private static boolean canBreak(String text, List<Integer> boundaries, int start, int end,
        int[] previousVisible, int[] nextVisible) {
        int before = previousVisible[end];
        int after = nextVisible[end];
        return before >= start && !cannotEndLine(text.codePointAt(boundaries.get(before)))
            && (after < 0 || !cannotStartLine(text.codePointAt(boundaries.get(after))));
    }

    private static boolean isInvisibleToken(String text, List<Integer> boundaries, int token) {
        int offset = boundaries.get(token);
        int codePoint = text.codePointAt(offset);
        return Character.isWhitespace(codePoint)
            || codePoint == '\u00a7' && boundaries.get(token + 1) - offset > 1;
    }

    private static boolean cannotStartLine(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.END_PUNCTUATION || type == Character.FINAL_QUOTE_PUNCTUATION
            || ",.!?:;%\u2030\u2026\u3001\u3002\uff0c\uff0e\uff01\uff1f\uff1a\uff1b\uff05"
                .indexOf(codePoint) >= 0;
    }

    private static boolean cannotEndLine(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.START_PUNCTUATION || type == Character.INITIAL_QUOTE_PUNCTUATION;
    }

    private static boolean fits(String text, List<Integer> boundaries, int start, int end, String prefix,
        int budget, ToIntFunction<String> measure) {
        return measure.applyAsInt(prefix + text.substring(boundaries.get(start), boundaries.get(end))) <= budget;
    }

    private static final class Formatting {
        private String color = "";
        private final StringBuilder styles = new StringBuilder();

        String prefix() {
            return color + styles;
        }

        void accept(String text) {
            for (int i = 0; i + 1 < text.length(); i++) {
                if (text.charAt(i) != '\u00a7') continue;
                char code = Character.toLowerCase(text.charAt(++i));
                if ("0123456789abcdef".indexOf(code) >= 0) {
                    color = "\u00a7" + code;
                    styles.setLength(0);
                } else if (code == 'r') {
                    color = "";
                    styles.setLength(0);
                } else if (code >= 'k' && code <= 'o') {
                    if (styles.indexOf(String.valueOf(code)) < 0) styles.append('\u00a7').append(code);
                } else {
                    // 1.12 renders unknown codes as white and clears every style.
                    color = "\u00a7f";
                    styles.setLength(0);
                }
            }
        }
    }
}
