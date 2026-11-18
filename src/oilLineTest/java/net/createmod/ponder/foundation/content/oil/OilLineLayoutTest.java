package net.createmod.ponder.foundation.content.oil;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.createmod.ponder.foundation.content.oil.OilLineLayout.Endpoint;
import net.createmod.ponder.foundation.content.oil.OilLineLayout.Facing;
import net.createmod.ponder.foundation.content.oil.OilLineLayout.Network;
import net.createmod.ponder.foundation.content.oil.OilLineLayout.Point;
import org.junit.jupiter.api.Test;

class OilLineLayoutTest {

    @Test
    void keepsTheAuditedControllersHatchesAndStoragePositions() {
        assertEquals(new Point(1, 1, 5), OilLineLayout.BOILER_CONTROLLER);
        assertEquals(new Point(6, 1, 5), OilLineLayout.PUMP_CONTROLLER);
        assertEquals(new Point(11, 1, 5), OilLineLayout.DISTILLATION_CONTROLLER);
        assertEquals(new Point(16, 1, 5), OilLineLayout.GENERATOR_CONTROLLER);
        assertEquals(new Point(2, 1, 5), OilLineLayout.BOILER_HATCH);
        assertEquals(new Point(7, 1, 5), OilLineLayout.PUMP_HATCH);
        assertEquals(new Point(12, 1, 5), OilLineLayout.DISTILLATION_HATCH);
        assertEquals(new Point(17, 1, 5), OilLineLayout.GENERATOR_HATCH);
        assertEquals(new Point(1, 1, 0), OilLineLayout.WATER_TANK);
        assertEquals(new Point(7, 1, 0), OilLineLayout.OIL_TANK);
        assertEquals(new Point(10, 1, 0), OilLineLayout.LUBRICANT_TANK);
        assertEquals(new Point(14, 1, 0), OilLineLayout.DIESEL_TANK);
        assertEquals(new Point(18, 1, 1), OilLineLayout.CAPACITOR_BANK);
        assertEquals(new Point(0, 1, 3), OilLineLayout.COAL_CHEST);
        assertEquals(new Point(12, 1, 1), OilLineLayout.BITUMEN_CHEST);
    }

    @Test
    void allRunsAreFaceConnectedWithoutOverlapsOrBlockedNodes() {
        assertDoesNotThrow(OilLineLayout::validate);
        Set<Point> allNodes = new HashSet<>();
        for (Network network : OilLineLayout.getNetworks()) {
            assertConnected(network.getNodes());
            for (Point node : network.getNodes()) {
                assertTrue(allNodes.add(node), "Overlapping node " + node);
                assertFalse(OilLineLayout.getReservedBlocks().contains(node), "Blocked node " + node);
            }
        }
        assertEquals(99, OilLineLayout.getReservedBlocks().size());
    }

    @Test
    void endpointsUseTheExposedFrontOrBottomFacesAndPointBackToTheirTargets() {
        for (Endpoint endpoint : OilLineLayout.STEAM_NETWORK.getEndpoints()) {
            assertEquals(Facing.NORTH, endpoint.targetFace);
            assertEquals(Facing.SOUTH, endpoint.nodeFace);
        }
        for (Endpoint endpoint : OilLineLayout.OIL_NETWORK.getEndpoints()) {
            assertEquals(Facing.DOWN, endpoint.targetFace);
            assertEquals(Facing.UP, endpoint.nodeFace);
        }
        assertEquals(Facing.EAST, OilLineLayout.DIESEL_NETWORK.getEndpoints().get(0).targetFace);
        assertEquals(Facing.NORTH, OilLineLayout.DIESEL_NETWORK.getEndpoints().get(1).targetFace);
        for (Endpoint endpoint : OilLineLayout.ENERGY_NETWORK.getEndpoints()) {
            assertEquals(Facing.DOWN, endpoint.targetFace);
            assertEquals(Facing.UP, endpoint.nodeFace);
        }
        for (Network network : OilLineLayout.getNetworks()) {
            for (Endpoint endpoint : network.getEndpoints()) {
                assertTrue(network.getNodes().contains(endpoint.node));
                assertTrue(OilLineLayout.getReservedBlocks().contains(endpoint.target));
                assertEquals(endpoint.target.offset(endpoint.targetFace), endpoint.node);
                assertEquals(endpoint.target, endpoint.node.offset(endpoint.nodeFace));
                assertEquals(1, endpoint.node.manhattanDistance(endpoint.target));
            }
        }
    }

