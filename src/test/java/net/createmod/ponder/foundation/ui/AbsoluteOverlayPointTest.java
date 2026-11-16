package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Collections;
import java.util.List;
import com.google.gson.JsonObject;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.authoring.AuthoringCompiler;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.AuthoringSceneForker;
import net.createmod.ponder.authoring.exchange.RestrictedPonderJsCodec;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AbsoluteOverlayPointTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    private static AuthoringDocument document(String steps) {
        return AuthoringDocument.parse("{\"id\":\"local:absolute_point\",\"items\":[\"minecraft:stone\"],\"scenes\":[{\"id\":\"main\",\"autoReveal\":false,\"autoKeyframes\":false,\"steps\":["+steps+"]}]}");
    }
    private static PonderScene scene(String steps) { return new AuthoringCompiler().compile(document(steps),null).get(0); }
    private static final String MOVED = "{\"type\":\"set_block\",\"block\":\"minecraft:stone\",\"blockPos\":[0,0,0]},"
        +"{\"type\":\"set_block\",\"block\":\"minecraft:stone\",\"blockPos\":[1,0,0]},"
        +"{\"type\":\"show_section_and_merge\",\"linkId\":\"a\",\"blockPos\":[0,0,0],\"duration\":0},"
        +"{\"type\":\"show_section_and_merge\",\"linkId\":\"b\",\"blockPos\":[1,0,0],\"duration\":0},"
        +"{\"type\":\"move_section\",\"linkId\":\"a\",\"offset\":[1,0,0],\"duration\":0},"
        +"{\"type\":\"move_section\",\"linkId\":\"b\",\"offset\":[5,0,0],\"duration\":0}";

    @Test void aPickedDisplayedPointDoesNotAcquireTheOtherMovedSourceBlock() {
        PonderScene scene=scene(MOVED+",{\"type\":\"text\",\"text\":\"absolute\",\"point\":[1.5,0.5,0.5],\"pointSpace\":\"scene\"},"
            +"{\"type\":\"text\",\"text\":\"legacy\",\"point\":[1.5,0.5,0.5]}");
        PonderScene.OverlayEvent absolute=scene.getOverlayEvents().get(0), legacy=scene.getOverlayEvents().get(1);
        assertEquals(1.5,PonderSceneRuntime.buildState(scene,0).blocksByPosition.get(BlockPos.ORIGIN).renderCenterX,1E-9);
        for(float tick:new float[]{0,20,0}) {
            assertEquals(new Vec3d(1.5,.5,.5),PonderSceneRuntime.overlayPointAtTick(scene,absolute,tick));
            assertEquals(new Vec3d(6.5,.5,.5),PonderSceneRuntime.overlayPointAtTick(scene,legacy,tick));
        }
        assertTrue(absolute.isScenePoint());assertTrue(absolute.getTargetPositions().isEmpty());
        List<PreviewCalloutContent.Group> groups=PreviewCalloutContent.collect(scene,0,key->key);
        assertEquals(2,groups.size());assertTrue(groups.get(0).scenePoint);assertFalse(groups.get(1).scenePoint);
    }

    @Test void equalCoordinatesInDifferentSpacesCannotShareAnInformationBox() {
        PonderScene scene=scene("");Vec3d point=new Vec3d(1.5,.5,.5);
        scene.createOverlayEvent(100).text("source").pointAt(point).targetPositions(Collections.emptyList());
        scene.createOverlayEvent(100).text("scene").pointAtScene(point);
        scene.createOverlayEvent(100).controls(point,Pointing.RIGHT).pointAtScene(point).controlRightClick();
        List<PreviewCalloutContent.Group> groups=PreviewCalloutContent.collect(scene,0,key->key);
        assertEquals(2,groups.size());assertEquals(1,groups.get(0).events.size());assertEquals(2,groups.get(1).events.size());
    }

    @Test void sourceBindingAndIndependentCaptionExplicitlyClearTheNewSpaceFlag() {
        PonderScene.OverlayEvent event=scene("").createOverlayEvent(20).pointAtScene(new Vec3d(1,2,3));
        event.pointAt(new Vec3d(2,3,4));assertFalse(event.isScenePoint());assertEquals(Collections.singletonList(new BlockPos(2,3,4)),event.getTargetPositions());
        event.pointAtScene(new Vec3d(1,2,3)).targetPositions(Collections.singletonList(BlockPos.ORIGIN));assertFalse(event.isScenePoint());
        event.pointAtScene(new Vec3d(1,2,3)).independent(5);assertFalse(event.isScenePoint());assertNull(event.getPointAt());
        assertThrows(IllegalArgumentException.class,()->event.pointAtScene(new Vec3d(Double.NaN,0,0)));
    }

    @Test void textControlsAndStaticGuiForkRetainScenePointsAndNeverInventSourceTargets() throws Exception {
        PonderScene source=scene(MOVED);
        source.createOverlayEvent(20).text("scene").pointAtScene(new Vec3d(1.5,.5,.5));
        source.createOverlayEvent(20).controls(new Vec3d(1.5,.5,.5),Pointing.RIGHT).pointAtScene(new Vec3d(1.5,.5,.5)).controlRightClick();
        source.createOverlayEvent(20).guiTexture(new ResourceLocation("minecraft:textures/gui/container/furnace.png"),0,0,176,76,256,256,176,76,0,0,false)
            .pointAtScene(new Vec3d(1.5,.5,.5)).overlayId("gui");
        source.createOverlayEvent(20).text("child").parentOverlayId("gui").pointAtScene(new Vec3d(1.5,.5,.5));
        AuthoringDocument fork=AuthoringSceneForker.fork(source,"local:absolute_fork",(id,asset)->fail("Empty schematic"));
        PonderScene copy=new AuthoringCompiler().compile(fork,null).get(0);
        assertEquals(4,copy.getOverlayEvents().size());
        for(PonderScene.OverlayEvent event:copy.getOverlayEvents()) {
            assertTrue(event.isScenePoint());assertTrue(event.getTargetPositions().isEmpty());
            assertEquals(new Vec3d(1.5,.5,.5),PonderSceneRuntime.overlayPointAtTick(copy,event,0));
        }
        List<PreviewCalloutContent.Group> groups=PreviewCalloutContent.collect(copy,0,key->key);
        assertEquals(1,groups.size());assertTrue(groups.get(0).scenePoint);assertEquals(4,groups.get(0).events.size());
    }

    @Test void invalidOrMisplacedSpaceMetadataFailsClosed() {
        for(String extra:new String[]{"\"pointSpace\":null","\"pointSpace\":true","\"pointSpace\":\"unknown\"",
            "\"pointSpace\":\"scene\",\"blockPos\":[1,0,0]","\"pointSpace\":\"scene\",\"positions\":[[1,0,0]]",
            "\"pointSpace\":\"scene\",\"independentY\":5"})
            assertTrue(new AuthoringCompiler().validate(document("{\"type\":\"text\",\"text\":\"x\",\"point\":[1,0,0],"+extra+"}"),null).hasErrors(),extra);
        assertTrue(new AuthoringCompiler().validate(document("{\"type\":\"text\",\"text\":\"x\",\"pointSpace\":\"scene\"}"),null).hasErrors());
        assertTrue(new AuthoringCompiler().validate(document("{\"type\":\"idle\",\"pointSpace\":\"scene\",\"point\":[0,0,0]}"),null).hasErrors());
    }

    @Test void nativeGuiImportCannotCombineAbsolutePointWithSourceTargetPositions() throws Exception {
        PonderScene source=scene("");
        source.createOverlayEvent(20).guiTexture(new ResourceLocation("minecraft:textures/gui/container/furnace.png"),0,0,176,76,256,256,176,76,0,0,false)
            .pointAtScene(new Vec3d(1.5,.5,.5));
        JsonObject root=AuthoringSceneForker.fork(source,"local:invalid_point",(id,asset)->fail("Empty schematic")).toJson();
        JsonObject overlay=null;
        for(com.google.gson.JsonElement value:root.getAsJsonArray("scenes").get(0).getAsJsonObject().getAsJsonArray("steps"))
            if("native_overlay".equals(AuthoringDocument.string(value.getAsJsonObject(),"type",""))) overlay=value.getAsJsonObject().getAsJsonObject("overlay");
        assertNotNull(overlay);com.google.gson.JsonArray positions=new com.google.gson.JsonArray();
        positions.add(new com.google.gson.JsonParser().parse("[1,0,0]"));overlay.add("targetPositions",positions);
        assertTrue(new AuthoringCompiler().validate(AuthoringDocument.fromJson(root),null).hasErrors());
    }

    @Test void restrictedJsCannotSilentlyEraseAbsolutePointSemantics() {
        AuthoringDocument doc=document("{\"type\":\"text\",\"text\":\"x\",\"point\":[1,0,0],\"pointSpace\":\"scene\"}");
        JsonObject root=doc.toJson();JsonObject segment=root.getAsJsonArray("scenes").get(0).getAsJsonObject();segment.remove("autoReveal");segment.remove("autoKeyframes");
        RestrictedPonderJsCodec.ExportResult result=RestrictedPonderJsCodec.exportText(AuthoringDocument.fromJson(root));
        assertNull(result.text);assertTrue(result.validation.hasErrors());
    }
}
