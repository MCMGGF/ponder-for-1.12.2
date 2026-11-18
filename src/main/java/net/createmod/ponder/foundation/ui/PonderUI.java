package net.createmod.ponder.foundation.ui;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

import javax.annotation.Nullable;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.client.PonderLessonPractice;
import net.createmod.ponder.authoring.AuthoringSettings;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.content.VanillaPonderPlugin;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public class PonderUI extends PonderDebugScreen {

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (mc != null && mc.isSingleplayer() && isCtrlKeyDown() && getSelectedScene() != null) {
            if (AuthoringSettings.isEnabled() && keyCode == org.lwjgl.input.Keyboard.KEY_E) {
                try { mc.displayGuiScreen(AuthoringEditorScreen.fork(this, getSelectedScene())); }
                catch (java.io.IOException | RuntimeException invalid) {
                    mc.displayGuiScreen(new AuthoringReviewScreen(this, I18n.format("ponder.authoring.import_review"),
                        String.valueOf(invalid.getMessage()), null));
                }
                return;
            }
            if (keyCode == org.lwjgl.input.Keyboard.KEY_J) {
                PonderLessonPractice practice = getSelectedPractice();
                if (practice != null) {
                    openPractice(practice);
                } else {
                    AuthoringEditorScreen.openPractice(this, getSelectedScene());
                }
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    private static final String UI_LANG_PREFIX = "ponder.ui.";
    private static final int BUTTON_PRACTICE = 180;
    private static final int[] TOOLBAR_IDS = { BUTTON_PREV_SCENE, BUTTON_PREV_STEP, BUTTON_PLAY_PAUSE, BUTTON_NEXT_STEP,
        BUTTON_NEXT_SCENE, BUTTON_START, BUTTON_RESET_CAMERA, BUTTON_SKIN_CURRENT,
        BUTTON_SKIN_STEVE, BUTTON_SKIN_ALEX, BUTTON_CLOSE };
    private final GuiButton[] toolbarButtons = new GuiButton[TOOLBAR_IDS.length];
    private GuiButton practiceButton;
    private int toolbarFontHeight;
    private ResourceLocation practiceNoticeScene;
    private String practiceNotice = "";
    private boolean vanillaTutorialsOnly;
    private ItemStack sourceStack = ItemStack.EMPTY;
    private ResourceLocation stackComponent;
    private final List<ChromeText> footerDraws = new ArrayList<>();
    private final List<ChromeText> hoverDraws = new ArrayList<>();
    private final List<ChromeText> playbackDraws = new ArrayList<>();
    private long chromeFrame;
    private int chromeWidth, chromeHeight, chromeFontHeight;
    private PreviewDebugLayout.Rect chromePlaybackBounds;

    protected PonderUI(@Nullable ResourceLocation componentId, int sceneIndex, @Nullable GuiScreen parentScreen) {
        super(componentId, sceneIndex, true, parentScreen);
    }

    public static PonderUI showcase(@Nullable ResourceLocation componentId, int sceneIndex) {
        return new PonderUI(componentId, sceneIndex, null);
    }

    public static PonderUI showcase(@Nullable ResourceLocation componentId, int sceneIndex,
        @Nullable GuiScreen parentScreen) {
        return new PonderUI(componentId, sceneIndex, parentScreen);
    }

    public static PonderUI showcaseForStack(ItemStack stack, int sceneIndex) {
        ResourceLocation component = PonderIndex.getSceneAccess().resolveComponentId(stack);
        if (component == null) throw new IllegalArgumentException("No matching storyboard for this item stack");
        PonderUI screen = new PonderUI(component, sceneIndex, null);
        screen.sourceStack = stack.copy();
        screen.stackComponent = component;
        return screen;
    }

    public static PonderUI showcaseForTag(ResourceLocation componentId, @Nullable ResourceLocation tag,
        @Nullable GuiScreen parentScreen) {
        PonderUI screen = new PonderUI(componentId, 0, parentScreen);
        screen.vanillaTutorialsOnly = VanillaPonderPlugin.TUTORIAL_TAG.equals(tag);
        return screen;
    }

    @Override
    protected List<PonderScene> compileIndexScenes(ResourceLocation component) {
        if (!sourceStack.isEmpty() && stackComponent.equals(component))
            return PonderIndex.getSceneAccess().compileForStack(sourceStack);
        if (!vanillaTutorialsOnly) return super.compileIndexScenes(component);
        return PonderIndex.getSceneAccess().compile(tutorialEntries(component,
            PonderIndex.getSceneAccess().getRegisteredEntries()));
    }

    @Override
    protected List<ResourceLocation> readIndexComponents() {
        if (!vanillaTutorialsOnly) return super.readIndexComponents();
        java.util.Set<ResourceLocation> components = new java.util.TreeSet<>();
        for (Map.Entry<ResourceLocation, StoryBoardEntry> entry : PonderIndex.getSceneAccess().getRegisteredEntries()) {
            if (entry.getValue().getTags().contains(VanillaPonderPlugin.TUTORIAL_TAG)) {
                components.add(entry.getKey());
            }
        }
        return new ArrayList<>(components);
    }

    @Override
    protected boolean isShowcaseGroupCandidate(PonderTag tag) {
        return vanillaTutorialsOnly ? tag != null && VanillaPonderPlugin.TUTORIAL_TAG.equals(tag.getId())
            : super.isShowcaseGroupCandidate(tag);
    }

    static List<StoryBoardEntry> tutorialEntries(ResourceLocation component,
        Iterable<Map.Entry<ResourceLocation, StoryBoardEntry>> registrations) {
        List<StoryBoardEntry> result = new ArrayList<>();
        for (Map.Entry<ResourceLocation, StoryBoardEntry> registration : registrations) {
            StoryBoardEntry entry = registration.getValue();
            if (component.equals(registration.getKey()) && entry.getTags().contains(VanillaPonderPlugin.TUTORIAL_TAG)) {
                result.add(entry);
            }
        }
        return result;
    }

    private String tr(String key) {
        return I18n.format(UI_LANG_PREFIX + key);
    }

    private String tr(String key, Object... args) {
        return I18n.format(UI_LANG_PREFIX + key, args);
    }

    @Override
    protected String getStandaloneTitle() {
        return tr("showcase.title");
    }

    @Override
    protected boolean allowDebugShortcutFromShowcase() {
        return false;
    }

    @Override
    protected boolean usePlayPauseButtonInShowcase() {
        return true;
    }

    @Override
    protected void initShowcaseButtons() {
        clearChromeDrawRecord();
        toolbarFontHeight = getShowcaseChromeLayout().fontHeight;
        PreviewDebugLayout.Rect[] slots = PreviewLessonControls.layout(width, height, toolbarFontHeight);
        String[] labels = { "<<", "|<", "||", ">|", ">>", "R", "C", tr("skin.current_short"), "S", "A", "X" };
        for (int i = 0; i < TOOLBAR_IDS.length; i++) {
            PreviewDebugLayout.Rect slot = slots[i];
            GuiButton button = new GuiButton(TOOLBAR_IDS[i], slot.x, slot.y, slot.width, slot.height, labels[i]);
            if (button.id == BUTTON_PREV_SCENE) prevSceneButton = button;
            if (button.id == BUTTON_NEXT_SCENE) nextSceneButton = button;
            if (button.id == BUTTON_PLAY_PAUSE) playPauseButton = button;
            if (button.id == BUTTON_START) startButton = button;
            toolbarButtons[i] = button;
            addCompatButton(button);
        }
        practiceButton = new PracticeButton(BUTTON_PRACTICE);
        addCompatButton(practiceButton);
        playing = true;
    }

    @Override
    protected void updateButtonState() {
        super.updateButtonState();
        if (playPauseButton != null) {
            playPauseButton.displayString = isPlaybackRunning() ? "||" : ">";
        }
        if (practiceButton == null) {
            return;
        }
        ResourceLocation sceneId = getSelectedSceneId();
        PonderLessonPractice practice = PonderLessonPractice.find(sceneId);
        boolean hasPractice = practice != null;
        if (!hasPractice || !java.util.Objects.equals(practiceNoticeScene, sceneId)) {
            practiceNotice = "";
        }
        practiceButton.displayString = hasPractice ? practice.getTitle() : "";
        int fontHeight = getShowcaseChromeLayout().fontHeight;
        if (practiceButton.visible != hasPractice || toolbarFontHeight != fontHeight) {
            practiceButton.visible = hasPractice;
            practiceButton.enabled = hasPractice;
            toolbarFontHeight = fontHeight;
            PreviewDebugLayout.Rect[] slots = PreviewLessonControls.layout(width, height, fontHeight, hasPractice);
            int closeIndex = TOOLBAR_IDS.length - 1;
            for (int i = 0; i < closeIndex; i++) {
                placeToolbarButton(toolbarButtons[i], slots[i]);
            }
            placeToolbarButton(toolbarButtons[closeIndex], slots[slots.length - 1]);
            if (hasPractice) {
                placeToolbarButton(practiceButton, slots[closeIndex]);
            }
        }
    }

    private static void placeToolbarButton(GuiButton button, PreviewDebugLayout.Rect slot) {
        button.x = slot.x;
        button.y = slot.y;
        button.width = slot.width;
        button.height = slot.height;
    }

    @Nullable
    private ResourceLocation getSelectedSceneId() {
        PonderScene scene = getSelectedScene();
        return scene == null ? null : scene.getSceneId();
    }

    @Nullable
    private PonderLessonPractice getSelectedPractice() {
        return PonderLessonPractice.find(getSelectedSceneId());
    }

    private void openPractice(PonderLessonPractice practice) {
        practiceNoticeScene = getSelectedSceneId();
        if (mc == null || !mc.isSingleplayer()) {
            practiceNotice = I18n.format("ponder.authoring.singleplayer");
            return;
        }
        playing = false;
        updateButtonState();
        GuiScreen screen;
        try {
            screen = practice.createScreen(this);
        } catch (RuntimeException | LinkageError failure) {
            practiceNotice = practice.getUnavailableNotice();
            Ponder.LOGGER.warn("Cannot open practice for scene " + practiceNoticeScene, failure);
            return;
        }
        practiceNotice = "";
        mc.displayGuiScreen(screen);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws java.io.IOException {
        if (button.id == BUTTON_PRACTICE) {
            PonderLessonPractice practice = getSelectedPractice();
            if (practice != null) openPractice(practice);
            return;
        }
        super.actionPerformed(button);
    }

    private static final class PracticeButton extends GuiButton {
        PracticeButton(int id) {
            super(id, 0, 0, 20, 20, "");
            visible = false;
            enabled = false;
        }

        @Override public void drawButton(net.minecraft.client.Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            String label = displayString;
            displayString = "";
            try { super.drawButton(minecraft, mouseX, mouseY, partialTicks); }
            finally { displayString = label; }
            if (!visible) return;
            float z = minecraft.getRenderItem().zLevel;
            try {
                PreviewRenderState.runIsolated(() -> PreviewNativeRenderGuard.run(() -> {
                    net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
                    minecraft.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(net.minecraft.init.Items.BOOK),
                        x + (width - 16) / 2, y + (height - 16) / 2);
                }));
            } catch (RuntimeException | LinkageError unavailable) {
                minecraft.fontRenderer.drawString("?", x + 7, y + (height - 9) / 2, 0xFFFFFF);
            } finally { minecraft.getRenderItem().zLevel = z; }
        }
    }

    @Override
    protected GuiScreen createDebugScreenFromShowcase() {
        return new PonderDebugScreen(getSelectedComponentId(), getSelectedSceneIndex(), this);
    }

    @Override
    protected String getNoSceneLabel() {
        return tr("showcase.no_scene");
    }

    @Override
    protected String getNoVisibleBlocksLabel() {
        return tr("showcase.no_visible");
    }

    @Override
    protected String getNoVisibleBlocksHint() {
        return tr("showcase.no_visible_hint");
    }

    @Override
    protected String getShowcaseEmptyTitle() {
        return tr("showcase.empty_title");
    }

    @Override
    protected String getShowcaseNoSceneSubtitle() {
        return tr("showcase.no_scene_subtitle");
    }

    @Override
    protected String getHoverHintForPlaybackBar(int estimatedTick, @Nullable PonderScene scene) {
        return scene == null ? tr("hint.scrub")
            : tr("hint.scrub_tick", Integer.valueOf(estimatedTick), Integer.valueOf(getSceneEndTick(scene)));
    }

    @Override
    protected String getHoverHintForGroupSelector() {
        return tr("hint.group");
    }

    @Override
    protected String getHoverHintForNextUp() {
        return tr("hint.next");
    }

    @Override
    protected void drawShowcaseScreen(int mouseX, int mouseY, float partialTicks) {
        beginChromeDrawRecord();
        drawDefaultBackground();
        resetShowcaseTransientState();

        PreviewLayout showcaseLayout = getShowcasePreviewLayout();
        int previewX = showcaseLayout.originX;
        int previewY = showcaseLayout.originY;
        int previewWidth = showcaseLayout.width;
        int previewHeight = showcaseLayout.height;
        int headerX = previewX + 12;
        int headerY = previewY + 10;
        boolean compact = PreviewExplanationLayout.isCompact(width, height);
        if (compact) {
            headerX = previewX + 2;
            headerY = 12;
        }
        float fade = getShowcaseFade(partialTicks);

        drawGradientRect(0, 0, width, height, 0xF40A1016, 0xFF020406);

        PonderScene scene = getSelectedScene();
        ResourceLocation componentId = getSelectedComponentId();
        ItemStack componentStack = createComponentStack(componentId);
        String stackLabel = componentStack.isEmpty() ? tr("showcase.missing_item") : componentStack.getDisplayName();
        String sceneTitle = scene == null ? getShowcaseEmptyTitle() : scene.getTitle();
        String title = sceneTitle;
        String subtitle = componentId == null ? getShowcaseNoSceneSubtitle() : stackLabel;

        drawScenePreview(previewX, previewY, previewWidth, previewHeight, partialTicks);
        drawShowcaseBackdrop(previewX, previewY, previewWidth, previewHeight, fade);
        drawSceneSpaceOverlays(scene, lastPreviewLayout, getRenderTick(scene, partialTicks), fade);
        drawShowcaseHeader(headerX, headerY, compact ? width - headerX - 18 : previewWidth - 58,
            componentStack, title, subtitle, fade);
        if (!compact) drawShowcaseLogo(previewX + previewWidth - 48, previewY + 14, fade);

        int playbackWidth = Math.min(320, Math.max(80, previewWidth - 32));
        int playbackX = previewX + (previewWidth - playbackWidth) / 2;
        int playbackY = getShowcaseChromeLayout().playbackY;
        drawPlaybackBar(playbackX, playbackY, playbackWidth, 3);
        drawNextUpCard(scene, fade);
        drawUserFooter();
        updateExplanationHover(mouseX, mouseY);
        PreviewExplanationLayout regions = PreviewExplanationLayout.showcase(width, height, fontRenderer.FONT_HEIGHT);
        int popupWidth = compact ? regions.model.width : previewWidth - 88;
        drawShowcaseGroupPopup(headerX - 2, regions.model.y - 4, popupWidth, fade);
    }

    @Override
    protected void drawShowcaseBackdrop(int previewX, int previewY, int previewWidth, int previewHeight, float fade) {
        int glowAlpha = (int) (fade * 68.0F) << 24;
        int borderAlpha = (int) (fade * 56.0F) << 24;
        int vignetteAlpha = (int) (fade * 116.0F) << 24;
        drawGradientRect(previewX - 24, previewY - 24, previewX + previewWidth + 24, previewY + previewHeight + 28,
            glowAlpha | 0x1B2430, 0x00000000);
        drawRect(previewX, previewY, previewX + previewWidth, previewY + 1, borderAlpha | 0xE6E0D2);
        drawRect(previewX, previewY + previewHeight - 1, previewX + previewWidth, previewY + previewHeight,
            borderAlpha | 0x2B323A);
        drawRect(previewX, previewY, previewX + 1, previewY + previewHeight, borderAlpha | 0x56606A);
        drawRect(previewX + previewWidth - 1, previewY, previewX + previewWidth, previewY + previewHeight,
            borderAlpha | 0x2B323A);
        drawGradientRect(previewX, previewY, previewX + previewWidth, previewY + 56, vignetteAlpha | 0x05080C,
            0x00000000);
        drawGradientRect(previewX, previewY + previewHeight - 68, previewX + previewWidth, previewY + previewHeight,
            0x00000000, vignetteAlpha | 0x05080C);
    }

    @Override
    protected void drawShowcaseLogo(int x, int y, float fade) {
        super.drawShowcaseLogo(x, y, fade);
    }

    @Override
    protected void drawShowcaseHeader(int x, int y, int width, ItemStack componentStack, String title, String subtitle,
        float fade) {
        if (PreviewExplanationLayout.isCompact(this.width, height)) {
            drawCompactHeader(x, y, width, componentStack, title, subtitle, fade);
            return;
        }
        int boxWidth = Math.max(100, Math.min(340, width));
        int lineHeight = Math.max(12, fontRenderer.FONT_HEIGHT + 2);
        int boxHeight = Math.max(44, 2 * lineHeight + 8);
        int alpha = (int) (fade * 188.0F) << 24;
        drawGradientRect(x, y, x + boxWidth, y + boxHeight, alpha | 0x111822, 0x00000000);
        drawRect(x + 36, y + boxHeight - 1, x + boxWidth - 10, y + boxHeight, ((int) (fade * 96.0F) << 24) | 0xD7DFE7);
        drawRect(x - 2, y + 4, x + 28, y + 34, alpha | 0x10151B);
        drawRect(x - 1, y + 5, x + 27, y + 33, ((int) (fade * 64.0F) << 24) | 0xE7E0D1);
        setShowcaseHeaderIconBounds(x - 1, y + 5, 28);

        if (!componentStack.isEmpty()) {
            renderOverlayItem(componentStack, x + 5, y + 11);
        }

        ShowcaseGroupState groupState = getShowcaseGroupState();
        if (groupState != null && groupState.components.size() > 1) {
            int chipX = x + 16;
            int chipY = y + 22;
            int chipFill = ((int) (fade * 188.0F) << 24) | 0x1A2028;
            int chipBorder = ((int) (fade * 156.0F) << 24) | 0xD7DFE7;
            drawRect(chipX, chipY, chipX + 12, chipY + 10, chipBorder);
            drawRect(chipX + 1, chipY + 1, chipX + 11, chipY + 9, chipFill);
            drawCenteredString(fontRenderer, String.valueOf(groupState.components.size()), chipX + 6, chipY + 1, 0xF2EFE7);
        }

        String sceneIndex = getCompiledSceneCount() == 0 ? ""
            : (getSelectedSceneIndex() + 1) + " / " + getCompiledSceneCount();
        int countWidth = fontRenderer.getStringWidth(sceneIndex);
        drawString(fontRenderer, fontRenderer.trimStringToWidth(title, Math.max(1, boxWidth - 56 - countWidth)),
            x + 36, y + 5, 0xF6F2EA);
        drawString(fontRenderer, fontRenderer.trimStringToWidth(subtitle, Math.max(1, boxWidth - 46)),
            x + 36, y + 5 + lineHeight, 0xAEB8C1);

        if (getCompiledSceneCount() > 0) {
            drawString(fontRenderer, sceneIndex, x + boxWidth - 12 - fontRenderer.getStringWidth(sceneIndex), y + 6,
                0xD7DFE7);
        }
    }

    private void drawCompactHeader(int x, int y, int available, ItemStack stack, String title, String subtitle, float fade) {
        int right = Math.min(this.width - 18, x + Math.max(80, available));
        int fill = ((int) (fade * 188F) << 24) | 0x111822;
        drawRect(x, y, x + 24, y + 26, fill);
        setShowcaseHeaderIconBounds(x, y, 24);
        if (!stack.isEmpty()) renderOverlayItem(stack, x + 4, y + 5);
        String count = getCompiledSceneCount() == 0 ? "" : (getSelectedSceneIndex() + 1) + "/" + getCompiledSceneCount();
        int countWidth = fontRenderer.getStringWidth(count);
        int textX = x + 30;
        drawString(fontRenderer, fontRenderer.trimStringToWidth(title, Math.max(1, right - textX - countWidth - 8)),
            textX, y + 1, 0xF6F2EA);
        drawString(fontRenderer, count, right - countWidth, y + 1, 0xD7DFE7);
        drawString(fontRenderer, fontRenderer.trimStringToWidth(subtitle, Math.max(1, right - textX)),
            textX, y + 1 + Math.max(20, fontRenderer.FONT_HEIGHT + 2), 0xAEB8C1);
    }

    @Override
    protected void drawPlaybackBar(int x, int y, int width, int height) {
        setPlaybackBarBounds(x, y, width, height);
        chromePlaybackBounds = new PreviewDebugLayout.Rect(x, y - 1, width, height + 2);
        PonderScene scene = getSelectedScene();
        int maxTick = scene == null ? 0 : Math.max(1, getSceneEndTick(scene));
        float progress = scene == null ? 0.0F : getPlaybackTickValue() / (float) maxTick;
        int filled = net.minecraft.util.math.MathHelper.clamp((int) (progress * width), 0, width);

        drawRect(x, y, x + width, y + height, 0x44121820);
        drawRect(x, y, x + filled, y + height, 0xFFD7DFE7);
        if (filled > 0) {
            drawRect(x + filled - 1, y - 1, x + filled, y + height + 1, 0xFFFDFBF4);
        }

        String leftLabel = scene == null ? tr("showcase.none_short")
            : tr("showcase.scene_short", Integer.valueOf(getSelectedSceneIndex() + 1));
        String rightLabel = scene == null ? "T+0" : "T+" + getPlaybackTickValue() + " / " + getSceneEndTick(scene);
        int labelY = y - fontRenderer.FONT_HEIGHT - 2;
        int rightWidth = fontRenderer.getStringWidth(rightLabel);
        int leftWidth = Math.max(0, width - rightWidth - 8);
        String fittedLeft = fontRenderer.trimStringToWidth(leftLabel, leftWidth);
        drawString(fontRenderer, fittedLeft, x, labelY, 0xC4CFD7);
        recordChromeText(playbackDraws, fittedLeft, x, labelY);
        drawString(fontRenderer, rightLabel, x + width - rightWidth, labelY, 0xC4CFD7);
        recordChromeText(playbackDraws, rightLabel, x + width - rightWidth, labelY);
    }

    @Override
    protected void drawNextUpCard(PonderScene scene, float fade) {
        setNextUpCardBounds(0, 0, 0, 0);
        PonderScene nextScene = getNextScene();
        if (scene == null || nextScene == null || nextSceneButton == null || width < 680) {
            return;
        }

        int maxTick = Math.max(1, getSceneEndTick(scene));
        if (getPlaybackTickValue() < (int) (maxTick * 0.7F)) {
            return;
        }

        String nextUpLabel = tr("showcase.next_up");
        String nextTitle = fontRenderer.trimStringToWidth(nextScene.getTitle(), 170);
        int boxWidth = Math.max(108,
            Math.max(fontRenderer.getStringWidth(nextUpLabel), fontRenderer.getStringWidth(nextTitle)) + 26);
        int boxX = width - 78 - boxWidth;
        int boxY = 30;
        setNextUpCardBounds(boxX, boxY, boxWidth, 28);

        int fillTop = ((int) (fade * 188.0F) << 24) | 0x111822;
        int fillBottom = ((int) (fade * 188.0F) << 24) | 0x090D12;
        int border = ((int) (fade * 148.0F) << 24) | 0xD7DFE7;
        drawGradientRect(boxX, boxY, boxX + boxWidth, boxY + 28, fillTop, fillBottom);
        drawRect(boxX, boxY, boxX + boxWidth, boxY + 1, border);
        drawRect(boxX, boxY + 27, boxX + boxWidth, boxY + 28, border);
        drawRect(boxX, boxY, boxX + 1, boxY + 28, border);
        drawRect(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + 28, border);
        drawCenteredString(fontRenderer, nextUpLabel, boxX + boxWidth / 2, boxY + 6, 0xAEB8C1);
        drawCenteredString(fontRenderer, nextTitle, boxX + boxWidth / 2, boxY + 17, 0xF3EFE7);
    }

    @Override
    protected void drawShowcaseHoverHints(int mouseX, int mouseY) {
        if (!practiceNotice.isEmpty()) {
            drawShowcaseCenteredHint(practiceNotice, 0xFF9A86);
            return;
        }
        if (drawShowcaseGroupHoverHint(mouseX, mouseY)) {
            return;
        }
        if (isMouseOverPlaybackBar(mouseX, mouseY)) {
            drawShowcaseCenteredHint(getHoverHintForPlaybackBar(estimatePlaybackTickForMouse(mouseX),
                getSelectedScene()), 0xB8C3CC);
            return;
        }
        if (isMouseOverShowcaseHeaderIcon(mouseX, mouseY) && hasShowcaseGroupChoices()) {
            drawShowcaseCenteredHint(getHoverHintForGroupSelector(), 0xB8C3CC);
            return;
        }
        if (isMouseOverNextUpCard(mouseX, mouseY)) {
            drawShowcaseCenteredHint(getHoverHintForNextUp(), 0xB8C3CC);
            return;
        }

        GuiButton hoveredButton = null;
        for (GuiButton button : buttonList) {
            if (button.visible && button.isMouseOver()) {
                hoveredButton = button;
                break;
            }
        }

        if (hoveredButton == null || hoveredButton.displayString == null || hoveredButton.displayString.isEmpty()) {
            return;
        }

        String hint;
        switch (hoveredButton.id) {
            case BUTTON_PREV_SCENE:
                hint = tr("button.previous");
                break;
            case BUTTON_NEXT_SCENE:
                hint = tr("button.next");
                break;
            case BUTTON_CLOSE:
                hint = tr("button.close");
                break;
            case BUTTON_START:
                hint = tr("button.restart");
                break;
            case BUTTON_RESET_CAMERA:
                hint = I18n.format("controls.reset");
                break;
            case BUTTON_PLAY_PAUSE:
                hint = tr(isPlaybackRunning() ? "button.pause" : "button.play");
                break;
            case BUTTON_PREV_STEP:
                hint = tr("button.previous_step");
                break;
            case BUTTON_NEXT_STEP:
                hint = tr("button.next_step");
                break;
            case BUTTON_SKIN_CURRENT:
                hint = tr("skin.current");
                break;
            case BUTTON_SKIN_STEVE:
                hint = tr("skin.steve");
                break;
            case BUTTON_SKIN_ALEX:
                hint = tr("skin.alex");
                break;
            default:
                hint = hoveredButton.displayString;
                break;
        }
        drawShowcaseCenteredHint(hint, 0xB8C3CC);
    }

    @Override
    protected void drawShowcaseHintText(String text, int x, int color) {
        super.drawShowcaseHintText(text, x, color);
        recordChromeText(hoverDraws, text, x, getShowcaseChromeLayout().hoverTextY);
    }

    private void drawUserFooter() {
        PonderScene scene = getSelectedScene();
        String left = scene == null ? tr("footer.no_scene")
            : tr("footer.scene", Integer.valueOf(getSelectedSceneIndex() + 1),
                Integer.valueOf(Math.max(1, getCompiledSceneCount())));
        String middle = scene == null ? "T+0"
            : tr("footer.tick", Integer.valueOf(getPlaybackTickValue()), Integer.valueOf(getSceneEndTick(scene)),
                tr(isPlaybackRunning() ? "footer.playing" : "footer.paused"));
        middle = getRegistryStatus(middle);
        PreviewShowcaseChromeLayout chrome = getShowcaseChromeLayout();
        int y = chrome.footerTextY;
        drawRect(18, chrome.footerBand.y, width - 18, chrome.footerBand.bottom(), 0x66101518);
        int availableWidth = Math.max(1, width - 56);
        if (hasModelControls()) {
            drawModelControls(28, chrome.modelControlsY);
            String status = fontRenderer.trimStringToWidth(getModelStatus(middle), Math.max(1, availableWidth - 92));
            drawString(fontRenderer, status, 120, y, 0xD7DFE7);
            recordChromeText(footerDraws, status, 120, y);
            return;
        }
        if (fontRenderer.getStringWidth(left) + fontRenderer.getStringWidth(middle) + 20 <= availableWidth) {
            drawString(fontRenderer, left, 28, y, 0xD7DFE7);
            recordChromeText(footerDraws, left, 28, y);
            drawString(fontRenderer, middle, width - 28 - fontRenderer.getStringWidth(middle), y, 0xD7DFE7);
            recordChromeText(footerDraws, middle, width - 28 - fontRenderer.getStringWidth(middle), y);
        } else {
            String fitted = fontRenderer.trimStringToWidth(middle, availableWidth);
            drawCenteredString(fontRenderer, fitted, width / 2, y, 0xD7DFE7);
            recordChromeText(footerDraws, fitted, width / 2 - fontRenderer.getStringWidth(fitted) / 2, y);
        }
    }

    private void beginChromeDrawRecord() {
        footerDraws.clear();
        hoverDraws.clear();
        playbackDraws.clear();
        chromePlaybackBounds = null;
        chromeFrame++;
        chromeWidth = width;
        chromeHeight = height;
        chromeFontHeight = fontRenderer.FONT_HEIGHT;
    }

    private void clearChromeDrawRecord() {
        footerDraws.clear();
        hoverDraws.clear();
        playbackDraws.clear();
        chromePlaybackBounds = null;
        chromeWidth = chromeHeight = chromeFontHeight = 0;
    }

    private void recordChromeText(List<ChromeText> target, String text, int x, int y) {
        if (chromeWidth > 0 && text != null && !text.isEmpty())
            target.add(new ChromeText(text, x, y, fontRenderer.getStringWidth(text) + 1, fontRenderer.FONT_HEIGHT + 1));
    }

    /** Immutable observations of actual draw calls, not proposed layout or an acceptance result. */
    public Map<String, Object> getShowcaseChromeDrawRecord() {
        if (chromeWidth <= 0 || chromeHeight <= 0) return Collections.emptyMap();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema", "ponder-showcase-chrome-draw/v1");
        result.put("frameId", chromeFrame);
        result.put("frameWidth", chromeWidth);
        result.put("frameHeight", chromeHeight);
        result.put("fontHeight", chromeFontHeight);
        result.put("footerTexts", chromeTexts(footerDraws));
        result.put("hoverTexts", chromeTexts(hoverDraws));
        result.put("playbackTexts", chromeTexts(playbackDraws));
        result.put("playbackBounds", chromeRect(chromePlaybackBounds));
        List<Map<String, Integer>> controls = new ArrayList<>();
        for (PreviewDebugLayout.Rect rect : getShowcaseModelControlBounds()) controls.add(chromeRect(rect));
        result.put("modelControlBounds", Collections.unmodifiableList(controls));
        return Collections.unmodifiableMap(result);
    }

    private static List<Map<String, Object>> chromeTexts(List<ChromeText> texts) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChromeText text : texts) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("text", text.text);
            row.put("x", text.x);
            row.put("y", text.y);
            row.put("width", text.width);
            row.put("height", text.height);
            result.add(Collections.unmodifiableMap(row));
        }
        return Collections.unmodifiableList(result);
    }

    private static Map<String, Integer> chromeRect(@Nullable PreviewDebugLayout.Rect rect) {
        if (rect == null) return Collections.emptyMap();
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("x", rect.x);
        result.put("y", rect.y);
        result.put("width", rect.width);
        result.put("height", rect.height);
        return Collections.unmodifiableMap(result);
    }

    private static final class ChromeText {
        final String text;
        final int x, y, width, height;
        ChromeText(String text, int x, int y, int width, int height) {
            this.text = text; this.x = x; this.y = y; this.width = width; this.height = height;
        }
    }

    @Override
    public void onGuiClosed() {
        clearChromeDrawRecord();
        super.onGuiClosed();
    }

}
