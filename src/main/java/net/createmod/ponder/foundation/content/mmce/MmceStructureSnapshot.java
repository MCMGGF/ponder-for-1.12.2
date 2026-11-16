package net.createmod.ponder.foundation.content.mmce;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/** Immutable display data copied from MMCE's parsed rules, not from a live world. */
public final class MmceStructureSnapshot {

    public enum Variant {
        MINIMUM("最小结构"),
        MAXIMUM("最大结构");

        private final String label;

        Variant(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public static final class Alternative {
        private final List<IBlockState> states;
        private final List<ItemStack> ingredients;
        private final NBTTagCompound matchingNbt;
        private final NBTTagCompound previewNbt;
        private final boolean customPredicate;

        Alternative(List<IBlockState> states, List<ItemStack> ingredients, NBTTagCompound matchingNbt,
            NBTTagCompound previewNbt, boolean customPredicate) {
            if (states.isEmpty()) {
                throw new IllegalArgumentException("结构位置没有可显示的有效方块状态");
            }
            this.states = Collections.unmodifiableList(new ArrayList<IBlockState>(states));
            this.ingredients = MmceMachineDescriptor.copyStacks(ingredients);
            this.matchingNbt = copy(matchingNbt);
            this.previewNbt = copy(previewNbt);
            this.customPredicate = customPredicate;
        }

        public List<IBlockState> getStates() {
            return states;
        }

        public List<ItemStack> getIngredients() {
            return MmceMachineDescriptor.copyStacks(ingredients);
        }

        public NBTTagCompound getMatchingNbt() {
            return copy(matchingNbt);
        }

        public NBTTagCompound getPreviewNbt() {
            return copy(previewNbt);
        }

        public boolean hasCustomPredicate() {
            return customPredicate;
        }
    }

    public static final class Part {
        private final BlockPos originalPosition;
        private final BlockPos previewPosition;
        private final IBlockState displayState;
        private final List<Alternative> alternatives;
        private final String selectorTag;
        private final boolean controller;
        private final boolean component;

        Part(BlockPos originalPosition, BlockPos previewPosition, IBlockState displayState,
            List<Alternative> alternatives, String selectorTag, boolean controller, boolean component) {
            this.originalPosition = originalPosition.toImmutable();
            this.previewPosition = previewPosition.toImmutable();
            this.displayState = displayState;
            this.alternatives = Collections.unmodifiableList(new ArrayList<Alternative>(alternatives));
            this.selectorTag = selectorTag == null ? "" : selectorTag;
            this.controller = controller;
            this.component = component;
        }

        public BlockPos getOriginalPosition() {
            return originalPosition;
        }

        public BlockPos getPreviewPosition() {
            return previewPosition;
        }

        public IBlockState getDisplayState() {
            return displayState;
        }

        public List<Alternative> getAlternatives() {
            return alternatives;
        }

        public NBTTagCompound getDisplayNbt() {
            Alternative first = alternatives.get(0);
            NBTTagCompound tag = first.getMatchingNbt();
            if (tag == null) {
                tag = new NBTTagCompound();
            }
            NBTTagCompound preview = first.getPreviewNbt();
            if (preview != null) {
                tag.merge(preview);
            }
            tag.setInteger("x", previewPosition.getX());
            tag.setInteger("y", previewPosition.getY());
            tag.setInteger("z", previewPosition.getZ());
            return tag;
        }

        public String getSelectorTag() {
            return selectorTag;
        }

        public boolean isController() {
            return controller;
        }

        public boolean isComponent() {
            return component;
        }

        public boolean isAir() {
            return displayState.getBlock() == Blocks.AIR;
        }
    }

    public static final class Material {
        private final List<Alternative> alternatives;
        private final IBlockState displayState;
        private final int count;
        private final List<BlockPos> positions;

        private Material(Part part, List<BlockPos> positions) {
            this.alternatives = part.getAlternatives();
            this.displayState = part.getDisplayState();
            this.count = positions.size();
            this.positions = Collections.unmodifiableList(new ArrayList<BlockPos>(positions));
        }

        public List<Alternative> getAlternatives() {
            return alternatives;
        }

        public IBlockState getDisplayState() {
            return displayState;
        }

        public int getCount() {
            return count;
        }

        public List<BlockPos> getPositions() {
            return positions;
        }

        public String getDisplayName() {
            for (Alternative alternative : alternatives) {
                for (ItemStack stack : alternative.ingredients) {
                    if (!stack.isEmpty()) {
                        return stack.getDisplayName();
                    }
                }
            }
            return displayState.getBlock().getLocalizedName();
        }

        public int getAlternativeStateCount() {
            int count = 0;
            for (Alternative alternative : alternatives) {
                count += alternative.states.size();
            }
            return count;
        }

        public boolean requiresNbt() {
            for (Alternative alternative : alternatives) {
                if (alternative.matchingNbt != null || alternative.customPredicate) {
                    return true;
                }
            }
            return false;
        }
    }

    private final MmceMachineDescriptor machine;
    private final Variant variant;
    private final List<Part> parts;
    private final List<Material> materials;
    private final Map<Integer, List<Part>> layers;
    private final BlockPos origin;
    private final BlockPos size;
    private final List<String> notes;
    private final AxisAlignedBB modelBounds;
    private final long generation;
    private final long buildNanos;

    MmceStructureSnapshot(MmceMachineDescriptor machine, Variant variant, List<Part> parts, BlockPos origin,
        BlockPos size, List<String> notes, long generation, long buildNanos) {
        this.machine = machine;
        this.variant = variant;
        this.parts = Collections.unmodifiableList(new ArrayList<Part>(parts));
        this.origin = origin;
        this.size = size;
        this.notes = Collections.unmodifiableList(new ArrayList<String>(notes));
        this.generation = generation;
        this.buildNanos = buildNanos;
        AxisAlignedBB sourceBounds = machine.getControllerBounds();
        this.modelBounds = sourceBounds == null ? null : sourceBounds.offset(origin);

        Map<Integer, List<Part>> mutableLayers = new TreeMap<Integer, List<Part>>();
        Map<String, List<BlockPos>> groupedPositions = new LinkedHashMap<String, List<BlockPos>>();
        Map<String, Part> groupedParts = new LinkedHashMap<String, Part>();
        for (Part part : parts) {
            if (part.isAir()) {
                continue;
            }
            Integer y = Integer.valueOf(part.getPreviewPosition().getY());
            if (!mutableLayers.containsKey(y)) {
                mutableLayers.put(y, new ArrayList<Part>());
            }
            mutableLayers.get(y).add(part);
            String key = materialKey(part);
            if (!groupedPositions.containsKey(key)) {
                groupedPositions.put(key, new ArrayList<BlockPos>());
                groupedParts.put(key, part);
            }
            groupedPositions.get(key).add(part.getPreviewPosition());
        }
        Map<Integer, List<Part>> immutableLayers = new LinkedHashMap<Integer, List<Part>>();
        for (Map.Entry<Integer, List<Part>> entry : mutableLayers.entrySet()) {
            immutableLayers.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        this.layers = Collections.unmodifiableMap(immutableLayers);
        List<Material> materialList = new ArrayList<Material>();
        for (Map.Entry<String, List<BlockPos>> entry : groupedPositions.entrySet()) {
            materialList.add(new Material(groupedParts.get(entry.getKey()), entry.getValue()));
        }
        Collections.sort(materialList, new Comparator<Material>() {
            @Override
            public int compare(Material left, Material right) {
                return Integer.compare(right.getCount(), left.getCount());
            }
        });
        this.materials = Collections.unmodifiableList(materialList);
    }

    public MmceMachineDescriptor getMachine() {
        return machine;
    }

    public Variant getVariant() {
        return variant;
    }

    public List<Part> getParts() {
        return parts;
    }

    public List<Material> getMaterials() {
        return materials;
    }

    public Map<Integer, List<Part>> getLayers() {
        return layers;
    }

    public BlockPos getOrigin() {
        return origin;
    }

    public BlockPos getSize() {
        return size;
    }

    public List<String> getNotes() {
        return notes;
    }

    public AxisAlignedBB getModelBounds() {
        return modelBounds;
    }

    public long getGeneration() {
        return generation;
    }

    public long getBuildNanos() {
        return buildNanos;
    }

    public int getVisibleBlockCount() {
        int count = 0;
        for (Part part : parts) {
            if (!part.isAir()) {
                count++;
            }
        }
        return count;
    }

    private static NBTTagCompound copy(NBTTagCompound tag) {
        return tag == null ? null : tag.copy();
    }

    private static String materialKey(Part part) {
        List<String> choices = new ArrayList<String>();
        boolean customPredicate = false;
        for (Alternative alternative : part.alternatives) {
            List<String> states = new ArrayList<String>();
            for (IBlockState state : alternative.states) {
                states.add(state.toString());
            }
            Collections.sort(states);
            choices.add(states.toString() + "|" + canonicalNbt(alternative.matchingNbt)
                + "|" + alternative.customPredicate);
            customPredicate |= alternative.customPredicate;
        }
        Collections.sort(choices);
        // Opaque callbacks are not proven equivalent merely because both exist.
        return choices.toString() + (customPredicate ? "|predicate-position:" + part.originalPosition : "");
    }

    private static String canonicalNbt(net.minecraft.nbt.NBTBase tag) {
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            List<String> keys = new ArrayList<String>(compound.getKeySet());
            Collections.sort(keys);
            StringBuilder result = new StringBuilder("{");
            for (String key : keys) {
                result.append(key.length()).append(':').append(key).append('=')
                    .append(canonicalNbt(compound.getTag(key))).append(';');
            }
            return result.append('}').toString();
        }
        if (tag instanceof net.minecraft.nbt.NBTTagList) {
            net.minecraft.nbt.NBTTagList list = (net.minecraft.nbt.NBTTagList) tag;
            StringBuilder result = new StringBuilder("[");
            for (int i = 0; i < list.tagCount(); i++) {
                result.append(canonicalNbt(list.get(i))).append(';');
            }
            return result.append(']').toString();
        }
        return tag == null ? "" : tag.getId() + ":" + tag.toString();
    }
}
