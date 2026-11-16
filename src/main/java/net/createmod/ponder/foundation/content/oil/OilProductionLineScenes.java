package net.createmod.ponder.foundation.content.oil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.PlayerElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.foundation.PonderSchematic;
import net.createmod.ponder.foundation.content.mmce.MmceMachineDescriptor;
import net.createmod.ponder.foundation.content.mmce.MmceMachineIndex;
import net.createmod.ponder.foundation.content.mmce.MmceStructureSnapshot;
import net.createmod.ponder.foundation.content.mmce.MmceStructureSnapshot.Part;
import net.createmod.ponder.foundation.content.oil.OilLineLayout.Endpoint;
import net.createmod.ponder.foundation.content.oil.OilLineLayout.Network;
import net.createmod.ponder.foundation.content.oil.OilLineLayout.Point;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.common.Loader;

/** The audited pack's early batch-operated line, entirely inside detached preview data. */
public final class OilProductionLineScenes {
    private static long reportedFailureGeneration = Long.MIN_VALUE;
    private static String reportedFailureDetail = "";
    public static final List<String> MACHINES = Collections.unmodifiableList(Arrays.asList(
        OilLineRecipeFacts.MACHINE_BOILER, OilLineRecipeFacts.MACHINE_PUMP,
        OilLineRecipeFacts.MACHINE_DISTILLATION, OilLineRecipeFacts.MACHINE_GENERATOR));
    private static final Point[] CONTROLLERS = { OilLineLayout.BOILER_CONTROLLER, OilLineLayout.PUMP_CONTROLLER,
        OilLineLayout.DISTILLATION_CONTROLLER, OilLineLayout.GENERATOR_CONTROLLER };
    private static final String[] TITLES = { "石油线：全线与物资", "石油线：模块与锅炉供汽", "石油线：勘探、抽油与缓冲",
        "石油线：批次分馏与副产物", "石油线：柴油储存与发电", "石油线：排查与自动化升级" };

    private OilProductionLineScenes() {}

    public static void build(SceneBuilder scene, SceneBuildingUtil util, int chapter) {
        checkChapter(chapter);
        try {
            build(scene, util, chapter, Assets.load());
        } catch (IllegalStateException failure) {
            reportAssetFailure(failure);
            scene.getScene().setSchematic(PonderSchematic.empty());
            scene.title("production/early_oil/" + OilProductionLinePlugin.CHAPTERS.get(chapter), TITLES[chapter]);
            scene.overlay().showText(240).colored(PonderPalette.RED)
                .text("本实例的石油线资料不完整，未替换成虚构机器：" + failure.getMessage());
            scene.idle(240);
            scene.markAsFinished();
        }
    }

    private static void reportAssetFailure(IllegalStateException failure) {
        long generation = PonderIndex.getGeneration();
        String detail = failure.getMessage();
        if (generation != reportedFailureGeneration || !Objects.equals(detail, reportedFailureDetail)) {
            reportedFailureGeneration = generation;
            reportedFailureDetail = detail;
            Ponder.LOGGER.warn("Cannot prepare oil production line lessons", failure);
        }
    }

    static void build(SceneBuilder scene, SceneBuildingUtil util, int chapter, Assets assets) {
        checkChapter(chapter);
        scene.getScene().setSchematic(PonderSchematic.empty());
        scene.title("production/early_oil/" + OilProductionLinePlugin.CHAPTERS.get(chapter), TITLES[chapter]);
        scene.removeShadow();
        Context c = new Context(scene, util, assets, chapter);
        c.plant();
        switch (chapter) {
            case 0: overview(c); break;
            case 1: steam(c); break;
            case 2: pumping(c); break;
            case 3: distillation(c); break;
            case 4: power(c); break;
            case 5: troubleshooting(c); break;
            default: throw new IllegalArgumentException("Unknown oil lesson");
        }
        scene.markAsFinished();
        scene.idle(12);
    }

