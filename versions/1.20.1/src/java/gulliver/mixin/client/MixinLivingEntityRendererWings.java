package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.client.VisualSize;
import gulliver.client.WingRender;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1 / 1.21.1: wings on a player's back (TinyWings). The body leans
 * into its flight in setupRotations, and the wings are drawn after the body
 * in the same leaned space.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRendererWings {

    //#if MC >= 1.20.5
    @Inject(method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("TAIL"), require = 0)
    private void gulliver$leanIntoFlight(LivingEntity entity, PoseStack pose, float bob, float bodyRot,
                                         float partialTick, float scale, CallbackInfo ci) {
    //#else
    //$$ @Inject(method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
    //$$         at = @At("TAIL"), require = 0)
    //$$ private void gulliver$leanIntoFlight(LivingEntity entity, PoseStack pose, float bob, float bodyRot,
    //$$                                      float partialTick, CallbackInfo ci) {
    //#endif
        if (entity instanceof Player p && entity.deathTime <= 0) {
            WingRender.Frame f = WingRender.capture(p, partialTick);
            if (f != null) WingRender.applyLean(pose, f.lean());
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"), require = 0)
    private void gulliver$drawWings(LivingEntity entity, float yaw, float partialTick, PoseStack pose,
                                    MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!(entity instanceof Player p) || entity.isInvisible() || entity.deathTime > 0) return;
        WingRender.Frame f = WingRender.capture(p, partialTick);
        if (f == null) return;
        float scale = VisualSize.renderScale(entity, partialTick);
        float bodyYaw = net.minecraft.util.Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        pose.pushPose();
        pose.scale(scale, scale, scale);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - bodyYaw));
        WingRender.applyLean(pose, f.lean());
        WingRender.build(pose, f, light, (ps, tex, v0, v1, argb, lit) ->
                WingRender.emit(ps.last(), buffers.getBuffer(RenderType.entityTranslucent(tex)), v0, v1, argb, lit));
        pose.popPose();
    }
}
