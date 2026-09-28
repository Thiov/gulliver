package gulliver.fabric;

import gulliver.network.GulliverPayload;
import gulliver.platform.Platform;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

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
    public void sendToServer(GulliverPayload payload) {
        //#if MC >= 1.20.5
        if (ClientPlayNetworking.canSend(payload.type())) {
            ClientPlayNetworking.send(payload);
        }
        //#else
        //$$ FabricNetworking.sendToServer(payload);
        //#endif
    }
}