    private static void overview(Context c) {
        c.step(OilLineLayout.BOILER_HATCH, "先按各机器原有蓝图搭好四站，控制器朝北。左至右是锅炉、抽油机、分馏塔、发电站；前方留操作走道，下方留检修沟。", new ItemStack(Items.COMPASS));
        c.step(OilLineLayout.PUMP_HATCH, "锅炉把水变成蒸汽，蒸汽分别送给抽油机与分馏塔。前三站不吃 FE，不需要先用电池启动。这里是配方和流向示意，不是真实机器运算。", c.assets.icon("fluid_component"), () -> c.flow(OilLineLayout.STEAM_NETWORK));
        c.step(OilLineLayout.OIL_TANK, "抽油机的原油经独立管沟进入原油罐。前期用桶把原油送入分馏仓；柴油、润滑油分罐收集，沥青单独入箱，不能把副产物堵在仓里。", c.bucket("oil"), () -> c.flow(OilLineLayout.OIL_NETWORK));
        c.step(OilLineLayout.DIESEL_TANK, "柴油罐经独立液管喂给发电站，再由能量管接基础电容库。普通液管一次只输一种液体，本基础线是半自动批次线，不伪装成无人值守。", c.bucket("diesel"), () -> c.flow(OilLineLayout.DIESEL_NETWORK));
        c.step(OilLineLayout.COAL_CHEST, "先备水、煤或木炭、空桶和储罐；锅炉与塔各装物品+流体组件，泵装流体组件，发电站装流体+能源组件。实际结构材料仍查看各机器原有教学。", new ItemStack(Items.COAL, 8), () -> c.chest(OilLineLayout.COAL_CHEST, new ItemStack(Items.COAL, 8)));
    }

    private static void steam(Context c) {
        c.step(OilLineLayout.BOILER_HATCH, "对基础功能仓安装 I 级物品组件与流体组件：手持组件右键，或放入 GUI 专用组件槽。新仓默认没有组件，不是放下就能收料。", c.assets.icon("fluid_component"));
        c.step(OilLineLayout.WATER_TANK, "水罐只作储备。用水桶从仓 GUI 的输入液体格灌水，煤或木炭放物品输入；先备一桶水和八份燃料供测试，不能当作无限运行保证。", new ItemStack(Items.WATER_BUCKET));
        c.step(OilLineLayout.BOILER_HATCH, "右键仓→仓面，选真实北侧：液体改为输出；泵与塔北侧液体改为输入。仓的上方被结构外壳占用，不能把管道穿过外壳。", c.assets.icon("liquid_conduit"), () -> c.flow(OilLineLayout.STEAM_NETWORK));
        c.step(OilLineLayout.BOILER_CONTROLLER, "投入燃料后先升温；至少 100℃ 才产汽。低温档每 40 刻用 10 mB 水产 80 mB 蒸汽，高温档更快。管道输送的是 ic2steam，不是另一种内部压力流体。", new ItemStack(Items.COAL));
        c.step(OilLineLayout.DISTILLATION_HATCH, "塔先预装原油，再短时开北侧供汽，汽量够用就关闭该面液体输入。持续通汽会占满空输入罐；GUI 倒桶不受仓面禁用影响。缺水或蒸汽出口堵住仍可能烧燃料。", c.bucket("oil"));
    }

    private static void pumping(Context c) {
        c.step(OilLineLayout.PUMP_CONTROLLER, "先完成区域勘探，在当前 16×16 区块确有剩余油藏的位置搭井口。本图是假定勘探已通过的井口，不是任意地点都能无限产油；重复勘探不会补满油藏。", new ItemStack(Items.COMPASS));
        c.step(OilLineLayout.PUMP_HATCH, "泵装流体组件，北侧液体输入接专用蒸汽干线；下方液体改为输出。先挖检修沟，再放独立原油管，不能拆掉上方或侧边的必需外壳。", c.assets.icon("fluid_component"));
        c.step(OilLineLayout.OIL_TANK, "原油管在仓端设抽取，罐端设插入；需要抽取的端点设无需红石。这里油管比蒸汽管低两层，避免不同液管相邻后自动串网。", c.assets.icon("liquid_conduit"), () -> c.flow(OilLineLayout.OIL_NETWORK));
        c.step(OilLineLayout.OIL_TANK, "每轮 400 刻（20 秒）扣 40 内部汽量，最多抽 250 mB 原油。四轮是机器累计出油一桶，不是首次空管已入罐一桶；本图跳过灌管等待，油藏不足时末轮也会更少。", c.bucket("oil"), () -> c.tank(OilLineLayout.OIL_TANK, "oil", 1000));
        c.step(OilLineLayout.OIL_TANK, "原油先缓存在罐里，再用桶转入塔。五桶至少需 20 轮、理想 400 秒，还要加预热、管内存量和运输等待。普通管首次向上送油可能需先灌满；罐满、缺汽或油藏耗尽都会停机。", c.bucket("oil"), () -> c.tank(OilLineLayout.OIL_TANK, "oil", 5000));
    }

