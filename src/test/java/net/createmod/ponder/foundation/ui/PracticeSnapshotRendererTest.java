package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import net.createmod.ponder.authoring.AuthoringCompiler;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.practice.PracticeSession;
import net.createmod.ponder.authoring.practice.PracticeStepHandlers;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.OverlayEvent;
import net.createmod.ponder.foundation.PonderScene.OverlayEventType;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PracticeSnapshotRendererTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    @BeforeEach void resetBefore() { PonderGuiSnapshotRegistry.releaseRuntimeState(); }
    @AfterEach void resetAfter() { PonderGuiSnapshotRegistry.releaseRuntimeState(); }
    private AuthoringCompiler compiler() { AuthoringCompiler c = new AuthoringCompiler(); PracticeStepHandlers.register(c); return c; }
    private AuthoringDocument document(String steps) {
        return AuthoringDocument.parse("{\"id\":\"example:snapshot\",\"items\":[\"minecraft:stone\"],\"scenes\":[{\"id\":\"main\",\"steps\":[" + steps + "]}]}");
    }
    private String show() { return "{\"type\":\"show_interface\",\"uiId\":\"minecraft:chest\",\"duration\":8,\"slots\":[{\"index\":0,\"item\":{\"id\":\"minecraft:paper\",\"count\":4}}]}"; }
    private OverlayEvent gui(PonderScene scene) { return scene.getOverlayEvents().stream().filter(e -> e.getType() == OverlayEventType.GUI_TEXTURE).findFirst().get(); }
    private PracticeSnapshotRenderer renderer(PonderScene scene, int tick) {
        PonderGuiSnapshotRegistry.Snapshot snapshot = PonderGuiSnapshotRegistry.resolve(gui(scene), tick);
        assertNotNull(snapshot); assertTrue(snapshot.renderer instanceof PracticeSnapshotRenderer); return (PracticeSnapshotRenderer) snapshot.renderer;
    }

    @Test void interfaceProducerCreatesRealForegroundGuiOverlayAndReadsFinalActions() {
        PonderScene scene = compiler().compile(document(show() + ",{\"type\":\"idle\",\"duration\":30},{\"type\":\"interface_action\",\"slot\":0,\"duration\":10}"), null).get(0);
        OverlayEvent event = gui(scene); assertEquals(0, event.getTick()); assertEquals(40, event.getDuration());
        assertTrue(PreviewCalloutContent.collect(scene, 35, key -> key).stream().anyMatch(g -> g.guiEvents().contains(event)));
        PracticeSnapshotRenderer renderer = renderer(scene, 40); PracticeSession state = renderer.stateAt(40);
        assertEquals(40, state.getTick()); assertTrue(state.getSlot(0).isEmpty()); assertEquals(4, state.getCursor().getCount());
        assertEquals(0, renderer.getRenderedFrames(), "Provider consumption does not pretend that GL drawing occurred");
    }

    @Test void watchPauseBackwardsAndReplayAreDeterministic() {
        PonderScene scene = compiler().compile(document(show() + ",{\"type\":\"interface_action\",\"slot\":0,\"duration\":6},{\"type\":\"interface_action\",\"slot\":1,\"action\":\"right\",\"duration\":6}"), null).get(0);
        PracticeSnapshotRenderer renderer = renderer(scene, 12); PracticeSession state = renderer.stateAt(12);
        assertEquals(1, state.getSlot(1).getCount()); assertEquals(3, state.getCursor().getCount());
        assertSame(state, renderer.stateAt(12)); assertEquals(1, state.getSlot(1).getCount());
        renderer.stateAt(0); assertEquals(4, state.getSlot(0).getCount()); assertTrue(state.getCursor().isEmpty());
        renderer.stateAt(12); assertEquals(1, state.getSlot(1).getCount()); assertEquals(3, state.getCursor().getCount());
    }

    @Test void hideAndOpeningTheNextMenuRemoveTheOldLiveOnlySnapshotWithoutTextureFallback() {
        PonderScene scene = compiler().compile(document(show() + ",{\"type\":\"idle\",\"duration\":5},{\"type\":\"show_interface\",\"uiId\":\"minecraft:hopper\",\"duration\":20}"), null).get(0);
        OverlayEvent first = gui(scene); PonderGuiSnapshotRegistry.Snapshot before = PonderGuiSnapshotRegistry.get(first.getGuiSnapshotId(), 4);
        assertNotNull(before); assertNull(before.texture); first.applySnapshot(before, 0, 0);
        assertNull(PonderGuiSnapshotRegistry.get(first.getGuiSnapshotId(), 5)); assertNull(first.getTextureLocation());
        assertEquals(2, PracticeStepHandlers.getDefinitions(scene).size());
        PonderScene hidden = compiler().compile(document(show() + ",{\"type\":\"idle\",\"duration\":3},{\"type\":\"hide_interface\"}"), null).get(0);
        assertNotNull(PonderGuiSnapshotRegistry.get(gui(hidden).getGuiSnapshotId(), 2));
        assertNull(PonderGuiSnapshotRegistry.get(gui(hidden).getGuiSnapshotId(), 3));
    }

    @Test void registrationIsLazyAndRepeatedRegistrationReusesOnlyTheSameSceneInstanceId() throws Exception {
        PonderScene first = compiler().compile(document(show()), null).get(0); ResourceLocation id = gui(first).getGuiSnapshotId();
        Field providers = PonderGuiSnapshotRegistry.class.getDeclaredField("SNAPSHOTS"); providers.setAccessible(true);
        Object provider = ((java.util.Map<?, ?>) providers.get(null)).get(id);
        Field session = PracticeSnapshotRenderer.class.getDeclaredField("session"); session.setAccessible(true);
        assertNull(session.get(provider), "Registering or compiling must not allocate a WATCH session");
        PracticeSession old = ((PracticeSnapshotRenderer) provider).stateAt(2);
        assertEquals(id, PracticeSnapshotRenderer.register(first, 0, 0));
        assertEquals(1, PonderGuiSnapshotRegistry.dynamicProviderCount()); assertTrue(old.isClosed());
        assertEquals(-1, ((PracticeSnapshotRenderer) ((java.util.Map<?, ?>) providers.get(null)).get(id)).getRuntimeTick());
        assertNotNull(PonderGuiSnapshotRegistry.resolve(gui(first), 2), "An event retains its own provider after a compatibility index replacement");
    }

    @Test void sameIdSceneInstancesKeepDifferentMenuStateAndIndependentLifecycle() {
        PonderScene first = compiler().compile(document(show()), null).get(0);
        PracticeSnapshotRenderer firstProvider = renderer(first, 2); PracticeSession firstState = firstProvider.stateAt(2);
        PonderScene second = compiler().compile(document(show().replace("\"count\":4", "\"count\":9")), null).get(0);
        PracticeSnapshotRenderer secondProvider = renderer(second, 2); PracticeSession secondState = secondProvider.stateAt(2);
        assertEquals(first.getSceneId(), second.getSceneId());
        assertNotEquals(gui(first).getGuiSnapshotId(), gui(second).getGuiSnapshotId());
        assertFalse(firstState.isClosed()); assertEquals(4, firstState.getSlot(0).getCount()); assertEquals(9, secondState.getSlot(0).getCount());
        firstProvider.reset(); assertTrue(firstState.isClosed()); assertFalse(secondState.isClosed());
        assertNotNull(PonderGuiSnapshotRegistry.resolve(gui(second), 2)); assertSame(secondState, secondProvider.stateAt(2));
        assertNotNull(PonderGuiSnapshotRegistry.resolve(gui(first), 2)); assertEquals(4, firstProvider.stateAt(2).getSlot(0).getCount());
        assertEquals(9, secondProvider.stateAt(2).getSlot(0).getCount());
    }

    @Test void moreThanTheRegistryBudgetKeepsEveryCompiledEventReadableAndUnloadResetsEvictedSession() {
        StringBuilder steps = new StringBuilder();
        for (int i = 0; i < 257; i++) {
            if (i > 0) steps.append(',');
            steps.append(show()).append(",{\"type\":\"idle\",\"duration\":1}");
        }
        PonderScene scene = compiler().compile(document(steps.toString()), null).get(0);
        java.util.List<OverlayEvent> events = new java.util.ArrayList<OverlayEvent>();
        for (OverlayEvent event : scene.getOverlayEvents()) if (event.getType() == OverlayEventType.GUI_TEXTURE) events.add(event);
        assertEquals(257, events.size()); assertEquals(256, PonderGuiSnapshotRegistry.dynamicProviderCount());
        assertNull(PonderGuiSnapshotRegistry.get(events.get(0).getGuiSnapshotId(), 0), "The compatibility index may evict, but the event is authoritative");
        for (OverlayEvent event : events) {
            PonderGuiSnapshotRegistry.Snapshot value = PonderGuiSnapshotRegistry.resolve(event, event.getTick());
            assertNotNull(value); assertTrue(value.renderer instanceof PracticeSnapshotRenderer);
            assertEquals(4, ((PracticeSnapshotRenderer) value.renderer).stateAt(event.getTick()).getSlot(0).getCount());
        }
        PracticeSnapshotRenderer evicted = (PracticeSnapshotRenderer) events.get(0).getGuiSnapshotProvider();
        PracticeSession reopened = evicted.stateAt(0); assertFalse(reopened.isClosed());
        PonderGuiSnapshotRegistry.releaseRuntimeState();
        assertTrue(reopened.isClosed()); assertEquals(-1, evicted.getRuntimeTick());
        assertEquals(0, PonderGuiSnapshotRegistry.dynamicProviderCount());
    }

    @Test void weakOwnerLossAndUnloadCloseSessionsAndReleaseDynamicProviders() throws Exception {
        PonderScene scene = compiler().compile(document(show()), null).get(0); PracticeSnapshotRenderer renderer = renderer(scene, 2);
        PracticeSession state = renderer.stateAt(2);
        Field owner = PracticeSnapshotRenderer.class.getDeclaredField("owner"); owner.setAccessible(true); ((WeakReference<?>) owner.get(renderer)).clear();
        assertNull(renderer.provide(3)); assertTrue(state.isClosed()); assertEquals(-1, renderer.getRuntimeTick());
        PonderGuiSnapshotRegistry.releaseRuntimeState(); assertEquals(0, PonderGuiSnapshotRegistry.dynamicProviderCount());
        assertNull(PonderGuiSnapshotRegistry.get(gui(scene).getGuiSnapshotId(), 0));
        for (Field field : PracticeSnapshotRenderer.class.getDeclaredFields()) assertNotEquals(PonderScene.class, field.getType());
    }

    @Test void unknownMenusRemainReadableSnapshotsWithoutApplyingClicksOrCompletingPractice() {
        PonderScene scene = compiler().compile(document(show().replace("minecraft:chest", "example:unknown")
            + ",{\"type\":\"interface_action\",\"slot\":0,\"duration\":6}"), null).get(0);
        PracticeSnapshotRenderer renderer = renderer(scene, 6); PracticeSession state = renderer.stateAt(6);
        assertTrue(state.isReadOnly()); assertTrue(state.isVisible()); assertEquals(4, state.getSlot(0).getCount());
        assertTrue(state.getCursor().isEmpty()); assertFalse(state.isComplete());
    }

    @Test void dynamicRegistryBoundEvictionReplacementAndStaticProtectionResetWithoutProvide() {
        class Counter implements PonderGuiSnapshotRegistry.ResettableSnapshotProvider {
            int resets, provides; public PonderGuiSnapshotRegistry.Snapshot provide(float t) { provides++; return null; } public void reset() { resets++; }
        }
        Counter first = new Counter(); ResourceLocation key = new ResourceLocation("test", "dynamic/first");
        PonderGuiSnapshotRegistry.registerDynamicProvider(key, first);
        for (int i = 0; i < 256; i++) PonderGuiSnapshotRegistry.registerDynamicProvider(new ResourceLocation("test", "dynamic/" + i), new Counter());
        assertEquals(256, PonderGuiSnapshotRegistry.dynamicProviderCount()); assertEquals(1, first.resets); assertEquals(0, first.provides);
        Counter replacement = new Counter(); PonderGuiSnapshotRegistry.registerDynamicProvider(new ResourceLocation("test", "dynamic/255"), replacement);
        PonderGuiSnapshotRegistry.releaseRuntimeState(); assertEquals(1, replacement.resets); assertEquals(0, replacement.provides);
        assertThrows(IllegalArgumentException.class, () -> PonderGuiSnapshotRegistry.registerDynamicProvider(new ResourceLocation("ponder_legacy", "gui_snapshot/minecraft_furnace"), new Counter()));
    }

    @Test void onlyExplicitWorldTargetsBindThreeDimensionsAndGuiDurationCannotMutateTextEvents() {
        JsonObject show = new JsonParser().parse(show()).getAsJsonObject(); show.add("blockPos", new JsonParser().parse("[2,3,4]"));
        PonderScene scene = compiler().compile(document(show.toString()), null).get(0);
        assertEquals(java.util.Collections.singletonList(new BlockPos(2, 3, 4)), gui(scene).getTargetPositions());
        JsonObject twoD = new JsonParser().parse(show()).getAsJsonObject(); twoD.add("point", new JsonParser().parse("[0.4,0.5]"));
        PonderScene noTarget = compiler().compile(document(twoD.toString()), null).get(0);
        assertNull(gui(noTarget).getPointAt()); assertTrue(gui(noTarget).getTargetPositions().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> gui(scene).setGuiDuration(24001));
        assertThrows(IllegalArgumentException.class, () -> scene.createOverlayEvent(10).setGuiDuration(20));
    }

    @Test void rendererSourceKeepsNativeItemStateGuardAndNoLiveScreenOrNetworkInteraction() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/net/createmod/ponder/foundation/ui/PracticeSnapshotRenderer.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("WeakReference<PonderScene>")); assertTrue(source.contains("PreviewNativeRenderGuard.run"));
        assertTrue(source.contains("PracticeLayout.x")); assertTrue(source.contains("PracticeMode.WATCH"));
        assertTrue(source.contains("float previousZ = mc.getRenderItem().zLevel"));
        assertTrue(source.contains("finally { mc.getRenderItem().zLevel = previousZ; }"));
        for (String forbidden : new String[] {"displayGuiScreen", "playerController", "sendPacket", "openContainer", "mc.player", "new GuiContainer"}) assertFalse(source.contains(forbidden), forbidden);
    }
}
