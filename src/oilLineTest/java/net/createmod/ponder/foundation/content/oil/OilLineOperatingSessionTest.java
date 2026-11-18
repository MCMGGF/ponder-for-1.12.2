package net.createmod.ponder.foundation.content.oil;

import static net.createmod.ponder.foundation.content.oil.OilLineOperatingSession.Action.*;
import static net.createmod.ponder.foundation.content.oil.OilLineOperatingSession.Station.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.createmod.ponder.foundation.content.oil.OilLineOperatingSession.Action;
import net.createmod.ponder.foundation.content.oil.OilLineOperatingSession.Station;
import net.createmod.ponder.foundation.content.oil.OilLineOperatingSession.Vessel;
import org.junit.jupiter.api.Test;

class OilLineOperatingSessionTest {
    @Test
    void startsWithFiniteIndependentSuppliesAndNoAutomaticOperation() {
        OilLineOperatingSession s = new OilLineOperatingSession();
        assertEquals(8000, s.getWaterSupply());
        assertEquals(8, s.getCoalSupply());
        assertEquals(12000, s.getReservoir());
        assertEquals(20, s.getTemperature());
        for (Station station : Station.values()) assertFalse(s.isComponentsInstalled(station));
        s.advance(24000);
        assertEquals(0, s.getOilProduced());
        assertEquals(0, s.getSteamProduced());
        assertEquals(0, s.getGeneratedEnergy());
        assertFalse(s.isComplete());
        assertLedger(s);
    }

    @Test
    void rejectsWrongStationAndMissingComponentsWithoutSpendingResources() {
        OilLineOperatingSession s = new OilLineOperatingSession();
        s.act(PUMP, ADD_COAL);
        assertTrue(s.getFeedback().contains("不属于"));
        s.act(BOILER, ADD_COAL);
        s.act(BOILER, ADD_WATER);
        assertEquals(8, s.getCoalSupply());
        assertEquals(8000, s.getWaterSupply());
        assertTrue(s.getFeedback().contains("组件"));
        s.act(STORAGE, INSTALL_COMPONENTS);
        assertFalse(s.isComponentsInstalled(STORAGE));
    }

    @Test
    void warmupAndSteamBatchKeepTheirExplicitDurations() {
        OilLineOperatingSession s = boiler();
        s.advance(799);
        assertEquals(99, s.getTemperature());
        assertEquals(0, s.getSteamProduced());
        assertFalse(s.isGoalComplete(0));
        s.advance(1);
        assertEquals(100, s.getTemperature());
        assertTrue(s.isGoalComplete(0));
        s.advance(39);
        assertEquals(80, s.getSteamProduced());
        assertEquals(4990, s.getWater());
        assertLedger(s);
    }

    @Test
    void fuelContinuesBurningWithoutWaterOrWhenTheSteamOutputIsBlocked() {
        OilLineOperatingSession dry = new OilLineOperatingSession();
        dry.act(BOILER, INSTALL_COMPONENTS);
        dry.act(BOILER, ADD_COAL);
        dry.act(BOILER, ADD_COAL);
        dry.advance(1000);
        assertEquals(600, dry.getFuelTicks());
        assertEquals(0, dry.getSteamProduced());
        OilLineOperatingSession blocked = boiler();
        blocked.advance(6400);
        assertEquals(0, blocked.getFuelTicks());
        assertEquals(0, blocked.getCoal());
        assertTrue(blocked.getBoilerSteam() > 4800);
        assertTrue(blocked.getBoilerSteam() <= 5000);
        assertLedger(blocked);
    }

    @Test
    void surveyingIsRequiredAndRepeatedConfirmationNeverRestocksOil() {
        OilLineOperatingSession s = boiler();
        s.act(PUMP, INSTALL_COMPONENTS);
        s.act(BOILER, TOGGLE_STEAM_LINE);
        s.advance(2400);
        assertEquals(1280, s.getPumpSteam());
        assertEquals(0, s.getOilProduced());
        s.act(PUMP, SURVEY);
        s.advance(400);
        assertEquals(250, s.getOilProduced());
        int remaining = s.getReservoir();
        s.act(PUMP, SURVEY);
        assertEquals(remaining, s.getReservoir());
        assertLedger(s);
    }

