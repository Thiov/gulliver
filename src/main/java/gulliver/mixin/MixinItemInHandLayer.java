package gulliver.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.access.IGlideRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.6.4 bfj.java:77 — held items rendered with sizerootdiv = 1/sqrt(size)
 * inside the player's already-size-scaled GL state. Net item scale =
 * size * (1/sqrt(size)) = sqrt(size).
 *
 * MC 26.1.2 ItemInHandLayer renders via `submit(PoseStack, SubmitNodeCollector,
 * int, ArmedEntityRenderState, float, float)` (deferred render). NOT
 * `render(...)`. The previous mixin used the wrong method name and never
 * fired — this is the corrected wiring.
 */
@Mixin(ItemInHandLayer.class)
public abstract class MixinItemInHandLayer {

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V",
            at = @At("HEAD"))
    private void gulliver$pushScale(PoseStack pose, SubmitNodeCollector buf, int light,
                                     ArmedEntityRenderState state, float yRot, float xRot,
                                     CallbackInfo ci) {
        if (!(state instanceof IGlideRenderState g)) return;
        float size = g.gulliver$getSizeMultiplier();
        if (size == 1.0F && !g.gulliver$isGliding()) return;
        pose.pushPose();
        if (size != 1.0F) {
            float invRoot = 1.0F / (float) Math.sqrt(size);
            pose.scale(invRoot, invRoot, invRoot);
        }
        // Centering: when gliding (paper held overhead like a parachute),
        // the held item is bone-attached to the right arm so it appears
        // off to the right of the head. The 1.6.4 visual was paper centered
        // above and between both raised hands. Translate the item -X by
        // half a shoulder width (in arm-local space, X+ is outward from
        // body for the right arm) to bring it back to center.
        if (g.gulliver$isGliding()) {
            pose.translate(0.3125F, 0.0F, 0.0F);
        }
    }

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V",
            at = @At("RETURN"))
    private void gulliver$popScale(PoseStack pose, SubmitNodeCollector buf, int light,
                                    ArmedEntityRenderState state, float yRot, float xRot,
                                    CallbackInfo ci) {
        if (!(state instanceof IGlideRenderState g)) return;
        if (g.gulliver$getSizeMultiplier() == 1.0F && !g.gulliver$isGliding()) return;
        pose.popPose();
    }
}
