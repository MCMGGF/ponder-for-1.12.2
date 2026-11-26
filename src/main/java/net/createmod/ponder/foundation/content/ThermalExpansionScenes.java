package net.createmod.ponder.foundation.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.registration.PonderComponentMatcher;
import net.createmod.ponder.foundation.ui.BlockGuiDemoDefinition;
import net.createmod.ponder.foundation.ui.sandbox.SandboxRecordings;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class ThermalExpansionScenes {

    public static final ResourceLocation THERMAL_MACHINES_TAG =
        new ResourceLocation("thermalexpansion", "machines");

    public static final ResourceLocation MACHINE_FURNACE =
        new ResourceLocation("thermalexpansion", "machine_furnace");
    public static final ResourceLocation MACHINE_CHARGER =
        new ResourceLocation("thermalexpansion", "machine_charger");
    public static final ResourceLocation MACHINE_INSOLATOR =
        new ResourceLocation("thermalexpansion", "machine_insolator");
    public static final ResourceLocation MACHINE_PULVERIZER =
        new ResourceLocation("thermalexpansion", "machine_pulverizer");
    public static final ResourceLocation MACHINE_SAWMILL =
        new ResourceLocation("thermalexpansion", "machine_sawmill");
    public static final ResourceLocation MACHINE_SMELTER =
        new ResourceLocation("thermalexpansion", "machine_smelter");
    public static final ResourceLocation MACHINE_BREWER =
        new ResourceLocation("thermalexpansion", "machine_brewer");
    public static final ResourceLocation MACHINE_CENTRIFUGE =
        new ResourceLocation("thermalexpansion", "machine_centrifuge");
    public static final ResourceLocation MACHINE_COMPACTOR =
        new ResourceLocation("thermalexpansion", "machine_compactor");
    public static final ResourceLocation MACHINE_CRAFTER =
        new ResourceLocation("thermalexpansion", "machine_crafter");
    public static final ResourceLocation MACHINE_CRUCIBLE =
        new ResourceLocation("thermalexpansion", "machine_crucible");
    public static final ResourceLocation MACHINE_ENCHANTER =
        new ResourceLocation("thermalexpansion", "machine_enchanter");
    public static final ResourceLocation MACHINE_EXTRUDER =
        new ResourceLocation("thermalexpansion", "machine_extruder");
    public static final ResourceLocation MACHINE_PRECIPITATOR =
        new ResourceLocation("thermalexpansion", "machine_precipitator");
    public static final ResourceLocation MACHINE_REFINERY =
        new ResourceLocation("thermalexpansion", "machine_refinery");
    public static final ResourceLocation MACHINE_TRANSPOSER =
        new ResourceLocation("thermalexpansion", "machine_transposer");

    private static final int MAIN_GUI_DURATION = 240;
    private static final ResourceLocation MACHINE_TABS = Ponder.asResource("ponder/gui_demos/thermal_machine_tabs.json");
    private static final BlockPos MACHINE_TARGET = new BlockPos(2, 2, 2);
    private static final ResourceLocation MACHINE_BLOCK = new ResourceLocation("thermalexpansion", "machine");
    private static final MachineStateResolver REGISTERED_MACHINE = metadata -> {
        if (!Block.REGISTRY.containsKey(MACHINE_BLOCK)) {
            return null;
        }
        Block block = Block.REGISTRY.getObject(MACHINE_BLOCK);
        return block == null || block == Blocks.AIR ? null : block.getStateFromMeta(metadata);
    };
    private static final int MAIN_GUI_OFFSET_Y = -12;
    private static final int PANEL_DURATION = 38;
    private static final int PANEL_GAP_TICKS = 46;
    private static final int PANEL_ATTACH_X = 198;
    private static final int PANEL_ATTACH_Y = 0;
    private static final int TAB_BUTTON_X = 176;
    private static final int TAB_BUTTON_WIDTH = 22;
    private static final int PANEL_CONTENT_X = 6;
    private static final int PANEL_CONTENT_Y = 6;
    private static final int PANEL_CONTENT_WIDTH = 88;
    private static final int PANEL_CONTENT_HEIGHT = 80;
    private static final int REDSTONE_PANEL_CONTENT_WIDTH = 100;
    private static final GuiCallout ENERGY_CALLOUT = new GuiCallout(8, 8, 24, 76,
        "左侧纵列用于显示 RF 储能与机器当前能量状态。", PonderPalette.BLUE, -24, -30);
    private static final GuiCallout PROCESS_CALLOUT = new GuiCallout(46, 16, 116, 56,
        "上层工作区负责展示输入、处理进度与输出结果。", PonderPalette.MEDIUM, 0, -18);

    private static final MachineSpec[] MACHINES = new MachineSpec[] {
        new MachineSpec(MACHINE_FURNACE, "furnace", 0, "红石炉",
            "使用 RF 将单个输入加工为单个输出。",
            new GuiCallout(54, 24, 22, 22, "输入槽", PonderPalette.INPUT, -10, -12),
            new GuiCallout(130, 24, 24, 24, "输出槽", PonderPalette.OUTPUT, 8, -12)),
        new MachineSpec(MACHINE_CHARGER, "charger", 9, "感应充能机",
            "使用 RF 为可充能物品快速充电。",
            new GuiCallout(49, 24, 24, 24, "待充能槽位", PonderPalette.INPUT, -6, -12),
            new GuiCallout(124, 24, 24, 24, "充能完成槽位", PonderPalette.OUTPUT, 8, -12)),
        new MachineSpec(MACHINE_INSOLATOR, "insolator", 4, "光照育生机",
            "种植输入在工作区加工，作物与副产物在右侧显示；具体输入以当前配方为准。",
            new GuiCallout(48, 24, 48, 24, "主要输入槽位", PonderPalette.INPUT, -4, -12),
            new GuiCallout(133, 20, 28, 54, "主产物与副产物槽位", PonderPalette.OUTPUT, 10, -12)),
        new MachineSpec(MACHINE_PULVERIZER, "pulverizer", 1, "粉碎机",
            "将单个输入处理为主要产物，并可能附带额外掉落。",
            new GuiCallout(54, 24, 22, 22, "输入槽", PonderPalette.INPUT, -10, -12),
            new GuiCallout(133, 20, 28, 54, "主产物与额外产物槽位", PonderPalette.OUTPUT, 10, -12)),
        new MachineSpec(MACHINE_SAWMILL, "sawmill", 2, "锯木机",
            "切割木材并产出主要结果与额外副产物。",
            new GuiCallout(54, 24, 22, 22, "输入槽", PonderPalette.INPUT, -10, -12),
            new GuiCallout(133, 20, 28, 54, "主产物与额外产物槽位", PonderPalette.OUTPUT, 10, -12)),
        new MachineSpec(MACHINE_SMELTER, "smelter", 3, "感应炉",
            "使用两种输入进行合金化处理，并可返回副产物。",
            new GuiCallout(48, 24, 48, 24, "主要输入槽位", PonderPalette.INPUT, -4, -12),
            new GuiCallout(133, 20, 28, 54, "主产物与副产物槽位", PonderPalette.OUTPUT, 10, -12)),
        new MachineSpec(MACHINE_BREWER, "brewer", 12, "药剂酿造机",
            "消耗原料与流体来酿造目标药剂。",
            new GuiCallout(48, 18, 20, 38, "原料槽", PonderPalette.INPUT, -10, -12),
            new GuiCallout(86, 25, 20, 22, "试剂槽", PonderPalette.INPUT, -6, -12),
            new GuiCallout(147, 8, 20, 76, "流体储槽", PonderPalette.OUTPUT, 18, -20)),
        new MachineSpec(MACHINE_CENTRIFUGE, "centrifuge", 10, "离心机",
            "将单个输入拆分成多个输出，并在右侧存储流体。",
            new GuiCallout(50, 18, 18, 18, "输入槽", PonderPalette.INPUT, -10, -12),
            new GuiCallout(107, 21, 38, 38, "四个输出槽位", PonderPalette.OUTPUT, 12, -12),
            new GuiCallout(148, 8, 18, 76, "流体储槽", PonderPalette.OUTPUT, 18, -20)),
        new MachineSpec(MACHINE_COMPACTOR, "compactor", 5, "压缩机",
            "把单个输入压制成指定配方的结果。",
            new GuiCallout(50, 20, 18, 18, "输入槽", PonderPalette.INPUT, -10, -12),
            new GuiCallout(111, 20, 24, 24, "输出槽", PonderPalette.OUTPUT, 10, -12),
            new GuiCallout(174, 3, 20, 90, "模式切换按钮", PonderPalette.BLUE, 28, -6)),
        new MachineSpec(MACHINE_CRAFTER, "crafter", 11, "顺序合成机",
            "在顶部区域完成配方、流程与结果的统一展示。",
            new GuiCallout(34, 8, 54, 54, "3x3 配方网格", PonderPalette.INPUT, -8, -14),
            new GuiCallout(120, 16, 18, 18, "成品输出槽", PonderPalette.OUTPUT, 10, -12),
            new GuiCallout(89, 38, 28, 22, "工序控制区域", PonderPalette.BLUE, 0, 16),
            new GuiCallout(174, 3, 20, 70, "流程切换按钮", PonderPalette.GREEN, 28, -8)),
        new MachineSpec(MACHINE_CRUCIBLE, "crucible", 6, "岩浆坩埚",
            "将固体输入熔融为右侧储槽中的流体。",
            new GuiCallout(50, 18, 18, 18, "输入槽", PonderPalette.INPUT, -10, -12),
            new GuiCallout(148, 8, 18, 76, "熔融流体储槽", PonderPalette.OUTPUT, 18, -20)),
        new MachineSpec(MACHINE_ENCHANTER, "enchanter", 13, "附魔机",
            "组合输入并输出附魔结果，右侧提供附魔选项控制。",
            new GuiCallout(29, 18, 40, 18, "主要输入槽位", PonderPalette.INPUT, -8, -12),
            new GuiCallout(110, 18, 24, 24, "附魔输出槽", PonderPalette.OUTPUT, 10, -12),
            new GuiCallout(174, 3, 20, 70, "选项切换按钮", PonderPalette.BLUE, 28, -8)),
        new MachineSpec(MACHINE_EXTRUDER, "extruder", 15, "火成挤压机",
            "使用两种流体输入、一个模具槽位和一个输出槽位。",
            new GuiCallout(31, 18, 40, 18, "主要输入槽位", PonderPalette.INPUT, -8, -12),
            new GuiCallout(86, 49, 24, 20, "模具槽位", PonderPalette.BLUE, 0, 18),
            new GuiCallout(112, 18, 24, 24, "输出槽", PonderPalette.OUTPUT, 10, -12),
            new GuiCallout(174, 3, 20, 70, "流体模式按钮", PonderPalette.BLUE, 28, -8)),
        new MachineSpec(MACHINE_PRECIPITATOR, "precipitator", 14, "流体沉淀机",
            "通过流体与过滤槽位生成固体输出。",
            new GuiCallout(50, 18, 18, 18, "输入槽", PonderPalette.INPUT, -10, -12),
            new GuiCallout(86, 49, 24, 20, "过滤槽位", PonderPalette.BLUE, 0, 18),
            new GuiCallout(112, 18, 24, 24, "输出槽", PonderPalette.OUTPUT, 10, -12),
            new GuiCallout(174, 3, 20, 70, "流体模式按钮", PonderPalette.BLUE, 28, -8)),
        new MachineSpec(MACHINE_REFINERY, "refinery", 7, "分馏精炼机",
            "将输入转化为精炼结果，并在右侧储存流体。",
            new GuiCallout(50, 18, 18, 18, "输入槽", PonderPalette.INPUT, -10, -12),
            new GuiCallout(111, 18, 24, 24, "输出槽", PonderPalette.OUTPUT, 10, -12),
            new GuiCallout(148, 8, 18, 76, "流体储槽", PonderPalette.OUTPUT, 18, -20)),
        new MachineSpec(MACHINE_TRANSPOSER, "transposer", 8, "流体转置机",
            "在顶部两侧物品槽与中央传输槽之间转移流体。",
            new GuiCallout(49, 18, 58, 18, "输入与输出物品槽", PonderPalette.INPUT, -6, -12),
            new GuiCallout(77, 41, 34, 24, "中央传输槽", PonderPalette.BLUE, 0, 18),
            new GuiCallout(148, 8, 18, 76, "流体储槽", PonderPalette.OUTPUT, 18, -20),
            new GuiCallout(174, 3, 20, 90, "桶模式按钮", PonderPalette.GREEN, 28, -8))
    };

    private ThermalExpansionScenes() {
    }

    public static void registerAll(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        for (MachineSpec machine : MACHINES) {
            add(helper, machine.componentId, "ui_test/runtime_test",
                new PonderStoryBoard() {
                    @Override
                    public void program(SceneBuilder scene, SceneBuildingUtil util) {
                        machineScene(scene, util, machine);
                    }
                },
                THERMAL_MACHINES_TAG);
            PonderIndex.registerComponentMatcher(machine.componentId,
                PonderComponentMatcher.exact(new ResourceLocation("thermalexpansion", "machine"), machine.machineMeta,
                    null, createDisplayNbt(machine.machineTitle)));
        }
    }

    public static ResourceLocation[] getAllMachineIds() {
        ResourceLocation[] ids = new ResourceLocation[MACHINES.length];
        for (int i = 0; i < MACHINES.length; i++) {
            ids[i] = MACHINES[i].componentId;
        }
        return ids;
    }

    private static void add(PonderSceneRegistrationHelper<ResourceLocation> helper, ResourceLocation componentId,
        String schematicPath, PonderStoryBoard storyBoard, ResourceLocation tag) {
        helper.addStoryBoard(componentId, Ponder.asResource(schematicPath), storyBoard, tag);
    }

    private static void machineScene(SceneBuilder scene, SceneBuildingUtil util, MachineSpec spec) {
        machineScene(scene, util, spec, REGISTERED_MACHINE);
    }

    static void machineScene(SceneBuilder scene, SceneBuildingUtil util, ResourceLocation componentId,
        MachineStateResolver resolver) {
        for (MachineSpec spec : MACHINES) {
            if (spec.componentId.equals(componentId)) {
                machineScene(scene, util, spec, resolver);
                return;
            }
        }
        throw new IllegalArgumentException("Unknown Thermal machine: " + componentId);
    }

    private static void machineScene(SceneBuilder scene, SceneBuildingUtil util, MachineSpec spec,
        MachineStateResolver resolver) {
        Vec3d anchor = util.vector().centerOf(MACHINE_TARGET);
        IBlockState machineState = null;
        try {
            machineState = resolver == null ? null : resolver.resolve(spec.machineMeta);
        } catch (RuntimeException | LinkageError unavailable) {
            // Optional machine registration can be incomplete during resource reloads.
        }
        boolean placeholder = machineState == null || machineState.getBlock() == Blocks.AIR;
        if (placeholder) {
            machineState = Blocks.IRON_BLOCK.getDefaultState();
        }

        scene.title("te_" + spec.machineName + "_gui",
            (placeholder ? "热力 GUI 参考（铁块占位）：" : "热力膨胀： ") + spec.machineTitle);
        scene.world().setBlocks(util.select().layersFrom(1), Blocks.AIR.getDefaultState(), false);
        scene.world().setBlock(MACHINE_TARGET.down(), Blocks.STONEBRICK.getDefaultState(), false);
        scene.world().setBlock(MACHINE_TARGET, machineState, false);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().position(MACHINE_TARGET.down())
            .add(util.select().position(MACHINE_TARGET)), EnumFacing.DOWN);
        scene.idle(10);

        Selection target = util.select().position(MACHINE_TARGET);
        if (!placeholder) {
            scene.overlay().showOutlineWithText(target, 30)
                .colored(PonderPalette.BLUE).placeNearTarget().text(spec.summary);
            PonderScene.OverlayEvent gui = bindMachineTarget(scene.getScene().createOverlayEvent(MAIN_GUI_DURATION))
                .guiSnapshot(MACHINE_TABS).overlayId("main").offset(0, MAIN_GUI_OFFSET_Y).placeNearTarget(true);
            gui.setGuiSnapshotProvider(SandboxRecordings.onView(thermalRecording(spec.machineMeta), gui.getTick()));
            scene.idle(MAIN_GUI_DURATION);
            scene.markAsFinished();
            return;
        }
        int walkthroughDuration = 18 + 16 + 16 + spec.mainCallouts.length * 18 + 3 * PANEL_GAP_TICKS + 12;
        scene.overlay().showOutline(PonderPalette.BLUE, "thermal.machine", target, walkthroughDuration);
        scene.overlay().showOutlineWithText(target, 70)
            .colored(PonderPalette.BLUE)
            .placeNearTarget()
            .text("未加载对应机器，当前为 GUI 参考与铁块占位，不代表机器外观。" + spec.summary);
        enqueueGuiSnapshot(scene, Math.max(MAIN_GUI_DURATION, walkthroughDuration),
            snapshot("te_" + spec.machineName + "_live"), "main", anchor,
            0, MAIN_GUI_OFFSET_Y, null, 0, 0, false);
        scene.idle(18);

        enqueueGuiHighlight(scene, 60, "main", ENERGY_CALLOUT.guiX, ENERGY_CALLOUT.guiY, ENERGY_CALLOUT.guiWidth,
            ENERGY_CALLOUT.guiHeight, ENERGY_CALLOUT.text, ENERGY_CALLOUT.palette, ENERGY_CALLOUT.captionOffsetX,
            ENERGY_CALLOUT.captionOffsetY);
        scene.idle(16);

        enqueueGuiHighlight(scene, 60, "main", PROCESS_CALLOUT.guiX, PROCESS_CALLOUT.guiY, PROCESS_CALLOUT.guiWidth,
            PROCESS_CALLOUT.guiHeight, PROCESS_CALLOUT.text, PROCESS_CALLOUT.palette, PROCESS_CALLOUT.captionOffsetX,
            PROCESS_CALLOUT.captionOffsetY);
        scene.idle(16);

        for (GuiCallout callout : spec.mainCallouts) {
            enqueueGuiHighlight(scene, 55, "main", callout.guiX, callout.guiY, callout.guiWidth, callout.guiHeight,
                callout.text, callout.palette, callout.captionOffsetX, callout.captionOffsetY);
            scene.idle(18);
        }

        scene.addKeyframe();
        enqueueGuiHighlight(scene, PANEL_DURATION, "main", TAB_BUTTON_X, 28, TAB_BUTTON_WIDTH, 20,
            "配置页用于调整输入输出方向与各面的工作模式。", PonderPalette.BLUE, 0, -10);
        enqueueChildPanel(scene, PANEL_DURATION, snapshot("te_" + spec.machineName + "_panel_config_live"),
            "config_panel", "main");
        enqueueGuiHighlight(scene, PANEL_DURATION, "config_panel", PANEL_CONTENT_X, PANEL_CONTENT_Y, PANEL_CONTENT_WIDTH,
            PANEL_CONTENT_HEIGHT,
            "配置侧页展示各面的输入输出设置；不可用时显示静态参考图。", PonderPalette.BLUE, 0, -8);
        scene.idle(PANEL_GAP_TICKS);

        scene.addKeyframe();
        enqueueGuiHighlight(scene, PANEL_DURATION, "main", TAB_BUTTON_X, 4, TAB_BUTTON_WIDTH, 20,
            "升级页会展示升级槽位；部分槽位需要安装升级后才会启用。", PonderPalette.GREEN, 0, -10);
        enqueueChildPanel(scene, PANEL_DURATION, snapshot("te_" + spec.machineName + "_panel_augment_live"),
            "augment_panel", "main");
        enqueueGuiHighlight(scene, PANEL_DURATION, "augment_panel", PANEL_CONTENT_X, PANEL_CONTENT_Y, PANEL_CONTENT_WIDTH,
            PANEL_CONTENT_HEIGHT,
            "升级侧页展示槽位与功能；不可用时显示静态参考图。", PonderPalette.GREEN, 0, -8);
        scene.idle(PANEL_GAP_TICKS);

        scene.addKeyframe();
        enqueueGuiHighlight(scene, PANEL_DURATION, "main", TAB_BUTTON_X, 52, TAB_BUTTON_WIDTH, 20,
            "红石页用于切换启用、禁用与受控等红石行为。", PonderPalette.RED, 0, -10);
        enqueueChildPanel(scene, PANEL_DURATION, snapshot("te_" + spec.machineName + "_panel_redstone_live"),
            "redstone_panel", "main");
        enqueueGuiHighlight(scene, PANEL_DURATION, "redstone_panel", PANEL_CONTENT_X, PANEL_CONTENT_Y,
            REDSTONE_PANEL_CONTENT_WIDTH, PANEL_CONTENT_HEIGHT,
            "红石侧页展示控制模式；不可用时显示静态参考图。", PonderPalette.RED, 0, -8);
        scene.idle(PANEL_GAP_TICKS + 12);

        scene.markAsFinished();
    }

    private static void enqueueGuiSnapshot(SceneBuilder scene, int duration, ResourceLocation snapshotId, String overlayId,
        Vec3d anchor, int offsetX, int offsetY, String parentOverlayId, int guiX, int guiY, boolean scaleToParent) {
        PonderScene.OverlayEvent overlayEvent = bindMachineTarget(scene.getScene().createOverlayEvent(duration))
            .guiSnapshot(snapshotId)
            .palette(PonderPalette.WHITE)
            .overlayId(overlayId)
            .offset(offsetX, offsetY)
            .connectorVisible(parentOverlayId == null)
            .placeNearTarget(parentOverlayId == null);
        if (anchor != null) {
            overlayEvent.pointAt(anchor);
        }
        if (parentOverlayId != null) {
            overlayEvent.parentOverlayId(parentOverlayId)
                .guiOrigin(guiX, guiY)
                .scaleToParent(scaleToParent);
        }
        scene.getScene().recordOperation("overlay.showGuiSnapshot(" + snapshotId + ", " + duration + ")"
            + (overlayId == null ? "" : " -> " + overlayId));
    }

    private static void enqueueChildPanel(SceneBuilder scene, int duration, ResourceLocation snapshotId, String overlayId,
        String parentOverlayId) {
        enqueueGuiSnapshot(scene, duration, snapshotId, overlayId, null, 0, 0, parentOverlayId, PANEL_ATTACH_X,
            PANEL_ATTACH_Y, true);
    }

    private static void enqueueGuiHighlight(SceneBuilder scene, int duration, String parentOverlayId, int guiX, int guiY,
        int guiWidth, int guiHeight, String text, PonderPalette palette, int captionOffsetX, int captionOffsetY) {
        bindMachineTarget(scene.getScene().createOverlayEvent(duration))
            .text(text)
            .palette(palette)
            .guiHighlight(parentOverlayId, guiX, guiY, guiWidth, guiHeight)
            .captionOffset(captionOffsetX, captionOffsetY)
            .connectorVisible(true)
            .placeNearTarget(true);
        scene.getScene().recordOperation("overlay.showGuiHighlight(" + parentOverlayId + ", " + guiX + ", " + guiY
            + ", " + guiWidth + ", " + guiHeight + ").text(\"" + text + "\")");
    }

    private static ResourceLocation snapshot(String path) {
        return Ponder.asResource("gui_snapshot/" + path);
    }

    static JsonObject thermalRecording(int metadata) {
        Minecraft client = Minecraft.getMinecraft();
        if (client != null && client.getResourceManager() != null) {
            try (IResource resource = client.getResourceManager().getResource(MACHINE_TABS)) {
                return readRecording(resource.getInputStream(), metadata);
            } catch (IOException | RuntimeException error) {
                Ponder.LOGGER.warn("Could not read {} from the active resources, using the bundled script", MACHINE_TABS, error);
            }
        }
        String path = "/assets/" + MACHINE_TABS.getNamespace() + "/" + MACHINE_TABS.getPath();
        try (InputStream stream = ThermalExpansionScenes.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing " + path);
            return readRecording(stream, metadata);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load the bundled Thermal machine recording", error);
        }
    }

    private static JsonObject readRecording(InputStream stream, int metadata) {
        JsonObject recording = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        recording.getAsJsonObject("source").addProperty("meta", metadata);
        if (BlockGuiDemoDefinition.parse(recording).sandbox() == null)
            throw new IllegalArgumentException("Expected a sandbox machine GUI script");
        return recording;
    }

    private static PonderScene.OverlayEvent bindMachineTarget(PonderScene.OverlayEvent event) {
        return event.pointAt(new Vec3d(MACHINE_TARGET.getX() + 0.5D, MACHINE_TARGET.getY() + 0.5D,
            MACHINE_TARGET.getZ() + 0.5D)).targetPositions(Collections.singletonList(MACHINE_TARGET));
    }

    private static NBTTagCompound createDisplayNbt(String machineTitle) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", machineTitle);
        root.setTag("display", display);
        return root;
    }

    private static final class MachineSpec {
        private final ResourceLocation componentId;
        private final String machineName;
        private final int machineMeta;
        private final String machineTitle;
        private final String summary;
        private final GuiCallout[] mainCallouts;

        private MachineSpec(ResourceLocation componentId, String machineName, int machineMeta, String machineTitle, String summary,
            GuiCallout... mainCallouts) {
            this.componentId = componentId;
            this.machineName = machineName;
            this.machineMeta = machineMeta;
            this.machineTitle = machineTitle;
            this.summary = summary;
            this.mainCallouts = mainCallouts;
        }
    }

    interface MachineStateResolver {
        IBlockState resolve(int metadata);
    }

    private static final class GuiCallout {
        private final int guiX;
        private final int guiY;
        private final int guiWidth;
        private final int guiHeight;
        private final String text;
        private final PonderPalette palette;
        private final int captionOffsetX;
        private final int captionOffsetY;

        private GuiCallout(int guiX, int guiY, int guiWidth, int guiHeight, String text, PonderPalette palette,
            int captionOffsetX, int captionOffsetY) {
            this.guiX = guiX;
            this.guiY = guiY;
            this.guiWidth = guiWidth;
            this.guiHeight = guiHeight;
            this.text = text;
            this.palette = palette;
            this.captionOffsetX = captionOffsetX;
            this.captionOffsetY = captionOffsetY;
        }
    }
}
