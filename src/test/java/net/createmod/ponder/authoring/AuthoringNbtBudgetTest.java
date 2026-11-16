package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSchematic;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuthoringNbtBudgetTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    private String text(int count) { char[] value = new char[count]; java.util.Arrays.fill(value, 'x'); return new String(value); }
    private NBTTagCompound tag(int chars) { NBTTagCompound tag = new NBTTagCompound(); tag.setString("Data", text(chars)); return tag; }
    private PonderSchematic asset(int tiles, int chars) {
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<BlockPos, IBlockState>(); Map<BlockPos, NBTTagCompound> nbt = new LinkedHashMap<BlockPos, NBTTagCompound>();
        for (int i = 0; i < tiles; i++) { BlockPos pos = new BlockPos(i, 0, 0); blocks.put(pos, Blocks.CHEST.getDefaultState()); nbt.put(pos, tag(chars)); }
        return PonderSchematic.of(new BlockPos(tiles, 1, 1), blocks, nbt);
    }
    private JsonObject extra(int offset) {
        JsonObject step = AuthoringModelTest.step("show_extra_structure"); step.addProperty("structure", "local:tile"); step.add("blockPos", AuthoringModelTest.pos(offset, 0, 0)); return step;
    }
    private AuthoringCompiler marker(AtomicInteger applied) {
        return new AuthoringCompiler().registerHandler("offline_marker", new AuthoringCompiler.StepHandler() {
            public void validate(AuthoringDocument document, JsonObject step, String path, AuthoringValidation.Builder issues) { }
            public void apply(net.createmod.ponder.api.scene.SceneBuilder scene, net.createmod.ponder.api.scene.SceneBuildingUtil util, JsonObject step, AuthoringCompiler.StepContext context) { applied.incrementAndGet(); }
        });
    }

    @Test void targetMultiplicationRejectsBeforeAnyHandlerOrBuilderExpansion() {
        JsonObject patch = AuthoringModelTest.step("modify_block_entity_nbt"); patch.add("blockPos", AuthoringModelTest.pos(0, 0, 0)); patch.add("blockPos2", AuthoringModelTest.pos(29, 29, 0));
        patch.addProperty("nbt", tag(20000).toString());
        AtomicInteger applied = new AtomicInteger(); AuthoringCompiler compiler = marker(applied);
        AuthoringDocument doc = AuthoringModelTest.document(AuthoringModelTest.step("offline_marker"), patch);
        assertTrue(compiler.validate(doc, null).getIssues().stream().anyMatch(issue -> "budget.nbt".equals(issue.getCode())));
        assertThrows(AuthoringCompiler.CompilationException.class, () -> compiler.compile(doc, null));
        assertEquals(0, applied.get(), "No scene operation may run before whole-document metadata preflight");
        assertTrue(60000L * 262144L > AuthoringNbtBudget.MAX_TOTAL_BYTES, "The 15 GiB case is checked arithmetically, not executed");
    }

    @Test void repeatedExtraAssetsAreDecodedOnceButChargedForEachRetainedEvent() {
        PonderSchematic tile = asset(1, 60000); AtomicInteger reads = new AtomicInteger(), applied = new AtomicInteger();
        List<JsonObject> steps = new ArrayList<JsonObject>(); steps.add(AuthoringModelTest.step("offline_marker"));
        for (int i = 0; i < 300; i++) steps.add(extra(0));
        AuthoringCompiler compiler = marker(applied);
        AuthoringDocument doc = AuthoringModelTest.document(steps.toArray(new JsonObject[0]));
        assertThrows(AuthoringCompiler.CompilationException.class, () -> compiler.compile(doc, id -> { reads.incrementAndGet(); return tile; }));
        assertEquals(1, reads.get()); assertEquals(0, applied.get());
    }

    @Test void hugeAssetTileIsRejectedWithoutRepeatedResolveOrUnboundedSnbtSerialization() {
        PonderSchematic large = asset(1, 1024 * 1024); AtomicInteger reads = new AtomicInteger();
        JsonObject[] steps = new JsonObject[1000]; for (int i = 0; i < steps.length; i++) steps[i] = extra(i);
        AuthoringValidation result = new AuthoringCompiler().validate(AuthoringModelTest.document(steps), id -> { reads.incrementAndGet(); return large; });
        assertTrue(result.hasErrors()); assertTrue(result.getIssues().stream().anyMatch(issue -> "budget.nbt".equals(issue.getCode()))); assertEquals(1, reads.get());
        NBTTagCompound array = new NBTTagCompound(); array.setTag("Data", new NBTTagByteArray(new byte[1024 * 1024]));
        assertThrows(AuthoringNbtBudget.LimitException.class, () -> AuthoringNbtBudget.encodedBytes(array));
    }

    @Test void fullSceneCountsExtraStructurePositionsBeforeMultiplyingAPatch() {
        PonderSchematic tile = asset(1, 1); List<JsonObject> steps = new ArrayList<JsonObject>();
        for (int i = 0; i < 1000; i++) steps.add(extra(i));
        JsonObject patch = AuthoringModelTest.step("modify_block_entity_nbt"); patch.addProperty("fullScene", true); patch.addProperty("nbt", tag(18000).toString()); steps.add(patch);
        AuthoringValidation result = new AuthoringCompiler().validate(AuthoringModelTest.document(steps.toArray(new JsonObject[0])), id -> tile);
        assertTrue(result.getIssues().stream().anyMatch(issue -> "budget.nbt".equals(issue.getCode())));
    }

    @Test void validFullScenePatchActuallyIncludesPrimaryExtraAndNewBlocks() {
        JsonObject show = AuthoringModelTest.step("show_structure"); show.addProperty("structure", "local:tile");
        JsonObject set = AuthoringModelTest.step("set_block"); set.addProperty("block", "minecraft:chest"); set.add("blockPos", AuthoringModelTest.pos(3, 0, 0));
        JsonObject patch = AuthoringModelTest.step("modify_block_entity_nbt"); patch.addProperty("fullScene", true); patch.addProperty("nbt", "{Name:\"all\"}");
        PonderScene scene = new AuthoringCompiler().compile(AuthoringModelTest.document(show, extra(1), set, patch), id -> asset(1, 1)).get(0);
        java.util.Set<BlockPos> patched = new java.util.HashSet<BlockPos>();
        for (PonderScene.WorldEvent event : scene.getWorldEvents()) if (event.getType() == PonderScene.WorldEventType.APPLY_NBT && event.getStateDescription().contains("all")) patched.addAll(event.getPositions());
        assertEquals(new java.util.HashSet<BlockPos>(java.util.Arrays.asList(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(3, 0, 0))), patched);
    }

    @Test void primaryAssetsAreChargedAcrossSegmentsAndRepeatedPatchesDoNotInventGrowth() {
        PonderSchematic fiveTiles = asset(5, 60000); JsonObject root = AuthoringModelTest.document().toJson(); root.addProperty("structure", "local:tile");
        JsonArray segments = new JsonArray();
        for (int i = 0; i < 64; i++) { JsonObject scene = new JsonObject(); scene.addProperty("id", "scene_" + i); scene.add("steps", new JsonArray()); segments.add(scene); }
        root.add("scenes", segments);
        assertTrue(new AuthoringCompiler().validate(AuthoringDocument.fromJson(root), id -> fiveTiles).hasErrors());
        JsonObject[] replacements = new JsonObject[250];
        for (int i = 0; i < replacements.length; i++) { JsonObject step = AuthoringModelTest.step("modify_block_entity_nbt"); step.add("blockPos", AuthoringModelTest.pos(0, 0, 0)); step.addProperty("nbt", tag(1000).toString()); replacements[i] = step; }
        assertFalse(new AuthoringCompiler().validate(AuthoringModelTest.document(replacements), null).hasErrors());
    }

    @Test void sharedDecodeBudgetAndCacheSpanValidationAndApply() {
        PonderSchematic tile = asset(1, 1); AtomicInteger reads = new AtomicInteger(); List<PonderSchematic.DecodeBudget> budgets = new ArrayList<PonderSchematic.DecodeBudget>();
        AuthoringCompiler.BudgetedAssetResolver resolver = new AuthoringCompiler.BudgetedAssetResolver() {
            public PonderSchematic resolve(String id) { fail("Compiler must request the shared decode budget"); return null; }
            public PonderSchematic resolve(String id, PonderSchematic.DecodeBudget budget) { reads.incrementAndGet(); budgets.add(budget); return tile; }
        };
        JsonObject show = AuthoringModelTest.step("show_structure"); show.addProperty("structure", "local:primary");
        new AuthoringCompiler().compile(AuthoringModelTest.document(show, extra(1), extra(2)), resolver);
        assertEquals(2, reads.get()); assertSame(budgets.get(0), budgets.get(1));
    }

    @Test void actorCreationLimitDoesNotLimitOrdinaryActorActions() {
        List<JsonObject> steps = new ArrayList<JsonObject>();
        for (int i = 1; i <= 257; i++) { JsonObject actor = AuthoringModelTest.step("actor_action"); actor.addProperty("actorId", i); actor.addProperty("kind", "PLAYER"); actor.addProperty("action", "SPAWN"); actor.add("pos", AuthoringModelTest.pos(0, 1, 0)); steps.add(actor); }
        assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(steps.toArray(new JsonObject[0])), null).getIssues().stream().anyMatch(issue -> "budget.actors".equals(issue.getCode())));
        steps.remove(256);
        for (int i = 0; i < 400; i++) { JsonObject move = AuthoringModelTest.step("actor_action"); move.addProperty("actorId", 1); move.addProperty("kind", "PLAYER"); move.addProperty("action", "MOVE"); move.add("offset", AuthoringModelTest.pos(0, 0, 0)); steps.add(move); }
        assertFalse(new AuthoringCompiler().validate(AuthoringModelTest.document(steps.toArray(new JsonObject[0])), null).hasErrors());
    }

    @Test void restoreAndNativeItemMetadataAreIncludedAndUtf8IsNotMistakenForCharacterCount() {
        PonderSchematic tile = asset(1, 60000); JsonObject[] restores = new JsonObject[300];
        for (int i = 0; i < restores.length; i++) { restores[i] = AuthoringModelTest.step("restore_blocks"); restores[i].add("blockPos", AuthoringModelTest.pos(0, 0, 0)); }
        JsonObject root = AuthoringModelTest.document(restores).toJson(); root.addProperty("structure", "local:tile");
        assertTrue(new AuthoringCompiler().validate(AuthoringDocument.fromJson(root), id -> tile).hasErrors());
        JsonObject actor = AuthoringModelTest.step("actor_action"); actor.addProperty("actorId", 1); actor.addProperty("kind", "ITEM"); actor.addProperty("action", "SPAWN");
        actor.add("pos", AuthoringModelTest.pos(0, 1, 0)); actor.addProperty("item", "minecraft:stone");
        char[] unicode = new char[30000]; java.util.Arrays.fill(unicode, '\u6C49'); actor.addProperty("nbt", "{Data:\"" + new String(unicode) + "\"}");
        assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(actor), null).getIssues().stream().anyMatch(issue -> "budget.nbt".equals(issue.getCode())));
    }
}
