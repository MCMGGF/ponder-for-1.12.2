package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** Pure freshness checks and source isolation guards do not claim a native-world rendering pass. */
class ProjectorForegroundContractTest {
    private boolean fresh(long captured,long current,long consumed,int w,int h,int dw,int dh) {
        return ProjectorSceneRenderer.freshFrame(captured,current,consumed,320,240,w,h,1280,960,dw,dh);
    }
    @Test void onlyOneCurrentGeometrySnapshotCanOwnAFrame() {
        assertTrue(fresh(15,15,14,320,240,1280,960));
        assertFalse(fresh(15,16,14,320,240,1280,960));
        assertFalse(fresh(15,15,15,320,240,1280,960));
        assertFalse(fresh(-1,-1,-2,320,240,1280,960));
    }
    @Test void resizeAndGuiScaleChangesCannotReuseOldClickCoordinates() {
        assertFalse(fresh(15,15,14,640,480,1280,960));
        assertFalse(fresh(15,15,14,320,240,1920,1080));
        assertFalse(fresh(15,15,14,0,240,1280,960));
    }
    @Test void captureFollowsTheActualModelTransformAndUsesTheSameTimelineTick() throws Exception {
        String source=read("ProjectorSceneRenderer.java");
        int transform=source.indexOf("GlStateManager.translate(-cx,-b.minY,-cz)");
        int geometry=source.indexOf("projection.renderer.renderWorldProjection(scene,tick,local)");
        int capture=source.indexOf("PreviewWorldProjection.captureNative(");
        assertTrue(transform>=0 && transform<geometry && geometry<capture);
        assertTrue(source.contains("projection.source=tile; projection.frame=worldFrame; projection.tick=tick"));
        assertTrue(source.contains("projection.renderer.renderWorldForeground(projection.scene,projection.tick,projection.screenProjection"));
        assertTrue(source.contains("mc.world.getTileEntity(pos)!=projection.source"));
        assertTrue(source.contains("projection.generation!=PonderIndex.getGeneration()"));
        assertTrue(source.contains("@SubscribeEvent(priority=EventPriority.LOWEST)"));
        assertTrue(source.contains("centerScore(new Vec3d("));
        assertTrue(source.contains("mc.currentScreen instanceof ProjectorReadingScreen"));
        assertFalse(source.contains("GlStateManager.clear(")); assertFalse(source.contains("mc.world ="));
    }
    @Test void readingScreenIsTransparentNonPausingAndCannotForwardWorldInteraction() throws Exception {
        String source=read("ProjectorReadingScreen.java");
        assertTrue(source.contains("doesGuiPauseGame() { return false; }"));
        assertTrue(source.contains("drawReadingForeground(pos,width,height,reading)"));
        assertTrue(source.contains("readingClick(pos,reading.localX(x),reading.localY(y),button)"));
        assertTrue(source.contains("readingWheel(pos,wheel,reading.localX(x),reading.localY(y))"));
        assertTrue(source.contains("releaseReading(pos)")); assertTrue(source.contains("if(key==1)"));
        for(String forbidden:new String[]{"drawDefaultBackground(","super.mouseClicked(","super.keyTyped(","playerController","sendPacket(","setBlockState(","openContainer"})
            assertFalse(source.contains(forbidden),forbidden);
    }
    @Test void readableForegroundCoordinatesAreScopedEvenWhenANativeCallbackThrows() throws Exception {
        PonderDebugScreen screen = new PonderDebugScreen(null,0);
        screen.width = 1280; screen.height = 720;
        ProjectorReadingLayout first = new ProjectorReadingLayout(1280,720,1280,720);
        ProjectorReadingLayout nested = new ProjectorReadingLayout(640,480,640,480);
        java.lang.reflect.Field active = PonderDebugScreen.class.getDeclaredField("foregroundReadingLayout");
        active.setAccessible(true);
        assertThrows(IllegalStateException.class, () -> screen.withForegroundReadingCanvas(first, () -> {
            assertEquals(640,screen.width); assertEquals(360,screen.height);
            try { assertSame(first,active.get(screen)); } catch (IllegalAccessException failure) { throw new AssertionError(failure); }
            assertThrows(IllegalArgumentException.class, () -> screen.withForegroundReadingCanvas(nested, () -> {
                assertEquals(320,screen.width); assertEquals(240,screen.height);
                throw new IllegalArgumentException("nested native callback fixture");
            }));
            assertEquals(640,screen.width); assertEquals(360,screen.height);
            try { assertSame(first,active.get(screen)); } catch (IllegalAccessException failure) { throw new AssertionError(failure); }
            throw new IllegalStateException("native callback fixture");
        }));
        assertEquals(1280,screen.width); assertEquals(720,screen.height);
        assertNull(active.get(screen));
    }
    @Test void readerOnlyCanvasDoesNotChangeTheOldForegroundApiOrWorldCameraPath() throws Exception {
        String renderer = read("ProjectorSceneRenderer.java"), debug = read("PonderDebugScreen.java");
        assertTrue(renderer.contains("return drawForeground(pos,width,height,null)"));
        assertTrue(renderer.contains("reading.matches(width,height,mc.displayWidth,mc.displayHeight)"));
        assertTrue(renderer.contains("reading==null ? new PreviewDebugLayout.Rect(8,24"));
        assertTrue(debug.contains("renderWorldForeground(scene, tick, projection, area, null)"));
        assertTrue(debug.contains("projection.withLogicalSize(width, height)"));
        assertTrue(debug.contains("foregroundReadingLayout.framebufferScissor("));
        String reader = read("ProjectorReadingScreen.java");
        assertTrue(reader.contains("PonderScreenOverlay.run(reading.width,reading.height"));
        assertTrue(reader.contains("layout=null; ProjectorSceneRenderer.INSTANCE.releaseReading(pos)"));
        assertFalse(reader.contains("gameSettings.guiScale"));
        assertFalse(reader.contains("GlStateManager.scale("));
    }
    @Test void readButtonUsesTheLoadedBindingBeforeParsingAnyUnsavedForm() throws Exception {
        String source=read("ProjectorConfigScreen.java");
        int action=source.indexOf("protected void actionPerformed(");
        int read=source.indexOf("if(b.id==7)",action),parse=source.indexOf("retain();",action);
        assertTrue(read>=0 && read<parse); assertTrue(source.contains("addButton(7,tr(\"projection_read\"))"));
        String branch=source.substring(read,parse);
        assertTrue(branch.contains("mc.world.isBlockLoaded(pos)")); assertTrue(branch.contains("getSettings().sceneId.isEmpty()"));
        assertTrue(branch.contains("new ProjectorReadingScreen(this,pos)")); assertFalse(branch.contains("configure("));
    }
    private static String read(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/net/createmod/ponder/foundation/ui",name)),StandardCharsets.UTF_8);
    }
}
