package net.createmod.ponder.api.scene;

import java.util.function.Consumer;

import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public interface SceneBuilder {

    OverlayInstructions overlay();

    WorldInstructions world();

    DebugInstructions debug();

    EffectInstructions effects();

    SpecialInstructions special();

    PonderScene getScene();

    void title(String sceneId, String title);

    void configureBasePlate(int xOffset, int zOffset, int basePlateSize);

    void scaleSceneView(float factor);

    void removeShadow();

    void setSceneOffsetY(float yOffset);

    void showBasePlate();

    void addInstruction(Consumer<PonderScene> callback);

    void idle(int ticks);

    void idleSeconds(int seconds);

    void markAsFinished();

    void setNextUpEnabled(boolean isEnabled);

    void rotateCameraY(float degrees);

    /** Authored pitch/yaw deltas; positive-duration authoring steps use the legacy fixed animation. */
    default void rotateCamera(float pitchDegrees, float yawDegrees, boolean instant) {
        rotateCamera(Float.valueOf(pitchDegrees), yawDegrees, instant);
    }

    /** A null pitch preserves an in-progress authored pitch while rotating yaw. */
    default void rotateCamera(Float pitchDegrees, float yawDegrees, boolean instant) {
        getScene().recordCameraRotation(pitchDegrees, yawDegrees, instant);
        getScene().recordOperation("scene.rotateCamera(" + pitchDegrees + ", " + yawDegrees + ", " + instant + ")");
    }

    /** A blocking, deterministic authored camera move; null arguments restore the default view. */
    default void zoomScene(Vec3d center, Float multiplier, int duration) {
        zoomScene(center, multiplier, duration, true);
    }

    /** Nonblocking mode preserves recorded instruction timing when forking an existing scene. */
    default void zoomScene(Vec3d center, Float multiplier, int duration, boolean waitForCompletion) {
        getScene().recordCameraView(center, multiplier, duration);
        getScene().recordOperation("scene.zoomScene(" + center + ", " + multiplier + ", " + duration + ", " + waitForCompletion + ")");
        if (waitForCompletion) idle(duration);
    }

    /** Extends the fixed framing envelope without creating blocks or changing a live world. */
    default void encapsulateBounds(BlockPos endpoint) {
        getScene().encapsulateBounds(endpoint);
        getScene().recordOperation("scene.encapsulateBounds(" + endpoint + ")");
    }

    void addKeyframe();

    void addLazyKeyframe();
}
