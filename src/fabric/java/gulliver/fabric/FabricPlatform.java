package gulliver.fabric;

import gulliver.common.BreakGuard;
import gulliver.common.CommonEvents;
import gulliver.network.GulliverPayload;
import gulliver.platform.Platform;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Path;

public final class FabricPlatform implements Platform {
    @Override
    public String loaderName() {
        return "Fabric";
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, GulliverPayload payload) {
        //#if MC >= 1.20.5
        if (ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
        //#else
        //$$ FabricNetworking.send(player, payload);
        //#endif
    }

    @Override
    public void sendToTrackingAndSelf(Entity entity, GulliverPayload payload) {
        for (ServerPlayer viewer : PlayerLookup.tracking(entity)) {
            if (viewer != entity) sendToPlayer(viewer, payload);
        }
        if (entity instanceof ServerPlayer self) sendToPlayer(self, payload);
    }

    @Override
    public boolean sendToServer(GulliverPayload payload) {
        //#if MC >= 1.20.5
        if (!ClientPlayNetworking.canSend(payload.type())) return false;
        ClientPlayNetworking.send(payload);
        return true;
        //#else
        //$$ return FabricNetworking.sendToServer(payload);
        //#endif
    }

    @Override
    public boolean mayBreakBlock(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (!BreakGuard.allowed(level, pos, player)) return false;
        return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos));
    }

    @Override
    public boolean mayInteractWithEntity(ServerPlayer player, Entity target, InteractionHand hand) {
        // A listener answering FAIL (claim and protection mods) is a veto.
        InteractionResult result = CommonEvents.probe(() ->
                UseEntityCallback.EVENT.invoker().interact(player, player.level(), hand, target, null));
        return result != InteractionResult.FAIL;
    }
}
