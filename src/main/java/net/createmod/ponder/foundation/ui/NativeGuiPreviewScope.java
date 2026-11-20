package net.createmod.ponder.foundation.ui;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import net.minecraft.stats.RecipeBook;
import net.minecraft.stats.StatisticsManager;
import org.lwjgl.input.Keyboard;

/** Routes vanilla screen dispatch and screen initialization to a connectionless preview player. */
public final class NativeGuiPreviewScope implements AutoCloseable {
    /** The capture hook adds this marker to the client before any preview scope is entered. */
    public interface Installed {
    }

    private static final ThreadLocal<NativeGuiPreviewScope> ACTIVE = new ThreadLocal<>();
    private static final GameProfile PROFILE = new GameProfile(
        UUID.fromString("d51cdd4b-cd15-4240-9f60-2f6383090391"), "PonderPreview");

    private final Minecraft mc;
    private final EntityPlayerSP originalPlayer;
    private final PlayerControllerMP originalController;
    private final WorldClient originalWorld;
    private final boolean keyboardRepeat;
    private GuiContainer captured;

    static EntityPlayerSP player(Minecraft mc, DetachedPonderWorld world) {
        NetHandlerPlayClient connection = new NetHandlerPlayClient(mc, null, null, PROFILE) {
            @Override
            public void sendPacket(Packet<?> packet) {
                // Preview callbacks have no remote peer.
            }
        };
        return new EntityPlayerSP(mc, world, connection, new StatisticsManager(), new RecipeBook());
    }

    NativeGuiPreviewScope(Minecraft mc, EntityPlayerSP player) {
        if (!(mc instanceof Installed) || ACTIVE.get() != null || !mc.isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Native GUI preview requires an unnested client render scope");
        }
        this.mc = mc;
        originalPlayer = mc.player;
        originalController = mc.playerController;
        originalWorld = mc.world;
        keyboardRepeat = Keyboard.areRepeatEventsEnabled();
        PlayerControllerMP detachedController = new PlayerControllerMP(mc, player.connection);
        ACTIVE.set(this);
        // WorldClient cannot represent the detached World. Use the world supplied to the
        // handler and player, and prevent callbacks from reaching the current client world here.
        mc.world = null;
        mc.playerController = detachedController;
        mc.player = player;
    }

    public static boolean capture(GuiScreen screen) {
        NativeGuiPreviewScope scope = ACTIVE.get();
        if (scope == null) {
            return false;
        }
        if (screen instanceof GuiContainer container) {
            scope.captured = container;
        }
        return true;
    }

    GuiContainer captured() {
        return captured;
    }

    @Override
    public void close() {
        mc.player = originalPlayer;
        mc.playerController = originalController;
        mc.world = originalWorld;
        ACTIVE.remove();
        Keyboard.enableRepeatEvents(keyboardRepeat);
    }
}
