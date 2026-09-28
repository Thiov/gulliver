package gulliver.mixin.client;

import gulliver.common.ShoulderHelper;
import net.minecraft.client.player.RemotePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Other players' aiStep on a client never reaches Player.aiStep, so their
 * carried entities were never placed here — observers saw them smeared
 * between the pickup point and the hand. Place them the same way.
 */
@Mixin(RemotePlayer.class)
public abstract class MixinRemotePlayerShoulder {

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void gulliver$positionPassengers(CallbackInfo ci) {
        ShoulderHelper.positionPassengers((RemotePlayer) (Object) this);
    }
}
