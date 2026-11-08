package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverConfig;
import gulliver.common.SizeScaling;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Size gaps decide who gets pushed (#14).
 *
 * Vanilla's Entity.push(Entity) shoves the two bodies apart by the same
 * amount, whatever their size, so a mouse could walk a whale across the
 * room. Here each body's share follows the size of the one pushing it
 * (SizeScaling.pushShare): a body pushed by something its own size or
 * bigger moves as in vanilla (a bit more for a much bigger pusher, so a
 * giant wading through a crowd shoves it aside), one pushed by something
 * smaller moves less, and against anything under a quarter of its size it
 * is a wall that doesn't move at all. The smaller body then slides off it
 * alone.
 *
 * The rest is vanilla's own push, unchanged (same distance falloff, same
 * 0.05 strength, same passenger, noPhysics, vehicle and pushable checks).
 * Equal sizes, or general.sizePushing off, leave the vanilla method alone.
 * Boats and minecarts have their own push and are not touched.
 */
@Mixin(Entity.class)
public abstract class MixinEntityPush {

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void gulliver$sizedPush(Entity other, CallbackInfo ci) {
        if (!GulliverConfig.INSTANCE.general.sizePushing) return;
        Entity self = (Entity) (Object) this;
        float mine = ((IResizeableEntity) self).getSizeMultiplier();
        float theirs = ((IResizeableEntity) other).getSizeMultiplier();
        if (mine == theirs || !(mine > 0.0F) || !(theirs > 0.0F)) return;
        ci.cancel();
        if (self.isPassengerOfSameVehicle(other) || other.noPhysics || self.noPhysics) return;
        double dx = other.getX() - self.getX();
        double dz = other.getZ() - self.getZ();
        double far = Mth.absMax(dx, dz);
        if (far < 0.01F) return;
        far = Math.sqrt(far);
        dx /= far;
        dz /= far;
        double falloff = Math.min(1.0D, 1.0D / far);
        dx *= falloff * 0.05F;
        dz *= falloff * 0.05F;
        if (!self.isVehicle() && self.isPushable()) {
            double share = SizeScaling.pushShare(theirs, mine);
            if (share > 0.0D) self.push(-dx * share, 0.0D, -dz * share);
        }
        if (!other.isVehicle() && other.isPushable()) {
            double share = SizeScaling.pushShare(mine, theirs);
            if (share > 0.0D) other.push(dx * share, 0.0D, dz * share);
        }
    }
}
