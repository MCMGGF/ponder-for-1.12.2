package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ThermalResourceBoundaryTest {
    private static final String[] MACHINES = { "furnace", "charger", "insolator", "pulverizer", "sawmill", "smelter" };
    private static final String[] PANELS = { "augment", "config", "redstone" };

    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    private static PonderGuiSnapshotRegistry.Snapshot snapshot(String name, float tick) {
        return PonderGuiSnapshotRegistry.get(new ResourceLocation("ponder_legacy", "gui_snapshot/te_" + name), tick);
    }

    private static ThermalGuiSnapshot thermal(PonderGuiSnapshotRegistry.Snapshot snapshot, int width, int height) {
        assertNotNull(snapshot);
        assertEquals(width, snapshot.displayWidth);
        assertEquals(height, snapshot.displayHeight);
        assertTrue(snapshot.framed);
        assertNull(snapshot.texture);
        assertTrue(snapshot.renderer instanceof ThermalGuiSnapshot);
        return (ThermalGuiSnapshot) snapshot.renderer;
    }

    @Test void legacyBaseAndCompositeIdsReadInstalledMachineTexturesWithoutBundledImages() {
        for (String machine : MACHINES) {
            ThermalGuiSnapshot base = thermal(snapshot(machine + "_base", 0), 198, 166);
            assertEquals(new ResourceLocation("thermalexpansion", "textures/gui/machine/" + machine + ".png"), base.texture);
            assertTrue(base.machineVisible);
            assertNull(base.panel);
            for (String panel : PANELS) {
                int width = "redstone".equals(panel) ? 288 : 276;
                ThermalGuiSnapshot composite = thermal(snapshot(machine + "_" + panel, 0), width, 166);
                assertEquals(base.texture, composite.texture);
                assertEquals(panel, composite.panel);
                assertTrue(composite.machineVisible);
                assertEquals(176, composite.panelX);
                assertEquals(30, composite.panelY);
                assertEquals(width, composite.panelX + composite.panelWidth);
                assertTrue(composite.panelY + composite.panelHeight <= composite.logicalHeight);
            }
        }
    }

    @Test void oldPanelAliasesKeepIdentityAndLogicalDimensions() {
        for (String panel : PANELS) {
            PonderGuiSnapshotRegistry.Snapshot legacy = snapshot("panel_" + panel, 0);
            assertSame(legacy, snapshot("furnace_panel_" + panel, 0));
            int width = "redstone".equals(panel) ? 112 : 100;
            ThermalGuiSnapshot renderer = thermal(legacy, width, 92);
            assertFalse(renderer.machineVisible);
            assertEquals(panel, renderer.panel);
            assertEquals(0, renderer.panelX);
            assertEquals(0, renderer.panelY);
            assertEquals(width, renderer.panelWidth);
            assertEquals(92, renderer.panelHeight);
        }
    }

    @Test void cycleFramesRemainDeterministicAcrossForwardBackwardAndRepeatedLookups() {
        for (String machine : MACHINES) {
            String[] frames = { "base", "config", "augment", "redstone" };
            int[] ticks = { 0, 19, 20, 39, 40, 59, 60, 79, 80, 137, 40, 20, 0, -1, -21 };
            for (int tick : ticks) {
                int frame = (tick / 20) % 4;
                String expected = frame >= 0 ? frames[frame] : "base";
                assertSame(snapshot(machine + "_" + expected, tick), snapshot(machine + "_cycle", tick));
            }
        }
    }

    @Test void missingDependencyResultsAreCachedButResetRechecksTheActualTexture() {
        ThermalGuiSnapshot renderer = ThermalGuiSnapshot.composite("furnace", "config", 276);
        AtomicInteger checks = new AtomicInteger();
        assertFalse(renderer.available(texture -> { checks.incrementAndGet(); assertEquals("thermalexpansion", texture.getNamespace()); return false; }));
        assertFalse(renderer.available(texture -> { fail("Cached missing texture must not repeatedly reopen resources"); return true; }));
        assertEquals(1, checks.get());
        renderer.reset();
        assertTrue(renderer.available(texture -> { checks.incrementAndGet(); return true; }));
        assertTrue(renderer.available(texture -> { fail("Availability is cached until reset"); return false; }));
        assertEquals(2, checks.get());
        renderer.reset();
        assertFalse(renderer.available(texture -> false));
    }

    @Test void legacyAdaptersRemainDrawOnlyAndRejectUnreviewedBlockBindings() throws Exception {
        for (String machine : MACHINES) {
            String gui = "cofh.thermalexpansion.gui.client.machine.Gui" + Character.toUpperCase(machine.charAt(0)) + machine.substring(1);
            Object adapter = new EmbeddedReflectiveGuiSnapshot(gui, "unused");
            assertEquals(machine + ".png", adapterTexture(adapter).texture.getPath().substring("textures/gui/machine/".length()));
            for (String panel : PANELS) {
                String field = "augment".equals(panel) ? "augmentTab" : "config".equals(panel) ? "configTab" : "redstoneTab";
                ThermalGuiSnapshot schematic = adapterTexture(new EmbeddedReflectiveTabGuiSnapshot(gui, "unused", field,
                    "redstone".equals(panel) ? 112 : 100, 92));
                assertEquals(panel, schematic.panel);
                assertFalse(schematic.machineVisible);
            }
        }
        ResourceLocation machine = new ResourceLocation("thermalexpansion", "machine");
        assertNotNull(adapterTexture(new EmbeddedReflectiveGuiSnapshot(machine, 0, new NBTTagCompound())));
        assertNull(adapterTexture(new EmbeddedReflectiveGuiSnapshot(machine, 1, null)));
        NBTTagCompound nbt = new NBTTagCompound(); nbt.setString("unsafe", "payload");
        assertNull(adapterTexture(new EmbeddedReflectiveGuiSnapshot(machine, 0, nbt)));
        assertNull(adapterTexture(new EmbeddedReflectiveGuiSnapshot("unknown.Gui", "unknown.Tile")));
    }

    private static ThermalGuiSnapshot adapterTexture(Object adapter) throws Exception {
        Field field = adapter.getClass().getDeclaredField("texture"); field.setAccessible(true);
        return (ThermalGuiSnapshot) field.get(adapter);
    }

    @Test void machineNamesAndPanelGeometryCannotEscapeTheirDeclaredResourceAndArea() {
        assertEquals("furnace", ThermalGuiSnapshot.machineFromGuiClass("cofh.thermalexpansion.gui.client.machine.GuiFurnace"));
        assertNull(ThermalGuiSnapshot.machineFromGuiClass("java.lang.Runtime"));
        assertNull(ThermalGuiSnapshot.machineFromGuiClass(null));
        for (String name : new String[] { "", "../furnace", "textures/x", "furnace.png", "C:\\x" })
            assertThrows(IllegalArgumentException.class, () -> ThermalGuiSnapshot.base(name));
        assertThrows(IllegalArgumentException.class, () -> ThermalGuiSnapshot.panel("furnace", "unknown", 100, 92));
        assertThrows(IllegalArgumentException.class, () -> ThermalGuiSnapshot.panel("furnace", "config", 10, 92));
        assertThrows(IllegalArgumentException.class, () -> ThermalGuiSnapshot.composite("furnace", "redstone", 276));
    }

    @Test void legacyCopiedPngsAreNotOnTheRuntimeClasspath() {
        for (String machine : MACHINES) for (String frame : new String[] { "base", "augment", "config", "redstone" })
            assertNull(getClass().getResource("/assets/ponder_legacy/textures/gui/thermalexpansion/" + machine + "_snapshot_" + frame + ".png"));
        for (String panel : PANELS)
            assertNull(getClass().getResource("/assets/ponder_legacy/textures/gui/thermalexpansion/furnace_panel_" + panel + ".png"));
        for (String path : new String[] { "gui/logo.png", "gui/widgets.png", "gui/placement_indicator.png", "special/blank.png" })
            assertNull(getClass().getResource("/assets/ponder_legacy/textures/" + path));
    }

    @Test void adaptersDoNotReintroduceNativeGuiConstructionAndLogoUsesAnInstalledItem() throws Exception {
        for (String name : new String[] { "ThermalGuiSnapshot", "EmbeddedReflectiveGuiSnapshot", "EmbeddedReflectiveTabGuiSnapshot", "PonderGuiSnapshotRegistry" }) {
            String source = source(name);
            assertFalse(source.contains("textures/gui/thermalexpansion/"));
            for (String forbidden : new String[] { "Class.forName", "new Container", "displayGuiScreen(", ".initGui(", "sendPacket(" })
                assertFalse(source.contains(forbidden), name + ": " + forbidden);
        }
        String debug = source("PonderDebugScreen");
        assertTrue(debug.contains("net.minecraft.init.Items.BOOK"));
        assertFalse(debug.contains("SHOWCASE_WIDGETS_TEXTURE"));
        assertFalse(debug.contains("textures/gui/logo.png"));
        assertFalse(source("PonderUI").contains("textures/gui/logo.png"));
    }

    private static String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/net/createmod/ponder/foundation/ui/" + name + ".java")), StandardCharsets.UTF_8);
    }
}
