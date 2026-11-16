package net.createmod.ponder.client;

import net.createmod.ponder.Reference;
import net.createmod.ponder.authoring.AuthoringContent;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid=Reference.MOD_ID,value=Side.CLIENT)
public final class AuthoringModels {
    @SubscribeEvent public static void models(ModelRegistryEvent event) {
        for(Item item:new Item[]{AuthoringContent.BLUEPRINT,Item.getItemFromBlock(AuthoringContent.MINIATURE),Item.getItemFromBlock(AuthoringContent.LIFE_SIZE)})
            ModelLoader.setCustomModelResourceLocation(item,0,new ModelResourceLocation(item.getRegistryName(),"inventory"));
    }
}
