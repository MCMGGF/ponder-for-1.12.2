package net.createmod.ponder.authoring.projection;

import net.createmod.ponder.PonderBackportMod;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class ProjectorBlock extends Block implements ITileEntityProvider {
    public final boolean miniature;
    public ProjectorBlock(boolean miniature) {
        super(Material.IRON); this.miniature = miniature;
        setHardness(2); setResistance(5);
        setTranslationKey("ponder." + (miniature ? "miniature_projector" : "life_size_projector"));
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.TOOLS);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new ProjectorTile(miniature); }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
        EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) PonderBackportMod.proxy.openProjector(pos, miniature);
        return true;
    }
}
