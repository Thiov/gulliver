package gulliver.effect;

import net.minecraft.world.effect.MobEffectCategory;

/**
 * 1.6.4 PotionResizing.tiny — color 0x7CFEC0 (8190976 decimal in the
 * original source). Per-amplifier multiplier: Math.scalb(0.25F, -amp).
 */
public final class TinyEffect extends ResizingEffect {
    public static final int COLOR = 0x7CFEC0;

    public TinyEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }

    @Override
    protected float multiplierForAmplifier(int amp) {
        return Math.scalb(0.25F, -amp);
    }
}
