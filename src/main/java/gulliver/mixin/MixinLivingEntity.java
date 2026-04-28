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
public abstract class MixinLivingEntity implements IResizeableLiving {

    @Unique private boolean gulliver$isGlidingFlag = false;
    @Unique private boolean gulliver$couldUseUmbrella = false;
    @Unique private boolean gulliver$isRaftingFlag = false;
    @Unique private boolean gulliver$isStruggling = false;

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
    @Override
    @Unique
    public boolean isWeighted() {
        LivingEntity self = (LivingEntity) (Object) this;
        try {
            for (net.minecraft.world.entity.EquipmentSlot slot
                    : net.minecraft.world.entity.EquipmentSlot.values()) {
                if (slot.getType() != net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) continue;
                if (!self.getItemBySlot(slot).isEmpty()) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * 1.6.4 of.java line 2948: tiny + holding slime ball OR alongStickySurface
     * OR overlapping a slime block. We honour the slime-ball-in-hand branch
     * (the most common one) and the sticky-surface predicate.
     */
    @Override
    @Unique
    public boolean isSticky() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!isTiny()) return false;
        net.minecraft.world.item.ItemStack hand = self.getMainHandItem();
        if (hand != null && hand.is(net.minecraft.world.item.Items.SLIME_BALL)) return true;
        return GulliverEnvoy.alongStickySurface(self);
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
        // Set the DESTINATION; the per-tick tween in MixinLivingEntitySizeTween
        // animates the live base toward it. Broadcast immediately so clients
        // receive the destination and animate locally with the same lerp.
        ((IGulliverEntityInternal) this).gulliver$setSizeBaseDestMultiplier(clamped);
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
