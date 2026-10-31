package gulliver.mixin;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.common.Appetite;
import gulliver.common.ShoulderHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every living body, server and client: chewing goes on (Appetite), and a
 * mob holding something in its hand (a monster that snatched its prey)
 * places it, like players do from Player.aiStep (MixinPlayerShoulder).
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityCarry {

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void gulliver$chewAndCarry(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        Appetite.tick(self);
        if (!(self instanceof Player) && ((IGulliverShoulderInternal) self).gulliver$hasAnyCarry()) {
            ShoulderHelper.positionPassengers(self);
        }
    }
}
