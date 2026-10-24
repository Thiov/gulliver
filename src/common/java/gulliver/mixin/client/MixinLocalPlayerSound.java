package gulliver.mixin.client;

import gulliver.api.IResizeableEntity;
import gulliver.common.SizeSound;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The local player plays its own sounds straight to the local sound
 * engine (LocalPlayer.playSound never reaches Entity.playSound), so
 * MixinEntitySound never saw them: a giant's own footsteps stayed at
 * vanilla volume while everyone else's were scaled. Same rule here.
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayerSound {

    @ModifyArg(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V",
               at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"),
               index = 5, require = 0)
    private float gulliver$scaleOwnVolume(float volume) {
        return SizeSound.scaleVolume((IResizeableEntity) this, volume);
    }
}
