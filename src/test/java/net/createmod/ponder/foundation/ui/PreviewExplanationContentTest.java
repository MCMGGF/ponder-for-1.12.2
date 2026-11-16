package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.OverlayEvent;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PreviewExplanationContentTest {

    @BeforeAll
    static void registerBlocks() {
        Bootstrap.register();
    }

    @Test
    void fallbackKeepsReadableDescriptionsWithoutDebugLabelsOrCameraMovement() throws Exception {
        PonderDebugScreen screen = screen();
        PonderScene scene = scene();
        OverlayEvent text = scene.createOverlayEvent(200).text("FIRST");
        prepare(screen, scene);
        float beforeScale = scale(screen, scene);
        PonderDebugScreen.PreviewLayout beforeViewport = screen.lastPreviewLayout;
        Object beforeBounds = get(screen, "captionBounds");
        String overflowText = String.join("\n", java.util.Collections.nCopies(4, PreviewTextLayoutTest.longChinese()));
        text.text("FIRST " + overflowText).captionPosition(-1000, -1000)
            .captionOffset(5000, 5000).pointAt(new Vec3d(5000, -5000, 5000));
        scene.createOverlayEvent(200).text("SECOND");
        scene.createOverlayEvent(200).controls(Vec3d.ZERO, Pointing.RIGHT).controlLeftClick().controlCtrl();
        scene.recordItemActorSpawn(1, Vec3d.ZERO, 10, "NAMED ITEM", ItemStack.EMPTY);
        scene.recordPointOfInterest(Vec3d.ZERO);
        prepare(screen, scene);
        String content = (String) get(screen, "captionText");
        for (String expected : new String[] { "FIRST", "SECOND", "\u5de6\u952e", "Ctrl" }) {
            assertTrue(content.contains(expected), expected);
        }
        for (String debugLabel : new String[] { "RIGHT", "NAMED ITEM", "POI" }) {
            assertFalse(content.contains(debugLabel), debugLabel);
        }
        assertSame(beforeViewport, screen.lastPreviewLayout);
        assertEquals(beforeScale, scale(screen, scene));
        PreviewDebugLayout.Rect bounds = (PreviewDebugLayout.Rect) get(screen, "captionBounds");
        PreviewDebugLayout.Rect previous = (PreviewDebugLayout.Rect) beforeBounds;
        assertEquals(previous.x, bounds.x);
        assertEquals(previous.y, bounds.y);
        assertEquals(previous.width, bounds.width);
        assertEquals(previous.height, bounds.height);
        PreviewDebugLayout.Rect dock = ((PreviewExplanationLayout) get(screen, "explanationLayout")).explanation;
        assertTrue(bounds.x >= dock.x && bounds.right() <= dock.right());
        assertTrue(bounds.y >= dock.y && bounds.bottom() <= dock.bottom());
        assertEquals(0, PreviewCalloutLayout.intersectionArea(bounds, new PreviewDebugLayout.Rect(
            beforeViewport.originX, beforeViewport.originY, beforeViewport.width, beforeViewport.height)));
        assertTrue(((List<?>) get(screen, "captionLines")).size() >= 12,
            "the fixture must overflow the taller compact explanation pane");
        assertTrue(((PreviewCaptionPaging) get(screen, "captionPaging")).pageCount() > 1);
    }

    @Test
    void everyTextLineAndGuiRootCanBePagedWithoutChangingCameraOrLosingChildren() throws Exception {
        PonderDebugScreen screen = screen();
        PonderScene scene = scene();
        scene.createOverlayEvent(200).text(PreviewTextLayoutTest.longChinese());
        scene.createOverlayEvent(200).guiTexture(id("root"), 0, 0, 200, 100, 200, 100, 200, 100, 0, 0, true)
            .overlayId("root");
        scene.createOverlayEvent(200).guiTexture(id("child"), 0, 0, 20, 20, 20, 20, 20, 20, 0, 0, false)
            .overlayId("child").parentOverlayId("root").guiOrigin(40, 20).scaleToParent(true);
        scene.createOverlayEvent(200).guiTexture(id("second"), 0, 0, 100, 50, 100, 50, 100, 50, 0, 0, true)
            .overlayId("second");
        scene.createOverlayEvent(200).guiHighlight("child", 4, 4, 8, 8).text("HIGHLIGHT");
        prepare(screen, scene);
        float beforeScale = scale(screen, scene);
        PreviewCaptionPaging paging = (PreviewCaptionPaging) get(screen, "captionPaging");
        int lines = ((List<?>) get(screen, "captionLines")).size();
        int nextLine = 0;
        int images = 0;
        for (int page = 0; page < paging.pageCount(); page++) {
            prepare(screen, scene);
            assertEquals(page, paging.page());
            assertEquals(beforeScale, scale(screen, scene));
            if (paging.imageIndex() < 0) {
                assertEquals(nextLine, paging.firstLine());
                nextLine = paging.endLine();
            } else {
                images++;
                List<?> placements = placements(screen, scene);
                assertEquals(paging.imageIndex() == 0 ? 2 : 1, placements.size());
                PreviewDebugLayout.Rect bounds = (PreviewDebugLayout.Rect) get(screen, "captionBounds");
                for (Object placement : placements) {
                    int x = value(placement, "panelX");
                    int y = value(placement, "panelY");
                    assertTrue(x >= bounds.x && x + value(placement, "panelWidth") <= bounds.right());
                    assertTrue(y >= bounds.y && y + value(placement, "panelHeight") <= bounds.bottom() - 14);
                }
            }
            paging.turn(1);
        }
        assertEquals(lines, nextLine);
        assertEquals(2, images);
    }

    @Test
    void explanationClicksCancelDragAndCannotEnterTheModelEvenAtItsEdges() throws Exception {
        PonderDebugScreen screen = screen();
        PonderScene scene = scene();
        scene.createOverlayEvent(200).text(PreviewTextLayoutTest.longChinese());
        prepare(screen, scene);
        PreviewDebugLayout.Rect bounds = (PreviewDebugLayout.Rect) get(screen, "captionBounds");
        PreviewPointerInput pointer = (PreviewPointerInput) get(screen, "pointerInput");
        pointer.beginPreviewDrag();
        screen.playing = true;
        screen.mouseClicked(bounds.x + 4, bounds.y + 4, 0);
        assertFalse(pointer.isPreviewDragging());
        assertFalse(screen.playing);
        Method hit = PonderDebugScreen.class.getDeclaredMethod("isMouseOverPreview", int.class, int.class);
        hit.setAccessible(true);
        assertFalse((Boolean) hit.invoke(screen, bounds.x, bounds.y));
        assertFalse((Boolean) hit.invoke(screen, bounds.right() - 1, bounds.bottom() - 1));
        assertTrue((Boolean) hit.invoke(screen, screen.lastPreviewLayout.originX + 1,
            screen.lastPreviewLayout.originY + 1));
    }

    @Test
    void modelFailureReplacesExplanationContentButDoesNotReframeTheModel() throws Exception {
        PonderDebugScreen screen = screen();
        PonderScene scene = scene();
        scene.createOverlayEvent(200).text("normal");
        prepare(screen, scene);
        float before = scale(screen, scene);
        PreviewModelMode<?> mode = (PreviewModelMode<?>) get(screen, "modelMode");
        mode.fail("FAILED MODEL");
        field("modelNotice").setBoolean(screen, true);
        prepare(screen, scene);
        assertEquals("FAILED MODEL", get(screen, "captionText"));
        assertEquals(before, scale(screen, scene));
    }

    private static PonderDebugScreen screen() throws Exception {
        PonderDebugScreen screen = PonderDebugScreen.showcase(null, 0);
        screen.width = 320;
        screen.height = 240;
        screen.fontRenderer = new PreviewTextLayoutTest.MetricsFont();
        PreviewExplanationLayout layout = PreviewExplanationLayout.showcase(320, 240);
        field("explanationLayout").set(screen, layout);
        Constructor<PonderDebugScreen.PreviewLayout> constructor = PonderDebugScreen.PreviewLayout.class
            .getDeclaredConstructor(int.class, int.class, int.class, int.class);
        constructor.setAccessible(true);
        screen.lastPreviewLayout = constructor.newInstance(layout.model.x, layout.model.y,
            layout.model.width, layout.model.height);
        return screen;
    }

    private static PonderScene scene() {
        PonderScene scene = new PonderScene(new PonderStoryBoardEntry((builder, util) -> {},
            "explanation_test", id("scene"), id("component")), new PonderLocalization());
        scene.setBasePlateSize(5);
        return scene;
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation("explanation_test", value);
    }

    private static void prepare(PonderDebugScreen screen, PonderScene scene) throws Exception {
        Method method = PonderDebugScreen.class.getDeclaredMethod("prepareExplanation", PonderScene.class, float.class);
        method.setAccessible(true);
        method.invoke(screen, scene, 20F);
    }

    private static float scale(PonderDebugScreen screen, PonderScene scene) throws Exception {
        Method method = PonderDebugScreen.class.getDeclaredMethod("computePreviewScale", PonderScene.class,
            PonderScenePreview.PreviewBounds.class, PonderDebugScreen.PreviewLayout.class);
        method.setAccessible(true);
        return (Float) method.invoke(screen, scene, PonderScenePreview.computeBounds(scene), screen.lastPreviewLayout);
    }

    private static List<?> placements(PonderDebugScreen screen, PonderScene scene) throws Exception {
        Method method = PonderDebugScreen.class.getDeclaredMethod("getActiveGuiOverlayPlacements", PonderScene.class,
            PonderDebugScreen.PreviewLayout.class, float.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(screen, scene, screen.lastPreviewLayout, 20F);
    }

    private static int value(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(instance);
    }

    private static Object get(Object screen, String name) throws Exception {
        return field(name).get(screen);
    }

    private static Field field(String name) throws Exception {
        Field field = PonderDebugScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
