package net.createmod.ponder.foundation.content;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.block.BlockRedstoneRepeater;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Vanilla-only scenes that exercise several Ponder systems at the same time.
 */
public final class ComplexTestScenes {

    private ComplexTestScenes() {
    }

    public static void factoryLine(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("complex_factory_line", "复杂演示：自动熔炼生产线");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.82F);
        scene.showBasePlate();
        scene.idle(16);

        Selection input = util.select().fromTo(1, 1, 3, 2, 2, 6);
        Selection transport = util.select().fromTo(2, 1, 3, 7, 2, 4);
        Selection processors = util.select().fromTo(2, 1, 2, 5, 4, 2);
        Selection output = util.select().fromTo(7, 1, 4, 7, 1, 4);

        scene.world().showSection(input, EnumFacing.WEST);
        scene.overlay().showOutlineWithText(input, 46)
            .colored(PonderPalette.INPUT)
            .text("输入箱、漏斗与总控拉杆");
        scene.idle(30);

        scene.world().showSection(transport, EnumFacing.DOWN);
        scene.overlay().showBigLine(PonderPalette.BLUE, util.vector().centerOf(2, 1, 4),
            util.vector().centerOf(7, 1, 4), 52);
        scene.overlay().showOutlineWithText(transport, 52)
            .colored(PonderPalette.BLUE)
            .placeNearTarget()
            .text("轨道连接输入端与处理区；物品移动为流程示意，不代表轨道能直接运输掉落物。");
        scene.idle(34);

        scene.world().showSection(processors, EnumFacing.NORTH);
        scene.world().showSection(output, EnumFacing.EAST);
        scene.idle(20);

        BlockPos lever = util.grid().at(1, 1, 6);
        Selection furnaces = util.select().position(3, 1, 2).add(util.select().position(5, 1, 2));
        Selection lamps = util.select().position(3, 4, 2).add(util.select().position(5, 4, 2));
        scene.addKeyframe();
        scene.overlay().showControls(util.vector().centerOf(lever), Pointing.DOWN, 48)
            .rightClick();
        scene.overlay().showOutlineWithText(util.select().position(lever), 48)
            .colored(PonderPalette.INPUT)
            .placeNearTarget()
            .text("操作总控拉杆，观察熔炉与指示灯同时切换；熔炼过程已压缩为动画示意。");
        scene.world().toggleRedstonePower(util.select().position(lever));
        scene.world().replaceBlocks(furnaces, Blocks.LIT_FURNACE.getDefaultState(), false);
        scene.world().replaceBlocks(lamps, Blocks.LIT_REDSTONE_LAMP.getDefaultState(), false);

        ParticleEmitter smoke = scene.effects().simpleParticleEmitter(EnumParticleTypes.SMOKE_LARGE,
            util.vector().of(0.0D, 0.035D, 0.0D));
        ParticleEmitter flame = scene.effects().simpleParticleEmitter(EnumParticleTypes.FLAME,
            util.vector().of(0.0D, 0.025D, 0.0D));
        scene.effects().emitParticles(util.vector().topOf(3, 1, 2), smoke, 4, 55);
        scene.effects().emitParticles(util.vector().topOf(5, 1, 2), flame, 5, 55);

        ElementLink<EntityElement> ore = scene.world().createItemEntity(util.vector().centerOf(2, 2, 4),
            util.vector().of(0.20D, 0.0D, 0.0D), new ItemStack(Blocks.IRON_ORE));
        ElementLink<EntityElement> fuel = scene.world().createItemEntity(util.vector().centerOf(3, 3, 2),
            util.vector().of(0.10D, 0.0D, 0.0D), new ItemStack(Items.COAL));
        scene.idle(24);
        scene.special().hideElement(ore, EnumFacing.DOWN);
        scene.special().hideElement(fuel, EnumFacing.DOWN);

