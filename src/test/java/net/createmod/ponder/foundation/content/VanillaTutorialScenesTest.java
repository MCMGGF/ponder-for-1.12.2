package net.createmod.ponder.foundation.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.content.VanillaTutorialRecipes.RecipePreview;
import net.createmod.ponder.foundation.PonderScene.ActorEvent;
import net.createmod.ponder.foundation.PonderScene.ActorEventType;
import net.createmod.ponder.foundation.PonderScene.ActorKind;
import net.createmod.ponder.foundation.PonderScene.WorldEvent;
import net.createmod.ponder.foundation.PonderScene.WorldEventType;
import net.createmod.ponder.foundation.registration.DefaultPonderSceneRegistrationHelper;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHopper;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.BlockRail;
import net.minecraft.block.BlockRailBase.EnumRailDirection;
import net.minecraft.block.BlockRailPowered;
import net.minecraft.block.BlockRedstoneRepeater;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VanillaTutorialScenesTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void sixQuestTargetsCompileToStableComponentAndSceneIdentities() {
        Map<String, String> expected = new LinkedHashMap<String, String>();
        expected.put("building", "stone");
        expected.put("crafting", "crafting_table");
        expected.put("smelting", "furnace");
        expected.put("logistics", "hopper");
        expected.put("redstone", "redstone");
        expected.put("rails", "rail");
        Map<String, PonderScene> scenes = lessons();
        assertEquals(6, scenes.size());
        PonderSceneRegistry registry = registry();
        for (Map.Entry<ResourceLocation, StoryBoardEntry> entry : registry.getRegisteredEntries()) {
            PonderScene scene = compile(entry.getValue());
            String lesson = scene.getSceneId().getPath().substring("vanilla/".length());
            assertEquals(new ResourceLocation("minecraft", expected.remove(lesson)), entry.getKey());
            assertTrue(entry.getValue().getTags().contains(VanillaPonderPlugin.TUTORIAL_TAG));
            assertNotNull(VanillaTutorialScenesTest.class.getClassLoader().getResource(
                "assets/ponder_legacy/ponder/" + entry.getValue().getSchematicLocation().getPath() + ".nbt"));
        }
        assertTrue(expected.isEmpty());
    }

    @Test
    void everyLessonHasMovingActingPlayerAndStableCamera() {
        for (PonderScene scene : lessons().values()) {
            Set<ActorEventType> actions = new HashSet<ActorEventType>();
            int playerSpawns = 0;
            for (ActorEvent event : scene.getActorEvents()) {
                if (event.getActorKind() == ActorKind.PLAYER) {
                    actions.add(event.getType());
                    if (event.getType() == ActorEventType.SPAWN) {
                        playerSpawns++;
                    }
                }
            }
            assertEquals(1, playerSpawns, scene.getSceneId().toString());
            assertTrue(actions.containsAll(Arrays.asList(ActorEventType.MOVE, ActorEventType.LOOK_AT,
                ActorEventType.SWING)), scene.getSceneId().toString());
            assertTrue(scene.getCameraEvents().isEmpty());
            assertTrue(scene.getPointOfInterestEvents().isEmpty());
            assertTrue(scene.getKeyframeTicks().size() >= 3, scene.getSceneId().toString());
            assertTrue(scene.getWorldEvents().size() >= 3);
        }
    }

    @Test
    void lessonStepsAndContainerImagesKeepConcreteSharedBlockTargets() {
        for (PonderScene scene : lessons().values()) {
            long boundSteps = scene.getOverlayEvents().stream()
                .filter(event -> event.getType() == PonderScene.OverlayEventType.TEXT
                    && !event.getTargetPositions().isEmpty()).count();
            assertTrue(boundSteps >= 3, scene.getSceneId().toString());
            for (PonderScene.OverlayEvent image : scene.getOverlayEvents()) {
                if (image.getType() != PonderScene.OverlayEventType.GUI_TEXTURE) {
                    continue;
                }
                assertFalse(image.getTargetPositions().isEmpty(), scene.getSceneId().toString());
                assertTrue(scene.getOverlayEvents().stream().anyMatch(caption ->
                    caption.getType() == PonderScene.OverlayEventType.TEXT
                        && caption.getTick() <= image.getTick()
                        && caption.getTick() + caption.getDuration() > image.getTick()
                        && caption.getTargetPositions().equals(image.getTargetPositions())),
                    scene.getSceneId() + " image at " + image.getTick());
            }
        }
    }

    @Test
    void craftingInputStepImmediatelyExplainsTheAvailableResult() {
        RecipePreview recipe = RecipePreview.available(Arrays.asList(new ItemStack(Blocks.PLANKS)),
            ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 2));
        PonderScene scene = craftingWith(recipe);
        PonderScene.OverlayEvent caption = scene.getOverlayEvents().stream()
            .filter(event -> event.getType() == PonderScene.OverlayEventType.TEXT && event.getTick() == 42)
            .findFirst().get();
        assertTrue(caption.getText().contains("右侧立即显示"));
        assertEquals(247, scene.getTotalIdleTicks());
        assertTrue(scene.getActorEvents().stream().anyMatch(event -> event.getTick() == 112
            && event.getType() == ActorEventType.HELD_ITEM && event.getItemStack().getItem() == Items.DIAMOND));
    }

    @Test
    void unavailableCraftingExplainsTheEmptyOutputInTheInputStepRatherThanAfterItsDelay() {
        String reason = "测试整合包没有匹配的配方。";
        RecipePreview recipe = RecipePreview.unavailable(Arrays.asList(new ItemStack(Blocks.PLANKS)),
            ItemStack.EMPTY, reason);
        PonderScene scene = craftingWith(recipe);
        PonderScene.OverlayEvent caption = scene.getOverlayEvents().stream()
            .filter(event -> event.getType() == PonderScene.OverlayEventType.TEXT && event.getTick() == 42)
            .findFirst().get();
        assertTrue(caption.getText().contains(reason));
        assertTrue(caption.getText().contains("结果槽保持为空"));
        assertFalse(caption.getText().contains("显示当前配方结果"));
        assertEquals(Arrays.asList(new BlockPos(3, 1, 3)), caption.getTargetPositions());
        assertEquals(177, scene.getTotalIdleTicks());
    }

    @Test
    void buildingHasSynchronizedPlacementAndBreakAnimation() {
        PonderScene scene = lessons().get("building");
        assertTrue(hasState(scene, new BlockPos(4, 2, 3), "minecraft:glass"));
        assertTrue(scene.getWorldEvents().stream().anyMatch(event -> event.getType() == WorldEventType.BREAK_PROGRESS));
        assertTrue(scene.getWorldEvents().stream().anyMatch(event -> event.getType() == WorldEventType.DESTROY_BLOCK));
        assertTrue(scene.getActorEvents().stream().anyMatch(event -> event.getType() == ActorEventType.SNEAK));
        assertTrue(scene.getActorEvents().stream().filter(event -> event.getType() == ActorEventType.SWING)
            .anyMatch(swing -> scene.getWorldEvents().stream().anyMatch(block -> block.getTick() == swing.getTick()
                && block.getType() == WorldEventType.SET_BLOCK)));
    }

    @Test
    void logisticsUsesActualLocalInventoryNbtAndPreservesItems() throws Exception {
        PonderScene scene = lessons().get("logistics");
        Map<BlockPos, NBTTagCompound> contents = new LinkedHashMap<BlockPos, NBTTagCompound>();
        for (WorldEvent event : scene.getWorldEvents()) {
            if (event.getType() == WorldEventType.APPLY_NBT) {
                for (BlockPos position : event.getPositions()) {
                    contents.put(position, JsonToNBT.getTagFromJson(event.getStateDescription()));
                }
            }
        }
        assertEquals(0, contents.get(new BlockPos(3, 2, 3)).getTagList("Items", 10).tagCount());
        assertEquals(0, contents.get(new BlockPos(3, 1, 3)).getTagList("Items", 10).tagCount());
        assertEquals(2, contents.get(new BlockPos(4, 1, 3)).getTagList("Items", 10).getCompoundTagAt(0).getByte("Count"));
        assertTrue(hasProperty(scene, new BlockPos(3, 1, 3), BlockHopper.ENABLED, false));
        assertTrue(hasProperty(scene, new BlockPos(3, 1, 3), BlockHopper.FACING, EnumFacing.EAST));
    }

    @Test
    void redstoneUsesDirectionalRepeaterAndNonStickyPistonRetraction() {
        PonderScene scene = lessons().get("redstone");
        assertTrue(hasProperty(scene, new BlockPos(4, 1, 3), BlockRedstoneRepeater.FACING, EnumFacing.WEST));
        assertTrue(hasProperty(scene, new BlockPos(4, 1, 3), BlockRedstoneRepeater.DELAY, 2));
        assertTrue(hasProperty(scene, new BlockPos(5, 1, 6), BlockPistonBase.EXTENDED, true));
        assertTrue(hasProperty(scene, new BlockPos(5, 1, 6), BlockPistonBase.EXTENDED, false));
        assertTrue(hasState(scene, new BlockPos(7, 1, 6), "minecraft:stonebrick"));
        assertFalse(scene.getWorldEvents().stream().anyMatch(event -> event.getType() == WorldEventType.DESTROY_BLOCK
            && event.getPositions().contains(new BlockPos(7, 1, 6))));
    }

    @Test
    void railsIncludeSupportedSlopeCurvesPowerAndMovingVanillaCart() {
        PonderScene scene = lessons().get("rails");
        assertTrue(hasProperty(scene, new BlockPos(4, 1, 4), BlockRail.SHAPE, EnumRailDirection.NORTH_WEST));
        assertTrue(hasProperty(scene, new BlockPos(4, 1, 2), BlockRail.SHAPE, EnumRailDirection.SOUTH_EAST));
        assertTrue(hasProperty(scene, new BlockPos(6, 1, 2), BlockRailPowered.SHAPE, EnumRailDirection.ASCENDING_EAST));
        assertTrue(hasProperty(scene, new BlockPos(6, 1, 2), BlockRailPowered.POWERED, true));
        assertTrue(hasState(scene, new BlockPos(7, 1, 2), "minecraft:stonebrick"));
        assertTrue(hasState(scene, new BlockPos(0, 1, 4), "minecraft:stonebrick"));
        assertTrue(scene.getActorEvents().stream().anyMatch(event -> event.getActorKind() == ActorKind.CART
            && event.getType() == ActorEventType.SPAWN));
        assertTrue(scene.getActorEvents().stream().anyMatch(event -> event.getActorKind() == ActorKind.CART
            && event.getType() == ActorEventType.MOVE && event.getOffset().y > 0));
    }

    @Test
    void oldWorkbenchEntryKeepsItsPublicSceneIdAndNowHasAnActor() {
        PonderSceneRegistry registry = new PonderSceneRegistry(new PonderLocalization());
        new BasePonderPlugin().registerScenes(new DefaultPonderSceneRegistrationHelper(Ponder.MOD_ID, registry));
        PonderScene scene = registry.compile(new ResourceLocation("minecraft", "crafting_table")).get(0);
        assertEquals(Ponder.asResource("crafting_table"), scene.getSceneId());
        assertTrue(scene.getActorEvents().stream().anyMatch(event -> event.getActorKind() == ActorKind.PLAYER));
    }

    @Test
    void authoredRedstoneConnectionsActuallyProvidePowerToTheLampAndPiston() {
        PonderScene scene = lessons().get("redstone");
        int tick = scene.getWorldEvents().stream().filter(event -> event.getStateDescription() != null
            && event.getStateDescription().startsWith("minecraft:lit_redstone_lamp"))
            .findFirst().get().getTick();
        MapBlockAccess world = new MapBlockAccess(statesAt(scene, tick));
        BlockPos lampWire = new BlockPos(6, 1, 3);
        BlockPos pistonWire = new BlockPos(5, 1, 5);
        assertEquals(14, world.getBlockState(lampWire).getWeakPower(world, lampWire, EnumFacing.WEST));
        assertEquals(13, world.getBlockState(pistonWire).getWeakPower(world, pistonWire, EnumFacing.NORTH));
        BlockPos repeater = new BlockPos(4, 1, 3);
        assertEquals(15, world.getBlockState(repeater).getWeakPower(world, repeater, EnumFacing.WEST));
        assertEquals(0, world.getBlockState(repeater).getWeakPower(world, repeater, EnumFacing.EAST));
    }

    @Test
    void lampTurnsOffFourGameTicksAfterRepeaterOutputStops() {
        PonderScene scene = lessons().get("redstone");
        int repeaterOff = -1;
        int lampOff = -1;
        for (WorldEvent event : scene.getWorldEvents()) {
            if (event.getTick() == 0 || event.getStateDescription() == null) {
                continue;
            }
            if (event.getStateDescription().startsWith("minecraft:unpowered_repeater")) {
                repeaterOff = event.getTick();
            }
            if (event.getStateDescription().startsWith("minecraft:redstone_lamp")) {
                lampOff = event.getTick();
            }
        }
        assertTrue(repeaterOff > 0);
        assertEquals(repeaterOff + 4, lampOff);
    }

    private static boolean hasState(PonderScene scene, BlockPos pos, String text) {
        return scene.getWorldEvents().stream().anyMatch(event -> event.getPositions().contains(pos)
            && event.getStateDescription() != null && event.getStateDescription().contains(text));
    }

    private static <T extends Comparable<T>> boolean hasProperty(PonderScene scene, BlockPos pos,
        IProperty<T> property, T expected) {
        for (WorldEvent event : scene.getWorldEvents()) {
            if (!event.getPositions().contains(pos) || !isBlockState(event)) {
                continue;
            }
            IBlockState state = decodeState(event.getStateDescription());
            if (state.getPropertyKeys().contains(property) && expected.equals(state.getValue(property))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlockState(WorldEvent event) {
        return event.getType() == WorldEventType.SET_BLOCK || event.getType() == WorldEventType.SET_BLOCKS
            || event.getType() == WorldEventType.REPLACE_BLOCKS;
    }

    private static IBlockState decodeState(String description) {
        String[] parts = description.split("#", 2);
        Block block = Block.REGISTRY.getObject(new ResourceLocation(parts[0]));
        assertNotNull(block, description);
        return block.getStateFromMeta(parts.length == 2 ? Integer.parseInt(parts[1]) : 0);
    }

    private static Map<BlockPos, IBlockState> statesAt(PonderScene scene, int tick) {
        Map<BlockPos, IBlockState> states = new LinkedHashMap<BlockPos, IBlockState>();
        for (WorldEvent event : scene.getWorldEvents()) {
            if (event.getTick() > tick) {
                continue;
            }
            for (BlockPos pos : event.getPositions()) {
                if (isBlockState(event)) {
                    states.put(pos, decodeState(event.getStateDescription()));
                } else if (event.getType() == WorldEventType.DESTROY_BLOCK) {
                    states.remove(pos);
                }
            }
        }
        return states;
    }

    private static final class MapBlockAccess implements IBlockAccess {
        private final Map<BlockPos, IBlockState> states;

        private MapBlockAccess(Map<BlockPos, IBlockState> states) {
            this.states = states;
        }

        @Override public TileEntity getTileEntity(BlockPos pos) { return null; }
        @Override public int getCombinedLight(BlockPos pos, int lightValue) { return 0x00F000F0; }
        @Override public IBlockState getBlockState(BlockPos pos) {
            return states.getOrDefault(pos, Blocks.AIR.getDefaultState());
        }
        @Override public boolean isAirBlock(BlockPos pos) { return getBlockState(pos).getBlock() == Blocks.AIR; }
        @Override public Biome getBiome(BlockPos pos) { return Biomes.PLAINS; }
        @Override public int getStrongPower(BlockPos pos, EnumFacing side) {
            return getBlockState(pos).getStrongPower(this, pos, side);
        }
        @Override public WorldType getWorldType() { return WorldType.FLAT; }
        @Override public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean fallback) {
            return getBlockState(pos).isSideSolid(this, pos, side);
        }
    }

    private static PonderSceneRegistry registry() {
        PonderSceneRegistry registry = new PonderSceneRegistry(new PonderLocalization());
        new VanillaPonderPlugin().registerScenes(new DefaultPonderSceneRegistrationHelper(Ponder.MOD_ID, registry));
        return registry;
    }

    private static PonderScene craftingWith(RecipePreview recipe) {
        StoryBoardEntry entry = new PonderStoryBoardEntry((scene, util) ->
            VanillaTutorialScenes.crafting(scene, util, "vanilla/crafting", recipe), Ponder.MOD_ID,
            Ponder.asResource("mmce/empty"), new ResourceLocation("minecraft", "crafting_table"));
        return compile(entry);
    }

    private static PonderScene compile(StoryBoardEntry entry) {
        return PonderSceneRegistry.compileScene(new PonderLocalization(), entry);
    }

    private static Map<String, PonderScene> lessons() {
        Map<String, PonderScene> scenes = new LinkedHashMap<String, PonderScene>();
        for (Map.Entry<ResourceLocation, StoryBoardEntry> entry : registry().getRegisteredEntries()) {
            PonderScene scene = compile(entry.getValue());
            scenes.put(scene.getSceneId().getPath().substring("vanilla/".length()), scene);
        }
        return scenes;
    }
}
