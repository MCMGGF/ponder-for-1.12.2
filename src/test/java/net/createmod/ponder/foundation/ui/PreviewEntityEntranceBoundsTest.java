package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.PreviewActorTimeline;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PreviewEntityEntranceBoundsTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void completePlayerBodyAndGlidingToolsFitTheFixedEntranceRouteWithoutWalkAnimation() {
        PonderScene scene = scene();
        scene.recordActorSpawn(1, PonderScene.ActorKind.PLAYER, new Vec3d(0, 0.5, 0), 10, 35, "pose");
        scene.recordActorEntranceMove(1, PonderScene.ActorKind.PLAYER, new Vec3d(0, -0.5, 0), 20);
        PreviewMotionBounds.Bounds bounds = PreviewActorBounds.compute(scene.getActorEvents());
        for (float tick = 0; tick <= 20; tick += 0.25F) {
            PreviewActorTimeline.State generic = PreviewActorTimeline.evaluate(scene.getActorEvents(), tick).get(0);
            PreviewPlayerTimeline.State player = PreviewPlayerTimeline.evaluate(scene.getActorEvents(), tick).get(0);
            assertEquals(generic.position, player.position);
            assertEquals(0F, player.limbSwingAmount);
            assertTrue(player.position.x - 1.25 >= bounds.minX && player.position.x + 1.25 <= bounds.maxX);
            assertTrue(player.position.y - 0.15 >= bounds.minY && player.position.y + 2.20 <= bounds.maxY);
            assertTrue(player.position.z - 1.25 >= bounds.minZ && player.position.z + 1.25 <= bounds.maxZ);
        }
    }

    @Test void quadraticEntranceCombinedWithAnOpposingLinearMoveIncludesInteriorExtrema() {
        PonderScene scene = scene();
        scene.recordActorSpawn(1, PonderScene.ActorKind.BIRB, new Vec3d(0.5, 0, 0), 10, 0, "pose");
        scene.recordActorEntranceMove(1, PonderScene.ActorKind.BIRB, new Vec3d(-0.5, 0, 0), 20);
        scene.recordActorMove(1, PonderScene.ActorKind.BIRB, new Vec3d(0.3, 0, 0), 20, false);
        PreviewMotionBounds.Bounds bounds = PreviewActorBounds.compute(scene.getActorEvents());
        for (float tick = 0; tick <= 25; tick += 0.125F) {
            Vec3d position = PreviewActorTimeline.evaluate(scene.getActorEvents(), tick).get(0).position;
            assertTrue(position.x - 0.55 >= bounds.minX, "interior quadratic extrema must not escape the static frame");
            assertTrue(position.x + 0.55 <= bounds.maxX);
        }
    }

    private static PonderScene scene() {
        return new PonderScene(new PonderStoryBoardEntry((s, u) -> { }, "entrance_test",
            new ResourceLocation("entrance_test", "main"), new ResourceLocation("minecraft", "stone")), new PonderLocalization());
    }
}
