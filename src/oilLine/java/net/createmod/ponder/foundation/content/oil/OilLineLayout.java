package net.createmod.ponder.foundation.content.oil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Coordinate-only layout. Water and the tower's oil/byproducts are handled through its GUI. */
public final class OilLineLayout {

    public static final Point BOILER_CONTROLLER = new Point(1, 1, 5);
    public static final Point PUMP_CONTROLLER = new Point(6, 1, 5);
    public static final Point DISTILLATION_CONTROLLER = new Point(11, 1, 5);
    public static final Point GENERATOR_CONTROLLER = new Point(16, 1, 5);
    public static final Point BOILER_HATCH = BOILER_CONTROLLER.offset(Facing.EAST);
    public static final Point PUMP_HATCH = PUMP_CONTROLLER.offset(Facing.EAST);
    public static final Point DISTILLATION_HATCH = DISTILLATION_CONTROLLER.offset(Facing.EAST);
    public static final Point GENERATOR_HATCH = GENERATOR_CONTROLLER.offset(Facing.EAST);

    public static final Point WATER_TANK = new Point(1, 1, 0);
    public static final Point OIL_TANK = new Point(7, 1, 0);
    public static final Point LUBRICANT_TANK = new Point(10, 1, 0);
    public static final Point DIESEL_TANK = new Point(14, 1, 0);
    public static final Point CAPACITOR_BANK = new Point(18, 1, 1);
    public static final Point COAL_CHEST = new Point(0, 1, 3);
    public static final Point BITUMEN_CHEST = new Point(12, 1, 1);

    public static final Network STEAM_NETWORK = new Network("steam", OilLineRecipeFacts.FLUID_STEAM,
        line(new Point(2, 1, 4), new Point(12, 1, 4)), Arrays.asList(
            new Endpoint("boiler-steam", BOILER_HATCH, Facing.NORTH),
            new Endpoint("pump-steam", PUMP_HATCH, Facing.NORTH),
            new Endpoint("distillation-steam", DISTILLATION_HATCH, Facing.NORTH)));
    public static final Network OIL_NETWORK = new Network("oil", OilLineRecipeFacts.FLUID_OIL,
        oilNodes(), Arrays.asList(new Endpoint("pump-oil", PUMP_HATCH, Facing.DOWN),
            new Endpoint("oil-buffer", OIL_TANK, Facing.DOWN)));
    public static final Network DIESEL_NETWORK = new Network("diesel", OilLineRecipeFacts.FLUID_DIESEL,
        joined(line(new Point(15, 1, 0), new Point(17, 1, 0)),
            line(new Point(17, 1, 0), new Point(17, 1, 4))), Arrays.asList(
                new Endpoint("diesel-buffer", DIESEL_TANK, Facing.EAST),
                new Endpoint("generator-diesel", GENERATOR_HATCH, Facing.NORTH)));
    public static final Network ENERGY_NETWORK = new Network("energy", null,
        joined(line(new Point(17, 0, 5), new Point(18, 0, 5)),
            line(new Point(18, 0, 5), new Point(18, 0, 1))), Arrays.asList(
                new Endpoint("generator-energy", GENERATOR_HATCH, Facing.DOWN),
                new Endpoint("energy-buffer", CAPACITOR_BANK, Facing.DOWN)));

    private static final List<Network> NETWORKS = Collections.unmodifiableList(Arrays.asList(
        STEAM_NETWORK, OIL_NETWORK, DIESEL_NETWORK, ENERGY_NETWORK));
    private static final Set<Point> RESERVED_BLOCKS = reservedBlocks();

    static {
        validate();
    }

    private OilLineLayout() {}

    public static List<Network> getNetworks() {
        return NETWORKS;
    }

    public static Set<Point> getReservedBlocks() {
        return RESERVED_BLOCKS;
    }

    public static void validate() {
        validateNetworks(NETWORKS);
        for (Network network : NETWORKS) {
            for (Point node : network.nodes) {
                if (RESERVED_BLOCKS.contains(node)) {
                    throw new IllegalArgumentException("Conduit crosses a machine or storage block: " + node);
                }
            }
        }
    }

    /** Rejects disconnected runs, overlaps, and face-adjacent networks carrying different fluids. */
    public static void validateNetworks(List<Network> networks) {
        Objects.requireNonNull(networks, "networks");
        Set<String> ids = new HashSet<>();
        for (Network network : networks) {
            Objects.requireNonNull(network, "network");
            if (!ids.add(network.id)) {
                throw new IllegalArgumentException("Duplicate conduit network: " + network.id);
            }
            validateConnected(network);
        }
        for (int i = 0; i < networks.size(); i++) {
            Network left = networks.get(i);
            for (int j = i + 1; j < networks.size(); j++) {
                Network right = networks.get(j);
                boolean differentFluids = left.fluidId != null && right.fluidId != null
                    && !left.fluidId.equals(right.fluidId);
                for (Point a : left.nodes) {
                    for (Point b : right.nodes) {
                        long distance = a.manhattanDistance(b);
                        if (distance == 0 || differentFluids && distance == 1) {
                            throw new IllegalArgumentException("Conduit networks touch: "
                                + left.id + " / " + right.id + " at " + a + " / " + b);
                        }
                    }
                }
            }
        }
    }

