package net.createmod.ponder.foundation.ui;

import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/** A paged, read-only review with a separate explicit commit action. */
public final class AuthoringReviewScreen extends AuthoringGuiScreen {
    private final GuiScreen parent;
    private final String title,text;
    private final Runnable confirm;
    private List<String> lines;
    private int page;
    public AuthoringReviewScreen(GuiScreen parent,String title,String text,Runnable confirm) {
        this.parent=parent; this.title=title; this.text=text; this.confirm=confirm;
    }
    private int rows() { return Math.max(1,(height-78)/(fontRenderer.FONT_HEIGHT+3)); }
    @Override public void initGui() {
        syncCompatFields();
        lines=PreviewTextLayout.wrap(fontRenderer,text,Math.max(32,width-32));
        page=Math.max(0,Math.min(page,(Math.max(1,lines.size())-1)/rows()));
        buttonList.clear();
        buttonList.add(new GuiButton(0,12,height-30,25,20,"<")); buttonList.add(new GuiButton(1,40,height-30,25,20,">"));
        if(confirm!=null) buttonList.add(new GuiButton(2,width-164,height-30,100,20,I18n.format("ponder.authoring.confirm")));
        buttonList.add(new GuiButton(3,width-60,height-30,48,20,I18n.format("gui.cancel")));
    }
    @Override protected void actionPerformed(GuiButton b) {
        if(b.id==0 || b.id==1) { page+=b.id==0?-1:1; initGui(); }
        else { mc.displayGuiScreen(parent); if(b.id==2 && confirm!=null) confirm.run(); }
    }
    @Override protected void keyTyped(char c,int key) throws IOException { if(key==1) mc.displayGuiScreen(parent); else super.keyTyped(c,key); }
    @Override public void drawScreen(int x,int y,float partial) {
        drawDefaultBackground(); drawCenteredString(fontRenderer,title,width/2,12,0xFFFFFF);
        for(int i=0;i<rows() && page*rows()+i<lines.size();i++) drawString(fontRenderer,lines.get(page*rows()+i),16,35+i*(fontRenderer.FONT_HEIGHT+3),0xD6DFE5);
        drawCenteredString(fontRenderer,(page+1)+" / "+Math.max(1,(lines.size()+rows()-1)/rows()),width/2,height-43,0xAAAAAA);
        super.drawScreen(x,y,partial);
    }
}
