package gulliver.mixin.client;

import gulliver.client.VisualSize;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.20.1: the crouch offset is a fixed 2/16 block (1.21 scales it with the
 * body), which sinks tinies into the floor and floats giants.
 *
 * The score line under a player's name sits a fixed line height below it;
 * the name tags themselves scale with the body (MixinEntityRendererNameTag),
 * so the gap between the two lines does too.
 */
@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerRendererOffset {

    @Inject(method = "getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void gulliver$scaleCrouchOffset(AbstractClientPlayer player, float partialTick,
                                             CallbackInfoReturnable<Vec3> cir) {
        float m = VisualSize.renderScale(player, partialTick);
        if (m != 1.0F) cir.setReturnValue(cir.getReturnValue().scale(m));
    }

    /** The player whose name tag is being drawn (render thread only). */
    @Unique private float gulliver$tagScale = 1.0F;

    //#if MC >= 1.20.5
    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At("HEAD"), require = 0)
    //#else
    //$$ @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
    //$$         at = @At("HEAD"), require = 0)
    //#endif
    private void gulliver$noteTagScale(AbstractClientPlayer player, net.minecraft.network.chat.Component name,
                                       com.mojang.blaze3d.vertex.PoseStack pose,
                                       net.minecraft.client.renderer.MultiBufferSource buffers, int light,
                                       //#if MC >= 1.20.5
                                       float partialTick,
                                       //#endif
                                       CallbackInfo ci) {
        gulliver$tagScale = VisualSize.nameTagScale(VisualSize.of(player));
    }

    //#if MC >= 1.20.5
    @ModifyArg(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
    //#else
    //$$ @ModifyArg(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
    //#endif
               at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"),
               index = 1, require = 0)
    private float gulliver$scaleScoreLine(float y) {
        return y * gulliver$tagScale;
    }
}
