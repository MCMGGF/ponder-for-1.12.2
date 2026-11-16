package net.createmod.ponder.foundation.ui;

import java.util.List;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.ActorEvent;
import net.createmod.ponder.foundation.PonderScene.ActorEventType;
import net.createmod.ponder.foundation.PonderScene.ActorKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelMinecart;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

/** Vanilla models with a detached, unticked pose carrier; no RenderPlayer or world entity registration. */
final class PreviewPlayerRenderer {
    private static final ResourceLocation CART_TEXTURE = new ResourceLocation("minecraft", "textures/entity/minecart.png");
    // A bounded client-wide cache avoids leaking new vanilla display lists on every screen open.
    private static ModelPlayer standardModel;
    private static ModelPlayer slimModel;
    private static ModelMinecart cartModel;
    private EntityPlayer carrier;

    void renderPlayers(PonderScene scene, float tick, PreviewPlayerSkin.Mode mode) {
        List<PreviewPlayerTimeline.State> players = PreviewPlayerTimeline.evaluate(scene.getActorEvents(), tick);
        if (players.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        PreviewPlayerSkin skin = PreviewPlayerSkin.resolve(minecraft, mode);
        for (PreviewPlayerTimeline.State state : players) {
            if (state.fade > 0.0F) {
                renderPlayer(minecraft, skin, state, tick);
            }
        }
    }

    private void renderPlayer(Minecraft minecraft, PreviewPlayerSkin skin, PreviewPlayerTimeline.State state, float tick) {
        if (carrier == null) {
            carrier = new DetachedPonderWorld().createPlayer();
        }
        if (standardModel == null) {
            standardModel = new ModelPlayer(0.0F, false);
            slimModel = new ModelPlayer(0.0F, true);
        }
        ModelPlayer model = skin.slim ? slimModel : standardModel;
        carrier.ticksExisted = (int) tick;
        carrier.setSneaking(state.sneaking);
        carrier.inventory.currentItem = 0;
        carrier.inventory.mainInventory.set(0, state.heldItem.copy());
        carrier.rotationYaw = carrier.prevRotationYaw = state.yaw;
        carrier.rotationPitch = carrier.prevRotationPitch = state.pitch;
        carrier.renderYawOffset = carrier.prevRenderYawOffset = state.yaw;
        carrier.rotationYawHead = carrier.prevRotationYawHead = state.yaw;
        carrier.swingProgress = carrier.prevSwingProgress = state.swingProgress;
        model.setVisible(true);
        model.isChild = false;
        model.isRiding = false;
        model.isSneak = state.sneaking;
        model.swingProgress = state.swingProgress;
        model.leftArmPose = ModelBiped.ArmPose.EMPTY;
        model.rightArmPose = state.heldItem.isEmpty() ? ModelBiped.ArmPose.EMPTY : ModelBiped.ArmPose.ITEM;

        try {
            PreviewRenderState.runIsolated(() -> PreviewClientArrayGuard.run(() -> {
            try (PreviewModelViewGuard matrix = new PreviewModelViewGuard()) {
                prepare(state.fade);
                minecraft.getTextureManager().bindTexture(skin.texture);
                GlStateManager.translate(state.position.x, state.position.y, state.position.z);
                GlStateManager.rotate(180.0F - state.yaw, 0.0F, 1.0F, 0.0F);
                GlStateManager.scale(-0.9375F, -0.9375F, 0.9375F);
                GlStateManager.translate(0.0F, -1.501F, 0.0F);
                model.render(carrier, state.limbSwing, state.limbSwingAmount, tick, 0.0F, state.pitch, 0.0625F);
                if (!state.heldItem.isEmpty()) {
                    renderHeldItem(minecraft, model, state.heldItem, state.sneaking);
                }
            }
            }));
        } finally {
            carrier.inventory.mainInventory.set(0, ItemStack.EMPTY);
        }
    }

    private void renderHeldItem(Minecraft minecraft, ModelPlayer model, ItemStack stack, boolean sneaking) {
        try (PreviewModelViewGuard projection = new PreviewModelViewGuard(GL11.GL_PROJECTION);
            PreviewModelViewGuard matrix = new PreviewModelViewGuard()) {
            if (sneaking) {
                GlStateManager.translate(0.0F, 0.2F, 0.0F);
            }
            model.postRenderArm(0.0625F, EnumHandSide.RIGHT);
            GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(0.0625F, 0.125F, -0.625F);
            GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_VIEWPORT_BIT);
            try {
                minecraft.getItemRenderer().renderItemSide(carrier, stack,
                    ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND, false);
            } finally {
                GL11.glPopAttrib();
            }
        }
    }

