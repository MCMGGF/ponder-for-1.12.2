package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;

/** Layer and neighbour rules shared by frame planning and model submission. */
final class PreviewBlockRendering {

    private PreviewBlockRendering() {
    }

    static List<BlockRenderLayer> modelLayers(IBlockState state) {
        List<BlockRenderLayer> layers = new ArrayList<BlockRenderLayer>();
        if (state.getRenderType() != EnumBlockRenderType.MODEL
            && state.getRenderType() != EnumBlockRenderType.LIQUID) {
            return layers;
        }
        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            if (state.getBlock().canRenderInLayer(state, layer)) {
                layers.add(layer);
            }
        }
        return layers;
    }

    static boolean transparent(PonderSceneRuntime.RuntimeBlockState block, BlockRenderLayer layer) {
        return block.fade < 1F || layer == BlockRenderLayer.TRANSLUCENT;
    }

    static float alpha(PonderSceneRuntime.RuntimeBlockState block) {
        return Math.max(0F, Math.min(1F, block.fade));
    }

    static void inLayer(BlockRenderLayer layer, Runnable render) {
        BlockRenderLayer previous = MinecraftForgeClient.getRenderLayer();
        try {
            ForgeHooksClient.setRenderLayer(layer);
            render.run();
        } finally {
            ForgeHooksClient.setRenderLayer(previous);
        }
    }

    static IBlockState neighborState(PonderSceneRuntime.RuntimeState runtime,
        PonderSceneRuntime.RuntimeBlockState rendering, BlockPos position) {
        PonderSceneRuntime.RuntimeBlockState neighbor = runtime.blocksByPosition.get(position);
        if (neighbor == null || !neighbor.visible || neighbor.fade <= 0F || neighbor.currentState == null) {
            return Blocks.AIR.getDefaultState();
        }
        if (rendering != null && neighbor != rendering && !sharesTransform(rendering, neighbor)) {
            return Blocks.AIR.getDefaultState();
        }
        return neighbor.currentState;
    }

    private static boolean sharesTransform(PonderSceneRuntime.RuntimeBlockState left,
        PonderSceneRuntime.RuntimeBlockState right) {
        if (!near(left.fadeOffsetX, right.fadeOffsetX) || !near(left.fadeOffsetY, right.fadeOffsetY)
            || !near(left.fadeOffsetZ, right.fadeOffsetZ) || !near(left.fade, right.fade)
            || left.transforms.size() != right.transforms.size()) {
            return false;
        }
        for (int i = 0; i < left.transforms.size(); i++) {
            PonderSceneRuntime.TransformStep a = left.transforms.get(i);
            PonderSceneRuntime.TransformStep b = right.transforms.get(i);
            if (!same(a.offset, b.offset) || !same(a.pivot, b.pivot) || !same(a.rotation, b.rotation)) {
                return false;
            }
        }
        return true;
    }

    private static boolean same(Vec3d left, Vec3d right) {
        return left == right || left != null && right != null
            && near(left.x, right.x) && near(left.y, right.y) && near(left.z, right.z);
    }

    private static boolean near(double left, double right) {
        return Math.abs(left - right) < 1.0E-6D;
    }
}
