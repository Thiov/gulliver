package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverEnvoy;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-tick callouts for huge-entity ground effects, replacing the 1.6.4
 * ASM patches into Entity#onUpdate / EntityLivingBase#onLivingUpdate.
 *
 * Phase 10(1): stepOnSmallerEntities — huge entities crush smaller ones
 * at their foot level for passive damage. Fires on aiStep RETURN so it
 * runs after the AI has updated motion / position for the tick.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityHugeEffects {

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void gulliver$hugeEffects(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (((IResizeableEntity) self).isHuge()) {
            GulliverEnvoy.stepOnSmallerEntities(self);
            GulliverEnvoy.leaveHugeFootprints(self);
            GulliverEnvoy.checkSupportingBlocksForHuge(self, GulliverEnvoy.getRand());
        }
    }
}
