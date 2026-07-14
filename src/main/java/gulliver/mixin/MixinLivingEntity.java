package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.api.IResizeableLiving;
import gulliver.common.GulliverConfig;
import gulliver.common.GulliverEnvoy;
import gulliver.access.IGulliverEntityInternal;
import gulliver.network.SizeSync;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Implements IResizeableLiving on every LivingEntity. The size data lives
 * on the parent Entity via MixinEntity; this mixin reads/writes through the
 * IGulliverEntityInternal interface that MixinEntity also implements.
 *
 * setBaseSize / adjustBaseSize clamp to the per-entity bounds from the
 * config: GulliverEnvoy.getMin/MaxSizeForEntity (size-limit category) AND
 * the global min/maxEntityBaseSize (general category). The intersection of
 * both is applied — the same composition the 1.6.4 mod did via two layered
 * Forge config keys.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity implements IResizeableLiving,
        gulliver.access.IGulliverFlagsInternal {

    @Unique private boolean gulliver$isGlidingFlag = false;
    @Unique private boolean gulliver$couldUseUmbrella = false;
    @Unique private boolean gulliver$isRaftingFlag = false;
    @Unique private boolean gulliver$isStruggling = false;

    @Override @Unique public void gulliver$setGlidingFlag(boolean v) { gulliver$isGlidingFlag = v; }
    @Override @Unique public void gulliver$setCouldUseUmbrella(boolean v) { gulliver$couldUseUmbrella = v; }
    @Override @Unique public void gulliver$setRaftingFlag(boolean v) { gulliver$isRaftingFlag = v; }
    @Override @Unique public void gulliver$setStruggling(boolean v) { gulliver$isStruggling = v; }

    /**
     * Per-tick feel-flag refresh. Mirrors 1.6.4 of.java l_() invocation of
     * updateResizingFlags() (called via super.l_() into the Player override
     * which in turn called the LivingEntity helper). On modern MC the
     * baseTick is the canonical "called every tick" entrypoint shared by
     * Player and all LivingEntity subclasses.
     */
    @Inject(method = "baseTick", at = @At("RETURN"))
    private void gulliver$updateResizingFlags(
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        GulliverEnvoy.updateResizingFlags(self, this);
    }

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
    public boolean isGliding() {
        return gulliver$isGlidingFlag;
    }

    @Override
    @Unique
    public boolean doesUmbrella() {
        return gulliver$couldUseUmbrella;
    }

    @Override
    @Unique
    public boolean isRafting() {
        return gulliver$isRaftingFlag;
    }

    @Override
    @Unique
    public boolean isStruggling() {
        return gulliver$isStruggling;
    }

    /**
     * 1.6.4 of.java line 2528: any worn-armor slot equipped with a leggings-tier
     * (or higher) item counts as weighted. We approximate with "any non-empty
     * armor slot" since 26.x equipment slots aren't ID-comparable. This is a
     * conservative widening: more entities count as weighted, biasing toward
     * linear (less generous) movement scaling.
     */
    /**
     * 1.6.4 of.java:2528 verbatim — `aqcv` resolves (yc.aq) to gold_boots
     * (id 317) and the loop checks armor slots boots/leggings/chest/helm
     * for ids aqcv-0..aqcv-3, i.e. the four GOLD armor pieces in their
     * matching slots. So: weighted iff ANY gold armor piece is worn in
     * its matching slot. Leather/iron/diamond/chain do NOT count.
     *
     * Folded in: 1.6.4 uf.java:1890 player override — gliding or rafting
     * disables weighted regardless of armor. Holds for every LivingEntity
     * in the port since the flags live on the IResizeableLiving interface.
     */
    @Override
    @Unique
    public boolean isWeighted() {
        if (isGliding() || isRafting()) return false;
        LivingEntity self = (LivingEntity) (Object) this;
        return self.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET)
                    .is(net.minecraft.world.item.Items.GOLDEN_BOOTS)
            || self.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS)
                    .is(net.minecraft.world.item.Items.GOLDEN_LEGGINGS)
            || self.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
                    .is(net.minecraft.world.item.Items.GOLDEN_CHESTPLATE)
            || self.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                    .is(net.minecraft.world.item.Items.GOLDEN_HELMET);
    }

    /**
     * 1.6.4 of.java line 2948: tiny + (holding slime ball OR alongStickySurface
     * OR bbox-overlaps a Slime entity). All three branches verbatim.
     */
    @Override
    @Unique
    public boolean isSticky() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!isTiny()) return false;
        net.minecraft.world.item.ItemStack hand = self.getMainHandItem();
        if (hand != null && hand.is(net.minecraft.world.item.Items.SLIME_BALL)) return true;
        if (GulliverEnvoy.alongStickySurface(self)) return true;
        return !self.level().getEntitiesOfClass(
                net.minecraft.world.entity.monster.Slime.class,
                self.getBoundingBox()).isEmpty();
    }

    /**
     * 1.6.4 of.java line 521: living entities default getStepSide to 1.
     */
    @Override
    @Unique
    public int getStepSide() {
        return 1;
    }

    @Unique
    private float gulliver$clampBase(float size) {
        LivingEntity self = (LivingEntity) (Object) this;
        GulliverConfig.General g = GulliverConfig.INSTANCE.general;
        float lo = (float) Math.max(GulliverEnvoy.getMinSizeForEntity(self), g.minEntityBaseSize);
        float hi = (float) Math.min(GulliverEnvoy.getMaxSizeForEntity(self), g.maxEntityBaseSize);
        return Math.max(lo, Math.min(hi, size));
    }

    @Override
    @Unique
    public void setBaseSize(float size) {
        LivingEntity self = (LivingEntity) (Object) this;
        float clamped = gulliver$clampBase(size);
        IGulliverEntityInternal access = (IGulliverEntityInternal) this;
        // Bbox BEFORE the resize — breakBlocksViaGrowth needs it to
        // know which blocks are newly swept by the grown body.
        net.minecraft.world.phys.AABB oldBox = self.getBoundingBox();

        // Snap LIVE base AND dest to the new value (no tween). The tween
        // caused gradual HP drops that the HUD interpreted as damage,
        // and the gradual eye-height shift caused camera jolt. Snapping
        // is uglier but visually MUCH less disruptive — the entity's
        // hearts and stats update once, and the HUD renders correctly
        // because attribute MAX_HEALTH is updated in the same step.
        float oldLive = access.gulliver$getSizeBaseMultiplier();
        access.gulliver$setSizeBaseMultiplier(clamped);
        access.gulliver$setSizeBaseDestMultiplier(clamped);

        // Apply size-based attribute modifiers (MAX_HEALTH, ARMOR) so
        // the HUD's heart count actually changes. The HUD reads MAX_HEALTH
        // attribute directly, bypassing getMaxHealth() injects.
        float potion = access.gulliver$getSizePotionMultiplier();
        float item   = access.gulliver$getSizeItemMultiplier();
        gulliver.common.SizeAttributes.applyForSize(self, clamped * potion * item);

        // Per user request: do NOT rescale current HP / air on resize.
        // Hearts stay full when shrinking (max scales but current stays
        // capped to new max via vanilla setHealth clamp the next time
        // any code path calls it). Damage-taken / damage-dealt scaling
        // by size still applies — see MixinLivingEntityDamage.
        // Just clamp once now so current can never exceed the new max.
        float maxHp = self.getMaxHealth();
        if (self.getHealth() > maxHp) self.setHealth(maxHp);
        int maxAir = self.getMaxAirSupply();
        if (self.getAirSupply() > maxAir) self.setAirSupply(maxAir);

        self.refreshDimensions();
        // Growing bodies burst through weak blocks newly inside their
        // bounds (1.6.4 breakBlocksViaGrowth) — ceiling planks splinter
        // as the giant stands up. Shrinking never breaks anything.
        if (clamped * potion * item > oldLive * potion * item) {
            GulliverEnvoy.breakBlocksViaGrowth(self, oldBox);
        }
        SizeSync.broadcast(self);
    }

    @Override
    @Unique
    public void adjustBaseSize(float factor) {
        float current = ((IGulliverEntityInternal) this).gulliver$getSizeBaseDestMultiplier();
        setBaseSize(current * factor);
    }

    /**
     * Override halveSize / doubleSize on LivingEntity to honour the per-class
     * config bounds. Without this, /halfsize and /doublesize bypass the
     * 0.125–8.0 limits because they bind to the raw Entity-level methods.
     */
    @Override
    @Unique
    public void halveSize() {
        float current = ((IGulliverEntityInternal) this).gulliver$getSizeBaseDestMultiplier();
        setBaseSize(current * 0.5F);
    }

    @Override
    @Unique
    public void doubleSize() {
        float current = ((IGulliverEntityInternal) this).gulliver$getSizeBaseDestMultiplier();
        setBaseSize(current * 2.0F);
    }

    /**
     * LivingEntity.getDimensions(Pose) is final and computes its own
     * EntityDimensions without calling Entity.getDimensions, so MixinEntity's
     * scaling inject never fires for any living entity. Apply the same
     * scaling here so refreshDimensions() actually picks up the resized
     * dimensions for players, mobs, etc.
     */
    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleLivingDimensions(Pose pose,
                                                 CallbackInfoReturnable<EntityDimensions> cir) {
        float m = ((IResizeableEntity) this).getSizeMultiplier();
        if (m == 1.0F) return;
        EntityDimensions base = cir.getReturnValue();
        if (base == null) return;
        cir.setReturnValue(base.scale(m));
    }
}
