package net.createmod.ponder.foundation.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.ActorKind;
import net.createmod.ponder.foundation.PonderScene.ActorEvent;
import net.createmod.ponder.foundation.PonderScene.ActorEventType;
import net.createmod.ponder.foundation.PonderScene.OverlayEvent;
import net.createmod.ponder.foundation.PonderScene.OverlayEventType;
import net.createmod.ponder.foundation.PonderScene.WorldEvent;
import net.createmod.ponder.foundation.PonderScene.WorldEventType;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.DefaultPonderSceneRegistrationHelper;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LegacySceneMigrationTest {

    private Object originalRegistry;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @BeforeEach
    void isolateMatcherRegistration() throws Exception {
        Field field = activeRegistry();
        originalRegistry = field.get(null);
        Constructor<?> constructor = field.getType().getDeclaredConstructor();
        constructor.setAccessible(true);
        field.set(null, constructor.newInstance());
    }

    @AfterEach
    void restoreMatcherRegistration() throws Exception {
        activeRegistry().set(null, originalRegistry);
    }

    @Test
    void complexExamplesKeepStableIdsAndOrdinaryViewsDoNotTurnTheCamera() {
        Map<String, PonderScene> scenes = complexScenes();
        assertEquals(new LinkedHashSet<String>(Arrays.asList("complex_factory_line", "complex_redstone_matrix",
            "complex_assembly_core", "complex_logistics_hub", "complex_gui_lab")), scenes.keySet());
        for (Map.Entry<String, PonderScene> entry : scenes.entrySet()) {
            if (!"complex_assembly_core".equals(entry.getKey())) {
                assertTrue(entry.getValue().getCameraEvents().isEmpty(), entry.getKey());
            }
        }
        assertFalse(scenes.get("complex_assembly_core").getCameraEvents().isEmpty());
        assertTrue(scenes.get("complex_assembly_core").getOverlayEvents().stream()
            .anyMatch(event -> event.getText() != null && event.getText().contains("诊断场景")));
    }

    @Test
    void complexLeverControlsBindTheLeverAndDescribeItsObservableResult() {
        Map<String, PonderScene> scenes = complexScenes();
        int controls = 0;
        for (String id : Arrays.asList("complex_factory_line", "complex_redstone_matrix")) {
            PonderScene scene = scenes.get(id);
            for (OverlayEvent event : scene.getOverlayEvents()) {
                if (event.getType() != OverlayEventType.CONTROLS) {
                    continue;
                }
                controls++;
                assertTrue(event.isControlRightClick());
                assertTrue(event.getControlItem().isEmpty(), "Operating a lever does not require redstone dust");
                assertEquals(1, event.getTargetPositions().size());
                BlockPos target = event.getTargetPositions().get(0);
                assertEquals(1, target.getX());
                assertEquals(1, target.getY(), "A top-face coordinate must not bind to AIR above the lever");
                assertTrue(scene.getOverlayEvents().stream().anyMatch(caption ->
                    caption.getType() == OverlayEventType.TEXT && caption.getTick() == event.getTick()
                        && caption.getDuration() == event.getDuration()
                        && caption.getTargetPositions().equals(event.getTargetPositions())
                        && caption.getText().contains("拉杆")));
                assertTrue(scene.getOverlayEvents().stream().anyMatch(outline ->
                    outline.getType() == OverlayEventType.SCENE_OUTLINE && outline.getTick() == event.getTick()
                        && outline.getSourcePositions().equals(event.getTargetPositions())));
            }
        }
        assertEquals(4, controls);
    }

    @Test
    void complexContainerImagesAndCaptionsShareOneExactBlockTarget() {
        PonderScene scene = complexScenes().get("complex_gui_lab");
        int imageCount = 0;
        for (OverlayEvent image : scene.getOverlayEvents()) {
            if (image.getType() != OverlayEventType.GUI_TEXTURE) {
                continue;
            }
            imageCount++;
            assertEquals(1, image.getTargetPositions().size());
            assertTrue(scene.getOverlayEvents().stream().anyMatch(caption ->
                caption.getType() == OverlayEventType.TEXT && caption.getTick() == image.getTick()
                    && caption.getDuration() == image.getDuration()
                    && caption.getTargetPositions().equals(image.getTargetPositions())),
                "Missing caption for image at " + image.getTick());
            assertTrue(scene.getOverlayEvents().stream().anyMatch(outline ->
                outline.getType() == OverlayEventType.SCENE_OUTLINE && outline.getTick() == image.getTick()
                    && outline.getSourcePositions().equals(image.getTargetPositions())),
                "Missing 3D source selection for image at " + image.getTick());
            assertEquals(0, image.getOffsetX());
            assertEquals(0, image.getOffsetY());
        }
        assertEquals(5, imageCount);
        assertTrue(scene.getKeyframeTicks().size() >= 5);
    }

    @Test
    void complexCaptionsDistinguishIllustrativeAnimationFromVanillaMechanics() {
        PonderScene factory = complexScenes().get("complex_factory_line");
        OverlayEvent railCaption = textContaining(factory, "轨道能直接运输掉落物");
        assertEquals(24, railCaption.getTargetPositions().size());
        assertTrue(textContaining(factory, "操作总控拉杆").getTargetPositions()
            .equals(Collections.singletonList(new BlockPos(1, 1, 6))));
        PonderScene logistics = complexScenes().get("complex_logistics_hub");
        assertFalse(textContaining(logistics, "不代表原版方块具备自动分类功能").getTargetPositions().isEmpty());
        assertEquals(4, logistics.getKeyframeTicks().size());
    }

    @Test
    void coordinateAndExplicitBoxApisKeepTheirOriginalThreeDimensionalSemantics() {
        PonderScene coordinates = compile(DebugScenes::coordinateScene, "ui_test/scene_1");
        Set<BlockPos> axisBlocks = new LinkedHashSet<BlockPos>();
        for (OverlayEvent event : coordinates.getOverlayEvents()) {
            if (event.getType() == OverlayEventType.SCENE_OUTLINE) {
                assertEquals(3, event.getSourcePositions().size());
                axisBlocks.addAll(event.getSourcePositions());
            }
        }
        assertEquals(9, axisBlocks.size());
        PonderScene tracking = compile((scene, util) -> {
            Object slot = "explicit.box.api.fixture";
            for (int i = 0; i < 10; i++) {
                scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, slot,
                    i % 2 == 0 ? new AxisAlignedBB(1.35D, 0, 0.35D, 1.65D, 2.5D, 0.65D)
                        : new AxisAlignedBB(0.05D, 1, 1.05D, 0.95D, 1.125D, 1.95D), 15);
                scene.idle(15);
            }
        }, "ui_test/scene_8");
        Set<String> slots = new LinkedHashSet<String>();
        int chasing = 0;
        for (OverlayEvent event : tracking.getOverlayEvents()) {
            if (event.getType() == OverlayEventType.SCENE_OUTLINE) {
                chasing++;
                assertTrue(event.isOutlineChasing());
                assertTrue(event.getSourcePositions().isEmpty());
                assertNotNull(event.getSceneBounds());
                slots.add(event.getOverlayId());
            }
        }
        assertEquals(10, chasing);
        assertEquals(1, slots.size());
        PonderScene particles = compile(DebugScenes::particleScene, "ui_test/scene_5");
        assertEquals(new Vec3d(2.5D, 2.25D, 2.5D), textContaining(particles, "生成火焰").getPointAt());
        assertFalse(particles.getCameraEvents().isEmpty());
    }

    @Test
    void migratedActorScenesKeepStableIdsAndSemanticStepsWithoutCameraOrPoiEvents() {
        Map<String, PonderScene> scenes = migratedActorScenes();
        assertEquals(new LinkedHashSet<String>(Arrays.asList("debug_birbs", "debug_tracking", "debug_items")),
            scenes.keySet());
        for (PonderScene scene : scenes.values()) {
            assertTrue(scene.getCameraEvents().isEmpty(), scene.getSceneId().toString());
            assertTrue(scene.getPointOfInterestEvents().isEmpty(), scene.getSceneId().toString());
            assertTrue(scene.getKeyframeTicks().size() >= 5, scene.getSceneId().toString());
            for (OverlayEvent caption : scene.getOverlayEvents()) {
                if (caption.getType() != OverlayEventType.TEXT) {
                    continue;
                }
                assertTrue(scene.getKeyframeTicks().contains(Integer.valueOf(caption.getTick())));
                assertTrue(caption.getTick() + caption.getDuration() <= scene.getTotalIdleTicks());
                assertFalse(caption.getText().contains("游标"));
                assertFalse(caption.getText().contains("视线跟随"));
                assertFalse(caption.getText().contains("POI"));
            }
        }
    }

    @Test
    void legacyCharacterAndTrackingScenesUseOneMovingActingPlayerInsteadOfPlaceholderActors() {
        for (String id : Arrays.asList("debug_birbs", "debug_tracking")) {
            PonderScene scene = migratedActorScenes().get(id);
            Set<ActorEventType> actions = new LinkedHashSet<ActorEventType>();
            int spawns = 0;
            for (ActorEvent event : scene.getActorEvents()) {
                assertEquals(ActorKind.PLAYER, event.getActorKind(), id);
                actions.add(event.getType());
                if (event.getType() == ActorEventType.SPAWN) {
                    spawns++;
                }
            }
            assertEquals(1, spawns, id);
            assertTrue(actions.containsAll(Arrays.asList(ActorEventType.MOVE, ActorEventType.LOOK_AT,
                ActorEventType.HELD_ITEM, ActorEventType.SWING, ActorEventType.SNEAK)), id);
            assertTrue(scene.getActorEvents().stream().anyMatch(event -> event.getType() == ActorEventType.SNEAK
                && event.getAngle() != 0));
            assertTrue(scene.getActorEvents().stream().anyMatch(event -> event.getType() == ActorEventType.SNEAK
                && event.getAngle() == 0));
        }
    }

    @Test
    void migratedPlayersKeepSupportedFeetAndUnobstructedBodiesAcrossTheirRoutes() throws Exception {
        for (String id : Arrays.asList("debug_birbs", "debug_tracking")) {
            PonderScene scene = migratedActorScenes().get(id);
            for (int tick = 10; tick <= scene.getTotalIdleTicks(); tick += 5) {
                Object player = playerState(scene, tick);
                Vec3d position = (Vec3d) runtimeField(player, "position");
                assertEquals(1.0D, position.y, 1.0E-8D, id + " at " + tick);
                Map<BlockPos, ?> blocks = runtimeBlocks(scene, tick);
                Object floor = blocks.get(new BlockPos(position.x, position.y - 0.01D, position.z));
                assertNotNull(floor, id + " at " + tick);
                assertEquals(Boolean.TRUE, runtimeField(floor, "visible"));
                assertEquals(Blocks.STONE, ((IBlockState) runtimeField(floor, "currentState")).getBlock());
                for (int height = 0; height < 2; height++) {
                    Object body = blocks.get(new BlockPos(position.x, position.y + height, position.z));
                    if (body != null) {
                        assertEquals(Blocks.AIR, ((IBlockState) runtimeField(body, "currentState")).getBlock());
                    }
                }
            }
        }
    }

    @Test
    void characterPlacementAndRealLookActionsMatchTheirOutlinedGoldTargets() throws Exception {
        PonderScene scene = migratedActorScenes().get("debug_birbs");
        for (int tick : Arrays.asList(52, 84, 120)) {
            OverlayEvent caption = scene.getOverlayEvents().stream().filter(event ->
                event.getType() == OverlayEventType.TEXT && event.getTick() == tick).findFirst().get();
            assertEquals(1, caption.getTargetPositions().size());
            BlockPos target = caption.getTargetPositions().get(0);
            assertEquals(Blocks.GOLD_BLOCK,
                ((IBlockState) runtimeField(runtimeBlocks(scene, tick).get(target), "currentState")).getBlock());
            assertTrue(scene.getActorEvents().stream().anyMatch(event -> event.getType() == ActorEventType.LOOK_AT
                && event.getTick() == tick && event.getLocation().equals(new Vec3d(target).add(0.5, 0.5, 0.5))));
            assertTrue(scene.getOverlayEvents().stream().anyMatch(event -> event.getType() == OverlayEventType.SCENE_OUTLINE
                && event.getTick() == tick && event.getSourcePositions().equals(caption.getTargetPositions())));
        }
        assertTrue(scene.getActorEvents().stream().anyMatch(event -> event.getTick() == 52
            && event.getType() == ActorEventType.SWING));
        assertTrue(scene.getActorEvents().stream().anyMatch(event -> event.getTick() == 52
            && event.getType() == ActorEventType.HELD_ITEM
            && event.getItemStack().getItem() == Item.getItemFromBlock(Blocks.GOLD_BLOCK)));
        float rightYaw = ((Float) runtimeField(playerState(scene, 64), "yaw")).floatValue();
        float leftYaw = ((Float) runtimeField(playerState(scene, 96), "yaw")).floatValue();
        assertTrue(Math.abs(rightYaw - leftYaw) > 30, "The character must actually turn between the gold targets");
    }

    @Test
    void trackingUsesOneSelectionSlotWithVisibleBlocksAndSynchronizedCaptions() throws Exception {
        PonderScene scene = migratedActorScenes().get("debug_tracking");
        Set<String> slots = new LinkedHashSet<String>();
        int outlines = 0;
        boolean scattered = false;
        for (OverlayEvent outline : scene.getOverlayEvents()) {
            if (outline.getType() != OverlayEventType.SCENE_OUTLINE) {
                continue;
            }
            outlines++;
            assertTrue(outline.isOutlineChasing());
            assertFalse(outline.getSourcePositions().isEmpty());
            slots.add(outline.getOverlayId());
            scattered |= outline.getSourcePositions().size() == 2;
            Map<BlockPos, ?> blocks = runtimeBlocks(scene, outline.getTick());
            for (BlockPos target : outline.getSourcePositions()) {
                assertEquals(Boolean.TRUE, runtimeField(blocks.get(target), "visible"));
                IBlockState state = (IBlockState) runtimeField(blocks.get(target), "currentState");
                assertTrue(state.getBlock() == Blocks.GOLD_BLOCK || state.getBlock() == Blocks.DIAMOND_BLOCK);
            }
            assertTrue(scene.getOverlayEvents().stream().anyMatch(caption ->
                caption.getType() == OverlayEventType.TEXT && caption.getTick() == outline.getTick()
                    && caption.getDuration() == outline.getDuration()
                    && caption.getTargetPositions().equals(outline.getSourcePositions())));
        }
        assertEquals(4, outlines);
        assertEquals(1, slots.size());
        assertTrue(scattered);
    }

    @Test
    void playerPoseAndPositionRemainDeterministicAfterRevisitingEarlierSteps() throws Exception {
        for (String id : Arrays.asList("debug_birbs", "debug_tracking")) {
            PonderScene scene = migratedActorScenes().get(id);
            Object before = playerState(scene, 96);
            playerState(scene, 24);
            Object after = playerState(scene, 96);
            for (String field : Arrays.asList("position", "yaw", "pitch", "sneaking", "swingProgress")) {
                assertEquals(runtimeField(before, field), runtimeField(after, field), id + ": " + field);
            }
        }
    }

    @Test
    void itemSceneRecordsThreeNonemptyStacksAndExplicitContinuousSeventyTwoTickRoutes() {
        PonderScene scene = migratedActorScenes().get("debug_items");
        Set<Integer> spawned = new LinkedHashSet<Integer>();
        Set<Integer> moved = new LinkedHashSet<Integer>();
        Set<Object> items = new LinkedHashSet<Object>();
        for (ActorEvent event : scene.getActorEvents()) {
            assertEquals(ActorKind.ITEM, event.getActorKind());
            assertEquals(20, event.getTick());
            if (event.getType() == ActorEventType.SPAWN) {
                spawned.add(Integer.valueOf(event.getActorId()));
                assertFalse(event.getItemStack().isEmpty());
                items.add(event.getItemStack().getItem());
            } else {
                assertEquals(ActorEventType.MOVE, event.getType());
                assertTrue(moved.add(Integer.valueOf(event.getActorId())), "Do not add the old implicit 20-tick motion");
                assertEquals(72, event.getDuration());
                assertTrue(event.getOffset().lengthSquared() > 1);
            }
        }
        assertEquals(3, spawned.size());
        assertEquals(spawned, moved);
        assertTrue(items.contains(Items.REDSTONE));
        assertTrue(items.contains(Items.COMPASS));
        assertTrue(items.contains(Item.getItemFromBlock(Blocks.GOLD_BLOCK)));
    }

    @Test
    void movingItemNotesStayAtTheEdgeAndDistinguishMovementFromTheStoppedEndpoint() {
        PonderScene scene = migratedActorScenes().get("debug_items");
        for (OverlayEvent caption : scene.getOverlayEvents()) {
            assertEquals(OverlayEventType.TEXT, caption.getType());
            assertTrue(caption.getTargetPositions().isEmpty());
            assertEquals(null, caption.getPointAt(), "A stationary spawn point cannot track a moving item");
        }
        for (int tick : Arrays.asList(20, 44, 68)) {
            OverlayEvent caption = scene.getOverlayEvents().stream().filter(event -> event.getTick() == tick)
                .findFirst().get();
            assertEquals(24, caption.getDuration());
            assertTrue(tick + caption.getDuration() <= 92);
        }
        assertEquals(92, textContaining(scene, "到达各自终点并停止").getTick());
        assertTrue(textContaining(scene, "不模拟掉落物的重力或拾取").getTargetPositions().isEmpty());
        assertEquals(122, scene.getTotalIdleTicks());
    }

    @Test
    void legacySectionCaptionsRetainTheirSpecificSourceBlocks() {
        PonderScene scene = compile(DebugScenes::sectionsScene, "ui_test/scene_8");
        assertEquals(Collections.singletonList(new BlockPos(1, 1, 1)),
            textContaining(scene, "合并到底板区段").getTargetPositions());
        assertEquals(Collections.singletonList(new BlockPos(3, 1, 1)),
            textContaining(scene, "属于独立区段").getTargetPositions());
        OverlayEvent irregular = textContaining(scene, "不规则选区");
        assertEquals(7, irregular.getTargetPositions().size());
        assertFalse(irregular.getTargetPositions().contains(new BlockPos(4, 1, 3)));
        assertFalse(irregular.getTargetPositions().contains(new BlockPos(2, 1, 3)));
    }

    @Test
    void guiDiagnosticRetainsIntentionalOffsetsButBindsEveryImageToItsExactBlock() {
        PonderScene scene = compile(DebugScenes::guiSnapshotScene, "ui_test/runtime_test");
        int images = 0;
        for (OverlayEvent event : scene.getOverlayEvents()) {
            if (event.getType() == OverlayEventType.GUI_TEXTURE) {
                images++;
                assertEquals(1, event.getTargetPositions().size());
                assertEquals(2, event.getTargetPositions().get(0).getY());
            }
        }
        assertEquals(8, images);
        assertTrue(scene.getOverlayEvents().stream().anyMatch(event -> event.getOffsetY() == 188));
        assertTrue(textContaining(scene, "保留刻意设置的面板偏移").getTargetPositions()
            .equals(Collections.singletonList(new BlockPos(2, 2, 2))));
        OverlayEvent outline = scene.getOverlayEvents().stream()
            .filter(event -> event.getType() == OverlayEventType.SCENE_OUTLINE).findFirst().get();
        assertEquals(Arrays.asList(new BlockPos(1, 2, 2), new BlockPos(2, 2, 2), new BlockPos(3, 2, 2)),
            outline.getSourcePositions());
        assertNotNull(outline.getSceneBounds());
        assertEquals(20, outline.getTick());
        assertEquals(234, outline.getDuration());
        assertEquals(scene.getTotalIdleTicks(), outline.getTick() + outline.getDuration());
        for (OverlayEvent image : scene.getOverlayEvents()) {
            if (image.getType() == OverlayEventType.GUI_TEXTURE) {
                assertTrue(outline.getTick() <= image.getTick());
                assertTrue(outline.getTick() + outline.getDuration() >= image.getTick() + image.getDuration());
            }
        }
    }

    @Test
    void guiDiagnosticClearsOldFixtureAndLabelsItsFurnaceAndGuiReferenceTargets() {
        PonderScene scene = compile(DebugScenes::guiSnapshotScene, "ui_test/runtime_test");
        int removed = 0;
        for (Map.Entry<BlockPos, IBlockState> original : scene.getSchematic().getBlocks().entrySet()) {
            if (original.getKey().getY() > 0) {
                assertEquals(Blocks.AIR, finalState(scene, original.getKey()).getBlock());
                removed++;
            } else {
                assertEquals(original.getValue(), finalState(scene, original.getKey()));
            }
        }
        assertEquals(9, removed);
        for (int x = 1; x <= 3; x++) {
            assertEquals(Blocks.STONEBRICK, finalState(scene, new BlockPos(x, 1, 2)).getBlock());
        }
        assertEquals(Blocks.FURNACE, finalState(scene, new BlockPos(1, 2, 2)).getBlock());
        assertEquals(Blocks.IRON_BLOCK, finalState(scene, new BlockPos(2, 2, 2)).getBlock());
        assertEquals(Blocks.GOLD_BLOCK, finalState(scene, new BlockPos(3, 2, 2)).getBlock());
        OverlayEvent caption = textContaining(scene, "热力 GUI 参考定位块");
        assertTrue(caption.getText().contains("不代表机器外观"));
    }

    @Test
    void everyGuiDiagnosticImageTargetsARealVisibleBlockAtStartMiddleAndLastActiveTick() throws Exception {
        PonderScene scene = compile(DebugScenes::guiSnapshotScene, "ui_test/runtime_test");
        int samples = 0;
        for (OverlayEvent image : scene.getOverlayEvents()) {
            if (image.getType() != OverlayEventType.GUI_TEXTURE) {
                continue;
            }
            int[] ticks = { image.getTick(), image.getTick() + image.getDuration() / 2,
                image.getTick() + image.getDuration() - 1 };
            for (int tick : ticks) {
                Map<BlockPos, ?> runtime = runtimeBlocks(scene, tick);
                for (BlockPos target : image.getTargetPositions()) {
                    Object block = runtime.get(target);
                    assertNotNull(block, target + " at " + tick);
                    IBlockState state = (IBlockState) runtimeField(block, "currentState");
                    assertNotNull(state);
                    assertFalse(state.getBlock() == Blocks.AIR, target + " at " + tick);
                    assertEquals(Boolean.TRUE, runtimeField(block, "visible"));
                    assertEquals(Boolean.FALSE, runtimeField(block, "destroyed"));
                    assertTrue(((Number) runtimeField(block, "fade")).floatValue() > 0.0F);
                    samples++;
                }
            }
        }
        assertEquals(24, samples);
    }

    @Test
    void runtimeDiagnosticKeepsCameraTestWhileDescribingBothActualSections() {
        PonderScene scene = compile(TestScenes::runtimeTestScene, "ui_test/runtime_test");
        assertEquals(3, scene.getCameraEvents().size());
        List<BlockPos> targets = textContaining(scene, "本诊断保留三次自动转镜头").getTargetPositions();
        assertEquals(8, targets.size());
        assertTrue(targets.containsAll(Arrays.asList(new BlockPos(1, 1, 3), new BlockPos(2, 2, 4))));
        PonderScene showcase = compile(TestScenes::visualShowcaseScene, "ui_test/scene_8");
        assertEquals(Collections.singletonList(new BlockPos(3, 6, 3)),
            textContaining(showcase, "中心光源").getTargetPositions());
    }

    @Test
    void thermalPanelsAndGuiHighlightsRetainSharedTargetAndNeverOutliveTheirParent() {
        PonderSceneRegistry registry = new PonderSceneRegistry(new PonderLocalization());
        ThermalExpansionScenes.registerAll(new DefaultPonderSceneRegistrationHelper("thermalexpansion", registry));
        assertEquals(16, registry.getRegisteredEntries().size());
        for (Map.Entry<ResourceLocation, StoryBoardEntry> registration : registry.getRegisteredEntries()) {
            PonderScene scene = PonderSceneRegistry.compileScene(new PonderLocalization(), registration.getValue());
            Map<String, OverlayEvent> images = new LinkedHashMap<String, OverlayEvent>();
            for (OverlayEvent event : scene.getOverlayEvents()) {
                assertEquals(Collections.singletonList(new BlockPos(2, 2, 2)),
                    event.getType() == OverlayEventType.SCENE_OUTLINE
                        ? event.getSourcePositions() : event.getTargetPositions(),
                    scene.getSceneId() + " " + event.getType());
                if (event.getType() == OverlayEventType.GUI_TEXTURE) {
                    images.put(event.getOverlayId(), event);
                }
            }
            assertEquals(4, images.size());
            OverlayEvent main = images.get("main");
            assertNotNull(main);
            assertTrue(main.getTick() + main.getDuration() >= scene.getTotalIdleTicks());
            assertTrue(scene.getOverlayEvents().stream().anyMatch(event ->
                event.getType() == OverlayEventType.SCENE_OUTLINE
                    && event.getTick() + event.getDuration() >= scene.getTotalIdleTicks()));
            for (OverlayEvent event : scene.getOverlayEvents()) {
                if (event.getParentOverlayId() == null) {
                    continue;
                }
                OverlayEvent parent = images.get(event.getParentOverlayId());
                assertNotNull(parent, event.getParentOverlayId());
                assertTrue(parent.getTick() <= event.getTick());
                assertTrue(parent.getTick() + parent.getDuration() >= event.getTick() + event.getDuration(),
                    scene.getSceneId() + " child " + event.getOverlayId());
            }
            if (registration.getKey().equals(ThermalExpansionScenes.MACHINE_INSOLATOR)) {
                assertTrue(scene.getTitle().contains("光照育生机"));
            }
        }
    }

    @Test
    void thermalPreviewClearsTheLegacyFixtureAndKeepsOnlyItsBaseplateAndSupportedTarget() {
        PonderScene scene = thermalScene(ThermalExpansionScenes.MACHINE_FURNACE, metadata -> null);
        int removed = 0;
        for (Map.Entry<BlockPos, IBlockState> original : scene.getSchematic().getBlocks().entrySet()) {
            if (original.getKey().getY() > 0) {
                assertEquals(Blocks.AIR, finalState(scene, original.getKey()).getBlock(),
                    original.getKey().toString());
                removed++;
            } else {
                assertEquals(original.getValue(), finalState(scene, original.getKey()));
            }
        }
        assertEquals(9, removed);
        assertEquals(Blocks.STONEBRICK, finalState(scene, new BlockPos(2, 1, 2)).getBlock());
        assertEquals(Blocks.IRON_BLOCK, finalState(scene, new BlockPos(2, 2, 2)).getBlock());
        assertTrue(textContaining(scene, "未加载对应机器").getText().contains("不代表机器外观"));
    }

    @Test
    void thermalResolverUsesEachMachinesExactMetadataAndPlacesItsReturnedState() {
        int[] expectedMetadata = { 0, 9, 4, 1, 2, 3, 12, 10, 5, 11, 6, 13, 15, 14, 7, 8 };
        ResourceLocation[] machines = ThermalExpansionScenes.getAllMachineIds();
        assertEquals(expectedMetadata.length, machines.length);
        for (int index = 0; index < machines.length; index++) {
            int[] resolvedMetadata = { -1 };
            PonderScene scene = thermalScene(machines[index], metadata -> {
                resolvedMetadata[0] = metadata;
                return Blocks.WOOL.getStateFromMeta(metadata);
            });
            assertEquals(expectedMetadata[index], resolvedMetadata[0], machines[index].toString());
            assertEquals(Blocks.WOOL.getStateFromMeta(expectedMetadata[index]),
                finalState(scene, new BlockPos(2, 2, 2)));
            assertFalse(scene.getOverlayEvents().stream().anyMatch(event -> event.getText() != null
                && event.getText().contains("未加载对应机器")));
            List<OverlayEvent> gui = scene.getOverlayEvents().stream()
                .filter(event -> event.getType() == OverlayEventType.GUI_TEXTURE).toList();
            assertEquals(1, gui.size());
            assertNotNull(gui.get(0).getGuiSnapshotProvider());
            assertTrue(scene.getOverlayEvents().stream().filter(event -> !event.getText().isEmpty())
                .allMatch(event -> event.getTick() + event.getDuration() <= gui.get(0).getTick()),
                "The summary must finish before the native GUI starts on its own page");
            assertTrue(scene.getOverlayEvents().stream().noneMatch(event -> event.getType() == OverlayEventType.GUI_HIGHLIGHT));
            assertEquals(expectedMetadata[index], ThermalExpansionScenes.thermalRecording(expectedMetadata[index])
                .getAsJsonObject("source").get("meta").getAsInt());
        }
    }

    @Test
    void absentAirAndBrokenOptionalMachineStatesAlwaysUseAnExplicitlyLabelledVisiblePlaceholder() {
        List<ThermalExpansionScenes.MachineStateResolver> unavailable = Arrays.asList(
            metadata -> null,
            metadata -> Blocks.AIR.getDefaultState(),
            metadata -> { throw new IllegalArgumentException("bad metadata"); },
            metadata -> { throw new NoClassDefFoundError("optional machine dependency"); });
        for (ThermalExpansionScenes.MachineStateResolver resolver : unavailable) {
            PonderScene scene = thermalScene(ThermalExpansionScenes.MACHINE_CHARGER, resolver);
            assertEquals(Blocks.IRON_BLOCK, finalState(scene, new BlockPos(2, 2, 2)).getBlock());
            OverlayEvent caption = textContaining(scene, "未加载对应机器");
            assertTrue(caption.getText().contains("GUI 参考"));
            assertTrue(caption.getText().contains("铁块占位"));
            assertTrue(caption.getText().contains("不代表机器外观"));
            assertTrue(scene.getTitle().contains("铁块占位"));
            assertEquals(Collections.singletonList(new BlockPos(2, 2, 2)), caption.getTargetPositions());
        }
    }

    @Test
    void legacyWorkbenchTemplateStillUsesTheModernPlayerLessonAndPublicId() {
        PonderScene scene = compile(TemplateScenes::templateMethod, "mmce/empty");
        assertEquals(Ponder.asResource("crafting_table"), scene.getSceneId());
        assertTrue(scene.getActorEvents().stream().anyMatch(event -> event.getActorKind() == ActorKind.PLAYER));
        assertTrue(scene.getCameraEvents().isEmpty());
        assertEquals(Collections.singletonList(new BlockPos(3, 1, 3)),
            scene.getOverlayEvents().stream().filter(event -> event.getType() == OverlayEventType.GUI_TEXTURE)
                .findFirst().get().getTargetPositions());
    }

    private static Map<String, PonderScene> migratedActorScenes() {
        Map<String, PonderScene> result = new LinkedHashMap<String, PonderScene>();
        result.put("debug_birbs", compile(DebugScenes::birbScene, "ui_test/scene_7"));
        result.put("debug_tracking", compile(DebugScenes::trackingScene, "ui_test/scene_8"));
        result.put("debug_items", compile(DebugScenes::itemScene, "ui_test/scene_9"));
        for (Map.Entry<String, PonderScene> entry : result.entrySet()) {
            assertEquals(Ponder.asResource(entry.getKey()), entry.getValue().getSceneId());
        }
        return result;
    }

    private static Object playerState(PonderScene scene, int tick) throws Exception {
        Class<?> timeline = Class.forName("net.createmod.ponder.foundation.ui.PreviewPlayerTimeline");
        Method evaluate = timeline.getDeclaredMethod("evaluate", List.class, float.class);
        evaluate.setAccessible(true);
        List<?> players = (List<?>) evaluate.invoke(null, scene.getActorEvents(), Float.valueOf(tick));
        assertEquals(1, players.size(), scene.getSceneId().toString());
        return players.get(0);
    }

    private static Map<String, PonderScene> complexScenes() {
        PonderSceneRegistry registry = new PonderSceneRegistry(new PonderLocalization());
        new ComplexTestPonderPlugin().registerScenes(new DefaultPonderSceneRegistrationHelper(Ponder.MOD_ID, registry));
        Map<String, PonderScene> result = new LinkedHashMap<String, PonderScene>();
        for (Map.Entry<ResourceLocation, StoryBoardEntry> entry : registry.getRegisteredEntries()) {
            PonderScene scene = PonderSceneRegistry.compileScene(new PonderLocalization(), entry.getValue());
            result.put(scene.getSceneId().getPath(), scene);
        }
        return result;
    }

    private static OverlayEvent textContaining(PonderScene scene, String expected) {
        return scene.getOverlayEvents().stream().filter(event -> event.getType() == OverlayEventType.TEXT
            && event.getText() != null && event.getText().contains(expected)).findFirst()
            .orElseThrow(() -> new AssertionError("Missing text: " + expected));
    }

    private static PonderScene compile(PonderStoryBoard storyboard, String schematic) {
        StoryBoardEntry entry = new PonderStoryBoardEntry(storyboard, Ponder.MOD_ID, Ponder.asResource(schematic),
            new ResourceLocation("minecraft", "stone"));
        return PonderSceneRegistry.compileScene(new PonderLocalization(), entry);
    }

    private static PonderScene thermalScene(ResourceLocation componentId,
        ThermalExpansionScenes.MachineStateResolver resolver) {
        return compile((scene, util) -> ThermalExpansionScenes.machineScene(scene, util, componentId, resolver),
            "ui_test/runtime_test");
    }

    private static IBlockState finalState(PonderScene scene, BlockPos position) {
        IBlockState result = scene.getSchematic().getBlocks().getOrDefault(position, Blocks.AIR.getDefaultState());
        for (WorldEvent event : scene.getWorldEvents()) {
            if (!event.getPositions().contains(position)) {
                continue;
            }
            if (event.getType() == WorldEventType.SET_BLOCK || event.getType() == WorldEventType.SET_BLOCKS
                || event.getType() == WorldEventType.REPLACE_BLOCKS) {
                String[] encoded = event.getStateDescription().split("#", 2);
                Block block = Block.REGISTRY.getObject(new ResourceLocation(encoded[0]));
                assertNotNull(block, event.getStateDescription());
                result = block.getStateFromMeta(encoded.length == 2 ? Integer.parseInt(encoded[1]) : 0);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<BlockPos, ?> runtimeBlocks(PonderScene scene, int tick) throws Exception {
        Class<?> runtime = Class.forName("net.createmod.ponder.foundation.ui.PonderSceneRuntime");
        Method build = runtime.getDeclaredMethod("buildState", PonderScene.class, int.class);
        build.setAccessible(true);
        Object state = build.invoke(null, scene, Integer.valueOf(tick));
        return (Map<BlockPos, ?>) runtimeField(state, "blocksByPosition");
    }

    private static Object runtimeField(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static Field activeRegistry() throws Exception {
        Field field = PonderIndex.class.getDeclaredField("active");
        field.setAccessible(true);
        return field;
    }
}
