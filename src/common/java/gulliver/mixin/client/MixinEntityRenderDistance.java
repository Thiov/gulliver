package gulliver.mixin.client;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tiny creatures stay drawn as far away as their normal-size selves (#26).
 *
 * Vanilla stops drawing an entity beyond 64 times the average side of its
 * hitbox (times the Entity Distance setting), so the smaller the box, the
 * sooner it goes: a 1/4-size player vanished at 16 blocks, a 1/8-size one at
 * 8 and a 1/8-size chicken at 4, while it was still plainly in view. For
 * anything smaller than normal the distance is measured with the hitbox it
 * has at size 1. Giants keep vanilla's rule, which already lets them be seen
 * from farther away.
 */
@Mixin(Entity.class)
public abstract class MixinEntityRenderDistance {

    @Inject(method = "shouldRenderAtSqrDistance", at = @At("HEAD"), cancellable = true, require = 0)
    private void gulliver$tinyStayVisible(double distanceSqr, CallbackInfoReturnable<Boolean> cir) {
        float size = ((IResizeableEntity) this).getSizeMultiplier();
        if (!(size > 0.0F) || size >= 1.0F || gulliver.client.ClientShowcaseWings.vanillaCulling) return;
        double side = ((Entity) (Object) this).getBoundingBox().getSize() / size;
        if (Double.isNaN(side)) side = 1.0D;
        side *= 64.0D * Entity.getViewScale();
        cir.setReturnValue(distanceSqr < side * side);
    }
}
