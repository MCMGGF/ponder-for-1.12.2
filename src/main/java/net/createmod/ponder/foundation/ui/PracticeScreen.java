package net.createmod.ponder.foundation.ui;

import java.io.IOException;
import java.util.List;

import com.google.gson.JsonObject;
import net.createmod.ponder.authoring.practice.PracticeDefinition;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Goal;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Menu;
import net.createmod.ponder.authoring.practice.PracticeJson;
import net.createmod.ponder.authoring.practice.PracticeLayout;
import net.createmod.ponder.authoring.practice.PracticeMode;
import net.createmod.ponder.authoring.practice.PracticeSession;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

/** Vanilla-style virtual inventory; deliberately not a GuiContainer. */
public final class PracticeScreen extends AuthoringGuiScreen implements PonderOwnedChildScreen {
    private final GuiScreen parent;
    private final PracticeSession session;
    private boolean playing;
    private int goalPage;
    private int left;
    private int top;
    private float scale;
    private String renderNotice = "";
    private final java.util.Set<String> failedItems = new java.util.HashSet<>();

    public PracticeScreen(GuiScreen parent, PracticeDefinition definition, PracticeMode mode) {
        this.parent = parent;
        session = new PracticeSession(definition, mode);
        playing = mode == PracticeMode.WATCH;
    }

    public static PracticeScreen fromStep(GuiScreen parent, JsonObject step, PracticeMode mode) {
        return new PracticeScreen(parent, PracticeJson.fromStep(step), mode);
    }

    public static PracticeScreen openVanilla(GuiScreen parent, String menuId, PracticeMode mode) {
        return new PracticeScreen(parent, net.createmod.ponder.authoring.practice.PracticeExamples.create(menuId), mode);
    }

    public PracticeSession getSession() { return session; }

    @Override public GuiScreen getPonderParent() { return parent; }

    @Override public void initGui() {
        super.initGui();
        clearCompatButtons();
        int modeWidth = Math.min(74, (width - 24) / 3);
        for (int i = 0; i < 3; i++) addCompatButton(new GuiButton(10 + i,
            width / 2 - modeWidth * 3 / 2 + i * modeWidth, 27, modeWidth, 20,
            new String[] {"\u89c2\u770b", "\u8bd5\u64cd\u4f5c", "\u7ec3\u4e60"}[i]));
        int controlWidth = Math.min(52, (width - 24) / 5);
        for (int i = 0; i < 5; i++) addCompatButton(new GuiButton(i, width / 2 - controlWidth * 5 / 2
            + i * controlWidth, height - 24, controlWidth, 20,
            new String[] {"<", "\u64ad\u653e", ">", "\u91cd\u7f6e", "\u8fd4\u56de"}[i]));
        addCompatButton(new GuiButton(20, width - 53, height - 48, 45, 18, "\u76ee\u6807 >"));
        if (session.getDefinition().getMenu() == Menu.HOPPER)
            addCompatButton(new GuiButton(21, 8, height - 48, 45, 18, "\u9501\u5b9a"));
        PracticeLayout.Viewport viewport = PracticeLayout.fit(width, height, session.getDefinition().getMenu());
        scale = viewport.scale; left = viewport.left; top = viewport.top;
    }

    @Override public void updateScreen() {
        if (playing) session.advance();
        if (session.getTick() >= session.getDefinition().getDuration()) playing = false;
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        PreviewRenderState.runIsolated(() -> drawContents(mouseX, mouseY, partialTicks));
    }

