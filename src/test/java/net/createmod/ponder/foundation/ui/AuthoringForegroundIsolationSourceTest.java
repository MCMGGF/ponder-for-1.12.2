package net.createmod.ponder.foundation.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Native screenshots still decide whether other mods leave visible overlays. */
class AuthoringForegroundIsolationSourceTest {
    @Test void opaqueAuthoringSurfaceDoesNotRenderTheRealWorldBehindVirtualSlots() throws Exception {
        String source = read("foundation/ui/AuthoringGuiScreen.java");
        assertTrue(source.contains("0xFF14191C"));
        assertFalse(source.contains("super.drawDefaultBackground()"));
        assertTrue(source.contains("BackgroundDrawnEvent(this)"));
        for (String name : new String[] {"AuthoringEditorScreen", "AuthoringChoiceScreen", "AuthoringExchangeScreen",
            "AuthoringImportScreen", "AuthoringReviewScreen", "PracticeScreen"}) {
            assertTrue(read("foundation/ui/" + name + ".java").contains("extends AuthoringGuiScreen"), name);
            assertTrue(read("foundation/ui/JeiScreenCompat.java").contains(name + ".class"), name);
        }
    }

    @Test void hudCancellationIsLimitedToCurrentAuthoringScreenAndNeedsNoPersistentFlag() throws Exception {
        String source = read("client/ClientProxy.java");
        int start = source.indexOf("public void onAuthoringHud(");
        assertTrue(start >= 0);
        String body = source.substring(start, source.indexOf("\n    }", start));
        assertTrue(body.contains("RenderGameOverlayEvent.ElementType.ALL"));
        assertTrue(body.contains("currentScreen instanceof net.createmod.ponder.foundation.ui.AuthoringGuiScreen"));
        assertTrue(body.contains("event.setCanceled(true)"));
        assertFalse(body.contains("gameSettings"));
    }

    @Test void practiceGuardsNativeItemAndTooltipCallbacksAndExposesFailures() throws Exception {
        String source = read("foundation/ui/PracticeScreen.java");
        assertTrue(source.contains("PreviewNativeRenderGuard.run(() -> renderToolTip"));
        assertTrue(source.contains("PreviewNativeRenderGuard.run(() -> {"));
        assertTrue(source.contains("public java.util.Set<String> getRenderFailureKeys()"));
        assertTrue(source.contains("java.util.Collections.unmodifiableSet"));
        assertTrue(source.contains("failedItems.contains"));
        assertTrue(source.contains("playing = false;"));
        assertTrue(source.contains("float previousZ = itemRender.zLevel;"));
        assertTrue(source.contains("finally { itemRender.zLevel = previousZ; }"));
    }

    private static String read(String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/net/createmod/ponder", relative)), StandardCharsets.UTF_8);
    }
}
