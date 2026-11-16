package net.createmod.ponder.foundation.ui;

import java.awt.Point;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Immutable OpenGL column-major snapshot; construction and projection require no native context. */
public final class PreviewWorldProjection {
    private final float[] modelView, projection;
    private final int[] viewport;
    private final int screenWidth, screenHeight, displayWidth, displayHeight;

    public PreviewWorldProjection(float[] modelView, float[] projection, int[] viewport,
        int screenWidth, int screenHeight, int displayWidth, int displayHeight) {
        this.modelView = matrix(modelView); this.projection = matrix(projection);
        if (viewport == null || viewport.length != 4 || viewport[2] <= 0 || viewport[3] <= 0)
            throw new IllegalArgumentException("Viewport requires four entries and positive dimensions");
        if (screenWidth <= 0 || screenHeight <= 0 || displayWidth <= 0 || displayHeight <= 0)
            throw new IllegalArgumentException("Logical and display dimensions must be positive");
        this.viewport = viewport.clone(); this.screenWidth = screenWidth; this.screenHeight = screenHeight;
        this.displayWidth = displayWidth; this.displayHeight = displayHeight;
    }

    /** Only call on the render thread with its current model-view and projection matrices active. */
    public static PreviewWorldProjection captureNative(int screenWidth, int screenHeight, int displayWidth, int displayHeight) {
        PreviewGlQueries queries = PreviewGlQueries.runtime();
        FloatBuffer model = BufferUtils.createFloatBuffer(PreviewGlQueries.VECTOR_CAPACITY);
        FloatBuffer projection = BufferUtils.createFloatBuffer(PreviewGlQueries.VECTOR_CAPACITY);
        IntBuffer viewport = BufferUtils.createIntBuffer(PreviewGlQueries.VECTOR_CAPACITY);
        queries.floats(GL11.GL_MODELVIEW_MATRIX, model);
        queries.floats(GL11.GL_PROJECTION_MATRIX, projection);
        queries.integers(GL11.GL_VIEWPORT, viewport);
        float[] modelValues = new float[16], projectionValues = new float[16]; int[] view = new int[4];
        for (int i = 0; i < 16; i++) { modelValues[i] = model.get(i); projectionValues[i] = projection.get(i); }
        for (int i = 0; i < 4; i++) view[i] = viewport.get(i);
        return new PreviewWorldProjection(modelValues, projectionValues, view, screenWidth, screenHeight, displayWidth, displayHeight);
    }

    /** Changes only the projected GUI coordinate space, not the captured world camera or viewport. */
    PreviewWorldProjection withLogicalSize(int width, int height) {
        return new PreviewWorldProjection(modelView, projection, viewport, width, height, displayWidth, displayHeight);
    }

    public Point project(Vec3d point) {
        if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) return null;
        double[] eye = multiply(modelView, new double[] {point.x, point.y, point.z, 1});
        double[] clip = multiply(projection, eye);
        for (double value : clip) if (!Double.isFinite(value)) return null;
        double w = clip[3];
        if (w <= 0 || Math.abs(clip[0]) > w || Math.abs(clip[1]) > w || Math.abs(clip[2]) > w) return null;
        double pixelX = viewport[0] + (clip[0] / w + 1) * viewport[2] * 0.5D;
        double pixelY = viewport[1] + (clip[1] / w + 1) * viewport[3] * 0.5D;
        if (!Double.isFinite(pixelX) || !Double.isFinite(pixelY)
            || pixelX < 0 || pixelY < 0 || pixelX > displayWidth || pixelY > displayHeight) return null;
        // Exact clip edges are visible: round to logical pixels, then retain the final in-bounds pixel.
        int x = (int) Math.round(pixelX * screenWidth / displayWidth);
        int y = (int) Math.round((displayHeight - pixelY) * screenHeight / displayHeight);
        return new Point(Math.max(0, Math.min(screenWidth - 1, x)), Math.max(0, Math.min(screenHeight - 1, y)));
    }

    /** Squared normalized distance from screen center; smaller is better, rejected points are infinite. */
    public double centerScore(Vec3d point) {
        Point projected = project(point); if (projected == null) return Double.POSITIVE_INFINITY;
        double x = (projected.x - screenWidth * 0.5D) / (screenWidth * 0.5D);
        double y = (projected.y - screenHeight * 0.5D) / (screenHeight * 0.5D);
        return x * x + y * y;
    }
    private static float[] matrix(float[] values) {
        if (values == null || values.length != 16) throw new IllegalArgumentException("A matrix requires exactly sixteen entries");
        float[] result = values.clone();
        for (float value : result) if (!Float.isFinite(value)) throw new IllegalArgumentException("Matrices must be finite");
        return result;
    }
    private static double[] multiply(float[] matrix, double[] vector) {
        double[] result = new double[4];
        for (int row = 0; row < 4; row++)
            result[row] = matrix[row] * vector[0] + matrix[4 + row] * vector[1]
                + matrix[8 + row] * vector[2] + matrix[12 + row] * vector[3];
        return result;
    }
}
