package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.createmod.ponder.foundation.PonderScene.OverlayEvent;
import net.minecraft.util.ResourceLocation;

class PreviewGuiPageLayoutCycleTest {

    @Test
    void descendantBeforeACycleRemainsOnItsParentsPage() throws Exception {
        OverlayEvent child = texture("child", 20, 10).parentOverlayId("a");
        OverlayEvent a = texture("a", 100, 100).parentOverlayId("b");
        OverlayEvent b = texture("b", 20, 20).parentOverlayId("a");

        List<List<OverlayEvent>> pages = PreviewGuiPageLayout.pages(Arrays.asList(child, a, b));

        assertEquals(1, pages.size());
        assertSame(a, pages.get(0).get(0));
        assertEquals(3, pages.get(0).size());
        assertParentFirst(pages.get(0));
        assertEquals("b", a.getParentOverlayId());
    }

    @Test
    void everyRegistrationOrderKeepsCycleDescendantsTogetherAndParentFirst() throws Exception {
        OverlayEvent child = texture("child", 20, 10).parentOverlayId("a");
        OverlayEvent a = texture("a", 100, 100).parentOverlayId("b");
        OverlayEvent b = texture("b", 20, 20).parentOverlayId("a");
        OverlayEvent grandchild = texture("grandchild", 5, 5).parentOverlayId("child");
        List<OverlayEvent> events = Arrays.asList(child, a, b, grandchild);

        for (int first = 0; first < events.size(); first++) {
            for (int second = 0; second < events.size(); second++) {
                for (int third = 0; third < events.size(); third++) {
                    if (first == second || first == third || second == third) {
                        continue;
                    }
                    int fourth = 6 - first - second - third;
                    List<List<OverlayEvent>> pages = PreviewGuiPageLayout.pages(Arrays.asList(
                        events.get(first), events.get(second), events.get(third), events.get(fourth)));

                    assertEquals(1, pages.size());
                    List<OverlayEvent> page = pages.get(0);
                    assertEquals(4, page.size());
                    assertTrue(page.get(0) == a || page.get(0) == b);
                    assertParentFirst(page);
                }
            }
        }
    }

    @Test
    void cycleDescendantSharesTheParentTransformAfterAnOversizedGroupIsFitted() throws Exception {
        OverlayEvent child = texture("child", 20, 10).parentOverlayId("a")
            .guiOrigin(50, 25).scaleToParent(true);
        OverlayEvent a = texture("a", 100, 100).parentOverlayId("b");
        OverlayEvent b = texture("b", 20, 20).parentOverlayId("a")
            .guiOrigin(10, 20).scaleToParent(true);
        PreviewDebugLayout.Rect area = new PreviewDebugLayout.Rect(10, 20, 50, 50);
        List<List<OverlayEvent>> pages = PreviewGuiPageLayout.pages(Arrays.asList(child, a, b));

        assertEquals(1, pages.size());
        List<PreviewGuiPageLayout.Placement> placements = PreviewGuiPageLayout.layout(pages.get(0), area);
        PreviewDebugLayout.Rect parent = placement(placements, a).draw;
        PreviewDebugLayout.Rect nested = placement(placements, child).draw;
        assertEquals(50, parent.width);
        assertEquals(50, parent.height);
        assertEquals(parent.x + parent.width * 0.5D, nested.x, 1.0D);
        assertEquals(parent.y + parent.height * 0.25D, nested.y, 1.0D);
        assertEquals(parent.width * 0.2D, nested.width, 1.0D);
        assertEquals(parent.height * 0.1D, nested.height, 1.0D);
        for (PreviewGuiPageLayout.Placement positioned : placements) {
            assertTrue(positioned.panel.x >= area.x && positioned.panel.right() <= area.right());
            assertTrue(positioned.panel.y >= area.y && positioned.panel.bottom() <= area.bottom());
        }
    }

    @Test
    void selfParentKeepsItsPreviouslyRegisteredDescendants() throws Exception {
        OverlayEvent root = texture("root", 100, 100).parentOverlayId("root");
        OverlayEvent child = texture("child", 20, 20).parentOverlayId("root");
        OverlayEvent grandchild = texture("grandchild", 5, 5).parentOverlayId("child");

        List<List<OverlayEvent>> pages = PreviewGuiPageLayout.pages(Arrays.asList(grandchild, child, root));

        assertEquals(1, pages.size());
        assertEquals(Arrays.asList(root, child, grandchild), pages.get(0));
        assertParentFirst(pages.get(0));
    }

