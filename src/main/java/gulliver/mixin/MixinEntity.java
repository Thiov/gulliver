package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.access.IGulliverEntityInternal;
import gulliver.access.IGulliverShoulderInternal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
public abstract class MixinEntity implements IResizeableEntity, IGulliverEntityInternal, IGulliverShoulderInternal {

    @Unique private float gulliver$sizeBaseMultiplier = 1.0F;
    @Unique private float gulliver$sizeBaseDestMultiplier = 1.0F;
    @Unique private float gulliver$sizePotionMultiplier = 1.0F;
    @Unique private float gulliver$sizeItemMultiplier = 1.0F;
    @Unique private java.util.UUID gulliver$holdingEntity = null;
    @Unique private java.util.UUID gulliver$heldEntity = null;

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
        // Set the destination; the per-tick tween in MixinLivingEntitySizeTween
        // will lerp the live base toward it. LivingEntity overrides this to
        // honour per-class config bounds; on raw Entity (items, projectiles)
        // we just halve the destination directly.
        gulliver$sizeBaseDestMultiplier *= 0.5F;
    }

    @Override
    @Unique
    public void doubleSize() {
        gulliver$sizeBaseDestMultiplier *= 2.0F;
    }

    @Override
    @Unique
    public boolean isTiny() {
        // 1.6.4 nn.java line 352: size < 0.3
        return getSizeMultiplier() < 0.3F;
    }

    @Override
    @Unique
    public boolean isExtraTiny() {
        // 1.6.4 nn.java line 357: size < 0.15
        return getSizeMultiplier() < 0.15F;
    }

    @Override
    @Unique
    public boolean isHuge() {
        // 1.6.4 nn.java line 362: size >= 2.4
        return getSizeMultiplier() >= 2.4F;
    }

    @Override
    @Unique
    public float getStepHeight() {
        // Default raw-Entity step height (overridden on LivingEntity by
        // MixinLivingEntity.getStepHeight with the 1.6.4 power-of-2 formula).
        return 0.0F;
    }

    @Override
    @Unique
    public float getRangeMultiplier() {
        // 1.6.4 nn.java line 304-313
        float s = getSizeMultiplier();
        if (s >= 1.0F) return s;
        return getSizeMultiplierRoot();
    }

    @Override
    @Unique
    public float getSizeMovementMultiplier() {
        // 1.6.4 nn.java line 316-325
        if (!isWeighted()) return getSizeMultiplierRoot();
        return getSizeMultiplier();
    }

    @Override
    @Unique
    public boolean isWeighted() {
        // 1.6.4 nn.java/of.java default; Player override comes from MixinPlayer.
        return false;
    }

    @Override
    @Unique
    public boolean isSticky() {
        return false;
    }

    @Override
    @Unique
    public int getStepSide() {
        // 1.6.4 nn.java line 374: default 0; LivingEntity override returns 1.
        return 0;
    }

    @Override @Unique public float gulliver$getSizeBaseMultiplier() { return gulliver$sizeBaseMultiplier; }
    @Override @Unique public float gulliver$getSizeBaseDestMultiplier() { return gulliver$sizeBaseDestMultiplier; }
    @Override @Unique public float gulliver$getSizePotionMultiplier() { return gulliver$sizePotionMultiplier; }
    @Override @Unique public float gulliver$getSizeItemMultiplier() { return gulliver$sizeItemMultiplier; }

    @Override @Unique public void gulliver$setSizeBaseMultiplier(float v) { gulliver$sizeBaseMultiplier = v; }
    @Override @Unique public void gulliver$setSizeBaseDestMultiplier(float v) { gulliver$sizeBaseDestMultiplier = v; }
    @Override @Unique public void gulliver$setSizePotionMultiplier(float v) { gulliver$sizePotionMultiplier = v; }
    @Override @Unique public void gulliver$setSizeItemMultiplier(float v) { gulliver$sizeItemMultiplier = v; }

    @Override @Unique public java.util.UUID gulliver$getHoldingEntity() { return gulliver$holdingEntity; }
    @Override @Unique public java.util.UUID gulliver$getHeldEntity() { return gulliver$heldEntity; }
    @Override @Unique public void gulliver$setHoldingEntity(java.util.UUID id) { gulliver$holdingEntity = id; }
    @Override @Unique public void gulliver$setHeldEntity(java.util.UUID id) { gulliver$heldEntity = id; }

    /**
     * Scale the entity's dimensions (width, height, eye height) uniformly by
     * sizeMultiplier. Mirrors the 1.6.4 mod where
     * EntityResizeablePlayerMP.f() returned 1.62F * getSizeMultiplier() —
     * the vanilla 1.62 default eye height multiplied. EntityDimensions.scale
     * does width*=s, height*=s, eyeHeight*=s — same shape as 1.6.4's manual
     * override, applied uniformly to every entity (not via Attributes.SCALE).
     */
    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        float m = getSizeMultiplier();
        if (m == 1.0F) return;
        EntityDimensions base = cir.getReturnValue();
        if (base == null) return;
        cir.setReturnValue(base.scale(m));
    }

    /**
     * Scale step height by sizeMultiplier. Placeholder formula
     * (vanillaStepHeight × multiplier) until the original 1.6.4 helper is
     * fully decoded — but the 1.6.4 IResizeableEntity.getStepHeight()
     * contract is exactly this multiplicative shape.
     */
    @Inject(method = "maxUpStep", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleStepHeight(CallbackInfoReturnable<Float> cir) {
        float m = getSizeMultiplier();
        if (m == 1.0F) return;
        cir.setReturnValue(cir.getReturnValue() * m);
    }
}
