package gulliver.mixin;

import gulliver.client.RelativePitch;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the listener-relative size pitch (see RelativePitch) at the
 * single point every played sound's pitch is resolved. RETURN inject
 * so vanilla's own clamping/randomization runs first and we scale the
 * final value.
 */
@Mixin(SoundEngine.class)
public abstract class MixinSoundEnginePitch {

    @Inject(method = "calculatePitch", at = @At("RETURN"), cancellable = true)
    private void gulliver$relativePitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        float base = cir.getReturnValueF();
        float adjusted = RelativePitch.applyTo(sound, base);
        if (adjusted != base) cir.setReturnValue(adjusted);
    }
}
