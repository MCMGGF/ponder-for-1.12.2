package net.createmod.ponder.foundation.content.oil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.ActorEvent;
import net.createmod.ponder.foundation.PonderScene.ActorEventType;
import net.createmod.ponder.foundation.PonderScene.ActorKind;
import net.createmod.ponder.foundation.PonderScene.OverlayEvent;
import net.createmod.ponder.foundation.PonderScene.OverlayEventType;
import net.createmod.ponder.foundation.PonderScene.WorldEvent;
import net.createmod.ponder.foundation.PonderScene.WorldEventType;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.content.oil.OilLineLayout.Endpoint;
import net.createmod.ponder.foundation.content.oil.OilLineLayout.Network;
import net.createmod.ponder.foundation.content.oil.OilLineLayout.Point;
import net.createmod.ponder.foundation.content.oil.OilProductionLineScenes.Assets;
import net.createmod.ponder.foundation.content.oil.OilProductionLineScenes.PreviewBlock;
import net.createmod.ponder.foundation.registration.DefaultPonderSceneRegistrationHelper;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Vanilla fixtures exercise the recorded lessons, not substitutes for installed machines. */
class OilProductionLineScenesTest {

    private static final List<String> CHAPTERS = Arrays.asList(
        "overview", "steam", "pumping", "distillation", "power", "troubleshooting");
    private static final Point[] CONTROLLERS = { OilLineLayout.BOILER_CONTROLLER,
        OilLineLayout.PUMP_CONTROLLER, OilLineLayout.DISTILLATION_CONTROLLER,
        OilLineLayout.GENERATOR_CONTROLLER };

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void sixChaptersKeepTheirStableIdsAndSharedComponent() {
        assertEquals(CHAPTERS, OilProductionLinePlugin.CHAPTERS);
        Map<String, PonderScene> lessons = lessons();
        assertEquals(new LinkedHashSet<>(CHAPTERS), lessons.keySet());
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (Map.Entry<String, PonderScene> lesson : lessons.entrySet()) {
            assertEquals(Ponder.asResource("production/early_oil/" + lesson.getKey()),
                lesson.getValue().getSceneId());
            assertTrue(ids.add(lesson.getValue().getSceneId()));
        }
        assertEquals(Ponder.asResource("production/early_oil_line"), OilProductionLinePlugin.COMPONENT);
    }

    @Test
    void everyChapterHasOneActingPlayerFourStepsAndNoAutomaticCamera() {
        for (PonderScene scene : lessons().values()) {
            Set<ActorEventType> actions = EnumSet.noneOf(ActorEventType.class);
            int players = 0;
            for (ActorEvent event : scene.getActorEvents()) {
                if (event.getActorKind() == ActorKind.PLAYER) {
                    actions.add(event.getType());
                    if (event.getType() == ActorEventType.SPAWN) players++;
                }
            }
            assertEquals(1, players, scene.getSceneId().toString());
            assertTrue(actions.containsAll(Arrays.asList(ActorEventType.MOVE,
                ActorEventType.LOOK_AT, ActorEventType.SWING)), scene.getSceneId().toString());
            assertTrue(scene.getKeyframeTicks().size() >= 4, scene.getSceneId().toString());
            assertTrue(scene.getCameraEvents().isEmpty(), scene.getSceneId().toString());
            assertTrue(scene.getPointOfInterestEvents().isEmpty(), scene.getSceneId().toString());
        }
    }

    @Test
    void allStepCaptionsReferToExistingConcreteBlocksAtTheirOwnTicks() {
        for (PonderScene scene : lessons().values()) {
            int captions = 0;
            for (OverlayEvent event : scene.getOverlayEvents()) {
                if (event.getType() != OverlayEventType.TEXT) continue;
                captions++;
                assertFalse(event.getText().trim().isEmpty(), scene.getSceneId().toString());
                assertFalse(event.getTargetPositions().isEmpty(), event.getText());
                assertTrue(event.isPlaceNearTarget(), event.getText());
                Map<BlockPos, IBlockState> states = statesAt(scene, event.getTick());
                for (BlockPos target : event.getTargetPositions()) {
                    assertTrue(states.containsKey(target), scene.getSceneId() + " missing " + target);
                    assertFalse(states.get(target).getBlock() == Blocks.AIR,
                        scene.getSceneId() + " air target " + target);
                }
                assertTrue(scene.getOverlayEvents().stream().anyMatch(outline ->
                    outline.getType() == OverlayEventType.SCENE_OUTLINE
                        && outline.getTick() == event.getTick()
                        && outline.getSourcePositions().equals(event.getTargetPositions())),
                    scene.getSceneId() + " caption lacks its shared 3D selection");
            }
            assertTrue(captions >= 4, scene.getSceneId().toString());
        }
    }

