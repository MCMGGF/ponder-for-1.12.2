package net.createmod.ponder.client;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import javax.annotation.Nullable;

import net.createmod.ponder.CommonProxy;
import net.createmod.ponder.PonderBackportMod;
import net.createmod.ponder.authoring.AuthoringPlugin;
import net.createmod.ponder.authoring.AuthoringSettings;
import net.createmod.ponder.authoring.AuthoringWorkspace;
import net.createmod.ponder.authoring.trigger.AuthoringTriggerManager;
import net.createmod.ponder.command.PonderCommand;
import net.createmod.ponder.compat.ftbquests.FtbQuestsCompat;
import net.createmod.ponder.compat.mmce.MmceScriptReloadCompat;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.content.MmcePonderPlugin;
import net.createmod.ponder.foundation.content.mmce.MmceStaticModelPreviews;
import net.createmod.ponder.foundation.ui.JeiScreenCompat;
import net.createmod.ponder.foundation.ui.AuthoringCaptureController;
import net.createmod.ponder.foundation.ui.AuthoringEditorScreen;
import net.createmod.ponder.foundation.ui.PonderDebugScreen;
import net.createmod.ponder.foundation.ui.PonderIndexScreen;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.ICommand;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class ClientProxy extends CommonProxy {

    private static final String KEY_CATEGORY = "key.categories.ponder";
    private static KeyBinding openPonderUiKey;
    private static ResourceLocation pendingComponentId;
    private static ItemStack pendingSourceStack = ItemStack.EMPTY;
    private static int pendingSceneIndex;
    private static boolean pendingDebugScreen;
    private static boolean pendingIndexScreen;
    private static boolean openViewerQueued;
    private static final PendingEditorOpen pendingEditorOpen = new PendingEditorOpen();
    private static boolean tooltipHandlerFailed;
    private static ResourceLocation pendingHintScene;
    private static ItemStack hoveredStack = ItemStack.EMPTY;
    private static ItemStack trackedStack = ItemStack.EMPTY;
    private static ResourceLocation trackedComponentId;
    private static float holdProgress;
    private static long lastFrameTimeMs = -1L;
    private static final boolean DIAGNOSTICS = Boolean.getBoolean("ponder.diagnostics");
    private int compatibilityRetryTicks;
    private boolean firstWorldFrameRecorded;
    private final net.createmod.ponder.foundation.ui.ProjectorSceneRenderer projectorRenderer =
        net.createmod.ponder.foundation.ui.ProjectorSceneRenderer.INSTANCE;
    private net.minecraft.util.math.BlockPos blueprintFirst;
    private Object blueprintWorld;
    private boolean captureRegistered;

    @Override
    public void preInit() {
        try {
            AuthoringSettings.load(Loader.instance().getConfigDir().toPath());
        } catch (IOException invalid) {
            PonderBackportMod.LOGGER.warn("Could not read the local visual authoring preference; using the disabled default", invalid);
        }
        PonderIndex.addPlugin(new AuthoringPlugin());
    }

    @Override
    public void init() {
        JeiScreenCompat.tryInstall();
        registerClientCommand(new PonderCommand());
        registerClientCommand(new PonderClientCommand());
        registerClientCommand(new PonderDebugClientCommand());
        registerClientCommand(new AuthoringClientCommand(this));
        if (openPonderUiKey == null) {
            openPonderUiKey = new KeyBinding("key.ponder.open_ui",
                KeyConflictContext.IN_GAME, Keyboard.KEY_P, KEY_CATEGORY);
            ClientRegistry.registerKeyBinding(openPonderUiKey);
        }
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(projectorRenderer);
        syncAuthoringCapture();
        FtbQuestsCompat.register();
        MmceScriptReloadCompat.register(PonderIndex::reload, () -> PonderIndex.getGeneration() > 0L);
        PonderBackportMod.LOGGER.info("Registered Ponder client commands and Ponder UI key binding");
    }

    private void syncAuthoringCapture() {
        if (AuthoringSettings.isEnabled() && !captureRegistered) {
            MinecraftForge.EVENT_BUS.register(AuthoringCaptureController.INSTANCE);
            captureRegistered = true;
        } else if (!AuthoringSettings.isEnabled() && captureRegistered) {
            MinecraftForge.EVENT_BUS.unregister(AuthoringCaptureController.INSTANCE);
            captureRegistered = false;
        }
    }

    boolean canDisableAuthoring() {
        return !captureRegistered || !AuthoringCaptureController.INSTANCE.isActive();
    }

    boolean changeAuthoring(boolean enabled) throws IOException {
        synchronized (PonderIndex.class) {
            boolean wasEnabled = AuthoringSettings.isEnabled();
            try {
                return AuthoringSettings.change(enabled, PonderIndex::getGeneration, PonderIndex::reload);
            } finally {
                if (wasEnabled != AuthoringSettings.isEnabled()) {
                    syncAuthoringCapture();
                    if (!AuthoringSettings.isEnabled()) {
                        pendingEditorOpen.clear();
                        if (pendingHintScene != null) {
                            pendingHintScene = null;
                            openViewerQueued = false;
                        }
                        AuthoringTriggerManager.disable();
                        blueprintFirst = null;
                        blueprintWorld = null;
                    }
                }
                if (!AuthoringSettings.isEnabled()) AuthoringWorkspace.release();
            }
        }
    }

    @Override
    public void onPonderReload() {
        MmceStaticModelPreviews.getInstance().clear();
        projectorRenderer.clear();
    }

    @Override public void openProjector(net.minecraft.util.math.BlockPos pos, boolean miniature) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.isSingleplayer()) return;
        net.minecraft.tileentity.TileEntity tile = mc.world.getTileEntity(pos);
        if (tile instanceof net.createmod.ponder.authoring.projection.ProjectorTile) {
            mc.displayGuiScreen(new net.createmod.ponder.foundation.ui.ProjectorConfigScreen(pos,
                ((net.createmod.ponder.authoring.projection.ProjectorTile) tile).getSettings()));
        }
    }

    @Override public void selectBlueprintCorner(net.minecraft.util.math.BlockPos pos, boolean second) {
        if (!AuthoringSettings.isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.isSingleplayer() || mc.world == null || mc.player == null) return;
        if (blueprintWorld != mc.world) { blueprintFirst = null; blueprintWorld = mc.world; }
        if (!second || blueprintFirst == null) {
            blueprintFirst = pos.toImmutable();
            mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(
                net.minecraft.client.resources.I18n.format("ponder.authoring.corner", pos.toString())));
            return;
        }
        try {
            net.createmod.ponder.authoring.assets.BlueprintCapture.Result captured =
                net.createmod.ponder.authoring.assets.BlueprintCapture.capture(
                    net.createmod.ponder.authoring.assets.BlueprintCapture.fromLoadedWorld(mc.world, Thread.currentThread()),
                    blueprintFirst, pos);
            String id = "local:blueprint_" + Long.toString(System.currentTimeMillis(), 36);
            AuthoringWorkspace.get().getAssets().saveNew(id, captured.schematic);
            mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(
                net.minecraft.client.resources.I18n.format("ponder.authoring.blueprint_saved", id)));
            blueprintFirst = null;
        } catch (Exception invalid) {
            mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(String.valueOf(invalid.getMessage())));
        }
    }

    private static void registerClientCommand(ICommand command) {
        if (command == null) {
            return;
        }

        ClientCommandHandler handler = ClientCommandHandler.instance;
        if (handler == null) {
            throw new IllegalStateException("ClientCommandHandler.instance is null while registering " + command.getName());
        }

        if (invokeCommandRegistration(handler, "registerCommand", command)) {
            return;
        }
        if (invokeCommandRegistration(handler, "func_71560_a", command)) {
            return;
        }

        throw new IllegalStateException("Unable to register client command " + command.getName());
    }

    private static boolean invokeCommandRegistration(ClientCommandHandler handler, String methodName, ICommand command) {
        Class<?> current = handler.getClass();
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName, ICommand.class);
                method.setAccessible(true);
                method.invoke(handler, command);
                return true;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (Throwable throwable) {
                PonderBackportMod.LOGGER.debug("Failed to invoke client command registration method {}", methodName,
                    throwable);
                return false;
            }
        }
        return false;
    }

    public static void queueOpenDebugScreen(@Nullable ResourceLocation componentId, int sceneIndex) {
        queueOpenScreen(componentId, sceneIndex, true);
    }

    public static void queueOpenShowcaseScreen(@Nullable ResourceLocation componentId, int sceneIndex) {
        queueOpenScreen(componentId, sceneIndex, false);
    }

    private static void queueOpenStackShowcase(ItemStack stack) {
        ResourceLocation component = getPonderComponentId(stack);
        if (component == null) return;
        queueOpenScreen(component, 0, false);
        pendingSourceStack = stack.copy();
    }

    public static void queueOpenIndexScreen() {
        pendingEditorOpen.clear();
        pendingHintScene = null;
        pendingComponentId = null;
        pendingSourceStack = ItemStack.EMPTY;
        pendingSceneIndex = 0;
        pendingDebugScreen = false;
        pendingIndexScreen = true;
        openViewerQueued = true;
    }

    private static void queueOpenScreen(@Nullable ResourceLocation componentId, int sceneIndex, boolean debugScreen) {
        pendingEditorOpen.clear();
        pendingHintScene = null;
        pendingComponentId = componentId;
        pendingSourceStack = ItemStack.EMPTY;
        pendingSceneIndex = Math.max(0, sceneIndex);
        pendingDebugScreen = debugScreen;
        pendingIndexScreen = false;
        openViewerQueued = true;
    }

    public static void queueOpenEditorScreen() {
        if (!AuthoringSettings.isEnabled()) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || !minecraft.isSingleplayer() || minecraft.world == null || minecraft.player == null) {
            return;
        }
        pendingHintScene = null;
        pendingComponentId = null;
        pendingSourceStack = ItemStack.EMPTY;
        pendingSceneIndex = 0;
        pendingDebugScreen = false;
        pendingIndexScreen = false;
        openViewerQueued = false;
        pendingEditorOpen.queue(minecraft.world, minecraft.player, minecraft.currentScreen,
            !(minecraft.currentScreen instanceof GuiChat));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        long started = DIAGNOSTICS ? System.nanoTime() : 0L;
        try {
            tickClient();
        } finally {
            if (DIAGNOSTICS && System.nanoTime() - started > 50000000L) {
                PonderBackportMod.LOGGER.warn("Ponder client tick took {} ms",
                    Long.valueOf((System.nanoTime() - started) / 1000000L));
            }
        }
    }

    private void tickClient() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        if (AuthoringSettings.isEnabled()) AuthoringTriggerManager.tick();
        if (compatibilityRetryTicks-- <= 0) {
            compatibilityRetryTicks = 20;
            JeiScreenCompat.tryInstall();
        }
        JeiScreenCompat.syncOverlaySuppression(minecraft.currentScreen);

        if (minecraft.player == null || minecraft.world == null) {
            clearPendingScreen();
            handleTooltipClientTick(null);
            return;
        }

        handleTooltipClientTick(minecraft.currentScreen);

        if (openPonderUiKey != null && minecraft.currentScreen == null) {
            while (openPonderUiKey.isPressed()) {
                if (!queueActiveHint()) queueOpenIndexScreen();
            }
        }

        PendingEditorOpen.Request editorRequest = pendingEditorOpen.consume(
            AuthoringSettings.isEnabled() && minecraft.isSingleplayer(),
            minecraft.world, minecraft.player, minecraft.currentScreen);
        if (editorRequest != null) {
            openEditorScreen(minecraft, (GuiScreen) editorRequest.parentScreen(minecraft.currentScreen));
            return;
        }

        if (!openViewerQueued) {
            return;
        }

        openViewerQueued = false;
        if (pendingIndexScreen) {
            minecraft.displayGuiScreen(new PonderIndexScreen());
            return;
        }

        ItemStack sourceStack = pendingSourceStack;
        pendingSourceStack = ItemStack.EMPTY;
        if (!sourceStack.isEmpty() && getPonderComponentId(sourceStack) == null) return;
        GuiScreen next = pendingDebugScreen
            ? new PonderDebugScreen(pendingComponentId, pendingSceneIndex)
            : !sourceStack.isEmpty() ? PonderUI.showcaseForStack(sourceStack, pendingSceneIndex)
                : PonderUI.showcase(pendingComponentId, pendingSceneIndex);
        ResourceLocation requestedHint = pendingHintScene;
        pendingHintScene = null;
        minecraft.displayGuiScreen(next);
        if (requestedHint != null && minecraft.currentScreen == next && next instanceof PonderDebugScreen
            && ((PonderDebugScreen) next).isShowingScene(requestedHint)) {
            AuthoringTriggerManager.markOpened(requestedHint);
        }
    }

    private static void openEditorScreen(Minecraft minecraft, @Nullable GuiScreen parent) {
        AuthoringEditorScreen editor = null;
        try {
            editor = new AuthoringEditorScreen(parent);
            minecraft.displayGuiScreen(editor);
        } catch (RuntimeException failure) {
            PonderBackportMod.LOGGER.error("Unable to open the local Ponder editor", failure);
            if (editor != null && minecraft.currentScreen == editor) {
                try {
                    minecraft.displayGuiScreen(parent);
                } catch (RuntimeException cleanupFailure) {
                    PonderBackportMod.LOGGER.error("Unable to restore the screen after a Ponder editor opening failure", cleanupFailure);
                }
            }
            if (minecraft.player != null) {
                minecraft.player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("ponder.authoring.open_failed"));
            }
        }
    }

    private static boolean queueActiveHint() {
        if (!AuthoringSettings.isEnabled()) return false;
        net.createmod.ponder.authoring.trigger.TriggerEngine.Hint hint =
            AuthoringTriggerManager.getActiveHint();
        if (hint == null) return false;
        int index = 0;
        for (java.util.Map.Entry<ResourceLocation, net.createmod.ponder.api.registration.StoryBoardEntry> entry
            : PonderIndex.getSceneAccess().getRegisteredEntries()) {
            if (!entry.getKey().equals(hint.getComponentId())) continue;
            if (entry.getValue().getSchematicLocation().equals(hint.getRuntimeSceneId())) {
                queueOpenShowcaseScreen(hint.getComponentId(), index);
                pendingHintScene = hint.getRuntimeSceneId();
                return true;
            }
            index++;
        }
        return false;
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) {
            return;
        }
        clearPendingScreen();
        resetTooltipState();
        if (AuthoringSettings.isEnabled()) AuthoringTriggerManager.clearSession();
        projectorRenderer.clear();
        PonderGuiSnapshotRegistry.releaseRuntimeState();
        MmcePonderPlugin.clearPreviewCache();
        MmceStaticModelPreviews.getInstance().clear();
        JeiScreenCompat.syncOverlaySuppression(null);
        firstWorldFrameRecorded = false;
        blueprintFirst = null;
        blueprintWorld = null;
        if (DIAGNOSTICS) {
            PonderBackportMod.LOGGER.info("Ponder client preview state released on world unload");
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.getWorld().isRemote) {
            firstWorldFrameRecorded = false;
            if (DIAGNOSTICS) {
                PonderBackportMod.LOGGER.info("Ponder observed client world load; visual acceptance still required");
            }
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!DIAGNOSTICS || firstWorldFrameRecorded || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world != null && minecraft.player != null && minecraft.currentScreen == null) {
            firstWorldFrameRecorded = true;
            PonderBackportMod.LOGGER.info("Ponder observed a client render tick without a loading screen");
        }
    }

    private static void clearPendingScreen() {
        pendingEditorOpen.clear();
        pendingHintScene = null;
        pendingComponentId = null;
        pendingSourceStack = ItemStack.EMPTY;
        pendingSceneIndex = 0;
        pendingDebugScreen = false;
        pendingIndexScreen = false;
        openViewerQueued = false;
    }

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (event.getEntityPlayer() == null) {
            return;
        }
        handleTooltipAddToTooltip(event);
    }

    @SubscribeEvent
    public void onGuiDrawPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        handleTooltipDrawPost(event);
    }

    @SubscribeEvent
    public void onAuthoringHud(net.minecraftforge.client.event.RenderGameOverlayEvent.Pre event) {
        if (event.getType() == net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL
            && Minecraft.getMinecraft().currentScreen instanceof net.createmod.ponder.foundation.ui.AuthoringGuiScreen) {
            event.setCanceled(true);
        }
    }

    private static void handleTooltipClientTick(@Nullable GuiScreen screen) {
        if (tooltipHandlerFailed) {
            return;
        }

        try {
            if (screen == null) {
                resetTooltipState();
            }
        } catch (Throwable throwable) {
            disableTooltipHandler("client tick", throwable);
        }
    }

    private static void handleTooltipAddToTooltip(ItemTooltipEvent event) {
        if (tooltipHandlerFailed) {
            return;
        }

        try {
            appendPonderTooltip(event.getItemStack(), event.getToolTip());
        } catch (Throwable throwable) {
            disableTooltipHandler("item tooltip", throwable);
        }
    }

    private static void handleTooltipDrawPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (tooltipHandlerFailed) {
            return;
        }

        try {
            updateTooltipHold(event.getGui(), event.getMouseX(), event.getMouseY());
        } catch (Throwable throwable) {
            disableTooltipHandler("gui draw", throwable);
        }
    }

    private static void disableTooltipHandler(String phase, Throwable throwable) {
        if (tooltipHandlerFailed) {
            return;
        }

        tooltipHandlerFailed = true;
        PonderBackportMod.LOGGER.error("Disabled Ponder tooltip handler after failure during {}", phase, throwable);
    }

    private static void appendPonderTooltip(ItemStack stack, List<String> tooltip) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.currentScreen == null || stack.isEmpty()) {
            return;
        }

        ResourceLocation componentId = getPonderComponentId(stack);
        if (componentId == null) {
            return;
        }

        hoveredStack = stack.copy();
        if (!areEquivalentForPonder(trackedStack, stack) || !componentId.equals(trackedComponentId)) {
            trackedStack = stack.copy();
            trackedComponentId = componentId;
            holdProgress = 0.0F;
        }

        int insertIndex = Math.min(1, tooltip.size());
        tooltip.add(insertIndex, buildTooltipLine(minecraft));
    }

    private static void updateTooltipHold(@Nullable GuiScreen screen, int mouseX, int mouseY) {
        long now = Minecraft.getSystemTime();
        if (screen == null) {
            resetTooltipState();
            lastFrameTimeMs = now;
            return;
        }

        float deltaMs = lastFrameTimeMs < 0L ? 50.0F : Math.min(100.0F, now - lastFrameTimeMs);
        lastFrameTimeMs = now;

        Minecraft minecraft = Minecraft.getMinecraft();
        boolean sameHoveredItem = !hoveredStack.isEmpty() && !trackedStack.isEmpty()
            && areEquivalentForPonder(hoveredStack, trackedStack);
        boolean forwardHeld = GameSettings.isKeyDown(minecraft.gameSettings.keyBindForward);

        if (sameHoveredItem && forwardHeld) {
            holdProgress = Math.min(1.0F, holdProgress + deltaMs / 550.0F);
        } else {
            holdProgress = Math.max(0.0F, holdProgress - deltaMs / 180.0F);
        }

        if (holdProgress >= 1.0F && trackedComponentId != null) {
            queueOpenStackShowcase(trackedStack);
            resetTooltipState();
            lastFrameTimeMs = now;
            return;
        }

        hoveredStack = ItemStack.EMPTY;
    }

    @Nullable
    private static ResourceLocation getPonderComponentId(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : PonderIndex.getSceneAccess().resolveComponentId(stack);
    }

    private static boolean areEquivalentForPonder(ItemStack left, ItemStack right) {
        return !left.isEmpty()
            && !right.isEmpty()
            && ItemStack.areItemsEqual(left, right)
            && ItemStack.areItemStackTagsEqual(left, right);
    }

    private static String buildTooltipLine(Minecraft minecraft) {
        String keyName = GameSettings.getKeyDisplayString(minecraft.gameSettings.keyBindForward.getKeyCode());
        float progress = Math.min(1.0F, holdProgress * 8.0F / 7.0F);
        if (progress > 0.0F) {
            return buildTooltipProgressBar(keyName, progress);
        }
        return TextFormatting.DARK_GRAY + "\u6309\u4F4F " + TextFormatting.GRAY + keyName + TextFormatting.DARK_GRAY
            + " \u5F00\u59CB\u601D\u7D22";
    }

    private static String buildTooltipProgressBar(String keyName, float progress) {
        int totalBars = 10;
        int filledBars = Math.min(totalBars, Math.max(0, Math.round(progress * totalBars)));
        StringBuilder builder = new StringBuilder();
        builder.append(TextFormatting.DARK_GRAY)
            .append("\u6309\u4F4F ")
            .append(TextFormatting.GRAY)
            .append(keyName)
            .append(TextFormatting.DARK_GRAY)
            .append(" \u5F00\u59CB\u601D\u7D22 ")
            .append(TextFormatting.GRAY)
            .append('[');
        for (int i = 0; i < totalBars; i++) {
            builder.append(i < filledBars ? TextFormatting.GRAY : TextFormatting.DARK_GRAY)
                .append('|');
        }
        builder.append(TextFormatting.GRAY).append(']');
        return builder.toString();
    }

    private static void resetTooltipState() {
        hoveredStack = ItemStack.EMPTY;
        trackedStack = ItemStack.EMPTY;
        trackedComponentId = null;
        holdProgress = 0.0F;
        lastFrameTimeMs = -1L;
    }
}
