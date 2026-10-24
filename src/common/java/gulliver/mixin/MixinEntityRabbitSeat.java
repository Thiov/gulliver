//#if MC >= 1.21.2
package gulliver.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chickens, cats, ocelots, wolves and foxes come with a passenger seat on
 * their backs; the rabbit doesn't, so a rider would perch on the very top of
 * its box, a hand above the fur. Seat it on the back instead (a tiny rides
 * a rabbit: see TinyMounts). Before 1.20.5 the seat comes from the old
 * riding offset, which is already about right.
 */
@Mixin(Entity.class)
public abstract class MixinEntityRabbitSeat {

    @Inject(method = "getPassengerAttachmentPoint", at = @At("RETURN"), cancellable = true)
    private void gulliver$rabbitSeat(Entity passenger, EntityDimensions dimensions, float scale,
                                     CallbackInfoReturnable<Vec3> cir) {
        if (((Entity) (Object) this).getType() != EntityTypes.RABBIT) return;
        cir.setReturnValue(new Vec3(0.0D, dimensions.height() * 0.7D, -dimensions.width() * 0.1D));
    }
}
//#endif
