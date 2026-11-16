package net.createmod.ponder.foundation.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.createmod.ponder.foundation.content.oil.OilLineOperatingSession;
import net.createmod.ponder.foundation.content.oil.OilLineOperatingSession.Action;
import net.createmod.ponder.foundation.content.oil.OilLineOperatingSession.Station;
import net.createmod.ponder.foundation.content.oil.OilLineOperatingSession.Vessel;
import net.createmod.ponder.foundation.ui.OilLineControlLayout.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.client.config.GuiCheckBox;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** A local process exercise, not the actual MMCE machine screen or a real inventory. */
public final class OilLineControlScreen extends AuthoringGuiScreen implements PonderOwnedChildScreen {
    private static final String[] STATIONS = {"锅炉", "抽油", "分馏", "储存", "发电"};
    private final GuiScreen parent;
    private final OilLineOperatingSession session;
    private final Map<Integer, String> tooltips = new HashMap<>();
    private final Map<Integer, Choice> actionButtons = new HashMap<>();
    private OilLineControlLayout layout;
    private Station station = Station.BOILER;
    private int compactView = 1;
    private int statsPage, goalIndex, goalPage, actionPage, feedbackPage;
    private int speed = 1;
    private boolean playing;
    private String lastFeedback = "";
    private String renderNotice = "";

    public OilLineControlScreen(GuiScreen parent) {
        this(parent, new OilLineOperatingSession());
    }

    OilLineControlScreen(GuiScreen parent, OilLineOperatingSession session) {
        if (session == null) throw new IllegalArgumentException("A local session is required");
        this.parent = parent;
        this.session = session;
    }

    @Override public GuiScreen getPonderParent() { return parent; }
    public OilLineOperatingSession getSession() { return session; }
    public boolean isPlaying() { return playing; }

    @Override public void initGui() {
        super.initGui();
        layout = new OilLineControlLayout(width, height, fontRenderer.FONT_HEIGHT);
        rebuildControls();
    }

    private void rebuildControls() {
        clearCompatButtons();
        tooltips.clear();
        actionButtons.clear();
        for (int i = 0; i < STATIONS.length; i++) {
            GuiButton button = new StationButton(100 + i, layout.stations[i], STATIONS[i], stationIcon(i));
            button.enabled = i != station.ordinal();
            addCompatButton(button);
            tooltips.put(button.id, STATIONS[i]);
        }
        if (layout.compact) {
            String[] views = {"状态", "操作", "目标"};
            for (int i = 0; i < views.length; i++) {
                GuiButton button = tool(110 + i, layout.views[i], views[i], views[i]);
                button.enabled = compactView != i;
            }
        }
        String[] labels = {"<", "R", playing ? "||" : ">", "x1", "x20", "+20s"};
        String[] tips = {"返回三维教学", "重置本次独立推演", playing ? "暂停推演" : "运行推演",
            "正常时间：每刻推进一刻", "二十倍时间：每刻推进二十刻", "单次推进二十秒（400 游戏刻）"};
        for (int i = 0; i < labels.length; i++) {
            GuiButton button = tool(i + 1, layout.transport[i], labels[i], tips[i]);
            button.enabled = !session.isClosed() && (i != 3 || speed != 1) && (i != 4 || speed != 20);
            if (i == 0) button.enabled = true;
        }
        if (!layout.compact || compactView == 0) pager(200, layout.stats, 2,
            new String[] {"上一页状态", "下一页状态"});
        if (!layout.compact || compactView == 2) pager(210, layout.goals, 4,
            new String[] {"上一项目标", "上一页目标文字", "下一页目标文字", "下一项目标"});
        if (!layout.compact || compactView == 1) {
            pager(220, layout.actions, 2, new String[] {"上一页操作", "下一页操作"});
            List<Choice> choices = choices(station);
            int pageSize = layout.actionsPerPage();
            actionPage = Math.max(0, Math.min(actionPage, OilLineControlLayout.pageCount(choices.size(), pageSize) - 1));
            int start = OilLineControlLayout.pageStart(actionPage, choices.size(), pageSize);
            List<Rect> cells = layout.actionCells(choices.size(), actionPage);
            for (int i = 0; i < cells.size(); i++) {
                Choice choice = choices.get(start + i);
                Rect cell = cells.get(i);
                int id = 1000 + start + i;
                String label = fontRenderer.trimStringToWidth(choice.label, Math.max(1, cell.width - (choice.toggle ? 18 : 8)));
                GuiButton button;
                if (choice.toggle) {
                    button = new GuiCheckBox(id, cell.x, cell.y, label, checked(choice.action));
                    button.width = cell.width;
                    button.height = cell.height;
                } else {
                    button = new FittedButton(id, cell.x, cell.y, cell.width, cell.height, label);
                    button.enabled = choice.vessel == null ? choice.action != Action.INSTALL_COMPONENTS
                        || !session.isComponentsInstalled(station) : session.getVessel() != choice.vessel;
                }
                addCompatButton(button);
                tooltips.put(id, choice.label);
                actionButtons.put(id, choice);
            }
        }
        Rect[] feedbackControls = layout.feedbackPager();
        if (feedbackControls.length == 2) {
            tool(230, feedbackControls[0], "<", "上一页反馈");
            tool(231, feedbackControls[1], ">", "下一页反馈");
        }
    }

