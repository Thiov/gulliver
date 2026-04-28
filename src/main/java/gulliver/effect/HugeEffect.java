package gulliver.effect;

import net.minecraft.world.effect.MobEffectCategory;

/**
 * 1.6.4 PotionResizing.huge — color 0x9362DB (9662683 decimal in the
 * original source). Per-amplifier multiplier: Math.scalb(4.0F, amp).
 */
public final class HugeEffect extends ResizingEffect {
    public static final int COLOR = 0x9362DB;

    public HugeEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }

    @Override
    protected float multiplierForAmplifier(int amp) {
        return Math.scalb(4.0F, amp);
    }
}
