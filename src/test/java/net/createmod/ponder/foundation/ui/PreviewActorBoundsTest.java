package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.ActorKind;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PreviewActorBoundsTest {

    private static final double ACTOR_RADIUS = 0.55D;
    private static final double ROUNDING_TOLERANCE = 1.0E-8D;

    @BeforeAll
    static void registerBlocks() {
        Bootstrap.register();
    }

    @Test
    void actorMovesWithNullLocationsContributeTheirWholeVisiblePath() throws Exception {
        PonderScene scene = scene();
        spawn(scene, 1, new Vec3d(2.5D, 1.5D, -3.5D));
        scene.addIdleTicks(7);
        move(scene, 1, new Vec3d(14, -3, 18), 29);
        assertNull(scene.getActorEvents().get(1).getLocation());

        assertEnclosesActors(scene, 0, 45, 0.125F);
        PonderScenePreview.PreviewBounds bounds = PonderSceneRuntime.computeBounds(scene);
        assertTrue(bounds.maxX >= 17);
        assertTrue(bounds.maxZ >= 15);
    }

    @Test
    void overlappingMovesFollowTheProductionLinearPlusOneTickProgress() throws Exception {
        PonderScene scene = scene();
        spawn(scene, 1, new Vec3d(2, 3, 4));
        move(scene, 1, new Vec3d(20, -12, 6), 20);
        scene.addIdleTicks(7);
        move(scene, 1, new Vec3d(-18, 9, 24), 31);
        scene.addIdleTicks(3);
        move(scene, 1, new Vec3d(8, 13, -16), 13);

        assertEnclosesActors(scene, 0, 50, 0.0625F);
    }

    @Test
    void repeatedRoundTripsDoNotAccumulateTheirTravelIntoCameraSize() throws Exception {
        PonderScene scene = scene();
        spawn(scene, 1, Vec3d.ZERO);
        scene.addIdleTicks(1);
        for (int trip = 0; trip < 30; trip++) {
            move(scene, 1, new Vec3d(10, 0, 0), 20);
            scene.addIdleTicks(20);
            move(scene, 1, new Vec3d(-10, 0, 0), 20);
            scene.addIdleTicks(20);
        }

        PonderScenePreview.PreviewBounds bounds = PonderSceneRuntime.computeBounds(scene);
        assertEquals(-1, bounds.minX);
        assertEquals(10, bounds.maxX);
        assertEnclosesActors(scene, 0, 1210, 0.5F);
    }

    @Test
    void simultaneousOppositeMovesCancelInsteadOfCreatingIndependentExcursions() throws Exception {
        PonderScene scene = scene();
        spawn(scene, 1, Vec3d.ZERO);
        for (int pair = 0; pair < 20; pair++) {
            move(scene, 1, new Vec3d(10, -4, 7), 60);
            move(scene, 1, new Vec3d(-10, 4, -7), 60);
        }

        assertBounds(PonderSceneRuntime.computeBounds(scene), -1, 0, -1, 0, -1, 0);
        assertEnclosesActors(scene, 0, 75, 0.125F);
    }

    @Test
    void spawningTheSameIdResetsItsOldMotionAndTruncatesTheOldLifetime() throws Exception {
        PonderScene scene = scene();
        spawn(scene, 1, Vec3d.ZERO);
        scene.addIdleTicks(1);
        move(scene, 1, new Vec3d(100, 0, 0), 100);
        scene.addIdleTicks(10);
        spawn(scene, 1, new Vec3d(50, 0, 0));
        move(scene, 1, new Vec3d(-10, 0, 0), 10);

        PonderScenePreview.PreviewBounds bounds = PonderSceneRuntime.computeBounds(scene);
        assertTrue(bounds.maxX <= 50, "the abandoned first move must never reach x=100");
        assertEnclosesActors(scene, 0, 120, 0.125F);
    }

    @Test
    void repeatedSameTickSpawnOnlyIncludesTheActorThatCanActuallyBeRendered() throws Exception {
        PonderScene scene = scene();
        spawn(scene, 1, new Vec3d(1000, 2000, -3000));
        move(scene, 1, new Vec3d(500, 500, 500), 100);
        spawn(scene, 1, new Vec3d(3, 1, 2));

        assertBounds(PonderSceneRuntime.computeBounds(scene), 2, 3, 0, 1, 1, 2);
        assertEnclosesActors(scene, 0, 120, 0.25F);
    }

    @Test
    void moveEventsBeforeSpawnAreIgnoredAndActorIdsKeepIndependentPaths() throws Exception {
        PonderScene scene = scene();
        move(scene, 99, new Vec3d(10000, 0, 0), 10);
        spawn(scene, 1, new Vec3d(-8, 1, 2));
        spawn(scene, 2, new Vec3d(10, 4, -3));
        move(scene, 1, new Vec3d(-10, 2, 7), 25);
        scene.addIdleTicks(4);
        move(scene, 2, new Vec3d(7, -3, -9), 17);

        assertEnclosesActors(scene, 0, 40, 0.125F);
        assertTrue(PonderSceneRuntime.computeBounds(scene).maxX < 30);
    }

    @Test
    void hideCompletionExcludesInvisibleRemainderOfAnOngoingMove() throws Exception {
        PonderScene scene = scene();
        spawn(scene, 1, Vec3d.ZERO);
        scene.addIdleTicks(1);
        move(scene, 1, new Vec3d(1000, 0, 0), 100);
        scene.addIdleTicks(4);
        scene.recordActorHide(1, ActorKind.BIRB, 5);

        PonderScenePreview.PreviewBounds bounds = PonderSceneRuntime.computeBounds(scene);
        assertEquals(90, bounds.maxX, "the actor becomes invisible at tick 9, not at its eventual x=1000");
        assertEnclosesActors(scene, 0, 120, 0.0625F);
    }

    @Test
    void immediatelyHiddenActorsDoNotExpandTheFallbackBaseplate() throws Exception {
        PonderScene empty = scene();
        PonderScene hidden = scene();
        spawn(hidden, 1, new Vec3d(1000, 2000, 3000));
        hidden.recordActorHide(1, ActorKind.BIRB, 1);
        hidden.addIdleTicks(1);
        move(hidden, 1, new Vec3d(5000, 0, 0), 10);

        PonderScenePreview.PreviewBounds expected = PonderSceneRuntime.computeBounds(empty);
        assertBounds(PonderSceneRuntime.computeBounds(hidden),
            expected.minX, expected.maxX, expected.minY, expected.maxY, expected.minZ, expected.maxZ);
        assertEnclosesActors(hidden, 0, 20, 0.125F);
    }

    @Test
    void laterHideEventsFollowRuntimeVisibilityResetWithoutLosingHiddenMovement() throws Exception {
        PonderScene scene = scene();
        spawn(scene, 1, Vec3d.ZERO);
        scene.addIdleTicks(2);
        scene.recordActorHide(1, ActorKind.BIRB, 1);
        scene.addIdleTicks(1);
        move(scene, 1, new Vec3d(500, 0, 0), 20);
        scene.addIdleTicks(27);
        scene.recordActorHide(1, ActorKind.BIRB, 10);

        assertEnclosesActors(scene, 0, 45, 0.125F);
        assertEquals(500, PonderSceneRuntime.computeBounds(scene).maxX);
    }

    @Test
    void nullSpawnLocationAndZeroDurationMovesMatchRuntimeDefaults() throws Exception {
        PonderScene scene = scene();
        spawn(scene, 1, null);
        scene.addIdleTicks(2);
        move(scene, 1, new Vec3d(40, 2, -12), 0);

        assertEnclosesActors(scene, 0, 5, 0.125F);
        assertBounds(PonderSceneRuntime.computeBounds(scene), -1, 40, -1, 2, -13, 0);
    }

    @Test
    void hugeActorDurationAndLateStartDoNotOverflowOrIterateEveryTick() throws Exception {
        PonderScene scene = scene();
        scene.addIdleTicks(2_000_000_000);
        spawn(scene, 1, Vec3d.ZERO);
        move(scene, 1, new Vec3d(100, -80, 25), Integer.MAX_VALUE);

        PonderScenePreview.PreviewBounds bounds = assertTimeoutPreemptively(Duration.ofSeconds(3),
            () -> PonderSceneRuntime.computeBounds(scene));
        PonderDebugScreen screen = new PonderDebugScreen(null, 0);
        Method runtime = actorRuntimeMethod();
        for (int step = 0; step <= 64; step++) {
            float tick = (float) (2_000_000_000.0D + (double) Integer.MAX_VALUE * step / 64.0D);
            assertActorsAt(scene, screen, runtime, bounds, tick);
        }
    }

    @Test
    void nonFiniteVisibleActorCoordinatesFailBeforeEnteringCameraMath() {
        for (double value : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            PonderScene invalidSpawn = scene();
            spawn(invalidSpawn, 1, new Vec3d(value, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> PonderSceneRuntime.computeBounds(invalidSpawn));

            PonderScene invalidMove = scene();
            spawn(invalidMove, 1, Vec3d.ZERO);
            move(invalidMove, 1, new Vec3d(0, value, 0), 20);
            assertThrows(IllegalArgumentException.class, () -> PonderSceneRuntime.computeBounds(invalidMove));
        }
    }

    private static void assertEnclosesActors(PonderScene scene, float first, float last, float step) throws Exception {
        PonderScenePreview.PreviewBounds bounds = PonderSceneRuntime.computeBounds(scene);
        PonderDebugScreen screen = new PonderDebugScreen(null, 0);
        Method runtime = actorRuntimeMethod();
        for (float tick = first; tick <= last; tick += step) {
            assertActorsAt(scene, screen, runtime, bounds, tick);
        }
    }

    private static void assertActorsAt(PonderScene scene, PonderDebugScreen screen, Method runtime,
        PonderScenePreview.PreviewBounds bounds, float tick) throws Exception {
        List<?> actors = (List<?>) runtime.invoke(screen, scene, Float.valueOf(tick));
        for (Object actor : actors) {
            if (!((Boolean) field(actor, "visible")).booleanValue() || ((Float) field(actor, "fade")).floatValue() <= 0) {
                continue;
            }
            Vec3d position = (Vec3d) field(actor, "position");
            String message = "tick " + tick + ", actor " + field(actor, "actorId") + ", position " + position;
            assertTrue(position.x - ACTOR_RADIUS >= bounds.minX - ROUNDING_TOLERANCE
                && position.x + ACTOR_RADIUS <= (double) bounds.maxX + 1 + ROUNDING_TOLERANCE, "x: " + message);
            assertTrue(position.y - ACTOR_RADIUS >= bounds.minY - ROUNDING_TOLERANCE
                && position.y + ACTOR_RADIUS <= (double) bounds.maxY + 1 + ROUNDING_TOLERANCE, "y: " + message);
            assertTrue(position.z - ACTOR_RADIUS >= bounds.minZ - ROUNDING_TOLERANCE
                && position.z + ACTOR_RADIUS <= (double) bounds.maxZ + 1 + ROUNDING_TOLERANCE, "z: " + message);
        }
    }

    private static Object field(Object actor, String name) throws Exception {
        Field field = actor.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(actor);
    }

    private static Method actorRuntimeMethod() throws Exception {
        Method method = PonderDebugScreen.class.getDeclaredMethod("getActorRuntimeStates", PonderScene.class, float.class);
        method.setAccessible(true);
        return method;
    }

    private static PonderScene scene() {
        ResourceLocation id = new ResourceLocation("ponder_test", "actor_bounds");
        return new PonderScene(new PonderStoryBoardEntry(null, "ponder_test", id, id), new PonderLocalization());
    }

    private static void spawn(PonderScene scene, int id, Vec3d position) {
        scene.recordActorSpawn(id, ActorKind.BIRB, position, 1, 0.0F, null);
    }

    private static void move(PonderScene scene, int id, Vec3d offset, int duration) {
        scene.recordActorMove(id, ActorKind.BIRB, offset, duration);
    }

    private static void assertBounds(PonderScenePreview.PreviewBounds bounds, int minX, int maxX,
        int minY, int maxY, int minZ, int maxZ) {
        assertEquals(minX, bounds.minX, "minX");
        assertEquals(maxX, bounds.maxX, "maxX");
        assertEquals(minY, bounds.minY, "minY");
        assertEquals(maxY, bounds.maxY, "maxY");
        assertEquals(minZ, bounds.minZ, "minZ");
        assertEquals(maxZ, bounds.maxZ, "maxZ");
    }
}
