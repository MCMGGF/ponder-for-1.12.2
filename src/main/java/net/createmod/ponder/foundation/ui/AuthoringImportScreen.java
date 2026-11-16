package net.createmod.ponder.foundation.ui;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.AuthoringValidation;
import net.createmod.ponder.authoring.AuthoringWorkspace;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

/** Explicit data-only diff review. Confirmation creates a draft, never publishes it. */
public final class AuthoringImportScreen extends AuthoringGuiScreen {
    private final GuiScreen parent;
    private final AuthoringDocument before,after;
    private final Consumer<AuthoringDocument> confirm;
    private GuiTextField targetId;
    private AuthoringValidation lastRenderedReviewValidation;
    private String lastRenderedReviewText;
    public AuthoringImportScreen(GuiScreen parent,AuthoringDocument before,AuthoringDocument after,Consumer<AuthoringDocument> confirm) {
        this.parent=parent; this.before=before; this.after=after; this.confirm=confirm;
    }
    @Override public void initGui() {
        syncCompatFields();
        lastRenderedReviewValidation=null; lastRenderedReviewText=null;
        buttonList.clear(); int w=Math.max(60,Math.min(120,(width-30)/2));
        String previous=targetId==null?"local:import_"+Long.toString(System.currentTimeMillis(),36):targetId.getText();
        targetId=new GuiTextField(10,fontRenderer,16,32,width-32,18);
        targetId.setMaxStringLength(200);
        targetId.setText(previous);
        buttonList.add(new GuiButton(0,width/2-w-4,height-32,w,20,I18n.format("ponder.authoring.import_draft")));
        buttonList.add(new GuiButton(1,width/2+4,height-32,w,20,I18n.format("gui.cancel")));
        buttonList.add(new GuiButton(2,16,54,28,20,"D"));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if(button.id==2) {
            mc.displayGuiScreen(new AuthoringReviewScreen(this,I18n.format("ponder.authoring.import_review"),
                net.createmod.ponder.authoring.exchange.AuthoringDiff.compare(before,after),null));
            return;
        }
        if(button.id==0) {
            if(!AuthoringDocument.validId(targetId.getText())) return;
            com.google.gson.JsonObject json=after.toJson(); json.addProperty("id",targetId.getText());
            mc.displayGuiScreen(parent); confirm.accept(AuthoringDocument.fromJson(json));
        } else mc.displayGuiScreen(parent);
    }
    @Override protected void mouseClicked(int x,int y,int b) throws IOException { targetId.mouseClicked(x,y,b); super.mouseClicked(x,y,b); }
    @Override protected void keyTyped(char c,int key) throws IOException { if(key==1) mc.displayGuiScreen(parent); else if(!targetId.textboxKeyTyped(c,key)) super.keyTyped(c,key); }
    @Override public void drawScreen(int x,int y,float partial) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer,I18n.format("ponder.authoring.import_review"),width/2,12,0xFFFFFF);
        targetId.drawTextBox();
        int textWidth=Math.max(40,width-32), yy=80;
        AuthoringValidation review=AuthoringWorkspace.get().getCompiler().validateSchema(after);
        String text=before.getId()+" -> "+after.getId()+"\n"+before.getScenes().size()+" -> "+after.getScenes().size()+" "+I18n.format("ponder.authoring.segments")
            +"\nSHA256: "+after.sha256()+"\nSCHEMA_ONLY - handler parameters and installed assets require full validation before publishing\n"+review.getIssues();
        List<String> lines=PreviewTextLayout.wrap(fontRenderer,text,textWidth);
        for(String line:lines) { if(yy>height-44) break; drawString(fontRenderer,line,16,yy,0xD2D8DE); yy+=fontRenderer.FONT_HEIGHT+3; }
        lastRenderedReviewValidation=review; lastRenderedReviewText=text;
        super.drawScreen(x,y,partial);
    }
    public AuthoringValidation getLastRenderedReviewValidation() { return lastRenderedReviewValidation; }
    public String getLastRenderedReviewText() { return lastRenderedReviewText; }
}
