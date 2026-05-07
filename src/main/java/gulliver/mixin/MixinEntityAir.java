package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scale max air supply with size — giants hold breath longer (bigger
 * lungs), tinies drown faster. Linear in size:
 *
 *   maxAir' = vanilla * size
 *
 * Vanilla baseline 300 ticks (15 sec):
 *   size 0.125 → 37 ticks (1.85 sec)   tinies suffocate fast
 *   size 0.5   → 150 ticks (7.5 sec)
 *   size 2     → 600 ticks (30 sec)
 *   size 4     → 1200 ticks (60 sec)
 *   size 8     → 2400 ticks (2 min)    giants are practically aquatic
 *
 * Note: cap at minimum 20 ticks (1 sec) so extra-tinies aren't instant-
 * dead the moment they touch water.
 */
@Mixin(Entity.class)
public abstract class MixinEntityAir {

    @Inject(method = "getMaxAirSupply", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleMaxAir(CallbackInfoReturnable<Integer> cir) {
        float size = ((IResizeableEntity) this).getSizeMultiplier();
        if (size == 1.0F) return;
        int scaled = (int) (cir.getReturnValueI() * size);
        if (scaled < 20) scaled = 20;
        cir.setReturnValue(scaled);
    }
}