    @Test
    void fourPumpBatchesDoNotInventABucketInTheInitiallyEmptyBuffer() {
        OilLineOperatingSession s = pump();
        s.advance(2440);
        assertEquals(1000, s.getOilProduced());
        assertEquals(1000, s.getPipeOil());
        assertEquals(0, s.getOilTank());
        assertTrue(s.isGoalComplete(1));
        assertFalse(s.isGoalComplete(2));
        s.act(TOWER, INSTALL_COMPONENTS);
        s.act(TOWER, ADD_OIL);
        assertEquals(0, s.getTowerOil());
        assertTrue(s.getFeedback().contains("到罐"));
        assertLedger(s);
    }

    @Test
    void mixedPipeStopsTransferWithoutDestroyingOilAndCanBeRepaired() {
        OilLineOperatingSession s = pump();
        s.act(STORAGE, TOGGLE_MIXED_PIPE);
        s.advance(22000);
        assertEquals(5000, s.getPumpOil());
        assertEquals(5000, s.getOilProduced());
        assertEquals(0, s.getOilTank());
        assertEquals(0, s.getPipeOil());
        assertTrue(s.getStationStatus(STORAGE).contains("串液"));
        s.act(STORAGE, TOGGLE_MIXED_PIPE);
        s.advance(1);
        assertEquals(4000, s.getPipeOil());
        assertEquals(1000, s.getOilTank());
        assertEquals(0, s.getPumpOil());
        assertTrue(s.isGoalComplete(2));
        assertLedger(s);
    }

    @Test
    void closedOilOutputEventuallyBlocksThePumpInsteadOfLosingProduct() {
        OilLineOperatingSession s = pump();
        s.act(PUMP, TOGGLE_OIL_OUTPUT);
        s.advance(22000);
        assertEquals(5000, s.getPumpOil());
        assertEquals(0, s.getPumpProgress());
        assertEquals(5000, s.getOilProduced());
        int remaining = s.getReservoir();
        s.advance(1000);
        assertEquals(remaining, s.getReservoir());
        s.act(PUMP, TOGGLE_OIL_OUTPUT);
        s.advance(1);
        assertEquals(1000, s.getOilTank());
        assertLedger(s);
    }

    @Test
    void fullFiniteReservoirEndsWithTwelveBucketsNotAnInfiniteSource() {
        OilLineOperatingSession s = oil();
        assertEquals(0, s.getReservoir());
        assertEquals(12000, s.getOilProduced());
        assertEquals(4000, s.getPipeOil());
        assertEquals(8000, s.getOilTank());
        s.act(PUMP, SURVEY);
        s.advance(24000);
        assertEquals(12000, s.getOilProduced());
        assertEquals(0, s.getReservoir());
        assertLedger(s);
    }

    @Test
    void manualOilFillingDoesNotNeedAnOpenFaceAndRespectsTheRepresentativeInputTank() {
        OilLineOperatingSession s = oil();
        s.act(TOWER, INSTALL_COMPONENTS);
        for (int i = 0; i < 5; i++) s.act(TOWER, ADD_OIL);
        assertFalse(s.isTowerSteamOpen());
        assertEquals(5000, s.getTowerOil());
        assertEquals(3000, s.getOilTank());
        s.act(TOWER, ADD_OIL);
        assertEquals(5000, s.getTowerOil());
        assertEquals(3000, s.getOilTank());
        assertTrue(s.getFeedback().contains("已满"));
        assertLedger(s);
    }

    @Test
    void fillingAllInputSlotsWithSteamCannotBeSilentlyUndoneByAnOilClick() {
        OilLineOperatingSession s = boiler();
        s.act(TOWER, INSTALL_COMPONENTS);
        s.act(BOILER, TOGGLE_STEAM_LINE);
        s.act(TOWER, TOGGLE_TOWER_STEAM);
        s.advance(6400);
        assertTrue(s.getTowerInputSteam() > 15000);
        int steam = s.getTowerInputSteam();
        s.act(TOWER, ADD_OIL);
        assertEquals(steam, s.getTowerInputSteam());
        assertEquals(0, s.getTowerOil());
        assertTrue(s.getFeedback().contains("蒸汽"));
        assertLedger(s);
    }