    private GuiButton tool(int id, Rect rect, String label, String tip) {
        GuiButton button = addCompatButton(new FittedButton(id, rect.x, rect.y, rect.width, rect.height,
            fontRenderer.trimStringToWidth(label, Math.max(1, rect.width - 6))));
        tooltips.put(id, tip);
        return button;
    }

    private void pager(int firstId, Rect area, int count, String[] tips) {
        Rect[] cells = layout.pager(area, count);
        for (int i = 0; i < count; i++) tool(firstId + i, cells[i],
            count == 4 ? new String[] {"|<", "<", ">", ">|"}[i] : i == 0 ? "<" : ">", tips[i]);
    }

    @Override public void updateScreen() {
        if (playing && !session.isClosed()) session.advance(speed);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        PreviewRenderState.runIsolated(() -> drawContents(mouseX, mouseY, partialTicks));
    }

    private void drawContents(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        String title = (session.isComplete() ? "推演完成" : "石油推演") + " · " + STATIONS[station.ordinal()] + "  T+" + session.getTick();
        if (layout.title.height >= fontRenderer.FONT_HEIGHT)
            drawString(fontRenderer, fontRenderer.trimStringToWidth(title, layout.title.width), layout.title.x,
                layout.title.y, session.isComplete() ? 0xA9E4A0 : 0xEDF0EF);
        if (layout.marker.height >= fontRenderer.FONT_HEIGHT)
            drawString(fontRenderer, fontRenderer.trimStringToWidth("独立推演 · 温控/管速简化", layout.marker.width),
                layout.marker.x, layout.marker.y, 0xA8C9C4);
        drawRect(8, layout.body.y - 2, width - 8, layout.body.y - 1, 0xFF465250);
        if (!layout.compact) {
            int divider = layout.stats.right() + 2;
            drawRect(divider, layout.body.y, divider + 1, layout.body.bottom(), 0xFF465250);
            drawRect(layout.goals.x, layout.goals.y - 2, layout.goals.right(), layout.goals.y - 1, 0xFF465250);
        }
        if (!layout.compact || compactView == 0) {
            heading(layout.stats, "状态 · " + STATIONS[station.ordinal()], 0xB8DED7);
            List<String> lines = wrapped(String.join("\n", session.getStationStats(station)), layout.textArea(layout.stats).width);
            statsPage = drawPage(layout.stats, lines, statsPage, 0xE2E7E5);
            pageState(200, statsPage, lines.size(), layout.textRows(layout.stats));
        }
        if (!layout.compact || compactView == 2) drawGoals();
        if (!layout.compact || compactView == 1) {
            heading(layout.actions, "操作 · " + STATIONS[station.ordinal()], 0xDFD1A4);
            pageState(220, actionPage, choices(station).size(), layout.actionsPerPage());
        }
        String feedback = renderNotice.isEmpty() ? session.getFeedback() : renderNotice;
        if (!feedback.equals(lastFeedback)) { feedbackPage = 0; lastFeedback = feedback; }
        if (layout.feedback.height >= fontRenderer.FONT_HEIGHT) {
            List<String> feedbackLines = wrapped(feedback, layout.feedbackText().width);
            feedbackPage = Math.max(0, Math.min(feedbackPage, feedbackLines.size() - 1));
            Rect feedbackText = layout.feedbackText();
            drawRect(8, layout.feedback.y - 2, width - 8, layout.feedback.y - 1, 0xFF465250);
            drawString(fontRenderer, feedbackLines.get(feedbackPage), feedbackText.x, feedbackText.y,
                feedback.contains("不足") || feedback.contains("错误") || feedback.contains("未安装")
                    ? 0xF1A3A3 : session.isComplete() ? 0xA9E4A0 : 0xE9D799);
            pageState(230, feedbackPage, feedbackLines.size(), 1);
        }
        for (GuiButton button : buttonList) {
            if (button.id == 3) button.displayString = playing ? "||" : ">";
            Choice choice = actionButtons.get(button.id);
            if (choice != null && button instanceof GuiCheckBox) ((GuiCheckBox) button).setIsChecked(checked(choice.action));
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        for (GuiButton button : buttonList) {
            if (button.visible && button.isMouseOver() && tooltips.containsKey(button.id)) {
                String tooltip = button.id >= 100 && button.id < 105 ? stationTooltip(button.id - 100) : tooltips.get(button.id);
                drawHoveringText(wrapped(tooltip, Math.max(40, Math.min(280, width - 24))), mouseX, mouseY);
                break;
            }
        }
    }

    private void heading(Rect rect, String text, int color) {
        if (!layout.compact) drawString(fontRenderer, fontRenderer.trimStringToWidth(text, rect.width), rect.x + 2, rect.y, color);
    }

    private void drawGoals() {
        List<String> goals = session.getGoals();
        if (goals.isEmpty()) return;
        goalIndex = Math.max(0, Math.min(goalIndex, goals.size() - 1));
        boolean complete = session.isGoalComplete(goalIndex);
        heading(layout.goals, "目标 " + (goalIndex + 1) + "/" + goals.size(), complete ? 0xA9E4A0 : 0xDFD1A4);
        List<String> lines = wrapped((complete ? "[+] " : "[ ] ") + (goalIndex + 1) + "/" + goals.size()
            + " " + goals.get(goalIndex), layout.textArea(layout.goals).width);
        goalPage = drawPage(layout.goals, lines, goalPage, complete ? 0xA9E4A0 : 0xE2E7E5);
        enable(210, goalIndex > 0);
        enable(211, goalPage > 0);
        enable(212, goalPage + 1 < OilLineControlLayout.pageCount(lines.size(), layout.textRows(layout.goals)));
        enable(213, goalIndex + 1 < goals.size());
    }

    private int drawPage(Rect panel, List<String> lines, int page, int color) {
        Rect area = layout.textArea(panel);
        int rows = layout.textRows(panel);
        int bounded = Math.max(0, Math.min(page, OilLineControlLayout.pageCount(lines.size(), rows) - 1));
        int start = bounded * rows;
        for (int i = 0; i < rows && start + i < lines.size() && (i + 1) * layout.lineHeight <= area.height; i++)
            drawString(fontRenderer, lines.get(start + i), area.x, area.y + i * layout.lineHeight, color);
        return bounded;
    }

    private List<String> wrapped(String text, int width) {
        return PreviewTextLayout.wrap(fontRenderer, text == null ? "" : text, Math.max(1, width));
    }

    private void pageState(int firstId, int page, int count, int size) {
        enable(firstId, page > 0);
        enable(firstId + 1, page + 1 < OilLineControlLayout.pageCount(count, size));
    }

    private void enable(int id, boolean value) {
        for (GuiButton button : buttonList) if (button.id == id) button.enabled = value;
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 1) { returnToParent(); return; }
        if (session.isClosed()) return;
        if (button.id == 2) {
            playing = false; session.reset(); station = Station.BOILER; compactView = 1;
            statsPage = goalIndex = goalPage = actionPage = feedbackPage = 0;
        } else if (button.id == 3) playing = !playing;
        else if (button.id == 4 || button.id == 5) speed = button.id == 4 ? 1 : 20;
        else if (button.id == 6) { playing = false; session.advance(400); }
        else if (button.id >= 100 && button.id < 105) {
            playing = false; station = Station.values()[button.id - 100]; statsPage = actionPage = 0;
        } else if (button.id >= 110 && button.id < 113) { playing = false; compactView = button.id - 110; }
        else if (button.id == 200 || button.id == 201) { playing = false; statsPage += button.id == 200 ? -1 : 1; }
        else if (button.id == 210 || button.id == 213) { playing = false; goalIndex += button.id == 210 ? -1 : 1; goalPage = 0; }
        else if (button.id == 211 || button.id == 212) { playing = false; goalPage += button.id == 211 ? -1 : 1; }
        else if (button.id == 220 || button.id == 221) { playing = false; actionPage += button.id == 220 ? -1 : 1; }
        else if (button.id == 230 || button.id == 231) { playing = false; feedbackPage += button.id == 230 ? -1 : 1; }
        else if (actionButtons.containsKey(button.id)) {
            playing = false;
            Choice choice = actionButtons.get(button.id);
            if (choice.vessel != null) session.setVessel(choice.vessel); else operate(choice.action);
            feedbackPage = 0;
        }
        if (layout != null) rebuildControls();
    }

