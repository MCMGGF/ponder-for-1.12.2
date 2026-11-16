package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class AuthoringActorExpansionBudgetTest {
    @Test void repeatedMergedActorSnapshotsAreChargedBeyondTheSmallDeclaredPatch() {
        List<JsonObject> steps = new ArrayList<JsonObject>();
        String payload = "{Custom:\"" + repeat('x', 24000) + "\"}";
        for (int index = 0; index < 32; index++) steps.add(item(payload));
        for (int index = 0; index < 32; index++) {
            JsonObject patch = AuthoringModelTest.step("modify_item_entities_nbt");
            patch.addProperty("nbt", "{Flag:1b}");
            steps.add(patch);
        }
        AuthoringDocument document = AuthoringModelTest.document(steps.toArray(new JsonObject[0]));
        assertDoesNotThrow(() -> AuthoringNbtBudget.checkDeclaredNbt(document));
        AuthoringNbtBudget.LimitException error = assertThrows(AuthoringNbtBudget.LimitException.class,
            () -> expanded(document));
        assertTrue(error.getMessage().contains("16 MiB"));
    }

    @Test void metadataFreeMassActorActionsStillHaveAFiniteTimelineBudget() {
        List<JsonObject> steps = new ArrayList<JsonObject>();
        for (int index = 0; index < 256; index++) steps.add(item(null));
        for (int index = 0; index < 32; index++) steps.add(AuthoringModelTest.step("modify_item_entities_nbt"));
        AuthoringNbtBudget.LimitException error = assertThrows(AuthoringNbtBudget.LimitException.class,
            () -> expanded(AuthoringModelTest.document(steps.toArray(new JsonObject[0]))));
        assertTrue(error.getMessage().contains("65536"));
    }

    @Test void itemEventsChargeBothSerializedAndIndependentStackSnapshots() {
        List<JsonObject> steps = new ArrayList<JsonObject>();
        for (int index = 0; index < 80; index++) steps.add(item("{Initial:\"" + repeat('x', 10000) + "\"}"));
        for (int index = 0; index < 2; index++) {
            JsonObject patch = AuthoringModelTest.step("modify_item_entities_nbt");
            patch.addProperty("fullScene", true); patch.addProperty("nbt", "{Patch:\"" + repeat('y', 40000) + "\"}");
            steps.add(patch);
        }
        AuthoringDocument document = AuthoringModelTest.document(steps.toArray(new JsonObject[0]));
        assertDoesNotThrow(() -> AuthoringNbtBudget.checkDeclaredNbt(document));
        assertTrue(assertThrows(AuthoringNbtBudget.LimitException.class, () -> expanded(document)).getMessage().contains("16 MiB"));
    }

    @Test void smallVisualActorEditsAndNativePatchesRemainWithinBudget() {
        JsonObject patch = AuthoringModelTest.step("modify_item_entities_nbt"); patch.addProperty("nbt", "{Flag:1b}");
        JsonObject nativePatch = AuthoringModelTest.step("actor_action");
        nativePatch.addProperty("action", "NBT_PATCH"); nativePatch.addProperty("kind", "ITEM");
        nativePatch.addProperty("nbt", "{Flag:2b}");
        assertDoesNotThrow(() -> expanded(AuthoringModelTest.document(item("{Custom:1}"), patch, nativePatch)));
    }

    @Test void itemBindingFiltersParticipateInTheDeclaredNbtBudget() {
        JsonObject root = AuthoringModelTest.document().toJson();
        root.addProperty("nbtFilter", "{Custom:\"" + repeat('x', 70000) + "\"}");
        assertThrows(IllegalArgumentException.class,
            () -> AuthoringNbtBudget.checkDeclaredNbt(AuthoringDocument.fromJson(root)));
    }

    private static void expanded(AuthoringDocument document) {
        AuthoringNbtBudget.check(document, new AuthoringCompiler.CachedAssets(null),
            new HashSet<String>(Arrays.asList("modify_item_entities_nbt", "modify_entities_nbt")));
    }

    private static JsonObject item(String nbt) {
        JsonObject step = AuthoringModelTest.step("create_item_entity");
        step.addProperty("item", "minecraft:stone");
        if (nbt != null) step.addProperty("nbt", nbt);
        return step;
    }

    private static String repeat(char value, int count) {
        char[] result = new char[count]; Arrays.fill(result, value); return new String(result);
    }
}
