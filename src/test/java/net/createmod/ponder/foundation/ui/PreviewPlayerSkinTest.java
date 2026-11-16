package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

class PreviewPlayerSkinTest {
    @Test
    void offlineSkinSelectionIsAvailableWithoutMinecraftOrNetwork() {
        assertEquals(PreviewPlayerSkin.STEVE, PreviewPlayerSkin.resolve(null, PreviewPlayerSkin.Mode.STEVE).texture);
        assertEquals(PreviewPlayerSkin.ALEX, PreviewPlayerSkin.resolve(null, PreviewPlayerSkin.Mode.ALEX).texture);
        assertTrue(PreviewPlayerSkin.resolve(null, PreviewPlayerSkin.Mode.ALEX).slim);
        assertFalse(PreviewPlayerSkin.resolve(null, PreviewPlayerSkin.Mode.CURRENT).slim);
        assertEquals(PreviewPlayerSkin.Mode.STEVE, PreviewPlayerSkin.Mode.CURRENT.next());
        assertEquals(PreviewPlayerSkin.Mode.CURRENT, PreviewPlayerSkin.Mode.ALEX.next());
    }

    @Test
    void mcpAndSrgTextureFieldsAreReadWithoutCallingLookupMethods() {
        ResourceLocation skin = new ResourceLocation("test", "cached_skin");
        McpInfo mcp = new McpInfo();
        SrgInfo srg = new SrgInfo();
        mcp.playerTextures.put(MinecraftProfileTexture.Type.SKIN, skin);
        srg.field_187107_a.put(MinecraftProfileTexture.Type.SKIN, skin);
        assertEquals(skin, PreviewPlayerSkin.cachedLocation(mcp));
        assertEquals(skin, PreviewPlayerSkin.cachedLocation(srg));
        assertNull(PreviewPlayerSkin.cachedLocation(null));
        assertNull(PreviewPlayerSkin.cachedLocation(new Object()));
        assertNull(PreviewPlayerSkin.cachedLocation(new McpInfo()));
    }

    @Test
    void inFlightDownloadIsRejectedUntilImageOrUploadExists() {
        McpDownload download = new McpDownload();
        assertFalse(PreviewPlayerSkin.completedDownload(download));
        download.bufferedImage = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        assertTrue(PreviewPlayerSkin.completedDownload(download));
        download.bufferedImage = null;
        download.textureUploaded = true;
        assertTrue(PreviewPlayerSkin.completedDownload(download));
        assertFalse(PreviewPlayerSkin.completedDownload(new Object()));
    }

    @Test
    void srgDownloadFieldsAndInheritedFieldsAreRecognized() {
        SrgDownload download = new SrgDownload();
        assertFalse(PreviewPlayerSkin.completedDownload(download));
        download.field_110559_g = true;
        assertTrue(PreviewPlayerSkin.completedDownload(download));
        McpInfo info = new McpInfo() {};
        ResourceLocation skin = new ResourceLocation("test", "inherited");
        info.playerTextures.put(MinecraftProfileTexture.Type.SKIN, skin);
        assertEquals(skin, PreviewPlayerSkin.cachedLocation(info));
    }

    static class McpInfo {
        private final Map<MinecraftProfileTexture.Type, ResourceLocation> playerTextures =
            new EnumMap<MinecraftProfileTexture.Type, ResourceLocation>(MinecraftProfileTexture.Type.class);
        public ResourceLocation getLocationSkin() { throw new AssertionError("must not initiate a download"); }
        public boolean hasLocationSkin() { throw new AssertionError("must not initiate a download"); }
    }

    static class SrgInfo {
        private final Map<MinecraftProfileTexture.Type, ResourceLocation> field_187107_a =
            new EnumMap<MinecraftProfileTexture.Type, ResourceLocation>(MinecraftProfileTexture.Type.class);
    }

    static class McpDownload {
        private BufferedImage bufferedImage;
        private boolean textureUploaded;
    }

    static class SrgDownload {
        private BufferedImage field_110560_d;
        private boolean field_110559_g;
    }
}
