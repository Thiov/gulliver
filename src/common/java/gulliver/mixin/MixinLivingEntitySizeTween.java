package gulliver.mixin;

import gulliver.access.IGulliverEntityInternal;
import gulliver.api.IResizeableEntity;
import gulliver.common.SizeAttributes;
import gulliver.common.SpawnSizes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-tick size housekeeping for every living entity:
 *
 *  - server: roll the configured spawn size once for new entities;
 *  - both sides: snap the live base size to its destination when some
 *    path (a size packet, a load) set only the destination;
 *  - server: keep the size-based attribute modifiers (reach, max health,
 *    armor) in line. Only when the size changed, plus a slow safety net;
 *    the server owns attributes and syncs them, so clients never write.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntitySizeTween {

    @Unique private float gulliver$attrSize = Float.NaN;

    @Inject(method = "tick", at = @At("RETURN"))
    private void gulliver$tweenSize(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        boolean server = !self.level().isClientSide();
        if (server) SpawnSizes.initIfNeeded(self);

        IGulliverEntityInternal access = (IGulliverEntityInternal) self;
        float dest = access.gulliver$getSizeBaseDestMultiplier();
        if (access.gulliver$getSizeBaseMultiplier() != dest) {
            access.gulliver$setSizeBaseMultiplier(dest);
            self.refreshDimensions();
        }

        if (server) {
            float live = ((IResizeableEntity) self).getSizeMultiplier();
            // Players re-check every tick: their reach also depends on what
            // they hold (a tiny with a sword reaches further).
            if (live != gulliver$attrSize || self instanceof net.minecraft.world.entity.player.Player
                    || (self.tickCount % 100) == 0) {
                SizeAttributes.applyForSize(self, live);
                gulliver$attrSize = live;
            }
        }
    }
}
