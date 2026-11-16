package net.createmod.ponder.foundation.content;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.util.ResourceLocation;

/**
 * Small, predictable content set for exercising the in-game browser and player.
 */
public class UiTestPonderPlugin implements PonderPlugin {

    private static final ResourceLocation UI_TAG = Ponder.asResource("ui_test");
    private static final ResourceLocation MOTION_TAG = Ponder.asResource("ui_motion");

    private static final ResourceLocation CRAFTING_TABLE = new ResourceLocation("minecraft", "crafting_table");
    private static final ResourceLocation FURNACE = new ResourceLocation("minecraft", "furnace");
    private static final ResourceLocation CHEST = new ResourceLocation("minecraft", "chest");
    private static final ResourceLocation PISTON = new ResourceLocation("minecraft", "piston");
    private static final ResourceLocation REDSTONE_LAMP = new ResourceLocation("minecraft", "redstone_lamp");
    private static final ResourceLocation WATER_BUCKET = new ResourceLocation("minecraft", "water_bucket");

    @Override
    public String getModId() {
        return Ponder.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        // Multiple scenes on one component exercise scene tabs and next/previous navigation.
        // These UI scenes use a vanilla-only schematic set so they remain useful
        // in a clean test instance without Create installed.
        helper.addStoryBoard(CRAFTING_TABLE, "ui_test/scene_1", DebugScenes::coordinateScene, UI_TAG);
        helper.addStoryBoard(CRAFTING_TABLE, "ui_test/scene_2", DebugScenes::blocksScene, UI_TAG);
        helper.addStoryBoard(CRAFTING_TABLE, "ui_test/scene_6", DebugScenes::controlsScene, MOTION_TAG);

        helper.addStoryBoard(FURNACE, "ui_test/scene_4", DebugScenes::offScreenScene, UI_TAG);
        helper.addStoryBoard(FURNACE, "ui_test/scene_5", DebugScenes::particleScene, MOTION_TAG);
        helper.addStoryBoard(FURNACE, "ui_test/runtime_test", DebugScenes::guiSnapshotScene, UI_TAG);

        helper.addStoryBoard(CHEST, "ui_test/scene_7", DebugScenes::birbScene, UI_TAG);
        helper.addStoryBoard(PISTON, "ui_test/scene_8", DebugScenes::sectionsScene, MOTION_TAG);
        helper.addStoryBoard(REDSTONE_LAMP, "ui_test/scene_8", DebugScenes::trackingScene, MOTION_TAG);
        helper.addStoryBoard(WATER_BUCKET, "ui_test/scene_9", DebugScenes::itemScene, UI_TAG);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(UI_TAG)
            .title("UI Test Scenes")
            .description("Scenes for testing the Ponder browser, text overlays and scene playback.")
            .item(Blocks.CRAFTING_TABLE)
            .addToIndex()
            .register();

        helper.registerTag(MOTION_TAG)
            .title("Motion Test Scenes")
            .description("Scenes for testing camera motion, section movement and interactive controls.")
            .item(Items.COMPASS)
            .addToIndex()
            .register();

        for (ResourceLocation component : new ResourceLocation[] {
            CRAFTING_TABLE, FURNACE, CHEST, PISTON, REDSTONE_LAMP, WATER_BUCKET
        }) {
            helper.addTagToComponent(component, UI_TAG);
        }
        helper.addTagToComponent(CRAFTING_TABLE, MOTION_TAG);
        helper.addTagToComponent(FURNACE, MOTION_TAG);
        helper.addTagToComponent(PISTON, MOTION_TAG);
        helper.addTagToComponent(REDSTONE_LAMP, MOTION_TAG);
    }
}
