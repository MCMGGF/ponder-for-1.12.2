package net.createmod.ponder.authoring.practice;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.createmod.ponder.authoring.AuthoringCompiler;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.AuthoringStore;
import net.createmod.ponder.authoring.AuthoringValidation;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Action;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Builder;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Operation;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PracticeSnapshotRenderer;
import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PracticeDataTweenJsonTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void upstreamIdFromToCompilesOneBoundedActionRatherThanPerTickWrites() {
        Action action = action("{\"id\":2,\"from\":0,\"to\":100}", ",\"duration\":4", 7);
        assertEquals(Operation.DATA_TWEEN, action.operation);
        assertEquals("cookTime", action.key); assertEquals(7, action.tick);
        assertEquals(0, action.from); assertEquals(100, action.value); assertEquals(4, action.duration);
    }

    @Test void allFourExplicitNumericIdsResolveToTheVanillaFurnaceDataKeys() {
        String[] keys = {"burnTime", "fuelTime", "cookTime", "cookTotal"};
        for (int i = 0; i < keys.length; i++) assertEquals(keys[i], action("{\"id\":" + i + ",\"to\":9}", "", 0).key);
    }

    @Test void fromDefaultsToTheTargetNotTheCurrentDataAndDurationDefaultsToOne() {
        Builder builder = PracticeDefinition.builder("minecraft:chest").data("cookTime", 600);
        append(builder, "{\"id\":2,\"to\":80}", "", 0);
        Action action = builder.build().getActions().get(0);
        assertEquals(80, action.from); assertEquals(1, action.duration);
        assertEquals(80, new PracticeSession(builder.build(), PracticeMode.WATCH).getData("cookTime"));
    }

    @Test void toTakesPrecedenceOverValueForTheUpstreamRoute() {
        Action action = action("{\"id\":2,\"from\":0,\"value\":70,\"to\":100}", "", 0);
        assertEquals(100, action.value);
        assertEquals(70, action("{\"id\":2,\"value\":70}", "", 0).value);
    }

    @Test void explicitZeroDurationUsesTheSameOneSampleContractAsUpstream() {
        Action action = action("{\"id\":2,\"from\":0,\"to\":70}", ",\"duration\":0", 0);
        assertEquals(1, action.duration);
        assertEquals(70, new PracticeSession(PracticeDefinition.builder("minecraft:chest").action(action).build(), PracticeMode.WATCH)
            .getData("cookTime"));
    }

    @Test void legacyLocalKeyValueRemainsInstantEvenWithAnOldDurationField() {
        Action action = action("{\"key\":\"cookTime\",\"value\":70}", ",\"duration\":400", 0);
        assertEquals(Operation.SET_DATA, action.operation); assertEquals(0, action.duration);
        assertEquals(70, action.value);
    }

    @Test void explicitLocalKeysCanOptIntoInterpolationWithoutNumericIds() {
        assertEquals(Operation.DATA_TWEEN, action("{\"key\":\"locked\",\"from\":0,\"to\":1}", ",\"duration\":4", 0).operation);
        assertEquals(Operation.DATA_TWEEN, action("{\"key\":\"cookTime\",\"from\":0,\"value\":40}", ",\"duration\":4", 0).operation);
        assertEquals(40, action("{\"key\":\"cookTime\",\"to\":40}", "", 0).from);
    }

    @Test void matchingKeyAndIdAreAllowedButConflictingTargetsAreRejected() {
        assertEquals("cookTime", action("{\"key\":\"cookTime\",\"id\":2,\"to\":40}", "", 0).key);
        assertThrows(IllegalArgumentException.class, () -> action("{\"key\":\"burnTime\",\"id\":2,\"to\":40}", "", 0));
    }

    @Test void missingNullUnknownOrMalformedIdentifiersCannotBecomeAnotherDataSlot() {
        for (String entry : new String[] {"{\"to\":1}", "{\"key\":\"\",\"to\":1}", "{\"key\":null,\"to\":1}",
            "{\"id\":null,\"to\":1}", "{\"id\":-1,\"to\":1}", "{\"id\":4,\"to\":1}",
            "{\"id\":1.5,\"to\":1}", "{\"id\":\"2\",\"to\":1}", "{\"key\":7,\"to\":1}",
            "{\"key\":\"unknown\",\"to\":1}"}) {
            assertThrows(IllegalArgumentException.class, () -> action(entry, "", 0), entry);
        }
    }

    @Test void tweenNumbersRejectFractionsNullStringsNegativeAndExcessiveValues() {
        for (String invalid : new String[] {"null", "1.5", "\"5\"", "-1", "100001", "true", "{}", "[]"}) {
            assertThrows(IllegalArgumentException.class, () -> action("{\"id\":2,\"to\":" + invalid + "}", "", 0), invalid);
            assertThrows(IllegalArgumentException.class, () -> action("{\"id\":2,\"from\":" + invalid + ",\"to\":5}", "", 0), invalid);
        }
    }

    @Test void malformedShadowValueIsNotSilentlyHiddenByAValidTo() {
        assertThrows(IllegalArgumentException.class, () -> action("{\"id\":2,\"to\":5,\"value\":1.5}", "", 0));
        assertThrows(IllegalArgumentException.class, () -> action("{\"id\":2}", "", 0));
        assertThrows(IllegalArgumentException.class, () -> action("{\"id\":2,\"from\":1}", "", 0));
    }

    @Test void unknownDataEntryFieldsAndInitialTweenFieldsRequireExplicitConversion() {
        assertThrows(IllegalArgumentException.class, () -> action("{\"id\":2,\"to\":5,\"easing\":\"linear\"}", "", 0));
        assertThrows(IllegalArgumentException.class, () -> PracticeJson.fromStep(json(
            "{\"uiId\":\"minecraft:furnace\",\"interface\":{\"data\":[{\"id\":2,\"from\":0,\"to\":5}]}}")));
        assertEquals(7, PracticeJson.fromStep(json(
            "{\"uiId\":\"minecraft:furnace\",\"interface\":{\"data\":[{\"id\":2,\"value\":7}]}}"))
            .getData().get("cookTime"));
    }

    @Test void newTweenDurationIsStrictAndIncludesItsRelativeStartInTheEnvelope() {
        for (String invalid : new String[] {"-1", "24001", "1.5", "null", "\"4\"", "true"})
            assertThrows(IllegalArgumentException.class, () -> action("{\"id\":2,\"to\":5}", ",\"duration\":" + invalid, 0), invalid);
        assertEquals(23999, action("{\"id\":2,\"to\":5}", "", 23999).tick);
        assertThrows(IllegalArgumentException.class, () -> action("{\"id\":2,\"to\":5}", ",\"duration\":2", 23999));
    }

    @Test void compilerExtendsProviderLifetimeWithoutAddingIdleOrExpandingActions() {
        PonderScene scene = compiler().compile(document("{\"type\":\"show_interface\",\"uiId\":\"minecraft:chest\",\"duration\":1},"
            + "{\"type\":\"set_interface_data\",\"duration\":4,\"data\":[{\"id\":2,\"from\":0,\"to\":100}]}"), null).get(0);
        PracticeDefinition definition = PracticeStepHandlers.getDefinitions(scene).get(0);
        assertEquals(0, scene.getTotalIdleTicks()); assertEquals(4, definition.getDuration());
        assertEquals(4, scene.getOverlayEvents().get(0).getDuration());
        assertEquals(1, definition.getActions().size());
        PracticeSession session = new PracticeSession(definition, PracticeMode.WATCH);
        session.seek(3); assertEquals(100, session.getData("cookTime"));
    }

    @Test void compileAndPublishRejectALateTweenPastTheBoundWithoutReplacingPublishedData() throws Exception {
        Path parent = Files.createDirectories(Paths.get("build", "test-fixtures", "practice-data-tween").toAbsolutePath().normalize());
        Path fixture = Files.createTempDirectory(parent, "case-");
        try {
            AuthoringCompiler compiler = compiler();
            AuthoringStore store = new AuthoringStore(fixture.resolve("config"), compiler);
            AuthoringDocument valid = document("{\"type\":\"show_interface\",\"uiId\":\"minecraft:chest\"}");
            String publishedHash = store.publish(valid, null, null).getHash();
            AuthoringDocument late = document("{\"type\":\"show_interface\",\"uiId\":\"minecraft:chest\"},"
                + "{\"type\":\"idle\",\"duration\":23999},{\"type\":\"set_interface_data\",\"duration\":2,\"data\":[{\"id\":2,\"to\":50}]}");
            assertThrows(AuthoringCompiler.CompilationException.class, () -> compiler.compile(late, null));
            String draftHash = store.saveDraft(late, null).getHash();
            assertThrows(AuthoringCompiler.CompilationException.class, () -> store.publish(late, publishedHash, null));
            assertEquals(publishedHash, store.loadPublished("test:data_tween").getHash());
            assertEquals(draftHash, store.loadDraft("test:data_tween").getHash());
        } finally {
            try (Stream<Path> walk = Files.walk(fixture)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.delete(path);
            }
        }
    }

    @Test void capabilityExplainsTheConvertedTimelineInsteadOfAnOpaqueGenericExtension() {
        AuthoringValidation.Capability capability = compiler().getCapabilities().get("set_interface_data");
        assertEquals(AuthoringValidation.Status.CONVERTED, capability.getStatus());
        assertTrue(capability.getReason().contains("duration/from/to"));
        assertTrue(capability.getReason().contains("legacy key/value is instant"));
        assertTrue(capability.getReason().contains("no real menu writes"));
    }

    @Test void snapshotResetClosesItsTweenSessionAndRebuildsItForTheNextPlayback() throws Exception {
        PonderScene scene = compiler().compile(document("{\"type\":\"show_interface\",\"uiId\":\"minecraft:chest\"},"
            + "{\"type\":\"set_interface_data\",\"duration\":8,\"data\":[{\"id\":2,\"from\":0,\"to\":80}]}"), null).get(0);
        PracticeSnapshotRenderer provider = new PracticeSnapshotRenderer(scene, 0, 0);
        java.lang.reflect.Field field = PracticeSnapshotRenderer.class.getDeclaredField("session");
        field.setAccessible(true);
        assertNotNull(provider.provide(2));
        PracticeSession previous = (PracticeSession) field.get(provider);
        assertEquals(30, previous.getData("cookTime"));
        provider.reset();
        assertTrue(previous.isClosed()); assertTrue(previous.getData().isEmpty()); assertNull(field.get(provider));
        assertNotNull(provider.provide(0));
        PracticeSession current = (PracticeSession) field.get(provider);
        assertNotSame(previous, current); assertEquals(10, current.getData("cookTime"));
        provider.reset(); assertTrue(current.isClosed());
    }

    private static Action action(String entry, String extra, int tick) {
        Builder builder = PracticeDefinition.builder("minecraft:chest").duration(0);
        append(builder, entry, extra, tick);
        PracticeDefinition definition = builder.build();
        assertEquals(1, definition.getActions().size());
        return definition.getActions().get(0);
    }
    private static void append(Builder builder, String entry, String extra, int tick) {
        PracticeJson.append(builder, null, json("{\"type\":\"set_interface_data\",\"data\":[" + entry + "]" + extra + "}"), tick);
    }
    private static AuthoringCompiler compiler() {
        AuthoringCompiler compiler = new AuthoringCompiler(); PracticeStepHandlers.register(compiler); return compiler;
    }
    private static AuthoringDocument document(String steps) {
        return AuthoringDocument.parse("{\"id\":\"test:data_tween\",\"items\":[\"minecraft:paper\"],\"scenes\":[{\"id\":\"main\",\"steps\":[" + steps + "]}]}");
    }
    private static JsonObject json(String value) { return new JsonParser().parse(value).getAsJsonObject(); }
}
