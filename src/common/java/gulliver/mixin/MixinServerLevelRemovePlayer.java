package gulliver.mixin;

import gulliver.common.ShoulderHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every way a player leaves a level goes through here: a portal or
 * teleport to another dimension, the respawn after the End exit, a
 * logout. The loaders' after-the-fact events come too late to let go of
 * what the player carries (the creatures would be left behind, frozen, in
 * the old dimension), so everything is released first, at the carrier's
 * last position, and a player who is itself carried is freed.
 */
@Mixin(ServerLevel.class)
public abstract class MixinServerLevelRemovePlayer {

    @Inject(method = "removePlayerImmediately", at = @At("HEAD"))
    private void gulliver$releaseBeforeLeaving(ServerPlayer player, Entity.RemovalReason reason, CallbackInfo ci) {
        ShoulderHelper.onLeavingLevel(player);
    }
}
