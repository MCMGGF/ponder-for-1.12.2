package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.opengl.GL11;

/** Draw after scene transparency, before leaving its model matrices and scissor. */
final class PreviewOutlineRenderer {

    private final PreviewOutlineGeometry geometry = new PreviewOutlineGeometry();
    private BufferBuilder lineBuffer;
    private WorldVertexBufferUploader lineUploader;

    void render(PonderScene scene, PonderSceneRuntime.RuntimeState state, float tick, IBlockAccess access) {
        render(scene, state, tick, access, true);
    }

    void render(PonderScene scene, PonderSceneRuntime.RuntimeState state, float tick, IBlockAccess access,
        boolean showOccluded) {
        List<PreviewOutlineGeometry.Outline> outlines = geometry.frame(scene, state, tick, access);
        if (!outlines.isEmpty()) {
            render(new NativeAccess(), outlines, showOccluded);
        }
    }

    PreviewOutlineGeometry geometry() { return geometry; }

    void renderSelection(BlockPos position, PonderSceneRuntime.RuntimeState state, IBlockAccess access) {
        List<PreviewOutlineGeometry.Outline> outlines = PreviewOutlineGeometry.selection(position, state, access);
        if (!outlines.isEmpty()) render(new NativeAccess(), outlines, true);
    }

    void clear() {
        geometry.clear();
        lineBuffer = null;
        lineUploader = null;
    }

    static void render(Access access, List<PreviewOutlineGeometry.Outline> outlines) {
        render(access, outlines, true);
    }

    static void render(Access access, List<PreviewOutlineGeometry.Outline> outlines, boolean showOccluded) {
        Object previous = access.save();
        try {
            access.configure();
            if (showOccluded) access.draw(outlines, GL11.GL_GREATER, 0.2F);
            access.draw(outlines, GL11.GL_LEQUAL, 0.95F);
        } finally {
            access.restore(previous);
        }
    }

    interface Access {
        Object save();
        void configure();
        void draw(List<PreviewOutlineGeometry.Outline> outlines, int depthFunction, float alpha);
        void restore(Object previous);
    }

    private final class NativeAccess implements Access {
        private float previousWidth;

        public Object save() {
            previousWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
            return new PreviewRenderState();
        }

        public void configure() {
            GlStateManager.enableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }

        public void draw(List<PreviewOutlineGeometry.Outline> outlines, int depthFunction, float alpha) {
            GlStateManager.depthFunc(depthFunction);
            Map<Float, List<PreviewOutlineGeometry.Outline>> batches =
                new LinkedHashMap<Float, List<PreviewOutlineGeometry.Outline>>();
            for (PreviewOutlineGeometry.Outline outline : outlines) {
                float width = outline.wide ? 3F : 2F;
                batches.computeIfAbsent(width, ignored -> new ArrayList<PreviewOutlineGeometry.Outline>()).add(outline);
            }
            for (Map.Entry<Float, List<PreviewOutlineGeometry.Outline>> batch : batches.entrySet()) {
                GL11.glLineWidth(batch.getKey());
                if (lineBuffer == null) {
                    lineBuffer = new BufferBuilder(4096);
                }
                if (lineUploader == null) {
                    lineUploader = new WorldVertexBufferUploader();
                }
                BufferBuilder buffer = lineBuffer;
                boolean complete = false;
                try {
                    buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
                    for (PreviewOutlineGeometry.Outline outline : batch.getValue()) {
                        int color = outline.color;
                        float red = (color >> 16 & 255) / 255F;
                        float green = (color >> 8 & 255) / 255F;
                        float blue = (color & 255) / 255F;
                        for (PreviewOutlineGeometry.Edge edge : outline.edges) {
                            float opacity = Math.max(0F, Math.min(1F, edge.alpha * outline.alpha * alpha));
                            vertex(buffer, edge.start, red, green, blue, opacity);
                            vertex(buffer, edge.end, red, green, blue, opacity);
                        }
                    }
                    buffer.finishDrawing();
                    PreviewClientArrayGuard.run(() -> lineUploader.draw(buffer));
                    complete = true;
                } finally {
                    if (complete) {
                        buffer.reset();
                        buffer.setTranslation(0D, 0D, 0D);
                    } else {
                        lineBuffer = null;
                        PreviewBlockBuffer.discard(buffer);
                    }
                }
            }
        }

        private void vertex(BufferBuilder buffer, Vec3d point, float red, float green, float blue, float alpha) {
            buffer.pos(point.x, point.y, point.z).color(red, green, blue, alpha).endVertex();
        }

        public void restore(Object previous) {
            try {
                GL11.glLineWidth(previousWidth);
            } finally {
                ((PreviewRenderState) previous).restore();
            }
        }
    }
}
