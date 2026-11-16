package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.util.Collections;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSchematic;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuthoringSectionContractTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    private JsonObject section() {
        JsonObject show = AuthoringModelTest.step("show_section_and_merge"); show.add("blockPos", AuthoringModelTest.pos(0, 0, 0));
        show.addProperty("linkId", "old"); return show;
    }
    private PonderSchematic asset() {
        return PonderSchematic.of(new BlockPos(1, 1, 1), Collections.singletonMap(BlockPos.ORIGIN, Blocks.STONE.getDefaultState()), Collections.emptyMap());
    }

    @Test void sectionDurationAndExplicitNoneAreNotSilentlyReplacedWithTheOldDefaultFade() {
        JsonObject show = section(); show.addProperty("duration", 7);
        PonderScene.WorldEvent animated = new AuthoringCompiler().compile(AuthoringModelTest.document(show), null).get(0).getWorldEvents().stream()
            .filter(event -> event.getType() == PonderScene.WorldEventType.SHOW_SECTION).findFirst().get();
        assertEquals(7, animated.getDuration()); assertEquals(EnumFacing.DOWN, animated.getDirection());
        show.addProperty("entranceAnimation", "none");
        PonderScene.WorldEvent immediate = new AuthoringCompiler().compile(AuthoringModelTest.document(show), null).get(0).getWorldEvents().stream()
            .filter(event -> event.getType() == PonderScene.WorldEventType.SHOW_SECTION).findFirst().get();
        assertNull(immediate.getDirection()); assertEquals(1, immediate.getDuration());
    }

    @Test void unsupportedOwnershipFieldsCannotBeAcceptedAndIgnored() {
        for (String field : new String[] {"entranceDuration", "entranceInterval", "smartDisplay", "entranceAnimation"}) {
            JsonObject show = section();
            if ("entranceAnimation".equals(field)) show.addProperty(field, "diagonal");
            else if ("smartDisplay".equals(field)) show.addProperty(field, "yes"); else show.addProperty(field, -1);
            assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(show), null).hasErrors(), field);
        }
    }

    @Test void sectionAndStructureDefaultDirectionsRemainDistinctAndExplicitDirectionWins() {
        for (String type : new String[] {"show_section_and_merge", "show_structure"}) {
            JsonObject step = section(); step.addProperty("type", type);
            PonderScene.WorldEvent event = new AuthoringCompiler().compile(AuthoringModelTest.document(step), null).get(0).getWorldEvents().stream()
                .filter(world -> world.getType() == PonderScene.WorldEventType.SHOW_SECTION).findFirst().get();
            assertEquals("show_structure".equals(type) ? EnumFacing.UP : EnumFacing.DOWN, event.getDirection());
            step.addProperty("direction", "east");
            event = new AuthoringCompiler().compile(AuthoringModelTest.document(step), null).get(0).getWorldEvents().stream()
                .filter(world -> world.getType() == PonderScene.WorldEventType.SHOW_SECTION).findFirst().get();
            assertEquals(EnumFacing.EAST, event.getDirection());
        }
    }

    @Test void anOldOwnerCannotCaptureReplacementGeometryThroughAFutureMove() {
        JsonObject extra = AuthoringModelTest.step("show_extra_structure"); extra.addProperty("structure", "local:one");
        extra.add("blockPos", AuthoringModelTest.pos(0, 0, 0)); extra.addProperty("linkId", "new");
        JsonObject later = AuthoringModelTest.step("move_section"); later.addProperty("linkId", "old"); later.add("offset", AuthoringModelTest.pos(3, 0, 0));
        PonderScene scene = new AuthoringCompiler().compile(AuthoringModelTest.document(section(), extra, later), id -> asset()).get(0);
        java.util.List<PonderScene.WorldEvent> shows = new java.util.ArrayList<>();
        for(PonderScene.WorldEvent event:scene.getWorldEvents()) if(event.getType()==PonderScene.WorldEventType.SHOW_SECTION) shows.add(event);
        assertEquals(2,shows.size());assertNotEquals(shows.get(0).getSectionOwnership().getSectionId(),shows.get(1).getSectionOwnership().getSectionId());
        PonderScene.WorldEvent move=scene.getWorldEvents().get(scene.getWorldEvents().size()-1);
        assertEquals(shows.get(0).getSectionOwnership().getSectionId(),move.getSectionOwnership().getSectionId(),"Old moves address an identity, not every later occupant of its original coordinates");
    }

    @Test void aVisibilityOnlyRevealDoesNotDeleteTheOriginalLinkIdentity() {
        JsonObject reveal = AuthoringModelTest.step("show_structure"); reveal.add("blockPos", AuthoringModelTest.pos(0, 0, 0));
        JsonObject later = AuthoringModelTest.step("move_section"); later.addProperty("linkId", "old"); later.add("offset", AuthoringModelTest.pos(3, 0, 0));
        PonderScene scene = new AuthoringCompiler().compile(AuthoringModelTest.document(section(), reveal, later), null).get(0);
        assertEquals(PonderScene.WorldEventType.MOVE_SECTION, scene.getWorldEvents().get(scene.getWorldEvents().size() - 1).getType());
    }
}
