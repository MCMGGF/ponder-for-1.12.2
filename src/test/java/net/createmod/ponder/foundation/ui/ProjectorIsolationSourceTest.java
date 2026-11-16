package net.createmod.ponder.foundation.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Structural guards supplement, but never replace, native-world acceptance. */
class ProjectorIsolationSourceTest {
    @Test void projectionCannotInvokeGuiDepthClearOrReplaceWorld() throws Exception {
        String source=read("PonderDebugScreen.java");
        String body=source.substring(source.indexOf("public void renderWorldProjection("),source.indexOf("public void releaseProjectionResources("));
        assertFalse(body.contains("GlStateManager.clear(")); assertFalse(body.contains("mc.world ="));
        assertFalse(body.contains("renderScenePreview(")); assertTrue(body.contains("renderSharedSceneGeometry(scene, frame, tick, false)"));
        assertTrue(body.contains("previous.restore()"));
    }
    @Test void configUsesIntegratedThreadAndValidatesOwnerDistanceAndLoadedState() throws Exception {
        String source=read("ProjectorConfigScreen.java");
        for(String expected:new String[]{"mc.isSingleplayer()","server.addScheduledTask", "getPlayerByUUID", "owner.dimension!=dimension", "owner.getDistanceSq(pos)>64", "world.isBlockLoaded(pos)"})
            assertTrue(source.contains(expected),expected);
        assertFalse(source.contains("sendPacket("));
    }
    @Test void newScreensKeepRuntimeFieldCompatibility() throws Exception {
        for(String name:new String[]{"AuthoringEditorScreen","AuthoringChoiceScreen","AuthoringImportScreen","AuthoringExchangeScreen","AuthoringReviewScreen","ProjectorConfigScreen"}) {
            String source=read(name+".java");
            assertTrue(source.contains("extends CompatGuiScreen") || source.contains("extends AuthoringGuiScreen"),name);
            assertTrue(source.contains("syncCompatFields()"),name);
        }
        assertTrue(read("AuthoringGuiScreen.java").contains("extends CompatGuiScreen"));
    }
    private static String read(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/net/createmod/ponder/foundation/ui",name)),StandardCharsets.UTF_8);
    }
}
