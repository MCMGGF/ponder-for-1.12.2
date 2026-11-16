package net.createmod.ponder.foundation.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.AuthoringCoordinateEdits;
import net.createmod.ponder.authoring.AuthoringEdits;
import net.createmod.ponder.authoring.AuthoringSession;
import net.createmod.ponder.authoring.AuthoringValidation;
import net.createmod.ponder.authoring.AuthoringWorkspace;
import net.createmod.ponder.authoring.practice.PracticeMode;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Local forms edit immutable drafts; only the explicit publish command changes the scene index. */
public final class AuthoringEditorScreen extends AuthoringGuiScreen {
    private final GuiScreen parent;
    private final AuthoringWorkspace workspace;
    private AuthoringSession session;
    private AuthoringEditorLayout layout;
    private int scene, step, pane, listOffset, propertyPage;
    private int sceneOffset;
    private boolean sceneProperties;
    private boolean documentProperties;
    private boolean diagnosticOnly;
    private final java.util.Set<String> readOnlyFields = new java.util.HashSet<>();
    private String formSource="";
    private final Map<String,GuiTextField> fields = new LinkedHashMap<>();
    private final Map<Integer,String> tooltips = new LinkedHashMap<>();
    private final Map<Integer,String> propertyTools = new LinkedHashMap<>();
    private GuiTextField newKey, newValue;
    private final PonderDebugScreen renderer = new PonderDebugScreen(null,0);
    private final PreviewSoundPlayback previewAudio = new PreviewSoundPlayback();
    private boolean audioFromStart = true;
    private List<PonderScene> compiled = Collections.emptyList();
    private String compiledHash = "", status = "";
    private int tick;
    private boolean playing, dragging;
    private int lastX,lastY;
    private float yaw=35,pitch=30,zoom=1;
    private String pickField, pickDocumentHash;
    private AuthoringSession pickSession;
    private int pickScene, pickStep;
    private BlockPos pickFirst, pickedMarker;
    private int pickReturnPane;
    private boolean pickFallback;

