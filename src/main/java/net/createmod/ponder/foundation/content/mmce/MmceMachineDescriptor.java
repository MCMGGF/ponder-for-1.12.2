package net.createmod.ponder.foundation.content.mmce;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;

/** Lightweight registry metadata. This object never contains a world or a tile entity. */
public final class MmceMachineDescriptor {

    private final ResourceLocation id;
    private final String title;
    private final int patternSize;
    private final List<ItemStack> bindings;
    private final boolean dynamic;
    private final AxisAlignedBB controllerBounds;
    private final List<String> notes;

    MmceMachineDescriptor(ResourceLocation id, String title, int patternSize, List<ItemStack> bindings,
        boolean dynamic, AxisAlignedBB controllerBounds, List<String> notes) {
        this.id = id;
        this.title = title;
        this.patternSize = patternSize;
        this.bindings = copyStacks(bindings);
        this.dynamic = dynamic;
        this.controllerBounds = controllerBounds;
        this.notes = Collections.unmodifiableList(new ArrayList<String>(notes));
    }

    public ResourceLocation getId() {
        return id;
    }

    public ResourceLocation getComponentId() {
        return new ResourceLocation("ponder_legacy", "mmce/" + id.getNamespace() + "/" + id.getPath());
    }

    public String getTitle() {
        return title;
    }

    public int getPatternSize() {
        return patternSize;
    }

    public List<ItemStack> getBindings() {
        return copyStacks(bindings);
    }

    public ItemStack getDisplayStack() {
        return bindings.isEmpty() ? ItemStack.EMPTY : bindings.get(0).copy();
    }

    public boolean hasDynamicPattern() {
        return dynamic;
    }

    public AxisAlignedBB getControllerBounds() {
        return controllerBounds;
    }

    public List<String> getNotes() {
        return notes;
    }

    static List<ItemStack> copyStacks(List<ItemStack> source) {
        List<ItemStack> copy = new ArrayList<ItemStack>(source.size());
        for (ItemStack stack : source) {
            copy.add(stack.copy());
        }
        return Collections.unmodifiableList(copy);
    }
}
