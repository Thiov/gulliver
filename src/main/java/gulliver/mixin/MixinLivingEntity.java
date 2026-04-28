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
        LivingEntity self = (LivingEntity) (Object) this;
        GulliverConfig.General g = GulliverConfig.INSTANCE.general;
        float lo = (float) Math.max(GulliverEnvoy.getMinSizeForEntity(self), g.minEntityBaseSize);
        float hi = (float) Math.min(GulliverEnvoy.getMaxSizeForEntity(self), g.maxEntityBaseSize);
        float clamped = Math.max(lo, Math.min(hi, size));
        ((IGulliverEntityInternal) this).gulliver$setSizeBaseMultiplier(clamped);
        self.refreshDimensions();
        SizeSync.broadcast(self);
    }

    @Override
    @Unique
    public void adjustBaseSize(float factor) {
        float current = ((IGulliverEntityInternal) this).gulliver$getSizeBaseMultiplier();
        setBaseSize(current * factor);
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
