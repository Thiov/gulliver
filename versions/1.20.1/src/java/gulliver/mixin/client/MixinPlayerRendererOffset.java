package gulliver.mixin.client;

import gulliver.client.VisualSize;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.20.1: the crouch offset is a fixed 2/16 block (1.21 scales it with the
 * body), which sinks tinies into the floor and floats giants.
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
}
