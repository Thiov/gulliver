package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.TinyMounts;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Steering for the small animals a tiny can ride (TinyMounts), through the
 * same three hooks vanilla uses for a saddled pig or horse: the ridden
 * input, the ridden speed and the per-tick ridden update. The classes that
 * override them (pigs, horses, striders, camels) are not affected, only
 * the animals on the TinyMounts list.
 *
 * Only the rider's own client simulates the animal; the server takes its
 * position from the vehicle-move packets, so the jump and hop below run
 * there alone.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityTinyMount {

    /** Ticks before a ridden animal may jump or hop again. */
    @Unique private int gulliver$hopDelay;
    /** Server: whether the ridden animal was on the ground last tick (to spot a rabbit's take-off). */
    @Unique private boolean gulliver$wasOnGround;

    @Inject(method = "getRiddenInput", at = @At("HEAD"), cancellable = true)
    private void gulliver$steerInput(Player controller, Vec3 selfInput, CallbackInfoReturnable<Vec3> cir) {
        if (TinyMounts.controllerOf((LivingEntity) (Object) this) != controller) return;
        cir.setReturnValue(TinyMounts.riddenInput(controller));
    }

    @Inject(method = "getRiddenSpeed", at = @At("HEAD"), cancellable = true)
    private void gulliver$steerSpeed(Player controller, CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (TinyMounts.controllerOf(self) != controller) return;
        cir.setReturnValue(TinyMounts.riddenSpeed(self, TinyMounts.specOf(self)));
    }

    @Inject(method = "tickRidden", at = @At("HEAD"))
    private void gulliver$steerTick(Player controller, Vec3 riddenInput, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (TinyMounts.controllerOf(self) != controller) return;
        TinyMounts.Spec spec = TinyMounts.specOf(self);

        // Follow the rider's look, as a horse does.
        self.setYRot(controller.getYRot());
        self.setXRot(controller.getXRot() * 0.5F);
        self.yRotO = self.yBodyRot = self.yHeadRot = self.getYRot();

        boolean onGround = self.onGround();
        if (gulliver$hopDelay > 0) gulliver$hopDelay--;
        if (!self.level().isClientSide()) {
            // The server doesn't simulate the animal, but it does tell
            // everyone when a rabbit leaves the ground so the hop shows.
            if (spec.hopper() && gulliver$wasOnGround && !onGround) {
                self.level().broadcastEntityEvent(self, (byte) 1);
            }
            gulliver$wasOnGround = onGround;
            return;
        }
        if (!controller.isLocalPlayer() || !onGround || gulliver$hopDelay > 0) return;

        boolean jump = ((LivingEntityJumpAccessor) controller).gulliver$isJumping();
        // A bigger or smaller animal jumps in proportion (kept within sane bounds).
        float scale = Mth.clamp(((IResizeableEntity) self).getSizeMultiplierRoot(), 0.5F, 2.0F);
        if (spec.hopper()) {
            gulliver$hop(self, riddenInput, jump, scale);
        } else if (jump) {
            Vec3 motion = self.getDeltaMovement();
            self.setDeltaMovement(motion.x, 0.42D * spec.jump() * scale, motion.z);
            gulliver$hopDelay = 10;
        }
    }

    /** A rabbit has no walk: it hops where the rider steers, higher and further when the jump key is held. */
    @Unique
    private void gulliver$hop(LivingEntity self, Vec3 input, boolean leap, float scale) {
        boolean steering = input.x * input.x + input.z * input.z > 0.0025D;
        if (!leap && !steering) return;
        double forward = (leap ? TinyMounts.LEAP_FORWARD : TinyMounts.HOP_FORWARD) * scale;
        double power = (leap ? TinyMounts.LEAP_POWER : TinyMounts.HOP_POWER) * scale;
        double dx = 0.0D;
        double dz = 0.0D;
        if (steering) {
            // Entity.getInputVector: input.z is forward, input.x sideways.
            float yaw = self.getYRot() * ((float) Math.PI / 180.0F);
            float sin = Mth.sin(yaw);
            float cos = Mth.cos(yaw);
            double x = input.x * cos - input.z * sin;
            double z = input.z * cos + input.x * sin;
            double len = Math.sqrt(x * x + z * z);
            dx = x / len * forward;
            dz = z / len * forward;
        }
        self.setDeltaMovement(dx, power, dz);
        // Entity event 1 is the rabbit's hop animation.
        self.handleEntityEvent((byte) 1);
        gulliver$hopDelay = 4;
    }
}
