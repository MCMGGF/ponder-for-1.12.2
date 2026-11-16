package net.createmod.ponder.foundation.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.assets.ImageAssetPool;
import net.createmod.ponder.authoring.assets.ImageStepHandlers;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Local, paged PNG browser. Only the current thumbnail page can own uploaded textures. */
public final class AuthoringImageScreen extends AuthoringGuiScreen {
    private final GuiScreen parent;
    private final AuthoringDocument document;
    private final ImageAssetPool pool;
    private final Consumer<AuthoringDocument> change;
    private final Map<String, ImageSnapshotProvider> previews = new LinkedHashMap<>();
    private final Map<String, String> failures = new LinkedHashMap<>();
    private List<String> images = new ArrayList<>();
    private AuthoringImageLayout layout;
    private GuiTextField imageId;
    private int page;
    private String status = "";
    public AuthoringImageScreen(GuiScreen parent, AuthoringDocument document, ImageAssetPool pool, Consumer<AuthoringDocument> change) {
        this.parent = parent; this.document = document; this.pool = pool; this.change = change;
    }
    private String tr(String name) { return I18n.format("ponder.authoring." + name); }
    private void clearPreviews() { for (ImageSnapshotProvider preview : previews.values()) preview.reset(); previews.clear(); failures.clear(); }
    @Override public void initGui() {
        syncCompatFields(); Keyboard.enableRepeatEvents(true); clearPreviews(); buttonList.clear();
        try { images = pool.listIds(); } catch (IOException invalid) { status = invalid.getMessage(); images = new ArrayList<>(); }
        layout = new AuthoringImageLayout(width, height); page = layout.clampPage(page, images.size());
        String previous = imageId == null ? "local:image" : imageId.getText();
        imageId = new GuiTextField(2000, fontRenderer, 12, 34, Math.max(64, width - 122), 20);
        imageId.setMaxStringLength(220); imageId.setText(previous);
        buttonList.add(new GuiButton(2001, width - 104, 34, 92, 20, tr("image_import")));
        for (int slot = 0; slot < layout.slots && page * layout.slots + slot < images.size(); slot++) {
            String id = images.get(page * layout.slots + slot); int x = layout.x(slot), y = layout.y(slot);
            buttonList.add(new GuiButton(slot, x + 2, y + 62, layout.cellWidth - 4, 20, fontRenderer.trimStringToWidth(id, layout.cellWidth - 16)));
        }
        buttonList.add(new GuiButton(2002, 12, height - 30, 28, 20, "<"));
        buttonList.add(new GuiButton(2003, 44, height - 30, 28, 20, ">"));
        buttonList.add(new GuiButton(2004, width - 40, height - 30, 28, 20, "X"));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 2002 || button.id == 2003) { page += button.id == 2002 ? -1 : 1; initGui(); }
        else if (button.id == 2004) mc.displayGuiScreen(parent);
        else if (button.id == 2001) {
            try {
                ImageAssetPool.normalizeId(imageId.getText()); String target = imageId.getText();
                mc.displayGuiScreen(new AuthoringChoiceScreen(this, tr("image_import"), pool.listInboxNames(), name -> {
                    try {
                        ImageAssetPool.ImportPlan plan = pool.previewInbox(name, target);
                        String text = plan.image.id + "\n" + plan.image.width + " x " + plan.image.height + "\n" + plan.image.encodedBytes + " B\n" + plan.image.sha256;
                        mc.displayGuiScreen(new AuthoringReviewScreen(this, tr("image_review"), text, () -> {
                            try { pool.confirmInbox(plan); status = plan.image.id; initGui(); } catch (IOException invalid) { status = invalid.getMessage(); }
                        }));
                    } catch (IOException invalid) { status = invalid.getMessage(); }
                }));
            } catch (IOException invalid) { status = invalid.getMessage(); }
        } else if (button.id >= 0 && button.id < layout.slots) bind(images.get(page * layout.slots + button.id));
    }
    private void bind(String id) {
        try {
            pool.inspect(id); List<String> scenes = new ArrayList<>(); List<Integer> indices = ImageStepHandlers.editableSceneIndices(document);
            com.google.gson.JsonArray rawScenes = document.toJson().getAsJsonArray("scenes");
            for (int index : indices) scenes.add((index + 1) + ": " + AuthoringDocument.string(rawScenes.get(index).getAsJsonObject(), "id", "scene"));
            mc.displayGuiScreen(new AuthoringChoiceScreen(this, tr("image_scene"), scenes, value -> {
                try {
                    int index = indices.get(scenes.indexOf(value)); AuthoringDocument changed = ImageStepHandlers.append(document, index, id, 80);
                    pool.inspectContext(changed); mc.displayGuiScreen(parent); change.accept(changed);
                } catch (IOException | RuntimeException invalid) { status = invalid.getMessage(); }
            }));
        } catch (IOException invalid) { status = invalid.getMessage(); }
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException { imageId.mouseClicked(x, y, button); super.mouseClicked(x, y, button); }
    @Override protected void keyTyped(char typed, int key) throws IOException {
        if (key == 1) mc.displayGuiScreen(parent); else if (!imageId.textboxKeyTyped(typed, key)) super.keyTyped(typed, key);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput(); int wheel = Mouse.getEventDWheel();
        if (wheel != 0) { page += wheel > 0 ? -1 : 1; initGui(); }
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground(); drawCenteredString(fontRenderer, tr("images"), width / 2, 12, 0xFFFFFF); imageId.drawTextBox();
        String hovered = null;
        for (int slot = 0; slot < layout.slots && page * layout.slots + slot < images.size(); slot++) {
            String id = images.get(page * layout.slots + slot); int x = layout.x(slot) + 2, y = layout.y(slot);
            drawRect(x, y, x + layout.cellWidth - 4, y + 60, 0xFF282D31);
            if (!previews.containsKey(id) && !failures.containsKey(id)) {
                try { previews.put(id, new ImageSnapshotProvider(pool, pool.inspect(id))); }
                catch (IOException invalid) { failures.put(id, invalid.getMessage()); }
            }
            ImageSnapshotProvider preview = previews.get(id);
            if (preview != null) { preview.render(x + 3, y + 3, layout.cellWidth - 10, 54, 0, 1); if (preview.getFailure() != null) failures.put(id, preview.getFailure()); }
            if (failures.containsKey(id)) drawString(fontRenderer, "!", x + 4, y + 4, 0xEC9A91);
            if (mouseX >= x && mouseX < x + layout.cellWidth - 4 && mouseY >= y && mouseY < y + 82) hovered = id;
        }
        if (images.isEmpty()) drawCenteredString(fontRenderer, tr("image_empty"), width / 2, 82, 0xBBBBBB);
        drawCenteredString(fontRenderer, (page + 1) + " / " + layout.pageCount(images.size()), width / 2, height - 23, 0xBBBBBB);
        drawString(fontRenderer, fontRenderer.trimStringToWidth(status, width - 24), 12, height - 43, 0xDDBE8A);
        super.drawScreen(mouseX, mouseY, partial);
        if (hovered != null) {
            String text = hovered + (failures.containsKey(hovered) ? "\n" + failures.get(hovered) : "");
            drawHoveringText(PreviewTextLayout.wrap(fontRenderer, text, Math.max(80, Math.min(260, width - 30))), mouseX, mouseY);
        }
    }
    @Override public void onGuiClosed() { clearPreviews(); Keyboard.enableRepeatEvents(false); }
}
