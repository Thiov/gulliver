package gulliver.mixin.client;

import gulliver.client.ParticleScale;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a creature ticks on the client, the particles it makes are its own
 * (ParticleScale). A passenger ticks inside its vehicle's tick, so owners
 * nest. tickNonPassenger is where every tick starts: an owner left over
 * from a tick that threw is dropped there.
 */
@Mixin(ClientLevel.class)
public abstract class MixinClientLevelParticleOwner {

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), require = 0)
    private void gulliver$ownerBegin(Entity entity, CallbackInfo ci) {
        ParticleScale.reset();
        ParticleScale.push(entity);
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"), require = 0)
    private void gulliver$ownerEnd(Entity entity, CallbackInfo ci) {
        ParticleScale.pop();
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"), require = 0)
    private void gulliver$passengerBegin(Entity vehicle, Entity passenger, CallbackInfo ci) {
        ParticleScale.push(passenger);
    }

    @Inject(method = "tickPassenger", at = @At("RETURN"), require = 0)
    private void gulliver$passengerEnd(Entity vehicle, Entity passenger, CallbackInfo ci) {
        ParticleScale.pop();
    }
}
