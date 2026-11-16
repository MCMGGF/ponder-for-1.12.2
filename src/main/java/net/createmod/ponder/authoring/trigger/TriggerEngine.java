package net.createmod.ponder.authoring.trigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/** Bounded hint decisions over an injected read-only view, independent of Minecraft UI. */
public final class TriggerEngine {
    public static final int CHECK_INTERVAL = 20;
    public static final int MAX_RULE_CHECKS = 8;
    public static final int MAX_BLOCK_READS = 512;
    public interface WorldView {
        int dimension();
        BlockPos playerPosition();
        boolean isLoaded(BlockPos position);
        IBlockState blockState(BlockPos position);
    }
    public static final class Hint {
        private final TriggerDefinition definition;
        Hint(TriggerDefinition definition) { this.definition = definition; }
        public String getDocumentId() { return definition.getDocumentId(); }
        public ResourceLocation getComponentId() { return definition.getComponentId(); }
        public ResourceLocation getRuntimeSceneId() { return definition.getRuntimeSceneId(); }
        public int getSegmentIndex() { return definition.getSegmentIndex(); }
        public String getText() { return definition.getText(); }
    }
    private List<TriggerDefinition> rules = new ArrayList<TriggerDefinition>();
    private final Set<String> shown = new HashSet<String>(), read = new HashSet<String>();
    private final Map<String, Long> lastShown = new HashMap<String, Long>();
    private TriggerDefinition active, actionable;
    private int cursor, lastRuleChecks, lastBlockReads;
    private long lastPoll = Long.MIN_VALUE, evaluations, hintCount, openedCount;

    public void replaceRules(List<TriggerDefinition> definitions) {
        if (definitions.size() > TriggerDefinition.MAX_RULES) throw new IllegalArgumentException("Too many trigger rules");
        rules = new ArrayList<TriggerDefinition>(definitions); cursor = 0; active = actionable = null; lastPoll = Long.MIN_VALUE;
        Set<String> ids = new HashSet<String>();
        for (TriggerDefinition rule : rules) ids.add(key(rule));
        shown.retainAll(ids); read.retainAll(ids); lastShown.keySet().retainAll(ids);
    }

    public Hint poll(WorldView world, long tick) {
        if (world == null) { clearSession(); return null; }
        if (lastPoll != Long.MIN_VALUE && tick >= lastPoll && tick - lastPoll < CHECK_INTERVAL) return null;
        lastPoll = tick; evaluations++; lastRuleChecks = 0; lastBlockReads = 0;
        if (active != null && !nearby(active, world)) active = null;
        if (actionable != null && (!canOpen(actionable) || !nearby(actionable, world))) actionable = null;
        Set<TriggerDefinition> checked = new HashSet<TriggerDefinition>();
        // Only a usable shortcut gets a priority read; consumed raw diagnostics cannot starve the fair cursor.
        if (actionable != null) {
            TriggerDefinition previous = actionable;
            boolean match = matches(previous, world);
            checked.add(previous);
            if (!match) { actionable = null; if (active == previous) active = null; }
        }
        int visited = 0;
        while (!rules.isEmpty() && visited < rules.size() && lastRuleChecks < MAX_RULE_CHECKS) {
            TriggerDefinition candidate = rules.get(cursor);
            if (checked.contains(candidate)) { cursor = (cursor + 1) % rules.size(); visited++; continue; }
            int cost = nearby(candidate, world) ? candidate.getBlocks().size() : 0;
            if (lastBlockReads + cost > MAX_BLOCK_READS) break;
            cursor = (cursor + 1) % rules.size(); visited++;
            boolean match = matches(candidate, world);
            checked.add(candidate);
            if (match) {
                if (active == null) active = candidate;
                if (actionable == null && canOpen(candidate)) actionable = candidate;
            } else {
                if (active == candidate) active = null;
                if (actionable == candidate) actionable = null;
            }
        }
        if (actionable == null) return null;
        String key = key(actionable);
        if (actionable.getFrequency() == TriggerDefinition.Frequency.FIRST_TIME && shown.contains(key)) return null;
        Long last = lastShown.get(key);
        if (last != null && tick >= last.longValue() && tick - last.longValue() < actionable.getCooldown()) return null;
        lastShown.put(key, tick); shown.add(key); hintCount++;
        return new Hint(actionable);
    }

    private boolean matches(TriggerDefinition rule, WorldView world) {
        lastRuleChecks++;
        if (!nearby(rule, world)) return false;
        List<TriggerDefinition.ExpectedBlock> pattern = rule.getBlocks();
        if (lastBlockReads + pattern.size() > MAX_BLOCK_READS) return false;
        // Complete loadedness preflight before reading any target block state.
        for (TriggerDefinition.ExpectedBlock expected : pattern) if (!world.isLoaded(expected.position)) return false;
        for (TriggerDefinition.ExpectedBlock expected : pattern) {
            lastBlockReads++;
            if (!expected.state.equals(world.blockState(expected.position))) return false;
        }
        return true;
    }
    private boolean nearby(TriggerDefinition rule, WorldView world) {
        BlockPos player = world.playerPosition();
        if (player == null || world.dimension() != rule.getDimension()) return false;
        double x = distance(player.getX(), rule.getMin().getX(), rule.getMax().getX());
        double y = distance(player.getY(), rule.getMin().getY(), rule.getMax().getY());
        double z = distance(player.getZ(), rule.getMin().getZ(), rule.getMax().getZ());
        return x * x + y * y + z * z <= (double) rule.getRadius() * rule.getRadius();
    }
    private static double distance(int value, int min, int max) {
        return value < min ? (double) min - value : value > max ? (double) value - max : 0;
    }
    private static String key(TriggerDefinition rule) { return rule.getRuntimeSceneId().toString(); }
    public Hint getActiveHint() { return active == null ? null : new Hint(active); }
    public Hint getActionableHint() {
        return actionable == null || !canOpen(actionable) ? null : new Hint(actionable);
    }
    public boolean markOpened(ResourceLocation sceneId) {
        if (actionable == null || !canOpen(actionable) || !actionable.getRuntimeSceneId().equals(sceneId)) return false;
        if (read.add(key(actionable))) openedCount++;
        return true;
    }
    private boolean canOpen(TriggerDefinition rule) {
        return rule.getFrequency() == TriggerDefinition.Frequency.ALWAYS || !read.contains(key(rule));
    }
    public void clearSession() {
        active = actionable = null; cursor = 0; shown.clear(); read.clear(); lastShown.clear();
        lastPoll = Long.MIN_VALUE; lastRuleChecks = 0; lastBlockReads = 0;
        evaluations = 0; hintCount = 0; openedCount = 0;
    }
    public int getLastRuleChecks() { return lastRuleChecks; }
    public int getLastBlockReads() { return lastBlockReads; }
    public long getEvaluations() { return evaluations; }
    public long getHintCount() { return hintCount; }
    public long getOpenedCount() { return openedCount; }
}
