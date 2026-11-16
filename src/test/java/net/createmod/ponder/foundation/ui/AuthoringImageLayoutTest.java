package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class AuthoringImageLayoutTest {
    @Test void compactAndWideLayoutsKeepThumbnailButtonsAboveStatusAndFooter() {
        for (int[] size : new int[][] {{320, 240}, {640, 360}, {960, 540}, {1920, 1080}}) {
            AuthoringImageLayout layout = new AuthoringImageLayout(size[0], size[1]);
            assertTrue(layout.columns >= 1 && layout.columns <= 5); assertTrue(layout.rows >= 1 && layout.rows <= 4);
            assertTrue(layout.slots <= 16, "A thumbnail page must fit the live texture budget without per-frame eviction thrashing");
            for (int slot = 0; slot < layout.slots; slot++) {
                assertTrue(layout.x(slot) >= 0); assertTrue(layout.x(slot) + layout.cellWidth <= size[0]);
                assertTrue(layout.y(slot) >= 64); assertTrue(layout.y(slot) + 82 < size[1] - 43);
            }
            assertEquals(0, layout.clampPage(-100, 512)); assertEquals(layout.pageCount(512) - 1, layout.clampPage(99999, 512));
        }
    }
    @Test void browserKeepsFullIdsInTooltipsAndReleasesEachPageAndClosedScreen() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/net/createmod/ponder/foundation/ui/AuthoringImageScreen.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("fontRenderer.trimStringToWidth(id")); assertTrue(source.contains("drawHoveringText"));
        assertTrue(source.contains("imageId.setMaxStringLength(220)")); assertTrue(source.contains("clearPreviews(); buttonList.clear()"));
        assertTrue(source.contains("onGuiClosed() { clearPreviews()")); assertTrue(source.contains("ImageStepHandlers.append(document, index"));
    }
    @Test void rendererUploadsOnlyInRenderAndRestoresStateWithoutRealInteraction() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/net/createmod/ponder/foundation/ui/ImageSnapshotProvider.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("MAX_LIVE_TEXTURES = 16")); assertTrue(source.contains("MAX_LIVE_BYTES = 64L"));
        assertTrue(source.contains("PreviewNativeRenderGuard.run")); assertTrue(source.contains("GlStateManager.depthMask(false)"));
        assertTrue(source.contains("pool.readChecked(image)")); assertTrue(source.contains("getTextureManager().deleteTexture(texture)"));
        String constructor = source.substring(source.indexOf("public ImageSnapshotProvider("), source.indexOf("public ResourceLocation register()"));
        assertFalse(constructor.contains("upload(")); assertFalse(constructor.contains("getMinecraft"));
        for (String forbidden : new String[] {"mc.player", "mc.world", "playerController", "sendPacket", "URLConnection", "new URL("}) assertFalse(source.contains(forbidden));
    }
}
