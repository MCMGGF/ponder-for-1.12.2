package net.createmod.ponder.authoring.practice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import net.createmod.ponder.authoring.practice.PracticeDefinition.Action;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Goal;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Menu;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Operation;
import net.createmod.ponder.foundation.ui.DetachedPonderWorld;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.NonNullList;

/** No live player, live world, network, or live Container is retained by this model. */
public final class PracticeSession {
    private final PracticeDefinition definition;
    private final List<ItemStack> slots = new ArrayList<ItemStack>();
    private final Map<String, Integer> data = new LinkedHashMap<String, Integer>();
    private final Map<String, PriorityQueue<DataTween>> dataTweens = new LinkedHashMap<String, PriorityQueue<DataTween>>();
    private PracticeMode mode;
    private ItemStack cursor = ItemStack.EMPTY;
    private int tick;
    private int eventIndex;
    private boolean visible;
    private String notice = "";
    private boolean closed;
    private DetachedPonderWorld recipeWorld;
    private final Map<NBTTagList, CraftingSnapshot> craftingCache = new LinkedHashMap<NBTTagList, CraftingSnapshot>();
    private final Map<NBTTagCompound, ItemStack> smeltingCache = new LinkedHashMap<NBTTagCompound, ItemStack>();
    private final Map<NBTTagCompound, FuelSnapshot> fuelCache = new LinkedHashMap<NBTTagCompound, FuelSnapshot>();

    public PracticeSession(PracticeDefinition definition, PracticeMode mode) {
        if (definition == null || mode == null) throw new IllegalArgumentException("Missing practice definition/mode");
        this.definition = definition;
        this.mode = mode;
        reset();
    }