    private static void validateConnected(Network network) {
        Set<Point> remaining = new HashSet<>(network.nodes);
        if (remaining.isEmpty() || remaining.size() != network.nodes.size()) {
            throw new IllegalArgumentException("Conduit network is empty or has duplicate nodes: " + network.id);
        }
        Deque<Point> pending = new ArrayDeque<>();
        Point first = network.nodes.get(0);
        remaining.remove(first);
        pending.add(first);
        while (!pending.isEmpty()) {
            Point node = pending.removeFirst();
            for (Facing face : Facing.values()) {
                Point next = node.offset(face);
                if (remaining.remove(next)) {
                    pending.addLast(next);
                }
            }
        }
        if (!remaining.isEmpty()) {
            throw new IllegalArgumentException("Disconnected conduit network: " + network.id);
        }
        for (Endpoint endpoint : network.endpoints) {
            if (!network.nodes.contains(endpoint.node) || network.nodes.contains(endpoint.target)) {
                throw new IllegalArgumentException("Invalid conduit endpoint: " + endpoint.id);
            }
        }
    }

    private static List<Point> oilNodes() {
        List<Point> nodes = new ArrayList<>();
        nodes.add(new Point(7, 0, 5));
        nodes.addAll(line(new Point(7, -1, 5), new Point(7, -1, 0)));
        nodes.add(new Point(7, 0, 0));
        return nodes;
    }

    private static List<Point> line(Point from, Point to) {
        int changed = (from.x == to.x ? 0 : 1) + (from.y == to.y ? 0 : 1) + (from.z == to.z ? 0 : 1);
        if (changed > 1) {
            throw new IllegalArgumentException("Conduit segment must follow one axis");
        }
        int dx = Integer.compare(to.x, from.x);
        int dy = Integer.compare(to.y, from.y);
        int dz = Integer.compare(to.z, from.z);
        List<Point> nodes = new ArrayList<>();
        Point node = from;
        nodes.add(node);
        while (!node.equals(to)) {
            node = new Point(node.x + dx, node.y + dy, node.z + dz);
            nodes.add(node);
        }
        return nodes;
    }

    private static List<Point> joined(List<Point> first, List<Point> second) {
        Set<Point> nodes = new LinkedHashSet<>(first);
        nodes.addAll(second);
        return new ArrayList<>(nodes);
    }

    private static Set<Point> reservedBlocks() {
        Set<Point> blocks = new HashSet<>();
        addMachine(blocks, BOILER_CONTROLLER, 2, 3, 2);
        addMachine(blocks, PUMP_CONTROLLER, 3, 2, 2);
        addMachine(blocks, DISTILLATION_CONTROLLER, 3, 5, 3);
        blocks.remove(new Point(12, 2, 6));
        blocks.remove(new Point(12, 3, 6));
        blocks.remove(new Point(12, 4, 6));
        addMachine(blocks, GENERATOR_CONTROLLER, 3, 3, 3);
        blocks.remove(new Point(17, 2, 6));
        Collections.addAll(blocks, WATER_TANK, OIL_TANK, LUBRICANT_TANK, DIESEL_TANK,
            CAPACITOR_BANK, COAL_CHEST, BITUMEN_CHEST);
        return Collections.unmodifiableSet(blocks);
    }

    private static void addMachine(Set<Point> blocks, Point origin, int sizeX, int sizeY, int sizeZ) {
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    blocks.add(new Point(origin.x + x, origin.y + y, origin.z + z));
                }
            }
        }
    }

    public enum Facing {
        DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);

        public final int dx;
        public final int dy;
        public final int dz;

        Facing(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        public Facing opposite() {
            return values()[ordinal() ^ 1];
        }
    }

    public static final class Point {
        public final int x;
        public final int y;
        public final int z;

        public Point(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Point offset(Facing face) {
            return new Point(Math.addExact(x, face.dx), Math.addExact(y, face.dy), Math.addExact(z, face.dz));
        }

        public long manhattanDistance(Point other) {
            return Math.abs((long) x - other.x) + Math.abs((long) y - other.y) + Math.abs((long) z - other.z);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof Point)) return false;
            Point other = (Point) object;
            return x == other.x && y == other.y && z == other.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + "," + z + ")";
        }
    }

    public static final class Endpoint {
        public final String id;
        public final Point target;
        public final Facing targetFace;
        public final Point node;
        public final Facing nodeFace;

        public Endpoint(String id, Point target, Facing targetFace) {
            this.id = Objects.requireNonNull(id, "id");
            this.target = Objects.requireNonNull(target, "target");
            this.targetFace = Objects.requireNonNull(targetFace, "targetFace");
            this.node = target.offset(targetFace);
            this.nodeFace = targetFace.opposite();
        }
    }

    public static final class Network {
        public final String id;
        public final String fluidId;
        private final List<Point> nodes;
        private final List<Endpoint> endpoints;

        /** A null fluid ID denotes the energy-only network. */
        public Network(String id, String fluidId, List<Point> nodes, List<Endpoint> endpoints) {
            this.id = Objects.requireNonNull(id, "id");
            this.fluidId = fluidId;
            this.nodes = immutableCopy(nodes);
            this.endpoints = immutableCopy(endpoints);
        }

        public List<Point> getNodes() {
            return nodes;
        }

        public List<Endpoint> getEndpoints() {
            return endpoints;
        }

        private static <T> List<T> immutableCopy(List<T> values) {
            List<T> copy = new ArrayList<>(Objects.requireNonNull(values, "values"));
            for (T value : copy) Objects.requireNonNull(value, "value");
            return Collections.unmodifiableList(copy);
        }
    }
}
