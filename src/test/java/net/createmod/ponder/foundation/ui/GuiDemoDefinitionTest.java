package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class GuiDemoDefinitionTest {
    @Test
    void resolvesWidgetChangesAndInterpolatesPointerWhenSeekingInEitherDirection() {
        GuiDemoDefinition demo = parse("""
            {
              "width": 96,
              "height": 64,
              "widgets": [
                {"id":"source","type":"slot","x":0,"y":2,"item":"minecraft:coal","count":3},
                {"id":"button","type":"button","x":38,"y":2,"width":18,"height":18,"text":"On"},
                {"id":"bar","type":"progress","x":2,"y":28,"width":70,"height":5}
              ],
              "keyframes": [
                {"tick":40,"pointer":null,"updates":{"bar":{"value":1}}},
                {"tick":20,"pointer":{"target":"button","pressed":true},
                 "updates":{"button":{"active":true},"source":{"item":null},"bar":{"value":0.25}}},
                {"tick":0,"pointer":{"target":"source"},"updates":{"source":{"highlighted":true}}},
                {"tick":25,"updates":{"source":{"highlighted":false}}}
              ]
            }
            """);
        GuiDemoDefinition.Widget source = demo.widgets().get(0);
        GuiDemoDefinition.Widget button = demo.widgets().get(1);
        GuiDemoDefinition.Widget bar = demo.widgets().get(2);
        GuiDemoDefinition.State active = button.at(20);
        assertEquals(40, demo.lastKeyframeTick());
        assertTrue(active.active());
        assertNull(source.at(41).item());
        assertFalse(source.at(41).highlighted());
        assertEquals(1F, bar.at(41).value());
        assertNull(demo.pointerAt(41));
        assertEquals(27.5F, demo.pointerAt(10).x());
        assertFalse(demo.pointerAt(10).pressed());
        assertEquals(3, source.at(10).count());
        assertTrue(source.at(10).highlighted());
        assertFalse(button.at(10).active());
        assertEquals(0.25F, bar.at(20).value());
        assertSame(active, button.at(20));
        assertTrue(demo.pointerAt(20).pressed());
    }

    @Test
    void validatesLayoutAndTimelineWithoutOpeningMinecraft() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
            {"width":16,"height":16,"widgets":[{"id":"tiny","type":"slot","x":0,"y":0}]}
            """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
            {"width":32,"height":32,"keyframes":[{"tick":20,"pointer":{"target":"missing"}}]}
            """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
            {"width":32,"height":32,"keyframes":[{"tick":20},{"tick":20}]}
            """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
            {"width":32,"height":32,"background":"test:textures/../demo.png"}
            """));
    }

    private static GuiDemoDefinition parse(String json) {
        return GuiDemoDefinition.parse(new JsonParser().parse(json).getAsJsonObject());
    }
}
