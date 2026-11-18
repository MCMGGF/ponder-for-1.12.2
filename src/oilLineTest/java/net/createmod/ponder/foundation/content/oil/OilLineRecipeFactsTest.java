package net.createmod.ponder.foundation.content.oil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OilLineRecipeFactsTest {

    @Test
    void usesTheActualPackMachineAndFluidIds() {
        assertEquals("aw_steam_bay", OilLineRecipeFacts.MACHINE_BOILER);
        assertEquals("aw_oil_pump", OilLineRecipeFacts.MACHINE_PUMP);
        assertEquals("aw_distillation_tower", OilLineRecipeFacts.MACHINE_DISTILLATION);
        assertEquals("aw_diesel_generator", OilLineRecipeFacts.MACHINE_GENERATOR);
        assertEquals("water", OilLineRecipeFacts.FLUID_WATER);
        assertEquals("ic2steam", OilLineRecipeFacts.FLUID_STEAM);
        assertEquals("oil", OilLineRecipeFacts.FLUID_OIL);
        assertEquals("diesel", OilLineRecipeFacts.FLUID_DIESEL);
        assertEquals("lubricant", OilLineRecipeFacts.FLUID_LUBRICANT);
        assertEquals("immersivepetroleum:material", OilLineRecipeFacts.ITEM_BITUMEN);
        assertEquals(0, OilLineRecipeFacts.ITEM_BITUMEN_META);
    }

    @Test
    void fourNormalPumpBatchesSupplyOneDistillation() {
        assertEquals(400, OilLineRecipeFacts.PUMP_TICKS);
        assertEquals(250, OilLineRecipeFacts.PUMP_OIL_MB);
        assertEquals(1000, OilLineRecipeFacts.DISTILL_OIL_MB);
        assertEquals(4, OilLineRecipeFacts.pumpBatchesPerDistillation());
        assertEquals(OilLineRecipeFacts.DISTILL_OIL_MB,
            OilLineRecipeFacts.pumpBatchesPerDistillation() * OilLineRecipeFacts.PUMP_OIL_MB);
    }

    @Test
    void processSteamLedgerDoesNotPromiseAColdStartBudget() {
        assertEquals(40, OilLineRecipeFacts.PUMP_STEAM);
        assertEquals(80, OilLineRecipeFacts.DISTILL_STEAM);
        assertEquals(240, OilLineRecipeFacts.steamPerDistillationIncludingOil());
        assertEquals(4 * 40 + 80, OilLineRecipeFacts.steamPerDistillationIncludingOil());
    }

    @Test
    void distillationKeepsBothMandatoryByproducts() {
        assertEquals(600, OilLineRecipeFacts.DISTILL_TICKS);
        assertEquals(700, OilLineRecipeFacts.DISTILL_DIESEL_MB);
        assertEquals(200, OilLineRecipeFacts.DISTILL_LUBRICANT_MB);
        assertEquals(1, OilLineRecipeFacts.DISTILL_BITUMEN);
    }

    @Test
    void oneDieselBatchRunsForSevenHundredTicksAndProducesItsActualEnergy() {
        assertEquals(20, OilLineRecipeFacts.GENERATOR_TICKS);
        assertEquals(20, OilLineRecipeFacts.GENERATOR_DIESEL_MB);
        assertEquals(256, OilLineRecipeFacts.GENERATOR_FE_PER_TICK);
        assertEquals(0, OilLineRecipeFacts.DISTILL_DIESEL_MB % OilLineRecipeFacts.GENERATOR_DIESEL_MB);
        assertEquals(700, OilLineRecipeFacts.generatorTicksPerDistillation());
        assertEquals(179200L, OilLineRecipeFacts.energyPerDistillation());
    }

    @Test
    void onePumpCannotSupplyThePeakGeneratorRateContinuously() {
        assertEquals(112.0, OilLineRecipeFacts.sustainableFePerTickOnePump(), 0.0);
        assertTrue(OilLineRecipeFacts.sustainableFePerTickOnePump()
            < OilLineRecipeFacts.GENERATOR_FE_PER_TICK);
        assertEquals((double) OilLineRecipeFacts.energyPerDistillation()
            / (OilLineRecipeFacts.pumpBatchesPerDistillation() * OilLineRecipeFacts.PUMP_TICKS),
            OilLineRecipeFacts.sustainableFePerTickOnePump(), 0.0);
    }

    @Test
    void continuousPeakOutputRequiresRoundingThePumpCountUp() {
        assertEquals(3, OilLineRecipeFacts.pumpsForContinuousGenerator());
        double dieselPerPumpTick = (double) OilLineRecipeFacts.PUMP_OIL_MB
            * OilLineRecipeFacts.DISTILL_DIESEL_MB
            / OilLineRecipeFacts.PUMP_TICKS / OilLineRecipeFacts.DISTILL_OIL_MB;
        double generatorDemand = (double) OilLineRecipeFacts.GENERATOR_DIESEL_MB
            / OilLineRecipeFacts.GENERATOR_TICKS;
        assertTrue(2 * dieselPerPumpTick < generatorDemand);
        assertTrue(3 * dieselPerPumpTick >= generatorDemand);
        assertTrue((double) OilLineRecipeFacts.DISTILL_DIESEL_MB / OilLineRecipeFacts.DISTILL_TICKS
            >= generatorDemand);
    }

    @Test
    void boilerThresholdsRetainTheAuditedInclusiveAndExclusiveBounds() {
        assertEquals(100, OilLineRecipeFacts.BOILER_MIN_TEMP);
        assertEquals(180, OilLineRecipeFacts.BOILER_MIDDLE_BAND_MIN_TEMP);
        assertEquals(260, OilLineRecipeFacts.BOILER_HIGH_BAND_MIN_TEMP);
        assertEquals(300, OilLineRecipeFacts.BOILER_MAX_TEMP);
        assertEquals(301, OilLineRecipeFacts.BOILER_HIGH_BAND_MAX_EXCLUSIVE_TEMP);
        assertEquals(OilLineRecipeFacts.BOILER_MAX_TEMP + 1,
            OilLineRecipeFacts.BOILER_HIGH_BAND_MAX_EXCLUSIVE_TEMP);
    }
}
