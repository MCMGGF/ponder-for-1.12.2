package net.createmod.ponder.authoring.capture;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.client.player.inventory.ContainerLocalMenu;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Synthetic exact-source policy checks, not a constructed HammerLib GUI or native acceptance. */
class HammerFurnaceCaptureAdapterTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    private boolean accepted(String gui, String base, String container, String version, String hash, boolean sameSource, boolean modLoaded) {
        return HammerFurnaceCaptureAdapter.exactPolicy(gui, base, container, version, hash, sameSource, modLoaded);
    }
    @Test void onlyTheAuditedExactLoadedPolicyIsAccepted() {
        assertTrue(accepted(HammerFurnaceCaptureAdapter.GUI, GuiFurnace.class.getName(), ContainerFurnace.class.getName(),
            "12.2.58", HammerFurnaceCaptureAdapter.JAR_SHA256, true, true));
        assertTrue(accepted(HammerFurnaceCaptureAdapter.GUI, GuiFurnace.class.getName(), ContainerFurnace.class.getName(),
            "12.2.58", HammerFurnaceCaptureAdapter.JAR_SHA256.toLowerCase(java.util.Locale.ROOT), true, true));
    }
    @Test void missingModOrMismatchedSourceCannotAuthorizeNamesAlone() {
        assertFalse(accepted(HammerFurnaceCaptureAdapter.GUI, GuiFurnace.class.getName(), ContainerFurnace.class.getName(),
            "12.2.58", HammerFurnaceCaptureAdapter.JAR_SHA256, true, false));
        assertFalse(accepted(HammerFurnaceCaptureAdapter.GUI, GuiFurnace.class.getName(), ContainerFurnace.class.getName(),
            "12.2.58", HammerFurnaceCaptureAdapter.JAR_SHA256, false, true));
    }
    @Test void changedVersionHashAndNullMetadataRemainUnsupported() {
        for (String version : new String[] {null, "", "12.2.57", "12.2.58-custom", "1.12.2-12.2.58"})
            assertFalse(accepted(HammerFurnaceCaptureAdapter.GUI, GuiFurnace.class.getName(), ContainerFurnace.class.getName(),
                version, HammerFurnaceCaptureAdapter.JAR_SHA256, true, true));
        for (String hash : new String[] {null, "", new String(new char[64]).replace('\0', '0')})
            assertFalse(accepted(HammerFurnaceCaptureAdapter.GUI, GuiFurnace.class.getName(), ContainerFurnace.class.getName(), "12.2.58", hash, true, true));
    }
    @Test void subclassesWrongBasesContainersAndBrewingAreNotPartOfTheAdapter() {
        assertFalse(accepted(HammerFurnaceCaptureAdapter.GUI + "$Custom", GuiFurnace.class.getName(), ContainerFurnace.class.getName(),
            "12.2.58", HammerFurnaceCaptureAdapter.JAR_SHA256, true, true));
        assertFalse(accepted(HammerFurnaceCaptureAdapter.GUI, "thirdparty.GuiFurnace", ContainerFurnace.class.getName(),
            "12.2.58", HammerFurnaceCaptureAdapter.JAR_SHA256, true, true));
        assertFalse(accepted(HammerFurnaceCaptureAdapter.GUI, GuiFurnace.class.getName(), ContainerFurnace.class.getName() + "$Custom",
            "12.2.58", HammerFurnaceCaptureAdapter.JAR_SHA256, true, true));
        assertFalse(accepted("com.zeitheron.hammercore.client.gui.impl.smooth.GuiBrewingStandSmooth", GuiFurnace.class.getName(), ContainerFurnace.class.getName(),
            "12.2.58", HammerFurnaceCaptureAdapter.JAR_SHA256, true, true));
    }
    @Test void absentAndVanillaObjectsDoNotResolveOrConstructOptionalGuiClasses() {
        assertNull(HammerFurnaceCaptureAdapter.resolve(null, null)); assertFalse(HammerFurnaceCaptureAdapter.supports(null, null));
        InventoryPlayer player = new InventoryPlayer(null);
        GuiFurnace gui = new GuiFurnace(player, new ContainerLocalMenu("minecraft:furnace", new TextComponentString("furnace"), 3));
        assertNull(HammerFurnaceCaptureAdapter.resolve(gui, gui.inventorySlots));
        assertNull(HammerFurnaceCaptureAdapter.resolve(gui, null));
        assertTrue(VanillaMenuCapture.supports(gui, gui.inventorySlots), "The original exact vanilla GUI remains supported without HammerLib");
    }
}
