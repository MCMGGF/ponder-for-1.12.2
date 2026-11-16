package net.createmod.ponder.foundation.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.createmod.ponder.authoring.projection.ProjectionSettings;
import net.createmod.ponder.authoring.projection.ProjectorTile;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.client.config.GuiUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public final class ProjectorConfigScreen extends CompatGuiScreen {
    private final BlockPos pos;
    private ProjectionSettings value;
    private GuiTextField scene,speed,yaw,scale;
    private boolean paused,loop,restart;
    private ProjectionSettings.Trigger trigger;
    private String status="";
    private String componentId;
    private int entryIndex;
    private ProjectorConfigLayout layout;
    private final Map<Integer,String> buttonLabels=new LinkedHashMap<>();
    public ProjectorConfigScreen(BlockPos pos,ProjectionSettings settings) {
        this.pos=pos.toImmutable(); value=settings; componentId=settings.componentId; entryIndex=settings.entryIndex; paused=settings.paused; loop=settings.loop; trigger=settings.trigger;
    }
    private String tr(String key) { return I18n.format("ponder.authoring."+key); }
    @Override public void initGui() {
        syncCompatFields();
        Keyboard.enableRepeatEvents(true); buttonList.clear(); buttonLabels.clear();
        layout=new ProjectorConfigLayout(width,height,mc.displayWidth,mc.displayHeight);
        scene=field(0,scene==null?value.sceneId:scene.getText());
        speed=field(1,speed==null?Double.toString(value.speed):speed.getText());
        yaw=field(2,yaw==null?Double.toString(value.yaw):yaw.getText());
        scale=field(3,scale==null?Double.toString(value.scale):scale.getText());
        addButton(0,tr(paused?"paused":"playing"));
        addButton(1,tr(loop?"loop_on":"loop_off"));
        addButton(2,tr("trigger_"+trigger.name().toLowerCase(java.util.Locale.ROOT)));
        addButton(3,tr("projection_choose"));
        addButton(4,tr("projection_restart"));
        addButton(7,tr("projection_read"));
        addButton(5,tr("apply"));
        addButton(6,I18n.format("gui.cancel"));
    }
    private GuiTextField field(int id,String text) {
        PreviewDebugLayout.Rect bounds=layout.field(id);
        GuiTextField f=new GuiTextField(id,fontRenderer,bounds.x,bounds.y,bounds.width,bounds.height);
        f.setMaxStringLength(id==0?256:24); f.setText(text); return f;
    }
    private void addButton(int id,String label) {
        PreviewDebugLayout.Rect bounds=layout.button(id);
        buttonLabels.put(id,label);
        buttonList.add(new GuiButton(id,bounds.x,bounds.y,bounds.width,bounds.height,
            fontRenderer.trimStringToWidth(label,Math.max(1,bounds.width-8))));
    }
    public int getPresentationScale() { return layout==null?1:layout.scale; }
    private void retain() { value=new ProjectionSettings(scene.getText(),componentId,entryIndex,paused,loop,Double.parseDouble(speed.getText()),Double.parseDouble(yaw.getText()),Double.parseDouble(scale.getText()),trigger); }
    @Override protected void actionPerformed(GuiButton b) {
        if(b.id==6) { mc.displayGuiScreen(null); return; }
        if(b.id==7) {
            if(!mc.isSingleplayer() || mc.world==null || !mc.world.isBlockLoaded(pos) || !(mc.world.getTileEntity(pos) instanceof ProjectorTile)
                || ((ProjectorTile)mc.world.getTileEntity(pos)).getSettings().sceneId.isEmpty()) { status=tr("projection_unavailable"); return; }
            mc.displayGuiScreen(new ProjectorReadingScreen(this,pos)); return;
        }
        try {
            retain();
            if(b.id==0) { paused=!paused; initGui(); }
            else if(b.id==1) { loop=!loop; initGui(); }
            else if(b.id==2) { trigger=ProjectionSettings.Trigger.values()[(trigger.ordinal()+1)%3]; initGui(); }
            else if(b.id==3) {
                List<String> ids=new ArrayList<>();
                PonderIndex.getSceneAccess().getRegisteredEntries().forEach(e -> { String id=e.getKey().toString(); if(!ids.contains(id)) ids.add(id); });
                java.util.Collections.sort(ids);
                mc.displayGuiScreen(new AuthoringChoiceScreen(this,tr("projection_scene"),ids,id -> {
                    try {
                        java.util.Map<String,net.createmod.ponder.foundation.PonderScene> choices=new java.util.LinkedHashMap<>();
                        for(net.createmod.ponder.foundation.PonderScene s:PonderIndex.getSceneAccess().compile(new net.minecraft.util.ResourceLocation(id)))
                            choices.put(s.getSceneId().toString(),s);
                        mc.displayGuiScreen(new AuthoringChoiceScreen(this,tr("projection_scene"),new ArrayList<>(choices.keySet()),selected -> {
                            componentId=id;
                            java.util.List<net.createmod.ponder.api.registration.StoryBoardEntry> entries=new ArrayList<>();
                            PonderIndex.getSceneAccess().getRegisteredEntries().forEach(e -> { if(e.getKey().toString().equals(id)) entries.add(e.getValue()); });
                            entryIndex=entries.indexOf(choices.get(selected).getEntry());
                            value=new ProjectionSettings(selected,id,entryIndex,paused,loop,value.speed,value.yaw,value.scale,trigger); scene=null; initGui();
                        }));
                    } catch(RuntimeException invalid) { status=invalid.getMessage(); }
                }));
            } else if(b.id==4) { restart=true; status=tr("restart_pending"); }
            else if(b.id==5) apply();
            else if(b.id==6) mc.displayGuiScreen(null);
        } catch(RuntimeException invalid) { status=invalid.getMessage(); }
    }
    private void apply() {
        if(!mc.isSingleplayer() || mc.player==null || mc.world==null) { status=tr("singleplayer"); return; }
        final IntegratedServer server=mc.getIntegratedServer(); final UUID player=mc.player.getUniqueID();
        final int dimension=mc.player.dimension; final ProjectionSettings next=value; final boolean reset=restart;
        server.addScheduledTask(() -> {
            EntityPlayerMP owner=server.getPlayerList().getPlayerByUUID(player); WorldServer world=server.getWorld(dimension);
            if(owner==null || owner.dimension!=dimension || world==null || owner.getDistanceSq(pos)>64 || !world.isBlockLoaded(pos)) {
                mc.addScheduledTask(() -> status=tr("projection_unavailable")); return;
            }
            TileEntity tile=world.getTileEntity(pos);
            if(!(tile instanceof ProjectorTile)) { mc.addScheduledTask(() -> status=tr("projection_unavailable")); return; }
            ((ProjectorTile)tile).configure(next,reset);
            mc.addScheduledTask(() -> mc.displayGuiScreen(null));
        });
    }
    @Override protected void mouseClicked(int x,int y,int b) throws IOException {
        int localX=layout.localX(x),localY=layout.localY(y);
        for(GuiTextField f:new GuiTextField[]{scene,speed,yaw,scale}) f.mouseClicked(localX,localY,b);
        super.mouseClicked(localX,localY,b);
    }
    @Override protected void mouseClickMove(int x,int y,int b,long elapsed) {
        super.mouseClickMove(layout.localX(x),layout.localY(y),b,elapsed);
    }
    @Override protected void mouseReleased(int x,int y,int b) {
        super.mouseReleased(layout.localX(x),layout.localY(y),b);
    }
    @Override protected void keyTyped(char c,int key) throws IOException { for(GuiTextField f:new GuiTextField[]{scene,speed,yaw,scale}) if(f.textboxKeyTyped(c,key)) return; super.keyTyped(c,key); }
    @Override public void drawScreen(int x,int y,float partial) {
        drawDefaultBackground();
        PreviewRenderState.runOverlay(() -> {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            try {
                GlStateManager.scale(layout.scale,layout.scale,1);
                drawForm(layout.localX(x),layout.localY(y),partial);
            } finally {
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                GlStateManager.popMatrix();
            }
        });
    }
    private void drawForm(int x,int y,float partial) {
        PreviewTextLayout title=PreviewTextLayout.centered(fontRenderer,tr("projector"),12,layout.width-12);
        drawString(fontRenderer,title.text,title.x,12,0xFFFFFF);
        drawString(fontRenderer,tr("projection_scene"),layout.left,25,0xBBBBBB);
        drawString(fontRenderer,tr("speed"),layout.field(1).x,63,0xBBBBBB);
        drawString(fontRenderer,tr("yaw"),layout.field(2).x,63,0xBBBBBB);
        drawString(fontRenderer,tr("scale"),layout.field(3).x,63,0xBBBBBB);
        for(GuiTextField f:new GuiTextField[]{scene,speed,yaw,scale}) f.drawTextBox();
        String message=status==null?"":status;
        drawString(fontRenderer,fontRenderer.trimStringToWidth(message,layout.width-20),10,layout.height-18,0xE8BB89);
        super.drawScreen(x,y,partial);
        for(GuiButton button:buttonList) {
            String label=buttonLabels.get(button.id);
            if(button.visible && label!=null && layout.button(button.id).contains(x,y) && !label.equals(button.displayString)) {
                GuiUtils.drawHoveringText(Collections.singletonList(label),x,y,layout.width,layout.height,-1,fontRenderer);
                return;
            }
        }
        if(!message.isEmpty() && x>=10 && x<layout.width-10 && y>=layout.height-20 && y<layout.height-6) {
            GuiUtils.drawHoveringText(Collections.singletonList(message),x,y,layout.width,layout.height,-1,fontRenderer);
        }
    }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
}
