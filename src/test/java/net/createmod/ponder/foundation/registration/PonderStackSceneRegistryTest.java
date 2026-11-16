package net.createmod.ponder.foundation.registration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PonderStackSceneRegistryTest {
    private static final ResourceLocation ITEM = new ResourceLocation("minecraft:compass");

    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void sameItemDocumentsKeepIndependentPredicatesAndNeverCompileUnmatchedScenes() {
        CapturingRegistry registry = new CapturingRegistry();
        StoryBoardEntry first = entry(ITEM, "first").withStackFilter(stack -> mode(stack) == 1);
        StoryBoardEntry second = entry(ITEM, "second").withStackFilter(stack -> mode(stack) == 2);
        registry.addStoryBoard(first); registry.addStoryBoard(second);
        registry.compileForStack(stack(1)); assertEquals(Collections.singletonList(first), registry.compiled);
        registry.compileForStack(stack(2)); assertEquals(Collections.singletonList(second), registry.compiled);
        registry.compileForStack(stack(3)); assertTrue(registry.compiled.isEmpty());
        assertNull(registry.resolveComponentId(stack(3)));
        registry.compile(ITEM); assertEquals(Arrays.asList(first, second), registry.compiled);
    }

    @Test void unfilteredBuiltinAndMatchingAuthorScenesBothRemainAvailable() {
        CapturingRegistry registry = new CapturingRegistry();
        StoryBoardEntry builtin = entry(ITEM, "builtin");
        StoryBoardEntry author = entry(ITEM, "author").withStackFilter(stack -> mode(stack) == 2);
        registry.addStoryBoard(builtin); registry.addStoryBoard(author);
        registry.compileForStack(stack(1)); assertEquals(Collections.singletonList(builtin), registry.compiled);
        registry.compileForStack(stack(2)); assertEquals(Arrays.asList(builtin, author), registry.compiled);
        assertEquals(ITEM, registry.resolveComponentId(stack(1)));
    }

    @Test void mmceSpecificComponentStillWinsAndDoesNotLeakUnfilteredBaseScenes() {
        CapturingRegistry registry = new CapturingRegistry();
        ResourceLocation machine = new ResourceLocation("mmce_test:machine");
        StoryBoardEntry base = entry(ITEM, "base");
        StoryBoardEntry specific = entry(machine, "specific");
        registry.addStoryBoard(base); registry.addStoryBoard(specific);
        NBTTagCompound required = new NBTTagCompound(); required.setInteger("mode", 2);
        registry.registerComponentMatcher(machine, PonderComponentMatcher.exact(ITEM, 0, required, required));
        assertEquals(machine, registry.resolveComponentId(stack(2)));
        registry.compileForStack(stack(2)); assertEquals(Collections.singletonList(specific), registry.compiled);
        registry.compileForStack(stack(1)); assertEquals(Collections.singletonList(base), registry.compiled);
        registry.compile(ITEM); assertEquals(Arrays.asList(base, specific), registry.compiled);
    }

    @Test void legacyMatcherListPrefixSemanticsAndSpecificityAreUnchanged() {
        NBTTagCompound pattern = new NBTTagCompound(); NBTTagList required = new NBTTagList();
        required.appendTag(new NBTTagString("first")); pattern.setTag("values", required);
        PonderComponentMatcher matcher = PonderComponentMatcher.exact(ITEM, -1, pattern, pattern);
        ItemStack stack = new ItemStack(Items.COMPASS); NBTTagCompound actual = new NBTTagCompound();
        NBTTagList values = new NBTTagList(); values.appendTag(new NBTTagString("first")); values.appendTag(new NBTTagString("second"));
        actual.setTag("values", values); stack.setTagCompound(actual);
        assertTrue(matcher.matches(stack));
        assertTrue(matcher.getSpecificity() > PonderComponentMatcher.simple(ITEM).getSpecificity());
    }

    @Test void throwingEntryFilterFailsClosedButDoesNotHideOtherValidScenes() {
        CapturingRegistry registry = new CapturingRegistry();
        StoryBoardEntry broken = entry(ITEM, "broken").withStackFilter(stack -> { throw new IllegalStateException("invalid"); });
        registry.addStoryBoard(broken);
        assertNull(registry.resolveComponentId(stack(1)));
        registry.compileForStack(stack(1)); assertTrue(registry.compiled.isEmpty());
        StoryBoardEntry valid = entry(ITEM, "valid"); registry.addStoryBoard(valid);
        assertEquals(ITEM, registry.resolveComponentId(stack(1)));
        registry.compileForStack(stack(1)); assertEquals(Collections.singletonList(valid), registry.compiled);
    }

    @Test void repeatedEntryFiltersAreCombinedNotOverwritten() {
        StoryBoardEntry entry = entry(ITEM, "combined")
            .withStackFilter(stack -> mode(stack) > 0).withStackFilter(stack -> mode(stack) < 3);
        assertFalse(entry.matchesStack(stack(0))); assertTrue(entry.matchesStack(stack(1)));
        assertTrue(entry.matchesStack(stack(2))); assertFalse(entry.matchesStack(stack(3)));
        assertThrows(NullPointerException.class, () -> entry.withStackFilter(null));
    }

    @Test void emptyStackNeverSelectsOrCompilesAnything() {
        CapturingRegistry registry = new CapturingRegistry(); registry.addStoryBoard(entry(ITEM, "base"));
        assertNull(registry.resolveComponentId(null)); assertNull(registry.resolveComponentId(ItemStack.EMPTY));
        registry.compileForStack(null); assertTrue(registry.compiled.isEmpty());
        registry.compileForStack(ItemStack.EMPTY); assertTrue(registry.compiled.isEmpty());
    }

    private static StoryBoardEntry entry(ResourceLocation component, String id) {
        return new PonderStoryBoardEntry((scene, util) -> { throw new AssertionError("Selection compiled a storyboard"); },
            "stack_filter_test", new ResourceLocation("ponder_authoring", id), component);
    }
    private static ItemStack stack(int mode) {
        ItemStack stack = new ItemStack(Items.COMPASS); NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("mode", mode); stack.setTagCompound(nbt); return stack;
    }
    private static int mode(ItemStack stack) { return stack.getTagCompound() == null ? 0 : stack.getTagCompound().getInteger("mode"); }
    private static final class CapturingRegistry extends PonderSceneRegistry {
        final List<StoryBoardEntry> compiled = new ArrayList<>();
        CapturingRegistry() { super(new PonderLocalization()); }
        @Override public List<PonderScene> compile(Collection<StoryBoardEntry> entries) {
            compiled.clear(); compiled.addAll(entries); return Collections.emptyList();
        }
        @Override public List<PonderScene> compileForStack(ItemStack stack) {
            compiled.clear(); return super.compileForStack(stack);
        }
    }
}
