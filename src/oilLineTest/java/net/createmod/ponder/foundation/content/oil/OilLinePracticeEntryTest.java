package net.createmod.ponder.foundation.content.oil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.createmod.ponder.api.client.PonderLessonPractice;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.content.MmcePonderPlugin;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OilLinePracticeEntryTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test void addonRunsAfterTheMachinePluginAndKeepsTheOriginalNamespace() {
        PonderPlugin oil = new OilProductionLinePlugin();
        PonderPlugin machines = new MmcePonderPlugin();
        assertEquals("ponder_early_oil_line", oil.getModId());
        assertEquals("ponder_legacy", oil.getRegistrationNamespace());
        assertEquals(new ResourceLocation("ponder_legacy:production/early_oil_line"), OilProductionLinePlugin.COMPONENT);
        assertEquals(new ResourceLocation("ponder_legacy:early_oil_line"), OilProductionLinePlugin.TAG);

        PonderIndex.addPlugin(oil);
        PonderIndex.addPlugin(machines);
        List<PonderPlugin> order = new ArrayList<>();
        PonderIndex.forEachPlugin(order::add);
        assertEquals(Arrays.asList(machines, oil), order);
    }

    @Test void onlyTheSixExactOilSceneIdentitiesExposeOperatingPractice() {
        ResourceLocation overview = new ResourceLocation("ponder_legacy", "production/early_oil/overview");
        assertNull(PonderLessonPractice.find(overview));
        EarlyOilLineMod.registerPractice();

        for (String chapter : new String[] {"overview", "steam", "pumping", "distillation", "power", "troubleshooting"}) {
            assertNotNull(PonderLessonPractice.find(new ResourceLocation("ponder_legacy", "production/early_oil/" + chapter)));
        }
        for (String id : new String[] {"other:production/early_oil/overview", "ponder_legacy:production/early_oil/unknown",
            "ponder_legacy:production/early_oil/overview/extra", "ponder_legacy:vanilla/crafting",
            "ponder_legacy:mmce/modularmachinery/aw_distillation_tower/minimum"}) {
            assertNull(PonderLessonPractice.find(new ResourceLocation(id)), id);
        }
        assertNull(PonderLessonPractice.find(null));
    }
}
