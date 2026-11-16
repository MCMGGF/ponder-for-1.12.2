package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.ActorKind;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.PreviewActorTimeline;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PreviewActorNbtTimelineTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void absolutePositionRotationAndVisibilityAreRecomputedAfterSeek() throws Exception {
        PonderScene scene=scene(); scene.recordActorSpawn(1,ActorKind.PLAYER,Vec3d.ZERO,1,0,null);
        scene.recordActorMove(1,ActorKind.PLAYER,new Vec3d(5,0,0),5); scene.addIdleTicks(5);
        scene.recordActorNbtPatch(1,ActorKind.PLAYER,tag("{Pos:[20d,2d,3d],Rotation:[90f,25f],Invisible:1b}"));
        scene.addIdleTicks(3); scene.recordActorNbtPatch(1,ActorKind.PLAYER,tag("{Invisible:0b}"));
        for(int tick:new int[]{8,0,5,4,8}) {
            PreviewPlayerTimeline.State player=PreviewPlayerTimeline.evaluate(scene.getActorEvents(),tick).get(0);
            PreviewActorTimeline.State generic=PreviewActorTimeline.evaluate(scene.getActorEvents(),tick).get(0);
            assertEquals(generic.position,player.position);
            if(tick>=5) { assertEquals(new Vec3d(20,2,3),player.position); assertEquals(90,player.yaw,0.001); assertEquals(25,player.pitch,0.001); }
            assertEquals(tick>=5 && tick<8?0:1,player.fade,0.001);
        }
    }

    @Test void motionCanRemainUnanimatedOrUseDeterministicPlayerWalking() {
        PonderScene still=scene(), walking=scene();
        still.recordActorSpawn(1,ActorKind.PLAYER,Vec3d.ZERO,1,0,null);
        walking.recordActorSpawn(1,ActorKind.PLAYER,Vec3d.ZERO,1,0,null);
        still.recordActorMove(1,ActorKind.PLAYER,new Vec3d(5,0,0),20,false);
        walking.recordActorMove(1,ActorKind.PLAYER,new Vec3d(5,0,0),20,true);
        PreviewPlayerTimeline.State a=PreviewPlayerTimeline.evaluate(still.getActorEvents(),10).get(0);
        PreviewPlayerTimeline.State b=PreviewPlayerTimeline.evaluate(walking.getActorEvents(),10).get(0);
        assertEquals(a.position,b.position); assertEquals(0,a.limbSwingAmount); assertEquals(0,a.limbSwing);
        assertTrue(b.limbSwingAmount>0); assertTrue(b.limbSwing>0);
    }

    @Test void boundsContainAbsoluteJumpsAndFollowingRoutesWithoutAddingOldTravelAgain() throws Exception {
        PonderScene scene=scene(); scene.recordActorSpawn(1,ActorKind.PLAYER,Vec3d.ZERO,1,0,null);
        scene.addIdleTicks(1);
        scene.recordActorMove(1,ActorKind.PLAYER,new Vec3d(10,0,0),5); scene.addIdleTicks(5);
        scene.recordActorNbtPatch(1,ActorKind.PLAYER,tag("{Pos:[100d,0d,0d]}"));
        scene.addIdleTicks(1);
        scene.recordActorMove(1,ActorKind.PLAYER,new Vec3d(-5,0,0),5);
        PreviewMotionBounds.Bounds bounds=PreviewActorBounds.compute(scene.getActorEvents());
        assertTrue(bounds.minX<=-1.25); assertTrue(bounds.maxX>=101.25); assertTrue(bounds.maxX<102);
        assertEquals(95,PreviewPlayerTimeline.evaluate(scene.getActorEvents(),11).get(0).position.x,0.0001);
    }

    @Test void itemSnapshotMutationAndReturnedStateCannotChangeAnEvent() throws Exception {
        PonderScene scene=scene(); scene.recordItemActorSpawn(1,Vec3d.ZERO,1,null,new ItemStack(Items.COMPASS));
        ItemStack replacement=new ItemStack(Items.DIAMOND,3); NBTTagCompound patch=tag("{Item:{id:\"minecraft:diamond\",Count:3b}}");
        scene.recordActorNbtPatch(1,ActorKind.ITEM,patch,replacement);
        patch.removeTag("Item"); replacement.setCount(99);
        PreviewActorTimeline.State state=PreviewActorTimeline.evaluate(scene.getActorEvents(),0).get(0);
        state.getItemStack().setCount(20); state.getNbt().removeTag("Item");
        assertEquals(3,PreviewActorTimeline.evaluate(scene.getActorEvents(),0).get(0).getItemStack().getCount());
        assertTrue(PreviewActorTimeline.evaluate(scene.getActorEvents(),0).get(0).getNbt().hasKey("Item"));
    }

    @Test void invisibleFalseCannotResurrectAnActorAfterItWasHidden() throws Exception {
        PonderScene scene=scene(); scene.recordActorSpawn(1,ActorKind.PLAYER,Vec3d.ZERO,1,0,null);
        scene.recordActorNbtPatch(1,ActorKind.PLAYER,tag("{Invisible:1b}"));
        assertFalse(PreviewActorVisibility.anyVisible(scene.getActorEvents(),0));
        scene.addIdleTicks(1); scene.recordActorHide(1,ActorKind.PLAYER,1);
        scene.addIdleTicks(1); scene.recordActorNbtPatch(1,ActorKind.PLAYER,tag("{Invisible:0b}"));
        assertFalse(PreviewActorVisibility.anyVisible(scene.getActorEvents(),2));
        assertFalse(PreviewActorTimeline.evaluate(scene.getActorEvents(),2).get(0).visible);
        assertEquals(0,PreviewPlayerTimeline.evaluate(scene.getActorEvents(),2).get(0).fade);
    }

    @Test void minecartAbsoluteNbtPitchAndVectorRotationReachTheVanillaModel() throws Exception {
        PonderScene scene=scene(); scene.recordActorSpawn(1,ActorKind.CART,Vec3d.ZERO,1,0,null);
        scene.recordActorNbtPatch(1,ActorKind.CART,tag("{Rotation:[40f,25f]}"));
        assertEquals(25,PreviewPlayerRenderer.cartPitch(scene.getActorEvents(),1,0),0.001);
        scene.addIdleTicks(1); scene.recordActorRotation(1,ActorKind.CART,new Vec3d(10,20,0),1);
        assertEquals(35,PreviewPlayerRenderer.cartPitch(scene.getActorEvents(),1,1),0.001);
        assertEquals(60,PreviewActorTimeline.evaluate(scene.getActorEvents(),1).get(0).yaw,0.001);
    }

    private static NBTTagCompound tag(String value) throws Exception { return JsonToNBT.getTagFromJson(value); }
    private static PonderScene scene() { ResourceLocation id=new ResourceLocation("test:actor_nbt"); return new PonderScene(new PonderStoryBoardEntry(null,"test",id,id),new PonderLocalization()); }
}
