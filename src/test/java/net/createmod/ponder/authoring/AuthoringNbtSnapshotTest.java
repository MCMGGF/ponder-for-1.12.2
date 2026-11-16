package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSceneBuilder;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuthoringNbtSnapshotTest {
    private static final BlockPos TARGET = new BlockPos(3, 2, 4);

    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void forkPreservesCompleteSnapshotsWhenTopLevelAndNestedKeysDisappear() throws Exception {
        PonderScene original = source((scene, util) -> {
            scene.world().setBlock(TARGET, Blocks.CHEST.getDefaultState(), false);
            scene.world().modifyBlockEntityNBT(util.select().position(TARGET), TileEntity.class, tag -> {
                tag.setString("id", "minecraft:chest");
                tag.setInteger("x", TARGET.getX()); tag.setInteger("y", TARGET.getY()); tag.setInteger("z", TARGET.getZ());
                tag.setString("obsolete", "must disappear");
                NBTTagCompound nested = new NBTTagCompound(); nested.setInteger("keep", 1); nested.setInteger("remove", 2);
                tag.setTag("nested", nested);
            });
            scene.idle(5);
            scene.world().modifyBlockEntityNBT(util.select().position(TARGET), TileEntity.class, tag -> {
                tag.removeTag("obsolete"); tag.getCompoundTag("nested").removeTag("remove");
            });
            scene.idle(5);
            scene.world().modifyBlockEntityNBT(util.select().position(TARGET), TileEntity.class, tag -> tag.removeTag("nested"));
            scene.idle(5);
            scene.world().modifyBlockEntityNBT(util.select().position(TARGET), TileEntity.class, tag -> {
                for (String key : new ArrayList<String>(tag.getKeySet())) tag.removeTag(key);
            });
            scene.idle(5);
        });
        List<String> operations = new ArrayList<String>(original.getOperationLog());
        AuthoringDocument document = AuthoringSceneForker.fork(original, "local:nbt_snapshot", (id, structure) -> fail());
        int replacements = 0;
        for (JsonElement element : document.getScenes().get(0).getAsJsonArray("steps")) {
            JsonObject step = element.getAsJsonObject();
            if ("modify_block_entity_nbt".equals(step.get("type").getAsString())) {
                assertEquals("replace", step.get("nbtMode").getAsString()); replacements++;
            }
        }
        assertEquals(4, replacements);
        PonderScene copy = new AuthoringCompiler().compile(document, null).get(0);
        List<PonderScene.WorldEvent> expected = snapshots(original), actual = snapshots(copy);
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).getTick(), actual.get(i).getTick());
            assertEquals(expected.get(i).getPositions(), actual.get(i).getPositions());
            assertEquals(JsonToNBT.getTagFromJson(expected.get(i).getStateDescription()),
                JsonToNBT.getTagFromJson(actual.get(i).getStateDescription()));
        }
        for (int tick : Arrays.asList(0, 4, 5, 9, 10, 14, 15, 20, 5, 15))
            assertEquals(runtimeNbt(original, tick), runtimeNbt(copy, tick), "Snapshot mismatch at tick " + tick);
        NBTTagCompound second = runtimeNbt(copy, 5);
        assertEquals("minecraft:chest", second.getString("id"));
        assertEquals(TARGET.getX(), second.getInteger("x"));
        assertEquals(TARGET.getY(), second.getInteger("y"));
        assertEquals(TARGET.getZ(), second.getInteger("z"));
        assertFalse(second.hasKey("obsolete")); assertFalse(second.getCompoundTag("nested").hasKey("remove"));
        assertTrue(runtimeNbt(copy, 15).isEmpty());
        assertEquals(operations, original.getOperationLog());
        assertNotEquals(original.getSceneId(), copy.getSceneId());
    }

    @Test void existingUnspecifiedAndExplicitMergeModesKeepOldKeys() throws Exception {
        for (String mode : new String[] {null, "merge"}) {
            JsonObject initial = nbtStep("{old:1,nested:{keep:2,update:3}}");
            JsonObject patch = nbtStep("{nested:{update:4}}");
            if (mode != null) patch.addProperty("nbtMode", mode);
            PonderScene scene = new AuthoringCompiler().compile(AuthoringModelTest.document(initial, patch), null).get(0);
            NBTTagCompound tag = runtimeNbt(scene, 0);
            assertEquals(1, tag.getInteger("old"));
            assertEquals(2, tag.getCompoundTag("nested").getInteger("keep"));
            assertEquals(4, tag.getCompoundTag("nested").getInteger("update"));
        }
    }

    @Test void replacementSnapshotsAreIndependentAcrossRepeatedCompilations() throws Exception {
        JsonObject initial = nbtStep("{old:1,nested:{old:2}}");
        JsonObject replace = nbtStep("{id:\"minecraft:chest\",x:3,y:2,z:4,nested:{keep:3}}");
        replace.addProperty("nbtMode", "replace");
        AuthoringDocument document = AuthoringModelTest.document(initial, replace);
        PonderScene first = new AuthoringCompiler().compile(document, null).get(0);
        NBTTagCompound firstNbt = runtimeNbt(first, 0);
        assertFalse(firstNbt.hasKey("old")); assertFalse(firstNbt.getCompoundTag("nested").hasKey("old"));
        firstNbt.getCompoundTag("nested").setInteger("keep", 99);
        PonderScene second = new AuthoringCompiler().compile(document, null).get(0);
        assertEquals(3, runtimeNbt(second, 0).getCompoundTag("nested").getInteger("keep"));
        assertEquals(3, runtimeNbt(first, 0).getCompoundTag("nested").getInteger("keep"));
    }

    @Test void unknownNonstringAndMisplacedModesCannotPublish() {
        for (JsonElement mode : Arrays.<JsonElement>asList(new JsonPrimitive("snapshot"), new JsonPrimitive("REPLACE"),
            new JsonPrimitive(""), new JsonPrimitive(true), new JsonPrimitive(1), new JsonObject(), JsonNull.INSTANCE)) {
            JsonObject step = nbtStep("{}"); step.add("nbtMode", mode);
            AuthoringDocument document = AuthoringModelTest.document(step);
            assertTrue(new AuthoringCompiler().validate(document, null).hasErrors(), String.valueOf(mode));
            assertThrows(AuthoringCompiler.CompilationException.class, () -> new AuthoringCompiler().compile(document, null));
        }
        JsonObject missingNbt = nbtStep("{}"); missingNbt.remove("nbt"); missingNbt.addProperty("nbtMode", "replace");
        assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(missingNbt), null).hasErrors());
        JsonObject block = AuthoringModelTest.step("set_block"); block.add("blockPos", AuthoringModelTest.pos(3, 2, 4));
        block.addProperty("block", "minecraft:chest"); block.addProperty("nbt", "{}"); block.addProperty("nbtMode", "replace");
        assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(block), null).hasErrors());
    }

    @Test void otherwiseValidNativeActorRejectsMisplacedNbtMode() {
        JsonObject actor = AuthoringModelTest.step("actor_action");
        actor.addProperty("actorId", 1); actor.addProperty("kind", "PLAYER"); actor.addProperty("action", "SPAWN");
        actor.add("pos", AuthoringModelTest.pos(1, 1, 1));
        assertFalse(new AuthoringCompiler().validate(AuthoringModelTest.document(actor), null).hasErrors());
        assertEquals(1, new AuthoringCompiler().compile(AuthoringModelTest.document(actor), null).get(0).getActorEvents().size());
        actor.addProperty("nbtMode", "replace"); actor.addProperty("nbt", "{}");
        AuthoringDocument misplaced = AuthoringModelTest.document(actor);
        assertTrue(new AuthoringCompiler().validate(misplaced, null).hasErrors());
        assertThrows(AuthoringCompiler.CompilationException.class, () -> new AuthoringCompiler().compile(misplaced, null));
    }

    private static JsonObject nbtStep(String value) {
        JsonObject step = AuthoringModelTest.step("modify_block_entity_nbt");
        step.add("blockPos", AuthoringModelTest.pos(TARGET.getX(), TARGET.getY(), TARGET.getZ()));
        step.addProperty("nbt", value); return step;
    }

    private static PonderScene source(PonderStoryBoard board) {
        PonderScene scene = new PonderScene(new PonderStoryBoardEntry(board, "ponder_legacy",
            new ResourceLocation("ponder_legacy", "nbt_fork_source"), new ResourceLocation("minecraft", "chest")),
            new PonderLocalization());
        scene.program(new PonderSceneBuilder(scene), scene.getSceneBuildingUtil()); return scene;
    }

    private static List<PonderScene.WorldEvent> snapshots(PonderScene scene) {
        List<PonderScene.WorldEvent> events = new ArrayList<PonderScene.WorldEvent>();
        for (PonderScene.WorldEvent event : scene.getWorldEvents())
            if (event.getType() == PonderScene.WorldEventType.APPLY_NBT) events.add(event);
        return events;
    }

    private static NBTTagCompound runtimeNbt(PonderScene scene, int tick) throws Exception {
        Class<?> runtime = Class.forName("net.createmod.ponder.foundation.ui.PonderSceneRuntime");
        Method build = runtime.getDeclaredMethod("buildState", PonderScene.class, int.class); build.setAccessible(true);
        Object state = build.invoke(null, scene, tick);
        Field blocksField = state.getClass().getDeclaredField("blocksByPosition"); blocksField.setAccessible(true);
        Object block = ((Map<?, ?>) blocksField.get(state)).get(TARGET); assertNotNull(block);
        Field nbtField = block.getClass().getDeclaredField("tileNbt"); nbtField.setAccessible(true);
        return JsonToNBT.getTagFromJson((String) nbtField.get(block));
    }
}
