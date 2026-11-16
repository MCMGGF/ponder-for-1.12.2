package net.createmod.ponder.compat.ftbquests;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.IdentityHashMap;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.registration.SceneRegistryAccess;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.compat.ftbquests.FtbQuestReflection.FooterState;
import net.createmod.ponder.compat.ftbquests.FtbQuestReflection.ReturnDestination;
import net.createmod.ponder.compat.ftbquests.FtbQuestReflection.ReturnView;
import net.createmod.ponder.compat.ftbquests.FtbQuestReflection.TaskItems;
import net.createmod.ponder.compat.ftbquests.FtbQuestReflection.ViewedQuest;
import net.createmod.ponder.compat.ftbquests.QuestSceneResolver.Failure;
import net.createmod.ponder.compat.ftbquests.QuestSceneResolver.Result;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.createmod.ponder.foundation.ui.PonderOwnedChildScreen;
import net.createmod.ponder.foundation.ui.PonderScreenOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Adds a read-only scene action to the existing quest-detail panel.
 * Registration must only happen from the client proxy.
 */
public final class FtbQuestsCompat {

    private static final int MAX_CONFIG_BYTES = 2 * 1024 * 1024;
    private static FtbQuestsCompat instance;
    private final FtbQuestReflection reflection;
    private final File configFile;
    private final SceneOpener opener;
    private QuestSceneLinks links = QuestSceneLinks.defaults();
    private boolean failed;
    private long configModified = Long.MIN_VALUE;
    private long configLength = Long.MIN_VALUE;
    private GuiScreen lastScreen;
    private Object lastQuest;
    private Object lastContent;
    private FooterState footerState;
    private GuiScreen frameScreen;
    private ViewedQuest preparedFrame;
    private ViewedQuest drawnFrame;
    private List<QuestSceneLinks.Target> targets = Collections.emptyList();
    private final Map<String, List<ItemStack>> automaticItemStacks = new LinkedHashMap<>();
    private int selectedTarget;
    private QuestFooterLayout layout;
    private GuiButton viewButton;
    private GuiButton previousButton;
    private GuiButton nextButton;
    private List<String> hoveredHints = Collections.emptyList();
    private Map<String, Integer> sceneCounts = Collections.emptyMap();
    private final Map<QuestSceneLinks.Target, Failure> targetFailures = new LinkedHashMap<>();
    private String questId = "";
    private long sceneGeneration = -1L;
    private QuestSceneLinks.Target pendingTarget;
    private GuiScreen pendingParent;
    private Object pendingQuest;
    private QuestSceneLinks pendingLinks;
    private long pendingGeneration;
    private ReturnView returnView;
    private boolean captureNextFooterState = true;
    private Map<String, Object> footerRenderDiagnostics = Collections.emptyMap();

    FtbQuestsCompat(FtbQuestReflection reflection, File configFile, SceneOpener opener) {
        this.reflection = reflection;
        this.configFile = configFile;
        this.opener = opener;
    }

