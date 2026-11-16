package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSchematic;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

class PonderViewerIndexRefreshTest {

    @BeforeAll
    static void registerBlocks() {
        Bootstrap.register();
    }

    @Test
    void refreshPreservesMachineScenePlaybackAndCameraAcrossReordering() throws Exception {
        Viewer screen = new Viewer(id("b"), 1);
        screen.publish(Arrays.asList(id("a"), id("b")), id("b"),
            scene("first", 5, 120), scene("selected", 5, 120));
        assertTrue(screen.synchronizeIndex());
        PonderScene previous = screen.getSelectedScene();
        set(screen, "playbackTick", 57);
        screen.playing = false;
        camera(screen, 81F, 42F, 1.8F);
        PreviewModelFraming framing = framing(screen);
        framing.initialize(0, 0, 0, 5, 1, 5);
        framing.include(-2, -1, -3, 7, 4, 8, false, 800, 600, false, 1F);
        double centerX = framing.centerX();
        float scale = framing.fitScale(800, 600, false, 1F);

        screen.publish(Arrays.asList(id("new"), id("b"), id("a")), id("b"),
            scene("selected", 5, 120), scene("inserted", 5, 120), scene("first", 5, 120));
        assertTrue(screen.synchronizeIndex());

        assertEquals(id("b"), screen.getSelectedComponentId());
        assertEquals(id("selected"), screen.getSelectedScene().getSceneId());
        assertEquals(0, screen.getSelectedSceneIndex());
        assertNotSame(previous, screen.getSelectedScene());
        assertEquals(57, screen.getPlaybackTickValue());
        assertFalse(screen.isPlaybackRunning());
        assertCamera(screen, 81F, 42F, 1.8F);
        assertEquals(centerX, framing(screen).centerX());
        assertEquals(scale, framing(screen).fitScale(800, 600, false, 1F));
        assertEquals(1, screen.cameraResets);

        for (int frame = 0; frame < 200; frame++) {
            assertFalse(screen.synchronizeIndex());
            screen.updateScreen();
        }
        assertEquals(2, screen.compilations);
        assertEquals(57, screen.getPlaybackTickValue());
        assertCamera(screen, 81F, 42F, 1.8F);
    }

    @Test
    void changedGeometryFitsOnceWithoutResettingManualRotation() throws Exception {
        Viewer screen = open("machine", scene("selected", 5, 120));
        camera(screen, -130F, 52F, 2F);
        set(screen, "playbackTick", 100);
        screen.playing = true;
        screen.publish(Collections.singletonList(id("machine")), id("machine"), scene("selected", 9, 60));

        screen.synchronizeIndex();

        assertCamera(screen, -130F, 52F, 1F);
        assertEquals(60, screen.getPlaybackTickValue());
        assertTrue(screen.isPlaybackRunning());
        assertEquals(1, screen.cameraResets);
        Method framingMethod = PonderDebugScreen.class.getDeclaredMethod("getModelFraming",
            PonderScenePreview.PreviewBounds.class);
        framingMethod.setAccessible(true);
        PreviewModelFraming fitted = (PreviewModelFraming) framingMethod.invoke(screen,
            PonderScenePreview.computeBounds(screen.getSelectedScene()));
        assertEquals(4.5D, fitted.centerX());
        set(screen, "previewZoom", 1.7F);
        for (int frame = 0; frame < 100; frame++) {
            assertFalse(screen.synchronizeIndex());
        }
        assertCamera(screen, -130F, 52F, 1.7F);
        assertEquals(2, screen.compilations);
    }

    @Test
    void removedMachineFallsBackOnceAndEmptyIndexClearsTheView() throws Exception {
        Viewer screen = open("removed", scene("selected", 5, 120));
        camera(screen, 90F, 42F, 2F);
        screen.publish(Collections.singletonList(id("remaining")), id("remaining"),
            scene("selected", 5, 120));
        screen.synchronizeIndex();

        assertEquals(id("remaining"), screen.getSelectedComponentId());
        assertEquals(2, screen.cameraResets);
        assertEquals(0, screen.getPlaybackTickValue());
        assertFalse(screen.isPlaybackRunning());
        assertFalse((Boolean) get(screen, "previewCameraManual"));
        assertFalse(screen.getRegistryStatus("").isEmpty());

        screen.components = Collections.emptyList();
        screen.generation++;
        screen.synchronizeIndex();
        assertNull(screen.getSelectedComponentId());
        assertNull(screen.getSelectedScene());
        assertEquals(0, screen.getCompiledSceneCount());
        assertFalse(screen.isPlaybackRunning());
        for (int frame = 0; frame < 100; frame++) {
            screen.updateScreen();
        }
        assertEquals(2, screen.compilations);
    }

