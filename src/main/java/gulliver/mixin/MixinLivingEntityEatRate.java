package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scale eat/drink/use rate by entity size.
 *
 * Tinies (size < 1): @Inject HEAD on updateUsingItem skips most ticks
 * via tickCount % factor gating. Vanilla decrement and onUseTick run
 * at full vanilla cadence on the ticks we DO run, so the eat sound
 * fires at normal rate (just over more wall-time).
 *
 * Giants (size > 1): cancel vanilla and run our own update with a
 * size-aware decrement. Calling completeUsingItem manually when the
 * counter reaches 0. Previous attempt used @Redirect on the putfield
 * — which left the stack value at vanilla -1, so the in-method
 * `ifne 48` jump skipped completeUsingItem indefinitely (the eat
 * looped forever).
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityEatRate {

    @Shadow protected int useItemRemaining;
    @Shadow protected ItemStack useItem;

    @Shadow protected abstract void completeUsingItem();

    @Inject(method = "updateUsingItem", at = @At("HEAD"), cancellable = true)
    private void gulliver$customUpdate(ItemStack stack, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        float size = ((IResizeableEntity) self).getSizeMultiplier();
        if (size == 1.0F) return;

        if (size < 1.0F) {
            // Tinies: gate by tickCount; let vanilla run only on
            // every Nth tick so the eat takes 1/size as long.
            int factor = Math.max(2, Math.round(1.0F / size));
            if ((self.tickCount % factor) != 0) {
                ci.cancel();
            }
            // else: don't cancel, vanilla updateUsingItem runs normally.
            return;
        }

        // Giants: cancel vanilla, run our own logic with multi-decrement.
        ci.cancel();
        int dec = (int) Math.ceil(size); // decrement per tick

        // Mimic vanilla's onUseTick call so visuals/sounds still fire.
        stack.onUseTick(self.level(), self, useItemRemaining);

        useItemRemaining -= dec;
        if (useItemRemaining <= 0) {
            useItemRemaining = 0;
            if (!self.level().isClientSide() && !stack.useOnRelease()) {
                completeUsingItem();
            }
        }
    }
}
