package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.EatContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.Consumable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scale the sound-trigger threshold inside Consumable.shouldEmit
 * by the eating entity's size. Without this, scaling getUseDuration
 * (so tinies eat 8x slower) leaves the unscaled `consumeTicks() *
 * 0.21875` threshold check firing only in the last 22% of the eat —
 * tinies hear silence until the very end.
 *
 * Vanilla logic:
 *   ticksUsed = consumeTicks() - remainingTicks
 *   threshold = consumeTicks() * 0.21875
 *   return ticksUsed > threshold && remainingTicks % 4 == 0
 *
 * Our logic, with size from EatContext (set by MixinItemStackUseDuration
 * around onUseTick):
 *   scaled = consumeTicks() / size
 *   ticksUsed = scaled - remainingTicks
 *   threshold = scaled * 0.21875
 *   ... rest unchanged.
 *
 * Sound now fires every 4 ticks past 22% of the SCALED duration —
 * tinies hear continuous chewing, giants hear a quick gulp.
 */
@Mixin(Consumable.class)
public abstract class MixinConsumableShouldEmit {

    @Inject(method = "shouldEmitParticlesAndSounds", at = @At("HEAD"), cancellable = true)
    private void gulliver$scaleThreshold(int remainingTicks,
                                           CallbackInfoReturnable<Boolean> cir) {
        LivingEntity eater = EatContext.get();
        if (eater == null) return;
        float size = ((IResizeableEntity) eater).getSizeMultiplier();
        if (size == 1.0F) return;

        Consumable self = (Consumable) (Object) this;
        int scaledConsume = Math.max(1, Math.round(self.consumeTicks() / size));
        int ticksUsed = scaledConsume - remainingTicks;
        int threshold = (int) (scaledConsume * 0.21875F);
        boolean fire = ticksUsed > threshold && (remainingTicks % 4) == 0;
        cir.setReturnValue(fire);
    }
}
