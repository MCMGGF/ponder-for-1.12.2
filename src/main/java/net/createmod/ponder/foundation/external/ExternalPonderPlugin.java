package net.createmod.ponder.foundation.external;

import java.util.function.Supplier;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.foundation.external.ExternalPonderScenes.ExternalDefinitionSet;
import net.minecraft.util.ResourceLocation;

public class ExternalPonderPlugin implements PonderPlugin {

    private final Supplier<ExternalDefinitionSet> definitions;
    private ExternalDefinitionSet reloadDefinitions;
    private boolean reloading;

    public ExternalPonderPlugin() {
        this(ExternalPonderScenes::loadDefinitions);
    }

    ExternalPonderPlugin(Supplier<ExternalDefinitionSet> definitions) {
        this.definitions = definitions;
    }

    @Override
    public String getModId() {
        return Ponder.MOD_ID;
    }

    @Override
    public void onPonderReloadStart() {
        if (reloading) {
            throw new IllegalStateException("External Ponder definitions are already reloading");
        }
        reloading = true;
        reloadDefinitions = null;
    }

    @Override
    public void onPonderReloadEnd(boolean successful) {
        reloadDefinitions = null;
        reloading = false;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ExternalPonderScenes.registerLoadedScenes(helper, currentDefinitions());
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        ExternalPonderScenes.registerLoadedTags(helper, currentDefinitions());
    }

    private ExternalDefinitionSet currentDefinitions() {
        if (!reloading) {
            return definitions.get();
        }
        if (reloadDefinitions == null) {
            reloadDefinitions = definitions.get();
        }
        return reloadDefinitions;
    }
}
