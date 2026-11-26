package net.createmod.ponder.foundation.ui.sandbox.worker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.createmod.ponder.foundation.ui.BlockGuiDemoDefinition.Insets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;

/** Samples the actual bound Minecraft framebuffer after the native screen and its overlays draw. */
final class SandboxFrames {
    private final Path job;
    private final JsonArray frames = new JsonArray();
    private IntBuffer readback;
    private int[] current;
    private int[] previous;
    private int previousWidth;
    private int lastSample = -1;
    private boolean sawGui;
    private long bytesWritten;

    SandboxFrames(Path job) throws IOException {
        this.job = job;
        Files.createDirectory(job.resolve("frames"));
    }

    record Bounds(int left, int top, int right, int bottom) {
        static Bounds container(int x, int y, int width, int height, Insets margins) {
            return new Bounds(x - margins.left(), y - margins.top(),
                x + width + margins.right(), y + height + margins.bottom());
        }

        Bounds visible(int width, int height) {
            return new Bounds(Math.max(0, left), Math.max(0, top), Math.min(width, right), Math.min(height, bottom));
        }

        boolean fits(int width, int height) {
            return left >= 0 && top >= 0 && right <= width && bottom <= height;
        }
    }

    static Bounds requested(GuiContainer container, Insets margins) {
        return Bounds.container(container.getGuiLeft(), container.getGuiTop(), container.getXSize(), container.getYSize(), margins);
    }

    void capture(Minecraft mc, GuiScreen gui, Insets margins, int tick) throws IOException {
        if (tick == lastSample) return;
        int width = 1, height = 1;
        if (gui != null) {
            Bounds visible = gui instanceof GuiContainer container
                ? requested(container, margins).visible(gui.width, gui.height)
                : new Bounds(0, 0, gui.width, gui.height);
            width = visible.right() - visible.left();
            height = visible.bottom() - visible.top();
            if (width < 1 || height < 1 || width > 4096 || height > 4096)
                throw new IOException("Invalid visible GUI capture: " + visible);
            int scale = new ScaledResolution(mc).getScaleFactor();
            int x = visible.left() * scale;
            int topPixel = visible.top() * scale;
            int pixelWidth = Math.min(mc.displayWidth - x, width * scale);
            int pixelHeight = Math.min(mc.displayHeight - topPixel, height * scale);
            int pixelCount = Math.multiplyExact(pixelWidth, pixelHeight);
            if (pixelCount <= 0 || pixelCount > 16 * 1024 * 1024)
                throw new IOException("Sandbox framebuffer capture exceeds 16 megapixels");
            int size = Math.multiplyExact(width, height);
            if (readback == null || readback.capacity() < pixelCount) readback = BufferUtils.createIntBuffer(pixelCount);
            readback.clear();
            GlStateManager.glReadPixels(x, mc.displayHeight - topPixel - pixelHeight, pixelWidth, pixelHeight, 32993, 33639, readback);
            if (current == null || current.length != size) current = new int[size];
            for (int row = 0; row < height; row++) {
                int sourceRow = pixelHeight - 1 - Math.min(row * scale + scale / 2, pixelHeight - 1);
                for (int col = 0; col < width; col++) {
                    int sourceCol = Math.min(col * scale + scale / 2, pixelWidth - 1);
                    current[row * width + col] = readback.get(sourceRow * pixelWidth + sourceCol) | 0xff000000;
                }
            }
            sawGui = true;
        } else {
            current = new int[1];
        }
        lastSample = tick;
        if (width == previousWidth && Arrays.equals(current, previous)) return;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, current, 0, width);
        String name = String.format(Locale.ROOT, "frames/%05d.png", frames.size());
        Path file = job.resolve(name);
        if (!ImageIO.write(image, "png", file.toFile())) throw new IOException("PNG encoder unavailable");
        bytesWritten += Files.size(file);
        if (bytesWritten > 512L * 1024 * 1024) throw new IOException("Sandbox recording exceeds 512 MiB");
        JsonObject frame = new JsonObject();
        frame.addProperty("tick", frames.size() == 0 ? 0 : tick);
        frame.addProperty("file", name);
        if (gui == null) frame.addProperty("hidden", true);
        frames.add(frame);
        int[] old = previous;
        previous = current;
        current = old;
        previousWidth = width;
    }

    void finish(int duration) throws IOException {
        if (!sawGui) throw new IOException("The script never rendered a native GUI");
        JsonObject manifest = new JsonObject();
        manifest.addProperty("duration", duration);
        manifest.add("frames", frames);
        Path temporary = job.resolve("frames.json.tmp");
        Files.writeString(temporary, manifest.toString());
        Files.move(temporary, job.resolve("frames.json"), StandardCopyOption.ATOMIC_MOVE);
    }
}