    private static void distillation(Context c) {
        c.step(OilLineLayout.DISTILLATION_HATCH, "先禁用塔北侧液体输入，装好物品与流体组件；在同一个空输入罐连续倒入五桶原油（5000 mB），先占住原料槽，再开启供汽。", c.bucket("oil"), () -> c.tank(OilLineLayout.OIL_TANK, "oil", 0));
        c.step(OilLineLayout.DISTILLATION_HATCH, "原油已预装后，短时把北侧设液体输入。画面打开供汽接口；实际操作时观察机器内部汽量，不把演示等待时间当成充汽计时器。", c.assets.icon("liquid_conduit"), () -> c.towerSteam(true));
        c.step(OilLineLayout.DISTILLATION_HATCH, "内部汽量达到至少 400 后关闭北侧液体输入，务必在原油罐耗尽前关汽。之后在 GUI 补油；残留蒸汽留在其他罐继续转换，不必清空或丢弃。", c.assets.icon("liquid_conduit"), () -> c.towerSteam(false));
        c.step(OilLineLayout.DISTILLATION_HATCH, "每批 600 刻（30 秒）：1000 mB 原油 + 80 内部汽量 → 700 mB 柴油、200 mB 润滑油、1 沥青。本节把等待时间压缩，不改实际配方。", c.bucket("diesel"));
        if (c.assets.icons.containsKey("fluid_cell")) {
            c.step(OilLineLayout.DIESEL_TANK, "另一种取液方法：用两个空的 IC2 通用流体单元，分别点输出格，可取首批的 700 mB 柴油和 200 mB 润滑油。单元支持部分液量；本节后续仍按普通桶积攒方案演示。", c.assets.icon("fluid_cell"));
        }
        c.step(OilLineLayout.LUBRICANT_TANK, "柴油与润滑油在同一仓的不同输出罐。普通空桶只能装整 1000 mB：柴油两批才可取一桶，润滑油五批才可取一桶。用空桶点对应输出格，不要点错原油输入格。", new ItemStack(Items.BUCKET));
        c.step(OilLineLayout.DIESEL_TANK, "五批得到柴油 3500 mB、润滑油 1000 mB、沥青 5 个。分别转存三桶柴油与一桶润滑油，余下 500 mB 柴油留在输出罐；沥青从物品输出取走入箱。", c.bucket("diesel"), () -> {
            c.tank(OilLineLayout.DIESEL_TANK, "diesel", 3000);
            c.tank(OilLineLayout.LUBRICANT_TANK, "lubricant", 1000);
            c.chest(OilLineLayout.BITUMEN_CHEST, c.assets.bitumen(5));
        });
        c.step(OilLineLayout.DISTILLATION_HATCH, "基础线不从塔下方接普通液管：它不能可靠选择同仓两种油。液体输出面保持禁用，先用桶分存；自动升级要换带过滤的末影液管，不能只给普通管锁一种油。", c.assets.icon("liquid_conduit"));
    }

