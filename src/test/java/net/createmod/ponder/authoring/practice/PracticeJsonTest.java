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
import net.createmod.ponder.authoring.practice.PracticeDefinition.Menu;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PracticeJsonTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void snapshotAndLegacyMenuIdsResolveWithoutCallingAnyGui() {
        PracticeDefinition definition = PracticeJson.fromStep(json("{\"type\":\"show_interface\",\"interface\":{\"target\":{\"kind\":\"block\",\"block\":\"minecraft:hopper\"},\"slots\":[{\"index\":0,\"item\":{\"id\":\"minecraft:paper\",\"count\":5,\"legacyNbt\":\"{test:2}\"}}],\"data\":[{\"key\":\"locked\",\"value\":1}]}}"));
        assertEquals(Menu.HOPPER, definition.getMenu());
        assertEquals(5, definition.getInitialSlots().get(0).getCount());
        assertEquals(2, definition.getInitialSlots().get(0).getTagCompound().getInteger("test"));
        assertEquals(1, definition.getData().get("locked"));
        assertEquals(Menu.READ_ONLY, PracticeJson.fromStep(json("{\"uiId\":\"mod:machine\"}")).getMenu());
    }

    @Test void upstreamOpeningBytesAreRejectedRatherThanSilentlyConverted() {
        for (String payload : new String[] {"\"AQID\"", "\"not base64\"", "\" \"", "3", "[]", "{}"}) {
            JsonObject step = json("{\"type\":\"show_interface\",\"interface\":{\"menuType\":\"minecraft:chest\",\"extraData\":" + payload + "}}");
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> PracticeJson.fromStep(step));
            assertTrue(failure.getMessage().contains("interface.extraData"));
            assertTrue(compiler().validate(document(step.toString()), null).hasErrors());
        }
        assertTrue(compiler().validate(document("{\"type\":\"show_interface\",\"uiId\":\"minecraft:chest\"},"
            + "{\"type\":\"hide_interface\",\"interface\":{\"extraData\":\"AQID\"}}"), null).hasErrors());
    }
    @Test void virtualMenuScenePointIsAnAbsoluteForegroundAnchorNotASourceBlock() {
        String step="{\"type\":\"show_interface\",\"uiId\":\"minecraft:chest\",\"point\":[1.5,0.5,0.5],\"pointSpace\":\"scene\"}";
        PonderScene.OverlayEvent event=compiler().compile(document(step),null).get(0).getOverlayEvents().get(0);
        assertTrue(event.isScenePoint());assertTrue(event.getTargetPositions().isEmpty());assertEquals(1.5,event.getPointAt().x);
        assertTrue(compiler().validate(document(step.replace("[1.5,0.5,0.5]","[1,2]")),null).hasErrors());
    }

    @Test void emptyOpeningDescriptorsRemainCompatibleAndSourceMetadataIsNotExecutableData() {
        for (String extra : new String[] {"", ",\"extraData\":null", ",\"extraData\":\"\""}) {
            JsonObject step = json("{\"type\":\"show_interface\",\"interface\":{\"menuType\":\"minecraft:chest\"" + extra
                + ",\"lookupOrigin\":[1,2,3],\"resolvedStrategy\":\"client_factory\"},"
                + "\"source\":{\"extraData\":\"historical metadata only\"}}");
            assertEquals(Menu.CHEST, PracticeJson.fromStep(step).getMenu());
            assertFalse(compiler().validate(document(step.toString()), null).hasErrors());
        }
    }

    @Test void openingPayloadCanStayInADraftButCannotCreateOrReplaceAPublishedScene() throws Exception {
        Path parent = Files.createDirectories(Paths.get("build", "test-fixtures", "practice-opening").toAbsolutePath().normalize());
        Path fixture = Files.createTempDirectory(parent, "case-");
        try {
            AuthoringCompiler compiler = compiler();
            AuthoringStore store = new AuthoringStore(fixture.resolve("config"), compiler);
            AuthoringDocument opaque = document("{\"type\":\"show_interface\",\"interface\":{\"menuType\":\"minecraft:chest\",\"extraData\":\"AQID\"}}");
            String draftHash = store.saveDraft(opaque, null).getHash();
            assertEquals("AQID", store.loadDraft("test:practice").getDocument().getScenes().get(0)
                .getAsJsonArray("steps").get(0).getAsJsonObject().getAsJsonObject("interface").get("extraData").getAsString());
            assertThrows(AuthoringCompiler.CompilationException.class, () -> store.publish(opaque, null, null));
            assertTrue(store.listPublishedIds().isEmpty());
            String publishedHash = store.publish(document("{\"type\":\"show_interface\",\"uiId\":\"minecraft:chest\"}"), null, null).getHash();
            assertThrows(AuthoringCompiler.CompilationException.class, () -> store.publish(opaque, publishedHash, null));
            assertEquals(publishedHash, store.loadPublished("test:practice").getHash());
            assertEquals(draftHash, store.loadDraft("test:practice").getHash());
        } finally {
            try (Stream<Path> walk = Files.walk(fixture)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.delete(path);
            }
        }
    }

    @Test void virtualContextNeverRequestsActualPlayerInventory() {
        PracticeDefinition definition = PracticeJson.fromStep(json("{\"uiId\":\"minecraft:chest\",\"interface\":{\"player\":{\"inventory\":{\"items\":[{\"slot\":0,\"item\":\"minecraft:paper\"}]}}}}"));
        assertEquals(Items.PAPER, definition.getInitialSlots().get(27).getItem());
        assertFalse(definition.getInitialSlots().containsKey(0));
    }

    @Test void declarativeActionsAndGoalsCompileAndSeekWithoutGui() {
        AuthoringDocument document = document("{\"type\":\"show_interface\",\"uiId\":\"minecraft:chest\",\"slots\":[{\"index\":0,\"item\":{\"id\":\"minecraft:paper\",\"count\":4}}]},"
            + "{\"type\":\"interface_action\",\"action\":\"click\",\"target\":{\"slot\":0},\"duration\":10},"
            + "{\"type\":\"interface_action\",\"action\":\"right\",\"target\":{\"slot\":1},\"duration\":10},"
            + "{\"type\":\"await_interface\",\"goal\":{\"slot\":1,\"item\":\"minecraft:paper\",\"minCount\":1}}");
        AuthoringCompiler compiler = compiler();
        assertFalse(compiler.validate(document, null).hasErrors(), () -> compiler.validate(document, null).getIssues().toString());
        PonderScene scene = compiler.compile(document, null).get(0);
        assertEquals(1, PracticeStepHandlers.getDefinitions(scene).size());
        PracticeDefinition definition = PracticeStepHandlers.getDefinitions(scene).get(0);
        assertEquals(2, definition.getActions().size());
        PracticeSession session = new PracticeSession(definition, PracticeMode.WATCH);
        session.seek(20); assertEquals(1, session.getSlot(1).getCount()); assertTrue(session.isComplete());
        session.seek(5); assertFalse(session.isComplete());
    }

    @Test void allNineHandlersAreExplicitAndDoNotReplaceCoreHandlers() {
        AuthoringCompiler compiler = compiler();
        assertThrows(IllegalArgumentException.class, () -> PracticeStepHandlers.register(compiler));
        AuthoringDocument document = document("{\"type\":\"show_interface\",\"uiId\":\"minecraft:chest\"},"
            + "{\"type\":\"set_interface_slots\",\"slots\":[{\"index\":0,\"item\":\"minecraft:paper\"}]},"
            + "{\"type\":\"change_interface_slot\",\"interfaceSlots\":[{\"slotIndex\":0,\"ingredientId\":\"minecraft:paper\"}]},"
            + "{\"type\":\"click_interface\",\"pos\":[0.1,0.1],\"action\":\"left\"},"
            + "{\"type\":\"set_interface_data\",\"data\":[{\"key\":\"locked\",\"value\":1}]},"
            + "{\"type\":\"set_interface_context\",\"player\":{\"inventory\":{\"mode\":\"empty\"}}},"
            + "{\"type\":\"interface_action\",\"action\":\"click\",\"target\":{\"slot\":0}},"
            + "{\"type\":\"await_interface\",\"goal\":{\"kind\":\"cursor_empty\"}},"
            + "{\"type\":\"hide_interface\"}");
        assertFalse(compiler.validate(document, null).hasErrors(), () -> compiler.validate(document, null).getIssues().toString());
        assertEquals(1, PracticeStepHandlers.getDefinitions(compiler.compile(document, null).get(0)).size());
    }

    @Test void arbitraryCallbacksInvalidItemsAndUnsupportedActionsAreRejected() {
        assertTrue(compiler().validate(document("{\"type\":\"show_interface\",\"uiId\":\"minecraft:chest\"},"
            + "{\"type\":\"interface_action\",\"action\":\"run_callback\",\"target\":{\"slot\":0}}"), null).hasErrors());
        assertThrows(IllegalArgumentException.class, () -> PracticeJson.fromStep(json("{\"uiId\":\"minecraft:chest\",\"slots\":[{\"index\":0,\"item\":\"missing:item\"}]}")));
        assertThrows(IllegalArgumentException.class, () -> PracticeJson.fromStep(json("{\"uiId\":\"minecraft:chest\",\"slots\":[{\"index\":0,\"item\":{\"id\":\"minecraft:paper\",\"components\":{}}}]}")));
        assertTrue(compiler().validate(document("{\"type\":\"show_interface\",\"uiId\":\"minecraft:chest\"},"
            + "{\"type\":\"set_interface_slots\",\"ghosts\":[{\"index\":0,\"ingredient\":{\"kind\":\"fluid\",\"id\":\"minecraft:water\"}}]}"), null).hasErrors());
    }

    @Test void mutationBeforeOpenAndWrongMenuSlotAreValidationErrors() {
        assertTrue(compiler().validate(document("{\"type\":\"hide_interface\"}"), null).hasErrors());
        assertTrue(compiler().validate(document("{\"type\":\"show_interface\",\"uiId\":\"minecraft:furnace\"},"
            + "{\"type\":\"set_interface_slots\",\"slots\":[{\"index\":39,\"item\":\"minecraft:paper\"}]}"), null).hasErrors());
    }

    @Test void twoMenusKeepSeparateDefinitionsAndUnknownMenuNeverCompletesPractice() {
        AuthoringDocument document = document("{\"type\":\"show_interface\",\"uiId\":\"minecraft:chest\"},"
            + "{\"type\":\"hide_interface\"},"
            + "{\"type\":\"show_interface\",\"uiId\":\"mod:unknown\",\"goal\":{\"kind\":\"cursor_empty\"}}");
        PonderScene scene = compiler().compile(document, null).get(0);
        assertEquals(2, PracticeStepHandlers.getDefinitions(scene).size());
        PracticeSession session = new PracticeSession(PracticeStepHandlers.getDefinitions(scene).get(1), PracticeMode.PRACTICE);
        assertFalse(session.isComplete());
        assertThrows(UnsupportedOperationException.class, () -> PracticeStepHandlers.getDefinitions(scene).clear());
    }

    @Test void everyVirtualSlotHasStableNonoverlappingHitCoordinates() {
        for (Menu menu : Menu.values()) {
            for (int slot = 0; slot < menu.slots + 36; slot++) {
                int x = PracticeLayout.x(menu, slot), y = PracticeLayout.y(menu, slot);
                assertTrue(x >= 0 && x + 18 <= PracticeLayout.WIDTH);
                assertTrue(y >= 0 && y + 18 <= PracticeLayout.height(menu));
                assertEquals(slot, PracticeLayout.hit(menu, x + 9, y + 9));
                for (int other = 0; other < slot; other++) {
                    int ox = PracticeLayout.x(menu, other), oy = PracticeLayout.y(menu, other);
                    assertTrue(x + 18 <= ox || ox + 18 <= x || y + 18 <= oy || oy + 18 <= y,
                        menu + " slots " + slot + " and " + other + " overlap");
                }
            }
        }
        assertEquals(-1, PracticeLayout.hit(Menu.CHEST, -1, 0));
    }

    @Test void logical320AndLargerViewportsLeaveControlsAndTitleOutsideInventory() {
        for (int[] size : new int[][] {{320, 240}, {640, 360}, {960, 540}, {1920, 1080}}) {
            PracticeLayout.Viewport legacy = PracticeLayout.fit(size[0], size[1]);
            PracticeLayout.Viewport chest = PracticeLayout.fit(size[0], size[1], Menu.CHEST);
            assertEquals(chest.left, legacy.left); assertEquals(chest.top, legacy.top); assertEquals(chest.scale, legacy.scale);
            for (Menu menu : Menu.values()) {
                PracticeLayout.Viewport viewport = PracticeLayout.fit(size[0], size[1], menu);
                assertTrue(viewport.scale > 0);
                assertTrue(viewport.left >= 12);
                assertTrue(viewport.left + PracticeLayout.WIDTH * viewport.scale <= size[0] - 11);
                assertTrue(viewport.top >= 47);
                assertTrue(viewport.top + PracticeLayout.height(menu) * viewport.scale <= size[1] - 60 + 0.01F);
                for (int slot = 0; slot < menu.slots + 36; slot++) {
                    double screenX = viewport.left + (PracticeLayout.x(menu, slot) + 9) * viewport.scale;
                    double screenY = viewport.top + (PracticeLayout.y(menu, slot) + 9) * viewport.scale;
                    assertEquals(slot, PracticeLayout.hit(menu, (screenX - viewport.left) / viewport.scale,
                        (screenY - viewport.top) / viewport.scale));
                }
            }
        }
    }

    private static AuthoringCompiler compiler() { AuthoringCompiler compiler = new AuthoringCompiler(); PracticeStepHandlers.register(compiler); return compiler; }
    private static AuthoringDocument document(String steps) {
        return AuthoringDocument.parse("{\"id\":\"test:practice\",\"items\":[\"minecraft:paper\"],\"scenes\":[{\"id\":\"main\",\"steps\":[" + steps + "]}]}");
    }
    private static JsonObject json(String value) { return new JsonParser().parse(value).getAsJsonObject(); }
}
