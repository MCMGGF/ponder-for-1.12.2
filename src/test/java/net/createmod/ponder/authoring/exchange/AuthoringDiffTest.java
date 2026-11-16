package net.createmod.ponder.authoring.exchange;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.AuthoringEdits;
import org.junit.jupiter.api.Test;

class AuthoringDiffTest {
    private AuthoringDocument doc() { return AuthoringEdits.empty("example:review"); }

    @Test void comparisonReportsActualJsonPointerAddRemoveAndChangedStepValues() {
        JsonObject before = doc().toJson(), after = doc().toJson();
        before.addProperty("remove", "old"); after.addProperty("a/b~c", "new");
        after.getAsJsonArray("scenes").get(0).getAsJsonObject().getAsJsonArray("steps").get(0).getAsJsonObject().addProperty("duration", 80);
        String diff = AuthoringDiff.compare(AuthoringDocument.fromJson(before), AuthoringDocument.fromJson(after));
        assertTrue(diff.contains("/scenes/0/steps/0/duration : 20 -> 80"));
        assertTrue(diff.contains("ADD /a~1b~0c")); assertTrue(diff.contains("REMOVE /remove")); assertTrue(diff.contains("Comparison complete"));
        assertTrue(diff.contains("Before SHA256: ")); assertTrue(diff.contains("After SHA256: "));
    }
    @Test void identicalDocumentsAndJsonNullAreNotConfusedWithMissingProperties() {
        assertTrue(AuthoringDiff.compare(doc(), doc()).contains("No JSON value changes"));
        JsonObject after = doc().toJson(); after.add("nullable", JsonNull.INSTANCE);
        String diff = AuthoringDiff.compare(doc(), AuthoringDocument.fromJson(after));
        assertTrue(diff.contains("ADD /nullable : <missing> -> null"));
    }
    @Test void differenceBudgetIsExplicitAndNeverClaimsFullComparisonAfterTruncation() {
        JsonObject before = doc().toJson(), after = doc().toJson(); JsonArray left = new JsonArray(), right = new JsonArray();
        for (int i = 0; i < 5000; i++) { left.add(0); right.add(1); }
        before.add("many", left); after.add("many", right);
        String diff = AuthoringDiff.compare(AuthoringDocument.fromJson(before), AuthoringDocument.fromJson(after));
        assertTrue(diff.contains("Listed differences: 4096")); assertTrue(diff.contains("TRUNCATED"));
        assertFalse(diff.contains("Comparison complete")); assertTrue(diff.length() < AuthoringDiff.MAX_OUTPUT_CHARS + 1024);
    }
    @Test void longValuesAreClearlyMarkedPreviewsWithoutLosingOriginalDocuments() {
        JsonObject changed = doc().toJson(); char[] many = new char[100000]; java.util.Arrays.fill(many, 'x'); changed.addProperty("large", new String(many));
        AuthoringDocument after = AuthoringDocument.fromJson(changed); String hash = after.sha256();
        String diff = AuthoringDiff.compare(doc(), after);
        assertTrue(diff.contains("[value shortened]")); assertTrue(diff.length() < 2000); assertEquals(hash, after.sha256());
        assertEquals(100000, after.toJson().get("large").getAsString().length());
    }
}
