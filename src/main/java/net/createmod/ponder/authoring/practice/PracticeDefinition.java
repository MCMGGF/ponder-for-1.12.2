package net.createmod.ponder.authoring.practice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.item.ItemStack;

/** Immutable input to an isolated interface session. Slots never refer to a player inventory. */
public final class PracticeDefinition {
    public enum Menu {
        WORKBENCH(10), FURNACE(3), CHEST(27), CHEST_LARGE(54), HOPPER(5), READ_ONLY(27);
        public final int slots;
        Menu(int slots) { this.slots = slots; }
        public static Menu fromId(String id) {
            String name = id == null ? "" : id.toLowerCase(Locale.ROOT);
            if ("minecraft:crafting".equals(name) || "minecraft:crafting_table".equals(name)
                || "minecraft:workbench".equals(name)) return WORKBENCH;
            if ("minecraft:furnace".equals(name)) return FURNACE;
            if ("minecraft:chest".equals(name)) return CHEST;
            if ("minecraft:generic_9x3".equals(name)) return CHEST;
            if ("minecraft:generic_9x6".equals(name) || "minecraft:large_chest".equals(name)) return CHEST_LARGE;
            if ("minecraft:hopper".equals(name)) return HOPPER;
            return READ_ONLY;
        }
    }

    public enum Operation { CLICK_LEFT, CLICK_RIGHT, TRANSFER, SET_SLOT, SET_DATA, HIDE, SHOW, DATA_TWEEN }

    public static final class Action {
        public final int tick;
        public final Operation operation;
        public final int slot;
        public final String key;
        public final int value;
        public final int from, duration;
        private final ItemStack stack;
        public Action(int tick, Operation operation, int slot, ItemStack stack, String key, int value) {
            this(tick, operation, slot, stack, key, value, value, 0);
        }
        private Action(int tick, Operation operation, int slot, ItemStack stack, String key, int value, int from, int duration) {
            if (tick < 0 || tick > 24000 || operation == null) throw new IllegalArgumentException("Invalid action");
            if (operation == Operation.DATA_TWEEN && (!validDataKey(key) || from < 0 || from > 100000 || value < 0 || value > 100000
                || duration < 1 || duration > 24000 || (long) tick + duration > 24000))
                throw new IllegalArgumentException("Invalid bounded data tween; use the explicit dataTween factory");
            this.tick = tick;
            this.operation = operation;
            this.slot = slot;
            this.stack = copyStack(stack);
            this.key = key == null ? "" : key;
            this.value = value;
            this.from = from;
            this.duration = duration;
        }
        public ItemStack getStack() { return stack.copy(); }
        public int getEndTick() { return operation == Operation.DATA_TWEEN ? tick + duration - 1 : tick; }
        public static Action dataTween(int tick, String key, int from, int to, int duration) {
            if (duration < 0 || duration > 24000) throw new IllegalArgumentException("Invalid data tween duration");
            return new Action(tick, Operation.DATA_TWEEN, -1, ItemStack.EMPTY, key, to, from, Math.max(1, duration));
        }
    }

    public static final class Goal {
        public enum Kind { SLOT_EXACT, SLOT_CONTAINS, SLOT_EMPTY, CURSOR_EMPTY, DATA_EQUALS }
        public final Kind kind;
        public final int slot;
        public final String key;
        public final int value;
        public final String description;
        private final ItemStack stack;
        public Goal(Kind kind, int slot, ItemStack stack, String key, int value, String description) {
            if (kind == null) throw new IllegalArgumentException("Missing goal kind");
            this.kind = kind;
            this.slot = slot;
            this.stack = copyStack(stack);
            this.key = key == null ? "" : key;
            this.value = value;
            this.description = description == null ? "" : description;
        }
        public ItemStack getStack() { return stack.copy(); }
    }

    private final String menuId;
    private final Menu menu;
    private final String title;
    private final Map<Integer, ItemStack> slots;
    private final Map<String, Integer> data;
    private final List<Action> actions;
    private final List<Goal> goals;
    private final int duration;

