package net.createmod.ponder.foundation.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.content.VanillaPonderPlugin;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Mouse;

public class PonderTagScreen extends AbstractPonderBrowserScreen {

    private static final int BUTTON_RELOAD = 1;
    private static final int BUTTON_DONE = 2;
    private static final int SUMMARY_HEIGHT = 122;
    private static final int GRID_SLOT_SIZE = 24;
    private static final int GRID_SLOT_GAP = 8;

    private final ResourceLocation tagId;
    private final List<PonderComponentEntry> components = new ArrayList<PonderComponentEntry>();
    private final List<PonderClickRegion<PonderComponentEntry>> visibleComponentRegions =
        new ArrayList<PonderClickRegion<PonderComponentEntry>>();

    private PonderTag tag;
    private int componentScroll;
    private String hoverHint = "";
    private ResourceLocation componentScrollAnchor;

    public PonderTagScreen(ResourceLocation tagId) {
        this(tagId, null);
    }

    public PonderTagScreen(ResourceLocation tagId, @Nullable GuiScreen parentScreen) {
        super(parentScreen);
        this.tagId = tagId;
    }

    public PonderTagScreen(PonderTag tag, @Nullable GuiScreen parentScreen) {
        this(tag.getId(), parentScreen);
    }

    @Override
    public void initGui() {
        super.initGui();
        synchronizeIndex();
        componentScroll = MathHelper.clamp(componentScroll, 0, getMaxComponentScroll());
        visibleComponentRegions.clear();

        clearCompatButtons();
        addFooterButton(BUTTON_RELOAD, OUTER_MARGIN, 60, tr("button.reload"));
        addFooterButton(BUTTON_DONE, width - OUTER_MARGIN - 70, 70, tr("button.done"));
    }

    @Override
    protected void reloadRegistryView() {
        rememberScrollAnchor();
        tag = registeredTag(tagId);
        components.clear();
        if (tag != null) {
            components.addAll(buildComponentEntries(PonderIndex.getTagAccess().getItems(tag),
                VanillaPonderPlugin.TUTORIAL_TAG.equals(tagId) ? tagId : null));
        }
        int columns = getComponentColumns();
        componentScroll = PonderIndexViewState.retain(components, componentScrollAnchor,
            componentScroll * columns, entry -> entry.componentId) / columns;
        componentScroll = MathHelper.clamp(componentScroll, 0, getMaxComponentScroll());
        visibleComponentRegions.clear();
        hoverHint = "";
    }

    private void rememberScrollAnchor() {
        if (!components.isEmpty()) {
            int index = Math.min(componentScroll * getComponentColumns(), components.size() - 1);
            componentScrollAnchor = components.get(index).componentId;
        }
    }

