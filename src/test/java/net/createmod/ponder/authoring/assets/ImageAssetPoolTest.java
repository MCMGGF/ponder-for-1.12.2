package net.createmod.ponder.authoring.assets;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.AuthoringCompiler;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.ImageSnapshotProvider;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImageAssetPoolTest {
    private Path fixture, root;
    private ImageAssetPool pool;
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    @BeforeEach void setup() throws Exception {
        fixture = Files.createTempDirectory(Files.createDirectories(Paths.get("build/test-fixtures/authoring-images").toAbsolutePath()), "case-");
        root = fixture.resolve("authoring"); pool = new ImageAssetPool(root);
    }
    @AfterEach void cleanup() throws Exception {
        PonderGuiSnapshotRegistry.releaseRuntimeState();
        try (Stream<Path> paths = Files.walk(fixture)) { for (Path p : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.delete(p); }
    }
    static byte[] png() throws Exception { return PngAssetValidatorTest.png(1, 1, 8, 6, 0, new byte[] {0, 1, 2, 3, 4}); }
    private void install(String id) throws Exception {
        Path path = root.resolve("assets").resolve(ImageAssetPool.relativePath(id)); Files.createDirectories(path.getParent()); Files.write(path, png());
    }
    private AuthoringDocument document(String extra, String steps) {
        return AuthoringDocument.parse("{\"id\":\"local:images\",\"items\":[\"minecraft:stone\"]," + extra
            + "\"scenes\":[{\"id\":\"main\",\"steps\":[" + steps + "]}]}");
    }
    private AuthoringCompiler compiler() { AuthoringCompiler compiler = new AuthoringCompiler(); ImageStepHandlers.register(compiler, pool); return compiler; }
    private String show(String id) { return "{\"type\":\"show_image\",\"image\":\"" + id + "\",\"duration\":80}"; }

    @Test void canonicalIdsAreExplicitAndNeverBecomeArbitraryPathsOrUrls() throws Exception {
        assertEquals("textures/local/guide/front.png", ImageAssetPool.relativePath("local:guide/front"));
        assertEquals("local:guide/front", ImageAssetPool.idFromPackPath("assets/textures/local/guide/front.png"));
        for (String invalid : new String[] {"front", "https://x/a.png", "file:///x", "C:/a", "local:../a", "local:CON", "local:x.png", "local:/x", "local:a\\b"})
            assertThrows(IOException.class, () -> ImageAssetPool.normalizeId(invalid), invalid);
        assertFalse(Files.exists(root));
    }
    @Test void onlyReviewedInboxImagesCanBeWrittenAndNeverOverwriteAnExistingId() throws Exception {
        Files.createDirectories(root.resolve("inbox")); Files.write(root.resolve("inbox/diagram.png"), png());
        ImageAssetPool.ImportPlan preview = pool.previewInbox("diagram.png", "local:diagram");
        assertFalse(Files.exists(root.resolve("assets"))); assertEquals(1, preview.image.width); assertEquals(5, preview.image.decodedBytes);
        pool.confirmInbox(preview); assertEquals(java.util.Collections.singletonList("local:diagram"), pool.listIds());
        assertThrows(IOException.class, () -> pool.confirmInbox(preview));
        assertThrows(IOException.class, () -> pool.previewInbox("../diagram.png", "local:x"));
        assertThrows(IOException.class, () -> new ImageAssetPool(root).confirmInbox(preview));
    }
    @Test void changedInboxAndChangedInstalledImagesAreRejectedBeforeGpuUpload() throws Exception {
        Files.createDirectories(root.resolve("inbox")); Files.write(root.resolve("inbox/diagram.png"), png());
        ImageAssetPool.ImportPlan plan = pool.previewInbox("diagram.png", "local:x");
        Files.write(root.resolve("inbox/diagram.png"), PngAssetValidatorTest.png(1, 1, 8, 6, 0, new byte[] {0, 9, 2, 3, 4}));
        assertThrows(IOException.class, () -> pool.confirmInbox(plan)); assertFalse(Files.exists(root.resolve("assets")));
        install("local:x"); ImageAssetPool.ImageInfo info = pool.inspect("local:x");
        Files.write(root.resolve("assets/textures/local/x.png"), new byte[] {1});
        assertThrows(IOException.class, () -> pool.readChecked(info));
    }
    @Test void pngContentAndAuthoringSpecificBudgetsAreStrict() throws Exception {
        assertThrows(IOException.class, () -> ImageAssetPool.inspectBytes("local:x", new byte[ImageAssetPool.MAX_FILE_BYTES + 1]));
        assertThrows(IOException.class, () -> ImageAssetPool.inspectBytes("local:x", new byte[100]));
        byte[] raw = new byte[1 + 2049 * 4];
        assertThrows(IOException.class, () -> ImageAssetPool.inspectBytes("local:x", PngAssetValidatorTest.png(2049, 1, 8, 6, 0, raw)));
        ImageAssetPool.ImageInfo info = ImageAssetPool.inspectBytes("local:x", png());
        assertEquals(4, info.rgbaBytes); assertEquals(64, info.sha256.length());
    }
    @Test void explicitForeignMappingSurvivesAndMissingMappingsDoNotFallBack() throws Exception {
        install("local:diagram");
        AuthoringDocument mapped = document("\"imageMappings\":{\"textures/modern/diagram.png\":\"local:diagram\"},", show("textures/modern/diagram.png"));
        assertEquals(java.util.Collections.singletonList("local:diagram"), ImageAssetPool.referencedIds(mapped));
        assertFalse(compiler().validate(mapped, null).hasErrors());
        assertTrue(compiler().validate(document("", show("textures/modern/diagram.png")), null).hasErrors());
        assertTrue(compiler().validate(document("", show("local:missing")), null).hasErrors());
        assertTrue(compiler().validate(document("\"imageMappings\":{\"foreign\":12},", show("foreign")), null).hasErrors());
    }
    @Test void distinctContextReferencesAreBoundedAndDuplicateImagesLoadOnce() throws Exception {
        install("local:x");
        assertEquals(1, pool.inspectContext(document("", show("local:x") + "," + show("local:x"))).size());
        StringBuilder steps = new StringBuilder();
        for (int i = 0; i < 33; i++) { if (i > 0) steps.append(','); steps.append(show("local:image_" + i)); }
        assertThrows(IOException.class, () -> ImageAssetPool.referencedIds(document("", steps.toString())));
    }
    @Test void compileProducesLazyForegroundImageWithActualWorldTargetAndCompatibleTimeline() throws Exception {
        install("local:x");
        String step = "{\"type\":\"show_image\",\"image\":\"local:x\",\"duration\":80,\"blockPos\":[2,1,3]}";
        PonderScene scene = compiler().compile(document("", "{\"type\":\"idle\",\"duration\":12}," + step), null).get(0);
        PonderScene.OverlayEvent event = scene.getOverlayEvents().get(0);
        assertEquals(PonderScene.OverlayEventType.GUI_TEXTURE, event.getType()); assertEquals(12, event.getTick()); assertEquals(80, event.getDuration());
        assertEquals(java.util.Collections.singletonList(new BlockPos(2, 1, 3)), event.getTargetPositions());
        ImageSnapshotProvider provider = (ImageSnapshotProvider) event.getGuiSnapshotProvider();
        assertFalse(provider.isUploaded()); assertNull(provider.getFailure());
        assertSame(provider, PonderGuiSnapshotRegistry.resolve(event, 12).renderer);
        assertSame(provider, PonderGuiSnapshotRegistry.resolve(event, 70).renderer);
        provider.reset(); assertFalse(provider.isUploaded()); assertNull(PonderGuiSnapshotRegistry.resolve(event, Float.NaN));
    }
    @Test void imageStepTargetsAndDurationDoNotExpandArbitrarySelectionOrTimeBudgets() throws Exception {
        install("local:x");
        for (String invalid : new String[] {"\"duration\":24001", "\"duration\":0", "\"point\":[1,2]", "\"point\":[1,2,30000001]"}) {
            String step = "{\"type\":\"show_image\",\"image\":\"local:x\"," + invalid + "}";
            assertTrue(compiler().validate(document("", step), null).hasErrors(), step);
        }
    }
    @Test void explicitImageScenePointDoesNotFloorIntoASourceBlockSelection() throws Exception {
        install("local:x");
        String step="{\"type\":\"show_image\",\"image\":\"local:x\",\"point\":[1.5,0.5,0.5],\"pointSpace\":\"scene\"}";
        PonderScene.OverlayEvent event=compiler().compile(document("",step),null).get(0).getOverlayEvents().get(0);
        assertTrue(event.isScenePoint());assertTrue(event.getTargetPositions().isEmpty());assertEquals(1.5,event.getPointAt().x);
        assertTrue(compiler().validate(document("",step.replace("\"scene\"","\"unknown\"")),null).hasErrors());
    }
    @Test void bindingAppendsToChosenSceneWithoutMutatingStableDocumentOrSource() throws Exception {
        AuthoringDocument original = AuthoringDocument.parse("{\"id\":\"local:images\",\"items\":[\"minecraft:stone\"],\"scenes\":[{\"id\":\"first\",\"steps\":[]},{\"id\":\"second\",\"steps\":[]}]}");
        AuthoringDocument bound = ImageStepHandlers.append(original, 1, "local:x", 80);
        assertEquals(original.getId(), bound.getId()); assertEquals(0, original.getScenes().get(1).getAsJsonArray("steps").size());
        assertEquals(0, bound.getScenes().get(0).getAsJsonArray("steps").size()); assertEquals("local:x", bound.getScenes().get(1).getAsJsonArray("steps").get(0).getAsJsonObject().get("image").getAsString());
        assertThrows(IOException.class, () -> ImageStepHandlers.append(original, 2, "local:x", 80));
    }
    @Test void malformedDraftSegmentsCannotShiftTheSelectedBindingTarget() throws Exception {
        AuthoringDocument draft = AuthoringDocument.parse("{\"id\":\"local:images\",\"items\":[\"minecraft:stone\"],\"scenes\":[{\"id\":\"first\",\"steps\":[]},null,{\"id\":\"third\",\"steps\":[]}]}");
        assertEquals(java.util.Arrays.asList(0, 2), ImageStepHandlers.editableSceneIndices(draft));
        AuthoringDocument bound = ImageStepHandlers.append(draft, ImageStepHandlers.editableSceneIndices(draft).get(1), "local:x", 80);
        assertEquals(0, bound.toJson().getAsJsonArray("scenes").get(0).getAsJsonObject().getAsJsonArray("steps").size());
        assertTrue(bound.toJson().getAsJsonArray("scenes").get(1).isJsonNull());
        assertEquals(1, bound.toJson().getAsJsonArray("scenes").get(2).getAsJsonObject().getAsJsonArray("steps").size());
    }
    @Test void imagePacksHaveCanonicalManifestMappingAndRoundTripReferencedIds() throws Exception {
        install("local:x"); AssetPackService service = new AssetPackService(root);
        Map<String, byte[]> files = new LinkedHashMap<>(); files.put("documents/local/images.json", document("", show("local:x")).toJson().toString().getBytes(StandardCharsets.UTF_8));
        files.put("assets/textures/local/x.png", png()); AssetPackService.ExportPlan export = service.previewExport(files);
        AssetPackService.Receipt zip = service.confirmExport(export, export.getReviewToken(), "images.zip");
        AssetPackService.ImportPlan imported = service.previewImport(zip.path); AssetPackService.Receipt staged = service.confirmImport(imported, imported.getReviewToken());
        String manifest = new String(Files.readAllBytes(staged.path.resolve("pack.json")), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("\"local:x\":\"assets/textures/local/x.png\""));
        Path fresh = fixture.resolve("fresh"); AssetPackService freshService = new AssetPackService(fresh);
        AssetPackService.ImportPlan freshPlan = freshService.previewImport(zip.path); freshService.installImportedAssets(freshService.confirmImport(freshPlan, freshPlan.getReviewToken()));
        ImageAssetPool destination = new ImageAssetPool(fresh); assertEquals(pool.inspect("local:x").sha256, destination.inspect("local:x").sha256);
        assertFalse(Files.exists(fresh.resolve("published"))); assertFalse(Files.exists(fresh.resolve("drafts")));
    }
    @Test void maliciousPackMappingsAndMissingReferencesAreExplicit() throws Exception {
        AssetPackService service = new AssetPackService(root); Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("pack.json", "{\"formatVersion\":1,\"imageAssets\":{\"local:x\":\"../outside.png\"}}".getBytes(StandardCharsets.UTF_8));
        files.put("assets/textures/local/x.png", png());
        // Export regenerates a canonical manifest rather than preserving a foreign filesystem mapping.
        AssetPackService.ExportPlan canonical = service.previewExport(files); assertNotNull(canonical);
        Path malicious = fixture.resolve("malicious.zip");
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(malicious))) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        assertThrows(IOException.class, () -> service.previewImport(malicious)); assertFalse(Files.exists(root));
        files.clear(); files.put("documents/local/images.json", document("", show("local:missing")).toJson().toString().getBytes(StandardCharsets.UTF_8));
        assertTrue(service.previewExport(files).warnings.stream().anyMatch(w -> w.contains("Missing image asset") && w.contains("local:missing")));
    }
    @Test void browsingDoesNotDecodeEveryFileOrCreateAnAuthorWorkspace() throws Exception {
        assertTrue(pool.listIds().isEmpty()); assertTrue(pool.listInboxNames().isEmpty()); assertFalse(Files.exists(root));
        Path bad = root.resolve("assets/textures/local/bad.png"); Files.createDirectories(bad.getParent()); Files.write(bad, new byte[] {1});
        assertEquals(java.util.Collections.singletonList("local:bad"), pool.listIds()); assertThrows(IOException.class, () -> pool.inspect("local:bad"));
    }
}
