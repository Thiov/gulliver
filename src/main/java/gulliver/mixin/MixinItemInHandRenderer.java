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
        // Override: render paper flat overhead, FIXED context (no
        // upright display transform). Camera space: +X right, +Y up,
        // -Z forward into scene. Position above eye and slightly
        // forward — placing it where you'd hold a parachute over your
        // head looking up.
        pose.pushPose();
        pose.last().pose().identity();
        pose.last().normal().identity();
        // Closer to eye (-0.3 Z) and lower (0.2 Y) so visible at edge
        // of view. Centered horizontally (X = -0.5 to span centered
        // since FIXED renders item with its center near origin).
        pose.translate(-0.5F, 0.2F, -0.3F);
        // 90° around X tips the paper from item-natural orientation
        // (vertical) to flat-facing-down (parallel to ground above
        // player's head).
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));
        pose.scale(1.0F, 1.0F, 1.0F);

        net.minecraft.client.renderer.item.ItemStackRenderState rs =
                new net.minecraft.client.renderer.item.ItemStackRenderState();
        net.minecraft.client.Minecraft.getInstance().getItemModelResolver()
                .updateForTopItem(rs, stack, ItemDisplayContext.FIXED,
                        entity.level(), entity instanceof net.minecraft.world.entity.LivingEntity le ? le : null, 0);
        rs.submit(pose, buf, light,
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
