package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Implements IResizeableEntity on every Entity. The three multipliers
 * (base, potion, item) compose into getSizeMultiplier() exactly as in
 * the 1.6.4 mod's ASM-injected fields. No vanilla Attributes.SCALE.
 *
 * Thresholds:
 *   isTiny   : sizeMultiplier < 1.0  (matches 1.6.4 isHoldingStringOrLeash use)
 *   isHuge   : sizeMultiplier > 1.0
 *   isExtraTiny : sizeMultiplier < 0.25  (matches the 1.6.4 client-side
 *                push-out threshold in EntityResizeableClientPlayerMP.i)
 */
@Mixin(Entity.class)
public abstract class MixinEntity implements IResizeableEntity, IGulliverEntityInternal {

    @Unique private float gulliver$sizeBaseMultiplier = 1.0F;
    @Unique private float gulliver$sizePotionMultiplier = 1.0F;
    @Unique private float gulliver$sizeItemMultiplier = 1.0F;

    @Override
    @Unique
    public float getSizeMultiplier() {
        return gulliver$sizeBaseMultiplier
                * gulliver$sizePotionMultiplier
                * gulliver$sizeItemMultiplier;
    }

    @Override
    @Unique
    public float getSizeMultiplierRoot() {
        return (float) Math.sqrt(getSizeMultiplier());
    }

    @Override
    @Unique
    public void halveSize() {
        gulliver$sizeBaseMultiplier *= 0.5F;
    }

    @Override
    @Unique
    public void doubleSize() {
        gulliver$sizeBaseMultiplier *= 2.0F;
    }

    @Override
    @Unique
    public boolean isTiny() {
        return getSizeMultiplier() < 1.0F;
    }

    @Override
    @Unique
    public boolean isExtraTiny() {
        return getSizeMultiplier() < 0.25F;
    }

    @Override
    @Unique
    public boolean isHuge() {
        return getSizeMultiplier() > 1.0F;
    }

    @Override
    @Unique
    public float getStepHeight() {
        return 0.6F * getSizeMultiplier();
    }

    @Override @Unique public float gulliver$getSizeBaseMultiplier() { return gulliver$sizeBaseMultiplier; }
    @Override @Unique public float gulliver$getSizePotionMultiplier() { return gulliver$sizePotionMultiplier; }
    @Override @Unique public float gulliver$getSizeItemMultiplier() { return gulliver$sizeItemMultiplier; }

    @Override @Unique public void gulliver$setSizeBaseMultiplier(float v) { gulliver$sizeBaseMultiplier = v; }
    @Override @Unique public void gulliver$setSizePotionMultiplier(float v) { gulliver$sizePotionMultiplier = v; }
    @Override @Unique public void gulliver$setSizeItemMultiplier(float v) { gulliver$sizeItemMultiplier = v; }
}