    @Override
    protected void releaseRegistryView() {
        rememberScrollAnchor();
        tag = null;
        components.clear();
        visibleComponentRegions.clear();
        hoverHint = "";
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BUTTON_RELOAD) {
            PonderIndex.reload();
            synchronizeIndex();
        } else if (button.id == BUTTON_DONE) {
            closeToParent();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        if (synchronizeIndex()) {
            return;
        }
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }

        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (!isMouseWithinGrid(mouseX, mouseY)) {
            return;
        }
        componentScroll = clampScroll(componentScroll, wheel > 0 ? -1 : 1, getMaxComponentScroll());
        visibleComponentRegions.clear();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (synchronizeIndex()) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) {
            return;
        }

        PonderComponentEntry entry = getClickedComponent(mouseX, mouseY);
        if (entry != null) {
            mc.displayGuiScreen(PonderUI.showcaseForTag(entry.componentId, tagId, this));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        synchronizeIndex();
        drawPonderBackground();
        hoverHint = "";
        visibleComponentRegions.clear();

        int panelX = OUTER_MARGIN;
        int panelY = OUTER_MARGIN;
        int panelWidth = width - OUTER_MARGIN * 2;
        int contentBottom = height - OUTER_MARGIN - FOOTER_HEIGHT;
        int panelHeight = contentBottom - panelY;

        drawPanel(panelX, panelY, panelWidth, panelHeight);
        drawSummary(panelX, panelY, panelWidth);
        drawComponents(mouseX, mouseY, panelX, panelY + SUMMARY_HEIGHT, panelWidth, panelHeight - SUMMARY_HEIGHT);

        super.drawScreen(mouseX, mouseY, partialTicks);
        drawHintLine(resolveHint());
    }

    private void drawSummary(int x, int y, int panelWidth) {
        drawCenteredString(fontRenderer, tr("tag.title"), width / 2, y + 8, COLOR_TEXT);
        if (tag == null) {
            drawSectionLabel("\u5206\u7ec4\u5df2\u79fb\u9664", x + 16, y + 28);
            drawString(fontRenderer, fontRenderer.trimStringToWidth(tagId.toString(), panelWidth - 32),
                x + 16, y + 46, COLOR_TEXT_DIM);
            return;
        }
        drawClippedString(tag.getTitle(), x + 64, y + 28, panelWidth - 80, COLOR_ACCENT);
        drawTagSlot(tag, x + 16, y + 24, 32, false, true);

        int totalScenes = 0;
        for (PonderComponentEntry entry : components) {
            totalScenes += entry.sceneCount;
        }

        int countColumnWidth = Math.max(0, (panelWidth - 80) / 2);
        drawClippedString(tr("tag.component_count", Integer.valueOf(components.size())), x + 64, y + 44,
            countColumnWidth - 4, COLOR_TEXT_MUTED);
        drawClippedString(tr("tag.scene_count", Integer.valueOf(totalScenes)), x + 64 + countColumnWidth, y + 44,
            countColumnWidth, COLOR_TEXT_MUTED);
        List<String> descriptionLines = getDescriptionLines(tag.getDescription(), panelWidth);
        for (int line = 0; line < descriptionLines.size(); line++) {
            drawString(fontRenderer, descriptionLines.get(line), x + 16, y + 68 + line * fontRenderer.FONT_HEIGHT,
                COLOR_TEXT_DIM);
        }
        drawClippedString(tr("tag.associated_components"), x + 16, y + SUMMARY_HEIGHT - 18,
            panelWidth - 32, COLOR_ACCENT);
    }

    List<String> getDescriptionLines(String description, int panelWidth) {
        List<String> lines = PreviewTextLayout.wrap(fontRenderer, description, Math.max(1, panelWidth - 32));
        int maxLines = Math.max(0, (SUMMARY_HEIGHT - 18 - 68 - 4) / Math.max(1, fontRenderer.FONT_HEIGHT));
        return lines.subList(0, Math.min(lines.size(), maxLines));
    }

    private void drawComponents(int mouseX, int mouseY, int x, int y, int panelWidth, int panelHeight) {
        int innerX = x + 16;
        int innerY = y + 10;
        int innerWidth = panelWidth - 32;
        int columns = Math.max(1, (innerWidth + GRID_SLOT_GAP) / (GRID_SLOT_SIZE + GRID_SLOT_GAP));
        int totalRows = (int) Math.ceil(components.size() / (double) columns);
        int rowsVisible = Math.max(1, (panelHeight - 26) / (GRID_SLOT_SIZE + GRID_SLOT_GAP));
        int startRow = Math.min(componentScroll, Math.max(0, totalRows - rowsVisible));
        int startIndex = startRow * columns;
        int endIndex = Math.min(components.size(), startIndex + rowsVisible * columns);

        if (components.isEmpty()) {
            drawClippedString(tr("tag.no_components"), innerX, innerY, innerWidth, COLOR_TEXT_DIM);
            return;
        }

        for (int index = startIndex; index < endIndex; index++) {
            PonderComponentEntry entry = components.get(index);
            int localIndex = index - startIndex;
            int column = localIndex % columns;
            int row = localIndex / columns;
            int slotX = innerX + column * (GRID_SLOT_SIZE + GRID_SLOT_GAP);
            int slotY = innerY + row * (GRID_SLOT_SIZE + GRID_SLOT_GAP);
            boolean hovered = isWithin(mouseX, mouseY, slotX, slotY, GRID_SLOT_SIZE, GRID_SLOT_SIZE);

            drawItemSlot(entry.displayStack, slotX, slotY, GRID_SLOT_SIZE, hovered, false);
            visibleComponentRegions.add(new PonderClickRegion<PonderComponentEntry>(entry, slotX, slotY,
                GRID_SLOT_SIZE, GRID_SLOT_SIZE));

            if (hovered) {
                hoverHint = tr("tag.hover.component", entry.label, Integer.valueOf(entry.sceneCount));
            }
        }

        drawClippedString(tr("tag.click_to_open"), x + 16, y + panelHeight - 16, innerWidth, COLOR_TEXT_MUTED);
    }

    private int getMaxComponentScroll() {
        int columns = getComponentColumns();
        int panelHeight = height - OUTER_MARGIN - FOOTER_HEIGHT - OUTER_MARGIN - SUMMARY_HEIGHT;
        int rowsVisible = Math.max(1, (panelHeight - 26) / (GRID_SLOT_SIZE + GRID_SLOT_GAP));
        int totalRows = (int) Math.ceil(components.size() / (double) columns);
        return Math.max(0, totalRows - rowsVisible);
    }

    private int getComponentColumns() {
        int innerWidth = width - OUTER_MARGIN * 2 - 32;
        return Math.max(1, (innerWidth + GRID_SLOT_GAP) / (GRID_SLOT_SIZE + GRID_SLOT_GAP));
    }

    private boolean isMouseWithinGrid(int mouseX, int mouseY) {
        return isWithin(mouseX, mouseY, OUTER_MARGIN, OUTER_MARGIN + SUMMARY_HEIGHT, width - OUTER_MARGIN * 2,
            height - OUTER_MARGIN - FOOTER_HEIGHT - (OUTER_MARGIN + SUMMARY_HEIGHT));
    }

    @Nullable
    private PonderComponentEntry getClickedComponent(int mouseX, int mouseY) {
        for (PonderClickRegion<PonderComponentEntry> region : visibleComponentRegions) {
            if (region.contains(mouseX, mouseY)) {
                return region.value;
            }
        }
        return null;
    }

    private String resolveHint() {
        if (!hoverHint.isEmpty()) {
            return hoverHint;
        }
        return tag == null ? "\u8be5\u5206\u7ec4\u5df2\u4e0d\u5728\u5f53\u524d\u7d22\u5f15\u4e2d"
            : tag.getTitle() + "  |  " + tag.getDescription();
    }
}
