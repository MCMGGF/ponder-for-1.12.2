package net.createmod.ponder.compat.ftbquests;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderComponentMatcher;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class QuestAutomaticItemBindingsTest {
    private static final ResourceLocation ITEM = new ResourceLocation("minecraft:compass");
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void automaticTargetOnlyCompilesTheMatchingSameItemDocument() {
        Registry registry = new Registry();
        StoryBoardEntry first = entry(ITEM, "first").withStackFilter(stack -> mode(stack) == 1);
        StoryBoardEntry second = entry(ITEM, "second").withStackFilter(stack -> mode(stack) == 2);
        registry.addStoryBoard(first); registry.addStoryBoard(second);
        FtbQuestsCompat.compileTargetScenes(registry, automatic(), Collections.singletonList(stack(2)));
        assertEquals(Collections.singletonList(second), registry.compiled);
        assertEquals(0, registry.manualCompilations);
    }

    @Test void automaticItemsUseUnionWithoutDuplicateEntriesOrUnmatchedThirdDocument() {
        Registry registry = new Registry();
        StoryBoardEntry first = entry(ITEM, "first").withStackFilter(stack -> mode(stack) == 1);
        StoryBoardEntry second = entry(ITEM, "second").withStackFilter(stack -> mode(stack) == 2);
        StoryBoardEntry third = entry(ITEM, "third").withStackFilter(stack -> mode(stack) == 3);
        registry.addStoryBoard(first); registry.addStoryBoard(first); registry.addStoryBoard(second); registry.addStoryBoard(third);
        FtbQuestsCompat.compileTargetScenes(registry, automatic(), Arrays.asList(stack(1), stack(2), stack(1)));
        assertEquals(Arrays.asList(first, second), registry.compiled);
        assertEquals(1, registry.entryCompilations);
    }

    @Test void absentOrNoLongerMatchingAutomaticItemsNeverFallbackToManualComponent() {
        Registry registry = new Registry(); registry.addStoryBoard(entry(ITEM, "only").withStackFilter(stack -> mode(stack) == 1));
        assertTrue(FtbQuestsCompat.compileTargetScenes(registry, automatic(), null).isEmpty());
        assertTrue(FtbQuestsCompat.compileTargetScenes(registry, automatic(), Collections.emptyList()).isEmpty());
        assertTrue(FtbQuestsCompat.compileTargetScenes(registry, automatic(), Collections.singletonList(stack(2))).isEmpty());
        assertEquals(0, registry.manualCompilations); assertEquals(0, registry.entryCompilations);
    }

    @Test void explicitStableAndOrdinalTargetsRemainManualEvenWithNonmatchingItemSnapshot() {
        Registry registry = new Registry();
        StoryBoardEntry first = entry(ITEM, "first").withStackFilter(stack -> mode(stack) == 1);
        StoryBoardEntry second = entry(ITEM, "second").withStackFilter(stack -> mode(stack) == 2);
        registry.addStoryBoard(first); registry.addStoryBoard(second);
        FtbQuestsCompat.compileTargetScenes(registry,
            new QuestSceneLinks.Target(ITEM.toString(), "ponder_authoring:first"), Collections.singletonList(stack(3)));
        assertEquals(Arrays.asList(first, second), registry.compiled);
        FtbQuestsCompat.compileTargetScenes(registry, new QuestSceneLinks.Target(ITEM.toString(), 1), null);
        assertEquals(Arrays.asList(first, second), registry.compiled);
        assertEquals(2, registry.manualCompilations);
    }

    @Test void mostSpecificMmceComponentCannotLeakBaseItemAuthorScenesThroughAutomaticQuestLink() {
        Registry registry = new Registry(); ResourceLocation machine = new ResourceLocation("mmce_test:machine");
        StoryBoardEntry base = entry(ITEM, "base"), specific = entry(machine, "machine");
        registry.addStoryBoard(base); registry.addStoryBoard(specific);
        NBTTagCompound pattern = new NBTTagCompound(); pattern.setInteger("mode", 2);
        registry.registerComponentMatcher(machine, PonderComponentMatcher.exact(ITEM, 0, pattern, pattern));
        FtbQuestsCompat.compileTargetScenes(registry, QuestSceneLinks.Target.automatic(machine.toString()),
            Collections.singletonList(stack(2)));
        assertEquals(Collections.singletonList(specific), registry.compiled);
        int prior = registry.entryCompilations;
        assertTrue(FtbQuestsCompat.compileTargetScenes(registry, automatic(), Collections.singletonList(stack(2))).isEmpty());
        assertEquals(prior, registry.entryCompilations);
    }

    @Test void automaticViewerRetainsIndependentItemNbtSnapshotForRefreshWhileManualViewerHasNone() throws Exception {
        ItemStack source = stack(1); List<ItemStack> input = new ArrayList<>(); input.add(source);
        QuestPonderUI viewer = new QuestPonderUI(ITEM, "ponder_authoring:first", Collections.emptyList(), 1, null, input);
        source.getTagCompound().setInteger("mode", 2); input.clear();
        Field field = QuestPonderUI.class.getDeclaredField("automaticItemStacks"); field.setAccessible(true);
        @SuppressWarnings("unchecked") List<ItemStack> retained = (List<ItemStack>) field.get(viewer);
        assertEquals(1, retained.size()); assertEquals(1, mode(retained.get(0)));
        assertThrows(UnsupportedOperationException.class, () -> retained.clear());
        QuestPonderUI manual = new QuestPonderUI(ITEM, "ponder_authoring:first", Collections.emptyList(), 1, null);
        assertNull(field.get(manual));
    }

    @Test void automaticCompilationDropsFailingFiltersButPreservesMatchingUnfilteredBuiltin() {
        Registry registry = new Registry();
        StoryBoardEntry broken = entry(ITEM, "broken").withStackFilter(stack -> { throw new IllegalStateException("broken"); });
        StoryBoardEntry builtin = entry(ITEM, "builtin"); registry.addStoryBoard(broken); registry.addStoryBoard(builtin);
        FtbQuestsCompat.compileTargetScenes(registry, automatic(), Collections.singletonList(stack(1)));
        assertEquals(Collections.singletonList(builtin), registry.compiled);
    }

    private static QuestSceneLinks.Target automatic() { return QuestSceneLinks.Target.automatic(ITEM.toString()); }
    private static StoryBoardEntry entry(ResourceLocation component, String path) {
        return new PonderStoryBoardEntry((scene, util) -> { throw new AssertionError("No eager storyboard compilation"); },
            "ponder_authoring", new ResourceLocation("ponder_authoring", path), component);
    }
    private static ItemStack stack(int mode) {
        ItemStack stack = new ItemStack(Items.COMPASS); NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("mode", mode); stack.setTagCompound(tag); return stack;
    }
    private static int mode(ItemStack stack) { return stack.getTagCompound().getInteger("mode"); }
    private static class Registry extends PonderSceneRegistry {
        final List<StoryBoardEntry> compiled = new ArrayList<>();
        int manualCompilations, entryCompilations;
        Registry() { super(new PonderLocalization()); }
        @Override public List<PonderScene> compile(ResourceLocation id) { manualCompilations++; return super.compile(id); }
        @Override public List<PonderScene> compile(Collection<StoryBoardEntry> entries) {
            entryCompilations++; compiled.clear(); compiled.addAll(entries); return Collections.emptyList();
        }
    }
}
