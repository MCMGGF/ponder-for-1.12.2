package net.createmod.ponder.authoring.assets;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.authoring.AuthoringCompiler;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.AuthoringValidation;
import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetPackSchemaReviewTest {
    private Path fixture, root;
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    @BeforeEach void before() throws Exception {
        fixture = Files.createTempDirectory(Files.createDirectories(Paths.get("build/test-fixtures/pack-schema-review").toAbsolutePath()), "case-");
        root = fixture.resolve("authoring");
    }
    @AfterEach void after() throws Exception {
        try (Stream<Path> paths = Files.walk(fixture)) { for (Path path : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.delete(path); }
    }
    private AuthoringDocument document(String type, int duration) {
        return AuthoringDocument.parse("{\"id\":\"local:review\",\"items\":[\"minecraft:stone\"],\"scenes\":[{\"id\":\"main\",\"steps\":[{\"type\":\"" + type + "\",\"image\":\"local:diagram\",\"duration\":" + duration + "}]}]}");
    }
    private Map<String, byte[]> files(AuthoringDocument document, boolean image) throws Exception {
        Map<String, byte[]> values = new LinkedHashMap<>();
        values.put("documents/local/review.json", document.toJson().toString().getBytes(StandardCharsets.UTF_8));
        if (image) values.put("assets/textures/local/diagram.png", PngAssetValidatorTest.png(1, 1, 8, 6, 0, new byte[] {0, 1, 2, 3, 4}));
        return values;
    }
    @Test void activeCompilerReviewRemovesOnlyTheFalseImageUnknownErrorWhileDefaultRemainsConservative() throws Exception {
        AuthoringCompiler compiler = new AuthoringCompiler(); ImageStepHandlers.register(compiler, new ImageAssetPool(root));
        Map<String, byte[]> values = files(document("show_image", 80), true);
        AssetPackService.ExportPlan conservative = new AssetPackService(root).previewExport(values);
        assertTrue(conservative.warnings.stream().anyMatch(w -> w.contains("ERROR") && w.contains("step.unknown") && w.contains("show_image")));
        AssetPackService service = new AssetPackService(root, compiler::validateSchema);
        AssetPackService.ExportPlan active = service.previewExport(values);
        assertFalse(active.warnings.stream().anyMatch(w -> w.contains("step.unknown")));
        assertTrue(active.warnings.stream().anyMatch(w -> w.contains("SCHEMA_ONLY") && w.contains("full validation")));
        AssetPackService.Receipt zip = service.confirmExport(active, active.getReviewToken(), "review.zip");
        assertFalse(service.previewImport(zip.path).warnings.stream().anyMatch(w -> w.contains("step.unknown")));
        assertTrue(compiler.validate(document("show_image", 80), null).hasErrors(), "Review never installs the bundled PNG or asserts publishability");
    }
    @Test void unknownStepAndMissingBundledImageAreStillExplicitAndMalformedHandlersFailFullValidation() throws Exception {
        AuthoringCompiler compiler = new AuthoringCompiler(); ImageStepHandlers.register(compiler, new ImageAssetPool(root));
        AssetPackService service = new AssetPackService(root, compiler::validateSchema);
        assertTrue(service.previewExport(files(document("unregistered_extension", 80), false)).warnings.stream()
            .anyMatch(w -> w.contains("ERROR") && w.contains("step.unknown") && w.contains("unregistered_extension")));
        assertTrue(service.previewExport(files(document("show_image", 80), false)).warnings.stream().anyMatch(w -> w.contains("Missing image asset")));
        Path png = root.resolve("assets/textures/local/diagram.png"); Files.createDirectories(png.getParent());
        Files.write(png, PngAssetValidatorTest.png(1, 1, 8, 6, 0, new byte[] {0, 1, 2, 3, 4}));
        assertTrue(compiler.validate(document("show_image", 0), null).hasErrors());
        assertThrows(AuthoringCompiler.CompilationException.class, () -> compiler.compile(document("show_image", 0), null));
    }
    @Test void schemaReviewNeverCallsHandlerValidationApplyOrAssetResolution() throws Exception {
        AtomicInteger validate = new AtomicInteger(), apply = new AtomicInteger();
        AuthoringCompiler compiler = new AuthoringCompiler().registerHandler("fixture_review", new AuthoringCompiler.StepHandler() {
            @Override public void validate(AuthoringDocument doc, JsonObject step, String path, AuthoringValidation.Builder issues) { validate.incrementAndGet(); throw new IllegalStateException("Detailed handler must not run during pack review"); }
            @Override public void apply(SceneBuilder scene, SceneBuildingUtil util, JsonObject step, AuthoringCompiler.StepContext context) { apply.incrementAndGet(); throw new IllegalStateException("Apply must never run during pack review"); }
        });
        AuthoringDocument document = document("fixture_review", 80);
        assertFalse(compiler.validateSchema(document).hasErrors());
        new AssetPackService(root, compiler::validateSchema).previewExport(files(document, false));
        assertEquals(0, validate.get()); assertEquals(0, apply.get()); assertFalse(Files.exists(root));
    }
}
