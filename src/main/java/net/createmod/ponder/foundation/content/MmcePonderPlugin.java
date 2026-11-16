package net.createmod.ponder.foundation.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSchematic;
import net.createmod.ponder.foundation.content.mmce.MmceMachineDescriptor;
import net.createmod.ponder.foundation.content.mmce.MmceMachineIndex;
import net.createmod.ponder.foundation.content.mmce.MmceStructureSnapshot;
import net.createmod.ponder.foundation.content.mmce.MmceStructureSnapshot.Material;
import net.createmod.ponder.foundation.content.mmce.MmceStructureSnapshot.Part;
import net.createmod.ponder.foundation.content.mmce.MmceStructureSnapshot.Variant;
import net.createmod.ponder.foundation.registration.PonderComponentMatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** On-demand, read-only storyboards of the machines actually registered by MMCE. */
public final class MmcePonderPlugin implements PonderPlugin {

    private static final ResourceLocation TAG = Ponder.asResource("mmce_machines");
    private static final ResourceLocation EMPTY_SCHEMATIC = Ponder.asResource("mmce/empty");
    private static final Map<PonderScene, MmceStructureSnapshot> SCENE_SNAPSHOTS =
        new WeakHashMap<PonderScene, MmceStructureSnapshot>();
    private final List<MmceMachineDescriptor> registered = new ArrayList<MmceMachineDescriptor>();
    private MmceMachineIndex.Checkpoint indexCheckpoint;
    private List<MmceMachineDescriptor> registeredCheckpoint;
    private Map<PonderScene, MmceStructureSnapshot> sceneCheckpoint;

    @Override
    public String getModId() {
        return Ponder.MOD_ID;
    }

    public static void refreshMachineIndex() {
        long previous = MmceMachineIndex.getInstance().getGeneration();
        MmceMachineIndex.getInstance().refresh();
        if (previous != MmceMachineIndex.getInstance().getGeneration()) {
            SCENE_SNAPSHOTS.clear();
        }
    }

    @Override
    public void onPonderReloadStart() {
        if (indexCheckpoint != null) {
            throw new IllegalStateException("MMCE Ponder reload is already in progress");
        }
        indexCheckpoint = MmceMachineIndex.getInstance().checkpoint();
        registeredCheckpoint = new ArrayList<MmceMachineDescriptor>(registered);
        sceneCheckpoint = new LinkedHashMap<PonderScene, MmceStructureSnapshot>(SCENE_SNAPSHOTS);
    }

    @Override
    public void onPonderReloadEnd(boolean successful) {
        try {
            if (!successful && indexCheckpoint != null) {
                MmceMachineIndex.getInstance().restore(indexCheckpoint);
                registered.clear();
                registered.addAll(registeredCheckpoint);
                SCENE_SNAPSHOTS.clear();
                SCENE_SNAPSHOTS.putAll(sceneCheckpoint);
            }
        } finally {
            indexCheckpoint = null;
            registeredCheckpoint = null;
            sceneCheckpoint = null;
        }
    }

    public static void clearPreviewCache() {
        SCENE_SNAPSHOTS.clear();
        MmceMachineIndex.getInstance().clearPreviewCache();
    }

    /** For a material/layer inspector; does not compile a scene or access a world. */
    public static MmceStructureSnapshot getSceneSnapshot(PonderScene scene) {
        return SCENE_SNAPSHOTS.get(scene);
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        refreshMachineIndex();
        registered.clear();
        registered.addAll(MmceMachineIndex.getInstance().getMachines());
        for (MmceMachineDescriptor machine : registered) {
            List<ItemStack> bindings = machine.getBindings();
            int entryCount = Math.max(1, bindings.size());
            for (int i = 0; i < entryCount; i++) {
                ResourceLocation component = i == 0 ? machine.getComponentId()
                    : Ponder.asResource(machine.getComponentId().getPath() + "/binding_" + i);
                if (i < bindings.size()) {
                    ItemStack stack = bindings.get(i);
                    PonderIndex.registerComponentMatcher(component, PonderComponentMatcher.exact(
                        stack.getItem().getRegistryName(),
                        stack.getItem().getHasSubtypes() ? stack.getMetadata() : -1,
                        stack.getTagCompound(), stack.getTagCompound()));
                }
                helper.addStoryBoard(component, EMPTY_SCHEMATIC,
                    (scene, util) -> machineScene(scene, util, machine, Variant.MINIMUM), TAG);
                if (machine.hasDynamicPattern()) {
                    helper.addStoryBoard(component, EMPTY_SCHEMATIC,
                        (scene, util) -> machineScene(scene, util, machine, Variant.MAXIMUM), TAG);
                }
            }
        }
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        if (registered.isEmpty()) {
            return;
        }
        helper.registerTag(TAG)
            .title("MMCE 真实机器")
            .description("已注册机器的真实结构、分层搭建、控制器与材料替代规则")
            .item(registered.get(0).getDisplayStack())
            .addToIndex()
            .register();
        for (MmceMachineDescriptor machine : registered) {
            helper.addTagToComponent(machine.getComponentId(), TAG);
        }
    }