    @Test
    void closingSteamRetainsTheChargeAndCompletesTheSafeBatchGoal() {
        OilLineOperatingSession s = chargedTower();
        int steam = s.getTowerInternalSteam();
        int residual = s.getTowerInputSteam();
        s.act(TOWER, TOGGLE_TOWER_STEAM);
        assertFalse(s.isTowerSteamOpen());
        assertEquals(steam, s.getTowerInternalSteam());
        assertEquals(residual, s.getTowerInputSteam());
        s.advance(599);
        assertEquals(1, s.getTowerBatches());
        assertEquals(700, s.getTowerDiesel());
        assertEquals(200, s.getTowerLubricant());
        assertEquals(1, s.getTowerBitumen());
        assertTrue(s.isGoalComplete(3));
        assertLedger(s);
    }

    @Test
    void openSteamIsNotInventedAsAMachineFailureCondition() {
        OilLineOperatingSession s = chargedTower();
        s.advance(599);
        assertTrue(s.isTowerSteamOpen());
        assertEquals(1, s.getTowerBatches());
        assertFalse(s.isGoalComplete(3));
        assertEquals(700, s.getTowerDiesel());
        assertLedger(s);
    }

    @Test
    void ordinaryBucketsRejectTheFirstPartialOutputsButCellsCollectExactlyWhatExists() {
        OilLineOperatingSession s = firstBatch();
        s.act(STORAGE, COLLECT_DIESEL);
        s.act(STORAGE, COLLECT_LUBRICANT);
        assertEquals(700, s.getTowerDiesel());
        assertEquals(200, s.getTowerLubricant());
        assertEquals(0, s.getDieselTank());
        assertTrue(s.getFeedback().contains("整桶"));
        s.setVessel(Vessel.CELL);
        s.act(STORAGE, COLLECT_DIESEL);
        s.act(STORAGE, COLLECT_LUBRICANT);
        s.act(STORAGE, COLLECT_BITUMEN);
        assertEquals(700, s.getDieselTank());
        assertEquals(200, s.getLubricantTank());
        assertEquals(1, s.getBitumenBox());
        assertEquals(0, s.getTowerDiesel());
        assertTrue(s.isGoalComplete(4));
        assertLedger(s);
    }

    @Test
    void fiveBatchBucketBranchKeepsItsHalfBucketRemainderAndDoesNotMixBranches() {
        OilLineOperatingSession s = chargedTower();
        s.act(TOWER, TOGGLE_TOWER_STEAM);
        s.advance(2999);
        assertEquals(5, s.getTowerBatches());
        for (int i = 0; i < 3; i++) s.act(STORAGE, COLLECT_DIESEL);
        s.act(STORAGE, COLLECT_LUBRICANT);
        s.act(STORAGE, COLLECT_BITUMEN);
        assertEquals(3000, s.getDieselTank());
        assertEquals(500, s.getTowerDiesel());
        assertEquals(1000, s.getLubricantTank());
        assertEquals(5, s.getBitumenBox());
        s.act(STORAGE, COLLECT_DIESEL);
        assertEquals(500, s.getTowerDiesel());
        assertLedger(s);
    }

    @Test
    void blockedDistillationDoesNotSpendItsNextInputUntilOutputSpaceIsAvailable() {
        OilLineOperatingSession s = chargedTower();
        s.act(TOWER, TOGGLE_TOWER_STEAM);
        s.advance(2999);
        for (int i = 0; i < 3; i++) s.act(TOWER, ADD_OIL);
        s.advance(1800);
        assertEquals(7, s.getTowerBatches());
        assertEquals(4900, s.getTowerDiesel());
        assertEquals(1000, s.getTowerOil());
        long steam = s.getTowerSteamUsed();
        s.advance(24000);
        assertEquals(7, s.getTowerBatches());
        assertEquals(steam, s.getTowerSteamUsed());
        assertEquals(1000, s.getTowerOil());
        s.act(STORAGE, COLLECT_DIESEL);
        s.advance(600);
        assertEquals(8, s.getTowerBatches());
        assertEquals(0, s.getTowerOil());
        assertEquals(8 * 80, s.getTowerSteamUsed());
        assertLedger(s);
    }