    public AuthoringEditorScreen(GuiScreen parent) {
        this.parent=parent; workspace=AuthoringWorkspace.get();
        session=new AuthoringSession(AuthoringEdits.empty("local:lesson_"+Long.toString(System.currentTimeMillis(),36)));
    }
    public static AuthoringEditorScreen fork(GuiScreen parent,PonderScene source) throws IOException {
        AuthoringEditorScreen editor=new AuthoringEditorScreen(parent);
        editor.session=new AuthoringSession(net.createmod.ponder.authoring.AuthoringSceneForker.fork(source,
            "local:copy_"+Long.toString(System.currentTimeMillis(),36),editor.workspace.getAssets()));
        return editor;
    }
    private String tr(String key) { return I18n.format("ponder.authoring."+key); }
    private void tool(int id,int x,int y,int w,String label,String hint) {
        buttonList.add(new GuiButton(id,x,y,w,20,label)); tooltips.put(id,tr(hint));
    }
    @Override public void initGui() {
        syncCompatFields();
        renderer.invalidateExternalPreview(); dragging=false;
        Keyboard.enableRepeatEvents(true);
        layout=new AuthoringEditorLayout(width,height,pane);
        compiledHash="";
        diagnosticOnly=!hasEditableShape(session.getDocument());
        if(diagnosticOnly) { documentProperties=true; sceneProperties=false; }
        else {
            scene=Math.max(0,Math.min(scene,session.getDocument().getScenes().size()-1));
            step=Math.max(0,Math.min(step,steps().size()-1));
        }
        String source=session.getDocument().getId()+"/"+scene+"/"+step+"/"+documentProperties+"/"+sceneProperties+"/"+currentObject();
        Map<String,String> pending=new LinkedHashMap<>();
        boolean restore=formSource.equals(source);
        if(restore) for(Map.Entry<String,GuiTextField> f:fields.entrySet()) pending.put(f.getKey(),f.getValue().getText());
        String pendingKey=restore && newKey!=null?newKey.getText():"";
        String pendingValue=restore && newValue!=null?newValue.getText():"";
        buttonList.clear(); fields.clear(); tooltips.clear(); propertyTools.clear(); readOnlyFields.clear();
        String[] labels={"+","D","S","<",">","P",">|","{ }","[ ]","H","A","X"};
        String[] tips={"new","documents","save","undo","redo","publish","preview","import","export","history","assets","close"};
        int bw=Math.max(20,Math.min(30,(width-16)/12));
        for(int i=0;i<labels.length;i++) tool(i,8+i*bw,24,bw-1,labels[i],tips[i]);
        tool(71,width-27,3,20,"!","validation");
        tool(72,width-49,3,20,"?","capabilities");
        if(layout.compact) for(int i=0;i<3;i++)
            tool(30+i,8+i*(width-16)/3,45,(width-16)/3-2,tr(i==0?"list":i==1?"preview":"properties"),i==0?"list":i==1?"preview":"properties");
        if(layout.list.width>0) {
            int x=layout.list.x, bottom=layout.list.y+layout.list.height;
            tool(40,x,bottom-22,24,"+","add_scene"); tool(41,x+26,bottom-22,24,"-","remove_scene");
            tool(42,x+52,bottom-22,24,"^","move_up"); tool(43,x+78,bottom-22,24,"v","move_down");
            tool(44,x+104,bottom-22,24,"E","scene_properties");
        }
        if(layout.preview.width>0) {
            int x=layout.preview.x,y=layout.preview.y+layout.preview.height-22;
            tool(50,x,y,24,"|<","start"); tool(51,x+26,y,24,playing?"||":">","play");
            tool(52,x+52,y,24,">|","next_step"); tool(53,x+78,y,24,"C","camera");
            tool(54,x+104,y,24,"T","practice");
            tool(55,x+130,y,24,"+","pick");
            tool(56,x+156,y,24,"[]","capture");
        }
        buildProperties();
        if(restore) for(Map.Entry<String,GuiTextField> f:fields.entrySet())
            if(pending.containsKey(f.getKey()) && !readOnlyFields.contains(f.getKey())) f.getValue().setText(pending.get(f.getKey()));
        if(newKey!=null) { newKey.setText(pendingKey); newValue.setText(pendingValue); }
        formSource=source;
    }
    static boolean hasEditableShape(AuthoringDocument document) {
        JsonObject json=document.toJson();
        if(!json.has("scenes") || !json.get("scenes").isJsonArray() || json.getAsJsonArray("scenes").size()==0) return false;
        for(JsonElement value:json.getAsJsonArray("scenes")) {
            if(!value.isJsonObject()) return false;
            JsonElement steps=value.getAsJsonObject().get("steps");
            if(steps==null || !steps.isJsonArray()) return false;
            for(JsonElement step:steps.getAsJsonArray()) if(!step.isJsonObject()) return false;
        }
        return true;
    }
    private JsonObject currentScene() { return session.getDocument().getScenes().get(scene); }
    private JsonArray steps() { return diagnosticOnly?new JsonArray():currentScene().getAsJsonArray("steps"); }
    private JsonObject currentObject() {
        if(documentProperties) return session.getDocument().toJson();
        return sceneProperties || steps().size()==0 ? currentScene() : steps().get(step).getAsJsonObject();
    }
    private void buildProperties() {
        AuthoringEditorLayout.Rect p=layout.properties;
        newKey=null; newValue=null;
        if(p.width<=0) return;
        int x=p.x+3,w=p.width-6,y=p.y+17;
        List<String> keys=new ArrayList<>(currentObject().keySet());
        if(documentProperties && !diagnosticOnly) keys.remove("scenes");
        else if(sceneProperties) keys.remove("steps");
        int count=Math.max(1,(p.height-100)/34);
        propertyPage=Math.max(0,Math.min(propertyPage,Math.max(0,(keys.size()-1)/count)));
        for(int i=propertyPage*count;i<keys.size() && i<(propertyPage+1)*count;i++) {
            String key=keys.get(i); JsonElement value=currentObject().get(key);
            boolean picker=!documentProperties && !sceneProperties && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && java.util.Arrays.asList("block","item","entity","sound","particle","nbt").contains(key);
            GuiTextField field=new GuiTextField(i,fontRenderer,x,y+11,w-(picker?22:0),18); field.setMaxStringLength(65536);
            String text=value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()?value.getAsString():value.toString();
            field.setText(text);
            if(text.length()>65536) { readOnlyFields.add(key); field.setEnabled(false); }
            fields.put(key,field);
            if(picker && !readOnlyFields.contains(key)) {
                int id=200+i; propertyTools.put(id,key); tool(id,x+w-20,y+11,20,"...","choose_value");
            }
            y+=34;
        }
        int bottom=p.y+p.height;
        newKey=new GuiTextField(900,fontRenderer,x,bottom-72,Math.max(28,w/3),18);
        newValue=new GuiTextField(901,fontRenderer,x+w/3+3,bottom-72,Math.max(28,w-w/3-3),18);
        newKey.setMaxStringLength(64); newValue.setMaxStringLength(65536);
        tool(60,x,bottom-50,28,"<","previous_page"); tool(61,x+30,bottom-50,28,">","next_page");
        tool(62,x+60,bottom-50,Math.max(32,w-60),tr("apply"),"apply");
        tool(63,x,bottom-26,28,"+","add_step"); tool(64,x+30,bottom-26,28,"-","remove_step");
        tool(65,x+60,bottom-26,28,"^","move_up"); tool(66,x+90,bottom-26,28,"v","move_down");
    }
    private boolean applyFields() {
        if(fields.isEmpty() && (newKey==null || newKey.getText().trim().isEmpty())) return true;
        try {
            JsonObject changed=AuthoringDocument.copy(currentObject());
            for(Map.Entry<String,GuiTextField> field:fields.entrySet()) {
                if(readOnlyFields.contains(field.getKey())) continue;
                JsonElement old=changed.get(field.getKey()); String value=field.getValue().getText();
                if(old.isJsonPrimitive() && old.getAsJsonPrimitive().isString()) changed.addProperty(field.getKey(),value);
                else changed.add(field.getKey(),parseField(value));
            }
            if(newKey!=null && !newKey.getText().trim().isEmpty()) {
                String key=newKey.getText().trim();
                if(!key.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}") || "steps".equals(key) || "scenes".equals(key)) throw new IllegalArgumentException(tr("bad_key"));
                changed.add(key,parseField(newValue.getText()));
            }
            if(documentProperties) {
                AuthoringDocument replacement=AuthoringDocument.fromJson(changed);
                if(!replacement.getId().equals(session.getDocument().getId())) {
                    // Save-as carries all current content forward without touching a conflicting source file.
                    session=new AuthoringSession(replacement);
                }
                else session.replace(replacement);
            } else if(sceneProperties || steps().size()==0) {
                JsonObject doc=session.getDocument().toJson(); doc.getAsJsonArray("scenes").set(scene,changed);
                session.replace(AuthoringDocument.fromJson(doc));
            } else session.replace(AuthoringEdits.replaceStep(session.getDocument(),scene,step,changed));
            return true;
        } catch(Exception invalid) { status=message(invalid); return false; }
    }
    private void changed(AuthoringDocument doc) {
        cancelPick(); pickedMarker=null;
        if(!session.getDocument().getId().equals(doc.getId())) session=new AuthoringSession(doc);
        else session.replace(doc);
        diagnosticOnly=!hasEditableShape(doc);
        scene=Math.max(0,Math.min(scene,doc.getScenes().size()-1));
        step=Math.max(0,Math.min(step,steps().size()-1)); playing=false; initGui();
    }
    private static JsonElement parseField(String value) {
        return AuthoringDocument.parse("{\"value\":"+value+"}").toJson().get("value");
    }
    @Override protected void actionPerformed(GuiButton button) {
        int id=button.id;
        if (pickField != null) cancelPick();
        if(id!=3 && id!=4 && id!=11 && id!=71 && !applyFields()) return;
        try {
            if(propertyTools.containsKey(id)) { openPropertyPicker(propertyTools.get(id)); }
            else if(id==71) {
                mc.displayGuiScreen(new AuthoringReviewScreen(this,tr("validation"),status+"\n"+
                    workspace.getCompiler().validate(session.getDocument(),workspace.getAssets()).getIssues(),null));
            } else if(id==72) {
                List<String> types=new ArrayList<>();
                for(AuthoringValidation.Capability c:workspace.getCompiler().getCapabilities().values())
                    types.add(c.getType()+"  "+c.getStatus()+"  "+c.getReason());
                mc.displayGuiScreen(new AuthoringChoiceScreen(this,tr("capabilities"),types,value -> {
                    if(diagnosticOnly || steps().size()==0) return;
                    JsonObject replacement=steps().get(step).getAsJsonObject();
                    replacement=net.createmod.ponder.authoring.AuthoringStepTemplates.withType(replacement,value.substring(0,value.indexOf(' ')));
                    documentProperties=sceneProperties=false;
                    changed(AuthoringEdits.replaceStep(session.getDocument(),scene,step,replacement));
                }));
            } else if(id==0) {
                session.save(workspace.getStore());
                session=new AuthoringSession(AuthoringEdits.empty("local:lesson_"+Long.toString(System.currentTimeMillis(),36)));
                scene=step=0; compiledHash=""; renderer.releaseProjectionResources(); initGui();
            } else if(id==1) {
                session.save(workspace.getStore());
                java.util.Set<String> ids=new java.util.TreeSet<>(workspace.getStore().listDraftIds());
                ids.addAll(workspace.getStore().listPublishedIds());
                mc.displayGuiScreen(new AuthoringChoiceScreen(this,tr("documents"),new ArrayList<>(ids),value -> {
                    try { session=AuthoringSession.load(workspace.getStore(),value); scene=step=0; compiledHash=""; initGui(); }
                    catch(IOException e) { status=message(e); }
                }));
            } else if(id==2) { session.save(workspace.getStore()); status=tr("saved"); }
            else if(id==3) { session.undo(); initGui(); }
            else if(id==4) { session.redo(); initGui(); }
            else if(id==5) {
                long generation=PonderIndex.getGeneration();
                session.publish(workspace.getStore(),workspace.getAssets()); PonderIndex.reload();
                status=tr(PonderIndex.getGeneration()>generation?"published":"publish_reload_failed");
            } else if(id==6) {
                compileDraft(); if(!compiled.isEmpty()) mc.displayGuiScreen(new AuthoringPreviewScreen(compiled,scene,this));
            } else if(id==7) {
                session.save(workspace.getStore());
                AuthoringDocument candidate=AuthoringDocument.parse(getClipboardString());
                mc.displayGuiScreen(new AuthoringImportScreen(this,session.getDocument(),candidate,doc -> {
                    session=new AuthoringSession(doc); scene=step=0; compiledHash=""; initGui();
                }));
            } else if(id==8) { setClipboardString(new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(session.getDocument().toJson())); status=tr("exported"); }
            else if(id==9) showHistory();
            else if(id==10) {
                session.save(workspace.getStore());
                mc.displayGuiScreen(new AuthoringExchangeScreen(this,session.getDocument(),doc -> changed(doc)));
            }
            else if(id==11) requestClose();
            else if(id>=30 && id<=32) { pane=id-30; initGui(); }
            else if(diagnosticOnly && ((id>=40 && id<=43) || (id>=50 && id<=54) || (id>=63 && id<=66))) { status=tr("invalid_shape"); }
            else if(id==40) { sceneProperties=true; documentProperties=false; changed(AuthoringEdits.addScene(session.getDocument())); scene=session.getDocument().getScenes().size()-1; sceneOffset=Math.max(0,scene-2); initGui(); }
            else if(id==41) changed(AuthoringEdits.removeScene(session.getDocument(),scene));
            else if(id==42 || id==43) {
                int next=scene+(id==42?-1:1); changed(AuthoringEdits.move(session.getDocument(),scene,scene,id==42?-1:1,true));
                scene=Math.max(0,Math.min(next,session.getDocument().getScenes().size()-1)); initGui();
            } else if(id==44) {
                if(documentProperties) { documentProperties=false; sceneProperties=true; }
                else { documentProperties=true; sceneProperties=false; }
                if(layout.compact) pane=2; propertyPage=0; initGui();
            }
            else if(id==50) { tick=0; playing=false; audioFromStart=true; previewAudio.reset(compiled.isEmpty()?null:compiled.get(scene)); }
            else if(id==51) { playing=!playing; if(!playing) previewAudio.pause(); initGui(); }
            else if(id==52) { compileDraft(); if(!compiled.isEmpty()) tick=PreviewLessonControls.step(compiled.get(scene).getKeyframeTicks(),tick,end(),1); playing=false; audioFromStart=false; previewAudio.seek(compiled.isEmpty()?null:compiled.get(scene),tick); }
            else if(id==53) { yaw=35; pitch=30; zoom=1; renderer.releaseProjectionResources(); }
            else if(id==54) {
                compileDraft();
                if(!compiled.isEmpty()) openPractice(this,compiled.get(scene));
            } else if(id==55) {
                if(diagnosticOnly || documentProperties || sceneProperties || steps().size()==0) { status=tr("pick_step"); return; }
                List<String> choices=AuthoringCoordinateEdits.fields(steps().get(step).getAsJsonObject());
                if(choices.isEmpty()) { status=tr("pick_step"); return; }
                List<String> labels=new ArrayList<>();
                for(String key:choices) labels.add(key+" : "+tr("pickfield_"+key));
                mc.displayGuiScreen(new AuthoringChoiceScreen(this,tr("pick"),labels,label -> beginPick(choices.get(labels.indexOf(label)))));
            } else if(id==56) {
                if(diagnosticOnly) { status=tr("invalid_shape"); return; }
                AuthoringCaptureController.INSTANCE.begin(mc,this,session,scene,message -> {
                    if(mc.currentScreen!=this) return;
                    compiledHash=""; documentProperties=sceneProperties=false;
                    step=Math.max(0,steps().size()-1); propertyPage=0; formSource=""; pickedMarker=null;
                    status=message; initGui();
                });
            } else if(id==60 || id==61) { propertyPage+=id==60?-1:1; initGui(); }
            else if(id==62) { status=tr("applied"); initGui(); }
            else if(id==63) {
                sceneProperties=false; documentProperties=false; JsonObject idle=new JsonObject(); idle.addProperty("type","idle"); idle.addProperty("duration",20);
                changed(AuthoringEdits.addStep(session.getDocument(),scene,idle)); step=steps().size()-1; initGui();
            } else if(id==64 && steps().size()>0) changed(AuthoringEdits.removeStep(session.getDocument(),scene,step));
            else if((id==65 || id==66) && steps().size()>0) {
                int next=step+(id==65?-1:1); changed(AuthoringEdits.move(session.getDocument(),scene,step,id==65?-1:1,false));
                step=Math.max(0,Math.min(next,steps().size()-1)); initGui();
            }
        } catch(Exception invalid) { status=message(invalid); }
    }
    private void openPropertyPicker(String key) {
        AuthoringSession expectedSession=session;
        String expectedHash=session.getDocument().sha256();
        int expectedScene=scene, expectedStep=step;
        java.util.function.Consumer<String> commit=value -> {
            if(session!=expectedSession || scene!=expectedScene || step!=expectedStep
                || !session.getDocument().sha256().equals(expectedHash)) { status=tr("pick_changed"); return; }
            setStepProperty(key,value);
        };
        if("nbt".equals(key)) {
            String candidate=getClipboardString();
            net.createmod.ponder.authoring.AuthoringCompiler.nbt(candidate);
            mc.displayGuiScreen(new AuthoringReviewScreen(this,tr("nbt_review"),candidate,() -> commit.accept(candidate)));
            return;
        }
        List<String> values=new ArrayList<>();
        if("block".equals(key)) for(net.minecraft.util.ResourceLocation id:net.minecraft.block.Block.REGISTRY.getKeys()) values.add(id.toString());
        else if("item".equals(key)) for(net.minecraft.util.ResourceLocation id:net.minecraft.item.Item.REGISTRY.getKeys()) values.add(id.toString());
        else if("sound".equals(key)) for(net.minecraft.util.ResourceLocation id:net.minecraft.util.SoundEvent.REGISTRY.getKeys()) values.add(id.toString());
        else if("entity".equals(key)) values.addAll(java.util.Arrays.asList("minecraft:player","minecraft:parrot","minecraft:minecart"));
        else if("particle".equals(key)) for(net.minecraft.util.EnumParticleTypes value:net.minecraft.util.EnumParticleTypes.values()) values.add(value.name());
        Collections.sort(values);
        mc.displayGuiScreen(new AuthoringChoiceScreen(this,tr("choose_value"),values,commit,true));
    }
    private void setStepProperty(String key,String value) {
        JsonObject replacement=AuthoringDocument.copy(steps().get(step).getAsJsonObject());
        replacement.addProperty(key,value);
        changed(AuthoringEdits.replaceStep(session.getDocument(),scene,step,replacement));
    }
    private void beginPick(String field) {
        compileDraft();
        if(compiled.isEmpty()) {
            try {
                JsonObject idle=new JsonObject(); idle.addProperty("type","idle"); idle.addProperty("duration",20);
                AuthoringDocument preview=AuthoringEdits.replaceStep(session.getDocument(),scene,step,idle);
                compiled=workspace.getCompiler().compile(preview,workspace.getAssets());
                pickFallback=true;
            } catch(RuntimeException invalid) { status=tr("pick_unavailable"); return; }
        }
        pickReturnPane=pane;
        if(layout.compact) { pane=1; initGui(); }
        pickField=field; pickFirst=null; pickDocumentHash=session.getDocument().sha256();
        pickSession=session; pickScene=scene; pickStep=step;
        playing=false; dragging=false; previewAudio.pause(); status=tr("selection".equals(field)?"pick_first":"pick_waiting");
    }
    private void cancelPick() {
        pickField=null; pickFirst=null; pickDocumentHash=null; pickSession=null; dragging=false;
        if(pickFallback) { compiled=Collections.emptyList(); compiledHash=""; pickFallback=false; renderer.invalidateExternalPreview(); }
    }
    private boolean pickBlock(int x,int y) {
        if(pickField==null) return false;
        if(session!=pickSession || scene!=pickScene || step!=pickStep || !session.getDocument().sha256().equals(pickDocumentHash)) {
            cancelPick(); status=tr("pick_changed"); return true;
        }
        PreviewBlockPicking.Hit hit=renderer.editorPickBlock(compiled.isEmpty()?null:compiled.get(scene),tick,x,y);
        if(hit==null) { status=tr("pick_empty"); return true; }
        pickedMarker=hit.block;
        if("selection".equals(pickField) && pickFirst==null) { pickFirst=hit.block; status=tr("pick_last"); return true; }
        try {
            AuthoringDocument edited;
            if("selection".equals(pickField)) edited=AuthoringCoordinateEdits.selection(session.getDocument(),scene,step,pickFirst,hit.block);
            else if("blockPos".equals(pickField) || "blockPos2".equals(pickField))
                edited=AuthoringCoordinateEdits.block(session.getDocument(),scene,step,pickField,hit.block);
            else edited=AuthoringCoordinateEdits.point(session.getDocument(),scene,step,pickField,hit.displayedPoint);
            BlockPos marker=hit.block; int returnPane=pickReturnPane;
            changed(edited); pickedMarker=marker;
            if(layout.compact) { pane=returnPane; initGui(); }
            status=tr("picked");
        } catch(RuntimeException invalid) { status=message(invalid); }
        return true;
    }
    static void openPractice(GuiScreen parent,PonderScene scene) {
        List<net.createmod.ponder.authoring.practice.PracticeDefinition> defs=
            net.createmod.ponder.authoring.practice.PracticeStepHandlers.getDefinitions(scene);
        if(!defs.isEmpty()) {
            List<String> labels=new ArrayList<>();
            for(int i=0;i<defs.size();i++) labels.add((i+1)+" "+defs.get(i).getTitle()+" "+defs.get(i).getMenuId());
            net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new AuthoringChoiceScreen(parent,
                I18n.format("ponder.authoring.practice"),labels,name -> {
                    int index=Integer.parseInt(name.substring(0,name.indexOf(' ')))-1;
                    net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new PracticeScreen(parent,defs.get(index),PracticeMode.WATCH));
                }));
        } else {
            List<String> menus=java.util.Arrays.asList("minecraft:crafting_table","minecraft:furnace","minecraft:chest","minecraft:hopper");
            net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new AuthoringChoiceScreen(parent,
                I18n.format("ponder.authoring.practice"),menus,menu -> net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                    new PracticeScreen(parent,net.createmod.ponder.authoring.practice.PracticeExamples.create(menu),PracticeMode.WATCH))));
        }
    }
    private void showHistory() throws IOException {
        List<String> versions=workspace.getStore().listHistory(session.getDocument().getId());
        mc.displayGuiScreen(new AuthoringChoiceScreen(this,tr("history"),versions,value -> {
            try { changed(workspace.getStore().loadHistory(session.getDocument().getId(),value).getDocument()); }
            catch(IOException invalid) { status=message(invalid); }
        }));
    }
    private void requestClose() {
        try { if(!applyFields()) return; session.save(workspace.getStore()); mc.displayGuiScreen(parent); }
        catch(IOException invalid) { status=message(invalid); }
    }
    private String message(Throwable error) { return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage(); }
    private void compileDraft() {
        if(pickFallback && pickField!=null) return;
        String hash=session.getDocument().sha256(); if(hash.equals(compiledHash)) return;
        previewAudio.close();
        renderer.invalidateExternalPreview();
        compiled=Collections.emptyList(); compiledHash=hash;
        AuthoringValidation validation=workspace.getCompiler().validate(session.getDocument(),workspace.getAssets());
        if(validation.hasErrors()) { status=validation.getIssues().toString(); return; }
        try { compiled=workspace.getCompiler().compile(session.getDocument(),workspace.getAssets()); }
        catch(RuntimeException invalid) { status=message(invalid); }
        tick=Math.min(tick,end());
        if(audioFromStart && tick==0) previewAudio.reset(compiled.isEmpty()?null:compiled.get(scene));
        else previewAudio.seek(compiled.isEmpty()?null:compiled.get(scene),tick);
    }
    private int end() { return compiled.isEmpty() || scene>=compiled.size()?0:Math.max(1,renderer.getSceneEndTick(compiled.get(scene))); }
    @Override public void updateScreen() {
        for(GuiTextField f:fields.values()) f.updateCursorCounter();
        if(playing && !compiled.isEmpty()) {
            int before=tick; tick++;
            if(tick>end()) { tick=end(); playing=false; }
            if(tick>before) { previewAudio.advance(compiled.get(scene),before,tick); audioFromStart=false; }
        } else previewAudio.pause();
    }
    @Override protected void mouseClicked(int x,int y,int button) throws IOException {
        if(renderer.externalCalloutClick(x,y,button)) { playing=false; dragging=false; return; }
        for(GuiTextField f:fields.values()) f.mouseClicked(x,y,button);
        if(newKey!=null) { newKey.mouseClicked(x,y,button); newValue.mouseClicked(x,y,button); }
        if(layout.list.contains(x,y) && y<layout.list.y+layout.list.height-24) {
            cancelPick(); pickedMarker=null;
            if(!applyFields()) return;
            int sceneRows=Math.min(session.getDocument().getScenes().size(),3);
            int row=(y-layout.list.y-16)/16;
            if(!diagnosticOnly && y>=layout.list.y+16 && row>=0 && row<sceneRows) { scene=sceneOffset+row; renderer.releaseProjectionResources(); step=0; sceneProperties=true; documentProperties=false; propertyPage=0; initGui(); return; }
            int index=(y-layout.list.y-38-sceneRows*16)/16+listOffset;
            if(y>=layout.list.y+38+sceneRows*16 && index>=0 && index<steps().size()) {
                step=index; sceneProperties=false; documentProperties=false; propertyPage=0; compileDraft();
                if(!compiled.isEmpty() && step<compiled.get(scene).getKeyframeTicks().size()) tick=compiled.get(scene).getKeyframeTicks().get(step);
                audioFromStart=false; previewAudio.seek(compiled.isEmpty()?null:compiled.get(scene),tick);
                playing=false; initGui(); return;
            }
        }
        if(button==0 && layout.preview.contains(x,y) && y<layout.preview.y+layout.preview.height-24) {
            if(pickBlock(x,y)) return;
            dragging=true; lastX=x; lastY=y;
        }
        if(button==1 && pickField!=null && layout.preview.contains(x,y)) { dragging=true; lastX=x; lastY=y; return; }
        super.mouseClicked(x,y,button);
    }
    @Override protected void mouseClickMove(int x,int y,int button,long elapsed) {
        if(renderer.externalCalloutContains(x,y)) { dragging=false; playing=false; return; }
        if(dragging && (button==0 && pickField==null || button==1 && pickField!=null)) { yaw+=(x-lastX)*0.7F; pitch=Math.max(-85,Math.min(85,pitch+(y-lastY)*0.7F)); lastX=x; lastY=y; }
    }
    @Override protected void mouseReleased(int x,int y,int button) { dragging=false; super.mouseReleased(x,y,button); }
    @Override public void handleMouseInput() throws IOException {
        int wheel=Mouse.getEventDWheel();
        int x=Mouse.getEventX()*width/mc.displayWidth, y=height-Mouse.getEventY()*height/mc.displayHeight-1;
        if(renderer.externalCalloutWheel(wheel,x,y)) { playing=false; dragging=false; return; }
        super.handleMouseInput(); if(wheel==0) return;
        if(layout.properties.contains(x,y)) { if(applyFields()) { propertyPage+=wheel>0?-1:1; initGui(); } }
        else if(layout.list.contains(x,y)) {
            if(y<layout.list.y+16+3*16) sceneOffset=Math.max(0,Math.min(Math.max(0,session.getDocument().getScenes().size()-3),sceneOffset+(wheel>0?-1:1)));
            else listOffset=Math.max(0,Math.min(Math.max(0,steps().size()-1),listOffset+(wheel>0?-1:1)));
        }
        else if(layout.preview.contains(x,y)) zoom=Math.max(0.25F,Math.min(4,zoom*(wheel>0?1.1F:0.9F)));
    }
    @Override protected void keyTyped(char c,int key) throws IOException {
        if(key==Keyboard.KEY_ESCAPE && pickField!=null) { cancelPick(); status=tr("pick_cancelled"); return; }
        if(key==Keyboard.KEY_ESCAPE) { requestClose(); return; }
        for(GuiTextField f:fields.values()) if(f.textboxKeyTyped(c,key)) return;
        if(newKey!=null && (newKey.textboxKeyTyped(c,key)||newValue.textboxKeyTyped(c,key))) return;
        if(isCtrlKeyDown() && key==Keyboard.KEY_S) { actionPerformed(new GuiButton(2,0,0,"")); return; }
        super.keyTyped(c,key);
    }
    @Override public void drawScreen(int x,int y,float partial) {
        drawDefaultBackground();
        drawString(fontRenderer,fontRenderer.trimStringToWidth(session.getDocument().getId()+(session.isDirty()?" *":""),width-64),8,8,0xFFFFFF);
        if(layout.list.width>0) drawList();
        if(layout.preview.width>0 && !diagnosticOnly) {
            AuthoringEditorLayout.Rect p=layout.preview; drawRect(p.x,p.y,p.x+p.width,p.y+p.height-24,0xFF101519);
            compileDraft(); if(!compiled.isEmpty()) {
                renderer.setEditorPickMarker(pickedMarker);
                renderer.renderEditorPreview(compiled.get(scene),tick,p.x,p.y,p.width,p.height-24,yaw,pitch,zoom);
            }
            drawString(fontRenderer,"T+"+tick+" / "+end(),p.x+4,p.y+4,0xDDDDDD);
        }
        if(layout.properties.width>0) {
            int fieldY=layout.properties.y+17;
            drawString(fontRenderer,tr(documentProperties?"document_properties":sceneProperties?"scene_properties":"properties"),layout.properties.x+3,layout.properties.y+3,0xFFFFFF);
            for(Map.Entry<String,GuiTextField> field:fields.entrySet()) {
                drawString(fontRenderer,fontRenderer.trimStringToWidth(field.getKey()+(readOnlyFields.contains(field.getKey())?" [R]":""),layout.properties.width-8),layout.properties.x+3,fieldY,0xAEBCC7);
                field.getValue().drawTextBox(); fieldY+=34;
            }
            newKey.drawTextBox(); newValue.drawTextBox();
        }
        drawString(fontRenderer,fontRenderer.trimStringToWidth(status,width-16),8,height-15,0xDCC888);
        super.drawScreen(x,y,partial);
        if(layout.preview.width>0 && !diagnosticOnly && !compiled.isEmpty())
            renderer.renderEditorForeground(compiled.get(scene),tick);
        for(GuiButton b:buttonList) if(b.isMouseOver() && tooltips.containsKey(b.id)) drawHoveringText(Collections.singletonList(tooltips.get(b.id)),x,y);
    }
    private void drawList() {
        AuthoringEditorLayout.Rect p=layout.list; int y=p.y+3;
        drawString(fontRenderer,tr("segments"),p.x+3,y,0xAEBCC7); y+=13;
        List<JsonObject> scenes=session.getDocument().getScenes();
        if(diagnosticOnly) { drawString(fontRenderer,fontRenderer.trimStringToWidth(tr("invalid_shape"),p.width-8),p.x+3,y,0xE8BB89); return; }
        sceneOffset=Math.max(0,Math.min(sceneOffset,Math.max(0,scenes.size()-3)));
        for(int i=sceneOffset;i<Math.min(scenes.size(),sceneOffset+3);i++) {
            if(i==scene) drawRect(p.x,y-2,p.x+p.width,y+12,0xFF2B3A42);
            drawString(fontRenderer,fontRenderer.trimStringToWidth((i+1)+" "+AuthoringDocument.string(scenes.get(i),"title",AuthoringDocument.string(scenes.get(i),"id","?")),p.width-8),p.x+3,y,0xEEEEEE); y+=16;
        }
        y+=6; drawString(fontRenderer,tr("steps"),p.x+3,y,0xAEBCC7); y+=16;
        for(int i=listOffset;i<steps().size() && y<p.y+p.height-35;i++) {
            if(i==step && !sceneProperties) drawRect(p.x,y-2,p.x+p.width,y+12,0xFF2B3A42);
            drawString(fontRenderer,fontRenderer.trimStringToWidth((i+1)+" "+AuthoringDocument.string(steps().get(i).getAsJsonObject(),"type","?"),p.width-8),p.x+3,y,0xEEEEEE); y+=16;
        }
    }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); previewAudio.close(); renderer.releaseProjectionResources(); cancelPick(); }
}
