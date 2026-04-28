package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverEnvoy;
import gulliver.init.GulliverDamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-tick callouts for tiny-entity ground/air effects, replacing the
 * 1.6.4 ASM patches into Entity#onUpdate / EntityLivingBase#onLivingUpdate
 * for tiny entities. Mirror of MixinLivingEntityHugeEffects, but for the
 * sub-1.0 size bracket.
 *
 * Three behaviors fire each tick on a tiny LivingEntity:
 *
 *   1. Rising updraft from heat sources — adds Y velocity scaled by
 *      sizeMultiplierRoot. Tinies near fire/lava/torches get lifted.
 *
 *   2. Rain damage if extra-tiny + caught in rain unsheltered. The
 *      1.6.4 'tinyCaughtInRain' fed the 'water dissolves them' channel
 *      — translated as 1 hp every 20 ticks (same cadence as drowning,
 *      passive damage source).
 *
 *   3. Sticky-surface spider-walk: along a ladder or wall-sign side, a
 *      tiny gets motion damping so they don't slide off (a primitive
 *      "stick to the wall" hint — the full ladder-spider-walk control
 *      logic isn't faithfully portable without ASM into the climb
 *      input pipeline, so this is a minimal expression of the mechanic).
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityTinyEffects {

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void gulliver$tinyEffects(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        if (!sized.isTiny()) return;

        // 1. Rising updraft — applies upward velocity from heat sources.
        double updraft = GulliverEnvoy.getRisingUpdraft(self);
        if (updraft > 0.0D) {
            Vec3 m = self.getDeltaMovement();
            self.setDeltaMovement(m.x, m.y + updraft, m.z);
        }

        // 2. Rain damage to extra-tinies. 1 hp every 20 ticks (= once per
        //    second at 20 TPS), only on server.
        if (sized.isExtraTiny()
                && !self.level().isClientSide()
                && self.tickCount % 20 == 0
                && GulliverEnvoy.tinyCaughtInRain(self)) {
            self.hurt(GulliverDamageTypes.passive(self.level(), self), 1.0F);
        }

        // 3. Sticky-surface damping on ladders / wall-signs. Halve
        //    horizontal motion so the tiny clings to the surface instead
        //    of immediately sliding off; vanilla ladder climbing logic
        //    handles the vertical component.
        if (GulliverEnvoy.alongStickySurface(self)) {
            Vec3 m = self.getDeltaMovement();
            self.setDeltaMovement(m.x * 0.5D, m.y, m.z * 0.5D);
        }
    }
}
