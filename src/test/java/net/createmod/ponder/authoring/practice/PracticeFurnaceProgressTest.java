package net.createmod.ponder.authoring.practice;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Action;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Operation;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PracticeFurnaceProgressTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void missingAndZeroDurationUseVanillaWithoutRewritingCapturedData() {
        for (boolean explicit : new boolean[] {false, true}) {
            PracticeDefinition.Builder builder = furnace();
            if (explicit) builder.data("cookTotal", 0);
            PracticeSession session = new PracticeSession(builder.build(), PracticeMode.WATCH);
            assertEquals(200, session.getFurnaceCookTotal());
            assertEquals(explicit, session.getData().containsKey("cookTotal"));
            session.seek(199);
            assertTrue(session.getSlot(2).isEmpty());
            assertEquals(27, session.getFurnaceProgressPixels(28));
            session.seek(200);
            assertEquals(Items.IRON_INGOT, session.getSlot(2).getItem());
            assertEquals(0, session.getFurnaceProgressPixels(28));
            assertEquals(0, session.getData("cookTotal"));
        }
    }

    @Test void positiveDeclaredDurationsDriveTheSameProgressAndCompletion() {
        for (int total : new int[] {100, 400}) {
            PracticeSession session = new PracticeSession(furnace().data("cookTotal", total).build(), PracticeMode.WATCH);
            assertEquals(total, session.getFurnaceCookTotal());
            session.seek(total / 2);
            assertEquals(14, session.getFurnaceProgressPixels(28));
            session.seek(total - 1);
            assertTrue(session.getSlot(2).isEmpty());
            assertEquals(27, session.getFurnaceProgressPixels(28));
            session.seek(total);
            assertEquals(1, session.getSlot(2).getCount());
            assertEquals(1, session.getSlot(0).getCount());
        }
    }

    @Test void timelineDurationChangesAndBackwardSeekRebuildIdentically() {
        PracticeDefinition definition = furnace().data("cookTotal", 200)
            .action(new Action(50, Operation.SET_DATA, -1, ItemStack.EMPTY, "cookTotal", 400))
            .action(new Action(200, Operation.SET_DATA, -1, ItemStack.EMPTY, "cookTotal", 100)).build();
        PracticeSession session = new PracticeSession(definition, PracticeMode.WATCH);
        session.seek(49);
        assertEquals(200, session.getFurnaceCookTotal());
        session.seek(50);
        assertEquals(400, session.getFurnaceCookTotal());
        assertEquals(3, session.getFurnaceProgressPixels(28));
        session.seek(200);
        assertTrue(session.getSlot(2).isEmpty());
        assertEquals(100, session.getFurnaceCookTotal());
        assertEquals(28, session.getFurnaceProgressPixels(28));
        session.seek(201);
        assertEquals(1, session.getSlot(2).getCount());
        int burn = session.getData("burnTime");
        session.seek(0);
        assertEquals(200, session.getFurnaceCookTotal());
        session.seek(201);
        assertEquals(burn, session.getData("burnTime"));
        assertEquals(1, session.getSlot(2).getCount());
        assertTrue(session.getCursor().isEmpty());
    }

    @Test void progressClampsUntrustedApiValuesAndDoesNotOverflow() {
        PracticeSession session = new PracticeSession(furnace().data("cookTotal", 1)
            .data("cookTime", Integer.MAX_VALUE).build(), PracticeMode.WATCH);
        assertEquals(Integer.MAX_VALUE, session.getFurnaceProgressPixels(Integer.MAX_VALUE));
        assertEquals(28, session.getFurnaceProgressPixels(28));
        assertEquals(0, session.getFurnaceProgressPixels(-1));
        session = new PracticeSession(furnace().data("cookTotal", -1).data("cookTime", -1).build(), PracticeMode.WATCH);
        assertEquals(200, session.getFurnaceCookTotal());
        assertEquals(0, session.getFurnaceProgressPixels(28));
        assertEquals(-1, session.getData("cookTotal"));
    }

    @Test void anExtremeCapturedCounterCannotOverflowTheVirtualCookingTick() {
        PracticeSession session = new PracticeSession(furnace().data("cookTotal", Integer.MAX_VALUE)
            .data("cookTime", Integer.MAX_VALUE).data("burnTime", 2).build(), PracticeMode.WATCH);
        session.advance();
        assertEquals(1, session.getSlot(2).getCount());
        assertEquals(0, session.getData("cookTime"));
    }

    @Test void jsonStillRejectsNegativeDurationsAndPreservesPositiveDeclaredValues() {
        assertThrows(IllegalArgumentException.class, () -> PracticeJson.fromStep(new JsonParser().parse(
            "{\"uiId\":\"minecraft:furnace\",\"interface\":{\"data\":[{\"key\":\"cookTotal\",\"value\":-1}]}}").getAsJsonObject()));
        PracticeDefinition definition = PracticeJson.fromStep(new JsonParser().parse(
            "{\"uiId\":\"minecraft:furnace\",\"interface\":{\"data\":[{\"key\":\"cookTotal\",\"value\":100}]}}").getAsJsonObject());
        assertEquals(100, definition.getData().get("cookTotal"));
    }

    private static PracticeDefinition.Builder furnace() {
        return PracticeDefinition.builder("minecraft:furnace").slot(0, new ItemStack(Blocks.IRON_ORE, 2))
            .slot(1, new ItemStack(Items.COAL)).duration(1000);
    }
}