    public PracticeDefinition getDefinition() { return definition; }
    public PracticeMode getMode() { return mode; }
    public int getTick() { return tick; }
    public int getSlotCount() { return slots.size(); }
    public boolean isVisible() { return visible; }
    public boolean isClosed() { return closed; }
    public boolean isReadOnly() { return definition.getMenu() == Menu.READ_ONLY; }
    public String getNotice() { return notice; }
    public String getReadOnlyReason() {
        return isReadOnly() ? "Unsupported menu: " + definition.getMenuId() + "; displayed as a read-only snapshot" : "";
    }
    public ItemStack getSlot(int slot) { checkSlot(slot); return slots.get(slot).copy(); }
    public ItemStack getCursor() { return cursor.copy(); }
    public Map<String, Integer> getData() { return Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(data)); }
    public int getData(String key) { Integer value = data.get(key); return value == null ? 0 : value; }
    public int getFurnaceCookTotal() { int value = getData("cookTotal"); return value > 0 ? value : 200; }
    public int getFurnaceProgressPixels(int width) {
        int boundedWidth = Math.max(0, width);
        return (int) Math.min((long) boundedWidth, (long) Math.max(0, getData("cookTime")) * boundedWidth / getFurnaceCookTotal());
    }
    public void setMode(PracticeMode mode) {
        if (mode == null) throw new IllegalArgumentException("Missing mode");
        this.mode = mode;
        reset();
    }

    public void reset() {
        if (closed) throw new IllegalStateException("Practice session is closed");
        slots.clear();
        for (int i = 0; i < definition.getSlotCount(); i++) slots.add(ItemStack.EMPTY);
        for (Map.Entry<Integer, ItemStack> entry : definition.getInitialSlots().entrySet())
            slots.set(entry.getKey(), entry.getValue().copy());
        data.clear();
        dataTweens.clear();
        data.putAll(definition.getData());
        cursor = ItemStack.EMPTY;
        tick = 0;
        eventIndex = 0;
        visible = true;
        notice = "";
        refreshCrafting();
        applyDueActions();
    }

    public void seek(int target) {
        if (target < 0 || target > definition.getDuration()) throw new IllegalArgumentException("Tick outside timeline");
        reset();
        while (tick < target) advance();
    }

    public void advance() {
        if (closed) return;
        if (tick >= definition.getDuration()) return;
        tick++;
        if (!isReadOnly()) {
            if (definition.getMenu() == Menu.FURNACE) tickFurnace();
            if (definition.getMenu() == Menu.HOPPER && getData("locked") == 0 && tick % 8 == 0) transferHopper();
        }
        tickDataTweens();
        applyDueActions();
    }

    private void applyDueActions() {
        List<Action> actions = definition.getActions();
        while (eventIndex < actions.size() && actions.get(eventIndex).tick <= tick) {
            Action action = actions.get(eventIndex++);
            if (mode == PracticeMode.PRACTICE && action.operation != Operation.SHOW
                && action.operation != Operation.HIDE) continue;
            apply(action);
        }
    }

    private void apply(Action action) {
        if (action.operation == Operation.SHOW) { visible = true; return; }
        if (action.operation == Operation.HIDE) { visible = false; return; }
        if (action.operation == Operation.SET_SLOT) {
            slots.set(action.slot, bounded(action.getStack())); refreshCrafting(); return;
        }
        if (action.operation == Operation.SET_DATA) { data.put(action.key, action.value); return; }
        if (action.operation == Operation.DATA_TWEEN) {
            DataTween tween = new DataTween(action, eventIndex);
            if (action.duration > 1) dataTweens.computeIfAbsent(action.key,
                key -> new PriorityQueue<DataTween>((a, b) -> Integer.compare(b.order, a.order))).add(tween);
            data.put(action.key, tween.valueAt(tick));
            return;
        }
        if (isReadOnly()) return;
        clickInternal(action.slot, action.operation == Operation.CLICK_RIGHT,
            action.operation == Operation.TRANSFER);
    }

    public boolean click(int slot, boolean right, boolean transfer) {
        if (closed) return false;
        checkSlot(slot);
        if (mode == PracticeMode.WATCH || isReadOnly() || !visible) return false;
        return clickInternal(slot, right, transfer);
    }

    private static final class DataTween {
        final Action action;
        final int order;
        DataTween(Action action, int order) { this.action = action; this.order = order; }
        int valueAt(int tick) {
            int elapsed = Math.max(0, Math.min(action.duration, tick - action.tick + 1));
            return (int) Math.round(action.from + ((double) action.value - action.from) * elapsed / action.duration);
        }
    }

    private void tickDataTweens() {
        java.util.Iterator<Map.Entry<String, PriorityQueue<DataTween>>> iterator = dataTweens.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PriorityQueue<DataTween>> entry = iterator.next();
            PriorityQueue<DataTween> queue = entry.getValue();
            // Keep declaration precedence without expanding one action into thousands of per-tick writes.
            while (!queue.isEmpty() && queue.peek().action.getEndTick() < tick) queue.poll();
            if (queue.isEmpty()) iterator.remove();
            else data.put(entry.getKey(), queue.peek().valueAt(tick));
        }
    }

    public boolean toggleLock() {
        if (closed) return false;
        if (mode == PracticeMode.WATCH || isReadOnly() || definition.getMenu() != Menu.HOPPER) return false;
        data.put("locked", getData("locked") == 0 ? 1 : 0);
        return true;
    }

    private boolean clickInternal(int slot, boolean right, boolean transfer) {
        notice = "";
        if (transfer) return transfer(slot);
        if (isOutput(slot)) return takeOutput(slot);
        ItemStack present = slots.get(slot);
        if (cursor.isEmpty()) {
            if (present.isEmpty()) return false;
            int count = right ? (present.getCount() + 1) / 2 : present.getCount();
            cursor = present.copy(); cursor.setCount(count);
            present.shrink(count);
            clean(slot);
        } else {
            if (!accepts(slot, cursor)) return false;
            if (present.isEmpty() || sameItem(present, cursor)) {
                int room = slotLimit(slot, cursor) - (present.isEmpty() ? 0 : present.getCount());
                int count = Math.min(room, right ? 1 : cursor.getCount());
                if (count <= 0) return false;
                if (present.isEmpty()) { present = cursor.copy(); present.setCount(count); slots.set(slot, present); }
                else present.grow(count);
                cursor.shrink(count);
            } else {
                if (right || cursor.getCount() > slotLimit(slot, cursor)) return false;
                slots.set(slot, cursor); cursor = present;
            }
        }
        if (cursor.isEmpty()) cursor = ItemStack.EMPTY;
        refreshCrafting();
        return true;
    }

    private boolean takeOutput(int slot) {
        ItemStack output = slots.get(slot);
        if (output.isEmpty() || (!cursor.isEmpty() && !sameItem(cursor, output))) return false;
        int held = cursor.isEmpty() ? 0 : cursor.getCount();
        if (held + output.getCount() > output.getMaxStackSize()) return false;
        if (definition.getMenu() == Menu.WORKBENCH) {
            CraftingSnapshot recipe = craftingSnapshot();
            if (!ItemStack.areItemStacksEqual(recipe.output, output)) { refreshCrafting(); return false; }
            List<ItemStack> remains = recipe.remains;
            // Reserve all remainder space before consuming, never silently discard a container item.
            List<ItemStack> after = copySlots();
            for (int i = 0; i < 9; i++) {
                ItemStack input = after.get(i + 1);
                if (!input.isEmpty()) input.shrink(1);
                ItemStack rest = remains.get(i).copy();
                if (rest.isEmpty()) continue;
                if (input.isEmpty()) after.set(i + 1, rest);
                else if (sameItem(input, rest) && input.getCount() + rest.getCount() <= input.getMaxStackSize()) input.grow(rest.getCount());
                else if (!insert(after, rest, definition.getMenu().slots, slots.size())) {
                    notice = "No virtual space for recipe remainder"; return false;
                }
            }
            slots.clear(); slots.addAll(after);
        } else slots.set(slot, ItemStack.EMPTY);
        if (cursor.isEmpty()) cursor = output.copy(); else cursor.grow(output.getCount());
        refreshCrafting();
        return true;
    }

    private boolean transfer(int from) {
        if (definition.getMenu() == Menu.WORKBENCH && from == 0) return transferCraftingOutput();
        ItemStack source = slots.get(from);
        if (source.isEmpty()) return false;
        int old = source.getCount();
        Menu menu = definition.getMenu();
        if (menu == Menu.WORKBENCH) {
            if (from < 10) mergeRange(source, 10, 46, false);
            else if (from < 37) mergeRange(source, 37, 46, false);
            else mergeRange(source, 10, 37, false);
        } else if (menu == Menu.FURNACE) {
            if (from == 2) mergeRange(source, 3, 39, true);
            else if (from < 3) mergeRange(source, 3, 39, false);
            else if (!smeltingResult(source).isEmpty()) mergeRange(source, 0, 1, false);
            else if (fuelSnapshot(source).burnTime > 0) mergeRange(source, 1, 2, false);
            else if (from < 30) mergeRange(source, 30, 39, false);
            else mergeRange(source, 3, 30, false);
        } else {
            boolean fromMenu = from < menu.slots;
            mergeRange(source, fromMenu ? menu.slots : 0, fromMenu ? slots.size() : menu.slots, fromMenu);
        }
        clean(from); refreshCrafting();
        return source.getCount() != old;
    }

    private boolean transferCraftingOutput() {
        ItemStack first = slots.get(0).copy();
        if (first.isEmpty()) return false;
        ItemStack held = cursor.copy();
        boolean moved = false;
        // Vanilla quick crafting repeats; retain a finite preview budget and never drop virtual overflow.
        for (int crafts = 0; crafts < 64 && sameItem(first, slots.get(0)); crafts++) {
            List<ItemStack> before = copySlots();
            boolean committed = false;
            try {
                cursor = ItemStack.EMPTY;
                if (!takeOutput(0)) return moved;
                mergeRange(cursor, 10, 46, true);
                if (!cursor.isEmpty()) { notice = "No virtual space for complete crafting output"; return moved; }
                committed = true; moved = true;
            } finally {
                cursor = held.copy();
                if (!committed) { slots.clear(); slots.addAll(before); }
            }
        }
        if (sameItem(first, slots.get(0))) notice = "Virtual quick crafting limit reached (64 recipes)";
        return moved;
    }

    private void mergeRange(ItemStack source, int start, int end, boolean reverse) {
        for (int pass = 0; pass < 2; pass++) for (int at = 0; at < end - start && !source.isEmpty(); at++) {
            int to = reverse ? end - 1 - at : start + at;
            if (slots.get(to).isEmpty() == (pass == 1) && accepts(to, source))
                moveInto(source, slots, to, slotLimit(to, source));
        }
    }

    private boolean accepts(int slot, ItemStack stack) {
        if (isOutput(slot)) return false;
        return definition.getMenu() != Menu.FURNACE || slot != 1
            || stack.getItem() == Items.BUCKET || fuelSnapshot(stack).burnTime > 0;
    }

    private int slotLimit(int slot, ItemStack stack) {
        return definition.getMenu() == Menu.FURNACE && slot == 1 && stack.getItem() == Items.BUCKET
            ? 1 : Math.min(64, stack.getMaxStackSize());
    }

    private boolean isOutput(int slot) {
        return definition.getMenu() == Menu.WORKBENCH && slot == 0
            || definition.getMenu() == Menu.FURNACE && slot == 2;
    }

    private void refreshCrafting() {
        if (definition.getMenu() != Menu.WORKBENCH) return;
        CraftingSnapshot recipe = craftingSnapshot();
        slots.set(0, recipe.output.copy());
        if (!recipe.error.isEmpty()) notice = recipe.error;
    }

    private CraftingSnapshot craftingSnapshot() {
        NBTTagList key = new NBTTagList();
        for (int i = 1; i <= 9; i++) key.appendTag(slots.get(i).writeToNBT(new NBTTagCompound()));
        CraftingSnapshot cached = craftingCache.get(key);
        if (cached != null) return cached;
        if (craftingCache.size() >= 512) return CraftingSnapshot.empty("Virtual recipe snapshot budget reached");
        CraftingSnapshot result;
        try {
            InventoryCrafting matrix = craftingMatrix();
            IRecipe recipe = CraftingManager.findMatchingRecipe(matrix, recipeWorld());
            if (recipe == null) result = CraftingSnapshot.empty("");
            else {
                ItemStack output = recipe.getCraftingResult(matrix);
                NonNullList<ItemStack> remains = recipe.getRemainingItems(matrix);
                if (remains.size() != 9) throw new IllegalArgumentException("Invalid recipe remainder size");
                if (!output.isEmpty() && output.getCount() > Math.min(64, output.getMaxStackSize()))
                    throw new IllegalArgumentException("Invalid recipe output count");
                for (ItemStack remainder : remains) {
                    if (!remainder.isEmpty() && remainder.getCount() > Math.min(64, remainder.getMaxStackSize()))
                        throw new IllegalArgumentException("Invalid recipe remainder count");
                }
                result = new CraftingSnapshot(output, remains, "");
            }
        } catch (RuntimeException failure) {
            result = CraftingSnapshot.empty("Registered recipe lookup failed: " + failure.getClass().getSimpleName());
        }
        craftingCache.put(key.copy(), result);
        return result;
    }

    private static final class CraftingSnapshot {
        final ItemStack output;
        final List<ItemStack> remains = new ArrayList<ItemStack>();
        final String error;
        CraftingSnapshot(ItemStack output, List<ItemStack> remains, String error) {
            this.output = output.copy(); this.error = error;
            for (ItemStack stack : remains) this.remains.add(stack.copy());
        }
        static CraftingSnapshot empty(String reason) {
            return new CraftingSnapshot(ItemStack.EMPTY, Collections.<ItemStack>emptyList(), reason);
        }
    }

    private InventoryCrafting craftingMatrix() {
        InventoryCrafting matrix = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return false; }
        }, 3, 3);
        for (int i = 0; i < 9; i++) matrix.setInventorySlotContents(i, slots.get(i + 1).copy());
        return matrix;
    }

    private DetachedPonderWorld recipeWorld() {
        if (recipeWorld == null) recipeWorld = new DetachedPonderWorld();
        return recipeWorld;
    }

    private void tickFurnace() {
        int burn = Math.max(0, getData("burnTime") - 1);
        ItemStack result = smeltingResult(slots.get(0));
        boolean canSmelt = !result.isEmpty() && (slots.get(2).isEmpty()
            || sameItem(slots.get(2), result) && slots.get(2).getCount() + result.getCount() <= result.getMaxStackSize());
        if (burn == 0 && canSmelt && !slots.get(1).isEmpty()) {
            ItemStack fuel = slots.get(1);
            FuelSnapshot snapshot = fuelSnapshot(fuel);
            burn = snapshot.burnTime;
            if (burn > 0) {
                data.put("fuelTime", burn);
                fuel.shrink(1);
                if (fuel.isEmpty()) slots.set(1, snapshot.remainder.copy());
            }
        }
        data.put("burnTime", burn);
        int total = getFurnaceCookTotal();
        int cook = burn > 0 && canSmelt ? Math.min(Math.max(0, getData("cookTime")), total - 1) + 1 : 0;
        // Captured progress and both render paths share the declared duration; vanilla defaults to 200 ticks.
        if (cook >= total) {
            slots.get(0).shrink(1); clean(0);
            if (slots.get(2).isEmpty()) slots.set(2, result.copy()); else slots.get(2).grow(result.getCount());
            cook = 0;
        }
        data.put("cookTime", cook);
    }

    private ItemStack smeltingResult(ItemStack input) {
        if (input.isEmpty()) return ItemStack.EMPTY;
        NBTTagCompound key = input.writeToNBT(new NBTTagCompound());
        ItemStack result = smeltingCache.get(key);
        if (result == null) {
            if (smeltingCache.size() >= 512) { notice = "Virtual recipe snapshot budget reached"; return ItemStack.EMPTY; }
            result = FurnaceRecipes.instance().getSmeltingResult(input.copy()).copy();
            if (!result.isEmpty() && result.getCount() > Math.min(64, result.getMaxStackSize())) {
                notice = "Invalid registered smelting output count"; result = ItemStack.EMPTY;
            }
            smeltingCache.put(key, result);
        }
        return result.copy();
    }

    private FuelSnapshot fuelSnapshot(ItemStack input) {
        NBTTagCompound key = input.writeToNBT(new NBTTagCompound());
        FuelSnapshot snapshot = fuelCache.get(key);
        if (snapshot == null) {
            if (fuelCache.size() >= 512) { notice = "Virtual fuel snapshot budget reached"; return new FuelSnapshot(0, ItemStack.EMPTY); }
            try {
                ItemStack fuel = input.copy();
                int duration = Math.max(0, TileEntityFurnace.getItemBurnTime(fuel));
                ItemStack remainder = fuel.isEmpty() || !fuel.getItem().hasContainerItem(fuel)
                    ? ItemStack.EMPTY : fuel.getItem().getContainerItem(fuel);
                snapshot = new FuelSnapshot(duration, remainder);
            } catch (RuntimeException failure) {
                notice = "Registered fuel lookup failed: " + failure.getClass().getSimpleName();
                snapshot = new FuelSnapshot(0, ItemStack.EMPTY);
            }
            fuelCache.put(key, snapshot);
        }
        return snapshot;
    }

    private static final class FuelSnapshot {
        final int burnTime;
        final ItemStack remainder;
        FuelSnapshot(int burnTime, ItemStack remainder) {
            this.burnTime = burnTime; this.remainder = remainder.copy();
        }
    }

    private void transferHopper() {
        int target = definition.getMenu().slots;
        for (int from = 0; from < definition.getMenu().slots; from++) {
            ItemStack source = slots.get(from);
            if (source.isEmpty()) continue;
            ItemStack one = source.copy(); one.setCount(1);
            if (insert(slots, one, target, slots.size())) { source.shrink(1); clean(from); }
            return;
        }
    }

    public boolean isGoalSatisfied(Goal goal) {
        switch (goal.kind) {
            case CURSOR_EMPTY: return cursor.isEmpty();
            case DATA_EQUALS: return getData(goal.key) == goal.value;
            case SLOT_EMPTY: return slots.get(goal.slot).isEmpty();
            case SLOT_EXACT: return ItemStack.areItemStacksEqual(slots.get(goal.slot), goal.getStack());
            case SLOT_CONTAINS:
                ItemStack expected = goal.getStack();
                return sameItem(slots.get(goal.slot), expected) && slots.get(goal.slot).getCount() >= expected.getCount();
            default: return false;
        }
    }

    public boolean isComplete() {
        if (closed || isReadOnly() || definition.getGoals().isEmpty()) return false;
        for (Goal goal : definition.getGoals()) if (!isGoalSatisfied(goal)) return false;
        return true;
    }

    public void close() {
        closed = true;
        visible = false;
        cursor = ItemStack.EMPTY;
        slots.clear();
        data.clear();
        dataTweens.clear();
        eventIndex = 0;
        craftingCache.clear();
        smeltingCache.clear();
        fuelCache.clear();
        recipeWorld = null;
    }

    private List<ItemStack> copySlots() {
        List<ItemStack> copy = new ArrayList<ItemStack>();
        for (ItemStack stack : slots) copy.add(stack.copy());
        return copy;
    }
    private static boolean insert(List<ItemStack> target, ItemStack source, int start, int end) {
        for (int i = start; i < end && !source.isEmpty(); i++) if (!target.get(i).isEmpty()) moveInto(source, target, i);
        for (int i = start; i < end && !source.isEmpty(); i++) if (target.get(i).isEmpty()) moveInto(source, target, i);
        return source.isEmpty();
    }
    private static void moveInto(ItemStack source, List<ItemStack> target, int slot) {
        moveInto(source, target, slot, Math.min(64, source.getMaxStackSize()));
    }
    private static void moveInto(ItemStack source, List<ItemStack> target, int slot, int limit) {
        ItemStack at = target.get(slot);
        if (!at.isEmpty() && !sameItem(at, source)) return;
        int count = Math.min(source.getCount(), limit - (at.isEmpty() ? 0 : at.getCount()));
        if (count <= 0) return;
        if (at.isEmpty()) { at = source.copy(); at.setCount(count); target.set(slot, at); }
        else at.grow(count);
        source.shrink(count);
    }
    private static ItemStack bounded(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy(); copy.setCount(Math.min(copy.getCount(), Math.min(64, copy.getMaxStackSize()))); return copy;
    }
    private static boolean sameItem(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
    }
    private void clean(int slot) { if (slots.get(slot).isEmpty()) slots.set(slot, ItemStack.EMPTY); }
    private void checkSlot(int slot) {
        if (slot < 0 || slot >= slots.size()) throw new IllegalArgumentException("Slot outside virtual inventory");
    }
}
