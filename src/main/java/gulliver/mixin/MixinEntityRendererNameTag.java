package gulliver.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scale the floating name tag along with the entity's body. Vanilla
 * submitNameDisplay positions the tag at a fixed height above the
 * entity's feet (eye + 0.5) using a constant {@link
 * EntityRenderer#NAMETAG_SCALE} 0.025 — neither the offset nor the
 * font size respond to the model scale we set in
 * MixinLivingEntityRenderer.extractRenderState (state.scale).
 *
 * Result without this mixin: a size-0.125 tiny has its name tag float
 * 8x its body height above its head; a size-8 giant has the tag stuck
 * at human-eye height somewhere mid-torso.
 *
 * Fix: at HEAD push the pose-stack and scale uniformly by state.scale
 * (which has already been multiplied by the entity's sizeMultiplier in
 * extractRenderState), so vanilla's translate-up + glyph render run in
 * a scaled coordinate space — the offset rises/falls with the body and
 * the text size matches. Pop at RETURN.
 *
 * The non-LivingEntityRenderState branch (items, projectiles, etc.) is
 * a no-op; their renderers don't fill in state.scale and rarely show
 * name tags anyway.
 *
 * Both 4-arg overloads share the same descriptor up to arity, but
 * Mixin can target by name + descriptor — only the public 4-arg
 * (pre-pack-light) overload is the override point. The 5-arg final
 * variant calls into it and inherits the scale.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererNameTag {

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"),
            require = 0)
    private void gulliver$pushScale(EntityRenderState state, PoseStack pose,
                                      SubmitNodeCollector buf, CameraRenderState cam,
                                      CallbackInfo ci) {
        if (!(state instanceof LivingEntityRenderState ls)) return;
        float s = ls.scale;
        if (s == 1.0F) return;
        pose.pushPose();
        pose.scale(s, s, s);
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"),
            require = 0)
    private void gulliver$popScale(EntityRenderState state, PoseStack pose,
                                     SubmitNodeCollector buf, CameraRenderState cam,
                                     CallbackInfo ci) {
        if (!(state instanceof LivingEntityRenderState ls)) return;
        if (ls.scale == 1.0F) return;
        pose.popPose();
    }
}
