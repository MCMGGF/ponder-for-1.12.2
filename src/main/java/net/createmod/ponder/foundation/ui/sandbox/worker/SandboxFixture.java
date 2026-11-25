package net.createmod.ponder.foundation.ui.sandbox.worker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import net.createmod.ponder.foundation.ui.BlockGuiDemoDefinition;
import net.createmod.ponder.foundation.ui.SandboxGuiScript.Action;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** The shared descriptor is immutable. All server reads and writes run on the integrated server thread. */
record SandboxFixture(IntegratedServer server, UUID playerId, Path worldDir, BlockPos pos,
                      BlockGuiDemoDefinition definition) {
    void initialize() throws IOException {
        EntityPlayerMP player = player();
        WorldServer world = player.getServerWorld();
        if (!world.getSaveHandler().getWorldDirectory().toPath().toRealPath().equals(worldDir.toRealPath()))
            throw new IOException("Worker connected to an unexpected world directory");
        world.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
        world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
        world.getGameRules().setOrCreateGameRule("doWeatherCycle", "false");
        for (int x = -2; x <= 2; x++)
            for (int z = -4; z <= 2; z++)
                world.setBlockState(pos.add(x, -1, z), Blocks.STONE.getDefaultState(), 3);
        for (var neighbor : definition.sandbox().neighbors())
            place(world, pos.add(neighbor.x(), neighbor.y(), neighbor.z()), neighbor.block(), neighbor.meta(), neighbor.tileNbt());
        place(world, pos, definition.block(), definition.meta(), definition.tileNbt());
        player.inventory.clear();
        definition.sandbox().inventory().forEach((index, stack) ->
            player.inventory.setInventorySlotContents(index, makeStack(stack)));
        player.inventory.currentItem = 0;
        player.inventory.markDirty();
        player.sendContainerToPlayer(player.inventoryContainer);
        player.connection.setPlayerLocation(pos.getX() + 0.5, pos.getY(), pos.getZ() - 2.0, 0, 12);
    }

    boolean clientReady(Minecraft mc) {
        if (mc.currentScreen != null || mc.player.getDistanceSqToCenter(pos) > 12 ||
            mc.world.getBlockState(pos).getBlock() != ForgeRegistries.BLOCKS.getValue(definition.block())) return false;
        return definition.sandbox().inventory().entrySet().stream().allMatch(entry ->
            ItemStack.areItemStacksEqual(mc.player.inventory.getStackInSlot(entry.getKey()), makeStack(entry.getValue())));
    }

    boolean serverReady() {
        EntityPlayerMP player = player();
        // Vanilla rejects use-on-block while the last server teleport is awaiting a client ACK.
        Object pendingTeleport = ReflectionHelper.getPrivateValue(NetHandlerPlayServer.class, player.connection,
            "targetPos", "field_184362_y");
        return pendingTeleport == null && player.getDistanceSqToCenter(pos) <= 12;
    }

    boolean matches(Action action, int windowId) {
        EntityPlayerMP player = player();
        if ("wait_nbt".equals(action.type())) {
            TileEntity tile = player.getServerWorld().getTileEntity(pos);
            return tile != null && NBTUtil.areNBTEquals(action.nbt(), tile.writeToNBT(new NBTTagCompound()), true);
        }
        Container menu = player.openContainer;
        return menu != player.inventoryContainer && menu.windowId == windowId && matchesSlot(menu, action);
    }

    String describe(Action action) {
        EntityPlayerMP player = player();
        Container container = player.openContainer;
        String slot = action.slot() != null && action.slot() < container.inventorySlots.size()
            ? container.getSlot(action.slot()).getStack().toString() : "(none)";
        TileEntity tile = player.getServerWorld().getTileEntity(pos);
        String data = tile == null ? "(none)" : tile.writeToNBT(new NBTTagCompound()).toString();
        return "world tick=" + player.world.getTotalWorldTime() + ", container=" + container.getClass().getName()
            + ", slot=" + slot + ", tile=" + data.substring(0, Math.min(2048, data.length()));
    }

    static boolean matchesSlot(Container menu, Action action) {
        return action.slot() < menu.inventorySlots.size() &&
            ItemStack.areItemStacksEqual(menu.getSlot(action.slot()).getStack(), makeStack(action.stack()));
    }

    private EntityPlayerMP player() {
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
        if (player == null || player.dimension != 0 || !player.connection.netManager.isLocalChannel())
            throw new IllegalStateException("Sandbox loopback player unavailable");
        return player;
    }

    private static void place(WorldServer world, BlockPos at, ResourceLocation name, int meta, NBTTagCompound input) {
        if (!ForgeRegistries.BLOCKS.containsKey(name)) throw new IllegalArgumentException("Unknown sandbox block " + name);
        Block block = ForgeRegistries.BLOCKS.getValue(name);
        IBlockState state = block.getStateFromMeta(meta);
        world.setBlockState(at, state, 3);
        if (world.getBlockState(at).getBlock() != block) throw new IllegalStateException("Failed to place " + name);
        if (input == null) return;
        TileEntity tile = world.getTileEntity(at);
        if (tile == null) throw new IllegalArgumentException("tileNbt supplied for a block without a tile entity");
        NBTTagCompound data = tile.writeToNBT(new NBTTagCompound());
        String id = data.getString("id");
        data.merge(input);
        data.setString("id", id);
        data.setInteger("x", at.getX());
        data.setInteger("y", at.getY());
        data.setInteger("z", at.getZ());
        tile.readFromNBT(data);
        tile.markDirty();
        world.notifyBlockUpdate(at, state, state, 3);
    }

    private static ItemStack makeStack(BlockGuiDemoDefinition.Stack value) {
        if (value == null) return ItemStack.EMPTY;
        if (!ForgeRegistries.ITEMS.containsKey(value.item()))
            throw new IllegalArgumentException("Unknown sandbox item " + value.item());
        Item item = ForgeRegistries.ITEMS.getValue(value.item());
        ItemStack stack = new ItemStack(item, value.count(), value.meta());
        stack.setTagCompound(value.nbt());
        return stack;
    }
}
