package net.createmod.ponder.foundation.content;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.element.MinecartElement;
import net.createmod.ponder.api.element.PlayerElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.content.VanillaTutorialRecipes.RecipePreview;
import net.createmod.ponder.foundation.ui.VanillaRecipeSnapshot;
import net.createmod.ponder.foundation.ui.VanillaLogisticsSnapshot;
import net.createmod.ponder.foundation.ui.VanillaLogisticsSnapshot.Stage;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockHopper;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.BlockPistonExtension;
import net.minecraft.block.BlockRail;
import net.minecraft.block.BlockRailBase.EnumRailDirection;
import net.minecraft.block.BlockRailPowered;
import net.minecraft.block.BlockRedstoneRepeater;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Scripted, display-only demonstrations of vanilla mechanics. No world ticks or player interactions. */
public final class VanillaTutorialScenes {

    private VanillaTutorialScenes() {
    }

    public static void building(SceneBuilder scene, SceneBuildingUtil util) {
        ElementLink<PlayerElement> player = start(scene, util, "vanilla/building", "搭建与拆除", 7);
        step(scene, util.select().position(3, 0, 3), "走到施工位置，手持石砖，对准地面开始搭建。", 42);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.STONEBRICK));
        scene.special().movePlayer(player, new Vec3d(2, 0, 0), 32);
        scene.idle(42);

        step(scene, util.select().position(3, 1, 3), "对准地面放置石砖，第一块石砖成为后续搭建的支点。", 42);
        place(scene, player, new BlockPos(3, 1, 3), Blocks.STONEBRICK.getDefaultState());
        scene.idle(42);

        step(scene, util.select().fromTo(4, 1, 3, 4, 2, 3),
            "对准相邻方块继续放置石砖和玻璃；潜行放置还可避免打开箱子等可交互方块。", 54);
        scene.special().sneakPlayer(player, true);
        place(scene, player, new BlockPos(4, 1, 3), Blocks.STONEBRICK.getDefaultState());
        scene.idle(20);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.GLASS));
        place(scene, player, new BlockPos(4, 2, 3), Blocks.GLASS.getDefaultState());
        scene.idle(34);
        scene.special().sneakPlayer(player, false);

        step(scene, util.select().position(3, 1, 3), "用铁镐持续挖掘石砖，裂纹加深后掉落石砖。玻璃需要精准采集才会掉落自身。", 58);
        scene.special().setPlayerItem(player, new ItemStack(Items.IRON_PICKAXE));
        scene.special().lookAtPlayer(player, new Vec3d(3.5, 1.5, 3.5), 6);
        for (int i = 0; i < 5; i++) {
            scene.special().swingPlayer(player, 6);
            scene.world().incrementBlockBreakingProgress(new BlockPos(3, 1, 3));
            scene.idle(6);
        }
        scene.world().destroyBlock(new BlockPos(3, 1, 3));
        ElementLink<EntityElement> drop = scene.world().createItemEntity(new Vec3d(3.5, 1.2, 3.5),
            Vec3d.ZERO, new ItemStack(Blocks.STONEBRICK));
        scene.idle(28);
        scene.special().hideElement(drop, EnumFacing.DOWN);
        finish(scene, "搭建完成后，选择合适的工具拆除方块；掉落物取决于方块和工具的采集条件。");
    }

    public static void crafting(SceneBuilder scene, SceneBuildingUtil util) {
        crafting(scene, util, "vanilla/crafting");
    }

    static void crafting(SceneBuilder scene, SceneBuildingUtil util, String sceneId) {
        crafting(scene, util, sceneId, VanillaTutorialRecipes.crafting());
    }

    static void crafting(SceneBuilder scene, SceneBuildingUtil util, String sceneId, RecipePreview recipe) {
        ElementLink<PlayerElement> player = start(scene, util, sceneId, "工作台合成", 7);
        Selection workbench = util.select().position(3, 1, 3);
        step(scene, workbench, "放置工作台并右键打开，工作台提供 3×3 合成栏。", 42);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.CRAFTING_TABLE));
        scene.special().movePlayer(player, new Vec3d(2, 0, 0), 24);
        scene.idle(24);
        place(scene, player, new BlockPos(3, 1, 3), Blocks.CRAFTING_TABLE.getDefaultState());
        scene.idle(18);

        step(scene, workbench, recipe.isAvailable()
            ? "在合成栏放入四块橡木木板，排列成 2×2；右侧立即显示当前配方结果。这项配方在背包也能完成。"
            : "将四块橡木木板排列成 2×2。" + recipe.getUnavailableReason() + "右侧结果槽保持为空。", 70);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.PLANKS));
        scene.special().swingPlayer(player, 8);
        scene.overlay().showGuiSnapshot(VanillaRecipeSnapshot.register(recipe, false, false), 70).pointAt(workbench);
        scene.idle(70);

        if (recipe.isAvailable()) {
            step(scene, workbench, "点击右侧结果槽取出产物；材料排列与结果以当前整合包注册的配方为准。", 70);
            scene.overlay().showGuiSnapshot(VanillaRecipeSnapshot.register(recipe, false, true), 70).pointAt(workbench);
            scene.special().swingPlayer(player, 8);
            scene.special().setPlayerItem(player, recipe.getOutput());
            scene.idle(70);
            finish(scene, "合成完成。需要更大排列空间的配方必须在工作台的 3×3 合成栏中制作。");
        } else {
            finish(scene, recipe.getUnavailableReason());
        }
    }

    public static void smelting(SceneBuilder scene, SceneBuildingUtil util) {
        ElementLink<PlayerElement> player = start(scene, util, "vanilla/smelting", "熔炉与燃料", 7);
        RecipePreview recipe = VanillaTutorialRecipes.smelting();
        BlockPos furnace = new BlockPos(3, 1, 3);
        Selection furnaceTarget = util.select().position(furnace);
        step(scene, furnaceTarget, "放置熔炉并右键打开；上方槽位接收原料，下方槽位接收燃料。", 44);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.FURNACE));
        scene.special().movePlayer(player, new Vec3d(2, 0, 0), 24);
        scene.idle(24);
        place(scene, player, furnace, Blocks.FURNACE.getDefaultState().withProperty(BlockFurnace.FACING, EnumFacing.SOUTH));
        scene.idle(20);

        step(scene, furnaceTarget, "把铁矿石放入上方槽位、燃料放入下方槽位；可用配方和燃料以当前整合包为准。", 64);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.IRON_ORE));
        scene.special().swingPlayer(player, 8);
        scene.overlay().showGuiSnapshot(VanillaRecipeSnapshot.register(recipe, true, false), 64).pointAt(furnaceTarget);
        scene.idle(24);
        scene.special().setPlayerItem(player, recipe.getFuel());
        scene.special().swingPlayer(player, 8);
        scene.idle(40);
        if (!recipe.isAvailable()) {
            finish(scene, recipe.getUnavailableReason());
            return;
        }

        step(scene, furnaceTarget, "燃料点燃后熔炉开始工作。原版一次熔炼为 200 游戏刻；此处压缩为 40 刻示意。", 60);
        scene.world().setBlock(furnace, Blocks.LIT_FURNACE.getDefaultState()
            .withProperty(BlockFurnace.FACING, EnumFacing.SOUTH), false);
        scene.idle(40);
        if (!recipe.hasRemainingFuel()) {
            scene.world().setBlock(furnace, Blocks.FURNACE.getDefaultState()
                .withProperty(BlockFurnace.FACING, EnumFacing.SOUTH), false);
        }
        scene.overlay().showGuiSnapshot(VanillaRecipeSnapshot.register(recipe, true, true), 20).pointAt(furnaceTarget);
        scene.idle(20);

        step(scene, furnaceTarget, recipe.hasRemainingFuel()
            ? "从右侧结果槽取出产物。燃料尚有剩余燃烧时间，取出产物不会让炉火立刻熄灭。"
            : "从右侧结果槽取出产物。当前燃料刚好烧完，炉火随之熄灭。", 64);
        scene.overlay().showGuiSnapshot(VanillaRecipeSnapshot.register(recipe, true, true), 64).pointAt(furnaceTarget);
        scene.special().swingPlayer(player, 8);
        scene.special().setPlayerItem(player, recipe.getOutput());
        scene.idle(64);
        finish(scene, "熔炼完成。继续熔炼需要有效的原料和燃料，结果槽也必须留有空间。");
    }

    public static void logistics(SceneBuilder scene, SceneBuildingUtil util) {
        ElementLink<PlayerElement> player = start(scene, util, "vanilla/logistics", "箱子、漏斗与红石锁定", 7);
        BlockPos output = new BlockPos(4, 1, 3);
        BlockPos hopper = new BlockPos(3, 1, 3);
        BlockPos input = new BlockPos(3, 2, 3);
        BlockPos lever = new BlockPos(2, 1, 3);
        Selection hopperTarget = util.select().position(hopper);
        Selection inputTarget = util.select().position(input);
        scene.special().movePlayer(player, new Vec3d(2, 0, 0), 24);
        step(scene, hopperTarget, "先放输出箱，再潜行把漏斗出口接向箱子。漏斗的尖端决定输出方向。", 68);
        scene.idle(24);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.CHEST));
        place(scene, player, output, Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, EnumFacing.SOUTH));
        scene.idle(14);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.HOPPER));
        scene.special().sneakPlayer(player, true);
        place(scene, player, hopper, Blocks.HOPPER.getDefaultState().withProperty(BlockHopper.FACING, EnumFacing.EAST));
        scene.idle(30);
        scene.special().sneakPlayer(player, false);

        step(scene, inputTarget, "在漏斗上方放输入箱，再放入两个圆石；下方漏斗会从这个箱子提取物品。", 66);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.CHEST));
        place(scene, player, input, Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, EnumFacing.SOUTH));
        scene.idle(24);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.COBBLESTONE));
        scene.special().swingPlayer(player, 8);
        chestItems(scene, util, input, new ItemStack(Blocks.COBBLESTONE, 2));
        logisticsContents(scene, inputTarget, Stage.LOADED, 42);
        scene.overlay().showBigLine(PonderPalette.INPUT, new Vec3d(3.5, 3, 3.5), new Vec3d(3.5, 1.6, 3.5), 42);
        scene.overlay().showBigLine(PonderPalette.OUTPUT, new Vec3d(3.5, 1.6, 3.5), new Vec3d(4.5, 1.6, 3.5), 42);
        scene.idle(42);

        step(scene, hopperTarget, "圆石先进入漏斗，再进入出口方向的箱子；原版漏斗通常每 8 游戏刻转移一个物品，此处放慢阶段供查看。", 54);
        chestItems(scene, util, input, new ItemStack(Blocks.COBBLESTONE));
        hopperItems(scene, util, hopper, new ItemStack(Blocks.COBBLESTONE));
        logisticsContents(scene, hopperTarget, Stage.IN_HOPPER, 8);
        scene.idle(8);
        hopperItems(scene, util, hopper, ItemStack.EMPTY);
        chestItems(scene, util, output, new ItemStack(Blocks.COBBLESTONE));
        logisticsContents(scene, hopperTarget, Stage.TRANSFERRED, 46);
        scene.idle(46);

        step(scene, hopperTarget, "打开相邻拉杆给漏斗供电，漏斗停止提取和输出；上方箱子中的圆石暂时留在原处。", 64);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.LEVER));
        place(scene, player, lever, floorLever(false));
        scene.idle(20);
        scene.special().setPlayerItem(player, ItemStack.EMPTY);
        scene.special().swingPlayer(player, 8);
        scene.world().setBlock(lever, floorLever(true), false);
        scene.world().setBlock(hopper, Blocks.HOPPER.getDefaultState().withProperty(BlockHopper.FACING, EnumFacing.EAST)
            .withProperty(BlockHopper.ENABLED, false), false);
        logisticsContents(scene, hopperTarget, Stage.LOCKED, 44);
        scene.idle(44);

        step(scene, hopperTarget, "关闭拉杆，漏斗恢复输送，余下的圆石经过漏斗进入输出箱。", 50);
        scene.special().swingPlayer(player, 8);
        scene.world().setBlock(lever, floorLever(false), false);
        scene.world().setBlock(hopper, Blocks.HOPPER.getDefaultState().withProperty(BlockHopper.FACING, EnumFacing.EAST), false);
        chestItems(scene, util, input, ItemStack.EMPTY);
        hopperItems(scene, util, hopper, new ItemStack(Blocks.COBBLESTONE));
        logisticsContents(scene, hopperTarget, Stage.RESUMED, 8);
        scene.idle(8);
        hopperItems(scene, util, hopper, ItemStack.EMPTY);
        chestItems(scene, util, output, new ItemStack(Blocks.COBBLESTONE, 2));
        logisticsContents(scene, hopperTarget, Stage.COMPLETE, 42);
        scene.idle(42);
        scene.markAsFinished();
    }

    public static void redstone(SceneBuilder scene, SceneBuildingUtil util) {
        ElementLink<PlayerElement> player = start(scene, util, "vanilla/redstone", "拉杆、中继器、红石灯与活塞", 9);
        BlockPos lever = new BlockPos(1, 1, 3);
        BlockPos repeater = new BlockPos(4, 1, 3);
        BlockPos lamp = new BlockPos(7, 1, 3);
        BlockPos piston = new BlockPos(5, 1, 6);
        scene.world().setBlock(lever, floorLever(false), false);
        for (int x : new int[] { 2, 3, 5, 6 }) {
            scene.world().setBlock(new BlockPos(x, 1, 3), Blocks.REDSTONE_WIRE.getDefaultState(), false);
        }
        scene.world().setBlock(repeater, repeater(false), false);
        scene.world().setBlock(lamp, Blocks.REDSTONE_LAMP.getDefaultState(), false);
        scene.world().setBlock(new BlockPos(5, 1, 4), Blocks.REDSTONE_WIRE.getDefaultState(), false);
        scene.world().setBlock(new BlockPos(5, 1, 5), Blocks.REDSTONE_WIRE.getDefaultState(), false);
        scene.world().setBlock(piston, Blocks.PISTON.getDefaultState().withProperty(BlockPistonBase.FACING, EnumFacing.EAST), false);
        scene.world().setBlock(new BlockPos(6, 1, 6), Blocks.STONEBRICK.getDefaultState(), false);

        step(scene, util.select().position(lever), "打开拉杆，信号沿红石线进入中继器，再传向红石灯和活塞。", 56);
        scene.special().lookAtPlayer(player, new Vec3d(1.5, 1.5, 3.5), 8);
        scene.idle(16);
        scene.special().swingPlayer(player, 8);
        scene.world().setBlock(lever, floorLever(true), false);
        wire(scene, new BlockPos(2, 1, 3), 15);
        wire(scene, new BlockPos(3, 1, 3), 14);
        scene.idle(4);
        scene.world().setBlock(repeater, repeater(true), false);
        wire(scene, new BlockPos(5, 1, 3), 15);
        wire(scene, new BlockPos(6, 1, 3), 14);
        scene.world().setBlock(lamp, Blocks.LIT_REDSTONE_LAMP.getDefaultState(), false);
        wire(scene, new BlockPos(5, 1, 4), 14);
        wire(scene, new BlockPos(5, 1, 5), 13);
        scene.world().destroyBlock(new BlockPos(6, 1, 6));
        scene.world().setBlock(new BlockPos(7, 1, 6), Blocks.STONEBRICK.getDefaultState(), false);
        scene.world().setBlock(piston, Blocks.PISTON.getDefaultState().withProperty(BlockPistonBase.FACING, EnumFacing.EAST)
            .withProperty(BlockPistonBase.EXTENDED, true), false);
        scene.world().setBlock(new BlockPos(6, 1, 6), Blocks.PISTON_HEAD.getDefaultState()
            .withProperty(BlockPistonExtension.FACING, EnumFacing.EAST), false);
        scene.idle(36);

        step(scene, util.select().position(repeater), "中继器按朝向传递信号，把输出强度恢复到 15。此处延迟设为 2 红石刻，也就是 4 游戏刻。", 60);
        scene.special().movePlayer(player, new Vec3d(2, 0, 0), 24);
        scene.special().lookAtPlayer(player, new Vec3d(4.5, 1.2, 3.5), 24);
        scene.idle(60);

        step(scene, util.select().position(piston), "关闭拉杆后，灯熄灭、普通活塞缩回；推出的石砖留在原处，普通活塞不会把它拉回。", 66);
        scene.special().movePlayer(player, new Vec3d(-2, 0, 0), 24);
        scene.special().lookAtPlayer(player, new Vec3d(1.5, 1.5, 3.5), 24);
        scene.idle(24);
        scene.special().swingPlayer(player, 8);
        scene.world().setBlock(lever, floorLever(false), false);
        wire(scene, new BlockPos(2, 1, 3), 0);
        wire(scene, new BlockPos(3, 1, 3), 0);
        scene.idle(4);
        scene.world().setBlock(repeater, repeater(false), false);
        wire(scene, new BlockPos(5, 1, 3), 0);
        wire(scene, new BlockPos(6, 1, 3), 0);
        wire(scene, new BlockPos(5, 1, 4), 0);
        wire(scene, new BlockPos(5, 1, 5), 0);
        scene.world().destroyBlock(new BlockPos(6, 1, 6));
        scene.world().setBlock(piston, Blocks.PISTON.getDefaultState().withProperty(BlockPistonBase.FACING, EnumFacing.EAST), false);
        scene.idle(4);
        scene.world().setBlock(lamp, Blocks.REDSTONE_LAMP.getDefaultState(), false);
        scene.idle(34);
        scene.markAsFinished();
    }

    public static void rails(SceneBuilder scene, SceneBuildingUtil util) {
        ElementLink<PlayerElement> player = start(scene, util, "vanilla/rails", "铁轨、动力铁轨与矿车", 9);
        scene.world().setBlocks(util.select().fromTo(7, 1, 2, 8, 1, 2), Blocks.STONEBRICK.getDefaultState(), false);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.GOLDEN_RAIL));
        step(scene, util.select().position(4, 1, 4), "把铁轨铺在支撑方块上。普通铁轨可形成弯轨，动力铁轨只能直行或爬坡。", 80);
        place(scene, player, new BlockPos(1, 1, 4), poweredRail(EnumRailDirection.EAST_WEST, false));
        scene.idle(10);
        place(scene, player, new BlockPos(2, 1, 4), poweredRail(EnumRailDirection.EAST_WEST, false));
        scene.idle(10);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.RAIL));
        place(scene, player, new BlockPos(3, 1, 4), rail(EnumRailDirection.EAST_WEST));
        scene.idle(10);
        place(scene, player, new BlockPos(4, 1, 4), rail(EnumRailDirection.NORTH_WEST));
        scene.idle(10);
        scene.special().movePlayer(player, new Vec3d(2, 0, 0), 20);
        scene.idle(20);
        place(scene, player, new BlockPos(4, 1, 3), rail(EnumRailDirection.NORTH_SOUTH));
        place(scene, player, new BlockPos(4, 1, 2), rail(EnumRailDirection.SOUTH_EAST));
        scene.idle(20);

        step(scene, util.select().position(6, 1, 2), "铺设向东升高一格的坡轨，再接上层平轨；上层平轨下方也要放实体支撑。", 96);
        scene.special().movePlayer(player, new Vec3d(2, 0, -2), 24);
        scene.idle(24);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.GOLDEN_RAIL));
        place(scene, player, new BlockPos(5, 1, 2), poweredRail(EnumRailDirection.EAST_WEST, false));
        scene.idle(12);
        place(scene, player, new BlockPos(6, 1, 2), poweredRail(EnumRailDirection.ASCENDING_EAST, false));
        scene.idle(12);
        place(scene, player, new BlockPos(7, 2, 2), poweredRail(EnumRailDirection.EAST_WEST, false));
        scene.idle(12);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.RAIL));
        place(scene, player, new BlockPos(8, 2, 2), rail(EnumRailDirection.EAST_WEST));
        scene.idle(16);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.LEVER));
        place(scene, player, new BlockPos(5, 1, 1), floorLever(false));
        scene.idle(20);

        step(scene, util.select().fromTo(5, 1, 2, 6, 1, 2).add(util.select().position(7, 2, 2)),
            "打开坡道旁的拉杆，相连的动力铁轨亮起；激活状态只能沿动力铁轨传递有限距离。", 54);
        scene.special().movePlayer(player, new Vec3d(0, 0, -3), 24);
        scene.idle(24);
        scene.special().setPlayerItem(player, ItemStack.EMPTY);
        scene.special().lookAtPlayer(player, new Vec3d(5.5, 1.3, 1.5), 6);
        scene.special().swingPlayer(player, 8);
        scene.world().setBlock(new BlockPos(5, 1, 1), floorLever(true), false);
        scene.world().setBlock(new BlockPos(5, 1, 2), poweredRail(EnumRailDirection.EAST_WEST, true), false);
        scene.world().setBlock(new BlockPos(6, 1, 2), poweredRail(EnumRailDirection.ASCENDING_EAST, true), false);
        scene.world().setBlock(new BlockPos(7, 2, 2), poweredRail(EnumRailDirection.EAST_WEST, true), false);
        scene.idle(30);

        step(scene, util.select().position(1, 1, 4), "手持矿车右键铁轨放置矿车。未供电的动力铁轨会制动，矿车暂时停在起点。", 54);
        scene.special().movePlayer(player, new Vec3d(-4, 0, 5), 24);
        scene.special().setPlayerItem(player, new ItemStack(Items.MINECART));
        scene.idle(24);
        scene.special().lookAtPlayer(player, new Vec3d(1.5, 1.2, 4.5), 6);
        scene.special().swingPlayer(player, 8);
        ElementLink<MinecartElement> cart = scene.special().createCart(new Vec3d(1.5, 1.1, 4.5), 0,
            EntityMinecartEmpty::new);
        scene.idle(30);

        step(scene, util.select().position(0, 1, 4), "在起点背后放实体挡块，并安装拉杆；给动力铁轨供电后，挡块可帮助矿车从静止起步。", 48);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.STONEBRICK));
        place(scene, player, new BlockPos(0, 1, 4), Blocks.STONEBRICK.getDefaultState());
        scene.special().setPlayerItem(player, new ItemStack(Blocks.LEVER));
        place(scene, player, new BlockPos(1, 1, 3), floorLever(false));
        scene.idle(48);

        step(scene, util.select().fromTo(1, 1, 4, 2, 1, 4),
            "打开起点拉杆，动力铁轨亮起，矿车沿轨道出发。此处按时间线展示路线，速度为教学示意。", 64);
        scene.special().setPlayerItem(player, ItemStack.EMPTY);
        scene.special().lookAtPlayer(player, new Vec3d(1.5, 1.5, 3.5), 6);
        scene.special().swingPlayer(player, 8);
        scene.world().setBlock(new BlockPos(1, 1, 3), floorLever(true), false);
        scene.world().setBlock(new BlockPos(1, 1, 4), poweredRail(EnumRailDirection.EAST_WEST, true), false);
        scene.world().setBlock(new BlockPos(2, 1, 4), poweredRail(EnumRailDirection.EAST_WEST, true), false);
        scene.special().moveCart(cart, new Vec3d(2.5, 0, 0), 32);
        scene.idle(32);
        scene.special().rotateCart(cart, 45, 8);
        scene.special().moveCart(cart, new Vec3d(0.5, 0, -0.5), 8);
        scene.idle(8);
        scene.special().rotateCart(cart, 45, 8);
        scene.special().moveCart(cart, new Vec3d(0, 0, -1), 16);
        scene.idle(16);
        scene.special().rotateCart(cart, -45, 8);
        scene.special().moveCart(cart, new Vec3d(0.5, 0, -0.5), 8);
        scene.idle(8);

        step(scene, util.select().position(6, 1, 2), "矿车经过已供电的动力铁轨获得动力，沿坡轨升到上层轨道。", 68);
        scene.special().rotateCart(cart, -45, 8);
        scene.special().moveCart(cart, new Vec3d(1, 0, 0), 16);
        scene.idle(16);
        scene.special().moveCart(cart, new Vec3d(1, 1, 0), 20);
        scene.idle(20);
        scene.special().moveCart(cart, new Vec3d(1.5, 0, 0), 20);
        scene.idle(32);
        scene.markAsFinished();
    }

    private static ElementLink<PlayerElement> start(SceneBuilder scene, SceneBuildingUtil util, String id,
        String title, int size) {
        scene.title(id, title);
        scene.configureBasePlate(0, 0, size);
        scene.removeShadow();
        scene.world().setBlocks(util.select().fromTo(0, 0, 0, size - 1, 0, size - 1),
            Blocks.STONE.getDefaultState(), false);
        scene.showBasePlate();
        return scene.special().createPlayer(new Vec3d(1.5, 1, 5.5), 180);
    }

    private static void step(SceneBuilder scene, String text, int duration) {
        scene.addKeyframe();
        scene.overlay().showText(duration).text(text);
    }

    private static void step(SceneBuilder scene, Selection target, String text, int duration) {
        scene.addKeyframe();
        scene.overlay().showOutlineWithText(target, duration).text(text).placeNearTarget();
    }

    private static void finish(SceneBuilder scene, String text) {
        step(scene, text, 65);
        scene.idle(65);
        scene.markAsFinished();
    }

    private static void place(SceneBuilder scene, ElementLink<PlayerElement> player, BlockPos pos, IBlockState state) {
        scene.special().lookAtPlayer(player, new Vec3d(pos).add(0.5, 0.5, 0.5), 6);
        scene.special().swingPlayer(player, 8);
        scene.world().setBlock(pos, state, false);
    }

    private static IBlockState floorLever(boolean powered) {
        return Blocks.LEVER.getDefaultState().withProperty(BlockLever.FACING, BlockLever.EnumOrientation.UP_X)
            .withProperty(BlockLever.POWERED, powered);
    }

    private static IBlockState repeater(boolean powered) {
        // In 1.12.2 FACING is the input side; the output is its opposite.
        return (powered ? Blocks.POWERED_REPEATER : Blocks.UNPOWERED_REPEATER).getDefaultState()
            .withProperty(BlockRedstoneRepeater.FACING, EnumFacing.WEST).withProperty(BlockRedstoneRepeater.DELAY, 2);
    }

    private static void wire(SceneBuilder scene, BlockPos pos, int power) {
        scene.world().setBlock(pos, Blocks.REDSTONE_WIRE.getDefaultState().withProperty(BlockRedstoneWire.POWER, power), false);
    }

    private static IBlockState rail(EnumRailDirection direction) {
        return Blocks.RAIL.getDefaultState().withProperty(BlockRail.SHAPE, direction);
    }

    private static IBlockState poweredRail(EnumRailDirection direction, boolean powered) {
        return Blocks.GOLDEN_RAIL.getDefaultState().withProperty(BlockRailPowered.SHAPE, direction)
            .withProperty(BlockRailPowered.POWERED, powered);
    }

    private static void chestItems(SceneBuilder scene, SceneBuildingUtil util, BlockPos position, ItemStack stack) {
        scene.world().modifyBlockEntityNBT(util.select().position(position), TileEntityChest.class,
            tag -> putItem(tag, stack));
    }

    private static void hopperItems(SceneBuilder scene, SceneBuildingUtil util, BlockPos position, ItemStack stack) {
        scene.world().modifyBlockEntityNBT(util.select().position(position), TileEntityHopper.class,
            tag -> putItem(tag, stack));
    }

    private static void putItem(NBTTagCompound tag, ItemStack stack) {
        NBTTagList items = new NBTTagList();
        if (!stack.isEmpty()) {
            NBTTagCompound item = stack.writeToNBT(new NBTTagCompound());
            item.setByte("Slot", (byte) 0);
            items.appendTag(item);
        }
        tag.setTag("Items", items);
    }

    private static void logisticsContents(SceneBuilder scene, Selection target, Stage stage, int duration) {
        scene.overlay().showGuiSnapshot(VanillaLogisticsSnapshot.register(stage), duration).pointAt(target);
    }
}
