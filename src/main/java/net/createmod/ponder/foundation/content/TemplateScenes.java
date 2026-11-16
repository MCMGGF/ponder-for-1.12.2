package net.createmod.ponder.foundation.content;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;

public class TemplateScenes {

    public static void templateMethod(SceneBuilder scene, SceneBuildingUtil util) {
        VanillaTutorialScenes.crafting(scene, util, "crafting_table");
    }
}