    @Test
    void overviewAndTroubleshootingRetainAllFourControllersAndUniqueHatches() {
        for (String chapter : Arrays.asList("overview", "troubleshooting")) {
            Map<BlockPos, IBlockState> states = statesAt(lessons().get(chapter), Integer.MAX_VALUE);
            for (Point controller : CONTROLLERS) {
                BlockPos p = pos(controller);
                assertEquals(Blocks.STONEBRICK, states.get(p).getBlock(), chapter + " " + p);
                assertEquals(Blocks.CHEST, states.get(p.east()).getBlock(), chapter + " hatch " + p);
                assertEquals(Blocks.GLASS, states.get(p.east().up()).getBlock(), chapter + " roof " + p);
            }
        }
    }

    @Test
    void oilRunKeepsItsDeepTrenchAndIsNeverOverwrittenByTheFloor() {
        for (String chapter : Arrays.asList("overview", "pumping", "troubleshooting")) {
            Map<BlockPos, IBlockState> states = statesAt(lessons().get(chapter), Integer.MAX_VALUE);
            for (Point node : OilLineLayout.OIL_NETWORK.getNodes()) {
                assertEquals(Blocks.HOPPER, states.get(pos(node)).getBlock(), chapter + " pipe " + node);
                if (node.y < 0) {
                    IBlockState above = states.get(new BlockPos(node.x, 0, node.z));
                    assertTrue(above == null || above.getBlock() == Blocks.HOPPER,
                        chapter + " floor closes the pipe trench at " + node);
                    assertEquals(Blocks.STONE, states.get(new BlockPos(node.x, -2, node.z)).getBlock());
                }
            }
        }
    }

    @Test
    void recordedConduitEndpointsHaveCorrectConduitRelativeDirections() throws Exception {
        PonderScene scene = lessons().get("overview");
        for (Network network : OilLineLayout.getNetworks()) {
            for (Endpoint end : network.getEndpoints()) {
                NBTTagCompound root = lastNbt(scene, pos(end.node));
                assertNotNull(root, end.id);
                NBTTagCompound conduit = root.getCompoundTag("conduits").getCompoundTag("0");
                EnumFacing face = EnumFacing.valueOf(end.nodeFace.name());
                assertTrue(contains(conduit.getIntArray("externalConnections"), face.ordinal()), end.id);
                byte[] modes = conduit.getByteArray("conModes");
                assertEquals(6, modes.length, end.id);
                boolean source = end.id.startsWith("boiler-") || end.id.equals("pump-oil")
                    || end.id.equals("diesel-buffer") || end.id.equals("generator-energy");
                assertEquals(source ? OilLinePreviewNbt.EXTRACT : OilLinePreviewNbt.INSERT,
                    modes[face.ordinal()], end.id);
                assertEquals(0, conduit.getShort((network.fluidId == null ? "pRsMode." : "extRM.")
                    + face.name()), end.id);
                if (network.fluidId != null) {
                    assertEquals(network.fluidId, conduit.getCompoundTag("tank").getString("FluidName"));
                }
            }
        }
    }

    @Test
    void batchDistillationShowsTheSteamEndpointDisabledInsteadOfAnUnlimitedFeed() throws Exception {
        PonderScene scene = lessons().get("distillation");
        boolean opened = false;
        boolean closedAfterOpening = false;
        BlockPos endpoint = pos(OilLineLayout.DISTILLATION_HATCH).north();
        for (WorldEvent event : scene.getWorldEvents()) {
            if (event.getType() != WorldEventType.APPLY_NBT || !event.getPositions().contains(endpoint)) continue;
            byte[] modes = JsonToNBT.getTagFromJson(event.getStateDescription())
                .getCompoundTag("conduits").getCompoundTag("0").getByteArray("conModes");
            if (modes[EnumFacing.SOUTH.ordinal()] == OilLinePreviewNbt.INSERT) opened = true;
            if (opened && modes[EnumFacing.SOUTH.ordinal()] == OilLinePreviewNbt.DISABLED) closedAfterOpening = true;
        }
        assertTrue(opened, "The batch must explicitly open its steam connection");
        assertTrue(closedAfterOpening, "The batch must close steam after charging");
        NBTTagCompound conduit = lastNbt(scene, pos(OilLineLayout.DISTILLATION_HATCH).north())
            .getCompoundTag("conduits").getCompoundTag("0");
        assertEquals(OilLinePreviewNbt.DISABLED,
            conduit.getByteArray("conModes")[EnumFacing.SOUTH.ordinal()]);
        String text = captions(scene);
        assertTrue(text.contains("5000 mB"));
        assertTrue(text.contains("\u5173\u95ed"));
        assertTrue(text.contains("\u539f\u6cb9\u7f50\u8017\u5c3d\u524d"));
    }

