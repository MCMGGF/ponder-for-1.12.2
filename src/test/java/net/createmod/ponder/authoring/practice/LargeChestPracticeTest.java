package net.createmod.ponder.authoring.practice;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.createmod.ponder.authoring.AuthoringEdits;
import net.createmod.ponder.authoring.capture.CaptureDraft;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Menu;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LargeChestPracticeTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    @Test void largeChestHasNinetyIndependentSlotsAndBoundedIndexing() {
        PracticeDefinition definition = PracticeDefinition.builder("minecraft:generic_9x6").slot(53, new ItemStack(Items.DIAMOND, 7)).build();
        PracticeSession session = new PracticeSession(definition, PracticeMode.TRY);
        assertEquals(Menu.CHEST_LARGE, definition.getMenu()); assertEquals(90, session.getSlotCount());
        assertEquals(7, session.getSlot(53).getCount()); assertThrows(IllegalArgumentException.class, () -> session.getSlot(90));
        assertThrows(IllegalArgumentException.class, () -> PracticeDefinition.builder("minecraft:generic_9x6").slot(90, ItemStack.EMPTY).build());
        ItemStack copy = session.getSlot(53); copy.setCount(1); assertEquals(7, session.getSlot(53).getCount());
    }
    @Test void chestShiftMoveMergesBeforeEmptySlotsAndUsesVanillaReversePlayerOrder() {
        for (String id : new String[] {"minecraft:chest", "minecraft:generic_9x6"}) {
            Menu menu = Menu.fromId(id);
            PracticeDefinition definition = PracticeDefinition.builder(id).slot(0, new ItemStack(Items.DIAMOND, 8))
                .slot(menu.slots + 35, new ItemStack(Items.DIAMOND, 60)).build();
            PracticeSession session = new PracticeSession(definition, PracticeMode.TRY);
            assertTrue(session.click(0, false, true)); assertTrue(session.getSlot(0).isEmpty());
            assertEquals(64, session.getSlot(menu.slots + 35).getCount()); assertEquals(4, session.getSlot(menu.slots + 34).getCount());
            assertTrue(session.getSlot(menu.slots).isEmpty());
            assertTrue(session.click(menu.slots + 34, false, true)); assertEquals(4, session.getSlot(0).getCount());
        }
    }
    @Test void allLargeChestHitRegionsFitAStableScaledCanonicalSurfaceAt320By240() {
        Menu menu = Menu.CHEST_LARGE; assertEquals(206, PracticeLayout.height(menu)); assertEquals(152, PracticeLayout.height(Menu.CHEST));
        PracticeLayout.Viewport fit = PracticeLayout.fit(320, 240, menu);
        assertTrue(fit.top + PracticeLayout.height(menu) * fit.scale <= 180.001);
        for (int slot = 0; slot < 90; slot++) {
            int x = PracticeLayout.x(menu, slot), y = PracticeLayout.y(menu, slot);
            assertTrue(y + 18 <= PracticeLayout.height(menu)); assertEquals(slot, PracticeLayout.hit(menu, x + 8, y + 8));
            for (int other = 0; other < slot; other++) assertFalse(x == PracticeLayout.x(menu, other) && y == PracticeLayout.y(menu, other));
        }
        assertTrue(PracticeLayout.y(menu, 54) >= PracticeLayout.y(menu, 53) + 18);
    }
    @Test void normalizedClicksUseTheLargeMenuHeightAndReachTheLastPlayerSlot() {
        JsonObject show = new JsonObject(); show.addProperty("uiId", "minecraft:generic_9x6");
        PracticeDefinition.Builder builder = PracticeJson.start(null, show); builder.slot(89, new ItemStack(Items.DIAMOND));
        JsonObject click = new JsonObject(); click.addProperty("type", "click_interface");
        JsonArray point = new JsonArray(); point.add((PracticeLayout.x(Menu.CHEST_LARGE, 89) + 8) / 180D);
        point.add((PracticeLayout.y(Menu.CHEST_LARGE, 89) + 8) / 206D); click.add("pos", point);
        PracticeJson.append(builder, null, click, 0);
        assertEquals(89, builder.build().getActions().get(0).slot);
        PracticeSession watch = new PracticeSession(builder.build(), PracticeMode.WATCH); assertEquals(Items.DIAMOND, watch.getCursor().getItem());
        watch.seek(0); assertEquals(Items.DIAMOND, watch.getCursor().getItem());
    }
    @Test void capturedMetadataSurvivesDocumentAwareCompilationInsteadOfBecomingVariantZero() {
        List<ItemStack> values = new ArrayList<>(Collections.nCopies(54, ItemStack.EMPTY)); values.set(53, new ItemStack(Blocks.WOOL, 4, 14));
        CaptureDraft capture = new CaptureDraft(Menu.CHEST_LARGE, values, Collections.emptyMap());
        PracticeDefinition result = PracticeJson.start(AuthoringEdits.empty("local:capture_metadata"), capture.toStep()).build();
        assertEquals(14, result.getInitialSlots().get(53).getMetadata()); assertEquals(4, result.getInitialSlots().get(53).getCount());
        assertTrue(result.getInitialSlots().get(54).isEmpty());
    }
    @Test void chestShiftMoveIsDeterministicWithFullTargetsPartialRoomAndOverflow() {
        for (String id : new String[] {"minecraft:chest", "minecraft:generic_9x6"}) {
            Menu menu = Menu.fromId(id); PracticeDefinition.Builder builder = PracticeDefinition.builder(id).slot(0, new ItemStack(Items.DIAMOND, 8));
            for (int slot = menu.slots; slot < menu.slots + 36; slot++) builder.slot(slot, new ItemStack(Items.EMERALD, 64));
            PracticeSession full = new PracticeSession(builder.build(), PracticeMode.TRY);
            assertFalse(full.click(0, false, true)); assertEquals(8, full.getSlot(0).getCount()); assertTrue(full.getCursor().isEmpty());
            builder.slot(menu.slots + 35, new ItemStack(Items.DIAMOND, 62));
            PracticeSession partial = new PracticeSession(builder.build(), PracticeMode.TRY);
            assertTrue(partial.click(0, false, true)); assertEquals(6, partial.getSlot(0).getCount()); assertEquals(64, partial.getSlot(menu.slots + 35).getCount());
            partial.reset(); assertEquals(8, partial.getSlot(0).getCount()); assertEquals(62, partial.getSlot(menu.slots + 35).getCount());
            assertTrue(partial.click(0, false, true)); assertEquals(6, partial.getSlot(0).getCount());
        }
    }
    @Test void scriptedShiftMovesResetAndReplayInBothDirectionsWithoutTouchingTheCursor() {
        for (String id : new String[] {"minecraft:chest", "minecraft:generic_9x6"}) {
            Menu menu = Menu.fromId(id);
            PracticeDefinition definition = PracticeDefinition.builder(id).slot(0, new ItemStack(Items.DIAMOND, 9))
                .action(new PracticeDefinition.Action(5, PracticeDefinition.Operation.TRANSFER, 0, ItemStack.EMPTY, "", 0))
                .action(new PracticeDefinition.Action(10, PracticeDefinition.Operation.TRANSFER, menu.slots + 35, ItemStack.EMPTY, "", 0)).build();
            PracticeSession session = new PracticeSession(definition, PracticeMode.WATCH);
            for (int tick : new int[] {5, 10, 0, 10, 5}) {
                session.seek(tick); assertTrue(session.getCursor().isEmpty());
                assertEquals(tick == 5 ? 0 : 9, session.getSlot(0).getCount());
                assertEquals(tick == 5 ? 9 : 0, session.getSlot(menu.slots + 35).getCount());
            }
        }
    }
}
