package net.createmod.ponder.authoring.practice;

import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PracticeExamplesTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    @Test void chestAndHopperHaveMaterialsAndCompleteTheirVirtualFlow() {
        for(String menu:new String[]{"minecraft:chest","minecraft:generic_9x6","minecraft:hopper"}) {
            PracticeDefinition definition=PracticeExamples.create(menu);
            assertFalse(definition.getInitialSlots().isEmpty()); assertFalse(definition.getGoals().isEmpty());
            PracticeSession session=new PracticeSession(definition,PracticeMode.WATCH);
            try {
                session.seek(definition.getDuration()); assertTrue(session.isComplete(),menu);
                java.util.List<net.minecraft.item.ItemStack> after=new java.util.ArrayList<>();
                for(int slot=0;slot<session.getSlotCount();slot++) after.add(session.getSlot(slot));
                session.seek(0); session.seek(definition.getDuration());
                for(int slot=0;slot<after.size();slot++) assertTrue(net.minecraft.item.ItemStack.areItemStacksEqual(after.get(slot),session.getSlot(slot)));
            } finally { session.close(); }
        }
    }
    @Test void unknownMenuIsReadOnlyRatherThanGettingFakeVanillaOperations() {
        PracticeSession session=new PracticeSession(PracticeExamples.create("other:machine"),PracticeMode.TRY);
        try { assertTrue(session.isReadOnly()); assertFalse(session.isComplete()); } finally { session.close(); }
    }
}
