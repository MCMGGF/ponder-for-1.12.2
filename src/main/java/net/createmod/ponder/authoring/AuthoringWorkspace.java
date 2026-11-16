package net.createmod.ponder.authoring;

import java.nio.file.Path;
import net.createmod.ponder.authoring.assets.StructurePool;
import net.createmod.ponder.authoring.practice.PracticeStepHandlers;
import net.minecraftforge.fml.common.Loader;

/** One local workspace, never the legacy scripts/ponder directory. */
public final class AuthoringWorkspace {
    private static AuthoringWorkspace instance;
    private final AuthoringCompiler compiler;
    private final AuthoringStore store;
    private final StructurePool assets;
    private final net.createmod.ponder.authoring.assets.ImageAssetPool images;

    public AuthoringWorkspace(Path configDirectory) {
        compiler = new AuthoringCompiler();
        PracticeStepHandlers.register(compiler);
        store = new AuthoringStore(configDirectory, compiler);
        try {
            assets = new StructurePool(store.getRoot());
            images = new net.createmod.ponder.authoring.assets.ImageAssetPool(store.getRoot());
            net.createmod.ponder.authoring.assets.ImageStepHandlers.register(compiler, images);
        }
        catch (java.io.IOException invalid) { throw new IllegalStateException("Cannot open authoring assets", invalid); }
    }
    public static synchronized AuthoringWorkspace get() {
        if (instance == null) instance = new AuthoringWorkspace(Loader.instance().getConfigDir().toPath());
        return instance;
    }
    public AuthoringCompiler getCompiler() { return compiler; }
    public AuthoringStore getStore() { return store; }
    public Path getStoreRoot() { return store.getRoot(); }
    public StructurePool getAssets() { return assets; }
    public net.createmod.ponder.authoring.assets.ImageAssetPool getImages() { return images; }
}
