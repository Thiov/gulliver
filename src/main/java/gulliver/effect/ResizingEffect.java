package gulliver.effect;

import gulliver.mixin.iface.IGulliverEntityInternal;
import gulliver.network.SizeSync;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Common skeleton for the Gulliver resizing effects, ported verbatim from
 * the 1.6.4 PotionResizing class.
 *
 * Each tick, computes the size-potion multiplier from the active amplifier
 * (clamped to 0..8 — the original capped at amp == 8 with amp >= 255 as a
 * sentinel that we collapse to 0 since modern MC clamps amplifier itself)
 * and writes it onto the entity's @Unique sizePotionMultiplier field. If
 * the value has actually changed since last tick, refreshes dimensions
 * and broadcasts a size-sync packet so clients update.
 *
 * Subclasses provide the formula:
 *   HugeEffect: Math.scalb(4.0F,  amp)   →  4×, 8×, 16×, ..., 1024×
 *   TinyEffect: Math.scalb(0.25F, -amp)  →  0.25×, 0.125×, ..., 0.001×
 */
public abstract class ResizingEffect extends MobEffect {

    protected ResizingEffect(MobEffectCategory cat, int color) {
        super(cat, color);
    }

    protected abstract float multiplierForAmplifier(int amp);

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // Apply every tick — same shape as the 1.6.4 isReady(d, a) returning true
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        int amp = Math.max(0, Math.min(8, amplifier));
        float mult = multiplierForAmplifier(amp);

        IGulliverEntityInternal sized = (IGulliverEntityInternal) entity;
        if (sized.gulliver$getSizePotionMultiplier() != mult) {
            sized.gulliver$setSizePotionMultiplier(mult);
            entity.refreshDimensions();
            SizeSync.broadcast(entity);
        }
        return true;
    }
}