    private static void power(Context c) {
        c.tank(OilLineLayout.DIESEL_TANK, "diesel", 3000);
        c.step(OilLineLayout.DIESEL_TANK, "把收集的柴油倒进专用罐，罐端液管抽取、发电仓端插入，并将抽取端设无需红石。不要把原油或润滑油倒进这条柴油网络。", c.bucket("diesel"), () -> c.flow(OilLineLayout.DIESEL_NETWORK));
        c.step(OilLineLayout.GENERATOR_HATCH, "发电站安装流体与能源组件；北侧液体输入接柴油，下方能量输出接能量管。液体、能量分别设置方向，不是把所有资源一起改成双向。", c.assets.icon("energy_component"));
        c.step(OilLineLayout.CAPACITOR_BANK, "下方能量管接基础电容库，再接实际用电设备。此处用的是基础型而非创造型；容量一百万 FE，当前配置输入上限 1000 FE/t，足够接这台发电站。", new ItemStack(c.assets.capacitor.getBlock(), 1, 1), () -> c.flow(OilLineLayout.ENERGY_NETWORK));
        c.step(OilLineLayout.GENERATOR_CONTROLLER, "发电期间每 20 刻耗 20 mB 柴油，输出 256 FE/t。单批 700 mB 柴油可发电 35 秒，共 179200 FE；电容库满或能源输出堵塞时先检查接线和负载。", c.bucket("diesel"));
        c.step(OilLineLayout.DIESEL_TANK, "一台泵的长期理想平均仅约 112 FE/t，储罐不能把它变成持续 256 FE/t。先攒油再分批发电；理论连续满功率至少需三台泵，还要满足油藏、蒸汽、运输和副产物排出。", new ItemStack(Items.COMPASS));
    }

    private static void troubleshooting(Context c) {
        c.step(OilLineLayout.BOILER_HATCH, "没有蒸汽：先查结构、仓内组件、燃料、水、温度是否到 100℃，再查北侧液体输出和管端抽取。普通液管如果已进错液，应先排空，不要混接水与蒸汽。", c.assets.icon("fluid_component"));
        c.step(OilLineLayout.PUMP_HATCH, "泵不出油：查所在区块的勘探与油藏余量、内部汽量、下方液体输出、原油管端与罐空间。汽管和原油管之间至少隔一格空气，检修后不要意外接成同网。", c.assets.icon("liquid_conduit"));
        c.step(OilLineLayout.DISTILLATION_HATCH, "塔不加工：查原油与内部汽量，检查输入罐是否被蒸汽占用，以及两种油和沥青是否堵输出。先保留原油槽，再短充汽并关闭；不足一桶的产物不是丢失。", c.bucket("oil"));
        c.step(OilLineLayout.CAPACITOR_BANK, "发电站没电：查柴油而不是原油、能量组件、下方能量输出、能量管与电容库剩余空间。开机峰值和全线长期产能不同，储油耗完后的间歇是供料瓶颈。", c.assets.icon("energy_component"));
        c.step(OilLineLayout.DISTILLATION_HATCH, "进阶自动化：另备末影液管。塔下方液体输出接抽取端，柴油与润滑油罐端分别插入并设对应液体白名单；沥青另走物品导管。此处不把高阶材料当作前期必需品。", c.assets.icon("liquid_conduit"));
        c.step(OilLineLayout.DISTILLATION_CONTROLLER, "全线启动顺序：水与燃料→锅炉预热→勘探井口抽油→储原油→塔预装原油并短充汽→分存产物→开柴油发电。所有人物、流向与储量只改演示，不消耗真实物品或完成任务。", new ItemStack(Items.COMPASS));
    }

    private static void checkChapter(int chapter) {
        if (chapter < 0 || chapter >= TITLES.length) throw new IllegalArgumentException("Unknown oil lesson");
    }

    static BlockPos pos(Point p) { return new BlockPos(p.x, p.y, p.z); }

    static final class PreviewBlock {
        final IBlockState state;
        final NBTTagCompound nbt;

        PreviewBlock(IBlockState state, NBTTagCompound nbt) {
            this.state = state;
            this.nbt = nbt.copy();
        }
    }

    static final class Assets {
        final List<Map<BlockPos, PreviewBlock>> stations;
        final IBlockState conduit;
        final IBlockState tank;
        final IBlockState capacitor;
        final Map<String, ItemStack> icons;

        Assets(List<Map<BlockPos, PreviewBlock>> stations, IBlockState conduit, IBlockState tank,
            IBlockState capacitor, Map<String, ItemStack> icons) {
            this.stations = stations;
            this.conduit = conduit;
            this.tank = tank;
            this.capacitor = capacitor;
            this.icons = icons;
        }

