package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CameraStepHandlersTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void defaultCompilerAcceptsTheThreeCoreViewConversionsAndPreservesSceneIds() {
        JsonObject zoom = AuthoringModelTest.step("zoom_scene");
        zoom.add("point", AuthoringModelTest.pos(2, 3, 4));
        zoom.addProperty("scale", 2);
        JsonObject bounds = AuthoringModelTest.step("encapsulate_bounds");
        bounds.add("bounds", AuthoringModelTest.pos(8, 6, 4));
        AuthoringDocument doc = AuthoringModelTest.document(zoom, bounds, AuthoringModelTest.step("next_scene"));
        AuthoringCompiler compiler = new AuthoringCompiler();
        assertFalse(compiler.validate(doc, null).hasErrors());
        java.util.List<PonderScene> scenes = compiler.compile(doc, null);
        assertEquals(1, scenes.size(), "the upstream marker never creates or automatically selects another scene");
        assertEquals(doc.getRuntimeSceneId(0), scenes.get(0).getSceneId());
        assertEquals(20, scenes.get(0).getTotalIdleTicks());
        assertEquals(1, scenes.get(0).getCameraViewEvents().size());
        assertEquals(new BlockPos(8, 6, 4), scenes.get(0).getFramingBounds());
    }

    @Test void defaultScaleAndCenterAreRepresentedWithoutFutureStaticMutation() {
        PonderScene scene = new AuthoringCompiler().compile(
            AuthoringModelTest.document(AuthoringModelTest.step("zoom_scene")), null).get(0);
        assertNull(scene.getCameraViewEvents().get(0).getCenter());
        assertEquals(1F, scene.getCameraViewEvents().get(0).getMultiplier());
        assertEquals(1F, scene.getScaleFactor());
    }

    @Test void nonblockingForkViewPreservesLaterInstructionsInsteadOfAddingAnImplicitWait() {
        JsonObject zoom = AuthoringModelTest.step("zoom_scene");
        zoom.addProperty("duration", 20);
        zoom.addProperty("waitForCompletion", false);
        JsonObject idle = AuthoringModelTest.step("idle");
        idle.addProperty("duration", 5);
        JsonObject later = AuthoringModelTest.step("zoom_scene");
        later.addProperty("scale", 3);
        later.addProperty("waitForCompletion", false);
        PonderScene scene = new AuthoringCompiler().compile(AuthoringModelTest.document(zoom, idle, later), null).get(0);
        assertEquals(5, scene.getTotalIdleTicks());
        assertEquals(0, scene.getCameraViewEvents().get(0).getTick());
        assertEquals(5, scene.getCameraViewEvents().get(1).getTick());
        zoom.addProperty("waitForCompletion", "false");
        assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(zoom), null).hasErrors());
    }

    @Test void malformedOrUnboundedCameraDataCannotPublish() {
        for (String json : new String[] {
            "{type:'zoom_scene',scale:0}", "{type:'zoom_scene',scale:65}", "{type:'zoom_scene',scale:1e-300}",
            "{type:'zoom_scene',scale:'2'}", "{type:'zoom_scene',point:[1,2]}",
            "{type:'zoom_scene',point:[30000001,0,0]}", "{type:'zoom_scene',duration:1.5}",
            "{type:'zoom_scene',duration:72001}", "{type:'encapsulate_bounds',bounds:[0,2,3]}",
            "{type:'encapsulate_bounds',bounds:[1,2.5,3]}", "{type:'encapsulate_bounds',bounds:[257,2,3]}"
        }) {
            JsonObject step = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
            AuthoringDocument doc = AuthoringModelTest.document(step);
            assertTrue(new AuthoringCompiler().validate(doc, null).hasErrors(), json);
            assertThrows(AuthoringCompiler.CompilationException.class, () -> new AuthoringCompiler().compile(doc, null));
        }
    }

    @Test void authoredPitchUsesInstantRotationOnlyWhenTheAuthoredWaitIsZero() {
        JsonObject animated = AuthoringModelTest.step("rotate_camera_y");
        animated.addProperty("degreesX", 30);
        animated.addProperty("degrees", 60);
        animated.addProperty("duration", 7);
        JsonObject instant = AuthoringModelTest.step("rotate_camera_y");
        instant.addProperty("degreesX", -30);
        instant.addProperty("degrees", -60);
        instant.addProperty("duration", 0);
        PonderScene scene = new AuthoringCompiler().compile(AuthoringModelTest.document(animated, instant), null).get(0);
        assertEquals(7, scene.getTotalIdleTicks());
        assertEquals(30F, scene.getCameraEvents().get(0).getPitchDegrees());
        assertEquals(18, scene.getCameraEvents().get(0).getDuration());
        assertEquals(7, scene.getCameraEvents().get(1).getTick());
        assertEquals(0, scene.getCameraEvents().get(1).getDuration());
    }
}
