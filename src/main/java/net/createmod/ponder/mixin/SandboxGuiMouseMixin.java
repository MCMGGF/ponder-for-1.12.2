package net.createmod.ponder.mixin;

import net.createmod.ponder.foundation.ui.sandbox.worker.SandboxWorker;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keep the real screen, overlays and carried stack at the scripted pointer in the recorder. */
@Mixin(value = ForgeHooksClient.class, remap = false)
abstract class SandboxGuiMouseMixin {
    @ModifyVariable(method = "drawScreen", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static int ponderRecorderMouseX(int actual) {
        return SandboxWorker.mouseX(actual);
    }

    @ModifyVariable(method = "drawScreen", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static int ponderRecorderMouseY(int actual) {
        return SandboxWorker.mouseY(actual);
    }
}