    @Test
    void oneCollectedBatchCanCompleteTheWholeExerciseWithoutRealInventoryOrPackets() {
        OilLineOperatingSession s = firstBatch();
        collectWithCells(s);
        s.act(GENERATOR, INSTALL_COMPONENTS);
        s.act(GENERATOR, TOGGLE_DIESEL_FEED);
        s.act(GENERATOR, TOGGLE_ENERGY_OUTPUT);
        s.advance(700);
        assertEquals(179200, s.getGeneratedEnergy());
        assertEquals(700, s.getDieselConsumed());
        assertEquals(0, s.getDieselTank());
        assertTrue(s.getCapacitorEnergy() > 0);
        assertTrue(s.isComplete());
        assertLedger(s);
    }

    @Test
    void oneInputBucketMayBeReservedBeforeSteamIsClosedAndStillCompleteEveryGoal() {
        OilLineOperatingSession s = singleBucketTower();
        assertEquals(0, s.getTowerOil());
        assertEquals(1, s.getTowerProgress());
        s.act(TOWER, TOGGLE_TOWER_STEAM);
        s.advance(599);
        assertTrue(s.isGoalComplete(3));
        assertEquals(1, s.getTowerBatches());
        assertEquals(80, s.getTowerSteamUsed());
        collectWithCells(s);
        s.act(GENERATOR, INSTALL_COMPONENTS);
        s.act(GENERATOR, TOGGLE_DIESEL_FEED);
        s.act(GENERATOR, TOGGLE_ENERGY_OUTPUT);
        s.advance(700);
        assertEquals(700, s.getDieselConsumed());
        assertEquals(179200, s.getGeneratedEnergy());
        assertTrue(s.isComplete());
        assertLedger(s);
    }

    @Test
    void singleBucketSafeClosureIsAlsoDeterministicAcrossSplitAdvances() {
        OilLineOperatingSession a = singleBucketTower(), b = singleBucketTower();
        a.act(TOWER, TOGGLE_TOWER_STEAM);
        b.act(TOWER, TOGGLE_TOWER_STEAM);
        a.advance(599);
        b.advance(300);
        b.advance(299);
        assertEquals(snapshot(a), snapshot(b));
        assertTrue(a.isGoalComplete(3));
        assertEquals(80, a.getTowerSteamUsed());
        assertLedger(a);
        assertLedger(b);
    }

    @Test
    void generatorDoesNotConsumeDieselWithoutItsComponents() {
        OilLineOperatingSession s = firstBatch();
        collectWithCells(s);
        s.act(GENERATOR, TOGGLE_DIESEL_FEED);
        s.advance(1000);
        assertEquals(700, s.getDieselTank());
        assertEquals(0, s.getDieselConsumed());
        assertEquals(0, s.getGeneratedEnergy());
        assertLedger(s);
    }

    @Test
    void disconnectedEnergyOutputFillsInternalBufferAndPausesThePaidFuelCycle() {
        OilLineOperatingSession s = firstBatch();
        collectWithCells(s);
        s.act(GENERATOR, INSTALL_COMPONENTS);
        s.act(GENERATOR, TOGGLE_DIESEL_FEED);
        s.advance(1000);
        assertEquals(99840, s.getGeneratorEnergy());
        assertEquals(0, s.getCapacitorEnergy());
        int remaining = s.getGeneratorTicks();
        long paid = s.getDieselConsumed();
        s.advance(10000);
        assertEquals(99840, s.getGeneratorEnergy());
        assertEquals(remaining, s.getGeneratorTicks());
        assertEquals(paid, s.getDieselConsumed());
        s.act(GENERATOR, TOGGLE_ENERGY_OUTPUT);
        s.advance(1);
        assertEquals(remaining - 1, s.getGeneratorTicks());
        assertEquals(paid, s.getDieselConsumed());
        assertEquals(256, s.getCapacitorEnergy());
        assertLedger(s);
    }

