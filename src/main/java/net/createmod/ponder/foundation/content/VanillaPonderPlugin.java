package net.createmod.ponder.foundation.content;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

/** The six curated lessons, independently of renderer and UI diagnostic scenes. */
public final class VanillaPonderPlugin implements PonderPlugin {

    public static final ResourceLocation TUTORIAL_TAG = Ponder.asResource("vanilla_tutorials");
    private static final String[] COMPONENTS = { "stone", "crafting_table", "furnace", "hopper", "redstone", "rail" };
    private static final PonderStoryBoard[] LESSONS = { VanillaTutorialScenes::building,
        VanillaTutorialScenes::crafting, VanillaTutorialScenes::smelting, VanillaTutorialScenes::logistics,
        VanillaTutorialScenes::redstone, VanillaTutorialScenes::rails };

    @Override
    public String getModId() {
        return Ponder.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        for (int i = 0; i < COMPONENTS.length; i++) {
            // Reuse the bundled empty schematic; every lesson authors its vanilla blocks as timeline events.
            helper.addStoryBoard(new ResourceLocation("minecraft", COMPONENTS[i]), Ponder.asResource("mmce/empty"),
                LESSONS[i], TUTORIAL_TAG);
        }
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(TUTORIAL_TAG).title("原版操作教学")
            .description("人物分步演示搭建、合成、熔炼、物流、红石与矿车。")
            .item(Blocks.CRAFTING_TABLE).addToIndex().register();
        for (String component : COMPONENTS) {
            helper.addTagToComponent(new ResourceLocation("minecraft", component), TUTORIAL_TAG);
        }
    }
}
