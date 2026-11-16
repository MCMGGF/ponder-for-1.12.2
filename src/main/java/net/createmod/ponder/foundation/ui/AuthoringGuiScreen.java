package net.createmod.ponder.foundation.ui;

/** Authoring and isolated practice never show a real inventory/HUD behind virtual slots. */
public abstract class AuthoringGuiScreen extends CompatGuiScreen {
    @Override public void drawDefaultBackground() {
        drawRect(0, 0, width, height, 0xFF14191C);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new net.minecraftforge.client.event.GuiScreenEvent.BackgroundDrawnEvent(this));
    }
}
