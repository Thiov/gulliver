package gulliver.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.access.IGlideRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.6.4 bfj.java:77 — held items were rendered with an inverse-sqrt
 * scale: `float sizerootdiv = 1.0F / par1EntityLivingBase.getSizeMultiplierRoot();`
 * applied via GL11.glScalef BEFORE the item draw, INSIDE the player's
 * already-size-scaled coordinate space. Net effect: held item appears
 * at sqrt(size) absolute scale, i.e. larger relative to the player when
 * the player is small (since sqrt(0.125)/0.125 = 2.83x), smaller relative
 * to the player when the player is huge (sqrt(8)/8 = 0.354x).
 *
 * Modern MC 26.x: state.scale = sizeMultiplier is applied by
 * LivingEntityRenderer.render at the parent pose-stack level, so
 * everything inside ItemInHandLayer.render inherits that scale. We push
 * a fresh pose, scale by 1/sqrt(size) — net item scale = size *
 * (1/sqrt(size)) = sqrt(size). Same outcome as 1.6.4.
 *
 * Pop on RETURN keeps the parent pose-stack balanced.
 */
@Mixin(ItemInHandLayer.class)
public abstract class MixinItemInHandLayer {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V",
            at = @At("HEAD"),
            require = 0)
    private void gulliver$pushScale(PoseStack pose, MultiBufferSource buf, int light,
                                     net.minecraft.client.renderer.entity.state.EntityRenderState state,
                                     float yRot, float xRot, CallbackInfo ci) {
        if (!(state instanceof IGlideRenderState g)) return;
        float size = g.gulliver$getSizeMultiplier();
        if (size == 1.0F) return;
        float invRoot = 1.0F / (float) Math.sqrt(size);
        pose.pushPose();
        pose.scale(invRoot, invRoot, invRoot);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V",
            at = @At("RETURN"),
            require = 0)
    private void gulliver$popScale(PoseStack pose, MultiBufferSource buf, int light,
                                    net.minecraft.client.renderer.entity.state.EntityRenderState state,
                                    float yRot, float xRot, CallbackInfo ci) {
        if (!(state instanceof IGlideRenderState g)) return;
        if (g.gulliver$getSizeMultiplier() == 1.0F) return;
        pose.popPose();
    }
}
