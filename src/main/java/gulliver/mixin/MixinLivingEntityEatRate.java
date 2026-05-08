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
 * Scale eat/drink/use rate by entity size — without breaking the
 * eating animation or sound.
 *
 * Both branches run their OWN replacement of updateUsingItem so we
 * have full control over what happens each tick.
 *
 * Tinies (size < 1): every tick fires onUseTick (animation + sound
 * continue smoothly), but the decrement only happens every Nth tick
 * so the total wall-time is ~1/size as long. Previous "skip the whole
 * tick" approach made the animation freeze for factor-1 ticks at a
 * time — felt jittery / laggy.
 *
 * Giants (size > 1): every tick fires onUseTick, decrement is
 * ceil(size) per tick so the eat completes in roughly 1/size ticks.
 *
 * Vanilla size 1.0: pass through, no override.
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
        if (size == 1.0F) return; // vanilla — no override

        ci.cancel();

        // onUseTick fires every tick regardless of size — animation and
        // particle/sound emission stay continuous. We're only changing
        // the rate at which useItemRemaining counts down.
        stack.onUseTick(self.level(), self, useItemRemaining);

        int dec;
        if (size < 1.0F) {
            // Tiny: decrement only on every Nth tick. tickCount mod gate
            // keeps the animation smooth (onUseTick still runs every
            // tick) while extending the total eat time to ~1/size as
            // long as vanilla.
            int factor = Math.max(2, Math.round(1.0F / size));
            dec = (self.tickCount % factor == 0) ? 1 : 0;
        } else {
            // Giant: multi-decrement per tick.
            dec = (int) Math.ceil(size);
        }

        if (dec > 0) {
            useItemRemaining -= dec;
            if (useItemRemaining <= 0) {
                useItemRemaining = 0;
                if (!self.level().isClientSide() && !stack.useOnRelease()) {
                    completeUsingItem();
                }
            }
        }
    }
}
