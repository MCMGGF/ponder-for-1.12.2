package net.createmod.ponder.foundation.content.mmce;

/** Copied resource identifiers only; never retains a native model provider. */
public final class MmceStaticModelSpec {

    public static final String STATIC_NOTE =
        "仅显示已注册默认模型的静态外观；不运行机器、动画、状态切换或发光效果";

    private final String name;
    private final String geometry;
    private final String texture;
    private final String animation;
    private final String note;

    private MmceStaticModelSpec(String name, String geometry, String texture, String animation, String note) {
        this.name = name;
        this.geometry = geometry;
        this.texture = texture;
        this.animation = animation;
        this.note = note;
    }

    public static MmceStaticModelSpec bound(String name, String geometry, String texture, String animation) {
        if (name == null || geometry == null || geometry.isEmpty() || texture == null || texture.isEmpty()) {
            throw new IllegalArgumentException("默认模型缺少几何或纹理资源");
        }
        return new MmceStaticModelSpec(name, geometry, texture, animation == null ? "" : animation, STATIC_NOTE);
    }

    public static MmceStaticModelSpec unavailable(String reason) {
        return new MmceStaticModelSpec("", "", "", "", reason);
    }

    public boolean isBound() {
        return !geometry.isEmpty() && !texture.isEmpty();
    }

    public String getName() {
        return name;
    }

    public String getGeometry() {
        return geometry;
    }

    public String getTexture() {
        return texture;
    }

    public String getAnimation() {
        return animation;
    }

    public String getNote() {
        return note;
    }
}