        ElementLink<EntityElement> product = scene.world().createItemEntity(util.vector().centerOf(5, 2, 4),
            util.vector().of(0.10D, 0.0D, 0.0D), new ItemStack(Items.IRON_INGOT));
        scene.effects().indicateSuccess(util.grid().at(7, 1, 4));
        scene.overlay().showOutlineWithText(output, 48)
            .colored(PonderPalette.OUTPUT)
            .text("输出箱标记成品出口；铁锭图标到达后结束本次流程示意。");
        scene.idle(34);
        scene.special().hideElement(product, EnumFacing.DOWN);
        scene.world().replaceBlocks(furnaces, Blocks.FURNACE.getDefaultState(), false);
        scene.world().replaceBlocks(lamps, Blocks.REDSTONE_LAMP.getDefaultState(), false);
        scene.world().toggleRedstonePower(util.select().position(lever));
        scene.idle(20);
        scene.markAsFinished();
    }

    public static void redstoneMatrix(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("complex_redstone_matrix", "复杂演示：三路红石信号矩阵");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.88F);
        Selection repeaters = util.select().position(4, 1, 2)
            .add(util.select().position(4, 1, 4)).add(util.select().position(4, 1, 6));
        scene.world().modifyBlocks(repeaters, state -> {
            IBlockState initialized = Blocks.UNPOWERED_REPEATER.getDefaultState()
                .withProperty(BlockRedstoneRepeater.FACING, EnumFacing.WEST);
            if (state != null && state.getPropertyKeys().contains(BlockRedstoneRepeater.DELAY)) {
                initialized = initialized.withProperty(BlockRedstoneRepeater.DELAY, state.getValue(BlockRedstoneRepeater.DELAY));
            }
            if (state != null && state.getPropertyKeys().contains(BlockRedstoneRepeater.LOCKED)) {
                initialized = initialized.withProperty(BlockRedstoneRepeater.LOCKED, state.getValue(BlockRedstoneRepeater.LOCKED));
            }
            return initialized;
        }, false);
        scene.showBasePlate();
        scene.idle(14);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 7, 2, 6), EnumFacing.DOWN);
        scene.world().showSection(util.select().fromTo(3, 1, 8, 5, 4, 8), EnumFacing.SOUTH);
        scene.idle(22);

        int[] rows = { 2, 4, 6 };
        PonderPalette[] colors = { PonderPalette.RED, PonderPalette.GREEN, PonderPalette.BLUE };
        for (int index = 0; index < rows.length; index++) {
            int z = rows[index];
            BlockPos lever = util.grid().at(1, 1, z);
            BlockPos repeater = util.grid().at(4, 1, z);
            BlockPos lamp = util.grid().at(7, 1, z);
            Selection signal = util.select().fromTo(2, 1, z, 6, 1, z);

            scene.addKeyframe();
            scene.overlay().showControls(util.vector().centerOf(lever), Pointing.DOWN, 40)
                .rightClick();
            scene.overlay().showOutlineWithText(util.select().position(lever), 40)
                .colored(colors[index])
                .placeNearTarget()
                .text("右键切换这条线路的拉杆；红石线和中继器通电，对应红石灯点亮。");
            scene.overlay().showBigLine(colors[index], util.vector().centerOf(1, 1, z),
                util.vector().centerOf(7, 1, z), 44);
            scene.world().toggleRedstonePower(util.select().position(lever));
            scene.world().toggleRedstonePower(signal);
            scene.world().setBlock(lamp, Blocks.LIT_REDSTONE_LAMP.getDefaultState(), false);
            scene.effects().indicateRedstone(repeater);
            scene.effects().indicateSuccess(lamp);
            scene.idle(24);
        }

        BlockPos centerRepeater = util.grid().at(4, 1, 4);
        scene.overlay().showRepeaterScrollInput(centerRepeater, 64);
        scene.overlay().showOutlineWithText(util.select().position(centerRepeater), 64)
            .colored(PonderPalette.INPUT)
            .placeNearTarget()
            .text("中间线路的中继器依次切换延迟，观察模型上的指示位置变化。");
        for (int i = 0; i < 3; i++) {
            scene.world().cycleBlockProperty(centerRepeater, BlockRedstoneRepeater.DELAY);
            scene.idle(12);
        }

        scene.overlay().showOutlineWithText(util.select().fromTo(1, 1, 2, 7, 2, 6), 54)
            .colored(PonderPalette.OUTPUT)
            .text("三条线路同时保持状态，便于检查连接模型和发光方块");
        scene.idle(54);

        for (int z : rows) {
            scene.world().toggleRedstonePower(util.select().position(1, 1, z));
            scene.world().toggleRedstonePower(util.select().fromTo(2, 1, z, 6, 1, z));
            scene.world().setBlock(util.grid().at(7, 1, z), Blocks.REDSTONE_LAMP.getDefaultState(), false);
            scene.idle(8);
        }
        scene.markAsFinished();
    }

    public static void assemblyCore(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("complex_assembly_core", "复杂演示：四臂装配核心");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.76F);
        scene.showBasePlate();
        scene.idle(16);

        Selection core = util.select().fromTo(3, 1, 3, 5, 4, 5);
        Selection west = util.select().fromTo(1, 1, 4, 2, 3, 4);
        Selection east = util.select().fromTo(6, 1, 4, 7, 3, 4);
        Selection north = util.select().fromTo(4, 1, 1, 4, 3, 2);
        Selection south = util.select().fromTo(4, 1, 6, 4, 3, 7);

        scene.world().showSection(core, EnumFacing.DOWN);
        scene.overlay().showOutlineWithText(core, 48)
            .colored(PonderPalette.BLUE)
            .text("中心结构先完成定位");
        scene.idle(32);

        ElementLink<WorldSectionElement> westLink = scene.world().showIndependentSection(west, EnumFacing.WEST);
        ElementLink<WorldSectionElement> eastLink = scene.world().showIndependentSection(east, EnumFacing.EAST);
        ElementLink<WorldSectionElement> northLink = scene.world().showIndependentSection(north, EnumFacing.NORTH);
        ElementLink<WorldSectionElement> southLink = scene.world().showIndependentSection(south, EnumFacing.SOUTH);
        scene.idle(22);

        Vec3d pivot = util.vector().centerOf(4, 2, 4);
        scene.world().configureCenterOfRotation(westLink, pivot);
        scene.world().configureCenterOfRotation(eastLink, pivot);
        scene.world().configureCenterOfRotation(northLink, pivot);
        scene.world().configureCenterOfRotation(southLink, pivot);
        scene.world().moveSection(westLink, util.vector().of(-1.25D, 0.5D, 0.0D), 44);
        scene.world().moveSection(eastLink, util.vector().of(1.25D, 0.5D, 0.0D), 44);
        scene.world().moveSection(northLink, util.vector().of(0.0D, 0.5D, -1.25D), 44);
        scene.world().moveSection(southLink, util.vector().of(0.0D, 0.5D, 1.25D), 44);
        scene.world().rotateSection(westLink, 0.0D, 90.0D, 0.0D, 44);
        scene.world().rotateSection(eastLink, 0.0D, -90.0D, 0.0D, 44);
        scene.world().rotateSection(northLink, 0.0D, 180.0D, 0.0D, 44);
        scene.world().rotateSection(southLink, 0.0D, -180.0D, 0.0D, 44);
        scene.rotateCameraY(90.0F);
        scene.idle(48);

        BlockPos coreBlock = util.grid().at(4, 2, 4);
        ParticleEmitter portal = scene.effects().particleEmitterWithinBlockSpace(EnumParticleTypes.PORTAL,
            util.vector().of(0.0D, 0.012D, 0.0D));
        scene.effects().emitParticles(util.vector().centerOf(coreBlock), portal, 10, 72);
        for (net.minecraft.block.state.IBlockState state : new net.minecraft.block.state.IBlockState[] {
            Blocks.EMERALD_BLOCK.getDefaultState(), Blocks.GOLD_BLOCK.getDefaultState(),
            Blocks.DIAMOND_BLOCK.getDefaultState()
        }) {
            scene.world().setBlock(coreBlock, state, false);
            scene.effects().indicateSuccess(coreBlock);
            scene.idle(18);
        }
        scene.overlay().showOutlineWithText(util.select().position(coreBlock), 58)
            .colored(PonderPalette.OUTPUT)
            .placeNearTarget()
            .text("此诊断场景同时测试核心换材质、粒子、自动转镜头与四组区段变换。");
        scene.idle(58);

        scene.world().hideIndependentSection(westLink, EnumFacing.WEST);
        scene.world().hideIndependentSection(eastLink, EnumFacing.EAST);
        scene.world().hideIndependentSection(northLink, EnumFacing.NORTH);
        scene.world().hideIndependentSection(southLink, EnumFacing.SOUTH);
        scene.idle(18);
        scene.markAsFinished();
    }

    public static void logisticsHub(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("complex_logistics_hub", "复杂演示：四向物流分拣中心");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.82F);
        scene.showBasePlate();
        scene.idle(16);

        Selection lanes = util.select().fromTo(1, 1, 1, 7, 2, 7);
        Selection controller = util.select().fromTo(3, 1, 3, 5, 3, 5);
        scene.world().showSection(lanes, EnumFacing.DOWN);
        scene.idle(18);
        scene.world().showSection(controller, EnumFacing.UP);
        scene.overlay().showOutlineWithText(controller, 48)
            .colored(PonderPalette.INPUT)
            .text("中央结构与四向出口用于展示分流动画，不代表原版方块具备自动分类功能。");
        scene.idle(34);

        BlockPos center = util.grid().at(4, 3, 4);
        scene.overlay().showControls(util.vector().topOf(center), Pointing.DOWN, 54)
            .rightClick()
            .whileSneaking()
            .withItem(new ItemStack(Blocks.HOPPER));
        scene.overlay().showFilterSlotInput(util.vector().blockSurface(center, EnumFacing.NORTH), EnumFacing.NORTH, 54);
        scene.idle(26);

        dispatchItem(scene, util, new ItemStack(Items.REDSTONE), util.vector().of(-0.15D, -0.05D, 0.0D),
            util.grid().at(1, 1, 4), PonderPalette.RED, "红石进入西侧输出箱");
        dispatchItem(scene, util, new ItemStack(Items.IRON_INGOT), util.vector().of(0.15D, -0.05D, 0.0D),
            util.grid().at(7, 1, 4), PonderPalette.BLUE, "铁锭进入东侧输出箱");
        dispatchItem(scene, util, new ItemStack(Items.GOLD_INGOT), util.vector().of(0.0D, -0.05D, -0.15D),
            util.grid().at(4, 1, 1), PonderPalette.OUTPUT, "金锭进入北侧输出箱");
        dispatchItem(scene, util, new ItemStack(Items.DIAMOND), util.vector().of(0.0D, -0.05D, 0.15D),
            util.grid().at(4, 1, 7), PonderPalette.GREEN, "钻石进入南侧输出箱");

        scene.overlay().showOutlineWithText(lanes, 60)
            .colored(PonderPalette.WHITE)
            .text("同时检查透明管道、物品图标、文字避让和重播一致性");
        scene.idle(60);
        scene.markAsFinished();
    }

    private static void dispatchItem(SceneBuilder scene, SceneBuildingUtil util, ItemStack stack, Vec3d motion,
        BlockPos output, PonderPalette color, String message) {
        ElementLink<EntityElement> item = scene.world().createItemEntity(util.vector().centerOf(4, 3, 4), motion,
            stack);
        scene.addKeyframe();
        scene.overlay().showBigLine(color, util.vector().centerOf(4, 2, 4), util.vector().centerOf(output), 34);
        scene.overlay().showOutlineWithText(util.select().position(output), 34)
            .colored(color)
            .placeNearTarget()
            .text(message);
        scene.effects().indicateSuccess(output);
        scene.idle(24);
        scene.special().hideElement(item, EnumFacing.DOWN);
        scene.idle(12);
    }

    public static void guiLab(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("complex_gui_lab", "复杂演示：GUI 与文字布局实验室");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.84F);
        scene.showBasePlate();
        scene.idle(16);

        Selection machines = util.select().fromTo(1, 1, 1, 7, 3, 7);
        scene.world().showSection(machines, EnumFacing.DOWN);
        scene.overlay().showOutlineWithText(machines, 48)
            .colored(PonderPalette.WHITE)
            .text("四种容器与中央工作台用于测试 GUI 面板定位");
        scene.idle(36);

        showGuiStation(scene, util, util.grid().at(2, 1, 2), util.grid().at(2, 3, 2),
            new ResourceLocation("minecraft", "textures/gui/container/furnace.png"), 176, 166,
            "熔炉界面：检查纹理缩放和目标连线", PonderPalette.OUTPUT);
        showGuiStation(scene, util, util.grid().at(6, 1, 2), util.grid().at(6, 3, 2),
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png"), 176, 222,
            "箱子界面：高面板应自动缩小而不越界", PonderPalette.INPUT);
        showGuiStation(scene, util, util.grid().at(2, 1, 6), util.grid().at(2, 3, 6),
            new ResourceLocation("minecraft", "textures/gui/container/hopper.png"), 176, 133,
            "漏斗界面：检查短面板与中文说明避让", PonderPalette.BLUE);
        showGuiStation(scene, util, util.grid().at(6, 1, 6), util.grid().at(6, 3, 6),
            new ResourceLocation("minecraft", "textures/gui/container/brewing_stand.png"), 176, 166,
            "酿造台界面：检查右下目标附近的自动翻转", PonderPalette.GREEN);

        BlockPos furnace = util.grid().at(2, 1, 2);
        Selection furnaceTarget = util.select().position(furnace);
        scene.addKeyframe();
        scene.overlay().showGuiSnapshot(snapshot("minecraft_furnace_live"), 90)
            .pointAt(furnaceTarget)
            .placeNearTarget();
        scene.overlay().showOutlineWithText(furnaceTarget, 90)
            .colored(PonderPalette.RED)
            .placeNearTarget()
            .text("最后显示动态熔炉快照，拖动时间轴检查状态是否稳定");
        scene.overlay().showControls(util.vector().blockSurface(furnace, EnumFacing.NORTH), Pointing.RIGHT, 70)
            .leftClick()
            .whileCTRL()
            .withItem(new ItemStack(Items.COAL));
        scene.world().setBlock(util.grid().at(2, 3, 2), Blocks.LIT_REDSTONE_LAMP.getDefaultState(), false);
        scene.idle(90);
        scene.markAsFinished();
    }

    private static void showGuiStation(SceneBuilder scene, SceneBuildingUtil util, BlockPos machine, BlockPos lamp,
        ResourceLocation texture, int width, int height, String message, PonderPalette color) {
        Selection target = util.select().position(machine);
        scene.addKeyframe();
        scene.world().setBlock(lamp, Blocks.LIT_REDSTONE_LAMP.getDefaultState(), false);
        scene.overlay().showGuiSnapshot(texture, width, height, 64)
            .pointAt(target)
            .placeNearTarget();
        scene.overlay().showOutlineWithText(target, 64)
            .colored(color)
            .placeNearTarget()
            .text(message);
        scene.overlay().showControls(util.vector().blockSurface(machine, EnumFacing.NORTH), Pointing.RIGHT, 48)
            .rightClick();
        scene.idle(64);
        scene.world().setBlock(lamp, Blocks.REDSTONE_LAMP.getDefaultState(), false);
        scene.idle(8);
    }

    private static ResourceLocation snapshot(String path) {
        return Ponder.asResource("gui_snapshot/" + path);
    }
}
