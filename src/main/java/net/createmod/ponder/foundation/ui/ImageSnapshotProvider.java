package net.createmod.ponder.foundation.ui;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.authoring.assets.ImageAssetPool;
import net.createmod.ponder.authoring.assets.ImageAssetPool.ImageInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;

/** PNGs are validated without GL; upload is lazy, bounded and released with the existing preview lifecycle. */
public final class ImageSnapshotProvider implements PonderGuiSnapshotRegistry.ResettableSnapshotProvider,
    PonderGuiSnapshotRegistry.SnapshotRenderer {
    private static final int MAX_LIVE_TEXTURES = 16;
    private static final long MAX_LIVE_BYTES = 64L * 1024 * 1024;
    private static final Map<ImageSnapshotProvider, Long> LIVE = new LinkedHashMap<ImageSnapshotProvider, Long>(16, .75F, true);
    private final ImageAssetPool pool;
    private final ImageInfo image;
    private final PonderGuiSnapshotRegistry.Snapshot snapshot;
    private ResourceLocation texture;
    private String failure;

    public ImageSnapshotProvider(ImageAssetPool pool, ImageInfo image) {
        if (pool == null || image == null) throw new IllegalArgumentException("An explicit validated local image is required");
        this.pool = pool; this.image = image;
        double scale = Math.min(1, 256D / Math.max(image.width, image.height));
        snapshot = PonderGuiSnapshotRegistry.Snapshot.liveRenderer(Math.max(1, (int) Math.round(image.width * scale)),
            Math.max(1, (int) Math.round(image.height * scale)), false, this);
    }
    public ResourceLocation register() {
        ResourceLocation id = Ponder.asResource("gui_snapshot/author_image/" + image.sha256.toLowerCase(java.util.Locale.ROOT));
        PonderGuiSnapshotRegistry.registerDynamicProvider(id, this); return id;
    }
    @Override public PonderGuiSnapshotRegistry.Snapshot provide(float tick) { return Float.isFinite(tick) ? snapshot : null; }
    public String getImageId() { return image.id; }
    public String getFailure() { return failure; }
    public boolean isUploaded() { return texture != null; }
    public static int getLiveTextureCount() { synchronized (LIVE) { return LIVE.size(); } }
    private void upload(Minecraft mc) throws IOException {
        if (texture != null) { synchronized (LIVE) { LIVE.get(this); } return; }
        byte[] bytes = pool.readChecked(image);
        synchronized (LIVE) {
            long used = 0; for (long size : LIVE.values()) used += size;
            Iterator<Map.Entry<ImageSnapshotProvider, Long>> iterator = LIVE.entrySet().iterator();
            while ((LIVE.size() >= MAX_LIVE_TEXTURES || used + image.rgbaBytes > MAX_LIVE_BYTES) && iterator.hasNext()) {
                Map.Entry<ImageSnapshotProvider, Long> old = iterator.next(); used -= old.getValue(); iterator.remove(); old.getKey().deleteTexture();
            }
            java.awt.image.BufferedImage decoded = TextureUtil.readBufferedImage(new ByteArrayInputStream(bytes));
            if (decoded == null || decoded.getWidth() != image.width || decoded.getHeight() != image.height)
                throw new IOException("PNG decoder dimensions differ from the validated image");
            DynamicTexture uploaded = new DynamicTexture(decoded);
            try { texture = mc.getTextureManager().getDynamicTextureLocation("ponder_authoring_image", uploaded); }
            catch (RuntimeException | LinkageError error) { uploaded.deleteGlTexture(); throw error; }
            LIVE.put(this, image.rgbaBytes);
        }
    }
    @Override public void render(int x, int y, int width, int height, float tick, float fade) {
        if (width < 1 || height < 1 || !Float.isFinite(tick) || !Float.isFinite(fade)) return;
        Minecraft mc = Minecraft.getMinecraft();
        PreviewNativeRenderGuard.run(() -> {
            GlStateManager.disableDepth(); GlStateManager.depthMask(false);
            try {
                if (failure == null) upload(mc);
                if (failure == null && texture != null) {
                    float scale = Math.min(width / (float) image.width, height / (float) image.height);
                    int w = Math.max(1, Math.round(image.width * scale)), h = Math.max(1, Math.round(image.height * scale));
                    GlStateManager.enableTexture2D(); GlStateManager.enableBlend();
                    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                    GlStateManager.color(1, 1, 1, Math.max(0, Math.min(1, fade)));
                    mc.getTextureManager().bindTexture(texture);
                    Gui.drawModalRectWithCustomSizedTexture(x + (width - w) / 2, y + (height - h) / 2, 0, 0, w, h, w, h);
                }
            } catch (IOException | RuntimeException | LinkageError error) {
                failure = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                resetTexture(); Ponder.LOGGER.warn("Could not render local authoring image {}", image.id, error);
            }
            if (failure != null) {
                Gui.drawRect(x, y, x + width, y + height, 0xE0202428);
                mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth("! " + image.id, Math.max(1, width - 8)), x + 4, y + 4, 0xFFEC9A91);
            }
        });
    }
    private void deleteTexture() {
        if (texture != null) { Minecraft.getMinecraft().getTextureManager().deleteTexture(texture); texture = null; }
    }
    private void resetTexture() { synchronized (LIVE) { LIVE.remove(this); deleteTexture(); } }
    @Override public void reset() { resetTexture(); failure = null; }
}
