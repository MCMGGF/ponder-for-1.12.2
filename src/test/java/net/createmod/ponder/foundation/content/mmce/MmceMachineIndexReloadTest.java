package net.createmod.ponder.foundation.content.mmce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.content.MmcePonderPlugin;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MmceMachineIndexReloadTest {

    private static final ResourceLocation OLD = new ResourceLocation("test", "old");
    private static final ResourceLocation NEXT = new ResourceLocation("test", "next");

    @BeforeAll
    static void registerBlocks() {
        Bootstrap.register();
    }

    @Test
    void checkpointRestoresMachinesFailuresSnapshotsGenerationAndMessage() throws Exception {
        MmceMachineIndex index = newIndex();
        MmceMachineDescriptor old = machine(OLD);
        publish(index, old, "old registry");
        failures(index).put(OLD, "old diagnostic");
        MmceStructureSnapshot snapshot = snapshot(old, index.getGeneration());
        snapshots(index).put(OLD + "|MINIMUM", snapshot);
        MmceMachineIndex.Checkpoint checkpoint = index.checkpoint();
        long generation = index.getGeneration();

        publish(index, machine(NEXT), "new registry");
        assertNull(index.getMachine(OLD));
        assertEquals(0, index.getCachedStructureCount());
        index.restore(checkpoint);

        assertSame(old, index.getMachine(OLD));
        assertNull(index.getMachine(NEXT));
        assertSame(snapshot, index.getSnapshot(OLD));
        assertEquals(generation, index.getGeneration());
        assertEquals("old registry", index.getAvailabilityMessage());
        assertEquals(Collections.singletonMap(OLD, "old diagnostic"), index.getFailures());
    }

    @Test
    void rollbackRetainsSnapshotLruOrderAndLimit() throws Exception {
        MmceMachineIndex index = newIndex();
        MmceMachineDescriptor old = machine(OLD);
        publish(index, old, "old registry");
        Map<String, MmceStructureSnapshot> cache = snapshots(index);
        MmceStructureSnapshot snapshot = snapshot(old, index.getGeneration());
        for (int i = 0; i < MmcePreviewLimits.MAX_CACHED_STRUCTURES; i++) {
            cache.put("entry_" + i, snapshot);
        }
        cache.get("entry_0");
        List<String> order = new ArrayList<String>(cache.keySet());
        MmceMachineIndex.Checkpoint checkpoint = index.checkpoint();
        cache.clear();
        cache.put("discard", snapshot);

        index.restore(checkpoint);
        assertEquals(order, new ArrayList<String>(cache.keySet()));
        cache.put("new", snapshot);
        assertEquals(MmcePreviewLimits.MAX_CACHED_STRUCTURES, cache.size());
        assertTrue(cache.containsKey("entry_0"));
        assertTrue(!cache.containsKey(order.get(0)));
    }

    @Test
    void checkpointsCannotBeAppliedToAnotherIndexOrThread() throws Exception {
        MmceMachineIndex first = newIndex();
        MmceMachineIndex second = newIndex();
        MmceMachineIndex.Checkpoint checkpoint = first.checkpoint();
        assertThrows(IllegalArgumentException.class, () -> second.restore(checkpoint));
        AtomicReference<Throwable> result = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try {
                first.restore(checkpoint);
            } catch (Throwable failure) {
                result.set(failure);
            }
        });
        worker.start();
        worker.join();
        assertTrue(result.get() instanceof IllegalStateException);
    }

    @Test
    void mmcePluginRollsBackItsSceneAssociationsAndRegistrationList() throws Exception {
        exercisePluginEnd(false);
    }

    @Test
    void mmcePluginRetainsItsNewGenerationOnSuccessAndReleasesCheckpoint() throws Exception {
        exercisePluginEnd(true);
    }

    @Test
    void reflectionDoesNotTurnVirtualMachineFailureIntoAnOptionalAdapterFailure() {
        OutOfMemoryError fatal = new OutOfMemoryError("test only");
        assertSame(fatal, assertThrows(OutOfMemoryError.class,
            () -> MmceMachineIndex.rethrowFatalTarget(new InvocationTargetException(fatal))));
        ThreadDeath death = new ThreadDeath();
        assertSame(death, assertThrows(ThreadDeath.class,
            () -> MmceMachineIndex.rethrowFatalTarget(new InvocationTargetException(death))));
    }

    @Test
    void nativeReloadPreservesTheIndexAndRejectsUncachedReadsWithoutTouchingSource() throws Exception {
        MmceMachineIndex index = newIndex();
        MmceMachineDescriptor old = machine(OLD);
        publish(index, old, "old registry");
        long generation = index.getGeneration();
        MmceRegistryReadGuard.beginReload();
        try {
            index.refresh();
            assertSame(old, index.getMachine(OLD));
            assertEquals(generation, index.getGeneration());
            assertEquals(0, index.getCachedStructureCount());
            assertTrue(index.getAvailabilityMessage().contains("MMCE"));
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> index.getSnapshot(OLD));
            assertTrue(failure.getCause() instanceof MmceRegistryReadGuard.ReloadInProgressException);
            assertTrue(index.getFailures().isEmpty());
        } finally {
            MmceRegistryReadGuard.completeReload();
        }
    }

    @Test
    void immutableCachedStructureRemainsReadableDuringAndAfterNativeReload() throws Exception {
        MmceMachineIndex index = newIndex();
        MmceMachineDescriptor old = machine(OLD);
        publish(index, old, "old registry");
        MmceStructureSnapshot snapshot = snapshot(old, index.getGeneration());
        snapshots(index).put(OLD + "|MINIMUM", snapshot);
        MmceRegistryReadGuard.beginReload();
        try {
            assertSame(snapshot, index.getSnapshot(OLD));
        } finally {
            MmceRegistryReadGuard.completeReload();
        }
        assertSame(snapshot, index.getSnapshot(OLD));
    }

    @Test
    void rollbackDoesNotMakeOldNativeReferencesReadableInANewSourceEpoch() throws Exception {
        MmceMachineIndex index = newIndex();
        publish(index, machine(OLD), "old registry");
        long oldEpoch = MmceRegistryReadGuard.currentEpoch();
        MmceMachineIndex.Checkpoint checkpoint = index.checkpoint();
        MmceRegistryReadGuard.beginReload();
        MmceRegistryReadGuard.completeReload();
        publish(index, machine(NEXT), "new registry");
        index.restore(checkpoint);

        assertEquals(oldEpoch, field(MmceMachineIndex.class, "sourceEpoch").getLong(index));
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> index.getSnapshot(OLD));
        assertTrue(failure.getMessage().contains("/ponder reload"));
        assertEquals(0, index.getCachedStructureCount());
    }

    private static void exercisePluginEnd(boolean success) throws Exception {
        MmceMachineIndex index = MmceMachineIndex.getInstance();
        MmceMachineIndex.Checkpoint original = index.checkpoint();
        Map<PonderScene, MmceStructureSnapshot> scenes = sceneSnapshots();
        Map<PonderScene, MmceStructureSnapshot> originalScenes =
            new LinkedHashMap<PonderScene, MmceStructureSnapshot>(scenes);
        MmcePonderPlugin plugin = new MmcePonderPlugin();
        try {
            MmceMachineDescriptor old = machine(OLD);
            MmceMachineDescriptor next = machine(NEXT);
            publish(index, old, "old registry");
            MmceStructureSnapshot oldSnapshot = snapshot(old, index.getGeneration());
            PonderScene oldScene = scene("old");
            scenes.put(oldScene, oldSnapshot);
            registered(plugin).add(old);
            plugin.onPonderReloadStart();
            publish(index, next, "new registry");
            scenes.clear();
            PonderScene newScene = scene("new");
            MmceStructureSnapshot newSnapshot = snapshot(next, index.getGeneration());
            scenes.put(newScene, newSnapshot);
            registered(plugin).clear();
            registered(plugin).add(next);

            plugin.onPonderReloadEnd(success);

            assertSame(success ? next : old, index.getMachines().get(0));
            assertSame(success ? next : old, registered(plugin).get(0));
            assertSame(success ? newSnapshot : oldSnapshot,
                MmcePonderPlugin.getSceneSnapshot(success ? newScene : oldScene));
            assertNull(MmcePonderPlugin.getSceneSnapshot(success ? oldScene : newScene));
            assertNull(field(MmcePonderPlugin.class, "indexCheckpoint").get(plugin));
            assertNull(field(MmcePonderPlugin.class, "sceneCheckpoint").get(plugin));
            plugin.onPonderReloadStart();
            plugin.onPonderReloadEnd(false);
        } finally {
            index.restore(original);
            scenes.clear();
            scenes.putAll(originalScenes);
        }
    }

    private static MmceMachineIndex newIndex() throws Exception {
        Constructor<MmceMachineIndex> constructor = MmceMachineIndex.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static MmceMachineDescriptor machine(ResourceLocation id) {
        return new MmceMachineDescriptor(id, id.toString(), 1, Collections.emptyList(),
            false, null, Collections.emptyList());
    }

    private static MmceStructureSnapshot snapshot(MmceMachineDescriptor machine, long generation) {
        return new MmceStructureSnapshot(machine, MmceStructureSnapshot.Variant.MINIMUM,
            Collections.emptyList(), BlockPos.ORIGIN, new BlockPos(1, 1, 1),
            Collections.emptyList(), generation, 0);
    }

    private static PonderScene scene(String name) {
        return new PonderScene(new PonderStoryBoardEntry((builder, util) -> { }, "test",
            new ResourceLocation("test", "empty"), new ResourceLocation("test", name)), new PonderLocalization());
    }

    private static void publish(MmceMachineIndex index, MmceMachineDescriptor machine, String message)
        throws Exception {
        Class<?> runtimeType = Class.forName(MmceMachineIndex.class.getName() + "$RuntimeMachine");
        Constructor<?> constructor = runtimeType.getDeclaredConstructor(Object.class,
            MmceMachineDescriptor.class, Block.class);
        constructor.setAccessible(true);
        Map<ResourceLocation, Object> machines = new LinkedHashMap<ResourceLocation, Object>();
        machines.put(machine.getId(), constructor.newInstance(new Object(), machine, Blocks.STONE));
        Method publish = MmceMachineIndex.class.getDeclaredMethod("publish",
            Map.class, Map.class, String.class, long.class);
        publish.setAccessible(true);
        publish.invoke(index, machines, Collections.emptyMap(), message, MmceRegistryReadGuard.currentEpoch());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, MmceStructureSnapshot> snapshots(MmceMachineIndex index) throws Exception {
        return (Map<String, MmceStructureSnapshot>) field(MmceMachineIndex.class, "snapshots").get(index);
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, String> failures(MmceMachineIndex index) throws Exception {
        return (Map<ResourceLocation, String>) field(MmceMachineIndex.class, "failures").get(index);
    }

    @SuppressWarnings("unchecked")
    private static Map<PonderScene, MmceStructureSnapshot> sceneSnapshots() throws Exception {
        return (Map<PonderScene, MmceStructureSnapshot>) field(MmcePonderPlugin.class, "SCENE_SNAPSHOTS").get(null);
    }

    @SuppressWarnings("unchecked")
    private static List<MmceMachineDescriptor> registered(MmcePonderPlugin plugin) throws Exception {
        return (List<MmceMachineDescriptor>) field(MmcePonderPlugin.class, "registered").get(plugin);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
