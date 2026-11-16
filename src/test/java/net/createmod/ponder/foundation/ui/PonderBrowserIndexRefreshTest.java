package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.createmod.ponder.CommonProxy;
import net.createmod.ponder.PonderBackportMod;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTag;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PonderBrowserIndexRefreshTest {

    private CommonProxy originalProxy;
    private Object originalRegistry;
    private long originalGeneration;
    private List<PonderPlugin> originalPlugins;
    private final Map<ResourceLocation, List<ResourceLocation>> fixtures = new LinkedHashMap<>();
    private final List<ResourceLocation> tags = new ArrayList<>();
    private int compilations;

    @BeforeAll
    static void registerBlocks() {
        Bootstrap.register();
    }

    @BeforeEach
    void isolateRegistry() throws Exception {
        originalProxy = PonderBackportMod.proxy;
        originalRegistry = indexField("active").get(null);
        originalGeneration = PonderIndex.getGeneration();
        originalPlugins = new ArrayList<>(plugins());
        plugins().clear();
        PonderBackportMod.proxy = null;
        PonderIndex.addPlugin(new PonderPlugin() {
            @Override
            public String getModId() {
                return "reload_ui";
            }

            @Override
            public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
                for (ResourceLocation component : fixtures.keySet()) {
                    helper.addStoryBoard(component, "empty", (scene, util) -> compilations++);
                }
            }

            @Override
            public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
                for (ResourceLocation tag : tags) {
                    helper.registerTag(tag).title(tag.getPath()).description("Reload fixture").addToIndex().register();
                }
                for (Map.Entry<ResourceLocation, List<ResourceLocation>> entry : fixtures.entrySet()) {
                    for (ResourceLocation tag : entry.getValue()) {
                        helper.addTagToComponent(entry.getKey(), tag);
                    }
                }
            }
        });
    }

    @AfterEach
    void restoreRegistry() throws Exception {
        PonderBackportMod.proxy = originalProxy;
        indexField("active").set(null, originalRegistry);
        indexField("generation").setLong(null, originalGeneration);
        plugins().clear();
        plugins().addAll(originalPlugins);
    }

    @Test
    void openIndexPreservesSearchAndTagFilterAndResortsNewMetadata() throws Exception {
        tags.addAll(Arrays.asList(id("keep"), id("other")));
        machine("machine_z", "keep");
        machine("machine_b", "keep");
        machine("machine_hidden", "other");
        PonderIndex.reload();
        Index screen = new Index();
        screen.synchronizeIndex();
        set(screen, "searchQuery", "MACHINE");
        set(screen, "selectedTagId", id("keep"));
        PonderComponentEntry previous = components(screen, "allComponents").get(0);
        regions(screen, "visibleComponentRegions").add(new PonderClickRegion<>(previous, 0, 0, 24, 24));

        fixtures.remove(id("machine_b"));
        machine("machine_c", "keep");
        machine("machine_a", "keep");
        machine("unrelated", "keep");
        PonderIndex.reload();
        screen.updateScreen();

        assertEquals("MACHINE", get(screen, "searchQuery"));
        assertEquals(id("keep"), get(screen, "selectedTagId"));
        assertEquals(Arrays.asList(id("machine_a"), id("machine_c"), id("machine_z")),
            componentIds(components(screen, "filteredComponents")));
        assertTrue(regions(screen, "visibleComponentRegions").isEmpty());
        assertNotSame(previous, components(screen, "allComponents").get(0));
        for (int tick = 0; tick < 100; tick++) {
            screen.updateScreen();
        }
        assertEquals(2, screen.refreshes);
        assertEquals(0, compilations);
    }

    @Test
    void deletedTagClearsItsFilterAndTagPageDoesNotKeepTheMissingSentinel() throws Exception {
        tags.add(id("removed"));
        machine("machine_a", "removed");
        PonderIndex.reload();
        Index index = new Index();
        index.synchronizeIndex();
        set(index, "selectedTagId", id("removed"));
        set(index, "searchQuery", "machine");
        PonderTag oldTag = AbstractPonderBrowserScreen.registeredTag(id("removed"));
        assertNotNull(oldTag);
        PonderTagScreen page = new PonderTagScreen(oldTag, index);
        page.width = 400;
        page.height = 300;
        page.synchronizeIndex();

        tags.clear();
        machine("machine_a");
        machine("machine_b");
        PonderIndex.reload();
        index.updateScreen();
        page.updateScreen();

        assertNotNull(PonderIndex.getTagAccess().getRegisteredTag(id("removed")));
        assertNull(AbstractPonderBrowserScreen.registeredTag(id("removed")));
        assertNull(get(index, "selectedTagId"));
        assertEquals("machine", get(index, "searchQuery"));
        assertEquals(2, components(index, "filteredComponents").size());
        assertNull(get(page, "tag"));
        assertTrue(components(page, "components").isEmpty());
        assertEquals(0, compilations);
    }

    @Test
    void closeReleasesBrowserMetadataAndReopenRetainsIdScrollAnchors() throws Exception {
        tags.add(id("keep"));
        for (int i = 0; i < 120; i++) {
            machine(String.format(java.util.Locale.ROOT, "machine_%03d", i), "keep");
        }
        PonderIndex.reload();
        Index index = new Index();
        index.synchronizeIndex();
        set(index, "searchQuery", "machine");
        set(index, "selectedTagId", id("keep"));
        set(index, "componentScroll", 20);
        PonderTagScreen tag = new PonderTagScreen(id("keep"), index);
        tag.width = 96;
        tag.height = 240;
        tag.synchronizeIndex();
        set(tag, "componentScroll", 20);
        int columns = componentColumns(index);
        int oldIndexOffset = 20 * columns;
        PonderComponentEntry oldIndexEntry = components(index, "filteredComponents").get(oldIndexOffset);
        PonderComponentEntry oldTagEntry = components(tag, "components").get(20);

        index.onGuiClosed();
        tag.onGuiClosed();

        assertTrue(components(index, "allComponents").isEmpty());
        assertTrue(components(index, "filteredComponents").isEmpty());
        assertTrue(((List<?>) get(index, "listedTags")).isEmpty());
        assertTrue(components(tag, "components").isEmpty());
        assertNull(get(tag, "tag"));
        // Add one full grid row so retained IDs must advance the actual scroll anchor.
        for (int i = 0; i < columns; i++) {
            machine("machine_000_added_" + i, "keep");
        }
        PonderIndex.reload();
        index.synchronizeIndex();
        tag.synchronizeIndex();

        assertEquals("machine", get(index, "searchQuery"));
        assertEquals(id("keep"), get(index, "selectedTagId"));
        assertEquals(21, get(index, "componentScroll"));
        assertEquals(20 + columns, get(tag, "componentScroll"));
        assertEquals(oldIndexEntry.componentId,
            components(index, "filteredComponents").get(oldIndexOffset + columns).componentId);
        assertEquals(oldTagEntry.componentId, components(tag, "components").get(20 + columns).componentId);
        assertNotSame(oldIndexEntry, components(index, "filteredComponents").get(oldIndexOffset + columns));
        assertNotSame(oldTagEntry, components(tag, "components").get(20 + columns));
        assertEquals(0, compilations);
    }

    @Test
    void refreshConsumesStaleClicksAndSearchChangesDropOldHitRegions() throws Exception {
        tags.add(id("keep"));
        machine("machine_a", "keep");
        PonderIndex.reload();
        Index index = new Index();
        index.synchronizeIndex();
        PonderTagScreen tag = new PonderTagScreen(id("keep"), index);
        tag.synchronizeIndex();
        PonderComponentEntry entry = components(index, "allComponents").get(0);
        regions(index, "visibleComponentRegions").add(new PonderClickRegion<>(entry, 0, 0, 24, 24));
        regions(tag, "visibleComponentRegions").add(new PonderClickRegion<>(entry, 0, 0, 24, 24));
        machine("machine_b", "keep");
        PonderIndex.reload();

        index.mouseClicked(10, 10, 0);
        tag.mouseClicked(10, 10, 0);

        assertTrue(regions(index, "visibleComponentRegions").isEmpty());
        assertTrue(regions(tag, "visibleComponentRegions").isEmpty());
        regions(index, "visibleComponentRegions").add(new PonderClickRegion<>(entry, 0, 0, 24, 24));
        index.keyTyped('z', 44);
        assertEquals("z", get(index, "searchQuery"));
        assertTrue(components(index, "filteredComponents").isEmpty());
        assertTrue(regions(index, "visibleComponentRegions").isEmpty());
        assertEquals(0, compilations);
    }

    @Test
    void unchangedAndFailedReloadsDoNotRebuildOpenBrowsers() throws Exception {
        machine("machine_a");
        PonderIndex.reload();
        Index index = new Index();
        assertTrue(index.synchronizeIndex());
        PonderIndex.addPlugin(new PonderPlugin() {
            @Override
            public String getModId() {
                return "failing_ui";
            }

            @Override
            public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
                throw new IllegalStateException("injected registry failure");
            }
        });
        PonderIndex.reload();
        assertFalse(index.synchronizeIndex());
        assertEquals(1, index.refreshes);
        assertEquals(Collections.singletonList(id("machine_a")), componentIds(components(index, "allComponents")));
    }

    private void machine(String name, String... tagNames) {
        fixtures.put(id(name), Arrays.stream(tagNames).map(PonderBrowserIndexRefreshTest::id)
            .collect(Collectors.toList()));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("reload_ui", path);
    }

    private static List<ResourceLocation> componentIds(List<PonderComponentEntry> entries) {
        return entries.stream().map(entry -> entry.componentId).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static List<PonderComponentEntry> components(Object screen, String name) throws Exception {
        return (List<PonderComponentEntry>) get(screen, name);
    }

    @SuppressWarnings("unchecked")
    private static List<PonderClickRegion<PonderComponentEntry>> regions(Object screen, String name) throws Exception {
        return (List<PonderClickRegion<PonderComponentEntry>>) get(screen, name);
    }

    private static Field field(Object screen, String name) throws Exception {
        Class<?> type = screen instanceof PonderIndexScreen ? PonderIndexScreen.class : PonderTagScreen.class;
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object get(Object screen, String name) throws Exception {
        return field(screen, name).get(screen);
    }

    private static void set(Object screen, String name, Object value) throws Exception {
        field(screen, name).set(screen, value);
    }

    private static int componentColumns(PonderIndexScreen screen) throws Exception {
        Method method = PonderIndexScreen.class.getDeclaredMethod("getComponentColumns");
        method.setAccessible(true);
        return (Integer) method.invoke(screen);
    }

    @SuppressWarnings("unchecked")
    private static List<PonderPlugin> plugins() throws Exception {
        return (List<PonderPlugin>) indexField("PLUGINS").get(null);
    }

    private static Field indexField(String name) throws Exception {
        Field field = PonderIndex.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class Index extends PonderIndexScreen {
        private int refreshes;

        Index() {
            width = 320;
            height = 240;
        }

        @Override
        protected void reloadRegistryView() {
            refreshes++;
            super.reloadRegistryView();
        }
    }
}
