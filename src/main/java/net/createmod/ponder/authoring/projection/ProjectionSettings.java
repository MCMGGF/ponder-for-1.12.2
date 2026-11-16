package net.createmod.ponder.authoring.projection;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/** Immutable single-player projector configuration; no world references. */
public final class ProjectionSettings {
    public enum Trigger { ALWAYS, POWERED, RISING_EDGE }
    public final String sceneId;
    public final String componentId;
    public final int entryIndex;
    public final boolean paused;
    public final boolean loop;
    public final double speed;
    public final double yaw;
    public final double scale;
    public final Trigger trigger;

    public ProjectionSettings(String sceneId, boolean paused, boolean loop, double speed, double yaw,
        double scale, Trigger trigger) {
        this(sceneId, "", paused, loop, speed, yaw, scale, trigger);
    }

    public ProjectionSettings(String sceneId, String componentId, boolean paused, boolean loop, double speed, double yaw,
        double scale, Trigger trigger) {
        this(sceneId, componentId, -1, paused, loop, speed, yaw, scale, trigger);
    }

    public ProjectionSettings(String sceneId, String componentId, int entryIndex, boolean paused, boolean loop,
        double speed, double yaw, double scale, Trigger trigger) {
        if (sceneId == null || sceneId.length() > 256 || (!sceneId.isEmpty()
            && !net.createmod.ponder.authoring.AuthoringDocument.validId(sceneId)))
            throw new IllegalArgumentException("Invalid scene ID");
        if (componentId == null || componentId.length() > 256 || (!componentId.isEmpty()
            && !net.createmod.ponder.authoring.AuthoringDocument.validId(componentId)))
            throw new IllegalArgumentException("Invalid component ID");
        if (!Double.isFinite(speed) || speed < 0.1 || speed > 4.0)
            throw new IllegalArgumentException("Speed must be 0.1..4");
        if (!Double.isFinite(yaw) || !Double.isFinite(scale) || scale < 0.03125 || scale > 4)
            throw new IllegalArgumentException("Scale must be 0.03125..4");
        this.sceneId = sceneId.isEmpty() ? "" : new ResourceLocation(sceneId).toString();
        this.componentId = componentId;
        if (entryIndex < -1 || entryIndex > 16384) throw new IllegalArgumentException("Invalid entry index");
        this.entryIndex = entryIndex;
        this.paused = paused;
        this.loop = loop;
        this.speed = speed;
        this.yaw = ((yaw % 360) + 360) % 360;
        this.scale = scale;
        this.trigger = trigger == null ? Trigger.ALWAYS : trigger;
    }

    public static ProjectionSettings defaults(boolean miniature) {
        return new ProjectionSettings("", true, true, 1, 0, miniature ? 0.125 : 1, Trigger.ALWAYS);
    }

    public NBTTagCompound toNbt() {
        NBTTagCompound n = new NBTTagCompound();
        n.setString("Scene", sceneId); n.setString("Component", componentId); n.setBoolean("Paused", paused); n.setBoolean("Loop", loop);
        n.setInteger("Entry", entryIndex);
        n.setDouble("Speed", speed); n.setDouble("Yaw", yaw); n.setDouble("Scale", scale);
        n.setString("Trigger", trigger.name());
        return n;
    }

    public static ProjectionSettings fromNbt(NBTTagCompound n, boolean miniature) {
        try {
            return new ProjectionSettings(n.getString("Scene"), n.getString("Component"), n.hasKey("Entry") ? n.getInteger("Entry") : -1, n.getBoolean("Paused"), n.getBoolean("Loop"),
                n.getDouble("Speed"), n.getDouble("Yaw"), n.getDouble("Scale"),
                Trigger.valueOf(n.getString("Trigger")));
        } catch (RuntimeException invalid) { return defaults(miniature); }
    }

    public double tick(double elapsed, int duration) {
        double t = Math.max(0, elapsed);
        int end = Math.max(1, duration);
        return loop ? t % end : Math.min(end, t);
    }
}
