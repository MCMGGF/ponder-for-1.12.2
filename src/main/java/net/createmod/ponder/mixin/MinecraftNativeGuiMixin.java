package net.createmod.ponder.mixin;

import net.createmod.ponder.foundation.ui.NativeGuiPreviewScope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftNativeGuiMixin implements NativeGuiPreviewScope.Installed {
    @Inject(method = "displayGuiScreen", at = @At("HEAD"), cancellable = true, require = 1)
    private void capturePonderPreview(GuiScreen screen, CallbackInfo callback) {
        if (NativeGuiPreviewScope.capture(screen)) {
            callback.cancel();
        }
    }
}
