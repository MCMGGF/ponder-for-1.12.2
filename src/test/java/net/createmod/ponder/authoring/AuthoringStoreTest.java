package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthoringStoreTest {
    private Path fixture;
    private AuthoringStore store;
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    @BeforeEach void create() throws Exception {
        Path parent = Files.createDirectories(Paths.get("build", "test-fixtures", "authoring-store").toAbsolutePath().normalize());
        fixture = Files.createTempDirectory(parent, "case-"); store = new AuthoringStore(fixture.resolve("config"));
    }
    @AfterEach void cleanup() throws Exception {
        try (Stream<Path> walk = Files.walk(fixture)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.delete(path);
        }
    }
    private AuthoringDocument doc(int value) {
        JsonObject object = AuthoringModelTest.document(AuthoringModelTest.step("idle")).toJson(); object.addProperty("revision", value);
        return AuthoringDocument.fromJson(object);
    }

    @Test void draftCreateReadAndCasNeverOverwriteAnotherEditorsChanges() throws Exception {
        AuthoringStore.SavedDocument first = store.saveDraft(doc(0), null);
        assertEquals(first.getHash(), store.loadDraft("example:lesson").getHash());
        assertThrows(AuthoringStore.ConflictException.class, () -> store.saveDraft(doc(1), null));
        AuthoringStore.SavedDocument second = store.saveDraft(doc(1), first.getHash());
        assertThrows(AuthoringStore.ConflictException.class, () -> store.saveDraft(doc(2), first.getHash()));
        assertEquals(second.getHash(), store.loadDraft("example:lesson").getHash());
        assertEquals(java.util.Collections.singletonList("example:lesson"), store.listDraftIds());
    }

    @Test void historyIsBoundedAndAllFilesStayUnderFixedAuthoringRoot() throws Exception {
        String hash = null;
        for (int revision = 0; revision < 26; revision++) hash = store.saveDraft(doc(revision), hash).getHash();
        try (Stream<Path> files = Files.walk(store.getRoot().resolve("history"))) {
            assertEquals(20, files.filter(path -> path.toString().endsWith(".json")).count());
        }
        assertEquals(fixture.resolve("config/ponder_legacy/authoring"), store.getRoot());
        try (Stream<Path> files = Files.walk(store.getRoot())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test void historyReadDoesNotOverwriteDraftAndRejectsTraversal() throws Exception {
        String first = store.saveDraft(doc(0), null).getHash();
        String current = store.saveDraft(doc(1), first).getHash();
        List<String> versions = store.listHistory("example:lesson");
        assertEquals(1, versions.size());
        assertEquals(0, store.loadHistory("example:lesson", versions.get(0)).getDocument().toJson().get("revision").getAsInt());
        assertEquals(current, store.loadDraft("example:lesson").getHash());
        assertThrows(java.io.IOException.class, () -> store.loadHistory("example:lesson", "../../drafts/example/lesson.json"));
    }

    @Test void unknownStepsCanBeSavedButPublishDoesNotCreateOrReplaceAnyFile() throws Exception {
        AuthoringDocument unknown = AuthoringModelTest.document(AuthoringModelTest.step("future_unknown"));
        store.saveDraft(unknown, null);
        assertThrows(AuthoringCompiler.CompilationException.class, () -> store.publish(unknown, null, null));
        assertTrue(store.listPublishedIds().isEmpty());
        AuthoringStore.SavedDocument published = store.publish(doc(0), null, null);
        assertThrows(AuthoringCompiler.CompilationException.class, () -> store.publish(unknown, published.getHash(), null));
        assertEquals(published.getHash(), store.loadPublished("example:lesson").getHash());
    }

    @Test void traversalAndFileIdMismatchAreRejected() throws Exception {
        JsonObject object = doc(0).toJson(); object.addProperty("id", "example:../outside");
        assertThrows(java.io.IOException.class, () -> store.saveDraft(AuthoringDocument.fromJson(object), null));
        assertThrows(java.io.IOException.class, () -> store.loadDraft("example:/absolute"));
        assertThrows(java.io.IOException.class, () -> store.loadDraft("..:outside"));
        assertThrows(java.io.IOException.class, () -> store.loadDraft("example:con"));
        store.saveDraft(doc(0), null);
        Path stored = store.getRoot().resolve("drafts/example/lesson.json");
        JsonObject tampered = doc(0).toJson(); tampered.addProperty("id", "example:other");
        Files.write(stored, tampered.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(java.io.IOException.class, () -> store.loadDraft("example:lesson"));
    }

    @Test void undoRedoHasAnActualOneHundredEditLimitAndNewEditsClearRedo() {
        AuthoringSession session = new AuthoringSession(doc(0));
        for (int i = 1; i <= 130; i++) session.replace(doc(i));
        int count = 0; while (session.undo()) count++;
        assertEquals(100, count); assertEquals(30, session.getDocument().toJson().get("revision").getAsInt());
        assertTrue(session.redo()); session.replace(doc(999)); assertFalse(session.canRedo());
        assertTrue(session.undo()); assertEquals(31, session.getDocument().toJson().get("revision").getAsInt());
    }

    @Test void sessionsRememberSeparateDraftAndPublishedHashes() throws Exception {
        AuthoringSession first = new AuthoringSession(doc(0)); first.save(store); first.publish(store, null);
        AuthoringSession stale = AuthoringSession.load(store, "example:lesson");
        first.replace(doc(1)); first.save(store);
        stale.replace(doc(2)); assertThrows(AuthoringStore.ConflictException.class, () -> stale.save(store));
        assertFalse(AuthoringSession.load(store, "example:lesson").isDirty());
        assertEquals(1, store.loadDraft("example:lesson").getDocument().toJson().get("revision").getAsInt());
        assertEquals(0, store.loadPublished("example:lesson").getDocument().toJson().get("revision").getAsInt());
    }

    @Test void publicationRunsTheHandlerBeforeCreatingOrReplacingPublishedData() throws Exception {
        AuthoringCompiler compiler = new AuthoringCompiler(); compiler.registerHandler("offline_fails", new AuthoringCompiler.StepHandler() {
            public void validate(AuthoringDocument doc, com.google.gson.JsonObject step, String path, AuthoringValidation.Builder issues) { }
            public void apply(net.createmod.ponder.api.scene.SceneBuilder scene, net.createmod.ponder.api.scene.SceneBuildingUtil util,
                com.google.gson.JsonObject step, AuthoringCompiler.StepContext context) { throw new IllegalStateException("Dryrun failure"); }
        });
        AuthoringDocument broken = AuthoringModelTest.document(AuthoringModelTest.step("offline_fails"));
        assertFalse(compiler.validate(broken, null).hasErrors());
        assertThrows(AuthoringCompiler.CompilationException.class, () -> store.publish(broken, null, null, compiler));
        assertNull(store.loadPublished("example:lesson"));
        AuthoringStore.SavedDocument valid = store.publish(doc(0), null, null);
        assertThrows(AuthoringCompiler.CompilationException.class, () -> store.publish(broken, valid.getHash(), null, compiler));
        assertEquals(valid.getHash(), store.loadPublished("example:lesson").getHash());
    }

    @Test void expandedNbtFailureCannotReplaceAnExistingPublishedDocument() throws Exception {
        AuthoringStore.SavedDocument before = store.publish(doc(0), null, null);
        JsonObject patch = AuthoringModelTest.step("modify_block_entity_nbt"); patch.add("blockPos", AuthoringModelTest.pos(0, 0, 0)); patch.add("blockPos2", AuthoringModelTest.pos(29, 29, 0));
        char[] value = new char[20000]; java.util.Arrays.fill(value, 'x');
        patch.addProperty("nbt", "{Data:\"" + new String(value) + "\"}");
        assertThrows(AuthoringCompiler.CompilationException.class, () -> store.publish(AuthoringModelTest.document(patch), before.getHash(), null));
        assertEquals(before.getHash(), store.loadPublished("example:lesson").getHash());
    }
}
