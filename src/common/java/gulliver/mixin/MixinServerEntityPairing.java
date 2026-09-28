package gulliver.mixin;

import gulliver.network.SizeSync;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player started tracking an entity: send its size right after the
 * spawn packets, so resized entities never flash at 1.0 when they come
 * into view.
 */
@Mixin(ServerEntity.class)
public abstract class MixinServerEntityPairing {

    @Shadow @Final private Entity entity;

    @Inject(method = "addPairing", at = @At("TAIL"))
    private void gulliver$sendSize(ServerPlayer player, CallbackInfo ci) {
        SizeSync.onStartTracking(entity, player);
    }
}