    @Test
    void capacitorAndInternalBufferCanFillThenRecoverWhenALoadIsConnected() {
        OilLineOperatingSession s = eightBatchesCollected();
        s.act(GENERATOR, INSTALL_COMPONENTS);
        s.act(GENERATOR, TOGGLE_DIESEL_FEED);
        s.act(GENERATOR, TOGGLE_ENERGY_OUTPUT);
        s.advance(10000);
        assertEquals(1000000, s.getCapacitorEnergy());
        assertTrue(s.getGeneratorEnergy() > 99744);
        long paid = s.getDieselConsumed();
        long generated = s.getGeneratedEnergy();
        assertTrue(s.getGeneratorTicks() > 0);
        s.advance(1000);
        assertEquals(generated, s.getGeneratedEnergy());
        assertEquals(paid, s.getDieselConsumed());
        s.act(GENERATOR, TOGGLE_LOAD);
        s.advance(2);
        assertTrue(s.getGeneratedEnergy() > generated);
        assertEquals(paid, s.getDieselConsumed());
        assertEquals(256, s.getConsumedEnergy());
        assertLedger(s);
    }

    @Test
    void equalActionsAndTicksAreIndependentOfFrameChunking() {
        OilLineOperatingSession a = pump(), b = pump();
        a.advance(22000);
        for (int i = 0; i < 1100; i++) b.advance(20);
        assertEquals(snapshot(a), snapshot(b));
        a.act(TOWER, INSTALL_COMPONENTS); b.act(TOWER, INSTALL_COMPONENTS);
        for (int i = 0; i < 5; i++) { a.act(TOWER, ADD_OIL); b.act(TOWER, ADD_OIL); }
        a.act(TOWER, TOGGLE_TOWER_STEAM); b.act(TOWER, TOGGLE_TOWER_STEAM);
        a.advance(1); b.advance(1);
        a.act(TOWER, TOGGLE_TOWER_STEAM); b.act(TOWER, TOGGLE_TOWER_STEAM);
        a.advance(2999);
        for (int i = 0; i < 2999; i++) b.advance(1);
        assertEquals(snapshot(a), snapshot(b));
        assertLedger(a); assertLedger(b);
    }

    @Test
    void noAdvanceMeansPauseAndInvalidBudgetsCannotMutateTheSession() {
        OilLineOperatingSession s = pump();
        s.advance(2000);
        List<String> before = snapshot(s);
        s.advance(0);
        assertThrows(IllegalArgumentException.class, () -> s.advance(-1));
        assertThrows(IllegalArgumentException.class, () -> s.advance(24001));
        assertEquals(before, snapshot(s));
    }

    @Test
    void objectivesLatchWhenResourcesAreLaterSpentOrTheFeedIsStopped() {
        OilLineOperatingSession s = firstBatch();
        collectWithCells(s);
        assertTrue(s.isGoalComplete(0));
        assertTrue(s.isGoalComplete(1));
        assertTrue(s.isGoalComplete(2));
        assertTrue(s.isGoalComplete(3));
        assertTrue(s.isGoalComplete(4));
        s.act(GENERATOR, INSTALL_COMPONENTS);
        s.act(GENERATOR, TOGGLE_DIESEL_FEED);
        s.act(GENERATOR, TOGGLE_ENERGY_OUTPUT);
        s.advance(700);
        s.act(GENERATOR, TOGGLE_DIESEL_FEED);
        s.act(GENERATOR, TOGGLE_LOAD);
        s.advance(24000);
        assertEquals(0, s.getDieselTank());
        assertEquals(0, s.getCapacitorEnergy());
        assertTrue(s.isComplete());
        assertLedger(s);
    }

    @Test
    void resetStartsFreshButCloseClearsAndCannotBeRevived() {
        OilLineOperatingSession s = firstBatch();
        s.reset();
        assertEquals(snapshot(new OilLineOperatingSession()), snapshot(s));
        s.act(BOILER, INSTALL_COMPONENTS);
        s.close();
        assertTrue(s.isClosed());
        assertEquals(0, s.getWaterSupply());
        assertEquals(0, s.getCoalSupply());
        assertEquals(0, s.getReservoir());
        assertEquals(0, s.getTemperature());
        s.reset();
        s.act(BOILER, INSTALL_COMPONENTS);
        s.act(PUMP, SURVEY);
        s.setVessel(Vessel.CELL);
        s.advance(24000);
        assertTrue(s.isClosed());
        assertEquals(0, s.getTick());
        assertFalse(s.isComponentsInstalled(BOILER));
        assertFalse(s.isSurveyed());
        assertEquals(Vessel.BUCKET, s.getVessel());
        assertFalse(s.isComplete());
        for (int i = 0; i < 6; i++) assertFalse(s.isGoalComplete(i));
    }