    @Test
    void separateCyclesNormalRootsAndMissingParentsDoNotMergeOrLoseEvents() throws Exception {
        OverlayEvent a = texture("a", 20, 20).parentOverlayId("b");
        OverlayEvent b = texture("b", 20, 20).parentOverlayId("a");
        OverlayEvent aChild = texture("a_child", 5, 5).parentOverlayId("a");
        OverlayEvent x = texture("x", 20, 20).parentOverlayId("y");
        OverlayEvent y = texture("y", 20, 20).parentOverlayId("x");
        OverlayEvent xChild = texture("x_child", 5, 5).parentOverlayId("x");
        OverlayEvent normal = texture("normal", 20, 20);
        OverlayEvent normalChild = texture("normal_child", 5, 5).parentOverlayId("normal");
        OverlayEvent orphan = texture("orphan", 20, 20).parentOverlayId("missing");
        OverlayEvent orphanChild = texture("orphan_child", 5, 5).parentOverlayId("orphan");
        List<OverlayEvent> events = Arrays.asList(aChild, xChild, normalChild, orphanChild,
            a, x, normal, orphan, b, y);

        List<List<OverlayEvent>> pages = PreviewGuiPageLayout.pages(events);

        assertEquals(4, pages.size());
        Map<OverlayEvent, List<OverlayEvent>> assigned = new IdentityHashMap<>();
        for (List<OverlayEvent> page : pages) {
            assertParentFirst(page);
            for (OverlayEvent event : page) {
                assertTrue(assigned.put(event, page) == null);
            }
        }
        assertEquals(events.size(), assigned.size());
        assertSame(assigned.get(a), assigned.get(b));
        assertSame(assigned.get(a), assigned.get(aChild));
        assertSame(assigned.get(x), assigned.get(y));
        assertSame(assigned.get(x), assigned.get(xChild));
        assertSame(assigned.get(normal), assigned.get(normalChild));
        assertSame(assigned.get(orphan), assigned.get(orphanChild));
    }

    @Test
    void longReversedParentChainIsResolvedWithoutRecursion() throws Exception {
        int count = 16384;
        List<OverlayEvent> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(texture("node_" + i, 16, 16)
                .parentOverlayId(i == 0 ? null : "node_" + (i - 1)));
        }
        Collections.reverse(events);

        List<List<OverlayEvent>> pages = PreviewGuiPageLayout.pages(events);

        assertEquals(1, pages.size());
        assertEquals(count, pages.get(0).size());
        for (int i = 0; i < count; i++) {
            assertEquals("node_" + i, pages.get(0).get(i).getOverlayId());
        }
        assertParentFirst(pages.get(0));
    }

    @Test
    void repeatedReferencesToOneOverlayDoNotDuplicateItsDescendantTree() throws Exception {
        OverlayEvent root = texture("root", 100, 100);
        OverlayEvent child = texture("child", 20, 20).parentOverlayId("root");

        List<List<OverlayEvent>> pages = PreviewGuiPageLayout.pages(Arrays.asList(child, root, child, root));

        assertEquals(1, pages.size());
        assertEquals(Arrays.asList(root, child), pages.get(0));
    }

    private static void assertParentFirst(List<OverlayEvent> page) {
        Map<String, OverlayEvent> byId = new HashMap<>();
        Map<OverlayEvent, Integer> positions = new IdentityHashMap<>();
        for (int i = 0; i < page.size(); i++) {
            byId.put(page.get(i).getOverlayId(), page.get(i));
            positions.put(page.get(i), i);
        }
        for (int i = 1; i < page.size(); i++) {
            OverlayEvent parent = byId.get(page.get(i).getParentOverlayId());
            assertTrue(parent != null && positions.get(parent) < i);
        }
    }

    private static PreviewGuiPageLayout.Placement placement(List<PreviewGuiPageLayout.Placement> placements,
        OverlayEvent event) {
        return placements.stream().filter(placement -> placement.event == event).findFirst()
            .orElseThrow(() -> new AssertionError("Missing overlay placement"));
    }

    private static OverlayEvent texture(String id, int width, int height) throws Exception {
        Constructor<OverlayEvent> constructor = OverlayEvent.class.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(0, 200).overlayId(id)
            .guiTexture(new ResourceLocation("test", id), 0, 0, width, height, width, height,
                width, height, 0, 0, false);
    }
}