        static Assets load() {
            java.io.File configDir = Loader.instance().getConfigDir();
            if (configDir == null || configDir.getParentFile() == null) {
                throw new IllegalStateException("尚未取得本实例配置目录，请在客户端加载完成后打开教学");
            }
            OilLinePackBaseline.verify(configDir.getParentFile().toPath());
            List<Map<BlockPos, PreviewBlock>> stations = new ArrayList<>();
            for (int i = 0; i < MACHINES.size(); i++) {
                MmceMachineDescriptor machine = OilProductionLinePlugin.findMachine(MACHINES.get(i));
                if (machine == null) throw new IllegalStateException("缺少机器 " + MACHINES.get(i));
                MmceStructureSnapshot snapshot = MmceMachineIndex.getInstance().getSnapshot(machine.getId(),
                    MmceStructureSnapshot.Variant.MINIMUM);
                BlockPos offset = pos(CONTROLLERS[i]).subtract(snapshot.getOrigin());
                Map<BlockPos, PreviewBlock> blocks = new LinkedHashMap<>();
                for (Part part : snapshot.getParts()) {
                    if (!part.isAir()) {
                        BlockPos target = part.getPreviewPosition().add(offset);
                        NBTTagCompound nbt = part.getDisplayNbt();
                        nbt.setInteger("x", target.getX());
                        nbt.setInteger("y", target.getY());
                        nbt.setInteger("z", target.getZ());
                        blocks.put(target, new PreviewBlock(part.getDisplayState(), nbt));
                    }
                }
                BlockPos hatch = pos(CONTROLLERS[i]).east();
                PreviewBlock actualHatch = blocks.get(hatch);
                if (actualHatch == null || !actualHatch.state.getBlock().getRegistryName().getPath().contains("hatch")
                    || blocks.containsKey(hatch.north()) || blocks.containsKey(hatch.down())) {
                    throw new IllegalStateException("共享仓位置或外露方向已变化：" + machine.getTitle());
                }
                stations.add(blocks);
            }
            for (String fluid : Arrays.asList("water", "ic2steam", "oil", "diesel", "lubricant")) {
                if (FluidRegistry.getFluid(fluid) == null) throw new IllegalStateException("缺少流体 " + fluid);
            }
            Map<String, ItemStack> icons = new LinkedHashMap<>();
            for (String type : Arrays.asList("item_component", "fluid_component", "energy_component")) {
                icons.put(type, requiredItem("mmcege_packext:" + type, 0));
            }
            icons.put("liquid_conduit", requiredItem("enderio:item_liquid_conduit", 0));
            icons.put("bitumen", requiredItem(OilLineRecipeFacts.ITEM_BITUMEN, OilLineRecipeFacts.ITEM_BITUMEN_META));
            Item cell = Item.REGISTRY.getObject(new ResourceLocation("ic2:fluid_cell"));
            if (cell != null && cell != Items.AIR) icons.put("fluid_cell", new ItemStack(cell));
            return new Assets(stations, requiredBlock("enderio:block_conduit_bundle", 0),
                requiredBlock("enderio:block_tank", 0), requiredBlock("enderio:block_cap_bank", 1), icons);
        }

        ItemStack icon(String name) { return icons.get(name).copy(); }
        ItemStack bitumen(int count) { ItemStack stack = icon("bitumen"); stack.setCount(count); return stack; }

        private static IBlockState requiredBlock(String id, int meta) {
            ResourceLocation key = new ResourceLocation(id);
            if (!Block.REGISTRY.containsKey(key)) throw new IllegalStateException("缺少方块 " + id);
            return Block.REGISTRY.getObject(key).getStateFromMeta(meta);
        }

        private static ItemStack requiredItem(String id, int meta) {
            Item item = Item.REGISTRY.getObject(new ResourceLocation(id));
            if (item == null || item == Items.AIR) throw new IllegalStateException("缺少物品 " + id);
            return new ItemStack(item, 1, meta);
        }
    }

    private static final class Context {
        final SceneBuilder scene;
        final SceneBuildingUtil util;
        final Assets assets;
        final int chapter;
        ElementLink<PlayerElement> player;
        Vec3d playerAt;

        Context(SceneBuilder scene, SceneBuildingUtil util, Assets assets, int chapter) {
            this.scene = scene; this.util = util; this.assets = assets; this.chapter = chapter;
        }

