package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.PlayerElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.ActorKind;
import net.createmod.ponder.foundation.PonderSceneBuilder;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PreviewPlayerTimelineTest {
    @BeforeAll
    static void initialize() {
        Bootstrap.register();
    }

    @Test
    void noPlayerIsReturnedBeforeItsSpawnOrForOtherActorKinds() {
        PonderScene scene = scene();
        scene.recordActorSpawn(9, ActorKind.CART, Vec3d.ZERO, 1, 0, null);
        scene.addIdleTicks(10);
        scene.recordActorSpawn(1, ActorKind.PLAYER, Vec3d.ZERO, 10, 0, null);
        assertTrue(PreviewPlayerTimeline.evaluate(scene.getActorEvents(), 9).isEmpty());
        assertEquals(1, PreviewPlayerTimeline.evaluate(scene.getActorEvents(), 10).size());
        assertEquals(0.1F, player(scene, 10).fade, 0.0001F);
    }

    @Test
    void seekingBackwardsRestoresMotionHeldItemSneakAndSwing() {
        PonderScene scene = scene();
        scene.recordActorSpawn(1, ActorKind.PLAYER, new Vec3d(2, 1, 3), 1, 20, null);
        scene.addIdleTicks(10);
        scene.recordActorMove(1, ActorKind.PLAYER, new Vec3d(4, 0, 0), 20);
        scene.recordPlayerItem(1, new ItemStack(Items.IRON_PICKAXE));
        scene.recordPlayerSneak(1, true);
        scene.recordPlayerSwing(1, 10);
        PreviewPlayerTimeline.State first = player(scene, 14.5F);
        player(scene, 200);
        PreviewPlayerTimeline.State repeated = player(scene, 14.5F);
        assertEquals(first.position, repeated.position);
        assertEquals(first.swingProgress, repeated.swingProgress);
        assertEquals(first.limbSwing, repeated.limbSwing);
        assertEquals(3.1D, repeated.position.x, 0.0001D);
        assertEquals(0.45F, repeated.swingProgress, 0.0001F);
        assertTrue(repeated.sneaking);
        assertEquals(Items.IRON_PICKAXE, repeated.heldItem.getItem());
        PreviewPlayerTimeline.State before = player(scene, 9);
        assertEquals(2.0D, before.position.x);
        assertTrue(before.heldItem.isEmpty());
        assertFalse(before.sneaking);
        assertEquals(0.0F, before.swingProgress);
    }

    @Test
    void completedMovesStopWalkingAndOppositeMovesCancelVelocity() {
        PonderScene scene = scene();
        scene.recordActorSpawn(1, ActorKind.PLAYER, Vec3d.ZERO, 1, 0, null);
        scene.recordActorMove(1, ActorKind.PLAYER, new Vec3d(5, 0, 0), 20);
        assertTrue(player(scene, 5).limbSwingAmount > 0);
        assertEquals(0.0F, player(scene, 19).limbSwingAmount);
        scene.recordActorMove(1, ActorKind.PLAYER, new Vec3d(-5, 0, 0), 20);
        assertEquals(Vec3d.ZERO, player(scene, 5).position);
        assertEquals(0.0F, player(scene, 5).limbSwingAmount);
    }

    @Test
    void lookingAtTargetsInterpolatesAndUsesShortestYaw() {
        PonderScene scene = scene();
        scene.recordActorSpawn(1, ActorKind.PLAYER, Vec3d.ZERO, 1, 170, null);
        scene.addIdleTicks(10);
        double radians = Math.toRadians(-170);
        scene.recordPlayerLook(1, new Vec3d(-Math.sin(radians) * 4, 1.62D, Math.cos(radians) * 4), 20);
        assertEquals(180.0F, player(scene, 19).yaw, 0.001F);
        assertEquals(190.0F, player(scene, 29).yaw, 0.001F);
        assertEquals(0.0F, player(scene, 29).pitch, 0.001F);
        scene.addIdleTicks(20);
        scene.recordPlayerLook(1, new Vec3d(0, -100, 0), 5);
        assertEquals(80.0F, player(scene, 34).pitch, 0.001F);
    }

    @Test
    void laterSpawnResetsOldMovesAndHideIsReversibleBySeek() {
        PonderScene scene = scene();
        scene.recordActorSpawn(1, ActorKind.PLAYER, Vec3d.ZERO, 1, 0, null);
        scene.recordActorMove(1, ActorKind.PLAYER, new Vec3d(100, 0, 0), 100);
        scene.addIdleTicks(10);
        scene.recordActorSpawn(1, ActorKind.PLAYER, new Vec3d(5, 1, 0), 1, 90, null);
        scene.addIdleTicks(10);
        scene.recordActorHide(1, ActorKind.PLAYER, 5);
        assertEquals(5.0D, player(scene, 100).position.x);
        assertEquals(0.0F, player(scene, 24).fade);
        assertEquals(1.0F, player(scene, 19).fade);
    }

    @Test
    void heldItemsAreSnapshotsAndCannotMutateTheSceneOrCallerStack() {
        PonderScene scene = scene();
        scene.recordActorSpawn(1, ActorKind.PLAYER, Vec3d.ZERO, 1, 0, null);
        ItemStack source = new ItemStack(Items.APPLE, 4);
        scene.recordPlayerItem(1, source);
        source.setCount(99);
        player(scene, 0).heldItem.setCount(1);
        assertEquals(4, player(scene, 0).heldItem.getCount());
    }

    @Test
    void publicBuilderRecordsEveryPlayerActionAndDoesNotExecuteWorldEvents() {
        PonderScene scene = scene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        ElementLink<PlayerElement> actor = builder.special().createPlayer(new Vec3d(1, 1, 1), 0);
        builder.special().movePlayer(actor, new Vec3d(2, 0, 0), 10);
        builder.special().lookAtPlayer(actor, new Vec3d(2, 1, 2), 10);
        builder.special().setPlayerItem(actor, new ItemStack(Items.STICK));
        builder.special().swingPlayer(actor, 6);
        builder.special().sneakPlayer(actor, true);
        builder.idle(20);
        builder.special().hideElement(actor, EnumFacing.DOWN);
        assertEquals(7, scene.getActorEvents().size());
        assertTrue(scene.getWorldEvents().isEmpty());
        assertEquals(0.0F, player(scene, 40).fade);
    }

    @Test
    void keyframeTicksAreDeduplicatedImmutableAndDoNotChangeLegacyCounts() {
        PonderScene scene = scene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        builder.addKeyframe();
        builder.addKeyframe();
        builder.idle(10);
        builder.addLazyKeyframe();
        builder.idle(10);
        builder.addKeyframe();
        List<Integer> ticks = scene.getKeyframeTicks();
        assertEquals(Arrays.asList(0, 20), ticks);
        assertEquals(3, scene.getKeyframeCount());
        assertEquals(1, scene.getLazyKeyframeCount());
        assertThrows(UnsupportedOperationException.class, () -> ticks.add(30));
        builder.idle(10);
        builder.addKeyframe();
        assertEquals(Arrays.asList(0, 20), ticks);
        assertEquals(Arrays.asList(0, 20, 30), scene.getKeyframeTicks());
    }

    @Test
    void playerBoundsContainFullModelToolsAndBothMovementEndpoints() {
        PonderScene scene = scene();
        scene.recordActorSpawn(1, ActorKind.PLAYER, new Vec3d(1, 1, 1), 10, 0, null);
        scene.addIdleTicks(20);
        scene.recordActorMove(1, ActorKind.PLAYER, new Vec3d(6, 2, -4), 30);
        PreviewMotionBounds.Bounds bounds = PreviewActorBounds.compute(scene.getActorEvents());
        assertTrue(bounds.minX <= -0.25D);
        assertTrue(bounds.maxX >= 8.25D);
        assertTrue(bounds.minY <= 0.85D);
        assertTrue(bounds.maxY >= 5.20D);
        assertTrue(bounds.minZ <= -4.25D);
        assertTrue(bounds.maxZ >= 2.25D);
    }

    @Test
    void minecartPitchComesFromCurrentSlopeAndResetsOnFlatOrRespawn() {
        PonderScene scene = scene();
        scene.recordActorSpawn(1, ActorKind.CART, Vec3d.ZERO, 1, 0, null);
        scene.recordActorMove(1, ActorKind.CART, new Vec3d(1, 1, 0), 20);
        assertEquals(45.0F, PreviewPlayerRenderer.cartPitch(scene.getActorEvents(), 1, 10), 0.001F);
        assertEquals(0.0F, PreviewPlayerRenderer.cartPitch(scene.getActorEvents(), 1, 20));
        scene.addIdleTicks(10);
        scene.recordActorSpawn(1, ActorKind.CART, Vec3d.ZERO, 1, 0, null);
        assertEquals(0.0F, PreviewPlayerRenderer.cartPitch(scene.getActorEvents(), 1, 10));
    }

    @Test
    void vanillaCartBoundsContainItsBodyWhenTurnedOrOnASlope() {
        PonderScene scene = scene();
        scene.recordActorSpawn(1, ActorKind.CART, new Vec3d(1, 1, 1), 1, 45, null);
        scene.recordActorMove(1, ActorKind.CART, new Vec3d(1, 1, 0), 20);
        PreviewMotionBounds.Bounds bounds = PreviewActorBounds.compute(scene.getActorEvents());
        assertTrue(bounds.minX <= 0.15D);
        assertTrue(bounds.maxX >= 2.9D);
        assertTrue(bounds.minY <= 0.5D);
        assertTrue(bounds.maxY >= 3.05D);
        assertTrue(bounds.minZ <= 0.1D);
        assertTrue(bounds.maxZ >= 1.9D);
    }

    @Test
    void minecartRisingInReverseTiltsTowardItsMovementDirection() {
        PonderScene scene = scene();
        scene.recordActorSpawn(1, ActorKind.CART, Vec3d.ZERO, 1, 0, null);
        scene.recordActorMove(1, ActorKind.CART, new Vec3d(-1, 1, 0), 20);
        assertEquals(-45.0F, PreviewPlayerRenderer.cartPitch(scene.getActorEvents(), 1, 10), 0.001F);
    }

    private static PreviewPlayerTimeline.State player(PonderScene scene, float tick) {
        return PreviewPlayerTimeline.evaluate(scene.getActorEvents(), tick).get(0);
    }

    private static PonderScene scene() {
        ResourceLocation id = new ResourceLocation("ponder_test", "player");
        return new PonderScene(new PonderStoryBoardEntry(null, "ponder_test", id, id), new PonderLocalization());
    }
}
