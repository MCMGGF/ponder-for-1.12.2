package net.createmod.ponder.client;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.createmod.ponder.PonderBackportMod;
import net.createmod.ponder.authoring.AuthoringSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

public final class AuthoringClientCommand extends CommandBase {
    private final ClientProxy client;

    public AuthoringClientCommand(ClientProxy client) {
        this.client = client;
    }

    @Override public String getName() { return "pondereditor"; }
    @Override public String getUsage(ICommandSender sender) { return "/pondereditor [enable|disable|status]"; }
    @Override public int getRequiredPermissionLevel() { return 0; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 1) {
            switch (args[0]) {
                case "enable": setEnabled(sender, true); return;
                case "disable": setEnabled(sender, false); return;
                case "status": sendStatus(sender); return;
                default: throw new WrongUsageException(getUsage(sender));
            }
        }
        if (args.length != 0) throw new WrongUsageException(getUsage(sender));

        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.isSingleplayer() || mc.world == null || mc.player == null) {
            throw new CommandException("ponder.authoring.command.singleplayer");
        }
        if (!AuthoringSettings.isEnabled()) throw new CommandException("ponder.authoring.command.enable_first");
        ClientProxy.queueOpenEditorScreen();
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
        @Nullable BlockPos targetPos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "enable", "disable", "status")
            : Collections.emptyList();
    }

    private void setEnabled(ICommandSender sender, boolean enabled) throws CommandException {
        if (!enabled && !client.canDisableAuthoring()) {
            throw new CommandException("ponder.authoring.command.capture_active");
        }
        try {
            if (!client.changeAuthoring(enabled)) {
                throw new CommandException("ponder.authoring.command.reload_failed");
            }
        } catch (IOException failure) {
            PonderBackportMod.LOGGER.warn("Could not save the local visual authoring preference", failure);
            throw new CommandException("ponder.authoring.command.save_failed");
        }
        sendStatus(sender);
    }

    private static void sendStatus(ICommandSender sender) {
        String key = AuthoringSettings.isEnabled() ? "enabled" : "disabled";
        sender.sendMessage(new TextComponentTranslation("ponder.authoring.command." + key));
        if (AuthoringSettings.hasPendingChange()) {
            sender.sendMessage(new TextComponentTranslation("ponder.authoring.command.pending"));
        }
    }
}
