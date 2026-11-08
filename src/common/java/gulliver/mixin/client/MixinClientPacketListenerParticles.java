package gulliver.mixin.client;

import gulliver.client.ParticleScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Owners for the particles the server causes (ParticleScale): an entity
 * event's particles are that entity's, particles sent to a point belong to
 * the creature there, and an explosion is as big as the creeper that set it
 * off. Each handler first runs on the network thread only to be handed to
 * the game thread; ParticleScale ignores that first pass.
 */
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListenerParticles {

    @Inject(method = "handleEntityEvent", at = @At("HEAD"), require = 0)
    private void gulliver$eventBegin(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) return;
        ParticleScale.push(mc.level == null ? null : packet.getEntity(mc.level));
    }

    @Inject(method = "handleEntityEvent", at = @At("RETURN"), require = 0)
    private void gulliver$eventEnd(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        ParticleScale.pop();
    }

    @Inject(method = "handleParticleEvent", at = @At("HEAD"), require = 0)
    private void gulliver$particlesBegin(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        //#if MC >= 26.3
        ParticleScale.pushAt(packet.x(), packet.y(), packet.z());
        //#else
        //$$ ParticleScale.pushAt(packet.getX(), packet.getY(), packet.getZ());
        //#endif
    }

    @Inject(method = "handleParticleEvent", at = @At("RETURN"), require = 0)
    private void gulliver$particlesEnd(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        ParticleScale.pop();
    }

    @Inject(method = "handleExplosion", at = @At("HEAD"), require = 0)
    private void gulliver$explosionBegin(ClientboundExplodePacket packet, CallbackInfo ci) {
        //#if MC >= 1.21.2
        ParticleScale.pushExplosion(packet.center().x, packet.center().y, packet.center().z);
        //#else
        //$$ ParticleScale.pushExplosion(packet.getX(), packet.getY(), packet.getZ());
        //#endif
    }

    @Inject(method = "handleExplosion", at = @At("RETURN"), require = 0)
    private void gulliver$explosionEnd(ClientboundExplodePacket packet, CallbackInfo ci) {
        ParticleScale.pop();
    }
}
