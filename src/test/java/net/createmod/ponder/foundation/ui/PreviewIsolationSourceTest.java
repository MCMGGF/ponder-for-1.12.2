package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/** Regression guards for known dangerous call paths, not a native-renderer proof. */
class PreviewIsolationSourceTest {

    private static final Path UI = Paths.get("src/main/java/net/createmod/ponder/foundation/ui");

    @Test
    void sandboxCannotActivateBlocksOrReachIntegratedServer() throws IOException {
        String source = read("SandboxTriggeredBlockGuiSnapshot.java");
        for (String forbidden : new String[] { "onBlockActivated(", "getIntegratedServer(",
            "WorldServer", ".setBlockState(", ".setTileEntity(", "setCanceled(true)" }) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void nativeSnapshotsNeverInitializeTheRealPlayersContainer() throws IOException {
        for (String name : new String[] { "EmbeddedReflectiveGuiSnapshot.java",
            "EmbeddedReflectiveTabGuiSnapshot.java" }) {
            // GuiContainer.initGui writes Minecraft.player.openContainer. Supplying
            // a detached InventoryPlayer to its constructor does not prevent this.
            assertFalse(read(name).contains("gui.setWorldAndResolution("),
                name + " must use a verified read-only draw adapter, not GuiContainer.initGui");
        }
    }

    @Test
    void sceneRendererNeverAttachesTilesToTheLiveWorld() throws IOException {
        String source = read("PonderDebugScreen.java");
        assertFalse(source.contains("setWorld(mc.world)"));
        assertFalse(source.contains("createTileEntity(mc.world"));
        assertTrue(source.contains("detachedPreviewWorld.getTileEntity("));
        assertTrue(source.contains("void onGuiClosed()"));
        assertTrue(source.contains("clearPreviewWorld();"));
    }

    @Test
    void worldAndSnapshotCachesHaveExplicitReleasePaths() throws IOException {
        String world = read("DetachedPonderWorld.java");
        assertFalse(world.contains("Minecraft.getMinecraft()"));
        assertFalse(world.contains("getIntegratedServer("));
        assertTrue(world.contains("new SaveHandlerMP()"));
        assertTrue(world.contains("void clear()"));
        String registry = read("PonderGuiSnapshotRegistry.java");
        assertTrue(registry.contains("void releaseRuntimeState()"));
        assertTrue(registry.contains("snapshot.renderer.reset();"));
        assertTrue(registry.contains("DYNAMIC_SNAPSHOTS.clear();"));
        assertTrue(registry.contains("SandboxTriggeredBlockGuiSnapshot.clearCache();"));
        assertTrue(registry.contains("PlayerInventoryResolver.clearSnapshot();"));
    }

    @Test
    void mmceControllersAreRejectedBeforeNativeTileCallbacks() throws IOException {
        String world = read("DetachedPonderWorld.java");
        int guard = world.indexOf(
            "if (MmcePreviewSafety.isNativeControllerBlock(state.getBlock().getClass()))");
        assertTrue(guard >= 0, "MMCE controller hierarchy guard must remain present");
        int returnWithoutTile = world.indexOf("return null;", guard);
        int tileCheck = world.indexOf(".hasTileEntity(state)");
        int construction = world.indexOf(".createTileEntity(this, state)");
        int deserialization = world.indexOf("tile.readFromNBT(displayNbt)");
        assertTrue(returnWithoutTile > guard && returnWithoutTile < tileCheck,
            "Controllers must return without querying native Tile support");
        assertTrue(tileCheck < construction && construction < deserialization,
            "Controller rejection must precede native construction and deserialization");
    }

    private static String read(String file) throws IOException {
        return new String(Files.readAllBytes(UI.resolve(file)), StandardCharsets.UTF_8);
    }
}
