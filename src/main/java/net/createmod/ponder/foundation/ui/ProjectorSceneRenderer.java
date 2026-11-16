package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.authoring.projection.ProjectorTile;
import net.createmod.ponder.authoring.projection.ProjectionSettings;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;

/** Bounded projection renderers live only for the nearest four loaded projectors. */
public final class ProjectorSceneRenderer {
    public static final ProjectorSceneRenderer INSTANCE=new ProjectorSceneRenderer();
    public static final int MAX_PROJECTORS=4, MAX_BLOCKS=4096, MAX_DISTANCE=32;
    private final Map<BlockPos,Projection> active=new HashMap<>();
    private Object world;
    private long worldFrame,foregroundFrame=-1;
    private BlockPos foregroundPos;
    public static boolean withinBudget(PonderScene scene) {
        if ((long)scene.getWorldEvents().size() + scene.getActorEvents().size() + scene.getOverlayEvents().size()
            + scene.getCameraEvents().size() + scene.getCameraViewEvents().size() + scene.getSoundEvents().size() > 16384
            || scene.getParticleEvents().size() > 256) return false;
        Set<Integer> actors=new HashSet<>();
        for(PonderScene.ActorEvent event:scene.getActorEvents()) { actors.add(event.getActorId()); if(actors.size()>64) return false; }
        Set<BlockPos> positions=new HashSet<>(scene.getSchematic().getBlocks().keySet());
        for(PonderScene.WorldEvent e:scene.getWorldEvents()) {
            positions.addAll(e.getPositions()); if(positions.size()>MAX_BLOCKS) return false;
        }
        return positions.size()<=MAX_BLOCKS;
    }
    private static final class Projection {
        final String id, component; final int entryIndex; final long generation;
        final PonderDebugScreen renderer=new PonderDebugScreen(null,0);
        PonderScene scene; PonderScenePreview.PreviewBounds bounds; String error;
        PreviewWorldProjection screenProjection;
        ProjectorTile source;
        long frame=-1;
        float tick;
        int screenWidth,screenHeight,displayWidth,displayHeight;
        Projection(String id, String component, int entryIndex) {
            this.id=id; this.component=component; this.entryIndex=entryIndex; generation=PonderIndex.getGeneration();
            try {
                if(!component.isEmpty() && entryIndex>=0) {
                    int index=0;
                    for(Map.Entry<net.minecraft.util.ResourceLocation,StoryBoardEntry> e:PonderIndex.getSceneAccess().getRegisteredEntries()) {
                        if(!e.getKey().toString().equals(component)) continue;
                        if(index++!=entryIndex) continue;
                        List<PonderScene> result=PonderIndex.getSceneAccess().compile(Collections.singletonList(e.getValue()));
                        if(!result.isEmpty() && result.get(0).getSceneId().toString().equals(id)) scene=result.get(0);
                        break;
                    }
                } else for(Map.Entry<net.minecraft.util.ResourceLocation,StoryBoardEntry> e:PonderIndex.getSceneAccess().getRegisteredEntries()) {
                    if(e.getValue().getSchematicLocation().toString().equals(id) && "ponder_authoring".equals(e.getValue().getSchematicLocation().getNamespace())) {
                        List<PonderScene> result=PonderIndex.getSceneAccess().compile(Collections.singletonList(e.getValue()));
                        if(!result.isEmpty()) scene=result.get(0);
                        break;
                    }
                }
                if(scene==null) error="ponder.authoring.projection_missing";
                else if(!withinBudget(scene)) { scene=null; error="ponder.authoring.projection_budget"; }
                else bounds=PonderScenePreview.computeBounds(scene);
            } catch(RuntimeException | LinkageError invalid) { scene=null; bounds=null; error="ponder.authoring.projection_invalid"; }
        }
        void invalidate() { screenProjection=null; source=null; frame=-1; renderer.invalidateExternalCallouts(); }
        void close() { invalidate(); renderer.releaseProjectionResources(); }
    }
    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        Minecraft mc=Minecraft.getMinecraft();
        worldFrame++; foregroundPos=null;
        for(Projection projection:active.values()) projection.invalidate();
        if(mc.world==null || mc.player==null || !mc.isSingleplayer()) { clear(); return; }
        if(world!=mc.world) { clear(); world=mc.world; }
        List<ProjectorTile> candidates=new ArrayList<>();
        for(TileEntity tile:new ArrayList<>(mc.world.loadedTileEntityList)) {
            if(tile instanceof ProjectorTile && !tile.isInvalid() && !((ProjectorTile)tile).getSettings().sceneId.isEmpty()
                && tile.getDistanceSq(mc.player.posX,mc.player.posY,mc.player.posZ)<=MAX_DISTANCE*MAX_DISTANCE) candidates.add((ProjectorTile)tile);
        }
        candidates.sort((a,b) -> Double.compare(a.getDistanceSq(mc.player.posX,mc.player.posY,mc.player.posZ),b.getDistanceSq(mc.player.posX,mc.player.posY,mc.player.posZ)));
        Set<BlockPos> keep=new HashSet<>();
        float partial=event.getPartialTicks();
        net.minecraft.entity.Entity camera=mc.getRenderViewEntity()==null?mc.player:mc.getRenderViewEntity();
        Vec3d origin=new Vec3d(camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial,
            camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial,
            camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial);
        Vec3d eye=camera.getPositionEyes(partial);
        ScaledResolution resolution=new ScaledResolution(mc);
        for(int i=0;i<Math.min(MAX_PROJECTORS,candidates.size());i++) {
            ProjectorTile tile=candidates.get(i); BlockPos pos=tile.getPos(); keep.add(pos);
            ProjectionSettings settings=tile.getSettings(); Projection projection=active.get(pos);
            if(projection==null || !projection.id.equals(settings.sceneId) || !projection.component.equals(settings.componentId)
                || projection.entryIndex!=settings.entryIndex || projection.generation!=PonderIndex.getGeneration()) {
                if(projection!=null) projection.close(); projection=new Projection(settings.sceneId,settings.componentId,settings.entryIndex); active.put(pos,projection);
            }
            if(projection.scene==null) continue;
            PonderScene scene=projection.scene;
            PonderScenePreview.PreviewBounds b=projection.bounds;
            double cx=(b.minX+b.maxX+1)/2.0, cz=(b.minZ+b.maxZ+1)/2.0;
            Vec3d local=eye.subtract(pos.getX()+0.5,pos.getY()+1,pos.getZ()+0.5)
                .scale(1/settings.scale).rotateYaw((float)Math.toRadians(-settings.yaw)).add(cx,b.minY,cz);
            PreviewRenderState previous=new PreviewRenderState();
            int mode=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_MATRIX_MODE);
            GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW); GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(pos.getX()+0.5-origin.x,pos.getY()+1-origin.y,pos.getZ()+0.5-origin.z);
                GlStateManager.rotate((float)settings.yaw,0,1,0);
                GlStateManager.scale(settings.scale,settings.scale,settings.scale);
                GlStateManager.translate(-cx,-b.minY,-cz);
                float tick=(float)settings.tick(tile.getElapsed(partial),projection.renderer.getSceneEndTick(scene));
                projection.renderer.renderWorldProjection(scene,tick,local);
                projection.screenProjection=PreviewWorldProjection.captureNative(resolution.getScaledWidth(),resolution.getScaledHeight(),mc.displayWidth,mc.displayHeight);
                projection.source=tile; projection.frame=worldFrame; projection.tick=tick;
                projection.screenWidth=resolution.getScaledWidth(); projection.screenHeight=resolution.getScaledHeight();
                projection.displayWidth=mc.displayWidth; projection.displayHeight=mc.displayHeight;
            } catch(RuntimeException | LinkageError invalid) {
                projection.error="ponder.authoring.projection_invalid"; projection.scene=null; projection.close();
            } finally { GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW); GlStateManager.popMatrix(); previous.restore(); GlStateManager.matrixMode(mode); }
        }
        for(BlockPos key:new ArrayList<>(active.keySet())) if(!keep.contains(key)) active.remove(key).close();
    }
    public String getError(BlockPos pos) { Projection p=active.get(pos); return p==null?null:p.error; }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void overlay(net.minecraftforge.client.event.RenderGameOverlayEvent.Post event) {
        if(event.getType()!=net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.world==null) return;
        if(mc.currentScreen instanceof ProjectorReadingScreen) return;
        if(mc.currentScreen!=null) { invalidateForeground(); foregroundFrame=worldFrame; return; }
        ScaledResolution resolution=event.getResolution();
        BlockPos selected=selectForeground(resolution.getScaledWidth(),resolution.getScaledHeight());
        if(selected!=null) drawForeground(selected,resolution.getScaledWidth(),resolution.getScaledHeight());
        else { invalidateForeground(); foregroundFrame=worldFrame; }
        if(mc.objectMouseOver==null || mc.objectMouseOver.getBlockPos()==null) return;
        BlockPos pos=mc.objectMouseOver.getBlockPos();
        if(!mc.world.isBlockLoaded(pos) || !(mc.world.getTileEntity(pos) instanceof ProjectorTile)) return;
        ProjectorTile tile=(ProjectorTile)mc.world.getTileEntity(pos); String error=getError(pos);
        if(error==null && !tile.getSettings().sceneId.isEmpty() && !active.containsKey(pos)) error="ponder.authoring.projection_limit";
        if(error==null) return;
        String text=net.minecraft.client.resources.I18n.format(error);
        PreviewRenderState.runOverlay(() -> mc.fontRenderer.drawStringWithShadow(text,8,8,0xFFE6B3));
    }
    private boolean valid(Projection projection,int width,int height) {
        Minecraft mc=Minecraft.getMinecraft();
        if(projection==null || projection.scene==null || projection.bounds==null || projection.screenProjection==null
            || !freshFrame(projection.frame,worldFrame,foregroundFrame,projection.screenWidth,projection.screenHeight,width,height,
                projection.displayWidth,projection.displayHeight,mc.displayWidth,mc.displayHeight) || projection.source==null
            || mc.world==null || mc.player==null || world!=mc.world || !mc.isSingleplayer()
            || projection.generation!=PonderIndex.getGeneration()) return false;
        BlockPos pos=projection.source.getPos();
        if(projection.source.isInvalid() || !mc.world.isBlockLoaded(pos) || mc.world.getTileEntity(pos)!=projection.source
            || projection.source.getDistanceSq(mc.player.posX,mc.player.posY,mc.player.posZ)>MAX_DISTANCE*MAX_DISTANCE) return false;
        ProjectionSettings settings=projection.source.getSettings();
        return projection.id.equals(settings.sceneId) && projection.component.equals(settings.componentId) && projection.entryIndex==settings.entryIndex;
    }
    static boolean freshFrame(long captured,long current,long consumed,int capturedWidth,int capturedHeight,int width,int height,
        int capturedDisplayWidth,int capturedDisplayHeight,int displayWidth,int displayHeight) {
        return captured>=0 && captured==current && consumed!=current && width>0 && height>0
            && capturedWidth==width && capturedHeight==height && capturedDisplayWidth==displayWidth && capturedDisplayHeight==displayHeight;
    }
    private BlockPos selectForeground(int width,int height) {
        double best=Double.POSITIVE_INFINITY; BlockPos selected=null;
        for(Map.Entry<BlockPos,Projection> entry:active.entrySet()) {
            Projection projection=entry.getValue(); if(!valid(projection,width,height)) continue;
            PonderScenePreview.PreviewBounds b=projection.bounds;
            double score=projection.screenProjection.centerScore(new Vec3d((b.minX+b.maxX+1)/2D,(b.minY+b.maxY+1)/2D,(b.minZ+b.maxZ+1)/2D));
            if(score<best || score==best && selected!=null && entry.getKey().compareTo(selected)<0) { best=score; selected=entry.getKey(); }
        }
        return selected;
    }
    /** One selected projector owns the foreground per rendered world frame, never a stale/offscreen snapshot. */
    public boolean drawForeground(BlockPos pos,int width,int height) {
        return drawForeground(pos,width,height,null);
    }
    boolean drawReadingForeground(BlockPos pos,int width,int height,ProjectorReadingLayout reading) {
        Minecraft mc=Minecraft.getMinecraft();
        if(reading==null || !reading.matches(width,height,mc.displayWidth,mc.displayHeight)) {
            invalidateForeground(); return false;
        }
        return drawForeground(pos,width,height,reading);
    }
    private boolean drawForeground(BlockPos pos,int width,int height,ProjectorReadingLayout reading) {
        Projection projection=active.get(pos);
        if(!valid(projection,width,height)) { invalidateForeground(); return false; }
        PonderScenePreview.PreviewBounds b=projection.bounds;
        if(!Double.isFinite(projection.screenProjection.centerScore(new Vec3d((b.minX+b.maxX+1)/2D,(b.minY+b.maxY+1)/2D,(b.minZ+b.maxZ+1)/2D)))) {
            invalidateForeground(); return false;
        }
        for(Map.Entry<BlockPos,Projection> entry:active.entrySet()) if(!entry.getKey().equals(pos)) entry.getValue().renderer.invalidateExternalCallouts();
        foregroundFrame=worldFrame; foregroundPos=pos.toImmutable();
        try {
            PreviewRenderState.runOverlay(() -> projection.renderer.renderWorldForeground(projection.scene,projection.tick,projection.screenProjection,
                reading==null ? new PreviewDebugLayout.Rect(8,24,Math.max(1,width-16),Math.max(1,height-72)) : reading.content(),reading));
            return true;
        } catch(RuntimeException | LinkageError invalid) {
            projection.error="ponder.authoring.projection_invalid"; projection.renderer.invalidateExternalCallouts(); foregroundPos=null; return false;
        }
    }
    public boolean readingClick(BlockPos pos,int x,int y,int button) {
        Projection projection=interactive(pos); return projection!=null && projection.renderer.externalCalloutClick(x,y,button);
    }
    public boolean readingWheel(BlockPos pos,int wheel,int x,int y) {
        Projection projection=interactive(pos); return projection!=null && projection.renderer.externalCalloutWheel(wheel,x,y);
    }
    private Projection interactive(BlockPos pos) {
        Minecraft mc=Minecraft.getMinecraft(); Projection projection=active.get(pos);
        if(pos==null || !pos.equals(foregroundPos) || projection==null || projection.frame!=worldFrame || foregroundFrame!=worldFrame
            || projection.screenProjection==null || projection.source==null || projection.source.isInvalid()
            || mc.world==null || mc.player==null || world!=mc.world || projection.generation!=PonderIndex.getGeneration()
            || !mc.world.isBlockLoaded(pos) || mc.world.getTileEntity(pos)!=projection.source
            || projection.source.getDistanceSq(mc.player.posX,mc.player.posY,mc.player.posZ)>MAX_DISTANCE*MAX_DISTANCE
            || mc.displayWidth!=projection.displayWidth || mc.displayHeight!=projection.displayHeight) return null;
        ProjectionSettings settings=projection.source.getSettings();
        if(!projection.id.equals(settings.sceneId) || !projection.component.equals(settings.componentId) || projection.entryIndex!=settings.entryIndex) return null;
        return projection;
    }
    public void releaseReading(BlockPos pos) {
        Projection projection=active.get(pos); if(projection!=null) { projection.renderer.invalidateExternalCallouts(); projection.renderer.releaseProjectionResources(); }
        if(pos!=null && pos.equals(foregroundPos)) foregroundPos=null;
    }
    public void invalidateForeground() { for(Projection projection:active.values()) projection.renderer.invalidateExternalCallouts(); foregroundPos=null; }
    @SubscribeEvent public void unload(WorldEvent.Unload event) { if(event.getWorld().isRemote) clear(); }
    public void clear() { for(Projection p:active.values()) p.close(); active.clear(); world=null; foregroundPos=null; foregroundFrame=worldFrame-1; }
}
