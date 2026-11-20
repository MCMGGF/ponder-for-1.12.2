package net.createmod.ponder.api.client;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Client registrations for Ponder screens that lack a Forge client GUI handler. */
public final class PonderNativeGuiScreens {
    private static final Map<ResourceLocation, Factory> FACTORIES = new ConcurrentHashMap<>();

    private PonderNativeGuiScreens() {
    }

    /** Registers a factory during client mod initialization under a GUI demo's {@code factory} identifier. */
    public static void register(ResourceLocation id, Factory factory) {
        if (FACTORIES.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(factory)) != null) {
            throw new IllegalArgumentException("Ponder native GUI factory already registered: " + id);
        }
    }

    @Nullable
    public static Factory get(ResourceLocation id) {
        return FACTORIES.get(id);
    }

    @FunctionalInterface
    public interface Factory {
        /**
         * Constructs a screen for the block in the supplied detached world using the preview player.
         * Ponder owns initialization and rendering of the returned screen. The factory must only
         * construct the preview screen and its container. Return null for an unsupported block.
         */
        @Nullable
        GuiContainer create(World world, BlockPos pos, EntityPlayerSP player);
    }
}
