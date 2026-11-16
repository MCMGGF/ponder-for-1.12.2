package net.createmod.ponder.foundation.ui;

import java.util.Collections;
import java.util.List;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;

/** An unpublished draft uses the one production player without changing the index. */
public final class AuthoringPreviewScreen extends PonderUI {
    private final List<PonderScene> draftScenes;
    private final ResourceLocation component;
    public AuthoringPreviewScreen(List<PonderScene> scenes,int index,GuiScreen parent) {
        super(scenes.get(0).getComponent(),index,parent);
        draftScenes=scenes; component=scenes.get(0).getComponent();
    }
    @Override protected long readIndexGeneration() { return 1; }
    @Override protected List<ResourceLocation> readIndexComponents() { return Collections.singletonList(component); }
    @Override protected List<PonderScene> compileIndexScenes(ResourceLocation id) { return draftScenes; }
}
