package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.util.Map;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SnapshotProviderLifecycleTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    @BeforeEach void before() { PonderGuiSnapshotRegistry.releaseRuntimeState(); }
    @AfterEach void after() { PonderGuiSnapshotRegistry.releaseRuntimeState(); }
    private static final class Provider implements PonderGuiSnapshotRegistry.ResettableSnapshotProvider {
        final PonderGuiSnapshotRegistry.Snapshot snapshot = PonderGuiSnapshotRegistry.Snapshot.fullTexture(new ResourceLocation("test", "image.png"), 16, 16);
        int resets, opens;
        boolean opened, failReset;
        @Override public PonderGuiSnapshotRegistry.Snapshot provide(float tick) {
            if (!opened) { opened = true; opens++; } return snapshot;
        }
        @Override public void reset() { resets++; opened = false; if (failReset) throw new IllegalStateException("Fixture reset failure"); }
    }
    @SuppressWarnings("unchecked")
    private Map<PonderGuiSnapshotRegistry.SnapshotProvider, Boolean> consumed() throws Exception {
        Field field = PonderGuiSnapshotRegistry.class.getDeclaredField("EVENT_PROVIDERS"); field.setAccessible(true);
        return (Map<PonderGuiSnapshotRegistry.SnapshotProvider, Boolean>) field.get(null);
    }
    private PonderScene.OverlayEvent event(PonderGuiSnapshotRegistry.SnapshotProvider provider, ResourceLocation id) {
        ResourceLocation sceneId = new ResourceLocation("test", "lifecycle");
        PonderScene scene = new PonderScene(new PonderStoryBoardEntry(null, "lifecycle_test", sceneId, sceneId), new PonderLocalization());
        PonderScene.OverlayEvent event = scene.createOverlayEvent(80).guiSnapshot(id); if (provider != null) event.setGuiSnapshotProvider(provider); return event;
    }
    @Test void sameHashReplacementSurvivesReleaseOfTheOldPrivateProvider() {
        ResourceLocation id = new ResourceLocation("test", "same_image_hash"); Provider old = new Provider(), current = new Provider();
        PonderGuiSnapshotRegistry.registerDynamicProvider(id, old);
        PonderScene.OverlayEvent oldEvent = event(old, id); assertNotNull(PonderGuiSnapshotRegistry.resolve(oldEvent, 0));
        PonderGuiSnapshotRegistry.registerDynamicProvider(id, current); assertEquals(1, old.resets);
        assertSame(current.snapshot, PonderGuiSnapshotRegistry.get(id, 0));
        PonderGuiSnapshotRegistry.releaseProvider(old);
        assertEquals(2, old.resets); assertEquals(0, current.resets); assertEquals(1, PonderGuiSnapshotRegistry.dynamicProviderCount());
        assertSame(current.snapshot, PonderGuiSnapshotRegistry.get(id, 0));
        PonderGuiSnapshotRegistry.releaseProvider(current); assertNull(PonderGuiSnapshotRegistry.get(id, 0));
        assertEquals(0, PonderGuiSnapshotRegistry.dynamicProviderCount());
    }
    @Test void releasedEventKeepsItsPrivateProviderAndLazilyReopensWithoutGlobalRegistration() throws Exception {
        ResourceLocation id = new ResourceLocation("test", "reopen"); Provider provider = new Provider();
        PonderGuiSnapshotRegistry.registerDynamicProvider(id, provider); PonderScene.OverlayEvent event = event(provider, id);
        assertSame(provider.snapshot, PonderGuiSnapshotRegistry.resolve(event, 10)); assertTrue(provider.opened); assertTrue(consumed().containsKey(provider));
        PonderGuiSnapshotRegistry.releaseProvider(provider);
        assertFalse(provider.opened); assertEquals(1, provider.resets); assertFalse(consumed().containsKey(provider));
        assertSame(provider, event.getGuiSnapshotProvider()); assertNull(PonderGuiSnapshotRegistry.get(id, 10));
        assertSame(provider.snapshot, PonderGuiSnapshotRegistry.resolve(event, 10)); assertEquals(2, provider.opens);
        assertTrue(consumed().containsKey(provider)); assertEquals(0, PonderGuiSnapshotRegistry.dynamicProviderCount());
        PonderGuiSnapshotRegistry.releaseRuntimeState(); assertFalse(provider.opened); assertEquals(2, provider.resets);
    }
    @Test void resetExceptionStillRemovesEveryMatchingDynamicIndexAndConsumedReference() throws Exception {
        Provider broken = new Provider(), other = new Provider(); ResourceLocation first = new ResourceLocation("test", "broken_first");
        ResourceLocation second = new ResourceLocation("test", "broken_second"), unrelated = new ResourceLocation("test", "unrelated");
        PonderGuiSnapshotRegistry.registerDynamicProvider(first, broken); PonderGuiSnapshotRegistry.registerDynamicProvider(second, broken);
        PonderGuiSnapshotRegistry.registerDynamicProvider(unrelated, other);
        PonderGuiSnapshotRegistry.resolve(event(broken, first), 0); PonderGuiSnapshotRegistry.resolve(event(other, unrelated), 0);
        broken.failReset = true; assertDoesNotThrow(() -> PonderGuiSnapshotRegistry.releaseProvider(broken));
        assertEquals(1, broken.resets); assertNull(PonderGuiSnapshotRegistry.get(first, 0)); assertNull(PonderGuiSnapshotRegistry.get(second, 0));
        assertFalse(consumed().containsKey(broken)); assertTrue(consumed().containsKey(other)); assertEquals(0, other.resets);
        assertSame(other.snapshot, PonderGuiSnapshotRegistry.get(unrelated, 0)); assertEquals(1, PonderGuiSnapshotRegistry.dynamicProviderCount());
    }
    @Test void nullReleaseIsHarmlessAndStaticSnapshotRegistrationIsNotRemoved() throws Exception {
        ResourceLocation id = new ResourceLocation("ponder_legacy", "gui_snapshot/minecraft_furnace");
        PonderGuiSnapshotRegistry.Snapshot original = PonderGuiSnapshotRegistry.get(id, 0);
        Field field = PonderGuiSnapshotRegistry.class.getDeclaredField("SNAPSHOTS"); field.setAccessible(true);
        PonderGuiSnapshotRegistry.SnapshotProvider shared = (PonderGuiSnapshotRegistry.SnapshotProvider) ((Map<?, ?>) field.get(null)).get(id);
        assertNotNull(shared); assertDoesNotThrow(() -> PonderGuiSnapshotRegistry.releaseProvider(null));
        PonderGuiSnapshotRegistry.releaseProvider(shared); assertSame(original, PonderGuiSnapshotRegistry.get(id, 0));
    }
    @Test void providerSelectionPrefersThePrivateEventAndNeverProvidesDuringInspection() {
        Provider indexed = new Provider(), local = new Provider(); ResourceLocation id = new ResourceLocation("test", "provider_selection");
        PonderGuiSnapshotRegistry.registerDynamicProvider(id, indexed);
        PonderScene.OverlayEvent event = event(local, id);
        assertSame(local, PonderGuiSnapshotRegistry.providerFor(event));
        assertEquals(0, local.opens); assertEquals(0, indexed.opens);
        assertSame(local.snapshot, PonderGuiSnapshotRegistry.resolve(event, 12));
        assertEquals(1, local.opens); assertEquals(0, indexed.opens);
        assertNull(PonderGuiSnapshotRegistry.providerFor(null));
    }
    @Test void idOnlyProviderSelectionIsLazyAndParticipatesInEventRelease() throws Exception {
        Provider indexed = new Provider(); ResourceLocation id = new ResourceLocation("test", "id_only_selection");
        PonderGuiSnapshotRegistry.registerDynamicProvider(id, indexed); PonderScene.OverlayEvent event = event(null, id);
        assertSame(indexed, PonderGuiSnapshotRegistry.providerFor(event)); assertEquals(0, indexed.opens);
        assertFalse(consumed().containsKey(indexed));
        assertSame(indexed.snapshot, PonderGuiSnapshotRegistry.resolve(event, 3)); assertTrue(consumed().containsKey(indexed));
        PonderGuiSnapshotRegistry.releaseProvider(PonderGuiSnapshotRegistry.providerFor(event));
        assertEquals(1, indexed.resets); assertFalse(consumed().containsKey(indexed));
        assertNull(PonderGuiSnapshotRegistry.providerFor(event)); assertNull(PonderGuiSnapshotRegistry.resolve(event, 3));
    }
}
