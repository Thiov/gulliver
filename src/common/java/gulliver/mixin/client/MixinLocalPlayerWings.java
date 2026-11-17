package gulliver.mixin.client;

import gulliver.client.WingFlight;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wings (WingFlight): take off, land and choose what the wings do, before the player's own tick. */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayerWings {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void gulliver$wings(CallbackInfo ci) {
        WingFlight.preTick((LocalPlayer) (Object) this);
    }
}
