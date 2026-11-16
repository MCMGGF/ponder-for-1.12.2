package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSchematic;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PreviewBlockPipelineTest {

    @BeforeAll
    static void registerBlocks() {
        Bootstrap.register();
    }

    @Test
    void fadingOpaqueGeometryIsSubmittedOnlyToTheTransparentPass() throws Exception {
        PonderScene scene = scene(Blocks.STONE.getDefaultState());
        Object frame = frame(new PonderDebugScreen(null, 0), scene, 2F);
        List<PonderSceneRuntime.RuntimeBlockState> transparent = blocks(frame, "translucent");
        assertEquals(1, transparent.size(), "partially faded stone needs blending without depth writes");
        assertTrue(transparent.get(0).fade > 0F && transparent.get(0).fade < 1F);
        assertTrue(blocks(frame, "solid").isEmpty());
    }

    @Test
    void opaqueAndTranslucentLayersOfOneBlockAreSubmittedToBothPasses() throws Exception {
        Block layered = new Block(Material.ROCK) {
            @Override
            public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
                return layer == BlockRenderLayer.SOLID || layer == BlockRenderLayer.TRANSLUCENT;
            }
        };
        Object frame = frame(new PonderDebugScreen(null, 0), scene(layered.getDefaultState()), 20F);
        assertEquals(1, blocks(frame, "solid").size());
        assertEquals(1, blocks(frame, "translucent").size(),
            "Forge multilayer windows must not be folded into their opaque shell");
        Field cachedLayers = frame.getClass().getDeclaredField("layers");
        cachedLayers.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<PonderSceneRuntime.RuntimeBlockState, List<BlockRenderLayer>> planned =
            (Map<PonderSceneRuntime.RuntimeBlockState, List<BlockRenderLayer>>) cachedLayers.get(frame);
        assertEquals(Arrays.asList(BlockRenderLayer.SOLID, BlockRenderLayer.TRANSLUCENT),
            planned.get(blocks(frame, "solid").get(0)));
    }

    @Test
    void fullyVisibleStoneRemainsInTheOpaquePass() throws Exception {
        Object frame = frame(new PonderDebugScreen(null, 0), scene(Blocks.STONE.getDefaultState()), 20F);
        assertEquals(1, blocks(frame, "solid").size());
        assertFalse(blocks(frame, "solid").get(0).fade < 1F);
        assertTrue(blocks(frame, "translucent").isEmpty());
    }

    @Test
    void brokenThirdPartyLayerDiscoveryDoesNotRemoveHealthyBlocks() throws Exception {
        for (boolean linkage : new boolean[] { false, true }) {
            Block broken = new Block(Material.ROCK) {
                @Override
                public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
                    if (linkage) throw new NoSuchMethodError("legacy renderer");
                    throw new IllegalArgumentException("invalid layer");
                }
            };
            Map<BlockPos, IBlockState> states = new LinkedHashMap<BlockPos, IBlockState>();
            states.put(BlockPos.ORIGIN, broken.getDefaultState());
            states.put(BlockPos.ORIGIN.east(), Blocks.STONE.getDefaultState());
            Object frame = frame(new PonderDebugScreen(null, 0), scene(states), 20F);
            assertEquals(1, blocks(frame, "solid").size());
            assertEquals(BlockPos.ORIGIN.east(), blocks(frame, "solid").get(0).pos);
        }
    }

    @Test
    void transparentFrameUsesFarToNearGuiDepthAndReversesWhenCameraTurns() throws Exception {
        BlockPos near = new BlockPos(0, 0, 3);
        BlockPos far = new BlockPos(0, 0, -3);
        Map<BlockPos, IBlockState> states = new LinkedHashMap<BlockPos, IBlockState>();
        states.put(near, Blocks.STAINED_GLASS.getDefaultState());
        states.put(far, Blocks.STAINED_GLASS.getDefaultState());
        PonderScene scene = scene(states);
        PonderDebugScreen screen = new PonderDebugScreen(null, 0);
        camera(screen, 0F, 0F);
        Object frame = frame(screen, scene, 20F);
        sort(screen, frame, scene);
        assertEquals(Arrays.asList(far, near), positions(blocks(frame, "translucent")));
        camera(screen, 180F, 0F);
        sort(screen, frame, scene);
        assertEquals(Arrays.asList(near, far), positions(blocks(frame, "translucent")));
    }

    @Test
    void rotatedTransparentFrameUsesTransformedDepthNotAuthoredZ() throws Exception {
        BlockPos near = new BlockPos(-3, 0, 0);
        BlockPos far = new BlockPos(3, 0, 0);
        Map<BlockPos, IBlockState> states = new LinkedHashMap<BlockPos, IBlockState>();
        states.put(near, Blocks.STAINED_GLASS.getDefaultState());
        states.put(far, Blocks.STAINED_GLASS.getDefaultState());
        PonderScene scene = scene(states);
        PonderDebugScreen screen = new PonderDebugScreen(null, 0);
        camera(screen, 90F, 30F);
        Object frame = frame(screen, scene, 20F);
        sort(screen, frame, scene);
        assertEquals(Arrays.asList(far, near), positions(blocks(frame, "translucent")));
    }

    @Test
    void separatedSectionsExposeFacesOnBothTheMovingAndStationaryBlock() throws Exception {
        PonderScene scene = adjacentScene();
        scene.addIdleTicks(20);
        scene.recordSectionMove(Collections.singletonList(BlockPos.ORIGIN.east()), new Vec3d(2D, 0D, 0D), 10);
        assertNeighborFaces(scene, true);
    }

    @Test
    void equallyMovedSectionsKeepTheirInternalFacesCulled() throws Exception {
        PonderScene scene = adjacentScene();
        scene.addIdleTicks(20);
        scene.recordSectionMove(Arrays.asList(BlockPos.ORIGIN, BlockPos.ORIGIN.east()),
            new Vec3d(2D, 1D, -3D), 10);
        assertNeighborFaces(scene, false);
    }

    @Test
    void differentlyRotatedSectionsExposeTheirOriginalSharedFaces() throws Exception {
        PonderScene scene = adjacentScene();
        scene.addIdleTicks(20);
        scene.recordSectionRotation(Collections.singletonList(BlockPos.ORIGIN.east()),
            new Vec3d(1.5D, 0.5D, 0.5D), new Vec3d(0D, 90D, 0D), 10);
        assertNeighborFaces(scene, true);
    }

    @Test
    void equallyRotatedSectionsKeepTheirInternalFacesCulled() throws Exception {
        PonderScene scene = adjacentScene();
        scene.addIdleTicks(20);
        scene.recordSectionRotation(Arrays.asList(BlockPos.ORIGIN, BlockPos.ORIGIN.east()),
            new Vec3d(0.5D, 0.5D, 0.5D), new Vec3d(0D, 90D, 0D), 10);
        assertNeighborFaces(scene, false);
    }

    @Test
    void forgeLayerScopeRestoresNullAndNonNullParentsIncludingNestedFailures() {
        BlockRenderLayer original = MinecraftForgeClient.getRenderLayer();
        try {
            for (BlockRenderLayer parent : new BlockRenderLayer[] { null, BlockRenderLayer.CUTOUT_MIPPED }) {
                ForgeHooksClient.setRenderLayer(parent);
                PreviewBlockRendering.inLayer(BlockRenderLayer.SOLID, () -> {
                    assertSame(BlockRenderLayer.SOLID, MinecraftForgeClient.getRenderLayer());
                    assertThrows(IllegalArgumentException.class,
                        () -> PreviewBlockRendering.inLayer(BlockRenderLayer.TRANSLUCENT, () -> {
                            assertSame(BlockRenderLayer.TRANSLUCENT, MinecraftForgeClient.getRenderLayer());
                            throw new IllegalArgumentException("broken model");
                        }));
                    assertSame(BlockRenderLayer.SOLID, MinecraftForgeClient.getRenderLayer());
                });
                assertSame(parent, MinecraftForgeClient.getRenderLayer());
                assertThrows(NoSuchMethodError.class,
                    () -> PreviewBlockRendering.inLayer(BlockRenderLayer.CUTOUT, () -> {
                        throw new NoSuchMethodError("legacy model");
                    }));
                assertSame(parent, MinecraftForgeClient.getRenderLayer());
            }
        } finally {
            ForgeHooksClient.setRenderLayer(original);
        }
    }

    @Test
    void layerSensitiveModelsReceiveOneMatchingSubmissionForEachSupportedLayer() {
        Block layered = new Block(Material.ROCK) {
            @Override
            public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
                return layer == BlockRenderLayer.SOLID || layer == BlockRenderLayer.TRANSLUCENT;
            }
        };
        List<BlockRenderLayer> observed = new java.util.ArrayList<BlockRenderLayer>();
        IBakedModel model = (IBakedModel) Proxy.newProxyInstance(IBakedModel.class.getClassLoader(),
            new Class<?>[] { IBakedModel.class }, (proxy, method, arguments) -> {
                if ("getQuads".equals(method.getName())) {
                    observed.add(MinecraftForgeClient.getRenderLayer());
                    return Collections.emptyList();
                }
                throw new AssertionError("unexpected model call: " + method.getName());
            });
        PonderSceneRuntime.RuntimeBlockState block = new PonderSceneRuntime.RuntimeBlockState(BlockPos.ORIGIN);
        block.currentState = layered.getDefaultState();
        block.fade = 1F;
        for (boolean transparent : new boolean[] { false, true }) {
            for (BlockRenderLayer layer : PreviewBlockRendering.modelLayers(block.currentState)) {
                if (PreviewBlockRendering.transparent(block, layer) == transparent) {
                    PreviewBlockRendering.inLayer(layer,
                        () -> model.getQuads(block.currentState, null, 0L));
                }
            }
        }
        assertEquals(Arrays.asList(BlockRenderLayer.SOLID, BlockRenderLayer.TRANSLUCENT), observed);
    }

    private static void assertNeighborFaces(PonderScene scene, boolean expected) throws Exception {
        PonderSceneRuntime.RuntimeState runtime = PonderSceneRuntime.buildState(scene, 30F);
        PonderDebugScreen screen = new PonderDebugScreen(null, 0);
        Class<?> accessClass = Class.forName(PonderDebugScreen.class.getName() + "$PreviewBlockAccess");
        Constructor<?> constructor = accessClass.getDeclaredConstructor(PonderDebugScreen.class,
            PonderSceneRuntime.RuntimeState.class, PonderSceneRuntime.RuntimeBlockState.class);
        constructor.setAccessible(true);
        BlockPos left = BlockPos.ORIGIN;
        BlockPos right = left.east();
        IBlockAccess leftWorld = (IBlockAccess) constructor.newInstance(screen, runtime,
            runtime.blocksByPosition.get(left));
        IBlockAccess rightWorld = (IBlockAccess) constructor.newInstance(screen, runtime,
            runtime.blocksByPosition.get(right));
        IBlockState stone = Blocks.STONE.getDefaultState();
        assertSame(stone, leftWorld.getBlockState(left), "the moving block must still see itself");
        assertSame(stone, rightWorld.getBlockState(right));
        assertEquals(expected, stone.shouldSideBeRendered(leftWorld, left, EnumFacing.EAST));
        assertEquals(expected, stone.shouldSideBeRendered(rightWorld, right, EnumFacing.WEST));
        assertEquals(!expected, leftWorld.isSideSolid(right, EnumFacing.WEST, true));
        assertEquals(!expected, rightWorld.isSideSolid(left, EnumFacing.EAST, true));
    }

    private static PonderScene adjacentScene() throws Exception {
        Map<BlockPos, IBlockState> states = new LinkedHashMap<BlockPos, IBlockState>();
        states.put(BlockPos.ORIGIN, Blocks.STONE.getDefaultState());
        states.put(BlockPos.ORIGIN.east(), Blocks.STONE.getDefaultState());
        return scene(states);
    }

    private static List<BlockPos> positions(List<PonderSceneRuntime.RuntimeBlockState> blocks) {
        List<BlockPos> positions = new java.util.ArrayList<BlockPos>();
        for (PonderSceneRuntime.RuntimeBlockState block : blocks) positions.add(block.pos);
        return positions;
    }

    private static void camera(PonderDebugScreen screen, float yaw, float pitch) throws Exception {
        Field yawField = PonderDebugScreen.class.getDeclaredField("previewYaw");
        Field pitchField = PonderDebugScreen.class.getDeclaredField("previewPitch");
        Field manualField = PonderDebugScreen.class.getDeclaredField("previewCameraManual");
        yawField.setAccessible(true);
        pitchField.setAccessible(true);
        manualField.setAccessible(true);
        yawField.setFloat(screen, yaw);
        pitchField.setFloat(screen, pitch);
        manualField.setBoolean(screen, true);
    }

    private static void sort(PonderDebugScreen screen, Object frame, PonderScene scene) throws Exception {
        Method method = PonderDebugScreen.class.getDeclaredMethod("sortTranslucentFrame", frame.getClass(),
            PonderScenePreview.PreviewBounds.class, PonderScene.class, float.class);
        method.setAccessible(true);
        method.invoke(screen, frame, PonderSceneRuntime.computeBounds(scene), scene, 20F);
    }

    private static Object frame(PonderDebugScreen screen, PonderScene scene, float tick) throws Exception {
        Method method = PonderDebugScreen.class.getDeclaredMethod("getPreviewFrame", PonderScene.class, float.class);
        method.setAccessible(true);
        return method.invoke(screen, scene, tick);
    }

    @SuppressWarnings("unchecked")
    private static List<PonderSceneRuntime.RuntimeBlockState> blocks(Object frame, String name) throws Exception {
        Field field = frame.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (List<PonderSceneRuntime.RuntimeBlockState>) field.get(frame);
    }

    private static PonderScene scene(IBlockState state) throws Exception {
        return scene(Collections.singletonMap(BlockPos.ORIGIN, state));
    }

    private static PonderScene scene(Map<BlockPos, IBlockState> states) throws Exception {
        ResourceLocation id = new ResourceLocation("ponder_test", "block_pipeline");
        PonderScene scene = new PonderScene(new PonderStoryBoardEntry(null, "ponder_test", id, id),
            new PonderLocalization());
        Constructor<PonderSchematic> constructor = PonderSchematic.class.getDeclaredConstructor(BlockPos.class,
            Map.class);
        constructor.setAccessible(true);
        scene.setSchematic(constructor.newInstance(new BlockPos(1, 1, 1), states));
        scene.recordWorldEvent(PonderScene.WorldEventType.SHOW_SECTION, states.keySet(), null, EnumFacing.UP);
        return scene;
    }
}