    void operate(Action action) { playing = false; session.act(station, action); feedbackPage = 0; }

    private String stationTooltip(int index) {
        return STATIONS[index] + "：" + session.getStationStatus(Station.values()[index]) + "\n"
            + stationProgressLabel(index) + "\n" + (renderNotice.isEmpty() ? session.getFeedback() : renderNotice)
            + "\n独立推演，温控与管速简化，不改变真实机器。";
    }

    private String stationProgressLabel(int index) {
        switch (index) {
            case 0: return "预热：" + session.getTemperature() + " / 100℃（达到工作温度）";
            case 1: return "本轮抽取：" + session.getPumpProgress() + " / 400 刻";
            case 2: return "本批分馏：" + session.getTowerProgress() + " / 600 刻";
            case 3: return "原油储罐：" + session.getOilTank() + " / 16000 mB";
            default: return "电容库：" + session.getCapacitorEnergy() + " / 1000000 FE";
        }
    }

    private double stationProgress(int index) {
        double value;
        switch (index) {
            case 0: value = session.getTemperature() / 100D; break;
            case 1: value = session.getPumpProgress() / 400D; break;
            case 2: value = session.getTowerProgress() / 600D; break;
            case 3: value = session.getOilTank() / 16000D; break;
            default: value = session.getCapacitorEnergy() / 1000000D; break;
        }
        return Math.max(0D, Math.min(1D, value));
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || session.isClosed() || layout == null) return;
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int change = wheel > 0 ? -1 : 1;
        if (layout.feedback.contains(x, y)) feedbackPage += change;
        else if ((!layout.compact || compactView == 0) && layout.stats.contains(x, y)) statsPage += change;
        else if ((!layout.compact || compactView == 2) && layout.goals.contains(x, y)) goalPage += change;
        else if ((!layout.compact || compactView == 1) && layout.actions.contains(x, y)) actionPage += change;
        else return;
        playing = false;
        rebuildControls();
    }

    @Override protected void keyTyped(char character, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) { returnToParent(); return; }
        if (key == Keyboard.KEY_SPACE && !session.isClosed()) { playing = !playing; rebuildControls(); return; }
        super.keyTyped(character, key);
    }

    private void returnToParent() { playing = false; mc.displayGuiScreen(parent); }
    @Override public boolean doesGuiPauseGame() { return true; }
    @Override public void onGuiClosed() { playing = false; session.close(); tooltips.clear(); actionButtons.clear(); super.onGuiClosed(); }

    private boolean checked(Action action) {
        switch (action) {
            case TOGGLE_STEAM_LINE: return session.isSteamLineOpen();
            case TOGGLE_OIL_OUTPUT: return session.isOilOutputOpen();
            case TOGGLE_TOWER_STEAM: return session.isTowerSteamOpen();
            case TOGGLE_DIESEL_FEED: return session.isDieselFeedOpen();
            case TOGGLE_ENERGY_OUTPUT: return session.isEnergyOutputOpen();
            case TOGGLE_LOAD: return session.isLoadEnabled();
            case TOGGLE_MIXED_PIPE: return session.isMixedPipe();
            default: return false;
        }
    }

    private static List<Choice> choices(Station station) {
        List<Choice> choices = new ArrayList<>();
        if (station != Station.STORAGE) choices.add(new Choice("安装本站组件", Action.INSTALL_COMPONENTS, false));
        switch (station) {
            case BOILER:
                choices.add(new Choice("加入一桶水", Action.ADD_WATER, false));
                choices.add(new Choice("加入一份煤", Action.ADD_COAL, false));
                choices.add(new Choice("蒸汽干线", Action.TOGGLE_STEAM_LINE, true)); break;
            case PUMP:
                choices.add(new Choice("勘探本区油藏", Action.SURVEY, false));
                choices.add(new Choice("原油输出", Action.TOGGLE_OIL_OUTPUT, true));
                choices.add(new Choice("模拟串液故障", Action.TOGGLE_MIXED_PIPE, true)); break;
            case TOWER:
                choices.add(new Choice("投入一桶原油", Action.ADD_OIL, false));
                choices.add(new Choice("分馏供汽", Action.TOGGLE_TOWER_STEAM, true)); break;
            case STORAGE:
                choices.add(new Choice("普通桶", Vessel.BUCKET)); choices.add(new Choice("流体单元", Vessel.CELL));
                choices.add(new Choice("收集柴油", Action.COLLECT_DIESEL, false));
                choices.add(new Choice("收集润滑油", Action.COLLECT_LUBRICANT, false));
                choices.add(new Choice("收集沥青", Action.COLLECT_BITUMEN, false)); break;
            case GENERATOR:
                choices.add(new Choice("柴油供给", Action.TOGGLE_DIESEL_FEED, true));
                choices.add(new Choice("能量输出", Action.TOGGLE_ENERGY_OUTPUT, true));
                choices.add(new Choice("连接用电负载", Action.TOGGLE_LOAD, true)); break;
            default: break;
        }
        return choices;
    }

    private static ItemStack stationIcon(int index) {
        switch (index) {
            case 0: return new ItemStack(Blocks.FURNACE);
            case 1: return new ItemStack(Items.IRON_PICKAXE);
            case 2: return new ItemStack(Items.BREWING_STAND);
            case 3: return new ItemStack(Items.BUCKET);
            default: return new ItemStack(Items.REDSTONE);
        }
    }

    private static final class Choice {
        final String label;
        final Action action;
        final Vessel vessel;
        final boolean toggle;
        Choice(String label, Action action, boolean toggle) { this.label = label; this.action = action; this.toggle = toggle; vessel = null; }
        Choice(String label, Vessel vessel) { this.label = label; this.vessel = vessel; action = null; toggle = false; }
    }

    private final class StationButton extends GuiButton {
        private final String label;
        private final ItemStack icon;
        StationButton(int id, Rect rect, String label, ItemStack icon) {
            super(id, rect.x, rect.y, rect.width, rect.height, ""); this.label = label; this.icon = icon;
        }
        @Override public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            super.drawButton(minecraft, mouseX, mouseY, partialTicks);
            if (!visible) return;
            if (!enabled) drawRect(x + 1, y + 1, x + width - 1, y + 3, 0xFF8DBF8E);
            int iconY = y + (height - 16) / 2;
            boolean iconOnly = minecraft.fontRenderer.getStringWidth(label) > width - 26;
            int iconX = iconOnly ? x + (width - 16) / 2 : x + 4;
            float previousZ = minecraft.getRenderItem().zLevel;
            try {
                PreviewNativeRenderGuard.run(() -> {
                    RenderHelper.enableGUIStandardItemLighting();
                    minecraft.getRenderItem().renderItemAndEffectIntoGUI(icon, iconX, iconY);
                    RenderHelper.disableStandardItemLighting();
                });
            } catch (RuntimeException | LinkageError failure) { renderNotice = "站点图标显示失败，操作与状态仍可使用。"; }
            finally { minecraft.getRenderItem().zLevel = previousZ; }
            GlStateManager.disableDepth(); GlStateManager.depthMask(false);
            if (!iconOnly) drawString(minecraft.fontRenderer, label,
                x + 23, y + (height - minecraft.fontRenderer.FONT_HEIGHT) / 2, 0xE5ECE9);
            int index = id - 100;
            int[] colors = {0xFFE4AD62, 0xFF78CFC5, 0xFFE5D271, 0xFF8DBF8E, 0xFFE49898};
            drawRect(x + 1, y + height - 3, x + width - 1, y + height - 1, 0xFF35413D);
            int fill = (int) Math.round(Math.max(0, width - 2) * stationProgress(index));
            if (fill > 0) drawRect(x + 1, y + height - 3, x + 1 + fill, y + height - 1, colors[index]);
        }
    }

    private static final class FittedButton extends GuiButton {
        FittedButton(int id, int x, int y, int width, int height, String text) { super(id, x, y, width, height, text); }
        @Override public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            String label = displayString;
            displayString = "";
            try { super.drawButton(mc, mouseX, mouseY, partialTicks); }
            finally { displayString = label; }
            if (visible) {
                int color = !enabled ? 0xA1AAA6 : hovered ? 0xFFF0B0 : 0xE5ECE9;
                mc.fontRenderer.drawStringWithShadow(label, x + (width - mc.fontRenderer.getStringWidth(label)) / 2F,
                    y + (height - mc.fontRenderer.FONT_HEIGHT) / 2F, color);
            }
        }
    }
}
