package net.createmod.ponder.authoring.practice;

import static org.junit.jupiter.api.Assertions.*;

import net.createmod.ponder.authoring.practice.PracticeDefinition.Action;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Builder;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Operation;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PracticeDataTweenTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void fourSamplesStartAtOneQuarterAndFinishOnTheFourthSample() {
        PracticeSession session = session(Action.dataTween(0, "cookTime", 0, 100, 4));
        assertValues(session, "cookTime", 25, 50, 75, 100, 100);
    }

    @Test void delayedTweenDoesNotWriteBeforeItsFirstSample() {
        PracticeSession session = new PracticeSession(chest().data("cookTime", 7)
            .action(Action.dataTween(2, "cookTime", 0, 100, 4)).build(), PracticeMode.WATCH);
        assertValues(session, "cookTime", 7, 7, 25, 50, 75, 100, 100);
    }

    @Test void zeroAndOneDurationBothUseOneImmediateSample() {
        for (int duration : new int[] {0, 1}) {
            Action action = Action.dataTween(0, "cookTime", 0, 80, duration);
            assertEquals(1, action.duration);
            assertEquals(0, action.getEndTick());
            assertValues(session(action), "cookTime", 80, 80, 80);
        }
    }

    @Test void integerRoundingMatchesMathRoundForAscendingAndDescendingValues() {
        assertValues(session(Action.dataTween(0, "cookTime", 0, 5, 4)), "cookTime", 1, 3, 4, 5);
        assertValues(session(Action.dataTween(0, "cookTime", 5, 0, 4)), "cookTime", 4, 3, 1, 0);
    }

    @Test void newerShortTweenOverridesThenTheOlderLongTweenResumes() {
        PracticeSession session = session(Action.dataTween(0, "cookTime", 0, 100, 10),
            Action.dataTween(2, "cookTime", 200, 220, 2));
        assertValues(session, "cookTime", 10, 20, 210, 220, 50, 60);
        session.seek(9); assertEquals(100, session.getData("cookTime"));
    }

    @Test void aOneSampleOverrideDoesNotCancelAnOlderActiveTween() {
        PracticeSession session = session(Action.dataTween(0, "cookTime", 0, 80, 8),
            Action.dataTween(2, "cookTime", 700, 900, 1));
        assertValues(session, "cookTime", 10, 20, 900, 40);
    }

    @Test void newerLongTweenDoesNotReviveAnOlderExpiredTween() {
        PracticeSession session = session(Action.dataTween(0, "cookTime", 0, 20, 2),
            Action.dataTween(1, "cookTime", 100, 160, 6));
        assertValues(session, "cookTime", 10, 110, 120, 130, 140, 150, 160, 160);
    }

    @Test void sameTickDeclarationOrderDecidesTheVisibleTween() {
        PracticeSession session = session(Action.dataTween(0, "cookTime", 0, 40, 4),
            Action.dataTween(0, "cookTime", 100, 120, 2));
        assertValues(session, "cookTime", 110, 120, 30, 40);
        session = session(Action.dataTween(0, "cookTime", 100, 120, 2),
            Action.dataTween(0, "cookTime", 0, 40, 4));
        assertValues(session, "cookTime", 10, 20, 30, 40);
    }

    @Test void instantSetDataInterleavesInDeclarationOrderThenAnActiveTweenResumes() {
        PracticeSession session = session(Action.dataTween(0, "cookTime", 0, 80, 8),
            instant(0, "cookTime", 600), instant(2, "cookTime", 700));
        assertValues(session, "cookTime", 600, 20, 700, 40);
        session = session(instant(0, "cookTime", 600), Action.dataTween(0, "cookTime", 0, 80, 8));
        assertValues(session, "cookTime", 10, 20);
    }

    @Test void separateKeysRunInParallelWithoutSharingPrecedence() {
        PracticeSession session = session(Action.dataTween(0, "cookTime", 0, 40, 4),
            Action.dataTween(0, "fuelTime", 100, 0, 2));
        assertEquals(10, session.getData("cookTime")); assertEquals(50, session.getData("fuelTime"));
        session.advance();
        assertEquals(20, session.getData("cookTime")); assertEquals(0, session.getData("fuelTime"));
        session.advance();
        assertEquals(30, session.getData("cookTime")); assertEquals(0, session.getData("fuelTime"));
    }

    @Test void seekBackwardAndResetRebuildBothPrecedenceAndValues() {
        PracticeSession session = session(Action.dataTween(0, "cookTime", 0, 100, 10),
            Action.dataTween(2, "cookTime", 200, 220, 2), instant(3, "fuelTime", 900));
        session.seek(5);
        assertEquals(60, session.getData("cookTime")); assertEquals(900, session.getData("fuelTime"));
        session.seek(2);
        assertEquals(210, session.getData("cookTime")); assertEquals(0, session.getData("fuelTime"));
        session.seek(5);
        assertEquals(60, session.getData("cookTime")); assertEquals(900, session.getData("fuelTime"));
        session.reset();
        assertEquals(10, session.getData("cookTime")); assertEquals(0, session.getData("fuelTime"));
    }

    @Test void watchAndTryRunScriptsWhilePracticeOnlyKeepsVisibilityActions() {
        PracticeDefinition definition = chest().data("cookTime", 7)
            .action(Action.dataTween(0, "cookTime", 0, 40, 4))
            .action(new Action(1, Operation.HIDE, -1, ItemStack.EMPTY, "", 0))
            .action(new Action(2, Operation.SHOW, -1, ItemStack.EMPTY, "", 0)).build();
        for (PracticeMode mode : new PracticeMode[] {PracticeMode.WATCH, PracticeMode.TRY}) {
            PracticeSession session = new PracticeSession(definition, mode);
            session.seek(2); assertEquals(30, session.getData("cookTime")); assertTrue(session.isVisible());
        }
        PracticeSession session = new PracticeSession(definition, PracticeMode.PRACTICE);
        session.seek(1); assertEquals(7, session.getData("cookTime")); assertFalse(session.isVisible());
        session.seek(2); assertEquals(7, session.getData("cookTime")); assertTrue(session.isVisible());
        session.setMode(PracticeMode.WATCH); assertEquals(10, session.getData("cookTime"));
        session.setMode(PracticeMode.PRACTICE); session.seek(3); assertEquals(7, session.getData("cookTime"));
    }

    @Test void virtualFurnacePhysicsRunsBeforeEachScriptSample() {
        PracticeDefinition definition = PracticeDefinition.builder("minecraft:furnace").duration(6)
            .action(Action.dataTween(0, "burnTime", 0, 8, 4)).build();
        PracticeSession session = new PracticeSession(definition, PracticeMode.WATCH);
        assertValues(session, "burnTime", 2, 4, 6, 8, 7, 6);
        session.seek(2); assertEquals(6, session.getData("burnTime"));
        session.seek(4); assertEquals(7, session.getData("burnTime"));
    }

    @Test void oldPublicActionConstructorKeepsInstantAndUnboundedApiDataValues() {
        Action action = new Action(0, Operation.SET_DATA, -1, ItemStack.EMPTY, "cookTime", Integer.MAX_VALUE);
        assertEquals(0, action.duration); assertEquals(0, action.getEndTick());
        assertEquals(Integer.MAX_VALUE, session(action).getData("cookTime"));
        assertThrows(IllegalArgumentException.class,
            () -> new Action(0, Operation.DATA_TWEEN, -1, ItemStack.EMPTY, "cookTime", 4));
    }

    @Test void tweenBoundsAndTheExclusiveLifetimeStayWithin24000Ticks() {
        assertEquals(24000, PracticeDefinition.builder("minecraft:chest").duration(0)
            .action(Action.dataTween(0, "cookTime", 0, 100000, 24000)).build().getDuration());
        assertEquals(24000, PracticeDefinition.builder("minecraft:chest").duration(0)
            .action(Action.dataTween(23999, "cookTime", 0, 1, 1)).build().getDuration());
        assertThrows(IllegalArgumentException.class, () -> Action.dataTween(24000, "cookTime", 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Action.dataTween(23999, "cookTime", 0, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> Action.dataTween(0, "cookTime", 0, 1, 24001));
        assertThrows(IllegalArgumentException.class, () -> Action.dataTween(0, "cookTime", 0, 1, -1));
    }

    @Test void invalidTweenKeysAndValuesAreRejectedWithoutChangingLegacySetData() {
        assertThrows(IllegalArgumentException.class, () -> Action.dataTween(0, "unknown", 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Action.dataTween(0, "cookTime", -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Action.dataTween(0, "cookTime", 0, 100001, 1));
        assertThrows(IllegalArgumentException.class, () -> Action.dataTween(-1, "cookTime", 0, 1, 1));
        assertEquals(-1, session(instant(0, "cookTime", -1)).getData("cookTime"));
    }

    @Test void actionBudgetCountsDeclarationsRatherThanExpandedSamples() {
        Builder builder = PracticeDefinition.builder("minecraft:chest").duration(0);
        for (int i = 0; i < 4096; i++) builder.action(Action.dataTween(0, "cookTime", 0, i, 24000));
        PracticeDefinition definition = builder.build();
        assertEquals(4096, definition.getActions().size()); assertEquals(24000, definition.getDuration());
        assertThrows(IllegalArgumentException.class, () -> builder.action(Action.dataTween(0, "cookTime", 0, 1, 1)));
    }

    @Test void closingDropsTweenStateAndDoesNotPermitAResetOrFurtherWrites() throws Exception {
        PracticeSession session = session(Action.dataTween(0, "cookTime", 0, 100, 10));
        java.lang.reflect.Field tweens = PracticeSession.class.getDeclaredField("dataTweens");
        tweens.setAccessible(true);
        assertFalse(((java.util.Map<?, ?>) tweens.get(session)).isEmpty());
        session.advance(); session.close(); session.advance();
        assertTrue(session.isClosed()); assertTrue(session.getData().isEmpty());
        assertTrue(((java.util.Map<?, ?>) tweens.get(session)).isEmpty());
        assertThrows(IllegalStateException.class, session::reset);
    }

    private static Builder chest() { return PracticeDefinition.builder("minecraft:chest").duration(12); }
    private static PracticeSession session(Action... actions) {
        Builder builder = chest();
        for (Action action : actions) builder.action(action);
        return new PracticeSession(builder.build(), PracticeMode.WATCH);
    }
    private static Action instant(int tick, String key, int value) {
        return new Action(tick, Operation.SET_DATA, -1, ItemStack.EMPTY, key, value);
    }
    private static void assertValues(PracticeSession session, String key, int... expected) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], session.getData(key), "sample " + i);
            if (i + 1 < expected.length) session.advance();
        }
    }
}
