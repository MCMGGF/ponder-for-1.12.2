package net.createmod.ponder.foundation.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.createmod.ponder.CommonProxy;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.PonderBackportMod;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSceneBuilder;
import net.createmod.ponder.foundation.external.ExternalPonderScenes.ExternalDefinitionSet;
import net.createmod.ponder.foundation.registration.DefaultPonderSceneRegistrationHelper;
import net.createmod.ponder.foundation.registration.DefaultPonderTagRegistrationHelper;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.createmod.ponder.foundation.registration.PonderTagRegistry;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExternalPonderScenesTest {

    private Path directory;
    private CommonProxy originalProxy;
    private Object originalRegistry;
    private long originalGeneration;
    private List<PonderPlugin> originalPlugins;

    @BeforeAll
    static void registerBlocks() {
        Bootstrap.register();
    }

    @BeforeEach
    void isolateRegistryAndFiles() throws Exception {
        Path fixtureRoot = Paths.get("build", "test-fixtures", "external-scenes").toAbsolutePath().normalize();
        directory = Files.createTempDirectory(Files.createDirectories(fixtureRoot), "test-");
        originalProxy = PonderBackportMod.proxy;
        originalRegistry = field("active").get(null);
        originalGeneration = PonderIndex.getGeneration();
        originalPlugins = new ArrayList<PonderPlugin>(plugins());
        plugins().clear();
        PonderBackportMod.proxy = null;
        PonderIndex.reload();
    }

    @AfterEach
    void restoreRegistryAndRemoveFixtures() throws Exception {
        PonderBackportMod.proxy = originalProxy;
        field("active").set(null, originalRegistry);
        field("generation").setLong(null, originalGeneration);
        plugins().clear();
        plugins().addAll(originalPlugins);
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.collect(Collectors.toList())) {
                Files.delete(file);
            }
        }
        Files.delete(directory);
    }

    @Test
    void aSmallAcyclicReferenceGraphCannotExpandPastTheFileBudget() {
        int depth = doublingDepthPast(ExternalPonderScenes.MAX_EXPANDED_OPERATIONS_PER_FILE);
        JsonObject document = document(scene("expanded", reference(depth)));
        document.add("interactionDefinitions", definitions(depth, 2));
        assertTrue(document.toString().length() < 10000, "the source is small despite its expanded size");

        register(write("expanded", document));

        assertEquals(0, entries());
    }

    @Test
    void separateTopLevelOperationsShareOneFileBudget() {
        int depth = doublingDepthPast(ExternalPonderScenes.MAX_EXPANDED_OPERATIONS_PER_FILE) - 1;
        JsonObject document = document(scene("two_calls", reference(depth), reference(depth)));
        document.add("interactionDefinitions", definitions(depth, 2));

        register(write("two-calls", document));

        assertEquals(0, entries());
    }

    @Test
    void scenesShareTheFileBudgetWhileOtherFilesRemainIndependent() {
        int depth = doublingDepthPast(ExternalPonderScenes.MAX_EXPANDED_OPERATIONS_PER_FILE) - 1;
        JsonObject first = document(scene("first", reference(depth)));
        first.add("interactionDefinitions", definitions(depth, 2));
        JsonObject second = document(scene("second", reference(depth)));
        second.add("interactionDefinitions", definitions(depth, 2));
        List<File> sources = new ArrayList<File>(Arrays.asList(write("first", first), write("second", second)));
        PonderIndex.addPlugin(new ExternalPonderPlugin(() -> ExternalPonderScenes.loadDefinitions(sources)));
        PonderIndex.reload();
        assertEquals(2, entries(), "each file has its own budget");

        JsonObject shared = document(scene("first", reference(depth)), scene("second", reference(depth)));
        shared.add("interactionDefinitions", definitions(depth, 2));
        sources.clear();
        sources.add(write("shared", shared));
        sources.add(write("unrelated", document(scene("unrelated", idle(3)))));
        PonderIndex.reload();

        assertEquals(1, entries(), "reject the entire over-budget file, not the unrelated file");
        assertEquals("unrelated", programOnlyScene().getTitle());
    }

    @Test
    void rootArrayScenesAlsoShareTheSameBudget() {
        JsonArray root = new JsonArray();
        int count = ExternalPonderScenes.MAX_EXPANDED_OPERATIONS_PER_FILE / 2 + 1;
        root.add(sceneWithIdles("first", count));
        root.add(sceneWithIdles("second", count));

        register(write("array", root), write("valid", document(scene("valid", idle(1)))));

        assertEquals(1, entries());
        assertEquals("valid", programOnlyScene().getTitle());
    }

    @Test
    void exactNodeLimitIsAcceptedButOneAdditionalNodeRejectsTheFile() {
        int limit = ExternalPonderScenes.MAX_EXPANDED_OPERATIONS_PER_FILE;
        register(write("boundary", document(sceneWithIdles("boundary", limit))),
            write("over-limit", document(sceneWithIdles("over_limit", limit + 1))));

        assertEquals(1, entries());
        assertEquals(limit, programOnlyScene().getTotalIdleTicks());
    }

    @Test
    void referenceDepthBoundaryIsAcceptedAndTheNextLevelIsRejected() {
        int accepted = ExternalPonderScenes.MAX_OPERATION_DEPTH - 2;
        JsonObject valid = document(scene("boundary", reference(accepted)));
        valid.add("interactionDefinitions", definitions(accepted, 1));
        JsonObject invalid = document(scene("too_deep", reference(accepted + 1)));
        invalid.add("interactionDefinitions", definitions(accepted + 1, 1));

        register(write("depth-boundary", valid), write("too-deep", invalid));

        assertEquals(1, entries());
        assertEquals("boundary", programOnlyScene().getTitle());
        assertEquals(1, programOnlyScene().getTotalIdleTicks());
    }

    @Test
    void inlineSequencesUseTheSameDepthLimitAsReferences() {
        register(write("inline-boundary", document(scene("boundary",
                inlineSequence(ExternalPonderScenes.MAX_OPERATION_DEPTH - 1)))),
            write("inline-too-deep", document(scene("too_deep",
                inlineSequence(ExternalPonderScenes.MAX_OPERATION_DEPTH)))));

        assertEquals(1, entries());
        assertEquals("boundary", programOnlyScene().getTitle());
    }

    @Test
    void repeatedReferencesRetainTheirSeparateInvocationDefaults() {
        JsonObject first = reference(1);
        first.addProperty("ticks", 2);
        JsonObject second = reference(1);
        second.addProperty("ticks", 3);
        JsonObject document = document(scene("reused", first, second));
        document.add("interactionDefinitions", definitions(1, 2));

        register(write("reused", document));

        assertEquals(1, entries());
        assertEquals(10, programOnlyScene().getTotalIdleTicks());
    }

    @Test
    void cyclicDefinitionsAreStillRejected() {
        JsonObject document = document(scene("cycle", reference(0)));
        JsonObject definition = new JsonObject();
        JsonArray steps = new JsonArray();
        steps.add(reference(0));
        definition.add("steps", steps);
        JsonObject definitions = new JsonObject();
        definitions.add("a0", definition);
        document.add("interactionDefinitions", definitions);

        register(write("cycle", document));

        assertEquals(0, entries());
    }

    @Test
    void externalOutlineTextPreservesSelectionForBothGeometryAndCaption() {
        JsonObject outline = range("outline_text", 3);
        outline.addProperty("text", "selected blocks");
        outline.addProperty("color", "green");
        outline.add("pointAt", array(10, 2, 0));
        register(write("outline-targets", document(scene("outline_targets", outline))));

        PonderScene scene = programOnlyScene();
        assertEquals(2, scene.getOverlayEvents().size());
        PonderScene.OverlayEvent geometry = scene.getOverlayEvents().get(0);
        PonderScene.OverlayEvent caption = scene.getOverlayEvents().get(1);
        assertEquals(PonderScene.OverlayEventType.SCENE_OUTLINE, geometry.getType());
        assertEquals(Arrays.asList(BlockPos.ORIGIN, new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)),
            geometry.getSourcePositions());
        assertEquals(geometry.getSourcePositions(), caption.getTargetPositions());
        assertEquals(geometry.getColor(), caption.getColor());
    }

    @Test
    void externalGuiSelectionSharesCaptionTargetWhileIndependentClearsIt() {
        JsonObject caption = range("text", 2);
        caption.addProperty("text", "machine");
        JsonObject image = range("gui_snapshot", 2);
        image.addProperty("snapshot", "ponder_test:image");
        JsonObject independent = range("gui_snapshot", 2);
        independent.addProperty("snapshot", "ponder_test:image");
        independent.addProperty("independentY", 0);
        register(write("gui-targets", document(scene("gui_targets", caption, image, independent))));

        PonderScene scene = programOnlyScene();
        assertEquals(3, scene.getOverlayEvents().size());
        assertEquals(scene.getOverlayEvents().get(0).getTargetPositions(),
            scene.getOverlayEvents().get(1).getTargetPositions());
        assertEquals(2, scene.getOverlayEvents().get(1).getTargetPositions().size());
        assertTrue(scene.getOverlayEvents().get(2).getTargetPositions().isEmpty());
        assertEquals(null, scene.getOverlayEvents().get(2).getPointAt());
    }

    @Test
    void externalTopAnchorRetainsTheBlockBelowItsSurface() {
        JsonObject caption = operation("text");
        caption.addProperty("text", "top surface");
        caption.add("pointAt", array(2, 1, 3));
        register(write("top-target", document(scene("top_target", caption))));
        assertEquals(Arrays.asList(new BlockPos(2, 1, 3)),
            programOnlyScene().getOverlayEvents().get(0).getTargetPositions());
    }

    @Test
    void repetitionLimitCountsGeneratedEventsNotOnlyJsonNodes() {
        int limit = ExternalPonderScenes.MAX_EXPANDED_OPERATIONS_PER_FILE;
        register(write("boundary", document(scene("boundary", repeat(limit)))),
            write("over-limit", document(scene("over_limit", repeat(limit + 1)))),
            write("integer-max", document(scene("integer_max", repeat(Integer.MAX_VALUE)))));

        assertEquals(1, entries());
        assertEquals(limit, programOnlyScene().getWorldEvents().size());
    }

    @Test
    void repetitionsShareTheBudgetAcrossOperationsAndScenes() {
        int half = ExternalPonderScenes.MAX_EXPANDED_OPERATIONS_PER_FILE / 2;
        register(write("valid", document(scene("valid", repeat(half), repeat(half)))),
            write("shared", document(scene("first", repeat(half)), scene("second", repeat(half), idle(1)))));

        assertEquals(1, entries());
        assertEquals(half * 2, programOnlyScene().getWorldEvents().size());
    }

    @Test
    void referencedInteractionsCannotHideRepeatedExecutionCost() {
        JsonObject document = document(scene("expanded", reference(0), reference(0), reference(0), reference(0)));
        JsonObject definitions = new JsonObject();
        definitions.add("a0", sequence(repeat(ExternalPonderScenes.MAX_EXPANDED_OPERATIONS_PER_FILE / 4)));
        document.add("interactionDefinitions", definitions);
        register(write("expanded", document), write("valid", document(scene("valid", idle(1)))));

        assertEquals(1, entries());
        assertEquals("valid", programOnlyScene().getTitle());
    }

    @Test
    void nonPositiveRepetitionCountsAreRejectedBeforeProgramming() {
        rejectOperations(repeat(0), repeat(-1), repeat(Integer.MIN_VALUE));
    }

    @Test
    void exactSelectionPositionLimitIsAcceptedWithoutCompilingTheLargeSelection() {
        int limit = ExternalPonderScenes.MAX_TARGET_POSITIONS_PER_FILE;
        register(write("boundary", document(scene("boundary", range("hide_section", limit)))),
            write("over-limit", document(scene("over_limit", range("hide_section", limit + 1)))));

        assertEquals(1, entries());
    }

    @Test
    void everySelectionTraversingOperationRejectsAnOversizedBox() {
        List<JsonObject> operations = new ArrayList<JsonObject>();
        for (String type : Arrays.asList("set_blocks", "replace_blocks", "show_section", "hide_section",
            "restore_blocks", "toggle_redstone_power", "move_section", "rotate_section")) {
            operations.add(range(type, ExternalPonderScenes.MAX_TARGET_POSITIONS_PER_FILE + 1));
        }
        rejectOperations(operations.toArray(new JsonObject[0]));
    }

    @Test
    void everyOverlaySelectionRejectsHugeRangesBeforeItsStoryboardCanAllocateThem() {
        List<JsonObject> operations = new ArrayList<JsonObject>();
        for (String type : Arrays.asList("text", "outline_text", "gui_texture", "gui_snapshot",
            "block_gui", "machine_gui", "gui_outline_text")) {
            JsonObject overlay = overlayRange(type, 1);
            overlay.add("to", array(1000, 1000, 1000));
            operations.add(overlay);
        }
        rejectOperations(operations.toArray(new JsonObject[0]));
    }

    @Test
    void overlaySelectionBoundaryIsAcceptedAndOutlineChargesBothEventSelections() {
        int limit = ExternalPonderScenes.MAX_TARGET_POSITIONS_PER_FILE;
        register(write("text-boundary", document(scene("text_boundary", overlayRange("text", limit)))),
            write("outline-boundary", document(scene("outline_boundary", overlayRange("outline_text", limit / 2)))),
            write("text-overflow", document(scene("text_overflow", overlayRange("text", limit + 1)))),
            write("outline-overflow", document(scene("outline_overflow", overlayRange("outline_text", limit / 2 + 1)))));
        assertEquals(2, entries());
    }

    @Test
    void textAndGuiSelectionsShareTheirFileBudgetAcrossScenesAndReversedRanges() {
        int half = ExternalPonderScenes.MAX_TARGET_POSITIONS_PER_FILE / 2;
        JsonObject reversed = overlayRange("text", half);
        reversed.add("from", array(half - 1, 0, 0));
        reversed.add("to", array(0, 0, 0));
        register(write("overlay-boundary", document(scene("first", reversed),
                scene("second", overlayRange("gui_snapshot", half)))),
            write("overlay-overflow", document(scene("first", reversed),
                scene("second", overlayRange("gui_snapshot", half), overlayRange("text", 1)))));
        assertEquals(2, entries());
    }

    @Test
    void referencedOverlaySelectionsShareTheFileBudget() {
        int half = ExternalPonderScenes.MAX_TARGET_POSITIONS_PER_FILE / 2;
        JsonObject document = document(scene("overlay_expansion", reference(0), reference(0), reference(0)));
        JsonObject definitions = new JsonObject();
        definitions.add("a0", sequence(overlayRange("text", half)));
        document.add("interactionDefinitions", definitions);
        register(write("overlay-expansion", document), write("valid", document(scene("valid", idle(1)))));
        assertEquals(1, entries());
        assertEquals("valid", programOnlyScene().getTitle());
    }

    @Test
    void pointOnlyOverlayAnchorsAlsoConsumeOneTargetPosition() {
        JsonObject pointOnly = operation("text");
        pointOnly.addProperty("text", "anchor");
        pointOnly.add("pointAt", array(2, 1, 3));
        int limit = ExternalPonderScenes.MAX_TARGET_POSITIONS_PER_FILE;
        register(write("point-boundary", document(scene("point_boundary", overlayRange("text", limit - 1), pointOnly))),
            write("point-overflow", document(scene("point_overflow", overlayRange("text", limit), pointOnly))));
        assertEquals(1, entries());
    }

    @Test
    void outlineTextCountsItsAdditionalOutlineEventAgainstTheExpandedNodeLimit() {
        int limit = ExternalPonderScenes.MAX_EXPANDED_OPERATIONS_PER_FILE;
        JsonObject boundary = scene("outline_nodes_boundary");
        JsonObject overflow = scene("outline_nodes_overflow");
        for (int i = 0; i < limit / 2; i++) {
            boundary.getAsJsonArray("operations").add(overlayRange("outline_text", 1));
            overflow.getAsJsonArray("operations").add(overlayRange("outline_text", 1));
        }
        overflow.getAsJsonArray("operations").add(overlayRange("outline_text", 1));
        register(write("outline-nodes-boundary", document(boundary)),
            write("outline-nodes-overflow", document(overflow)));
        assertEquals(1, entries());
    }

    @Test
    void outlineTextWithoutASelectionIsRejectedBeforeSceneExecution() {
        JsonObject outline = operation("outline_text");
        outline.addProperty("text", "missing selection");
        outline.add("pointAt", array(2, 1, 3));
        rejectOperations(outline);
    }

    @Test
    void selectionPositionsShareTheBudgetAcrossScenesAndReversedRanges() {
        int half = ExternalPonderScenes.MAX_TARGET_POSITIONS_PER_FILE / 2;
        JsonObject reversed = range("hide_section", half);
        reversed.add("from", array(half - 1, 0, 0));
        reversed.add("to", array(0, 0, 0));
        register(write("boundary", document(scene("first", reversed),
                scene("second", range("show_section", half)))),
            write("over-limit", document(scene("first", reversed),
                scene("second", range("show_section", half), range("hide_section", 1)))));

        assertEquals(2, entries());
    }

    @Test
    void referencedSelectionsShareTheSameFileBudget() {
        int half = ExternalPonderScenes.MAX_TARGET_POSITIONS_PER_FILE / 2;
        JsonObject document = document(scene("expanded", reference(0), reference(0), reference(0)));
        JsonObject definitions = new JsonObject();
        definitions.add("a0", sequence(range("hide_section", half)));
        document.add("interactionDefinitions", definitions);
        register(write("expanded", document), write("valid", document(scene("valid", idle(1)))));

        assertEquals(1, entries());
        assertEquals("valid", programOnlyScene().getTitle());
    }

    @Test
    void separateFilesHaveIndependentSelectionBudgets() {
        int limit = ExternalPonderScenes.MAX_TARGET_POSITIONS_PER_FILE;
        register(write("first", document(scene("first", range("hide_section", limit)))),
            write("second", document(scene("second", range("hide_section", limit)))));

        assertEquals(2, entries());
    }

    @Test
    void nbtApplicationsConsumeTheirOwnTargetPositions() {
        int half = ExternalPonderScenes.MAX_TARGET_POSITIONS_PER_FILE / 2;
        JsonObject boundary = range("set_blocks", half);
        boundary.addProperty("nbt", "{}");
        JsonObject overflow = range("replace_blocks", half + 1);
        overflow.addProperty("nbt", "{}");
        JsonObject single = operation("set_block");
        single.add("pos", array(0, 0, 0));
        single.addProperty("state", "minecraft:stone");
        single.addProperty("nbt", "{}");
        register(write("boundary", document(scene("boundary", boundary))),
            write("over-limit", document(scene("over_limit", overflow))),
            write("single-nbt", document(scene("single_nbt",
                range("hide_section", ExternalPonderScenes.MAX_TARGET_POSITIONS_PER_FILE - 1), single))));

        assertEquals(1, entries());
    }

    @Test
    void basePlateShowsUseTheConfiguredFootprintIncludingNestedChanges() {
        JsonObject boundary = document(scene("boundary", basePlate(512), operation("show_base_plate")));
        JsonObject repeated = document(scene("repeated", basePlate(512),
            operation("show_base_plate"), operation("show_base_plate")));
        JsonObject nested = document(scene("nested", basePlate(512), sequence(basePlate(1)),
            operation("show_base_plate"), range("hide_section", ExternalPonderScenes.MAX_TARGET_POSITIONS_PER_FILE - 1)));
        JsonObject nestedOverflow = document(scene("nested_overflow", sequence(basePlate(512)),
            operation("show_base_plate"), range("hide_section", 1)));
        register(write("boundary", boundary), write("repeated", repeated),
            write("nested", nested), write("nested-overflow", nestedOverflow));

        assertEquals(2, entries());
    }

    @Test
    void basePlateStateResetsForEachSceneButItsWorkBudgetDoesNot() {
        register(write("reset", document(scene("configure_only", basePlate(512)),
                scene("default_plate", operation("show_base_plate"), operation("show_base_plate")))),
            write("shared", document(scene("full_budget", basePlate(512), operation("show_base_plate")),
                scene("over_limit", operation("show_base_plate")))));

        assertEquals(2, entries());
    }

    @Test
    void oversizedBasePlatesAndUnsafeOffsetEndpointsAreRejected() {
        JsonObject unsafeX = basePlate(1);
        unsafeX.addProperty("xOffset", Integer.MAX_VALUE);
        JsonObject unsafeZ = basePlate(1);
        unsafeZ.addProperty("zOffset", Integer.MIN_VALUE);
        JsonObject unsafeEnd = basePlate(2);
        unsafeEnd.addProperty("xOffset", ExternalPonderScenes.MAX_ABSOLUTE_BLOCK_COORDINATE);
        rejectOperations(basePlate(Integer.MAX_VALUE), basePlate(513), unsafeX, unsafeZ, unsafeEnd);
    }

    @Test
    void integerBoundaryCoordinatesAreRejectedWithoutExecutingTheirLoops() {
        List<JsonObject> operations = new ArrayList<JsonObject>();
        for (int axis = 0; axis < 3; axis++) {
            for (int value : new int[] { Integer.MIN_VALUE, Integer.MAX_VALUE }) {
                JsonArray position = array(0, 0, 0);
                position.set(axis, new JsonPrimitive(value));
                JsonObject move = operation("move_section");
                move.add("from", position);
                move.add("to", position);
                move.add("offset", array(1, 0, 0));
                operations.add(move);
                JsonObject single = operation("destroy_block");
                single.add("pos", position);
                operations.add(single);
            }
        }
        JsonObject largeProduct = operation("show_section");
        int max = ExternalPonderScenes.MAX_ABSOLUTE_BLOCK_COORDINATE;
        largeProduct.add("from", array(-max, -max, -max));
        largeProduct.add("to", array(max, max, max));
        operations.add(largeProduct);
        rejectOperations(operations.toArray(new JsonObject[0]));
    }

    @Test
    void integersCannotSilentlyWrapOrTruncate() {
        List<JsonObject> operations = new ArrayList<JsonObject>();
        for (String value : Arrays.asList("2147483648", "-2147483649", "4294967297", "1.5", "NaN", "Infinity")) {
            JsonObject repeats = repeat(1);
            repeats.addProperty("times", value);
            operations.add(repeats);
            JsonObject move = range("move_section", 1);
            move.add("offset", array(1, 0, 0));
            JsonArray position = array(0, 0, 0);
            position.set(0, new JsonPrimitive(value));
            move.add("from", position);
            operations.add(move);
        }
        rejectOperations(operations.toArray(new JsonObject[0]));
    }

    @Test
    void nonFiniteScalarsAndVectorsAreRejectedDuringParsing() {
        List<JsonObject> operations = new ArrayList<JsonObject>();
        for (String field : Arrays.asList("factor", "degrees", "offsetY", "rotX", "rotY", "rotZ")) {
            for (String value : Arrays.asList("NaN", "Infinity", "-Infinity", "1e100")) {
                JsonObject operation = operation("text");
                operation.addProperty(field, value);
                operations.add(operation);
            }
        }
        for (String field : Arrays.asList("offset", "rotation", "rot", "pivot", "pointAt")) {
            for (String value : Arrays.asList("NaN", "Infinity", "-Infinity", "1e309")) {
                JsonObject operation = operation("text");
                JsonArray vector = array(0, 0, 0);
                vector.set(0, new JsonPrimitive(value));
                operation.add(field, vector);
                operations.add(operation);
            }
        }
        rejectOperations(operations.toArray(new JsonObject[0]));
    }

    @Test
    void finiteFractionalVectorsAndReversedSelectionsStillProgramNormally() {
        JsonObject move = operation("move_section");
        move.add("from", array(1, 0, 0));
        move.add("to", array(-1, 0, 0));
        move.add("offset", array(0.5, 1.25, -0.5));
        JsonObject rotate = operation("rotate_section");
        rotate.add("pos", array(-1, 0, 0));
        rotate.add("rotation", array(0, 90.5, 0));
        rotate.add("pivot", array(0.5, 0.5, 0.5));
        JsonObject repeated = repeat(4);
        repeated.addProperty("times", "4.0");
        register(write("normal", document(scene("normal", basePlate(0),
            operation("show_base_plate"), move, rotate, repeated))));

        PonderScene scene = programOnlyScene();
        assertEquals(7, scene.getWorldEvents().size());
        assertEquals(1, scene.getWorldEvents().get(0).getPositions().size());
        assertEquals(3, scene.getWorldEvents().get(1).getPositions().size());
        assertEquals(1, scene.getWorldEvents().get(2).getPositions().size());
    }

    @Test
    void disabledInteractionsDoNotChargeOrExecuteInactiveWork() {
        JsonObject disabled = sequence(repeat(Integer.MAX_VALUE), basePlate(Integer.MAX_VALUE));
        disabled.addProperty("enabled", false);
        register(write("disabled", document(scene("disabled", disabled, idle(2)))));

        assertEquals(1, entries());
        assertEquals(2, programOnlyScene().getTotalIdleTicks());
        assertEquals(0, programOnlyScene().getWorldEvents().size());
    }

    @Test
    void existingJsonTutorialDocumentsRemainCompatible() {
        Path examples = Paths.get("docs", "examples", "json_tutorial");
        register(examples.resolve("01_minimal_scene.json").toFile(),
            examples.resolve("02_redstone_and_nbt.json").toFile(),
            examples.resolve("03_gui_interaction.json").toFile(),
            examples.resolve("04_supported_features_no_gui.json").toFile());

        assertEquals(6, entries());
    }

    @Test
    void oneReloadReusesItsSnapshotAndTheNextReloadReadsUpdatedFiles() {
        File file = write("changing", taggedDocument("old"));
        int[] loads = { 0 };
        PonderIndex.addPlugin(new ExternalPonderPlugin(() -> {
            ExternalDefinitionSet loaded = ExternalPonderScenes.loadDefinitions(Arrays.asList(file));
            if (++loads[0] == 1) {
                write("changing", taggedDocument("new"));
            }
            return loaded;
        }));

        PonderIndex.reload();
        assertEquals(1, loads[0]);
        assertEquals("old", programOnlyScene().getTitle());
        assertEquals(location("old"), PonderIndex.getTagAccess().getRegisteredTag(location("old")).getId());
        assertEquals(Ponder.asResource("not_registered"),
            PonderIndex.getTagAccess().getRegisteredTag(location("new")).getId());

        PonderIndex.reload();
        assertEquals(2, loads[0]);
        assertEquals("new", programOnlyScene().getTitle());
        assertEquals(location("new"), PonderIndex.getTagAccess().getRegisteredTag(location("new")).getId());
        assertEquals(Ponder.asResource("not_registered"),
            PonderIndex.getTagAccess().getRegisteredTag(location("old")).getId());
    }

    @Test
    void aFailedReloadDoesNotRetainOldParsedDefinitions() {
        File file = write("changing", taggedDocument("old"));
        int[] loads = { 0 };
        boolean[] fail = { true };
        PonderIndex.addPlugin(new ExternalPonderPlugin(() -> {
            loads[0]++;
            return ExternalPonderScenes.loadDefinitions(Arrays.asList(file));
        }));
        PonderIndex.addPlugin(new PonderPlugin() {
            public String getModId() { return "reload_failure"; }

            public void registerSharedText(SharedTextRegistrationHelper helper) {
                if (fail[0]) throw new IllegalStateException("Injected registration failure");
            }
        });
        long generation = PonderIndex.getGeneration();

        PonderIndex.reload();
        assertEquals(1, loads[0]);
        assertEquals(generation, PonderIndex.getGeneration());
        assertEquals(0, entries());

        write("changing", taggedDocument("new"));
        fail[0] = false;
        PonderIndex.reload();
        assertEquals(2, loads[0]);
        assertEquals("new", programOnlyScene().getTitle());
        assertEquals(location("new"), PonderIndex.getTagAccess().getRegisteredTag(location("new")).getId());
        assertEquals(Ponder.asResource("not_registered"),
            PonderIndex.getTagAccess().getRegisteredTag(location("old")).getId());
    }

    @Test
    void registrationWithoutReloadHooksDoesNotCacheDefinitions() {
        File file = write("changing", taggedDocument("old"));
        int[] loads = { 0 };
        ExternalPonderPlugin plugin = new ExternalPonderPlugin(() -> {
            loads[0]++;
            return ExternalPonderScenes.loadDefinitions(Arrays.asList(file));
        });
        PonderLocalization localization = new PonderLocalization();
        PonderSceneRegistry scenes = new PonderSceneRegistry(localization);
        PonderTagRegistry tags = new PonderTagRegistry(localization);

        plugin.registerScenes(new DefaultPonderSceneRegistrationHelper("ponder", scenes));
        write("changing", taggedDocument("new"));
        plugin.registerTags(new DefaultPonderTagRegistrationHelper("ponder", tags, localization));

        assertEquals(2, loads[0]);
        assertEquals(location("new"), tags.getRegisteredTag(location("new")).getId());
        assertEquals(Ponder.asResource("not_registered"), tags.getRegisteredTag(location("old")).getId());
    }

    private void register(File... files) {
        PonderIndex.addPlugin(new ExternalPonderPlugin(() -> ExternalPonderScenes.loadDefinitions(Arrays.asList(files))));
        PonderIndex.reload();
    }

    private void rejectOperations(JsonObject... operations) {
        List<File> sources = new ArrayList<File>();
        for (int i = 0; i < operations.length; i++) {
            sources.add(write("rejected-" + i, document(scene("rejected_" + i, operations[i]))));
        }
        sources.add(write("unrelated", document(scene("unrelated", idle(1)))));
        register(sources.toArray(new File[0]));
        assertEquals(1, entries(), "invalid input must be rejected before its storyboard can run");
        assertEquals("unrelated", programOnlyScene().getTitle());
    }

    private static int entries() {
        return PonderIndex.getSceneAccess().getRegisteredEntries().size();
    }

    private static PonderScene programOnlyScene() {
        assertEquals(1, entries());
        StoryBoardEntry entry = PonderIndex.getSceneAccess().getRegisteredEntries().iterator().next().getValue();
        PonderScene scene = new PonderScene(entry, new PonderLocalization());
        scene.program(new PonderSceneBuilder(scene), scene.getSceneBuildingUtil());
        return scene;
    }

    private File write(String name, JsonElement document) {
        Path path = directory.resolve(name + ".json");
        try {
            Files.write(path, document.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return path.toFile();
    }

    private static JsonObject document(JsonObject... scenes) {
        JsonObject result = new JsonObject();
        JsonArray array = new JsonArray();
        for (JsonObject scene : scenes) array.add(scene);
        result.add("scenes", array);
        return result;
    }

    private static JsonObject taggedDocument(String name) {
        JsonObject result = document(scene(name, idle(1)));
        JsonObject tag = new JsonObject();
        tag.addProperty("id", name);
        tag.addProperty("title", name);
        JsonArray tags = new JsonArray();
        tags.add(tag);
        result.add("tagDefinitions", tags);
        return result;
    }

    private static JsonObject scene(String name, JsonObject... operations) {
        JsonObject result = new JsonObject();
        result.addProperty("component", "minecraft:stone");
        result.addProperty("schematic", Ponder.asResource("mmce/empty").toString());
        result.addProperty("sceneId", name);
        result.addProperty("title", name);
        JsonArray array = new JsonArray();
        for (JsonObject operation : operations) array.add(operation);
        result.add("operations", array);
        return result;
    }

    private static JsonObject sceneWithIdles(String name, int count) {
        JsonObject result = scene(name);
        JsonArray operations = result.getAsJsonArray("operations");
        for (int i = 0; i < count; i++) operations.add(idle(1));
        return result;
    }

    private static JsonObject definitions(int depth, int branches) {
        JsonObject result = new JsonObject();
        for (int i = 0; i <= depth; i++) {
            JsonObject definition = new JsonObject();
            JsonArray steps = new JsonArray();
            if (i == 0) {
                steps.add(operation("idle"));
            } else {
                for (int branch = 0; branch < branches; branch++) steps.add(reference(i - 1));
            }
            definition.add("steps", steps);
            result.add("a" + i, definition);
        }
        return result;
    }

    private static JsonObject reference(int index) {
        JsonObject result = operation("sequence");
        result.addProperty("interaction", "a" + index);
        return result;
    }

    private static JsonObject inlineSequence(int levels) {
        JsonObject result = idle(1);
        for (int i = 0; i < levels; i++) {
            JsonObject parent = operation("sequence");
            JsonArray steps = new JsonArray();
            steps.add(result);
            parent.add("steps", steps);
            result = parent;
        }
        return result;
    }

    private static JsonObject idle(int ticks) {
        JsonObject result = operation("idle");
        result.addProperty("ticks", ticks);
        return result;
    }

    private static JsonObject repeat(int count) {
        JsonObject result = operation("break_progress");
        result.add("pos", array(0, 0, 0));
        result.addProperty("times", count);
        return result;
    }

    private static JsonObject range(String type, int positions) {
        JsonObject result = operation(type);
        result.add("from", array(0, 0, 0));
        result.add("to", array(positions - 1, 0, 0));
        result.addProperty("state", "minecraft:stone");
        return result;
    }

    private static JsonObject overlayRange(String type, int positions) {
        JsonObject result = range(type, positions);
        result.addProperty("text", "selected blocks");
        if ("gui_texture".equals(type)) {
            result.addProperty("texture", "ponder_test:textures/image.png");
            result.addProperty("textureWidth", 16);
            result.addProperty("textureHeight", 16);
        } else if ("gui_snapshot".equals(type)) {
            result.addProperty("snapshot", "ponder_test:image");
        } else if ("block_gui".equals(type) || "machine_gui".equals(type)) {
            result.addProperty("blockGui", "minecraft:furnace");
            result.addProperty("guiWidth", 176);
            result.addProperty("guiHeight", 166);
        } else if ("gui_outline_text".equals(type)) {
            result.addProperty("gui", "container");
            result.addProperty("guiWidth", 16);
            result.addProperty("guiHeight", 16);
        }
        return result;
    }

    private static JsonObject basePlate(int size) {
        JsonObject result = operation("base_plate");
        result.addProperty("size", size);
        return result;
    }

    private static JsonObject sequence(JsonObject... operations) {
        JsonObject result = operation("sequence");
        JsonArray steps = new JsonArray();
        for (JsonObject operation : operations) {
            steps.add(operation);
        }
        result.add("steps", steps);
        return result;
    }

    private static JsonArray array(Number... values) {
        JsonArray result = new JsonArray();
        for (Number value : values) {
            result.add(value);
        }
        return result;
    }

    private static JsonObject operation(String type) {
        JsonObject result = new JsonObject();
        result.addProperty("type", type);
        return result;
    }

    private static int doublingDepthPast(int maximum) {
        int nodes = 2;
        int depth = 0;
        while (nodes <= maximum) {
            nodes = 1 + 2 * nodes;
            depth++;
        }
        return depth;
    }

    private static ResourceLocation location(String path) {
        return new ResourceLocation("ponder", path);
    }

    @SuppressWarnings("unchecked")
    private static List<PonderPlugin> plugins() throws Exception {
        return (List<PonderPlugin>) field("PLUGINS").get(null);
    }

    private static Field field(String name) throws Exception {
        Field field = PonderIndex.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
