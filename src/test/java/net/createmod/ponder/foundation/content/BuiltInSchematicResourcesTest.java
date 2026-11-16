package net.createmod.ponder.foundation.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.WorldEvent;
import net.createmod.ponder.foundation.PonderScene.WorldEventType;
import net.createmod.ponder.foundation.PonderSchematic;
import net.createmod.ponder.foundation.registration.DefaultPonderSceneRegistrationHelper;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BuiltInSchematicResourcesTest {

    private Object originalRegistry;

    @BeforeAll
    static void registerVanillaBlocks() {
        Bootstrap.register();
    }

    @BeforeEach
    void isolateMatcherRegistration() throws Exception {
        Field field = activeRegistry();
        originalRegistry = field.get(null);
        Constructor<?> constructor = field.getType().getDeclaredConstructor();
        constructor.setAccessible(true);
        field.set(null, constructor.newInstance());
    }

    @AfterEach
    void restoreMatcherRegistration() throws Exception {
        activeRegistry().set(null, originalRegistry);
    }

    @Test
    void registeredDebugSchematicsContainOnlyAvailableVanillaBlocks() throws Exception {
        List<StoryBoardEntry> entries = debugEntries();
        assertEquals(12, entries.size());
        for (StoryBoardEntry entry : entries) {
            assertVanillaSchematic(entry);
        }
    }

    @Test
    void thermalSchematicsResolveBundledResourcesOutsideThePluginNamespace() throws Exception {
        List<StoryBoardEntry> entries = thermalEntries();
        assertEquals(16, entries.size());
        for (StoryBoardEntry entry : entries) {
            assertEquals("thermalexpansion", entry.getNamespace());
            assertEquals(Ponder.asResource("ui_test/runtime_test"), entry.getSchematicLocation());
            assertVanillaSchematic(entry);
        }
    }

    @Test
    void ordinaryUiRegistrationsContainOnlyAvailableVanillaBlocks() throws Exception {
        List<StoryBoardEntry> entries = uiEntries();
        assertEquals(10, entries.size());
        for (StoryBoardEntry entry : entries) {
            assertVanillaSchematic(entry);
        }
    }

    @Test
    void debugSceneIdentitiesSurviveSchematicMigration() {
        Set<String> actual = new LinkedHashSet<String>();
        for (StoryBoardEntry entry : debugEntries()) {
            PonderScene scene = compile(entry);
            assertTrue(actual.add(scene.getSceneId().toString()), "Duplicate explicit scene identity");
        }
        Set<String> expected = new LinkedHashSet<String>();
        for (String id : Arrays.asList("debug_runtime_test", "debug_visual_showcase", "debug_coords",
            "debug_blocks", "debug_baseplate", "debug_particles", "debug_controls", "debug_birbs",
            "debug_sections", "debug_tracking", "debug_items", "debug_gui_snapshot")) {
            expected.add(Ponder.asResource(id).toString());
        }
        assertEquals(expected, actual);
    }

    @Test
    void thermalSceneIdentitiesKeepTheirOriginalNamespaceAndMachineNames() {
        for (StoryBoardEntry entry : thermalEntries()) {
            String machine = entry.getComponent().getPath().substring("machine_".length());
            assertEquals(new ResourceLocation("thermalexpansion", "te_" + machine + "_gui"),
                compile(entry).getSceneId());
        }
    }

    @Test
    void coordinateAndBlockExamplesRetainTheirAuthoredTargets() {
        PonderScene coordinates = debugScene("debug_coords");
        assertSolid(coordinates, new BlockPos(1, 1, 1), new BlockPos(4, 1, 1),
            new BlockPos(1, 4, 1), new BlockPos(1, 1, 4));
        PonderScene blocks = debugScene("debug_blocks");
        assertSolid(blocks, new BlockPos(3, 1, 1), new BlockPos(1, 1, 3), new BlockPos(2, 2, 4));
    }

    @Test
    void interactionAndSectionExamplesUseTheMatchingVanillaGeometry() {
        PonderScene controls = debugScene("debug_controls");
        assertSolid(controls, new BlockPos(3, 1, 1), new BlockPos(1, 1, 3),
            new BlockPos(1, 2, 3), new BlockPos(1, 4, 3), new BlockPos(3, 3, 3));
        PonderScene sections = debugScene("debug_sections");
        assertSolid(sections, new BlockPos(1, 1, 1), new BlockPos(3, 1, 1),
            new BlockPos(2, 1, 2), new BlockPos(4, 1, 4));
        PonderScene particles = debugScene("debug_particles");
        assertSolid(particles, new BlockPos(2, 1, 2));
    }

    @Test
    void outOfBoundsExampleRetainsGeometryBeyondTheConfiguredBasePlate() {
        PonderScene scene = debugScene("debug_baseplate");
        assertEquals(1, scene.getBasePlateOffsetX());
        assertEquals(6, scene.getBasePlateSize());
        assertSolid(scene, new BlockPos(0, 1, 2), new BlockPos(8, 3, 5));
        assertTrue(scene.getSchematic().getSize().getX() > 7);
    }

    @Test
    void ordinaryUiEntryPointsPreserveSceneIdentitiesAndTheirAuthoredTargets() {
        List<StoryBoardEntry> entries = uiEntries();
        Set<String> actual = new LinkedHashSet<String>();
        for (StoryBoardEntry entry : entries) {
            assertTrue(actual.add(compile(entry).getSceneId().toString()), "Duplicate explicit scene identity");
        }
        Set<String> expected = new LinkedHashSet<String>();
        for (String id : Arrays.asList("debug_coords", "debug_blocks", "debug_controls", "debug_baseplate",
            "debug_particles", "debug_gui_snapshot", "debug_birbs", "debug_sections", "debug_tracking",
            "debug_items")) {
            expected.add(Ponder.asResource(id).toString());
        }
        assertEquals(expected, actual);
        assertSolid(scene(entries, "debug_baseplate"), new BlockPos(0, 1, 2), new BlockPos(8, 3, 5));
        assertSolid(scene(entries, "debug_particles"), new BlockPos(2, 1, 2));
        assertSolid(scene(entries, "debug_controls"), new BlockPos(3, 1, 1), new BlockPos(1, 1, 3),
            new BlockPos(1, 2, 3), new BlockPos(1, 4, 3), new BlockPos(3, 3, 3));
        assertSolid(scene(entries, "debug_birbs"), new BlockPos(1, 2, 3), new BlockPos(0, 1, 2),
            new BlockPos(3, 1, 3));
        assertSolid(scene(entries, "debug_sections"), new BlockPos(1, 1, 1), new BlockPos(3, 1, 1),
            new BlockPos(2, 1, 2), new BlockPos(4, 1, 4));
    }

    @Test
    void runtimeExampleRevealsAndHidesCompleteOriginalSections() {
        PonderScene scene = debugScene("debug_runtime_test");
        Set<BlockPos> sectionPositions = new LinkedHashSet<BlockPos>();
        int shows = 0;
        int hides = 0;
        for (WorldEvent event : scene.getWorldEvents()) {
            if (event.getType() != WorldEventType.SHOW_SECTION
                && event.getType() != WorldEventType.HIDE_SECTION) {
                continue;
            }
            if (event.getPositions().isEmpty() || event.getPositions().get(0).getY() == 0) {
                continue;
            }
            assertEquals(4, event.getPositions().size(), event.getType().toString());
            assertSolid(scene, event.getPositions().toArray(new BlockPos[0]));
            sectionPositions.addAll(event.getPositions());
            if (event.getType() == WorldEventType.SHOW_SECTION) {
                shows++;
            } else {
                hides++;
            }
        }
        assertEquals(3, shows);
        assertEquals(2, hides);
        assertEquals(8, sectionPositions.size());
        assertEquals(sectionPositions,
            new LinkedHashSet<BlockPos>(lastEvent(scene, WorldEventType.RESTORE_BLOCKS).getPositions()));
        assertSolid(scene, new BlockPos(2, 2, 3));
    }

    @Test
    void runtimeExampleRestoresTheOriginalSectionsAfterReplacementAndHiding() throws Exception {
        PonderScene scene = debugScene("debug_runtime_test");
        WorldEvent replacement = lastEvent(scene, WorldEventType.REPLACE_BLOCKS);
        WorldEvent restore = lastEvent(scene, WorldEventType.RESTORE_BLOCKS);
        Map<BlockPos, ?> initial = runtimeBlocks(scene, replacement.getTick() - 1);
        Map<BlockPos, ?> replaced = runtimeBlocks(scene, replacement.getTick());
        Map<BlockPos, ?> hidden = runtimeBlocks(scene, restore.getTick() - 1);
        Map<BlockPos, ?> restored = runtimeBlocks(scene, scene.getTotalIdleTicks());

        assertEquals(4, replacement.getPositions().size());
        for (BlockPos position : replacement.getPositions()) {
            IBlockState changed = (IBlockState) runtimeField(replaced.get(position), "currentState");
            assertEquals(Blocks.QUARTZ_BLOCK, changed.getBlock(), position.toString());
            assertEquals(Boolean.FALSE, runtimeField(hidden.get(position), "visible"), position.toString());
        }
        assertEquals(8, restore.getPositions().size());
        for (BlockPos position : restore.getPositions()) {
            IBlockState original = scene.getSchematic().getBlocks().get(position);
            assertNotNull(original, position.toString());
            assertEquals(Blocks.IRON_BLOCK, original.getBlock(), position.toString());
            assertEquals(original, runtimeField(initial.get(position), "currentState"), position.toString());
            assertEquals(Boolean.TRUE, runtimeField(initial.get(position), "visible"), position.toString());
            assertEquals(original, runtimeField(restored.get(position), "currentState"), position.toString());
            assertEquals(Boolean.TRUE, runtimeField(restored.get(position), "visible"), position.toString());
            assertEquals(Boolean.FALSE, runtimeField(restored.get(position), "destroyed"), position.toString());
            assertEquals(Float.valueOf(1.0F), runtimeField(restored.get(position), "fade"), position.toString());
        }
    }

    private static void assertVanillaSchematic(StoryBoardEntry entry) throws Exception {
        ResourceLocation location = entry.getSchematicLocation();
        assertEquals(Ponder.MOD_ID, location.getNamespace());
        assertTrue(location.getPath().startsWith("ui_test/"), location.toString());
        String path = "assets/" + location.getNamespace() + "/ponder/" + location.getPath() + ".nbt";
        NBTTagCompound data;
        try (InputStream input = BuiltInSchematicResourcesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            data = CompressedStreamTools.readCompressed(input);
        }
        NBTTagList palette = data.getTagList("palette", 10);
        assertTrue(palette.tagCount() > 0, path);
        for (int i = 0; i < palette.tagCount(); i++) {
            NBTTagCompound tag = palette.getCompoundTagAt(i);
            ResourceLocation declared = new ResourceLocation(tag.getString("Name"));
            assertEquals("minecraft", declared.getNamespace(), path + ": " + declared);
            IBlockState decoded = NBTUtil.readBlockState(tag);
            assertEquals(declared, decoded.getBlock().getRegistryName(), path + ": " + declared);
            assertNotEquals(Blocks.AIR, decoded.getBlock(), path + ": " + declared);
        }
        PonderSchematic schematic = PonderSchematic.load(location);
        assertFalse(schematic.isEmpty(), path);
        assertEquals(data.getTagList("blocks", 10).tagCount(), schematic.getBlocks().size(), path);
        for (IBlockState state : schematic.getBlocks().values()) {
            assertNotEquals(Blocks.AIR, state.getBlock(), path);
        }
    }

    private static void assertSolid(PonderScene scene, BlockPos... positions) {
        for (BlockPos position : positions) {
            IBlockState state = scene.getSchematic().getBlocks().get(position);
            String message = scene.getSceneId() + " at " + position;
            assertNotNull(state, message);
            assertNotEquals(Blocks.AIR, state.getBlock(), message);
        }
    }

    private static PonderScene debugScene(String id) {
        return scene(debugEntries(), id);
    }

    private static PonderScene scene(List<StoryBoardEntry> entries, String id) {
        ResourceLocation expected = Ponder.asResource(id);
        for (StoryBoardEntry entry : entries) {
            PonderScene scene = compile(entry);
            if (expected.equals(scene.getSceneId())) {
                return scene;
            }
        }
        throw new AssertionError("Missing scene " + expected);
    }

    private static PonderScene compile(StoryBoardEntry entry) {
        return PonderSceneRegistry.compileScene(new PonderLocalization(), entry);
    }

    private static WorldEvent lastEvent(PonderScene scene, WorldEventType type) {
        WorldEvent found = null;
        for (WorldEvent event : scene.getWorldEvents()) {
            if (event.getType() == type) {
                found = event;
            }
        }
        assertNotNull(found, type.toString());
        return found;
    }

    @SuppressWarnings("unchecked")
    private static Map<BlockPos, ?> runtimeBlocks(PonderScene scene, int tick) throws Exception {
        Class<?> runtime = Class.forName("net.createmod.ponder.foundation.ui.PonderSceneRuntime");
        Method buildState = runtime.getDeclaredMethod("buildState", PonderScene.class, int.class);
        buildState.setAccessible(true);
        Object state = buildState.invoke(null, scene, Integer.valueOf(tick));
        return (Map<BlockPos, ?>) runtimeField(state, "blocksByPosition");
    }

    private static Object runtimeField(Object state, String name) throws Exception {
        assertNotNull(state, name);
        Field field = state.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(state);
    }

    private static List<StoryBoardEntry> debugEntries() {
        PonderSceneRegistry registry = new PonderSceneRegistry(new PonderLocalization());
        new DebugPonderPlugin().registerScenes(new DefaultPonderSceneRegistrationHelper(Ponder.MOD_ID, registry));
        return entries(registry);
    }

    private static List<StoryBoardEntry> thermalEntries() {
        PonderSceneRegistry registry = new PonderSceneRegistry(new PonderLocalization());
        ThermalExpansionScenes.registerAll(new DefaultPonderSceneRegistrationHelper("thermalexpansion", registry));
        return entries(registry);
    }

    private static List<StoryBoardEntry> uiEntries() {
        PonderSceneRegistry registry = new PonderSceneRegistry(new PonderLocalization());
        new UiTestPonderPlugin().registerScenes(new DefaultPonderSceneRegistrationHelper(Ponder.MOD_ID, registry));
        return entries(registry);
    }

    private static List<StoryBoardEntry> entries(PonderSceneRegistry registry) {
        List<StoryBoardEntry> result = new ArrayList<StoryBoardEntry>();
        for (Map.Entry<ResourceLocation, StoryBoardEntry> entry : registry.getRegisteredEntries()) {
            result.add(entry.getValue());
        }
        return result;
    }

    private static Field activeRegistry() throws Exception {
        Field field = PonderIndex.class.getDeclaredField("active");
        field.setAccessible(true);
        return field;
    }
}
