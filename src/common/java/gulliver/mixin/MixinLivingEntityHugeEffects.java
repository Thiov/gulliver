package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverEnvoy;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-tick ground effects of size (1.6.4 Entity#onUpdate patches):
 * anything tramples creatures much smaller than itself (issue #1: by size
 * difference, not only when huge); huge bodies also leave footprints
 * (step triggers on the blocks underfoot) and crack brittle floors.
 *
 * Movement is measured from the entity's own positions between ticks —
 * a server-side player's delta movement stays near zero, which used to
 * stop giant PLAYERS from ever trampling anything.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityHugeEffects {

    @Unique private double gulliver$lastX = Double.NaN;
    @Unique private double gulliver$lastZ;

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void gulliver$hugeEffects(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) return;
        double x = self.getX();
        double z = self.getZ();
        double moved = Double.isNaN(gulliver$lastX) ? 0.0D
                : (x - gulliver$lastX) * (x - gulliver$lastX) + (z - gulliver$lastZ) * (z - gulliver$lastZ);
        gulliver$lastX = x;
        gulliver$lastZ = z;

        GulliverEnvoy.stepOnSmallerEntities(self, moved);
        if (((IResizeableEntity) self).isHuge()) {
            GulliverEnvoy.leaveHugeFootprints(self, moved);
            GulliverEnvoy.checkSupportingBlocksForHuge(self, GulliverEnvoy.getRand());
        }
    }
}
