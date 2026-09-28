package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.api.IResizeableLiving;
import gulliver.common.GulliverEnvoy;
import gulliver.init.GulliverDamageTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-tick callouts for small-entity air/ground effects, replacing the
 * 1.6.4 ASM patches into Entity#onUpdate / EntityLivingBase#onLivingUpdate
 * for the sub-1.0 size bracket. Mirror of MixinLivingEntityHugeEffects.
 *
 * Behaviors, in order:
 *
 *   1. Terminal velocity (any size < 1) — the square-cube law: drag
 *      area shrinks with L² while weight shrinks with L³, so the
 *      smaller the body, the lower its terminal fall speed. Sustained
 *      falls ease toward `vanillaTerminal × size`; short hops and jump
 *      arcs never reach that speed, so movement stays snappy — only
 *      real falls turn into a leaf-light descent. (The huge-side
 *      counterpart is NOT faster falling — gravity doesn't care how
 *      big you are — it's the landing shockwave in
 *      MixinLivingEntityHugeFall: the world reacts, not the fall.)
 *
 *   2. Rising updraft from heat sources (tiny) — adds Y velocity
 *      scaled by sizeMultiplierRoot. Tinies near fire/lava/torches get
 *      lifted.
 *
 *   3. Rain drowning (extra-tiny) — to something that small, raindrops
 *      are body-sized masses of water: being caught in the open drains
 *      air like being submerged (net -1/tick against vanilla's +4
 *      replenish), and when the air runs out they take drowning damage
 *      on the vanilla cadence (2 hp every 20 ticks). Shelter, sneaking
 *      (huddling), or holding a lily-pad umbrella overhead stops the
 *      drain, and air recovers at vanilla +4/tick once out of the rain.
 *
 *   4. Sticky-surface spider-walk (tiny): along a ladder or wall-sign
 *      side, damp horizontal motion so the tiny clings instead of
 *      sliding off.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityTinyEffects {

    /** Vanilla air terminal velocity ≈ gravity×drag/(1−drag) = 0.08×0.98/0.02. */
    private static final double GULLIVER$VANILLA_TERMINAL = 3.92D;

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void gulliver$tinyEffects(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        float size = sized.getSizeMultiplier();
        if (size >= 1.0F) return;

        // 1. Terminal velocity — smooth across the whole sub-1 bracket
        //    (no threshold pop at the isTiny boundary). Skip in fluids
        //    (buoyancy owns the motion), on ladders (climb caps apply),
        //    and during elytra flight (dive speed is the point of it).
        Vec3 dm0 = self.getDeltaMovement();
        double terminal = GULLIVER$VANILLA_TERMINAL * size;
        if (dm0.y < -terminal
                && !self.isInWater() && !self.isInLava()
                && !self.onClimbable() && !self.isFallFlying()) {
            // Ease toward terminal instead of hard-clamping — reads as
            // air resistance taking hold, and absorbs mid-fall size
            // changes without a velocity snap.
            self.setDeltaMovement(dm0.x, Mth.lerp(0.3D, dm0.y, -terminal), dm0.z);
        }

        if (!sized.isTiny()) return;

        // 2. Rising updraft — applies upward velocity from heat sources.
        double updraft = GulliverEnvoy.getRisingUpdraft(self);
        if (updraft > 0.0D) {
            Vec3 m = self.getDeltaMovement();
            self.setDeltaMovement(m.x, m.y + updraft, m.z);
        }

        // 3. Rain drowning for extra-tinies. Runs after vanilla's air
        //    handling in baseTick (+4/tick replenish on land), so the
        //    -5 here nets -1/tick while exposed — the exact vanilla
        //    underwater drain rate, bubbles HUD and all.
        if (sized.isExtraTiny()
                && !self.level().isClientSide()
                && GulliverEnvoy.tinyCaughtInRain(self)) {
            int air = self.getAirSupply() - 5;
            if (air <= -20) {
                air = 0;
                self.hurt(GulliverDamageTypes.rain(self.level()), 2.0F);
            }
            self.setAirSupply(air);
        }

        // 4. Sticky-surface damping on ladders / wall-signs. Halve
        //    horizontal motion so the tiny clings to the surface instead
        //    of immediately sliding off; vanilla ladder climbing logic
        //    handles the vertical component.
        if (GulliverEnvoy.alongStickySurface(self)) {
            Vec3 m = self.getDeltaMovement();
            self.setDeltaMovement(m.x * 0.5D, m.y, m.z * 0.5D);
        }
    }
}