    @Test
    void oilDropsBelowTheSteamRunInsteadOfConnectingAtItsUnderside() {
        assertEquals(11, OilLineLayout.STEAM_NETWORK.getNodes().size());
        assertEquals(8, OilLineLayout.OIL_NETWORK.getNodes().size());
        assertTrue(OilLineLayout.OIL_NETWORK.getNodes().contains(new Point(7, -1, 4)));
        assertFalse(OilLineLayout.OIL_NETWORK.getNodes().contains(new Point(7, 0, 4)));
        assertEquals(2, minimumDistance(OilLineLayout.STEAM_NETWORK, OilLineLayout.OIL_NETWORK));
    }

    @Test
    void distinctFluidNetworksHaveAtLeastOneBlockOfSeparation() {
        List<Network> networks = OilLineLayout.getNetworks();
        for (int i = 0; i < networks.size(); i++) {
            for (int j = i + 1; j < networks.size(); j++) {
                Network left = networks.get(i);
                Network right = networks.get(j);
                if (left.fluidId != null && right.fluidId != null && !left.fluidId.equals(right.fluidId)) {
                    assertTrue(minimumDistance(left, right) >= 2, left.id + " touches " + right.id);
                }
            }
        }
    }

    @Test
    void rejectsTheTemptingUpperOilTrenchThatWouldMergeIntoTheSteamLine() {
        List<Point> upperTrench = new ArrayList<>();
        for (int z = 5; z >= 0; z--) upperTrench.add(new Point(7, 0, z));
        Network unsafeOil = new Network("upper-oil", OilLineRecipeFacts.FLUID_OIL, upperTrench,
            OilLineLayout.OIL_NETWORK.getEndpoints());
        assertThrows(IllegalArgumentException.class, () -> OilLineLayout.validateNetworks(
            Arrays.asList(OilLineLayout.STEAM_NETWORK, unsafeOil)));
    }

    @Test
    void rejectsDisconnectedRunsDuplicateNodesAndMissingEndpointNodes() {
        Network disconnected = run("disconnected", "water", new Point(0, 0, 0), new Point(2, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> OilLineLayout.validateNetworks(Collections.singletonList(disconnected)));
        Network duplicate = run("duplicate", "water", new Point(0, 0, 0), new Point(0, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> OilLineLayout.validateNetworks(Collections.singletonList(duplicate)));
        Network missingEndpoint = new Network("missing", "water", Collections.singletonList(new Point(0, 0, 0)),
            Collections.singletonList(new Endpoint("tank", new Point(5, 0, 0), Facing.DOWN)));
        assertThrows(IllegalArgumentException.class,
            () -> OilLineLayout.validateNetworks(Collections.singletonList(missingEndpoint)));
    }

    @Test
    void rejectsCrossResourceOverlapsEvenWhenEnergyCannotMixFluids() {
        Network fluid = run("fluid", "water", new Point(0, 0, 0));
        Network energy = run("energy", null, new Point(0, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> OilLineLayout.validateNetworks(Arrays.asList(fluid, energy)));
        Network duplicateId = run("fluid", "oil", new Point(10, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> OilLineLayout.validateNetworks(Arrays.asList(fluid, duplicateId)));
    }

    @Test
    void exposesDefensiveImmutableNodeAndEndpointLists() {
        List<Point> nodes = new ArrayList<>(Collections.singletonList(new Point(0, 0, 0)));
        List<Endpoint> endpoints = new ArrayList<>();
        Network network = new Network("copy", "water", nodes, endpoints);
        nodes.clear();
        endpoints.add(new Endpoint("later", new Point(5, 0, 0), Facing.DOWN));
        assertEquals(1, network.getNodes().size());
        assertTrue(network.getEndpoints().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> network.getNodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> network.getEndpoints().clear());
        assertThrows(UnsupportedOperationException.class, () -> OilLineLayout.getNetworks().clear());
        assertThrows(UnsupportedOperationException.class, () -> OilLineLayout.getReservedBlocks().clear());
    }

    private static Network run(String id, String fluid, Point... points) {
        return new Network(id, fluid, Arrays.asList(points), Collections.<Endpoint>emptyList());
    }

    private static long minimumDistance(Network left, Network right) {
        long minimum = Long.MAX_VALUE;
        for (Point a : left.getNodes()) {
            for (Point b : right.getNodes()) minimum = Math.min(minimum, a.manhattanDistance(b));
        }
        return minimum;
    }

    private static void assertConnected(List<Point> nodes) {
        Set<Point> remaining = new HashSet<>(nodes);
        Deque<Point> pending = new ArrayDeque<>();
        Point first = nodes.get(0);
        remaining.remove(first);
        pending.add(first);
        while (!pending.isEmpty()) {
            Point current = pending.removeFirst();
            for (Facing facing : Facing.values()) {
                Point next = current.offset(facing);
                if (remaining.remove(next)) pending.addLast(next);
            }
        }
        assertTrue(remaining.isEmpty());
    }
}