        void plant() {
            Set<Integer> visible = new LinkedHashSet<>();
            if (chapter == 1) visible.addAll(Arrays.asList(0, 1, 2));
            else if (chapter == 2) visible.add(1);
            else if (chapter == 3) visible.add(2);
            else if (chapter == 4) visible.add(3);
            else visible.addAll(Arrays.asList(0, 1, 2, 3));
            Set<BlockPos> occupied = new LinkedHashSet<>();
            for (int i : visible) {
                for (Map.Entry<BlockPos, PreviewBlock> entry : assets.stations.get(i).entrySet()) {
                    put(entry.getKey(), entry.getValue().state, entry.getValue().nbt);
                    occupied.add(entry.getKey());
                }
            }
            if (visible.contains(0)) {
                storage(OilLineLayout.WATER_TANK, "water", 8000, occupied);
                put(pos(OilLineLayout.COAL_CHEST), Blocks.CHEST.getDefaultState(), new NBTTagCompound());
                occupied.add(pos(OilLineLayout.COAL_CHEST));
            }
            if (visible.contains(1) || visible.contains(2)) storage(OilLineLayout.OIL_TANK, "oil", 0, occupied);
            if (visible.contains(2)) {
                storage(OilLineLayout.LUBRICANT_TANK, "lubricant", 0, occupied);
                put(pos(OilLineLayout.BITUMEN_CHEST), Blocks.CHEST.getDefaultState(), new NBTTagCompound());
                occupied.add(pos(OilLineLayout.BITUMEN_CHEST));
            }
            if (visible.contains(2) || visible.contains(3)) storage(OilLineLayout.DIESEL_TANK, "diesel", 0, occupied);
            if (visible.contains(3)) {
                put(pos(OilLineLayout.CAPACITOR_BANK), assets.capacitor, new NBTTagCompound());
                occupied.add(pos(OilLineLayout.CAPACITOR_BANK));
            }
            List<Network> networks = new ArrayList<>();
            if (chapter == 0 || chapter == 1 || chapter == 5) networks.add(OilLineLayout.STEAM_NETWORK);
            if (chapter == 0 || chapter == 2 || chapter == 5) networks.add(OilLineLayout.OIL_NETWORK);
            if (chapter == 0 || chapter == 4 || chapter == 5) {
                networks.add(OilLineLayout.DIESEL_NETWORK);
                networks.add(OilLineLayout.ENERGY_NETWORK);
            }
            for (Network network : networks) {
                for (Point node : network.getNodes()) {
                    if (occupied.contains(pos(node))) throw new IllegalStateException("管线穿过机器或储物方块：" + node);
                    occupied.add(pos(node));
                }
                pipe(network, true);
            }
            if (chapter == 3) towerSteam(false);
            int minX = 19, maxX = 0, maxZ = 0;
            for (BlockPos p : occupied) { minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX()); maxZ = Math.max(maxZ, p.getZ()); }
            scene.configureBasePlate(minX, 0, Math.max(maxX - minX + 1, maxZ + 1));
            for (int x = minX; x <= maxX; x++) {
                for (int z = 0; z <= maxZ; z++) {
                    BlockPos floor = new BlockPos(x, 0, z);
                    boolean trench = occupied.contains(floor) || occupied.contains(floor.down());
                    scene.world().setBlock(trench ? floor.down(2) : floor, Blocks.STONE.getDefaultState(), false);
                }
            }
            playerAt = new Vec3d(minX + .5, 1, 2.5);
            player = scene.special().createPlayer(playerAt, 0);
        }

        void storage(Point point, String fluid, int amount, Set<BlockPos> occupied) {
            put(pos(point), assets.tank, OilLinePreviewNbt.tank(fluid, amount)); occupied.add(pos(point));
        }

        void put(BlockPos p, IBlockState state, NBTTagCompound nbt) {
            scene.world().setBlock(p, state, false);
            if (state.getBlock().hasTileEntity(state)) {
                scene.world().modifyBlockEntityNBT(util.select().position(p), TileEntity.class, tag -> {
                    for (String key : nbt.getKeySet()) tag.setTag(key, nbt.getTag(key).copy());
                });
            }
        }

        void tank(Point point, String fluid, int amount) {
            NBTTagCompound snapshot = OilLinePreviewNbt.tank(fluid, amount);
            scene.world().modifyBlockEntityNBT(util.select().position(pos(point)), TileEntity.class,
                tag -> OilLinePreviewNbt.applySnapshot(tag, snapshot));
        }

