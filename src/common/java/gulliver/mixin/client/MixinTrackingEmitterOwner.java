package gulliver.mixin.client;

import gulliver.client.ParticleScale;
import net.minecraft.client.particle.TrackingEmitter;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Critical and enchanted hits sparkle at the size of the creature that was hit (ParticleScale). */
@Mixin(TrackingEmitter.class)
public abstract class MixinTrackingEmitterOwner {

    @Shadow @Final private Entity entity;

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void gulliver$ownerBegin(CallbackInfo ci) {
        ParticleScale.push(entity);
    }

    @Inject(method = "tick", at = @At("RETURN"), require = 0)
    private void gulliver$ownerEnd(CallbackInfo ci) {
        ParticleScale.pop();
    }
}
