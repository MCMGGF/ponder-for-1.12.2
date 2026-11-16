package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PreviewItemRendererTest {
    @BeforeAll
    static void initialize() { Bootstrap.register(); }

    @Test
    void drawsTheRealStackAtItsIndependentThreeDimensionalActorTransform() {
        FakeAccess access = new FakeAccess();
        ItemStack source = new ItemStack(Items.IRON_INGOT, 3);
        new PreviewItemRenderer(access).render(source, new Vec3d(2, 3, 4), new Vec3d(10, 20, 30), 0.125F, 0.5F);
        assertEquals(new Vec3d(2, 3.125D, 4), access.position);
        assertEquals(Arrays.asList("20.0:0.0,1.0,0.0", "10.0:1.0,0.0,0.0", "30.0:0.0,0.0,1.0"), access.rotations);
        assertEquals(Items.IRON_INGOT, access.stack.getItem());
        assertEquals(3, access.stack.getCount());
        assertNotSame(source, access.stack);
        assertEquals(0.5F, access.alpha);
        assertTrue(access.restored);
    }

    @Test
    void aNativeRendererCannotMutateTheSceneStackThroughItsDrawCopy() {
        FakeAccess access = new FakeAccess();
        access.mutateStack = true;
        ItemStack source = new ItemStack(Items.DIAMOND, 3);
        new PreviewItemRenderer(access).render(source, Vec3d.ZERO, Vec3d.ZERO, 0F, 1F);
        assertEquals(3, source.getCount());
        assertEquals(1, access.stack.getCount());
    }

    @Test
    void emptyHiddenAndNonfiniteActorsDoNotEnterTheNativeCallback() {
        FakeAccess access = new FakeAccess();
        PreviewItemRenderer renderer = new PreviewItemRenderer(access);
        ItemStack stack = new ItemStack(Items.DIAMOND);
        renderer.render(ItemStack.EMPTY, Vec3d.ZERO, Vec3d.ZERO, 0F, 1F);
        renderer.render(stack, Vec3d.ZERO, Vec3d.ZERO, 0F, 0F);
        renderer.render(stack, Vec3d.ZERO, Vec3d.ZERO, 0F, Float.NaN);
        renderer.render(stack, new Vec3d(Double.NaN, 0, 0), Vec3d.ZERO, 0F, 1F);
        renderer.render(stack, Vec3d.ZERO, Vec3d.ZERO, Float.NaN, 1F);
        assertEquals(0, access.calls);
    }

    @Test
    void opacityIsBoundedBeforeReachingTheNativeState() {
        FakeAccess access = new FakeAccess();
        new PreviewItemRenderer(access).render(new ItemStack(Items.DIAMOND), Vec3d.ZERO, Vec3d.ZERO, 0F, 2F);
        assertEquals(1F, access.alpha);
    }

    @Test
    void runtimeAndLinkageFailuresStillLeaveTheIsolatedCallback() {
        for (Throwable failure : Arrays.asList(new IllegalStateException("item failed"), new NoSuchMethodError("old item"))) {
            FakeAccess access = new FakeAccess();
            access.failure = failure;
            assertSame(failure, assertThrows(failure.getClass(), () -> new PreviewItemRenderer(access)
                .render(new ItemStack(Items.DIAMOND), Vec3d.ZERO, Vec3d.ZERO, 0F, 1F)));
            assertTrue(access.restored);
        }
    }

    @Test
    void productionUsesGroundModelAndCompleteNativeGuardsWithoutRealWorldEntities() throws IOException {
        String item = source("PreviewItemRenderer.java");
        assertTrue(item.contains("PreviewNativeRenderGuard.run(render)")
            || item.contains("PreviewNativeRenderGuard.run(() ->"));
        assertTrue(item.contains("GL11.GL_LIGHTING_BIT | GL11.GL_TEXTURE_BIT"));
        assertTrue(item.contains("ItemCameraTransforms.TransformType.GROUND"));
        assertTrue(item.contains("finally"));
        assertTrue(item.contains("GL11.glPopAttrib()"));
        for (String forbidden : Arrays.asList("new EntityItem", ".getRenderManager(", "renderItemAndEffectIntoGUI",
            "transformPointAtTick", ".spawnEntity(", ".sendPacket(", ".setBlockState(")) {
            assertFalse(item.contains(forbidden), forbidden);
        }
    }

    @Test
    void screenNeverDrawsTheLegacyItemBillboardPrismOrHeading() throws IOException {
        String screen = source("PonderDebugScreen.java");
        int overlayStart = screen.indexOf("private void drawActorOverlay(");
        int overlayEnd = screen.indexOf("private void renderActorPreviews(", overlayStart);
        String overlay = screen.substring(overlayStart, overlayEnd);
        assertTrue(overlay.contains("|| actor.kind == PonderScene.ActorKind.ITEM"));
        assertFalse(overlay.contains("renderOverlayItem("));
        assertFalse(screen.contains("drawItemBody("));
        assertTrue(screen.contains("itemRenderer.render(actor.itemStack, actor.position, actor.rotation"));
        assertTrue(screen.contains("runtimeState.visibleBlocks > 0 || PreviewActorVisibility.anyVisible("));
    }

    private static String source(String name) throws IOException {
        return new String(Files.readAllBytes(Paths.get("src/main/java/net/createmod/ponder/foundation/ui", name)),
            StandardCharsets.UTF_8);
    }

    private static final class FakeAccess implements PreviewItemRenderer.Access {
        final List<String> rotations = new ArrayList<String>();
        Vec3d position;
        ItemStack stack;
        float alpha;
        int calls;
        boolean restored, mutateStack;
        Throwable failure;
        public void isolate(Runnable render) {
            calls++;
            try { render.run(); } finally { restored = true; }
        }
        public void prepare(float alpha) { this.alpha = alpha; }
        public void translate(double x, double y, double z) { position = new Vec3d(x, y, z); }
        public void rotate(float degrees, float x, float y, float z) { rotations.add(degrees + ":" + x + "," + y + "," + z); }
        public void draw(ItemStack stack, float alpha) {
            this.stack = stack;
            assertEquals(this.alpha, alpha);
            if (mutateStack) stack.setCount(1);
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            if (failure instanceof Error) throw (Error) failure;
        }
    }
}
