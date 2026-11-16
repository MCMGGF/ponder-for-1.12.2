package net.createmod.ponder.foundation.ui;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Mouse;

/** Bounded, paged chooser used by local documents, history and assets. */
public final class AuthoringChoiceScreen extends AuthoringGuiScreen {
    private final GuiScreen parent;
    private final String title;
    private final List<String> allValues;
    private List<String> values;
    private final Consumer<String> chosen;
    private final boolean searchable;
    private GuiTextField search;
    private String query="";
    private int page;
    public AuthoringChoiceScreen(GuiScreen parent, String title, List<String> values, Consumer<String> chosen) {
        this(parent,title,values,chosen,false);
    }
    public AuthoringChoiceScreen(GuiScreen parent, String title, List<String> values, Consumer<String> chosen, boolean searchable) {
        this.parent=parent; this.title=title; this.allValues=new java.util.ArrayList<>(values);
        this.values=this.allValues; this.chosen=chosen; this.searchable=searchable;
    }
    private int rows() { return Math.max(1,(height-(searchable?100:76))/22); }
    @Override public void initGui() {
        syncCompatFields();
        buttonList.clear();
        int w=Math.max(32,Math.min(460,width-24)), x=(width-w)/2;
        if(searchable) {
            boolean focused=search==null || search.isFocused();
            search=new GuiTextField(1100,fontRenderer,x,30,w,18); search.setMaxStringLength(128);
            search.setText(query); search.setFocused(focused);
            String needle=query.toLowerCase(java.util.Locale.ROOT);
            values=new java.util.ArrayList<>();
            for(String value:allValues) if(value.toLowerCase(java.util.Locale.ROOT).contains(needle)) values.add(value);
        }
        page=Math.max(0,Math.min(page,Math.max(0,(values.size()-1)/rows())));
        for(int row=0;row<rows() && page*rows()+row<values.size();row++) {
            String value=values.get(page*rows()+row);
            buttonList.add(new GuiButton(row,x,(searchable?58:34)+row*22,w,20,fontRenderer.trimStringToWidth(value,w-12)));
        }
        buttonList.add(new GuiButton(1000,x,height-30,30,20,"<"));
        buttonList.add(new GuiButton(1001,x+34,height-30,30,20,">"));
        buttonList.add(new GuiButton(1002,x+w-30,height-30,30,20,"X"));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if(button.id==1000) { page--; initGui(); }
        else if(button.id==1001) { page++; initGui(); }
        else if(button.id==1002) mc.displayGuiScreen(parent);
        else { String value=values.get(page*rows()+button.id); mc.displayGuiScreen(parent); chosen.accept(value); }
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput(); int wheel=Mouse.getEventDWheel();
        if(wheel!=0) { page+=wheel>0?-1:1; initGui(); }
    }
    @Override protected void keyTyped(char c,int key) throws IOException {
        if(key==1) mc.displayGuiScreen(parent);
        else if(searchable && search.textboxKeyTyped(c,key)) {
            query=search.getText(); int cursor=search.getCursorPosition(), selection=search.getSelectionEnd();
            page=0; initGui(); search.setCursorPosition(cursor); search.setSelectionPos(selection);
        }
        else super.keyTyped(c,key);
    }
    @Override protected void mouseClicked(int x,int y,int button) throws IOException {
        if(searchable && search!=null) search.mouseClicked(x,y,button);
        super.mouseClicked(x,y,button);
    }
    @Override public void updateScreen() { if(search!=null) search.updateCursorCounter(); }
    @Override public void drawScreen(int x,int y,float partial) {
        drawDefaultBackground(); drawCenteredString(fontRenderer,title,width/2,12,0xFFFFFF);
        if(searchable) {
            search.drawTextBox();
            if(values.isEmpty()) drawCenteredString(fontRenderer,I18n.format("ponder.authoring.no_matches"),width/2,62,0xBBBBBB);
        }
        drawCenteredString(fontRenderer,(page+1)+" / "+Math.max(1,(values.size()+rows()-1)/rows()),width/2,height-23,0xBBBBBB);
        super.drawScreen(x,y,partial);
    }
}
