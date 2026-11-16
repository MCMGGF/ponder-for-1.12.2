package net.createmod.ponder.client;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public final class AuthoringClientCommand extends CommandBase {
    @Override public String getName() { return "pondereditor"; }
    @Override public String getUsage(ICommandSender sender) { return "/pondereditor"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args) throws CommandException {
        Minecraft mc=Minecraft.getMinecraft();
        if(!mc.isSingleplayer() || mc.world == null || mc.player == null)
            throw new CommandException("\u672c\u5730\u521b\u4f5c\u4ec5\u652f\u6301\u5355\u4eba\u4e16\u754c");
        ClientProxy.queueOpenEditorScreen();
    }
}
