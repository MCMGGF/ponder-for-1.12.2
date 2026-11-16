package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSchematic;
import net.minecraft.block.BlockRail;
import net.minecraft.block.BlockRailBase.EnumRailDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExtraStructureCompilationTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void directionalGroupsCompileWithActualDirectionDurationTicksAndFinalInterval() throws Exception {
        JsonObject step = extra(BlockPos.ORIGIN); step.addProperty("entranceAnimation", "up");
        step.addProperty("direction", "west"); step.addProperty("entranceDuration", 6); step.addProperty("entranceInterval", 2);
        PonderSchematic source = source(new BlockPos(1, 3, 1), BlockPos.ORIGIN, new BlockPos(0, 1, 0), new BlockPos(0, 2, 0));
        PonderScene scene = compile(source, step);
        List<PonderScene.WorldEvent> shows = events(scene, PonderScene.WorldEventType.SHOW_SECTION);
        assertEquals(3, shows.size()); assertEquals(6, scene.getTotalIdleTicks());
        for (int index = 0; index < 3; index++) {
            assertEquals(index * 2, shows.get(index).getTick()); assertEquals(6, shows.get(index).getDuration());
            assertEquals(EnumFacing.WEST, shows.get(index).getDirection());
            assertEquals(Collections.singletonList(new BlockPos(0, index, 0)), shows.get(index).getPositions());
        }
        assertFalse(visible(scene, new BlockPos(0, 2, 0), 0));
        assertFalse(visible(scene, new BlockPos(0, 2, 0), 3));
        assertTrue(visible(scene, new BlockPos(0, 2, 0), 4));
        assertEquals(1F, fade(scene, new BlockPos(0, 2, 0), 9), 0.00001F);
    }

    @Test void noneImmediateFalseIsHiddenAtFirstTickWhileOrdinaryPlacementIsImmediatelyVisible() throws Exception {
        PonderSchematic source = source(new BlockPos(1, 1, 1), BlockPos.ORIGIN);
        JsonObject hidden = extra(BlockPos.ORIGIN); hidden.addProperty("immediateDisplay", false);
        PonderScene scene = compile(source, hidden);
        for (float tick : new float[] {0, 0.25F, 1, 10, 0}) {
            assertFalse(visible(scene, BlockPos.ORIGIN, tick)); assertEquals(0F, fade(scene, BlockPos.ORIGIN, tick));
        }
        assertTrue(events(scene, PonderScene.WorldEventType.SHOW_SECTION).isEmpty());
        PonderScene ordinary = compile(source, extra(BlockPos.ORIGIN));
        assertTrue(visible(ordinary, BlockPos.ORIGIN, 0)); assertEquals(1F, fade(ordinary, BlockPos.ORIGIN, 0));
    }

    @Test void simultaneousRevealsUseDeclaredDurationAndNoImplicitIdle() throws Exception {
        PonderSchematic source = source(new BlockPos(3, 2, 1), BlockPos.ORIGIN, new BlockPos(2, 0, 0), new BlockPos(1, 1, 0));
        JsonObject step = extra(BlockPos.ORIGIN); step.addProperty("entranceAnimation", "simultaneous");
        step.addProperty("entranceDuration", 8); step.addProperty("entranceInterval", 7); step.addProperty("direction", "north");
        PonderScene scene = compile(source, step);
        assertEquals(0, scene.getTotalIdleTicks());
        for (PonderScene.WorldEvent event : events(scene, PonderScene.WorldEventType.SHOW_SECTION)) {
            assertEquals(0, event.getTick()); assertEquals(8, event.getDuration()); assertEquals(EnumFacing.NORTH, event.getDirection());
        }
        assertEquals(0.234375F, fade(scene, BlockPos.ORIGIN, 0), 0.00001F);
        assertEquals(1F, fade(scene, BlockPos.ORIGIN, 7), 0.00001F);
    }

    @Test void zeroDurationRevealIsInstantAndDoesNotUseTheDefaultSectionFade() throws Exception {
        JsonObject step = extra(BlockPos.ORIGIN); step.addProperty("entranceAnimation", "up");
        step.addProperty("entranceDuration", 0); step.addProperty("entranceInterval", 0);
        PonderScene scene = compile(source(new BlockPos(1, 1, 1), BlockPos.ORIGIN), step);
        PonderScene.WorldEvent show = events(scene, PonderScene.WorldEventType.SHOW_SECTION).get(0);
        assertNull(show.getDirection()); assertTrue(visible(scene, BlockPos.ORIGIN, 0)); assertEquals(1F, fade(scene, BlockPos.ORIGIN, 0));
    }

    @Test void rotatedStatesAndNbtCoordinatesUseTheSameNormalizedPlacementAndRemainReplayable() throws Exception {
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<>();
        blocks.put(new BlockPos(0, 0, 1), Blocks.CHEST.getDefaultState());
        blocks.put(new BlockPos(2, 0, 0), Blocks.RAIL.getDefaultState().withProperty(BlockRail.SHAPE, EnumRailDirection.ASCENDING_EAST));
        NBTTagCompound tile = new NBTTagCompound(); tile.setString("id", "minecraft:chest");
        tile.setInteger("x", 99); tile.setInteger("y", 99); tile.setInteger("z", 99); tile.setString("label", "independent");
        PonderSchematic source = PonderSchematic.of(new BlockPos(3, 1, 2), blocks,
            Collections.singletonMap(new BlockPos(0, 0, 1), tile));
        JsonObject step = extra(new BlockPos(10, 3, 20)); step.addProperty("rotation", 90);
        PonderScene first = compile(source, step), second = compile(source, step);
        for (int tick : Arrays.asList(0, 10, 1, 0, 10)) {
            NBTTagCompound expected = nbt(first, new BlockPos(10, 3, 20), tick);
            assertEquals(10, expected.getInteger("x")); assertEquals(3, expected.getInteger("y")); assertEquals(20, expected.getInteger("z"));
            assertEquals("independent", expected.getString("label"));
            assertEquals(expected, nbt(second, new BlockPos(10, 3, 20), tick));
            IBlockState rail = (IBlockState) attribute(first, new BlockPos(11, 3, 22), tick, "currentState");
            assertEquals(EnumRailDirection.ASCENDING_SOUTH, rail.getValue(BlockRail.SHAPE));
        }
        assertEquals(99, source.getBlockEntityNbt(new BlockPos(0, 0, 1)).getInteger("x"));
    }

    @Test void replacingAirClearsMissingDeclaredCellsInsteadOfLeavingPreviousBlocksOrTiles() throws Exception {
        JsonObject block = AuthoringModelTest.step("set_block"); block.addProperty("block", "minecraft:chest");
        block.add("blockPos", AuthoringModelTest.pos(0, 0, 0)); block.addProperty("nbt", "{old:1}");
        JsonObject overlay = extra(BlockPos.ORIGIN); overlay.addProperty("replaceAir", true);
        PonderSchematic source = source(new BlockPos(2, 1, 1), new BlockPos(1, 0, 0));
        PonderScene scene = compile(source, block, overlay);
        assertEquals(Blocks.AIR, ((IBlockState) attribute(scene, BlockPos.ORIGIN, 0, "currentState")).getBlock());
        assertFalse(visible(scene, BlockPos.ORIGIN, 0)); assertNull(attribute(scene, BlockPos.ORIGIN, 0, "tileNbt"));
        assertTrue(visible(scene, new BlockPos(1, 0, 0), 0));
    }

    @Test void sameBlockPlacementResetsOldTileKeysAndThenAppliesOnlyItsOwnSnapshot() throws Exception {
        JsonObject block = AuthoringModelTest.step("set_block"); block.addProperty("block", "minecraft:chest");
        block.add("blockPos", AuthoringModelTest.pos(0, 0, 0)); block.addProperty("nbt", "{old:1}");
        NBTTagCompound own = new NBTTagCompound(); own.setString("id", "minecraft:chest"); own.setInteger("fresh", 2);
        PonderSchematic source = PonderSchematic.of(new BlockPos(1, 1, 1), Collections.singletonMap(BlockPos.ORIGIN, Blocks.CHEST.getDefaultState()),
            Collections.singletonMap(BlockPos.ORIGIN, own));
        PonderScene scene = compile(source, block, extra(BlockPos.ORIGIN));
        NBTTagCompound actual = nbt(scene, BlockPos.ORIGIN, 0);
        assertEquals(2, actual.getInteger("fresh")); assertFalse(actual.hasKey("old"));
    }

    @Test void explicitLinkTransformsOnlyItsNormalizedPlacementAndReplaysWithoutAccumulatingMotion() throws Exception {
        JsonObject first = extra(new BlockPos(5, 0, 0)); first.addProperty("linkId", "overlay");
        JsonObject move = AuthoringModelTest.step("move_section"); move.addProperty("linkId", "overlay");
        move.add("offset", AuthoringModelTest.pos(2, 0, 0)); move.addProperty("duration", 10);
        JsonObject stationary = AuthoringModelTest.step("set_block"); stationary.addProperty("block", "minecraft:stone");
        stationary.add("blockPos", AuthoringModelTest.pos(9, 0, 0));
        PonderScene scene = compile(source(new BlockPos(3, 1, 1), new BlockPos(2, 0, 0)), stationary, first, move);
        List<PonderScene.WorldEvent> moves = events(scene, PonderScene.WorldEventType.MOVE_SECTION);
        assertEquals(1, moves.size()); assertEquals(Collections.singletonList(new BlockPos(5, 0, 0)), moves.get(0).getPositions());
        for (int tick : Arrays.asList(10, 0, 5, 10, 0)) {
            float progress = Math.min(1F, (tick + 1F) / 10F);
            float eased = 1F - (1F - progress) * (1F - progress);
            double expectedOffset = 2D * eased;
            assertEquals(5.5 + expectedOffset,
                ((Number) attribute(scene, new BlockPos(5, 0, 0), tick, "renderCenterX")).doubleValue(), 0.00001D);
            assertEquals(0.5D, ((Number) attribute(scene, new BlockPos(5, 0, 0), tick, "renderCenterY")).doubleValue(), 0.00001D);
            assertEquals(0.5D, ((Number) attribute(scene, new BlockPos(5, 0, 0), tick, "renderCenterZ")).doubleValue(), 0.00001D);
            List<?> transforms = (List<?>) attribute(scene, new BlockPos(5, 0, 0), tick, "transforms");
            assertEquals(1, transforms.size());
            Field offset = transforms.get(0).getClass().getDeclaredField("offset"); offset.setAccessible(true);
            Vec3d actual = (Vec3d) offset.get(transforms.get(0));
            assertEquals(expectedOffset, actual.x, 0.00001D); assertEquals(0D, actual.y); assertEquals(0D, actual.z);
            assertEquals(9.5D, ((Number) attribute(scene, new BlockPos(9, 0, 0), tick, "renderCenterX")).doubleValue(), 0.00001D);
            assertTrue(((List<?>) attribute(scene, new BlockPos(9, 0, 0), tick, "transforms")).isEmpty());
        }
    }

    @Test void overwritingTransformedOwnershipAndReusingALinkUseExplicitIdentityEvents() throws Exception {
        JsonObject first = extra(BlockPos.ORIGIN); first.addProperty("linkId", "overlay");
        JsonObject move = AuthoringModelTest.step("move_section"); move.addProperty("linkId", "overlay"); move.add("offset", AuthoringModelTest.pos(1, 0, 0));
        PonderSchematic source = source(new BlockPos(1, 1, 1), BlockPos.ORIGIN);
        PonderScene replaced = compile(source, first, move, extra(BlockPos.ORIGIN));
        assertEquals(.5D,((Number)attribute(replaced,BlockPos.ORIGIN,20,"renderCenterX")).doubleValue(),.00001D);
        JsonObject duplicate = extra(new BlockPos(2, 0, 0)); duplicate.addProperty("linkId", "overlay");
        assertTrue(new AuthoringCompiler().validate(document(first, duplicate), id -> source).canPublish());
        PonderScene merged=compile(source,first,move,duplicate);
        assertEquals(3.5D,((Number)attribute(merged,new BlockPos(2,0,0),20,"renderCenterX")).doubleValue(),.00001D);
    }

    @Test void independentNonoverlappingSectionsRetainDistinctLinkSelections() {
        JsonObject first = extra(BlockPos.ORIGIN); first.addProperty("linkId", "first");
        JsonObject second = extra(new BlockPos(5, 0, 0)); second.addProperty("linkId", "second");
        JsonObject move = AuthoringModelTest.step("move_section"); move.addProperty("linkId", "first"); move.add("offset", AuthoringModelTest.pos(1, 0, 0));
        PonderScene scene = compile(source(new BlockPos(1, 1, 1), BlockPos.ORIGIN), first, second, move);
        assertEquals(Collections.singletonList(BlockPos.ORIGIN), events(scene, PonderScene.WorldEventType.MOVE_SECTION).get(0).getPositions());
    }

    @Test void smartOverlapRetainsCurrentVisibilityWhileOptedOutReRevealRemainsDeterministic() throws Exception {
        JsonObject placed = AuthoringModelTest.step("set_block"); placed.addProperty("block", "minecraft:stone");
        placed.add("blockPos", AuthoringModelTest.pos(0, 0, 0));
        JsonObject reveal = extra(BlockPos.ORIGIN); reveal.addProperty("entranceAnimation", "up");
        PonderSchematic source = source(new BlockPos(1, 1, 1), BlockPos.ORIGIN);
        assertEquals(1F,fade(compile(source,placed,reveal),BlockPos.ORIGIN,0));
        reveal.addProperty("smartDisplay", true);
        assertEquals(1F,fade(compile(source,placed,reveal),BlockPos.ORIGIN,0));
        reveal.addProperty("smartDisplay", false);
        PonderScene scene = compile(source, placed, reveal);
        float initial = fade(scene, BlockPos.ORIGIN, 0);
        assertTrue(initial > 0 && initial < 1);
        assertEquals(1F, fade(scene, BlockPos.ORIGIN, 20));
        assertEquals(initial, fade(scene, BlockPos.ORIGIN, 0));
    }

    @Test void nbtBudgetSimulatesRotatedNormalizedTileLocationsBeforeSubsequentPatches() {
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<>();
        blocks.put(new BlockPos(0, 0, 1), Blocks.CHEST.getDefaultState()); blocks.put(new BlockPos(2, 0, 0), Blocks.STONE.getDefaultState());
        NBTTagCompound tile = new NBTTagCompound(); tile.setString("first", repeat(40000));
        PonderSchematic source = PonderSchematic.of(new BlockPos(3, 1, 2), blocks, Collections.singletonMap(new BlockPos(0, 0, 1), tile));
        JsonObject overlay = extra(BlockPos.ORIGIN); overlay.addProperty("rotation", 90);
        JsonObject patch = AuthoringModelTest.step("modify_block_entity_nbt"); patch.add("blockPos", AuthoringModelTest.pos(0, 0, 0));
        patch.addProperty("nbt", "{second:\"" + repeat(40000) + "\"}");
        AuthoringValidation validation = new AuthoringCompiler().validate(document(overlay, patch), id -> source);
        assertFalse(validation.canPublish());
        assertTrue(validation.getIssues().stream().anyMatch(issue -> "budget.nbt".equals(issue.getCode())));
    }

    @Test void exact64CubedReplaceVolumeDoesNotAlsoChargeItsAnchorPosition() {
        PonderSchematic sparse = PonderSchematic.of(new BlockPos(64, 64, 64), Collections.emptyMap(), Collections.emptyMap());
        JsonObject overlay = extra(BlockPos.ORIGIN); overlay.addProperty("replaceAir", true);
        AuthoringValidation validation = new AuthoringCompiler().validate(document(overlay), id -> sparse);
        assertTrue(validation.canPublish(), () -> validation.getIssues().toString());
    }

    private static PonderScene compile(PonderSchematic source, JsonObject... steps) {
        return new AuthoringCompiler().compile(document(steps), id -> source).get(0);
    }
    private static AuthoringDocument document(JsonObject... steps) {
        JsonObject root = AuthoringModelTest.document(steps).toJson();
        root.getAsJsonArray("scenes").get(0).getAsJsonObject().addProperty("autoReveal", false);
        return AuthoringDocument.fromJson(root);
    }
    private static JsonObject extra(BlockPos pos) {
        JsonObject step = AuthoringModelTest.step("show_extra_structure"); step.addProperty("structure", "example:extra");
        step.add("blockPos", AuthoringModelTest.pos(pos.getX(), pos.getY(), pos.getZ())); return step;
    }
    private static PonderSchematic source(BlockPos size, BlockPos... positions) {
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<>(); for (BlockPos pos : positions) blocks.put(pos, Blocks.STONE.getDefaultState());
        return PonderSchematic.of(size, blocks, Collections.emptyMap());
    }
    private static List<PonderScene.WorldEvent> events(PonderScene scene, PonderScene.WorldEventType type) {
        return scene.getWorldEvents().stream().filter(event -> event.getType() == type).collect(Collectors.toList());
    }
    private static boolean visible(PonderScene scene, BlockPos pos, float tick) throws Exception { return (Boolean) attribute(scene, pos, tick, "visible"); }
    private static float fade(PonderScene scene, BlockPos pos, float tick) throws Exception { return ((Number) attribute(scene, pos, tick, "fade")).floatValue(); }
    private static NBTTagCompound nbt(PonderScene scene, BlockPos pos, float tick) throws Exception {
        return JsonToNBT.getTagFromJson((String) attribute(scene, pos, tick, "tileNbt"));
    }
    private static Object attribute(PonderScene scene, BlockPos pos, float tick, String fieldName) throws Exception {
        Class<?> runtime = Class.forName("net.createmod.ponder.foundation.ui.PonderSceneRuntime");
        Method build = runtime.getDeclaredMethod("buildState", PonderScene.class, float.class); build.setAccessible(true);
        Object state = build.invoke(null, scene, tick); Field map = state.getClass().getDeclaredField("blocksByPosition"); map.setAccessible(true);
        Object block = ((Map<?, ?>) map.get(state)).get(pos); assertNotNull(block, "Missing runtime block " + pos);
        Field field = block.getClass().getDeclaredField(fieldName); field.setAccessible(true); return field.get(block);
    }
    private static String repeat(int size) { return String.join("", Collections.nCopies(size, "x")); }
}