    @Test
    void fiveBatchesPreserveTheHalfBucketDieselRemainderAndSeparateByproducts() throws Exception {
        PonderScene scene = lessons().get("distillation");
        String text = captions(scene);
        assertEquals(3500, 5 * OilLineRecipeFacts.DISTILL_DIESEL_MB);
        assertEquals(1000, 5 * OilLineRecipeFacts.DISTILL_LUBRICANT_MB);
        assertEquals(5, 5 * OilLineRecipeFacts.DISTILL_BITUMEN);
        assertEquals(500, 5 * OilLineRecipeFacts.DISTILL_DIESEL_MB % 1000);
        for (String expected : Arrays.asList("3500 mB", "1000 mB", "500 mB")) {
            assertTrue(text.contains(expected), expected);
        }
        assertTrue(text.contains("\u4e94\u6279"));
        assertTrue(text.contains("\u666e\u901a\u7a7a\u6876"));
        assertTank(lastNbt(scene, pos(OilLineLayout.DIESEL_TANK)), "diesel", 3000);
        assertTank(lastNbt(scene, pos(OilLineLayout.LUBRICANT_TANK)), "lubricant", 1000);
        NBTTagList items = lastNbt(scene, pos(OilLineLayout.BITUMEN_CHEST)).getTagList("Items", 10);
        assertEquals(1, items.tagCount());
        assertEquals(5, items.getCompoundTagAt(0).getByte("Count"));
        assertEquals("minecraft:clay_ball", items.getCompoundTagAt(0).getString("id"));
    }

    @Test
    void pumpBufferRecordsFourAndTwentyBatchQuantitiesWithoutInventingInstantProduction() throws Exception {
        PonderScene scene = lessons().get("pumping");
        assertTrue(hasTankSnapshot(scene, pos(OilLineLayout.OIL_TANK), "oil", 1000));
        assertTrue(hasTankSnapshot(scene, pos(OilLineLayout.OIL_TANK), "oil", 5000));
        String text = captions(scene);
        assertTrue(text.contains("400 \u523b"));
        assertTrue(text.contains("250 mB"));
        assertTrue(text.contains("400 \u79d2"));
    }

    @Test
    void absentOptionalMachinesFailClosedInsteadOfPublishingFixtureSubstitutes() {
        PonderSceneRegistry registry = new PonderSceneRegistry(new PonderLocalization());
        new OilProductionLinePlugin().registerScenes(
            new DefaultPonderSceneRegistrationHelper(Ponder.MOD_ID, registry));
        assertEquals(0, registry.getRegisteredEntryCount());
        assertThrows(IllegalStateException.class, Assets::load);
        StoryBoardEntry entry = new PonderStoryBoardEntry((scene, util) ->
            OilProductionLineScenes.build(scene, util, 0), Ponder.MOD_ID,
            Ponder.asResource("mmce/empty"), OilProductionLinePlugin.COMPONENT);
        PonderScene unavailable = compile(entry);
        assertTrue(unavailable.getWorldEvents().isEmpty());
        assertTrue(unavailable.getActorEvents().isEmpty());
        assertTrue(captions(unavailable).contains("\u672a\u66ff\u6362\u6210\u865a\u6784\u673a\u5668"));
    }

    @Test
    void teachingSourceUsesOnlyDetachedInstructionsNotRealWorldOrNetworkInteraction() throws IOException {
        for (String file : Arrays.asList("OilProductionLineScenes.java", "OilProductionLinePlugin.java")) {
            String source = new String(Files.readAllBytes(Paths.get(
                "src/oilLine/java/net/createmod/ponder/foundation/content/oil", file)), StandardCharsets.UTF_8);
            for (String forbidden : Arrays.asList("Minecraft.getMinecraft", "playerController", "sendToServer",
                "getConnection()", "WorldServer", "WorldClient", ".setBlockState(", ".openGui(",
                ".onBlockActivated(", "processRightClick", "CPacket", "SimpleNetworkWrapper")) {
                assertFalse(source.contains(forbidden), file + " uses " + forbidden);
            }
        }
    }

