package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import net.createmod.ponder.authoring.AuthoringCompiler;
import net.createmod.ponder.authoring.AuthoringCoordinateEdits;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

/** Pure edit semantics and input wiring; native pointer/render acceptance remains separate. */
class AuthoringCoordinatePickerContractTest {
    private static JsonObject step(String type) {
        JsonObject value = new JsonObject(); value.addProperty("type", type); return value;
    }
    private static String editorSource() throws Exception {
        return new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/createmod/ponder/foundation/ui/AuthoringEditorScreen.java")), StandardCharsets.UTF_8);
    }
    private static String method(String source, String declaration) {
        int start = source.indexOf(declaration); assertTrue(start >= 0, declaration);
        int opening = source.indexOf('{', start), depth = 0;
        for (int i = opening; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            else if (source.charAt(i) == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("Unclosed method " + declaration);
    }
    private static void before(String body, String first, String second) {
        int a = body.indexOf(first), b = body.indexOf(second);
        assertTrue(a >= 0, first); assertTrue(b >= 0, second);
        assertTrue(a < b, first + " must precede " + second);
    }

    @Test void pickerPinsOriginalSessionSceneStepAndHashBeforeAnyWrite() throws Exception {
        String source = editorSource(), begin = method(source, "private void beginPick(");
        assertTrue(begin.contains("pickSession=session"));
        assertTrue(begin.contains("pickScene=scene"));
        assertTrue(begin.contains("pickStep=step"));
        assertTrue(begin.contains("pickDocumentHash=session.getDocument().sha256()"));
        String pick = method(source, "private boolean pickBlock(");
        for (String check : new String[] {"session!=pickSession", "scene!=pickScene", "step!=pickStep",
            "!session.getDocument().sha256().equals(pickDocumentHash)"}) {
            before(pick, check, "renderer.editorPickBlock(");
        }
        before(pick, "cancelPick(); status=tr(\"pick_changed\"); return true;", "AuthoringCoordinateEdits.point(");
        String mouse = method(source, "protected void mouseClicked(");
        String navigation = mouse.substring(mouse.indexOf("if(layout.list.contains("));
        before(navigation, "cancelPick()", "scene=sceneOffset+row");
        before(navigation, "cancelPick()", "step=index");
        assertTrue(method(source, "private void cancelPick(").contains("pickSession=null"));
    }

    @Test void absolutePointsUseDisplayedSurfaceButBlockSelectionsKeepSourceBlockIdentity() throws Exception {
        String pick = method(editorSource(), "private boolean pickBlock(");
        assertTrue(pick.contains("AuthoringCoordinateEdits.point(session.getDocument(),scene,step,pickField,hit.displayedPoint)"));
        assertTrue(pick.contains("AuthoringCoordinateEdits.block(session.getDocument(),scene,step,pickField,hit.block)"));
        assertTrue(pick.contains("AuthoringCoordinateEdits.selection(session.getDocument(),scene,step,pickFirst,hit.block)"));
        assertFalse(pick.contains("pickField,hit.point)"));
        String begin = method(editorSource(), "private void beginPick(");
        assertTrue(begin.contains("AuthoringDocument preview=AuthoringEdits.replaceStep("));
        assertTrue(begin.contains("workspace.getCompiler().compile(preview,"));
        assertFalse(begin.contains("session.replace("));
        assertFalse(begin.contains("session.save("));
    }

    @Test void selectingAbsoluteOverlayPointRemovesCompetingSelectionWithoutChangingOtherFields() {
        for (String type : Arrays.asList("text", "shared_text", "show_image")) {
            AuthoringDocument original = AuthoringDocument.parse("{\"id\":\"local:picker\",\"scenes\":[{\"id\":\"main\",\"steps\":[{\"type\":\""
                + type + "\",\"blockPos\":[0,0,0],\"blockPos2\":[1,1,1],\"positions\":[[2,2,2]],\"fullScene\":true,\"future\":{\"keep\":7}}]}]}");
            String before = original.sha256(); Vec3d displayed = new Vec3d(5.5, 2.5, -3.25);
            AuthoringDocument changed = AuthoringCoordinateEdits.point(original, 0, 0, "point", displayed);
            JsonObject edited = changed.getScenes().get(0).getAsJsonArray("steps").get(0).getAsJsonObject();
            assertEquals(displayed, AuthoringCompiler.vector(edited, "point", Vec3d.ZERO));
            for (String key : Arrays.asList("blockPos", "blockPos2", "positions", "fullScene")) assertFalse(edited.has(key), key);
            assertEquals(7, edited.getAsJsonObject("future").get("keep").getAsInt());
            assertEquals(before, original.sha256());
        }
    }

    @Test void linkedMotionOffersOnlyFieldsThatItsCompilerActuallyUses() {
        JsonObject move = step("move_section"); move.addProperty("linkId", "base");
        assertEquals(Collections.emptyList(), AuthoringCoordinateEdits.fields(move));
        JsonObject rotate = step("rotate_section"); rotate.addProperty("linkId", "base");
        assertEquals(Collections.singletonList("pivot"), AuthoringCoordinateEdits.fields(rotate));
        move.remove("linkId"); rotate.remove("linkId");
        assertTrue(AuthoringCoordinateEdits.fields(move).contains("selection"));
        assertTrue(AuthoringCoordinateEdits.fields(rotate).containsAll(Arrays.asList("selection", "pivot")));
    }

    @Test void playerLookAtIsOfferedOnlyWithoutExplicitRotationOrInvalidNbt() {
        JsonObject player = step("create_entity"); player.addProperty("entity", "minecraft:player");
        assertTrue(AuthoringCoordinateEdits.fields(player).contains("lookAt"));
        for (String key : Arrays.asList("yaw", "pitch")) {
            player.addProperty(key, 0);
            assertFalse(AuthoringCoordinateEdits.fields(player).contains("lookAt"), key);
            player.remove(key);
        }
        for (String nbt : Arrays.asList("{Rotation:[0f,0f]}", "{Rotation:[1f,2f]}", "{not valid")) {
            player.addProperty("nbt", nbt);
            assertFalse(AuthoringCoordinateEdits.fields(player).contains("lookAt"), nbt);
        }
        player.addProperty("nbt", "{Invisible:1b}");
        assertTrue(AuthoringCoordinateEdits.fields(player).contains("lookAt"));
        player.remove("nbt"); player.addProperty("entity", "minecraft:parrot");
        assertEquals(Collections.singletonList("pos"), AuthoringCoordinateEdits.fields(player));
    }
}
