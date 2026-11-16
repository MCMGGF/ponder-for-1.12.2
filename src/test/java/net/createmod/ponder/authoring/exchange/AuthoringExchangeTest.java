package net.createmod.ponder.authoring.exchange;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import net.createmod.ponder.authoring.AuthoringCompiler;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.foundation.PonderSchematic;
import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuthoringExchangeTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    static AuthoringDocument document(JsonObject... steps) {
        JsonObject root = new JsonObject(); root.addProperty("id", "example:lesson");
        JsonArray items = new JsonArray(); items.add("minecraft:stone"); root.add("items", items);
        JsonObject segment = new JsonObject(); segment.addProperty("id", "main"); segment.addProperty("title", "A \"quoted\" title");
        JsonArray list = new JsonArray(); for (JsonObject step : steps) list.add(step); segment.add("steps", list);
        JsonArray scenes = new JsonArray(); scenes.add(segment); root.add("scenes", scenes); return AuthoringDocument.fromJson(root);
    }
    static JsonObject step(String type) { JsonObject value = new JsonObject(); value.addProperty("type", type); return value; }
    static String wrap(String body) { return "Ponder.registry((event) => { event.create(['minecraft:stone']).scene('main', 'Title', '', (scene, util) => { " + body + " }); });"; }

    @Test void restrictedTextExportDoesNotSilentlyDiscardNewTimelineOrCameraFields() {
        for (String type : new String[] {"zoom_scene", "encapsulate_bounds", "play_sound", "modify_entities_nbt", "modify_item_entities_nbt", "next_scene"}) {
            RestrictedPonderJsCodec.ExportResult result = RestrictedPonderJsCodec.exportText(document(
                net.createmod.ponder.authoring.AuthoringStepTemplates.withType(null, type)));
            assertNull(result.text, type); assertTrue(result.validation.hasErrors(), type);
        }
        JsonObject rotation = step("rotate_camera_y"); rotation.addProperty("degrees", 30); rotation.addProperty("degreesX", 15); rotation.addProperty("duration", 0);
        assertTrue(RestrictedPonderJsCodec.exportText(document(rotation)).validation.hasErrors());
    }

    @Test void restrictedItemListsCannotBroadenNbtBoundScenesDuringExport() {
        JsonObject root = document(step("idle")).toJson(); root.addProperty("nbtFilter", "{Custom:1b}");
        RestrictedPonderJsCodec.ExportResult result = RestrictedPonderJsCodec.exportText(AuthoringDocument.fromJson(root));
        assertNull(result.text); assertTrue(result.validation.getIssues().stream().anyMatch(issue -> "exchange.item_filters".equals(issue.getCode())));
        root.remove("nbtFilter"); JsonArray filters = new JsonArray(); filters.add("{Custom:2b}"); root.add("itemNbtFilters", filters);
        assertNull(RestrictedPonderJsCodec.exportText(AuthoringDocument.fromJson(root)).text);
    }

    @Test void restrictedPonderJsFullyParsesLiteralCallsAndRoundtripsEditedText() {
        String source = wrap("scene.idle(20); scene.text(60, 'First \\'text\\' \\u4e2d\\u6587', [1, 2, 3]).placeNearTarget(); scene.world.setBlock([1,0,-2], 'minecraft:stone', false); scene.world.destroyBlock([1,0,-2]);");
        RestrictedPonderJsCodec.ImportResult imported = RestrictedPonderJsCodec.importText(source, "example:lesson");
        assertNotNull(imported.document, imported.validation.getIssues().toString()); assertFalse(imported.validation.hasErrors());
        assertEquals(4, imported.document.getScenes().get(0).getAsJsonArray("steps").size());
        assertEquals("First 'text' \u4e2d\u6587", imported.document.getScenes().get(0).getAsJsonArray("steps").get(1).getAsJsonObject().get("text").getAsString());
        RestrictedPonderJsCodec.ExportResult output = RestrictedPonderJsCodec.exportText(imported.document);
        assertNotNull(output.text, output.validation.getIssues().toString());
        RestrictedPonderJsCodec.ImportResult again = RestrictedPonderJsCodec.importText(output.text.replace("First", "Edited"), "example:lesson");
        assertTrue(again.document.getScenes().get(0).getAsJsonArray("steps").get(1).getAsJsonObject().get("text").getAsString().startsWith("Edited"));
    }

    @Test void parserRejectsExecutableUnknownAndTrailingSyntaxRatherThanSkippingIt() {
        for (String body : Arrays.asList("while (true) {}", "scene.world.setBlock([0,0,0], getSecret(), false);", "scene.unknown();", "scene.text(20, `${Java.type('x')}`).placeNearTarget();", "scene.idle(2.5);", "scene.world.setBlock([0.5,0,0], 'minecraft:stone', false);")) {
            RestrictedPonderJsCodec.ImportResult result = RestrictedPonderJsCodec.importText(wrap(body), "example:lesson");
            assertNull(result.document, body); assertTrue(result.validation.hasErrors(), body);
        }
        assertNull(RestrictedPonderJsCodec.importText(wrap("scene.idle(1);") + "Java.type('java.io.File');", "example:lesson").document);
        assertNull(RestrictedPonderJsCodec.importText("/* unfinished", "example:lesson").document);
    }

    @Test void hiddenBase64CommentsCannotOverrideCurrentSourceAndNoEvalIsUsed() {
        String source = "// PONDERER_EXPORT_BASE64: eyJpZCI6ImV2aWw6b2xkIn0=\n" + wrap("scene.text(20, 'Current text').placeNearTarget();");
        RestrictedPonderJsCodec.ImportResult result = RestrictedPonderJsCodec.importText(source, "example:lesson");
        assertEquals("example:lesson", result.document.getId());
        assertEquals("Current text", result.document.getScenes().get(0).getAsJsonArray("steps").get(0).getAsJsonObject().get("text").getAsString());
    }

    @Test void exporterDoesNotSilentlyDropUnsupportedSemanticFieldsOrMappings() {
        JsonObject text = step("text"); text.addProperty("text", "Look"); text.addProperty("duration", 20); text.addProperty("color", "red");
        RestrictedPonderJsCodec.ExportResult result = RestrictedPonderJsCodec.exportText(document(text));
        assertNull(result.text); assertTrue(result.validation.hasErrors());
        JsonObject idle = step("idle"); JsonObject root = document(idle).toJson();
        JsonObject mappings = new JsonObject(); mappings.addProperty("modern:block", "minecraft:stone"); root.add("idMappings", mappings);
        assertNull(RestrictedPonderJsCodec.exportText(AuthoringDocument.fromJson(root)).text);
        JsonObject invalid = step("idle"); invalid.addProperty("duration", 0.5);
        assertNull(RestrictedPonderJsCodec.exportText(document(invalid)).text);
        JsonObject extraSegment = document(step("idle")).toJson(); extraSegment.getAsJsonArray("scenes").get(0).getAsJsonObject().addProperty("futureCameraMode", "follow");
        assertNull(RestrictedPonderJsCodec.exportText(AuthoringDocument.fromJson(extraSegment)).text);
    }

    @Test void restrictedSourceHasExplicitBudgetsAndAllowsSecondsOnlyAtExactTicks() {
        RestrictedPonderJsCodec.ImportResult seconds = RestrictedPonderJsCodec.importText(wrap("scene.idleSeconds(1.5);"), "example:lesson");
        assertEquals(30, seconds.document.getScenes().get(0).getAsJsonArray("steps").get(0).getAsJsonObject().get("duration").getAsInt());
        assertNull(RestrictedPonderJsCodec.importText(wrap("scene.idleSeconds(0.012);"), "example:lesson").document);
        char[] huge = new char[RestrictedPonderJsCodec.MAX_SOURCE_CHARS + 1]; java.util.Arrays.fill(huge, ' ');
        assertNull(RestrictedPonderJsCodec.importText(new String(huge), "example:lesson").document);
    }

    @Test void offlineContextUsesOnlyExplicitIdsAndNoTileNbtOrAmbientSecrets() {
        OfflineAiExchange.RegistryContext registry = new OfflineAiExchange.RegistryContext(Collections.singleton("minecraft:stone"), Collections.singleton("minecraft:stone"));
        String prompt = OfflineAiExchange.exportPrompt("Explain stone", document(step("idle")), registry, Collections.singletonMap("example:empty", PonderSchematic.empty()));
        assertTrue(prompt.contains("minecraft:stone")); assertTrue(prompt.contains("tileNbtOmitted")); assertTrue(prompt.contains("offlineOnly"));
        assertFalse(prompt.contains("SessionId")); assertFalse(prompt.contains("accessToken"));
        assertThrows(IllegalArgumentException.class, () -> new OfflineAiExchange.RegistryContext(Collections.singleton("../bad"), Collections.emptyList()));
    }

    @Test void offlineDraftValidationNeverPublishesOrPretendsMissingIdsAreValid() {
        OfflineAiExchange.DraftResult valid = OfflineAiExchange.validateDraft(document(step("idle")).toJson().toString(), new AuthoringCompiler(), null);
        assertNotNull(valid.document); assertFalse(valid.validation.hasErrors());
        JsonObject badRoot = document(step("idle")).toJson(); JsonArray bad = new JsonArray(); bad.add("missing:item"); badRoot.add("items", bad);
        assertTrue(OfflineAiExchange.validateDraft(badRoot.toString(), new AuthoringCompiler(), null).validation.hasErrors());
        assertNull(OfflineAiExchange.validateDraft("{bad JSON", new AuthoringCompiler(), null).document);
    }

    @Test void offlinePromptUsesActiveCompilerNativeAndGuiCapabilitiesWithoutMutatingTheDefault() {
        OfflineAiExchange.RegistryContext registry = new OfflineAiExchange.RegistryContext(Collections.singleton("minecraft:stone"), Collections.singleton("minecraft:stone"));
        AuthoringCompiler active = new AuthoringCompiler(); net.createmod.ponder.authoring.practice.PracticeStepHandlers.register(active);
        String configured = OfflineAiExchange.exportPrompt("Describe actions", document(step("idle")), registry, Collections.emptyMap(), active);
        String defaults = OfflineAiExchange.exportPrompt("Describe actions", document(step("idle")), registry, Collections.emptyMap());
        JsonArray activeCapabilities = new com.google.gson.JsonParser().parse(configured.substring(configured.indexOf('\n') + 1)).getAsJsonObject().getAsJsonArray("capabilities");
        JsonArray defaultCapabilities = new com.google.gson.JsonParser().parse(defaults.substring(defaults.indexOf('\n') + 1)).getAsJsonObject().getAsJsonArray("capabilities");
        java.util.Map<String, String> activeStatus = new java.util.LinkedHashMap<>(), defaultStatus = new java.util.LinkedHashMap<>();
        for (com.google.gson.JsonElement capability : activeCapabilities) activeStatus.put(capability.getAsJsonObject().get("type").getAsString(), capability.getAsJsonObject().get("status").getAsString());
        for (com.google.gson.JsonElement capability : defaultCapabilities) defaultStatus.put(capability.getAsJsonObject().get("type").getAsString(), capability.getAsJsonObject().get("status").getAsString());
        assertEquals("CONVERTED", activeStatus.get("actor_action")); assertEquals("IMPLEMENTED", activeStatus.get("restore_blocks"));
        assertEquals("CONVERTED", activeStatus.get("show_interface")); assertEquals("UNSUPPORTED", defaultStatus.get("show_interface"));
        assertEquals(active.getCapabilities().size(), activeCapabilities.size());
        assertThrows(IllegalArgumentException.class, () -> OfflineAiExchange.exportPrompt("x", document(), registry, Collections.emptyMap(), null));
    }

    @Test void javaExporterProducesOnlyEscapedTextAndRetainsUnsupportedDraftDataForValidation() {
        JsonObject unsupported = step("zoom_scene"); unsupported.addProperty("note", "\u4e2d\u6587 \"\\\n");
        String output = JavaSceneTextExporter.exportText(document(unsupported), "example.ponder", "ExampleLessons");
        assertTrue(output.contains("compiler.compile(document(), assets)")); assertTrue(output.contains("zoom_scene"));
        assertTrue(output.contains("\\u4e2d\\u6587")); assertFalse(output.contains("Base64")); assertFalse(output.contains("ScriptEngine"));
        assertThrows(IllegalArgumentException.class, () -> JavaSceneTextExporter.exportText(document(), "evil;import x", "Example"));
        assertThrows(IllegalArgumentException.class, () -> JavaSceneTextExporter.exportText(document(), "example.ponder", "Bad {}"));
        assertThrows(IllegalArgumentException.class, () -> JavaSceneTextExporter.exportText(document(), "example.class", "Example"));
    }
}
