package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scale eat/drink/use duration by entity size. Smaller mouth/throat
 * eats slower; bigger eats faster. Same axis as block-break-speed.
 *
 *   duration' = duration / size
 *
 *   size 0.125 → 8× longer  (tiny chews forever)
 *   size 0.5   → 2× longer
 *   size 1     → vanilla
 *   size 2     → 0.5× (twice as fast)
 *   size 4     → 0.25×
 *   size 8     → 0.125× (giant gulps it down in a few ticks)
 *
 * Vanilla 1.21+ ItemStack.getUseDuration(LivingEntity) is the canonical
 * "how many ticks to use this item" entry. Both startUsingItem and the
 * sync-update path (LivingEntity onSyncedDataUpdated) call into it.
 */
@Mixin(ItemStack.class)
public abstract class MixinItemStackUseDuration {

    @Inject(method = "getUseDuration", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleByEntitySize(LivingEntity entity,
                                              CallbackInfoReturnable<Integer> cir) {
        int base = cir.getReturnValueI();
        if (base <= 0) return;
        float size = ((IResizeableEntity) entity).getSizeMultiplier();
        if (size == 1.0F) return;
        // Cap at 1 tick minimum so giants still pause briefly.
        int scaled = Math.max(1, (int) (base / size));
        cir.setReturnValue(scaled);
    }
}
