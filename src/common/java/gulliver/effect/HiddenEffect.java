package gulliver.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Shown while an extra-tiny hides in a flower, sapling or flower pot, so
 * the player knows others can't see them. It does nothing by itself: the
 * server adds it while hiding and takes it away when the hiding ends.
 */
public final class HiddenEffect extends MobEffect {
    public static final int COLOR = 0x7FB77E;

    public HiddenEffect() {
        super(MobEffectCategory.NEUTRAL, COLOR);
    }
}
