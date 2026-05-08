package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.EatContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two responsibilities on ItemStack:
 *
 *  1. Scale getUseDuration(LivingEntity) by 1/size so eating/drinking
 *     takes longer for tinies and shorter for giants. Vanilla then
 *     decrements useItemRemaining by 1 per tick — animation
 *     interpolates smoothly over the extended duration.
 *
 *  2. Capture the eating entity into EatContext for the duration of
 *     onUseTick. MixinConsumableShouldEmit reads that context to scale
 *     the sound-trigger threshold to match the entity-scaled duration.
 *     Without this, scaling getUseDuration alone left tiny eating
 *     silent until the last 22% (Consumable.shouldEmitParticlesAndSounds
 *     uses unscaled consumeTicks() for its threshold).
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
        cir.setReturnValue(Math.max(1, Math.round(base / size)));
    }

    @Inject(method = "onUseTick", at = @At("HEAD"))
    private void gulliver$captureEater(Level level, LivingEntity entity, int remaining,
                                         CallbackInfo ci) {
        EatContext.set(entity);
    }

    @Inject(method = "onUseTick", at = @At("RETURN"))
    private void gulliver$clearEater(Level level, LivingEntity entity, int remaining,
                                       CallbackInfo ci) {
        EatContext.clear();
    }
}
