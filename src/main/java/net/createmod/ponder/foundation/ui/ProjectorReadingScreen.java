package net.createmod.ponder.foundation.ui;

import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Mouse;

/** A transparent, non-pausing reader. All captured input stays inside the foreground callout system. */
public final class ProjectorReadingScreen extends CompatGuiScreen {
    private final GuiScreen parent;
    private final BlockPos pos;
    private ProjectorReadingLayout layout;
    public ProjectorReadingScreen(GuiScreen parent,BlockPos pos) {
        if(pos==null) throw new IllegalArgumentException("A fixed loaded projector position is required");
        this.parent=parent; this.pos=pos.toImmutable();
    }
    public BlockPos getProjectorPos() { return pos; }
    @Override public void initGui() { syncCompatFields(); buttonList.clear(); layout=null; ProjectorSceneRenderer.INSTANCE.invalidateForeground(); }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void drawScreen(int mouseX,int mouseY,float partial) {
        ProjectorReadingLayout reading=readingLayout();
        if(!ProjectorSceneRenderer.INSTANCE.drawReadingForeground(pos,width,height,reading)) {
            PonderScreenOverlay.run(reading.width,reading.height,mc.displayWidth,mc.displayHeight,() -> {
                int y=12;
                for(String line:PreviewTextLayout.wrap(fontRenderer,I18n.format("ponder.authoring.projection_reading_unavailable"),Math.max(1,reading.width-16))) {
                    drawCenteredString(fontRenderer,line,reading.width/2,y,0xE8BB89);
                    y+=Math.max(10,fontRenderer.FONT_HEIGHT);
                }
            });
        }
    }
    @Override protected void mouseClicked(int x,int y,int button) throws IOException {
        ProjectorReadingLayout reading=readingLayout();
        ProjectorSceneRenderer.INSTANCE.readingClick(pos,reading.localX(x),reading.localY(y),button);
    }
    @Override protected void mouseClickMove(int x,int y,int button,long elapsed) { }
    @Override protected void mouseReleased(int x,int y,int button) { }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput(); int wheel=Mouse.getEventDWheel();
        if(wheel!=0) {
            int x=(int)((long)Mouse.getEventX()*width/mc.displayWidth),y=height-(int)((long)Mouse.getEventY()*height/mc.displayHeight)-1;
            ProjectorReadingLayout reading=readingLayout();
            ProjectorSceneRenderer.INSTANCE.readingWheel(pos,wheel,reading.localX(x),reading.localY(y));
        }
    }
    private ProjectorReadingLayout readingLayout() {
        if(layout==null || !layout.matches(width,height,mc.displayWidth,mc.displayHeight)) {
            layout=new ProjectorReadingLayout(width,height,mc.displayWidth,mc.displayHeight);
            ProjectorSceneRenderer.INSTANCE.invalidateForeground();
        }
        return layout;
    }
    @Override protected void keyTyped(char typed,int key) throws IOException { if(key==1) mc.displayGuiScreen(mc.world==null?null:parent); }
    @Override public void onGuiClosed() { layout=null; ProjectorSceneRenderer.INSTANCE.releaseReading(pos); }
}
