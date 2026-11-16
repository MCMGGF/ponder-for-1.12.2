package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.List;

public final class PreviewCameraMathTest {

    public static void main(String[] args) {
        int assertions = 0;
        for (int[] viewport : new int[][] { { 152, 74 }, { 180, 64 }, { 284, 204 }, { 604, 324 }, { 924, 504 },
            { 1244, 684 }, { 2524, 1044 } }) {
            for (double[] size : new double[][] { { 1, 1, 1 }, { 15, 4, 37 }, { 3, 96, 4 },
                { 160, 1, 160 }, { 1500, 500, 400 }, { 1, 1024, 1 }, { 1, 1, 240 } }) {
                for (boolean showcase : new boolean[] { false, true }) {
                    float scale = PreviewCameraMath.fitScale(size[0], size[1], size[2],
                        viewport[0], viewport[1], showcase, 1.0F);
                    check(Float.isFinite(scale) && scale > 0.0F, "Non-finite fit");
                    assertions++;
                    List<Double> yaws = new ArrayList<Double>();
                    for (int yaw = -360; yaw <= 360; yaw += 5) {
                        yaws.add(Math.toRadians(yaw));
                    }
                    double widthPeak = Math.atan2(size[2], size[0]);
                    double depthPeak = Math.atan2(size[0], size[2]);
                    yaws.add(widthPeak);
                    yaws.add(-widthPeak);
                    yaws.add(depthPeak);
                    yaws.add(-depthPeak);
                    for (double yaw : yaws) {
                        double projectedX = Math.abs(size[0] * Math.cos(yaw))
                            + Math.abs(size[2] * Math.sin(yaw));
                        double projectedZ = Math.abs(size[0] * Math.sin(yaw))
                            + Math.abs(size[2] * Math.cos(yaw));
                        List<Double> pitches = new ArrayList<Double>();
                        for (int pitch = -180; pitch <= 180; pitch += 5) {
                            pitches.add(Math.toRadians(pitch));
                        }
                        pitches.add(Math.toRadians(PreviewCameraMath.DEFAULT_PITCH));
                        pitches.add(-Math.toRadians(PreviewCameraMath.DEFAULT_PITCH));
                        double heightPeak = Math.atan2(projectedZ, size[1]);
                        pitches.add(heightPeak);
                        pitches.add(-heightPeak);
                        for (double pitch : pitches) {
                            double projectedY = size[1] * Math.abs(Math.cos(pitch))
                                + projectedZ * Math.abs(Math.sin(pitch));
                            check(projectedX * scale <= viewport[0] - (showcase ? 24 : 12) + 0.001D,
                                "Horizontal orbit clipping");
                            check(projectedY * scale <= viewport[1] - 12 + 0.001D,
                                "Vertical pitch clipping");
                            assertions += 2;
                        }
                    }
                    float exaggerated = PreviewCameraMath.fitScale(size[0], size[1], size[2],
                        viewport[0], viewport[1], showcase, 8.0F);
                    check(Math.abs(scale - exaggerated) < 0.0001F, "Authored scale defeats initial fit");
                    assertions++;
                }
            }
        }
        check(PreviewCameraMath.fitScale(1500, 500, 400, 284, 204, true, 1) < 1,
            "Large previews must support subpixel scale");
        check(PreviewCameraMath.anchorY(18, 204, true) == 120.0F, "Wrong model center");
        check(PreviewCameraMath.anchorY(10, 100, false) == 60.0F, "Wrong debug center");
        assertions += 3;
        check(PreviewCameraMath.usableModelBounds(-30, -15, -20, 35, 50, 40, 5, 5, 5),
            "Valid offset model rejected");
        check(!PreviewCameraMath.usableModelBounds(Double.NEGATIVE_INFINITY, 0, 0,
            Double.POSITIVE_INFINITY, 1, 1, 0, 0, 0), "Infinite model accepted");
        check(!PreviewCameraMath.usableModelBounds(0, Double.NaN, 0, 1, 1, 1, 0, 0, 0),
            "NaN model accepted");
        check(!PreviewCameraMath.usableModelBounds(-1E20, 0, 0, 1E20, 1, 1, 0, 0, 0),
            "Huge model accepted");
        check(!PreviewCameraMath.usableModelBounds(100000, 0, 0, 100001, 1, 1, 0, 0, 0),
            "Far-away model accepted");
        check(!PreviewCameraMath.usableModelBounds(2, 0, 0, 1, 1, 1, 0, 0, 0),
            "Inverted model accepted");
        assertions += 6;
        System.out.println("PreviewCameraMathTest: " + assertions + " assertions passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
