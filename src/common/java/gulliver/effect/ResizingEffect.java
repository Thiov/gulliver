package gulliver.effect;

import gulliver.access.IGulliverEntityInternal;
import gulliver.common.GulliverEnvoy;
import gulliver.network.SizeSync;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
//#if MC >= 1.21.2
import net.minecraft.server.level.ServerLevel;
//#endif

/**
 * Common base of the two resizing effects (1.6.4 PotionResizing).
 *
 * The entity's potion multiplier is the product over every active
 * resizing effect, so Ensmallening and Embiggening together cancel out
 * instead of fighting over the value each tick. Growing through the
 * effect bursts weak blocks overhead (breakBlocksViaGrowth), shrinking
 * never breaks anything.
 *
 *   Huge: 4x, 8x, 16x ... per amplifier (clamped by the size limits)
 *   Tiny: 1/4x, 1/8x ... per amplifier
 */
public abstract class ResizingEffect extends MobEffect {

    protected ResizingEffect(MobEffectCategory cat, int color) {
        super(cat, color);
    }

    protected abstract float multiplierForAmplifier(int amp);

    /** Product of all active resizing effects' multipliers (1 when none). */
    public static float combinedMultiplier(LivingEntity entity, MobEffectInstance ignored) {
        float mult = 1.0F;
        for (MobEffectInstance inst : entity.getActiveEffects()) {
            if (inst == ignored) continue;
            //#if MC >= 1.20.5
            MobEffect effect = inst.getEffect().value();
            //#else
            //$$ MobEffect effect = inst.getEffect();
            //#endif
            if (effect instanceof ResizingEffect re) {
                mult *= re.multiplierForAmplifier(Math.max(0, Math.min(8, inst.getAmplifier())));
            }
        }
        return mult;
    }

    /** Recompute and apply the potion multiplier; returns true if it changed. */
    public static boolean refreshPotionMultiplier(LivingEntity entity, MobEffectInstance ignored) {
        IGulliverEntityInternal sized = (IGulliverEntityInternal) entity;
        float mult = combinedMultiplier(entity, ignored);
        float old = sized.gulliver$getSizePotionMultiplier();
        if (old == mult) return false;
        net.minecraft.world.phys.AABB oldBox = entity.getBoundingBox();
        sized.gulliver$setSizePotionMultiplier(mult);
        entity.refreshDimensions();
        if (mult > old) GulliverEnvoy.breakBlocksViaGrowth(entity, oldBox);
        SizeSync.broadcast(entity);
        return true;
    }

    //#if MC >= 1.20.5
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
    //#else
    //$$ @Override
    //$$ public boolean isDurationEffectTick(int duration, int amplifier) {
    //$$     return true;
    //$$ }
    //#endif

    //#if MC >= 1.21.2
    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        refreshPotionMultiplier(entity, null);
        return true;
    }
    //#elif MC >= 1.20.5
    //$$ @Override
    //$$ public boolean applyEffectTick(LivingEntity entity, int amplifier) {
    //$$     if (!entity.level().isClientSide()) refreshPotionMultiplier(entity, null);
    //$$     return true;
    //$$ }
    //#else
    //$$ @Override
    //$$ public void applyEffectTick(LivingEntity entity, int amplifier) {
    //$$     if (!entity.level().isClientSide()) refreshPotionMultiplier(entity, null);
    //$$ }
    //#endif
}