    @Test
    void callerCannotMutateGoalsOrStatsAndSessionsShareNoMutableStaticState() {
        OilLineOperatingSession a = pump(), b = new OilLineOperatingSession();
        a.advance(22000);
        assertEquals(12000, b.getReservoir());
        assertEquals(0, b.getOilProduced());
        assertFalse(b.isComponentsInstalled(BOILER));
        assertThrows(UnsupportedOperationException.class, () -> a.getGoals().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.getStationStats(PUMP).clear());
        assertThrows(IndexOutOfBoundsException.class, () -> a.isGoalComplete(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> a.isGoalComplete(6));
        for (java.lang.reflect.Field field : OilLineOperatingSession.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) assertTrue(field.getType().isPrimitive(), field.getName());
        }
    }

    @Test
    void randomizedLocalActionsNeverViolateAnyMaterialOrEnergyLedger() {
        OilLineOperatingSession s = new OilLineOperatingSession();
        Random random = new Random(20261101L);
        for (int i = 0; i < 600; i++) {
            if (random.nextInt(20) == 0) s.reset();
            else if (random.nextBoolean()) s.advance(random.nextInt(1000));
            else s.act(Station.values()[random.nextInt(Station.values().length)],
                Action.values()[random.nextInt(Action.values().length)]);
            if (random.nextInt(5) == 0) s.setVessel(random.nextBoolean() ? Vessel.BUCKET : Vessel.CELL);
            assertLedger(s);
        }
    }

    private static OilLineOperatingSession boiler() {
        OilLineOperatingSession s = new OilLineOperatingSession();
        s.act(BOILER, INSTALL_COMPONENTS);
        for (int i = 0; i < 5; i++) s.act(BOILER, ADD_WATER);
        for (int i = 0; i < 8; i++) s.act(BOILER, ADD_COAL);
        return s;
    }

    private static OilLineOperatingSession pump() {
        OilLineOperatingSession s = boiler();
        s.act(PUMP, INSTALL_COMPONENTS);
        s.act(PUMP, SURVEY);
        s.act(PUMP, TOGGLE_OIL_OUTPUT);
        s.act(BOILER, TOGGLE_STEAM_LINE);
        return s;
    }

    private static OilLineOperatingSession oil() {
        OilLineOperatingSession s = pump(); s.advance(22000); return s;
    }

    private static OilLineOperatingSession chargedTower() {
        OilLineOperatingSession s = oil();
        s.act(TOWER, INSTALL_COMPONENTS);
        for (int i = 0; i < 5; i++) s.act(TOWER, ADD_OIL);
        s.act(TOWER, TOGGLE_TOWER_STEAM);
        s.advance(1);
        return s;
    }

    private static OilLineOperatingSession firstBatch() {
        OilLineOperatingSession s = chargedTower();
        s.act(TOWER, TOGGLE_TOWER_STEAM);
        s.advance(599);
        return s;
    }

    private static OilLineOperatingSession singleBucketTower() {
        OilLineOperatingSession s = oil();
        s.act(TOWER, INSTALL_COMPONENTS);
        s.act(TOWER, ADD_OIL);
        s.act(TOWER, TOGGLE_TOWER_STEAM);
        s.advance(1);
        return s;
    }

    private static void collectWithCells(OilLineOperatingSession s) {
        s.setVessel(Vessel.CELL);
        while (s.getTowerDiesel() > 0) s.act(STORAGE, COLLECT_DIESEL);
        while (s.getTowerLubricant() > 0) s.act(STORAGE, COLLECT_LUBRICANT);
        s.act(STORAGE, COLLECT_BITUMEN);
    }

