package net.createmod.ponder.authoring.trigger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import com.google.gson.JsonObject;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TriggerActionableSelectionTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    private static TriggerDefinition coordinate(String id, String frequency) {
        JsonObject root = TriggerDefinitionTest.coordinate(id);
        root.getAsJsonObject("legacyTrigger").addProperty("frequency", frequency);
        return TriggerDefinitionTest.parse(root);
    }

    private static TriggerDefinition pattern(String id, int x, int size, String frequency) {
        JsonObject root = TriggerDefinitionTest.pattern(id, x, size);
        root.getAsJsonObject("legacyTrigger").addProperty("frequency", frequency);
        return TriggerDefinitionTest.parse(root);
    }

    private static TriggerEngine engine(TriggerDefinition... rules) {
        TriggerEngine engine = new TriggerEngine();
        engine.replaceRules(Arrays.asList(rules));
        return engine;
    }

    private static ResourceLocation scene(String id) {
        return new ResourceLocation("ponder_authoring", "test/" + id + "/main");
    }

    @Test void readUntilReadRuleReleasesAnOverlappingUnreadRuleWithoutLosingRawDiagnostics() {
        TriggerEngine engine = engine(coordinate("a", "until_read"), coordinate("b", "until_read"));
        TriggerEngineTest.View view = new TriggerEngineTest.View();
        assertEquals(scene("a"), engine.poll(view, 0).getRuntimeSceneId());
        assertTrue(engine.markOpened(scene("a")));
        assertNull(engine.getActionableHint());
        assertEquals(scene("b"), engine.poll(view, 40).getRuntimeSceneId());
        assertEquals(scene("b"), engine.getActionableHint().getRuntimeSceneId());
        assertEquals(scene("a"), engine.getActiveHint().getRuntimeSceneId(), "Raw match diagnostics may still describe A");
        assertTrue(engine.markOpened(scene("b")), "The actual actionable target, not raw A, receives the read acknowledgement");
        assertEquals(2, engine.getOpenedCount());
    }

    @Test void firstTimeDisplayKeepsItsShortcutUntilOpenedThenTheNextRuleCanAppear() {
        TriggerEngine engine = engine(coordinate("first_a", "first_time"), coordinate("first_b", "first_time"));
        TriggerEngineTest.View view = new TriggerEngineTest.View();
        assertEquals(scene("first_a"), engine.poll(view, 0).getRuntimeSceneId());
        assertNull(engine.poll(view, 40), "First-time suppresses repeated text, not the unread shortcut");
        assertEquals(scene("first_a"), engine.getActionableHint().getRuntimeSceneId());
        assertTrue(engine.markOpened(scene("first_a")));
        assertEquals(scene("first_b"), engine.poll(view, 80).getRuntimeSceneId());
        assertTrue(engine.markOpened(scene("first_b")));
        assertNull(engine.poll(view, 120));
        assertNull(engine.getActionableHint());
    }

    @Test void allConsumedOverlappingRulesStopOfferingHintsButKeepMatchingDiagnostics() {
        TriggerEngine engine = engine(coordinate("done_a", "first_time"), coordinate("done_b", "until_read"));
        TriggerEngineTest.View view = new TriggerEngineTest.View();
        assertNotNull(engine.poll(view, 0)); assertTrue(engine.markOpened(scene("done_a")));
        assertNotNull(engine.poll(view, 40)); assertTrue(engine.markOpened(scene("done_b")));
        for (int tick = 80; tick <= 400; tick += 20) {
            assertNull(engine.poll(view, tick)); assertNull(engine.getActionableHint());
            assertNotNull(engine.getActiveHint());
            assertTrue(engine.getLastRuleChecks() <= TriggerEngine.MAX_RULE_CHECKS);
            assertTrue(engine.getLastBlockReads() <= TriggerEngine.MAX_BLOCK_READS);
        }
        assertEquals(2, engine.getOpenedCount());
    }

    @Test void alwaysRuleStillAllowsExplicitRepeatedOpeningAndHonorsItsCooldown() {
        TriggerEngine engine = engine(coordinate("repeat", "always"), coordinate("other", "until_read"));
        TriggerEngineTest.View view = new TriggerEngineTest.View();
        assertEquals(scene("repeat"), engine.poll(view, 0).getRuntimeSceneId());
        assertTrue(engine.markOpened(scene("repeat")));
        assertEquals(scene("repeat"), engine.getActionableHint().getRuntimeSceneId());
        assertNull(engine.poll(view, 20));
        assertEquals(scene("repeat"), engine.poll(view, 40).getRuntimeSceneId());
        assertTrue(engine.markOpened(scene("repeat")));
        assertEquals(1, engine.getOpenedCount(), "Repeated explicit opening is allowed without counting a second first read");
    }

    @Test void leavingClearsBothTargetsAndReenteringSelectsOnlyTheStillUnreadRule() {
        TriggerEngine engine = engine(coordinate("left_a", "until_read"), coordinate("left_b", "until_read"));
        TriggerEngineTest.View view = new TriggerEngineTest.View();
        assertNotNull(engine.poll(view, 0)); assertTrue(engine.markOpened(scene("left_a")));
        assertEquals(scene("left_b"), engine.poll(view, 40).getRuntimeSceneId());
        view.player = new BlockPos(1000, 64, 0);
        assertNull(engine.poll(view, 80)); assertNull(engine.getActiveHint()); assertNull(engine.getActionableHint());
        view.player = new BlockPos(0, 64, 0);
        assertEquals(scene("left_b"), engine.poll(view, 120).getRuntimeSceneId());
        assertEquals(scene("left_b"), engine.getActionableHint().getRuntimeSceneId());
    }

    @Test void reloadPreservesKnownReadIdentitiesWhileSessionResetAllowsThemAgain() {
        TriggerEngine engine = engine(coordinate("reload_a", "until_read"), coordinate("reload_b", "until_read"));
        TriggerEngineTest.View view = new TriggerEngineTest.View();
        assertNotNull(engine.poll(view, 0)); assertTrue(engine.markOpened(scene("reload_a")));
        engine.replaceRules(Arrays.asList(coordinate("reload_a", "until_read"), coordinate("reload_b", "until_read")));
        assertNull(engine.getActiveHint()); assertNull(engine.getActionableHint());
        assertEquals(scene("reload_b"), engine.poll(view, 40).getRuntimeSceneId());
        engine.clearSession();
        assertEquals(scene("reload_a"), engine.poll(view, 60).getRuntimeSceneId());
        engine.replaceRules(Collections.<TriggerDefinition>emptyList());
        assertNull(engine.getActiveHint()); assertNull(engine.getActionableHint()); assertNull(engine.poll(view, 100));
    }

    @Test void unknownOrNonActionableAcknowledgementsCannotConsumeOtherRules() {
        TriggerEngine engine = engine(coordinate("known_a", "until_read"), coordinate("known_b", "until_read"));
        TriggerEngineTest.View view = new TriggerEngineTest.View();
        assertNotNull(engine.poll(view, 0));
        assertFalse(engine.markOpened(scene("absent")));
        assertFalse(engine.markOpened(scene("known_b")), "A scene cannot be marked read just because it matched a nonselected rule");
        assertEquals(0, engine.getOpenedCount());
        assertTrue(engine.markOpened(scene("known_a")));
        assertFalse(engine.markOpened(scene("known_a")), "A consumed nonrepeatable target no longer owns the shortcut");
        assertEquals(scene("known_b"), engine.poll(view, 40).getRuntimeSceneId());
    }

    @Test void rawAndActionableAliasesDoNotReadTheSamePatternTwiceInOnePoll() {
        TriggerEngine engine = engine(pattern("one_pattern", 0, 128, "always"));
        TriggerEngineTest.View view = new TriggerEngineTest.View();
        assertNotNull(engine.poll(view, 0));
        assertEquals(128, view.reads); assertEquals(1, engine.getLastRuleChecks());
        view.reads = 0;
        assertNull(engine.poll(view, 20));
        assertEquals(128, view.reads); assertEquals(1, engine.getLastRuleChecks());
        assertEquals(128, engine.getLastBlockReads());
    }

    @Test void aFull512ReadPollCannotMakeConsumedRawDiagnosticsStarveTheTail() {
        TriggerEngine engine = engine(
            pattern("full_a", 0, 128, "until_read"), pattern("middle_1", 1, 128, "until_read"),
            pattern("middle_2", 2, 128, "until_read"), pattern("middle_3", 3, 128, "until_read"),
            pattern("tail_b", 4, 128, "until_read"));
        TriggerEngineTest.View view = new TriggerEngineTest.View();
        assertEquals(scene("full_a"), engine.poll(view, 0).getRuntimeSceneId());
        assertEquals(512, view.reads); assertEquals(512, engine.getLastBlockReads());
        assertFalse(view.seen.contains(19), "The tail pattern extends to x19 and was not read in the exhausted first poll");
        assertTrue(engine.markOpened(scene("full_a")));
        view.reads = 0;
        assertEquals(scene("tail_b"), engine.poll(view, 40).getRuntimeSceneId(), "The saved fair cursor, not raw A, gets the next read budget");
        assertTrue(view.seen.contains(19));
        assertTrue(view.reads <= 512); assertTrue(engine.getLastRuleChecks() <= 8);
        assertEquals(scene("full_a"), engine.getActiveHint().getRuntimeSceneId());
        assertEquals(scene("tail_b"), engine.getActionableHint().getRuntimeSceneId());
    }

    @Test void anInvalidatedActionablePatternReleasesItsShortcutToTheNextValidMatch() {
        TriggerEngine engine = engine(pattern("raw_a", 0, 1, "until_read"),
            pattern("changing_b", 2, 1, "until_read"), pattern("valid_c", 4, 1, "until_read"));
        final boolean[] changed = {false};
        TriggerEngineTest.View view = new TriggerEngineTest.View() {
            @Override public net.minecraft.block.state.IBlockState blockState(BlockPos position) {
                net.minecraft.block.state.IBlockState original = super.blockState(position);
                return changed[0] && position.getX() == 2 ? Blocks.GOLD_BLOCK.getDefaultState() : original;
            }
        };
        assertEquals(scene("raw_a"), engine.poll(view, 0).getRuntimeSceneId());
        assertTrue(engine.markOpened(scene("raw_a")));
        assertEquals(scene("changing_b"), engine.poll(view, 40).getRuntimeSceneId());
        changed[0] = true;
        assertEquals(scene("valid_c"), engine.poll(view, 80).getRuntimeSceneId());
        assertEquals(scene("raw_a"), engine.getActiveHint().getRuntimeSceneId());
    }
}

