package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.Set;
import java.util.function.Function;
import net.createmod.ponder.foundation.ui.GuiDemoDefinition.Pointer;
import org.junit.jupiter.api.Test;

class BlockGuiDemoDefinitionTest {
    @Test
    void evaluatesSlotAndPropertyTracksIndependentlyInEitherDirection() {
        BlockGuiDemoDefinition demo = parse("""
            {
              "source":{"type":"block_gui","block":"minecraft:furnace","meta":3,"tileNbt":"{BurnTime:10s}"},
              "keyframes":[
                {"tick":20,"slots":{"0":null,"2":{"item":"minecraft:iron_ingot"}},"properties":{"2":0}},
                {"tick":4,"slots":{"0":{"item":"minecraft:iron_ore","count":3,"nbt":"{Demo:1b}"}}},
                {"tick":10,"properties":{"2":100}},
                {"tick":2,"properties":{"1":1600}}
              ]
            }
            """);

        assertEquals("minecraft:furnace", demo.block().toString());
        assertEquals(3, demo.meta());
        assertEquals(new BlockGuiDemoDefinition.Insets(0, 0, 0, 0), demo.capture());
        assertEquals(20, demo.lastKeyframeTick());
        assertEquals(Set.of(0, 2), demo.stateAt(20).slots().keySet());
        assertEquals(Set.of(1, 2), demo.stateAt(20).properties().keySet());
        assertTrue(demo.stateAt(20).slots().containsKey(0));
        assertNull(demo.stateAt(20).slots().get(0));
        assertEquals("minecraft:iron_ingot", demo.stateAt(50).slots().get(2).item().toString());
        assertEquals(0, demo.stateAt(50).properties().get(2));

        BlockGuiDemoDefinition.State rewind = demo.stateAt(19.9F);
        BlockGuiDemoDefinition.Stack ore = rewind.slots().get(0);
        assertEquals("minecraft:iron_ore", ore.item().toString());
        assertEquals(3, ore.count());
        assertEquals(1600, rewind.properties().get(1));
        assertEquals(100, rewind.properties().get(2));
        assertFalse(demo.stateAt(3.9F).slots().containsKey(0));
        assertFalse(demo.stateAt(9.9F).properties().containsKey(2));
        assertTrue(demo.stateAt(0).properties().isEmpty());

        ore.nbt().setByte("Demo", (byte) 0);
        assertEquals(1, ore.nbt().getByte("Demo"));
        demo.tileNbt().setShort("BurnTime", (short) 0);
        assertEquals(10, demo.tileNbt().getShort("BurnTime"));
    }

    @Test
    void captureMarginsKeepPointerCoordinatesRelativeToTheGui() {
        BlockGuiDemoDefinition demo = parse("""
            {
              "source":{"type":"block_gui","block":"minecraft:furnace","capture":{"left":40,"top":12,"right":512}},
              "keyframes":[{"tick":0,"pointer":{"x":-40,"y":-12}},{"tick":10,"pointer":{"slot":0}}]
            }
            """);

        assertEquals(new BlockGuiDemoDefinition.Insets(40, 12, 512, 0), demo.capture());
        assertEquals(new Pointer(-40, -12, false), demo.pointerAt(0, index -> new Pointer(20, 8, false)));
        assertEquals(new Pointer(-10, -2, false), demo.pointerAt(5, index -> new Pointer(20, 8, false)));
    }

    @Test
    void rejectsMalformedCaptureAndOutOfMarginPointerCoordinates() {
        for (String capture : Set.of("null", "[]", "{\"width\":1}", "{\"left\":-1}", "{\"right\":513}",
            "{\"bottom\":0.5}", "{\"top\":\"1\"}")) {
            assertThrows(IllegalArgumentException.class, () -> parse("""
                {"source":{"type":"block_gui","block":"minecraft:furnace","capture":%s}}
                """.formatted(capture)), capture);
        }
        for (String pointer : Set.of("{\"x\":-5,\"y\":0}", "{\"x\":0,\"y\":-3}", "{\"y\":0}", "{\"x\":0}")) {
            assertThrows(IllegalArgumentException.class, () -> parse("""
                {
                  "source":{"type":"block_gui","block":"minecraft:furnace","capture":{"left":4,"top":2}},
                  "keyframes":[{"tick":0,"pointer":%s}]
                }
                """.formatted(pointer)), pointer);
        }
    }

    @Test
    void interpolatesSlotAndCoordinatePointerTracksAtSceneTime() {
        BlockGuiDemoDefinition demo = parse("""
            {
              "source":{"type":"block_gui","block":"sample:machine","guiId":-1,"mod":"samplegui"},
              "keyframes":[
                {"tick":4,"pointer":{"slot":2}},
                {"tick":12,"pointer":{"x":28,"y":14,"pressed":true}},
                {"tick":16,"pointer":null}
              ]
            }
            """);
        Function<Integer, Pointer> slotCenter = index -> index == 2 ? new Pointer(12, 6, false) : null;
        assertEquals(-1, demo.guiId());
        assertEquals("samplegui", demo.guiModId());
        assertNull(demo.pointerAt(3, slotCenter));
        assertEquals(new Pointer(28, 14, true), demo.pointerAt(12, slotCenter));
        assertEquals(new Pointer(20, 10, false), demo.pointerAt(8, slotCenter));
        assertEquals(new Pointer(28, 14, true), demo.pointerAt(15, slotCenter));
        assertNull(demo.pointerAt(16, slotCenter));
        assertNull(demo.pointerAt(8, index -> null));
    }

