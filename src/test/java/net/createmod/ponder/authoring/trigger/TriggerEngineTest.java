package net.createmod.ponder.authoring.trigger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.google.gson.JsonObject;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TriggerEngineTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    static class View implements TriggerEngine.WorldView {
        int dimension, reads, probes; boolean loaded = true; BlockPos player = new BlockPos(0, 64, 0);
        IBlockState state = Blocks.STONE.getDefaultState(); Set<Integer> seen = new HashSet<Integer>();
        @Override public int dimension() { return dimension; }
        @Override public BlockPos playerPosition() { return player; }
        @Override public boolean isLoaded(BlockPos pos) { probes++; return loaded; }
        @Override public IBlockState blockState(BlockPos pos) { reads++; seen.add(pos.getX()); return state; }
    }
    static TriggerEngine engine(TriggerDefinition... rules) {
        TriggerEngine engine = new TriggerEngine(); engine.replaceRules(java.util.Arrays.asList(rules)); return engine;
    }

    @Test void coordinateHintsAreDimensionBoundAndDoNotReadAnyWorldBlocks() {
        TriggerEngine engine = engine(TriggerDefinitionTest.parse(TriggerDefinitionTest.coordinate("coordinate")));
        View view = new View(); view.dimension = -1; assertNull(engine.poll(view, 0));
        view.dimension = 0; assertNotNull(engine.poll(view, 20));
        assertEquals(0, view.reads); assertEquals(0, view.probes);
        view.player = new BlockPos(1000, 64, 0); assertNull(engine.poll(view, 40)); assertNull(engine.getActiveHint());
    }

    @Test void unloadedFarAndWrongDimensionPatternsNeverReadBlockStates() {
        TriggerEngine engine = engine(TriggerDefinitionTest.parse(TriggerDefinitionTest.pattern("pattern", 0, 2)));
        View view = new View(); view.loaded = false; assertNull(engine.poll(view, 0)); assertEquals(0, view.reads);
        view.loaded = true; view.player = new BlockPos(1000, 64, 0); assertNull(engine.poll(view, 20));
        assertEquals(0, view.reads); int probes = view.probes;
        view.player = new BlockPos(0, 64, 0); view.dimension = 1; assertNull(engine.poll(view, 40));
        assertEquals(probes, view.probes); assertEquals(0, view.reads);
    }

    @Test void allLoadednessIsCheckedBeforeReadingAnyPartOfAPattern() {
        TriggerEngine engine = engine(TriggerDefinitionTest.parse(TriggerDefinitionTest.pattern("partial", 0, 3)));
        View view = new View() { @Override public boolean isLoaded(BlockPos pos) { return pos.getX() != 2; } };
        assertNull(engine.poll(view, 0)); assertEquals(0, view.reads);
    }

    @Test void changedStatesDeactivateTheHintWithoutRetainingAnOldMatch() {
        TriggerEngine engine = engine(TriggerDefinitionTest.parse(TriggerDefinitionTest.pattern("state", 0, 1)));
        View view = new View(); assertNotNull(engine.poll(view, 0));
        view.state = Blocks.GOLD_BLOCK.getDefaultState(); assertNull(engine.poll(view, 20)); assertNull(engine.getActiveHint());
    }

    @Test void pollingAndRepeatedHintsRespectTheConfiguredCooldown() {
        TriggerEngine engine = engine(TriggerDefinitionTest.parse(TriggerDefinitionTest.coordinate("cooldown")));
        View view = new View(); assertNotNull(engine.poll(view, 0));
        for (int tick = 1; tick < 20; tick++) assertNull(engine.poll(view, tick));
        assertEquals(1, engine.getEvaluations()); assertNull(engine.poll(view, 20));
        assertNotNull(engine.poll(view, 40)); assertEquals(2, engine.getHintCount());
    }

    @Test void firstTimeAndUntilReadAreExplicitSessionStateNotTaskCompletion() {
        JsonObject root = TriggerDefinitionTest.coordinate("read"); root.getAsJsonObject("legacyTrigger").addProperty("frequency", "until_read");
        TriggerEngine engine = engine(TriggerDefinitionTest.parse(root)); View view = new View();
        TriggerEngine.Hint hint = engine.poll(view, 0); assertNotNull(hint);
        assertFalse(engine.markOpened(new ResourceLocation("test", "wrong"))); assertEquals(0, engine.getOpenedCount());
        assertTrue(engine.markOpened(hint.getRuntimeSceneId())); assertEquals(1, engine.getOpenedCount());
        assertNull(engine.poll(view, 40)); assertNotNull(engine.getActiveHint());
        engine.clearSession(); assertNotNull(engine.poll(view, 60)); assertEquals(0, engine.getOpenedCount());
        root.getAsJsonObject("legacyTrigger").addProperty("frequency", "first_time");
        engine = engine(TriggerDefinitionTest.parse(root)); assertNotNull(engine.poll(view, 0)); assertNull(engine.poll(view, 40));
    }

    @Test void firstTimeHintRemainsActionableUntilAnExplicitSuccessfulOpenNotMerelyItsFirstDisplay() {
        JsonObject root = TriggerDefinitionTest.coordinate("first_action");
        root.getAsJsonObject("legacyTrigger").addProperty("frequency", "first_time");
        TriggerEngine engine = engine(TriggerDefinitionTest.parse(root)); View view = new View();
        TriggerEngine.Hint displayed = engine.poll(view, 0); assertNotNull(displayed);
        assertNotNull(engine.getActionableHint(), "Showing the initial hint must not consume its shortcut");
        assertNull(engine.poll(view, 40)); assertNotNull(engine.getActionableHint());
        assertTrue(engine.markOpened(displayed.getRuntimeSceneId()));
        assertNull(engine.getActionableHint(), "The ordinary P menu must regain control after the one-time open");
        assertNotNull(engine.getActiveHint(), "Raw diagnostic matching is independent of shortcut ownership");
    }

    @Test void untilReadReleasesTheShortcutAfterOpenWhileKeepingRawConditionDiagnostics() {
        JsonObject root = TriggerDefinitionTest.coordinate("until_action");
        root.getAsJsonObject("legacyTrigger").addProperty("frequency", "until_read");
        TriggerEngine engine = engine(TriggerDefinitionTest.parse(root)); View view = new View();
        assertNotNull(engine.poll(view, 0)); assertNotNull(engine.getActionableHint());
        assertNotNull(engine.poll(view, 40));
        assertTrue(engine.markOpened(engine.getActionableHint().getRuntimeSceneId()));
        assertNull(engine.getActionableHint()); assertNull(engine.poll(view, 80)); assertNotNull(engine.getActiveHint());
        engine.clearSession(); assertNotNull(engine.poll(view, 100)); assertNotNull(engine.getActionableHint());
    }

    @Test void alwaysModeContinuesToOfferExplicitRepeatedOpeningAfterBeingRead() {
        TriggerEngine engine = engine(TriggerDefinitionTest.parse(TriggerDefinitionTest.coordinate("always_action"))); View view = new View();
        TriggerEngine.Hint first = engine.poll(view, 0); assertNotNull(first);
        assertTrue(engine.markOpened(first.getRuntimeSceneId())); assertNotNull(engine.getActionableHint());
        assertNotNull(engine.poll(view, 40)); assertNotNull(engine.getActionableHint()); assertEquals(1, engine.getOpenedCount());
    }

    @Test void boundedFairCursorChecksTheTailEvenWhileTheFirstRuleStaysActive() {
        List<TriggerDefinition> rules = new ArrayList<TriggerDefinition>();
        for (int i = 0; i < 64; i++) rules.add(TriggerDefinitionTest.parse(TriggerDefinitionTest.pattern("fair_" + i, i, 1)));
        TriggerEngine engine = new TriggerEngine(); engine.replaceRules(rules); View view = new View(); view.player = new BlockPos(32, 64, 0);
        for (int i = 0; i < 12; i++) {
            engine.poll(view, i * 20L); assertTrue(engine.getLastRuleChecks() <= 8); assertTrue(engine.getLastBlockReads() <= 512);
        }
        assertTrue(view.seen.contains(63), "The final rule must not starve behind a matching active rule");
        assertEquals("ponder_authoring:test/fair_0/main", engine.getActiveHint().getRuntimeSceneId().toString());
    }

    @Test void maximumPatternsCannotExceedThePerPollReadBudget() {
        List<TriggerDefinition> rules = new ArrayList<TriggerDefinition>();
        for (int i = 0; i < 8; i++) rules.add(TriggerDefinitionTest.parse(TriggerDefinitionTest.pattern("budget_" + i, 0, 128)));
        TriggerEngine engine = new TriggerEngine(); engine.replaceRules(rules); View view = new View();
        for (int i = 0; i < 8; i++) {
            view.reads = 0; engine.poll(view, i * 20L); assertTrue(view.reads <= 512);
            assertEquals(view.reads, engine.getLastBlockReads()); assertTrue(engine.getLastRuleChecks() <= 8);
        }
    }

    @Test void ruleReplacementAndSessionClearReleaseOldActiveTargets() {
        TriggerEngine engine = engine(TriggerDefinitionTest.parse(TriggerDefinitionTest.coordinate("old"))); View view = new View();
        assertNotNull(engine.poll(view, 0)); engine.replaceRules(Collections.<TriggerDefinition>emptyList());
        assertNull(engine.getActiveHint()); assertNull(engine.poll(view, 20));
        engine.clearSession(); assertEquals(0, engine.getHintCount()); assertEquals(0, engine.getEvaluations());
    }
}
