package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.createmod.ponder.authoring.AuthoringCompiler;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.AuthoringEdits;
import net.createmod.ponder.authoring.AuthoringSceneForker;
import net.createmod.ponder.authoring.ExtraStructurePlanning;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSchematic;
import net.createmod.ponder.foundation.SectionOwnershipEvent;
import net.createmod.ponder.foundation.SectionOwnershipBudget;
import net.createmod.ponder.foundation.SectionOwnershipEvent.Action;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PreviewSectionOwnershipTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    private static JsonObject step(String type) { JsonObject result=new JsonObject(); result.addProperty("type",type); return result; }
    private static JsonArray vec(double x,double y,double z) {JsonArray a=new JsonArray();a.add(x);a.add(y);a.add(z);return a;}
    private static JsonObject block(int x,String id) {JsonObject s=step("set_block");s.addProperty("block",id);s.add("blockPos",vec(x,0,0));return s;}
    private static JsonObject show(int x,String link,int duration) {JsonObject s=step("show_section_and_merge");s.add("blockPos",vec(x,0,0));s.addProperty("linkId",link);s.addProperty("duration",duration);return s;}
    private static JsonObject idle(int ticks) {JsonObject s=step("idle");s.addProperty("duration",ticks);return s;}
    private static JsonObject move(String link,int x,int duration) {JsonObject s=step("move_section");s.addProperty("linkId",link);s.add("offset",vec(x,0,0));s.addProperty("duration",duration);return s;}
    private static JsonObject rotate(String link,int degrees) {JsonObject s=step("rotate_section");s.addProperty("linkId",link);s.addProperty("rotY",degrees);s.add("pivot",vec(.5,.5,.5));s.addProperty("duration",0);return s;}
    private static AuthoringDocument doc(JsonObject...steps) {
        JsonObject root=AuthoringEdits.empty("local:ownership").toJson();JsonObject segment=root.getAsJsonArray("scenes").get(0).getAsJsonObject();
        segment.addProperty("autoReveal",false);segment.addProperty("autoKeyframes",false);JsonArray list=new JsonArray();for(JsonObject s:steps)list.add(s);segment.add("steps",list);
        return AuthoringDocument.fromJson(root);
    }
    private static PonderScene scene(JsonObject...steps) {return new AuthoringCompiler().compile(doc(steps),null).get(0);}
    private static PonderSceneRuntime.RuntimeBlockState state(PonderScene scene,int x,float tick) {return PonderSceneRuntime.buildState(scene,tick).blocksByPosition.get(new BlockPos(x,0,0));}
    private static void center(PonderSceneRuntime.RuntimeBlockState state,double x,double y,double z) {assertEquals(x,state.renderCenterX,1E-6);assertEquals(y,state.renderCenterY,1E-6);assertEquals(z,state.renderCenterZ,1E-6);}
    private static JsonObject extra(int x,String link) {JsonObject s=step("show_extra_structure");s.addProperty("structure","local:one");s.add("blockPos",vec(x,0,0));if(link!=null)s.addProperty("linkId",link);return s;}
    private static PonderSchematic one(String nbt) throws Exception {
        Map<BlockPos,NBTTagCompound> tiles=new LinkedHashMap<>();if(nbt!=null)tiles.put(BlockPos.ORIGIN,JsonToNBT.getTagFromJson(nbt));
        return PonderSchematic.of(new BlockPos(1,1,1),Collections.singletonMap(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState()),tiles);
    }

    @Test void repeatedLinkAppendWaitsForItsOwnFadeThenInheritsExistingMoveAndFutureMove() {
        PonderScene s=scene(block(0,"minecraft:stone"),block(1,"minecraft:stone"),show(0,"a",0),idle(1),move("a",3,4),idle(5),show(1,"a",3),idle(3),move("a",1,1));
        center(state(s,0,6),3.5,.5,.5);assertEquals(4.5,state(s,1,6).renderCenterX,1E-6);
        assertNotEquals(state(s,0,6).sectionId,state(s,1,6).sectionId);
        center(state(s,1,8),4.5,.5,.5);assertEquals(state(s,0,8).sectionId,state(s,1,8).sectionId);
        center(state(s,0,9),4.5,.5,.5);center(state(s,1,9),5.5,.5,.5);
        for(float tick:new float[]{9,6,8,0,9}) assertEquals(tick==9?5.5:tick==0?1.5:4.5,state(s,1,tick).renderCenterX,1E-6);
    }
    @Test void appendAfterMoveAndRotationUsesTargetTransformNotTheNewCellsOldOwner() {
        PonderScene s=scene(block(0,"minecraft:stone"),block(1,"minecraft:stone"),show(0,"a",0),move("a",2,0),rotate("a",90),show(1,"a",0));
        center(state(s,0,0),.5,.5,-1.5);center(state(s,1,0),.5,.5,-2.5);
        assertEquals(2,state(s,1,0).transforms.size());assertEquals(state(s,0,0).sectionId,state(s,1,0).sectionId);
    }
    @Test void temporaryRevealCopiesTargetOffsetButNotRotationUntilNativeMergeCompletion() {
        JsonObject entrance=show(1,"a",3);entrance.addProperty("direction","up");
        PonderScene s=scene(block(0,"minecraft:stone"),block(1,"minecraft:stone"),show(0,"a",0),move("a",2,0),rotate("a",90),idle(1),entrance);
        center(state(s,1,1),3.5,.5-2.0/9.0,.5);
        center(state(s,1,3),.5,.5,-2.5);
        center(state(s,1,1),3.5,.5-2.0/9.0,.5);
    }
    @Test void transferringAnExistingCoordinateRevokesEveryFutureMoveOfItsPreviousOwner() {
        PonderScene s=scene(block(0,"minecraft:stone"),show(0,"old",0),move("old",4,0),show(0,"new",0),move("old",8,0),move("new",1,0));
        center(state(s,0,0),1.5,.5,.5);assertEquals(1,state(s,0,0).transforms.size());
    }
    @Test void replacingMovedExtraGeometryClearsOldTileNbtAndOldOwnership() throws Exception {
        JsonObject old=block(0,"minecraft:chest");old.addProperty("nbt","{id:\"minecraft:chest\",oldOnly:1,CustomName:\"old\"}");
        PonderSchematic asset=one(null);
        PonderScene s=new AuthoringCompiler().compile(doc(old,show(0,"old",0),move("old",5,0),extra(0,"new"),move("old",7,0)),id->asset).get(0);
        center(state(s,0,0),.5,.5,.5);assertNull(state(s,0,0).tileNbt);
    }
    @Test void pendingTemporaryMergeCannotResurrectAReleasedCoordinateAtItsCompletion() {
        PonderScene s=scene(block(0,"minecraft:stone"),block(1,"minecraft:stone"),show(0,"a",0),move("a",5,0),show(1,"a",10),idle(2),block(1,"minecraft:glass"),idle(12),move("a",3,0));
        assertFalse(state(s,1,1).sectionId.isEmpty());
        for(float tick:new float[]{2,9,14,2,14}) {center(state(s,1,tick),1.5,.5,.5);assertTrue(state(s,1,tick).sectionId.isEmpty());}
    }
    @Test void pendingMergeOnlyTransfersTheCellsStillOwnedByItsTemporarySource() {
        PonderScene s=scene(block(0,"minecraft:stone"),block(1,"minecraft:stone"),show(0,"a",0),show(1,"a",10),idle(2),show(1,"b",0),move("b",2,0),idle(12),move("a",6,0));
        center(state(s,1,14),3.5,.5,.5);assertNotEquals(state(s,0,14).sectionId,state(s,1,14).sectionId);
    }
    @Test void sameTickReleaseAfterImmediateMergeWinsButTheReverseOrderRecruitsNewGeometry() {
        PonderScene after=scene(block(0,"minecraft:stone"),block(1,"minecraft:stone"),show(0,"a",0),move("a",2,0),show(1,"a",1),block(1,"minecraft:glass"));
        PonderScene before=scene(block(0,"minecraft:stone"),block(1,"minecraft:stone"),show(0,"a",0),move("a",2,0),block(1,"minecraft:glass"),show(1,"a",1));
        center(state(after,1,0),1.5,.5,.5);center(state(before,1,0),3.5,.5,.5);
        assertTrue(state(after,1,0).sectionId.isEmpty());assertFalse(state(before,1,0).sectionId.isEmpty());
    }
    @Test void smartDisplaySkipsVisibleOwnedCellsButExplicitFalseRecruitsThem() {
        JsonObject smart=show(0,"new",0);smart.addProperty("entranceAnimation","up");smart.addProperty("entranceDuration",1);
        PonderScene kept=scene(block(0,"minecraft:stone"),smart,move("new",4,0));center(state(kept,0,0),.5,.5,.5);center(state(kept,0,1),.5,.5,.5);
        smart.addProperty("smartDisplay",false);
        PonderScene recruited=scene(block(0,"minecraft:stone"),smart,move("new",4,0));center(state(recruited,0,0),.5,.5,.5);center(state(recruited,0,1),4.5,.5,.5);
        assertEquals(1,recruited.getWorldEvents().stream().filter(event->event.getType()==PonderScene.WorldEventType.MOVE_SECTION).findFirst().get().getTick());
    }
    @Test void smartDisplayCapturesAirBeforeWritingReplacementState() throws Exception {
        JsonObject animated=extra(0,"new");animated.addProperty("entranceAnimation","up");animated.addProperty("entranceDuration",6);
        PonderSchematic asset=one(null);
        PonderScene s=new AuthoringCompiler().compile(doc(block(0,"minecraft:air"),animated),id->asset).get(0);
        assertTrue(state(s,0,0).visible);assertTrue(state(s,0,0).fade>0 && state(s,0,0).fade<1);
    }
    @Test void directionalMergeKeepsUnrevealedRowsHiddenAndJoinsEachCompletedTemporaryRow() {
        JsonObject reveal=show(0,"rows",0);reveal.add("blockPos2",vec(2,0,0));reveal.addProperty("entranceAnimation","east");reveal.addProperty("entranceDuration",4);reveal.addProperty("entranceInterval",2);reveal.addProperty("smartDisplay",false);
        PonderScene s=scene(block(0,"minecraft:stone"),block(1,"minecraft:stone"),block(2,"minecraft:stone"),reveal);
        assertEquals(6,s.getTotalIdleTicks());assertFalse(state(s,1,1).visible);assertFalse(state(s,2,3).visible);
        assertTrue(state(s,1,2).visible);assertEquals(state(s,0,7).sectionId,state(s,2,7).sectionId);
    }
    @Test void anAirCoordinateInACompletedShowCannotRecruitALaterLegacyPlacement() {
        JsonObject air=block(1,"minecraft:air");JsonObject later=block(1,"minecraft:stone");later.addProperty("legacyOwnership",true);
        PonderScene s=scene(block(0,"minecraft:stone"),air,show(1,"air",0),later,move("air",9,0));
        center(state(s,1,0),1.5,.5,.5);assertTrue(state(s,1,0).sectionId.isEmpty());
    }
    @Test void sourcePointOutlineAndPickGeometryShareTheOwnedRuntimeTransform() {
        PonderScene s=scene(block(0,"minecraft:stone"),show(0,"a",0),move("a",3,0),rotate("a",90));
        PonderSceneRuntime.RuntimeState frame=PonderSceneRuntime.buildState(s,0);PonderSceneRuntime.RuntimeBlockState block=frame.blocksByPosition.get(BlockPos.ORIGIN);
        Vec3d center=PonderSceneRuntime.transformPoint(block,new Vec3d(.5,.5,.5));
        assertEquals(center,PonderSceneRuntime.transformPointAtTick(s,new Vec3d(.5,.5,.5),0));
        assertEquals(PonderSceneRuntime.transformPoint(block,new Vec3d(.5,1,.5)),PonderSceneRuntime.transformPointAtTick(s,new Vec3d(.5,1,.5),0));
        List<PreviewOutlineGeometry.Outline> outline=PreviewOutlineGeometry.selection(BlockPos.ORIGIN,frame,null);assertEquals(12,outline.get(0).edges.size());
        for(PreviewOutlineGeometry.Edge edge:outline.get(0).edges) {
            assertEquals(.5,Math.abs(edge.start.x-center.x),1E-6);assertEquals(.5,Math.abs(edge.start.y-center.y),1E-6);assertEquals(.5,Math.abs(edge.start.z-center.z),1E-6);
        }
    }
    @Test void fixedBoundsIncludeAnAppendedCellInheritingMotionRecordedBeforeItJoined() {
        PonderScene s=scene(block(0,"minecraft:stone"),block(1,"minecraft:stone"),show(0,"a",0),move("a",100,0),show(1,"a",0));
        PonderScenePreview.PreviewBounds bounds=PonderSceneRuntime.computeBounds(s);assertTrue(bounds.maxX>=101);
        for(float tick:new float[]{0,20,0}) {state(s,1,tick);assertSame(bounds,PonderSceneRuntime.computeBounds(s));}
    }
    @Test void nativeForkRoundTripPreservesMembershipAndTransformsAcrossBackwardReplay() throws Exception {
        PonderScene original=scene(block(0,"minecraft:stone"),block(1,"minecraft:stone"),show(0,"a",0),move("a",3,5),idle(2),show(1,"a",4),idle(2),block(1,"minecraft:glass"),idle(5),move("a",2,0));
        AuthoringDocument fork=AuthoringSceneForker.fork(original,"local:fork_owned",(id,asset)->fail("Empty initial structure needs no asset copy"));
        PonderScene copy=new AuthoringCompiler().compile(fork,null).get(0);
        for(float tick:new float[]{0,2,4,5,9,2,9}) for(int x=0;x<2;x++) {
            PonderSceneRuntime.RuntimeBlockState a=state(original,x,tick),b=state(copy,x,tick);
            center(b,a.renderCenterX,a.renderCenterY,a.renderCenterZ);assertEquals(a.visible,b.visible);assertEquals(a.fade,b.fade);assertEquals(a.sectionId,b.sectionId);
        }
    }
    @Test void appendingNewAuthoredSectionsAfterNativeForkCannotCollideWithReservedIdentities() throws Exception {
        PonderScene source=scene(block(0,"minecraft:stone"),show(0,"a",0));
        AuthoringDocument fork=AuthoringSceneForker.fork(source,"local:extend_owned",(id,asset)->{});
        fork=AuthoringEdits.addStep(fork,0,show(0,"new",0));fork=AuthoringEdits.addStep(fork,0,move("new",2,0));
        PonderScene copy=new AuthoringCompiler().compile(fork,null).get(0);center(state(copy,0,0),2.5,.5,.5);
    }
    @Test void unmappedLegacyEventsWithoutMetadataRetainTheirOldPerPositionMotionSemantics() {
        JsonObject legacy=block(0,"minecraft:stone");legacy.addProperty("legacyOwnership",true);
        PonderScene s=scene(legacy);s.recordSectionMove(Collections.singletonList(BlockPos.ORIGIN),new Vec3d(2,0,0),0);
        s.recordWorldEvent(PonderScene.WorldEventType.SET_BLOCK,BlockPos.ORIGIN,"minecraft:glass#0");
        assertFalse(s.hasSectionOwnership());center(state(s,0,0),2.5,.5,.5);
    }
    @Test void malformedSectionIdentityOrderAndInapplicableFlagsFailClosed() {
        JsonObject invalid=step("native_section");invalid.addProperty("nativeVersion",1);invalid.addProperty("action","MOVE");invalid.addProperty("sectionId","missing");invalid.addProperty("visible",true);invalid.add("positions",new JsonArray());invalid.add("offset",vec(1,0,0));
        assertTrue(new AuthoringCompiler().validate(doc(invalid),null).hasErrors());
        invalid.addProperty("action","PLACE");invalid.addProperty("sectionId","");assertTrue(new AuthoringCompiler().validate(doc(invalid),null).hasErrors());
        JsonObject bad=move("a",1,0);bad.addProperty("legacyOwnership",true);assertTrue(new AuthoringCompiler().validate(doc(bad),null).hasErrors());
        JsonObject rows=show(0,"a",0);rows.add("blockPos2",vec(2,0,0));rows.addProperty("entranceAnimation","east");rows.addProperty("entranceInterval",72000);
        assertTrue(new AuthoringCompiler().validate(doc(rows),null).hasErrors());
    }
    @Test void metadataBudgetsCountRepeatedPositionsEvenWhenTheyReferToTheSameCell() {
        PonderScene s=scene();List<BlockPos> positions=Collections.nCopies(262144,BlockPos.ORIGIN);
        SectionOwnershipEvent event=new SectionOwnershipEvent(Action.PLACE,"","","",false,true);
        for(int i=0;i<4;i++)s.recordSectionOwnership(event,positions,null,0,null,null,null);
        assertThrows(IllegalArgumentException.class,()->s.recordSectionOwnership(event,Collections.singletonList(BlockPos.ORIGIN),null,0,null,null,null));
    }
    @Test void metadataPositionsAreDefensivelyCopiedAndExposedAsImmutableValues() {
        PonderScene s=scene();List<BlockPos> positions=new ArrayList<>(Collections.singletonList(BlockPos.ORIGIN));
        s.recordSectionOwnership(new SectionOwnershipEvent(Action.PLACE,"","","",false,true),positions,null,0,null,null,null);positions.clear();
        PonderScene.WorldEvent event=s.getWorldEvents().get(0);assertEquals(1,event.getPositions().size());
        assertThrows(UnsupportedOperationException.class,()->event.getPositions().clear());
    }
    @Test void enormousDirectionalRangesFailBeforeAnyCoordinateEnumeration() {
        for(String type:new String[]{"show_section_and_merge","set_block"}) {
            JsonObject s=step(type);s.add("blockPos",vec(-30000000,-30000000,-30000000));s.add("blockPos2",vec(30000000,30000000,30000000));
            s.addProperty("block","minecraft:stone");s.addProperty("entranceAnimation","up");
            assertTimeout(java.time.Duration.ofSeconds(2),()->assertTrue(new AuthoringCompiler().validate(doc(s),null).hasErrors()));
        }
    }
    @Test void directEntrancePlannerEnforcesItsOwnPositionBudgetBeforeSorting() {
        assertThrows(IllegalArgumentException.class,()->ExtraStructurePlanning.groupsForPositions(
            Collections.nCopies(262145,BlockPos.ORIGIN),"up",1,0,EnumFacing.DOWN));
    }
    @Test void ownedAndLegacyMotionsRespectTheirSharedNonCommutativeEventOrder() {
        PonderScene s=scene(block(0,"minecraft:stone"),show(0,"a",0),move("a",2,0));
        String id=state(s,0,0).sectionId;
        s.recordSectionRotation(Collections.singletonList(BlockPos.ORIGIN),new Vec3d(.5,.5,.5),new Vec3d(0,90,0),0);
        s.recordSectionOwnership(new SectionOwnershipEvent(Action.MOVE,id,"","",false,true),Collections.emptyList(),null,0,new Vec3d(1,0,0),null,null);
        center(state(s,0,0),1.5,.5,-1.5);
        PonderScenePreview.PreviewBounds bounds=PonderSceneRuntime.computeBounds(s);
        assertTrue(bounds.maxX>=2 && bounds.minZ<=-2);
    }
    @Test void implicitEmptyPositionMotionsChargeTheEntireOwnedGroup() {
        SectionOwnershipBudget budget=new SectionOwnershipBudget();
        budget.add(new SectionOwnershipEvent(Action.SHOW,"a","","",false,true),8193);
        SectionOwnershipEvent motion=new SectionOwnershipEvent(Action.MOVE,"a","","",false,true);
        for(int i=0;i<127;i++)budget.add(motion,0);
        assertThrows(IllegalArgumentException.class,()->budget.add(motion,0));
    }
    @Test void futureMergedMembersRetroactivelyChargeEarlierTargetMotions() {
        SectionOwnershipBudget budget=new SectionOwnershipBudget();
        budget.add(new SectionOwnershipEvent(Action.SHOW,"a","","",false,true),1);
        for(int i=0;i<128;i++)budget.add(new SectionOwnershipEvent(Action.MOVE,"a","","",false,true),0);
        assertThrows(IllegalArgumentException.class,()->budget.add(new SectionOwnershipEvent(Action.SHOW,"b","a","",false,true),8192));
    }
    @Test void nativeImportCannotEvadeTheMotionCountOrRotationNumericBudget() {
        PonderScene source=scene(block(0,"minecraft:stone"),show(0,"a",0));
        String id=state(source,0,0).sectionId;
        JsonObject shown=step("native_section");shown.addProperty("nativeVersion",1);shown.addProperty("action","SHOW");shown.addProperty("sectionId",id);shown.addProperty("visible",true);
        JsonArray selected=new JsonArray();selected.add(vec(0,0,0));shown.add("positions",selected);
        JsonObject moved=step("native_section");moved.addProperty("nativeVersion",1);moved.addProperty("action","MOVE");moved.addProperty("sectionId",id);moved.addProperty("visible",true);moved.add("positions",new JsonArray());moved.add("offset",vec(1,0,0));
        List<JsonObject> steps=new ArrayList<>();steps.add(shown);for(int i=0;i<129;i++)steps.add(moved);
        assertTrue(new AuthoringCompiler().validate(doc(steps.toArray(new JsonObject[0])),null).hasErrors());
        JsonObject rotated=AuthoringDocument.copy(moved);rotated.addProperty("action","ROTATE");rotated.remove("offset");rotated.add("pivot",vec(0,0,0));rotated.add("rotation",vec(0,1000001,0));
        assertTrue(new AuthoringCompiler().validate(doc(shown,rotated),null).hasErrors());
    }
}
