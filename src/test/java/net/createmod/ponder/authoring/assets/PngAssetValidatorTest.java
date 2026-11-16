package net.createmod.ponder.authoring.assets;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.Test;

class PngAssetValidatorTest {
    static byte[] header(int w, int h, int depth, int color, int interlace) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); DataOutputStream data = new DataOutputStream(out);
        data.writeInt(w); data.writeInt(h); data.writeByte(depth); data.writeByte(color); data.writeByte(0); data.writeByte(0); data.writeByte(interlace); return out.toByteArray();
    }
    static byte[] compress(byte[] raw) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); try (DeflaterOutputStream deflater = new DeflaterOutputStream(bytes)) { deflater.write(raw); } return bytes.toByteArray();
    }
    static byte[] chunk(String type, byte[] data) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream output = new DataOutputStream(bytes); byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        output.writeInt(data.length); output.write(name); output.write(data); CRC32 crc = new CRC32(); crc.update(name); crc.update(data); output.writeInt((int) crc.getValue()); return bytes.toByteArray();
    }
    static byte[] image(byte[]... chunks) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); bytes.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        for (byte[] chunk : chunks) bytes.write(chunk); return bytes.toByteArray();
    }
    static byte[] png(int w, int h, int depth, int color, int interlace, byte[] raw) throws Exception {
        return image(chunk("IHDR", header(w, h, depth, color, interlace)), chunk("IDAT", compress(raw)), chunk("IEND", new byte[0]));
    }
    @Test void validStaticAndAdam7ImagesAreCheckedWithoutAllocatingImages() throws Exception {
        assertEquals(5, PngAssetValidator.validate(png(1, 1, 8, 6, 0, new byte[] {0, 1, 2, 3, 4}), 100));
        assertEquals(271, PngAssetValidator.validate(png(8, 8, 8, 6, 1, new byte[271]), 1000));
        byte[] zlib = compress(new byte[] {0, 1, 2, 3, 4}); int split = zlib.length / 2;
        byte[] contiguous = image(chunk("IHDR", header(1, 1, 8, 6, 0)), chunk("IDAT", Arrays.copyOfRange(zlib, 0, split)), chunk("IDAT", Arrays.copyOfRange(zlib, split, zlib.length)), chunk("IEND", new byte[0]));
        assertEquals(5, PngAssetValidator.validate(contiguous, 100));
    }
    @Test void everyChunkCrcIendAndTrailingBytesAreRequired() throws Exception {
        byte[] valid = png(1, 1, 8, 6, 0, new byte[5]); byte[] crc = valid.clone(); crc[29] ^= 1;
        assertThrows(IOException.class, () -> PngAssetValidator.validate(crc, 100));
        assertThrows(IOException.class, () -> PngAssetValidator.validate(Arrays.copyOf(valid, valid.length - 12), 100));
        assertThrows(IOException.class, () -> PngAssetValidator.validate(Arrays.copyOf(valid, valid.length + 1), 100));
        byte[] headerOnly = image(chunk("IHDR", header(1, 1, 8, 6, 0)), chunk("IEND", new byte[0]));
        assertThrows(IOException.class, () -> PngAssetValidator.validate(headerOnly, 100));
    }
    @Test void dimensionsColorDepthAndDecodedByteBudgetAreCheckedBeforePixels() throws Exception {
        assertThrows(IOException.class, () -> PngAssetValidator.validate(png(4097, 1, 8, 6, 0, new byte[5]), 100));
        assertThrows(IOException.class, () -> PngAssetValidator.validate(png(1, 1, 4, 6, 0, new byte[5]), 100));
        assertThrows(IOException.class, () -> PngAssetValidator.validate(png(1, 1, 8, 6, 0, new byte[5]), 4));
        assertThrows(IOException.class, () -> PngAssetValidator.validate(png(1, 1, 8, 6, 2, new byte[5]), 100));
    }
    @Test void zlibChecksumExactPixelCountFiltersAndHiddenCompressedPayloadAreVerified() throws Exception {
        assertThrows(IOException.class, () -> PngAssetValidator.validate(png(1, 1, 8, 6, 0, new byte[] {5, 0, 0, 0, 0}), 100));
        assertThrows(IOException.class, () -> PngAssetValidator.validate(png(1, 1, 8, 6, 0, new byte[6]), 100));
        assertThrows(IOException.class, () -> PngAssetValidator.validate(png(1, 1, 8, 6, 0, new byte[4]), 100));
        byte[] zlib = compress(new byte[5]); zlib[zlib.length - 1] ^= 1;
        byte[] badChecksum = image(chunk("IHDR", header(1, 1, 8, 6, 0)), chunk("IDAT", zlib), chunk("IEND", new byte[0]));
        assertThrows(IOException.class, () -> PngAssetValidator.validate(badChecksum, 100));
        byte[] hidden = image(chunk("IHDR", header(1, 1, 8, 6, 0)), chunk("IDAT", Arrays.copyOf(compress(new byte[5]), compress(new byte[5]).length + 1)), chunk("IEND", new byte[0]));
        assertThrows(IOException.class, () -> PngAssetValidator.validate(hidden, 100));
    }
    @Test void paletteAndChunkOrderAreValidatedAndCompressedMetadataIsNotInterpreted() throws Exception {
        byte[] idat = chunk("IDAT", compress(new byte[] {0, 0}));
        byte[] indexed = image(chunk("IHDR", header(1, 1, 1, 3, 0)), chunk("PLTE", new byte[] {0, 0, 0}), idat, chunk("IEND", new byte[0]));
        assertEquals(2, PngAssetValidator.validate(indexed, 100));
        byte[] absentPalette = image(chunk("IHDR", header(1, 1, 1, 3, 0)), idat, chunk("IEND", new byte[0]));
        assertThrows(IOException.class, () -> PngAssetValidator.validate(absentPalette, 100));
        byte[] unsupported = image(chunk("IHDR", header(1, 1, 8, 6, 0)), chunk("zTXt", new byte[] {1, 2, 3}), chunk("IDAT", compress(new byte[5])), chunk("IEND", new byte[0]));
        assertThrows(IOException.class, () -> PngAssetValidator.validate(unsupported, 100));
        byte[] zlib = compress(new byte[5]); int split = zlib.length / 2;
        byte[] interrupted = image(chunk("IHDR", header(1, 1, 8, 6, 0)), chunk("IDAT", Arrays.copyOfRange(zlib, 0, split)), chunk("tEXt", new byte[] {'K', 0, 'V'}), chunk("IDAT", Arrays.copyOfRange(zlib, split, zlib.length)), chunk("IEND", new byte[0]));
        assertThrows(IOException.class, () -> PngAssetValidator.validate(interrupted, 100));
    }
}
