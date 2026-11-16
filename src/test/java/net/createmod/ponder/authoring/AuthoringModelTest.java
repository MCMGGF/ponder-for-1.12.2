package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSchematic;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuthoringModelTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    static JsonObject step(String type) { JsonObject object = new JsonObject(); object.addProperty("type", type); return object; }
    static JsonArray pos(int x, int y, int z) { JsonArray array = new JsonArray(); array.add(x); array.add(y); array.add(z); return array; }
    static AuthoringDocument document(JsonObject... steps) {
        JsonObject root = new JsonObject(); root.addProperty("id", "example:lesson");
        JsonArray items = new JsonArray(); items.add("minecraft:stone"); root.add("items", items);
        JsonObject scene = new JsonObject(); scene.addProperty("id", "main"); scene.addProperty("title", "Lesson");
        JsonArray list = new JsonArray(); for (JsonObject step : steps) list.add(step); scene.add("steps", list);
        JsonArray scenes = new JsonArray(); scenes.add(scene); root.add("scenes", scenes);
        return AuthoringDocument.fromJson(root);
    }

    @Test void documentRetainsUnknownUpstreamDataWithoutExposingMutableState() {
        JsonObject root = document(step("idle")).toJson();
        root.addProperty("futureFeature", "keep me");
        root.getAsJsonObject("source").addProperty("originalId", "ponderer:lesson");
        AuthoringDocument document = AuthoringDocument.fromJson(root);
        root.addProperty("id", "evil:replacement");
        JsonObject view = document.toJson(); view.addProperty("futureFeature", "changed");
        document.getScenes().get(0).addProperty("id", "changed");
        assertEquals("example:lesson", document.getId());
        assertEquals("keep me", document.toJson().get("futureFeature").getAsString());
        assertEquals("ponderer:lesson", document.toJson().getAsJsonObject("source").get("originalId").getAsString());
        assertEquals("ponder_authoring:example/lesson/main", document.getRuntimeSceneId(0).toString());
    }

    @Test void rawSingleSceneStepsAreWrappedWithoutLosingOriginalFields() {
        JsonObject root = document().toJson(); root.remove("scenes");
        JsonArray steps = new JsonArray(); steps.add(step("idle")); root.add("steps", steps);
        AuthoringDocument document = AuthoringDocument.fromJson(root);
        assertEquals(1, document.getScenes().size());
        assertTrue(document.toJson().has("steps"));
    }

    @Test void allThirtyNineUpstreamCapabilitiesHaveExplicitReasons() {
        assertEquals(39, AuthoringValidation.capabilities().size());
        for (AuthoringValidation.Capability capability : AuthoringValidation.capabilities().values()) {
            assertNotNull(capability.getStatus()); assertFalse(capability.getReason().isEmpty());
        }
        assertEquals(AuthoringValidation.Status.CONVERTED, AuthoringValidation.capabilities().get("modify_entities_nbt").getStatus());
    }

    @Test void unknownStepsSurviveDraftRoundTripButCannotCompile() {
        JsonObject future = step("future_jump"); future.addProperty("secret", 7);
        AuthoringDocument document = AuthoringDocument.parse(document(future).toJson().toString());
        assertEquals(7, document.getScenes().get(0).getAsJsonArray("steps").get(0).getAsJsonObject().get("secret").getAsInt());
        AuthoringCompiler.CompilationException error = assertThrows(AuthoringCompiler.CompilationException.class,
            () -> new AuthoringCompiler().compile(document, null));
        assertEquals("/scenes/0/steps/0/type", error.getValidation().getIssues().get(0).getPath());
    }

    @Test void malformedConvertedStepsAreNotLoggedAsSuccess() {
        for (String type : new String[] {"play_sound", "encapsulate_bounds", "modify_entities_nbt", "modify_item_entities_nbt"})
            assertThrows(AuthoringCompiler.CompilationException.class, () -> new AuthoringCompiler().compile(document(step(type)), null));
        assertThrows(IllegalArgumentException.class, () -> new AuthoringCompiler().registerHandler("zoom_scene", null));
        assertEquals(0, new AuthoringCompiler().compile(document(step("next_scene")), null).get(0).getTotalIdleTicks());
    }

    @Test void knownBlocksAndTextProduceExistingWorldAndOverlayTimelineWithSourceMarkers() {
        JsonObject block = step("set_block"); block.addProperty("block", "minecraft:stone"); block.add("blockPos", pos(1, 0, 1));
        JsonObject idle = step("idle"); idle.addProperty("duration", 12);
        JsonObject text = step("text"); text.addProperty("text", "Inspect the block"); text.add("blockPos", pos(1, 0, 1));
        List<PonderScene> scenes = new AuthoringCompiler().compile(document(block, idle, text), null);
        assertEquals(1, scenes.size());
        assertEquals(12, scenes.get(0).getTotalIdleTicks());
        assertEquals(2, scenes.get(0).getWorldEvents().size());
        assertEquals(1, scenes.get(0).getWorldEvents().stream().filter(event -> event.getType() == PonderScene.WorldEventType.SET_BLOCK).count());
        assertEquals(net.createmod.ponder.foundation.SectionOwnershipEvent.Action.PLACE,
            scenes.get(0).getWorldEvents().get(1).getSectionOwnership().getAction());
        assertEquals(1, scenes.get(0).getOverlayEvents().size());
        assertEquals(12, scenes.get(0).getOverlayEvents().get(0).getTick());
        assertTrue(scenes.get(0).getOperationLog().stream().anyMatch(value -> value.contains("/scenes/0/steps/2")));
        assertEquals("ponder_authoring:example/lesson/main", scenes.get(0).getSceneId().toString());
    }

    @Test void modernUnknownIdsRequireAnExplicitMappingAndNeverBecomeAir() {
        JsonObject block = step("set_block"); block.addProperty("block", "minecraft:oak_planks"); block.add("blockPos", pos(0, 0, 0));
        AuthoringDocument unknown = document(block);
        assertTrue(new AuthoringCompiler().validate(unknown, null).hasErrors());
        assertThrows(AuthoringCompiler.CompilationException.class, () -> new AuthoringCompiler().compile(unknown, null));
        JsonObject root = unknown.toJson(), mappings = new JsonObject(), target = new JsonObject();
        target.addProperty("id", "minecraft:planks"); target.addProperty("meta", 0); mappings.add("minecraft:oak_planks", target); root.add("idMappings", mappings);
        PonderScene mapped = new AuthoringCompiler().compile(AuthoringDocument.fromJson(root), null).get(0);
        assertTrue(mapped.getWorldEvents().get(0).getStateDescription().contains("planks"));
    }

    @Test void invalidPropertiesAndOversizedOrFractionalSelectionsFailBeforeExpansion() {
        JsonObject block = step("set_block"); block.addProperty("block", "minecraft:stone"); block.add("blockPos", pos(0, 0, 0));
        JsonObject properties = new JsonObject(); properties.addProperty("does_not_exist", "true"); block.add("blockProperties", properties);
        assertTrue(new AuthoringCompiler().validate(document(block), null).hasErrors());
        JsonObject outline = step("highlight_section"); outline.add("blockPos", pos(-30000000, 0, 0)); outline.add("blockPos2", pos(30000000, 1, 1));
        assertTrue(new AuthoringCompiler().validate(document(outline), null).hasErrors());
        JsonArray fractional = pos(0, 0, 0); fractional.set(0, new com.google.gson.JsonPrimitive(0.5)); outline.add("blockPos", fractional); outline.remove("blockPos2");
        assertTrue(new AuthoringCompiler().validate(document(outline), null).hasErrors());
    }

    @Test void missingAssetsAreHardErrorsAndLocalStructureTileNbtIsPreserved() {
        JsonObject show = step("show_structure"); show.addProperty("structure", "example:platform");
        assertTrue(new AuthoringCompiler().validate(document(show), reference -> null).hasErrors());
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<BlockPos, IBlockState>(); blocks.put(BlockPos.ORIGIN, Blocks.CHEST.getDefaultState());
        NBTTagCompound nbt = new NBTTagCompound(); nbt.setString("CustomName", "Teaching chest");
        PonderSchematic schematic = PonderSchematic.of(new BlockPos(1, 1, 1), blocks, Collections.singletonMap(BlockPos.ORIGIN, nbt));
        PonderScene compiled = new AuthoringCompiler().compile(document(show), reference -> schematic).get(0);
        assertEquals("Teaching chest", compiled.getSchematic().getBlockEntityNbt(BlockPos.ORIGIN).getString("CustomName"));
    }

    @Test void itemAndPlayerStepsCreateRealPreviewActorsNotGenericEntityCallbacks() {
        JsonObject player = step("create_entity"); player.addProperty("entity", "minecraft:player"); player.add("pos", pos(1, 1, 1));
        JsonObject item = step("create_item_entity"); item.addProperty("item", "minecraft:compass"); item.add("pos", pos(2, 1, 1));
        PonderScene scene = new AuthoringCompiler().compile(document(player, item), null).get(0);
        assertEquals(2, scene.getActorEvents().size());
        assertEquals(PonderScene.ActorKind.PLAYER, scene.getActorEvents().get(0).getActorKind());
        assertEquals(PonderScene.ActorKind.ITEM, scene.getActorEvents().get(1).getActorKind());
        JsonObject zombie = step("create_entity"); zombie.addProperty("entity", "minecraft:zombie");
        assertTrue(new AuthoringCompiler().validate(document(zombie), null).hasErrors());
    }

    @Test void registeredOfflineExtensionUsesSharedStepStateAndCannotReplaceBuiltins() {
        AuthoringCompiler compiler = new AuthoringCompiler();
        compiler.registerHandler("show_interface", new AuthoringCompiler.StepHandler() {
            public void validate(AuthoringDocument document, JsonObject step, String path, AuthoringValidation.Builder issues) { }
            public void apply(net.createmod.ponder.api.scene.SceneBuilder scene, net.createmod.ponder.api.scene.SceneBuildingUtil util,
                JsonObject step, AuthoringCompiler.StepContext context) {
                int old = context.getState().containsKey("calls") ? (Integer) context.getState().get("calls") : 0;
                context.getState().put("calls", old + 1); scene.idle(old + 1);
            }
        });
        assertEquals(3, compiler.compile(document(step("show_interface"), step("show_interface")), null).get(0).getTotalIdleTicks());
        assertThrows(IllegalArgumentException.class, () -> compiler.registerHandler("idle", null));
    }

    @Test void deepJsonAndSnbtAreRejectedBeforeRecursiveParsersCanOverflow() {
        String nested = "{}";
        for (int i = 0; i < 70; i++) nested = "{\"nested\":" + nested + "}";
        final String json = nested;
        assertThrows(IllegalArgumentException.class, () -> AuthoringDocument.parse(json));
        JsonObject block = step("modify_block_entity_nbt"); block.add("blockPos", pos(0, 0, 0));
        String snbt = "{}"; for (int i = 0; i < 70; i++) snbt = "{a:" + snbt + "}"; block.addProperty("nbt", snbt);
        assertTrue(new AuthoringCompiler().validate(document(block), null).hasErrors());
    }

    @Test void nbtBindingFiltersAndEntityLevelItemNbtAreNotSilentlyDiscarded() {
        JsonObject root = document(step("idle")).toJson(); root.addProperty("nbtFilter", "{Custom:1}");
        assertTrue(AuthoringValidation.validate(AuthoringDocument.fromJson(root)).canPublish());
        root.addProperty("nbtFilter", "{Custom:");
        assertFalse(AuthoringValidation.validate(AuthoringDocument.fromJson(root)).canPublish());
        JsonObject item = step("create_item_entity"); item.addProperty("item", "minecraft:stone"); item.addProperty("nbt", "{Motion:[1d,0d,0d]}");
        assertFalse(new AuthoringCompiler().validate(document(item), null).hasErrors());
        item.addProperty("nbt", "{UUIDMost:1L,UUIDLeast:2L}");
        assertTrue(new AuthoringCompiler().validate(document(item), null).hasErrors());
    }

    @Test void pooledStructureAndFollowingSegmentInheritTheExplicitUpstreamAsset() {
        JsonObject root = document(step("show_structure")).toJson(); JsonArray structures = new JsonArray();
        structures.add("example:one"); structures.add("example:two"); root.add("structures", structures);
        JsonObject first = root.getAsJsonArray("scenes").get(0).getAsJsonObject();
        first.getAsJsonArray("steps").get(0).getAsJsonObject().addProperty("structure", 2);
        JsonObject second = new JsonObject(); second.addProperty("id", "second"); second.add("steps", new JsonArray()); root.getAsJsonArray("scenes").add(second);
        PonderSchematic one = PonderSchematic.of(new BlockPos(1, 1, 1), Collections.singletonMap(BlockPos.ORIGIN, Blocks.STONE.getDefaultState()), Collections.emptyMap());
        PonderSchematic two = PonderSchematic.of(new BlockPos(1, 1, 1), Collections.singletonMap(BlockPos.ORIGIN, Blocks.GOLD_BLOCK.getDefaultState()), Collections.emptyMap());
        List<PonderScene> scenes = new AuthoringCompiler().compile(AuthoringDocument.fromJson(root), id -> "example:two".equals(id) ? two : one);
        assertEquals(2, scenes.size());
        assertEquals(Blocks.GOLD_BLOCK, scenes.get(0).getSchematic().getBlocks().get(BlockPos.ORIGIN).getBlock());
        assertEquals(Blocks.GOLD_BLOCK, scenes.get(1).getSchematic().getBlocks().get(BlockPos.ORIGIN).getBlock());
    }

    @Test void sectionLinksCanMoveWithoutRepeatingTheirSelectionButUnknownLinksFailValidation() {
        JsonObject show = step("show_section_and_merge"); show.add("blockPos", pos(0, 0, 0)); show.addProperty("linkId", "platform");
        JsonObject move = step("move_section"); move.addProperty("linkId", "platform"); move.add("offset", pos(0, 1, 0));
        PonderScene compiled = new AuthoringCompiler().compile(document(show, move), null).get(0);
        assertTrue(compiled.getWorldEvents().stream().anyMatch(event -> event.getType() == PonderScene.WorldEventType.MOVE_SECTION));
        assertTrue(new AuthoringCompiler().validate(document(move), null).hasErrors());
        assertFalse(new AuthoringCompiler().validate(document(show, show), null).hasErrors());
        List<PonderScene.WorldEvent> reveals = new java.util.ArrayList<>();
        for (PonderScene.WorldEvent event : new AuthoringCompiler().compile(document(show, show), null).get(0).getWorldEvents())
            if (event.getType() == PonderScene.WorldEventType.SHOW_SECTION) reveals.add(event);
        assertEquals(2, reveals.size());
        assertEquals(reveals.get(0).getSectionOwnership().getSectionId(), reveals.get(1).getSectionOwnership().getTargetId());
        assertNotEquals(reveals.get(0).getSectionOwnership().getSectionId(), reveals.get(1).getSectionOwnership().getSectionId());
    }

    @Test void worldTriggersRemainExplicitlyUnsupportedWhileHintMetadataIsPreserved() {
        JsonObject root = document(step("idle")).toJson(); root.addProperty("triggerMode", "coordinate"); root.addProperty("hintTitle", "always");
        AuthoringDocument document = AuthoringDocument.fromJson(root);
        assertTrue(AuthoringValidation.validate(document).getIssues().stream().anyMatch(issue -> "trigger.unsupported".equals(issue.getCode())));
        assertEquals("always", document.toJson().get("hintTitle").getAsString());
    }
}