    @Test
    void failedCompilationAndInvalidBoundsClearOldScenesWithoutFrameRetries() throws Exception {
        Viewer screen = open("machine", scene("selected", 5, 120));
        screen.compileFailure = new IllegalStateException("injected compile failure");
        screen.generation++;
        screen.synchronizeIndex();
        assertNull(screen.getSelectedScene());
        assertFalse(screen.isPlaybackRunning());
        assertTrue(screen.getRegistryStatus("").contains("injected compile failure"));
        for (int frame = 0; frame < 30; frame++) {
            screen.updateScreen();
        }
        assertEquals(2, screen.compilations);

        screen.compileFailure = null;
        PonderScene invalid = new PonderScene(entry("invalid"), new PonderLocalization()) {
            @Override
            public PonderSchematic getSchematic() {
                throw new IllegalStateException("injected bounds failure");
            }
        };
        invalid.setSceneId(id("invalid"));
        screen.publish(Collections.singletonList(id("machine")), id("machine"), invalid);
        screen.synchronizeIndex();
        assertNull(screen.getSelectedScene());
        assertTrue(screen.getRegistryStatus("").contains("injected bounds failure"));
        for (int frame = 0; frame < 30; frame++) {
            screen.updateScreen();
        }
        assertEquals(3, screen.compilations);

        screen.publish(Collections.singletonList(id("machine")), id("machine"), scene("fixed", 5, 120));
        screen.synchronizeIndex();
        assertEquals(id("fixed"), screen.getSelectedScene().getSceneId());
        assertEquals(4, screen.compilations);
    }

    @Test
    void closeReleasesOwnedObjectsAndReopenUsesOnlyTheBookmark() throws Exception {
        Viewer screen = open("machine", scene("selected", 5, 120));
        PonderScene previous = screen.getSelectedScene();
        camera(screen, 33F, 44F, 1.4F);
        set(screen, "playbackTick", 48);
        screen.playing = false;
        DetachedPonderWorld world = new DetachedPonderWorld();
        world.putBlock(BlockPos.ORIGIN, Blocks.STONE.getDefaultState(), null);
        set(screen, "detachedPreviewWorld", world);
        PreviewOverlayHitTest hits = (PreviewOverlayHitTest) get(screen, "overlayHitAreas");
        hits.add(0, 0, 40, 40);
        PreviewModelMode<?> mode = (PreviewModelMode<?>) get(screen, "modelMode");
        mode.fail("old model failure");
        assertTrue(mode.wasAttempted());

        screen.onGuiClosed();

        assertNull(screen.getSelectedScene());
        assertNull(screen.getSelectedComponentId());
        assertTrue(((Map<?, ?>) get(screen, "sceneEndTicks")).isEmpty());
        assertNull(get(screen, "detachedPreviewWorld"));
        assertSame(Blocks.AIR.getDefaultState(), world.getBlockState(BlockPos.ORIGIN));
        assertFalse(mode.wasAttempted());
        assertFalse(hits.contains(10, 10));
        Object bookmark = get(screen, "closedView");
        assertNotNull(bookmark);
        for (Field field : bookmark.getClass().getDeclaredFields()) {
            assertFalse(PonderScene.class.isAssignableFrom(field.getType()), field.getName());
            assertFalse(net.minecraft.world.World.class.isAssignableFrom(field.getType()), field.getName());
        }

        screen.scenes.put(id("machine"), Collections.singletonList(scene("selected", 5, 120)));
        assertTrue(screen.synchronizeIndex());
        assertNotSame(previous, screen.getSelectedScene());
        assertEquals(id("selected"), screen.getSelectedScene().getSceneId());
        assertEquals(48, screen.getPlaybackTickValue());
        assertFalse(screen.isPlaybackRunning());
        assertCamera(screen, 33F, 44F, 1.4F);
        assertEquals(2, screen.compilations);
        assertEquals(1, screen.cameraResets);
        assertNull(get(screen, "closedView"));
    }

    @Test
    void aClickAcrossGenerationChangeCannotReachStaleControls() throws Exception {
        Viewer screen = open("machine", scene("selected", 5, 120));
        PreviewOverlayHitTest hits = (PreviewOverlayHitTest) get(screen, "overlayHitAreas");
        hits.add(0, 0, 40, 40);
        screen.publish(Collections.singletonList(id("machine")), id("machine"), scene("selected", 5, 120));
        screen.mouseClicked(10, 10, 0);
        assertFalse(hits.contains(10, 10));
        assertEquals(2, screen.compilations);
    }

