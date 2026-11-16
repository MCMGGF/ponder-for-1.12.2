package net.createmod.ponder.foundation.ui;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PreviewActorTimeline;
import net.createmod.ponder.foundation.Vec3iAccessor;
import net.createmod.ponder.foundation.PonderScene.RecordedOperation;
import net.createmod.ponder.foundation.PonderScene.WorldEvent;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.content.MmcePonderPlugin;
import net.createmod.ponder.foundation.content.mmce.MmceMachineIndex;
import net.createmod.ponder.foundation.content.mmce.MmceStaticModelMesh;
import net.createmod.ponder.foundation.content.mmce.MmceStaticModelPreviews;
import net.createmod.ponder.foundation.content.mmce.MmceStructureSnapshot;
import net.createmod.ponder.foundation.ui.PonderScenePreview.PreviewBounds;
import net.createmod.ponder.api.PonderPalette;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class PonderDebugScreen extends CompatGuiScreen {

    private static final int OUTER_MARGIN = 12;
    private static final int LINE_HEIGHT = 12;
    private static final int SHOWCASE_MARGIN = 18;
    private static final int SHOWCASE_BAR_WIDTH = 320;
    private static final int SHOWCASE_BAR_BOTTOM_OFFSET = 14;
    private static final int SHOWCASE_CAPTION_SIDE_CLEARANCE = 16;
    private static final int EXPLANATION_HOVER_TICKS = 40;
    private static final int PREVIEW_FULL_BRIGHT = 0x00F000F0;
    private static final Set<String> WARNED_RENDER_FAILURES = Collections.synchronizedSet(new HashSet<String>());
    protected static final int SHOWCASE_ICON_SIZE = 20;
    protected static final int SHOWCASE_ICON_U_LEFT = 0;
    protected static final int SHOWCASE_ICON_U_CLOSE = 16;
    protected static final int SHOWCASE_ICON_U_RIGHT = 32;
    protected static final int SHOWCASE_ICON_U_REPLAY = 64;
    protected static final int SHOWCASE_ICON_V = 32;
    protected static final int BUTTON_PREV_SCENE = 1;
    protected static final int BUTTON_NEXT_SCENE = 2;
    protected static final int BUTTON_PLAY_PAUSE = 3;
    protected static final int BUTTON_RELOAD = 4;
    protected static final int BUTTON_CLOSE = 5;
    protected static final int BUTTON_START = 6;
    protected static final int BUTTON_END = 7;
    protected static final int BUTTON_DEBUG = 8;
    protected static final int BUTTON_RESET_CAMERA = 9;
    protected static final int BUTTON_PREV_STEP = 13;
    protected static final int BUTTON_NEXT_STEP = 14;
    protected static final int BUTTON_SKIN_CURRENT = 15;
    protected static final int BUTTON_SKIN_STEVE = 16;
    protected static final int BUTTON_SKIN_ALEX = 17;
    private static final int BUTTON_DEBUG_PREVIEW = 10;
    private static final int BUTTON_DEBUG_DETAILS = 11;
    private static final int BUTTON_DEBUG_TIMELINE = 12;
    private static final Method BLOCKSTATE_ACTUAL_STATE = resolveBlockStateMethod();
    private static final Method BLOCK_RENDER_LAYER = resolveBlockRenderLayerMethod();

    @Nullable
    private final GuiScreen parentScreen;
    private final ResourceLocation requestedComponentId;
    private final int requestedSceneIndex;
    private final boolean showcaseMode;

    private List<ResourceLocation> componentIds = Collections.emptyList();
    private List<PonderScene> compiledScenes = Collections.emptyList();
    private int selectedComponentIndex;
    private int selectedSceneIndex;
    private int componentScroll;
    private int operationScroll;
    private int summaryScroll;
    private int summaryLineCount;
    private int debugTab;
    private PreviewDebugLayout debugLayout;
    private int playbackTick;
    private final PreviewSoundPlayback previewAudio = new PreviewSoundPlayback();
    protected boolean playing;

    protected GuiButton prevSceneButton;
    protected GuiButton nextSceneButton;
    protected GuiButton playPauseButton;
    protected GuiButton startButton;
    protected GuiButton endButton;
    protected GuiButton debugButton;
    protected PreviewLayout lastPreviewLayout;
    private float previewYaw = -45.0F;
    private float previewPitch = PreviewCameraMath.DEFAULT_PITCH;
    private float previewZoom = 1.0F;
    private boolean previewCameraManual;
    private PreviewCameraTimeline.View manualCameraView;
    private PonderScene evaluatedCameraScene;
    private float evaluatedCameraTick = Float.NaN;
    private Vec3d evaluatedCameraDefault;
    private PreviewCameraTimeline.View evaluatedCameraView;
    private boolean initialized;
    private final PonderIndexViewState registryView = new PonderIndexViewState();
    private RetainedSceneView closedView;
    private PreviewBounds lastSceneBounds;
    private String registryNotice = "";
    private int registryNoticeTicks;
    private final PreviewPointerInput pointerInput = new PreviewPointerInput();
    private int previewDragMouseX;
    private int previewDragMouseY;
    private int showcaseFadeTicks;
    private DetachedPonderWorld detachedPreviewWorld;
    private final Map<String, NBTTagCompound> previewNbtCache = new LinkedHashMap<String, NBTTagCompound>();
    private final Set<String> previewRenderFailureKeys = new LinkedHashSet<>();
    private final PreviewOverlayHitTest overlayHitAreas = new PreviewOverlayHitTest();
    private final PreviewFrameCache<PreviewFrame> previewFrames = new PreviewFrameCache<PreviewFrame>();
    private PreviewFrame appliedWorldFrame;
    private final Map<PonderScene, Integer> sceneEndTicks = new LinkedHashMap<PonderScene, Integer>();
    private PreviewCaptionPaging captionPaging = new PreviewCaptionPaging();
    private PreviewExplanationLayout explanationLayout;
    private List<Object> explanationIdentity = Collections.emptyList();
    private List<List<PonderScene.OverlayEvent>> explanationImages = Collections.emptyList();
    private String explanationHoverText;
    private int explanationHoverTicks;
    private final PreviewModelMode<MmceStaticModelPreviews.Preview> modelMode = new PreviewModelMode<>();
    private final PreviewModelFraming modelFraming = new PreviewModelFraming();
    private final PreviewStaticModelRenderer modelRenderer = new PreviewStaticModelRenderer();
    private final PreviewPlayerRenderer playerRenderer = new PreviewPlayerRenderer();
    private final PreviewItemRenderer itemRenderer = new PreviewItemRenderer();
    private final PreviewOutlineRenderer outlineRenderer = new PreviewOutlineRenderer();
    private BlockPos editorPickMarker;
    private final Map<Object, CalloutView> calloutViews = new LinkedHashMap<>();
    private List<CalloutView> visibleCallouts = Collections.emptyList();
    private final Set<PonderGuiSnapshotRegistry.SnapshotProvider> usedSnapshotProviders =
        Collections.newSetFromMap(new java.util.IdentityHashMap<PonderGuiSnapshotRegistry.SnapshotProvider, Boolean>());
    private PreviewDebugLayout.Rect externalOverlayArea;
    private PreviewWorldProjection worldProjection;
    private ProjectorReadingLayout foregroundReadingLayout;
    private PonderScene externalScene;
    private Vec3d projectionViewer = Vec3d.ZERO;
    private int worldParticleSamples;
    private List<PreviewCalloutContent.Group> calloutGroups = Collections.emptyList();
    private List<Object> calloutKeys = Collections.emptyList();
    private Object selectedCallout;
    private Object focusedCallout;
    private PreviewDebugLayout.Rect calloutTargetPrevious;
    private PreviewDebugLayout.Rect calloutTargetNext;
    private int calloutGroupCount;
    private int calloutSelectedIndex;
    private PreviewPlayerSkin.Mode playerSkinMode = PreviewPlayerSkin.Mode.CURRENT;
    private final PreviewBlockBuffer blockBuffer = new PreviewBlockBuffer();
    private final WorldVertexBufferUploader blockUploader = new WorldVertexBufferUploader();
    private PreviewModelControls modelControls;
    private long modelResourceGeneration = -1;
    private long modelIndexGeneration = -1;
    private boolean modelNotice;
    private PreviewDebugLayout.Rect captionBounds;
    private PreviewDebugLayout.Rect captionPrevious;
    private PreviewDebugLayout.Rect captionNext;
    private boolean captionFocused;
    private String captionText;
    private int captionTextWidth;
    private List<String> captionLines = Collections.emptyList();
    private int captionLongestLine;
    private List<ShowcaseGroupIcon> lastShowcaseGroupIcons = Collections.emptyList();
    private final PreviewGroupLayout groupLayout = new PreviewGroupLayout();
    private ShowcaseGroupState lastShowcaseGroupState;
    private boolean showcaseGroupSelectorOpen;
    private int lastShowcaseHeaderIconX;
    private int lastShowcaseHeaderIconY;
    private int lastShowcaseHeaderIconSize;
    private int lastPlaybackBarX;
    private int lastPlaybackBarY;
    private int lastPlaybackBarWidth;
    private int lastPlaybackBarHeight;
    private int lastNextUpCardX;
    private int lastNextUpCardY;
    private int lastNextUpCardWidth;
    private int lastNextUpCardHeight;

    public PonderDebugScreen(ResourceLocation componentId, int sceneIndex) {
        this(componentId, sceneIndex, false, null);
    }

    public PonderDebugScreen(ResourceLocation componentId, int sceneIndex, @Nullable GuiScreen parentScreen) {
        this(componentId, sceneIndex, false, parentScreen);
    }

    protected PonderDebugScreen(ResourceLocation componentId, int sceneIndex, boolean showcaseMode,
        @Nullable GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
        this.requestedComponentId = componentId;
        this.requestedSceneIndex = Math.max(0, sceneIndex);
        this.showcaseMode = showcaseMode;
    }

    public static PonderDebugScreen showcase(ResourceLocation componentId, int sceneIndex) {
        return new PonderDebugScreen(componentId, sceneIndex, true, null);
    }

    public static PonderDebugScreen showcase(ResourceLocation componentId, int sceneIndex,
        @Nullable GuiScreen parentScreen) {
        return new PonderDebugScreen(componentId, sceneIndex, true, parentScreen);
    }

    protected String getStandaloneTitle() {
        return "Ponder Debug Viewer";
    }

    protected boolean allowDebugShortcutFromShowcase() {
        return true;
    }

    protected GuiScreen createDebugScreenFromShowcase() {
        return new PonderDebugScreen(getSelectedComponentId(), selectedSceneIndex, this);
    }

    protected String getNoSceneLabel() {
        return "No Scene";
    }

    protected String getNoVisibleBlocksLabel() {
        return net.minecraft.client.resources.I18n.format("ponder.ui.showcase.no_visible");
    }

    protected String getNoVisibleBlocksHint() {
        return "Press Play or step the timeline";
    }

    protected String getShowcaseEmptyTitle() {
        return "\u601D\u7D22\u5C55\u793A";
    }

    protected String getShowcaseNoSceneSubtitle() {
        return "\u6CA1\u6709\u53EF\u7528\u7684\u601D\u7D22\u573A\u666F";
    }

    protected String getHoverHintForPlaybackBar(int estimatedTick, @Nullable PonderScene scene) {
        return scene == null ? "Drag timeline" : "Drag timeline | T+" + estimatedTick + " / " + getSceneEndTick(scene);
    }

    protected String getHoverHintForGroupSelector() {
        return "\u70B9\u51FB\u56FE\u6807\u9009\u62E9\u540C\u7EC4\u673A\u5668";
    }

    protected String getHoverHintForNextUp() {
        return "Open next scene";
    }

    protected void setShowcaseHeaderIconBounds(int x, int y, int size) {
        lastShowcaseHeaderIconX = x;
        lastShowcaseHeaderIconY = y;
        lastShowcaseHeaderIconSize = size;
    }

    protected void resetShowcaseTransientState() {
        lastPreviewLayout = null;
        lastShowcaseGroupIcons = Collections.emptyList();
        lastShowcaseGroupState = null;
        groupLayout.invalidate();
        lastShowcaseHeaderIconSize = 0;
        lastNextUpCardWidth = 0;
        lastNextUpCardHeight = 0;
    }

    protected float getShowcaseFade(float partialTicks) {
        return MathHelper.clamp((showcaseFadeTicks + partialTicks) / 20.0F, 0.0F, 1.0F);
    }

    protected boolean usePlayPauseButtonInShowcase() {
        return false;
    }

    protected void initShowcaseButtons() {
        PreviewShowcaseChromeLayout chrome = getShowcaseChromeLayout();
        int buttonY = chrome.toolbarY;
        int centerX = width / 2;
        int prevX = usePlayPauseButtonInShowcase() ? centerX - 86 : centerX - 58;
        int closeX = usePlayPauseButtonInShowcase() ? centerX - 38 : centerX - 10;
        int playPauseX = centerX - 27;
        int nextX = centerX + 38;
        int replayX = centerX + 86;

        prevSceneButton = new GuiButton(BUTTON_PREV_SCENE, prevX, buttonY, SHOWCASE_ICON_SIZE, chrome.toolbarHeight, "<");
        GuiButton closeButton = new GuiButton(BUTTON_CLOSE, closeX, buttonY, SHOWCASE_ICON_SIZE, chrome.toolbarHeight, "X");
        nextSceneButton = new GuiButton(BUTTON_NEXT_SCENE, nextX, buttonY, SHOWCASE_ICON_SIZE, chrome.toolbarHeight, ">");
        startButton = new GuiButton(BUTTON_START, replayX, buttonY, SHOWCASE_ICON_SIZE, chrome.toolbarHeight, "R");
        addCompatButton(prevSceneButton);
        addCompatButton(closeButton);
        if (usePlayPauseButtonInShowcase()) {
            playPauseButton = new GuiButton(BUTTON_PLAY_PAUSE, playPauseX, buttonY, 54, chrome.toolbarHeight, "Pause");
            addCompatButton(playPauseButton);
        } else {
            playPauseButton = null;
        }
        addCompatButton(nextSceneButton);
        addCompatButton(startButton);
        playing = true;
    }

    public static List<ResourceLocation> getRegisteredComponents() {
        Set<ResourceLocation> components = new LinkedHashSet<ResourceLocation>();
        Collection<Map.Entry<ResourceLocation, net.createmod.ponder.api.registration.StoryBoardEntry>> entries =
            PonderIndex.getSceneAccess().getRegisteredEntries();
        for (Map.Entry<ResourceLocation, net.createmod.ponder.api.registration.StoryBoardEntry> entry : entries) {
            components.add(entry.getKey());
        }

        List<ResourceLocation> list = new ArrayList<ResourceLocation>(components);
        Collections.sort(list, new Comparator<ResourceLocation>() {
            @Override
            public int compare(ResourceLocation left, ResourceLocation right) {
                return left.toString().compareTo(right.toString());
            }
        });
        return list;
    }

    public static List<String> getRegisteredComponentsAsStrings() {
        List<ResourceLocation> components = getRegisteredComponents();
        List<String> strings = new ArrayList<String>(components.size());
        for (ResourceLocation component : components) {
            strings.add(component.toString());
        }
        return strings;
    }

    @Override
    public void initGui() {
        super.initGui();
        overlayHitAreas.clear();
        modelControls = null;
        captionBounds = null;
        captionText = null;
        for (CalloutView view : calloutViews.values()) view.wrappedWidth = -1;
        captionPrevious = null;
        captionNext = null;
        explanationLayout = null;
        lastPreviewLayout = null;
        lastPlaybackBarWidth = 0;
        lastPlaybackBarHeight = 0;
        lastShowcaseHeaderIconSize = 0;
        lastNextUpCardWidth = 0;
        lastNextUpCardHeight = 0;
        lastShowcaseGroupIcons = Collections.emptyList();
        lastShowcaseGroupState = null;
        groupLayout.invalidate();
        debugLayout = new PreviewDebugLayout(width, height);
        invalidateCalloutHits();
        focusedCallout = null;
        synchronizeIndex();
        boolean resumePlaying = playing;
        pointerInput.cancelDrag();

        clearCompatButtons();
        if (showcaseMode) {
            initShowcaseButtons();
        } else {
            prevSceneButton = debugButton(BUTTON_PREV_SCENE, 0, "<");
            nextSceneButton = debugButton(BUTTON_NEXT_SCENE, 1, ">");
            startButton = debugButton(BUTTON_START, 2, "|<");
            playPauseButton = debugButton(BUTTON_PLAY_PAUSE, 3, "Play");
            endButton = debugButton(BUTTON_END, 4, ">|");
            addCompatButton(prevSceneButton);
            addCompatButton(nextSceneButton);
            addCompatButton(startButton);
            addCompatButton(playPauseButton);
            addCompatButton(endButton);
            addCompatButton(debugButton(BUTTON_RELOAD, 5, "R"));
            addCompatButton(debugButton(BUTTON_CLOSE, 6, "X"));
            if (debugLayout.compact) {
                String[] labels = { "Preview", "Details", "Timeline" };
                for (int i = 0; i < labels.length; i++) {
                    PreviewDebugLayout.Rect tab = debugLayout.tabs[i];
                    addCompatButton(new GuiButton(BUTTON_DEBUG_PREVIEW + i, tab.x, tab.y, tab.width, tab.height,
                        labels[i]));
                }
            }
        }

        componentScroll = MathHelper.clamp(componentScroll, 0, getMaxComponentScroll());
        operationScroll = MathHelper.clamp(operationScroll, 0, getMaxOperationScroll());
        playing = resumePlaying;
        updateButtonState();
    }

    private GuiButton debugButton(int id, int index, String label) {
        PreviewDebugLayout.Rect rect = getDebugLayout().toolbar[index];
        return new GuiButton(id, rect.x, rect.y, rect.width, rect.height, label);
    }

    private PreviewDebugLayout getDebugLayout() {
        if (debugLayout == null || debugLayout.screenWidth != width || debugLayout.screenHeight != height) {
            debugLayout = new PreviewDebugLayout(width, height);
        }
        return debugLayout;
    }

    private PreviewExplanationLayout getDebugExplanationLayout() {
        PreviewDebugLayout.Rect preview = getDebugLayout().preview;
        return PreviewExplanationLayout.debug(new PreviewDebugLayout.Rect(
            preview.x + 7, preview.y + 19, preview.width - 14, preview.height - 40));
    }

    private PreviewDebugLayout.Rect getDebugTextRows(PreviewDebugLayout.Rect rows) {
        if (!getDebugLayout().compact) {
            return rows;
        }
        int bottom = getDebugExplanationLayout().explanation.y - 8;
        int lines = Math.max(0, (bottom - rows.y) / LINE_HEIGHT);
        return new PreviewDebugLayout.Rect(rows.x, rows.y, rows.width, lines * LINE_HEIGHT);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BUTTON_PREV_SCENE) {
            selectScene(selectedSceneIndex - 1);
        } else if (button.id == BUTTON_NEXT_SCENE) {
            selectScene(selectedSceneIndex + 1);
        } else if (button.id == BUTTON_START) {
            setPlaybackTick(0);
            previewAudio.reset(getSelectedScene());
            playing = showcaseMode;
        } else if (button.id == BUTTON_RESET_CAMERA) {
            resetPreviewCamera();
        } else if (button.id == BUTTON_PREV_STEP || button.id == BUTTON_NEXT_STEP) {
            stepLesson(button.id == BUTTON_PREV_STEP ? -1 : 1);
        } else if (button.id >= BUTTON_SKIN_CURRENT && button.id <= BUTTON_SKIN_ALEX) {
            playerSkinMode = PreviewPlayerSkin.Mode.values()[button.id - BUTTON_SKIN_CURRENT];
        } else if (button.id == BUTTON_PLAY_PAUSE) {
            togglePlayback();
        } else if (button.id == BUTTON_END) {
            PonderScene scene = getSelectedScene();
            setPlaybackTick(scene == null ? 0 : getSceneEndTick(scene));
            playing = false;
        } else if (button.id == BUTTON_DEBUG) {
            mc.displayGuiScreen(createDebugScreenFromShowcase());
            return;
        } else if (button.id == BUTTON_RELOAD) {
            PonderIndex.reload();
            synchronizeIndex();
        } else if (button.id == BUTTON_CLOSE) {
            mc.displayGuiScreen(parentScreen);
        } else if (button.id >= BUTTON_DEBUG_PREVIEW && button.id <= BUTTON_DEBUG_TIMELINE) {
            debugTab = button.id - BUTTON_DEBUG_PREVIEW;
            invalidateCalloutHits();
            focusedCallout = null;
            lastPreviewLayout = null;
            explanationLayout = null;
            captionBounds = null;
            captionPrevious = null;
            captionNext = null;
            captionFocused = false;
            clearExplanationHover();
            modelControls = null;
            overlayHitAreas.clear();
            pointerInput.cancelDrag();
        }
        updateButtonState();
    }

    @Override
    public void updateScreen() {
        if (initialized) {
            synchronizeIndex();
        }
        if (registryNoticeTicks > 0) {
            registryNoticeTicks--;
        }
        if (explanationHoverTicks > 0) {
            explanationHoverTicks--;
        }
        refreshModelPreview();
        if (showcaseMode && showcaseFadeTicks < 20) {
            showcaseFadeTicks++;
        }

        if (!playing) {
            previewAudio.pause();
            return;
        }
        if (modelMode.isAppearance()) {
            modelMode.chooseStructure();
            modelNotice = false;
        }

        PonderScene scene = getSelectedScene();
        if (scene == null) {
            playing = false;
            updateButtonState();
            return;
        }

        int maxTick = getSceneEndTick(scene);
        if (playbackTick < maxTick) {
            setPlaybackTick(playbackTick + 1, true);
        } else {
            playing = false;
            updateButtonState();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        if (initialized && synchronizeIndex()) {
            return;
        }
        int wheel = Mouse.getEventDWheel();
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (handleCalloutWheel(wheel, mouseX, mouseY)) return;
        if (handleShowcaseGroupWheel(wheel, mouseX, mouseY)) {
            return;
        }
        if (wheel != 0 && modelControls != null && modelControls.contains(mouseX, mouseY)) {
            pointerInput.cancelDrag();
            return;
        }
        // GuiScreen's default dispatch can turn a wheel event into a held-button
        // drag. Caption input must take ownership before that callback runs.
        if (pointerInput.routeWheel(wheel, captionBounds != null && captionBounds.contains(mouseX, mouseY),
            this::changeCaptionPage, () -> super.handleMouseInput()) || wheel == 0) {
            return;
        }
        int delta = wheel > 0 ? -1 : 1;
        if (showcaseMode) {
            if (isMouseOverPreview(mouseX, mouseY)) {
                if (isShiftKeyDown()) {
                    stepPlaybackTick(delta < 0 ? -1 : 1);
                } else if (isControlKeyDown()) {
                    takeManualCamera();
                    float zoomStep = wheel > 0 ? 0.12F : -0.12F;
                    previewZoom = MathHelper.clamp(previewZoom + zoomStep, 0.45F, 2.4F);
                }
            }
            return;
        }

        if (isMouseOverComponentList(mouseX, mouseY)) {
            componentScroll = MathHelper.clamp(componentScroll + delta, 0, getMaxComponentScroll());
        } else if (isMouseOverSummary(mouseX, mouseY)) {
            summaryScroll = MathHelper.clamp(summaryScroll + delta * 3, 0,
                Math.max(0, summaryLineCount - getDebugTextRows(getDebugLayout().summaryRows).lines()));
        } else if (isMouseOverOperations(mouseX, mouseY)) {
            operationScroll = MathHelper.clamp(operationScroll + delta * 3, 0, getMaxOperationScroll());
        } else if (isMouseOverPreview(mouseX, mouseY)) {
            if (isShiftKeyDown()) {
                stepPlaybackTick(delta < 0 ? -1 : 1);
            } else if (isControlKeyDown()) {
                takeManualCamera();
                float zoomStep = wheel > 0 ? 0.12F : -0.12F;
                previewZoom = MathHelper.clamp(previewZoom + zoomStep, 0.45F, 2.4F);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (initialized && synchronizeIndex()) {
            return;
        }
        if (handleCalloutClick(mouseX, mouseY, mouseButton)) return;
        focusedCallout = null;
        if (handleShowcaseGroupClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (mouseButton == 0 && modelControls != null && modelControls.contains(mouseX, mouseY)) {
            selectModelMode(modelControls.appearance.contains(mouseX, mouseY));
            return;
        }
        if (mouseButton == 0 && captionBounds != null && captionBounds.contains(mouseX, mouseY)) {
            if (captionPrevious != null && captionPrevious.contains(mouseX, mouseY)) {
                changeCaptionPage(-1);
            } else if (captionNext != null && captionNext.contains(mouseX, mouseY)) {
                changeCaptionPage(1);
            } else {
                focusCaptionForReading();
            }
            return;
        }
        captionFocused = false;
        clearExplanationHover();
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton != 0 || mc.currentScreen != this || isMouseOverButton(mouseX, mouseY)) {
            return;
        }

        if (isMouseOverPlaybackBar(mouseX, mouseY)) {
            pointerInput.beginTimelineDrag();
            seekPlaybackToMouse(mouseX);
            playing = false;
            updateButtonState();
            return;
        }

        if (showcaseMode) {
            if (isMouseOverNextUpCard(mouseX, mouseY)) {
                selectScene(selectedSceneIndex + 1);
                updateButtonState();
                return;
            }
            if (isMouseOverShowcaseHeaderIcon(mouseX, mouseY) && hasShowcaseGroupChoices()) {
                pointerInput.cancelDrag();
                groupLayout.clear();
                showcaseGroupSelectorOpen = true;
                playing = false;
                updateButtonState();
                return;
            }
            if (isMouseOverPreview(mouseX, mouseY)) {
                beginPreviewDrag(mouseX, mouseY);
            }
            return;
        }

        int componentIndex = getComponentIndexAt(mouseX, mouseY);
        if (componentIndex >= 0) {
            selectComponent(componentIndex, 0);
            return;
        }

        int operationIndex = getOperationIndexAt(mouseX, mouseY);
        if (operationIndex >= 0) {
            List<RecordedOperation> operations = getSelectedRecordedOperations();
            setPlaybackTick(operations.get(operationIndex).getTick());
            playing = false;
            updateButtonState();
            return;
        }

        if (isMouseOverPreview(mouseX, mouseY)) {
            beginPreviewDrag(mouseX, mouseY);
        }
    }

    private boolean isMouseOverButton(int mouseX, int mouseY) {
        for (GuiButton button : buttonList) {
            if (button.visible && isWithin(mouseX, mouseY, button.x, button.y,
                button.x + button.width, button.y + button.height)) {
                return true;
            }
        }
        return false;
    }

    private void beginPreviewDrag(int mouseX, int mouseY) {
        takeManualCamera();
        pointerInput.beginPreviewDrag();
        previewDragMouseX = mouseX;
        previewDragMouseY = mouseY;
    }

    private void takeManualCamera() {
        PonderScene scene = getSelectedScene();
        if (!previewCameraManual && scene != null) {
            PreviewBounds bounds = lastSceneBounds == null ? PonderScenePreview.computeBounds(scene) : lastSceneBounds;
            manualCameraView = getPreviewView(scene, bounds, playbackTick);
            previewYaw = MathHelper.wrapDegrees(previewYaw + PonderSceneRuntime.getCameraYaw(scene, playbackTick));
            previewPitch = MathHelper.wrapDegrees(previewPitch + PonderSceneRuntime.getCameraPitch(scene, playbackTick));
        }
        previewCameraManual = true;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (calloutAt(mouseX, mouseY) != null) {
            pointerInput.cancelDrag();
            return;
        }
        if (showcaseGroupSelectorOpen) {
            pointerInput.cancelDrag();
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);

        if (pointerInput.isTimelineDragging() && clickedMouseButton == 0) {
            seekPlaybackToMouse(mouseX);
            playing = false;
            updateButtonState();
            return;
        }

        if (!pointerInput.isPreviewDragging() || clickedMouseButton != 0) {
            return;
        }

        if (!isMouseOverPreview(mouseX, mouseY)) {
            previewDragMouseX = mouseX;
            previewDragMouseY = mouseY;
            return;
        }
        int deltaX = mouseX - previewDragMouseX;
        int deltaY = mouseY - previewDragMouseY;
        previewYaw = MathHelper.wrapDegrees(previewYaw + deltaX * 0.75F);
        previewPitch = MathHelper.wrapDegrees(previewPitch + deltaY * 0.6F);
        previewDragMouseX = mouseX;
        previewDragMouseY = mouseY;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (state == 0) {
            pointerInput.cancelDrag();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (initialized) {
            synchronizeIndex();
        }
        if (!showcaseGroupSelectorOpen && focusedCallout != null
            && (keyCode == Keyboard.KEY_PRIOR || keyCode == Keyboard.KEY_NEXT)) {
            CalloutView view = calloutViews.get(focusedCallout);
            if ((view == null || !visibleCallouts.contains(view)) && !visibleCallouts.isEmpty()) {
                view = visibleCallouts.get(0);
                focusedCallout = view.key;
            }
            if (view != null && visibleCallouts.contains(view)) {
                view.paging.turn(keyCode == Keyboard.KEY_PRIOR ? -1 : 1);
                playing = false;
                pointerInput.cancelDrag();
                updateButtonState();
                return;
            }
        }
        if (handleShowcaseGroupKey(keyCode)) {
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parentScreen);
            return;
        }
        if (captionFocused && captionBounds != null
            && (keyCode == Keyboard.KEY_PRIOR || keyCode == Keyboard.KEY_NEXT)) {
            changeCaptionPage(keyCode == Keyboard.KEY_PRIOR ? -1 : 1);
            return;
        }
        if (keyCode == Keyboard.KEY_C) {
            resetPreviewCamera();
            return;
        }
        if (keyCode == Keyboard.KEY_LBRACKET || keyCode == Keyboard.KEY_RBRACKET) {
            stepLesson(keyCode == Keyboard.KEY_LBRACKET ? -1 : 1);
            return;
        }

        if (showcaseMode) {
            if (keyCode == Keyboard.KEY_SPACE) {
                togglePlayback();
                updateButtonState();
                return;
            }

            if (keyCode == Keyboard.KEY_R) {
                PonderIndex.reload();
                synchronizeIndex();
                updateButtonState();
                return;
            }

            if (keyCode == Keyboard.KEY_LEFT) {
                selectScene(selectedSceneIndex - 1);
                updateButtonState();
                return;
            }

            if (keyCode == Keyboard.KEY_RIGHT) {
                selectScene(selectedSceneIndex + 1);
                updateButtonState();
                return;
            }

            if (keyCode == Keyboard.KEY_UP) {
                stepPlaybackTick(-1);
                return;
            }

            if (keyCode == Keyboard.KEY_DOWN) {
                stepPlaybackTick(1);
                return;
            }

            if (keyCode == Keyboard.KEY_D && allowDebugShortcutFromShowcase()) {
                mc.displayGuiScreen(createDebugScreenFromShowcase());
                return;
            }

            super.keyTyped(typedChar, keyCode);
            return;
        }

        if (keyCode == Keyboard.KEY_SPACE) {
            togglePlayback();
            updateButtonState();
            return;
        }

        if (keyCode == Keyboard.KEY_R) {
            PonderIndex.reload();
            synchronizeIndex();
            updateButtonState();
            return;
        }

        if (keyCode == Keyboard.KEY_LEFT) {
            selectScene(selectedSceneIndex - 1);
            updateButtonState();
            return;
        }

        if (keyCode == Keyboard.KEY_RIGHT) {
            selectScene(selectedSceneIndex + 1);
            updateButtonState();
            return;
        }

        if (keyCode == Keyboard.KEY_UP) {
            stepPlaybackTick(-1);
            return;
        }

        if (keyCode == Keyboard.KEY_DOWN) {
            stepPlaybackTick(1);
            return;
        }

        if (keyCode == Keyboard.KEY_PRIOR) {
            stepPlaybackTick(-20);
            return;
        }

        if (keyCode == Keyboard.KEY_NEXT) {
            stepPlaybackTick(20);
            return;
        }

        if (keyCode == Keyboard.KEY_HOME) {
            setPlaybackTick(0);
            playing = false;
            updateButtonState();
            return;
        }

        if (keyCode == Keyboard.KEY_END) {
            PonderScene scene = getSelectedScene();
            setPlaybackTick(scene == null ? 0 : getSceneEndTick(scene));
            playing = false;
            updateButtonState();
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void onGuiClosed() {
        if (closedView == null) {
            closedView = captureSceneView();
        }
        pointerInput.cancelDrag();
        closeShowcaseGroupSelector();
        overlayHitAreas.clear();
        clearPreviewWorld();
        compiledScenes = Collections.emptyList();
        componentIds = Collections.emptyList();
        resetShowcaseTransientState();
        lastPlaybackBarWidth = 0;
        registryView.invalidate();
        super.onGuiClosed();
    }

    protected void drawShowcaseScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        resetShowcaseTransientState();

        PreviewLayout showcaseLayout = getShowcasePreviewLayout();
        int previewX = showcaseLayout.originX;
        int previewY = showcaseLayout.originY;
        int previewWidth = showcaseLayout.width;
        int previewHeight = showcaseLayout.height;
        int headerX = previewX + 12;
        int headerY = previewY + 10;
        float fade = MathHelper.clamp((showcaseFadeTicks + partialTicks) / 20.0F, 0.0F, 1.0F);

        drawGradientRect(0, 0, width, height, 0xF306090D, 0xFF010205);

        PonderScene scene = getSelectedScene();
        ResourceLocation componentId = getSelectedComponentId();
        ItemStack componentStack = createComponentStack(componentId);
        String stackLabel = componentStack.isEmpty() ? "\u672A\u627E\u5230\u5BF9\u5E94\u7269\u54C1" : componentStack.getDisplayName();
        String sceneTitle = scene == null ? getShowcaseEmptyTitle() : scene.getTitle();
        String title = fontRenderer.trimStringToWidth(sceneTitle, 240);
        String subtitle = componentId == null ? getShowcaseNoSceneSubtitle() : stackLabel;

        drawScenePreview(previewX, previewY, previewWidth, previewHeight, partialTicks);
        drawShowcaseBackdrop(previewX, previewY, previewWidth, previewHeight, fade);
        drawSceneSpaceOverlays(scene, lastPreviewLayout, getRenderTick(scene, partialTicks), fade);
        drawShowcaseHeader(headerX, headerY, previewWidth - 80, componentStack, title, subtitle, fade);
        drawShowcaseLogo(previewX + previewWidth - 46, previewY + 12, fade);
        int playbackWidth = Math.min(SHOWCASE_BAR_WIDTH, previewWidth - 150);
        int playbackX = previewX + (previewWidth - playbackWidth) / 2;
        int playbackY = getShowcaseChromeLayout().playbackY;
        drawPlaybackBar(playbackX, playbackY, playbackWidth, 3);
        drawNextUpCard(scene, fade);
        if (hasModelControls()) {
            drawModelControls(SHOWCASE_MARGIN + 8, getShowcaseChromeLayout().modelControlsY);
        }
        updateExplanationHover(mouseX, mouseY);
        drawShowcaseGroupPopup(headerX - 2, headerY + 48, previewWidth - 96, fade);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        PreviewRenderState.runOverlay(() -> drawScreenContents(mouseX, mouseY, partialTicks));
    }

    private void drawScreenContents(int mouseX, int mouseY, float partialTicks) {
        if (initialized) {
            synchronizeIndex();
        }
        refreshModelPreview();
        overlayHitAreas.clear();
        modelControls = null;
        captionBounds = null;
        captionPrevious = null;
        captionNext = null;
        if (showcaseMode) {
            drawShowcaseScreen(mouseX, mouseY, partialTicks);
            super.drawScreen(mouseX, mouseY, partialTicks);
            drawShowcaseHoverHints(mouseX, mouseY);
            drawForegroundCallouts(getSelectedScene(), getRenderTick(getSelectedScene(), partialTicks),
                getShowcaseFade(partialTicks));
            return;
        }

        drawDefaultBackground();
        lastPreviewLayout = null;
        lastNextUpCardWidth = 0;
        lastNextUpCardHeight = 0;

        PreviewDebugLayout layout = getDebugLayout();
        explanationLayout = getDebugExplanationLayout();
        drawRect(layout.components.x, layout.components.y, layout.components.right(), layout.components.bottom(),
            0xAA101015);
        drawRect(layout.content.x, layout.content.y, layout.content.right(), layout.content.bottom(), 0xAA101015);

        drawCenteredString(fontRenderer, getStandaloneTitle(), width / 2, 6, 0xFFFFFF);
        drawString(fontRenderer, "Components", layout.components.x + 6, layout.components.y + 6, 0xFFEEDD);
        drawComponentList(mouseX, mouseY, layout.componentRows);
        if (!layout.compact) {
            drawString(fontRenderer, "Scene", layout.content.x + 6, layout.content.y + 6, 0xFFEEDD);
        }
        if (!layout.compact || debugTab == 1) {
            drawSceneSummary(getDebugTextRows(layout.summaryRows), partialTicks);
        }
        if (!layout.compact || debugTab == 0) {
            PreviewDebugLayout.Rect preview = layout.preview;
            drawScenePreview(preview.x, preview.y, preview.width, preview.height, partialTicks);
            float renderTick = getRenderTick(getSelectedScene(), partialTicks);
            drawSceneSpaceOverlays(getSelectedScene(), lastPreviewLayout, renderTick, 1.0F);
        }
        if (!layout.compact || debugTab == 2) {
            drawOperationList(mouseX, mouseY, getDebugTextRows(layout.operationRows), !layout.compact);
        }

        updateExplanationHover(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (lastPreviewLayout != null) {
            drawForegroundCallouts(getSelectedScene(), getRenderTick(getSelectedScene(), partialTicks), 1.0F);
        } else {
            invalidateCalloutHits();
            focusedCallout = null;
            drawShowcaseCaption(getSelectedScene(), layout.preview.x, layout.preview.y,
                layout.preview.width, layout.preview.height, getRenderTick(getSelectedScene(), partialTicks), 1.0F);
        }
    }

    protected PreviewLayout getShowcasePreviewLayout() {
        return new PreviewLayout(SHOWCASE_MARGIN, SHOWCASE_MARGIN, width - SHOWCASE_MARGIN * 2,
            height - SHOWCASE_MARGIN * 2);
    }

    private void drawComponentList(int mouseX, int mouseY, PreviewDebugLayout.Rect rows) {
        int visibleLines = rows.lines();
        int maxIndex = Math.min(componentIds.size(), componentScroll + visibleLines);

        for (int index = componentScroll; index < maxIndex; index++) {
            int y = rows.y + (index - componentScroll) * LINE_HEIGHT;
            boolean selected = index == selectedComponentIndex;
            boolean hovered = rows.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + LINE_HEIGHT;
            int background = selected ? 0xCC304060 : hovered ? 0x66303030 : 0x33202020;
            drawRect(rows.x, y, rows.right(), y + LINE_HEIGHT, background);

            String label = fontRenderer.trimStringToWidth(componentIds.get(index).toString(), rows.width - 8);
            drawString(fontRenderer, label, rows.x + 4, y + 2, selected ? 0xFFFFFF : 0xD0D0D0);
        }
    }

    private void drawSceneSummary(PreviewDebugLayout.Rect rows, float partialTicks) {
        PonderScene scene = getSelectedScene();
        ResourceLocation componentId = getSelectedComponentId();

        if (componentId == null) {
            drawString(fontRenderer, fontRenderer.trimStringToWidth("No Ponder components registered.", rows.width),
                rows.x, rows.y, 0xFF8888);
            return;
        }

        List<String> lines = new ArrayList<String>();
        lines.add("Component: " + componentId);

        if (scene == null) {
            lines.add("Scene: none");
            lines.add("Tip: use /ponder reload if you just changed registrations.");
        } else {
            PreviewBounds bounds = PonderScenePreview.computeBounds(scene);
            PonderSceneRuntime.RuntimeState previewState = getPreviewFrame(scene,
                getRenderTick(scene, partialTicks)).state;
            RecordedOperation activeOperation = getActiveOperation(scene.getRecordedOperations(), playbackTick);
            WorldEvent latestWorldEvent = getLatestWorldEvent(scene, playbackTick);
            lines.add("Scene: " + (selectedSceneIndex + 1) + "/" + compiledScenes.size() + "  id=" + scene.getSceneId());
            lines.add("Title: " + scene.getTitle());
            lines.add("Schematic: " + scene.getSchematicLocation());
            lines.add("Ticks: " + playbackTick + "/" + getSceneEndTick(scene) + "  status=" + (playing ? "playing" : "paused"));
            lines.add("Active op: " + (activeOperation == null ? "none yet" : activeOperation.getDescription()));
            lines.add("Latest world: " + (latestWorldEvent == null ? "none yet" : formatWorldEvent(latestWorldEvent)));
            lines.add("Ops: " + scene.getRecordedOperations().size() + "  worldEvents=" + scene.getWorldEvents().size());
            lines.add("Visible blocks: " + previewState.visibleBlocks + "  columns=" + previewState.columnsWithBlocks);
            lines.add("Bounds: x " + bounds.minX + ".." + bounds.maxX + "  z " + bounds.minZ + ".." + bounds.maxZ
                + "  y " + bounds.minY + ".." + bounds.maxY);
            lines.add("Camera Y: " + Math.round(PonderSceneRuntime.getCameraYaw(scene, playbackTick)) + " / "
                + Math.round(scene.getAccumulatedCameraYaw()) + "  scale=" + scene.getScaleFactor());
            lines.add("Keyframes: " + scene.getKeyframeCount() + "  lazy=" + scene.getLazyKeyframeCount()
                + "  finished=" + scene.isFinished());
            lines.add("Tags: " + formatTags(PonderIndex.getTagAccess().getTags(componentId)));
        }

        summaryLineCount = lines.size();
        summaryScroll = MathHelper.clamp(summaryScroll, 0, Math.max(0, lines.size() - rows.lines()));
        for (int i = summaryScroll; i < Math.min(lines.size(), summaryScroll + rows.lines()); i++) {
            drawString(fontRenderer, fontRenderer.trimStringToWidth(lines.get(i), rows.width), rows.x,
                rows.y + (i - summaryScroll) * LINE_HEIGHT + 2,
                0xE0E0E0);
        }
    }

    protected void drawScenePreview(int x, int y, int width, int height, float partialTicks) {
        if (showcaseMode) {
            explanationLayout = PreviewExplanationLayout.showcase(this.width, this.height, fontRenderer.FONT_HEIGHT);
        } else {
            drawRect(x, y, x + width, y + height, 0x66192026);
            drawString(fontRenderer, "3D Preview", x + 6, y + 6, 0xFFEEDD);

            int viewportX = x + 6;
            int viewportY = y + 18;
            int viewportWidth = width - 12;
            int viewportHeight = height - 38;
            drawRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, 0xCC0B1016);
            drawRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + 1, 0x55F0E6D2);
            drawRect(viewportX, viewportY + viewportHeight - 1, viewportX + viewportWidth, viewportY + viewportHeight,
                0x33202020);
            explanationLayout = PreviewExplanationLayout.debug(new PreviewDebugLayout.Rect(
                viewportX + 1, viewportY + 1, viewportWidth - 2, viewportHeight - 2));
        }
        PreviewDebugLayout.Rect model = explanationLayout.model;
        lastPreviewLayout = new PreviewLayout(model.x, model.y, model.width, model.height);

        PonderScene scene = getSelectedScene();
        if (scene == null) {
            return;
        }

        PreviewBounds bounds = PonderScenePreview.computeBounds(scene);
        lastSceneBounds = bounds;
        float renderTick = getRenderTick(scene, partialTicks);
        if (modelMode.isAppearance() && renderModelPreview(scene, bounds, lastPreviewLayout, renderTick)) {
            if (!showcaseMode) {
                drawModelControls(x + 6, y + height - 18);
                drawString(fontRenderer, fontRenderer.trimStringToWidth(getRegistryStatus("\u9759\u6001\u6574\u4f53"),
                    Math.max(1, width - 100)), x + 94, y + height - 14, 0xC0C0C0);
            }
            return;
        }
        PreviewFrame frame = getPreviewFrame(scene, renderTick);
        PonderSceneRuntime.RuntimeState runtimeState = frame.state;

        if (runtimeState.visibleBlocks > 0 || PreviewActorVisibility.anyVisible(scene.getActorEvents(), renderTick)) {
            renderScenePreview(scene, bounds, frame, lastPreviewLayout, renderTick);
        }

        if (!showcaseMode) {
            if (hasModelControls()) {
                drawModelControls(x + 6, y + height - 18);
                String status = modelNotice ? modelMode.getNote()
                    : getRegistryStatus("Blocks " + runtimeState.visibleBlocks);
                drawString(fontRenderer, fontRenderer.trimStringToWidth(status, Math.max(1, width - 100)),
                    x + 94, y + height - 14, modelNotice ? 0xFFCC88 : 0xC0C0C0);
                return;
            }
            String stateLine = "Blocks " + runtimeState.visibleBlocks + "  zoom "
                + Math.round(previewZoom * 100.0F) + "%  drag orbit";
            drawString(fontRenderer, fontRenderer.trimStringToWidth(getRegistryStatus(stateLine), width - 12),
                x + 6, y + height - 18,
                0xC0C0C0);
            drawString(fontRenderer, fontRenderer.trimStringToWidth("Ctrl+wheel zoom  Shift+wheel tick", width - 12),
                x + 6, y + height - 8, 0xA0A0A0);
        }
    }

    protected void drawPlaybackBar(int x, int y, int width, int height) {
        setPlaybackBarBounds(x, y, width, height);
        PonderScene scene = getSelectedScene();
        int maxTick = scene == null ? 0 : Math.max(1, getSceneEndTick(scene));
        float progress = scene == null ? 0.0F : playbackTick / (float) maxTick;
        int filled = MathHelper.clamp((int) (progress * width), 0, width);

        if (showcaseMode) {
            drawRect(x, y, x + width, y + height, 0x44101518);
            drawRect(x, y, x + filled, y + height, 0xFFD9D1BF);
            if (filled > 0) {
                drawRect(x + filled - 1, y - 1, x + filled, y + height + 1, 0xFFFDFBF4);
            }

            if (scene != null) {
                String leftLabel = "\u573A\u666F " + (selectedSceneIndex + 1) + " / " + compiledScenes.size();
                String rightLabel = playbackTick + " / " + getSceneEndTick(scene);
                int labelY = y - fontRenderer.FONT_HEIGHT - 2;
                drawString(fontRenderer, leftLabel, x, labelY, 0xC4CFD7);
                drawString(fontRenderer, rightLabel, x + width - fontRenderer.getStringWidth(rightLabel), labelY,
                    0xC4CFD7);
            }
            return;
        }

        drawRect(x, y, x + width, y + height, 0x44202020);
        drawRect(x, y, x + filled, y + height, 0xCC6AB8FF);
        if (filled > 0) {
            drawRect(x + filled - 1, y, x + filled, y + height, 0xFFEFF8FF);
        }

        String label = scene == null ? "No scene"
            : "T+" + playbackTick + " / " + getSceneEndTick(scene) + (playing ? "   looping" : "   paused");
        drawCenteredString(fontRenderer, label, x + width / 2, y - fontRenderer.FONT_HEIGHT - 2, 0xD8E4E8);
    }

    protected void setPlaybackBarBounds(int x, int y, int width, int height) {
        lastPlaybackBarX = x;
        lastPlaybackBarY = y;
        lastPlaybackBarWidth = width;
        lastPlaybackBarHeight = height;
    }

    protected void drawShowcaseHeader(int x, int y, int width, ItemStack componentStack, String title, String subtitle,
        float fade) {
        int boxWidth = Math.min(326, width);
        int boxHeight = 42;
        int alpha = (int) (fade * 185.0F) << 24;
        drawGradientRect(x, y, x + boxWidth, y + boxHeight, alpha | 0x141920, 0x00000000);
        drawGradientRect(x + boxWidth - 26, y, x + boxWidth, y + boxHeight, 0x00000000, alpha | 0x0E1218);
        drawRect(x + 34, y + boxHeight - 1, x + boxWidth - 12, y + boxHeight, ((int) (fade * 120.0F) << 24) | 0xD5CCB8);
        drawRect(x - 2, y + 4, x + 28, y + 34, alpha | 0x10151B);
        drawRect(x - 1, y + 5, x + 27, y + 33, ((int) (fade * 64.0F) << 24) | 0xE7E0D1);
        lastShowcaseHeaderIconX = x - 1;
        lastShowcaseHeaderIconY = y + 5;
        lastShowcaseHeaderIconSize = 28;

        if (!componentStack.isEmpty()) {
            renderOverlayItem(componentStack, x + 5, y + 11);
        }

        ShowcaseGroupState groupState = getShowcaseGroupState();
        if (groupState != null && groupState.components.size() > 1) {
            int chipX = x + 16;
            int chipY = y + 22;
            int chipFill = ((int) (fade * 188.0F) << 24) | 0x1A2028;
            int chipBorder = ((int) (fade * 156.0F) << 24) | 0xD5CCB8;
            drawRect(chipX, chipY, chipX + 12, chipY + 10, chipBorder);
            drawRect(chipX + 1, chipY + 1, chipX + 11, chipY + 9, chipFill);
            drawCenteredStringNoShadow(String.valueOf(groupState.components.size()), chipX + 6, chipY + 2, 0xF2EFE7);
        }

        String hint = "\u601D\u7D22";
        int sceneCountColor = ((int) (fade * 255.0F) << 24) | 0xC8D0D8;
        drawString(fontRenderer, hint, x + 36, y + 5, 0xB8C3CC);
        drawString(fontRenderer, fontRenderer.trimStringToWidth(title, boxWidth - 90), x + 36, y + 17, 0xF6F2EA);
        drawString(fontRenderer, fontRenderer.trimStringToWidth(subtitle, boxWidth - 90), x + 36, y + 29, 0xAEB8C1);
        if (!compiledScenes.isEmpty()) {
            String sceneIndex = (selectedSceneIndex + 1) + " / " + compiledScenes.size();
            drawString(fontRenderer, sceneIndex, x + boxWidth - 12 - fontRenderer.getStringWidth(sceneIndex), y + 6,
                sceneCountColor);
        }
    }

    protected void drawShowcaseGroupPopup(int x, int y, int maxWidth, float fade) {
        ShowcaseGroupState groupState = getShowcaseGroupState();
        if (!layoutShowcaseGroupPopup(groupState, x, y, maxWidth)) {
            return;
        }

        String label = "\u5206\u7EC4\u9009\u62E9  " + groupState.tag.getTitle();
        int boxX = groupLayout.bounds.x;
        int boxY = groupLayout.bounds.y;
        int boxWidth = groupLayout.bounds.width;
        int boxHeight = groupLayout.bounds.height;
        int alpha = (int) (fade * 164.0F) << 24;
        drawGradientRect(boxX, boxY, boxX + boxWidth, boxY + boxHeight, alpha | 0x12181E, alpha | 0x0A0E14);
        drawRect(boxX, boxY, boxX + boxWidth, boxY + 1, ((int) (fade * 120.0F) << 24) | 0xE7E0D1);
        drawRect(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight,
            ((int) (fade * 96.0F) << 24) | 0xD5CCB8);
        drawRect(boxX, boxY, boxX + 1, boxY + boxHeight, ((int) (fade * 90.0F) << 24) | 0xE7E0D1);
        drawRect(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, ((int) (fade * 84.0F) << 24) | 0x2B323A);
        drawString(fontRenderer, fontRenderer.trimStringToWidth(label, boxWidth - 16), boxX + 8, boxY + 8, 0xC8D0D8);

        for (ShowcaseGroupIcon icon : lastShowcaseGroupIcons) {
            ItemStack stack = createComponentStack(icon.componentId);
            int iconX = icon.x;
            int iconY = icon.y;
            boolean selected = icon.componentId.equals(getSelectedComponentId());
            int borderColor = selected ? ((int) (fade * 255.0F) << 24) | 0xE7E0D1 : ((int) (fade * 140.0F) << 24) | 0x596570;
            int fillColor = selected ? ((int) (fade * 188.0F) << 24) | 0x1A2028 : ((int) (fade * 120.0F) << 24) | 0x10151B;
            drawRect(iconX, iconY, iconX + icon.width, iconY + icon.height, borderColor);
            drawRect(iconX + 1, iconY + 1, iconX + icon.width - 1, iconY + icon.height - 1, fillColor);

            if (!stack.isEmpty()) {
                renderOverlayItem(stack, iconX + 2, iconY + 2);
            }
        }
        if (groupLayout.previous != null) {
            drawCenteredStringNoShadow("<", groupLayout.previous.x + 11, groupLayout.previous.y + 4,
                groupLayout.page() > 0 ? 0xF2F5F8 : 0x58616A);
            drawCenteredStringNoShadow(">", groupLayout.next.x + 11, groupLayout.next.y + 4,
                groupLayout.page() + 1 < groupLayout.pageCount() ? 0xF2F5F8 : 0x58616A);
            String page = (groupLayout.page() + 1) + " / " + groupLayout.pageCount();
            drawCenteredStringNoShadow(fontRenderer.trimStringToWidth(page, boxWidth - 54),
                boxX + boxWidth / 2, groupLayout.previous.y + 4, 0xAEB8C1);
        }
    }

    private boolean layoutShowcaseGroupPopup(ShowcaseGroupState state, int x, int y, int maxWidth) {
        if (!showcaseGroupSelectorOpen) {
            return false;
        }
        int bottom = Math.min(height - 58, lastPlaybackBarWidth > 0 ? lastPlaybackBarY - 18 : height - 90);
        if (state == null || state.components.size() <= 1 || maxWidth < 120
            || !groupLayout.configure(state.components.size(), x, y, maxWidth, bottom)) {
            closeShowcaseGroupSelector();
            return false;
        }
        lastShowcaseGroupState = state;
        refreshShowcaseGroupIcons();
        return true;
    }

    private void refreshShowcaseGroupIcons() {
        List<ShowcaseGroupIcon> icons = new ArrayList<ShowcaseGroupIcon>();
        if (lastShowcaseGroupState != null && groupLayout.bounds != null) {
            for (int index = groupLayout.firstIndex(); index < groupLayout.endIndex(); index++) {
                PreviewDebugLayout.Rect rect = groupLayout.icon(index);
                icons.add(new ShowcaseGroupIcon(lastShowcaseGroupState.components.get(index),
                    lastShowcaseGroupState.tag, rect.x, rect.y, rect.width, rect.height));
            }
        }
        lastShowcaseGroupIcons = icons;
    }

    private void changeShowcaseGroupPage(int amount) {
        pointerInput.cancelDrag();
        captionFocused = false;
        if (groupLayout.turn(amount)) {
            refreshShowcaseGroupIcons();
        }
    }

    private void closeShowcaseGroupSelector() {
        showcaseGroupSelectorOpen = false;
        lastShowcaseGroupState = null;
        lastShowcaseGroupIcons = Collections.emptyList();
        groupLayout.clear();
    }

    private boolean handleShowcaseGroupClick(int mouseX, int mouseY, int button) {
        if (!showcaseGroupSelectorOpen) {
            return false;
        }
        pointerInput.cancelDrag();
        captionFocused = false;
        if (button == 0 && groupLayout.previous != null && groupLayout.previous.contains(mouseX, mouseY)) {
            changeShowcaseGroupPage(-1);
        } else if (button == 0 && groupLayout.next != null && groupLayout.next.contains(mouseX, mouseY)) {
            changeShowcaseGroupPage(1);
        } else if (button == 0 && getShowcaseGroupIconAt(mouseX, mouseY) != null) {
            ResourceLocation component = getShowcaseGroupIconAt(mouseX, mouseY).componentId;
            closeShowcaseGroupSelector();
            selectComponent(component, 0);
        } else if (groupLayout.bounds == null || !groupLayout.bounds.contains(mouseX, mouseY)) {
            closeShowcaseGroupSelector();
        }
        return true;
    }

    private boolean handleShowcaseGroupWheel(int wheel, int mouseX, int mouseY) {
        if (!showcaseGroupSelectorOpen || wheel == 0) {
            return false;
        }
        pointerInput.cancelDrag();
        if (groupLayout.bounds != null && groupLayout.bounds.contains(mouseX, mouseY)) {
            changeShowcaseGroupPage(wheel > 0 ? -1 : 1);
        }
        return true;
    }

    private boolean handleShowcaseGroupKey(int keyCode) {
        if (!showcaseGroupSelectorOpen) {
            return false;
        }
        pointerInput.cancelDrag();
        captionFocused = false;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeShowcaseGroupSelector();
        } else if (keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_PRIOR) {
            changeShowcaseGroupPage(-1);
        } else if (keyCode == Keyboard.KEY_RIGHT || keyCode == Keyboard.KEY_DOWN || keyCode == Keyboard.KEY_NEXT) {
            changeShowcaseGroupPage(1);
        } else if (keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END) {
            changeShowcaseGroupPage(keyCode == Keyboard.KEY_HOME ? -Integer.MAX_VALUE : Integer.MAX_VALUE);
        }
        return true;
    }

    protected void drawShowcaseCaption(PonderScene scene, int previewX, int previewY, int previewWidth, int previewHeight,
        float currentTick, float fade) {
        if (explanationLayout == null) {
            return;
        }
        prepareExplanation(scene, currentTick);
        PreviewDebugLayout.Rect area = explanationLayout.explanation;
        overlayHitAreas.add(area.x, area.y, area.width, area.height);
        runClippedOverlay(area, () -> {
            drawRect(area.x, area.y, area.right(), area.bottom(), 0xDD10161D);
            drawRect(area.x, area.y, area.x + 1, area.bottom(), 0x88626B74);
            if (captionPaging.imageIndex() >= 0) {
                drawGuiTextureOverlays(scene, lastPreviewLayout, currentTick, fade);
            }
            drawCaptionText(captionLines);
        });
    }

    private void invalidateCalloutHits() {
        visibleCallouts = Collections.emptyList();
        calloutTargetPrevious = null;
        calloutTargetNext = null;
    }

    /** Final HUD pass: shared snapshot rendering is scoped to one target at a time. */
    protected void drawForegroundCallouts(PonderScene scene, float tick, float fade) {
        prepareForegroundCallouts(scene, tick);
        if (visibleCallouts.isEmpty()) return;
        PreviewRenderState.runOverlay(() -> {
            for (CalloutView view : visibleCallouts) {
                PreviewDebugLayout.Rect panel = view.placement.panel;
                overlayHitAreas.add(panel.x, panel.y, panel.width, panel.height);
                if (view.target != null && view.connectorVisible) {
                    int startX = MathHelper.clamp(view.target.x, panel.x + 3, panel.right() - 3);
                    int startY = MathHelper.clamp(view.target.y, panel.y + 3, panel.bottom() - 3);
                    drawCaptionConnector(startX, startY, view.target.x, view.target.y, view.color, fade);
                }
            }
            for (CalloutView view : visibleCallouts) {
                drawCallout(scene, view, tick, fade);
            }
        });
    }

    private void prepareForegroundCallouts(PonderScene scene, float tick) {
        invalidateCalloutHits();
        if (explanationLayout == null || fontRenderer == null) return;
        PreviewDebugLayout.Rect area = calloutArea();
        if (area.width < 40 || area.height < 28) return;

        String notice = explanationHoverText != null ? explanationHoverText
            : modelNotice ? modelMode.getNote() : scene == null ? getRegistryStatus(getNoSceneLabel()) : null;
        List<PreviewCalloutContent.Group> groups = scene == null || modelMode.isAppearance()
            ? Collections.emptyList() : PreviewCalloutContent.collect(scene, tick);
        if (notice == null && groups.isEmpty() && scene != null
            && getPreviewFrame(scene, tick).state.visibleBlocks == 0) notice = getNoVisibleBlocksLabel();
        calloutGroups = groups;
        List<CalloutView> candidates = new ArrayList<>();
        Set<Object> liveKeys = new HashSet<>();
        if (notice != null && !notice.isEmpty()) {
            for (PreviewCalloutContent.Group group : groups) liveKeys.add(group.key);
            CalloutView view = calloutViews.computeIfAbsent("system-note", CalloutView::new);
            updateCalloutContent(view, notice, Collections.emptyList(), Collections.singletonList(notice));
            view.color = 0xE8D6A8;
            view.connectorVisible = false;
            view.target = null;
            view.targetBounds = null;
            candidates.add(view);
            liveKeys.add(view.key);
        } else {
            for (PreviewCalloutContent.Group group : groups) {
                List<PonderScene.OverlayEvent> images = new ArrayList<>();
                for (PonderScene.OverlayEvent event : group.guiEvents()) {
                    if (event.getType() != PonderScene.OverlayEventType.GUI_TEXTURE) continue;
                    PonderGuiSnapshotRegistry.Snapshot snapshot = resolveSnapshot(event, tick);
                    if (snapshot != null) event.applySnapshot(snapshot, event.getOffsetX(), event.getOffsetY());
                    if (event.getTextureLocation() != null || snapshot != null && snapshot.renderer != null) images.add(event);
                }
                if (group.text().isEmpty() && images.isEmpty()) continue;
                CalloutView view = calloutViews.computeIfAbsent(group.key, CalloutView::new);
                List<Object> identity = new ArrayList<>(group.events);
                identity.add(group.text());
                updateCalloutContent(view, group.text(), images, identity);
                view.color = group.color;
                view.eventTick = group.events.stream().mapToInt(PonderScene.OverlayEvent::getTick).max().orElse(0);
                view.connectorVisible = group.connectorVisible;
                view.targetBounds = projectCalloutTarget(scene, tick, group.targetPositions, group.pointAt, group.scenePoint);
                view.target = view.targetBounds == null ? null : new Point(
                    view.targetBounds.x + view.targetBounds.width / 2, view.targetBounds.y + view.targetBounds.height / 2);
                candidates.add(view);
                liveKeys.add(view.key);
            }
        }
        calloutViews.keySet().retainAll(liveKeys);
        if (candidates.isEmpty()) return;
        int selected = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (Objects.equals(candidates.get(i).key, selectedCallout)) selected = i;
        }
        if (notice == null && focusedCallout == null && !showcaseGroupSelectorOpen) {
            for (int i = 0; i < candidates.size(); i++) {
                if (candidates.get(i).eventTick >= candidates.get(selected).eventTick) selected = i;
            }
            selectedCallout = candidates.get(selected).key;
        }
        if (selectedCallout == null || !liveKeys.contains(selectedCallout)) {
            selected = candidates.size() - 1;
            if (notice == null) selectedCallout = candidates.get(selected).key;
        }
        calloutGroupCount = candidates.size();
        calloutSelectedIndex = selected;
        calloutKeys = new ArrayList<>();
        for (CalloutView view : candidates) calloutKeys.add(view.key);
        List<PreviewDebugLayout.Rect> obstacles = calloutObstacles(scene, tick);
        PreviewDebugLayout.Rect modelBounds = projectVisibleModelBounds(scene, tick);
        for (CalloutView view : candidates) {
            if (view.targetBounds != null) obstacles.add(view.targetBounds);
        }
        List<CalloutView> shown = new ArrayList<>();
        for (int offset = 0; offset < candidates.size(); offset++) {
            CalloutView view = candidates.get((selected + offset) % candidates.size());
            int panelWidth = Math.min(area.width, Math.max(124, Math.min(220, area.width / 2 - 12)));
            wrapCallout(view, panelWidth);
            int tabs = calloutGroupCount > 1 ? 16 : 0;
            int desiredHeight = view.images.isEmpty() ? 12 + Math.min(6, Math.max(1, view.lines.size())) * calloutLineHeight()
                : 12 + PreviewGuiPageLayout.preferredHeight(view.images, panelWidth - 12)
                    + (view.images.size() > 1 || !view.lines.isEmpty() ? PreviewCaptionPaging.FOOTER_HEIGHT : 0);
            desiredHeight = Math.min(area.height, Math.max(36 + tabs, desiredHeight + tabs));
            int minimumHeight = view.images.isEmpty() ? 36 + tabs
                : PreviewGuiPageLayout.minimumReadableHeight(desiredHeight, tabs);
            PreviewCalloutLayout.Placement placed = PreviewCalloutLayout.place(area, view.targetBounds, panelWidth,
                desiredHeight, obstacles, view.placement, modelBounds);
            if (shown.isEmpty() && (placed.panel.width < 100 || placed.panel.height < minimumHeight)) {
                // On cramped screens a readable paged edge panel takes priority over soft model avoidance.
                List<PreviewDebugLayout.Rect> hard = new ArrayList<>();
                if (showcaseGroupSelectorOpen && groupLayout.bounds != null) hard.add(groupLayout.bounds);
                for (GuiButton button : buttonList) if (button.visible) hard.add(
                    new PreviewDebugLayout.Rect(button.x, button.y, button.width, button.height));
                placed = PreviewCalloutLayout.place(area, null, panelWidth, desiredHeight, hard, null);
            }
            if (placed.panel.width < 100 || placed.panel.height < 36 + tabs) continue;
            // One readable edge panel owns overflow targets through its target tabs.
            if (!shown.isEmpty() && placed.fallback) continue;
            view.placement = placed;
            wrapCallout(view, placed.panel.width);
            PreviewDebugLayout.Rect panel = placed.panel;
            view.content = new PreviewDebugLayout.Rect(panel.x, panel.y + tabs, panel.width, panel.height - tabs);
            view.paging.configure(view.identity, view.lines.size(), view.content.height,
                view.images.size(), calloutLineHeight());
            view.previous = null;
            view.next = null;
            if (view.paging.isPaged()) {
                int footer = view.content.bottom() - PreviewCaptionPaging.FOOTER_HEIGHT;
                view.previous = new PreviewDebugLayout.Rect(panel.x + 3, footer, 22, 14);
                view.next = new PreviewDebugLayout.Rect(panel.right() - 25, footer, 22, 14);
            }
            shown.add(view);
            obstacles.add(panel);
            if (placed.fallback) break;
        }
        visibleCallouts = shown;
        if (!shown.isEmpty() && calloutGroupCount > 1) {
            PreviewDebugLayout.Rect first = shown.get(0).placement.panel;
            calloutTargetPrevious = new PreviewDebugLayout.Rect(first.x + 2, first.y + 1, 20, 14);
            calloutTargetNext = new PreviewDebugLayout.Rect(first.right() - 22, first.y + 1, 20, 14);
        }
    }

    private PreviewDebugLayout.Rect calloutArea() {
        if (externalOverlayArea != null) return externalOverlayArea;
        if (showcaseMode) {
            PreviewExplanationLayout regions = PreviewExplanationLayout.showcase(width, height,
                fontRenderer == null ? 9 : fontRenderer.FONT_HEIGHT);
            if (PreviewExplanationLayout.isCompact(width, height)) return regions.explanation;
            return regions.model;
        }
        PreviewDebugLayout.Rect preview = getDebugLayout().preview;
        return new PreviewDebugLayout.Rect(preview.x + 7, preview.y + 19,
            preview.width - 14, Math.max(0, preview.height - 40));
    }

    private int calloutLineHeight() {
        return Math.max(10, fontRenderer.FONT_HEIGHT);
    }

    private void updateCalloutContent(CalloutView view, String text, List<PonderScene.OverlayEvent> images,
        List<Object> identity) {
        if (!identity.equals(view.identity)) {
            view.identity = identity;
            view.paging.clear();
        }
        view.text = text;
        view.images = PreviewGuiPageLayout.pages(images);
    }

    private void wrapCallout(CalloutView view, int width) {
        if (view.wrappedWidth != width || !view.text.equals(view.wrappedText)) {
            view.wrappedWidth = width;
            view.wrappedText = view.text;
            view.lines = view.text.isEmpty() ? Collections.emptyList()
                : PreviewTextLayout.wrap(fontRenderer, view.text, Math.max(1, width - 12));
        }
    }

    private List<PreviewDebugLayout.Rect> calloutObstacles(PonderScene scene, float tick) {
        List<PreviewDebugLayout.Rect> result = new ArrayList<>();
        for (GuiButton button : buttonList) if (button.visible) {
            result.add(new PreviewDebugLayout.Rect(button.x, button.y, button.width, button.height));
        }
        if (showcaseGroupSelectorOpen && groupLayout.bounds != null) result.add(groupLayout.bounds);
        if (lastNextUpCardWidth > 0) result.add(new PreviewDebugLayout.Rect(lastNextUpCardX, lastNextUpCardY,
            lastNextUpCardWidth, lastNextUpCardHeight));
        if (scene != null && lastPreviewLayout != null) {
            for (PonderScene.ActorEvent event : scene.getActorEvents()) {
                if (event.getType() != PonderScene.ActorEventType.SPAWN || event.getTick() > tick
                    || event.getActorKind() != PonderScene.ActorKind.PLAYER) continue;
                for (PreviewPlayerTimeline.State player : PreviewPlayerTimeline.evaluate(scene.getActorEvents(), tick)) {
                    if (player.actorId == event.getActorId() && player.fade > 0) {
                        List<Vec3d> corners = new ArrayList<>();
                        for (double x : new double[] {-0.5, 0.5}) {
                            for (double y : new double[] {0, 2}) {
                                for (double z : new double[] {-0.5, 0.5}) corners.add(player.position.add(x, y, z));
                            }
                        }
                        PreviewDebugLayout.Rect bounds = projectTransformedBounds(scene, tick, corners);
                        if (bounds != null) result.add(bounds);
                    }
                }
            }
        }
        return result;
    }

    private PreviewDebugLayout.Rect projectCalloutTarget(PonderScene scene, float tick,
        List<BlockPos> positions, Vec3d point, boolean scenePoint) {
        if (scene == null || lastPreviewLayout == null) return null;
        if (scenePoint) return point == null ? null : projectTransformedBounds(scene,tick,Collections.singletonList(point));
        PreviewFrame frame = getPreviewFrame(scene, tick);
        List<Vec3d> points = PreviewOutlineGeometry.targetPoints(positions, frame.state, new PreviewBlockAccess(frame.state));
        if (points.isEmpty() && positions.stream().anyMatch(frame.state.blocksByPosition::containsKey)) return null;
        if (points.isEmpty() && point != null) {
            PonderSceneRuntime.RuntimeBlockState owner = positions.isEmpty() ? null
                : frame.state.blocksByPosition.get(positions.get(0));
            points = Collections.singletonList(PonderSceneRuntime.transformPoint(owner, point));
        }
        return projectTransformedBounds(scene, tick, points);
    }

    private PreviewDebugLayout.Rect projectVisibleModelBounds(PonderScene scene, float tick) {
        if (scene == null || lastPreviewLayout == null || modelMode.isAppearance()) return null;
        PreviewFrame frame = getPreviewFrame(scene, tick);
        if (!frame.calloutModelBoundsComputed) {
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
            for (PonderSceneRuntime.RuntimeBlockState block : frame.state.blocksByPosition.values()) {
                if (!block.visible || block.destroyed || block.fade <= 0 || block.currentState == null
                    || block.currentState.getBlock() == Blocks.AIR) continue;
                boolean rotated = false;
                for (PonderSceneRuntime.TransformStep step : block.transforms) {
                    if (step.rotation != null) { rotated = true; break; }
                }
                if (!rotated) {
                    minX = Math.min(minX, block.renderCenterX - 0.5D); maxX = Math.max(maxX, block.renderCenterX + 0.5D);
                    minY = Math.min(minY, block.renderCenterY - 0.5D); maxY = Math.max(maxY, block.renderCenterY + 0.5D);
                    minZ = Math.min(minZ, block.renderCenterZ - 0.5D); maxZ = Math.max(maxZ, block.renderCenterZ + 0.5D);
                    continue;
                }
                for (int corner = 0; corner < 8; corner++) {
                    Vec3d point = PonderSceneRuntime.transformPoint(block, new Vec3d(
                        block.pos.getX() + ((corner & 1) == 0 ? 0 : 1),
                        block.pos.getY() + ((corner & 2) == 0 ? 0 : 1),
                        block.pos.getZ() + ((corner & 4) == 0 ? 0 : 1)));
                    minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
                    minY = Math.min(minY, point.y); maxY = Math.max(maxY, point.y);
                    minZ = Math.min(minZ, point.z); maxZ = Math.max(maxZ, point.z);
                }
            }
            if (Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(minZ)
                && Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ)) {
                frame.calloutModelBounds = new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
            }
            frame.calloutModelBoundsComputed = true;
        }
        AxisAlignedBB bounds = frame.calloutModelBounds;
        if (bounds == null) return null;
        // Cache one conservative geometry envelope per runtime frame; camera changes project only eight corners.
        List<Vec3d> corners = new ArrayList<>(8);
        for (int corner = 0; corner < 8; corner++) corners.add(new Vec3d(
            (corner & 1) == 0 ? bounds.minX : bounds.maxX, (corner & 2) == 0 ? bounds.minY : bounds.maxY,
            (corner & 4) == 0 ? bounds.minZ : bounds.maxZ));
        return projectTransformedBounds(scene, tick, corners);
    }

    private PreviewDebugLayout.Rect projectTransformedBounds(PonderScene scene, float tick, List<Vec3d> points) {
        if (points.isEmpty() || lastPreviewLayout == null) return null;
        PreviewBounds sceneBounds = lastSceneBounds == null ? PonderScenePreview.computeBounds(scene) : lastSceneBounds;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Vec3d point : points) {
            Point projected;
            if (worldProjection == null) projected = projectTransformedPoint(scene, sceneBounds, lastPreviewLayout, tick, point);
            else {
                java.awt.Point screen = worldProjection.project(point);
                projected = screen == null ? null : new Point(screen.x, screen.y);
            }
            if (projected == null) continue;
            minX = Math.min(minX, projected.x); minY = Math.min(minY, projected.y);
            maxX = Math.max(maxX, projected.x); maxY = Math.max(maxY, projected.y);
        }
        return minX == Integer.MAX_VALUE ? null : new PreviewDebugLayout.Rect(minX - 2, minY - 2,
            Math.max(4, maxX - minX + 4), Math.max(4, maxY - minY + 4));
    }

    private void drawCallout(PonderScene scene, CalloutView view, float tick, float fade) {
        PreviewDebugLayout.Rect panel = view.placement.panel;
        runClippedOverlay(panel, () -> {
            drawRect(panel.x, panel.y, panel.right(), panel.bottom(), 0xF2141B20);
            drawRect(panel.x, panel.y, panel.x + 2, panel.bottom(), withAlpha(view.color, 240 * fade));
            withCallout(view, () -> {
                if (view.paging.imageIndex() >= 0) drawGuiTextureOverlays(scene, lastPreviewLayout, tick, fade);
                // Native snapshot callbacks may have temporarily enabled depth.
                PreviewRenderState.runOverlay(() -> drawCaptionText(view.lines));
            });
            if (calloutGroupCount > 1) {
                drawExplanationText("<", panel.x + 2, panel.y + 3, 20, 10, true, 0xE6EEF2);
                drawExplanationText(">", panel.right() - 22, panel.y + 3, 20, 10, true, 0xE6EEF2);
                drawExplanationText(net.minecraft.client.resources.I18n.format("ponder.ui.callout.target",
                    calloutKeys.indexOf(view.key) + 1, calloutGroupCount), panel.x + 24, panel.y + 3,
                    panel.width - 48, 10, true, 0xBFCBD2);
            }
        });
    }

    private void withCallout(CalloutView view, Runnable render) {
        PreviewCaptionPaging oldPaging = captionPaging;
        PreviewDebugLayout.Rect oldBounds = captionBounds, oldPrevious = captionPrevious, oldNext = captionNext;
        List<List<PonderScene.OverlayEvent>> oldImages = explanationImages;
        try {
            captionPaging = view.paging;
            captionBounds = view.content;
            captionPrevious = view.previous;
            captionNext = view.next;
            explanationImages = view.images;
            render.run();
        } finally {
            captionPaging = oldPaging;
            captionBounds = oldBounds;
            captionPrevious = oldPrevious;
            captionNext = oldNext;
            explanationImages = oldImages;
        }
    }

    private CalloutView calloutAt(int x, int y) {
        if (showcaseGroupSelectorOpen && groupLayout.bounds != null && groupLayout.bounds.contains(x, y)) return null;
        for (CalloutView view : visibleCallouts) if (view.placement.panel.contains(x, y)) return view;
        return null;
    }

    private boolean handleCalloutClick(int x, int y, int button) {
        CalloutView view = calloutAt(x, y);
        if (view == null) return false;
        pointerInput.cancelDrag();
        focusedCallout = view.key;
        playing = false;
        if (button == 0) {
            PreviewDebugLayout.Rect panel = view.placement.panel;
            if (calloutGroupCount > 1 && y < panel.y + 16 && x < panel.x + 22) turnCalloutTarget(view.key, -1);
            else if (calloutGroupCount > 1 && y < panel.y + 16 && x >= panel.right() - 22) turnCalloutTarget(view.key, 1);
            else if (view.previous != null && view.previous.contains(x, y)) view.paging.turn(-1);
            else if (view.next != null && view.next.contains(x, y)) view.paging.turn(1);
        }
        updateButtonState();
        return true;
    }

    private boolean handleCalloutWheel(int wheel, int x, int y) {
        CalloutView view = calloutAt(x, y);
        if (wheel == 0 || view == null) return false;
        pointerInput.cancelDrag();
        focusedCallout = view.key;
        view.paging.turn(wheel > 0 ? -1 : 1);
        playing = false;
        updateButtonState();
        return true;
    }

    private void turnCalloutTarget(Object current, int direction) {
        if (showcaseGroupSelectorOpen || calloutKeys.isEmpty()) return;
        int index = Math.floorMod(calloutKeys.indexOf(current) + direction, calloutKeys.size());
        selectedCallout = calloutKeys.get(index);
        focusedCallout = selectedCallout;
    }

    private static final class CalloutView {
        final Object key;
        final PreviewCaptionPaging paging = new PreviewCaptionPaging();
        List<Object> identity = Collections.emptyList();
        String text = "", wrappedText = "";
        int wrappedWidth;
        List<String> lines = Collections.emptyList();
        List<List<PonderScene.OverlayEvent>> images = Collections.emptyList();
        PreviewCalloutLayout.Placement placement;
        PreviewDebugLayout.Rect content, previous, next, targetBounds;
        Point target;
        int color = 0xFFFFFF;
        int eventTick;
        boolean connectorVisible;
        CalloutView(Object key) { this.key = key; }
    }

    private void prepareExplanation(PonderScene scene, float currentTick) {
        List<String> paragraphs = new ArrayList<>();
        List<Object> identity = new ArrayList<>();
        identity.add(scene);
        List<PonderScene.OverlayEvent> textures = new ArrayList<>();
        if (explanationHoverText != null) {
            paragraphs.add(explanationHoverText);
        } else if (modelNotice) {
            paragraphs.add(modelMode.getNote());
        } else if (scene == null) {
            paragraphs.add(getRegistryStatus(getNoSceneLabel()));
        } else if (!modelMode.isAppearance()) {
            for (PonderScene.OverlayEvent event : scene.getOverlayEvents()) {
                if (!isOverlayActive(event, currentTick) || scene.isOverlayCleared(event, currentTick)) {
                    continue;
                }
                if (event.getText() != null && !event.getText().isEmpty()) {
                    paragraphs.add(event.getText());
                    identity.add(event);
                }
                if (event.getType() == PonderScene.OverlayEventType.CONTROLS) {
                    paragraphs.add(controlDescription(event));
                    identity.add(event);
                } else if (event.getType() == PonderScene.OverlayEventType.GUI_TEXTURE) {
                    PonderGuiSnapshotRegistry.Snapshot snapshot = resolveSnapshot(event, currentTick);
                    if (snapshot != null) {
                        event.applySnapshot(snapshot, event.getOffsetX(), event.getOffsetY());
                    }
                    if (event.getTextureLocation() != null || snapshot != null && snapshot.renderer != null) {
                        textures.add(event);
                        identity.add(event);
                    }
                }
            }
            if (paragraphs.isEmpty() && textures.isEmpty() && getPreviewFrame(scene, currentTick).state.visibleBlocks == 0) {
                paragraphs.add(getNoVisibleBlocksLabel());
            }
        }
        String text = String.join("\n\n", paragraphs);
        identity.add(text);
        if (!identity.equals(explanationIdentity)) {
            explanationIdentity = identity;
        }
        float fontScale = Math.min(1F, explanationLineHeight() / (float) Math.max(1, fontRenderer.FONT_HEIGHT));
        int textWidth = Math.max(1, (int) ((explanationLayout.explanation.width - 12) / fontScale));
        if (captionTextWidth != textWidth || !text.equals(captionText)) {
            captionText = text;
            captionTextWidth = textWidth;
            captionLines = text.isEmpty() ? Collections.emptyList()
                : PreviewTextLayout.wrap(fontRenderer, text, textWidth);
        }
        explanationImages = PreviewGuiPageLayout.pages(textures);
        layoutExplanation(explanationIdentity, captionLines.size(), explanationImages.size());
    }

    private void layoutExplanation(Object identity, int lineCount, int imageCount) {
        captionPaging.configure(identity, lineCount, explanationLayout.explanation.height, imageCount,
            explanationLineHeight());
        PreviewDebugLayout.Rect area = explanationLayout.explanation;
        setCaptionBounds(area.x, area.y, area.width, area.height);
    }

    private int explanationLineHeight() {
        return Math.min(Math.max(PreviewCaptionPaging.LINE_HEIGHT, fontRenderer == null ? 0 : fontRenderer.FONT_HEIGHT),
            Math.max(1, explanationLayout.explanation.height - PreviewCaptionPaging.FOOTER_HEIGHT - 6));
    }

    protected void updateExplanationHover(int mouseX, int mouseY) {
        if (showcaseGroupSelectorOpen) {
            clearExplanationHover();
            return;
        }
        // Keep the note stable while crossing into the dock or paging through it.
        if (explanationHoverText != null && (captionFocused
            || calloutAt(mouseX, mouseY) != null
            || explanationLayout != null && explanationLayout.explanation.contains(mouseX, mouseY))) {
            explanationHoverTicks = EXPLANATION_HOVER_TICKS;
            return;
        }
        String hint = getModelHoverHint(mouseX, mouseY);
        if (hint == null && !showcaseMode) {
            hint = getDebugHoverHint(mouseX, mouseY);
        }
        if (hint != null && !hint.isEmpty()) {
            explanationHoverText = hint;
            explanationHoverTicks = EXPLANATION_HOVER_TICKS;
        } else if (explanationHoverTicks == 0) {
            clearExplanationHover();
        }
    }

    private void clearExplanationHover() {
        explanationHoverText = null;
        explanationHoverTicks = 0;
    }

    private void togglePlayback() {
        playing = !playing;
        if (playing) {
            captionFocused = false;
            focusedCallout = null;
            clearExplanationHover();
        }
    }

    private boolean isOverlayActive(PonderScene.OverlayEvent event, float tick) {
        return event.getTick() <= tick && tick <= (long) event.getTick() + event.getDuration();
    }

    private String controlDescription(PonderScene.OverlayEvent event) {
        return PreviewCalloutContent.controlDescription(event, net.minecraft.client.resources.I18n::format);
    }

    private void runClippedOverlay(PreviewDebugLayout.Rect area, Runnable render) {
        PreviewRenderState.runOverlay(() -> {
            enablePreviewScissor(new PreviewLayout(area.x, area.y, area.width, area.height));
            try {
                render.run();
            } finally {
                disablePreviewScissor();
            }
        });
    }

    protected void renderOverlayItem(ItemStack stack, int x, int y) {
        PreviewRenderState.runOverlay(() -> {
            GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_VIEWPORT_BIT);
            try (PreviewModelViewGuard projection = new PreviewModelViewGuard(GL11.GL_PROJECTION);
                PreviewModelViewGuard matrix = new PreviewModelViewGuard()) {
                RenderHelper.enableGUIStandardItemLighting();
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
            } finally {
                GL11.glPopAttrib();
            }
        });
    }

    private void setCaptionBounds(int boxX, int boxY, int boxWidth, int boxHeight) {
        captionBounds = new PreviewDebugLayout.Rect(boxX, boxY, boxWidth, boxHeight);
        captionPrevious = null;
        captionNext = null;
        if (captionPaging.isPaged()) {
            int footerY = boxY + boxHeight - PreviewCaptionPaging.FOOTER_HEIGHT;
            captionPrevious = new PreviewDebugLayout.Rect(boxX + 3, footerY, 22, 14);
            captionNext = new PreviewDebugLayout.Rect(boxX + boxWidth - 25, footerY, 22, 14);
        }
    }

    private void drawCaptionText(List<String> lines) {
        for (int i = captionPaging.firstLine(); i < captionPaging.endLine(); i++) {
            drawWrappedExplanationText(lines.get(i), captionBounds.x + 6,
                captionBounds.y + 6 + (i - captionPaging.firstLine()) * captionPaging.lineHeight(),
                captionBounds.width - 12, captionPaging.lineHeight(), false, 0xF2F5F8);
        }
        if (captionPaging.isPaged()) {
            drawExplanationText("<", captionPrevious.x, captionPrevious.y + 3, captionPrevious.width, 10, true,
                captionPaging.page() > 0 ? 0xF2F5F8 : 0x58616A);
            drawExplanationText(">", captionNext.x, captionNext.y + 3, captionNext.width, 10, true,
                captionPaging.page() + 1 < captionPaging.pageCount() ? 0xF2F5F8 : 0x58616A);
            drawExplanationText((captionPaging.page() + 1) + " / " + captionPaging.pageCount(),
                captionBounds.x + 27, captionPrevious.y + 3, captionBounds.width - 54, 10, true, 0xAEB8C1);
        }
    }

    private void drawExplanationText(String text, int x, int y, int width, int height, boolean centered, int color) {
        float scale = Math.min(1F, height / (float) Math.max(1, fontRenderer.FONT_HEIGHT));
        String fitted = fontRenderer.trimStringToWidth(text, Math.max(1, (int) (width / scale)));
        drawWrappedExplanationText(fitted, x, y, width, height, centered, color);
    }

    private void drawWrappedExplanationText(String text, int x, int y, int width, int height, boolean centered, int color) {
        float scale = Math.min(1F, height / (float) Math.max(1, fontRenderer.FONT_HEIGHT));
        int offset = centered ? Math.max(0, (width - Math.round(fontRenderer.getStringWidth(text) * scale)) / 2) : 0;
        if (scale == 1F) {
            drawString(fontRenderer, text, x + offset, y, color);
            return;
        }
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + offset, y, 0F);
            GlStateManager.scale(scale, scale, 1F);
            drawString(fontRenderer, text, 0, 0, color);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void changeCaptionPage(int direction) {
        captionPaging.turn(direction);
        focusCaptionForReading();
    }

    private void focusCaptionForReading() {
        pointerInput.cancelDrag();
        captionFocused = true;
        playing = false;
        updateButtonState();
    }

    protected void drawGuiTextureOverlays(PonderScene scene, PreviewLayout layout, float currentTick, float fade) {
        if (scene == null || fade <= 0.0F || modelMode.isAppearance() || modelNotice) {
            return;
        }

        List<GuiOverlayPlacement> guiPlacements = getActiveGuiOverlayPlacements(scene, layout, currentTick);
        for (GuiOverlayPlacement placement : guiPlacements) {
            PonderScene.OverlayEvent overlayEvent = placement.overlayEvent;
            float overlayFade = computeOverlayFade(overlayEvent.getTick(), overlayEvent.getDuration(), currentTick)
                * fade;
            if (overlayFade <= 0.0F) {
                continue;
            }

            overlayHitAreas.add(placement.panelX, placement.panelY, placement.panelWidth, placement.panelHeight);
            if (overlayEvent.isFramed()) {
                drawGuiTexturePanel(placement.panelX, placement.panelY, placement.panelWidth, placement.panelHeight,
                    overlayEvent.getColor(), overlayFade);
            }

            if (overlayEvent.isFramed() && overlayEvent.isConnectorVisible() && placement.targetPoint != null) {
                int startX = placement.panelX + placement.panelWidth / 2;
                int startY = placement.aboveTarget ? placement.panelY + placement.panelHeight : placement.panelY;
                drawCaptionConnector(startX, startY, placement.targetPoint.x, placement.targetPoint.y,
                    overlayEvent.getColor(), overlayFade * 0.85F);
            }

            PonderGuiSnapshotRegistry.Snapshot snapshot = resolveSnapshot(overlayEvent, currentTick);
            if (snapshot != null && snapshot.renderer != null) {
                GlStateManager.enableTexture2D();
                GlStateManager.enableBlend();
                GlStateManager.enableAlpha();
                GlStateManager.color(1.0F, 1.0F, 1.0F, overlayFade);
                try {
                    PreviewRenderState.runOverlay(() -> {
                        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_VIEWPORT_BIT);
                        try (PreviewModelViewGuard projection = new PreviewModelViewGuard(GL11.GL_PROJECTION);
                            PreviewModelViewGuard matrix = new PreviewModelViewGuard()) {
                            setPreviewScissor(new PreviewLayout(placement.drawX, placement.drawY,
                                placement.drawWidth, placement.drawHeight));
                            PreviewClientArrayGuard.run(() -> snapshot.renderer.render(placement.drawX,
                                placement.drawY, placement.drawWidth, placement.drawHeight, currentTick, overlayFade));
                        } finally {
                            GL11.glPopAttrib();
                        }
                    });
                } catch (RuntimeException | LinkageError failure) {
                    warnRenderFailure("gui-snapshot:" + overlayEvent.getGuiSnapshotId(),
                        "Ponder GUI snapshot renderer failed for " + overlayEvent.getGuiSnapshotId(), failure);
                }
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            } else {
                mc.getTextureManager().bindTexture(overlayEvent.getTextureLocation());
                GlStateManager.enableBlend();
                GlStateManager.enableAlpha();
                GlStateManager.color(1.0F, 1.0F, 1.0F, overlayFade);
                if (overlayEvent.isStretchTexture()) {
                    drawStretchGuiTexture(placement, overlayEvent);
                } else {
                    drawScaledCustomSizeModalRect(placement.drawX, placement.drawY, overlayEvent.getTextureU(),
                        overlayEvent.getTextureV(), overlayEvent.getRegionWidth(), overlayEvent.getRegionHeight(),
                        placement.drawWidth, placement.drawHeight, overlayEvent.getTextureWidth(),
                        overlayEvent.getTextureHeight());
                }
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }

        for (GuiHighlightPlacement placement : getActiveGuiHighlightPlacements(scene, layout, currentTick, guiPlacements)) {
            PonderScene.OverlayEvent overlayEvent = placement.overlayEvent;
            float overlayFade = computeOverlayFade(overlayEvent.getTick(), overlayEvent.getDuration(), currentTick)
                * fade;
            if (overlayFade <= 0.0F) {
                continue;
            }
            overlayHitAreas.add(placement.rectX - 1, placement.rectY - 1,
                placement.rectWidth + 2, placement.rectHeight + 2);
            drawGuiHighlight(placement.rectX, placement.rectY, placement.rectWidth, placement.rectHeight,
                overlayEvent.getColor(), overlayFade);
        }
    }

    protected void drawSceneSpaceOverlays(PonderScene scene, PreviewLayout layout, float currentTick, float fade) {
        if (scene == null || layout == null || fade <= 0.0F || modelMode.isAppearance() || modelNotice) {
            return;
        }
        runClippedOverlay(new PreviewDebugLayout.Rect(layout.originX, layout.originY, layout.width, layout.height),
            () -> drawModelSpaceMarkers(scene, layout, currentTick, fade));
    }

    private void drawModelSpaceMarkers(PonderScene scene, PreviewLayout layout, float currentTick, float fade) {
        PreviewBounds bounds = PonderScenePreview.computeBounds(scene);
        drawActorOverlay(scene, bounds, layout, currentTick, fade);
        drawParticleEffectsOverlay(scene, bounds, layout, currentTick, fade);
        drawPointOfInterestOverlay(scene, bounds, layout, currentTick, fade);
    }

    private void drawControlsOverlay(PonderScene scene, PreviewBounds bounds, PreviewLayout layout, float currentTick,
        PonderScene.OverlayEvent overlayEvent, float fade) {
        drawTargetMarker(projectOverlayPoint(scene, bounds, layout, currentTick, overlayEvent),
            overlayEvent.getColor(), fade);
    }

    private void drawTargetMarker(@Nullable Point target, int color, float fade) {
        if (target == null) {
            return;
        }
        int accent = withAlpha(color, fade * 235F);
        drawRect(target.x - 3, target.y - 3, target.x + 4, target.y - 2, accent);
        drawRect(target.x - 3, target.y + 3, target.x + 4, target.y + 4, accent);
        drawRect(target.x - 3, target.y - 2, target.x - 2, target.y + 3, accent);
        drawRect(target.x + 3, target.y - 2, target.x + 4, target.y + 3, accent);
    }

    private String getPointingLabel(Pointing pointing) {
        if (pointing == null) {
            return "INPUT";
        }
        switch (pointing) {
            case UP:
                return "UP";
            case DOWN:
                return "DOWN";
            case LEFT:
                return "LEFT";
            case RIGHT:
                return "RIGHT";
            default:
                return pointing.name();
        }
    }

    private void drawPointOfInterestOverlay(PonderScene scene, PreviewBounds bounds, PreviewLayout layout, float currentTick,
        float fade) {
        PonderScene.PoiEvent poiEvent = getActivePoiEvent(scene, currentTick);
        if (poiEvent == null) {
            return;
        }

        Point target = projectScenePoint(scene, bounds, layout, currentTick, poiEvent.getLocation());
        if (target == null) {
            return;
        }

        int accent = withAlpha(PonderPalette.RED.getColor(), fade * 235.0F);
        int fill = withAlpha(PonderPalette.RED.getColor(), fade * 72.0F);
        drawRect(target.x - 5, target.y - 1, target.x + 6, target.y + 1, accent);
        drawRect(target.x - 1, target.y - 5, target.x + 1, target.y + 6, accent);
        drawRect(target.x - 2, target.y - 2, target.x + 3, target.y + 3, fill);
    }

    private void drawParticleEffectsOverlay(PonderScene scene, PreviewBounds bounds, PreviewLayout layout,
        float currentTick, float fade) {
        for (PonderScene.ParticleEffectEvent event : scene.getParticleEvents()) {
            for (PreviewParticleTimeline.Sample sample : PreviewParticleTimeline.evaluate(event, currentTick, fade)) {
                Point projected = projectScenePoint(scene, bounds, layout, currentTick, sample.position);
                if (projected == null) {
                    continue;
                }
                int size = sample.radius;
                drawRect(projected.x - size, projected.y - size, projected.x + size + 1, projected.y + size + 1,
                    sample.argb());
            }
        }
    }

    private void drawActorOverlay(PonderScene scene, PreviewBounds bounds, PreviewLayout layout, float currentTick,
        float fade) {
        List<ActorRuntimeState> actors = getActorRuntimeStates(scene, currentTick);
        for (ActorRuntimeState actor : actors) {
            if (!actor.visible || actor.fade <= 0.0F || actor.kind == PonderScene.ActorKind.CART
                || actor.kind == PonderScene.ActorKind.ITEM) {
                continue;
            }

            Point target = projectScenePoint(scene, bounds, layout, currentTick, actor.position);
            if (target == null) {
                continue;
            }

            int baseColor = getActorBaseColor(actor);
            int border = withAlpha(baseColor, fade * actor.fade * 235.0F);
            int fill = withAlpha(baseColor, fade * actor.fade * 72.0F);
            int boxSize = actor.kind == PonderScene.ActorKind.BIRB ? 8 : 10;
            if (actor.kind != PonderScene.ActorKind.ITEM) {
                drawRect(target.x - boxSize / 2, target.y - boxSize / 2, target.x + boxSize / 2 + 1,
                    target.y + boxSize / 2 + 1, fill);
                drawRect(target.x - boxSize / 2, target.y - boxSize / 2, target.x + boxSize / 2 + 1,
                    target.y - boxSize / 2 + 1, border);
                drawRect(target.x - boxSize / 2, target.y + boxSize / 2, target.x + boxSize / 2 + 1,
                    target.y + boxSize / 2 + 1, border);
                drawRect(target.x - boxSize / 2, target.y - boxSize / 2, target.x - boxSize / 2 + 1,
                    target.y + boxSize / 2 + 1, border);
                drawRect(target.x + boxSize / 2, target.y - boxSize / 2, target.x + boxSize / 2 + 1,
                    target.y + boxSize / 2 + 1, border);
            }

            double yawDegrees = actor.kind == PonderScene.ActorKind.CART ? actor.cartYaw : actor.rotation.y;
            double yawRadians = Math.toRadians(yawDegrees);
            int lineX = target.x + (int) Math.round(Math.sin(yawRadians) * (boxSize + 2));
            int lineY = target.y - (int) Math.round(Math.cos(yawRadians) * (boxSize + 2));
            drawLineSegment(target.x, target.y, lineX, lineY, border, 1.5F);

        }
        for (PreviewActorTimeline.State actor : PreviewActorTimeline.evaluate(scene.getActorEvents(), currentTick)) {
            if (!actor.visible || !actor.getNbt().hasKey("CustomName", 8) || actor.displayName == null || actor.displayName.isEmpty()) continue;
            Point target = projectScenePoint(scene, bounds, layout, currentTick,
                actor.position.add(0, actor.kind == PonderScene.ActorKind.PLAYER ? 2.25D : 0.8D, 0));
            if (target == null) continue;
            List<String> lines = PreviewTextLayout.wrap(fontRenderer, actor.displayName, Math.min(120, Math.max(1, layout.width - 8)));
            int y = target.y - lines.size() * fontRenderer.FONT_HEIGHT;
            for (String line : lines) {
                fontRenderer.drawStringWithShadow(line, target.x - fontRenderer.getStringWidth(line) / 2F, y, 0xFFFFFF);
                y += fontRenderer.FONT_HEIGHT;
            }
        }
    }

    private void renderActorPreviews(PonderScene scene, float currentTick) {
        playerRenderer.renderPlayers(scene, currentTick, playerSkinMode);
        List<ActorRuntimeState> actors = getActorRuntimeStates(scene, currentTick);
        if (actors.isEmpty()) {
            return;
        }

        PreviewRenderState.runIsolated(() -> {
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.disableCull();
            for (ActorRuntimeState actor : actors) {
                if (!actor.visible || actor.fade <= 0.0F) {
                    continue;
                }
                renderActorPreview(actor, currentTick, scene);
            }
        });
    }

    private void renderActorPreview(ActorRuntimeState actor, float currentTick, PonderScene scene) {
        if (actor.kind == PonderScene.ActorKind.CART) {
            playerRenderer.renderCart(actor.position, actor.cartYaw,
                PreviewPlayerRenderer.cartPitch(scene.getActorEvents(), actor.actorId, currentTick), actor.fade);
            return;
        }
        if (actor.kind == PonderScene.ActorKind.ITEM) {
            itemRenderer.render(actor.itemStack, actor.position, actor.rotation, getActorBobOffset(actor, currentTick),
                actor.fade);
            return;
        }
        float alpha = MathHelper.clamp(actor.fade, 0.0F, 1.0F);
        int color = getActorBaseColor(actor);
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float bobOffset = getActorBobOffset(actor, currentTick);
        float yaw = actor.kind == PonderScene.ActorKind.CART ? actor.cartYaw : (float) actor.rotation.y;
        yaw += getActorYawOffset(actor, currentTick);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(actor.position.x, actor.position.y + bobOffset, actor.position.z);
            GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate((float) actor.rotation.x, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate((float) actor.rotation.z, 0.0F, 0.0F, 1.0F);

            if (actor.kind == PonderScene.ActorKind.BIRB) {
                drawBirbBody(actor, red, green, blue, alpha, currentTick);
                drawActorHeading(alpha);
            } else {
                drawActorPrism(red, green, blue, alpha, 0.30F, 0.16F, 0.18F);
                drawCartWheels(alpha);
                drawActorHeading(alpha);
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void drawActorHeading(float alpha) {
        int lineColor = withAlpha(0xF2F5F8, alpha * 235.0F);
        float red = ((lineColor >> 16) & 0xFF) / 255.0F;
        float green = ((lineColor >> 8) & 0xFF) / 255.0F;
        float blue = (lineColor & 0xFF) / 255.0F;
        float lineAlpha = ((lineColor >>> 24) & 0xFF) / 255.0F;

        GlStateManager.color(red, green, blue, lineAlpha);
        GL11.glLineWidth(2.0F);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3f(0.0F, 0.05F, 0.0F);
        GL11.glVertex3f(0.0F, 0.05F, -0.38F);
        GL11.glEnd();
        GL11.glLineWidth(1.0F);
    }

    private void drawBirbBody(ActorRuntimeState actor, float red, float green, float blue, float alpha,
        float currentTick) {
        if (isBirbDancePose(actor)) {
            drawActorPrism(red, green, blue, alpha, 0.16F, 0.28F, 0.16F);
            float wing = 0.04F + Math.abs(MathHelper.sin(currentTick * 0.35F)) * 0.05F;
            drawActorPrism(red * 0.92F, green * 0.92F, blue, alpha * 0.92F, wing, 0.12F, 0.04F, -0.18F, 0.08F, 0.0F);
            drawActorPrism(red * 0.92F, green * 0.92F, blue, alpha * 0.92F, wing, 0.12F, 0.04F, 0.18F, 0.08F, 0.0F);
            return;
        }
        if (isBirbCursorPose(actor)) {
            drawActorPrism(red, green, blue, alpha, 0.13F, 0.34F, 0.13F);
            drawActorPrism(red * 0.85F, green * 0.95F, blue, alpha * 0.9F, 0.04F, 0.10F, 0.10F, 0.0F, 0.26F, -0.14F);
            return;
        }
        drawActorPrism(red, green, blue, alpha, 0.14F, 0.34F, 0.14F);
    }

    private void drawCartWheels(float alpha) {
        int wheelColor = withAlpha(0x2B2B2B, alpha * 255.0F);
        float red = ((wheelColor >> 16) & 0xFF) / 255.0F;
        float green = ((wheelColor >> 8) & 0xFF) / 255.0F;
        float blue = (wheelColor & 0xFF) / 255.0F;
        float wheelAlpha = ((wheelColor >>> 24) & 0xFF) / 255.0F;
        float[][] wheels = new float[][] {
            {-0.22F, -0.12F},
            {0.22F, -0.12F},
            {-0.22F, 0.12F},
            {0.22F, 0.12F}
        };
        for (float[] wheel : wheels) {
            drawActorPrism(red, green, blue, wheelAlpha, 0.05F, 0.05F, 0.05F, wheel[0], -0.12F, wheel[1]);
        }
    }

    private void drawActorPrism(float red, float green, float blue, float alpha, float halfX, float height,
        float halfZ) {
        drawActorPrism(red, green, blue, alpha, halfX, height, halfZ, 0.0F, 0.0F, 0.0F);
    }

    private void drawActorPrism(float red, float green, float blue, float alpha, float halfX, float height,
        float halfZ, float offsetX, float offsetY, float offsetZ) {
        float x1 = offsetX - halfX;
        float x2 = offsetX + halfX;
        float y1 = offsetY;
        float y2 = offsetY + height;
        float z1 = offsetZ - halfZ;
        float z2 = offsetZ + halfZ;

        GlStateManager.color(red, green, blue, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(x1, y1, z1);
        GL11.glVertex3f(x2, y1, z1);
        GL11.glVertex3f(x2, y2, z1);
        GL11.glVertex3f(x1, y2, z1);

        GL11.glVertex3f(x1, y1, z2);
        GL11.glVertex3f(x2, y1, z2);
        GL11.glVertex3f(x2, y2, z2);
        GL11.glVertex3f(x1, y2, z2);

        GL11.glVertex3f(x1, y1, z1);
        GL11.glVertex3f(x1, y1, z2);
        GL11.glVertex3f(x1, y2, z2);
        GL11.glVertex3f(x1, y2, z1);

        GL11.glVertex3f(x2, y1, z1);
        GL11.glVertex3f(x2, y1, z2);
        GL11.glVertex3f(x2, y2, z2);
        GL11.glVertex3f(x2, y2, z1);

        GL11.glVertex3f(x1, y2, z1);
        GL11.glVertex3f(x2, y2, z1);
        GL11.glVertex3f(x2, y2, z2);
        GL11.glVertex3f(x1, y2, z2);

        GL11.glVertex3f(x1, y1, z1);
        GL11.glVertex3f(x2, y1, z1);
        GL11.glVertex3f(x2, y1, z2);
        GL11.glVertex3f(x1, y1, z2);
        GL11.glEnd();
    }

    private List<GuiOverlayPlacement> getActiveGuiOverlayPlacements(PonderScene scene, PreviewLayout layout,
        float currentTick) {
        int image = captionPaging.imageIndex();
        if (scene == null || captionBounds == null
            || image < 0 || image >= explanationImages.size()) {
            return Collections.emptyList();
        }
        PreviewDebugLayout.Rect area = new PreviewDebugLayout.Rect(captionBounds.x + 6, captionBounds.y + 6,
            Math.max(1, captionBounds.width - 12),
            Math.max(1, captionBounds.height - 12 - (captionPaging.isPaged() ? PreviewCaptionPaging.FOOTER_HEIGHT : 0)));
        List<GuiOverlayPlacement> placements = new ArrayList<GuiOverlayPlacement>();
        for (PreviewGuiPageLayout.Placement placement : PreviewGuiPageLayout.layout(explanationImages.get(image), area)) {
            placements.add(new GuiOverlayPlacement(placement.event, null, false, placement.panel.x, placement.panel.y,
                placement.panel.width, placement.panel.height, placement.draw.x, placement.draw.y,
                placement.draw.width, placement.draw.height));
        }
        return placements;
    }

    private GuiOverlayPlacement computeGuiOverlayPlacement(PonderScene scene, PreviewBounds bounds, PreviewLayout layout,
        float currentTick, PonderScene.OverlayEvent overlayEvent, Map<String, GuiOverlayPlacement> placementsById) {
        int logicalWidth = Math.max(1, overlayEvent.getRegionWidth());
        int logicalHeight = Math.max(1, overlayEvent.getRegionHeight());
        int drawWidth = Math.max(1, overlayEvent.getDisplayWidth());
        int drawHeight = Math.max(1, overlayEvent.getDisplayHeight());
        int padding = overlayEvent.isFramed() ? 4 : 0;
        int panelWidth = drawWidth + padding * 2;
        int panelHeight = drawHeight + padding * 2;
        Point targetPoint = overlayEvent.getPointAt() == null ? null
            : projectOverlayPoint(scene, bounds, layout, currentTick, overlayEvent);
        boolean aboveTarget = true;

        String parentOverlayId = overlayEvent.getParentOverlayId();
        if (parentOverlayId != null && !parentOverlayId.trim().isEmpty()) {
            GuiOverlayPlacement parentPlacement = placementsById.get(parentOverlayId);
            if (parentPlacement == null) {
                return null;
            }

            float parentScaleX =
                parentPlacement.drawWidth / (float) Math.max(1, parentPlacement.overlayEvent.getRegionWidth());
            float parentScaleY =
                parentPlacement.drawHeight / (float) Math.max(1, parentPlacement.overlayEvent.getRegionHeight());
            if (overlayEvent.isScaleToParent()) {
                drawWidth = Math.max(1, Math.round(logicalWidth * parentScaleX));
                drawHeight = Math.max(1, Math.round(logicalHeight * parentScaleY));
            }
            panelWidth = drawWidth + padding * 2;
            panelHeight = drawHeight + padding * 2;

            int drawX = parentPlacement.drawX + Math.round(overlayEvent.getGuiX() * parentScaleX)
                + overlayEvent.getOffsetX();
            int drawY = parentPlacement.drawY + Math.round(overlayEvent.getGuiY() * parentScaleY)
                + overlayEvent.getOffsetY();
            int panelX = MathHelper.clamp(drawX - padding, layout.originX + 2,
                layout.originX + layout.width - panelWidth - 2);
            int panelY = MathHelper.clamp(drawY - padding, layout.originY + 2,
                layout.originY + layout.height - panelHeight - 2);
            int finalDrawX = panelX + padding;
            int finalDrawY = panelY + padding;
            Point finalTargetPoint = new Point(finalDrawX + drawWidth / 2, finalDrawY + drawHeight / 2);
            return new GuiOverlayPlacement(overlayEvent, finalTargetPoint, false, panelX, panelY, panelWidth,
                panelHeight, finalDrawX, finalDrawY, drawWidth, drawHeight);
        }

        int panelX = layout.originX + (layout.width - panelWidth) / 2 + overlayEvent.getOffsetX();
        int panelY = overlayEvent.getIndependentY() >= 0
            ? layout.originY + 8 + overlayEvent.getIndependentY() * 2 + overlayEvent.getOffsetY()
            : layout.originY + 8 + overlayEvent.getOffsetY();

        float fitScale = Math.min(1.0F,
            Math.min((layout.width - 8.0F) / Math.max(1.0F, panelWidth),
                (layout.height - 8.0F) / Math.max(1.0F, panelHeight)));
        if (fitScale < 1.0F) {
            drawWidth = Math.max(1, Math.round(drawWidth * fitScale));
            drawHeight = Math.max(1, Math.round(drawHeight * fitScale));
            panelWidth = drawWidth + padding * 2;
            panelHeight = drawHeight + padding * 2;
        }

        if (targetPoint != null) {
            panelX = targetPoint.x - panelWidth / 2 + overlayEvent.getOffsetX();
            panelY = targetPoint.y - panelHeight - 18 + overlayEvent.getOffsetY();
            if (overlayEvent.isPlaceNearTarget() && panelY < layout.originY + 6) {
                panelY = targetPoint.y + 14 + overlayEvent.getOffsetY();
                aboveTarget = false;
            }
        }

        panelX = MathHelper.clamp(panelX, layout.originX + 4,
            layout.originX + layout.width - panelWidth - 4);
        panelY = MathHelper.clamp(panelY, layout.originY + 4,
            layout.originY + layout.height - panelHeight - 4);
        return new GuiOverlayPlacement(overlayEvent, targetPoint, aboveTarget, panelX, panelY, panelWidth, panelHeight,
            panelX + padding, panelY + padding, drawWidth, drawHeight);
    }

    private void drawStretchGuiTexture(GuiOverlayPlacement placement, PonderScene.OverlayEvent overlayEvent) {
        int logicalWidth = Math.max(1, overlayEvent.getRegionWidth());
        int logicalHeight = Math.max(1, overlayEvent.getRegionHeight());
        int textureWidth = Math.max(1, overlayEvent.getTextureWidth());
        int textureHeight = Math.max(1, overlayEvent.getTextureHeight());
        int border = Math.min(overlayEvent.getStretchBorder(), Math.min(textureWidth / 2, textureHeight / 2));
        border = Math.max(1, border);

        int drawBorderX = Math.max(1, Math.round(border * (placement.drawWidth / (float) logicalWidth)));
        int drawBorderY = Math.max(1, Math.round(border * (placement.drawHeight / (float) logicalHeight)));
        drawBorderX = Math.min(drawBorderX, Math.max(1, placement.drawWidth / 2));
        drawBorderY = Math.min(drawBorderY, Math.max(1, placement.drawHeight / 2));

        int centerSourceWidth = Math.max(1, textureWidth - border * 2);
        int centerSourceHeight = Math.max(1, textureHeight - border * 2);
        int centerDrawWidth = Math.max(0, placement.drawWidth - drawBorderX * 2);
        int centerDrawHeight = Math.max(0, placement.drawHeight - drawBorderY * 2);
        int sourceRightU = overlayEvent.getTextureU() + textureWidth - border;
        int sourceBottomV = overlayEvent.getTextureV() + textureHeight - border;

        drawScaledCustomSizeModalRect(placement.drawX, placement.drawY, overlayEvent.getTextureU(),
            overlayEvent.getTextureV(), border, border, drawBorderX, drawBorderY, textureWidth, textureHeight);
        drawScaledCustomSizeModalRect(placement.drawX + placement.drawWidth - drawBorderX, placement.drawY, sourceRightU,
            overlayEvent.getTextureV(), border, border, drawBorderX, drawBorderY, textureWidth, textureHeight);
        drawScaledCustomSizeModalRect(placement.drawX, placement.drawY + placement.drawHeight - drawBorderY,
            overlayEvent.getTextureU(), sourceBottomV, border, border, drawBorderX, drawBorderY, textureWidth,
            textureHeight);
        drawScaledCustomSizeModalRect(placement.drawX + placement.drawWidth - drawBorderX,
            placement.drawY + placement.drawHeight - drawBorderY, sourceRightU, sourceBottomV, border, border,
            drawBorderX, drawBorderY, textureWidth, textureHeight);

        if (centerDrawWidth > 0) {
            drawScaledCustomSizeModalRect(placement.drawX + drawBorderX, placement.drawY, overlayEvent.getTextureU() + border,
                overlayEvent.getTextureV(), centerSourceWidth, border, centerDrawWidth, drawBorderY, textureWidth,
                textureHeight);
            drawScaledCustomSizeModalRect(placement.drawX + drawBorderX,
                placement.drawY + placement.drawHeight - drawBorderY, overlayEvent.getTextureU() + border, sourceBottomV,
                centerSourceWidth, border, centerDrawWidth, drawBorderY, textureWidth, textureHeight);
        }
        if (centerDrawHeight > 0) {
            drawScaledCustomSizeModalRect(placement.drawX, placement.drawY + drawBorderY, overlayEvent.getTextureU(),
                overlayEvent.getTextureV() + border, border, centerSourceHeight, drawBorderX, centerDrawHeight,
                textureWidth, textureHeight);
            drawScaledCustomSizeModalRect(placement.drawX + placement.drawWidth - drawBorderX,
                placement.drawY + drawBorderY, sourceRightU, overlayEvent.getTextureV() + border, border,
                centerSourceHeight, drawBorderX, centerDrawHeight, textureWidth, textureHeight);
        }
        if (centerDrawWidth > 0 && centerDrawHeight > 0) {
            drawScaledCustomSizeModalRect(placement.drawX + drawBorderX, placement.drawY + drawBorderY,
                overlayEvent.getTextureU() + border, overlayEvent.getTextureV() + border, centerSourceWidth,
                centerSourceHeight, centerDrawWidth, centerDrawHeight, textureWidth, textureHeight);
        }
    }

    private List<GuiHighlightPlacement> getActiveGuiHighlightPlacements(PonderScene scene, PreviewLayout layout,
        float currentTick, List<GuiOverlayPlacement> guiPlacements) {
        if (scene == null || guiPlacements == null || guiPlacements.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, GuiOverlayPlacement> guiPlacementById = new LinkedHashMap<String, GuiOverlayPlacement>();
        for (GuiOverlayPlacement placement : guiPlacements) {
            String overlayId = placement.overlayEvent.getOverlayId();
            if (overlayId != null && !overlayId.trim().isEmpty()) {
                guiPlacementById.put(overlayId, placement);
            }
        }
        if (guiPlacementById.isEmpty()) {
            return Collections.emptyList();
        }

        List<GuiHighlightPlacement> placements = new ArrayList<GuiHighlightPlacement>();
        for (PonderScene.OverlayEvent overlayEvent : scene.getOverlayEvents()) {
            if (overlayEvent.getType() != PonderScene.OverlayEventType.GUI_HIGHLIGHT) {
                continue;
            }
            if (overlayEvent.getTick() > currentTick
                || currentTick > overlayEvent.getTick() + overlayEvent.getDuration()) {
                continue;
            }

            GuiOverlayPlacement parentPlacement = guiPlacementById.get(overlayEvent.getParentOverlayId());
            if (parentPlacement == null) {
                continue;
            }

            GuiHighlightPlacement placement = computeGuiHighlightPlacement(parentPlacement, overlayEvent);
            if (placement != null) {
                placements.add(placement);
            }
        }
        return placements;
    }

    private GuiHighlightPlacement computeGuiHighlightPlacement(GuiOverlayPlacement parentPlacement,
        PonderScene.OverlayEvent overlayEvent) {
        float scaleX = parentPlacement.drawWidth / (float) Math.max(1, parentPlacement.overlayEvent.getRegionWidth());
        float scaleY = parentPlacement.drawHeight / (float) Math.max(1, parentPlacement.overlayEvent.getRegionHeight());
        int rectX = parentPlacement.drawX + Math.round(overlayEvent.getGuiX() * scaleX);
        int rectY = parentPlacement.drawY + Math.round(overlayEvent.getGuiY() * scaleY);
        int rectWidth = Math.max(2, Math.round(overlayEvent.getGuiWidth() * scaleX));
        int rectHeight = Math.max(2, Math.round(overlayEvent.getGuiHeight() * scaleY));
        int maxX = parentPlacement.drawX + parentPlacement.drawWidth;
        int maxY = parentPlacement.drawY + parentPlacement.drawHeight;

        if (rectX >= maxX || rectY >= maxY) {
            return null;
        }

        int right = Math.min(maxX, rectX + rectWidth);
        int bottom = Math.min(maxY, rectY + rectHeight);
        rectX = Math.max(parentPlacement.drawX, rectX);
        rectY = Math.max(parentPlacement.drawY, rectY);
        rectWidth = right - rectX;
        rectHeight = bottom - rectY;
        if (rectWidth <= 0 || rectHeight <= 0) {
            return null;
        }

        Point targetPoint = new Point(rectX + rectWidth / 2, rectY + rectHeight / 2);
        return new GuiHighlightPlacement(overlayEvent, rectX, rectY, rectWidth, rectHeight, targetPoint);
    }

    private CaptionPlacement avoidGuiOverlayOverlap(int boxX, int boxY, int boxWidth, int boxHeight,
        SpeechPointing pointing, int minBoxY, int maxBoxY, List<GuiOverlayPlacement> placements) {
        if (placements == null || placements.isEmpty()) {
            return new CaptionPlacement(boxX, boxY, pointing);
        }

        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        boolean intersects = false;
        for (GuiOverlayPlacement placement : placements) {
            left = Math.min(left, placement.panelX);
            top = Math.min(top, placement.panelY);
            right = Math.max(right, placement.panelX + placement.panelWidth);
            bottom = Math.max(bottom, placement.panelY + placement.panelHeight);
            if (rectsIntersect(boxX, boxY, boxWidth, boxHeight, placement.panelX, placement.panelY, placement.panelWidth,
                placement.panelHeight)) {
                intersects = true;
            }
        }

        if (!intersects) {
            return new CaptionPlacement(boxX, boxY, pointing);
        }

        int aboveY = top - boxHeight - 12;
        int belowY = bottom + 12;
        boolean canAbove = aboveY >= minBoxY;
        boolean canBelow = belowY <= maxBoxY;
        if (canAbove && (!canBelow || Math.abs(boxY - aboveY) <= Math.abs(boxY - belowY))) {
            return new CaptionPlacement(boxX, aboveY, SpeechPointing.DOWN);
        }
        if (canBelow) {
            return new CaptionPlacement(boxX, belowY, SpeechPointing.UP);
        }

        int clampedY = MathHelper.clamp(boxY, minBoxY, maxBoxY);
        return new CaptionPlacement(boxX, clampedY, pointing);
    }

    private Point resolveCaptionTargetPoint(PonderScene scene, float currentTick, PonderScene.OverlayEvent overlayEvent,
        List<GuiHighlightPlacement> guiHighlightPlacements) {
        if (scene == null || overlayEvent == null || lastPreviewLayout == null) {
            return null;
        }

        if (overlayEvent.getType() == PonderScene.OverlayEventType.GUI_HIGHLIGHT) {
            for (GuiHighlightPlacement placement : guiHighlightPlacements) {
                if (placement.overlayEvent == overlayEvent) {
                    return placement.targetPoint;
                }
            }
        }

        return overlayEvent.getPointAt() == null ? null
            : projectOverlayPoint(scene, PonderScenePreview.computeBounds(scene), lastPreviewLayout, currentTick, overlayEvent);
    }

    private int resolveManualCaptionCoordinate(int coordinate, int origin, int size, int boxSize) {
        return coordinate >= 0 ? origin + coordinate : origin + size - boxSize + coordinate;
    }

    private SpeechPointing choosePointing(int boxX, int boxY, int boxWidth, int boxHeight, Point targetPoint) {
        if (targetPoint == null) {
            return SpeechPointing.NONE;
        }

        int centerX = boxX + boxWidth / 2;
        int centerY = boxY + boxHeight / 2;
        int deltaX = targetPoint.x - centerX;
        int deltaY = targetPoint.y - centerY;
        if (Math.abs(deltaX) > Math.abs(deltaY)) {
            return deltaX > 0 ? SpeechPointing.RIGHT : SpeechPointing.LEFT;
        }
        return deltaY > 0 ? SpeechPointing.DOWN : SpeechPointing.UP;
    }

    private boolean rectsIntersect(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    protected void drawNextUpCard(PonderScene scene, float fade) {
        lastNextUpCardWidth = 0;
        lastNextUpCardHeight = 0;
        if (scene == null || selectedSceneIndex + 1 >= compiledScenes.size()) {
            return;
        }

        int maxTick = Math.max(1, getSceneEndTick(scene));
        if (playbackTick < (int) (maxTick * 0.7F)) {
            return;
        }

        PonderScene nextScene = compiledScenes.get(selectedSceneIndex + 1);
        if (nextSceneButton == null) {
            return;
        }

        String nextUpLabel = "\u63A5\u4E0B\u6765";
        String nextTitle = fontRenderer.trimStringToWidth(nextScene.getTitle(), 150);
        int boxWidth = Math.max(96, Math.max(fontRenderer.getStringWidth(nextUpLabel), fontRenderer.getStringWidth(nextTitle)) + 22);
        int anchorX = nextSceneButton.x + nextSceneButton.width / 2;
        int anchorY = nextSceneButton.y - 8;
        int boxX = anchorX - boxWidth / 2;
        int boxY = anchorY - 34;
        lastNextUpCardX = boxX;
        lastNextUpCardY = boxY;
        lastNextUpCardWidth = boxWidth;
        lastNextUpCardHeight = 26;
        drawSpeechBox(boxX, boxY, boxWidth, 26, SpeechPointing.DOWN, 0xD5CCB8, fade);
        drawCenteredStringNoShadow(nextUpLabel, boxX + boxWidth / 2, boxY + 6, 0xAEB8C1);
        drawCenteredStringNoShadow(nextTitle, boxX + boxWidth / 2, boxY + 16, 0xF3EFE7);
    }

    protected void drawShowcaseBackdrop(int previewX, int previewY, int previewWidth, int previewHeight, float fade) {
        int glowAlpha = (int) (fade * 52.0F) << 24;
        int borderAlpha = (int) (fade * 44.0F) << 24;
        int vignetteAlpha = (int) (fade * 92.0F) << 24;
        drawGradientRect(previewX - 20, previewY - 20, previewX + previewWidth + 20, previewY + previewHeight + 26,
            glowAlpha | 0x17202A, 0x00000000);
        drawRect(previewX, previewY, previewX + previewWidth, previewY + 1, borderAlpha | 0xF1E8D4);
        drawRect(previewX, previewY + previewHeight - 1, previewX + previewWidth, previewY + previewHeight,
            borderAlpha | 0x2C3138);
        drawRect(previewX, previewY, previewX + 1, previewY + previewHeight, borderAlpha | 0x4D545E);
        drawRect(previewX + previewWidth - 1, previewY, previewX + previewWidth, previewY + previewHeight,
            borderAlpha | 0x2C3138);
        drawGradientRect(previewX, previewY, previewX + previewWidth, previewY + 52, vignetteAlpha | 0x030508,
            0x00000000);
        drawGradientRect(previewX, previewY + previewHeight - 60, previewX + previewWidth, previewY + previewHeight,
            0x00000000, vignetteAlpha | 0x030508);
    }

    protected void drawShowcaseLogo(int x, int y, float fade) {
        if (fade > 0) renderOverlayItem(new ItemStack(net.minecraft.init.Items.BOOK), x + 8, y + 8);
    }

    private void drawGuiTexturePanel(int x, int y, int width, int height, int accentColor, float fade) {
        int fillTop = withAlpha(0x121920, fade * 205.0F);
        int fillBottom = withAlpha(0x090D12, fade * 205.0F);
        int neutralBorder = withAlpha(0xE7E0D0, fade * 165.0F);
        int shadowBorder = withAlpha(0x2F343B, fade * 96.0F);
        int accentBorder = withAlpha(blendColors(0xD5CCB8, accentColor, 0.72F), fade * 164.0F);
        int innerAccent = withAlpha(blendColors(0xEEE6D4, accentColor, 0.34F), fade * 124.0F);

        drawGradientRect(x, y, x + width, y + height, fillTop, fillBottom);
        drawHorizontalGradientRect(x, y, x + width, y + 1, neutralBorder, accentBorder);
        drawHorizontalGradientRect(x, y + height - 1, x + width, y + height, shadowBorder, accentBorder);
        drawGradientRect(x, y, x + 1, y + height, neutralBorder, shadowBorder);
        drawGradientRect(x + width - 1, y, x + width, y + height, accentBorder, shadowBorder);
        drawHorizontalGradientRect(x + 2, y + 2, x + width - 2, y + 4, neutralBorder, innerAccent);
    }

    private void drawGuiHighlight(int x, int y, int width, int height, int accentColor, float fade) {
        int glowColor = withAlpha(blendColors(0x2A3643, accentColor, 0.72F), fade * 52.0F);
        int fillColor = withAlpha(blendColors(0x18212B, accentColor, 0.66F), fade * 86.0F);
        int edgeColor = withAlpha(blendColors(0xEDE4D4, accentColor, 0.80F), fade * 228.0F);
        int accentEdge = withAlpha(blendColors(0xC2B18F, accentColor, 0.90F), fade * 180.0F);

        drawRect(x - 2, y - 2, x + width + 2, y + height + 2, glowColor);
        drawRect(x, y, x + width, y + height, fillColor);
        drawHorizontalGradientRect(x, y, x + width, y + 1, edgeColor, accentEdge);
        drawHorizontalGradientRect(x, y + height - 1, x + width, y + height, accentEdge, edgeColor);
        drawGradientRect(x, y, x + 1, y + height, edgeColor, accentEdge);
        drawGradientRect(x + width - 1, y, x + width, y + height, accentEdge, edgeColor);
    }

    protected void drawShowcaseHoverHints(int mouseX, int mouseY) {
        if (drawShowcaseGroupHoverHint(mouseX, mouseY)) {
            return;
        }
        if (isMouseOverPlaybackBar(mouseX, mouseY)) {
            PonderScene scene = getSelectedScene();
            String label = scene == null ? "拖动时间轴" : "拖动时间轴  |  T+" + estimatePlaybackTickForMouse(mouseX) + " / " + getSceneEndTick(scene);
            drawShowcaseCenteredHint(label, 0xB8C3CC);
            return;
        }
        if (isMouseOverShowcaseHeaderIcon(mouseX, mouseY) && hasShowcaseGroupChoices()) {
            drawShowcaseCenteredHint("\u70B9\u51FB\u56FE\u6807\u9009\u62E9\u540C\u7EC4\u673A\u5668", 0xB8C3CC);
            return;
        }
        if (isMouseOverNextUpCard(mouseX, mouseY)) {
            drawShowcaseCenteredHint("点击切换到下一幕", 0xB8C3CC);
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

        drawShowcaseCenteredHint(hoveredButton.displayString, 0xB8C3CC);
    }

    protected PreviewShowcaseChromeLayout getShowcaseChromeLayout() {
        return PreviewShowcaseChromeLayout.layout(width, height, fontRenderer == null ? 9 : fontRenderer.FONT_HEIGHT);
    }

    protected void drawShowcaseCenteredHint(String label, int color) {
        PreviewTextLayout text = PreviewTextLayout.centered(fontRenderer, label, 18, width - 18);
        drawShowcaseHintText(text.text, text.x, color);
    }

    protected void drawShowcaseHintText(String text, int x, int color) {
        drawString(fontRenderer, text, x, getShowcaseChromeLayout().hoverTextY, color);
    }

    protected boolean drawShowcaseGroupHoverHint(int mouseX, int mouseY) {
        if (!showcaseGroupSelectorOpen) {
            return false;
        }
        ShowcaseGroupIcon icon = getShowcaseGroupIconAt(mouseX, mouseY);
        if (icon != null) {
            ItemStack stack = createComponentStack(icon.componentId);
            String label = stack.isEmpty() ? icon.componentId.toString() : stack.getDisplayName();
            String groupLabel = icon.tag == null ? label : label + "\u00a7r  |  " + icon.tag.getTitle();
            PreviewTextLayout text = PreviewTextLayout.centered(fontRenderer, groupLabel, 18, width - 18);
            drawShowcaseHintText(text.text, text.x, 0xB8C3CC);
        }
        return true;
    }

    private void drawSpeechBox(int boxX, int boxY, int boxWidth, int boxHeight, SpeechPointing pointing, int accentColor,
        float fade) {
        if (fade > 0.0F) {
            int left = pointing == SpeechPointing.LEFT ? 8 : 0;
            int top = pointing == SpeechPointing.UP ? 8 : 0;
            int right = pointing == SpeechPointing.RIGHT ? 8 : 0;
            int bottom = pointing == SpeechPointing.DOWN ? 8 : 0;
            overlayHitAreas.add(boxX - left, boxY - top, boxWidth + left + right, boxHeight + top + bottom);
        }
        int fillTop = withAlpha(0x151A20, fade * 205.0F);
        int fillBottom = withAlpha(0x090C10, fade * 205.0F);
        int neutralBorder = withAlpha(0xE7E0D0, fade * 170.0F);
        int shadowBorder = withAlpha(0x2F343B, fade * 96.0F);
        int accentBorder = withAlpha(blendColors(0xD5CCB8, accentColor, 0.72F), fade * 168.0F);
        int accentEdge = withAlpha(blendColors(0xBDAE93, accentColor, 0.85F), fade * 148.0F);
        int innerAccent = withAlpha(blendColors(0xE1D7C4, accentColor, 0.38F), fade * 132.0F);

        drawGradientRect(boxX, boxY, boxX + boxWidth, boxY + boxHeight, fillTop, fillBottom);
        drawHorizontalGradientRect(boxX, boxY, boxX + boxWidth, boxY + 1, neutralBorder, accentBorder);
        drawHorizontalGradientRect(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, shadowBorder,
            accentEdge);
        drawGradientRect(boxX, boxY, boxX + 1, boxY + boxHeight, neutralBorder, shadowBorder);
        drawGradientRect(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, accentBorder, accentEdge);
        drawHorizontalGradientRect(boxX + 2, boxY + 2, boxX + boxWidth - 2, boxY + 4, neutralBorder, innerAccent);

        if (pointing == SpeechPointing.NONE) {
            return;
        }

        int divotX = boxX + boxWidth / 2 - 4;
        int divotY = boxY + boxHeight - 1;
        int rotation = 0;
        if (pointing == SpeechPointing.UP) {
            divotY = boxY - 7;
            rotation = 180;
        } else if (pointing == SpeechPointing.LEFT) {
            divotX = boxX - 7;
            divotY = boxY + boxHeight / 2 - 4;
            rotation = 90;
        } else if (pointing == SpeechPointing.RIGHT) {
            divotX = boxX + boxWidth - 1;
            divotY = boxY + boxHeight / 2 - 4;
            rotation = 270;
        }

        drawSpeechDivot(divotX, divotY, rotation, accentColor, fade);
    }

    private void drawCenteredStringNoShadow(String text, int centerX, int y, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        fontRenderer.drawString(text, centerX - fontRenderer.getStringWidth(text) / 2.0F, y, color, false);
    }

    private void drawSpeechDivot(int x, int y, int rotation, int accentColor, float fade) {
        int fillColor = ((int) (fade * 205.0F) << 24) | 0x10161D;
        int edgeColor = ((int) (fade * 160.0F) << 24) | 0xE7E0D0;
        int accent = withAlpha(blendColors(0xD5CCB8, accentColor, 0.75F), fade * 148.0F);
        if (rotation == 180) {
            drawSpeechTriangleDown(x + 4, y + 8, fillColor, edgeColor, accent);
        } else if (rotation == 90) {
            drawSpeechTriangleLeft(x, y + 4, fillColor, edgeColor, accent);
        } else if (rotation == 270) {
            drawSpeechTriangleRight(x + 8, y + 4, fillColor, edgeColor, accent);
        } else {
            drawSpeechTriangleUp(x + 4, y, fillColor, edgeColor, accent);
        }
    }

    private void drawSpeechTriangleDown(int tipX, int tipY, int fillColor, int edgeColor, int accentColor) {
        drawFilledTriangle(tipX - 7, tipY - 7, tipX + 7, tipY - 7, tipX, tipY, fillColor);
        drawLineSegment(tipX - 7, tipY - 7, tipX, tipY, edgeColor, 1.5F);
        drawLineSegment(tipX + 7, tipY - 7, tipX, tipY, accentColor, 1.5F);
    }

    private void drawSpeechTriangleUp(int tipX, int tipY, int fillColor, int edgeColor, int accentColor) {
        drawFilledTriangle(tipX - 7, tipY + 7, tipX + 7, tipY + 7, tipX, tipY, fillColor);
        drawLineSegment(tipX - 7, tipY + 7, tipX, tipY, edgeColor, 1.5F);
        drawLineSegment(tipX + 7, tipY + 7, tipX, tipY, accentColor, 1.5F);
    }

    private void drawSpeechTriangleLeft(int tipX, int tipY, int fillColor, int edgeColor, int accentColor) {
        drawFilledTriangle(tipX + 7, tipY - 7, tipX + 7, tipY + 7, tipX, tipY, fillColor);
        drawLineSegment(tipX + 7, tipY - 7, tipX, tipY, edgeColor, 1.5F);
        drawLineSegment(tipX + 7, tipY + 7, tipX, tipY, accentColor, 1.5F);
    }

    private void drawSpeechTriangleRight(int tipX, int tipY, int fillColor, int edgeColor, int accentColor) {
        drawFilledTriangle(tipX - 7, tipY - 7, tipX - 7, tipY + 7, tipX, tipY, fillColor);
        drawLineSegment(tipX - 7, tipY - 7, tipX, tipY, edgeColor, 1.5F);
        drawLineSegment(tipX - 7, tipY + 7, tipX, tipY, accentColor, 1.5F);
    }

    private void drawFilledTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int color) {
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        float red = ((color >>> 16) & 0xFF) / 255.0F;
        float green = ((color >>> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.color(red, green, blue, alpha);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(x1 + 0.5F, y1 + 0.5F);
        GL11.glVertex2f(x2 + 0.5F, y2 + 0.5F);
        GL11.glVertex2f(x3 + 0.5F, y3 + 0.5F);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawHorizontalGradientRect(int left, int top, int right, int bottom, int leftColor, int rightColor) {
        float leftAlpha = ((leftColor >>> 24) & 0xFF) / 255.0F;
        float leftRed = ((leftColor >>> 16) & 0xFF) / 255.0F;
        float leftGreen = ((leftColor >>> 8) & 0xFF) / 255.0F;
        float leftBlue = (leftColor & 0xFF) / 255.0F;
        float rightAlpha = ((rightColor >>> 24) & 0xFF) / 255.0F;
        float rightRed = ((rightColor >>> 16) & 0xFF) / 255.0F;
        float rightGreen = ((rightColor >>> 8) & 0xFF) / 255.0F;
        float rightBlue = (rightColor & 0xFF) / 255.0F;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(right, top, 0.0D).color(rightRed, rightGreen, rightBlue, rightAlpha).endVertex();
        buffer.pos(left, top, 0.0D).color(leftRed, leftGreen, leftBlue, leftAlpha).endVertex();
        buffer.pos(left, bottom, 0.0D).color(leftRed, leftGreen, leftBlue, leftAlpha).endVertex();
        buffer.pos(right, bottom, 0.0D).color(rightRed, rightGreen, rightBlue, rightAlpha).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private int blendColors(int baseColor, int accentColor, float accentWeight) {
        float clampedWeight = MathHelper.clamp(accentWeight, 0.0F, 1.0F);
        float baseWeight = 1.0F - clampedWeight;
        int red = Math.round(((baseColor >> 16) & 0xFF) * baseWeight + ((accentColor >> 16) & 0xFF) * clampedWeight);
        int green = Math.round(((baseColor >> 8) & 0xFF) * baseWeight + ((accentColor >> 8) & 0xFF) * clampedWeight);
        int blue = Math.round((baseColor & 0xFF) * baseWeight + (accentColor & 0xFF) * clampedWeight);
        return red << 16 | green << 8 | blue;
    }

    private int withAlpha(int color, float alpha) {
        int alphaChannel = MathHelper.clamp((int) alpha, 0, 255);
        return alphaChannel << 24 | (color & 0x00FFFFFF);
    }

    private Point getSpeechPointerTip(int boxX, int boxY, int boxWidth, int boxHeight, SpeechPointing pointing) {
        if (pointing == SpeechPointing.NONE) {
            return null;
        }
        if (pointing == SpeechPointing.UP) {
            return new Point(boxX + boxWidth / 2, boxY - 7);
        }
        if (pointing == SpeechPointing.LEFT) {
            return new Point(boxX - 7, boxY + boxHeight / 2);
        }
        if (pointing == SpeechPointing.RIGHT) {
            return new Point(boxX + boxWidth + 7, boxY + boxHeight / 2);
        }
        return new Point(boxX + boxWidth / 2, boxY + boxHeight + 7);
    }

    private void drawCaptionConnector(int startX, int startY, int endX, int endY, int color, float fade) {
        int alphaColor = ((int) (fade * 255.0F) << 24) | (color & 0x00FFFFFF);
        drawLineSegment(startX, startY, endX, endY, alphaColor, 2.0F);
        drawRect(startX - 1, startY - 1, startX + 2, startY + 2, alphaColor);
    }

    private void drawLineSegment(int startX, int startY, int endX, int endY, int color, float width) {
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        float red = ((color >>> 16) & 0xFF) / 255.0F;
        float green = ((color >>> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.color(red, green, blue, alpha);
        GL11.glLineWidth(width);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(startX + 0.5F, startY + 0.5F);
        GL11.glVertex2f(endX + 0.5F, endY + 0.5F);
        GL11.glEnd();
        GL11.glLineWidth(1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private Point projectOverlayPoint(PonderScene scene, PreviewBounds bounds, PreviewLayout layout, float renderTick,
        PonderScene.OverlayEvent event) {
        Vec3d point = PonderSceneRuntime.overlayPointAtTick(scene,event,renderTick);
        if (point != null && worldProjection != null) {
            java.awt.Point projected = worldProjection.project(point);
            return projected == null ? null : new Point(projected.x,projected.y);
        }
        return point == null ? null : projectTransformedPoint(scene,bounds,layout,renderTick,point);
    }

    private Point projectScenePoint(PonderScene scene, PreviewBounds bounds, PreviewLayout layout, float renderTick,
        Vec3d point) {
        if (point == null) {
            return null;
        }

        Vec3d transformedPoint = PonderSceneRuntime.transformPointAtTick(scene, point, renderTick);
        return projectTransformedPoint(scene, bounds, layout, renderTick, transformedPoint);
    }

    private Point projectTransformedPoint(PonderScene scene, PreviewBounds bounds, PreviewLayout layout, float renderTick,
        Vec3d transformedPoint) {
        float scale = computePreviewScale(scene, bounds, layout, renderTick);
        Vec3d center = getPreviewView(scene, bounds, renderTick).center;
        float yaw = getPreviewYaw(scene, renderTick);
        float pitch = getPreviewPitch(scene, renderTick);

        Vec3d relative = transformedPoint.subtract(center);
        Vec3d rotatedYaw = rotateY(relative, yaw);
        Vec3d rotated = rotateX(rotatedYaw, pitch);

        float screenX = layout.originX + layout.width / 2.0F + (float) (rotated.x * scale);
        float screenY = getPreviewAnchorY(layout) - (float) (rotated.y * scale);
        return new Point(Math.round(screenX), Math.round(screenY));
    }

    private ProjectedBounds projectSceneBounds(PonderScene scene, PreviewBounds bounds, PreviewLayout layout,
        float renderTick, AxisAlignedBB sceneBounds) {
        if (sceneBounds == null) {
            return null;
        }

        Vec3d[] corners = new Vec3d[] {
            new Vec3d(sceneBounds.minX, sceneBounds.minY, sceneBounds.minZ),
            new Vec3d(sceneBounds.minX, sceneBounds.minY, sceneBounds.maxZ),
            new Vec3d(sceneBounds.minX, sceneBounds.maxY, sceneBounds.minZ),
            new Vec3d(sceneBounds.minX, sceneBounds.maxY, sceneBounds.maxZ),
            new Vec3d(sceneBounds.maxX, sceneBounds.minY, sceneBounds.minZ),
            new Vec3d(sceneBounds.maxX, sceneBounds.minY, sceneBounds.maxZ),
            new Vec3d(sceneBounds.maxX, sceneBounds.maxY, sceneBounds.minZ),
            new Vec3d(sceneBounds.maxX, sceneBounds.maxY, sceneBounds.maxZ)
        };

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Vec3d corner : corners) {
            Point projected = projectScenePoint(scene, bounds, layout, renderTick, corner);
            if (projected == null) {
                continue;
            }
            minX = Math.min(minX, projected.x);
            minY = Math.min(minY, projected.y);
            maxX = Math.max(maxX, projected.x);
            maxY = Math.max(maxY, projected.y);
        }

        if (minX == Integer.MAX_VALUE) {
            return null;
        }

        minX = MathHelper.clamp(minX - 1, layout.originX, layout.originX + layout.width - 1);
        minY = MathHelper.clamp(minY - 1, layout.originY, layout.originY + layout.height - 1);
        maxX = MathHelper.clamp(maxX + 1, minX + 2, layout.originX + layout.width);
        maxY = MathHelper.clamp(maxY + 1, minY + 2, layout.originY + layout.height);
        return new ProjectedBounds(minX, minY, maxX, maxY);
    }

    private PonderScene.PoiEvent getActivePoiEvent(PonderScene scene, float currentTick) {
        if (scene == null) {
            return null;
        }
        PonderScene.PoiEvent active = null;
        for (PonderScene.PoiEvent event : scene.getPointOfInterestEvents()) {
            if (event.getTick() <= currentTick) {
                active = event;
            } else {
                break;
            }
        }
        return active;
    }

    private int getParticleColor(String particleName) {
        return PreviewParticleTimeline.color(particleName);
    }

    private int getActorBaseColor(ActorRuntimeState actor) {
        if (actor.kind == PonderScene.ActorKind.CART) {
            return 0xD9C27A;
        }
        if (actor.kind == PonderScene.ActorKind.ITEM) {
            return 0xE0D6A8;
        }
        if (isBirbDancePose(actor)) {
            return 0xE58ACF;
        }
        if (isBirbCursorPose(actor)) {
            return 0x7FCDE0;
        }
        if (isBirbPoiPose(actor)) {
            return 0x82D173;
        }
        return 0xA8D89A;
    }

    private String getActorLabel(ActorRuntimeState actor) {
        if (actor.kind == PonderScene.ActorKind.CART) {
            return "CART";
        }
        if (actor.kind == PonderScene.ActorKind.ITEM) {
            return actor.displayName == null || actor.displayName.isEmpty() ? "ITEM" : actor.displayName;
        }
        if (isBirbDancePose(actor)) {
            return "BIRB Dance";
        }
        if (isBirbCursorPose(actor)) {
            return "BIRB Cursor";
        }
        if (isBirbPoiPose(actor)) {
            return "BIRB POI";
        }
        return actor.poseName == null || actor.poseName.isEmpty() ? "BIRB" : "BIRB " + actor.poseName;
    }

    private float getActorBobOffset(ActorRuntimeState actor, float currentTick) {
        if (actor.kind == PonderScene.ActorKind.CART) {
            return MathHelper.sin(currentTick * 0.15F + actor.actorId) * 0.01F;
        }
        if (actor.kind == PonderScene.ActorKind.ITEM) {
            return Math.abs(MathHelper.sin(currentTick * 0.22F + actor.actorId * 0.4F)) * 0.05F;
        }
        if (isBirbDancePose(actor)) {
            return Math.abs(MathHelper.sin(currentTick * 0.35F + actor.actorId * 0.5F)) * 0.10F;
        }
        return Math.abs(MathHelper.sin(currentTick * 0.18F + actor.actorId * 0.35F)) * 0.04F;
    }

    private float getActorYawOffset(ActorRuntimeState actor, float currentTick) {
        if (actor.kind != PonderScene.ActorKind.BIRB) {
            return 0.0F;
        }
        if (isBirbCursorPose(actor)) {
            return MathHelper.sin(currentTick * 0.30F + actor.actorId) * 18.0F;
        }
        if (isBirbDancePose(actor)) {
            return MathHelper.sin(currentTick * 0.45F + actor.actorId) * 10.0F;
        }
        return 0.0F;
    }

    private boolean isBirbDancePose(ActorRuntimeState actor) {
        return actor.poseName != null && actor.poseName.contains("DancePose");
    }

    private boolean isBirbCursorPose(ActorRuntimeState actor) {
        return actor.poseName != null && actor.poseName.contains("FaceCursorPose");
    }

    private boolean isBirbPoiPose(ActorRuntimeState actor) {
        return actor.poseName != null && actor.poseName.contains("FacePointOfInterestPose");
    }

    private List<ActorRuntimeState> getActorRuntimeStates(PonderScene scene, float currentTick) {
        if (scene == null) {
            return Collections.emptyList();
        }

        List<ActorRuntimeState> actors = new ArrayList<ActorRuntimeState>();
        for (PreviewActorTimeline.State state : PreviewActorTimeline.evaluate(scene.getActorEvents(), currentTick)) {
            if (state.kind == PonderScene.ActorKind.PLAYER) continue;
            ActorRuntimeState actor = new ActorRuntimeState(state.actorId, state.kind, state.position);
            actor.rotation = state.rotation; actor.cartYaw = state.yaw; actor.poseName = state.poseName;
            actor.displayName = state.displayName; actor.itemStack = state.getItemStack();
            actor.fade = state.fade; actor.visible = state.visible; actors.add(actor);
        }
        return actors;
    }

    private float actorProgress(PonderScene.ActorEvent event, float currentTick) {
        int duration = Math.max(1, event.getDuration());
        return MathHelper.clamp((currentTick - event.getTick() + 1.0F) / duration, 0.0F, 1.0F);
    }

    private Vec3d rotateY(Vec3d vec, float degrees) {
        double radians = Math.toRadians(degrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double x = vec.x * cosine + vec.z * sine;
        double z = vec.z * cosine - vec.x * sine;
        return new Vec3d(x, vec.y, z);
    }

    private Vec3d rotateX(Vec3d vec, float degrees) {
        double radians = Math.toRadians(degrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double y = vec.y * cosine - vec.z * sine;
        double z = vec.y * sine + vec.z * cosine;
        return new Vec3d(vec.x, y, z);
    }

    private ShowcaseCaption getShowcaseCaption(PonderScene scene, float currentTick) {
        if (scene == null) {
            return null;
        }
        ShowcaseCaption activeCaption = null;
        for (PonderScene.OverlayEvent overlayEvent : scene.getOverlayEvents()) {
            if ((overlayEvent.getType() == PonderScene.OverlayEventType.TEXT
                || overlayEvent.getType() == PonderScene.OverlayEventType.GUI_HIGHLIGHT)
                && overlayEvent.getTick() <= currentTick
                && currentTick <= overlayEvent.getTick() + overlayEvent.getDuration()
                && !scene.isOverlayCleared(overlayEvent, currentTick)
                && overlayEvent.getText() != null
                && !overlayEvent.getText().isEmpty()) {
                activeCaption = new ShowcaseCaption(overlayEvent);
            }
        }
        return activeCaption;
    }

    private float computeCaptionFade(ShowcaseCaption caption, float currentTick) {
        return caption == null || caption.overlayEvent == null ? 0.0F
            : computeOverlayFade(caption.overlayEvent.getTick(), caption.overlayEvent.getDuration(), currentTick);
    }

    private float computeOverlayFade(int startTick, int duration, float currentTick) {
        float fadeIn = MathHelper.clamp((currentTick - startTick + 1.0F) / 6.0F, 0.0F, 1.0F);
        float fadeOut = MathHelper.clamp((startTick + duration - currentTick) / 6.0F, 0.0F, 1.0F);
        return Math.min(fadeIn, fadeOut);
    }

    protected ShowcaseGroupState getShowcaseGroupState() {
        ResourceLocation componentId = getSelectedComponentId();
        if (componentId == null) {
            return null;
        }

        PonderTag bestTag = null;
        List<ResourceLocation> bestComponents = Collections.emptyList();
        for (PonderTag tag : PonderIndex.getTagAccess().getTags(componentId)) {
            if (!isShowcaseGroupCandidate(tag)) {
                continue;
            }

            Set<ResourceLocation> groupedComponents = PonderIndex.getTagAccess().getItems(tag);
            if (groupedComponents.size() <= 1) {
                continue;
            }

            List<ResourceLocation> sortedComponents = new ArrayList<ResourceLocation>(groupedComponents);
            Collections.sort(sortedComponents, new Comparator<ResourceLocation>() {
                @Override
                public int compare(ResourceLocation left, ResourceLocation right) {
                    boolean leftSelected = left.equals(getSelectedComponentId());
                    boolean rightSelected = right.equals(getSelectedComponentId());
                    if (leftSelected != rightSelected) {
                        return leftSelected ? -1 : 1;
                    }
                    return left.toString().compareTo(right.toString());
                }
            });

            if (bestTag == null || sortedComponents.size() > bestComponents.size()) {
                bestTag = tag;
                bestComponents = sortedComponents;
            }
        }

        return bestTag == null ? null : new ShowcaseGroupState(bestTag, bestComponents);
    }

    protected boolean isShowcaseGroupCandidate(PonderTag tag) {
        if (tag == null || tag.getId() == null) {
            return false;
        }
        ResourceLocation id = tag.getId();
        return !PonderTag.Highlight.ALL.equals(id)
            && !(Ponder.MOD_ID.equals(id.getNamespace()) && "not_registered".equals(id.getPath()));
    }

    protected ItemStack createComponentStack(ResourceLocation componentId) {
        return componentId == null ? ItemStack.EMPTY : PonderIndex.getSceneAccess().getDisplayStack(componentId);
    }

    private void drawOperationList(int mouseX, int mouseY, PreviewDebugLayout.Rect rows, boolean title) {
        List<RecordedOperation> operations = getSelectedRecordedOperations();
        if (title) {
            drawString(fontRenderer, "Timeline", rows.x, rows.y - 14, 0xFFEEDD);
        }

        if (operations.isEmpty()) {
            drawString(fontRenderer, fontRenderer.trimStringToWidth("No recorded operations.", rows.width),
                rows.x, rows.y, 0xA0A0A0);
            return;
        }

        int visibleLines = rows.lines();
        int maxIndex = Math.min(operations.size(), operationScroll + visibleLines);
        int activeIndex = getActiveOperationIndex(operations, playbackTick);

        for (int index = operationScroll; index < maxIndex; index++) {
            RecordedOperation operation = operations.get(index);
            int y = rows.y + (index - operationScroll) * LINE_HEIGHT;
            boolean hovered = rows.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + LINE_HEIGHT;
            boolean active = index == activeIndex;
            boolean reached = operation.getTick() <= playbackTick;
            int background = active ? 0xCC6F5A1E : reached ? 0x663A4A28 : hovered ? 0x55303030 : 0x33202020;
            drawRect(rows.x, y, rows.right(), y + LINE_HEIGHT, background);

            String label = "T+" + operation.getTick() + " " + operation.getDescription();
            drawString(fontRenderer, fontRenderer.trimStringToWidth(label, rows.width - 8), rows.x + 4, y + 2,
                active ? 0xFFFFFF : reached ? 0xE5F0D0 : 0xD0D0D0);
        }
    }

    private String getDebugHoverHint(int mouseX, int mouseY) {
        int componentIndex = getComponentIndexAt(mouseX, mouseY);
        if (componentIndex >= 0) {
            return componentIds.get(componentIndex).toString();
        }

        int operationIndex = getOperationIndexAt(mouseX, mouseY);
        if (operationIndex >= 0) {
            RecordedOperation operation = getSelectedRecordedOperations().get(operationIndex);
            return "T+" + operation.getTick() + " " + operation.getDescription();
        }
        for (GuiButton button : buttonList) {
            if (!button.visible || mouseX < button.x || mouseX >= button.x + button.width
                || mouseY < button.y || mouseY >= button.y + button.height) {
                continue;
            }
            String label = button.displayString;
            switch (button.id) {
                case BUTTON_PREV_SCENE: label = "Previous scene"; break;
                case BUTTON_NEXT_SCENE: label = "Next scene"; break;
                case BUTTON_START: label = "Restart"; break;
                case BUTTON_END: label = "End"; break;
                case BUTTON_RELOAD: label = "Reload"; break;
                case BUTTON_PREV_STEP:
                    label = net.minecraft.client.resources.I18n.format("ponder.ui.button.previous_step"); break;
                case BUTTON_NEXT_STEP:
                    label = net.minecraft.client.resources.I18n.format("ponder.ui.button.next_step"); break;
                case BUTTON_SKIN_CURRENT:
                    label = net.minecraft.client.resources.I18n.format("ponder.ui.skin.current"); break;
                case BUTTON_SKIN_STEVE:
                    label = net.minecraft.client.resources.I18n.format("ponder.ui.skin.steve"); break;
                case BUTTON_SKIN_ALEX:
                    label = net.minecraft.client.resources.I18n.format("ponder.ui.skin.alex"); break;
                case BUTTON_CLOSE: label = "Close"; break;
                default: break;
            }
            return label;
        }
        return null;
    }

    protected long readIndexGeneration() {
        return PonderIndex.getGeneration();
    }

    protected List<ResourceLocation> readIndexComponents() {
        return getRegisteredComponents();
    }

    protected List<PonderScene> compileIndexScenes(ResourceLocation component) {
        return PonderIndex.getSceneAccess().compile(component);
    }

    protected int selectCompiledScene(List<PonderScene> scenes, @Nullable ResourceLocation sceneId,
        int preferredIndex) {
        return PonderIndexViewState.retain(scenes, sceneId, preferredIndex, PonderScene::getSceneId);
    }

    final boolean synchronizeIndex() {
        if (mc != null && !mc.isCallingFromMinecraftThread()) {
            return false;
        }
        long previousGeneration = registryView.generation();
        return registryView.refresh(readIndexGeneration(), () -> replaceIndexView(previousGeneration));
    }

    private void replaceIndexView(long previousGeneration) {
        boolean first = !initialized;
        RetainedSceneView previous = closedView != null ? closedView : captureSceneView(previousGeneration);
        List<ResourceLocation> nextComponents = new ArrayList<>(readIndexComponents());
        ResourceLocation wantedComponent = first ? requestedComponentId : previous.component;
        int nextComponentIndex = PonderIndexViewState.retain(nextComponents, wantedComponent,
            first ? 0 : previous.componentIndex, id -> id);
        ResourceLocation nextComponent = nextComponents.isEmpty() ? null : nextComponents.get(nextComponentIndex);
        String failureMessage = replaceComponentScenes(nextComponent, first ? null : previous.scene,
            first ? requestedSceneIndex : previous.sceneIndex);
        PonderScene nextScene = getSelectedScene();
        boolean sameSelection = !first && nextScene != null && Objects.equals(previous.component, nextComponent)
            && Objects.equals(previous.scene, nextScene.getSceneId());
        boolean sameFraming = sameSelection && previous.matches(nextScene, lastSceneBounds);

        componentIds = nextComponents;
        selectedComponentIndex = nextComponentIndex;
        closedView = null;
        initialized = true;
        componentScroll = PonderIndexViewState.retain(componentIds, previous.componentAnchor,
            previous.componentScroll, id -> id);
        componentScroll = MathHelper.clamp(componentScroll, 0, getMaxComponentScroll());
        playbackTick = sameSelection ? MathHelper.clamp(previous.tick, 0, getSceneEndTick(nextScene)) : 0;
        playing = nextScene != null && (first ? showcaseMode : sameSelection && previous.playing);
        operationScroll = sameSelection ? MathHelper.clamp(previous.operationScroll, 0, getMaxOperationScroll()) : 0;
        summaryScroll = sameSelection ? previous.summaryScroll : 0;
        if (sameFraming) {
            modelFraming.copyFrom(previous.framing);
        } else if (sameSelection) {
            // A changed source gets one fit; user yaw/pitch and manual-camera ownership survive.
            previewZoom = Math.min(previewZoom, 1F);
        } else {
            resetPreviewCamera();
        }
        if (failureMessage != null) {
            showRegistryNotice(failureMessage);
        } else if (!first && previous.generation != registryView.generation()) {
            if (nextScene == null) {
                showRegistryNotice("\u7d22\u5f15\u5df2\u66f4\u65b0\uff0c\u5f53\u524d\u6ca1\u6709\u53ef\u7528\u6f14\u793a");
            } else if (!sameSelection) {
                showRegistryNotice("\u539f\u6f14\u793a\u5df2\u79fb\u9664\uff0c\u5df2\u9009\u62e9\u53ef\u7528\u9879");
            } else if (!sameFraming) {
                showRegistryNotice("\u6f14\u793a\u5df2\u66f4\u65b0\uff0c\u5df2\u91cd\u65b0\u9002\u914d\u89c6\u56fe");
            } else {
                showRegistryNotice("\u7d22\u5f15\u5df2\u66f4\u65b0");
            }
        }
        updateButtonState();
    }

    private RetainedSceneView captureSceneView() {
        return captureSceneView(registryView.generation());
    }

    private RetainedSceneView captureSceneView(long generation) {
        PonderScene scene = getSelectedScene();
        PreviewBounds bounds = scene == null ? null : lastSceneBounds;
        ResourceLocation anchor = componentIds.isEmpty() ? null
            : componentIds.get(MathHelper.clamp(componentScroll, 0, componentIds.size() - 1));
        return new RetainedSceneView(generation, getSelectedComponentId(), scene, bounds, selectedComponentIndex,
            selectedSceneIndex, playbackTick, playing, operationScroll, summaryScroll,
            componentScroll, anchor, modelFraming.copy());
    }

    protected String getRegistryStatus(String fallback) {
        return registryNoticeTicks > 0 ? registryNotice : fallback;
    }

    private void showRegistryNotice(String notice) {
        registryNotice = notice;
        registryNoticeTicks = 140;
    }

    @Nullable
    private String replaceComponentScenes(ResourceLocation componentId, ResourceLocation sceneId, int preferredIndex) {
        List<PonderScene> nextScenes = Collections.emptyList();
        int nextSceneIndex = 0;
        PonderScene nextScene = null;
        PreviewBounds nextBounds = null;
        int nextEndTick = 0;
        String failureMessage = null;
        if (componentId != null) {
            try {
                nextScenes = new ArrayList<>(compileIndexScenes(componentId));
                nextSceneIndex = selectCompiledScene(nextScenes, sceneId, preferredIndex);
                nextScene = nextScenes.isEmpty() ? null : nextScenes.get(nextSceneIndex);
                nextBounds = nextScene == null ? null : PonderScenePreview.computeBounds(nextScene);
                nextEndTick = nextScene == null ? 0 : getSceneEndTick(nextScene);
            } catch (RuntimeException | LinkageError failure) {
                nextScenes = Collections.emptyList();
                nextSceneIndex = 0;
                nextScene = null;
                nextBounds = null;
                failureMessage = "\u6f14\u793a\u65e0\u6cd5\u52a0\u8f7d\uff1a"
                    + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
                Ponder.LOGGER.warn("Cannot prepare Ponder component {}", componentId, failure);
            }
        }
        clearPreviewWorld();
        resetShowcaseTransientState();
        closeShowcaseGroupSelector();
        lastPlaybackBarWidth = 0;
        compiledScenes = nextScenes;
        selectedSceneIndex = nextSceneIndex;
        lastSceneBounds = nextBounds;
        if (nextScene != null) {
            sceneEndTicks.put(nextScene, nextEndTick);
        }
        return failureMessage;
    }

    private void reloadCurrentComponentAt(int preferredSceneIndex) {
        registryNoticeTicks = 0;
        String failureMessage = replaceComponentScenes(getSelectedComponentId(), null, preferredSceneIndex);
        if (failureMessage != null) {
            showRegistryNotice(failureMessage);
        }

        playbackTick = 0;
        operationScroll = 0;
        playing = showcaseMode && getSelectedScene() != null;
        resetPreviewCamera();
        centerOperationsOnActiveLine();
    }

    private void selectComponent(int componentIndex, int preferredSceneIndex) {
        if (componentIds.isEmpty()) {
            return;
        }
        selectedComponentIndex = MathHelper.clamp(componentIndex, 0, componentIds.size() - 1);
        componentScroll = MathHelper.clamp(componentScroll, 0, getMaxComponentScroll());
        reloadCurrentComponentAt(preferredSceneIndex);
        updateButtonState();
    }

    private void selectComponent(ResourceLocation componentId, int preferredSceneIndex) {
        if (componentId == null) {
            return;
        }
        for (int index = 0; index < componentIds.size(); index++) {
            if (componentId.equals(componentIds.get(index))) {
                selectComponent(index, preferredSceneIndex);
                return;
            }
        }
    }

    private void selectScene(int sceneIndex) {
        if (sceneIndex < 0 || sceneIndex >= compiledScenes.size() || sceneIndex == selectedSceneIndex) {
            return;
        }

        selectedSceneIndex = sceneIndex;
        registryNoticeTicks = 0;
        playbackTick = 0;
        operationScroll = 0;
        playing = showcaseMode;
        clearPreviewWorld();
        closeShowcaseGroupSelector();
        resetPreviewCamera();
        centerOperationsOnActiveLine();
    }

    private void stepPlaybackTick(int delta) {
        PonderScene scene = getSelectedScene();
        if (scene == null) {
            return;
        }
        setPlaybackTick(playbackTick + delta);
        playing = false;
        updateButtonState();
    }

    protected void stepLesson(int direction) {
        PonderScene scene = getSelectedScene();
        if (scene == null || scene.getKeyframeTicks().isEmpty()) return;
        pointerInput.cancelDrag();
        captionFocused = false;
        focusedCallout = null;
        clearExplanationHover();
        setPlaybackTick(PreviewLessonControls.step(scene.getKeyframeTicks(), playbackTick,
            getSceneEndTick(scene), direction));
        playing = false;
        updateButtonState();
    }

    private void setPlaybackTick(int tick) {
        setPlaybackTick(tick, false);
    }

    private void setPlaybackTick(int tick, boolean automatic) {
        if (modelMode.isAppearance()) {
            modelMode.chooseStructure();
        }
        modelNotice = false;
        PonderScene scene = getSelectedScene();
        int maxTick = scene == null ? 0 : getSceneEndTick(scene);
        int before = playbackTick;
        playbackTick = MathHelper.clamp(tick, 0, maxTick);
        if (automatic) previewAudio.advance(scene, before, playbackTick);
        else previewAudio.seek(scene, playbackTick);
        centerOperationsOnActiveLine();
        updateButtonState();
    }

    protected void updateButtonState() {
        boolean hasScenes = !compiledScenes.isEmpty();
        if (prevSceneButton != null) {
            prevSceneButton.enabled = hasScenes && selectedSceneIndex > 0;
        }
        if (nextSceneButton != null) {
            nextSceneButton.enabled = hasScenes && selectedSceneIndex + 1 < compiledScenes.size();
        }
        if (startButton != null) {
            startButton.enabled = hasScenes && (showcaseMode || playbackTick > 0);
        }
        if (playPauseButton != null) {
            playPauseButton.enabled = hasScenes;
            playPauseButton.displayString = playing ? "Pause" : "Play";
        }
        if (endButton != null) {
            endButton.enabled = hasScenes && playbackTick < getSceneEndTick(getSelectedScene());
        }
        if (debugButton != null) {
            debugButton.enabled = hasScenes;
        }
        for (GuiButton button : buttonList) {
            if (button.id == BUTTON_PREV_STEP || button.id == BUTTON_NEXT_STEP) {
                PonderScene scene = getSelectedScene();
                button.enabled = scene != null && !scene.getKeyframeTicks().isEmpty()
                    && (button.id == BUTTON_PREV_STEP ? playbackTick > 0 : playbackTick < getSceneEndTick(scene));
            }
            if (button.id >= BUTTON_SKIN_CURRENT && button.id <= BUTTON_SKIN_ALEX) {
                PonderScene scene = getSelectedScene();
                boolean hasPlayer = scene != null && scene.getActorEvents().stream()
                    .anyMatch(event -> event.getActorKind() == PonderScene.ActorKind.PLAYER);
                button.enabled = hasPlayer && button.id - BUTTON_SKIN_CURRENT != playerSkinMode.ordinal();
            }
            if (button.id >= BUTTON_DEBUG_PREVIEW && button.id <= BUTTON_DEBUG_TIMELINE) {
                button.enabled = button.id != BUTTON_DEBUG_PREVIEW + debugTab;
            }
        }
    }

    protected ResourceLocation getSelectedComponentId() {
        if (componentIds.isEmpty()) {
            return null;
        }
        return componentIds.get(MathHelper.clamp(selectedComponentIndex, 0, componentIds.size() - 1));
    }

    protected int getCompiledSceneCount() {
        return compiledScenes.size();
    }

    protected int getPlaybackTickValue() {
        return playbackTick;
    }

    protected boolean isPlaybackRunning() {
        return playing;
    }

    @Nullable
    protected PonderScene getNextScene() {
        return selectedSceneIndex + 1 < compiledScenes.size() ? compiledScenes.get(selectedSceneIndex + 1) : null;
    }

    protected void setNextUpCardBounds(int x, int y, int width, int height) {
        lastNextUpCardX = x;
        lastNextUpCardY = y;
        lastNextUpCardWidth = width;
        lastNextUpCardHeight = height;
    }

    protected int getSelectedSceneIndex() {
        return selectedSceneIndex;
    }

    protected PonderScene getSelectedScene() {
        if (compiledScenes.isEmpty()) {
            return null;
        }
        return compiledScenes.get(MathHelper.clamp(selectedSceneIndex, 0, compiledScenes.size() - 1));
    }

    public boolean isShowingScene(ResourceLocation sceneId) {
        PonderScene selected = getSelectedScene();
        return selected != null && sceneId != null && sceneId.equals(selected.getSceneId());
    }

    private List<RecordedOperation> getSelectedRecordedOperations() {
        PonderScene scene = getSelectedScene();
        return scene == null ? Collections.<RecordedOperation>emptyList() : scene.getRecordedOperations();
    }

    protected int getSceneEndTick(PonderScene scene) {
        Integer cached = sceneEndTicks.get(scene);
        if (cached != null) {
            return cached.intValue();
        }
        int maxTick = scene.getTotalIdleTicks();
        for (RecordedOperation operation : scene.getRecordedOperations()) {
            maxTick = Math.max(maxTick, operation.getTick());
        }
        for (WorldEvent event : scene.getWorldEvents()) {
            maxTick = Math.max(maxTick, event.getTick() + Math.max(0, event.getDuration()));
            if ((event.getType() == PonderScene.WorldEventType.SHOW_SECTION && event.getDirection() != null)
                || event.getType() == PonderScene.WorldEventType.HIDE_SECTION) {
                maxTick = Math.max(maxTick, event.getTick() + (event.getDuration() > 0 ? event.getDuration() : PonderSceneRuntime.SECTION_FADE_TICKS));
            }
        }
        for (PonderScene.CameraEvent event : scene.getCameraEvents()) {
            maxTick = Math.max(maxTick, event.getTick() + event.getDuration());
        }
        for (PonderScene.CameraViewEvent event : scene.getCameraViewEvents()) {
            maxTick = Math.max(maxTick, event.getTick() + event.getDuration());
        }
        for (PonderScene.SoundEvent event : scene.getSoundEvents()) {
            maxTick = Math.max(maxTick, event.getTick());
        }
        for (PonderScene.ActorEvent event : scene.getActorEvents()) {
            maxTick = Math.max(maxTick, event.getTick() + event.getDuration());
        }
        for (PonderScene.ParticleEffectEvent event : scene.getParticleEvents()) {
            maxTick = Math.max(maxTick, event.getTick() + event.getCycles());
        }
        for (PonderScene.OverlayEvent event : scene.getOverlayEvents()) {
            maxTick = Math.max(maxTick, event.getTick() + event.getDuration());
        }
        sceneEndTicks.put(scene, Integer.valueOf(maxTick));
        return maxTick;
    }

    protected float getRenderTick(PonderScene scene, float partialTicks) {
        if (scene == null) {
            return 0.0F;
        }
        if (!playing) {
            return playbackTick;
        }
        return Math.min(getSceneEndTick(scene), playbackTick + Math.max(0.0F, partialTicks));
    }

    private int getActiveOperationIndex(List<RecordedOperation> operations, int currentTick) {
        int activeIndex = -1;
        for (int i = 0; i < operations.size(); i++) {
            if (operations.get(i).getTick() <= currentTick) {
                activeIndex = i;
            } else {
                break;
            }
        }
        return activeIndex;
    }

    private RecordedOperation getActiveOperation(List<RecordedOperation> operations, int currentTick) {
        int activeIndex = getActiveOperationIndex(operations, currentTick);
        return activeIndex >= 0 && activeIndex < operations.size() ? operations.get(activeIndex) : null;
    }

    private WorldEvent getLatestWorldEvent(PonderScene scene, int currentTick) {
        WorldEvent latest = null;
        for (WorldEvent event : scene.getWorldEvents()) {
            if (event.getTick() > currentTick) {
                break;
            }
            latest = event;
        }
        return latest;
    }

    private String formatWorldEvent(WorldEvent event) {
        StringBuilder builder = new StringBuilder();
        builder.append("T+")
            .append(event.getTick())
            .append(' ')
            .append(event.getType().name().toLowerCase().replace('_', ' '))
            .append(" x")
            .append(event.getPositions().size());
        if (event.getDirection() != null) {
            builder.append(" @ ").append(event.getDirection().name().toLowerCase());
        }
        if (event.getStateDescription() != null && !event.getStateDescription().isEmpty()) {
            builder.append(" -> ").append(event.getStateDescription());
        }
        return builder.toString();
    }

    private void centerOperationsOnActiveLine() {
        List<RecordedOperation> operations = getSelectedRecordedOperations();
        if (operations.isEmpty()) {
            operationScroll = 0;
            return;
        }

        int activeIndex = Math.max(0, getActiveOperationIndex(operations, playbackTick));
        int visibleLines = getVisibleOperationLines();
        operationScroll = MathHelper.clamp(activeIndex - visibleLines / 2, 0, getMaxOperationScroll());
    }

    private int getVisibleOperationLines() {
        return getDebugTextRows(getDebugLayout().operationRows).lines();
    }

    private int getMaxOperationScroll() {
        return Math.max(0, getSelectedRecordedOperations().size() - getVisibleOperationLines());
    }

    private int getMaxComponentScroll() {
        int visible = getDebugLayout().componentRows.lines();
        return Math.max(0, componentIds.size() - visible);
    }

    private int getComponentIndexAt(int mouseX, int mouseY) {
        if (!isMouseOverComponentList(mouseX, mouseY)) {
            return -1;
        }

        int relativeY = mouseY - getDebugLayout().componentRows.y;
        int index = componentScroll + relativeY / LINE_HEIGHT;
        return index >= 0 && index < componentIds.size() ? index : -1;
    }

    protected ShowcaseGroupIcon getShowcaseGroupIconAt(int mouseX, int mouseY) {
        for (ShowcaseGroupIcon icon : lastShowcaseGroupIcons) {
            if (mouseX >= icon.x && mouseX < icon.x + icon.width
                && mouseY >= icon.y && mouseY < icon.y + icon.height) {
                return icon;
            }
        }
        return null;
    }

    protected boolean hasShowcaseGroupChoices() {
        ShowcaseGroupState groupState = getShowcaseGroupState();
        return groupState != null && groupState.components.size() > 1;
    }

    protected boolean isMouseOverPlaybackBar(int mouseX, int mouseY) {
        return lastPlaybackBarWidth > 0 && isWithin(mouseX, mouseY, lastPlaybackBarX, lastPlaybackBarY - 3,
            lastPlaybackBarX + lastPlaybackBarWidth, lastPlaybackBarY + lastPlaybackBarHeight + 4);
    }

    protected boolean isMouseOverNextUpCard(int mouseX, int mouseY) {
        return lastNextUpCardWidth > 0 && isWithin(mouseX, mouseY, lastNextUpCardX, lastNextUpCardY,
            lastNextUpCardX + lastNextUpCardWidth, lastNextUpCardY + lastNextUpCardHeight + 8);
    }

    private void seekPlaybackToMouse(int mouseX) {
        setPlaybackTick(estimatePlaybackTickForMouse(mouseX));
    }

    protected int estimatePlaybackTickForMouse(int mouseX) {
        PonderScene scene = getSelectedScene();
        if (scene == null || lastPlaybackBarWidth <= 0) {
            return 0;
        }

        int maxTick = Math.max(1, getSceneEndTick(scene));
        float progress = (mouseX - lastPlaybackBarX) / (float) Math.max(1, lastPlaybackBarWidth);
        progress = MathHelper.clamp(progress, 0.0F, 1.0F);
        return Math.round(progress * maxTick);
    }

    protected boolean isMouseOverShowcaseHeaderIcon(int mouseX, int mouseY) {
        return lastShowcaseHeaderIconSize > 0
            && isWithin(mouseX, mouseY, lastShowcaseHeaderIconX, lastShowcaseHeaderIconY,
                lastShowcaseHeaderIconX + lastShowcaseHeaderIconSize, lastShowcaseHeaderIconY + lastShowcaseHeaderIconSize);
    }

    private int getOperationIndexAt(int mouseX, int mouseY) {
        if (!isMouseOverOperations(mouseX, mouseY)) {
            return -1;
        }

        int relativeY = mouseY - getDebugTextRows(getDebugLayout().operationRows).y;
        int index = operationScroll + relativeY / LINE_HEIGHT;
        List<RecordedOperation> operations = getSelectedRecordedOperations();
        return index >= 0 && index < operations.size() ? index : -1;
    }

    private boolean isMouseOverComponentList(int mouseX, int mouseY) {
        return !showcaseMode && getDebugLayout().componentRows.contains(mouseX, mouseY);
    }

    private boolean isMouseOverOperations(int mouseX, int mouseY) {
        PreviewDebugLayout layout = getDebugLayout();
        return !showcaseMode && (!layout.compact || debugTab == 2)
            && getDebugTextRows(layout.operationRows).contains(mouseX, mouseY);
    }

    private boolean isMouseOverSummary(int mouseX, int mouseY) {
        PreviewDebugLayout layout = getDebugLayout();
        return !showcaseMode && (!layout.compact || debugTab == 1)
            && getDebugTextRows(layout.summaryRows).contains(mouseX, mouseY);
    }

    private boolean isMouseOverPreview(int mouseX, int mouseY) {
        if (lastPreviewLayout == null) {
            return false;
        }

        if (showcaseGroupSelectorOpen || isMouseOverButton(mouseX, mouseY) || overlayHitAreas.contains(mouseX, mouseY)
            || isMouseOverPlaybackBar(mouseX, mouseY) || isMouseOverNextUpCard(mouseX, mouseY)
            || modelControls != null && modelControls.contains(mouseX, mouseY)
            || captionBounds != null && captionBounds.contains(mouseX, mouseY)) {
            return false;
        }
        return mouseX >= lastPreviewLayout.originX && mouseX < lastPreviewLayout.originX + lastPreviewLayout.width
            && mouseY >= lastPreviewLayout.originY && mouseY < lastPreviewLayout.originY + lastPreviewLayout.height;
    }

    private boolean isControlKeyDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    private boolean isWithin(int mouseX, int mouseY, int minX, int minY, int maxX, int maxY) {
        return mouseX >= minX && mouseX <= maxX && mouseY >= minY && mouseY <= maxY;
    }

    private String formatTags(Collection<PonderTag> tags) {
        if (tags.isEmpty()) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (PonderTag tag : tags) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(tag.getId());
        }
        builder.append(']');
        return builder.toString();
    }

    protected void resetPreviewCamera() {
        modelFraming.resetCamera();
        previewYaw = -45.0F;
        previewPitch = PreviewCameraMath.DEFAULT_PITCH;
        // The fit calculation already fills the safe viewport. Start at 1x so
        // tall/rotated scenes never clip on their first frame.
        previewZoom = 1.0F;
        previewCameraManual = false;
        manualCameraView = null;
        pointerInput.cancelDrag();
        showcaseFadeTicks = 0;
    }

    private float getPreviewAnchorY(PreviewLayout layout) {
        return PreviewCameraMath.anchorY(layout.originY, layout.height, showcaseMode);
    }

    private float getPreviewYaw(PonderScene scene, float renderTick) {
        return previewYaw + (previewCameraManual ? 0.0F : PonderSceneRuntime.getCameraYaw(scene, renderTick));
    }

    private float getPreviewPitch(PonderScene scene, float renderTick) {
        return previewPitch + (previewCameraManual ? 0.0F : PonderSceneRuntime.getCameraPitch(scene, renderTick));
    }

    protected boolean hasModelControls() {
        return MmcePonderPlugin.getSceneSnapshot(getSelectedScene()) != null;
    }

    protected void drawModelControls(int x, int y) {
        modelControls = new PreviewModelControls(x, y, Math.max(16, fontRenderer.FONT_HEIGHT + 7));
        drawModelSegment(modelControls.structure, "\u7ed3\u6784", !modelMode.isAppearance());
        drawModelSegment(modelControls.appearance, "\u5916\u89c2", modelMode.isAppearance());
    }

    protected PreviewDebugLayout.Rect[] getShowcaseModelControlBounds() {
        return modelControls == null ? new PreviewDebugLayout.Rect[0]
            : new PreviewDebugLayout.Rect[] { modelControls.structure, modelControls.appearance };
    }

    private void drawModelSegment(PreviewDebugLayout.Rect rect, String label, boolean selected) {
        drawRect(rect.x, rect.y, rect.right(), rect.bottom(), selected ? 0xFF7CA6AE : 0xCC3B4650);
        drawRect(rect.x + 1, rect.y + 1, rect.right() - 1, rect.bottom() - 1,
            selected ? 0xFF263B42 : 0xFF141B20);
        drawCenteredString(fontRenderer, label, rect.x + rect.width / 2, rect.y + 4,
            selected ? 0xF3FAFC : 0xAEB8C1);
    }

    protected String getModelStatus(String normalStatus) {
        MmceStructureSnapshot snapshot = MmcePonderPlugin.getSceneSnapshot(getSelectedScene());
        String appearance = snapshot != null && snapshot.getVariant() == MmceStructureSnapshot.Variant.MAXIMUM
            ? "\u9759\u6001\u5916\u89c2 \u00b7 \u4e0d\u4f38\u7f29" : "\u9759\u6001\u5916\u89c2";
        return modelNotice ? modelMode.getNote()
            : modelMode.isAppearance() ? appearance : normalStatus;
    }

    private String getModelHoverHint(int mouseX, int mouseY) {
        if (showcaseGroupSelectorOpen || modelControls == null || !modelControls.contains(mouseX, mouseY)) {
            return null;
        }
        String note = modelMode.getNote();
        if (note.isEmpty()) {
            note = "\u7ed3\u6784 / \u9759\u6001\u5916\u89c2";
        }
        MmceStructureSnapshot snapshot = MmcePonderPlugin.getSceneSnapshot(getSelectedScene());
        if (snapshot != null && snapshot.getVariant() == MmceStructureSnapshot.Variant.MAXIMUM) {
            note += "\n\u6700\u5927\u7ed3\u6784\u4ecd\u663e\u793a\u540c\u4e00\u4e2a\u5df2\u6ce8\u518c\u9759\u6001\u6a21\u578b\uff0c"
                + "\u5916\u89c2\u4e0d\u81ea\u52a8\u4f38\u7f29\u3002";
        }
        return note;
    }

    private void selectModelMode(boolean appearance) {
        pointerInput.cancelDrag();
        captionFocused = false;
        clearExplanationHover();
        overlayHitAreas.clear();
        captionBounds = null;
        captionPrevious = null;
        captionNext = null;
        if (!appearance) {
            modelMode.chooseStructure();
            modelNotice = false;
            return;
        }
        PonderScene scene = getSelectedScene();
        MmceStructureSnapshot snapshot = MmcePonderPlugin.getSceneSnapshot(scene);
        if (snapshot == null || lastPreviewLayout == null) {
            return;
        }
        playing = false;
        MmceStaticModelPreviews previews = MmceStaticModelPreviews.getInstance();
        MmceStaticModelPreviews.Preview previous = modelMode.getPreview();
        try {
            boolean available = modelMode.chooseAppearance(
                () -> previews.getPreview(snapshot.getMachine().getId(), mc.getResourceManager()),
                MmceStaticModelPreviews.Preview::hasMesh, MmceStaticModelPreviews.Preview::getNote);
            modelNotice = !available;
            if (available && previous != modelMode.getPreview()) {
                MmceStaticModelMesh.Bounds mesh = modelMode.getPreview().getMesh().getBounds();
                BlockPos anchor = snapshot.getOrigin();
                double x = Vec3iAccessor.x(anchor);
                double y = Vec3iAccessor.y(anchor);
                double z = Vec3iAccessor.z(anchor);
                PreviewModelFraming framing = getModelFraming(PonderScenePreview.computeBounds(scene));
                if (!PreviewCameraMath.usableModelBounds(mesh.minX + x, mesh.minY + y, mesh.minZ + z,
                    mesh.maxX + x, mesh.maxY + y, mesh.maxZ + z,
                    framing.centerX(), framing.centerY(), framing.centerZ())) {
                    throw new IllegalArgumentException("\u6a21\u578b\u663e\u793a\u8303\u56f4\u8d85\u51fa\u9884\u89c8\u9650\u5236");
                }
                boolean initialFit = modelMode.claimInitialFit();
                framing.include(
                    mesh.minX + x, mesh.minY + y, mesh.minZ + z,
                    mesh.maxX + x, mesh.maxY + y, mesh.maxZ + z, initialFit,
                    lastPreviewLayout.width, lastPreviewLayout.height, showcaseMode, scene.getScaleFactor());
                if (initialFit) {
                    previewZoom = Math.min(previewZoom, 1F);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            failModelPreview(failure);
        } finally {
            modelResourceGeneration = previews.getResourceGeneration();
            modelIndexGeneration = MmceMachineIndex.getInstance().getGeneration();
        }
        updateButtonState();
    }

    private void refreshModelPreview() {
        if (!modelMode.wasAttempted()) {
            return;
        }
        MmceStaticModelPreviews previews = MmceStaticModelPreviews.getInstance();
        MmceStaticModelPreviews.Preview preview = modelMode.getPreview();
        boolean current = preview != null ? previews.isCurrent(preview)
            : modelResourceGeneration == previews.getResourceGeneration()
                && modelIndexGeneration == MmceMachineIndex.getInstance().getGeneration();
        boolean showNotice = modelMode.isAppearance() || modelNotice;
        if (modelMode.invalidate(current)) {
            modelRenderer.clear();
            pointerInput.cancelDrag();
            modelNotice = showNotice && hasModelControls();
        }
    }

    private PreviewModelFraming getModelFraming(PreviewBounds bounds) {
        modelFraming.initialize(bounds.minX, bounds.minY, bounds.minZ,
            (double) bounds.maxX + 1, (double) bounds.maxY + 1, (double) bounds.maxZ + 1);
        return modelFraming;
    }

    private boolean renderModelPreview(PonderScene scene, PreviewBounds bounds, PreviewLayout layout, float tick) {
        MmceStaticModelPreviews.Preview preview = modelMode.getPreview();
        MmceStructureSnapshot snapshot = MmcePonderPlugin.getSceneSnapshot(scene);
        if (preview == null || !preview.hasMesh() || snapshot == null) {
            modelMode.chooseStructure();
            return false;
        }
        try {
            modelRenderer.draw(mc, preview.getMesh(), preview.getTexture(), () -> {
                setPreviewScissor(layout);
                float scale = computePreviewScale(scene, bounds, layout, tick);
                Vec3d center = getPreviewView(scene, bounds, tick).center;
                GlStateManager.translate(layout.originX + layout.width / 2F, getPreviewAnchorY(layout), 190F);
                GlStateManager.scale(scale, -scale, scale);
                GlStateManager.rotate(getPreviewPitch(scene, tick), 1F, 0F, 0F);
                GlStateManager.rotate(getPreviewYaw(scene, tick), 0F, 1F, 0F);
                GlStateManager.translate(-center.x, -center.y, -center.z);
                // The baker already added MMCE's local (0.5, 0.01, 0.5) origin.
                BlockPos anchor = snapshot.getOrigin();
                GlStateManager.translate(Vec3iAccessor.x(anchor), Vec3iAccessor.y(anchor), Vec3iAccessor.z(anchor));
            });
            return true;
        } catch (RuntimeException | LinkageError failure) {
            failModelPreview(failure);
            return false;
        }
    }

    private void failModelPreview(Throwable failure) {
        String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        modelMode.fail("\u5916\u89c2\u65e0\u6cd5\u663e\u793a\uff0c\u5df2\u8fd4\u56de\u7ed3\u6784\uff1a" + reason);
        modelRenderer.clear();
        modelNotice = true;
        Ponder.LOGGER.warn("Cannot display read-only MMCE appearance preview", failure);
    }

    private void drawModelNotice(int x, int y, int width, int height) {
        String text = modelMode.getNote();
        int textWidth = Math.max(1, Math.min(250, width - 34));
        if (captionTextWidth != textWidth || !text.equals(captionText)) {
            captionText = text;
            captionTextWidth = textWidth;
            captionLines = PreviewTextLayout.wrap(fontRenderer, text, textWidth);
            captionLongestLine = 0;
            for (String line : captionLines) {
                captionLongestLine = Math.max(captionLongestLine, fontRenderer.getStringWidth(line));
            }
        }
        int bottom = showcaseMode ? PreviewCaptionPaging.bottom(y, height,
            lastPlaybackBarWidth > 0 ? lastPlaybackBarY : this.height - 70) : y + height - 4;
        int top = showcaseMode ? PreviewCaptionPaging.top(y, bottom) : y + 4;
        captionPaging.configure(text, captionLines.size(), bottom - top);
        int boxWidth = textWidth + 22;
        int boxHeight = captionPaging.height();
        int boxX = x + (width - boxWidth) / 2;
        int boxY = Math.max(top, bottom - boxHeight);
        drawSpeechBox(boxX, boxY, boxWidth, boxHeight, SpeechPointing.NONE, 0xE1AE72, 1F);
        captionBounds = new PreviewDebugLayout.Rect(boxX, boxY, boxWidth, boxHeight);
        for (int i = captionPaging.firstLine(); i < captionPaging.endLine(); i++) {
            drawCenteredStringNoShadow(captionLines.get(i), boxX + boxWidth / 2,
                boxY + 6 + (i - captionPaging.firstLine()) * PreviewCaptionPaging.LINE_HEIGHT, 0xF2F5F8);
        }
        if (captionPaging.isPaged()) {
            int footerY = boxY + boxHeight - PreviewCaptionPaging.FOOTER_HEIGHT;
            captionPrevious = new PreviewDebugLayout.Rect(boxX + 3, footerY, 22, 14);
            captionNext = new PreviewDebugLayout.Rect(boxX + boxWidth - 25, footerY, 22, 14);
            drawCenteredStringNoShadow("<", captionPrevious.x + 11, footerY + 3,
                captionPaging.page() > 0 ? 0xF2F5F8 : 0x58616A);
            drawCenteredStringNoShadow(">", captionNext.x + 11, footerY + 3,
                captionPaging.page() + 1 < captionPaging.pageCount() ? 0xF2F5F8 : 0x58616A);
            drawCenteredStringNoShadow((captionPaging.page() + 1) + " / " + captionPaging.pageCount(),
                boxX + boxWidth / 2, footerY + 3, 0xAEB8C1);
        }
    }

    private PreviewFrame getPreviewFrame(PonderScene scene, float renderTick) {
        return previewFrames.get(scene, renderTick, () -> {
            PonderSceneRuntime.RuntimeState state = PonderSceneRuntime.buildState(scene, renderTick);
            List<PonderSceneRuntime.RuntimeBlockState> solid = new ArrayList<PonderSceneRuntime.RuntimeBlockState>();
            List<PonderSceneRuntime.RuntimeBlockState> translucent =
                new ArrayList<PonderSceneRuntime.RuntimeBlockState>();
            Map<PonderSceneRuntime.RuntimeBlockState, List<BlockRenderLayer>> layers =
                new LinkedHashMap<PonderSceneRuntime.RuntimeBlockState, List<BlockRenderLayer>>();
            for (PonderSceneRuntime.RuntimeBlockState block : state.blocksByPosition.values()) {
                if (!block.visible || block.fade <= 0.0F || block.currentState == null) {
                    continue;
                }
                List<BlockRenderLayer> supported;
                boolean hasTile;
                try {
                    supported = PreviewBlockRendering.modelLayers(block.currentState);
                    hasTile = block.currentState.getBlock().hasTileEntity(block.currentState);
                } catch (RuntimeException | LinkageError failure) {
                    warnRenderFailure("model-layers:" + block.currentState.getBlock(),
                        "Ponder could not discover render layers for " + getBlockId(block.currentState)
                            + " at " + block.pos, failure);
                    continue;
                }
                layers.put(block, supported);
                boolean hasSolid = false;
                boolean hasTranslucent = false;
                for (BlockRenderLayer layer : supported) {
                    if (PreviewBlockRendering.transparent(block, layer)) {
                        hasTranslucent = true;
                    } else {
                        hasSolid = true;
                    }
                }
                if (hasTile) {
                    if (isTranslucentTilePreview(block)) {
                        hasTranslucent = true;
                    } else {
                        hasSolid = true;
                    }
                }
                if (hasSolid) solid.add(block);
                if (hasTranslucent) translucent.add(block);
            }
            Collections.sort(solid, new Comparator<PonderSceneRuntime.RuntimeBlockState>() {
                @Override
                public int compare(PonderSceneRuntime.RuntimeBlockState left,
                    PonderSceneRuntime.RuntimeBlockState right) {
                    int y = Double.compare(left.renderCenterY, right.renderCenterY);
                    if (y != 0) {
                        return y;
                    }
                    int z = Double.compare(left.renderCenterZ, right.renderCenterZ);
                    return z != 0 ? z : Double.compare(left.renderCenterX, right.renderCenterX);
                }
            });
            return new PreviewFrame(state, solid, translucent, layers);
        });
    }

    private void renderScenePreview(PonderScene scene, PreviewBounds bounds, PreviewFrame frame,
        PreviewLayout layout, float renderTick) {
        PreviewRenderState previousRenderState = new PreviewRenderState();
        enablePreviewScissor(layout);

        int previousAmbientOcclusion = mc != null && mc.gameSettings != null ? mc.gameSettings.ambientOcclusion : 0;
        boolean previewMatrixPushed = false;
        if (mc != null && mc.gameSettings != null) {
            // Force bright, readable previews instead of inheriting the user's world AO setting.
            mc.gameSettings.ambientOcclusion = 0;
        }

        try {
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.disableFog();
        GlStateManager.depthMask(true);
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        previewMatrixPushed = true;
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        RenderHelper.enableGUIStandardItemLighting();
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        float viewportCenterX = layout.originX + layout.width / 2.0F;
        float viewportAnchorY = getPreviewAnchorY(layout);
        float scale = computePreviewScale(scene, bounds, layout, renderTick);
        Vec3d center = getPreviewView(scene, bounds, renderTick).center;
        float animatedYaw = getPreviewYaw(scene, renderTick);

        GlStateManager.translate(viewportCenterX, viewportAnchorY, 190.0F);
        GlStateManager.scale(scale, -scale, scale);
        GlStateManager.rotate(getPreviewPitch(scene, renderTick), 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(animatedYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(-center.x, -center.y, -center.z);

        drawSceneShadow(scene, bounds);

        sortTranslucentFrame(frame, bounds, scene, renderTick);
        renderSharedSceneGeometry(scene, frame, renderTick, true);

        RenderHelper.disableStandardItemLighting();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableRescaleNormal();
        } finally {
            if (previewMatrixPushed) {
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                GlStateManager.popMatrix();
            }
            if (mc.entityRenderer != null) {
                mc.entityRenderer.disableLightmap();
            }
            previousRenderState.restore();
            disablePreviewScissor();
            if (mc != null && mc.gameSettings != null) {
                mc.gameSettings.ambientOcclusion = previousAmbientOcclusion;
            }
        }
    }

    // GUI previews and world projectors share geometry, never framebuffer setup.
    private void renderSharedSceneGeometry(PonderScene scene, PreviewFrame frame, float renderTick, boolean showOccluded) {
        BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();
        PreviewBlockAccess previewWorld = new PreviewBlockAccess(frame.state);
        if (appliedWorldFrame != frame) {
            updatePreviewWorld(frame.state);
            appliedWorldFrame = frame;
        }

        if (mc.entityRenderer != null) {
            mc.entityRenderer.enableLightmap();
        }
        setPreviewLightmap();

        GlStateManager.disableBlend();
        for (PonderSceneRuntime.RuntimeBlockState block : frame.solid) {
            if (!block.visible || block.fade <= 0.0F) {
                continue;
            }

            IBlockState state = block.currentState;
            if (state == null) {
                continue;
            }

            renderBlockModelPreview(dispatcher, previewWorld, block, state, frame.layers.get(block), false);
            if (!isTranslucentTilePreview(block)) {
                renderTileEntityPreview(block, state);
            }
        }

        // Actors write depth before glass/water so foreground transparency also
        // blends over people and minecarts, rather than being painted underneath.
        renderActorPreviews(scene, renderTick);
        if (!showOccluded) renderWorldParticles(scene, renderTick);

        // Translucent blocks must be drawn after the solid pass and from far to
        // near. They also must not write depth, otherwise glass/water can hide
        // the blocks behind them in the preview.
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        for (PonderSceneRuntime.RuntimeBlockState block : frame.translucent) {
            if (!block.visible || block.fade <= 0.0F || block.currentState == null) {
                continue;
            }
            renderBlockModelPreview(dispatcher, previewWorld, block, block.currentState, frame.layers.get(block), true);
            if (isTranslucentTilePreview(block)) {
                renderTileEntityPreview(block, block.currentState);
            }
        }
        GlStateManager.depthMask(true);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);

        outlineRenderer.render(scene, frame.state, renderTick, previewWorld, showOccluded);
        if (showOccluded && scene == externalScene && editorPickMarker != null)
            outlineRenderer.renderSelection(editorPickMarker, frame.state, previewWorld);

        if (mc.entityRenderer != null) {
            mc.entityRenderer.disableLightmap();
        }

    }

    /** Caller supplies a model transform. This path never clears world depth or changes mc.world. */
    public void renderWorldProjection(PonderScene scene, float tick, Vec3d localViewer) {
        prepareExternalContext(scene);
        projectionViewer = localViewer == null ? Vec3d.ZERO : localViewer;
        PreviewRenderState previous = new PreviewRenderState();
        float lineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        int ao = mc.gameSettings.ambientOcclusion;
        try {
            mc.gameSettings.ambientOcclusion = 0;
            GlStateManager.enableDepth();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.depthMask(true);
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.enableRescaleNormal();
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            PreviewFrame frame = getPreviewFrame(scene, tick);
            Collections.sort(frame.translucent, (a, b) -> Double.compare(
                projectionViewer.squareDistanceTo(b.renderCenterX, b.renderCenterY, b.renderCenterZ),
                projectionViewer.squareDistanceTo(a.renderCenterX, a.renderCenterY, a.renderCenterZ)));
            frame.sortedYaw = Float.NaN;
            frame.sortedPitch = Float.NaN;
            renderSharedSceneGeometry(scene, frame, tick, false);
        } finally {
            mc.gameSettings.ambientOcclusion = ao;
            if (mc.entityRenderer != null) mc.entityRenderer.disableLightmap();
            GL11.glLineWidth(lineWidth);
            previous.restore();
        }
    }

    public void releaseProjectionResources() {
        clearPreviewWorld();
    }

    public void renderEditorPreview(PonderScene scene, float tick, int x, int y, int w, int h,
        float yaw, float pitch, float zoom) {
        prepareExternalContext(scene);
        worldProjection = null;
        previewYaw = yaw;
        previewPitch = MathHelper.clamp(pitch, -85, 85);
        previewZoom = MathHelper.clamp(zoom, 0.25F, 4F);
        PreviewLayout layout = new PreviewLayout(x, y, Math.max(1, w), Math.max(1, h));
        PreviewBounds bounds = PonderScenePreview.computeBounds(scene);
        lastPreviewLayout = layout;
        lastSceneBounds = bounds;
        externalOverlayArea = new PreviewDebugLayout.Rect(x + 3, y + 16, Math.max(0, w - 6), Math.max(0, h - 19));
        explanationLayout = PreviewExplanationLayout.embedded(externalOverlayArea);
        renderScenePreview(scene, bounds, getPreviewFrame(scene, tick), layout, tick);
    }

    void setEditorPickMarker(BlockPos position) { editorPickMarker = position == null ? null : position.toImmutable(); }

    PreviewBlockPicking.Hit editorPickBlock(PonderScene scene, float tick, int x, int y) {
        if (scene == null || scene != externalScene || lastPreviewLayout == null || lastSceneBounds == null
            || worldProjection != null || externalCalloutContains(x, y)
            || x < lastPreviewLayout.originX || y < lastPreviewLayout.originY
            || x >= lastPreviewLayout.originX + lastPreviewLayout.width
            || y >= lastPreviewLayout.originY + lastPreviewLayout.height) return null;
        PreviewFrame frame = getPreviewFrame(scene, tick);
        PreviewBlockAccess access = new PreviewBlockAccess(frame.state);
        List<PreviewBlockPicking.Candidate> candidates = new ArrayList<>();
        for (PonderSceneRuntime.RuntimeBlockState block : frame.state.blocksByPosition.values()) {
            AxisAlignedBB box = PreviewOutlineGeometry.selectionBounds(block.pos, frame.state, access);
            if (box != null) candidates.add(new PreviewBlockPicking.Candidate(block, box));
        }
        PreviewBlockPicking.Camera camera = new PreviewBlockPicking.Camera(
            getPreviewView(scene, lastSceneBounds, tick).center, getPreviewYaw(scene, tick), getPreviewPitch(scene, tick),
            computePreviewScale(scene, lastSceneBounds, lastPreviewLayout, tick),
            lastPreviewLayout.originX + lastPreviewLayout.width / 2.0D, getPreviewAnchorY(lastPreviewLayout));
        return PreviewBlockPicking.pick(candidates, camera, x, y);
    }

    /** Called after the host editor's controls; shares the full foreground pipeline with the player. */
    public void renderEditorForeground(PonderScene scene, float tick) {
        if (scene == null || scene != externalScene || lastPreviewLayout == null || externalOverlayArea == null) return;
        overlayHitAreas.clear();
        PonderScreenOverlay.run(width, height, mc.displayWidth, mc.displayHeight, () -> {
            drawSceneSpaceOverlays(scene, lastPreviewLayout, tick, 1F);
            runClippedOverlay(externalOverlayArea, () -> drawForegroundCallouts(scene, tick, 1F));
        });
    }

    public void renderWorldForeground(PonderScene scene, float tick, PreviewWorldProjection projection,
        PreviewDebugLayout.Rect area) {
        renderWorldForeground(scene, tick, projection, area, null);
    }

    void renderWorldForeground(PonderScene scene, float tick, PreviewWorldProjection projection,
        PreviewDebugLayout.Rect area, ProjectorReadingLayout reading) {
        if (scene == null || projection == null || area == null) { invalidateExternalCallouts(); return; }
        prepareExternalContext(scene);
        if (reading != null && !reading.matches(width, height, mc.displayWidth, mc.displayHeight)) {
            invalidateExternalCallouts(); return;
        }
        withForegroundReadingCanvas(reading, () -> {
            worldProjection = reading == null ? projection : projection.withLogicalSize(width, height);
            externalOverlayArea = area;
            explanationLayout = PreviewExplanationLayout.embedded(area);
            lastPreviewLayout = new PreviewLayout(area.x, area.y, area.width, area.height);
            lastSceneBounds = PonderScenePreview.computeBounds(scene);
            overlayHitAreas.clear();
            PonderScreenOverlay.run(width, height, mc.displayWidth, mc.displayHeight,
                () -> runClippedOverlay(area, () -> drawForegroundCallouts(scene, tick, 1F)));
        });
    }

    void withForegroundReadingCanvas(ProjectorReadingLayout reading, Runnable render) {
        int oldWidth = width, oldHeight = height;
        ProjectorReadingLayout oldReading = foregroundReadingLayout;
        try {
            foregroundReadingLayout = reading;
            if (reading != null) { width = reading.width; height = reading.height; }
            render.run();
        } finally {
            width = oldWidth; height = oldHeight;
            foregroundReadingLayout = oldReading;
        }
    }

    private void prepareExternalContext(PonderScene scene) {
        mc = Minecraft.getMinecraft();
        fontRenderer = mc.fontRenderer;
        itemRender = mc.getRenderItem();
        ScaledResolution resolution = new ScaledResolution(mc);
        width = resolution.getScaledWidth(); height = resolution.getScaledHeight();
        if (externalScene != scene) {
            invalidateExternalPreview();
            externalScene = scene;
        }
    }

    public void invalidateExternalPreview() {
        PreviewModelFraming framing = modelFraming.copy();
        clearPreviewWorld();
        modelFraming.copyFrom(framing);
    }

    public boolean externalCalloutClick(int x, int y, int button) { return handleCalloutClick(x, y, button); }
    public boolean externalCalloutWheel(int wheel, int x, int y) { return handleCalloutWheel(wheel, x, y); }
    public boolean externalCalloutContains(int x, int y) { return calloutAt(x, y) != null; }
    public void invalidateExternalCallouts() { invalidateCalloutHits(); overlayHitAreas.clear(); }

    private PonderGuiSnapshotRegistry.Snapshot resolveSnapshot(PonderScene.OverlayEvent event, float tick) {
        PonderGuiSnapshotRegistry.SnapshotProvider provider = PonderGuiSnapshotRegistry.providerFor(event);
        if (provider != null) usedSnapshotProviders.add(provider);
        return PonderGuiSnapshotRegistry.resolve(event, tick);
    }

    private void releaseSnapshotProviders() {
        for (PonderGuiSnapshotRegistry.SnapshotProvider provider : usedSnapshotProviders)
            PonderGuiSnapshotRegistry.releaseProvider(provider);
        usedSnapshotProviders.clear();
    }

    public int getWorldParticleSampleCount() { return worldParticleSamples; }

    private void renderWorldParticles(PonderScene scene, float tick) {
        List<PreviewParticleTimeline.Sample> samples = new ArrayList<>(PreviewParticleTimeline.evaluate(scene, tick, 1F));
        worldParticleSamples = samples.size();
        if (samples.isEmpty()) return;
        samples.sort((a, b) -> Double.compare(projectionViewer.squareDistanceTo(b.position), projectionViewer.squareDistanceTo(a.position)));
        PreviewRenderState previous = new PreviewRenderState();
        try {
            GlStateManager.disableTexture2D(); GlStateManager.disableLighting();
            GlStateManager.disableCull(); GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            GlStateManager.enableDepth(); GlStateManager.depthFunc(GL11.GL_LEQUAL); GlStateManager.depthMask(false);
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0F);
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (PreviewParticleTimeline.Sample sample : samples) {
                Vec3d normal = projectionViewer.subtract(sample.position).normalize();
                if (normal.lengthSquared() < 1E-8D) normal = new Vec3d(0, 0, 1);
                Vec3d right = new Vec3d(0, 1, 0).crossProduct(normal).normalize();
                if (right.lengthSquared() < 1E-8D) right = new Vec3d(1, 0, 0);
                double radius = 0.06D * sample.radius;
                Vec3d up = normal.crossProduct(right).normalize().scale(radius);
                right = right.scale(radius);
                for (int corner : new int[] {0, 1, 3, 2}) {
                    Vec3d point = sample.position.add(right.scale((corner & 1) == 0 ? -1 : 1))
                        .add(up.scale((corner & 2) == 0 ? -1 : 1));
                    buffer.pos(point.x, point.y, point.z).color((sample.color >> 16) & 255,
                        (sample.color >> 8) & 255, sample.color & 255, (sample.argb() >>> 24)).endVertex();
                }
            }
            tessellator.draw();
        } finally { previous.restore(); }
    }

    private float computePreviewScale(PonderScene scene, PreviewBounds bounds, PreviewLayout layout) {
        return computePreviewScale(scene, bounds, layout, 0.0F);
    }

    private void sortTranslucentFrame(PreviewFrame frame, PreviewBounds bounds, PonderScene scene, float renderTick) {
        float yaw = getPreviewYaw(scene, renderTick);
        float pitch = getPreviewPitch(scene, renderTick);
        if (frame.sortedYaw == yaw && frame.sortedPitch == pitch) {
            return;
        }
        Collections.sort(frame.translucent, new Comparator<PonderSceneRuntime.RuntimeBlockState>() {
            @Override
            public int compare(PonderSceneRuntime.RuntimeBlockState left, PonderSceneRuntime.RuntimeBlockState right) {
                // The GUI orthographic camera is nearer at larger model-space Z.
                return Double.compare(cameraDepth(left, bounds, scene, renderTick),
                    cameraDepth(right, bounds, scene, renderTick));
            }
        });
        frame.sortedYaw = yaw;
        frame.sortedPitch = pitch;
    }

    private float computePreviewScale(PonderScene scene, PreviewBounds bounds, PreviewLayout layout, float renderTick) {
        return getModelFraming(bounds).fitScale(layout.width, layout.height, showcaseMode, scene.getScaleFactor())
            * previewZoom * getPreviewView(scene, bounds, renderTick).multiplier;
    }

    private PreviewCameraTimeline.View getPreviewView(PonderScene scene, PreviewBounds bounds, float tick) {
        if (previewCameraManual && manualCameraView != null) return manualCameraView;
        PreviewModelFraming framing = getModelFraming(bounds);
        Vec3d defaultCenter = new Vec3d(framing.centerX(), framing.centerY() + scene.getSceneOffsetY(), framing.centerZ());
        if (evaluatedCameraScene != scene || Float.compare(evaluatedCameraTick, tick) != 0
            || !defaultCenter.equals(evaluatedCameraDefault)) {
            evaluatedCameraView = PreviewCameraTimeline.evaluate(scene.getCameraViewEvents(), tick, defaultCenter);
            evaluatedCameraScene = scene;
            evaluatedCameraTick = tick;
            evaluatedCameraDefault = defaultCenter;
        }
        return evaluatedCameraView;
    }

    private double cameraDepth(PonderSceneRuntime.RuntimeBlockState block, PreviewBounds bounds, PonderScene scene,
        float renderTick) {
        Vec3d center = getPreviewView(scene, bounds, renderTick).center;
        Vec3d relative = new Vec3d(block.renderCenterX - center.x, block.renderCenterY - center.y,
            block.renderCenterZ - center.z);
        Vec3d yawRotated = rotateY(relative, getPreviewYaw(scene, renderTick));
        Vec3d rotated = rotateX(yawRotated, getPreviewPitch(scene, renderTick));
        return rotated.z;
    }

    private float computeBlockBrightness(PonderSceneRuntime.RuntimeBlockState block) {
        float base = showcaseMode ? 0.84F : 0.74F;
        float range = showcaseMode ? 0.22F : 0.28F;
        return MathHelper.clamp(base + block.fade * range, 0.68F, 1.06F);
    }

    private void renderBlockModelPreview(BlockRendererDispatcher dispatcher, PreviewBlockAccess previewWorld,
        PonderSceneRuntime.RuntimeBlockState block, IBlockState state, List<BlockRenderLayer> layers,
        boolean translucent) {
        if (state == null || layers == null || layers.isEmpty()) {
            return;
        }

        PreviewBlockAccess modelWorld = previewWorld.forBlock(block);
        IBlockState renderState = state;
        // Preserve authored rail states, including mod rails that resolve their
        // shape from neighbours. The dispatcher must also be bypassed below.
        if (!(state.getBlock() instanceof BlockRailBase)) {
            try {
                renderState = getActualStateCompat(state, modelWorld, block.pos);
            } catch (RuntimeException ignored) {
                renderState = state;
            }
        }

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        try {
            PonderSceneRuntime.applyRenderTransforms(block);
            float brightness = Math.min(1.0F, computeBlockBrightness(block));
            float alpha = PreviewBlockRendering.alpha(block);
            GlStateManager.color(1F, 1F, 1F, 1F);
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F * alpha);
            GlStateManager.enableTexture2D();
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            setPreviewLightmap();

            final IBlockState submittedState = renderState;
            for (BlockRenderLayer layer : layers) {
                if (PreviewBlockRendering.transparent(block, layer) != translucent) {
                    continue;
                }
                try {
                    PreviewBlockRendering.inLayer(layer, () -> blockBuffer.draw(brightness, alpha, buffer -> {
                        if (submittedState.getBlock() instanceof BlockRailBase
                            && submittedState.getRenderType() == EnumBlockRenderType.MODEL) {
                            IBakedModel model = dispatcher.getModelForState(submittedState);
                            IBlockState extended = submittedState.getBlock().getExtendedState(submittedState,
                                modelWorld, block.pos);
                            dispatcher.getBlockModelRenderer().renderModel(modelWorld, model, extended, block.pos,
                                buffer, true);
                        } else {
                            dispatcher.renderBlock(submittedState, block.pos, modelWorld, buffer);
                        }
                    }, this::uploadBlockBuffer));
                } catch (RuntimeException | LinkageError failure) {
                    warnRenderFailure("model:" + renderState.getBlock() + ":" + layer,
                        "Ponder could not render block model " + getBlockId(renderState) + " at " + block.pos
                            + " in " + layer, failure);
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            warnRenderFailure("model:" + renderState.getBlock(),
                "Ponder could not render block model " + getBlockId(renderState) + " at " + block.pos, ignored);
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
        }
    }

    private void uploadBlockBuffer(BufferBuilder buffer) {
        PreviewBlockLighting.runUnlit(() -> PreviewClientArrayGuard.run(() -> blockUploader.draw(buffer)));
    }

    private static Method resolveBlockStateMethod() {
        try {
            Method method = IBlockState.class.getMethod("getActualState", IBlockAccess.class, BlockPos.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
        }

        try {
            Method method = IBlockState.class.getMethod("func_185899_b", IBlockAccess.class, BlockPos.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
        }

        return null;
    }

    private static Method resolveBlockRenderLayerMethod() {
        for (String name : new String[] { "getRenderLayer", "getBlockLayer", "func_180664_k" }) {
            try {
                Method method = Block.class.getMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static boolean isTranslucentPreviewBlock(IBlockState state) {
        if (state == null || state.getBlock() == null) {
            return false;
        }
        if (BLOCK_RENDER_LAYER != null) {
            try {
                Object layer = BLOCK_RENDER_LAYER.invoke(state.getBlock());
                if (layer != null && "TRANSLUCENT".equalsIgnoreCase(layer.toString())) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        String id = getBlockId(state).toLowerCase(Locale.ROOT);
        return id.contains("glass") || id.contains("water") || id.contains("ice") || id.contains("slime")
            || id.contains("portal");
    }

    private static boolean isTranslucentTilePreview(PonderSceneRuntime.RuntimeBlockState block) {
        return block.fade < 1F || isTranslucentPreviewBlock(block.currentState);
    }

    private static IBlockState getActualStateCompat(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (state == null || BLOCKSTATE_ACTUAL_STATE == null) {
            return state;
        }

        try {
            Object resolved = BLOCKSTATE_ACTUAL_STATE.invoke(state, world, pos);
            return resolved instanceof IBlockState ? (IBlockState) resolved : state;
        } catch (Exception ignored) {
            return state;
        }
    }

    @SuppressWarnings("unchecked")
    private void renderTileEntityPreview(PonderSceneRuntime.RuntimeBlockState block, IBlockState state) {
        if (state == null || detachedPreviewWorld == null || !state.getBlock().hasTileEntity(state)) {
            return;
        }

        TileEntity tileEntity = getOrCreatePreviewTileEntity(block, state);
        if (tileEntity == null) {
            return;
        }

        try {
            TileEntitySpecialRenderer<TileEntity> renderer = TileEntityRendererDispatcher.instance.getRenderer(tileEntity);
            if (renderer == null) {
                return;
            }

            PreviewBlockRendering.inLayer(null, () -> PreviewNativeRenderGuard.run(() -> {
                PonderSceneRuntime.applyRenderTransforms(block);
                GlStateManager.translate(Vec3iAccessor.x(block.pos), Vec3iAccessor.y(block.pos),
                    Vec3iAccessor.z(block.pos));
                setPreviewLightmap();
                float alpha = PreviewBlockRendering.alpha(block);
                GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F * alpha);
                GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
                renderer.render(tileEntity, 0.0D, 0.0D, 0.0D, 0.0F, -1, alpha);
            }));
        } catch (RuntimeException | LinkageError ignored) {
            PreviewBlockBuffer.discard(Tessellator.getInstance().getBuffer());
            warnRenderFailure("tile-render:" + state.getBlock(),
                "Ponder TileEntity renderer failed for " + getBlockId(state) + " at " + block.pos, ignored);
        }
    }

    private TileEntity getOrCreatePreviewTileEntity(PonderSceneRuntime.RuntimeBlockState block, IBlockState state) {
        return detachedPreviewWorld == null ? null : detachedPreviewWorld.getTileEntity(block.pos);
    }

    private void updatePreviewWorld(PonderSceneRuntime.RuntimeState state) {
        if (detachedPreviewWorld == null) {
            detachedPreviewWorld = new DetachedPonderWorld();
        }
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<BlockPos, IBlockState>();
        Map<BlockPos, NBTTagCompound> nbt = new LinkedHashMap<BlockPos, NBTTagCompound>();
        for (PonderSceneRuntime.RuntimeBlockState block : state.blocksByPosition.values()) {
            if (!block.visible || block.currentState == null || block.currentState.getBlock() == Blocks.AIR) {
                continue;
            }
            blocks.put(block.pos, block.currentState);
            if (block.tileNbt != null && !block.tileNbt.isEmpty()) {
                NBTTagCompound parsed = previewNbtCache.get(block.tileNbt);
                if (parsed == null) {
                    try {
                        parsed = JsonToNBT.getTagFromJson(block.tileNbt);
                    } catch (NBTException invalid) {
                        warnRenderFailure("tile-nbt:" + block.tileNbt,
                            "Ponder ignored invalid TileEntity NBT at " + block.pos, invalid);
                        parsed = new NBTTagCompound();
                    }
                    if (previewNbtCache.size() >= 256) {
                        previewNbtCache.clear();
                    }
                    previewNbtCache.put(block.tileNbt, parsed);
                }
                nbt.put(block.pos, parsed);
            }
        }
        detachedPreviewWorld.setBlocks(blocks, nbt);
    }

    private void clearPreviewWorld() {
        previewAudio.close();
        evaluatedCameraScene = null;
        evaluatedCameraView = null;
        evaluatedCameraDefault = null;
        evaluatedCameraTick = Float.NaN;
        releaseSnapshotProviders();
        externalScene = null;
        externalOverlayArea = null;
        worldProjection = null;
        worldParticleSamples = 0;
        previewRenderFailureKeys.clear();
        playerRenderer.clear();
        outlineRenderer.clear();
        calloutViews.clear();
        calloutGroups = Collections.emptyList();
        calloutKeys = Collections.emptyList();
        selectedCallout = null;
        focusedCallout = null;
        invalidateCalloutHits();
        pointerInput.cancelDrag();
        overlayHitAreas.clear();
        lastSceneBounds = null;
        modelMode.clear();
        modelFraming.clear();
        modelRenderer.clear();
        blockBuffer.clear();
        modelControls = null;
        modelResourceGeneration = -1;
        modelIndexGeneration = -1;
        modelNotice = false;
        if (detachedPreviewWorld != null) {
            detachedPreviewWorld.clear();
            detachedPreviewWorld = null;
        }
        previewNbtCache.clear();
        previewFrames.clear();
        appliedWorldFrame = null;
        sceneEndTicks.clear();
        captionPaging.clear();
        captionText = null;
        captionLines = Collections.emptyList();
        explanationLayout = null;
        explanationIdentity = Collections.emptyList();
        explanationImages = Collections.emptyList();
        clearExplanationHover();
        captionBounds = null;
        captionPrevious = null;
        captionNext = null;
        captionFocused = false;
        summaryScroll = 0;
    }

    private static String getBlockId(IBlockState state) {
        if (state == null || state.getBlock() == null) {
            return "<unknown>";
        }
        ResourceLocation id = Block.REGISTRY.getNameForObject(state.getBlock());
        return id == null ? state.getBlock().getClass().getName() : id.toString();
    }

    protected final List<String> getPreviewRenderFailureKeys() {
        return Collections.unmodifiableList(new ArrayList<>(previewRenderFailureKeys));
    }

    private void warnRenderFailure(String key, String message, Throwable cause) {
        // Log deduplication must not hide the same failure in a later scene.
        if (previewRenderFailureKeys.size() < 128 || previewRenderFailureKeys.contains(key)) {
            previewRenderFailureKeys.add(key);
        } else {
            previewRenderFailureKeys.add("additional-render-failures-truncated");
        }
        if (!WARNED_RENDER_FAILURES.add(key)) {
            return;
        }
        Ponder.LOGGER.warn(message, cause);
    }

    private void setPreviewLightmap() {
        int sky = PREVIEW_FULL_BRIGHT & 0xFFFF;
        int block = PREVIEW_FULL_BRIGHT >> 16 & 0xFFFF;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, sky, block);
    }

    private static final class PreviewFrame {
        final PonderSceneRuntime.RuntimeState state;
        final List<PonderSceneRuntime.RuntimeBlockState> solid;
        final List<PonderSceneRuntime.RuntimeBlockState> translucent;
        final Map<PonderSceneRuntime.RuntimeBlockState, List<BlockRenderLayer>> layers;
        float sortedYaw = Float.NaN;
        float sortedPitch = Float.NaN;
        boolean calloutModelBoundsComputed;
        AxisAlignedBB calloutModelBounds;

        PreviewFrame(PonderSceneRuntime.RuntimeState state, List<PonderSceneRuntime.RuntimeBlockState> solid,
            List<PonderSceneRuntime.RuntimeBlockState> translucent,
            Map<PonderSceneRuntime.RuntimeBlockState, List<BlockRenderLayer>> layers) {
            this.state = state;
            this.solid = solid;
            this.translucent = translucent;
            this.layers = layers;
        }
    }

    /** A return bookmark contains no compiled scene, World, Tile or mesh reference. */
    private static final class RetainedSceneView {
        final long generation;
        final ResourceLocation component;
        final ResourceLocation scene;
        final PreviewBounds bounds;
        final float authoredScale;
        final float offsetY;
        final int componentIndex;
        final int sceneIndex;
        final int tick;
        final boolean playing;
        final int operationScroll;
        final int summaryScroll;
        final int componentScroll;
        final ResourceLocation componentAnchor;
        final PreviewModelFraming framing;

        RetainedSceneView(long generation, ResourceLocation component, PonderScene source, PreviewBounds bounds,
            int componentIndex, int sceneIndex, int tick, boolean playing, int operationScroll, int summaryScroll,
            int componentScroll, ResourceLocation componentAnchor, PreviewModelFraming framing) {
            this.generation = generation;
            this.component = component;
            this.scene = source == null ? null : source.getSceneId();
            this.bounds = bounds;
            this.authoredScale = source == null ? 1F : source.getScaleFactor();
            this.offsetY = source == null ? 0F : source.getSceneOffsetY();
            this.componentIndex = componentIndex;
            this.sceneIndex = sceneIndex;
            this.tick = tick;
            this.playing = playing;
            this.operationScroll = operationScroll;
            this.summaryScroll = summaryScroll;
            this.componentScroll = componentScroll;
            this.componentAnchor = componentAnchor;
            this.framing = framing;
        }

        boolean matches(PonderScene source, PreviewBounds next) {
            return bounds != null && next != null && bounds.minX == next.minX && bounds.maxX == next.maxX
                && bounds.minY == next.minY && bounds.maxY == next.maxY && bounds.minZ == next.minZ
                && bounds.maxZ == next.maxZ && Float.compare(authoredScale, source.getScaleFactor()) == 0
                && Float.compare(offsetY, source.getSceneOffsetY()) == 0;
        }
    }

    private final class PreviewBlockAccess implements IBlockAccess {

        private final PonderSceneRuntime.RuntimeState runtimeState;
        private final PonderSceneRuntime.RuntimeBlockState renderingBlock;

        private PreviewBlockAccess(PonderSceneRuntime.RuntimeState runtimeState) {
            this(runtimeState, null);
        }

        private PreviewBlockAccess(PonderSceneRuntime.RuntimeState runtimeState,
            PonderSceneRuntime.RuntimeBlockState renderingBlock) {
            this.runtimeState = runtimeState;
            this.renderingBlock = renderingBlock;
        }

        private PreviewBlockAccess forBlock(PonderSceneRuntime.RuntimeBlockState block) {
            return new PreviewBlockAccess(runtimeState, block);
        }

        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            PonderSceneRuntime.RuntimeBlockState block = runtimeState.blocksByPosition.get(pos);
            if (block == null || block.currentState == null || getBlockState(pos).getBlock() == Blocks.AIR) {
                return null;
            }
            if (!block.currentState.getBlock().hasTileEntity(block.currentState)
                && (block.tileNbt == null || block.tileNbt.isEmpty())) {
                return null;
            }
            return getOrCreatePreviewTileEntity(block, block.currentState);
        }

        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) {
            return PREVIEW_FULL_BRIGHT;
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return PreviewBlockRendering.neighborState(runtimeState, renderingBlock, pos);
        }

        @Override
        public boolean isAirBlock(BlockPos pos) {
            return getBlockState(pos).getBlock() == Blocks.AIR;
        }

        @Override
        public Biome getBiome(BlockPos pos) {
            return Biomes.PLAINS;
        }

        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return getBlockState(pos).getStrongPower(this, pos, direction);
        }

        @Override
        public WorldType getWorldType() {
            return WorldType.FLAT;
        }

        @Override
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
            IBlockState state = getBlockState(pos);
            if (state == null || state.getBlock() == Blocks.AIR) {
                return false;
            }
            return state.isSideSolid(this, pos, side);
        }
    }

    private void drawSceneShadow(PonderScene scene, PreviewBounds bounds) {
        // The showcase is rendered over a transparent UI layer. A world-space
        // quad here becomes an opaque black occluder with several 1.12.2
        // renderers, so platform shadows are intentionally disabled globally.
    }

    private void enablePreviewScissor(PreviewLayout layout) {
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        try {
            setPreviewScissor(layout);
        } catch (RuntimeException | Error failure) {
            GL11.glPopAttrib();
            throw failure;
        }
    }

    private void setPreviewScissor(PreviewLayout layout) {
        if (foregroundReadingLayout != null) {
            int[] box = foregroundReadingLayout.framebufferScissor(
                new PreviewDebugLayout.Rect(layout.originX, layout.originY, layout.width, layout.height));
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(box[0], box[1], box[2], box[3]);
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        int scaleFactor = resolution.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(layout.originX * scaleFactor,
            mc.displayHeight - (layout.originY + layout.height) * scaleFactor, layout.width * scaleFactor,
            layout.height * scaleFactor);
    }

    private void disablePreviewScissor() {
        GL11.glPopAttrib();
    }

    private static final class ShowcaseCaption {
        private final PonderScene.OverlayEvent overlayEvent;

        private ShowcaseCaption(PonderScene.OverlayEvent overlayEvent) {
            this.overlayEvent = overlayEvent;
        }
    }

    protected static final class ShowcaseGroupState {
        protected final PonderTag tag;
        protected final List<ResourceLocation> components;

        private ShowcaseGroupState(PonderTag tag, List<ResourceLocation> components) {
            this.tag = tag;
            this.components = components;
        }
    }

    protected static final class ShowcaseGroupIcon {
        protected final ResourceLocation componentId;
        protected final PonderTag tag;
        protected final int x;
        protected final int y;
        protected final int width;
        protected final int height;

        private ShowcaseGroupIcon(ResourceLocation componentId, PonderTag tag, int x, int y, int width, int height) {
            this.componentId = componentId;
            this.tag = tag;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static final class Point {
        private final int x;
        private final int y;

        private Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class CaptionPlacement {
        private final int x;
        private final int y;
        private final SpeechPointing pointing;

        private CaptionPlacement(int x, int y, SpeechPointing pointing) {
            this.x = x;
            this.y = y;
            this.pointing = pointing;
        }
    }

    private static final class GuiOverlayPlacement {
        private final PonderScene.OverlayEvent overlayEvent;
        private final Point targetPoint;
        private final boolean aboveTarget;
        private final int panelX;
        private final int panelY;
        private final int panelWidth;
        private final int panelHeight;
        private final int drawX;
        private final int drawY;
        private final int drawWidth;
        private final int drawHeight;

        private GuiOverlayPlacement(PonderScene.OverlayEvent overlayEvent, Point targetPoint, boolean aboveTarget,
            int panelX, int panelY, int panelWidth, int panelHeight, int drawX, int drawY, int drawWidth,
            int drawHeight) {
            this.overlayEvent = overlayEvent;
            this.targetPoint = targetPoint;
            this.aboveTarget = aboveTarget;
            this.panelX = panelX;
            this.panelY = panelY;
            this.panelWidth = panelWidth;
            this.panelHeight = panelHeight;
            this.drawX = drawX;
            this.drawY = drawY;
            this.drawWidth = drawWidth;
            this.drawHeight = drawHeight;
        }
    }

    private static final class GuiHighlightPlacement {
        private final PonderScene.OverlayEvent overlayEvent;
        private final int rectX;
        private final int rectY;
        private final int rectWidth;
        private final int rectHeight;
        private final Point targetPoint;

        private GuiHighlightPlacement(PonderScene.OverlayEvent overlayEvent, int rectX, int rectY, int rectWidth,
            int rectHeight, Point targetPoint) {
            this.overlayEvent = overlayEvent;
            this.rectX = rectX;
            this.rectY = rectY;
            this.rectWidth = rectWidth;
            this.rectHeight = rectHeight;
            this.targetPoint = targetPoint;
        }
    }

    protected static final class PreviewLayout {
        protected final int originX;
        protected final int originY;
        protected final int width;
        protected final int height;

        private PreviewLayout(int originX, int originY, int width, int height) {
            this.originX = originX;
            this.originY = originY;
            this.width = width;
            this.height = height;
        }
    }

    private static final class ProjectedBounds {
        private final int minX;
        private final int minY;
        private final int maxX;
        private final int maxY;

        private ProjectedBounds(int minX, int minY, int maxX, int maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }
    }

    private static final class ActorRuntimeState {
        private final int actorId;
        private final PonderScene.ActorKind kind;
        private Vec3d position;
        private Vec3d rotation = Vec3d.ZERO;
        private float cartYaw;
        private float fade = 1.0F;
        private boolean visible = true;
        private String poseName;
        private String displayName;
        private ItemStack itemStack = ItemStack.EMPTY;

        private ActorRuntimeState(int actorId, PonderScene.ActorKind kind, Vec3d position) {
            this.actorId = actorId;
            this.kind = kind;
            this.position = position;
        }
    }

    private enum SpeechPointing {
        NONE,
        DOWN,
        LEFT,
        RIGHT,
        UP
    }

}
