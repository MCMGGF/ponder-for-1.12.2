package net.createmod.ponder.foundation.content.mmce;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Small, bounded resource checks; never asks MMCE to reload its global models. */
final class MmceModelResources {

    static final int MAX_TEXTURE_BYTES = 4 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    private MmceModelResources() {
    }

    static byte[] readLimited(InputStream stream, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximum, 8192));
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (output.size() > maximum - count) {
                throw new IOException("模型资源超过预览容量限制");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    static void validateTexture(byte[] bytes) throws IOException {
        if (bytes.length < PNG_SIGNATURE.length || bytes.length > MAX_TEXTURE_BYTES) {
            throw new IOException("模型纹理无效或超过 4 MiB");
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) {
                throw new IOException("静态模型预览目前仅支持 PNG 纹理");
            }
        }
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) {
            throw new IOException("当前运行环境无法解码 PNG 纹理");
        }
        ImageReader reader = readers.next();
        try (MemoryCacheImageInputStream input =
            new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width < 1 || height < 1 || width > 1024 || height > 1024) {
                throw new IOException("模型纹理尺寸超过 1024 像素预览限制");
            }
            BufferedImage image = reader.read(0);
            try {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int alpha = image.getRGB(x, y) >>> 24;
                        if (alpha != 0 && alpha != 255) {
                            throw new IOException("此纹理包含尚未适配的半透明像素，当前显示方块结构");
                        }
                    }
                }
            } finally {
                image.flush();
            }
        } finally {
            reader.dispose();
        }
    }
}
