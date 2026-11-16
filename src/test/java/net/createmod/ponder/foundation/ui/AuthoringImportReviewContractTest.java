package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import net.createmod.ponder.authoring.AuthoringDocument;
import org.junit.jupiter.api.Test;

class AuthoringImportReviewContractTest {
    private String source() throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/net/createmod/ponder/foundation/ui/AuthoringImportScreen.java")),StandardCharsets.UTF_8);
    }
    @Test void importScreenUsesTheRegisteredSchemaReviewerAndDoesNotPretendToPublish() throws Exception {
        String source=source();
        assertTrue(source.contains("AuthoringWorkspace.get().getCompiler().validateSchema(after)"));
        assertFalse(source.contains("AuthoringValidation.validate(after)"));
        assertTrue(source.contains("SCHEMA_ONLY - handler parameters and installed assets require full validation before publishing"));
        assertTrue(source.contains("review.getIssues()"));
        assertFalse(source.contains("getCompiler().compile(")); assertFalse(source.contains("getStore().publish("));
    }
    @Test void inspectionGettersReturnOnlyTheExactRenderedReviewAndResizeClearsStaleEvidence() throws Exception {
        String source=source();
        assertTrue(source.contains("lastRenderedReviewValidation=null; lastRenderedReviewText=null;"));
        assertTrue(source.contains("lastRenderedReviewValidation=review; lastRenderedReviewText=text;"));
        assertTrue(source.indexOf("for(String line:lines)")<source.indexOf("lastRenderedReviewValidation=review;"));
        assertTrue(source.contains("getLastRenderedReviewValidation() { return lastRenderedReviewValidation; }"));
        assertTrue(source.contains("getLastRenderedReviewText() { return lastRenderedReviewText; }"));
    }
    @Test void unrenderedImportScreenCannotClaimRenderedValidationOrText() {
        AuthoringDocument document=AuthoringDocument.parse("{\"id\":\"local:review\",\"items\":[\"minecraft:stone\"],\"scenes\":[{\"id\":\"main\",\"steps\":[]}]}");
        AuthoringImportScreen screen=new AuthoringImportScreen(null,document,document,ignored -> {});
        assertNull(screen.getLastRenderedReviewValidation()); assertNull(screen.getLastRenderedReviewText());
    }
}
