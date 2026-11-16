package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PreviewExplanationLayoutTest {

    private static final int[][] SCREEN_SIZES = {
        { 320, 240 }, { 360, 240 }, { 500, 267 }, { 800, 450 }, { 960, 540 }, { 1280, 720 },
        { 1920, 1080 }, { 2560, 1080 }, { 3840, 2160 }
    };

    @Test
    void minimumShowcaseSeparatesModelAndReadableDockWithMoreVerticalRoom() {
        PreviewExplanationLayout layout = PreviewExplanationLayout.showcase(320, 240);

        assertRect(layout.model, 18, 56, 152, 104);
        assertRect(layout.explanation, 178, 56, 124, 104);
        assertFalse(overlap(layout.model, layout.explanation));
        assertTrue(layout.model.height > 74);
    }

    @Test
    void showcaseKeepsTheModelAndFallbackInsideTheContentArea() {
        for (int[] size : SCREEN_SIZES) {
            PreviewExplanationLayout layout = PreviewExplanationLayout.showcase(size[0], size[1]);
            PreviewDebugLayout.Rect screen = new PreviewDebugLayout.Rect(0, 0, size[0], size[1]);

            assertInside(layout.model, screen);
            assertInside(layout.explanation, screen);
            boolean compact = PreviewExplanationLayout.isCompact(size[0], size[1]);
            if (compact) assertFalse(overlap(layout.model, layout.explanation));
            else assertInside(layout.explanation, layout.model);
            assertTrue(layout.model.width >= 152);
            assertTrue(layout.model.height >= 74);
            assertEquals(size[0] - 18, layout.explanation.right());
            assertEquals(layout.model.y, layout.explanation.y);
            assertEquals(layout.model.height, layout.explanation.height);
            assertEquals(18, layout.model.x);
            assertEquals(compact ? layout.explanation.x - 8 : size[0] - 18, layout.model.right());
        }
    }

    @Test
    void showcaseReservesHeaderAndTimelineSpaceAtEverySupportedSize() {
        for (int[] size : SCREEN_SIZES) {
            PreviewExplanationLayout layout = PreviewExplanationLayout.showcase(size[0], size[1]);

            boolean compact = PreviewExplanationLayout.isCompact(size[0], size[1]);
            assertEquals(compact ? 56 : 76, layout.model.y);
            assertEquals(layout.model.y, layout.explanation.y);
            assertEquals(size[1] - (compact ? 80 : 90), layout.model.bottom());
            assertEquals(layout.model.bottom(), layout.explanation.bottom());
            assertTrue(layout.model.bottom() < PreviewExplanationLayout.playbackY(size[0], size[1]) - 11);
            assertTrue(PreviewExplanationLayout.playbackY(size[0], size[1]) + 3
                < PreviewExplanationLayout.toolbarY(size[0], size[1]));
        }
    }

    @Test
    void showcaseExplanationWidthUsesTheViewportAndStaysBounded() {
        for (int[] size : SCREEN_SIZES) {
            PreviewExplanationLayout layout = PreviewExplanationLayout.showcase(size[0], size[1]);
            int expectedWidth = Math.max(124, Math.min(240, (size[0] - 36) / 3));

            assertEquals(expectedWidth, layout.explanation.width);
            assertTrue(layout.explanation.width >= 124 && layout.explanation.width <= 240);
        }
        assertEquals(240, PreviewExplanationLayout.showcase(3840, 2160).explanation.width);
    }

    @Test
    void showcaseHeightChangesDoNotChangeTheFullWidthModelOrFallbackWidth() {
        PreviewExplanationLayout shortLayout = PreviewExplanationLayout.showcase(800, 360);
        PreviewExplanationLayout tallLayout = PreviewExplanationLayout.showcase(800, 1080);

        assertEquals(shortLayout.model.x, tallLayout.model.x);
        assertEquals(shortLayout.model.width, tallLayout.model.width);
        assertEquals(shortLayout.explanation.x, tallLayout.explanation.x);
        assertEquals(shortLayout.explanation.width, tallLayout.explanation.width);
    }

    @Test
    void debugStacksRegionsInsideTheExistingPreviewInnerFrame() {
        for (int[] size : SCREEN_SIZES) {
            PreviewDebugLayout.Rect preview = new PreviewDebugLayout(size[0], size[1]).preview;
            PreviewDebugLayout.Rect inner = new PreviewDebugLayout.Rect(preview.x + 7, preview.y + 19,
                preview.width - 14, preview.height - 40);
            PreviewExplanationLayout layout = PreviewExplanationLayout.debug(inner);

            assertInside(layout.model, inner);
            assertInside(layout.explanation, inner);
            assertFalse(overlap(layout.model, layout.explanation));
            assertEquals(inner.x, layout.model.x);
            assertEquals(inner.x, layout.explanation.x);
            assertEquals(inner.y, layout.model.y);
            assertEquals(inner.width, layout.model.width);
            assertEquals(inner.width, layout.explanation.width);
            assertEquals(inner.bottom(), layout.explanation.bottom());
            assertEquals(8, layout.explanation.y - layout.model.bottom());
            assertTrue(layout.model.height >= 64);
            assertTrue(layout.explanation.height >= 36 && layout.explanation.height <= 96);
        }
    }

    @Test
    void debugMinimumInnerFrameLeavesSpaceForModelAndPaginatedExplanation() {
        PreviewExplanationLayout layout = PreviewExplanationLayout.debug(
            new PreviewDebugLayout.Rect(119, 69, 180, 108));

        assertRect(layout.model, 119, 69, 180, 64);
        assertRect(layout.explanation, 119, 141, 180, 36);
    }

    @Test
    void debugExplanationHeightUsesTheViewportAndHasAnUpperLimit() {
        PreviewExplanationLayout medium = PreviewExplanationLayout.debug(new PreviewDebugLayout.Rect(12, 34, 300, 180));
        PreviewExplanationLayout tall = PreviewExplanationLayout.debug(new PreviewDebugLayout.Rect(12, 34, 300, 900));

        assertEquals(60, medium.explanation.height);
        assertEquals(96, tall.explanation.height);
        assertEquals(112, medium.model.height);
        assertEquals(796, tall.model.height);
    }

    @Test
    void debugHorizontalResizingDoesNotChangeItsVerticalSplit() {
        PreviewExplanationLayout narrow = PreviewExplanationLayout.debug(new PreviewDebugLayout.Rect(12, 34, 180, 180));
        PreviewExplanationLayout wide = PreviewExplanationLayout.debug(new PreviewDebugLayout.Rect(12, 34, 900, 180));

        assertEquals(narrow.model.y, wide.model.y);
        assertEquals(narrow.model.height, wide.model.height);
        assertEquals(narrow.explanation.y, wide.explanation.y);
        assertEquals(narrow.explanation.height, wide.explanation.height);
    }

    @Test
    void resizingAwayAndBackRestoresExactlyTheSameRegions() {
        PreviewExplanationLayout showcaseBefore = PreviewExplanationLayout.showcase(320, 240);
        PreviewExplanationLayout debugBefore = PreviewExplanationLayout.debug(
            new PreviewDebugLayout.Rect(119, 69, 180, 108));

        for (int[] size : SCREEN_SIZES) {
            PreviewExplanationLayout.showcase(size[0], size[1]);
            PreviewExplanationLayout.debug(new PreviewDebugLayout.Rect(0, 0, size[0], size[1]));
        }

        PreviewExplanationLayout showcaseAfter = PreviewExplanationLayout.showcase(320, 240);
        PreviewExplanationLayout debugAfter = PreviewExplanationLayout.debug(
            new PreviewDebugLayout.Rect(119, 69, 180, 108));
        assertSameRegions(showcaseBefore, showcaseAfter);
        assertSameRegions(debugBefore, debugAfter);
    }

    @Test
    void undersizedShowcaseViewportsNeverProduceNegativeOrOutOfBoundsRegions() {
        for (int width : new int[] { 0, 1, 2, 17, 35, 36, 37, 40, 123, 160, 240, 319 }) {
            for (int height : new int[] { 0, 1, 18, 75, 76, 90, 165, 166, 167, 239 }) {
                PreviewExplanationLayout layout = PreviewExplanationLayout.showcase(width, height);
                PreviewDebugLayout.Rect screen = new PreviewDebugLayout.Rect(0, 0, width, height);

                assertInside(layout.model, screen);
                assertInside(layout.explanation, screen);
                assertFalse(overlap(layout.model, layout.explanation));
            }
        }
    }

    @Test
    void undersizedDebugViewportsKeepOneModelPixelWheneverHeightPermits() {
        for (int width : new int[] { 0, 1, 20, 180 }) {
            for (int height = 0; height <= 108; height++) {
                PreviewDebugLayout.Rect inner = new PreviewDebugLayout.Rect(12, 34, width, height);
                PreviewExplanationLayout layout = PreviewExplanationLayout.debug(inner);

                assertInside(layout.model, inner);
                assertInside(layout.explanation, inner);
                assertFalse(overlap(layout.model, layout.explanation));
                assertEquals(inner.bottom(), layout.explanation.bottom());
                assertTrue(height == 0 || layout.model.height > 0);
                if (height >= 37) {
                    assertTrue(layout.explanation.height >= 36);
                }
            }
        }
    }

    @Test
    void negativeShowcaseDimensionsCollapseToAnEmptyOrigin() {
        PreviewExplanationLayout layout = PreviewExplanationLayout.showcase(-320, -240);

        assertRect(layout.model, 0, 0, 0, 0);
        assertRect(layout.explanation, 0, 0, 0, 0);
    }

    @Test
    void movingAndResizingAnExplanationKeepsExactlyTheSameModelFrame() {
        PreviewExplanationLayout layout = PreviewExplanationLayout.showcase(320, 240);
        PreviewExplanationLayout moved = layout.withExplanation(new PreviewDebugLayout.Rect(24, 80, 190, 42));

        assertEquals(layout.model, moved.model);
        assertRect(moved.explanation, 24, 80, 190, 42);
    }

    @Test
    void minimumShowcaseCameraDoesNotReserveHeaderOrFooterSpaceAgain() {
        PreviewDebugLayout.Rect model = PreviewExplanationLayout.showcase(320, 240).model;

        assertEquals(108.0F, PreviewCameraMath.anchorY(model.y, model.height, true), 0.0001F);
        assertEquals(92.0D / Math.sqrt(3.0D),
            PreviewCameraMath.fitScale(1, 1, 1, model.width, model.height, true, 1.0F), 0.0001D);
    }

    @Test
    void explicitFontConfigurationReservesHeaderAndTimelineWithoutLookingAtCaptionContent() {
        for (int[] size : SCREEN_SIZES) {
            for (int fontHeight : new int[] {9, 12, 18, 24}) {
                PreviewExplanationLayout layout = PreviewExplanationLayout.showcase(size[0], size[1], fontHeight);
                boolean compact = PreviewExplanationLayout.isCompact(size[0], size[1]);
                int headerY = compact ? 12 : 28;
                int lineHeight = Math.max(compact ? 20 : 12, fontHeight + 2);
                assertTrue(headerY + 5 + lineHeight + fontHeight < layout.model.y);
                int labelY = PreviewExplanationLayout.playbackY(size[0], size[1], fontHeight) - fontHeight - 2;
                assertTrue(layout.model.bottom() < labelY);
                assertEquals(layout.model, layout.withExplanation(layout.explanation).model);
            }
        }
    }

    @Test
    void minimumDebugCameraUsesTheActualUpperModelRegion() {
        PreviewDebugLayout.Rect model = debugModel(320, 240);

        assertEquals(101.0F, PreviewCameraMath.anchorY(model.y, model.height, false), 0.0001F);
        assertEquals(52.0D / Math.sqrt(3.0D),
            PreviewCameraMath.fitScale(1, 1, 1, model.width, model.height, false, 1.0F), 0.0001D);
    }

    @Test
    void cameraOrbitEnvelopesStayInsideEveryActualModelRegion() {
        for (int[] screen : SCREEN_SIZES) {
            PreviewDebugLayout.Rect showcaseModel = PreviewExplanationLayout.showcase(screen[0], screen[1]).model;
            PreviewDebugLayout.Rect debugModel = debugModel(screen[0], screen[1]);
            for (double[] size : new double[][] {
                { 1, 1, 1 }, { 15, 4, 37 }, { 3, 96, 4 }, { 160, 1, 160 },
                { 1500, 500, 400 }, { 1, 1024, 1 }, { 1, 1, 240 }
            }) {
                assertOrbitFits(showcaseModel, true, size);
                assertOrbitFits(debugModel, false, size);
            }
        }
    }

    private static PreviewDebugLayout.Rect debugModel(int screenWidth, int screenHeight) {
        PreviewDebugLayout.Rect preview = new PreviewDebugLayout(screenWidth, screenHeight).preview;
        return PreviewExplanationLayout.debug(new PreviewDebugLayout.Rect(preview.x + 7, preview.y + 19,
            preview.width - 14, preview.height - 40)).model;
    }

    private static void assertOrbitFits(PreviewDebugLayout.Rect model, boolean showcase, double[] size) {
        PreviewModelFraming framing = new PreviewModelFraming();
        framing.initialize(0, 0, 0, size[0], size[1], size[2]);
        float scale = framing.fitScale(model.width, model.height, showcase, 1.0F);
        assertTrue(Float.isFinite(scale) && scale > 0.0F);
        assertEquals(model.y + model.height / 2.0F,
            PreviewCameraMath.anchorY(model.y, model.height, showcase), 0.0001F);

        for (int yaw = -180; yaw <= 180; yaw += 15) {
            assertYawFits(model, showcase, size, scale, Math.toRadians(yaw));
        }
        // Include extrema between the sampled angles for long, narrow structures.
        double widthPeak = Math.atan2(size[2], size[0]);
        double depthPeak = Math.atan2(size[0], size[2]);
        for (double yaw : new double[] { widthPeak, -widthPeak, depthPeak, -depthPeak }) {
            assertYawFits(model, showcase, size, scale, yaw);
        }
    }

    private static void assertYawFits(PreviewDebugLayout.Rect model, boolean showcase, double[] size,
        float scale, double yaw) {
        double projectedWidth = Math.abs(size[0] * Math.cos(yaw)) + Math.abs(size[2] * Math.sin(yaw));
        double projectedDepth = Math.abs(size[0] * Math.sin(yaw)) + Math.abs(size[2] * Math.cos(yaw));
        double maxPitch = Math.toRadians(PreviewCameraMath.MAX_PITCH);
        double defaultPitch = Math.toRadians(PreviewCameraMath.DEFAULT_PITCH);
        double heightPeak = Math.min(maxPitch, Math.atan2(projectedDepth, size[1]));
        double centerX = model.x + model.width / 2.0D;
        double centerY = PreviewCameraMath.anchorY(model.y, model.height, showcase);
        double horizontalPadding = showcase ? 12.0D : 6.0D;
        double halfWidth = projectedWidth * scale / 2.0D;
        double epsilon = 0.001D;
        assertTrue(centerX - halfWidth >= model.x + horizontalPadding - epsilon,
            "Orbit crosses the model region's left padding");
        assertTrue(centerX + halfWidth <= model.right() - horizontalPadding + epsilon,
            "Orbit crosses the model region's right padding");

        for (double pitch : new double[] {
            -maxPitch, -defaultPitch, 0, defaultPitch, maxPitch, -heightPeak, heightPeak
        }) {
            double projectedHeight = size[1] * Math.abs(Math.cos(pitch))
                + projectedDepth * Math.abs(Math.sin(pitch));
            double halfHeight = projectedHeight * scale / 2.0D;
            assertTrue(centerY - halfHeight >= model.y + 6.0D - epsilon,
                "Pitch crosses the model region's top padding");
            assertTrue(centerY + halfHeight <= model.bottom() - 6.0D + epsilon,
                "Pitch crosses the model region's bottom padding");
        }
    }

    private static void assertSameRegions(PreviewExplanationLayout expected, PreviewExplanationLayout actual) {
        assertRect(actual.model, expected.model.x, expected.model.y, expected.model.width, expected.model.height);
        assertRect(actual.explanation, expected.explanation.x, expected.explanation.y, expected.explanation.width,
            expected.explanation.height);
    }

    private static void assertRect(PreviewDebugLayout.Rect rect, int x, int y, int width, int height) {
        assertEquals(x, rect.x);
        assertEquals(y, rect.y);
        assertEquals(width, rect.width);
        assertEquals(height, rect.height);
    }

    private static void assertInside(PreviewDebugLayout.Rect rect, PreviewDebugLayout.Rect container) {
        assertTrue(rect.width >= 0 && rect.height >= 0);
        assertTrue(rect.x >= container.x && rect.y >= container.y);
        assertTrue(rect.right() <= container.right() && rect.bottom() <= container.bottom());
    }

    private static boolean overlap(PreviewDebugLayout.Rect first, PreviewDebugLayout.Rect second) {
        return first.width > 0 && first.height > 0 && second.width > 0 && second.height > 0
            && first.x < second.right() && first.right() > second.x && first.y < second.bottom()
            && first.bottom() > second.y;
    }
}
