package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

class OilLinePracticeEntryTest {
    @Test void onlyTheSixExactOilSceneIdentitiesExposeOperatingPractice() {
        for (String chapter : new String[] {"overview", "steam", "pumping", "distillation", "power", "troubleshooting"}) {
            assertTrue(OilLinePracticeEntry.matches(new ResourceLocation("ponder_legacy", "production/early_oil/" + chapter)));
        }
        for (String id : new String[] {"other:production/early_oil/overview", "ponder_legacy:production/early_oil/unknown",
            "ponder_legacy:production/early_oil/overview/extra", "ponder_legacy:vanilla/crafting",
            "ponder_legacy:mmce/modularmachinery/aw_distillation_tower/minimum"}) {
            assertFalse(OilLinePracticeEntry.matches(new ResourceLocation(id)), id);
        }
        assertFalse(OilLinePracticeEntry.matches(null));
    }
}
