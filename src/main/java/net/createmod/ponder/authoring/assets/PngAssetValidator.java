package net.createmod.ponder.authoring.assets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Static texture validation only. No AWT image objects, profiles, plugins or GPU calls are involved. */
final class PngAssetValidator {
    static final int MAX_FILE_BYTES = 64 * 1024 * 1024;
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList("IHDR", "PLTE", "IDAT", "IEND", "tRNS", "gAMA", "cHRM", "sRGB", "pHYs", "sBIT", "bKGD", "hIST", "tIME", "tEXt"));
    private PngAssetValidator() { }

    static long validate(byte[] bytes, long remainingDecodedBytes) throws IOException {
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        if (bytes == null || bytes.length < 45 || bytes.length > MAX_FILE_BYTES) throw new IOException("PNG file is truncated or exceeds 64 MiB");
        for (int i = 0; i < 8; i++) if (bytes[i] != signature[i]) throw new IOException("Not a PNG image");
        Set<String> seen = new HashSet<>(); List<int[]> data = new ArrayList<>(); List<long[]> passes = new ArrayList<>();
        int width = 0, height = 0, depth = 0, color = 0, paletteEntries = 0, chunks = 0;
        long expected = 0, compressedData = 0; boolean dataEnded = false, end = false;
        for (int at = 8; at < bytes.length;) {
            if (++chunks > 4096 || bytes.length - at < 12) throw new IOException("PNG chunk count/truncation budget exceeded");
            long length = unsignedInt(bytes, at);
            if (length > MAX_FILE_BYTES || at + length + 12 > bytes.length) throw new IOException("PNG chunk exceeds file bounds");
            int n = (int) length, payload = at + 8;
            for (int i = 0; i < 4; i++) { int c = bytes[at + 4 + i] & 255; if (!(c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z')) throw new IOException("Invalid PNG chunk type"); }
            if ((bytes[at + 6] & 32) != 0) throw new IOException("Invalid PNG reserved chunk bit");
            String type = new String(bytes, at + 4, 4, StandardCharsets.US_ASCII);
            if (!ALLOWED.contains(type)) throw new IOException("Unsupported PNG chunk (compressed metadata/animation are not imported): " + type);
            CRC32 crc = new CRC32(); crc.update(bytes, at + 4, n + 4);
            if (crc.getValue() != unsignedInt(bytes, payload + n)) throw new IOException("PNG CRC mismatch in " + type);
            if (chunks == 1 && !"IHDR".equals(type)) throw new IOException("IHDR must be the first PNG chunk");
            if (!"IDAT".equals(type) && !"tEXt".equals(type) && !seen.add(type)) throw new IOException("Duplicate PNG chunk: " + type);
            if ("IHDR".equals(type)) {
                if (n != 13) throw new IOException("Invalid PNG IHDR length");
                long w = unsignedInt(bytes, payload), h = unsignedInt(bytes, payload + 4);
                if (w < 1 || h < 1 || w > 4096 || h > 4096 || w * h > 16777216) throw new IOException("PNG dimensions exceed 4096 squared");
                width = (int) w; height = (int) h; depth = bytes[payload + 8] & 255; color = bytes[payload + 9] & 255;
                if (!validDepth(depth, color) || bytes[payload + 10] != 0 || bytes[payload + 11] != 0 || (bytes[payload + 12] & 255) > 1)
                    throw new IOException("Unsupported PNG color depth/compression/filter/interlace");
                expected = scanlines(width, height, depth, color, bytes[payload + 12], passes);
                if (expected > remainingDecodedBytes) throw new IOException("Cumulative PNG decoded byte budget exceeded");
            } else if ("PLTE".equals(type)) {
                if (!data.isEmpty() || n < 3 || n > 768 || n % 3 != 0 || color == 0 || color == 4) throw new IOException("Invalid PNG palette");
                paletteEntries = n / 3;
                if (color == 3 && paletteEntries > (1 << depth)) throw new IOException("PNG palette exceeds its bit depth");
            } else if ("IDAT".equals(type)) {
                if (dataEnded || color == 3 && paletteEntries == 0) throw new IOException("PNG IDAT sequence/palette is invalid");
                data.add(new int[] {payload, n}); compressedData += n;
            } else if ("IEND".equals(type)) {
                if (n != 0 || data.isEmpty() || compressedData == 0 || payload + n + 4 != bytes.length) throw new IOException("Invalid IEND or trailing PNG bytes");
                end = true;
            } else {
                if (!data.isEmpty()) dataEnded = true;
                validateMetadata(type, bytes, payload, n, color, depth, paletteEntries, !data.isEmpty());
            }
            at = payload + n + 4;
            if (end) break;
        }
        if (!end) throw new IOException("PNG is missing IEND");
        validatePixels(bytes, data, expected, passes);
        return expected;
    }
    private static boolean validDepth(int depth, int color) {
        if (color == 0) return depth == 1 || depth == 2 || depth == 4 || depth == 8 || depth == 16;
        if (color == 3) return depth == 1 || depth == 2 || depth == 4 || depth == 8;
        return (color == 2 || color == 4 || color == 6) && (depth == 8 || depth == 16);
    }
    private static int channels(int color) { return color == 0 || color == 3 ? 1 : color == 2 ? 3 : color == 4 ? 2 : 4; }
    private static long scanlines(int w, int h, int depth, int color, int interlace, List<long[]> passes) {
        int[][] geometry = interlace == 0 ? new int[][] {{0, 0, 1, 1}}
            : new int[][] {{0, 0, 8, 8}, {4, 0, 8, 8}, {0, 4, 4, 8}, {2, 0, 4, 4}, {0, 2, 2, 4}, {1, 0, 2, 2}, {0, 1, 1, 2}};
        long total = 0;
        for (int[] pass : geometry) {
            int pw = w <= pass[0] ? 0 : (w - pass[0] + pass[2] - 1) / pass[2];
            int ph = h <= pass[1] ? 0 : (h - pass[1] + pass[3] - 1) / pass[3];
            if (pw == 0 || ph == 0) continue;
            long span = 1 + ((long) pw * channels(color) * depth + 7) / 8, end = total + span * ph;
            passes.add(new long[] {total, end, span}); total = end;
        }
        return total;
    }
    private static void validatePixels(byte[] bytes, List<int[]> segments, long expected, List<long[]> passes) throws IOException {
        Inflater inflater = new Inflater(); int next = 0; long decoded = 0; byte[] scratch = new byte[8192];
        try {
            while (!inflater.finished()) {
                if (inflater.needsInput()) {
                    while (next < segments.size() && segments.get(next)[1] == 0) next++;
                    if (next >= segments.size()) throw new IOException("Truncated PNG IDAT zlib stream");
                    int[] segment = segments.get(next++); inflater.setInput(bytes, segment[0], segment[1]);
                }
                int count = inflater.inflate(scratch);
                if (count > 0) {
                    if (count > expected - decoded) throw new IOException("PNG pixel payload exceeds IHDR bounds");
                    for (long[] pass : passes) {
                        long first = Math.max(pass[0], decoded);
                        long row = pass[0] + ((first - pass[0] + pass[2] - 1) / pass[2]) * pass[2];
                        for (; row < decoded + count && row < pass[1]; row += pass[2])
                            if ((scratch[(int) (row - decoded)] & 255) > 4) throw new IOException("Invalid PNG scanline filter");
                    }
                    decoded += count;
                } else if (inflater.needsDictionary() || !inflater.needsInput() && !inflater.finished()) throw new IOException("Invalid PNG IDAT zlib stream");
            }
            long extra = inflater.getRemaining(); while (next < segments.size()) extra += segments.get(next++)[1];
            if (extra != 0 || decoded != expected) throw new IOException("PNG IDAT has trailing bytes or an incorrect pixel count");
        } catch (DataFormatException invalid) { throw new IOException("Invalid PNG IDAT compression/checksum", invalid); }
        finally { inflater.end(); }
    }
    private static void validateMetadata(String type, byte[] bytes, int p, int n, int color, int depth, int entries, boolean afterData) throws IOException {
        if (!"tEXt".equals(type) && !"tIME".equals(type) && afterData) throw new IOException("PNG color metadata must precede IDAT");
        if ("tRNS".equals(type) && !(color == 0 && n == 2 || color == 2 && n == 6 || color == 3 && entries > 0 && n > 0 && n <= entries)) throw new IOException("Invalid PNG transparency chunk");
        if ("gAMA".equals(type) && (n != 4 || unsignedInt(bytes, p) == 0) || "cHRM".equals(type) && n != 32
            || "sRGB".equals(type) && (n != 1 || (bytes[p] & 255) > 3) || "pHYs".equals(type) && (n != 9 || (bytes[p + 8] & 255) > 1)
            || "tIME".equals(type) && n != 7 || "hIST".equals(type) && (entries == 0 || n != entries * 2)) throw new IOException("Invalid PNG metadata length/value: " + type);
        if ("sBIT".equals(type)) {
            int expected = color == 3 ? 3 : channels(color);
            if (n != expected) throw new IOException("Invalid PNG sBIT length");
            for (int i = 0; i < n; i++) if ((bytes[p + i] & 255) < 1 || (bytes[p + i] & 255) > (color == 3 ? 8 : depth)) throw new IOException("Invalid PNG significant bit depth");
        }
        if ("bKGD".equals(type) && (n != (color == 3 ? 1 : color == 0 || color == 4 ? 2 : 6) || color == 3 && ((bytes[p] & 255) >= entries || entries == 0))) throw new IOException("Invalid PNG background chunk");
        if ("tEXt".equals(type)) {
            if (n > 65536) throw new IOException("PNG text metadata exceeds 64 KiB");
            int keyword = 0; while (keyword < n && bytes[p + keyword] != 0) keyword++;
            if (keyword < 1 || keyword > 79 || keyword == n) throw new IOException("Invalid PNG text keyword");
        }
    }
    private static long unsignedInt(byte[] b, int p) { return ((b[p] & 255L) << 24) | ((b[p + 1] & 255L) << 16) | ((b[p + 2] & 255L) << 8) | (b[p + 3] & 255L); }
}
