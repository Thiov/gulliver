package gulliver.neoforge;

import gulliver.network.GulliverPayload;
import gulliver.platform.Platform;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;
import java.util.List;

public final class NeoForgePlatform implements Platform {
    @Override
    public String loaderName() {
        return "NeoForge";
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, GulliverPayload payload) {
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    @Override
    public void sendToTrackingAndSelf(Entity entity, GulliverPayload payload) {
        // NeoForge throws (and kicks the player) for a payload sent to a
        // connection without Gulliver's channel, so go player by player:
        // everyone watching the entity's chunk (a superset of its trackers;
        // clients ignore ids they don't know), plus the entity itself.
        if (!(entity.level() instanceof ServerLevel level)) return;
        List<ServerPlayer> watchers = level.getChunkSource().chunkMap.getPlayers(entity.chunkPosition(), false);
        for (ServerPlayer p : watchers) sendToPlayer(p, payload);
        if (entity instanceof ServerPlayer self && !watchers.contains(self)) sendToPlayer(self, payload);
    }

    @Override
    public boolean sendToServer(GulliverPayload payload) {
        return NeoForgeClientNetworking.sendToServer(payload);
    }
}
