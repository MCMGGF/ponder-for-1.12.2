package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.util.List;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PreviewParticleTimeline;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuthoringParticleDistributionTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    private JsonObject emitter(String distribution) {
        JsonObject step = AuthoringStepTemplates.withType(null, "spawn_particles");
        step.addProperty("distribution", distribution); step.add("spread", AuthoringModelTest.pos(2, 3, 4));
        return step;
    }

    @Test void pointEmitterHasNoInventedOffsetAndKeepsDeclaredMotion() {
        JsonObject step = emitter("point"); step.add("motion", AuthoringModelTest.pos(1, 0, 0));
        PonderScene scene = new AuthoringCompiler().compile(AuthoringModelTest.document(step), null).get(0);
        List<PreviewParticleTimeline.Sample> samples = PreviewParticleTimeline.evaluate(scene, 5, 1);
        assertEquals(10, samples.size());
        for (PreviewParticleTimeline.Sample sample : samples) assertEquals(new Vec3d(5, 1, 0), sample.position);
    }

    @Test void boxAndSphereAreBoundedAndRewindDeterministically() {
        for (String distribution : new String[] {"box", "sphere"}) {
            PonderScene scene = new AuthoringCompiler().compile(AuthoringModelTest.document(emitter(distribution)), null).get(0);
            List<PreviewParticleTimeline.Sample> before = PreviewParticleTimeline.evaluate(scene, 5, 1);
            PreviewParticleTimeline.evaluate(scene, 17, 1);
            List<PreviewParticleTimeline.Sample> rewind = PreviewParticleTimeline.evaluate(scene, 5, 1);
            assertFalse(before.isEmpty());
            for (int i = 0; i < before.size(); i++) {
                Vec3d pos = before.get(i).position.subtract(0, 1, 0);
                assertTrue(Math.abs(pos.x) <= 2 && Math.abs(pos.y) <= 3 && Math.abs(pos.z) <= 4);
                if ("sphere".equals(distribution)) assertTrue(pos.x * pos.x / 4 + pos.y * pos.y / 9 + pos.z * pos.z / 16 <= 1.00000001);
                assertEquals(before.get(i).position, rewind.get(i).position);
            }
        }
    }

    @Test void invalidDistributionNegativeSpreadAndAggregateBudgetsFailBeforeCompilation() {
        AuthoringCompiler compiler = new AuthoringCompiler();
        assertTrue(compiler.validate(AuthoringModelTest.document(emitter("cone")), null).hasErrors());
        JsonObject invalid = emitter("box"); invalid.add("spread", AuthoringModelTest.pos(-1, 0, 0));
        assertTrue(compiler.validate(AuthoringModelTest.document(invalid), null).hasErrors());
        JsonObject huge = emitter("box"); huge.addProperty("count", 256); huge.addProperty("duration", 256);
        assertTrue(compiler.validate(AuthoringModelTest.document(huge, huge), null).hasErrors());
        JsonObject[] many = new JsonObject[257]; java.util.Arrays.fill(many, emitter("point"));
        assertTrue(compiler.validate(AuthoringModelTest.document(many), null).hasErrors());
    }

    @Test void upstreamZeroCountAndDefaultOneCycleAreNotInventedAsVisibleParticles() {
        JsonObject zero = emitter("box"); zero.addProperty("count", 0);
        assertTrue(new AuthoringCompiler().compile(AuthoringModelTest.document(zero), null).get(0).getParticleEvents().isEmpty());
        JsonObject defaults = AuthoringModelTest.step("spawn_particles"); defaults.addProperty("particle", "FLAME");
        PonderScene.ParticleEffectEvent event = new AuthoringCompiler().compile(AuthoringModelTest.document(defaults), null).get(0).getParticleEvents().get(0);
        assertEquals(1, event.getCycles()); assertEquals(10, event.getAmountPerCycle());
    }
}
