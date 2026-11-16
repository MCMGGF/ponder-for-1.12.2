package net.createmod.ponder.foundation.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.createmod.ponder.authoring.capture.CaptureDraft;
import net.createmod.ponder.authoring.capture.CaptureDraft.SlotValue;
import net.createmod.ponder.authoring.capture.CaptureReviewLayout;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Unscaled text values and explicit approval, never the original container or an item renderer. */
public final class AuthoringCaptureReviewScreen extends AuthoringGuiScreen {
    private final AuthoringCaptureController owner;
    private final CaptureDraft draft;
    private CaptureReviewLayout layout;
    private int pane, page;
    private boolean privacyReviewed;
    private List<String> lines = new ArrayList<>();
    AuthoringCaptureReviewScreen(AuthoringCaptureController owner, CaptureDraft draft) {
        if (owner == null || draft == null) throw new IllegalArgumentException("A detached capture and owning controller are required");
        this.owner = owner; this.draft = draft;
    }
    public CaptureDraft getDraft() { return draft; }
    public int getPane() { return pane; }
    public int getPage() { return page; }
    public int getPageCount() { return layout == null ? 1 : layout.pageCount(lines.size()); }
    public boolean isPrivacyReviewed() { return privacyReviewed; }
    public boolean canConfirm() { return layout != null && layout.readable && privacyReviewed; }
    @Override public void initGui() {
        super.initGui();
        clearCompatButtons(); layout = new CaptureReviewLayout(width, height, fontRenderer.FONT_HEIGHT);
        int tabWidth = Math.max(1, (width - 24) / 3);
        for (int index = 0; index < 3; index++) addCompatButton(new GuiButton(10 + index, 12 + index * tabWidth, layout.tabsY, tabWidth, layout.buttonHeight,
            new String[] {"\u6982\u89c8", "\u69fd\u4f4d", "\u6570\u636e"}[index]));
        int buttonWidth = Math.min(110, Math.max(1, (width - 30) / 2));
        addCompatButton(new GuiButton(0, width / 2 - buttonWidth - 3, layout.buttonsY, buttonWidth, layout.buttonHeight, "\u53d6\u6d88"));
        addCompatButton(new GuiButton(1, width / 2 + 3, layout.buttonsY, buttonWidth, layout.buttonHeight, "\u6dfb\u52a0"));
        addCompatButton(new GuiButton(2, 12, layout.pagerY, 40, layout.buttonHeight, "<"));
        addCompatButton(new GuiButton(3, width - 52, layout.pagerY, 40, layout.buttonHeight, ">"));
        rebuildLines(); updateButtons();
    }
    private void rebuildLines() {
        List<String> values = new ArrayList<>();
        if (pane == 0) {
            values.add("\u9690\u79c1\u8fc7\u6ee4\uff1a\u4ec5\u4fdd\u7559 ID\u3001\u6570\u91cf\u4e0e\u53d8\u79cd\u3002");
            values.add("\u4e0d\u5bfc\u51fa\u73a9\u5bb6\u80cc\u5305\uff0c\u4e5f\u4e0d\u5bfc\u51fa\u7269\u54c1 NBT\u3002");
            values.add("\u8fd9\u4e0d\u662f\u65e0\u635f\u6355\u83b7\uff1b\u5df2\u8fc7\u6ee4 NBT \u7684\u7269\u54c1\u69fd\u4f4d\uff1a" + draft.getFilteredTagCount());
            values.add("\u83dc\u5355\u69fd\u4f4d\uff1a" + draft.getSlots().size() + "\uff1b\u540c\u6b65\u6570\u636e\uff1a" + draft.getData().size());
            values.add("\u865a\u62df\u5408\u6210\u4f7f\u7528\u5f53\u524d\u914d\u65b9\uff0c\u8fc7\u6ee4 NBT \u53ef\u80fd\u6539\u53d8\u7ed3\u679c\u3002");
            values.add("\u786e\u8ba4\u4ec5\u6dfb\u52a0\u539f\u7f16\u8f91\u4f1a\u8bdd\u7684\u6b65\u9aa4\u8349\u7a3f\uff0c\u4e0d\u81ea\u52a8\u4fdd\u5b58\u6216\u53d1\u5e03\u3002");
        } else if (pane == 1) {
            for (int index = 0; index < draft.getSlots().size(); index++) {
                SlotValue value = draft.getSlots().get(index);
                values.add("[" + index + "] " + (value.itemId.isEmpty() ? "\u7a7a" : value.itemId + "  x" + value.count + "  @" + value.meta));
            }
        } else {
            for (Map.Entry<String, Integer> field : draft.getData().entrySet()) {
                String label = "burnTime".equals(field.getKey()) ? "\u5269\u4f59\u71c3\u70e7\u65f6\u95f4" : "fuelTime".equals(field.getKey()) ? "\u71c3\u6599\u603b\u65f6\u957f"
                    : "cookTime".equals(field.getKey()) ? "\u5f53\u524d\u7194\u70bc\u8fdb\u5ea6" : "\u7194\u70bc\u6240\u9700\u65f6\u95f4";
                values.add(label + ": " + field.getValue() + " ticks");
            }
            if (values.isEmpty()) values.add("\u6b64\u539f\u7248\u83dc\u5355\u6ca1\u6709\u6355\u83b7\u5230\u7684\u540c\u6b65\u6570\u636e\u3002");
            if (draft.getMenu() == net.createmod.ponder.authoring.practice.PracticeDefinition.Menu.HOPPER)
                values.add("\u6f0f\u6597\u9501\u5b9a\u72b6\u6001\u672a\u7531\u6b64\u83dc\u5355\u540c\u6b65\uff0c\u672a\u81ea\u884c\u63a8\u6d4b\u3002");
        }
        lines = new ArrayList<>();
        for (String value : values) lines.addAll(PreviewTextLayout.wrap(fontRenderer, value, layout.bodyWidth));
        page = Math.max(0, Math.min(page, getPageCount() - 1));
    }
    private void updateButtons() {
        for (GuiButton button : buttonList) {
            if (button.id == 1) button.enabled = layout.readable && privacyReviewed && owner.isReviewOwned(this);
            else if (button.id == 2) button.enabled = page > 0;
            else if (button.id == 3) button.enabled = page + 1 < getPageCount();
            else if (button.id >= 10) button.enabled = button.id - 10 != pane;
        }
    }
    @Override public void updateScreen() { updateButtons(); }
    @Override public void drawScreen(int x, int y, float partial) {
        PreviewRenderState.runIsolated(() -> {
            drawDefaultBackground();
            drawCenteredString(fontRenderer, "\u83dc\u5355\u6355\u83b7\u5ba1\u9605", width / 2, layout.titleY, 0xFFFFFF);
            drawCenteredString(fontRenderer, fontRenderer.trimStringToWidth(draft.getMenuId(), Math.max(1, width - 24)), width / 2, layout.menuY, 0xB9D5DD);
            if (layout.readable) {
                int row = 0;
                for (int index = layout.firstLine(page, lines.size()); index < layout.endLine(page, lines.size()); index++)
                    drawString(fontRenderer, lines.get(index), layout.margin, layout.bodyY + row++ * layout.lineHeight, pane == 0 ? 0xE6D994 : 0xE4EBEE);
                if (pane == 0 && page + 1 == getPageCount()) privacyReviewed = true;
            } else drawCenteredString(fontRenderer, fontRenderer.trimStringToWidth("\u5f53\u524d\u5b57\u4f53\u4e0e\u7a97\u53e3\u7a7a\u95f4\u4e0d\u8db3", Math.max(1, width - 24)), width / 2, layout.bodyY, 0xE6B594);
            drawCenteredString(fontRenderer, (page + 1) + " / " + getPageCount(), width / 2, layout.pagerY + (layout.buttonHeight - fontRenderer.FONT_HEIGHT) / 2, 0xC9D6DD);
            updateButtons();
            GlStateManager.disableDepth();
            for (GuiButton button : buttonList) if (button.visible) {
                boolean hover = x >= button.x && x < button.x + button.width && y >= button.y && y < button.y + button.height;
                drawRect(button.x, button.y, button.x + button.width, button.y + button.height, button.enabled && hover ? 0xFF66808A : 0xFF536168);
                drawRect(button.x + 1, button.y + 1, button.x + button.width - 1, button.y + button.height - 1, button.enabled ? 0xFF303C43 : 0xFF20292E);
                String label = fontRenderer.trimStringToWidth(button.displayString, Math.max(1, button.width - 8));
                drawCenteredString(fontRenderer, label, button.x + button.width / 2,
                    button.y + (button.height - fontRenderer.FONT_HEIGHT) / 2, button.enabled ? 0xE4EBEE : 0x83939C);
            }
        });
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) owner.cancelReview(this);
        else if (button.id == 1 && button.enabled) owner.acceptReview(this);
        else if (button.id == 2 || button.id == 3) { page += button.id == 2 ? -1 : 1; page = Math.max(0, Math.min(page, getPageCount() - 1)); }
        else if (button.id >= 10 && button.id <= 12) { pane = button.id - 10; page = 0; rebuildLines(); }
        updateButtons();
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput(); int wheel = Mouse.getEventDWheel();
        if (wheel != 0) { page += wheel < 0 ? 1 : -1; page = Math.max(0, Math.min(page, getPageCount() - 1)); updateButtons(); }
    }
    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) { owner.cancelReview(this); return; }
        super.keyTyped(typedChar, keyCode);
    }
    @Override public void onGuiClosed() { owner.closedReview(this); super.onGuiClosed(); }
    @Override public boolean doesGuiPauseGame() { return true; }
}
