package net.createmod.ponder.foundation.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.AuthoringWorkspace;
import net.createmod.ponder.authoring.assets.AssetPackService;
import net.createmod.ponder.authoring.assets.AuthoringAssetPaths;
import net.createmod.ponder.authoring.assets.StructurePool;
import net.createmod.ponder.authoring.assets.ImageAssetPool;
import net.createmod.ponder.authoring.exchange.JavaSceneTextExporter;
import net.createmod.ponder.authoring.exchange.OfflineAiExchange;
import net.createmod.ponder.authoring.exchange.RestrictedPonderJsCodec;
import net.createmod.ponder.foundation.PonderSchematic;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/** All exchange is explicit data review inside the author workspace. No imported code is executed. */
public final class AuthoringExchangeScreen extends AuthoringGuiScreen {
    private final GuiScreen parent;
    private final AuthoringDocument document;
    private final Consumer<AuthoringDocument> change;
    private final AuthoringWorkspace workspace=AuthoringWorkspace.get();
    private GuiTextField instruction;
    private String status="";
    public AuthoringExchangeScreen(GuiScreen parent,AuthoringDocument doc,Consumer<AuthoringDocument> change) {
        this.parent=parent; document=doc; this.change=change;
    }
    private String tr(String key) { return I18n.format("ponder.authoring."+key); }
    @Override public void initGui() {
        syncCompatFields();
        Keyboard.enableRepeatEvents(true); buttonList.clear(); int w=Math.min(420,width-24),x=(width-w)/2;
        String previous=instruction==null?"":instruction.getText();
        instruction=new GuiTextField(0,fontRenderer,x,38,w,20); instruction.setMaxStringLength(32768); instruction.setText(previous);
        String[] names={"asset_library","offline_prompt","ai_import","js_import","js_export","java_export","pack_import","pack_export","close","images"};
        for(int i=0;i<names.length;i++) buttonList.add(new GuiButton(i,x+(i%2)*(w/2+2),66+(i/2)*24,w/2-2,20,tr(names[i])));
    }
    private List<String> files(Path directory,String suffix) throws IOException {
        List<String> result=new ArrayList<>(); AuthoringAssetPaths.verifyAncestors(directory);
        if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS)) return result;
        Files.walkFileTree(directory,new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path p,BasicFileAttributes a) throws IOException {
                AuthoringAssetPaths.verifyAncestors(p);
                if(directory.relativize(p).getNameCount()>32) throw new IOException("Directory depth budget exceeded");
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path p,BasicFileAttributes a) throws IOException {
                AuthoringAssetPaths.verifyAncestors(p);
                if(a.isRegularFile() && p.toString().endsWith(suffix)) result.add(directory.relativize(p).toString().replace('\\','/'));
                if(result.size()>4096) throw new IOException("Asset list exceeds 4096 files");
                return FileVisitResult.CONTINUE;
            }
        });
        java.util.Collections.sort(result); return result;
    }
    private Map<String,PonderSchematic> structures() throws IOException {
        return net.createmod.ponder.authoring.assets.AuthoringAssetSelection.loadContext(workspace.getAssets(),document);
    }
    @Override protected void actionPerformed(GuiButton button) {
        try {
            if(button.id==0) {
                mc.displayGuiScreen(new AuthoringChoiceScreen(this,tr("asset_library"),workspace.getAssets().listIds(),id -> {
                    JsonObject json=document.toJson(); json.addProperty("structure",id);
                    JsonArray steps=json.getAsJsonArray("scenes").get(0).getAsJsonObject().getAsJsonArray("steps");
                    JsonObject show=new JsonObject(); show.addProperty("type","show_structure"); show.addProperty("structure",id);
                    JsonArray reordered=new JsonArray(); reordered.add(show); for(com.google.gson.JsonElement e:steps) reordered.add(e);
                    json.getAsJsonArray("scenes").get(0).getAsJsonObject().add("steps",reordered);
                    mc.displayGuiScreen(parent); change.accept(AuthoringDocument.fromJson(json));
                }));
            } else if(button.id==1) {
                exportText("prompt.txt",OfflineAiExchange.exportPrompt(instruction.getText(),document,OfflineAiExchange.RegistryContext.snapshotLoadedRegistries(),structures(),workspace.getCompiler()));
            } else if(button.id==2) {
                OfflineAiExchange.DraftResult draft=OfflineAiExchange.validateDraft(getClipboardString(),workspace.getCompiler(),workspace.getAssets());
                if(draft.document==null) throw new IOException(draft.validation.getIssues().toString());
                reviewDraft(draft.document);
            } else if(button.id==3) {
                RestrictedPonderJsCodec.ImportResult result=RestrictedPonderJsCodec.importText(getClipboardString(),document.getId());
                if(result.document==null || result.validation.hasErrors()) throw new IOException(result.validation.getIssues().toString());
                reviewDraft(result.document);
            } else if(button.id==4) {
                RestrictedPonderJsCodec.ExportResult result=RestrictedPonderJsCodec.exportText(document);
                if(result.text==null || result.validation.hasErrors()) throw new IOException(result.validation.getIssues().toString());
                exportText("scene.js.txt",result.text);
            } else if(button.id==5) exportText("SceneExport.java.txt",JavaSceneTextExporter.exportText(document,"local.ponder","SceneExport"));
            else if(button.id==6) {
                Path inbox=workspace.getStoreRoot().resolve("inbox");
                mc.displayGuiScreen(new AuthoringChoiceScreen(this,tr("pack_import"),files(inbox,".zip"),name -> {
                    try { previewPack(inbox.resolve(name)); } catch(IOException invalid) { status=invalid.getMessage(); }
                }));
            } else if(button.id==7) previewExport();
            else if(button.id==8) mc.displayGuiScreen(parent);
            else if(button.id==9) mc.displayGuiScreen(new AuthoringImageScreen(this,document,new ImageAssetPool(workspace.getStoreRoot()),value -> {
                mc.displayGuiScreen(parent); change.accept(value);
            }));
        } catch(Exception invalid) { status=invalid.getMessage()==null?invalid.getClass().getSimpleName():invalid.getMessage(); }
    }
    private void reviewDraft(AuthoringDocument draft) {
        mc.displayGuiScreen(new AuthoringImportScreen(this,document,draft,value -> { mc.displayGuiScreen(parent); change.accept(value); }));
    }
    private void exportText(String suffix,String text) throws IOException {
        AuthoringAssetPaths root=new AuthoringAssetPaths(workspace.getStoreRoot()); root.ensureRoot();
        String name="exports/"+Long.toString(System.currentTimeMillis(),36)+"-"+suffix;
        root.writeNew(name,text.getBytes(StandardCharsets.UTF_8)); setClipboardString(text); status=tr("exported")+" "+name;
    }
    private void previewPack(Path zip) throws IOException {
        AssetPackService service=new AssetPackService(workspace.getStoreRoot(),workspace.getCompiler()::validateSchema); AssetPackService.ImportPlan plan=service.previewImport(zip);
        StringBuilder review=new StringBuilder(plan.archiveSha256+"\n");
        for(AssetPackService.EntryInfo entry:plan.entries) review.append(entry.path).append("  ").append(entry.bytes).append('\n');
        review.append(plan.warnings);
        mc.displayGuiScreen(new AuthoringReviewScreen(this,tr("import_review"),review.toString(),() -> {
            try {
                AssetPackService.Receipt receipt=service.confirmImport(plan,plan.getReviewToken());
                service.installImportedAssets(receipt);
                List<String> docs=files(receipt.path.resolve("documents"),".json");
                mc.displayGuiScreen(new AuthoringChoiceScreen(this,tr("documents"),docs,name -> {
                    try {
                        Path path=receipt.path.resolve("documents").resolve(name); AuthoringAssetPaths.verifyAncestors(path);
                        if(Files.size(path)>AuthoringDocument.MAX_JSON_BYTES) throw new IOException("Document too large");
                        reviewDraft(AuthoringDocument.parse(new String(Files.readAllBytes(path),StandardCharsets.UTF_8)));
                    } catch(IOException invalid) { status=invalid.getMessage(); }
                }));
            } catch(IOException invalid) { status=invalid.getMessage(); }
        }));
    }
    private void previewExport() throws IOException {
        Map<String,byte[]> data=new LinkedHashMap<>();
        JsonObject manifest=new JsonObject(); manifest.addProperty("formatVersion",1); manifest.addProperty("targetVersion","1.12.2");
        data.put("pack.json",manifest.toString().getBytes(StandardCharsets.UTF_8));
        data.put("documents/local/current.json",document.toJson().toString().getBytes(StandardCharsets.UTF_8));
        for(Map.Entry<String,PonderSchematic> asset:structures().entrySet()) {
            Path path=workspace.getAssets().root().resolve(StructurePool.relativePath(asset.getKey()));
            if(Files.size(path)>64L*1024*1024) throw new IOException("Asset too large");
            data.put("assets/"+StructurePool.relativePath(asset.getKey()),Files.readAllBytes(path));
        }
        ImageAssetPool images=new ImageAssetPool(workspace.getStoreRoot());
        for(ImageAssetPool.ImageInfo image:images.inspectContext(document).values())
            data.put("assets/"+ImageAssetPool.relativePath(image.id),images.readChecked(image));
        AssetPackService service=new AssetPackService(workspace.getStoreRoot(),workspace.getCompiler()::validateSchema); AssetPackService.ExportPlan plan=service.previewExport(data);
        StringBuilder review=new StringBuilder(); for(AssetPackService.EntryInfo e:plan.entries) review.append(e.path).append("  ").append(e.bytes).append('\n');
        review.append(plan.warnings);
        mc.displayGuiScreen(new AuthoringReviewScreen(this,tr("pack_export"),review.toString(),() -> {
            try { status=service.confirmExport(plan,plan.getReviewToken(),"lesson-"+Long.toString(System.currentTimeMillis(),36)+".zip").path.toString(); }
            catch(IOException invalid) { status=invalid.getMessage(); }
        }));
    }
    @Override protected void mouseClicked(int x,int y,int b) throws IOException { instruction.mouseClicked(x,y,b); super.mouseClicked(x,y,b); }
    @Override protected void keyTyped(char c,int key) throws IOException { if(key==1) mc.displayGuiScreen(parent); else if(!instruction.textboxKeyTyped(c,key)) super.keyTyped(c,key); }
    @Override public void drawScreen(int x,int y,float partial) {
        drawDefaultBackground(); drawCenteredString(fontRenderer,tr("assets"),width/2,12,0xFFFFFF); instruction.drawTextBox();
        drawString(fontRenderer,fontRenderer.trimStringToWidth(status,width-20),10,height-18,0xDDBE8A); super.drawScreen(x,y,partial);
    }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
}
