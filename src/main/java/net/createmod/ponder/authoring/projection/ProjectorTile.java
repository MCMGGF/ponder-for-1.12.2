package net.createmod.ponder.authoring.projection;

import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;

/** This tile only stores its own user-placed projector state. No scene actions run in a World. */
public final class ProjectorTile extends TileEntity implements ITickable {
    private ProjectionSettings settings = ProjectionSettings.defaults(true);
    private double elapsed;
    private boolean powered;
    private boolean edgeRunning;

    public ProjectorTile() {}
    public ProjectorTile(boolean miniature) { settings = ProjectionSettings.defaults(miniature); }

    public ProjectionSettings getSettings() { return settings; }
    public double getElapsed(float partial) {
        return elapsed + (isRunning() ? Math.max(0, Math.min(1, partial)) * settings.speed : 0);
    }
    private boolean isRunning() {
        return !settings.paused && (settings.trigger == ProjectionSettings.Trigger.ALWAYS
            || (settings.trigger == ProjectionSettings.Trigger.POWERED ? powered : edgeRunning));
    }
    @Override public void update() {
        if (world == null) return;
        boolean next = world.isBlockPowered(pos);
        if (settings.trigger == ProjectionSettings.Trigger.RISING_EDGE && next && !powered) {
            elapsed = 0; edgeRunning = true;
        }
        powered = next;
        if (isRunning()) elapsed = Math.min(1000000000, elapsed + settings.speed);
        if (!world.isRemote && isRunning() && world.getTotalWorldTime() % 20 == 0) markDirty();
    }
    public void configure(ProjectionSettings next, boolean restart) {
        if (world == null || world.isRemote) throw new IllegalStateException("Configure on integrated server only");
        if (restart || !settings.sceneId.equals(next.sceneId) || !settings.componentId.equals(next.componentId)
            || settings.entryIndex != next.entryIndex) {
            elapsed = 0;
            edgeRunning = false;
        }
        if (settings.trigger != next.trigger) edgeRunning = false;
        settings = next;
        markDirty();
        world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n) {
        super.writeToNBT(n); n.setTag("Projection", settings.toNbt());
        n.setDouble("Elapsed", elapsed); n.setBoolean("EdgeRunning", edgeRunning); n.setBoolean("Powered", powered); return n;
    }
    @Override public void readFromNBT(NBTTagCompound n) {
        super.readFromNBT(n); settings = ProjectionSettings.fromNbt(n.getCompoundTag("Projection"), true);
        double value = n.getDouble("Elapsed"); elapsed = Double.isFinite(value) ? Math.max(0, Math.min(1000000000, value)) : 0;
        edgeRunning = n.getBoolean("EdgeRunning");
        powered = n.getBoolean("Powered");
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Nullable @Override public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }
    @Override public void onDataPacket(NetworkManager manager, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }
}
