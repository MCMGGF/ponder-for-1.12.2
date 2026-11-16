package net.createmod.ponder.foundation.ui;

import java.util.function.Consumer;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.authoring.AuthoringSession;
import net.createmod.ponder.authoring.capture.CaptureDraft;
import net.createmod.ponder.authoring.capture.CaptureLease;
import net.createmod.ponder.authoring.capture.VanillaMenuCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

/** A finite singleplayer observation, not a menu opener or a real inventory interaction handler. */
public final class AuthoringCaptureController {
    public static final AuthoringCaptureController INSTANCE = new AuthoringCaptureController();
    public static final int CAPTURE_BUTTON_ID = 18462;
    private static final int TIMEOUT_TICKS = 1200;
    private Request active;
    private long tick;
    private int consumeReleaseButton = -1;
    private GuiScreen consumeReleaseScreen;
    private static final class Request {
        final GuiScreen editor;
        final AuthoringSession session;
        final int scene;
        final String documentHash;
        final Consumer<String> onReturn;
        final CaptureLease lease;
        GuiContainer sourceScreen;
        GuiButton button;
        CaptureDraft draft;
        AuthoringCaptureReviewScreen review;
        Request(Minecraft mc, GuiScreen editor, AuthoringSession session, int scene, Consumer<String> onReturn, long tick) {
            this.editor = editor; this.session = session; this.scene = scene; this.onReturn = onReturn;
            documentHash = session.getDocument().sha256(); lease = new CaptureLease(mc.world, mc.player, tick, TIMEOUT_TICKS);
        }
    }
    private AuthoringCaptureController() { }
    public boolean isActive() { return active != null; }
    public CaptureLease.State getState() { return active == null ? CaptureLease.State.FINISHED : active.lease.getState(); }
    public GuiButton getCaptureButton() { return active == null ? null : active.button; }
    public CaptureDraft getDraft() { return active == null ? null : active.draft; }
    public void begin(Minecraft mc, GuiScreen editor, AuthoringSession session, int scene, Consumer<String> onReturn) {
        if (active != null) throw new IllegalStateException("A vanilla menu capture is already active");
        if (!mc.isCallingFromMinecraftThread() || !mc.isSingleplayer() || mc.world == null || mc.player == null
            || mc.currentScreen != editor || !(editor instanceof AuthoringEditorScreen)
            || mc.player.openContainer != mc.player.inventoryContainer || session == null || scene < 0 || scene >= session.getDocument().getScenes().size())
            throw new IllegalStateException("Capture must start from the current singleplayer editor with no real container open");
        active = new Request(mc, editor, session, scene, onReturn, tick);
        mc.displayGuiScreen(null);
        mc.player.sendStatusMessage(new TextComponentString("\u83dc\u5355\u6355\u83b7\uff1a\u5df2\u5c31\u7eea"), true);
    }
    public static void cancelIfOwned() {
        INSTANCE.consumeReleaseButton = -1; INSTANCE.consumeReleaseScreen = null;
        INSTANCE.finish(false, "\u83dc\u5355\u6355\u83b7\u5df2\u53d6\u6d88\uff0c\u8349\u7a3f\u672a\u4fee\u6539", false);
    }
    @SubscribeEvent public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote && active != null && active.lease.ownsWorld(event.getWorld())) cancelIfOwned();
    }
    @SubscribeEvent public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick++;
        Minecraft mc = Minecraft.getMinecraft();
        if (consumeReleaseScreen != null && mc.currentScreen != consumeReleaseScreen) { consumeReleaseButton = -1; consumeReleaseScreen = null; }
        Request request = active;
        if (request == null) return;
        try {
            if (!mc.isSingleplayer() || mc.player == null || !request.lease.owns(mc.world, mc.player)) { finish(false, "\u4e16\u754c\u6216\u73a9\u5bb6\u5df2\u6539\u53d8\uff0c\u6355\u83b7\u5df2\u505c\u6b62", false); return; }
            if (request.lease.getState() == CaptureLease.State.ARMED && mc.currentScreen != null && !(mc.currentScreen instanceof GuiContainer)) {
                finish(false, "\u5df2\u5207\u6362\u5176\u4ed6\u754c\u9762\uff0c\u6355\u83b7\u5df2\u505c\u6b62", false); return;
            }
            if (request.lease.getState() == CaptureLease.State.ARMED && mc.currentScreen instanceof GuiContainer) {
                GuiContainer screen = (GuiContainer) mc.currentScreen;
                if (screen.inventorySlots != mc.player.openContainer || !VanillaMenuCapture.supports(screen, screen.inventorySlots)
                    || !request.lease.bind(mc.world, mc.player, mc.player.openContainer, screen)) {
                    finish(false, "\u6b64\u83dc\u5355\u6ca1\u6709\u5b89\u5168\u7684\u539f\u7248\u6355\u83b7\u9002\u914d\u5668", false); return;
                }
                request.sourceScreen = screen;
                request.button = new GuiButton(CAPTURE_BUTTON_ID, Math.max(4, screen.width - 70), 6, Math.min(64, Math.max(1, screen.width - 8)), Math.max(20, mc.fontRenderer.FONT_HEIGHT + 6), "\u6355\u83b7");
            }
            CaptureLease.Observation observation = request.lease.observe(mc.world, mc.player, mc.player.openContainer, mc.currentScreen, mc.player.inventoryContainer, tick);
            if (observation == CaptureLease.Observation.CANCEL) { finish(false, "\u6355\u83b7\u8d85\u65f6\u6216\u83dc\u5355\u5df2\u6539\u53d8\uff0c\u8349\u7a3f\u672a\u4fee\u6539", true); return; }
            if (observation == CaptureLease.Observation.REVIEW_READY) {
                request.sourceScreen = null; request.button = null;
                request.review = new AuthoringCaptureReviewScreen(this, request.draft);
                request.lease.reviewing(request.review);
                mc.displayGuiScreen(request.review);
            }
        } catch (RuntimeException failure) {
            Ponder.LOGGER.error("Could not capture a read-only vanilla menu", failure);
            finish(false, "\u539f\u7248\u83dc\u5355\u6355\u83b7\u5931\u8d25\uff0c\u8349\u7a3f\u672a\u4fee\u6539", true);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        int button = Mouse.getEventButton();
        if (button >= 0 && !Mouse.getEventButtonState() && button == consumeReleaseButton) {
            boolean owned = event.getGui() == consumeReleaseScreen;
            consumeReleaseButton = -1; consumeReleaseScreen = null;
            if (owned) { event.setCanceled(true); return; }
        }
        if (active == null) return;
        if (!Mouse.getEventButtonState() || button < 0) return;
        Minecraft mc = Minecraft.getMinecraft(); GuiScreen screen = event.getGui();
        int x = Mouse.getEventX() * screen.width / Math.max(1, mc.displayWidth);
        int y = screen.height - Mouse.getEventY() * screen.height / Math.max(1, mc.displayHeight) - 1;
        if (handleCaptureButton(screen, x, y, button)) { event.setCanceled(true); consumeReleaseButton = button; consumeReleaseScreen = screen; }
    }
    /** The real input route also used by finite native QA, without invoking GuiContainer.mouseClicked. */
    public boolean handleCaptureButton(GuiScreen screen, int x, int y, int button) {
        Request request = active; Minecraft mc = Minecraft.getMinecraft();
        if (request == null || request.button == null || request.sourceScreen != screen || mc.currentScreen != screen
            || x < request.button.x || x >= request.button.x + request.button.width || y < request.button.y || y >= request.button.y + request.button.height) return false;
        if (button != 0 || !request.button.enabled) return true;
        try {
            if (!request.lease.owns(mc.world, mc.player) || mc.player == null || mc.player.openContainer != request.sourceScreen.inventorySlots
                || !VanillaMenuCapture.supports(screen, mc.player.openContainer)) throw new IllegalStateException("Capture ownership changed");
            CaptureDraft draft = VanillaMenuCapture.capture(screen, mc.player.openContainer, mc.player.inventory);
            if (!request.lease.capture(mc.player.openContainer, screen)) throw new IllegalStateException("Capture request is no longer bound");
            request.draft = draft; request.button.enabled = false; request.button.displayString = "\u5b8c\u6210";
            mc.player.sendStatusMessage(new TextComponentString("\u5df2\u6355\u83b7\uff0c\u7b49\u5f85\u83dc\u5355\u6b63\u5e38\u5173\u95ed"), true);
        } catch (RuntimeException failure) {
            Ponder.LOGGER.error("Could not copy a vanilla menu snapshot", failure);
            finish(false, "\u6355\u83b7\u5931\u8d25\uff0c\u672a\u4fee\u6539\u8349\u7a3f\u6216\u771f\u5b9e\u7269\u54c1", false);
        }
        return true;
    }
    @SubscribeEvent public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        Request request = active;
        if (request == null || request.button == null || event.getGui() != request.sourceScreen) return;
        request.button.x = Math.max(4, event.getGui().width - request.button.width - 6);
        PreviewRenderState.runIsolated(() -> {
            GlStateManager.disableDepth(); GlStateManager.depthMask(false);
            GuiButton button = request.button; Minecraft mc = Minecraft.getMinecraft();
            boolean hover = event.getMouseX() >= button.x && event.getMouseX() < button.x + button.width && event.getMouseY() >= button.y && event.getMouseY() < button.y + button.height;
            Gui.drawRect(button.x, button.y, button.x + button.width, button.y + button.height, button.enabled && hover ? 0xFF66808A : 0xFF536168);
            Gui.drawRect(button.x + 1, button.y + 1, button.x + button.width - 1, button.y + button.height - 1, button.enabled ? 0xFF303C43 : 0xFF20292E);
            String label = mc.fontRenderer.trimStringToWidth(button.displayString, Math.max(1, button.width - 8));
            mc.fontRenderer.drawStringWithShadow(label, button.x + (button.width - mc.fontRenderer.getStringWidth(label)) / 2F,
                button.y + (button.height - mc.fontRenderer.FONT_HEIGHT) / 2F, button.enabled ? 0xE4EBEE : 0x83939C);
        });
    }
    boolean isReviewOwned(GuiScreen screen) {
        Request request = active; Minecraft mc = Minecraft.getMinecraft();
        return request != null && request.review == screen && mc.currentScreen == screen && request.lease.getState() == CaptureLease.State.REVIEW
            && request.lease.owns(mc.world, mc.player) && mc.player != null && mc.isSingleplayer() && mc.player.openContainer == mc.player.inventoryContainer;
    }
    void acceptReview(GuiScreen screen) {
        if (!isReviewOwned(screen)) { finish(false, "\u6355\u83b7\u5ba1\u9605\u5df2\u5931\u6548", false); return; }
        Request request = active;
        if (!request.review.canConfirm()) return;
        if (!request.documentHash.equals(request.session.getDocument().sha256())) { finish(false, "\u539f\u7f16\u8f91\u6587\u6863\u5df2\u6539\u53d8\uff0c\u672a\u8986\u76d6\u8349\u7a3f", true); return; }
        try { request.draft.appendTo(request.session, request.scene, request.documentHash); }
        catch (RuntimeException failure) {
            Ponder.LOGGER.error("Could not append the reviewed vanilla capture draft", failure);
            finish(false, "\u83dc\u5355\u6b65\u9aa4\u672a\u901a\u8fc7\u8349\u7a3f\u6821\u9a8c\uff0c\u539f\u6587\u6863\u672a\u4fee\u6539", true); return;
        }
        finish(true, "\u5df2\u6dfb\u52a0\u83dc\u5355\u6b65\u9aa4\u8349\u7a3f\uff0c\u5c1a\u672a\u4fdd\u5b58\u6216\u53d1\u5e03", true);
    }
    void cancelReview(GuiScreen screen) { if (active != null && active.review == screen) finish(false, "\u5df2\u53d6\u6d88\uff0c\u539f\u8349\u7a3f\u672a\u4fee\u6539", true); }
    void closedReview(GuiScreen screen) { if (active != null && active.review == screen) finish(false, "\u5ba1\u9605\u754c\u9762\u5df2\u5173\u95ed\uff0c\u8349\u7a3f\u672a\u4fee\u6539", false); }
    private void finish(boolean accepted, String message, boolean allowReturn) {
        Request request = active;
        if (request == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        boolean safe = allowReturn && request.lease.owns(mc.world, mc.player) && mc.player != null
            && mc.player.openContainer == mc.player.inventoryContainer && (mc.currentScreen == null || mc.currentScreen == request.review);
        active = null; request.lease.finish(); request.sourceScreen = null; request.button = null; request.draft = null; request.review = null;
        if (safe) try { mc.displayGuiScreen(request.editor); }
        catch (RuntimeException failure) {
            Ponder.LOGGER.error("Could not return from a vanilla menu capture", failure);
            if (mc.currentScreen == request.editor) try { mc.displayGuiScreen(null); }
            catch (RuntimeException restoreFailure) { Ponder.LOGGER.error("Could not close the partially initialized authoring screen", restoreFailure); }
        }
        if (request.onReturn != null && safe && mc.currentScreen == request.editor) try { request.onReturn.accept(message); }
        catch (RuntimeException failure) { Ponder.LOGGER.error("Could not refresh the authoring capture return", failure); }
        if (!safe && mc.player != null) mc.player.sendStatusMessage(new TextComponentString(message), true);
    }
}
