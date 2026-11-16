package net.createmod.ponder.foundation.ui;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.practice.PracticeDefinition;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Action;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Menu;
import net.createmod.ponder.authoring.practice.PracticeLayout;
import net.createmod.ponder.authoring.practice.PracticeMode;
import net.createmod.ponder.authoring.practice.PracticeSession;
import net.createmod.ponder.authoring.practice.PracticeStepHandlers;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** Lazy draw-only WATCH adapter. The registry owns no strong Scene, player, real Container or native screen. */
public final class PracticeSnapshotRenderer implements PonderGuiSnapshotRegistry.SnapshotRenderer,
    PonderGuiSnapshotRegistry.ResettableSnapshotProvider {
    public static final int WIDTH = PracticeLayout.WIDTH;
    public static final int HEIGHT = PracticeLayout.HEIGHT + 32;
    private static final java.util.Map<PonderScene, Long> SCENE_TOKENS = new java.util.WeakHashMap<PonderScene, Long>();
    private static long nextSceneToken;
    private final WeakReference<PonderScene> owner;
    private final int definitionIndex, startTick;
    private final int canonicalHeight;
    private final PonderGuiSnapshotRegistry.Snapshot snapshot;
    private PracticeDefinition definition;
    private PracticeSession session;
    private long renderedFrames;

    public PracticeSnapshotRenderer(PonderScene scene, int definitionIndex, int startTick) {
        if (scene == null || definitionIndex < 0 || startTick < 0) throw new IllegalArgumentException("Snapshot scene, index and start are required");
        owner = new WeakReference<>(scene); this.definitionIndex = definitionIndex; this.startTick = startTick;
        java.util.List<PracticeDefinition> definitions = PracticeStepHandlers.getDefinitions(scene);
        canonicalHeight = definitionIndex < definitions.size() ? PracticeLayout.height(definitions.get(definitionIndex).getMenu()) + 32 : HEIGHT;
        snapshot = PonderGuiSnapshotRegistry.Snapshot.liveRenderer(WIDTH, canonicalHeight, false, this);
    }
    public static ResourceLocation register(PonderScene scene, int index, int start) {
        return new PracticeSnapshotRenderer(scene, index, start).register();
    }
    public ResourceLocation register() {
        PonderScene scene = owner.get();
        if (scene == null) throw new IllegalStateException("The GUI scene has been released");
        String digest = AuthoringDocument.hash(scene.getSceneId().toString().getBytes(StandardCharsets.UTF_8)).toLowerCase(java.util.Locale.ROOT);
        long token;
        synchronized (SCENE_TOKENS) {
            Long existing = SCENE_TOKENS.get(scene);
            if (existing == null) { existing = ++nextSceneToken; SCENE_TOKENS.put(scene, existing); }
            token = existing;
        }
        ResourceLocation id = Ponder.asResource("gui_snapshot/practice/" + digest + "/" + token + "/" + definitionIndex);
        PonderGuiSnapshotRegistry.registerDynamicProvider(id, this);
        return id;
    }
    @Override public PonderGuiSnapshotRegistry.Snapshot provide(float currentTick) {
        PracticeSession current = stateAt(currentTick);
        return current != null && current.isVisible() ? snapshot : null;
    }
    PracticeSession stateAt(float currentTick) {
        PonderScene scene = owner.get();
        if (scene == null) { reset(); return null; }
        java.util.List<PracticeDefinition> values = PracticeStepHandlers.getDefinitions(scene);
        if (definitionIndex >= values.size() || !Float.isFinite(currentTick) || currentTick < startTick) return null;
        PracticeDefinition latest = values.get(definitionIndex);
        if (latest != definition || session == null || session.isClosed()) {
            reset(); definition = latest; session = new PracticeSession(latest, PracticeMode.WATCH);
        }
        int target = Math.max(0, Math.min(latest.getDuration(), (int) Math.floor(currentTick - startTick)));
        if (target < session.getTick()) session.seek(target);
        else while (session.getTick() < target) session.advance();
        return session;
    }
    @Override public void reset() {
        if (session != null) session.close(); session = null; definition = null; renderedFrames = 0;
    }
    public long getRenderedFrames() { return renderedFrames; }
    public int getRuntimeTick() { return session == null ? -1 : session.getTick(); }
    @Override public void render(int x, int y, int width, int height, float currentTick, float fade) {
        PracticeSession current = stateAt(currentTick);
        if (current == null || !current.isVisible() || width < 1 || height < 1) return;
        Minecraft mc = Minecraft.getMinecraft();
        PreviewNativeRenderGuard.run(() -> {
            GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW); GlStateManager.pushMatrix();
            try {
                float scale = Math.min(width / (float) WIDTH, height / (float) canonicalHeight);
                GlStateManager.translate(x + (width - WIDTH * scale) / 2F, y + (height - canonicalHeight * scale) / 2F, 0);
                GlStateManager.scale(scale, scale, 1); GlStateManager.disableDepth(); GlStateManager.depthMask(false);
                Gui.drawRect(0, 0, WIDTH, canonicalHeight, 0xFFB7B9BC);
                String title = current.getDefinition().getTitle();
                if (title.isEmpty()) title = current.getDefinition().getMenuId();
                mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(title, WIDTH - 12), 6, 5, 0xFF20262B);
                GlStateManager.translate(0, 18, 0); Menu menu = current.getDefinition().getMenu();
                RenderHelper.enableGUIStandardItemLighting();
                for (int slot = 0; slot < current.getSlotCount(); slot++) {
                    int sx = PracticeLayout.x(menu, slot), sy = PracticeLayout.y(menu, slot);
                    Gui.drawRect(sx, sy, sx + 18, sy + 18, 0xFF36383D);
                    Gui.drawRect(sx + 1, sy + 1, sx + 18, sy + 18, 0xFF85898D);
                    drawItem(mc, current.getSlot(slot), sx + 1, sy + 1);
                }
                RenderHelper.disableStandardItemLighting(); GlStateManager.disableDepth(); GlStateManager.depthMask(false);
                if (menu == Menu.FURNACE) {
                    int progress = current.getFurnaceProgressPixels(28);
                    Gui.drawRect(73, 33, 102, 37, 0xFF44464A); Gui.drawRect(73, 33, 73 + progress, 37, 0xFF75CF91);
                }
                if (menu == Menu.HOPPER) mc.fontRenderer.drawString(current.getData("locked") == 0 ? "\u8f93\u9001" : "\u5df2\u9501\u5b9a", 111, 31, 0xFFFFFFFF);
                Action latest = null;
                for (Action action : current.getDefinition().getActions()) if (action.tick <= current.getTick() && action.slot >= 0) latest = action;
                if (!current.getCursor().isEmpty()) {
                    int sx = latest == null ? WIDTH - 24 : Math.min(WIDTH - 16, PracticeLayout.x(menu, latest.slot) + 9);
                    int sy = latest == null ? 59 : Math.min(PracticeLayout.height(menu) - 16, PracticeLayout.y(menu, latest.slot) + 9);
                    RenderHelper.enableGUIStandardItemLighting(); drawItem(mc, current.getCursor(), sx, sy); RenderHelper.disableStandardItemLighting();
                }
                GlStateManager.disableDepth(); GlStateManager.depthMask(false);
                String footer = current.isReadOnly() ? "\u4ec5\u8bfb\u5feb\u7167" : "\u865a\u62df\u6f14\u793a  T+" + current.getTick();
                mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(footer, WIDTH - 12), 6, PracticeLayout.height(menu) + 2, 0xFF26322B);
            } finally { GlStateManager.popMatrix(); }
        });
        renderedFrames++;
    }
    private static void drawItem(Minecraft mc, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        float previousZ = mc.getRenderItem().zLevel;
        try {
            PreviewNativeRenderGuard.run(() -> {
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
                mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, stack, x, y, null);
            });
        } finally { mc.getRenderItem().zLevel = previousZ; }
    }
}
