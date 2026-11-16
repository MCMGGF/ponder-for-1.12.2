package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

class AuthoringCoordinateEditsTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() { net.minecraft.init.Bootstrap.register(); }
    @Test void fieldChooserOffersOnlyCoordinatesUsedByTheSelectedStep() {
        JsonObject value=new JsonObject(); value.addProperty("type","idle");
        assertTrue(AuthoringCoordinateEdits.fields(value).isEmpty());
        value.addProperty("type","destroy_block"); assertEquals(java.util.Collections.singletonList("blockPos"),AuthoringCoordinateEdits.fields(value));
        value.addProperty("type","highlight_section"); assertTrue(AuthoringCoordinateEdits.fields(value).contains("selection"));
        value.addProperty("type","create_entity"); value.addProperty("entity","minecraft:parrot");
        assertEquals(java.util.Collections.singletonList("pos"),AuthoringCoordinateEdits.fields(value));
        value.addProperty("entity","minecraft:player"); assertTrue(AuthoringCoordinateEdits.fields(value).contains("lookAt"));
        value.addProperty("type","show_controls"); assertEquals(java.util.Collections.singletonList("point"),AuthoringCoordinateEdits.fields(value));
        value.addProperty("type","move_section"); value.addProperty("linkId","existing");
        assertTrue(AuthoringCoordinateEdits.fields(value).isEmpty());
        value.addProperty("type","rotate_section");
        assertEquals(java.util.Collections.singletonList("pivot"),AuthoringCoordinateEdits.fields(value));
        value.remove("linkId"); value.addProperty("type","create_entity"); value.addProperty("entity","minecraft:player"); value.addProperty("yaw",0);
        assertFalse(AuthoringCoordinateEdits.fields(value).contains("lookAt"));
        value.remove("yaw"); value.addProperty("nbt","{Rotation:[0f,0f]}");
        assertFalse(AuthoringCoordinateEdits.fields(value).contains("lookAt"));
    }
    private AuthoringDocument document() {
        return AuthoringDocument.parse("{\"id\":\"local:pick\",\"items\":[\"minecraft:stone\"],\"scenes\":[{\"id\":\"main\",\"steps\":[{\"type\":\"highlight_section\",\"fullScene\":true,\"positions\":[[9,9,9]],\"future\":{\"keep\":7}}]}]}");
    }
    private JsonObject step(AuthoringDocument doc) { return doc.getScenes().get(0).getAsJsonArray("steps").get(0).getAsJsonObject(); }
    @Test void pickingPointPreservesUnknownDraftDataAndOriginal() {
        AuthoringDocument original = document(); String hash = original.sha256();
        AuthoringDocument changed = AuthoringCoordinateEdits.point(original,0,0,"point",new Vec3d(1.25,2,3));
        assertEquals(hash,original.sha256()); assertFalse(step(original).has("point"));
        assertEquals(new Vec3d(1.25,2,3),AuthoringCompiler.vector(step(changed),"point",Vec3d.ZERO));
        assertEquals(7,step(changed).getAsJsonObject("future").get("keep").getAsInt());
    }
    @Test void selectedBlockReplacesConflictingFullAndExplicitSelections() {
        AuthoringDocument changed = AuthoringCoordinateEdits.block(document(),0,0,"blockPos",new BlockPos(-2,4,7));
        assertEquals(new BlockPos(-2,4,7),AuthoringCompiler.blockPos(step(changed),"blockPos"));
        assertFalse(step(changed).has("positions")); assertFalse(step(changed).has("fullScene")); assertFalse(step(changed).has("blockPos2"));
    }
    @Test void twoCornerSelectionIsOneUndoableOperation() {
        AuthoringSession session = new AuthoringSession(document()); String before = session.getDocument().sha256();
        session.replace(AuthoringCoordinateEdits.selection(session.getDocument(),0,0,new BlockPos(4,3,2),new BlockPos(1,1,1)));
        String after = session.getDocument().sha256();
        assertEquals(24,AuthoringCompiler.volume(step(session.getDocument())));
        assertTrue(session.undo()); assertEquals(before,session.getDocument().sha256()); assertFalse(session.undo());
        assertTrue(session.redo()); assertEquals(after,session.getDocument().sha256());
    }
    @Test void adjustingSecondCornerKeepsFirstAndAllNonSelectionFields() {
        AuthoringDocument first = AuthoringCoordinateEdits.block(document(),0,0,"blockPos",new BlockPos(1,2,3));
        AuthoringDocument second = AuthoringCoordinateEdits.block(first,0,0,"blockPos2",new BlockPos(4,5,6));
        assertEquals(new BlockPos(1,2,3),AuthoringCompiler.blockPos(step(second),"blockPos"));
        assertEquals(new BlockPos(4,5,6),AuthoringCompiler.blockPos(step(second),"blockPos2"));
        assertTrue(step(second).has("future"));
    }
    @Test void allAbsolutePointFieldsAreExplicitAndRelativeOffsetsAreRejected() {
        for(String key:new String[]{"point","pos","pivot","lookAt"}) assertTrue(step(AuthoringCoordinateEdits.point(document(),0,0,key,Vec3d.ZERO)).has(key));
        assertThrows(IllegalArgumentException.class,()->AuthoringCoordinateEdits.point(document(),0,0,"offset",Vec3d.ZERO));
        assertThrows(IllegalArgumentException.class,()->AuthoringCoordinateEdits.block(document(),0,0,"point",BlockPos.ORIGIN));
    }
    @Test void coordinateAndSelectionBudgetsAreNotBypassed() {
        assertThrows(IllegalArgumentException.class,()->AuthoringCoordinateEdits.point(document(),0,0,"point",new Vec3d(Double.NaN,0,0)));
        assertThrows(IllegalArgumentException.class,()->AuthoringCoordinateEdits.point(document(),0,0,"point",new Vec3d(30000001,0,0)));
        assertThrows(IllegalArgumentException.class,()->AuthoringCoordinateEdits.selection(document(),0,0,BlockPos.ORIGIN,new BlockPos(100,100,100)));
        assertThrows(IllegalArgumentException.class,()->AuthoringCoordinateEdits.selection(document(),0,0,null,BlockPos.ORIGIN));
    }
    @Test void choosingAnAbsoluteOverlayPointExplicitlyReplacesFollowingSelectionBindings() {
        AuthoringDocument original=document();
        JsonObject text=step(original); text.addProperty("type","text"); text.addProperty("text","Target");
        original=AuthoringEdits.replaceStep(original,0,0,text);
        AuthoringDocument edited=AuthoringCoordinateEdits.point(original,0,0,"point",new Vec3d(5.5,1,2));
        assertEquals(new Vec3d(5.5,1,2),AuthoringCompiler.vector(step(edited),"point",Vec3d.ZERO));
        assertFalse(step(edited).has("positions")); assertFalse(step(edited).has("fullScene"));
        assertTrue(step(original).has("positions"));
        assertEquals("scene",step(edited).get("pointSpace").getAsString());
    }
    @Test void allOverlayAbsolutePointsAreExplicitButBlockTargetsReturnToFollowingSemantics() {
        for (String type : new String[]{"text","shared_text","show_controls","show_image","show_interface"}) {
            AuthoringDocument original=document(); JsonObject value=step(original); value.addProperty("type",type);
            original=AuthoringEdits.replaceStep(original,0,0,value);
            AuthoringDocument absolute=AuthoringCoordinateEdits.point(original,0,0,"point",new Vec3d(1.5,2,3));
            assertEquals("scene",step(absolute).get("pointSpace").getAsString());
            assertFalse(step(absolute).has("positions"));
            assertFalse(step(AuthoringCoordinateEdits.block(absolute,0,0,"blockPos",BlockPos.ORIGIN)).has("pointSpace"));
            assertFalse(step(AuthoringCoordinateEdits.selection(absolute,0,0,BlockPos.ORIGIN,new BlockPos(1,1,1))).has("pointSpace"));
        }
        for (String type : new String[]{"show_image","show_interface"}) {
            JsonObject value=new JsonObject(); value.addProperty("type",type);
            assertTrue(AuthoringCoordinateEdits.fields(value).contains("blockPos"));
            assertTrue(AuthoringCoordinateEdits.fields(value).contains("point"));
        }
    }
    @Test void graphicallyBindingIndependentCaptionsReplacesTheirPreviousPositionWithOneUndoableEdit() {
        for (String type : new String[]{"text","shared_text"}) for (int y : new int[]{0,100}) {
            AuthoringDocument original=AuthoringDocument.parse("{\"id\":\"local:caption_pick\",\"items\":[\"minecraft:stone\"],\"scenes\":[{\"id\":\"main\",\"steps\":[{\"type\":\""
                +type+"\",\"text\":\"Caption\",\"independentY\":"+y+",\"future\":{\"keep\":7}}]}]}");
            String before=original.sha256();
            for (int mode=0;mode<3;mode++) {
                AuthoringDocument edited=mode==0 ? AuthoringCoordinateEdits.point(original,0,0,"point",new Vec3d(1.5,2,3))
                    : mode==1 ? AuthoringCoordinateEdits.block(original,0,0,"blockPos",BlockPos.ORIGIN)
                    : AuthoringCoordinateEdits.selection(original,0,0,BlockPos.ORIGIN,new BlockPos(1,0,0));
                JsonObject value=step(edited);
                assertFalse(value.has("independentY"));
                assertEquals(7,value.getAsJsonObject("future").get("keep").getAsInt());
                net.createmod.ponder.foundation.PonderScene.OverlayEvent event=new AuthoringCompiler().compile(edited,null).get(0).getOverlayEvents().get(0);
                assertEquals(-1,event.getIndependentY());
                assertEquals(mode==0,event.isScenePoint());
                assertEquals(mode==0 ? java.util.Collections.emptyList() : mode==1 ? java.util.Collections.singletonList(BlockPos.ORIGIN)
                    : java.util.Arrays.asList(BlockPos.ORIGIN,new BlockPos(1,0,0)),event.getTargetPositions());
                AuthoringSession session=new AuthoringSession(original);session.replace(edited);
                assertTrue(session.undo());assertEquals(before,session.getDocument().sha256());assertFalse(session.undo());
                assertTrue(session.redo());assertEquals(edited.sha256(),session.getDocument().sha256());
                assertEquals(before,original.sha256());
            }
        }
    }
    @Test void selectingAFollowingOverlayTargetClearsTheOldAbsolutePointButOtherPointFieldsDoNotMoveCaptions() {
        AuthoringDocument original=AuthoringDocument.parse("{\"id\":\"local:point_override\",\"items\":[\"minecraft:stone\"],\"scenes\":[{\"id\":\"main\",\"steps\":[{\"type\":\"text\",\"text\":\"Caption\",\"point\":[5,5,5],\"pointSpace\":\"scene\"}]}]}");
        for (AuthoringDocument edited : new AuthoringDocument[]{AuthoringCoordinateEdits.block(original,0,0,"blockPos",BlockPos.ORIGIN),
            AuthoringCoordinateEdits.selection(original,0,0,BlockPos.ORIGIN,new BlockPos(1,0,0))}) {
            assertFalse(step(edited).has("point"));assertFalse(step(edited).has("pointSpace"));
            assertFalse(new AuthoringCompiler().compile(edited,null).get(0).getOverlayEvents().get(0).isScenePoint());
        }
        JsonObject caption=step(original);caption.addProperty("independentY",100);
        original=AuthoringEdits.replaceStep(original,0,0,caption);
        for(String key:new String[]{"pos","pivot","lookAt"}) {
            JsonObject edited=step(AuthoringCoordinateEdits.point(original,0,0,key,Vec3d.ZERO));
            assertEquals(100,edited.get("independentY").getAsInt());assertTrue(edited.has("pointSpace"));
        }
    }
}