        void chest(Point point, ItemStack stack) {
            NBTTagCompound entry = stack.writeToNBT(new NBTTagCompound()); entry.setByte("Slot", (byte) 0);
            NBTTagList items = new NBTTagList(); items.appendTag(entry);
            scene.world().modifyBlockEntityNBT(util.select().position(pos(point)), TileEntity.class, tag -> tag.setTag("Items", items.copy()));
        }

        void pipe(Network network, boolean towerOpen) {
            Set<Point> nodes = new LinkedHashSet<>(network.getNodes());
            for (Point node : network.getNodes()) {
                Set<EnumFacing> internal = EnumSet.noneOf(EnumFacing.class);
                Map<EnumFacing, Integer> external = new EnumMap<>(EnumFacing.class);
                for (OilLineLayout.Facing face : OilLineLayout.Facing.values()) {
                    if (nodes.contains(node.offset(face))) internal.add(EnumFacing.valueOf(face.name()));
                }
                for (Endpoint end : network.getEndpoints()) {
                    if (end.node.equals(node)) {
                        boolean extract = end.id.startsWith("boiler-") || end.id.equals("pump-oil")
                            || end.id.equals("diesel-buffer") || end.id.equals("generator-energy");
                        int mode = extract ? OilLinePreviewNbt.EXTRACT : OilLinePreviewNbt.INSERT;
                        if (!towerOpen && end.id.equals("distillation-steam")) mode = OilLinePreviewNbt.DISABLED;
                        external.put(EnumFacing.valueOf(end.nodeFace.name()), mode);
                    }
                }
                NBTTagCompound nbt = network.fluidId == null
                    ? OilLinePreviewNbt.energyConduit(internal, external)
                    : OilLinePreviewNbt.fluidConduit(internal, external, network.fluidId, 125);
                put(pos(node), assets.conduit, nbt);
            }
        }

        void towerSteam(boolean open) {
            Map<EnumFacing, Integer> modes = new EnumMap<>(EnumFacing.class);
            modes.put(EnumFacing.SOUTH, open ? OilLinePreviewNbt.INSERT : OilLinePreviewNbt.DISABLED);
            put(pos(OilLineLayout.DISTILLATION_HATCH).north(), assets.conduit,
                OilLinePreviewNbt.fluidConduit(EnumSet.of(EnumFacing.WEST), modes, "ic2steam", 125));
        }

        void flow(Network network) {
            for (int i = 1; i < network.getNodes().size(); i++) {
                Point a = network.getNodes().get(i - 1), b = network.getNodes().get(i);
                if (a.manhattanDistance(b) == 1) scene.overlay().showBigLine(
                    network.fluidId == null ? PonderPalette.OUTPUT : PonderPalette.INPUT,
                    new Vec3d(a.x + .5, a.y + .5, a.z + .5), new Vec3d(b.x + .5, b.y + .5, b.z + .5), 100);
            }
        }

        ItemStack bucket(String fluid) {
            if (FluidRegistry.getFluid(fluid) == null) return new ItemStack(Items.BUCKET);
            ItemStack result = FluidUtil.getFilledBucket(new FluidStack(FluidRegistry.getFluid(fluid), 1000));
            return result.isEmpty() ? new ItemStack(Items.BUCKET) : result;
        }

        void step(Point target, String text, ItemStack item) { step(target, text, item, () -> {}); }

        void step(Point target, String text, ItemStack item, Runnable result) {
            scene.addKeyframe();
            Vec3d next = new Vec3d(target.x + .5, 1, 2.5);
            scene.special().movePlayer(player, next.subtract(playerAt), 18); playerAt = next;
            scene.idle(18);
            scene.special().lookAtPlayer(player, new Vec3d(target.x + .5, target.y + .5, target.z + .5), 8);
            scene.special().setPlayerItem(player, item);
            scene.special().swingPlayer(player, 8);
            result.run();
            scene.overlay().showOutlineWithText(util.select().position(pos(target)), 160)
                .colored(PonderPalette.INPUT).text(text).placeNearTarget();
            scene.idle(160);
        }
    }
}
