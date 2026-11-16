package net.createmod.ponder.foundation.content.oil;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OilLinePackBaselineTest {

    @TempDir Path directory;

    @Test
    void productionManifestAcceptsTheSuppliedAuditedPack() {
        String gameDir = System.getenv("PONDER_AUDITED_GAME_DIR");
        assumeTrue(gameDir != null && !gameDir.isEmpty(), "No audited pack supplied");
        assertDoesNotThrow(() -> OilLinePackBaseline.verify(java.nio.file.Paths.get(gameDir)));
    }

    @Test
    void acceptsAnUnchangedFixtureWithoutWritingItsContentsOrModificationTime() throws Exception {
        Path file = write("scripts/recipe.zs", "original");
        FileTime modified = Files.getLastModifiedTime(file);
        Map<String, String> expected = expected("scripts/recipe.zs", file);
        assertDoesNotThrow(() -> OilLinePackBaseline.verify(directory, expected));
        assertEquals("original", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        assertEquals(modified, Files.getLastModifiedTime(file));
    }

    @Test
    void acceptsEitherCompleteAuditedRevisionWithoutChangingFiles() throws Exception {
        Path script = write("scripts/recipe.zs", "old recipe");
        Path config = write("config/content.json", "old config");
        Map<String, String> old = new LinkedHashMap<>(expected("scripts/recipe.zs", script));
        old.putAll(expected("config/content.json", config));
        write("scripts/recipe.zs", "new recipe");
        write("config/content.json", "new config");
        Map<String, String> current = new LinkedHashMap<>(expected("scripts/recipe.zs", script));
        current.putAll(expected("config/content.json", config));
        assertDoesNotThrow(() -> OilLinePackBaseline.verifyProfiles(directory, Arrays.asList(current, old)));
        write("scripts/recipe.zs", "old recipe");
        write("config/content.json", "old config");
        FileTime modified = Files.getLastModifiedTime(script);
        assertDoesNotThrow(() -> OilLinePackBaseline.verifyProfiles(directory, Arrays.asList(current, old)));
        assertEquals(modified, Files.getLastModifiedTime(script));
        assertEquals("old recipe", new String(Files.readAllBytes(script), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsMixedRevisionsEvenWhenEveryIndividualHashWasAudited() throws Exception {
        Path script = write("scripts/recipe.zs", "old recipe");
        Path config = write("config/content.json", "old config");
        Map<String, String> old = new LinkedHashMap<>(expected("scripts/recipe.zs", script));
        old.putAll(expected("config/content.json", config));
        write("scripts/recipe.zs", "new recipe");
        write("config/content.json", "new config");
        Map<String, String> current = new LinkedHashMap<>(expected("scripts/recipe.zs", script));
        current.putAll(expected("config/content.json", config));
        write("config/content.json", "old config");
        assertThrows(IllegalStateException.class,
            () -> OilLinePackBaseline.verifyProfiles(directory, Arrays.asList(current, old)));
        write("scripts/recipe.zs", "old recipe");
        write("config/content.json", "new config");
        assertThrows(IllegalStateException.class,
            () -> OilLinePackBaseline.verifyProfiles(directory, Arrays.asList(current, old)));
    }

    @Test
    void rechecksApprovedProfilesAndRejectsUnknownChangesOrMissingFiles() throws Exception {
        Path file = write("scripts/recipe.zs", "approved");
        Map<String, String> profile = expected("scripts/recipe.zs", file);
        assertDoesNotThrow(() -> OilLinePackBaseline.verifyProfiles(directory, Collections.singletonList(profile)));
        write("scripts/recipe.zs", "changed");
        assertThrows(IllegalStateException.class,
            () -> OilLinePackBaseline.verifyProfiles(directory, Collections.singletonList(profile)));
        Files.delete(file);
        assertThrows(IllegalStateException.class,
            () -> OilLinePackBaseline.verifyProfiles(directory, Collections.singletonList(profile)));
        assertThrows(IllegalStateException.class,
            () -> OilLinePackBaseline.verifyProfiles(directory, Collections.emptyList()));
    }

    @Test
    void rechecksEveryInvocationAndRejectsChangedContentWithClearChineseMessage() throws Exception {
        Path file = write("scripts/recipe.zs", "original");
        Map<String, String> expected = expected("scripts/recipe.zs", file);
        OilLinePackBaseline.verify(directory, expected);
        Files.write(file, "modified".getBytes(StandardCharsets.UTF_8));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> OilLinePackBaseline.verify(directory, expected));
        assertTrue(failure.getMessage().contains("本教程配方或结构基线已变，请重新核对，未用旧数值演示"));
        assertTrue(failure.getMessage().contains("scripts/recipe.zs"));
    }

    @Test
    void refusesMissingFilesAndDirectoriesInPlaceOfFiles() throws Exception {
        Path file = write("recipe.zs", "original");
        Map<String, String> expected = expected("recipe.zs", file);
        Files.delete(file);
        assertThrows(IllegalStateException.class, () -> OilLinePackBaseline.verify(directory, expected));
        Files.createDirectory(file);
        assertThrows(IllegalStateException.class, () -> OilLinePackBaseline.verify(directory, expected));
    }

    @Test
    void rejectsOrdinaryFilesOverEightMebibytesBeforeHashing() throws Exception {
        Path file = sizedFile("scripts/large.zs", OilLinePackBaseline.MAX_STANDARD_FILE_BYTES + 1);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> OilLinePackBaseline.verify(directory, Collections.singletonMap("scripts/large.zs", zeroHash())));
        assertTrue(failure.getMessage().contains("大小"));
        assertEquals(OilLinePackBaseline.MAX_STANDARD_FILE_BYTES + 1, Files.size(file));
    }

    @Test
    void allowsTheActualContentFileToExceedEightButNotSixtyFourMebibytes() throws Exception {
        Path file = sizedFile(OilLinePackBaseline.CONTENT_PATH, OilLinePackBaseline.MAX_STANDARD_FILE_BYTES + 1);
        Map<String, String> expected = expected(OilLinePackBaseline.CONTENT_PATH, file);
        assertDoesNotThrow(() -> OilLinePackBaseline.verify(directory, expected));
        sizedFile(OilLinePackBaseline.CONTENT_PATH, OilLinePackBaseline.MAX_CONTENT_FILE_BYTES + 1);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> OilLinePackBaseline.verify(directory, expected));
        assertTrue(failure.getMessage().contains("大小"));
    }

    @Test
    void rejectsDirectorySymlinksEscapingTheGameRootEvenForMatchingContent() throws Exception {
        Path root = Files.createDirectories(directory.resolve("game"));
        Path outside = Files.createDirectories(directory.resolve("outside"));
        Path file = Files.write(outside.resolve("recipe.zs"), "outside".getBytes(StandardCharsets.UTF_8));
        createSymlinkOrSkip(root.resolve("scripts"), outside);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> OilLinePackBaseline.verify(root, expected("scripts/recipe.zs", file)));
        assertTrue(failure.getMessage().contains("目录链接"));
    }

    @Test
    void rejectsFileSymlinksEscapingTheGameRoot() throws Exception {
        Path root = Files.createDirectories(directory.resolve("game"));
        Path file = write("outside.zs", "outside");
        createSymlinkOrSkip(root.resolve("recipe.zs"), file);
        assertThrows(IllegalStateException.class,
            () -> OilLinePackBaseline.verify(root, expected("recipe.zs", file)));
    }

    @Test
    void rejectsAbsoluteAndTraversalPathsFromInjectedManifests() throws Exception {
        Path root = Files.createDirectories(directory.resolve("game"));
        Path outside = write("outside.zs", "outside");
        assertThrows(IllegalStateException.class,
            () -> OilLinePackBaseline.verify(root, expected("../outside.zs", outside)));
        assertThrows(IllegalStateException.class,
            () -> OilLinePackBaseline.verify(root, expected(outside.toAbsolutePath().toString(), outside)));
    }

    @Test
    void productionManifestCannotSilentlyAcceptAnEmptyOrUnrelatedPack() throws Exception {
        assertThrows(IllegalStateException.class, () -> OilLinePackBaseline.verify(directory));
        assertThrows(IllegalStateException.class,
            () -> OilLinePackBaseline.verify(directory, Collections.<String, String>emptyMap()));
        assertThrows(IllegalStateException.class,
            () -> OilLinePackBaseline.verify(directory, Collections.singletonMap("recipe.zs", "invalid-hash")));
    }

    private Path write(String name, String contents) throws IOException {
        Path file = directory.resolve(name);
        Files.createDirectories(file.getParent());
        return Files.write(file, contents.getBytes(StandardCharsets.UTF_8));
    }

    private Path sizedFile(String name, long size) throws IOException {
        Path file = directory.resolve(name);
        Files.createDirectories(file.getParent());
        try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.CREATE,
            StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.position(size - 1);
            channel.write(ByteBuffer.wrap(new byte[1]));
        }
        return file;
    }

    private static Map<String, String> expected(String name, Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (InputStream input = Files.newInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : digest.digest()) hash.append(String.format("%02x", value & 255));
        return Collections.singletonMap(name, hash.toString());
    }

    private static String zeroHash() {
        return "0000000000000000000000000000000000000000000000000000000000000000";
    }

    private static void createSymlinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            assumeTrue(false, "Symbolic-link creation is unavailable: " + exception.getClass().getSimpleName());
        }
    }
}
