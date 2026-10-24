package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GiantBossBars;
import gulliver.common.GulliverEnvoy;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
 *
 * Also carries the boss bar of a giant mob (GiantBossBars): created and
 * updated here every tick, taken down when the entity dies or is removed.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityHugeEffects {

    @Unique private double gulliver$lastX = Double.NaN;
    @Unique private double gulliver$lastZ;
    /** Boss bar of a size-4+ mob while the option is on; never saved. */
    @Unique private ServerBossEvent gulliver$bossBar;

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

        GulliverEnvoy.noteBodySize(self);
        GulliverEnvoy.stepOnSmallerEntities(self, moved);
        if (((IResizeableEntity) self).isHuge()) {
            GulliverEnvoy.leaveHugeFootprints(self, moved);
            GulliverEnvoy.checkSupportingBlocksForHuge(self, GulliverEnvoy.getRand());
        }
        if (gulliver$bossBar != null || GiantBossBars.wants(self)) {
            gulliver$bossBar = GiantBossBars.update(self, gulliver$bossBar);
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void gulliver$dropBossBarOnDeath(DamageSource source, CallbackInfo ci) {
        if (gulliver$bossBar != null) {
            GiantBossBars.discard(gulliver$bossBar);
            gulliver$bossBar = null;
        }
    }

    /** Unloading, changing dimension, discarding: every way out of the world passes here. */
    @Inject(method = "remove", at = @At("HEAD"))
    private void gulliver$dropBossBarOnRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        if (gulliver$bossBar != null) {
            GiantBossBars.discard(gulliver$bossBar);
            gulliver$bossBar = null;
        }
    }
}
