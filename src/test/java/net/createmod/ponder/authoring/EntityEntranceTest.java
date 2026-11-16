package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PreviewActorTimeline;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityEntranceTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void supportedEntitiesEnterFromHalfABlockAwayWithoutWalkingOrAdvancingTheScript() {
        for (String entity : new String[] {"player", "parrot", "minecart"}) {
            JsonObject step = spawn(entity, "down", 20);
            if ("player".equals(entity)) step.remove("entranceDuration");
            PonderScene scene = compile(step);
            assertEquals(0, scene.getTotalIdleTicks());
            assertEquals(new Vec3d(2, 3.5, 4), actor(scene, 0).position);
            Vec3d midway = actor(scene, 10).position;
            assertEquals(new Vec3d(2, 3.125, 4), midway);
            assertEquals(new Vec3d(2, 3, 4), actor(scene, 20).position);
            assertEquals(midway, actor(scene, 10).position);
            assertEquals(new Vec3d(2, 3.28125, 4), actor(scene, 5).position);
            PonderScene.ActorEvent movement = scene.getActorEvents().stream()
                .filter(e -> e.getType() == PonderScene.ActorEventType.MOVE).findFirst().get();
            assertTrue(movement.isEntranceMotion());
            assertEquals(20, movement.getDuration());
            assertFalse(movement.hasWalkAnimation());
            assertEquals(actor(scene, 0).poseName, actor(scene, 20).poseName);
        }
    }

    @Test void eachDirectionStartsOppositeItsTravelDirectionAndEndsAtTheDeclaredTarget() {
        String[] names = {"down", "up", "north", "south", "east", "west"};
        Vec3d[] offsets = {new Vec3d(0, .5, 0), new Vec3d(0, -.5, 0), new Vec3d(0, 0, .5),
            new Vec3d(0, 0, -.5), new Vec3d(-.5, 0, 0), new Vec3d(.5, 0, 0)};
        for (int index = 0; index < names.length; index++) {
            PonderScene scene = compile(spawn("player", names[index], 20));
            assertEquals(new Vec3d(2, 3, 4).add(offsets[index]), actor(scene, 0).position, names[index]);
            assertEquals(new Vec3d(2, 3, 4), actor(scene, 20).position, names[index]);
        }
    }

    @Test void simultaneousUsesDirectionAndAliasesResolveToTheSameDeterministicRoute() {
        JsonObject step = spawn("player", "simultaneous", 8); step.addProperty("direction", "east");
        PonderScene scene = compile(step);
        assertEquals(new Vec3d(1.5, 3, 4), actor(scene, 0).position);
        assertEquals(new Vec3d(1.875, 3, 4), actor(scene, 4).position);
        assertEquals(new Vec3d(2, 3, 4), actor(scene, 8).position);
        PonderScene alias = compile(spawn("player", "top_to_bottom", 20));
        assertEquals(new Vec3d(2, 3.5, 4), actor(alias, 0).position);
    }

    @Test void zeroDurationOrNoEntranceNeverAddsAHiddenOffsetOrMovement() {
        for (JsonObject step : new JsonObject[] {spawn("player", "down", 0), spawn("parrot", "none", 20)}) {
            PonderScene scene = compile(step);
            assertEquals(new Vec3d(2, 3, 4), actor(scene, 0).position);
            assertTrue(scene.getActorEvents().stream().noneMatch(PonderScene.ActorEvent::isEntranceMotion));
        }
    }

    @Test void entrancePreservesPoseNamesAndDeclaredOrientation() {
        JsonObject step = spawn("player", "up", 20); step.addProperty("yaw", 40); step.addProperty("pitch", 15);
        step.addProperty("nbt", "{CustomName:\"Actor\"}");
        PonderScene scene = compile(step);
        for (int tick : new int[] {0, 5, 20, 5}) {
            assertEquals("Actor", actor(scene, tick).displayName);
            assertEquals(40F, actor(scene, tick).yaw);
            assertEquals(new Vec3d(15, 40, 0), actor(scene, tick).rotation);
        }
    }

    @Test void ambiguousMotionPositionAndWalkingAreRejectedBeforeAnySceneIsPublished() {
        for (String conflict : new String[] {"motion", "Motion", "Pos", "walkAnimation"}) {
            JsonObject step = spawn("player", "down", 20);
            if ("motion".equals(conflict)) step.add("motion", AuthoringModelTest.pos(1, 0, 0));
            else if ("walkAnimation".equals(conflict)) step.addProperty(conflict, true);
            else step.addProperty("nbt", "{" + conflict + ":[1d,0d,0d]}");
            assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(step), null).hasErrors(), conflict);
            assertThrows(AuthoringCompiler.CompilationException.class, () -> compile(step));
        }
    }

    @Test void durationAndCoordinateBudgetsIncludeTheEntranceEndpoint() {
        for (int duration : new int[] {-1, 72001}) {
            JsonObject step = spawn("player", "down", duration);
            assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(step), null).hasErrors());
        }
        JsonObject outside = spawn("player", "west", 20); outside.add("pos", AuthoringModelTest.pos(30000000, 0, 0));
        assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(outside), null).hasErrors());
        JsonObject inside = spawn("player", "east", 20); inside.add("pos", AuthoringModelTest.pos(30000000, 0, 0));
        assertEquals(29999999.5, actor(compile(inside), 0).position.x);
    }

    @Test void upstreamItemCreationDoesNotPretendToSupportEntityEntrance() {
        JsonObject item = spawn("player", "down", 20); item.addProperty("type", "create_item_entity");
        item.remove("entity"); item.addProperty("item", "minecraft:stone");
        assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(item), null).hasErrors());
        item.addProperty("entranceAnimation", "none");
        assertEquals(new Vec3d(2, 3, 4), actor(compile(item), 0).position);
    }

    @Test void nativeEntranceMoveHasExplicitValidatedMetadataAndDoesNotAlterNormalMoveDefaults() {
        JsonObject spawn = AuthoringModelTest.step("actor_action"); spawn.addProperty("actorId", 1);
        spawn.addProperty("kind", "PLAYER"); spawn.addProperty("action", "SPAWN");
        spawn.add("pos", AuthoringModelTest.pos(0, 0, 0));
        JsonObject move = AuthoringModelTest.step("actor_action"); move.addProperty("actorId", 1);
        move.addProperty("kind", "PLAYER"); move.addProperty("action", "MOVE");
        move.add("offset", AuthoringModelTest.pos(1, 0, 0)); move.addProperty("duration", 20);
        move.addProperty("entranceMotion", true);
        PonderScene scene = new AuthoringCompiler().compile(AuthoringModelTest.document(spawn, move), null).get(0);
        assertEquals(0D, actor(scene, 0).position.x);
        assertEquals(0.75D, actor(scene, 10).position.x);
        move.addProperty("walkAnimation", true);
        assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(spawn, move), null).hasErrors());
        move.addProperty("walkAnimation", false); move.addProperty("duration", 0);
        assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(spawn, move), null).hasErrors());
        move.remove("entranceMotion"); move.addProperty("duration", 20);
        PonderScene ordinary = new AuthoringCompiler().compile(AuthoringModelTest.document(spawn, move), null).get(0);
        assertFalse(ordinary.getActorEvents().get(1).isEntranceMotion());
        assertEquals(0.05D, actor(ordinary, 0).position.x, 0.000001D);
    }

    @Test void positionPatchesCannotCutAcrossTheLastStillActiveEntranceTick() {
        JsonObject spawn = spawn("player", "down", 20); spawn.addProperty("linkId", "actor");
        JsonObject idle = AuthoringModelTest.step("idle"); idle.addProperty("duration", 19);
        JsonObject patch = AuthoringModelTest.step("modify_entities_nbt"); patch.addProperty("linkId", "actor");
        patch.addProperty("nbt", "{Pos:[2d,3d,4d]}");
        assertThrows(AuthoringCompiler.CompilationException.class,
            () -> new AuthoringCompiler().compile(AuthoringModelTest.document(spawn, idle, patch), null));
        idle.addProperty("duration", 20);
        assertDoesNotThrow(() -> new AuthoringCompiler().compile(AuthoringModelTest.document(spawn, idle, patch), null));
    }

    private static JsonObject spawn(String entity, String animation, int duration) {
        JsonObject step = AuthoringModelTest.step("create_entity"); step.addProperty("entity", "minecraft:" + entity);
        step.add("pos", AuthoringModelTest.pos(2, 3, 4)); step.addProperty("entranceAnimation", animation);
        step.addProperty("entranceDuration", duration); return step;
    }

    private static PonderScene compile(JsonObject step) {
        return new AuthoringCompiler().compile(AuthoringModelTest.document(step), null).get(0);
    }

    private static PreviewActorTimeline.State actor(PonderScene scene, float tick) {
        return PreviewActorTimeline.evaluate(scene.getActorEvents(), tick).get(0);
    }
}
