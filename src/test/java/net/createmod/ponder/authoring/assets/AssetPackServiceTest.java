package net.createmod.ponder.authoring.assets;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetPackServiceTest {
    Path fixture, root;
    AssetPackService service;
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    @BeforeEach void setup() throws Exception {
        fixture = Files.createTempDirectory(Files.createDirectories(Paths.get("build/test-fixtures/authoring-packs").toAbsolutePath()), "case-");
        root = fixture.resolve("authoring"); service = new AssetPackService(root);
    }
    @AfterEach void cleanup() throws Exception {
        try (Stream<Path> paths = Files.walk(fixture)) { for (Path path : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.delete(path); }
    }
    static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    static Map<String, byte[]> files() {
        Map<String, byte[]> values = new LinkedHashMap<>(); values.put("pack.json", bytes("{\"formatVersion\":1}"));
        values.put("documents/example/lesson.json", bytes("{\"id\":\"example:lesson\",\"items\":[\"minecraft:stone\"],\"scenes\":[{\"id\":\"main\",\"steps\":[{\"type\":\"idle\",\"duration\":20}]}]}"));
        return values;
    }
    Path archive(Map<String, byte[]> files) throws Exception {
        Path zip = fixture.resolve("source-" + java.util.UUID.randomUUID() + ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) { output.putNextEntry(new ZipEntry(entry.getKey())); output.write(entry.getValue()); output.closeEntry(); }
        }
        return zip;
    }

    @Test void importPreviewIsReadOnlyAndConfirmationStagesWithoutPublishing() throws Exception {
        Path zip = archive(files()); AssetPackService.ImportPlan plan = service.previewImport(zip);
        assertFalse(Files.exists(root)); assertEquals(2, plan.entries.size());
        assertThrows(IOException.class, () -> service.confirmImport(plan, "not-reviewed")); assertFalse(Files.exists(root));
        AssetPackService.Receipt imported = service.confirmImport(plan, plan.getReviewToken());
        assertTrue(Files.isRegularFile(imported.path.resolve("documents/example/lesson.json")));
        assertFalse(Files.exists(root.resolve("published"))); assertFalse(Files.exists(root.resolve("drafts")));
        assertThrows(IOException.class, () -> service.confirmImport(plan, plan.getReviewToken()));
    }

    @Test void importRefusesArchiveChangedAfterPreviewWithoutWrites() throws Exception {
        Path zip = archive(files()); AssetPackService.ImportPlan plan = service.previewImport(zip);
        Map<String, byte[]> changed = files(); changed.put("documents/example/other.json", changed.get("documents/example/lesson.json"));
        Files.copy(archive(changed), zip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertThrows(IOException.class, () -> service.confirmImport(plan, plan.getReviewToken())); assertFalse(Files.exists(root));
    }

    @Test void zipSlipBackslashesReservedAndCaseDuplicatesAreRejected() throws Exception {
        for (String unsafe : new String[] {"../evil", "/evil", "a\\evil", "a:evil", "a/CON.txt", "a/./b", "a//b"}) {
            Map<String, byte[]> values = files(); values.put(unsafe, bytes("x")); Path zip = archive(values);
            assertThrows(IOException.class, () -> service.previewImport(zip), unsafe);
        }
        Map<String, byte[]> values = files(); values.put("PACK.JSON", bytes("{}")); Path duplicate = archive(values);
        assertThrows(IOException.class, () -> service.previewImport(duplicate)); assertFalse(Files.exists(root));
    }

    @Test void fileDirectoryConflictsAndExecutableContentAreRejected() throws Exception {
        Map<String, byte[]> values = files(); values.put("documents", bytes("x")); Path conflict = archive(values);
        assertThrows(IOException.class, () -> service.previewImport(conflict));
        for (String path : new String[] {"scripts/auto.js", "mods/evil.jar", "payload.class", "assets/structures/x/test.exe"}) {
            values = files(); values.put(path, bytes("not executed")); Path zip = archive(values);
            assertThrows(IOException.class, () -> service.previewImport(zip));
        }
    }

    @Test void directoryPayloadAndDeepJsonCannotBypassExpansionOrParserBudgets() throws Exception {
        Map<String, byte[]> values = files(); values.put("assets/", new byte[500]); Path directoryPayload = archive(values);
        assertThrows(IOException.class, () -> service.previewImport(directoryPayload));
        StringBuilder json = new StringBuilder("{\"formatVersion\":1,\"deep\":");
        for (int i = 0; i < 70; i++) json.append('['); json.append('0');
        for (int i = 0; i < 70; i++) json.append(']'); json.append('}');
        values = files(); values.put("pack.json", bytes(json.toString())); Path deep = archive(values);
        assertThrows(IOException.class, () -> service.previewImport(deep)); assertFalse(Files.exists(root));
    }

    @Test void zipCentralSymlinkMetadataIsRejectedEvenThoughExtractionWouldBeRegular() throws Exception {
        Path zip = archive(files()); byte[] data = Files.readAllBytes(zip); boolean changed = false;
        for (int i = 0; i < data.length - 46; i++) {
            if ((data[i] & 255) == 0x50 && (data[i + 1] & 255) == 0x4b && (data[i + 2] & 255) == 1 && (data[i + 3] & 255) == 2) {
                data[i + 5] = 3; data[i + 40] = (byte) 0xff; data[i + 41] = (byte) 0xa1; changed = true; break;
            }
        }
        assertTrue(changed); Files.write(zip, data);
        assertThrows(IOException.class, () -> service.previewImport(zip)); assertFalse(Files.exists(root));
    }

    @Test void practicalNarrowedLimitsEnforceCompressedExpandedAndEntryBudgets() throws Exception {
        Path zip = archive(files());
        assertThrows(IOException.class, () -> new AssetPackService(root, new AssetPackService.Limits(10, 4096, 10)).previewImport(zip));
        assertThrows(IOException.class, () -> new AssetPackService(root, new AssetPackService.Limits(4096, 20, 10)).previewImport(zip));
        assertThrows(IOException.class, () -> new AssetPackService(root, new AssetPackService.Limits(4096, 4096, 1)).previewImport(zip));
        assertFalse(Files.exists(root));
    }

    @Test void invalidManifestJsonStructureAndPngFailInPreview() throws Exception {
        Map<String, byte[]> values = files(); values.put("pack.json", bytes("{\"formatVersion\":2}")); Path version = archive(values);
        assertThrows(IOException.class, () -> service.previewImport(version));
        values = files(); values.put("documents/x/bad.json", bytes("this is not JSON")); Path json = archive(values);
        assertThrows(IOException.class, () -> service.previewImport(json));
        values = files(); values.put("assets/structures/x/bad.nbt", bytes("this is not NBT")); Path nbt = archive(values);
        assertThrows(IOException.class, () -> service.previewImport(nbt));
        values = files(); values.put("assets/textures/x/bad.png", bytes("not PNG")); Path png = archive(values);
        assertThrows(IOException.class, () -> service.previewImport(png));
    }

    @Test void exportDefaultStripsTilePrivacyRequiresConfirmationAndNeverOverwrites() throws Exception {
        Map<String, byte[]> values = files(); values.put("assets/structures/x/chest.nbt", AuthoringAssetSafetyTest.encoded(AuthoringAssetSafetyTest.schematic().toNbt(), true));
        AssetPackService.ExportPlan plan = service.previewExport(values);
        assertFalse(Files.exists(root)); assertThrows(IOException.class, () -> service.confirmExport(plan, "bad", "export.zip"));
        AssetPackService.Receipt result = service.confirmExport(plan, plan.getReviewToken(), "export.zip");
        assertTrue(Files.isRegularFile(result.path)); assertThrows(IOException.class, () -> service.confirmExport(plan, plan.getReviewToken(), "export.zip"));
        AssetPackService.ImportPlan importedPlan = service.previewImport(result.path);
        AssetPackService.Receipt imported = service.confirmImport(importedPlan, importedPlan.getReviewToken());
        AssetPackService.InstallReceipt installed = service.installImportedAssets(imported);
        assertEquals(java.util.Collections.singletonList("x:chest"), installed.structureIds);
        assertFalse(new StructurePool(root).resolve("x:chest").getBlockEntityNbt(BlockPos.ORIGIN).hasKey("Items"));
        assertThrows(IOException.class, () -> service.installImportedAssets(imported));
    }

    @Test void activationChecksAllConflictsBeforeInstallingAnyAndRejectsTamperedStaging() throws Exception {
        Map<String, byte[]> values = files(); byte[] nbt = AuthoringAssetSafetyTest.encoded(AuthoringAssetSafetyTest.schematic().toNbt(), true);
        values.put("assets/structures/x/a.nbt", nbt); values.put("assets/structures/x/z.nbt", nbt);
        AssetPackService.ImportPlan plan = service.previewImport(archive(values)); AssetPackService.Receipt receipt = service.confirmImport(plan, plan.getReviewToken());
        new StructurePool(root).saveNew("x:z", AuthoringAssetSafetyTest.schematic());
        assertThrows(IOException.class, () -> service.installImportedAssets(receipt));
        assertFalse(Files.exists(root.resolve("assets/structures/x/a.nbt")));
        Files.write(receipt.path.resolve("documents/example/lesson.json"), bytes("{}"));
        assertThrows(IOException.class, () -> service.installImportedAssets(receipt));
    }

    @Test void fullNbtExportIsOptInButActivationStillUsesSafeDefault() throws Exception {
        Map<String, byte[]> values = files(); values.put("assets/structures/x/chest.nbt", AuthoringAssetSafetyTest.encoded(AuthoringAssetSafetyTest.schematic().toNbt(), true));
        AssetPackService.ExportPlan plan = service.previewExport(values, BlueprintCapture.NbtPolicy.FULL_NBT_EXPLICIT_OPT_IN);
        assertFalse(plan.warnings.isEmpty());
        AssetPackService.Receipt exported = service.confirmExport(plan, plan.getReviewToken(), "full.zip");
        AssetPackService.ImportPlan importedPlan = service.previewImport(exported.path); AssetPackService.Receipt imported = service.confirmImport(importedPlan, importedPlan.getReviewToken());
        assertEquals("private-items", net.createmod.ponder.foundation.PonderSchematic.loadExternal(imported.path.resolve("assets/structures/x/chest.nbt")).getBlockEntityNbt(BlockPos.ORIGIN).getString("Items"));
        service.installImportedAssets(imported); assertFalse(new StructurePool(root).resolve("x:chest").getBlockEntityNbt(BlockPos.ORIGIN).hasKey("Items"));
    }

    @Test void nestedNbtInflationAndBlockCountsAreCumulativeAcrossAllPackEntries() throws Exception {
        byte[] nbt = AuthoringAssetSafetyTest.encoded(AuthoringAssetSafetyTest.schematic().toNbt(), true);
        int rawSize = AuthoringAssetSafetyTest.encoded(AuthoringAssetSafetyTest.schematic().toNbt(), false).length;
        Map<String, byte[]> values = files(); values.put("assets/structures/x/first.nbt", nbt); values.put("assets/structures/x/second.nbt", nbt);
        Path zip = archive(values);
        AssetPackService byteLimited = new AssetPackService(root, new AssetPackService.Limits(4096, 16384, 20, rawSize * 2L - 1, 10, 100));
        assertThrows(IOException.class, () -> byteLimited.previewImport(zip));
        assertThrows(IOException.class, () -> byteLimited.previewExport(values));
        AssetPackService blockLimited = new AssetPackService(root, new AssetPackService.Limits(4096, 16384, 20, 16384, 1, 100));
        assertThrows(IOException.class, () -> blockLimited.previewImport(zip));
        assertThrows(IOException.class, () -> blockLimited.previewExport(values));
        assertFalse(Files.exists(root));
    }

    @Test void pngPixelBytesHaveTheirOwnCumulativeBudgetBeforeInflatingEachImage() throws Exception {
        Map<String, byte[]> values = files(); byte[] png = PngAssetValidatorTest.png(1, 1, 8, 6, 0, new byte[] {0, 1, 2, 3, 4});
        values.put("assets/textures/x/first.png", png); values.put("assets/textures/x/second.png", png); Path zip = archive(values);
        AssetPackService limited = new AssetPackService(root, new AssetPackService.Limits(4096, 16384, 20, 16384, 10, 9));
        assertThrows(IOException.class, () -> limited.previewImport(zip)); assertThrows(IOException.class, () -> limited.previewExport(values));
        assertFalse(Files.exists(root));
        AssetPackService.ImportPlan valid = service.previewImport(zip);
        AssetPackService.Receipt receipt = service.confirmImport(valid, valid.getReviewToken());
        assertEquals(2, service.installImportedAssets(receipt).texturePaths.size());
    }
}
