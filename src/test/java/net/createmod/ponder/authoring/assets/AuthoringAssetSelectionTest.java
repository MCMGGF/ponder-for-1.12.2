package net.createmod.ponder.authoring.assets;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.AuthoringEdits;
import net.createmod.ponder.foundation.PonderSchematic;
import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthoringAssetSelectionTest {
    Path fixture; StructurePool pool;
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    @BeforeEach void setup() throws Exception {
        fixture = Files.createTempDirectory(Files.createDirectories(Paths.get("build/test-fixtures/authoring-context").toAbsolutePath()), "case-");
        pool = new StructurePool(fixture.resolve("authoring"));
    }
    @AfterEach void cleanup() throws Exception {
        try (Stream<Path> paths = Files.walk(fixture)) { for (Path path : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.delete(path); }
    }
    JsonObject doc() { return AuthoringEdits.empty("example:selection").toJson(); }
    @Test void onlyKnownExplicitReferencesAreCollectedAndPoolIndicesMatchCompilerConvention() throws Exception {
        JsonObject root = doc(); root.addProperty("structure", "x:base"); JsonArray rootPool = new JsonArray(); rootPool.add("x:first"); rootPool.add("x:second"); rootPool.add("x:unused"); root.add("structures", rootPool);
        JsonObject extra = new JsonObject(); extra.addProperty("type", "show_extra_structure"); extra.addProperty("structure", 2);
        root.getAsJsonArray("scenes").get(0).getAsJsonObject().getAsJsonArray("steps").add(extra);
        root.addProperty("unrelatedText", "x:not_a_reference");
        assertEquals(Arrays.asList("x:base", "x:first", "x:second"), AuthoringAssetSelection.collectReferencedIds(AuthoringDocument.fromJson(root)));
    }
    @Test void contextLoadsOnlyReferencedAssetsAndDoesNotParseUnrelatedBrokenLibraryEntry() throws Exception {
        pool.saveNew("x:used", AuthoringAssetSafetyTest.schematic());
        new AuthoringAssetPaths(pool.root()).writeNew("structures/x/broken.nbt", new byte[] {1, 2, 3});
        JsonObject root = doc(); root.addProperty("structure", "x:used");
        Map<String, PonderSchematic> context = AuthoringAssetSelection.loadContext(pool, AuthoringDocument.fromJson(root));
        assertEquals(Collections.singleton("x:used"), context.keySet());
        assertThrows(UnsupportedOperationException.class, context::clear);
    }
    @Test void missingReferencesAndOutOfRangePoolIndicesAreExplicitFailuresWithoutRootCreation() throws Exception {
        JsonObject root = doc(); root.addProperty("structure", "x:missing");
        assertThrows(IOException.class, () -> AuthoringAssetSelection.loadContext(pool, AuthoringDocument.fromJson(root)));
        assertFalse(Files.exists(pool.root()));
        JsonObject index = doc(); JsonArray entries = new JsonArray(); entries.add("x:only"); index.add("structures", entries);
        JsonObject extra = new JsonObject(); extra.addProperty("type", "show_extra_structure"); extra.addProperty("structure", 5);
        index.getAsJsonArray("scenes").get(0).getAsJsonObject().getAsJsonArray("steps").add(extra);
        assertThrows(IOException.class, () -> AuthoringAssetSelection.collectReferencedIds(AuthoringDocument.fromJson(index)));
    }
    @Test void contextHasExplicitAssetCountAndTraversalBudgetsRatherThanFallingBackToLibraryScan() {
        JsonObject root = doc(); JsonArray scenes = new JsonArray();
        for (int i = 0; i < 65; i++) { JsonObject segment = new JsonObject(); segment.addProperty("id", "s" + i); segment.addProperty("structure", "x:a" + i); segment.add("steps", new JsonArray()); scenes.add(segment); }
        root.add("scenes", scenes);
        assertThrows(IOException.class, () -> AuthoringAssetSelection.collectReferencedIds(AuthoringDocument.fromJson(root)));
        JsonObject unsafe = doc(); unsafe.addProperty("structure", "x:../escape");
        assertThrows(IOException.class, () -> AuthoringAssetSelection.collectReferencedIds(AuthoringDocument.fromJson(unsafe)));
    }
}
