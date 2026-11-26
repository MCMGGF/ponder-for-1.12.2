package net.createmod.ponder.foundation.ui.sandbox;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.ui.PreviewTextLayout;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.ResettableSnapshotProvider;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.Snapshot;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.SnapshotProvider;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.SnapshotRenderer;
import net.createmod.ponder.foundation.ui.sandbox.RecordingFrames.Frame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;

/** Timeline playback uses only PNGs from a completed worker generation. */
public final class RecordedGuiPlayback implements ResettableSnapshotProvider, SnapshotRenderer {
    public interface PendingPreview {
        Snapshot previewSnapshot(float tick, String statusKey);

        default void reset() { }
    }

    private static final Map<String, RecordingFrames> RECORDINGS = new ConcurrentHashMap<>();
    private static final ThreadPoolExecutor READER = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(8), task -> {
            Thread thread = new Thread(task, "Ponder GUI frame reader");
            thread.setDaemon(true);
            return thread;
        });
    // Only the render thread owns textures and per-player lookahead buffers.
    private static final Map<RecordedGuiPlayback, Long> LIVE = new LinkedHashMap<>(16, .75f, true);
    private static final long MAX_TEXTURE_BYTES = 64L * 1024 * 1024;
    private final String id;
    private final int startTick;
    private final Runnable onView;
    private final PendingPreview preview;
    private boolean showingPreview;
    private Snapshot statusSnapshot;
    private RecordingFrames recording;
    private Snapshot snapshot;
    private Frame uploaded;
    private Frame failed;
    private DynamicTexture texture;
    private ResourceLocation location;
    private Frame next;
    private FutureTask<BufferedImage> pending;

    private RecordedGuiPlayback(String id, int startTick, Runnable onView, PendingPreview preview) {
        this.id = id;
        this.startTick = startTick;
        this.onView = onView;
        this.preview = preview;
    }

    public static SnapshotProvider provider(String id, int startTick) {
        return provider(id, startTick, null);
    }

    public static SnapshotProvider provider(String id, int startTick, Runnable onView) {
        return provider(id, startTick, onView, null);
    }

    public static SnapshotProvider provider(String id, int startTick, Runnable onView, PendingPreview preview) {
        return new RecordedGuiPlayback(id, startTick, onView, preview);
    }

    public static void publish(String id, Path immutableGeneration) throws IOException {
        RECORDINGS.put(id, RecordingFrames.read(immutableGeneration));
    }

    public static void retain(Set<String> ids) { RECORDINGS.keySet().retainAll(ids); }

    @Override
    public boolean waitingForRecording() {
        if (statusSnapshot == null || recording != null || RECORDINGS.containsKey(id)) return false;
        String state = SandboxRecordings.statusMessage(id);
        return "ponder.sandbox.waiting".equals(state) || "ponder.sandbox.recording".equals(state);
    }

    @Override
    public Snapshot provide(float tick) {
        if (!Float.isFinite(tick) || tick < startTick) return null;
        if (recording == null) recording = RECORDINGS.get(id);
        if (recording == null && onView != null) {
            onView.run();
            recording = RECORDINGS.get(id);
            String status = SandboxRecordings.statusMessage(id);
            if (recording == null && status != null) {
                if (statusSnapshot == null)
                    statusSnapshot = Snapshot.liveRenderer(176, 40, true, this::renderStatus);
                Snapshot nativePreview = preview == null ? null : preview.previewSnapshot(tick, status);
                if (nativePreview != null) {
                    showingPreview = true;
                    return nativePreview;
                }
                return statusSnapshot;
            }
        }
        if (recording == null) return null;
        int index = recording.indexAt((int) (tick - startTick));
        if (showingPreview && recording.beforeFirstVisible(index)) {
            Snapshot nativePreview = preview.previewSnapshot(tick, "ponder.sandbox.ready");
            if (nativePreview != null) return nativePreview;
        }
        clearPreview();
        if (recording.hidden(index)) return null;
        Frame frame = recording.frame(index);
        if (snapshot == null || snapshot.displayWidth != frame.width() || snapshot.displayHeight != frame.height())
            snapshot = Snapshot.liveRenderer(frame.width(), frame.height(), true, this);
        return snapshot;
    }

    private void renderStatus(int x, int y, int width, int height, float tick, float fade) {
        String key = SandboxRecordings.statusMessage(id);
        if (key == null || width < 12 || height <= 0) return;
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int alpha = Math.min(255, Math.round(255 * fade));
        if (alpha < 4) return;
        var lines = PreviewTextLayout.wrap(font, I18n.format(key), width - 8);
        int lineY = y + Math.max(0, (height - lines.size() * font.FONT_HEIGHT) / 2);
        for (String line : lines) {
            if (lineY + font.FONT_HEIGHT > y + height) break;
            font.drawStringWithShadow(line, x + (width - font.getStringWidth(line)) / 2f, lineY,
                alpha << 24 | 0xFFFFFF);
            lineY += font.FONT_HEIGHT;
        }
    }

    @Override
    public void render(int x, int y, int width, int height, float tick, float fade) {
        if (recording == null || !Float.isFinite(tick) || tick < startTick || width <= 0 || height <= 0) return;
        int index = recording.indexAt((int) (tick - startTick));
        if (recording.hidden(index)) return;
        Frame frame = recording.frame(index);
        if (frame.equals(failed)) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (!frame.equals(uploaded)) {
                upload(mc, frame);
                prefetch(recording.next(index));
            }
            LIVE.get(this);
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1, 1, 1, Math.max(0, Math.min(1, fade)));
            mc.getTextureManager().bindTexture(location);
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, width, height, width, height);
        } catch (IOException | RuntimeException failure) {
            releaseTextures();
            failed = frame;
            Ponder.LOGGER.warn("Could not play Ponder GUI recording frame {}", frame.path(), failure);
        }
    }

    private void upload(Minecraft mc, Frame frame) throws IOException {
        BufferedImage image;
        if (frame.equals(next)) {
            // A seek may reach a queued frame before the reader starts. Claim it on this thread.
            pending.run();
            try {
                image = pending.get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted reading recording frame", interrupted);
            } catch (ExecutionException failure) {
                throw new IOException("Failed to decode recording frame", failure.getCause());
            }
        } else {
            image = decode(frame);
        }
        cancelPrefetch();
        if (uploaded == null || uploaded.width() != frame.width() || uploaded.height() != frame.height()) {
            deleteTexture();
            long bytes = 4L * frame.width() * frame.height();
            long used = LIVE.values().stream().mapToLong(Long::longValue).sum();
            while (!LIVE.isEmpty() && (LIVE.size() >= 16 || used + bytes > MAX_TEXTURE_BYTES)) {
                RecordedGuiPlayback oldest = LIVE.keySet().iterator().next();
                used -= LIVE.get(oldest);
                oldest.releaseTextures();
            }
            texture = new DynamicTexture(frame.width(), frame.height());
            location = mc.getTextureManager().getDynamicTextureLocation("ponder_recorded_gui", texture);
            LIVE.put(this, bytes);
        }
        image.getRGB(0, 0, frame.width(), frame.height(), texture.getTextureData(), 0, frame.width());
        texture.updateDynamicTexture();
        uploaded = frame;
        failed = null;
    }

    private static BufferedImage decode(Frame frame) throws IOException {
        try (InputStream input = Files.newInputStream(frame.path())) {
            BufferedImage image = TextureUtil.readBufferedImage(input);
            if (image == null || image.getWidth() != frame.width() || image.getHeight() != frame.height())
                throw new IOException("Recording frame dimensions changed: " + frame.path());
            return image;
        }
    }

    private void prefetch(Frame frame) {
        if (frame == null || frame.equals(uploaded) || 4L * frame.width() * frame.height() > 4 * 1024 * 1024) return;
        next = frame;
        pending = new FutureTask<>(() -> decode(frame));
        try {
            READER.execute(pending);
        } catch (RejectedExecutionException busy) {
            cancelPrefetch();
        }
    }

    private void cancelPrefetch() {
        if (pending != null) {
            READER.remove(pending);
            pending.cancel(false);
        }
        pending = null;
        next = null;
    }

    private void deleteTexture() {
        LIVE.remove(this);
        if (location != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(location);
        else if (texture != null) texture.deleteGlTexture();
        texture = null;
        location = null;
        uploaded = null;
    }

    private void releaseTextures() {
        cancelPrefetch();
        deleteTexture();
    }

    @Override
    public void reset() {
        releaseTextures();
        clearPreview();
        statusSnapshot = null;
        recording = null;
        snapshot = null;
        failed = null;
    }

    private void clearPreview() {
        if (showingPreview) {
            showingPreview = false;
            preview.reset();
        }
    }
}
