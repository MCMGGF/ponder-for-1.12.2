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

    private static BlockGuiDemoDefinition parse(String json) {
        return (BlockGuiDemoDefinition) GuiDemoTimeline.parse(new JsonParser().parse(json).getAsJsonObject());
    }
}
