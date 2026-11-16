package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/** Structural regression guards, not a substitute for an OpenGL visual acceptance run. */
class PreviewPlayerIsolationSourceTest {
    @Test
    void actorDepthIsDrawnAfterSolidButBeforeTranslucentBlocks() throws IOException {
        String source = source("PonderDebugScreen.java");
        int start = source.indexOf("private void renderSharedSceneGeometry(");
        int end = source.indexOf("\n    private ", start + 1);
        assertTrue(start >= 0 && end > start);
        String render = source.substring(start, end);
        int solid = render.indexOf("frame.solid");
        int actors = render.indexOf("renderActorPreviews(scene, renderTick)");
        int translucent = render.indexOf("frame.translucent");
        assertTrue(solid >= 0 && solid < actors && actors < translucent,
            "opaque geometry and actors must write depth before glass/water blends over them");
    }

    @Test
    void modelRendererCannotUseRealWorldInteractionsOrGlobalEntityRendering() throws IOException {
        String source = source("PreviewPlayerRenderer.java");
        for (String forbidden : new String[] { "minecraft.world", "mc.world", "playerController", ".spawnEntity(",
            ".setBlockState(", ".onUpdate(", ".onLivingUpdate(", ".sendPacket(", "new RenderPlayer(",
            ".getRenderManager(", ".renderEntity(", ".getLocationSkin(" }) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertTrue(source.contains("new DetachedPonderWorld().createPlayer()"));
        assertTrue(source.contains("new ModelPlayer(0.0F, false)"));
        assertTrue(source.contains("new ModelPlayer(0.0F, true)"));
        assertTrue(source.contains("new ModelMinecart()"));
        assertTrue(source.contains("carrier = null;"));
        assertTrue(source.contains("PreviewRenderState.runIsolated("));
    }

    @Test
    void skinSelectionNeverCallsDownloadTriggeringLookupMethods() throws IOException {
        String source = source("PreviewPlayerSkin.java");
        for (String forbidden : new String[] { ".getLocationSkin(", ".hasLocationSkin(", ".hasSkin(",
            ".loadPlayerTextures(", ".loadProfileTextures(", ".getSkinManager(", ".loadSkin(" }) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertTrue(source.contains(".getTexture(location)"));
        assertTrue(source.contains("completedDownload(texture)"));
    }

    @Test
    void timelineDoesNotDependOnWallClockRealPlayerOrIncrementalEntityTicks() throws IOException {
        String source = source("PreviewPlayerTimeline.java");
        for (String forbidden : new String[] { "Minecraft.getMinecraft", "System.currentTimeMillis", "System.nanoTime",
            "ticksExisted", ".onUpdate(", ".setBlockState(", ".sendPacket(" }) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    private static String source(String file) throws IOException {
        return new String(Files.readAllBytes(Paths.get("src/main/java/net/createmod/ponder/foundation/ui", file)),
            StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