    void renderCart(Vec3d position, float yaw, float pitch, float fade) {
        if (fade <= 0.0F) {
            return;
        }
        if (cartModel == null) {
            cartModel = new ModelMinecart();
        }
        PreviewRenderState.runIsolated(() -> PreviewClientArrayGuard.run(() -> {
            try (PreviewModelViewGuard matrix = new PreviewModelViewGuard()) {
                prepare(fade);
                Minecraft.getMinecraft().getTextureManager().bindTexture(CART_TEXTURE);
                GlStateManager.translate(position.x, position.y + 0.375D, position.z);
                GlStateManager.rotate(180.0F - yaw, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(-pitch, 0.0F, 0.0F, 1.0F);
                GlStateManager.scale(-1.0F, -1.0F, 1.0F);
                // ModelMinecart does not inspect an entity; supplying none avoids any entity lifecycle.
                cartModel.render(null, 0.0F, 0.0F, -0.1F, 0.0F, 0.0F, 0.0625F);
            }
        }));
    }

    static float cartPitch(List<ActorEvent> events, int actorId, float tick) {
        Vec3d velocity = Vec3d.ZERO;
        float yaw = 0.0F;
        float authoredPitch = 0.0F;
        for (ActorEvent event : events) {
            if (event.getActorKind() != ActorKind.CART || event.getActorId() != actorId || event.getTick() > tick) {
                continue;
            }
            if (event.getType() == ActorEventType.SPAWN) {
                velocity = Vec3d.ZERO;
                yaw = event.getAngle();
                authoredPitch = 0;
            } else if (event.getType() == ActorEventType.ROTATE) {
                yaw += event.getAngle() * PreviewPlayerTimeline.progress(event, tick);
                if (event.getRotation() != null) {
                    yaw += (float)event.getRotation().y * PreviewPlayerTimeline.progress(event, tick);
                    authoredPitch += (float)event.getRotation().x * PreviewPlayerTimeline.progress(event, tick);
                }
            } else if (event.getType() == ActorEventType.NBT_PATCH && event.getActorNbt().hasKey("Rotation", 9)) {
                yaw = event.getActorNbt().getTagList("Rotation", 5).getFloatAt(0);
                authoredPitch = event.getActorNbt().getTagList("Rotation", 5).getFloatAt(1);
            } else if (event.getType() == ActorEventType.MOVE && event.getOffset() != null
                && PreviewPlayerTimeline.progress(event, tick) < 1.0F) {
                velocity = velocity.add(event.getOffset().scale(1.0D / Math.max(1, event.getDuration())));
            }
        }
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        double alongAxis = velocity.x * Math.cos(Math.toRadians(yaw)) + velocity.z * Math.sin(Math.toRadians(yaw));
        return authoredPitch + (horizontal <= 0.000001D ? 0.0F
            : (float) Math.toDegrees(Math.atan2(velocity.y, horizontal)) * (alongAxis < 0.0D ? -1.0F : 1.0F));
    }

    private static void prepare(float fade) {
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.01F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, MathHelper.clamp(fade, 0.0F, 1.0F));
    }

    void clear() {
        if (carrier != null) {
            carrier.inventory.clear();
        }
        carrier = null;
        // Models contain no world/player references and are reused across screen instances.
    }
}
