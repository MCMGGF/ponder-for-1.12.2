package net.createmod.ponder.foundation.ui;

import static net.createmod.ponder.foundation.ui.GuiDemoDefinition.fields;
import static net.createmod.ponder.foundation.ui.GuiDemoDefinition.integer;
import static net.createmod.ponder.foundation.ui.GuiDemoDefinition.object;
import static net.createmod.ponder.foundation.ui.GuiDemoDefinition.string;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.createmod.ponder.foundation.ui.BlockGuiDemoDefinition.Insets;
import net.createmod.ponder.foundation.ui.BlockGuiDemoDefinition.Stack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/** Inputs and observable conditions for an isolated, real client/server GUI recording. */
public record SandboxGuiScript(Map<Integer, Stack> inventory, List<Neighbor> neighbors, List<Action> actions) {
    public SandboxGuiScript {
        inventory = Map.copyOf(inventory);
        neighbors = List.copyOf(neighbors);
        actions = List.copyOf(actions);
    }

    public record Neighbor(int x, int y, int z, ResourceLocation block, int meta, NBTTagCompound tileNbt) {
        @Override
        public NBTTagCompound tileNbt() {
            return tileNbt == null ? null : tileNbt.copy();
        }
    }

    public record Action(int tick, String type, Integer slot, int x, int y, int button, int timeout,
                         Stack stack, String screen, NBTTagCompound nbt) {
        @Override
        public NBTTagCompound nbt() {
            return nbt == null ? null : nbt.copy();
        }
    }

    static SandboxGuiScript parse(JsonObject data, Insets capture) {
        fields(data, Set.of("inventory", "neighbors", "actions"));
        Map<Integer, Stack> inventory = new HashMap<>();
        if (data.has("inventory")) {
            for (var entry : object(data.get("inventory"), "sandbox.inventory").entrySet()) {
                String key = entry.getKey();
                if (!key.matches("0|[1-9]|[12][0-9]|3[0-5]") || entry.getValue().isJsonNull()) {
                    throw new IllegalArgumentException("sandbox.inventory expects slots 0..35 with item stacks");
                }
                inventory.put(Integer.parseInt(key), BlockGuiDemoDefinition.readStack(entry.getValue()));
            }
        }
        JsonElement sequence = data.get("actions");
        if (sequence == null || !sequence.isJsonArray() || sequence.getAsJsonArray().size() < 1
            || sequence.getAsJsonArray().size() > 2048) {
            throw new IllegalArgumentException("sandbox.actions must contain 1..2048 actions");
        }
        List<Action> actions = new ArrayList<>();
        for (JsonElement element : sequence.getAsJsonArray()) {
            try {
                Action action = parseAction(object(element, "action"), capture);
                if (!actions.isEmpty() && action.tick() < actions.getLast().tick()) {
                    throw new IllegalArgumentException("tick must be in chronological order");
                }
                actions.add(action);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("sandbox.actions[" + actions.size() + "]: " + invalid.getMessage(), invalid);
            }
        }
        return new SandboxGuiScript(inventory, neighbors(data), actions);
    }

    private static List<Neighbor> neighbors(JsonObject data) {
        if (!data.has("neighbors")) {
            return List.of();
        }
        JsonElement list = data.get("neighbors");
        if (!list.isJsonArray() || list.getAsJsonArray().size() > 6) {
            throw new IllegalArgumentException("sandbox.neighbors must contain at most six adjacent blocks");
        }
        List<Neighbor> result = new ArrayList<>();
        Set<BlockPos> positions = new HashSet<>();
        for (JsonElement element : list.getAsJsonArray()) {
            JsonObject entry = object(element, "sandbox.neighbors entry");
            fields(entry, Set.of("x", "y", "z", "block", "meta", "tileNbt"));
            int x = integer(entry, "x", 0, -1, 1);
            int y = integer(entry, "y", 0, -1, 1);
            int z = integer(entry, "z", 0, -1, 1);
            if (Math.abs(x) + Math.abs(y) + Math.abs(z) != 1 || !positions.add(new BlockPos(x, y, z))) {
                throw new IllegalArgumentException("sandbox.neighbors requires distinct face-adjacent offsets");
            }
            result.add(new Neighbor(x, y, z, BlockGuiDemoDefinition.requiredResource(entry, "block"),
                integer(entry, "meta", 0, 0, 15), BlockGuiDemoDefinition.nbt(entry, "tileNbt")));
        }
        return result;
    }

    private static Action parseAction(JsonObject data, Insets capture) {
        String type = string(data, "type", "");
        Set<String> allowed = switch (type) {
            case "activate", "close" -> Set.of("type", "tick");
            case "click" -> Set.of("type", "tick", "slot", "x", "y", "button");
            case "wait_screen" -> Set.of("type", "tick", "screen", "timeout");
            case "wait_slot" -> Set.of("type", "tick", "slot", "stack", "timeout");
            case "wait_nbt" -> Set.of("type", "tick", "nbt", "timeout");
            default -> throw new IllegalArgumentException("Unknown sandbox action type: " + type);
        };
        fields(data, allowed);
        int tick = integer(data, "tick", -1, 0, 23_999);
        Integer slot = data.has("slot") ? integer(data, "slot", -1, 0, 4095) : null;
        int x = 0;
        int y = 0;
        String screen = null;
        Stack stack = null;
        NBTTagCompound nbt = null;
        switch (type) {
            case "click" -> {
                if (slot == null) {
                    x = integer(data, "x", Integer.MIN_VALUE, -capture.left(), 4095);
                    y = integer(data, "y", Integer.MIN_VALUE, -capture.top(), 4095);
                } else if (data.has("x") || data.has("y")) {
                    throw new IllegalArgumentException("Choose a slot or a pair of GUI coordinates");
                }
            }
            case "wait_screen" -> {
                screen = string(data, "screen", "");
                if (!screen.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) {
                    throw new IllegalArgumentException("screen must be a simple or fully qualified GUI class name");
                }
            }
            case "wait_slot" -> {
                if (slot == null || !data.has("stack")) {
                    throw new IllegalArgumentException("wait_slot requires slot and stack (null for empty)");
                }
                stack = BlockGuiDemoDefinition.readStack(data.get("stack"));
            }
            case "wait_nbt" -> {
                nbt = BlockGuiDemoDefinition.nbt(data, "nbt");
                if (nbt == null || nbt.isEmpty()) {
                    throw new IllegalArgumentException("wait_nbt requires a nonempty SNBT compound");
                }
            }
        }
        return new Action(tick, type, slot, x, y, integer(data, "button", 0, 0, 2),
            integer(data, "timeout", 100, 1, 2400), stack, screen, nbt);
    }
}
