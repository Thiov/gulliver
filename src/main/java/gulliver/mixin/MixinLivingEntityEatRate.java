package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scale eat/drink/use rate by size — without breaking the eat sound.
 * The previous getUseDuration mixin extended the duration value but
 * Consumable.shouldEmitParticlesAndSounds uses Consumable.consumeTicks
 * (unscaled) for its sound-trigger threshold. Result: tinies took 8×
 * longer but the sound only fired in the last 22% of that time, which
 * felt like "no sound while eating".
 *
 * This mixin instead:
 *  - Tinies: skip updateUsingItem most ticks. With size 0.125, only
 *    one tick in eight actually decrements useItemRemaining and runs
 *    onUseTick. Sound fires at vanilla cadence in eat-tick-time, just
 *    over 8× more wall-time.
 *  - Giants: redirect the `useItemRemaining--` write to decrement by
 *    `size` instead of 1. Eat finishes in 1/size as many wall ticks.
 *    `remainingTicks % 4 == 0` still holds because size is integer-
 *    aligned to power-of-two thresholds in Gulliver (1, 2, 4, 8).
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityEatRate {

    @Shadow protected int useItemRemaining;

    @Inject(method = "updateUsingItem", at = @At("HEAD"), cancellable = true)
    private void gulliver$slowForTiny(ItemStack stack, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        float size = ((IResizeableEntity) self).getSizeMultiplier();
        if (size >= 1.0F) return;
        int factor = Math.max(2, Math.round(1.0F / size));
        // tickCount progresses every tick; modulo gating yields ~1 in
        // `factor` ticks where vanilla actually runs. The other ticks
        // skip (no decrement, no onUseTick → eating animation freezes
        // briefly, sound still fires when we DO run).
        if ((self.tickCount % factor) != 0) {
            ci.cancel();
        }
    }

    @Redirect(method = "updateUsingItem",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/entity/LivingEntity;useItemRemaining:I",
                    opcode = 181 /* Opcodes.PUTFIELD */))
    private void gulliver$fastForGiant(LivingEntity instance, int newValue) {
        float size = ((IResizeableEntity) instance).getSizeMultiplier();
        // After Mixin merges, `this` and `instance` reference the same
        // entity. Write through the @Shadow field so the protected-field
        // access compiles cleanly from outside LivingEntity's package.
        if (size <= 1.0F) {
            this.useItemRemaining = newValue;
            return;
        }
        // newValue = current - 1 (vanilla decrement). Decrement extra
        // (size - 1) more so the total per-tick decrement equals `size`.
        int extra = (int) (size - 1.0F);
        this.useItemRemaining = Math.max(0, newValue - extra);
    }
}