    private static Assets fixture() {
        List<Map<BlockPos, PreviewBlock>> stations = new ArrayList<>();
        for (Point controller : CONTROLLERS) {
            Map<BlockPos, PreviewBlock> parts = new LinkedHashMap<>();
            BlockPos p = pos(controller);
            parts.put(p, new PreviewBlock(Blocks.STONEBRICK.getDefaultState(), new NBTTagCompound()));
            parts.put(p.east(), new PreviewBlock(Blocks.CHEST.getDefaultState(), new NBTTagCompound()));
            parts.put(p.east().up(), new PreviewBlock(Blocks.GLASS.getDefaultState(), new NBTTagCompound()));
            stations.add(parts);
        }
        Map<String, ItemStack> icons = new LinkedHashMap<>();
        icons.put("item_component", new ItemStack(Items.IRON_INGOT));
        icons.put("fluid_component", new ItemStack(Items.BUCKET));
        icons.put("energy_component", new ItemStack(Items.REDSTONE));
        icons.put("liquid_conduit", new ItemStack(Items.STICK));
        icons.put("fluid_cell", new ItemStack(Items.GLASS_BOTTLE));
        icons.put("bitumen", new ItemStack(Items.CLAY_BALL));
        return new Assets(stations, Blocks.HOPPER.getDefaultState(), Blocks.CHEST.getDefaultState(),
            Blocks.CHEST.getDefaultState(), icons);
    }

    private static Map<String, PonderScene> lessons() {
        Map<String, PonderScene> lessons = new LinkedHashMap<>();
        for (int i = 0; i < CHAPTERS.size(); i++) {
            final int chapter = i;
            StoryBoardEntry entry = new PonderStoryBoardEntry((scene, util) ->
                OilProductionLineScenes.build(scene, util, chapter, fixture()), Ponder.MOD_ID,
                Ponder.asResource("mmce/empty"), OilProductionLinePlugin.COMPONENT);
            lessons.put(CHAPTERS.get(i), compile(entry));
        }
        return lessons;
    }

    private static PonderScene compile(StoryBoardEntry entry) {
        return PonderSceneRegistry.compileScene(new PonderLocalization(), entry);
    }

    private static BlockPos pos(Point p) { return new BlockPos(p.x, p.y, p.z); }

    private static String captions(PonderScene scene) {
        StringBuilder text = new StringBuilder();
        for (OverlayEvent event : scene.getOverlayEvents()) {
            if (event.getType() == OverlayEventType.TEXT) text.append(event.getText()).append('\n');
        }
        return text.toString();
    }

    private static Map<BlockPos, IBlockState> statesAt(PonderScene scene, int tick) {
        Map<BlockPos, IBlockState> states = new LinkedHashMap<>();
        for (WorldEvent event : scene.getWorldEvents()) {
            if (event.getTick() > tick) continue;
            boolean set = event.getType() == WorldEventType.SET_BLOCK
                || event.getType() == WorldEventType.SET_BLOCKS || event.getType() == WorldEventType.REPLACE_BLOCKS;
            if (!set && event.getType() != WorldEventType.DESTROY_BLOCK) continue;
            for (BlockPos p : event.getPositions()) {
                if (!set) { states.remove(p); continue; }
                String[] description = event.getStateDescription().split("#", 2);
                Block block = Block.REGISTRY.getObject(new ResourceLocation(description[0]));
                assertNotNull(block, event.getStateDescription());
                states.put(p, block.getStateFromMeta(description.length == 2
                    ? Integer.parseInt(description[1]) : 0));
            }
        }
        return states;
    }

    private static NBTTagCompound lastNbt(PonderScene scene, BlockPos position) throws Exception {
        NBTTagCompound last = null;
        for (WorldEvent event : scene.getWorldEvents()) {
            if (event.getType() == WorldEventType.APPLY_NBT && event.getPositions().contains(position)) {
                last = JsonToNBT.getTagFromJson(event.getStateDescription());
            }
        }
        return last;
    }

    private static boolean hasTankSnapshot(PonderScene scene, BlockPos position, String fluid, int amount)
        throws Exception {
        for (WorldEvent event : scene.getWorldEvents()) {
            if (event.getType() == WorldEventType.APPLY_NBT && event.getPositions().contains(position)) {
                NBTTagCompound tank = JsonToNBT.getTagFromJson(event.getStateDescription()).getCompoundTag("tank");
                if (fluid.equals(tank.getString("FluidName")) && amount == tank.getInteger("Amount")) return true;
            }
        }
        return false;
    }

    private static void assertTank(NBTTagCompound root, String fluid, int amount) {
        assertNotNull(root);
        NBTTagCompound tank = root.getCompoundTag("tank");
        assertEquals(fluid, tank.getString("FluidName"));
        assertEquals(amount, tank.getInteger("Amount"));
    }

    private static boolean contains(int[] values, int expected) {
        for (int value : values) if (value == expected) return true;
        return false;
    }
}
