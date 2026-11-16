package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.JsonObject;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSceneBuilder;
import net.createmod.ponder.foundation.PonderSchematic;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.content.DebugScenes;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuthoringSceneForkerTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    private PonderScene source(PonderStoryBoard board) {
        PonderScene source = new PonderScene(new PonderStoryBoardEntry(board, "ponder_legacy", new ResourceLocation("ponder_legacy", "fork_source"),
            new ResourceLocation("minecraft", "compass")), new PonderLocalization());
        source.program(new PonderSceneBuilder(source), source.getSceneBuildingUtil()); return source;
    }

    @Test void playerAndTrackingScenesRetainEveryActorActionAndTickWithoutMutatingBuiltins() throws Exception {
        for (PonderStoryBoard board : new PonderStoryBoard[] {DebugScenes::birbScene, DebugScenes::trackingScene, DebugScenes::itemScene}) {
            PonderScene original = source(board); List<String> operations = new ArrayList<String>(original.getOperationLog());
            AuthoringDocument document = AuthoringSceneForker.fork(original, "local:copied", (id, asset) -> fail("Empty initial structure must not create an asset"));
            AuthoringCompiler compiler = new AuthoringCompiler();
            assertFalse(compiler.validate(document, null).hasErrors(), compiler.validate(document, null).getIssues().toString());
            PonderScene copy = compiler.compile(document, null).get(0);
            assertEquals(original.getTotalIdleTicks(), copy.getTotalIdleTicks());
            assertEquals(original.getKeyframeTicks(), copy.getKeyframeTicks());
            assertEquals(original.getActorEvents().size(), copy.getActorEvents().size());
            for (int i = 0; i < original.getActorEvents().size(); i++) {
                PonderScene.ActorEvent a = original.getActorEvents().get(i), b = copy.getActorEvents().get(i);
                assertEquals(a.getTick(), b.getTick()); assertEquals(a.getDuration(), b.getDuration()); assertEquals(a.getType(), b.getType());
                assertEquals(a.getActorKind(), b.getActorKind()); assertEquals(a.getLocation(), b.getLocation()); assertEquals(a.getOffset(), b.getOffset());
                assertEquals(a.getRotation(), b.getRotation()); assertEquals(a.getAngle(), b.getAngle());
                assertTrue(net.minecraft.item.ItemStack.areItemStacksEqual(a.getItemStack(), b.getItemStack()));
            }
            assertEquals(original.getOverlayEvents().size(), copy.getOverlayEvents().size());
            for (int i = 0; i < original.getOverlayEvents().size(); i++) {
                PonderScene.OverlayEvent a = original.getOverlayEvents().get(i), b = copy.getOverlayEvents().get(i);
                assertEquals(a.getTick(), b.getTick()); assertEquals(a.getText(), b.getText()); assertEquals(a.getPointAt(), b.getPointAt());
                assertEquals(a.getSourcePositions(), b.getSourcePositions()); assertEquals(a.getTargetPositions(), b.getTargetPositions());
                assertEquals(a.isOutlineChasing(), b.isOutlineChasing());
            }
            assertEquals(operations, original.getOperationLog()); assertNotEquals(original.getSceneId(), copy.getSceneId());
        }
    }

    @Test void explicitForkWritesANewBoundedAssetOnlyOnceAndKeepsOriginalReference() throws Exception {
        PonderScene original = source((scene, util) -> scene.idle(5));
        PonderSchematic asset = PonderSchematic.of(new BlockPos(1, 1, 1), Collections.singletonMap(BlockPos.ORIGIN, Blocks.STONE.getDefaultState()), Collections.emptyMap());
        original.setSchematic(asset);
        List<String> written = new ArrayList<String>();
        AuthoringDocument document = AuthoringSceneForker.fork(original, "local:forked", (id, value) -> { written.add(id); assertSame(asset, value); });
        assertEquals(Collections.singletonList("ponder_authoring:local/forked/fork_initial"), written);
        assertEquals(original.getSceneId().toString(), document.toJson().getAsJsonObject("source").get("originalSceneId").getAsString());
        PonderScene copy = new AuthoringCompiler().compile(document, id -> asset).get(0);
        assertEquals(5, copy.getTotalIdleTicks());
        assertEquals(0, copy.getWorldEvents().size(), "Fork must not automatically reveal a previously hidden schematic");
    }

    @Test void staticBoundsAndTexturesAreDeclarativeButUnknownGuiCallbacksStayExplicitlyUnpublishable() throws Exception {
        PonderScene original = source((scene, util) -> {
            scene.overlay().chaseBoundingBoxOutline(net.createmod.ponder.api.PonderPalette.RED, "aabb", new AxisAlignedBB(0, 0, 0, 1, 2, 3), 40);
            scene.getScene().createOverlayEvent(20).guiTexture(new ResourceLocation("minecraft", "textures/gui/container/furnace.png"), 4, 5, 6, 7, 256, 256, 90, 80, 3, 9, false);
            scene.getScene().createOverlayEvent(20).guiSnapshot(new ResourceLocation("fork_test", "unknown_callback"))
                .setGuiSnapshotProvider(tick -> { throw new AssertionError("Fork must not invoke an unknown provider"); });
        });
        AuthoringDocument document = AuthoringSceneForker.fork(original, "local:needs_adapter", (id, asset) -> fail());
        assertFalse(new AuthoringCompiler().validate(document, null).canPublish());
        String json = document.toJson().toString();
        assertTrue(json.contains("unsupported_builtin_event")); assertTrue(json.contains("native_overlay")); assertTrue(json.contains("furnace.png"));
        assertTrue(json.contains("textureU")); assertTrue(json.contains("offsetY")); assertTrue(json.contains("bounds"));
    }

    @Test void malformedNativeActorReferencesAndArbitraryKindsCannotCompile() {
        JsonObject move = AuthoringModelTest.step("actor_action"); move.addProperty("action", "MOVE"); move.addProperty("kind", "PLAYER"); move.addProperty("actorId", 1); move.add("offset", AuthoringModelTest.pos(0, 1, 0));
        assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(move), null).hasErrors());
        move.addProperty("action", "SPAWN"); move.addProperty("kind", "ZOMBIE"); move.add("pos", AuthoringModelTest.pos(0, 1, 0));
        assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(move), null).hasErrors());
        assertEquals(39, AuthoringValidation.capabilities().size()); assertTrue(AuthoringValidation.nativeCapabilities().containsKey("actor_action"));
    }

    @Test void authoredViewsKeepBoundsTimingAndSameTickOverlayResetOrder() throws Exception {
        PonderScene original = source((scene, util) -> {
            scene.encapsulateBounds(new BlockPos(6, 4, 5));
            scene.overlay().showText(80).text("before");
            scene.zoomScene(new Vec3d(1, 2, 3), 1.5F, 12, false);
            scene.overlay().showText(80).text("after");
            scene.rotateCameraY(30);
            scene.idle(7);
            scene.rotateCamera(Float.valueOf(15F), 45F, true);
            scene.idle(20);
        });
        AuthoringDocument fork = AuthoringSceneForker.fork(original, "local:camera_copy", (id, asset) -> fail());
        PonderScene copy = new AuthoringCompiler().compile(fork, null).get(0);
        assertEquals(original.getTotalIdleTicks(), copy.getTotalIdleTicks());
        assertEquals(original.getFramingBounds(), copy.getFramingBounds());
        assertEquals(original.getCameraViewEvents().size(), copy.getCameraViewEvents().size());
        assertEquals(original.getCameraViewEvents().get(0).getCenter(), copy.getCameraViewEvents().get(0).getCenter());
        for (int i = 0; i < original.getCameraEvents().size(); i++) {
            PonderScene.CameraEvent a = original.getCameraEvents().get(i), b = copy.getCameraEvents().get(i);
            assertEquals(a.getTick(), b.getTick()); assertEquals(a.getDuration(), b.getDuration());
            assertEquals(a.getYawDegrees(), b.getYawDegrees()); assertEquals(a.getPitchDegrees(), b.getPitchDegrees());
            assertEquals(a.affectsPitch(), b.affectsPitch()); assertEquals(a.isTargetBased(), b.isTargetBased());
        }
        assertTrue(copy.isOverlayCleared(copy.getOverlayEvents().get(0), 0));
        assertFalse(copy.isOverlayCleared(copy.getOverlayEvents().get(1), 0));
    }

    @Test void distributedParticleParametersSurviveAnEditableFork() throws Exception {
        JsonObject emitter = AuthoringStepTemplates.withType(null, "spawn_particles"); emitter.addProperty("distribution", "sphere");
        emitter.add("spread", AuthoringModelTest.pos(1, 2, 3));
        PonderScene original = new AuthoringCompiler().compile(AuthoringModelTest.document(emitter), null).get(0);
        AuthoringDocument fork = AuthoringSceneForker.fork(original, "local:particle_copy", (id, asset) -> fail());
        PonderScene copy = new AuthoringCompiler().compile(fork, null).get(0);
        assertEquals("sphere", copy.getParticleEvents().get(0).getDistribution());
        assertEquals(new Vec3d(1, 2, 3), copy.getParticleEvents().get(0).getSpread());
        assertEquals(original.getParticleEvents().get(0).getCycles(), copy.getParticleEvents().get(0).getCycles());
    }

    @Test void multipleViewsWithoutInterveningOverlaysAndNullablePitchSurviveForking() throws Exception {
        PonderScene original = source((scene, util) -> {
            scene.overlay().showText(100).text("earlier");
            scene.zoomScene(new Vec3d(1, 1, 1), 1.25F, 18, false);
            scene.rotateCamera(Float.valueOf(45F), 20F, false);
            scene.idle(7);
            scene.zoomScene(null, 1F, 10, false);
            scene.rotateCamera((Float) null, 30F, true);
            scene.idle(5);
            scene.rotateCamera(Float.valueOf(0F), 0F, true);
            scene.overlay().showText(50).text("later");
            scene.idle(40);
        });
        PonderScene copy = new AuthoringCompiler().compile(AuthoringSceneForker.fork(original, "local:multi_view", (id, asset) -> fail()), null).get(0);
        assertEquals(original.getTotalIdleTicks(), copy.getTotalIdleTicks());
        assertEquals(2, copy.getCameraViewEvents().size());
        for (int i = 0; i < 2; i++) {
            PonderScene.CameraViewEvent a = original.getCameraViewEvents().get(i), b = copy.getCameraViewEvents().get(i);
            assertEquals(a.getTick(), b.getTick()); assertEquals(a.getOverlayCutoff(), b.getOverlayCutoff());
            assertEquals(a.getDuration(), b.getDuration()); assertEquals(a.getCenter(), b.getCenter());
        }
        assertFalse(copy.getCameraEvents().get(1).affectsPitch());
        assertTrue(copy.getCameraEvents().get(2).affectsPitch());
    }
}
