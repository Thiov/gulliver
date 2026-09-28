package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Name tags grow and shrink with their entity (square root of the size,
 * so a tiny's tag stays readable and a titan's doesn't fill the sky).
 * The tag's anchor already moves with the resized hitbox, so the scale
 * pivots on that anchor instead of the entity's feet; hooked on the
 * final method every renderer (players included) goes through.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererNameTag {

    private static float gulliver$tagScale(EntityRenderState state) {
        if (!(state instanceof LivingEntityRenderState ls) || ls.scale == 1.0F || state.nameTagAttachment == null) {
            return 1.0F;
        }
        return Math.max(0.35F, Math.min(3.0F, (float) Math.sqrt(ls.scale)));
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
            at = @At("HEAD"))
    private void gulliver$pushScale(EntityRenderState state, PoseStack pose, SubmitNodeCollector buf,
                                      CameraRenderState cam, int offset, CallbackInfo ci) {
        float s = gulliver$tagScale(state);
        if (s == 1.0F) return;
        Vec3 a = state.nameTagAttachment;
        pose.pushPose();
        pose.translate((float) a.x, (float) a.y, (float) a.z);
        pose.scale(s, s, s);
        pose.translate((float) -a.x, (float) -a.y, (float) -a.z);
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
            at = @At("RETURN"))
    private void gulliver$popScale(EntityRenderState state, PoseStack pose, SubmitNodeCollector buf,
                                     CameraRenderState cam, int offset, CallbackInfo ci) {
        if (gulliver$tagScale(state) != 1.0F) pose.popPose();
    }
}
