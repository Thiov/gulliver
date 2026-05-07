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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
    @Unique private float gulliver$prevMaxHealth = Float.NaN;
    @Unique private int   gulliver$prevMaxAir = -1;
    @Unique private java.util.UUID gulliver$holdingEntity = null;
    @Unique private java.util.UUID gulliver$handEntity = null;
    @Unique private java.util.UUID gulliver$rightShoulder = null;
    @Unique private java.util.UUID gulliver$leftShoulder = null;

    @Override @Unique public float gulliver$getPrevMaxHealth() { return gulliver$prevMaxHealth; }
    @Override @Unique public void gulliver$setPrevMaxHealth(float v) { gulliver$prevMaxHealth = v; }
    @Override @Unique public int gulliver$getPrevMaxAir() { return gulliver$prevMaxAir; }
    @Override @Unique public void gulliver$setPrevMaxAir(int v) { gulliver$prevMaxAir = v; }

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
        // Linear scaling for ALL sizes — user playtest: sqrt-for-tinies
        // gave ~3 block reach at size 0.125, which is too long. Linear
        // gives 0.56 blocks at 0.125x, 2.25 at 0.5x, 4.5 at vanilla,
        // 36 at size 8 — matches the original Gulliver mod's tighter
        // reach feel.
        return getSizeMultiplier();
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
    @Override @Unique public void gulliver$setHoldingEntity(java.util.UUID id) { gulliver$holdingEntity = id; }
    @Override @Unique public java.util.UUID gulliver$getHandEntity()      { return gulliver$handEntity; }
    @Override @Unique public java.util.UUID gulliver$getRightShoulder()   { return gulliver$rightShoulder; }
    @Override @Unique public java.util.UUID gulliver$getLeftShoulder()    { return gulliver$leftShoulder; }
    @Override @Unique public void gulliver$setHandEntity(java.util.UUID id)    { gulliver$handEntity = id; }
    @Override @Unique public void gulliver$setRightShoulder(java.util.UUID id) { gulliver$rightShoulder = id; }
    @Override @Unique public void gulliver$setLeftShoulder(java.util.UUID id)  { gulliver$leftShoulder = id; }

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
     * 1.6.4 of.java lines 486-512 getStepHeight — power-of-2 normalization,
     * NOT a simple linear multiplier:
     *
     *   f = Y          (vanilla maxUpStep)
     *   norm = 1.0
     *   while (norm * size < 0.5) { norm *= 2;  f /= 2; }
     *   while (norm * size > 1.0) { norm /= 2;  f *= 2; }
     *   if (norm * size < 0.65)   { f *= norm * size; }
     *
     * For size=2  (huge): one downward iteration -> norm=0.5,  f=2Y;
     * for size=0.5      : norm=1, no iter, last clause: f *= 0.5 -> 0.5Y
     * for size=0.125    : norm=8, f=Y/8; last clause not hit (1.0 >= 0.65) -> Y/8
     * for size=0.25     : norm=2, f=Y/2; norm*size=0.5 < 0.65 -> f*=0.5 -> Y/4
     *
     * Linear "0.6 * size" would give 0.15 at size=0.25 — the 1.6.4 formula
     * gives 0.15 for vanilla Y=0.6 too (Y/4 = 0.15), but for size=0.5 gives
     * 0.3 vs linear's 0.3 — only diverges at extreme sizes. The whole point
     * is huge entities can step over 2-block walls and tinies don't slip up
     * staircases meant for full-size entities.
     */
    @Inject(method = "maxUpStep", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleStepHeight(CallbackInfoReturnable<Float> cir) {
        float m = getSizeMultiplier();
        if (m == 1.0F) return;
        float f = cir.getReturnValue();
        float norm = 1.0F;
        while (norm * m < 0.5F) { norm *= 2.0F; f /= 2.0F; }
        while (norm * m > 1.0F) { norm /= 2.0F; f *= 2.0F; }
        if (norm * m < 0.65F) { f *= norm * m; }
        cir.setReturnValue(f);
    }

    /**
     * 1.6.4 of.java:2638 — `moveFlying(par1, par2, getSizeMovementMultiplier()
     * * f4)` scaled the third argument (friction-influenced speed) by the
     * size-movement multiplier (sqrt(size) when !isWeighted, else linear).
     *
     * Modern equivalent: Entity.moveRelative(float amount, Vec3 input) is
     * called from every travel path (travelInAir, travelInWater, etc.).
     * Scale `amount` here so the input vector gets multiplied by the
     * sized speed regardless of which travel branch the entity uses.
     *
     * Catches:
     *   - LivingEntity.travelInAir's moveRelative
     *   - travelInFluid / travelInWater / travelInLava
     *   - Player.travel overrides if any
     *   - Mob AI travel paths
     *
     * Replaces the previous getSpeed() injection which didn't propagate
     * through every travel branch.
     */
    @ModifyVariable(method = "moveRelative(FLnet/minecraft/world/phys/Vec3;)V",
                    at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float gulliver$scaleMoveSpeed(float amount) {
        float m = getSizeMultiplier();
        if (m == 1.0F) return amount;
        return amount * ((IResizeableEntity) this).getSizeMovementMultiplier();
    }
}
