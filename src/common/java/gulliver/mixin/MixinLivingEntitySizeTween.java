package gulliver.mixin;

import gulliver.access.IGulliverEntityInternal;
import gulliver.api.IResizeableEntity;
import gulliver.common.BodyFit;
import gulliver.common.GiantExtents;
import gulliver.common.SizeAttributes;
import gulliver.common.SpawnSizes;
import gulliver.network.SizeSync;
import net.minecraft.server.level.ServerPlayer;
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
 *  - both sides: when the effective size moved without anyone applying it
 *    (a config reload changed the limits) refresh the dimensions, and on the
 *    server tell the clients;
 *  - server: keep the size-based attribute modifiers (reach, max health,
 *    armor) in line. Only when the size changed, plus a slow safety net;
 *    the server owns attributes and syncs them, so clients never write;
 *  - server: a player whose body did not fit where it arrived keeps looking
 *    for room for a moment (chunks around a new dimension load late).
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

        float live = ((IResizeableEntity) self).getSizeMultiplier();
        if (live != access.gulliver$getAppliedSize()) {
            access.gulliver$setAppliedSize(live);
            self.refreshDimensions();
            if (server) SizeSync.broadcast(self);
        }
        if (live > 1.0F) GiantExtents.report(self);

        if (server) {
            // Players re-check every tick: their reach also depends on what
            // they hold (a tiny with a sword reaches further).
            if (self instanceof ServerPlayer player) gulliver.common.GulliverEnvoy.updateHidden(player);
            if (live != gulliver$attrSize && self instanceof ServerPlayer player) {
                gulliver.common.GulliverAdvancements.onPlayerSize(player, live);
            }
            if (live != gulliver$attrSize || self instanceof net.minecraft.world.entity.player.Player
                    || (self.tickCount % 100) == 0) {
                SizeAttributes.applyForSize(self, live);
                gulliver$attrSize = live;
            }
            if (access.gulliver$getSettleTicks() > 0 && self instanceof ServerPlayer player) {
                BodyFit.tickSettle(player);
            }
        }
    }
}
