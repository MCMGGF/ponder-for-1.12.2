package net.createmod.ponder.authoring.trigger;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.AuthoringValidation;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TriggerDefinitionTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    static JsonObject coordinate(String id) {
        return json("{\"id\":\"test:" + id + "\",\"items\":[\"minecraft:paper\"],\"triggerMode\":\"coordinate\","
            + "\"triggerCoord1\":[0,64,0],\"triggerCoord2\":[2,65,2],"
            + "\"legacyTrigger\":{\"version\":1,\"dimension\":0,\"adapter\":\"coordinate_aabb\",\"scene\":\"main\",\"radius\":0,\"cooldown\":40,\"text\":\"Inspect this structure\"},"
            + "\"scenes\":[{\"id\":\"main\",\"steps\":[{\"type\":\"idle\",\"duration\":20}]}]}");
    }
    static JsonObject pattern(String id, int x, int count) {
        JsonObject root = coordinate(id); root.addProperty("triggerMode", "structure"); root.addProperty("triggerStructure", "minecraft:village_plains");
        JsonObject mapping = root.getAsJsonObject("legacyTrigger"); mapping.addProperty("adapter", "block_pattern");
        mapping.addProperty("sourceStructure", "minecraft:village_plains"); mapping.addProperty("radius", 64);
        mapping.add("origin", json("{\"v\":[" + x + ",64,0]}").get("v"));
        JsonArray blocks = new JsonArray();
        for (int i = 0; i < count; i++) blocks.add(json("{\"offset\":[" + (i % 16) + ",0," + (i / 16) + "],\"block\":\"minecraft:stone\"}"));
        mapping.add("blocks", blocks); return root;
    }
    static TriggerDefinition parse(JsonObject root) { return TriggerDefinition.parse(AuthoringDocument.fromJson(root), dimension -> dimension == 0); }
    private static JsonObject json(String value) { return new JsonParser().parse(value).getAsJsonObject(); }

    @Test void explicitCoordinatesKeepDimensionStableSceneIdentityAndBoundedDefaults() {
        TriggerDefinition definition = parse(coordinate("coordinates"));
        assertEquals(0, definition.getDimension()); assertEquals(0, definition.getRadius());
        assertEquals("ponder_authoring:test/coordinates/main", definition.getRuntimeSceneId().toString());
        assertEquals("minecraft:paper", definition.getComponentId().toString());
        assertEquals(0, definition.getSegmentIndex()); assertTrue(definition.getBlocks().isEmpty());
    }

    @Test void unmappedImportsRemainDraftsInsteadOfGuessingDimensionOrStructureIds() {
        JsonObject root = coordinate("missing"); root.remove("legacyTrigger");
        AuthoringDocument document = AuthoringDocument.fromJson(root);
        assertTrue(AuthoringValidation.validate(document).hasErrors());
        assertEquals("coordinate", document.toJson().get("triggerMode").getAsString());
        root = pattern("wrong_source", 0, 1); root.getAsJsonObject("legacyTrigger").addProperty("sourceStructure", "minecraft:stronghold");
        JsonObject wrong = root; assertThrows(IllegalArgumentException.class, () -> parse(wrong));
    }

    @Test void malformedDimensionsCoordinatesAndUnboundedAreasAreRejected() {
        for (String mutation : new String[] {"fractional", "unknown_dimension", "height", "wide", "radius", "missing_scene"}) {
            JsonObject root = coordinate("invalid"); JsonObject mapping = root.getAsJsonObject("legacyTrigger");
            if ("fractional".equals(mutation)) root.add("triggerCoord1", json("{\"v\":[0.5,64,0]}").get("v"));
            if ("unknown_dimension".equals(mutation)) mapping.addProperty("dimension", 19);
            if ("height".equals(mutation)) root.add("triggerCoord1", json("{\"v\":[0,256,0]}").get("v"));
            if ("wide".equals(mutation)) root.add("triggerCoord2", json("{\"v\":[300,65,2]}").get("v"));
            if ("radius".equals(mutation)) mapping.addProperty("radius", 33);
            if ("missing_scene".equals(mutation)) mapping.addProperty("scene", "absent");
            assertThrows(IllegalArgumentException.class, () -> parse(root), mutation);
        }
    }

    @Test void structuresRequireLoadedBlockPatternsNotModernWorldGenerationLookups() {
        TriggerDefinition rule = parse(pattern("pattern", -1, 2));
        assertEquals(2, rule.getBlocks().size()); assertEquals(-1, rule.getBlocks().get(0).position.getX());
        assertEquals(Blocks.STONE, rule.getBlocks().get(0).state.getBlock());
        assertThrows(UnsupportedOperationException.class, () -> rule.getBlocks().clear());
        JsonObject source = pattern("copy", 0, 1); TriggerDefinition copied = parse(source);
        source.getAsJsonObject("legacyTrigger").getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("block", "minecraft:gold_block");
        assertEquals(Blocks.STONE, copied.getBlocks().get(0).state.getBlock());
    }

    @Test void unknownBlocksAndPropertiesNeverFallBackToAir() {
        for (String block : new String[] {"missing:block", "minecraft:oak_planks"}) {
            JsonObject root = pattern("missing_block", 0, 1);
            root.getAsJsonObject("legacyTrigger").getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("block", block);
            assertThrows(IllegalArgumentException.class, () -> parse(root));
        }
        JsonObject root = pattern("property", 0, 1);
        root.getAsJsonObject("legacyTrigger").getAsJsonArray("blocks").get(0).getAsJsonObject().add("blockProperties", json("{\"invalid\":\"true\"}"));
        assertThrows(IllegalArgumentException.class, () -> parse(root));
    }

    @Test void explicitIdMappingIsSupportedButEmptyDuplicateAndOversizedPatternsAreNot() {
        JsonObject root = pattern("mapped", 0, 1);
        root.add("idMappings", json("{\"minecraft:oak_planks\":\"minecraft:planks\"}"));
        root.getAsJsonObject("legacyTrigger").getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("block", "minecraft:oak_planks");
        assertEquals(Blocks.PLANKS, parse(root).getBlocks().get(0).state.getBlock());
        assertThrows(IllegalArgumentException.class, () -> parse(pattern("empty", 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> parse(pattern("large", 0, 129)));
        JsonObject duplicate = pattern("duplicate", 0, 1);
        JsonArray blocks = duplicate.getAsJsonObject("legacyTrigger").getAsJsonArray("blocks"); blocks.add(blocks.get(0));
        assertThrows(IllegalArgumentException.class, () -> parse(duplicate));
        JsonObject air = pattern("air", 0, 1); air.getAsJsonObject("legacyTrigger").getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("block", "minecraft:air");
        assertThrows(IllegalArgumentException.class, () -> parse(air));
    }

    @Test void automaticOpenIsNeverEnabledByImportedHintMetadata() {
        JsonObject root = coordinate("auto"); root.addProperty("hintAuto", "always");
        assertThrows(IllegalArgumentException.class, () -> parse(root));
        root.getAsJsonObject("legacyTrigger").addProperty("autoOpen", false);
        assertNotNull(parse(root));
        root.getAsJsonObject("legacyTrigger").addProperty("autoOpen", true);
        assertThrows(IllegalArgumentException.class, () -> parse(root));
    }

    @Test void frequencyAndHintTextAreStrictAndSourceMetadataIsRetained() {
        JsonObject root = coordinate("hint"); root.addProperty("hintSubtitle", "until_read");
        assertEquals(TriggerDefinition.Frequency.UNTIL_READ, parse(root).getFrequency());
        root.getAsJsonObject("legacyTrigger").addProperty("frequency", "call_callback");
        assertThrows(IllegalArgumentException.class, () -> parse(root));
        root.getAsJsonObject("legacyTrigger").addProperty("frequency", "always");
        root.getAsJsonObject("legacyTrigger").addProperty("text", "line1\nline2");
        assertThrows(IllegalArgumentException.class, () -> parse(root));
    }
}