    private void drawContents(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        String title = session.getDefinition().getTitle();
        if (title.isEmpty()) title = menuName(session.getDefinition().getMenu());
        drawCenteredString(fontRenderer, fontRenderer.trimStringToWidth(title, Math.max(20, width - 24)), width / 2, 9, 0xFFFFFF);
        GlStateManager.pushMatrix();
        try {
        GlStateManager.translate(left, top, 0);
        GlStateManager.scale(scale, scale, 1);
        drawRect(0, 0, PracticeLayout.WIDTH, PracticeLayout.height(session.getDefinition().getMenu()), 0xFFB7B9BC);
        Menu menu = session.getDefinition().getMenu();
        RenderHelper.enableGUIStandardItemLighting();
        for (int slot = 0; session.isVisible() && slot < session.getSlotCount(); slot++) {
            int x = PracticeLayout.x(menu, slot), y = PracticeLayout.y(menu, slot);
            drawRect(x, y, x + 18, y + 18, 0xFF36383D);
            drawRect(x + 1, y + 1, x + 18, y + 18, 0xFF85898D);
            ItemStack stack = session.getSlot(slot);
            if (!stack.isEmpty()) {
                renderVirtualItem(stack, x + 1, y + 1);
            }
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        if (session.isVisible() && menu == Menu.FURNACE) {
            int progress = session.getFurnaceProgressPixels(28);
            drawRect(73, 33, 102, 37, 0xFF44464A);
            drawRect(73, 33, 73 + progress, 37, 0xFF75CF91);
        }
        if (session.isVisible() && menu == Menu.HOPPER) drawString(fontRenderer,
            session.getData("locked") == 0 ? "\u8f93\u9001" : "\u5df2\u9501\u5b9a", 111, 31, 0xFFFFFF);
        } finally { GlStateManager.popMatrix(); }
        GlStateManager.color(1, 1, 1, 1);
        int timelineY = height - 60;
        drawRect(12, timelineY, width - 12, timelineY + 4, 0xFF45494F);
        int progressX = 12 + (width - 24) * session.getTick() / Math.max(1, session.getDefinition().getDuration());
        drawRect(12, timelineY, progressX, timelineY + 4, 0xFF75CF91);
        List<Goal> goals = session.getDefinition().getGoals();
        String message;
        if (!renderNotice.isEmpty()) message = renderNotice;
        else if (session.isReadOnly()) message = "\u4ec5\u8bfb\u5feb\u7167\uff1a\u6b64\u83dc\u5355\u6ca1\u6709\u5b89\u5168\u7684\u539f\u7248\u9002\u914d\u5668";
        else if (!session.getNotice().isEmpty()) message = session.getNotice();
        else if (!goals.isEmpty()) {
            Goal goal = goals.get(goalPage % goals.size());
            String name = goal.description.isEmpty() ? "\u76ee\u6807 " + (goalPage % goals.size() + 1) : goal.description;
            message = (session.isGoalSatisfied(goal) ? "[+] " : "[ ] ") + name;
        } else message = "";
        int messageWidth = Math.max(20, width - 120);
        drawCenteredString(fontRenderer, fontRenderer.trimStringToWidth(message, messageWidth), width / 2, height - 42,
            session.isComplete() ? 0x8EE7A5 : 0xE5E5E5);
        for (GuiButton button : buttonList) {
            if (button.id >= 10 && button.id <= 12) button.enabled = session.getMode().ordinal() != button.id - 10;
            if (button.id == 1) button.displayString = playing ? "\u6682\u505c" : "\u64ad\u653e";
            if (button.id == 20) button.visible = goals.size() > 1;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        int hovered = hit(mouseX, mouseY);
        if (hovered >= 0 && !session.getSlot(hovered).isEmpty()
            && !failedItems.contains(String.valueOf(session.getSlot(hovered).getItem().getRegistryName()))) {
            ItemStack stack = session.getSlot(hovered);
            try { PreviewNativeRenderGuard.run(() -> renderToolTip(stack, mouseX, mouseY)); }
            catch (RuntimeException | LinkageError failure) { failedItem(stack); }
        }
        ItemStack held = session.getCursor();
        if (!held.isEmpty()) {
            RenderHelper.enableGUIStandardItemLighting();
            renderVirtualItem(held, mouseX + 6, mouseY + 6);
            RenderHelper.disableStandardItemLighting();
        }
        GlStateManager.disableDepth();
    }

    private void renderVirtualItem(ItemStack stack, int x, int y) {
        if (failedItems.contains(String.valueOf(stack.getItem().getRegistryName()))) return;
        float previousZ = itemRender.zLevel;
        try {
            PreviewNativeRenderGuard.run(() -> {
                itemRender.renderItemAndEffectIntoGUI(stack, x, y);
                itemRender.renderItemOverlayIntoGUI(fontRenderer, stack, x, y, null);
            });
        } catch (RuntimeException | LinkageError failure) { failedItem(stack); }
        finally { itemRender.zLevel = previousZ; }
    }

    private void failedItem(ItemStack stack) {
        String id = String.valueOf(stack.getItem().getRegistryName());
        failedItems.add(id);
        renderNotice = "\u7269\u54c1\u6e32\u67d3\u5931\u8d25\uff1a" + id;
        playing = false;
    }

    public java.util.Set<String> getRenderFailureKeys() {
        return java.util.Collections.unmodifiableSet(new java.util.HashSet<>(failedItems));
    }

    @Override protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0: playing = false; session.seek(Math.max(0, session.getTick() - 20)); break;
            case 1: playing = !playing; break;
            case 2: playing = false; session.seek(Math.min(session.getDefinition().getDuration(), session.getTick() + 20)); break;
            case 3: playing = false; session.reset(); break;
            case 4: mc.displayGuiScreen(parent); break;
            case 10: case 11: case 12:
                session.setMode(PracticeMode.values()[button.id - 10]); playing = session.getMode() == PracticeMode.WATCH; break;
            case 20: goalPage++; break;
            case 21: session.toggleLock(); break;
            default: break;
        }
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0 && mouseButton != 1) return;
        if (mouseY >= height - 60 && mouseY < height - 54 && mouseX >= 12 && mouseX < width - 12) {
            playing = false;
            session.seek(Math.max(0, Math.min(session.getDefinition().getDuration(),
                (mouseX - 12) * session.getDefinition().getDuration() / Math.max(1, width - 24))));
            return;
        }
        int slot = hit(mouseX, mouseY);
        if (slot >= 0) { playing = false; session.click(slot, mouseButton == 1, isShiftKeyDown()); }
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(parent); return; }
        if (keyCode == Keyboard.KEY_SPACE) { playing = !playing; return; }
        super.keyTyped(typedChar, keyCode);
    }
    @Override public boolean doesGuiPauseGame() { return true; }
    @Override public void onGuiClosed() { playing = false; session.close(); super.onGuiClosed(); }
    private int hit(int x, int y) {
        if (!session.isVisible()) return -1;
        return PracticeLayout.hit(session.getDefinition().getMenu(), (x - left) / scale, (y - top) / scale);
    }
    private static String menuName(Menu menu) {
        switch (menu) {
            case WORKBENCH: return "\u5de5\u4f5c\u53f0";
            case FURNACE: return "\u7194\u7089";
            case CHEST: return "\u7bb1\u5b50";
            case CHEST_LARGE: return "\u5927\u7bb1\u5b50";
            case HOPPER: return "\u6f0f\u6597";
            default: return "\u754c\u9762\u5feb\u7167";
        }
    }
}
