package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import net.createmod.ponder.authoring.practice.PracticeDefinition;
import net.createmod.ponder.authoring.practice.PracticeJson;
import net.createmod.ponder.authoring.practice.PracticeStepHandlers;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSchematic;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuthoringStepTemplatesTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void coreTimelineTemplatesHaveExecutableDefaults() {
        AuthoringCompiler compiler = new AuthoringCompiler();
        for (String type : new String[] {"zoom_scene", "encapsulate_bounds", "play_sound", "modify_entities_nbt",
            "modify_item_entities_nbt", "next_scene", "rotate_camera_y", "spawn_particles", "create_entity", "create_item_entity"}) {
            JsonObject template = AuthoringStepTemplates.withType(null, type);
            AuthoringValidation validation = compiler.validate(AuthoringModelTest.document(template), null);
            assertFalse(validation.hasErrors(), type + ": " + validation.getIssues());
            assertEquals(1, compiler.compile(AuthoringModelTest.document(template), null).size());
        }
    }

    @Test void typeChangePreservesExplicitValuesAndUnknownDraftDataWithoutAliasing() {
        JsonObject source = AuthoringModelTest.step("idle"); source.addProperty("duration", 47); source.addProperty("future", "keep");
        JsonObject replacement = AuthoringStepTemplates.withType(source, "zoom_scene");
        assertEquals("idle", source.get("type").getAsString());
        assertFalse(source.has("scale")); assertEquals(47, replacement.get("duration").getAsInt());
        assertEquals("keep", replacement.get("future").getAsString()); assertTrue(replacement.get("point").isJsonNull());
        JsonObject unsupported = AuthoringStepTemplates.withType(source, "future_step");
        assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(unsupported), null).hasErrors());
    }

    @Test void newBlockTextAndControlTemplatesCompileToTheirActualCoreEvents() {
        AuthoringCompiler compiler = new AuthoringCompiler();
        for (String type : Arrays.asList("idle", "set_block", "text", "shared_text", "show_controls")) {
            JsonObject template = AuthoringStepTemplates.withType(null, type);
            AuthoringDocument document = AuthoringModelTest.document(template);
            assertFalse(compiler.validate(document, null).hasErrors(), type);
            PonderScene scene = compiler.compile(document, null).get(0);
            if ("idle".equals(type)) assertEquals(20, scene.getTotalIdleTicks());
            else if ("set_block".equals(type)) {
                PonderScene.WorldEvent block = scene.getWorldEvents().stream()
                    .filter(event -> event.getType() == PonderScene.WorldEventType.SET_BLOCK).findFirst().get();
                assertEquals(Collections.singletonList(BlockPos.ORIGIN), block.getPositions());
                assertTrue(block.getStateDescription().contains("minecraft:stone"));
                assertFalse(template.get("spawnParticles").getAsBoolean());
            } else {
                assertEquals(1, scene.getOverlayEvents().size());
                PonderScene.OverlayEvent overlay = scene.getOverlayEvents().get(0);
                assertEquals(60, overlay.getDuration()); assertEquals(new Vec3d(0, 1, 0), overlay.getPointAt());
                if ("show_controls".equals(type)) assertTrue(overlay.isControlRightClick());
                else assertEquals(template.get("text").getAsString(), overlay.getText());
            }
        }
    }

    @Test void targetDependentTemplatesHaveEditablePlaceholdersAndCannotPretendToBeStandaloneLessons() {
        AuthoringCompiler compiler = new AuthoringCompiler();
        for (String type : Arrays.asList("show_structure", "show_section_and_merge", "hide_section", "highlight_section",
            "move_section", "rotate_section", "replace_blocks", "destroy_block", "break_progress", "restore_blocks",
            "toggle_redstone_power", "modify_block_entity_nbt", "indicate_redstone", "indicate_success")) {
            JsonObject template = AuthoringStepTemplates.withType(null, type);
            assertTrue(template.has("blockPos") && template.get("blockPos").isJsonNull(), type);
            assertTrue(compiler.validate(AuthoringModelTest.document(template), null).hasErrors(), type);
            assertThrows(AuthoringCompiler.CompilationException.class,
                () -> compiler.compile(AuthoringModelTest.document(template), null), type);
        }
    }

    @Test void explicitSectionSelectionAndLinkIdsAreNotOverriddenByTargetPlaceholders() {
        JsonObject selected = AuthoringModelTest.step("idle"); selected.addProperty("fullScene", true);
        for (String type : Arrays.asList("show_structure", "hide_section", "highlight_section", "move_section", "rotate_section")) {
            JsonObject template = AuthoringStepTemplates.withType(selected, type);
            assertFalse(template.has("blockPos"), type); assertTrue(template.get("fullScene").getAsBoolean());
        }
        JsonObject show = AuthoringStepTemplates.withType(null, "show_structure");
        show.add("blockPos", AuthoringModelTest.pos(0, 0, 0)); show.addProperty("linkId", "base");
        JsonObject linked = AuthoringModelTest.step("idle"); linked.addProperty("linkId", "base");
        JsonObject move = AuthoringStepTemplates.withType(linked, "move_section");
        JsonObject rotate = AuthoringStepTemplates.withType(linked, "rotate_section");
        assertFalse(move.has("blockPos")); assertFalse(rotate.has("blockPos"));
        assertEquals(new Vec3d(1, 0, 0), AuthoringCompiler.vector(move, "offset", Vec3d.ZERO));
        assertEquals(90, rotate.get("rotY").getAsInt());
        AuthoringCompiler compiler = new AuthoringCompiler();
        assertTrue(compiler.validate(AuthoringModelTest.document(move), null).hasErrors());
        JsonObject block = AuthoringStepTemplates.withType(null, "set_block");
        AuthoringDocument document = AuthoringModelTest.document(block, show, move, rotate);
        assertFalse(compiler.validate(document, null).hasErrors());
        assertEquals(1, compiler.compile(document, null).size());
    }

    @Test void extraStructureTemplateNeverInventsAnAssetAndAcceptsOnlyAnExplicitResolvableOne() {
        JsonObject template = AuthoringStepTemplates.withType(null, "show_extra_structure");
        assertFalse(template.has("structure"));
        AuthoringCompiler compiler = new AuthoringCompiler();
        assertTrue(compiler.validate(AuthoringModelTest.document(template), null).hasErrors());
        template.addProperty("structure", "local:one_block");
        PonderSchematic schematic = PonderSchematic.of(new BlockPos(1, 1, 1),
            Collections.<BlockPos, IBlockState>singletonMap(BlockPos.ORIGIN, Blocks.STONE.getDefaultState()),
            Collections.<BlockPos, NBTTagCompound>emptyMap());
        AuthoringCompiler.AssetResolver assets = id -> "local:one_block".equals(id) ? schematic : null;
        assertFalse(compiler.validate(AuthoringModelTest.document(template), assets).hasErrors());
        PonderScene scene = compiler.compile(AuthoringModelTest.document(template), assets).get(0);
        assertTrue(scene.getWorldEvents().stream().anyMatch(event -> event.getType() == PonderScene.WorldEventType.SET_BLOCKS
            && event.getPositions().equals(Collections.singletonList(BlockPos.ORIGIN))));
    }

    @Test void knownEmptyInterfaceTemplateHasNoInventedRecipeInventoryOrPracticeGoal() {
        JsonObject template = AuthoringStepTemplates.withType(null, "show_interface");
        PracticeDefinition definition = PracticeJson.fromStep(template);
        assertEquals(PracticeDefinition.Menu.CHEST, definition.getMenu());
        assertEquals(200, definition.getDuration()); assertTrue(definition.getGoals().isEmpty());
        assertTrue(definition.getActions().isEmpty()); assertTrue(definition.getData().isEmpty());
        assertTrue(definition.getInitialSlots().values().stream().allMatch(stack -> stack.isEmpty()));
        assertFalse(template.has("goal"));
        AuthoringCompiler compiler = new AuthoringCompiler(); PracticeStepHandlers.register(compiler);
        assertFalse(compiler.validate(AuthoringModelTest.document(template), null).hasErrors());
        assertEquals(1, PracticeStepHandlers.getDefinitions(compiler.compile(AuthoringModelTest.document(template), null).get(0)).size());
    }

    @Test void interfaceActionsStillRequireTheirActualPrecedingMenuAndAwaitRequiresAnExplicitGoal() {
        AuthoringCompiler compiler = new AuthoringCompiler(); PracticeStepHandlers.register(compiler);
        JsonObject show = AuthoringStepTemplates.withType(null, "show_interface");
        for (String type : Arrays.asList("click_interface", "interface_action", "set_interface_slots", "change_interface_slot",
            "set_interface_data", "set_interface_context", "hide_interface")) {
            JsonObject action = AuthoringStepTemplates.withType(null, type);
            assertTrue(compiler.validate(AuthoringModelTest.document(action), null).hasErrors(), type);
            AuthoringDocument document = AuthoringModelTest.document(show, action);
            assertFalse(compiler.validate(document, null).hasErrors(), type);
            assertEquals(1, compiler.compile(document, null).size());
        }
        JsonObject await = AuthoringStepTemplates.withType(null, "await_interface");
        assertTrue(await.get("goal").isJsonNull());
        assertTrue(compiler.validate(AuthoringModelTest.document(show, await), null).hasErrors());
    }

    @Test void existingSharedTextMenuBindingsAndNestedFutureFieldsArePreservedWithoutAliasing() {
        JsonObject shared = AuthoringModelTest.step("idle"); shared.addProperty("key", "intro");
        JsonObject text = AuthoringStepTemplates.withType(shared, "shared_text");
        assertFalse(text.has("text")); assertEquals("intro", text.get("key").getAsString());
        JsonObject root = AuthoringModelTest.document(text).toJson();
        JsonObject sharedTexts = new JsonObject(); sharedTexts.addProperty("intro", "Existing shared text");
        root.add("sharedTexts", sharedTexts);
        assertEquals("Existing shared text", new AuthoringCompiler().compile(AuthoringDocument.fromJson(root), null)
            .get(0).getOverlayEvents().get(0).getText());

        JsonObject source = AuthoringModelTest.step("idle"), descriptor = new JsonObject(), future = new JsonObject();
        descriptor.addProperty("menuType", "minecraft:furnace"); future.addProperty("keep", 7);
        descriptor.add("future", future); source.add("interface", descriptor);
        JsonObject replacement = AuthoringStepTemplates.withType(source, "show_interface");
        assertFalse(replacement.has("uiId"));
        assertEquals(PracticeDefinition.Menu.FURNACE, PracticeJson.fromStep(replacement).getMenu());
        replacement.getAsJsonObject("interface").getAsJsonObject("future").addProperty("keep", 9);
        assertEquals(7, source.getAsJsonObject("interface").getAsJsonObject("future").get("keep").getAsInt());
        assertFalse(source.has("title"));

        JsonObject explicit = AuthoringModelTest.step("idle"); explicit.addProperty("block", "minecraft:glass");
        explicit.add("blockPos", AuthoringModelTest.pos(3, 4, 5)); explicit.addProperty("spawnParticles", true);
        explicit.addProperty("duration", 47);
        JsonObject block = AuthoringStepTemplates.withType(explicit, "set_block");
        assertEquals("minecraft:glass", block.get("block").getAsString());
        assertEquals(new BlockPos(3, 4, 5), AuthoringCompiler.blockPos(block, "blockPos"));
        assertTrue(block.get("spawnParticles").getAsBoolean()); assertEquals(47, block.get("duration").getAsInt());
        JsonObject hide = AuthoringStepTemplates.withType(explicit, "hide_section");
        assertEquals(47, hide.get("fadeDuration").getAsInt());
        explicit.addProperty("degrees", 17);
        assertEquals(17, AuthoringStepTemplates.withType(explicit, "rotate_section").get("rotY").getAsInt());
        explicit.addProperty("entranceAnimation", "none");
        assertFalse(AuthoringStepTemplates.withType(explicit, "show_structure").has("fadeDuration"));
        JsonObject controls = AuthoringModelTest.step("idle"); controls.addProperty("leftClick", true);
        assertFalse(AuthoringStepTemplates.withType(controls, "show_controls").has("action"));
    }
}