    private static void machineScene(SceneBuilder scene, SceneBuildingUtil util,
        MmceMachineDescriptor machine, Variant variant) {
        scene.getScene().setSchematic(PonderSchematic.empty());
        scene.title("mmce/" + machine.getId().getNamespace() + "/" + machine.getId().getPath()
            + "/" + variant.name().toLowerCase(java.util.Locale.ROOT),
            machine.getTitle() + (machine.hasDynamicPattern() ? "：" + variant.getLabel() : ""));
        scene.removeShadow();
        final MmceStructureSnapshot snapshot;
        try {
            snapshot = MmceMachineIndex.getInstance().getSnapshot(machine.getId(), variant);
        } catch (RuntimeException failure) {
            scene.configureBasePlate(0, 0, 3);
            scene.overlay().showText(240).colored(PonderPalette.RED)
                .text(failure.getMessage() == null ? "结构生成失败，请检查日志中的机器标识" : failure.getMessage());
            scene.idle(240);
            scene.markAsFinished();
            return;
        }
        SCENE_SNAPSHOTS.put(scene.getScene(), snapshot);
        scene.configureBasePlate(0, 0, Math.max(snapshot.getSize().getX(), snapshot.getSize().getZ()));
        scene.scaleSceneView(0.92F);
        for (Part part : snapshot.getParts()) {
            scene.world().setBlock(part.getPreviewPosition(), part.getDisplayState(), false);
            if (part.getDisplayState().getBlock().hasTileEntity(part.getDisplayState())) {
                scene.world().modifyBlockEntityNBT(util.select().position(part.getPreviewPosition()), TileEntity.class,
                    tag -> tag.merge(part.getDisplayNbt()));
            }
        }
        scene.overlay().showText(70).colored(PonderPalette.BLUE)
            .text(machine.getTitle() + "："
                + snapshot.getSize().getX() + " × " + snapshot.getSize().getY() + " × " + snapshot.getSize().getZ()
                + "，当前方案 " + snapshot.getVisibleBlockCount() + " 个方块");
        scene.idle(76);
        if (machine.hasDynamicPattern()) {
            for (String note : snapshot.getNotes()) {
                if (note.startsWith("动态段")) {
                    scene.overlay().showText(80).text(note);
                    scene.idle(86);
                }
            }
        }
        showAssembly(scene, snapshot);
        showComponents(scene, util, snapshot);
        showMaterials(scene, snapshot);
        scene.markAsFinished();
        scene.idle(20);
    }

    private static void showAssembly(SceneBuilder scene, MmceStructureSnapshot snapshot) {
        scene.addKeyframe();
        List<BlockPos> all = new ArrayList<BlockPos>();
        for (Part part : snapshot.getParts()) {
            if (!part.isAir()) {
                all.add(part.getPreviewPosition());
            }
        }
        scene.world().hideSection(new Positions(all), EnumFacing.DOWN);
        scene.idle(12);
        int number = 0;
        for (Map.Entry<Integer, List<Part>> layer : snapshot.getLayers().entrySet()) {
            scene.addKeyframe();
            List<BlockPos> positions = new ArrayList<BlockPos>();
            for (Part part : layer.getValue()) {
                positions.add(part.getPreviewPosition());
            }
            scene.world().showSection(new Positions(positions), EnumFacing.UP);
            int relativeY = layer.getKey().intValue() - snapshot.getOrigin().getY();
            scene.overlay().showText(36).colored(PonderPalette.GREEN)
                .text("第 " + (++number) + " 层，控制器相对高度 Y=" + relativeY
                    + "，" + positions.size() + " 个方块");
            scene.idle(42);
        }
        scene.idle(10);
    }

