package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import net.createmod.ponder.authoring.assets.StructurePool;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PreviewActorTimeline;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigratedSceneExamplesTest {
    private static final Path EXAMPLES = Paths.get("examples");
    private final AuthoringCompiler compiler = new AuthoringCompiler();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void jsonPackLoadsFromTheInstalledLayoutAndProgramsAReplayableScene() throws Exception {
        PonderScene scene = compileInstalled("json", "ponder_json:demo_pack");

        assertEquals("ponder_authoring:ponder_json/demo_pack/crafting_demo", scene.getSceneId().toString());
        assertExampleStructureAndSectionEvents(scene);
        assertEquals(List.of(35), scene.getKeyframeTicks());
        assertTrue(scene.getOverlayEvents().stream().anyMatch(overlay -> "JSON step: handles and SNBT".equals(overlay.getText())));
        assertTrue(scene.getOverlayEvents().stream().anyMatch(PonderScene.OverlayEvent::isControlRightClick));
        assertEquals(Items.PAPER, actor(scene, 29).getItemStack().getItem());
        assertTrue(actor(scene, 29).visible);
        assertFalse(actor(scene, 30).visible);
        assertTrue(actor(scene, 35).visible);
        assertFalse(actor(scene, 95).alive);
    }

    @Test
    void zenScriptMigrationProgramsTheDiagnosticsWalkthrough() throws Exception {
        PonderScene scene = compileInstalled("zenscript", "ponder_zen:diagnostics");

        assertEquals("ponder_authoring:ponder_zen/diagnostics/diagnostics_demo", scene.getSceneId().toString());
        assertExampleStructureAndSectionEvents(scene);
        assertTrue(scene.getWorldEvents().stream().anyMatch(event -> event.getType() == PonderScene.WorldEventType.ROTATE_SECTION));
        assertEquals(List.of(30, 220, 330), scene.getKeyframeTicks());
        assertEquals(1, scene.getPointOfInterestEvents().size());
        assertTrue(scene.getOverlayEvents().stream().anyMatch(overlay -> overlay.getSceneBounds() != null));
        assertTrue(scene.getOverlayEvents().stream().anyMatch(overlay -> overlay.getLineStart() != null));
        assertTrue(scene.getOverlayEvents().stream().anyMatch(PonderScene.OverlayEvent::isControlScroll));
        assertEquals(Items.BOOK, actor(scene, 429).getItemStack().getItem());
        assertTrue(actor(scene, 429).visible);
        assertFalse(actor(scene, 430).visible);
        assertTrue(actor(scene, 450).visible);
        assertFalse(actor(scene, 460).alive);
    }

    @Test
    void negativeDocumentsCannotEnterThePublishedWorkspace(@TempDir Path temporaryConfig) throws Exception {
        AuthoringStore store = new AuthoringStore(temporaryConfig, compiler);
        StructurePool assets = new StructurePool(installedStore("zenscript").getRoot());

        assertPublicationError(store, assets, "90_duplicate_id.json", "scene.id");
        assertPublicationError(store, assets, "91_missing_structure.json", "asset.structure");
        assertPublicationError(store, assets, "92_invalid_handle.json", "step.invalid");
        assertPublicationError(store, assets, "93_oversized_nbt.json", "budget.nbt");
        assertTrue(store.listPublishedIds().isEmpty());
    }

    private static AuthoringStore installedStore(String pack) {
        return new AuthoringStore(EXAMPLES.resolve(pack).resolve("config"));
    }

    private PonderScene compileInstalled(String pack, String id) throws Exception {
        AuthoringStore store = installedStore(pack);
        assertEquals(List.of(id), store.listPublishedIds());
        return compiler.compile(store.loadPublished(id).getDocument(), new StructurePool(store.getRoot())).get(0);
    }

    private static void assertExampleStructureAndSectionEvents(PonderScene scene) {
        assertEquals(new BlockPos(5, 3, 5), scene.getSchematic().getSize());
        assertEquals(Blocks.CHEST, scene.getSchematic().getBlocks().get(new BlockPos(3, 1, 2)).getBlock());
        assertTrue(scene.getWorldEvents().stream().anyMatch(event -> event.getType() == PonderScene.WorldEventType.MOVE_SECTION));
        assertTrue(scene.getWorldEvents().stream().anyMatch(event -> event.getType() == PonderScene.WorldEventType.HIDE_SECTION));
        assertTrue(scene.getWorldEvents().stream().anyMatch(event -> event.getType() == PonderScene.WorldEventType.APPLY_NBT));
        assertFalse(scene.getParticleEvents().isEmpty());
        assertTrue(scene.isFinished());
    }

    private static PreviewActorTimeline.State actor(PonderScene scene, int tick) {
        return PreviewActorTimeline.evaluate(scene.getActorEvents(), tick).get(0);
    }

    private static void assertPublicationError(AuthoringStore store, StructurePool assets, String file, String code)
        throws Exception {
        AuthoringDocument document = AuthoringDocument.parse(Files.readString(EXAMPLES.resolve("zenscript/diagnostics").resolve(file)));
        AuthoringCompiler.CompilationException failure = assertThrows(AuthoringCompiler.CompilationException.class,
            () -> store.publish(document, null, assets), file);
        assertTrue(failure.getValidation().getIssues().stream().anyMatch(issue -> code.equals(issue.getCode())),
            () -> file + " produced " + failure.getValidation().getIssues());
    }
}
