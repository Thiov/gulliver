package gulliver.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.api.IResizeableLiving;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person held-item path. ItemInHandRenderer.renderItem is called
 * after the camera-attached arm pose has been set up. We hook RETURN of
 * renderItem and apply scale/glide-pose modifications BEFORE the actual
 * draw — actually wait: we hook HEAD because renderItem itself calls
 * the item-model submission. Easier path: hook HEAD, push pose, scale
 * and glide-pose translate, the inner submission then runs scaled.
 *
 * For 1st-person glide we don't try to mimic the 1.6.4 GL11 rotations
 * (those were authored against a pre-rotation OpenGL state that doesn't
 * map cleanly to PoseStack pre-multiply order). Instead we translate
 * the item to a fixed overhead-and-forward position (above and slightly
 * in front of the camera origin), which visually places the paper as
 * if held above the player's head — same effective look as the original.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer {

    /**
     * 1st-person glide paper rendering. Cancels vanilla renderItem and
     * submits the item ourselves with ItemDisplayContext.FIXED — that
     * context lays the item flat (item-frame transform), avoiding the
     * upright FIRST_PERSON_RIGHT_HAND display rotation that was making
     * the paper "stand up to the right" in 1st person.
     */
    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"), cancellable = true)
    private void gulliver$replaceFirstPersonGlide(LivingEntity entity, ItemStack stack,
                                                    ItemDisplayContext ctx, PoseStack pose,
                                                    SubmitNodeCollector buf, int light, CallbackInfo ci) {
        if (!ctx.firstPerson()) return;
        IResizeableLiving sized = (IResizeableLiving) entity;
        boolean gliding = sized.isGliding();
        boolean umbrella = sized.doesUmbrella();
        if (!gliding && !umbrella) {
            // Non-glide first-person: just apply size scale at HEAD,
            // pop on RETURN. Vanilla rendering proceeds unchanged.
            float size = sized.getSizeMultiplier();
            if (size == 1.0F) return;
            pose.pushPose();
            float invRoot = 1.0F / (float) Math.sqrt(size);
            pose.scale(invRoot, invRoot, invRoot);
            return;
        }
        // 1st person paper: use NATURAL pose-stack (no identity reset
        // — that put us at world origin and broke positioning). Apply
        // the conjugate of camera.rotation() to undo the camera's
        // rotation -> local +Y becomes world-up. Re-apply body yaw so
        // paper rotates with body. Translate up. Lay flat.
        net.minecraft.client.Camera cam = net.minecraft.client.Minecraft.getInstance()
                .gameRenderer.getMainCamera();
        pose.pushPose();
        // Undo camera rotation -> world-axis frame at the current
        // pose origin (which is approximately camera position).
        pose.mulPose(new org.joml.Quaternionf(cam.rotation()).conjugate());
        // Now move up in WORLD up direction (since +Y is world-up).
        pose.translate(0.0F, 0.5F, 0.0F);
        // Re-apply body yaw so paper rotates with body when player turns.
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - entity.yBodyRot));
        // Lay flat
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));
        // Recenter (corner -> center after X-rot).
        pose.translate(-0.5F, 0.0F, 0.5F);
        // Render with NONE context (no display transform offset).
        net.minecraft.client.renderer.item.ItemStackRenderState rs =
                new net.minecraft.client.renderer.item.ItemStackRenderState();
        net.minecraft.client.Minecraft.getInstance().getItemModelResolver()
                .updateForTopItem(rs, stack, ItemDisplayContext.NONE,
                        entity.level(), entity, 0);
        rs.submit(pose, buf, 15728880,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
        ci.cancel();
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("RETURN"))
    private void gulliver$popFirstPerson(LivingEntity entity, ItemStack stack,
                                          ItemDisplayContext ctx, PoseStack pose,
                                          SubmitNodeCollector buf, int light, CallbackInfo ci) {
        if (!ctx.firstPerson()) return;
        IResizeableLiving sized = (IResizeableLiving) entity;
        if (sized.isGliding() || sized.doesUmbrella()) return; // already cancelled
        float size = sized.getSizeMultiplier();
        if (size == 1.0F) return;
        pose.popPose();
    }
}
