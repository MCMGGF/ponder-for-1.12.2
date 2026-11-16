package net.createmod.ponder.authoring;

import net.createmod.ponder.Reference;
import net.createmod.ponder.authoring.projection.ProjectorBlock;
import net.createmod.ponder.authoring.projection.ProjectorTile;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class AuthoringContent {
    public static final ProjectorBlock MINIATURE = new ProjectorBlock(true);
    public static final ProjectorBlock LIFE_SIZE = new ProjectorBlock(false);
    public static final Item BLUEPRINT = new Item().setMaxStackSize(1).setTranslationKey("ponder.blueprint")
        .setCreativeTab(net.minecraft.creativetab.CreativeTabs.TOOLS);
    static {
        MINIATURE.setRegistryName(Reference.MOD_ID, "miniature_projector");
        LIFE_SIZE.setRegistryName(Reference.MOD_ID, "life_size_projector");
        BLUEPRINT.setRegistryName(Reference.MOD_ID, "blueprint");
    }
    private AuthoringContent() {}
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(MINIATURE, LIFE_SIZE);
        GameRegistry.registerTileEntity(ProjectorTile.class, new ResourceLocation(Reference.MOD_ID, "projector"));
    }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(BLUEPRINT, new ItemBlock(MINIATURE).setRegistryName(MINIATURE.getRegistryName()),
            new ItemBlock(LIFE_SIZE).setRegistryName(LIFE_SIZE.getRegistryName()));
    }
    @SubscribeEvent public static void firstCorner(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if (event.getItemStack().getItem() != BLUEPRINT) return;
        event.setCanceled(true);
        if (event.getWorld().isRemote) net.createmod.ponder.PonderBackportMod.proxy.selectBlueprintCorner(event.getPos(), false);
    }
    @SubscribeEvent public static void secondCorner(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() != BLUEPRINT) return;
        event.setCanceled(true);
        if (event.getWorld().isRemote && event.getHand() == net.minecraft.util.EnumHand.MAIN_HAND)
            net.createmod.ponder.PonderBackportMod.proxy.selectBlueprintCorner(event.getPos(), true);
    }
}
