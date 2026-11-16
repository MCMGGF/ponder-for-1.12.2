package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.Function;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.OverlayEvent;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PreviewCalloutContentTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void equalPointTargetsShareOneGroupAndDifferentTargetsStaySeparate() {
        PonderScene scene = scene();
        Vec3d first = new Vec3d(1, 2, 3);
        scene.createOverlayEvent(100).text("FIRST").pointAt(first);
        scene.createOverlayEvent(100).controls(new Vec3d(1, 2, 3), Pointing.RIGHT).controlRightClick();
        scene.createOverlayEvent(100).text("SECOND").pointAt(new Vec3d(4, 2, 3));
        List<PreviewCalloutContent.Group> groups = collect(scene, 20);
        assertEquals(2, groups.size());
        assertEquals(2, groups.get(0).events.size());
        assertEquals("FIRST\n\n\u53f3\u952e", groups.get(0).text());
        assertEquals("SECOND", groups.get(1).text());
        assertEquals(groups.get(0).key, collect(scene, 21).get(0).key);
        assertThrows(UnsupportedOperationException.class, () -> groups.get(0).events.clear());
    }

    @Test
    void guiDescendantsAndHighlightUseTheirRootTargetEvenIfChildHasAnotherPoint() {
        PonderScene scene = scene();
        Vec3d furnace = new Vec3d(1, 1, 1);
        scene.createOverlayEvent(100).text("FURNACE").pointAt(furnace);
        OverlayEvent child = texture(scene, "child").parentOverlayId("root").pointAt(new Vec3d(8, 8, 8));
        OverlayEvent highlight = scene.createOverlayEvent(100).guiHighlight("child", 0, 0, 8, 8).text("FUEL");
        OverlayEvent root = texture(scene, "root").pointAt(furnace);
        List<PreviewCalloutContent.Group> groups = collect(scene, 20);
        assertEquals(1, groups.size());
        assertEquals(furnace, groups.get(0).pointAt);
        assertEquals(4, groups.get(0).events.size());
        assertTrue(groups.get(0).guiEvents().containsAll(java.util.Arrays.asList(root, child, highlight)));
        assertEquals("FURNACE\n\nFUEL", groups.get(0).text());
    }

    @Test
    void exactSelectionsAreOrderIndependentButNeverMergeDifferentHolesOrMovingTargets() {
        PonderScene scene = scene();
        BlockPos first = new BlockPos(1, 1, 1);
        BlockPos last = new BlockPos(3, 1, 1);
        scene.createOverlayEvent(100).text("SPARSE")
            .targetPositions(java.util.Arrays.asList(first, last));
        scene.createOverlayEvent(100).text("SAME")
            .targetPositions(java.util.Arrays.asList(last, first, first));
        scene.createOverlayEvent(100).text("SOLID")
            .targetPositions(java.util.Arrays.asList(first, new BlockPos(2, 1, 1), last));
        List<PreviewCalloutContent.Group> groups = collect(scene, 20);
        assertEquals(2, groups.size());
        assertEquals("SPARSE\n\nSAME", groups.get(0).text());
        assertEquals(2, groups.get(0).targetPositions.size());
        assertEquals("SOLID", groups.get(1).text());
        assertThrows(UnsupportedOperationException.class, () -> groups.get(0).targetPositions.clear());
    }

    @Test
    void legacyPointOnlyTargetsRetainSubBlockPrecision() {
        PonderScene scene = scene();
        scene.createOverlayEvent(100).text("FIRST").pointAt(new Vec3d(1.1, 1, 1))
            .targetPositions(java.util.Collections.emptyList());
        scene.createOverlayEvent(100).text("SECOND").pointAt(new Vec3d(1.2, 1, 1))
            .targetPositions(java.util.Collections.emptyList());
        assertEquals(2, collect(scene, 20).size());
    }

    @Test
    void untargetedGuiRootsDoNotMergeIntoOneAnonymousCaption() {
        PonderScene scene = scene();
        texture(scene, "chest");
        texture(scene, "furnace");
        scene.createOverlayEvent(100).text("INTRO");
        List<PreviewCalloutContent.Group> groups = collect(scene, 20);
        assertEquals(3, groups.size());
        assertNotEquals(groups.get(0).key, groups.get(1).key);
        assertEquals("INTRO", groups.get(2).text());
    }

    @Test
    void guiCycleStaysIntactAndCanStillBeDisplayed() {
        PonderScene scene = scene();
        texture(scene, "one").parentOverlayId("two");
        texture(scene, "two").parentOverlayId("one");
        scene.createOverlayEvent(100).guiHighlight("one", 0, 0, 8, 8).text("DETAIL");
        List<PreviewCalloutContent.Group> groups = collect(scene, 20);
        assertEquals(1, groups.size());
        assertEquals(3, groups.get(0).events.size());
        assertEquals("DETAIL", groups.get(0).text());
    }

    @Test
    void inactiveEventsAndDiagnosticActorNamesNeverBecomeTeachingText() {
        PonderScene scene = scene();
        scene.createOverlayEvent(5).text("EXPIRED");
        scene.createOverlayEvent(100).text("CURRENT");
        scene.recordPointOfInterest(Vec3d.ZERO);
        scene.recordItemActorSpawn(1, Vec3d.ZERO, 100, "INTERNAL_ACTOR", net.minecraft.item.ItemStack.EMPTY);
        List<PreviewCalloutContent.Group> groups = collect(scene, 20);
        assertEquals(1, groups.size());
        assertEquals("CURRENT", groups.get(0).text());
        assertEquals(2, collect(scene, 5).get(0).events.size());
        assertTrue(collect(scene, 101).isEmpty());
    }

    @Test
    void controlLabelsUseLocalizationAndNeverExposePointingEnumNames() {
        PonderScene scene = scene();
        OverlayEvent controls = scene.createOverlayEvent(100).controls(Vec3d.ZERO, Pointing.RIGHT)
            .controlLeftClick().controlRightClick().controlScroll().controlSneaking().controlCtrl();
        String english = PreviewCalloutContent.controlDescription(controls,
            key -> key.substring(key.lastIndexOf('.') + 1));
        assertEquals("left_click  right_click  scroll  sneak  ctrl", english);
        String fallback = PreviewCalloutContent.controlDescription(controls, Function.identity());
        assertEquals("\u5de6\u952e  \u53f3\u952e  \u6eda\u8f6e  \u6f5c\u884c  Ctrl", fallback);
        assertFalse(fallback.contains("RIGHT"));
    }

    @Test
    void unavailableLocalizationStillLeavesControlsReadable() {
        OverlayEvent controls = scene().createOverlayEvent(100).controls(Vec3d.ZERO, Pointing.LEFT)
            .controlRightClick();
        assertEquals("\u53f3\u952e", PreviewCalloutContent.controlDescription(controls, key -> {
            throw new IllegalStateException("locale unavailable");
        }));
    }

    private static List<PreviewCalloutContent.Group> collect(PonderScene scene, float tick) {
        return PreviewCalloutContent.collect(scene, tick, Function.identity());
    }

    private static OverlayEvent texture(PonderScene scene, String id) {
        return scene.createOverlayEvent(100).guiTexture(new ResourceLocation("callout_test", id),
            0, 0, 100, 50, 100, 50, 100, 50, 0, 0, true).overlayId(id);
    }

    private static PonderScene scene() {
        return new PonderScene(new PonderStoryBoardEntry((builder, util) -> {}, "callout_test",
            new ResourceLocation("callout_test", "scene"), new ResourceLocation("callout_test", "component")),
            new PonderLocalization());
    }
}
