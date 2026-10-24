package gulliver.mixin;

import gulliver.access.IGulliverShoulderInternal;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A carried player is placed by the server alone, like a passenger: the
 * carrier's tick puts it in the hand or on the shoulder every tick. The
 * position its client reports is not used while it is carried, which
 * closes two holes at once:
 *
 *  - vanilla would count the hovering client as flying and kick it after
 *    about four seconds, and rubber-band it with "moved wrongly";
 *  - the carried player has noPhysics set, and vanilla accepts any client
 *    position for a noPhysics player, so a hacked client could walk through
 *    walls and break free on the far side.
 *
 * Rotation is still taken from the packet (the player can look around), and
 * the chunk tracking is refreshed as vanilla does for passengers.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketCarried {

    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Shadow private int aboveGroundTickCount;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void gulliver$ignoreCarriedPosition(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (((IGulliverShoulderInternal) player).gulliver$getHoldingEntity() == null) return;
        // On the network thread let vanilla queue the packet; it comes back here on the server thread.
        MinecraftServer server = player.level().getServer();
        if (server == null || !server.isSameThread()) return;
        ci.cancel();
        float yRot = packet.getYRot(player.getYRot());
        float xRot = packet.getXRot(player.getXRot());
        if (Float.isFinite(yRot) && Float.isFinite(xRot)) {
            player.setYRot(Mth.wrapDegrees(yRot));
            player.setXRot(Mth.clamp(Mth.wrapDegrees(xRot), -90.0F, 90.0F));
        }
        ((ServerLevel) player.level()).getChunkSource().move(player);
        clientIsFloating = false;
        aboveGroundTickCount = 0;
    }

    /** A "floating" verdict from before the pickup must not tick on while the player hangs in a hand. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void gulliver$notFloatingWhileCarried(CallbackInfo ci) {
        if (((IGulliverShoulderInternal) player).gulliver$getHoldingEntity() != null) {
            clientIsFloating = false;
            aboveGroundTickCount = 0;
        }
    }
}
