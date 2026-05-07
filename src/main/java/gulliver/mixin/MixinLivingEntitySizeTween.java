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

        // Proportional HP/air scaling on resize:
        //   newCurrent = oldCurrent * newMax / oldMax
        // Keeps the % full constant when size (and therefore maxHealth /
        // maxAirSupply) changes. Without this, growing then shrinking
        // either left HP above the new max (broke regen — gate is
        // `current < max`) or, if clamped to max, dropped HP in big
        // chunks (looked like taking damage with screen-shake / hurt
        // flash on the client).
        float prevMax = access.gulliver$getPrevMaxHealth();
        float curMax = self.getMaxHealth();
        if (Float.isNaN(prevMax)) {
            // First tick — no scaling, just record.
            access.gulliver$setPrevMaxHealth(curMax);
        } else if (prevMax != curMax && prevMax > 0.0F) {
            float ratio = curMax / prevMax;
            float newHp = self.getHealth() * ratio;
            // Cap at curMax to be safe against tiny float drift.
            if (newHp > curMax) newHp = curMax;
            self.setHealth(newHp);
            access.gulliver$setPrevMaxHealth(curMax);
        }

        int prevAir = access.gulliver$getPrevMaxAir();
        int curMaxAir = self.getMaxAirSupply();
        if (prevAir < 0) {
            access.gulliver$setPrevMaxAir(curMaxAir);
        } else if (prevAir != curMaxAir && prevAir > 0) {
            int newAir = (int) ((long) self.getAirSupply() * curMaxAir / prevAir);
            if (newAir > curMaxAir) newAir = curMaxAir;
            self.setAirSupply(newAir);
            access.gulliver$setPrevMaxAir(curMaxAir);
        }
    }
}
