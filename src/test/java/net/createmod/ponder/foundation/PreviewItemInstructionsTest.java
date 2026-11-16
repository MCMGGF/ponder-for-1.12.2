package net.createmod.ponder.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.foundation.PonderScene.ActorEvent;
import net.createmod.ponder.foundation.PonderScene.ActorEventType;
import net.createmod.ponder.foundation.PonderScene.ActorKind;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PreviewItemInstructionsTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void explicitMovementUsesTheOriginalPreviewActorAndRequestedDuration() {
        PonderScene scene = scene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        ElementLink<EntityElement> link = builder.world().createItemEntity(new Vec3d(1, 1, 1), Vec3d.ZERO,
            new ItemStack(Items.REDSTONE));
        builder.idle(15);
        builder.world().moveEntity(link, new Vec3d(2, 0, 0), 60);
        assertEquals(2, scene.getActorEvents().size());
        ActorEvent spawn = scene.getActorEvents().get(0);
        ActorEvent move = scene.getActorEvents().get(1);
        assertEquals(ActorEventType.SPAWN, spawn.getType());
        assertEquals(ActorEventType.MOVE, move.getType());
        assertEquals(ActorKind.ITEM, move.getActorKind());
        assertEquals(spawn.getActorId(), move.getActorId());
        assertEquals(new Vec3d(2, 0, 0), move.getOffset());
        assertEquals(60, move.getDuration());
        assertEquals(15, move.getTick());
        assertSame(Items.REDSTONE, spawn.getItemStack().getItem());
    }

    @Test
    void legacySpawnMotionStillUsesTwentyTicks() {
        PonderScene scene = scene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        builder.world().createItemEntity(Vec3d.ZERO, new Vec3d(0.1, 0, 0), new ItemStack(Items.COMPASS));
        ActorEvent move = scene.getActorEvents().get(1);
        assertEquals(20, move.getDuration());
        assertEquals(new Vec3d(2, 0, 0), move.getOffset());
    }

    @Test
    void unknownOrNullLinksNeverCreateLiveEntityMovement() {
        PonderScene scene = scene();
        PonderSceneBuilder builder = new PonderSceneBuilder(scene);
        ElementLink<EntityElement> unknown = builder.world().createEntity(world -> {
            throw new AssertionError("Preview factory must not run");
        });
        builder.world().moveEntity(unknown, new Vec3d(1, 0, 0), 10);
        builder.world().moveEntity(null, new Vec3d(1, 0, 0), 10);
        ElementLink<EntityElement> item = builder.world().createItemEntity(Vec3d.ZERO, Vec3d.ZERO,
            new ItemStack(Items.COMPASS));
        builder.world().moveEntity(item, null, 10);
        assertEquals(1, scene.getActorEvents().size());
    }

    private static PonderScene scene() {
        ResourceLocation id = new ResourceLocation("ponder_test", "item_instructions");
        return new PonderScene(new PonderStoryBoardEntry(null, "ponder_test", id, id), new PonderLocalization());
    }
}
