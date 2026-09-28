package gulliver.mixin;

import gulliver.common.ShoulderHelper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carry placement for the server and the local player (Player.aiStep);
 * other players on a client are placed by MixinRemotePlayerShoulder.
 * Dropping everything on death is in MixinServerPlayerDeathDrop.
 */
@Mixin(Player.class)
public abstract class MixinPlayerShoulder {

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void gulliver$positionPassengers(CallbackInfo ci) {
        ShoulderHelper.positionPassengers((Player) (Object) this);
    }
}
