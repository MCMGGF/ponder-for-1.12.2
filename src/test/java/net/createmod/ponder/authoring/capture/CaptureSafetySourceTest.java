package net.createmod.ponder.authoring.capture;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class CaptureSafetySourceTest {
    private String read(String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/net/createmod/ponder", relative)), StandardCharsets.UTF_8);
    }
    @Test void captureNeverCallsRealInteractionOrOpeningAndNeverSerializesSourceNbt() throws Exception {
        String source = read("authoring/capture/VanillaMenuCapture.java") + read("authoring/capture/CaptureDraft.java")
            + read("foundation/ui/AuthoringCaptureController.java") + read("foundation/ui/AuthoringCaptureReviewScreen.java");
        for (String forbidden : new String[] {"sendPacket(", "windowClick(", "processRightClick", "closeScreen(", "onContainerClosed(",
            "openGui(", "displayGUIChest(", "setInventorySlotContents(", "getTagCompound(", "writeToNBT(", "getDeclaredField(",
            ".save(", ".publish(", "FtbQuests"}) assertFalse(source.contains(forbidden), forbidden);
        assertTrue(source.contains("playerInventoryExported")); assertTrue(source.contains("itemNbtExported"));
    }
    @Test void realGuiContainerClicksAreConsumedBeforeNativeSlotDispatchIncludingReleaseAfterCancellation() throws Exception {
        String source = read("foundation/ui/AuthoringCaptureController.java");
        assertTrue(source.contains("GuiScreenEvent.MouseInputEvent.Pre")); assertTrue(source.contains("EventPriority.HIGHEST"));
        int release = source.indexOf("button == consumeReleaseButton"); int noRequest = source.indexOf("if (active == null) return;", release);
        assertTrue(release >= 0 && noRequest > release, "The release must still be consumed after a failed capture clears its request");
        assertTrue(source.contains("boolean owned = event.getGui() == consumeReleaseScreen"));
        assertTrue(source.contains("consumeReleaseScreen != null && mc.currentScreen != consumeReleaseScreen"));
        assertFalse(source.contains("getButtonList().add")); assertFalse(source.contains("buttonList.add"));
        assertTrue(source.contains("handleCaptureButton(screen, x, y, button)"));
    }
    @Test void onlyOwnedReviewCanCommitAndOnlyItsExactReturnedEditorCanRefresh() throws Exception {
        String source = read("foundation/ui/AuthoringCaptureController.java");
        assertTrue(source.contains("if (!isReviewOwned(screen))"));
        assertTrue(source.contains("request.draft.appendTo(request.session, request.scene, request.documentHash)"));
        assertTrue(source.contains("request.onReturn != null && safe && mc.currentScreen == request.editor"));
        assertTrue(source.contains("request.lease.finish()")); assertTrue(source.contains("request.sourceScreen = null"));
        assertTrue(source.contains("request.draft = null")); assertTrue(source.contains("request.review = null"));
    }
    @Test void replacementMetadataRequiresTheAlreadyVerifiedGuiAndCannotSkipVanillaChecks() throws Exception {
        String controller = read("foundation/ui/AuthoringCaptureController.java");
        assertTrue(controller.contains("VanillaMenuCapture.capture(screen, mc.player.openContainer, mc.player.inventory)"));
        String capture = read("authoring/capture/VanillaMenuCapture.java");
        assertTrue(capture.contains("((GuiContainer) screen).inventorySlots != container"));
        assertTrue(capture.contains("return captureVerified(container, playerInventory, null)"), "The old API cannot claim a GUI it never observed");
        assertTrue(capture.contains("!safeInventory(slot.inventory, menu)"));
        assertTrue(capture.contains("slot.inventory != playerInventory || slot.getSlotIndex() != expected"));
        String adapter = read("authoring/capture/HammerFurnaceCaptureAdapter.java");
        assertTrue(adapter.contains("screen.getClass().getSuperclass() != GuiFurnace.class"));
        assertTrue(adapter.contains("container.getClass() != ContainerFurnace.class"));
        assertTrue(adapter.contains("((GuiFurnace) screen).inventorySlots != container"));
        assertTrue(adapter.contains("!source.equals(codeSource) || !source.equals(resourceSource)"));
        assertTrue(adapter.contains("Files.size(path) != 2591232"));
        assertTrue(adapter.contains("JAR_SHA256.equalsIgnoreCase(hash)"));
        assertFalse(adapter.contains("Class.forName(")); assertFalse(adapter.contains("new GuiFurnace"));
    }
}
