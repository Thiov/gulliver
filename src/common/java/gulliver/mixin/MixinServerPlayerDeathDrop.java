package gulliver.mixin;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.common.ShoulderHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dying drops everything carried. ServerPlayer.die doesn't call
 * Player.die, so the hook has to sit here.
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerDeathDrop {

    @Inject(method = "die", at = @At("HEAD"))
    private void gulliver$dropOnDeath(DamageSource source, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (((IGulliverShoulderInternal) self).gulliver$hasAnyCarry()) ShoulderHelper.drop(self);
    }
}
