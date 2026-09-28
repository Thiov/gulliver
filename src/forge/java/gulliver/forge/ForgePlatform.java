package gulliver.forge;

import gulliver.network.GulliverPayload;
import gulliver.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

import java.nio.file.Path;

public final class ForgePlatform implements Platform {
    @Override
    public String loaderName() {
        return "Forge";
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        //#if MC >= 26.1
        return ModList.isLoaded(modId);
        //#else
        //$$ return ModList.get().isLoaded(modId);
        //#endif
    }

    @Override
    public void sendToPlayer(ServerPlayer player, GulliverPayload payload) {
        if (ForgeNetworking.CHANNEL.isRemotePresent(player.connection.getConnection())) {
            ForgeNetworking.CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
        }
    }

    @Override
    public void sendToTrackingAndSelf(Entity entity, GulliverPayload payload) {
        ForgeNetworking.CHANNEL.send(payload, PacketDistributor.TRACKING_ENTITY_AND_SELF.with(entity));
    }

    @Override
    public boolean sendToServer(GulliverPayload payload) {
        return ForgeClientNetworking.sendToServer(payload);
    }
}