    @Test
    void validatesNativeSourcesAndPreciseTimelineValues() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
            {"source":{"type":"unknown","block":"minecraft:furnace"}}
            """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
            {"source":{"type":"block_gui","block":"minecraft:furnace","mod":"sample"}}
            """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
            {"source":{"type":"block_gui","block":"minecraft:furnace","tileNbt":"["}}
            """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
            {"source":{"type":"block_gui","block":"minecraft:furnace"},"keyframes":[{"tick":24000}]}
            """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
            {"source":{"type":"block_gui","block":"minecraft:furnace"},"keyframes":[{"tick":1,"properties":{"0":2.5}}]}
            """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
            {"source":{"type":"block_gui","block":"minecraft:furnace"},"keyframes":[{"tick":1,"slots":{"01":null}}]}
            """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
            {"source":{"type":"block_gui","block":"minecraft:furnace"},"keyframes":[{"tick":1,"pointer":{"slot":0,"x":1}}]}
            """));
    }

    @Test
    void switchesScreensAtKeyframeBoundariesWithIndependentRewindableState() {
        BlockGuiDemoDefinition demo = parse("""
            {
              "source":{"type":"block_gui","block":"sample:machine","guiId":3,"mod":"samplegui","capture":{"left":4}},
              "screens":{"priority":{"guiId":7,"capture":{"left":12}},"config":{"mod":"sampleconfig"}},
              "keyframes":[
                {"tick":20,"screen":"main"},
                {"tick":10,"screen":"priority","slots":{"0":null},"properties":{"0":9}},
                {"tick":0,"slots":{"0":{"item":"minecraft:iron_ore"}},"properties":{"0":2}},
                {"tick":15,"properties":{"0":10}},
                {"tick":30,"screen":"priority"},
                {"tick":40,"screen":"config"}
              ]
            }
            """);

        assertEquals(40, demo.lastKeyframeTick());
        assertEquals(new BlockGuiDemoDefinition.Screen("main", 3, "samplegui", null,
            new BlockGuiDemoDefinition.Insets(4, 0, 0, 0)), demo.screenAt(9.9F));
        assertEquals(new BlockGuiDemoDefinition.Screen("priority", 7, "samplegui", null,
            new BlockGuiDemoDefinition.Insets(12, 0, 0, 0)), demo.screenAt(10));
        assertEquals(10, demo.stateAt(19.9F).properties().get(0));
        assertEquals("main", demo.screenAt(20).name());
        assertEquals("minecraft:iron_ore", demo.stateAt(20).slots().get(0).item().toString());
        assertEquals(2, demo.stateAt(20).properties().get(0));
        assertEquals("priority", demo.screenAt(30).name());
        assertEquals(10, demo.stateAt(30).properties().get(0));
        assertTrue(demo.stateAt(30).slots().containsKey(0));
        assertNull(demo.stateAt(30).slots().get(0));
        assertEquals(new BlockGuiDemoDefinition.Screen("config", 3, "sampleconfig", null,
            new BlockGuiDemoDefinition.Insets(4, 0, 0, 0)), demo.screenAt(40));
        assertTrue(demo.stateAt(40).properties().isEmpty());
        assertTrue(demo.stateAt(40).slots().isEmpty());
        assertEquals("priority", demo.screenAt(10).name());
        assertEquals(9, demo.stateAt(10).properties().get(0));
        assertEquals(2, demo.stateAt(9.9F).properties().get(0));
    }

    @Test
    void pointerHoldsAcrossSwitchesAndInterpolatesWithinOneScreenVisit() {
        BlockGuiDemoDefinition demo = parse("""
            {
              "source":{"type":"block_gui","block":"sample:machine","guiId":3},
              "screens":{"priority":{"guiId":7,"capture":{"left":12}}},
              "keyframes":[
                {"tick":0,"pointer":{"x":4,"y":4}},
                {"tick":10,"screen":"priority"},
                {"tick":12,"pointer":{"x":-12,"y":4}},
                {"tick":14,"screen":"priority","pointer":{"slot":0,"pressed":true}},
                {"tick":20,"screen":"main"},
                {"tick":24,"pointer":{"x":24,"y":24}},
                {"tick":28,"pointer":{"x":32,"y":32}},
                {"tick":30,"screen":"priority","pointer":null}
              ]
            }
            """);
        Function<Integer, Pointer> slotCenter = index -> new Pointer(4, 12, false);

        assertEquals(new Pointer(4, 4, false), demo.pointerAt(9.9F, slotCenter));
        assertNull(demo.pointerAt(10, slotCenter));
        assertEquals(new Pointer(-4, 8, false), demo.pointerAt(13, slotCenter));
        assertEquals(new Pointer(4, 12, true), demo.pointerAt(19.9F, slotCenter));
        assertEquals(new Pointer(4, 4, false), demo.pointerAt(22, slotCenter));
        assertEquals(new Pointer(28, 28, false), demo.pointerAt(26, slotCenter));
        assertNull(demo.pointerAt(30, slotCenter));
        assertEquals(new Pointer(4, 12, true), demo.pointerAt(16, slotCenter));
    }

    @Test
    void rejectsInvalidScreensAndValidatesPointerAgainstTheSelectedScreen() {
        for (String screens : Set.of("null", "[]", "{\"main\":{}}", "{\"\":{}}",
            "{\"priority\":{\"block\":\"minecraft:chest\"}}", "{\"priority\":{\"mod\":\"sample\"}}",
            "{\"priority\":{\"guiId\":2.5}}", "{\"priority\":{\"capture\":{\"left\":513}}}")) {
            assertThrows(IllegalArgumentException.class, () -> parse("""
                {"source":{"type":"block_gui","block":"minecraft:furnace"},"screens":%s}
                """.formatted(screens)), screens);
        }
        for (String frame : Set.of("{\"tick\":0,\"screen\":\"missing\"}", "{\"tick\":0,\"screen\":null}",
            "{\"tick\":0,\"screen\":\"priority\",\"pointer\":{\"x\":-3,\"y\":0}}")) {
            assertThrows(IllegalArgumentException.class, () -> parse("""
                {
                  "source":{"type":"block_gui","block":"minecraft:furnace","capture":{"left":4}},
                  "screens":{"priority":{"capture":{"left":2}}},
                  "keyframes":[%s]
                }
                """.formatted(frame)), frame);
        }
    }

    @Test
    void factoryScreensInheritCaptureAndUseTheirOwnGuiResolution() {
        BlockGuiDemoDefinition demo = parse("""
            {
              "source":{"type":"block_gui","block":"sample:machine","guiId":3,"mod":"samplegui","capture":{"left":4}},
              "screens":{"priority":{"factory":"sample:priority"}},
              "keyframes":[{"tick":10,"screen":"priority"},{"tick":20,"screen":"main"}]
            }
            """);
        BlockGuiDemoDefinition.Screen screen = demo.screenAt(10);
        assertEquals("sample:priority", screen.factory().toString());
        assertNull(screen.guiId());
        assertNull(screen.guiModId());
        assertEquals(new BlockGuiDemoDefinition.Insets(4, 0, 0, 0), screen.capture());
        assertEquals(3, demo.screenAt(20).guiId());
        assertNull(demo.screenAt(20).factory());

        for (String screenJson : Set.of("{\"factory\":null}", "{\"factory\":\"priority\"}",
            "{\"factory\":\"sample:priority\",\"guiId\":7}", "{\"factory\":\"sample:priority\",\"mod\":\"sample\"}")) {
            assertThrows(IllegalArgumentException.class, () -> parse("""
                {
                  "source":{"type":"block_gui","block":"sample:machine","guiId":3},
                  "screens":{"priority":%s}
                }
                """.formatted(screenJson)), screenJson);
        }
    }

    @Test
    void mainFactoryIsInheritedUnlessTheNamedScreenSelectsItsOwnResolver() {
        BlockGuiDemoDefinition demo = parse("""
            {
              "source":{"type":"block_gui","block":"sample:machine","factory":"sample:main"},
              "screens":{
                "expanded":{"capture":{"left":6}},
                "forge":{"guiId":7},
                "priority":{"factory":"sample:priority"}
              },
              "keyframes":[
                {"tick":10,"screen":"expanded"},
                {"tick":20,"screen":"forge"},
                {"tick":30,"screen":"priority"},
                {"tick":40,"screen":"main"}
              ]
            }
            """);

        assertEquals("sample:main", demo.screenAt(0).factory().toString());
        assertEquals("sample:main", demo.screenAt(10).factory().toString());
        assertEquals(6, demo.screenAt(10).capture().left());
        assertEquals(7, demo.screenAt(20).guiId());
        assertEquals("sample", demo.screenAt(20).guiModId());
        assertNull(demo.screenAt(20).factory());
        assertEquals("sample:priority", demo.screenAt(30).factory().toString());
        assertEquals(demo.screenAt(0), demo.screenAt(40));
        for (String fields : Set.of("\"guiId\":7", "\"mod\":\"sample\"")) {
            assertThrows(IllegalArgumentException.class, () -> parse("""
                {"source":{"type":"block_gui","block":"sample:machine","factory":"sample:main",%s}}
                """.formatted(fields)), fields);
        }
    }

    private static BlockGuiDemoDefinition parse(String json) {
        return (BlockGuiDemoDefinition) GuiDemoTimeline.parse(new JsonParser().parse(json).getAsJsonObject());
    }
}
