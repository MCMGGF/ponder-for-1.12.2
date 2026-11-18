package net.createmod.ponder.foundation.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import net.createmod.ponder.foundation.content.oil.OilLineOperatingSession;
import net.createmod.ponder.foundation.content.oil.OilLineOperatingSession.Action;
import net.createmod.ponder.foundation.content.oil.OilLineOperatingSession.Station;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OilLineControlScreenTest {
    @Test void sessionsAreOwnedPerScreenAndParentIdentityIsPreserved() {
        GuiScreen parent = new GuiScreen() {};
        OilLineControlScreen first = new OilLineControlScreen(parent);
        OilLineControlScreen second = new OilLineControlScreen(parent);
        assertSame(parent, first.getPonderParent());
        assertNotSame(first.getSession(), second.getSession());
        first.getSession().advance(20);
        assertEquals(20, first.getSession().getTick());
        assertEquals(0, second.getSession().getTick());
        assertTrue(first.doesGuiPauseGame());
    }

    @Test void timeControlsAreForwardOnlyAndSingleAdvancePauses() {
        OilLineControlScreen screen = new OilLineControlScreen(null);
        press(screen, 3); screen.updateScreen();
        assertEquals(1, screen.getSession().getTick());
        press(screen, 5); screen.updateScreen();
        assertEquals(21, screen.getSession().getTick());
        press(screen, 6);
        assertFalse(screen.isPlaying());
        assertEquals(421, screen.getSession().getTick());
        screen.updateScreen();
        assertEquals(421, screen.getSession().getTick());
        press(screen, 4); press(screen, 3); screen.updateScreen();
        assertEquals(422, screen.getSession().getTick());
    }

    @Test void manualActionsPauseAndAffectOnlyTheInjectedLocalSession() {
        OilLineOperatingSession model = new OilLineOperatingSession();
        OilLineControlScreen screen = new OilLineControlScreen(null, model);
        press(screen, 3);
        screen.operate(Action.INSTALL_COMPONENTS);
        assertFalse(screen.isPlaying());
        assertTrue(model.isComponentsInstalled(Station.BOILER));
        press(screen, 3);
        screen.operate(Action.ADD_WATER);
        assertFalse(screen.isPlaying());
        assertTrue(model.getWaterSupply() < 8000 || model.getWater() > 0);
        assertEquals(0, model.getTick());
    }

    @Test void resetStopsPlaybackAndCloseCannotBeResumedOrReset() {
        OilLineControlScreen screen = new OilLineControlScreen(null);
        screen.operate(Action.INSTALL_COMPONENTS);
        press(screen, 6); press(screen, 3); press(screen, 2);
        assertFalse(screen.isPlaying());
        assertEquals(0, screen.getSession().getTick());
        assertFalse(screen.getSession().isComponentsInstalled(Station.BOILER));
        press(screen, 3); screen.onGuiClosed();
        assertTrue(screen.getSession().isClosed());
        assertFalse(screen.isPlaying());
        press(screen, 3); press(screen, 2); screen.updateScreen();
        assertTrue(screen.getSession().isClosed());
        assertFalse(screen.isPlaying());
    }

    @Test void navigationPausesWithoutRewindingTheProcess() {
        OilLineControlScreen screen = new OilLineControlScreen(null);
        press(screen, 6); press(screen, 3); press(screen, 104);
        assertFalse(screen.isPlaying());
        assertEquals(400, screen.getSession().getTick());
        screen.operate(Action.INSTALL_COMPONENTS);
        assertTrue(screen.getSession().isComponentsInstalled(Station.GENERATOR));
        press(screen, 3); press(screen, 112);
        assertFalse(screen.isPlaying());
        assertEquals(400, screen.getSession().getTick());
    }

    @Test void sourceKeepsResizeAndNativeRenderingIsolated() throws Exception {
        String source = source();
        String init = source.substring(source.indexOf("@Override public void initGui()"), source.indexOf("private void rebuildControls()"));
        assertFalse(init.contains("session.reset"));
        assertFalse(init.contains("new OilLineOperatingSession"));
        assertTrue(source.contains("PreviewRenderState.runIsolated"));
        assertTrue(source.contains("PreviewNativeRenderGuard.run"));
        assertTrue(source.contains("finally { minecraft.getRenderItem().zLevel = previousZ; }"));
        assertTrue(source.contains("new GuiCheckBox"));
        assertTrue(source.contains("cell.width - (choice.toggle ? 18 : 8)"));
        assertTrue(source.contains("PreviewTextLayout.wrap"));
        assertTrue(source.contains("return parent;"));
        assertTrue(source.contains("mc.displayGuiScreen(parent)"));
        assertTrue(source.contains("layout.marker.height >= fontRenderer.FONT_HEIGHT"));
        assertTrue(source.contains("stationTooltip(button.id - 100)"));
        assertTrue(source.contains("session.getStationStatus(Station.values()[index])"));
        for (String forbidden : new String[] {"extends GuiContainer", "playerController", "sendPacket(",
            "getConnection(", ".setBlockState(", "inventoryContainer", "new PonderDebugScreen", "new PonderUI"})
            assertFalse(source.contains(forbidden), forbidden);
    }

    private static void press(OilLineControlScreen screen, int id) { screen.actionPerformed(new GuiButton(id, 0, 0, "")); }
    private static String source() throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/oilLine/java/net/createmod/ponder/foundation/ui/OilLineControlScreen.java")), StandardCharsets.UTF_8);
    }
}
