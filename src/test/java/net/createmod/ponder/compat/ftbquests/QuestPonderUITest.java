package net.createmod.ponder.compat.ftbquests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSchematic;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.Locale;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class QuestPonderUITest {

    private static final ResourceLocation COMPONENT = new ResourceLocation("test", "machine");
    private static Field localeField;
    private static Object previousLocale;

    @BeforeAll
    static void registerBlocks() throws Exception {
        Bootstrap.register();
        localeField = I18n.class.getDeclaredField("i18nLocale");
        localeField.setAccessible(true);
        previousLocale = localeField.get(null);
        localeField.set(null, new Locale());
    }

    @AfterAll
    static void restoreLocale() throws Exception {
        if (localeField != null) {
            localeField.set(null, previousLocale);
        }
    }

    @Test
    void realViewerInitializationConsumesTheValidatedSnapshotAndSelectsItsActualId() throws Exception {
        PonderScene first = scene("first");
        PonderScene selected = scene("selected");
        List<PonderScene> compiled = new ArrayList<>(Arrays.asList(first, selected));
        Viewer viewer = new Viewer(compiled);
        compiled.clear();

        viewer.initGui();

        assertSame(selected, viewer.selected());
        assertEquals(1, viewer.selectedIndex());
        assertEquals(2, viewer.count());
        assertEquals(0, viewer.freshCompilations);
        assertNull(prepared(viewer));
        viewer.initGui();
        for (int frame = 0; frame < 20; frame++) {
            viewer.updateScreen();
        }
        assertEquals(0, viewer.freshCompilations);
        assertSame(selected, viewer.selected());
    }

    @Test
    void generationChangedBeforeInitializationDisplaysNoSceneAndNeverCompilesAgain() throws Exception {
        Viewer viewer = new Viewer(Arrays.asList(scene("selected")));
        viewer.generation = 2;

        viewer.initGui();

        assertNull(viewer.selected());
        assertEquals(0, viewer.count());
        assertEquals(0, viewer.freshCompilations);
        assertNull(prepared(viewer));
        assertTrue(viewer.notice().contains("ponder.ftbquests.index_changed"));
    }

    @Test
    void removedRequestedComponentDoesNotSelectAnUnrelatedRegisteredComponent() throws Exception {
        Viewer viewer = new Viewer(Arrays.asList(scene("selected")));
        viewer.components = Collections.singletonList(new ResourceLocation("test", "unrelated"));

        viewer.initGui();

        assertNull(viewer.component());
        assertNull(viewer.selected());
        assertEquals(0, viewer.count());
        assertEquals(0, viewer.freshCompilations);
        assertNull(prepared(viewer));
    }

    @Test
    void preparedTargetCannotFallbackToTheFirstOrDuplicateScene() {
        for (List<PonderScene> compiled : Arrays.asList(
            Arrays.asList(scene("first"), scene("replacement")),
            Arrays.asList(scene("selected"), scene("selected")))) {
            Viewer viewer = new Viewer(compiled);
            viewer.initGui();
            assertNull(viewer.selected());
            assertFalse(viewer.playing());
            assertEquals(0, viewer.freshCompilations);
        }
    }

    @Test
    void actualRefreshPathRetainsIdAcrossReorderButRejectsRemovedAndDuplicateIds() {
        Viewer viewer = new Viewer(Arrays.asList(scene("first"), scene("selected")));
        viewer.initGui();
        viewer.publish(scene("selected"), scene("inserted"), scene("first"));
        viewer.updateScreen();
        assertEquals(new ResourceLocation("test", "selected"), viewer.selected().getSceneId());
        assertEquals(0, viewer.selectedIndex());
        assertEquals(1, viewer.freshCompilations);

        viewer.publish(scene("replacement"), scene("first"));
        viewer.updateScreen();
        assertNull(viewer.selected());
        assertTrue(viewer.notice().contains("ponder.ftbquests.scene_missing"));
        for (int frame = 0; frame < 20; frame++) {
            viewer.updateScreen();
        }
        assertEquals(2, viewer.freshCompilations);

        viewer.publish(scene("selected"), scene("selected"));
        viewer.updateScreen();
        assertNull(viewer.selected());
        assertTrue(viewer.notice().contains("ponder.ftbquests.ambiguous_id"));
        assertEquals(3, viewer.freshCompilations);

        viewer.publish(scene("first"), scene("selected"));
        viewer.updateScreen();
        assertEquals(new ResourceLocation("test", "selected"), viewer.selected().getSceneId());
        assertEquals(1, viewer.selectedIndex());
        assertEquals(4, viewer.freshCompilations);
    }

    @Test
    void userSelectedVariantIsRetainedByIdWhenTheTaskViewerReloads() throws Exception {
        Viewer viewer = new Viewer(Arrays.asList(scene("selected"), scene("other_variant")));
        viewer.initGui();
        viewer.nextScene();
        assertEquals(new ResourceLocation("test", "other_variant"), viewer.selected().getSceneId());

        viewer.publish(scene("other_variant"), scene("selected"));
        viewer.updateScreen();

        assertEquals(new ResourceLocation("test", "other_variant"), viewer.selected().getSceneId());
        assertEquals(0, viewer.selectedIndex());
        assertEquals(1, viewer.freshCompilations);
    }

    @Test
    void removingTheLinkedMachineDuringViewingCannotFallBackToAnotherMachine() {
        Viewer viewer = new Viewer(Arrays.asList(scene("selected")));
        viewer.initGui();
        viewer.components = Collections.singletonList(new ResourceLocation("test", "unrelated"));
        viewer.generation++;

        viewer.updateScreen();

        assertNull(viewer.component());
        assertNull(viewer.selected());
        assertFalse(viewer.playing());
        assertEquals(0, viewer.freshCompilations);
    }

    @Test
    void closeReleasesSceneObjectsAndReopenUsesFreshDataWithTheSameStableId() throws Exception {
        Viewer viewer = new Viewer(Arrays.asList(scene("selected"), scene("other_variant")));
        viewer.initGui();
        viewer.nextScene();
        viewer.onGuiClosed();
        assertNull(viewer.selected());
        assertNull(prepared(viewer));
        PonderScene replacement = scene("other_variant");
        viewer.freshScenes = Arrays.asList(replacement, scene("selected"));

        viewer.initGui();

        assertSame(replacement, viewer.selected());
        assertEquals(0, viewer.selectedIndex());
        assertEquals(1, viewer.freshCompilations);
    }

    @Test
    void closingBeforeInitializationReleasesPreparedScenes() throws Exception {
        Viewer viewer = new Viewer(Arrays.asList(scene("selected")));
        viewer.onGuiClosed();
        assertNull(prepared(viewer));
    }

    private static Object prepared(QuestPonderUI viewer) throws Exception {
        Field field = QuestPonderUI.class.getDeclaredField("preparedScenes");
        field.setAccessible(true);
        return field.get(viewer);
    }

    private static PonderScene scene(String name) {
        PonderStoryBoardEntry entry = new PonderStoryBoardEntry((scene, util) -> {
            throw new AssertionError("A validated storyboard must not be compiled twice on quest entry");
        }, "test", new ResourceLocation("test", "shared"), COMPONENT);
        PonderScene scene = new PonderScene(entry, new PonderLocalization());
        scene.setSchematic(PonderSchematic.empty());
        scene.setSceneId(new ResourceLocation("test", name));
        scene.setBasePlateSize(3);
        scene.addIdleTicks(100);
        return scene;
    }

    private static final class Viewer extends QuestPonderUI {
        long generation = 1;
        int freshCompilations;
        List<ResourceLocation> components = Collections.singletonList(COMPONENT);
        List<PonderScene> freshScenes = Collections.emptyList();

        Viewer(List<PonderScene> scenes) {
            super(COMPONENT, "test:selected", scenes, 1, null);
            setGuiSize(800, 600);
        }

        void publish(PonderScene... scenes) {
            freshScenes = Arrays.asList(scenes);
            generation++;
        }

        PonderScene selected() {
            return getSelectedScene();
        }

        ResourceLocation component() {
            return getSelectedComponentId();
        }

        int selectedIndex() {
            return getSelectedSceneIndex();
        }

        int count() {
            return getCompiledSceneCount();
        }

        String notice() {
            return getRegistryStatus("");
        }

        boolean playing() {
            return isPlaybackRunning();
        }

        void nextScene() throws IOException {
            actionPerformed(new GuiButton(BUTTON_NEXT_SCENE, 0, 0, 20, 20, ">"));
        }

        @Override
        protected long readIndexGeneration() {
            return generation;
        }

        @Override
        protected List<ResourceLocation> readAvailableComponents() {
            return components;
        }

        @Override
        protected List<PonderScene> compileFreshScenes(ResourceLocation component) {
            assertEquals(COMPONENT, component);
            freshCompilations++;
            return freshScenes;
        }
    }
}
