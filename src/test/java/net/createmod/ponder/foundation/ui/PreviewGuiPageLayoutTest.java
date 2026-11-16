package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.createmod.ponder.foundation.PonderScene.OverlayEvent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PreviewGuiPageLayoutTest {

    @BeforeAll
    static void registerBlocks() {
        Bootstrap.register();
    }

    @Test
    void separateRootsAndOrphanChildrenAllRemainReachableRegardlessOfRegistrationOrder() throws Exception {
        OverlayEvent root = texture("root", 200, 100);
        OverlayEvent child = texture("child", 20, 20).parentOverlayId("root");
        OverlayEvent orphan = texture("orphan", 30, 30).parentOverlayId("missing");
        OverlayEvent second = texture("second", 90, 70);
        List<List<OverlayEvent>> pages = PreviewGuiPageLayout.pages(Arrays.asList(child, root, orphan, second));
        assertEquals(3, pages.size());
        assertEquals(Arrays.asList(root, child), pages.get(0));
        assertEquals(Collections.singletonList(orphan), pages.get(1));
        assertEquals(Collections.singletonList(second), pages.get(2));
    }

    @Test
    void cyclicParentsDoNotHangOrLoseAnyOverlay() throws Exception {
        OverlayEvent left = texture("left", 30, 30).parentOverlayId("right");
        OverlayEvent right = texture("right", 30, 30).parentOverlayId("left");
        List<List<OverlayEvent>> pages = PreviewGuiPageLayout.pages(Arrays.asList(left, right));
        assertEquals(1, pages.size());
        assertEquals(Arrays.asList(left, right), pages.get(0));
    }

    @Test
    void parentAndChildUseTheSameTransformAndStayInsideCalloutAbovePaginationFooter() throws Exception {
        PreviewExplanationLayout layout = PreviewExplanationLayout.showcase(320, 240);
        PreviewDebugLayout.Rect area = new PreviewDebugLayout.Rect(layout.explanation.x + 6,
            layout.explanation.y + 6, layout.explanation.width - 12, layout.explanation.height - 26);
        OverlayEvent root = texture("root", 200, 100);
        OverlayEvent child = texture("child", 40, 20).parentOverlayId("root").guiOrigin(100, 50).scaleToParent(true);
        List<PreviewGuiPageLayout.Placement> placements =
            PreviewGuiPageLayout.layout(Arrays.asList(root, child), area);
        assertEquals(2, placements.size());
        PreviewDebugLayout.Rect parent = placements.get(0).draw;
        PreviewDebugLayout.Rect nested = placements.get(1).draw;
        assertEquals(parent.x + parent.width / 2.0, nested.x, 1.0);
        assertEquals(parent.y + parent.height / 2.0, nested.y, 1.0);
        assertEquals(parent.width * 0.2, nested.width, 1.0);
        for (PreviewGuiPageLayout.Placement placement : placements) {
            assertTrue(placement.panel.x >= area.x);
            assertTrue(placement.panel.y >= area.y);
            assertTrue(placement.panel.right() <= area.right());
            assertTrue(placement.panel.bottom() <= area.bottom());
            assertTrue(placement.panel.x >= layout.explanation.x);
            assertTrue(placement.panel.bottom() < layout.explanation.bottom() - 14);
        }
    }

    @Test
    void childrenOutsideTheParentExpandTheSharedFitInsteadOfBeingIndependentlyClamped() throws Exception {
        OverlayEvent root = texture("root", 100, 100);
        OverlayEvent child = texture("child", 80, 80).parentOverlayId("root").guiOrigin(-40, 120);
        PreviewDebugLayout.Rect area = new PreviewDebugLayout.Rect(180, 82, 112, 48);
        List<PreviewGuiPageLayout.Placement> placements =
            PreviewGuiPageLayout.layout(Arrays.asList(root, child), area);
        assertTrue(placements.get(1).draw.x < placements.get(0).draw.x);
        assertTrue(placements.get(1).draw.y > placements.get(0).draw.bottom());
        for (PreviewGuiPageLayout.Placement placement : placements) {
            assertTrue(placement.panel.x >= area.x && placement.panel.right() <= area.right());
            assertTrue(placement.panel.y >= area.y && placement.panel.bottom() <= area.bottom());
        }
    }

    static OverlayEvent texture(String id, int width, int height) throws Exception {
        Constructor<OverlayEvent> constructor = OverlayEvent.class.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(0, 200).overlayId(id)
            .guiTexture(new ResourceLocation("test", id), 0, 0, width, height, width, height,
                width, height, 0, 0, false);
    }

    @Test void aVanillaMenuKeepsNativeHeightWhenTheCalloutHasRoom() throws Exception {
        List<List<OverlayEvent>> pages = Collections.singletonList(Collections.singletonList(texture("chest", 176, 166)));
        assertEquals(166, PreviewGuiPageLayout.preferredHeight(pages, 208));
        List<PreviewGuiPageLayout.Placement> placements = PreviewGuiPageLayout.layout(pages.get(0),
            new PreviewDebugLayout.Rect(0, 0, 208, PreviewGuiPageLayout.preferredHeight(pages, 208)));
        assertEquals(176, placements.get(0).draw.width);
        assertEquals(166, placements.get(0).draw.height);
    }

    @Test void preferredImageHeightFitsSmallWidthsAndBoundsLargePortraits() throws Exception {
        List<List<OverlayEvent>> pages = Collections.singletonList(Collections.singletonList(texture("chest", 176, 166)));
        assertEquals(83, PreviewGuiPageLayout.preferredHeight(pages, 88));
        assertEquals(240, PreviewGuiPageLayout.preferredHeight(Collections.singletonList(
            Collections.singletonList(texture("portrait", 64, 2048))), 208));
        assertEquals(1, PreviewGuiPageLayout.preferredHeight(Collections.emptyList(), 0));
    }

    @Test void pagedImageHeightReservesTheLargestComposedPageWithoutChangingPageOrder() throws Exception {
        OverlayEvent root = texture("root", 100, 100);
        OverlayEvent child = texture("child", 40, 40).parentOverlayId("root").guiOrigin(0, 140);
        List<List<OverlayEvent>> pages = Arrays.asList(Collections.singletonList(texture("small", 32, 32)),
            Arrays.asList(root, child));
        assertEquals(180, PreviewGuiPageLayout.preferredHeight(pages, 208));
        assertEquals("small", pages.get(0).get(0).getOverlayId());
    }

    @Test void modelAvoidanceCannotCompressMenusBelowTheirReadableEdgeFallback() {
        assertEquals(96, PreviewGuiPageLayout.minimumReadableHeight(186, 0));
        assertEquals(83, PreviewGuiPageLayout.minimumReadableHeight(83, 0));
        assertEquals(112, PreviewGuiPageLayout.minimumReadableHeight(202, 16));
        assertEquals(52, PreviewGuiPageLayout.minimumReadableHeight(40, 16));
    }

    @Test void imageReadabilityFallbackNeverFeedsTheCameraFit() throws Exception {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/main/java/net/createmod/ponder/foundation/ui/PonderDebugScreen.java")), java.nio.charset.StandardCharsets.UTF_8);
        int start = source.indexOf("int minimumHeight ="), end = source.indexOf("view.placement = placed;", start);
        String placement = source.substring(start, end);
        assertTrue(placement.contains("placed.panel.height < minimumHeight"));
        assertTrue(placement.contains("PreviewCalloutLayout.place(area, null"));
        assertFalse(placement.contains("previewZoom"));
        assertFalse(placement.contains("modelFraming"));
    }
}
