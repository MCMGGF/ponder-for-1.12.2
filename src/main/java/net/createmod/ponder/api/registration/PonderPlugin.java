package net.createmod.ponder.api.registration;

import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.util.ResourceLocation;

public interface PonderPlugin {

    String getModId();

    /** Override when moving existing content whose public IDs must retain their original namespace. */
    default String getRegistrationNamespace() {
        return getModId();
    }

    default void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
    }

    default void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
    }

    default void registerSharedText(SharedTextRegistrationHelper helper) {
    }

    /** Save plugin-owned state before a full index reload. The no-op server shim skips these hooks. */
    default void onPonderReloadStart() {
    }

    /** Restore saved state on failure; called after staging ends, including when the start hook fails. */
    default void onPonderReloadEnd(boolean successful) {
    }

    default void onPonderLevelRestore(PonderLevel ponderLevel) {
    }

    default void indexExclusions(IndexExclusionHelper helper) {
    }
}
