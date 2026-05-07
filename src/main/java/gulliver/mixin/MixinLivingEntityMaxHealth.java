package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scale max HP linearly with size — tinies have less HP, giants have
 * more. 1.6.4 didn't scale max HP directly (it scaled received damage
 * via /sizeroot in damageEntity); we go further per the user's request:
 * actually move the HP bar around so the body's HP visibly reflects size.
 *
 *   maxHP_final = maxHP_base * sizeMultiplier
 *
 * Examples (vanilla baseline 20 hp):
 *   size 0.125 → 2.5 hp     (tinies die in 1 strong hit)
 *   size 0.5   → 10 hp
 *   size 2     → 40 hp
 *   size 4     → 80 hp
 *   size 8     → 160 hp     (giants are bullet sponges)
 *
 * This combines with MixinLivingEntityDamage's /size target-side scaling:
 * tiny has less HP AND takes more damage, so they're very fragile.
 * Giants have more HP AND take less damage, so they're tough.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityMaxHealth {

    @Inject(method = "getMaxHealth", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleMaxHealth(CallbackInfoReturnable<Float> cir) {
        float size = ((IResizeableEntity) this).getSizeMultiplier();
        if (size == 1.0F) return;
        cir.setReturnValue(cir.getReturnValueF() * size);
    }
}
