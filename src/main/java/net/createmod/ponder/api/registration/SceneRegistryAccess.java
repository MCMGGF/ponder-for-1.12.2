package net.createmod.ponder.api.registration;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.List;
import java.util.Map;

import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public interface SceneRegistryAccess {

    boolean doScenesExistForId(ResourceLocation id);

    ResourceLocation resolveComponentId(ItemStack stack);

    Collection<Map.Entry<ResourceLocation, StoryBoardEntry>> getRegisteredEntries();

    /** Counts registered storyboards without loading schematics or executing them. */
    default int getSceneCount(ResourceLocation id) {
        int count = 0;
        for (Map.Entry<ResourceLocation, StoryBoardEntry> entry : getRegisteredEntries()) {
            if (entry.getKey().equals(id)) {
                count++;
            }
        }
        return count;
    }

    List<PonderScene> compile(ResourceLocation id);

    /** Compiles only matching entries of the most-specific component selected for this exact item. */
    default List<PonderScene> compileForStack(ItemStack stack) {
        ResourceLocation component = resolveComponentId(stack);
        if (component == null) return Collections.emptyList();
        List<StoryBoardEntry> entries = new ArrayList<>();
        Set<StoryBoardEntry> visited = Collections.newSetFromMap(new IdentityHashMap<StoryBoardEntry, Boolean>());
        for (Map.Entry<ResourceLocation, StoryBoardEntry> registration : getRegisteredEntries()) {
            if (!component.equals(registration.getKey())) continue;
            try {
                if (registration.getValue().matchesStack(stack) && visited.add(registration.getValue()))
                    entries.add(registration.getValue());
            } catch (RuntimeException invalidFilter) {
                // A failed filter never grants access through an unfiltered fallback.
            }
        }
        return compile(entries);
    }

    List<PonderScene> compile(Collection<StoryBoardEntry> entries);

    ItemStack getDisplayStack(ResourceLocation componentId);
}