    public static void register() {
        register(null);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pendingTarget == null) {
            return;
        }
        QuestSceneLinks.Target target = pendingTarget;
        GuiScreen parent = pendingParent;
        Object quest = pendingQuest;
        QuestSceneLinks config = pendingLinks;
        long generation = pendingGeneration;
        pendingTarget = null;
        pendingParent = null;
        pendingQuest = null;
        pendingLinks = null;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (failed || minecraft.player == null || minecraft.currentScreen != parent || config != links) {
            return;
        }
        try {
            ViewedQuest current = reflection.inspect(parent);
            if (current == null || current.quest != quest || !updateView(parent) || !targets.contains(target)) {
                return;
            }
            if (generation != PonderIndex.getGeneration()) {
                Ponder.LOGGER.info("Cancelled stale pending quest scene for {}; the refreshed entry can be retried",
                    target.component);
                return;
            }
            openScene(target, parent);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            disable(exception);
        }
    }

    public static void register(SceneOpener opener) {
        if (instance != null || !Loader.isModLoaded("ftbquests") || !Loader.isModLoaded("ftblib")) {
            return;
        }
        try {
            FtbQuestReflection reflection = new FtbQuestReflection(FtbQuestsCompat.class.getClassLoader());
            File config = new File(Loader.instance().getConfigDir(), "ponder/ftbquests-links.json");
            instance = new FtbQuestsCompat(reflection, config, opener);
            instance.reloadLinksIfChanged();
            MinecraftForge.EVENT_BUS.register(instance);
            Ponder.LOGGER.info("Enabled optional FTB Quests client scene links (read-only task-item matching)");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            Ponder.LOGGER.warn("FTB Quests scene links are unavailable for this FTB Quests/FTBLib API", exception);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGuiOpen(GuiOpenEvent event) {
        if (failed || event.isCanceled()) {
            return;
        }
        boolean requestedReturn = returnView != null && event.getGui() == returnView.screen;
        try {
            if (returnView != null) {
                if (requestedReturn) {
                    ReturnDestination destination = reflection.prepareReturn(returnView);
                    event.setGui(destination.screen);
                    returnView = destination.view;
                    if (returnView != null) {
                        returnView.returning = true;
                    }
                } else if (event.getGui() instanceof PonderUI) {
                    returnView.viewerScreen = event.getGui();
                } else if (!isOwnedPonderChild(event.getGui(), returnView.viewerScreen, returnView.returning)) {
                    returnView = null;
                }
            }
            if (event.getGui() != lastScreen) {
                clearView();
                reloadLinksIfChanged();
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            if (requestedReturn) {
                event.setGui(null);
            }
            disable(exception);
        }
    }

    private static boolean isOwnedPonderChild(GuiScreen screen, GuiScreen viewer, boolean returning) {
        return !returning && viewer != null && screen instanceof PonderOwnedChildScreen
            && ((PonderOwnedChildScreen) screen).getPonderParent() == viewer;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        updateView(event.getGui());
        restoreQuestView(event.getGui(), false);
    }

    @SubscribeEvent
    public void onDrawPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        boolean visible = updateView(event.getGui());
        restoreQuestView(event.getGui(), false);
        if (visible) {
            prepareFooterFrame(event.getGui());
        } else {
            resetFooterFrame();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrawPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        // A deferred FTB rebuild has already drawn unreserved geometry. Wait for the next Pre.
        if (!footerFrameMatches(event.getGui())) {
            restoreQuestView(event.getGui(), true);
            resetFooterFrame();
            return;
        }
        boolean visible = updateView(event.getGui());
        restoreQuestView(event.getGui(), true);
        if (!visible || layout == null || viewButton == null) {
            resetFooterFrame();
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        try {
            drawFooterOverlay(event, minecraft);
        } catch (RuntimeException | LinkageError failure) {
            disable(failure);
            return;
        }
        drawnFrame = preparedFrame;
        preparedFrame = null;
    }

    private void drawFooterOverlay(GuiScreenEvent.DrawScreenEvent.Post event, Minecraft minecraft) {
        boolean diagnose = captureNextFooterState;
        Map<String, Object> diagnostics = new LinkedHashMap<String, Object>();
        if (diagnose) {
            diagnostics.put("before", PonderScreenOverlay.snapshot());
            diagnostics.put("logicalWidth", event.getGui().width);
            diagnostics.put("logicalHeight", event.getGui().height);
            diagnostics.put("displayWidth", minecraft.displayWidth);
            diagnostics.put("displayHeight", minecraft.displayHeight);
            diagnostics.put("footer", footerRectangle(layout.x, layout.y, layout.width, layout.height));
            diagnostics.put("button", footerRectangle(viewButton.x, viewButton.y, viewButton.width, viewButton.height));
            diagnostics.put("buttonLabel", viewButton.displayString);
        }
        PonderScreenOverlay.run(event.getGui().width, event.getGui().height,
            minecraft.displayWidth, minecraft.displayHeight, () -> {
                if (diagnose) diagnostics.put("foreground", PonderScreenOverlay.snapshot());
                if (previousButton != null) {
                    previousButton.drawButton(minecraft, event.getMouseX(), event.getMouseY(), event.getRenderPartialTicks());
                    nextButton.drawButton(minecraft, event.getMouseX(), event.getMouseY(), event.getRenderPartialTicks());
                }
                viewButton.drawButton(minecraft, event.getMouseX(), event.getMouseY(), event.getRenderPartialTicks());
                if (layout.contains(event.getMouseX(), event.getMouseY())) {
                    GuiUtils.drawHoveringText(hoveredHints, event.getMouseX(), event.getMouseY(),
                        event.getGui().width, event.getGui().height,
                        Math.min(320, event.getGui().width - 16), minecraft.fontRenderer);
                }
            });
        if (diagnose) {
            diagnostics.put("after", PonderScreenOverlay.snapshot());
            diagnostics.put("drawSubmitted", true);
            diagnostics.put("pixelsVerified", false);
            footerRenderDiagnostics = Collections.unmodifiableMap(diagnostics);
            captureNextFooterState = false;
        }
    }

    private static Map<String, Integer> footerRectangle(int x, int y, int width, int height) {
        Map<String, Integer> result = new LinkedHashMap<String, Integer>();
        result.put("x", x);
        result.put("y", y);
        result.put("width", width);
        result.put("height", height);
        return Collections.unmodifiableMap(result);
    }

    Map<String, Object> getFooterRenderDiagnostics() {
        return footerRenderDiagnostics;
    }

    void requestFooterRenderDiagnostics() {
        captureNextFooterState = true;
    }

    void prepareFooterFrame(GuiScreen screen) {
        try {
            frameScreen = screen;
            preparedFrame = reflection.inspect(screen);
            drawnFrame = null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            disable(exception);
        }
    }

    boolean footerFrameMatches(GuiScreen screen) {
        try {
            return frameScreen == screen && preparedFrame != null
                && preparedFrame.sameGeometry(reflection.inspect(screen));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            disable(exception);
            return false;
        }
    }

    private void resetFooterFrame() {
        frameScreen = null;
        preparedFrame = null;
        drawnFrame = null;
    }

    private boolean hasDrawnFooter(GuiScreen screen) {
        try {
            return frameScreen == screen && drawnFrame != null && drawnFrame.sameGeometry(reflection.inspect(screen));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            disable(exception);
            return false;
        }
    }

    boolean footerInputReady(GuiScreen screen) {
        return hasDrawnFooter(screen) && updateView(screen) && layout != null && hasDrawnFooter(screen);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMousePre(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (Mouse.getEventDWheel() != 0 || (Mouse.getEventButton() >= 0 && Mouse.getEventButtonState())) {
            stopRestoringForInput(event.getGui());
        }
        if (!Mouse.getEventButtonState() || Mouse.getEventButton() != 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen screen = event.getGui();
        int mouseX = Mouse.getEventX() * screen.width / Math.max(1, minecraft.displayWidth);
        int mouseY = screen.height - Mouse.getEventY() * screen.height / Math.max(1, minecraft.displayHeight) - 1;
        if (handleFooterClick(screen, mouseX, mouseY)) {
            event.setCanceled(true);
        }
    }

    boolean handleFooterClick(GuiScreen screen, int mouseX, int mouseY) {
        stopRestoringForInput(screen);
        if (!footerInputReady(screen) || !layout.contains(mouseX, mouseY)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (previousButton != null && previousButton.mousePressed(minecraft, mouseX, mouseY)) {
            previousButton.playPressSound(minecraft.getSoundHandler());
            selectedTarget = (selectedTarget + targets.size() - 1) % targets.size();
            rebuildButtons();
        } else if (nextButton != null && nextButton.mousePressed(minecraft, mouseX, mouseY)) {
            nextButton.playPressSound(minecraft.getSoundHandler());
            selectedTarget = (selectedTarget + 1) % targets.size();
            rebuildButtons();
        } else if (viewButton != null && viewButton.mousePressed(minecraft, mouseX, mouseY)) {
            QuestSceneLinks.Target target = targets.get(selectedTarget);
            if (targetFailure(target) == null) {
                viewButton.playPressSound(minecraft.getSoundHandler());
                if (opener == null) {
                    pendingTarget = target;
                    pendingParent = screen;
                    pendingQuest = lastQuest;
                    pendingLinks = links;
                    pendingGeneration = PonderIndex.getGeneration();
                } else {
                    openScene(target, screen);
                }
            }
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyboardPre(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (Keyboard.getEventKeyState()) {
            stopRestoringForInput(event.getGui());
        }
    }

    private void openScene(QuestSceneLinks.Target target, GuiScreen screen) {
        SceneRegistryAccess registry = PonderIndex.getSceneAccess();
        long generation = PonderIndex.getGeneration();
        Failure metadataFailure = QuestSceneResolver.metadataFailure(target, registeredSceneCounts().get(target.component));
        if (metadataFailure != null) {
            unavailable(target, metadataFailure, null);
            return;
        }
        Result resolved;
        List<PonderScene> compiledScenes;
        List<ItemStack> sourceStacks = target.isAutomatic() ? copyStacks(automaticItemStacks.get(target.component)) : null;
        try {
            compiledScenes = compileTargetScenes(registry, target, sourceStacks);
            resolved = QuestSceneResolver.resolveCompiled(target, compiledScenes);
            if (generation != PonderIndex.getGeneration() || registry != PonderIndex.getSceneAccess()) {
                resolved = Result.failed(Failure.INDEX_CHANGED);
            }
        } catch (RuntimeException | LinkageError exception) {
            unavailable(target, Failure.COMPILE_FAILED, exception);
            return;
        }
        if (!resolved.isAvailable()) {
            unavailable(target, resolved.failure, null);
            return;
        }
        try {
            returnView = reflection.captureReturnView(screen);
            if (returnView == null) {
                return;
            }
            Ponder.LOGGER.info("Opening quest {} component {} scene ID {} from read-only FTB Quests action",
                questId, target.component, resolved.sceneId);
            if (opener == null) {
                Minecraft.getMinecraft().displayGuiScreen(
                    new QuestPonderUI(new ResourceLocation(target.component), resolved.sceneId, compiledScenes,
                        generation, screen, sourceStacks));
            } else {
                opener.open(new ResourceLocation(target.component), resolved.index, screen);
            }
        } catch (ReflectiveOperationException exception) {
            disable(exception);
        } catch (RuntimeException | LinkageError exception) {
            returnView = null;
            unavailable(target, Failure.COMPILE_FAILED, exception);
        }
    }

    private void unavailable(QuestSceneLinks.Target target, Failure reason, Throwable cause) {
        if (!targets.contains(target)) {
            return;
        }
        targetFailures.put(target, reason);
        Ponder.LOGGER.warn("Quest {} cannot open component {} scene {}: {}", questId, target.component,
            target.stableSceneId() == null ? "#" + target.scene : target.stableSceneId(), reason, cause);
        if (layout != null) {
            rebuildButtons();
        }
    }

    private void restoreQuestView(GuiScreen screen, boolean afterDraw) {
        if (failed || returnView == null || !returnView.returning || returnView.screen != screen) {
            return;
        }
        try {
            reflection.restoreReturnView(screen, returnView);
            if (returnView.cancelled || (afterDraw && returnView.finishDraw())) {
                returnView = null;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            disable(exception);
        }
    }

    private void stopRestoringForInput(GuiScreen screen) {
        if (returnView != null && returnView.returning && returnView.screen == screen) {
            updateView(screen);
            restoreQuestView(screen, false);
            returnView = null;
        }
    }

    private boolean updateView(GuiScreen screen) {
        if (failed) {
            return false;
        }
        try {
            if (!links.enabled) {
                clearView();
                return false;
            }
            ViewedQuest view = reflection.inspect(screen);
            if (view == null) {
                clearView();
                return false;
            }
            if (lastScreen != screen || lastQuest != view.quest || lastContent != view.content
                || sceneGeneration != PonderIndex.getGeneration()) {
                clearView();
                view = reflection.inspect(screen);
                if (view == null) {
                    return false;
                }
                lastScreen = screen;
                lastQuest = view.quest;
                lastContent = view.content;
                footerState = reflection.captureFooter(view);
                questId = view.id;
                sceneGeneration = PonderIndex.getGeneration();
                targets = resolveTargets(view);
                selectedTarget = 0;
            }
            if (targets.isEmpty()) {
                return false;
            }
            QuestFooterLayout nextLayout = reflection.reserveFooter(view, footerState, screen.width, screen.height);
            if (nextLayout == null) {
                clearView();
                return false;
            }
            boolean moved = layout == null || layout.x != nextLayout.x || layout.y != nextLayout.y
                || layout.width != nextLayout.width;
            layout = nextLayout;
            if (moved || viewButton == null) {
                rebuildButtons();
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            disable(exception);
            return false;
        }
    }

    private List<QuestSceneLinks.Target> resolveTargets(ViewedQuest view) throws ReflectiveOperationException {
        sceneCounts = registeredSceneCounts();
        automaticItemStacks.clear();
        Map<String, List<String>> components = new LinkedHashMap<String, List<String>>();
        for (TaskItems task : reflection.taskItems(view)) {
            List<String> ids = new ArrayList<String>();
            if (links.autoLinkItems) {
                for (ItemStack stack : task.items) {
                    String component = itemComponent(stack);
                    if (component != null && !ids.contains(component)) {
                        ids.add(component);
                    }
                    rememberAutomaticStack(component, stack);
                }
            }
            components.put(task.id, ids);
        }
        ItemStack icon = links.autoLinkItems ? reflection.questIcon(view) : ItemStack.EMPTY;
        String iconComponent = itemComponent(icon);
        rememberAutomaticStack(iconComponent, icon);
        return links.resolve(view.id, components, iconComponent, sceneCounts);
    }

    private void rememberAutomaticStack(String component, ItemStack stack) {
        if (component == null || stack == null || stack.isEmpty()) return;
        List<ItemStack> stacks = automaticItemStacks.computeIfAbsent(component, key -> new ArrayList<>());
        for (ItemStack previous : stacks)
            if (ItemStack.areItemsEqual(previous, stack) && ItemStack.areItemStackTagsEqual(previous, stack)) return;
        stacks.add(stack.copy());
    }

    static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copy = new ArrayList<>();
        if (stacks != null) for (ItemStack stack : stacks)
            if (stack != null && !stack.isEmpty()) copy.add(stack.copy());
        return Collections.unmodifiableList(copy);
    }

    static List<PonderScene> compileTargetScenes(SceneRegistryAccess registry, QuestSceneLinks.Target target,
        List<ItemStack> sourceStacks) {
        ResourceLocation component = new ResourceLocation(target.component);
        if (!target.isAutomatic()) return registry.compile(component);
        if (sourceStacks == null || sourceStacks.isEmpty()) return Collections.emptyList();
        List<ItemStack> eligible = new ArrayList<>();
        for (ItemStack stack : sourceStacks)
            if (component.equals(registry.resolveComponentId(stack))) eligible.add(stack);
        if (eligible.isEmpty()) return Collections.emptyList();
        List<StoryBoardEntry> entries = new ArrayList<>();
        Set<StoryBoardEntry> visited = Collections.newSetFromMap(new IdentityHashMap<StoryBoardEntry, Boolean>());
        for (Map.Entry<ResourceLocation, StoryBoardEntry> registration : registry.getRegisteredEntries()) {
            if (!component.equals(registration.getKey())) continue;
            for (ItemStack stack : eligible) {
                try {
                    if (registration.getValue().matchesStack(stack)) {
                        if (visited.add(registration.getValue())) entries.add(registration.getValue());
                        break;
                    }
                } catch (RuntimeException invalidFilter) {
                    // Automatic links cannot bypass a failing item filter via a manual component fallback.
                }
            }
        }
        return entries.isEmpty() ? Collections.emptyList() : registry.compile(entries);
    }

    private static String itemComponent(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation component = PonderIndex.getSceneAccess().resolveComponentId(stack);
        return component == null ? null : component.toString();
    }

    private Failure targetFailure(QuestSceneLinks.Target target) {
        Failure failure = targetFailures.get(target);
        return failure == null ? QuestSceneResolver.metadataFailure(target, sceneCounts.get(target.component)) : failure;
    }

    private static Map<String, Integer> registeredSceneCounts() {
        SceneRegistryAccess registry = PonderIndex.getSceneAccess();
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (Map.Entry<ResourceLocation, StoryBoardEntry> entry : registry.getRegisteredEntries()) {
            String component = entry.getKey().toString();
            Integer count = counts.get(component);
            counts.put(component, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
        }
        // Exact component IDs avoid ambiguous item aliases selecting a different machine.
        return counts;
    }

    private void rebuildButtons() {
        QuestSceneLinks.Target target = targets.get(selectedTarget);
        ItemStack stack = PonderIndex.getSceneAccess().getDisplayStack(new ResourceLocation(target.component));
        String name = stack.isEmpty() ? target.component : stack.getDisplayName();
        Failure failure = targetFailure(target);
        String label = failure == null ? I18n.format("ponder.ftbquests.view", name)
            : I18n.format("ponder.ftbquests.unavailable", I18n.format(failure.translationKey));
        int arrows = targets.size() > 1 ? 44 : 0;
        int buttonX = layout.x + arrows / 2;
        int buttonWidth = layout.width - arrows;
        Minecraft minecraft = Minecraft.getMinecraft();
        label = minecraft.fontRenderer.trimStringToWidth(label, buttonWidth - 10);
        viewButton = new GuiButton(0, buttonX, layout.y, buttonWidth, layout.height, label);
        viewButton.enabled = failure == null;
        previousButton = targets.size() > 1
            ? new GuiButton(1, layout.x, layout.y, 20, layout.height, "<") : null;
        nextButton = targets.size() > 1
            ? new GuiButton(2, layout.x + layout.width - 20, layout.y, 20, layout.height, ">") : null;
        hoveredHints = new ArrayList<>();
        hoveredHints.add(I18n.format("ponder.ftbquests.hint", name, Integer.valueOf(selectedTarget + 1),
            Integer.valueOf(targets.size())));
        if (failure != null) {
            hoveredHints.add(I18n.format(failure.translationKey));
        }
        hoveredHints.add(I18n.format("ponder.ftbquests.component", target.component));
        if (!target.isAutomatic()) {
            String sceneId = target.stableSceneId();
            hoveredHints.add(I18n.format("ponder.ftbquests.scene_id",
                sceneId == null ? "#" + target.scene : sceneId));
        }
    }

    private void clearView() throws ReflectiveOperationException {
        reflection.restoreFooter(footerState);
        footerState = null;
        resetFooterFrame();
        lastScreen = null;
        lastQuest = null;
        lastContent = null;
        targets = Collections.emptyList();
        automaticItemStacks.clear();
        layout = null;
        viewButton = null;
        previousButton = null;
        nextButton = null;
        hoveredHints = Collections.emptyList();
        sceneCounts = Collections.emptyMap();
        targetFailures.clear();
        sceneGeneration = -1L;
        captureNextFooterState = true;
    }

    private void reloadLinksIfChanged() {
        long modified = configFile.isFile() ? configFile.lastModified() : -1;
        long length = configFile.isFile() ? configFile.length() : -1;
        if (modified == configModified && length == configLength) {
            return;
        }
        configModified = modified;
        configLength = length;
        if (!configFile.isFile()) {
            links = QuestSceneLinks.defaults();
            return;
        }
        try {
            if (length > MAX_CONFIG_BYTES) {
                throw new IOException("Quest scene link file is larger than 2 MiB");
            }
            try (Reader reader = Files.newBufferedReader(configFile.toPath(), StandardCharsets.UTF_8)) {
                links = QuestSceneLinks.parse(reader);
            }
            Ponder.LOGGER.info("Loaded client-only FTB Quests scene links from {}", configFile);
        } catch (IOException | RuntimeException exception) {
            Ponder.LOGGER.warn("Keeping previous FTB Quests scene links after invalid config {}", configFile, exception);
        }
    }

    private void disable(Throwable failure) {
        if (!failed) {
            try {
                clearView();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Preserve the original API failure in the log.
            }
            failed = true;
            pendingTarget = null;
            pendingParent = null;
            pendingQuest = null;
            pendingLinks = null;
            returnView = null;
            Ponder.LOGGER.warn("Disabled only the optional FTB Quests scene button after an API error", failure);
        }
    }

    public interface SceneOpener {
        void open(ResourceLocation component, int scene, GuiScreen parent);
    }
}
