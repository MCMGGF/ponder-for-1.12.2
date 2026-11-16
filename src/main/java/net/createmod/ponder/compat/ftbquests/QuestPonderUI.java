package net.createmod.ponder.compat.ftbquests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.createmod.ponder.compat.ftbquests.QuestSceneResolver.Result;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** The first displayed scene is selected from the exact snapshot whose IDs were checked. */
class QuestPonderUI extends PonderUI {

    private final ResourceLocation linkedComponent;
    private final long preparedGeneration;
    private List<PonderScene> preparedScenes;
    private ResourceLocation retainedSceneId;
    private final List<ItemStack> automaticItemStacks;

    QuestPonderUI(ResourceLocation component, String sceneId, List<PonderScene> scenes, long generation,
        GuiScreen parent) {
        this(component, sceneId, scenes, generation, parent, null);
    }

    QuestPonderUI(ResourceLocation component, String sceneId, List<PonderScene> scenes, long generation,
        GuiScreen parent, @Nullable List<ItemStack> automaticItemStacks) {
        super(component, 0, parent);
        linkedComponent = component;
        retainedSceneId = new ResourceLocation(sceneId);
        preparedGeneration = generation;
        preparedScenes = new ArrayList<>(scenes);
        this.automaticItemStacks = automaticItemStacks == null ? null : FtbQuestsCompat.copyStacks(automaticItemStacks);
    }

    @Override
    protected List<ResourceLocation> readIndexComponents() {
        PonderScene current = getSelectedScene();
        if (current != null) {
            retainedSceneId = current.getSceneId();
        }
        // A task viewer stays on its linked machine. The general browser still permits group navigation.
        if (readAvailableComponents().contains(linkedComponent)) {
            return Collections.singletonList(linkedComponent);
        }
        preparedScenes = null;
        return Collections.emptyList();
    }

    protected List<ResourceLocation> readAvailableComponents() {
        return super.readIndexComponents();
    }

    @Override
    protected ShowcaseGroupState getShowcaseGroupState() {
        return null;
    }

    @Override
    protected List<PonderScene> compileIndexScenes(ResourceLocation component) {
        if (!linkedComponent.equals(component)) {
            preparedScenes = null;
            throw unavailable(QuestSceneResolver.Failure.COMPONENT_MISSING);
        }
        if (preparedScenes != null) {
            List<PonderScene> scenes = preparedScenes;
            preparedScenes = null;
            if (preparedGeneration != readIndexGeneration()) {
                throw unavailable(QuestSceneResolver.Failure.INDEX_CHANGED);
            }
            return scenes;
        }
        long generation = readIndexGeneration();
        List<PonderScene> scenes = compileFreshScenes(component);
        if (generation != readIndexGeneration()) {
            throw unavailable(QuestSceneResolver.Failure.INDEX_CHANGED);
        }
        return scenes;
    }

    protected List<PonderScene> compileFreshScenes(ResourceLocation component) {
        if (automaticItemStacks != null)
            return FtbQuestsCompat.compileTargetScenes(PonderIndex.getSceneAccess(),
                QuestSceneLinks.Target.automatic(component.toString()), automaticItemStacks);
        return super.compileIndexScenes(component);
    }

    @Override
    protected int selectCompiledScene(List<PonderScene> scenes, @Nullable ResourceLocation sceneId,
        int preferredIndex) {
        ResourceLocation wanted = sceneId == null ? retainedSceneId : sceneId;
        if (wanted == null) {
            throw unavailable(QuestSceneResolver.Failure.SCENE_MISSING);
        }
        Result resolved = QuestSceneResolver.resolveCompiled(
            new QuestSceneLinks.Target(linkedComponent.toString(), wanted.toString()), scenes);
        if (!resolved.isAvailable()) {
            throw unavailable(resolved.failure);
        }
        retainedSceneId = wanted;
        return resolved.index;
    }

    private static IllegalStateException unavailable(QuestSceneResolver.Failure failure) {
        return new IllegalStateException(I18n.format(failure.translationKey));
    }

    @Override
    public void onGuiClosed() {
        PonderScene current = getSelectedScene();
        if (current != null) {
            retainedSceneId = current.getSceneId();
        }
        preparedScenes = null;
        super.onGuiClosed();
    }
}