    private PracticeDefinition(Builder builder) {
        menuId = builder.menuId;
        menu = Menu.fromId(menuId);
        title = builder.title;
        Map<Integer, ItemStack> copy = new LinkedHashMap<Integer, ItemStack>();
        for (Map.Entry<Integer, ItemStack> entry : builder.slots.entrySet()) {
            if (entry.getKey() < 0 || entry.getKey() >= menu.slots + 36)
                throw new IllegalArgumentException("Slot outside virtual inventory: " + entry.getKey());
            copy.put(entry.getKey(), entry.getValue().copy());
        }
        slots = Collections.unmodifiableMap(copy);
        data = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(builder.data));
        List<Action> ordered = new ArrayList<Action>(builder.actions);
        Collections.sort(ordered, (a, b) -> Integer.compare(a.tick, b.tick));
        for (Action action : ordered) {
            if (action.operation != Operation.SET_DATA && action.operation != Operation.DATA_TWEEN && action.operation != Operation.HIDE
                && action.operation != Operation.SHOW && (action.slot < 0 || action.slot >= menu.slots + 36))
                throw new IllegalArgumentException("Action slot outside virtual inventory");
        }
        actions = Collections.unmodifiableList(ordered);
        for (Goal goal : builder.goals) {
            if ((goal.kind == Goal.Kind.SLOT_EXACT || goal.kind == Goal.Kind.SLOT_CONTAINS
                || goal.kind == Goal.Kind.SLOT_EMPTY) && (goal.slot < 0 || goal.slot >= menu.slots + 36))
                throw new IllegalArgumentException("Goal slot outside virtual inventory");
            if ((goal.kind == Goal.Kind.SLOT_EXACT || goal.kind == Goal.Kind.SLOT_CONTAINS) && goal.stack.isEmpty())
                throw new IllegalArgumentException("Item goal requires a nonempty stack");
            if (goal.kind == Goal.Kind.DATA_EQUALS && !validDataKey(goal.key))
                throw new IllegalArgumentException("Unknown virtual data goal");
        }
        goals = Collections.unmodifiableList(new ArrayList<Goal>(builder.goals));
        int last = 0;
        // GUI intervals have an exclusive end, so the final tween sample must remain visible.
        for (Action action : ordered) last = Math.max(last,
            action.getEndTick() + (action.operation == Operation.DATA_TWEEN ? 1 : 0));
        duration = Math.max(builder.duration, last);
    }

    public static Builder builder(String menuId) { return new Builder(menuId); }
    public String getMenuId() { return menuId; }
    public Menu getMenu() { return menu; }
    public String getTitle() { return title; }
    public int getSlotCount() { return menu.slots + 36; }
    public int getDuration() { return duration; }
    public List<Action> getActions() { return actions; }
    public List<Goal> getGoals() { return goals; }
    public Map<String, Integer> getData() { return data; }
    public Map<Integer, ItemStack> getInitialSlots() {
        Map<Integer, ItemStack> copy = new LinkedHashMap<Integer, ItemStack>();
        for (Map.Entry<Integer, ItemStack> entry : slots.entrySet()) copy.put(entry.getKey(), entry.getValue().copy());
        return copy;
    }

    public static final class Builder {
        private final String menuId;
        private String title = "";
        private final Map<Integer, ItemStack> slots = new LinkedHashMap<Integer, ItemStack>();
        private final Map<String, Integer> data = new LinkedHashMap<String, Integer>();
        private final List<Action> actions = new ArrayList<Action>();
        private final List<Goal> goals = new ArrayList<Goal>();
        private int duration = 200;
        public Builder(String menuId) { this.menuId = menuId == null ? "" : menuId; }
        public Builder title(String title) { this.title = title == null ? "" : title; return this; }
        public Builder slot(int slot, ItemStack stack) {
            slots.put(slot, copyStack(stack)); return this;
        }
        public Builder data(String key, int value) { data.put(key, value); return this; }
        public Builder action(Action action) {
            if (actions.size() >= 4096) throw new IllegalArgumentException("Too many interface actions");
            actions.add(action); return this;
        }
        public Builder goal(Goal goal) {
            if (goals.size() >= 128) throw new IllegalArgumentException("Too many interface goals");
            goals.add(goal); return this;
        }
        public Builder duration(int ticks) {
            if (ticks < 0 || ticks > 24000) throw new IllegalArgumentException("Invalid duration");
            duration = ticks; return this;
        }
        Menu menu() { return Menu.fromId(menuId); }
        int duration() { return duration; }
        public PracticeDefinition build() { return new PracticeDefinition(this); }
    }

    private static ItemStack copyStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        if (stack.getCount() > Math.min(64, stack.getMaxStackSize()))
            throw new IllegalArgumentException("Virtual stack exceeds its item limit");
        return stack.copy();
    }

    private static boolean validDataKey(String key) {
        return "burnTime".equals(key) || "fuelTime".equals(key) || "cookTime".equals(key)
            || "cookTotal".equals(key) || "locked".equals(key);
    }
}
