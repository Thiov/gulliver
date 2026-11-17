package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.access.IGlideRenderState;
import gulliver.client.WingRender;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC >= 1.21.11
import net.minecraft.client.renderer.rendertype.RenderTypes;
//#else
//$$ import net.minecraft.client.renderer.RenderType;
//#endif

/**
 * Wings on a player's back (TinyWings): taken from the player with the rest
 * of its render state, the body leaned into its flight, and the wings drawn
 * after the body in the same leaned space. (1.20.1 and 1.21.1 have their own
 * copy of this mixin for their render path.)
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRendererWings {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("RETURN"), require = 0)
    private void gulliver$captureWings(LivingEntity entity, LivingEntityRenderState state, float partialTick,
                                       CallbackInfo ci) {
        if (state instanceof IGlideRenderState g) {
            g.gulliver$setWings(entity instanceof Player p ? WingRender.capture(p, partialTick) : null);
        }
    }

    @Inject(method = "setupRotations", at = @At("TAIL"), require = 0)
    private void gulliver$leanIntoFlight(LivingEntityRenderState state, PoseStack pose, float bodyRot, float scale,
                                         CallbackInfo ci) {
        if (state instanceof IGlideRenderState g && g.gulliver$wings() instanceof WingRender.Frame f
                && state.deathTime <= 0.0F) {
            WingRender.applyLean(pose, f.lean());
        }
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"), require = 0)
    private void gulliver$drawWings(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector buf,
                                    net.minecraft.client.renderer.state.level.CameraRenderState cam, CallbackInfo ci) {
        if (!(state instanceof IGlideRenderState g) || !(g.gulliver$wings() instanceof WingRender.Frame f)) return;
        if (state.isInvisible || state.deathTime > 0.0F) return;
        pose.pushPose();
        pose.scale(state.scale, state.scale, state.scale);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - state.bodyRot));
        WingRender.applyLean(pose, f.lean());
        WingRender.build(pose, f, state.lightCoords, (p, tex, v0, v1, argb, light) ->
                buf.submitCustomGeometry(p,
                        //#if MC >= 1.21.11
                        RenderTypes.entityTranslucent(tex),
                        //#else
                        //$$ RenderType.entityTranslucent(tex),
                        //#endif
                        (pp, vc) -> WingRender.emit(pp, vc, v0, v1, argb, light)));
        pose.popPose();
    }
}
