package net.createmod.ponder.authoring;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthoringEditsTest {
    @Test void sceneAndStepCommandsDoNotMutateSource() {
        AuthoringDocument original=AuthoringEdits.empty("local:example"); String hash=original.sha256();
        AuthoringDocument doc=AuthoringEdits.addScene(original);
        assertEquals(2,doc.getScenes().size()); assertEquals(hash,original.sha256());
        JsonObject step=new JsonObject(); step.addProperty("type","show_text"); step.addProperty("text","\u4e2d\u6587");
        doc=AuthoringEdits.addStep(doc,0,step); step.addProperty("text","changed");
        assertEquals("\u4e2d\u6587",doc.getScenes().get(0).getAsJsonArray("steps").get(1).getAsJsonObject().get("text").getAsString());
        doc=AuthoringEdits.move(doc,0,1,-1,false);
        assertEquals("show_text",doc.getScenes().get(0).getAsJsonArray("steps").get(0).getAsJsonObject().get("type").getAsString());
        doc=AuthoringEdits.removeStep(doc,0,0); assertEquals(1,doc.getScenes().get(0).getAsJsonArray("steps").size());
        doc=AuthoringEdits.removeScene(doc,1); assertEquals(1,doc.getScenes().size());
        AuthoringDocument finalDoc=doc; assertThrows(IllegalArgumentException.class,()->AuthoringEdits.removeScene(finalDoc,0));
    }
    @Test void sceneNamesStayUniqueAfterDeleteAndReadd() {
        AuthoringDocument doc=AuthoringEdits.addScene(AuthoringEdits.empty("local:example"));
        doc=AuthoringEdits.addScene(doc); doc=AuthoringEdits.removeScene(doc,1); doc=AuthoringEdits.addScene(doc);
        assertNotEquals(doc.getScenes().get(1).get("id"),doc.getScenes().get(2).get("id"));
    }
}