    private static void showComponents(SceneBuilder scene, SceneBuildingUtil util, MmceStructureSnapshot snapshot) {
        scene.addKeyframe();
        BlockPos origin = snapshot.getOrigin();
        scene.overlay().showOutlineWithText(util.select().position(origin), 70).colored(PonderPalette.RED)
            .text("控制器原点 (0, 0, 0)，正面朝北");
        scene.idle(76);
        Map<String, List<BlockPos>> components = new LinkedHashMap<String, List<BlockPos>>();
        for (Part part : snapshot.getParts()) {
            if (part.isController() || !part.isComponent() || part.isAir()) {
                continue;
            }
            String name = displayName(part);
            if (!part.getSelectorTag().isEmpty()) {
                name += "，选择器：" + part.getSelectorTag();
            }
            if (!components.containsKey(name)) {
                components.put(name, new ArrayList<BlockPos>());
            }
            components.get(name).add(part.getPreviewPosition());
        }
        for (Map.Entry<String, List<BlockPos>> group : components.entrySet()) {
            scene.addKeyframe();
            scene.overlay().showOutlineWithText(new Positions(group.getValue()), 56).colored(PonderPalette.INPUT)
                .text(group.getKey() + " × " + group.getValue().size());
            scene.idle(62);
        }
    }

    private static void showMaterials(SceneBuilder scene, MmceStructureSnapshot snapshot) {
        scene.addKeyframe();
        List<Material> materials = snapshot.getMaterials();
        for (int page = 0; page * 4 < materials.size(); page++) {
            StringBuilder text = new StringBuilder("材料清单 ").append(page + 1).append('/')
                .append((materials.size() + 3) / 4);
            for (int row = page * 4; row < Math.min(materials.size(), page * 4 + 4); row++) {
                Material material = materials.get(row);
                text.append('\n').append(material.getDisplayName()).append(" × ").append(material.getCount());
                if (material.getAlternativeStateCount() > 1) {
                    text.append("（").append(material.getAlternativeStateCount()).append(" 种有效状态）");
                }
                if (material.requiresNbt()) {
                    text.append(" [NBT 条件]");
                }
            }
            scene.overlay().showText(100).text(text.toString());
            scene.idle(106);
        }
        for (String note : snapshot.getNotes()) {
            if (!note.startsWith("动态段")) {
                scene.overlay().showText(80).colored(PonderPalette.BLUE).text(note);
                scene.idle(86);
            }
        }
    }

    private static String displayName(Part part) {
        for (MmceStructureSnapshot.Alternative choice : part.getAlternatives()) {
            for (ItemStack stack : choice.getIngredients()) {
                if (!stack.isEmpty()) {
                    return stack.getDisplayName();
                }
            }
        }
        return part.getDisplayState().getBlock().getLocalizedName();
    }

    /** Sparse selections avoid expanding large hollow structures into bounding-box volumes. */
    private static final class Positions implements Selection {
        private final Set<BlockPos> positions;

        private Positions(Iterable<BlockPos> source) {
            positions = new LinkedHashSet<BlockPos>();
            for (BlockPos position : source) {
                positions.add(position.toImmutable());
            }
        }

        @Override
        public Selection add(Selection other) {
            for (BlockPos position : other) {
                positions.add(position.toImmutable());
            }
            return this;
        }

        @Override
        public Selection substract(Selection other) {
            for (BlockPos position : other) {
                positions.remove(position);
            }
            return this;
        }

        @Override
        public Selection copy() {
            return new Positions(positions);
        }

        @Override
        public Vec3d getCenter() {
            if (positions.isEmpty()) {
                return Vec3d.ZERO;
            }
            double x = 0;
            double y = 0;
            double z = 0;
            for (BlockPos position : positions) {
                x += position.getX() + 0.5D;
                y += position.getY() + 0.5D;
                z += position.getZ() + 0.5D;
            }
            return new Vec3d(x / positions.size(), y / positions.size(), z / positions.size());
        }

        @Override
        public boolean test(BlockPos position) {
            return positions.contains(position);
        }

        @Override
        public Iterator<BlockPos> iterator() {
            return Collections.unmodifiableSet(positions).iterator();
        }
    }
}
