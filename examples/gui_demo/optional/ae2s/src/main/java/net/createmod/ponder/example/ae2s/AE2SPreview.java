package net.createmod.ponder.example.ae2s;

import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.orientation.RelativeSide;
import ae2.client.gui.implementations.GuiInscriber;
import ae2.client.gui.implementations.GuiOutputSides;
import ae2.container.GuiIds.GuiKey;
import ae2.container.implementations.ContainerOutputSides;
import ae2.core.gui.AEGuiHandler;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.tile.misc.TileInscriber;
import java.util.Set;
import net.createmod.ponder.api.client.PonderNativeGuiScreens;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = AE2SPreview.MOD_ID, name = "Ponder AE2S GUI Preview", version = "1.0.0",
    clientSideOnly = true, dependencies = "required-after:ponder_legacy;required-after:ae2@[1.1.2]")
public final class AE2SPreview {
    static final String MOD_ID = "ponder_ae2s_preview";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        PonderNativeGuiScreens.register(new ResourceLocation(MOD_ID, "inscriber"), AE2SPreview::inscriber);
        PonderNativeGuiScreens.register(new ResourceLocation(MOD_ID, "output_sides"), AE2SPreview::outputSides);
    }

    private static GuiContainer inscriber(World world, BlockPos pos, EntityPlayerSP player) {
        if (!(world.getTileEntity(pos) instanceof TileInscriber host)) {
            return null;
        }
        Object screen = new AEGuiHandler().getClientGuiElement(
            GuiKey.INSCRIBER.ordinal(), player, world, pos.getX(), pos.getY(), pos.getZ());
        if (!(screen instanceof GuiInscriber gui)) {
            return null;
        }
        // The live screen receives this setting from its server-side container.
        gui.getContainer().autoExport = (YesNo) host.getConfigManager().getSetting(Settings.AUTO_EXPORT);
        return gui;
    }

    private static GuiContainer outputSides(World world, BlockPos pos, EntityPlayerSP player) {
        if (!(world.getTileEntity(pos) instanceof TileInscriber host)) {
            return null;
        }
        var container = new ContainerOutputSides(player.inventory, host);
        container.setLocator(GuiHostLocators.forTile(host));
        container.allowedSideMask = sideMask(host, host.getAllowedOutputSides());
        container.outputSideMask = sideMask(host, host.getOutputSides());
        return new GuiOutputSides(container, player.inventory);
    }

    private static int sideMask(TileInscriber host, Set<EnumFacing> faces) {
        int mask = 0;
        var orientation = host.getBlockOrientation();
        for (RelativeSide side : RelativeSide.values()) {
            if (faces.contains(orientation.getSide(side))) {
                mask |= 1 << side.ordinal();
            }
        }
        return mask;
    }
}