    private static OilLineOperatingSession eightBatchesCollected() {
        OilLineOperatingSession s = chargedTower();
        s.act(TOWER, TOGGLE_TOWER_STEAM);
        s.advance(2999);
        collectWithCells(s);
        for (int i = 0; i < 3; i++) s.act(TOWER, ADD_OIL);
        s.advance(1800);
        collectWithCells(s);
        return s;
    }

    private static List<String> snapshot(OilLineOperatingSession s) {
        List<String> values = new ArrayList<>();
        values.add(String.valueOf(s.getTick()));
        values.add(s.getVessel().name());
        values.add(s.getFeedback());
        values.add(s.getReservoir() + ":" + s.getPumpInFlightOil() + ":" + s.getSteamProduced());
        values.add(s.getPumpSteamUsed() + ":" + s.getTowerSteamUsed() + ":" + s.getDieselConsumed());
        values.add(s.getGeneratedEnergy() + ":" + s.getConsumedEnergy() + ":" + s.getExportedEnergy());
        for (Station station : Station.values()) {
            values.add(String.valueOf(s.isComponentsInstalled(station)));
            values.addAll(s.getStationStats(station));
        }
        for (int i = 0; i < 6; i++) values.add(String.valueOf(s.isGoalComplete(i)));
        return values;
    }

    private static void assertLedger(OilLineOperatingSession s) {
        assertEquals(8000, s.getWaterSupply() + s.getWater() + s.getSteamProduced() / 8);
        assertEquals(s.getSteamProduced(), s.getBoilerSteam() + s.getPumpSteam() + s.getTowerInternalSteam()
            + s.getTowerInputSteam() + s.getPumpSteamUsed() + s.getTowerSteamUsed());
        assertEquals(12000, s.getReservoir() + s.getPumpInFlightOil() + s.getPumpOil() + s.getPipeOil()
            + s.getOilTank() + s.getTowerOil() + (s.getTowerProgress() > 0 ? 1000 : 0) + s.getTowerBatches() * 1000);
        assertEquals(s.getTowerBatches() * 700, s.getTowerDiesel() + s.getDieselTank() + s.getDieselConsumed());
        assertEquals(s.getTowerBatches() * 200, s.getTowerLubricant() + s.getLubricantTank());
        assertEquals(s.getTowerBatches(), s.getTowerBitumen() + s.getBitumenBox());
        assertEquals(s.getGeneratedEnergy(), s.getGeneratorEnergy() + s.getCapacitorEnergy() + s.getConsumedEnergy());
        assertEquals(s.getDieselConsumed(), s.getGeneratedEnergy() / 256 + s.getGeneratorTicks());
        assertTrue(s.getWater() >= 0 && s.getWater() <= 5000);
        assertTrue(s.getBoilerSteam() >= 0 && s.getBoilerSteam() <= 5000);
        assertTrue(s.getPumpSteam() >= 0 && s.getPumpSteam() <= 1280);
        assertTrue(s.getTowerInternalSteam() >= 0 && s.getTowerInternalSteam() <= 2560);
        assertTrue(s.getTowerInputSteam() >= 0 && s.getTowerInputSteam() <= 20000);
        assertTrue(s.getPumpOil() >= 0 && s.getPumpOil() <= 5000);
        assertTrue(s.getPipeOil() >= 0 && s.getPipeOil() <= 4000);
        assertTrue(s.getOilTank() >= 0 && s.getOilTank() <= 16000);
        assertTrue(s.getTowerOil() >= 0 && s.getTowerOil() <= 5000);
        assertTrue(s.getTowerDiesel() >= 0 && s.getTowerDiesel() <= 5000);
        assertTrue(s.getTowerLubricant() >= 0 && s.getTowerLubricant() <= 5000);
        assertTrue(s.getTowerBitumen() >= 0 && s.getTowerBitumen() <= 64);
        assertTrue(s.getDieselTank() >= 0 && s.getDieselTank() <= 16000);
        assertTrue(s.getLubricantTank() >= 0 && s.getLubricantTank() <= 16000);
        assertTrue(s.getGeneratorEnergy() >= 0 && s.getGeneratorEnergy() <= 100000);
        assertTrue(s.getCapacitorEnergy() >= 0 && s.getCapacitorEnergy() <= 1000000);
    }
}
