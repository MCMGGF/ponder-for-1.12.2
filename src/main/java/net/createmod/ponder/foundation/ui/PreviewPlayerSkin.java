package net.createmod.ponder.foundation.ui;

import java.lang.reflect.Field;
import java.util.Map;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

/** Reads completed texture caches only. No skin lookup method that initiates a download is used. */
final class PreviewPlayerSkin {
    enum Mode {
        CURRENT, STEVE, ALEX;

        Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    static final ResourceLocation STEVE = new ResourceLocation("minecraft", "textures/entity/steve.png");
    static final ResourceLocation ALEX = new ResourceLocation("minecraft", "textures/entity/alex.png");
    final ResourceLocation texture;
    final boolean slim;

    private PreviewPlayerSkin(ResourceLocation texture, boolean slim) {
        this.texture = texture;
        this.slim = slim;
    }

    static PreviewPlayerSkin resolve(Minecraft minecraft, Mode mode) {
        if (mode == Mode.ALEX) {
            return new PreviewPlayerSkin(ALEX, true);
        }
        if (mode == Mode.STEVE || minecraft == null || minecraft.player == null) {
            return new PreviewPlayerSkin(STEVE, false);
        }
        java.util.UUID uuid = minecraft.player.getUniqueID();
        PreviewPlayerSkin fallback = new PreviewPlayerSkin(DefaultPlayerSkin.getDefaultSkin(uuid),
            "slim".equals(DefaultPlayerSkin.getSkinType(uuid)));
        if (minecraft.getConnection() == null) {
            return fallback;
        }
        try {
            NetworkPlayerInfo info = minecraft.getConnection().getPlayerInfo(uuid);
            ResourceLocation location = cachedLocation(info);
            if (location == null) {
                return fallback;
            }
            ITextureObject texture = minecraft.getTextureManager().getTexture(location);
            if (texture == null || (texture instanceof ThreadDownloadImageData && !completedDownload(texture))) {
                return fallback;
            }
            // getSkinType is a field/default-model read in 1.12.2, unlike getLocationSkin/hasLocationSkin.
            return new PreviewPlayerSkin(location, "slim".equals(info.getSkinType()));
        } catch (RuntimeException | LinkageError failure) {
            return fallback;
        }
    }

    static ResourceLocation cachedLocation(Object info) {
        Object textures = read(info, "playerTextures", "field_187107_a");
        if (!(textures instanceof Map)) {
            return null;
        }
        Object location = ((Map<?, ?>) textures).get(MinecraftProfileTexture.Type.SKIN);
        return location instanceof ResourceLocation ? (ResourceLocation) location : null;
    }

    static boolean completedDownload(Object texture) {
        return read(texture, "bufferedImage", "field_110560_d") instanceof java.awt.image.BufferedImage
            || Boolean.TRUE.equals(read(texture, "textureUploaded", "field_110559_g"));
    }

    private static Object read(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Unsupported mappings or an inaccessible cache must fall back without triggering a lookup.
                }
            }
        }
        return null;
    }
}
