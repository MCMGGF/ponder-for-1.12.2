package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

/** Production callout preparation and input routing without a GL or Minecraft context. */
class PreviewForegroundCalloutTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void minimumSizeKeepsEachTargetsWholeTextAndImagesReachableThroughSeparatePages() throws Exception {
        PonderScene scene = scene();
        Vec3d first = new Vec3d(1, 1, 1);
        scene.createOverlayEvent(100).text(longText("FIRST")).pointAt(first);
        scene.createOverlayEvent(100).guiTexture(new ResourceLocation("foreground_test", "chest"),
            0, 0, 176, 166, 256, 256, 176, 166, 0, 0, true).overlayId("chest").pointAt(first);
        scene.createOverlayEvent(100).text(longText("SECOND")).pointAt(new Vec3d(3, 1, 1));
        int actorId = 1;
        for (double x : new double[] {0.5, 2.5, 4.5}) {
            for (double z : new double[] {0.5, 2.5, 4.5}) {
                scene.recordActorSpawn(actorId++, PonderScene.ActorKind.PLAYER, new Vec3d(x, 1, z), 1, 0, "");
            }
        }
        Viewer screen = viewer(scene, 320, 240, true);
        prepare(screen, scene);
        PonderDebugScreen.PreviewLayout model = screen.lastPreviewLayout;
        assertEquals(2, value(screen, "calloutGroupCount"));
        assertEquals(1, visible(screen).size());
        Object initial = visible(screen).get(0);
        assertEquals(0, PreviewCalloutLayout.intersectionArea(panel(initial), new PreviewDebugLayout.Rect(
            model.originX, model.originY, model.width, model.height)),
            "compact explanations have a dedicated foreground dock outside the fixed model frame");
        Object initialKey = value(initial, "key");
        PreviewCaptionPaging initialPaging = paging(initial);
        screen.mouseClicked(panel(initial).x + 30, panel(initial).y + 20, 0);
        screen.keyTyped('\0', Keyboard.KEY_NEXT);
        assertEquals(1, initialPaging.page());
        int initialPage = initialPaging.page();
        selectNextTarget(screen, initial);
        prepare(screen, scene);
        Object other = visible(screen).get(0);
        assertNotEquals(initialKey, value(other, "key"));
        assertEquals(value(other, "key"), value(screen, "focusedCallout"));
        assertEquals(0, paging(other).page());
        int nextLine = 0;
        int images = 0;
        PreviewCaptionPaging otherPaging = paging(other);
        for (int page = 0; page < otherPaging.pageCount(); page++) {
            prepare(screen, scene);
            assertSame(model, screen.lastPreviewLayout);
            if (otherPaging.imageIndex() < 0) {
                assertEquals(nextLine, otherPaging.firstLine());
                nextLine = otherPaging.endLine();
            } else {
                images++;
            }
            otherPaging.turn(1);
        }
        assertEquals(((List<?>) value(other, "lines")).size(), nextLine);
        assertEquals(1, images);
        selectNextTarget(screen, other);
        prepare(screen, scene);
        assertEquals(initialKey, value(visible(screen).get(0), "key"));
        assertEquals(initialPage, paging(visible(screen).get(0)).page());
    }

    @Test
    void targetSwitchMovesKeyboardFocusToTheCurrentlyVisibleTarget() throws Exception {
        PonderScene scene = twoTargets();
        Viewer screen = viewer(scene, 320, 240, true);
        prepare(screen, scene);
        Object first = visible(screen).get(0);
        selectNextTarget(screen, first);
        prepare(screen, scene);
        Object second = visible(screen).get(0);
        assertNotSame(first, second);
        screen.keyTyped('\0', Keyboard.KEY_NEXT);
        assertEquals(1, paging(second).page());
        assertEquals(0, paging(first).page());
        assertEquals(20, screen.getPlaybackTickValue());
    }

    @Test
    void clicksWheelsAndHeldDragsBelongToTheForegroundPanel() throws Exception {
        PonderScene scene = twoTargets();
        Viewer screen = viewer(scene, 320, 240, true);
        prepare(screen, scene);
        Object view = visible(screen).get(0);
        PreviewDebugLayout.Rect panel = panel(view);
        PreviewPointerInput pointer = (PreviewPointerInput) value(screen, "pointerInput");
        pointer.beginPreviewDrag();
        screen.playing = true;
        screen.mouseClicked(panel.x + 30, panel.y + 20, 0);
        assertFalse(pointer.isPreviewDragging());
        assertFalse(screen.playing);
        assertEquals(value(view, "key"), value(screen, "focusedCallout"));
        pointer.beginTimelineDrag();
        screen.playing = true;
        assertTrue((Boolean) invoke(screen, "handleCalloutWheel",
            new Class<?>[] { int.class, int.class, int.class }, -120, panel.x + 30, panel.y + 20));
        assertEquals(1, paging(view).page());
        assertEquals(20, screen.getPlaybackTickValue());
        assertFalse(screen.playing);
        assertFalse(pointer.isTimelineDragging());
        pointer.beginPreviewDrag();
        screen.mouseClickMove(panel.x + 30, panel.y + 20, 0, 100L);
        assertFalse(pointer.isPreviewDragging());
    }

    @Test
    void groupMenuPageKeysWinOverAnAlreadyFocusedCallout() throws Exception {
        PonderScene scene = twoTargets();
        Viewer screen = viewer(scene, 320, 240, true);
        prepare(screen, scene);
        Object view = visible(screen).get(0);
        screen.mouseClicked(panel(view).x + 30, panel(view).y + 20, 0);
        set(screen, "showcaseGroupSelectorOpen", true);
        PreviewGroupLayout menu = (PreviewGroupLayout) value(screen, "groupLayout");
        assertTrue(menu.configure(100, 28, 76, 196, 150));
        screen.keyTyped('\0', Keyboard.KEY_NEXT);
        assertEquals(1, menu.page());
        assertEquals(0, paging(view).page());
        assertEquals(20, screen.getPlaybackTickValue());
    }

    @Test
    void compactDebugTabSwitchClearsHiddenCalloutHitRegionsImmediately() throws Exception {
        PonderScene scene = twoTargets();
        Viewer screen = viewer(scene, 320, 240, false);
        prepare(screen, scene);
        assertFalse(visible(screen).isEmpty());
        Object view = visible(screen).get(0);
        set(screen, "focusedCallout", value(view, "key"));
        screen.actionPerformed(new GuiButton(11, 0, 0, "Details"));
        assertTrue(visible(screen).isEmpty());
        assertNull(value(screen, "focusedCallout"));
        assertNull(invoke(screen, "calloutAt", new Class<?>[] { int.class, int.class },
            panel(view).x + 1, panel(view).y + 1));
        assertNull(screen.lastPreviewLayout);
    }

    @Test
    void temporaryHoverNoticePreservesTeachingPagesAndTargetSelection() throws Exception {
        PonderScene scene = twoTargets();
        Viewer screen = viewer(scene, 320, 240, true);
        prepare(screen, scene);
        Object view = visible(screen).get(0);
        Object selected = value(view, "key");
        paging(view).turn(1);
        set(screen, "explanationHoverText", "Temporary model hint");
        prepare(screen, scene);
        assertEquals("system-note", value(visible(screen).get(0), "key"));
        assertSame(view, ((Map<?, ?>) value(screen, "calloutViews")).get(selected));
        set(screen, "explanationHoverText", null);
        prepare(screen, scene);
        assertSame(view, visible(screen).get(0));
        assertEquals(selected, value(screen, "selectedCallout"));
        assertEquals(1, paging(view).page());
    }

    @Test
    void pageKeysFollowTheVisibleNoticeAndReturnToTeachingWithoutSeekingTheScene() throws Exception {
        PonderScene scene = twoTargets();
        Viewer screen = viewer(scene, 320, 240, true);
        prepare(screen, scene);
        Object teaching = visible(screen).get(0);
        screen.mouseClicked(panel(teaching).x + 30, panel(teaching).y + 20, 0);
        assertEquals(value(teaching, "key"), value(screen, "focusedCallout"));
        set(screen, "explanationHoverText", longText("NOTICE"));
        prepare(screen, scene);
        Object notice = visible(screen).get(0);
        assertEquals("system-note", value(notice, "key"));
        assertEquals(0, paging(notice).page());
        screen.keyTyped('\0', Keyboard.KEY_NEXT);
        assertEquals(1, paging(notice).page());
        assertEquals(value(notice, "key"), value(screen, "focusedCallout"));
        assertEquals(0, paging(teaching).page());
        assertEquals(20, screen.getPlaybackTickValue());
        set(screen, "explanationHoverText", null);
        prepare(screen, scene);
        Object currentTeaching = visible(screen).get(0);
        assertNotEquals("system-note", value(currentTeaching, "key"));
        assertEquals(0, paging(currentTeaching).page());
        screen.keyTyped('\0', Keyboard.KEY_NEXT);
        assertEquals(1, paging(currentTeaching).page());
        assertEquals(value(currentTeaching, "key"), value(screen, "focusedCallout"));
        assertEquals(20, screen.getPlaybackTickValue());
    }

    @Test
    void aMenuNeverSharesPixelsWithAReadableForegroundPanel() throws Exception {
        PonderScene scene = twoTargets();
        Viewer screen = viewer(scene, 640, 360, true);
        set(screen, "showcaseGroupSelectorOpen", true);
        PreviewGroupLayout menu = (PreviewGroupLayout) value(screen, "groupLayout");
        assertTrue(menu.configure(100, 28, 76, 196, 270));
        prepare(screen, scene);
        assertFalse(visible(screen).isEmpty());
        for (Object view : visible(screen)) {
            assertEquals(0, PreviewCalloutLayout.intersectionArea(panel(view), menu.bounds));
            assertTrue(panel(view).width >= 100);
        }
    }

    @Test
    void panelsAvoidEveryTargetIncludingTargetsWhosePanelHasNotBeenPlacedYet() throws Exception {
        PonderScene scene = scene();
        scene.createOverlayEvent(100).text("FIRST").pointAt(new Vec3d(2.5, 1, 2.5));
        scene.createOverlayEvent(100).text("SECOND").pointAt(new Vec3d(2.5, 2.5, 2.5));
        Viewer screen = viewer(scene, 640, 360, true);
        Object firstKey = PreviewCalloutContent.collect(scene, 20F).get(0).key;
        set(screen, "selectedCallout", firstKey);
        set(screen, "focusedCallout", firstKey);
        PonderDebugScreen.PreviewLayout model = screen.lastPreviewLayout;
        prepare(screen, scene);
        assertEquals(2, visible(screen).size());
        Map<?, ?> views = (Map<?, ?>) value(screen, "calloutViews");
        Object first = views.get(firstKey);
        Object second = visible(screen).get(1);
        PreviewDebugLayout.Rect firstTarget = (PreviewDebugLayout.Rect) value(first, "targetBounds");
        PreviewDebugLayout.Rect secondTarget = (PreviewDebugLayout.Rect) value(second, "targetBounds");
        PreviewDebugLayout.Rect area = (PreviewDebugLayout.Rect) invoke(screen, "calloutArea", new Class<?>[0]);
        PreviewCalloutLayout.Placement unreserved = PreviewCalloutLayout.place(area, firstTarget,
            panel(first).width, panel(first).height, Collections.emptyList(), null);
        assertTrue(PreviewCalloutLayout.intersectionArea(unreserved.panel, secondTarget) > 0,
            "without reserving other targets, the first preferred panel would cover the second target");
        for (Object view : visible(screen)) {
            assertFalse(((PreviewCalloutLayout.Placement) value(view, "placement")).fallback);
            for (Object targetView : views.values()) {
                assertEquals(0, PreviewCalloutLayout.intersectionArea(panel(view),
                    (PreviewDebugLayout.Rect) value(targetView, "targetBounds")));
            }
        }
        assertSame(model, screen.lastPreviewLayout);
    }

    @Test
    void playerAvoidanceBoundsContainAllEightProjectedCornersAfterCameraRotation() throws Exception {
        PonderScene scene = scene();
        Vec3d position = new Vec3d(2.5, 1, 2.5);
        scene.recordActorSpawn(1, PonderScene.ActorKind.PLAYER, position, 1, 0, "");
        Viewer screen = viewer(scene, 640, 360, true);
        set(screen, "previewYaw", 45F);
        set(screen, "previewPitch", 30F);
        List<?> obstacles = (List<?>) invoke(screen, "calloutObstacles",
            new Class<?>[] { PonderScene.class, float.class }, scene, 20F);
        assertEquals(1, obstacles.size());
        PreviewDebugLayout.Rect body = (PreviewDebugLayout.Rect) obstacles.get(0);
        PonderScenePreview.PreviewBounds bounds = PonderScenePreview.computeBounds(scene);
        for (double x : new double[] {-0.5, 0.5}) {
            for (double y : new double[] {0, 2}) {
                for (double z : new double[] {-0.5, 0.5}) {
                    Object point = invoke(screen, "projectTransformedPoint", new Class<?>[] {
                        PonderScene.class, PonderScenePreview.PreviewBounds.class,
                        PonderDebugScreen.PreviewLayout.class, float.class, Vec3d.class
                    }, scene, bounds, screen.lastPreviewLayout, 20F, position.add(x, y, z));
                    assertTrue(body.contains((Integer) value(point, "x"), (Integer) value(point, "y")),
                        "every player corner must participate in foreground panel avoidance");
                }
            }
        }
    }

    @Test
    void controlsForSmallTargetsAvoidTheRestOfTheVisibleModelWithoutChangingItsCamera() throws Exception {
        PonderScene scene = modelWithControls();
        Viewer screen = viewer(scene, 750, 400, true);
        PonderDebugScreen.PreviewLayout model = screen.lastPreviewLayout;
        Object yaw = value(screen, "previewYaw"), pitch = value(screen, "previewPitch"), zoom = value(screen, "previewZoom");
        prepare(screen, scene);
        PreviewDebugLayout.Rect geometry = (PreviewDebugLayout.Rect) invoke(screen, "projectVisibleModelBounds",
            new Class<?>[] { PonderScene.class, float.class }, scene, 20F);
        assertNotNull(geometry);
        assertTrue(geometry.width < model.width, "reserve actual geometry, never the whole model viewport");
        assertEquals(2, visible(screen).size());
        for (Object view : visible(screen)) {
            assertFalse(((PreviewCalloutLayout.Placement) value(view, "placement")).fallback);
            assertEquals(0, PreviewCalloutLayout.intersectionArea(panel(view), geometry),
                "information about one tiny target must not cover neighbouring blocks");
        }
        assertSame(model, screen.lastPreviewLayout);
        assertEquals(yaw, value(screen, "previewYaw"));
        assertEquals(pitch, value(screen, "previewPitch"));
        assertEquals(zoom, value(screen, "previewZoom"));
    }

    @Test
    void minimumSizeStillProvidesAReadablePagedEdgePanelWhenTheModelOccupiesTheFreeSpace() throws Exception {
        PonderScene scene = modelWithControls();
        Viewer screen = viewer(scene, 320, 240, true);
        PonderDebugScreen.PreviewLayout model = screen.lastPreviewLayout;
        prepare(screen, scene);
        assertEquals(1, visible(screen).size());
        Object first = visible(screen).get(0);
        assertEquals(0, PreviewCalloutLayout.intersectionArea(panel(first), new PreviewDebugLayout.Rect(
            model.originX, model.originY, model.width, model.height)));
        assertTrue(panel(first).width >= 100);
        assertTrue(panel(first).height >= 52);
        assertEquals(2, value(screen, "calloutGroupCount"));
        Object key = value(first, "key");
        selectNextTarget(screen, first);
        prepare(screen, scene);
        assertNotEquals(key, value(visible(screen).get(0), "key"));
        assertSame(model, screen.lastPreviewLayout);
    }

    @Test
    void modelEnvelopeTracksMovedRotatedBlocksIgnoresHiddenBlocksAndReusesTheRuntimeCache() throws Exception {
        PonderScene scene = scene();
        BlockPos moved = new BlockPos(0, 0, 0), hidden = new BlockPos(200, 100, 200);
        scene.recordWorldEvent(PonderScene.WorldEventType.SET_BLOCK, moved, "minecraft:stone");
        scene.recordWorldEvent(PonderScene.WorldEventType.SET_BLOCK, hidden, "minecraft:stone");
        scene.recordSectionMove(Collections.singletonList(moved), new Vec3d(5, 2, 0), 10);
        scene.recordSectionRotation(Collections.singletonList(moved), Vec3d.ZERO, new Vec3d(0, 90, 0), 10);
        scene.recordWorldEvent(PonderScene.WorldEventType.HIDE_SECTION, Collections.singletonList(hidden), null);
        Viewer screen = viewer(scene, 750, 400, true);
        PreviewDebugLayout.Rect projected = (PreviewDebugLayout.Rect) invoke(screen, "projectVisibleModelBounds",
            new Class<?>[] { PonderScene.class, float.class }, scene, 20F);
        Object frame = invoke(screen, "getPreviewFrame", new Class<?>[] { PonderScene.class, float.class }, scene, 20F);
        AxisAlignedBB geometry = (AxisAlignedBB) value(frame, "calloutModelBounds");
        assertNotNull(geometry);
        assertTrue(geometry.maxX < 10 && geometry.maxY < 10 && geometry.maxZ < 10,
            "hidden distant blocks cannot expand the current visible envelope");
        PonderSceneRuntime.RuntimeState state = (PonderSceneRuntime.RuntimeState) value(frame, "state");
        PonderSceneRuntime.RuntimeBlockState runtime = state.blocksByPosition.get(moved);
        PonderScenePreview.PreviewBounds fixed = PonderScenePreview.computeBounds(scene);
        for (int corner = 0; corner < 8; corner++) {
            Vec3d point = PonderSceneRuntime.transformPoint(runtime,
                new Vec3d((corner & 1) == 0 ? 0 : 1, (corner & 2) == 0 ? 0 : 1, (corner & 4) == 0 ? 0 : 1));
            Object pixel = invoke(screen, "projectTransformedPoint", new Class<?>[] { PonderScene.class,
                PonderScenePreview.PreviewBounds.class, PonderDebugScreen.PreviewLayout.class, float.class, Vec3d.class },
                scene, fixed, screen.lastPreviewLayout, 20F, point);
            assertTrue(projected.contains((Integer) value(pixel, "x"), (Integer) value(pixel, "y")));
        }
        set(screen, "previewYaw", 15F);
        invoke(screen, "projectVisibleModelBounds", new Class<?>[] { PonderScene.class, float.class }, scene, 20F);
        assertSame(geometry, value(frame, "calloutModelBounds"), "camera changes project eight cached corners, not every block again");
    }

    @Test
    void nonRotatingMovesAndDirectionalFadeUseActualRuntimeCentersAndIgnoreDestroyedBlocks() throws Exception {
        PonderScene scene = scene();
        BlockPos moved = new BlockPos(0, 0, 0), destroyed = new BlockPos(100, 100, 100);
        scene.recordWorldEvent(PonderScene.WorldEventType.SET_BLOCK, moved, "minecraft:stone");
        scene.recordWorldEvent(PonderScene.WorldEventType.SET_BLOCK, destroyed, "minecraft:stone");
        scene.recordSectionMove(Collections.singletonList(moved), new Vec3d(4, 2, 0), 10);
        scene.recordWorldEvent(PonderScene.WorldEventType.SHOW_SECTION, Collections.singletonList(moved), null, EnumFacing.UP);
        scene.recordWorldEvent(PonderScene.WorldEventType.DESTROY_BLOCK, destroyed, null);
        Viewer screen = viewer(scene, 750, 400, true);
        invoke(screen, "projectVisibleModelBounds", new Class<?>[] { PonderScene.class, float.class }, scene, 5F);
        Object frame = invoke(screen, "getPreviewFrame", new Class<?>[] { PonderScene.class, float.class }, scene, 5F);
        PonderSceneRuntime.RuntimeState state = (PonderSceneRuntime.RuntimeState) value(frame, "state");
        PonderSceneRuntime.RuntimeBlockState runtime = state.blocksByPosition.get(moved);
        AxisAlignedBB geometry = (AxisAlignedBB) value(frame, "calloutModelBounds");
        assertTrue(runtime.fade > 0 && runtime.fade < 1);
        assertTrue(runtime.fadeOffsetY < 0, "this fixture exercises a nonzero appearing-section displacement");
        assertTrue(state.blocksByPosition.get(destroyed).destroyed);
        assertEquals(runtime.renderCenterX - 0.5, geometry.minX, 1.0E-8);
        assertEquals(runtime.renderCenterX + 0.5, geometry.maxX, 1.0E-8);
        assertEquals(runtime.renderCenterY - 0.5, geometry.minY, 1.0E-8);
        assertEquals(runtime.renderCenterY + 0.5, geometry.maxY, 1.0E-8);
        assertEquals(runtime.renderCenterZ - 0.5, geometry.minZ, 1.0E-8);
        assertEquals(runtime.renderCenterZ + 0.5, geometry.maxZ, 1.0E-8);
    }

    private static PonderScene modelWithControls() {
        PonderScene scene = scene();
        List<BlockPos> floor = new ArrayList<>();
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) floor.add(new BlockPos(x, 0, z));
        scene.recordWorldEvent(PonderScene.WorldEventType.SET_BLOCKS, floor, "minecraft:stone");
        scene.recordWorldEvent(PonderScene.WorldEventType.SET_BLOCK, new BlockPos(2, 1, 3), "minecraft:lever");
        scene.recordWorldEvent(PonderScene.WorldEventType.SET_BLOCK, new BlockPos(4, 1, 3), "minecraft:lever");
        scene.createOverlayEvent(100).controls(new Vec3d(2.5, 1.5, 3.5), Pointing.RIGHT).controlRightClick();
        scene.createOverlayEvent(100).controls(new Vec3d(4.5, 1.5, 3.5), Pointing.RIGHT).controlRightClick();
        return scene;
    }

    private static void selectNextTarget(Viewer screen, Object view) throws Exception {
        PreviewDebugLayout.Rect panel = panel(view);
        screen.mouseClicked(panel.right() - 10, panel.y + 6, 0);
    }

    private static PreviewCaptionPaging paging(Object view) throws Exception {
        return (PreviewCaptionPaging) value(view, "paging");
    }

    private static PreviewDebugLayout.Rect panel(Object view) throws Exception {
        PreviewCalloutLayout.Placement placement = (PreviewCalloutLayout.Placement) value(view, "placement");
        return placement.panel;
    }

    private static Viewer viewer(PonderScene scene, int width, int height, boolean showcase) throws Exception {
        Viewer screen = new Viewer(showcase);
        screen.width = width;
        screen.height = height;
        screen.fontRenderer = new PreviewTextLayoutTest.MetricsFont();
        PreviewExplanationLayout layout = showcase ? PreviewExplanationLayout.showcase(width, height)
            : PreviewExplanationLayout.debug(new PreviewDebugLayout.Rect(119, 69, 180, 108));
        set(screen, "compiledScenes", Collections.singletonList(scene));
        set(screen, "explanationLayout", layout);
        set(screen, "playbackTick", 20);
        Constructor<PonderDebugScreen.PreviewLayout> constructor = PonderDebugScreen.PreviewLayout.class
            .getDeclaredConstructor(int.class, int.class, int.class, int.class);
        constructor.setAccessible(true);
        screen.lastPreviewLayout = constructor.newInstance(layout.model.x, layout.model.y,
            layout.model.width, layout.model.height);
        return screen;
    }

    private static void prepare(Viewer screen, PonderScene scene) throws Exception {
        invoke(screen, "prepareForegroundCallouts", new Class<?>[] { PonderScene.class, float.class }, scene, 20F);
    }

    private static List<?> visible(PonderDebugScreen screen) throws Exception {
        return (List<?>) value(screen, "visibleCallouts");
    }

    private static PonderScene twoTargets() {
        PonderScene scene = scene();
        scene.createOverlayEvent(100).text(longText("FIRST")).pointAt(new Vec3d(1, 1, 1));
        scene.createOverlayEvent(100).text(longText("SECOND")).pointAt(new Vec3d(3, 1, 1));
        return scene;
    }

    private static String longText(String word) {
        return String.join(" ", Collections.nCopies(80, word));
    }

    private static PonderScene scene() {
        PonderScene result = new PonderScene(new PonderStoryBoardEntry((builder, util) -> {}, "foreground_test",
            new ResourceLocation("foreground_test", "scene"), new ResourceLocation("foreground_test", "component")),
            new PonderLocalization());
        result.setBasePlateSize(5);
        return result;
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = PonderDebugScreen.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Object value(Object target, String name) throws Exception {
        Field field = field(target, name);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target, name).set(target, value);
    }

    private static Field field(Object target, String name) throws Exception {
        Class<?> owner = target instanceof PonderDebugScreen ? PonderDebugScreen.class : target.getClass();
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class Viewer extends PonderDebugScreen {
        Viewer(boolean showcase) {
            super(null, 0, showcase, null);
        }
    }
}
