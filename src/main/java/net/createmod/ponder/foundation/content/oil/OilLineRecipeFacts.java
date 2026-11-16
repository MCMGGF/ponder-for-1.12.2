package net.createmod.ponder.foundation.content.oil;

/** Read-only teaching facts from the audited pack recipes, not a live recipe resolver. */
public final class OilLineRecipeFacts {

    public static final String FLUID_WATER = "water";
    public static final String FLUID_STEAM = "ic2steam";
    public static final String FLUID_OIL = "oil";
    public static final String FLUID_DIESEL = "diesel";
    public static final String FLUID_LUBRICANT = "lubricant";

    public static final String MACHINE_BOILER = "aw_steam_bay";
    public static final String MACHINE_PUMP = "aw_oil_pump";
    public static final String MACHINE_DISTILLATION = "aw_distillation_tower";
    public static final String MACHINE_GENERATOR = "aw_diesel_generator";
    public static final String ITEM_BITUMEN = "immersivepetroleum:material";
    public static final int ITEM_BITUMEN_META = 0;

    public static final int PUMP_TICKS = 400;
    public static final int PUMP_OIL_MB = 250;
    public static final int PUMP_STEAM = 40;
    public static final int DISTILL_TICKS = 600;
    public static final int DISTILL_OIL_MB = 1000;
    public static final int DISTILL_STEAM = 80;
    public static final int DISTILL_DIESEL_MB = 700;
    public static final int DISTILL_LUBRICANT_MB = 200;
    public static final int DISTILL_BITUMEN = 1;
    public static final int GENERATOR_TICKS = 20;
    public static final int GENERATOR_DIESEL_MB = 20;
    public static final int GENERATOR_FE_PER_TICK = 256;

    public static final int BOILER_MIN_TEMP = 100;
    public static final int BOILER_MIDDLE_BAND_MIN_TEMP = 180;
    public static final int BOILER_HIGH_BAND_MIN_TEMP = 260;
    public static final int BOILER_MAX_TEMP = 300;
    public static final int BOILER_HIGH_BAND_MAX_EXCLUSIVE_TEMP = 301;

    private OilLineRecipeFacts() {}

    public static int pumpBatchesPerDistillation() {
        return (DISTILL_OIL_MB + PUMP_OIL_MB - 1) / PUMP_OIL_MB;
    }

    /** Process debits only: excludes warm-up, surveying and automatic steam-reserve filling. */
    public static int steamPerDistillationIncludingOil() {
        return pumpBatchesPerDistillation() * PUMP_STEAM + DISTILL_STEAM;
    }

    public static int generatorTicksPerDistillation() {
        return DISTILL_DIESEL_MB / GENERATOR_DIESEL_MB * GENERATOR_TICKS;
    }

    public static long energyPerDistillation() {
        return (long) generatorTicksPerDistillation() * GENERATOR_FE_PER_TICK;
    }

    /** Ideal long-run average; a tank smooths the supply but cannot increase this throughput. */
    public static double sustainableFePerTickOnePump() {
        long numerator = (long) PUMP_OIL_MB * DISTILL_DIESEL_MB
            * GENERATOR_TICKS * GENERATOR_FE_PER_TICK;
        long denominator = (long) PUMP_TICKS * DISTILL_OIL_MB * GENERATOR_DIESEL_MB;
        return (double) numerator / denominator;
    }

    /** Assumes an adequate oil reservoir, steam supply, transport and unblocked byproduct outputs. */
    public static int pumpsForContinuousGenerator() {
        long required = (long) GENERATOR_DIESEL_MB * PUMP_TICKS * DISTILL_OIL_MB;
        long perPump = (long) PUMP_OIL_MB * DISTILL_DIESEL_MB * GENERATOR_TICKS;
        return (int) ((required + perPump - 1) / perPump);
    }
}
