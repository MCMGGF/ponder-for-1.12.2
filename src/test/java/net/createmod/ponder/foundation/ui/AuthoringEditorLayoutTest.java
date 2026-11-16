package net.createmod.ponder.foundation.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthoringEditorLayoutTest {
    @Test void malformedDraftsUseDiagnosticModeWhileUnknownObjectStepsStayEditable() {
        assertFalse(AuthoringEditorScreen.hasEditableShape(net.createmod.ponder.authoring.AuthoringDocument.parse("{\"id\":\"x:a\",\"scenes\":[]}")));
        assertFalse(AuthoringEditorScreen.hasEditableShape(net.createmod.ponder.authoring.AuthoringDocument.parse("{\"id\":\"x:a\",\"scenes\":[{\"steps\":[1]}]}")));
        assertTrue(AuthoringEditorScreen.hasEditableShape(net.createmod.ponder.authoring.AuthoringDocument.parse("{\"id\":\"x:a\",\"scenes\":[{\"steps\":[{\"type\":\"unknown\"}]}]}")));
    }
    @Test void compactLayoutShowsExactlyOneUsablePane() {
        for(int pane=0;pane<3;pane++) {
            AuthoringEditorLayout l=new AuthoringEditorLayout(320,240,pane);
            assertTrue(l.compact); int visible=0;
            for(AuthoringEditorLayout.Rect r:new AuthoringEditorLayout.Rect[]{l.list,l.preview,l.properties}) {
                if(r.width>0) { visible++; assertTrue(r.width>=280); assertTrue(r.y+r.height<=240); }
            }
            assertEquals(1,visible);
        }
    }
    @Test void normalPanelsDoNotOverlapOrExceedViewport() {
        for(int width:new int[]{600,640,960,1280,1920}) {
            AuthoringEditorLayout l=new AuthoringEditorLayout(width,720,0);
            assertFalse(l.compact); assertTrue(l.preview.width>0);
            assertTrue(l.list.x+l.list.width<l.preview.x);
            assertTrue(l.preview.x+l.preview.width<l.properties.x);
            assertTrue(l.properties.x+l.properties.width<=width);
        }
    }
}
