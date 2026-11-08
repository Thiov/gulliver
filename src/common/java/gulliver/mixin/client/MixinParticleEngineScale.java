package gulliver.mixin.client;

import gulliver.client.ParticleScale;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every particle passes through add(): it takes on the size of its owner (ParticleScale). */
@Mixin(ParticleEngine.class)
public abstract class MixinParticleEngineScale {

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), require = 0)
    private void gulliver$scaleToOwner(Particle particle, CallbackInfo ci) {
        ParticleScale.apply(particle);
    }
}
