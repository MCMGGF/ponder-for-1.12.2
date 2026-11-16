package net.createmod.ponder.foundation.content;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

public class BasePonderPlugin implements PonderPlugin {

    private static final ResourceLocation WELCOME_TAG = Ponder.asResource("welcome");
    private static final ResourceLocation CRAFTING_TABLE = new ResourceLocation("minecraft", "crafting_table");

    @Override
    public String getModId() {
        return Ponder.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.addStoryBoard(CRAFTING_TABLE, Ponder.asResource("mmce/empty"), TemplateScenes::templateMethod, WELCOME_TAG);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(WELCOME_TAG)
            .title("开始原版教学")
            .description("通过人物操作学习工作台与当前实例的合成配方。")
            .item(Blocks.CRAFTING_TABLE)
            .addToIndex()
            .register();

        helper.addTagToComponent(CRAFTING_TABLE, WELCOME_TAG);
    }

    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {
        helper.registerSharedText("backport.placeholder", "Ponder 原版操作教学");
    }
}
