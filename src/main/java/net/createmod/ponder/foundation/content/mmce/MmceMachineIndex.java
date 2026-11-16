package net.createmod.ponder.foundation.content.mmce;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.content.mmce.MmceStructureSnapshot.Alternative;
import net.createmod.ponder.foundation.content.mmce.MmceStructureSnapshot.Part;
import net.createmod.ponder.foundation.content.mmce.MmceStructureSnapshot.Variant;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;

/**
 * Optional MMCE 2.3.2 adapter. Reflection touches only public registry/data APIs;
 * it never creates a tile, accesses a world, or invokes machine event handlers.
 */
public final class MmceMachineIndex {

    private static final MmceMachineIndex INSTANCE = new MmceMachineIndex();
    private final Map<ResourceLocation, RuntimeMachine> machines =
        new LinkedHashMap<ResourceLocation, RuntimeMachine>();
    private final Map<String, MmceStructureSnapshot> snapshots =
        new LinkedHashMap<String, MmceStructureSnapshot>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, MmceStructureSnapshot> eldest) {
                return size() > MmcePreviewLimits.MAX_CACHED_STRUCTURES;
            }
        };
    private final Map<ResourceLocation, String> failures = new LinkedHashMap<ResourceLocation, String>();
    private Bridge bridge;
    private MmceDefaultModelLookup modelLookup;
    private Thread ownerThread;
    private long generation;
    private long sourceEpoch = MmceRegistryReadGuard.NO_COMPLETION;
    private String availabilityMessage = "尚未读取 MMCE 注册表";

    private MmceMachineIndex() {
    }

    public static MmceMachineIndex getInstance() {
        return INSTANCE;
    }

    /** Call after registry loading or on explicit Ponder reload, not from a tick handler. */
    public void refresh() {
        checkThread();
        long start = System.nanoTime();
        try {
            IndexedMachines next = MmceRegistryReadGuard.readChecked(() -> readRegistry(start));
            publish(next.machines, next.failures, next.message, next.epoch);
            Ponder.LOGGER.info("MMCE lightweight index: {} machines, {} failures, {} ms; no previews compiled",
                Integer.valueOf(machines.size()), Integer.valueOf(failures.size()),
                Long.valueOf((System.nanoTime() - start) / 1000000L));
        } catch (MmceRegistryReadGuard.ReloadInProgressException changing) {
            availabilityMessage = "MMCE 正在重载，暂时保留上一版思索数据";
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rethrowFatalTarget(failure);
            availabilityMessage = "MMCE 适配接口不可用：" + describe(failure);
            Ponder.LOGGER.warn("Cannot refresh the MMCE index; retaining its previous data", failure);
        }
    }

    private IndexedMachines readRegistry(long start) throws ReflectiveOperationException {
        IndexedMachines next = new IndexedMachines(MmceRegistryReadGuard.currentEpoch());
        if (!Loader.isModLoaded("modularmachinery")) {
            next.message = "未安装 Modular Machinery CE";
            return next;
        }
        if (bridge == null) {
            bridge = new Bridge();
        }
        Collection<?> loaded = new ArrayList<Object>(bridge.loadedMachines());
        List<RuntimeMachine> indexed = new ArrayList<RuntimeMachine>();
        for (Object machine : loaded) {
            checkBudget(start);
            ResourceLocation id = null;
            try {
                id = (ResourceLocation) bridge.call(machine, "getRegistryName");
                indexed.add(indexMachine(machine, id));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                rethrowFatalTarget(failure);
                if (id != null) {
                    next.failures.put(id, describe(failure));
                }
                Ponder.LOGGER.warn("Cannot index MMCE machine {}", id, failure);
            }
        }
        Collections.sort(indexed, new Comparator<RuntimeMachine>() {
            @Override
            public int compare(RuntimeMachine left, RuntimeMachine right) {
                return left.description.getId().toString().compareTo(right.description.getId().toString());
            }
        });
        for (RuntimeMachine entry : indexed) {
            next.machines.put(entry.description.getId(), entry);
            if (Boolean.getBoolean("ponder.diagnostics")) {
                Ponder.LOGGER.info("MMCE_INDEX machine={} component={} bindings={} patternPositions={} dynamic={}",
                    entry.description.getId(), entry.description.getComponentId(),
                    Integer.valueOf(entry.description.getBindings().size()),
                    Integer.valueOf(entry.description.getPatternSize()),
                    Boolean.valueOf(entry.description.hasDynamicPattern()));
            }
        }
        checkBudget(start);
        next.message = next.machines.isEmpty()
            ? "MMCE 注册表目前没有已加载机器，请在机器加载完成后重新载入思索"
            : "已索引 " + next.machines.size() + " 台已注册机器";
        return next;
    }

    private void publish(Map<ResourceLocation, RuntimeMachine> nextMachines,
        Map<ResourceLocation, String> nextFailures, String message, long epoch) {
        machines.clear();
        machines.putAll(nextMachines);
        failures.clear();
        failures.putAll(nextFailures);
        snapshots.clear();
        generation++;
        sourceEpoch = epoch;
        availabilityMessage = message;
    }

    /** Keeps owned metadata/cache references so a failed Ponder reload can restore the matching index. */
    public Checkpoint checkpoint() {
        checkThread();
        return new Checkpoint(this);
    }

    public void restore(Checkpoint checkpoint) {
        checkThread();
        if (checkpoint == null || checkpoint.owner != this) {
            throw new IllegalArgumentException("MMCE checkpoint belongs to a different index");
        }
        machines.clear();
        machines.putAll(checkpoint.machines);
        failures.clear();
        failures.putAll(checkpoint.failures);
        snapshots.clear();
        snapshots.putAll(checkpoint.snapshots);
        generation = checkpoint.generation;
        sourceEpoch = checkpoint.sourceEpoch;
        availabilityMessage = checkpoint.availabilityMessage;
    }

    public List<MmceMachineDescriptor> getMachines() {
        List<MmceMachineDescriptor> result = new ArrayList<MmceMachineDescriptor>(machines.size());
        for (RuntimeMachine machine : machines.values()) {
            result.add(machine.description);
        }
        return Collections.unmodifiableList(result);
    }

    public MmceMachineDescriptor getMachine(ResourceLocation id) {
        RuntimeMachine machine = machines.get(id);
        return machine == null ? null : machine.description;
    }

    /** Lazy metadata lookup only. The caller must already be on the physical client thread. */
    public MmceStaticModelSpec getDefaultModelSpec(ResourceLocation id) {
        checkThread();
        if (!FMLCommonHandler.instance().getSide().isClient()
            || !Loader.isModLoaded("modularmachinery") || !Loader.isModLoaded("geckolib3")) {
            return MmceStaticModelSpec.unavailable("默认模型预览需要客户端安装 MMCE 与 GeckoLib");
        }
        RuntimeMachine machine = machines.get(id);
        if (machine == null) {
            return MmceStaticModelSpec.unavailable("此机器未在 MMCE 注册，无法读取默认模型");
        }
        try {
            return MmceRegistryReadGuard.readChecked(() -> {
                requireCurrentSource();
                if (modelLookup == null) {
                    modelLookup = new MmceDefaultModelLookup();
                }
                return modelLookup.lookup(machine.source);
            });
        } catch (MmceRegistryReadGuard.ReloadInProgressException changing) {
            return MmceStaticModelSpec.unavailable("MMCE 正在重载，暂时显示已有方块结构");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rethrowFatalTarget(failure);
            Ponder.LOGGER.warn("Cannot read default MMCE model metadata for {}", id, failure);
            return MmceStaticModelSpec.unavailable("默认模型接口不可用，当前显示方块结构：" + describe(failure));
        }
    }

    public String getAvailabilityMessage() {
        return availabilityMessage;
    }

    public Map<ResourceLocation, String> getFailures() {
        return Collections.unmodifiableMap(new LinkedHashMap<ResourceLocation, String>(failures));
    }

    public long getGeneration() {
        return generation;
    }

    public int getCachedStructureCount() {
        return snapshots.size();
    }

    public void clearPreviewCache() {
        checkThread();
        snapshots.clear();
    }

    public MmceStructureSnapshot getSnapshot(ResourceLocation id) {
        return getSnapshot(id, Variant.MINIMUM);
    }

    /** The only path that expands a structure; invoked when this machine is opened. */
    public MmceStructureSnapshot getSnapshot(ResourceLocation id, Variant variant) {
        checkThread();
        String key = id + "|" + variant;
        MmceStructureSnapshot cached = snapshots.get(key);
        if (cached != null) {
            return cached;
        }
        RuntimeMachine machine = machines.get(id);
        if (machine == null) {
            throw new IllegalArgumentException("MMCE 机器未注册：" + id);
        }
        long start = System.nanoTime();
        try {
            MmceStructureSnapshot snapshot = MmceRegistryReadGuard.readChecked(() -> {
                requireCurrentSource();
                return buildSnapshot(machine, variant, start);
            });
            snapshots.put(key, snapshot);
            failures.remove(id);
            Ponder.LOGGER.info("MMCE preview {} [{}]: {} positions, {} materials, {} ms", id, variant,
                Integer.valueOf(snapshot.getParts().size()), Integer.valueOf(snapshot.getMaterials().size()),
                Long.valueOf((System.nanoTime() - start) / 1000000L));
            return snapshot;
        } catch (MmceRegistryReadGuard.ReloadInProgressException changing) {
            throw new IllegalStateException("MMCE 正在重载，请待索引更新后再打开此演示", changing);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rethrowFatalTarget(failure);
            String reason = describe(failure);
            failures.put(id, reason);
            Ponder.LOGGER.warn("Cannot create read-only MMCE preview {} [{}]", id, variant, failure);
            throw new IllegalStateException("机器 " + id + " 无法生成演示：" + reason, failure);
        }
    }

    private RuntimeMachine indexMachine(Object source, ResourceLocation id) throws ReflectiveOperationException {
        Object pattern = bridge.call(source, "getPattern");
        int size = asMap(bridge.call(pattern, "getPattern")).size();
        String title = String.valueOf(bridge.call(source, "getLocalizedName"));
        boolean factoryOnly = Boolean.TRUE.equals(bridge.call(source, "isFactoryOnly"))
            && Boolean.TRUE.equals(bridge.call(source, "isHasFactory"));
        List<Block> controllers = bridge.controllers(source, factoryOnly);
        List<ItemStack> bindings = new ArrayList<ItemStack>();
        for (Block controller : controllers) {
            Item item = Item.getItemFromBlock(controller);
            if (item != null && item != Items.AIR) {
                bindings.add(new ItemStack(item));
            }
        }
        ItemStack blueprint = bridge.blueprint(id);
        if (!blueprint.isEmpty()) {
            bindings.add(blueprint);
        }
        List<String> notes = new ArrayList<String>();
        notes.add("仅预览静态结构与已绑定默认外观；不运行机器逻辑、动画或发光效果");
        if (bindings.isEmpty()) {
            notes.add("未找到已注册的控制器物品或专属蓝图，仍可通过机器目录查看");
        }
        Block displayController = controllers.isEmpty() ? bridge.genericController(factoryOnly) : controllers.get(0);
        if (controllers.isEmpty()) {
            notes.add("此机器使用通用控制器，物品入口使用带机器标识的蓝图");
        }
        Map<?, ?> dynamic = asMap(bridge.call(source, "getDynamicPatterns"));
        AxisAlignedBB bounds = (AxisAlignedBB) bridge.call(source, "getControllerBoundingBox");
        MmceMachineDescriptor description = new MmceMachineDescriptor(id, title, size, bindings,
            !dynamic.isEmpty(), bounds, notes);
        return new RuntimeMachine(source, description, displayController);
    }

    private MmceStructureSnapshot buildSnapshot(RuntimeMachine entry, Variant variant, long started)
        throws ReflectiveOperationException {
        Object machine = entry.source;
        Object sourcePattern = bridge.call(machine, "getPattern");
        MmcePreviewLimits.checkBlockCount((long) asMap(bridge.call(sourcePattern, "getPattern")).size() + 1L);
        Object pattern = bridge.copyPattern(sourcePattern);
        List<String> notes = new ArrayList<String>(entry.description.getNotes());
        Map<?, ?> dynamic = asMap(bridge.call(machine, "getDynamicPatterns"));
        for (Map.Entry<?, ?> dynamicEntry : dynamic.entrySet()) {
            checkBudget(started);
            Object extension = dynamicEntry.getValue();
            int minimum = ((Number) bridge.call(extension, "getMinSize")).intValue();
            int maximum = ((Number) bridge.call(extension, "getMaxSize")).intValue();
            if (minimum < 0 || maximum < minimum) {
                throw new IllegalArgumentException("动态结构尺寸无效：" + dynamicEntry.getKey());
            }
            int repeats = variant == Variant.MINIMUM ? minimum : maximum;
            Object repeatedPattern = bridge.call(extension, "getPattern");
            Object endPattern = bridge.call(extension, "getPatternEnd");
            int repeatedCount = repeatedPattern == null ? 0
                : asMap(bridge.call(repeatedPattern, "getPattern")).size();
            int endCount = endPattern == null ? 0 : asMap(bridge.call(endPattern, "getPattern")).size();
            MmcePreviewLimits.expansionUpperBound(
                asMap(bridge.call(pattern, "getPattern")).size() + 1L, repeatedCount, repeats, endCount);
            Collection<?> faces = (Collection<?>) bridge.call(extension, "getFaces");
            if (faces.isEmpty() || repeatedPattern == null) {
                throw new IllegalArgumentException("动态结构缺少方向或重复片段：" + dynamicEntry.getKey());
            }
            EnumFacing face = firstFacing(faces);
            bridge.call(extension, "addPatternToBlockArray",
                new Class<?>[] { bridge.blockArrayClass, int.class, EnumFacing.class, EnumFacing.class },
                pattern, Integer.valueOf(repeats), face, EnumFacing.NORTH);
            notes.add("动态段 " + dynamicEntry.getKey() + "：本例 " + repeats + " 段，允许 "
                + minimum + " 至 " + maximum + " 段；控制器朝北");
        }
        Map<?, ?> patternMap = asMap(bridge.call(pattern, "getPattern"));
        Map<BlockPos, List<Object>> information = new LinkedHashMap<BlockPos, List<Object>>();
        Map<BlockPos, String> selectorTags = new LinkedHashMap<BlockPos, String>();
        for (Map.Entry<?, ?> part : patternMap.entrySet()) {
            BlockPos position = ((BlockPos) part.getKey()).toImmutable();
            information.put(position, new ArrayList<Object>(Collections.singletonList(part.getValue())));
        }
        Map<?, ?> tags = asMap(bridge.call(pattern, "getTaggedPositions"));
        for (Map.Entry<?, ?> tag : tags.entrySet()) {
            selectorTags.put(((BlockPos) tag.getKey()).toImmutable(),
                String.valueOf(bridge.call(tag.getValue(), "getTag")));
        }
        Map<?, ?> replacements = asMap(bridge.call(machine, "getModifiersAsMatchingReplacements"));
        int optionalPositions = 0;
        for (Map.Entry<?, ?> replacement : replacements.entrySet()) {
            BlockPos position = ((BlockPos) replacement.getKey()).toImmutable();
            List<Object> choices = information.get(position);
            if (choices == null) {
                choices = new ArrayList<Object>();
                choices.add(null);
                information.put(position, choices);
                optionalPositions++;
            }
            choices.addAll((Collection<?>) replacement.getValue());
        }
        if (optionalPositions > 0) {
            notes.add(optionalPositions + " 个可选升级位置保留为空气；可替代方块规则已保留在材料数据中");
        }
        if (entry.controller == null || entry.controller == Blocks.AIR) {
            throw new IllegalArgumentException("注册表中没有有效的控制器方块");
        }
        // MMCE removes the origin from its matching pattern. It is always the controller.
        information.remove(BlockPos.ORIGIN);
        information.put(BlockPos.ORIGIN, Collections.<Object>emptyList());
        MmcePreviewLimits.checkBlockCount(information.size());
        List<BlockPos> positions = new ArrayList<BlockPos>(information.keySet());
        Collections.sort(positions, new Comparator<BlockPos>() {
            @Override
            public int compare(BlockPos left, BlockPos right) {
                int y = Integer.compare(left.getY(), right.getY());
                int z = Integer.compare(left.getZ(), right.getZ());
                return y != 0 ? y : z != 0 ? z : Integer.compare(left.getX(), right.getX());
            }
        });
        List<int[]> coordinateData = new ArrayList<int[]>(positions.size());
        for (BlockPos position : positions) {
            coordinateData.add(new int[] { position.getX(), position.getY(), position.getZ() });
        }
        MmceStructureGeometry.Bounds bounds = MmceStructureGeometry.bounds(coordinateData);
        BlockPos size = new BlockPos(bounds.sizeX, bounds.sizeY, bounds.sizeZ);
        BlockPos origin = new BlockPos(bounds.originX, bounds.originY, bounds.originZ);
        List<Part> parts = new ArrayList<Part>(positions.size());
        Map<Object, Alternative> alternativeCache = new IdentityHashMap<Object, Alternative>();
        long alternativesCount = 0L;
        int nbtPredicates = 0;
        for (BlockPos position : positions) {
            checkBudget(started);
            boolean controller = position.equals(BlockPos.ORIGIN);
            List<Alternative> alternatives = new ArrayList<Alternative>();
            IBlockState displayState;
            if (controller) {
                displayState = entry.controller.getDefaultState();
                alternatives.add(simpleAlternative(displayState));
            } else {
                List<Object> choices = information.get(position);
                Object first = choices.get(0);
                displayState = first == null ? Blocks.AIR.getDefaultState()
                    : (IBlockState) bridge.call(first, "getSampleState", new Class<?>[] { long.class }, Long.valueOf(0L));
                for (Object choice : choices) {
                    Alternative alternative = alternativeCache.get(choice);
                    if (alternative == null) {
                        alternative = choice == null ? simpleAlternative(Blocks.AIR.getDefaultState())
                            : copyAlternative(choice);
                        alternativeCache.put(choice, alternative);
                    }
                    alternatives.add(alternative);
                    alternativesCount += alternative.getStates().size();
                    if (alternative.hasCustomPredicate()) {
                        nbtPredicates++;
                    }
                    if (alternativesCount > MmcePreviewLimits.MAX_TOTAL_ALTERNATIVES) {
                        throw new IllegalArgumentException("替代状态数量超过预览预算");
                    }
                }
            }
            if (displayState == null) {
                throw new IllegalArgumentException("位置 " + position + " 没有可显示的方块");
            }
            parts.add(new Part(position, position.add(origin), displayState, alternatives, selectorTags.get(position),
                controller, bridge.isMachineComponent(displayState.getBlock())));
        }
        if (nbtPredicates > 0) {
            notes.add("包含 " + nbtPredicates + " 个自定义 NBT 检查条件；预览不会执行检查器或生产逻辑");
        }
        notes.add("材料数量按当前展示方案统计；同一位置的替代状态不是额外所需材料");
        checkBudget(started);
        return new MmceStructureSnapshot(entry.description, variant, parts, origin, size, notes, generation,
            System.nanoTime() - started);
    }

    private Alternative copyAlternative(Object information) throws ReflectiveOperationException {
        Set<IBlockState> states = new LinkedHashSet<IBlockState>();
        for (Object descriptor : (Collection<?>) bridge.call(information, "getMatchingStates")) {
            for (Object state : (Collection<?>) bridge.call(descriptor, "getApplicable")) {
                if (!(state instanceof IBlockState)) {
                    throw new IllegalArgumentException("MMCE 方块匹配规则包含无效状态");
                }
                states.add((IBlockState) state);
                if (states.size() > MmcePreviewLimits.MAX_ALTERNATIVES_PER_POSITION) {
                    throw new IllegalArgumentException("单位置替代状态过多");
                }
            }
        }
        List<ItemStack> ingredients = new ArrayList<ItemStack>();
        for (Object ingredient : (Collection<?>) bridge.call(information, "getIngredientList")) {
            if (ingredient instanceof ItemStack && !((ItemStack) ingredient).isEmpty()) {
                ingredients.add(((ItemStack) ingredient).copy());
            }
        }
        return new Alternative(new ArrayList<IBlockState>(states), ingredients,
            (NBTTagCompound) bridge.call(information, "getMatchingTag"),
            (NBTTagCompound) bridge.call(information, "getPreviewTag"),
            bridge.call(information, "getNBTChecker") != null);
    }

    private static Alternative simpleAlternative(IBlockState state) {
        Item item = Item.getItemFromBlock(state.getBlock());
        List<ItemStack> ingredients = item == null || item == Items.AIR
            ? Collections.<ItemStack>emptyList()
            : Collections.singletonList(new ItemStack(item, 1, state.getBlock().damageDropped(state)));
        return new Alternative(Collections.singletonList(state), ingredients, null, null, false);
    }

    private static EnumFacing firstFacing(Collection<?> faces) {
        for (EnumFacing direction : EnumFacing.values()) {
            if (faces.contains(direction)) {
                return direction;
            }
        }
        throw new IllegalArgumentException("动态结构方向无效");
    }

    private void checkThread() {
        if (ownerThread == null) {
            ownerThread = Thread.currentThread();
        } else if (ownerThread != Thread.currentThread()) {
            throw new IllegalStateException("MMCE 演示注册数据只能在客户端主线程读取");
        }
    }

    private static void checkBudget(long started) {
        if (System.nanoTime() - started > MmcePreviewLimits.MAX_BUILD_NANOS) {
            throw new IllegalArgumentException("结构生成超过时间预算，已停止以避免界面长时间无响应");
        }
    }

    private void requireCurrentSource() {
        if (sourceEpoch != MmceRegistryReadGuard.currentEpoch()) {
            throw new IllegalStateException("MMCE 机器定义已变化，请等待思索刷新或使用 /ponder reload");
        }
    }

    private static Map<?, ?> asMap(Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("MMCE 接口返回了非映射数据");
        }
        return (Map<?, ?>) value;
    }

    private static String describe(Throwable cause) {
        while (cause instanceof InvocationTargetException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    static void rethrowFatalTarget(Throwable cause) {
        while (cause instanceof InvocationTargetException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof VirtualMachineError) {
            throw (VirtualMachineError) cause;
        }
        if (cause instanceof ThreadDeath) {
            throw (ThreadDeath) cause;
        }
    }

    public static final class Checkpoint {
        private final MmceMachineIndex owner;
        private final Map<ResourceLocation, RuntimeMachine> machines;
        private final Map<ResourceLocation, String> failures;
        private final Map<String, MmceStructureSnapshot> snapshots;
        private final long generation;
        private final long sourceEpoch;
        private final String availabilityMessage;

        private Checkpoint(MmceMachineIndex index) {
            owner = index;
            machines = new LinkedHashMap<ResourceLocation, RuntimeMachine>(index.machines);
            failures = new LinkedHashMap<ResourceLocation, String>(index.failures);
            snapshots = new LinkedHashMap<String, MmceStructureSnapshot>(index.snapshots);
            generation = index.generation;
            sourceEpoch = index.sourceEpoch;
            availabilityMessage = index.availabilityMessage;
        }
    }

    private static final class IndexedMachines {
        private final Map<ResourceLocation, RuntimeMachine> machines =
            new LinkedHashMap<ResourceLocation, RuntimeMachine>();
        private final Map<ResourceLocation, String> failures = new LinkedHashMap<ResourceLocation, String>();
        private final long epoch;
        private String message;

        private IndexedMachines(long epoch) {
            this.epoch = epoch;
        }
    }

    private static final class RuntimeMachine {
        private final Object source;
        private final MmceMachineDescriptor description;
        private final Block controller;

        private RuntimeMachine(Object source, MmceMachineDescriptor description, Block controller) {
            this.source = source;
            this.description = description;
            this.controller = controller;
        }
    }

    private static final class Bridge {
        private static final String BASE = "hellfirepvp.modularmachinery.common.";
        private final Class<?> registryClass = load(BASE + "machine.MachineRegistry");
        private final Class<?> controllerClass = load(BASE + "block.BlockController");
        private final Class<?> factoryClass = load(BASE + "block.BlockFactoryController");
        private final Class<?> componentClass = load(BASE + "block.BlockMachineComponent");
        private final Class<?> blueprintClass = load(BASE + "item.ItemBlueprint");
        private final Class<?> blocksClass = load(BASE + "lib.BlocksMM");
        private final Class<?> blockArrayClass = load(BASE + "util.BlockArray");
        private final Class<?> taggedArrayClass = load(BASE + "machine.TaggedPositionBlockArray");
        private final Map<String, Method> methods = new LinkedHashMap<String, Method>();

        private Bridge() throws ClassNotFoundException {
        }

        private Collection<?> loadedMachines() throws ReflectiveOperationException {
            return (Collection<?>) call(registryClass, "getLoadedMachines");
        }

        private Object copyPattern(Object original) throws ReflectiveOperationException {
            Constructor<?> copy = taggedArrayClass.getConstructor(taggedArrayClass);
            return copy.newInstance(original);
        }

        private List<Block> controllers(Object machine, boolean factoryOnly) throws ReflectiveOperationException {
            List<Block> result = new ArrayList<Block>();
            if (factoryOnly) {
                addController(result, factoryClass, "FACTORY_CONTROLLERS", machine);
            } else {
                addController(result, controllerClass, "MACHINE_CONTROLLERS", machine);
                addController(result, controllerClass, "MOC_MACHINE_CONTROLLERS", machine);
                addController(result, factoryClass, "FACTORY_CONTROLLERS", machine);
            }
            return result;
        }

        private void addController(List<Block> result, Class<?> owner, String field, Object machine)
            throws ReflectiveOperationException {
            Object block = asMap(owner.getField(field).get(null)).get(machine);
            if (block instanceof Block && !result.contains(block)) {
                result.add((Block) block);
            }
        }

        private Block genericController(boolean factory) throws ReflectiveOperationException {
            Object block = blocksClass.getField(factory ? "blockFactoryController" : "blockController").get(null);
            return block instanceof Block ? (Block) block : null;
        }

        private ItemStack blueprint(ResourceLocation id) throws ReflectiveOperationException {
            for (Item item : Item.REGISTRY) {
                if (blueprintClass.isInstance(item)) {
                    ItemStack stack = new ItemStack(item);
                    call(blueprintClass, "setAssociatedMachine",
                        new Class<?>[] { ItemStack.class, ResourceLocation.class }, stack, id);
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        }

        private boolean isMachineComponent(Block block) {
            return componentClass.isInstance(block);
        }

        private Object call(Object receiver, String name) throws ReflectiveOperationException {
            return call(receiver, name, new Class<?>[0]);
        }

        private Object call(Object receiver, String name, Class<?>[] types, Object... parameters)
            throws ReflectiveOperationException {
            Class<?> owner = receiver instanceof Class ? (Class<?>) receiver : receiver.getClass();
            String key = owner.getName() + '#' + name + Arrays.toString(types);
            Method method = methods.get(key);
            if (method == null) {
                method = owner.getMethod(name, types);
                methods.put(key, method);
            }
            return method.invoke(receiver instanceof Class ? null : receiver, parameters);
        }

        private static Class<?> load(String name) throws ClassNotFoundException {
            return Class.forName(name, false, MmceMachineIndex.class.getClassLoader());
        }
    }
}
