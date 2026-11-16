package net.createmod.ponder.foundation.ui;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.content.oil.OilProductionLinePlugin;
import net.minecraft.util.ResourceLocation;

/** An explicit lesson allowlist, not a substring match on third-party scene names. */
final class OilLinePracticeEntry {
    private OilLinePracticeEntry() {}

    static boolean matches(ResourceLocation sceneId) {
        if (sceneId == null || !Ponder.MOD_ID.equals(sceneId.getNamespace())) return false;
        for (String chapter : OilProductionLinePlugin.CHAPTERS) {
            if (("production/early_oil/" + chapter).equals(sceneId.getPath())) return true;
        }
        return false;
    }
}
