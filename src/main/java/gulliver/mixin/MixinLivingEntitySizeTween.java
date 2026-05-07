package gulliver.mixin;

import gulliver.access.IGulliverEntityInternal;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smooth resize: each tick, lerp sizeBaseMultiplier toward
 * sizeBaseDestMultiplier so a /doublesize or potion drink grows the
 * entity over ~half a second instead of snapping instantly.
 *
 * Lerp formula: linear, 0.05 per tick of the destination-vs-current
 * delta. That's ~20 ticks (1 second) regardless of step magnitude — the
 * 1.6.4 mod's exact rate isn't visible from the JDCore source but a
 * one-second feel matches the "Alice growing" tone of the source mod.
 *
 * Snap to dest when |diff| < 0.001 to avoid endless tiny lerps. Each
 * tick the tween updates the base, refreshes dimensions, but does NOT
 * re-broadcast — the destination is broadcast once at setBaseSize time
 * and clients run their own lerp using the same formula starting from
 * their own stored base.
 *
 * Runs on both server and client (LivingEntity.tick fires on both).
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntitySizeTween {

    @Inject(method = "tick", at = @At("RETURN"))
    private void gulliver$tweenSize(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        IGulliverEntityInternal access = (IGulliverEntityInternal) self;
        float base = access.gulliver$getSizeBaseMultiplier();
        float dest = access.gulliver$getSizeBaseDestMultiplier();

        if (base != dest) {
            float diff = dest - base;
            float step = diff * 0.15F; // ~6-7 ticks to close 99% of distance
            float next;
            if (Math.abs(diff) < 0.001F) {
                next = dest;
            } else {
                next = base + step;
            }
            access.gulliver$setSizeBaseMultiplier(next);
            self.refreshDimensions();
        }

        // Clamp current HP to (now possibly-shrunken) max HP. Without
        // this, after a /doublesize+heal+/halfsize sequence, currentHP
        // ends up above maxHP — vanilla regen gates on `current < max`,
        // so regen silently stops working until the player loses HP back
        // below the new ceiling. Clamp every tick so the bar stays sane
        // and natural regen resumes immediately after a shrink.
        float maxHp = self.getMaxHealth();
        if (self.getHealth() > maxHp) {
            self.setHealth(maxHp);
        }
        // Same idea for air: getMaxAirSupply scales with size, so after
        // a shrink the current air can be above max. Clamp it.
        int maxAir = self.getMaxAirSupply();
        if (self.getAirSupply() > maxAir) {
            self.setAirSupply(maxAir);
        }
    }
}
