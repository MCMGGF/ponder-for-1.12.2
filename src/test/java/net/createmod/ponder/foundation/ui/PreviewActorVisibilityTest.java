package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.createmod.ponder.foundation.PonderScene.ActorEvent;
import net.createmod.ponder.foundation.PonderScene.ActorEventType;
import net.createmod.ponder.foundation.PonderScene.ActorKind;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PreviewActorVisibilityTest {
    @BeforeAll
    static void initialize() { Bootstrap.register(); }

    @Test
    void anActorOnlySceneRendersForEverySupportedActorKind() {
        for (ActorKind kind : ActorKind.values()) {
            List<ActorEvent> events = Collections.singletonList(event(4, 10, 1, kind, ActorEventType.SPAWN));
            assertFalse(PreviewActorVisibility.anyVisible(events, 3));
            assertTrue(PreviewActorVisibility.anyVisible(events, 4));
            assertTrue(PreviewActorVisibility.anyVisible(events, 40));
        }
    }

    @Test
    void hideFadeAndBackwardSeekMatchTheRuntimePlusOneProgress() {
        List<ActorEvent> events = Arrays.asList(event(0, 1, 1, ActorKind.ITEM, ActorEventType.SPAWN),
            event(8, 3, 1, ActorKind.ITEM, ActorEventType.HIDE));
        assertTrue(PreviewActorVisibility.anyVisible(events, 8));
        assertTrue(PreviewActorVisibility.anyVisible(events, 9));
        assertFalse(PreviewActorVisibility.anyVisible(events, 10));
        assertFalse(PreviewActorVisibility.anyVisible(events, 80));
        assertTrue(PreviewActorVisibility.anyVisible(events, 9));
    }

    @Test
    void hidingOneActorDoesNotHideAnother() {
        List<ActorEvent> events = Arrays.asList(event(0, 1, 1, ActorKind.PLAYER, ActorEventType.SPAWN),
            event(1, 1, 2, ActorKind.CART, ActorEventType.SPAWN),
            event(2, 1, 1, ActorKind.PLAYER, ActorEventType.HIDE));
        assertTrue(PreviewActorVisibility.anyVisible(events, 20));
    }

    @Test
    void respawningAnActorResetsItsHiddenLifetime() {
        List<ActorEvent> events = Arrays.asList(event(0, 1, 1, ActorKind.ITEM, ActorEventType.SPAWN),
            event(2, 1, 1, ActorKind.ITEM, ActorEventType.HIDE),
            event(4, 1, 1, ActorKind.ITEM, ActorEventType.SPAWN));
        assertFalse(PreviewActorVisibility.anyVisible(events, 3));
        assertTrue(PreviewActorVisibility.anyVisible(events, 4));
    }

    @Test
    void hideAndMovementDoNotInventAnUnspawnedActor() {
        assertFalse(PreviewActorVisibility.anyVisible(Arrays.asList(
            event(0, 10, 1, ActorKind.ITEM, ActorEventType.HIDE),
            event(1, 10, 1, ActorKind.ITEM, ActorEventType.MOVE)), 5));
    }

    @Test
    void nonpositiveHideDurationIsImmediateLikeTheExistingRuntime() {
        assertFalse(PreviewActorVisibility.anyVisible(Arrays.asList(
            event(0, 1, 1, ActorKind.BIRB, ActorEventType.SPAWN),
            event(1, 0, 1, ActorKind.BIRB, ActorEventType.HIDE)), 1));
    }

    @Test
    void emptyAndNonfiniteInputsNeverInventVisibleActors() {
        assertFalse(PreviewActorVisibility.anyVisible(null, 1));
        assertFalse(PreviewActorVisibility.anyVisible(Collections.emptyList(), 1));
        List<ActorEvent> events = Collections.singletonList(event(0, 1, 1, ActorKind.PLAYER, ActorEventType.SPAWN));
        assertFalse(PreviewActorVisibility.anyVisible(events, Float.NaN));
        assertFalse(PreviewActorVisibility.anyVisible(events, Float.POSITIVE_INFINITY));
    }

    private static ActorEvent event(int tick, int duration, int id, ActorKind kind, ActorEventType type) {
        return new ActorEvent(tick, duration, id, kind, type, Vec3d.ZERO, Vec3d.ZERO, Vec3d.ZERO,
            0F, null, null, ItemStack.EMPTY);
    }
}