    @Test
    void unavailableSceneNavigationPreservesTheEntireCurrentView() throws Exception {
        for (boolean showcase : new boolean[] { false, true }) {
            for (int count : new int[] { 0, 1, 3 }) {
                for (int key : new int[] { Keyboard.KEY_LEFT, Keyboard.KEY_RIGHT }) {
                    int selected = key == Keyboard.KEY_LEFT ? 0 : Math.max(0, count - 1);
                    Viewer screen = new Viewer(id("machine"), selected, showcase);
                    PonderScene[] scenes = new PonderScene[count];
                    for (int i = 0; i < count; i++) {
                        scenes[i] = scene("scene_" + i, 5, 120);
                    }
                    screen.publish(Collections.singletonList(id("machine")), id("machine"), scenes);
                    screen.synchronizeIndex();
                    assertUnavailableNavigationPreservesView(screen, key, false);
                    assertUnavailableNavigationPreservesView(screen, key, true);
                }
            }
        }
    }

    @Test
    void availableSceneNavigationStillResetsAndSelectsTheAdjacentScene() throws Exception {
        for (boolean showcase : new boolean[] { false, true }) {
            Viewer screen = new Viewer(id("machine"), 1, showcase);
            PonderScene first = scene("first", 5, 120);
            PonderScene middle = scene("middle", 5, 120);
            PonderScene last = scene("last", 5, 120);
            screen.publish(Collections.singletonList(id("machine")), id("machine"), first, middle, last);
            screen.synchronizeIndex();
            screen.prevSceneButton = new GuiButton(PonderDebugScreen.BUTTON_PREV_SCENE, 0, 0, "Previous");
            screen.nextSceneButton = new GuiButton(PonderDebugScreen.BUTTON_NEXT_SCENE, 0, 0, "Next");
            int resets = screen.cameraResets;
            for (int index : new int[] { 0, 1, 2 }) {
                set(screen, "playbackTick", 57);
                screen.playing = !showcase;
                camera(screen, 81F, 42F, 1.8F);
                screen.keyTyped('\0', index == 0 ? Keyboard.KEY_LEFT : Keyboard.KEY_RIGHT);
                assertSame(Arrays.asList(first, middle, last).get(index), screen.getSelectedScene());
                assertEquals(index, screen.getSelectedSceneIndex());
                assertEquals(0, screen.getPlaybackTickValue());
                assertEquals(showcase, screen.isPlaybackRunning());
                assertFalse((Boolean) get(screen, "previewCameraManual"));
                assertEquals(1F, (Float) get(screen, "previewZoom"));
                assertEquals(++resets, screen.cameraResets);
                assertEquals(index > 0, screen.prevSceneButton.enabled);
                assertEquals(index < 2, screen.nextSceneButton.enabled);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertUnavailableNavigationPreservesView(Viewer screen, int key, boolean playing)
        throws Exception {
        PonderScene selected = screen.getSelectedScene();
        int selectedIndex = screen.getSelectedSceneIndex();
        int resets = screen.cameraResets;
        int compilations = screen.compilations;
        set(screen, "playbackTick", selected == null ? 0 : 57);
        screen.playing = playing;
        camera(screen, 81F, 42F, 1.8F);
        set(screen, "operationScroll", 3);
        set(screen, "summaryScroll", 2);
        set(screen, "registryNoticeTicks", 80);
        set(screen, "showcaseGroupSelectorOpen", false);
        PreviewModelFraming framing = framing(screen);
        framing.initialize(0, 0, 0, 5, 1, 5);
        framing.include(-2, -1, -3, 7, 4, 8, false, 800, 600, false, 1F);
        double centerX = framing.centerX();
        float scale = framing.fitScale(800, 600, false, 1F);
        DetachedPonderWorld world = new DetachedPonderWorld();
        world.putBlock(BlockPos.ORIGIN, Blocks.STONE.getDefaultState(), null);
        set(screen, "detachedPreviewWorld", world);
        PreviewModelMode<Object> mode = (PreviewModelMode<Object>) get(screen, "modelMode");
        mode.clear();
        Object preview = new Object();
        mode.chooseAppearance(() -> preview, value -> true, value -> "owned preview");
        PreviewCaptionPaging paging = (PreviewCaptionPaging) get(screen, "captionPaging");
        paging.configure(new Object(), 30, 58);
        paging.turn(2);
        PreviewDebugLayout.Rect caption = new PreviewDebugLayout.Rect(20, 20, 120, 58);
        set(screen, "captionBounds", caption);
        set(screen, "captionFocused", true);
        PreviewPointerInput pointer = (PreviewPointerInput) get(screen, "pointerInput");
        pointer.beginPreviewDrag();
        PreviewOverlayHitTest hits = (PreviewOverlayHitTest) get(screen, "overlayHitAreas");
        hits.add(20, 20, 120, 58);

        screen.keyTyped('\0', key);

        assertSame(selected, screen.getSelectedScene());
        assertEquals(selectedIndex, screen.getSelectedSceneIndex());
        assertEquals(selected == null ? 0 : 57, screen.getPlaybackTickValue());
        assertEquals(playing, screen.isPlaybackRunning());
        assertCamera(screen, 81F, 42F, 1.8F);
        assertEquals(resets, screen.cameraResets);
        assertEquals(compilations, screen.compilations);
        assertEquals(3, get(screen, "operationScroll"));
        assertEquals(2, get(screen, "summaryScroll"));
        assertEquals(80, get(screen, "registryNoticeTicks"));
        assertFalse((Boolean) get(screen, "showcaseGroupSelectorOpen"));
        assertEquals(centerX, framing.centerX());
        assertEquals(scale, framing.fitScale(800, 600, false, 1F));
        assertSame(world, get(screen, "detachedPreviewWorld"));
        assertSame(Blocks.STONE.getDefaultState(), world.getBlockState(BlockPos.ORIGIN));
        assertSame(preview, mode.getPreview());
        assertTrue(mode.isAppearance());
        assertEquals(2, paging.page());
        assertSame(caption, get(screen, "captionBounds"));
        assertTrue((Boolean) get(screen, "captionFocused"));
        assertTrue(pointer.isPreviewDragging());
        assertTrue(hits.contains(30, 30));
    }

    @Test
    void manualComponentCompilationFailuresReleaseOldPreviewAndAllowAnotherChoice() throws Exception {
        for (boolean linkageFailure : new boolean[] { false, true }) {
            Viewer screen = manualViewer(scene("broken", 5, 120));
            if (linkageFailure) {
                screen.compileLinkageFailure = new NoClassDefFoundError("injected missing dependency");
            } else {
                screen.compileFailure = new IllegalStateException("injected storyboard failure");
            }
            verifyManualFailure(screen, linkageFailure ? "injected missing dependency" : "injected storyboard failure");
        }
    }

    @Test
    void manualComponentBoundsFailureReleasesOldPreviewWithoutAutomaticRetries() throws Exception {
        PonderScene invalid = new PonderScene(entry("broken"), new PonderLocalization()) {
            @Override
            public PonderSchematic getSchematic() {
                throw new IllegalArgumentException("injected manual bounds failure");
            }
        };
        invalid.setSceneId(id("broken"));
        verifyManualFailure(manualViewer(invalid), "injected manual bounds failure");
    }

    private static Viewer manualViewer(PonderScene brokenScene) {
        Viewer screen = new Viewer(id("working"), 0, true);
        screen.publish(Arrays.asList(id("working"), id("broken")), id("working"), scene("working", 5, 120));
        screen.scenes.put(id("broken"), Collections.singletonList(brokenScene));
        screen.synchronizeIndex();
        return screen;
    }

    @SuppressWarnings("unchecked")
    private static void verifyManualFailure(Viewer screen, String reason) throws Exception {
        PonderScene oldScene = screen.getSelectedScene();
        DetachedPonderWorld world = new DetachedPonderWorld();
        world.putBlock(BlockPos.ORIGIN, Blocks.STONE.getDefaultState(), null);
        set(screen, "detachedPreviewWorld", world);
        PreviewModelMode<Object> mode = (PreviewModelMode<Object>) get(screen, "modelMode");
        mode.chooseAppearance(Object::new, value -> true, value -> "owned preview");
        PreviewOverlayHitTest hits = (PreviewOverlayHitTest) get(screen, "overlayHitAreas");
        hits.add(0, 0, 40, 40);
        set(screen, "modelControls", new PreviewModelControls(20, 200));
        set(screen, "lastPlaybackBarWidth", 120);
        set(screen, "playbackTick", 65);
        screen.playPauseButton = new GuiButton(3, 0, 0, 20, 20, "Pause");
        assertTrue(screen.isPlaybackRunning());

        Method select = PonderDebugScreen.class.getDeclaredMethod("selectComponent", ResourceLocation.class, int.class);
        select.setAccessible(true);
        select.invoke(screen, id("broken"), 0);

        assertEquals(id("broken"), screen.getSelectedComponentId());
        assertNull(screen.getSelectedScene());
        assertEquals(0, screen.getCompiledSceneCount());
        assertEquals(0, screen.getPlaybackTickValue());
        assertFalse(screen.isPlaybackRunning());
        assertFalse(screen.playPauseButton.enabled);
        assertTrue(screen.getRegistryStatus("").contains(reason));
        assertNull(get(screen, "detachedPreviewWorld"));
        assertSame(Blocks.AIR.getDefaultState(), world.getBlockState(BlockPos.ORIGIN));
        assertNull(mode.getPreview());
        assertFalse(mode.wasAttempted());
        assertNull(get(screen, "modelControls"));
        assertFalse(hits.contains(10, 10));
        assertEquals(0, get(screen, "lastPlaybackBarWidth"));
        assertTrue(((Map<?, ?>) get(screen, "sceneEndTicks")).isEmpty());
        for (int tick = 0; tick < 100; tick++) {
            screen.updateScreen();
            assertFalse(screen.synchronizeIndex());
        }
        assertEquals(2, screen.compilations);
        assertFalse(screen.isPlaybackRunning());

        screen.compileFailure = null;
        screen.compileLinkageFailure = null;
        select.invoke(screen, id("working"), 0);
        assertSame(oldScene, screen.getSelectedScene());
        assertEquals(id("working"), screen.getSelectedComponentId());
        assertTrue(screen.isPlaybackRunning());
        assertTrue(screen.playPauseButton.enabled);
        assertEquals("", screen.getRegistryStatus(""));
        assertEquals(3, screen.compilations);
    }

    private static Viewer open(String machine, PonderScene scene) {
        Viewer screen = new Viewer(id(machine), 0);
        screen.publish(Collections.singletonList(id(machine)), id(machine), scene);
        screen.synchronizeIndex();
        return screen;
    }

    private static void camera(Viewer screen, float yaw, float pitch, float zoom) throws Exception {
        set(screen, "previewYaw", yaw);
        set(screen, "previewPitch", pitch);
        set(screen, "previewZoom", zoom);
        set(screen, "previewCameraManual", true);
    }

    private static void assertCamera(Viewer screen, float yaw, float pitch, float zoom) throws Exception {
        assertEquals(yaw, (Float) get(screen, "previewYaw"));
        assertEquals(pitch, (Float) get(screen, "previewPitch"));
        assertEquals(zoom, (Float) get(screen, "previewZoom"));
        assertTrue((Boolean) get(screen, "previewCameraManual"));
    }

    private static PreviewModelFraming framing(Viewer screen) throws Exception {
        return (PreviewModelFraming) get(screen, "modelFraming");
    }

    private static Object get(Viewer screen, String name) throws Exception {
        Field field = PonderDebugScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }

    private static void set(Viewer screen, String name, Object value) throws Exception {
        Field field = PonderDebugScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(screen, value);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("reload_ui", path);
    }

    private static PonderStoryBoardEntry entry(String name) {
        return new PonderStoryBoardEntry((scene, util) -> {}, "reload_ui", id(name), id("machine"));
    }

    private static PonderScene scene(String name, int size, int ticks) {
        PonderScene scene = new PonderScene(entry(name), new PonderLocalization());
        scene.setSceneId(id(name));
        scene.setBasePlateSize(size);
        scene.addIdleTicks(ticks);
        return scene;
    }

    private static final class Viewer extends PonderDebugScreen {
        private long generation;
        private List<ResourceLocation> components = Collections.emptyList();
        private final Map<ResourceLocation, List<PonderScene>> scenes = new LinkedHashMap<>();
        private int compilations;
        private int cameraResets;
        private RuntimeException compileFailure;
        private LinkageError compileLinkageFailure;

        Viewer(ResourceLocation component, int sceneIndex) {
            this(component, sceneIndex, false);
        }

        Viewer(ResourceLocation component, int sceneIndex, boolean showcase) {
            super(component, sceneIndex, showcase, null);
            width = 800;
            height = 600;
        }

        void publish(List<ResourceLocation> components, ResourceLocation component, PonderScene... scenes) {
            this.components = new ArrayList<>(components);
            this.scenes.put(component, Arrays.asList(scenes));
            generation++;
        }

        @Override
        protected long readIndexGeneration() {
            return generation;
        }

        @Override
        protected List<ResourceLocation> readIndexComponents() {
            return components;
        }

        @Override
        protected List<PonderScene> compileIndexScenes(ResourceLocation component) {
            compilations++;
            if (compileFailure != null) {
                throw compileFailure;
            }
            if (compileLinkageFailure != null) {
                throw compileLinkageFailure;
            }
            return scenes.getOrDefault(component, Collections.emptyList());
        }

        @Override
        protected void resetPreviewCamera() {
            super.resetPreviewCamera();
            cameraResets++;
        }
    }
}
