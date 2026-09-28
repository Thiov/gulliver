package gulliver.mixin;

import gulliver.access.IGulliverShoulderInternal;
import gulliver.common.ShoulderHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carry placement for the server and the local player (Player.aiStep);
 * other players on a client are placed by MixinRemotePlayerShoulder.
 * Dying drops everything carried.
 */
@Mixin(Player.class)
public abstract class MixinPlayerShoulder {

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void gulliver$positionPassengers(CallbackInfo ci) {
        ShoulderHelper.positionPassengers((Player) (Object) this);
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void gulliver$dropOnDeath(DamageSource source, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer sp
                && ((IGulliverShoulderInternal) sp).gulliver$hasAnyCarry()) {
            ShoulderHelper.drop(sp);
        }
    }
}
