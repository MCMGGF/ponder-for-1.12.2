package net.createmod.ponder.foundation.content;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.element.PlayerElement;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class DebugScenes {

    private DebugScenes() {
    }

    public static void registerAll(PonderSceneRegistrationHelper<ResourceLocation> helper,
        ResourceLocation componentId, ResourceLocation debugTag) {
        add(helper, componentId, "ui_test/scene_1", DebugScenes::coordinateScene, debugTag);
        add(helper, componentId, "ui_test/scene_2", DebugScenes::blocksScene, debugTag);
        add(helper, componentId, "ui_test/scene_4", DebugScenes::offScreenScene, debugTag);
        add(helper, componentId, "ui_test/scene_5", DebugScenes::particleScene, debugTag);
        add(helper, componentId, "ui_test/scene_6", DebugScenes::controlsScene, debugTag);
        add(helper, componentId, "ui_test/scene_7", DebugScenes::birbScene, debugTag);
        add(helper, componentId, "ui_test/scene_8", DebugScenes::sectionsScene, debugTag);
        add(helper, componentId, "ui_test/scene_8", DebugScenes::trackingScene, debugTag);
        add(helper, componentId, "ui_test/scene_9", DebugScenes::itemScene, debugTag);
        add(helper, componentId, "ui_test/runtime_test", DebugScenes::guiSnapshotScene, debugTag);
    }

    private static void add(PonderSceneRegistrationHelper<ResourceLocation> helper, ResourceLocation componentId,
        String schematicPath, PonderStoryBoard storyBoard, ResourceLocation debugTag) {
        helper.addStoryBoard(componentId, schematicPath, storyBoard, debugTag);
    }

    public static void coordinateScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("debug_coords", "诊断：三维坐标轴");
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().layersFrom(1), EnumFacing.DOWN);

        Selection xAxis = util.select().fromTo(2, 1, 1, 4, 1, 1);
        Selection yAxis = util.select().fromTo(1, 2, 1, 1, 4, 1);
        Selection zAxis = util.select().fromTo(1, 1, 2, 1, 1, 4);

        scene.idle(10);
        scene.overlay().showOutlineWithText(xAxis, 20)
            .colored(PonderPalette.RED)
            .text("红色选区沿 X 轴延伸，描边应贴合每个方块。");
        scene.idle(20);
        scene.overlay().showOutlineWithText(yAxis, 20)
            .colored(PonderPalette.GREEN)
            .text("绿色选区沿 Y 轴向上延伸，检查高度与立体棱边。");
        scene.idle(20);
        scene.overlay().showOutlineWithText(zAxis, 20)
            .colored(PonderPalette.BLUE)
            .text("蓝色选区沿 Z 轴延伸，旋转视角后仍应保持原选区。");
        scene.idle(20);
        scene.markAsFinished();
    }

    public static void blocksScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("debug_blocks", "诊断：替换与破坏方块");
        scene.showBasePlate();
        scene.scaleSceneView(0.75F);
        scene.idle(10);
        scene.world().showSection(util.select().layersFrom(1), EnumFacing.DOWN);
        scene.idle(10);
        scene.overlay().showText(1000)
            .independent(10)
            .text("方块依次替换并显示破坏进度；本诊断保留自动转镜头。");
        scene.idle(20);
        scene.world().replaceBlocks(util.select().fromTo(1, 1, 3, 2, 2, 4), Blocks.QUARTZ_BLOCK.getDefaultState(),
            true);
        scene.idle(10);
        scene.addKeyframe();
        scene.world().replaceBlocks(util.select().position(3, 1, 1), Blocks.GOLD_BLOCK.getDefaultState(), true);
        scene.rotateCameraY(180);

        for (int i = 0; i < 20; i++) {
            scene.world().incrementBlockBreakingProgress(util.grid().at(3, 1, 1));
            scene.idle(10);
        }

        scene.markAsFinished();
    }

    public static void offScreenScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("debug_baseplate", "诊断：底板边界与外部选区");
        scene.configureBasePlate(1, 0, 6);
        scene.showBasePlate();

        Selection out1 = util.select().fromTo(7, 0, 0, 8, 0, 5);
        Selection out2 = util.select().fromTo(0, 0, 0, 0, 0, 5);
        Selection blocksExceptBasePlate = util.select().layersFrom(1)
            .add(out1)
            .add(out2);

        scene.addKeyframe();
        scene.idle(10);
        scene.world().showSection(blocksExceptBasePlate, EnumFacing.DOWN);
        scene.idle(10);
        scene.addKeyframe();
        scene.idle(20);
        scene.addKeyframe();
        scene.idle(20);
        scene.addKeyframe();

        scene.overlay().showOutlineWithText(out1, 100)
            .colored(PonderPalette.BLACK)
            .text("右侧选区超出配置底板，检查边缘模型、描边与说明框是否仍可见。");
        scene.overlay().showOutlineWithText(out2, 100)
            .colored(PonderPalette.BLACK)
            .text("左侧选区位于底板之外；底板尺寸不应裁掉选区说明。");
        scene.idle(20);
        scene.markAsFinished();
    }

    public static void particleScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("debug_particles", "诊断：粒子与转镜头");
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().layersFrom(1), EnumFacing.DOWN);
        scene.idle(10);

        Vec3d emitterPos = util.vector().of(2.5D, 2.25D, 2.5D);
        ParticleEmitter flame = scene.effects().simpleParticleEmitter(EnumParticleTypes.FLAME, util.vector().of(0, 0.08D, 0));
        ParticleEmitter portal =
            scene.effects().simpleParticleEmitter(EnumParticleTypes.PORTAL, util.vector().of(0, 0.08D, 0));

        scene.overlay().showText(20)
            .text("此处即将生成火焰与传送门粒子；观察转镜头后的显示。")
            .pointAt(emitterPos);
        scene.idle(30);
        scene.effects().emitParticles(emitterPos, flame, 1, 60);
        scene.effects().emitParticles(emitterPos, portal, 20, 1);
        scene.idle(30);
        scene.rotateCameraY(180);
        scene.markAsFinished();
    }

    public static void controlsScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("debug_controls", "诊断：操作提示与选区变化");
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().layer(1), EnumFacing.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().layer(2), EnumFacing.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().layer(3), EnumFacing.DOWN);
        scene.idle(10);

        BlockPos shaftPos = util.grid().at(3, 1, 1);
        Selection shaftSelection = util.select().position(shaftPos);
        scene.overlay().showControls(util.vector().topOf(shaftPos), Pointing.DOWN, 40)
            .rightClick()
            .whileSneaking()
            .withItem(new ItemStack(Items.REDSTONE));
        scene.idle(20);
        scene.world().replaceBlocks(shaftSelection, Blocks.REDSTONE_BLOCK.getDefaultState(), true);

        scene.idle(20);
        scene.world().hideSection(shaftSelection, EnumFacing.UP);
        scene.idle(20);

        scene.overlay().showControls(util.vector().of(1, 4.5D, 3.5D), Pointing.LEFT, 20)
            .rightClick()
            .withItem(new ItemStack(Blocks.STONE));
        scene.world().showSection(util.select().layer(4), EnumFacing.DOWN);
        scene.idle(40);

        BlockPos chassis = util.grid().at(1, 1, 3);
        Vec3d chassisSurface = util.vector().blockSurface(chassis, EnumFacing.NORTH);

        Object chassisValueBoxHighlight = new Object();
        Object chassisEffectHighlight = new Object();

        AxisAlignedBB point = boxAround(chassisSurface, 0.02D, 0.02D, 0.02D);
        AxisAlignedBB expanded = boxAround(chassisSurface, 0.25D, 0.25D, 0.08D);

        Selection singleBlock = util.select().position(1, 2, 3);
        Selection twoBlocks = util.select().fromTo(1, 2, 3, 1, 3, 3);
        Selection threeBlocks = util.select().fromTo(1, 2, 3, 1, 4, 3);
        Selection singleRow = util.select().fromTo(1, 2, 3, 3, 2, 3);
        Selection twoRows = util.select().fromTo(1, 2, 3, 3, 3, 3);
        Selection threeRows = twoRows.copy().add(threeBlocks);

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, chassisValueBoxHighlight, point, 1);
        scene.idle(1);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, chassisValueBoxHighlight, expanded, 120);
        scene.overlay().showControls(chassisSurface, Pointing.UP, 40)
            .scroll()
            .withItem(new ItemStack(Items.REDSTONE));

        scene.overlay().showOutline(PonderPalette.WHITE, chassisEffectHighlight, singleBlock, 10);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.WHITE, chassisEffectHighlight, twoBlocks, 10);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.WHITE, chassisEffectHighlight, threeBlocks, 10);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.WHITE, chassisEffectHighlight, twoBlocks, 10);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.WHITE, chassisEffectHighlight, singleBlock, 10);
        scene.idle(10);

        scene.idle(30);
        scene.overlay().showControls(chassisSurface, Pointing.UP, 40)
            .whileCTRL()
            .scroll()
            .withItem(new ItemStack(Items.COMPASS));

        scene.overlay().showOutline(PonderPalette.WHITE, chassisEffectHighlight, singleRow, 10);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.WHITE, chassisEffectHighlight, twoRows, 10);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.WHITE, chassisEffectHighlight, threeRows, 10);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.WHITE, chassisEffectHighlight, twoRows, 10);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.WHITE, chassisEffectHighlight, singleRow, 10);
        scene.idle(10);
        scene.markAsFinished();
    }

    public static void birbScene(SceneBuilder scene, SceneBuildingUtil util) {
        prepareActorStage(scene, util, "debug_birbs", "人物动作：持物、潜行与转向");
        ElementLink<PlayerElement> player = scene.special().createPlayer(new Vec3d(1.5D, 1, 3.5D), 180);
        actorNote(scene, "人物站在底板上，接下来依次行走、持物、潜行和转向。", 20);
        scene.idle(20);

        actorStep(scene, util.select().position(2, 0, 3), "手持石砖沿底板走到施工位置，人物双脚始终接触地面。", 32);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.STONEBRICK));
        scene.special().movePlayer(player, new Vec3d(1, 0, 0), 24);
        scene.idle(32);

        BlockPos right = util.grid().at(3, 1, 1);
        actorStep(scene, util.select().position(right), "潜行并对准右侧位置，挥手放置金块；描边贴合新放置的方块。", 32);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.GOLD_BLOCK));
        scene.special().sneakPlayer(player, true);
        scene.special().lookAtPlayer(player, util.vector().centerOf(right), 8);
        scene.special().swingPlayer(player, 8);
        scene.world().setBlock(right, Blocks.GOLD_BLOCK.getDefaultState(), false);
        scene.idle(32);

        BlockPos left = util.grid().at(1, 1, 1);
        actorStep(scene, util.select().position(left), "起身并换成指南针，转向左侧新出现的金块；身体与头部朝向随时间线改变。", 36);
        scene.special().sneakPlayer(player, false);
        scene.special().setPlayerItem(player, new ItemStack(Items.COMPASS));
        scene.world().setBlock(left, Blocks.GOLD_BLOCK.getDefaultState(), false);
        scene.special().lookAtPlayer(player, util.vector().centerOf(left), 8);
        scene.idle(36);

        actorStep(scene, util.select().position(right), "再次转向右侧金块，确认人物朝向与这一步标出的目标一致。", 30);
        scene.special().lookAtPlayer(player, util.vector().centerOf(right), 12);
        scene.idle(30);
        actorNote(scene, "这些人物动作只改变演示时间线，不会操作真实玩家、背包或世界。", 35);
        scene.idle(35);
        scene.markAsFinished();
    }

    public static void sectionsScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("debug_sections", "诊断：独立区段与变换");
        scene.showBasePlate();
        scene.idle(10);
        scene.rotateCameraY(95);

        BlockPos mergePos = util.grid().at(1, 1, 1);
        BlockPos independentPos = util.grid().at(3, 1, 1);
        Selection toMerge = util.select().position(mergePos);
        Selection independent = util.select().position(independentPos);
        Selection start = util.select().layersFrom(1).substract(toMerge).substract(independent);

        scene.world().showSection(start, EnumFacing.DOWN);
        scene.idle(20);

        scene.world().showSection(toMerge, EnumFacing.DOWN);
        ElementLink<WorldSectionElement> link = scene.world().showIndependentSection(independent, EnumFacing.DOWN);
        scene.idle(20);

        scene.overlay().showOutlineWithText(toMerge, 40)
            .colored(PonderPalette.GREEN)
            .text("该方块合并到底板区段，说明与描边应绑定此方块。");
        scene.idle(10);
        scene.overlay().showOutlineWithText(independent, 40)
            .colored(PonderPalette.RED)
            .text("该方块属于独立区段，后续隐藏时不应留下悬空说明。");

        scene.idle(40);
        scene.world().hideIndependentSection(link, EnumFacing.DOWN);
        scene.world().hideSection(util.select().fromTo(mergePos, util.grid().at(1, 1, 4)), EnumFacing.DOWN);
        scene.idle(20);

        Selection hiddenReplaceArea = util.select().fromTo(2, 1, 2, 4, 1, 4)
            .substract(util.select().position(4, 1, 3))
            .substract(util.select().position(2, 1, 3));

        scene.world().hideSection(hiddenReplaceArea, EnumFacing.UP);
        scene.idle(20);
        scene.world().setBlocks(hiddenReplaceArea, Blocks.OBSIDIAN.getDefaultState(), false);
        scene.world().showSection(hiddenReplaceArea, EnumFacing.DOWN);
        scene.idle(20);
        scene.overlay().showOutlineWithText(hiddenReplaceArea, 30)
            .colored(PonderPalette.BLUE)
            .text("不规则选区替换为黑曜石，缺口处不应出现填充矩形。");

        scene.idle(40);
        ElementLink<WorldSectionElement> helicopter = scene.world().makeSectionIndependent(hiddenReplaceArea);
        scene.world().rotateSection(helicopter, 50, 5 * 360, 0, 60);
        scene.world().moveSection(helicopter, util.vector().of(0, 4, 5), 50);
        scene.overlay().showText(30)
            .colored(PonderPalette.BLUE)
            .text("独立区段同时旋转与上移，检查变换后的模型范围。")
            .independent(30);

        scene.idle(40);
        scene.world().hideIndependentSection(helicopter, EnumFacing.UP);
        scene.markAsFinished();
    }

    public static void trackingScene(SceneBuilder scene, SceneBuildingUtil util) {
        prepareActorStage(scene, util, "debug_tracking", "人物指向与方块轮廓追随");
        BlockPos gold = util.grid().at(1, 1, 1);
        BlockPos diamond = util.grid().at(3, 1, 1);
        scene.world().setBlock(gold, Blocks.GOLD_BLOCK.getDefaultState(), false);
        scene.world().setBlock(diamond, Blocks.DIAMOND_BLOCK.getDefaultState(), false);
        ElementLink<PlayerElement> player = scene.special().createPlayer(new Vec3d(2.5D, 1, 3.5D), 180);
        actorNote(scene, "人物依次指向两个真实方块，立体描边始终使用当前选区的方块范围。", 20);
        scene.idle(20);

        Object outlineSlot = "debug.tracking.target";
        Selection first = util.select().position(gold);
        Selection second = util.select().position(diamond);
        trackingStep(scene, outlineSlot, first, "先指向金块，轮廓沿金块的十二条棱边显示。", 36);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.GOLD_BLOCK));
        scene.special().lookAtPlayer(player, util.vector().centerOf(gold), 8);
        scene.special().swingPlayer(player, 8);
        scene.idle(36);

        trackingStep(scene, outlineSlot, second, "转向钻石块，同一条追随轮廓换到新目标，不重复叠画旧选区。", 36);
        scene.special().setPlayerItem(player, new ItemStack(Blocks.DIAMOND_BLOCK));
        scene.special().lookAtPlayer(player, util.vector().centerOf(diamond), 8);
        scene.idle(36);

        trackingStep(scene, outlineSlot, first.copy().add(second), "同时选中两个分开的方块，各自描边；中间的空位不会被大矩形包住。", 36);
        scene.special().movePlayer(player, new Vec3d(0, 0, -1), 20);
        scene.special().lookAtPlayer(player, new Vec3d(2.5D, 1.5D, 1.5D), 8);
        scene.idle(36);

        trackingStep(scene, outlineSlot, first, "潜行后回看金块，选区重新缩为一块；钻石块不再保留旧轮廓。", 36);
        scene.special().setPlayerItem(player, new ItemStack(Items.COMPASS));
        scene.special().sneakPlayer(player, true);
        scene.special().lookAtPlayer(player, util.vector().centerOf(gold), 8);
        scene.idle(36);
        scene.special().sneakPlayer(player, false);
        actorNote(scene, "本轮选区追随结束，方块仍保留，临时描边和对应说明随后消退。", 24);
        scene.idle(24);
        scene.markAsFinished();
    }

    public static void itemScene(SceneBuilder scene, SceneBuildingUtil util) {
        prepareActorStage(scene, util, "debug_items", "三维物品与连续移动");
        actorNote(scene, "三种物品将沿脚本路线移动；这是动画示意，不模拟掉落物的重力或拾取。", 20);
        scene.idle(20);

        ElementLink<EntityElement> redstone = scene.world().createItemEntity(new Vec3d(1.25D, 1.35D, 1.5D),
            Vec3d.ZERO, new ItemStack(Items.REDSTONE));
        ElementLink<EntityElement> compass = scene.world().createItemEntity(new Vec3d(2.5D, 1.35D, 1.5D),
            Vec3d.ZERO, new ItemStack(Items.COMPASS));
        ElementLink<EntityElement> gold = scene.world().createItemEntity(new Vec3d(3.75D, 1.35D, 3.5D),
            Vec3d.ZERO, new ItemStack(Blocks.GOLD_BLOCK));
        scene.world().moveEntity(redstone, new Vec3d(2, 0.4D, 0), 72);
        scene.world().moveEntity(compass, new Vec3d(0, 0.8D, 2), 72);
        scene.world().moveEntity(gold, new Vec3d(-2, 0.4D, 0), 72);
        actorNote(scene, "红石粉向右、指南针向后、金块向左移动，三件物品连续前进。", 24);
        scene.idle(24);
        actorNote(scene, "物品仍在移动；比较三维模型与各自的位置，说明不再指向已经离开的起点。", 24);
        scene.idle(24);
        actorNote(scene, "三件物品继续接近路线终点，暂停或倒退后应回到对应的路线位置。", 24);
        scene.idle(24);
        actorNote(scene, "物品已经到达各自终点并停止，本次移动示意完成。", 30);
        scene.idle(30);
        scene.markAsFinished();
    }

    public static void guiSnapshotScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("debug_gui_snapshot", "诊断：界面快照与偏移");
        scene.world().setBlocks(util.select().layersFrom(1), Blocks.AIR.getDefaultState(), false);
        scene.world().setBlocks(util.select().fromTo(1, 1, 2, 3, 1, 2),
            Blocks.STONEBRICK.getDefaultState(), false);
        scene.world().setBlock(util.grid().at(1, 2, 2), Blocks.FURNACE.getDefaultState(), false);
        scene.world().setBlock(util.grid().at(2, 2, 2), Blocks.IRON_BLOCK.getDefaultState(), false);
        scene.world().setBlock(util.grid().at(3, 2, 2), Blocks.GOLD_BLOCK.getDefaultState(), false);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 3, 2, 2), EnumFacing.DOWN);
        scene.idle(10);

        int guiSequenceDuration = 24 + 6 * 20 + 90;
        scene.overlay().showOutline(PonderPalette.BLUE, "debug.gui.targets",
            util.select().fromTo(1, 2, 2, 3, 2, 2), guiSequenceDuration);
        scene.overlay().showText(60)
            .colored(PonderPalette.WHITE)
            .pointAt(util.select().position(2, 2, 2))
            .placeNearTarget()
            .text("原版熔炉展示熔炉界面；铁块与金块仅为热力 GUI 参考定位块，不代表机器外观。本诊断保留刻意设置的面板偏移。");
        scene.overlay().showGuiSnapshot(snapshot("te_furnace_base"), 55)
            .pointAt(util.select().position(2, 2, 2))
            .placeNearTarget()
            .offset(0, -12);
        scene.idle(24);

        scene.overlay().showGuiSnapshot(snapshot("te_panel_config"), 55)
            .pointAt(util.select().position(3, 2, 2))
            .placeNearTarget()
            .offset(80, -10);
        scene.idle(20);

        scene.overlay().showGuiSnapshot(snapshot("te_insolator_live"), 70)
            .pointAt(util.select().position(2, 2, 2))
            .placeNearTarget()
            .offset(140, 10);
        scene.idle(20);

        scene.overlay().showGuiSnapshot(snapshot("minecraft_furnace"), 55)
            .pointAt(util.select().position(1, 2, 2))
            .placeNearTarget()
            .offset(-110, -8);
        scene.idle(20);

        scene.overlay().showGuiSnapshot(snapshot("minecraft_furnace_live"), 55)
            .pointAt(util.select().position(1, 2, 2))
            .placeNearTarget()
            .offset(-110, 90);
        scene.idle(20);

        scene.overlay().showGuiSnapshot(snapshot("te_furnace_live"), 70)
            .pointAt(util.select().position(2, 2, 2))
            .placeNearTarget()
            .offset(140, 110);
        scene.idle(20);

        scene.overlay().showGuiSnapshot(new ResourceLocation("minecraft", "textures/gui/container/furnace.png"),
            176, 166, 55)
            .pointAt(util.select().position(1, 2, 2))
            .placeNearTarget()
            .offset(-110, 188);
        scene.idle(20);

        scene.overlay().showGuiSnapshot(snapshot("te_furnace_cycle"), 90)
            .pointAt(util.select().position(2, 2, 2))
            .placeNearTarget()
            .offset(0, -12);
        scene.overlay().showText(90)
            .colored(PonderPalette.GREEN)
            .pointAt(util.select().position(2, 2, 2))
            .placeNearTarget()
            .text("界面每 20 刻切换基础、配置、升级和红石页，检查翻页与重播一致性。");
        scene.idle(90);
        scene.markAsFinished();
    }

    private static void prepareActorStage(SceneBuilder scene, SceneBuildingUtil util, String id, String title) {
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 5);
        scene.removeShadow();
        scene.world().setBlocks(util.select().layersFrom(1), Blocks.AIR.getDefaultState(), false);
        scene.world().setBlocks(util.select().fromTo(0, 0, 0, 4, 0, 4), Blocks.STONE.getDefaultState(), false);
        scene.showBasePlate();
    }

    private static void actorStep(SceneBuilder scene, Selection target, String text, int duration) {
        scene.addKeyframe();
        scene.overlay().showOutlineWithText(target, duration).colored(PonderPalette.GREEN).text(text).placeNearTarget();
    }

    private static void actorNote(SceneBuilder scene, String text, int duration) {
        scene.addKeyframe();
        scene.overlay().showText(duration).text(text).independent(0);
    }

    private static void trackingStep(SceneBuilder scene, Object slot, Selection target, String text, int duration) {
        scene.addKeyframe();
        scene.overlay().chaseOutline(PonderPalette.RED, slot, target, duration);
        scene.overlay().showText(duration).colored(PonderPalette.RED).text(text).pointAt(target).placeNearTarget();
    }

    private static ResourceLocation snapshot(String path) {
        return Ponder.asResource("gui_snapshot/" + path);
    }

    private static AxisAlignedBB boxAround(Vec3d center, double xRadius, double yRadius, double zRadius) {
        return new AxisAlignedBB(center.x - xRadius, center.y - yRadius, center.z - zRadius, center.x + xRadius,
            center.y + yRadius, center.z + zRadius);
    }

}
