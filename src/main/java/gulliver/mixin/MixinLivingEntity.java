package gulliver.mixin;

import gulliver.api.IResizeableLiving;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Implements IResizeableLiving on every LivingEntity. The size data lives
 * on the parent Entity via MixinEntity; this mixin reads/writes through the
 * IGulliverEntityInternal interface that MixinEntity also implements.
 *
 * setBaseSize / adjustBaseSize clamp to the hard bounds 0.125–8.0 from the
 * 1.6.4 mod's GulliverConfigHelper defaults. Per-class min/max-size and
 * per-entity overrides will tighten this further once GulliverEnvoy and the
 * config are ported in later phases.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity implements IResizeableLiving {

    @Unique private static final float HARD_MIN = 0.125F;
    @Unique private static final float HARD_MAX = 8.0F;

    @Override
    @Unique
    public float getSizePotionMultiplier() {
        return ((IGulliverEntityInternal) this).gulliver$getSizePotionMultiplier();
    }

    @Override
    @Unique
    public float getSizeItemMultiplier() {
        return ((IGulliverEntityInternal) this).gulliver$getSizeItemMultiplier();
    }

    @Override
    @Unique
    public void setBaseSize(float size) {
        float clamped = Math.max(HARD_MIN, Math.min(HARD_MAX, size));
        ((IGulliverEntityInternal) this).gulliver$setSizeBaseMultiplier(clamped);
    }

    @Override
    @Unique
    public void adjustBaseSize(float factor) {
        float current = ((IGulliverEntityInternal) this).gulliver$getSizeBaseMultiplier();
        setBaseSize(current * factor);
    }
}
