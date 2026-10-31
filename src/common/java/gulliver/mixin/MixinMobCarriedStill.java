package gulliver.mixin;

import gulliver.access.IGulliverShoulderInternal;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A mob in someone's hand thinks of nothing: no path finding, no looking around, no goals. */
@Mixin(Mob.class)
public abstract class MixinMobCarriedStill {

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void gulliver$pauseAiInHand(CallbackInfo ci) {
        if (((IGulliverShoulderInternal) this).gulliver$getHoldingEntity() != null) ci.cancel();
    }
}
