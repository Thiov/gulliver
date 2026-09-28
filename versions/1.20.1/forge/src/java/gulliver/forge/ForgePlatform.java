package gulliver.forge;

import gulliver.network.GulliverPayload;
import gulliver.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

import java.nio.file.Path;

/** Forge 1.20.1 (and the NeoForge 1.20.1 fork). */
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
        return ModList.get().isLoaded(modId);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, GulliverPayload payload) {
        if (ForgeNetworking.CHANNEL.isRemotePresent(player.connection.connection)) {
            ForgeNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        }
    }

    @Override
    public void sendToTrackingAndSelf(Entity entity, GulliverPayload payload) {
        ForgeNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), payload);
    }

    @Override
    public boolean sendToServer(GulliverPayload payload) {
        return ForgeClientNetworking.sendToServer(payload);
    }

    @Override
    public Attribute[] reachAttributes() {
        return new Attribute[] { ForgeMod.BLOCK_REACH.get(), ForgeMod.ENTITY_REACH.get() };
    }

    @Override
    public double blockReach(Player player) {
        return player.getBlockReach();
    }

    @Override
    public double entityReach(Player player) {
        return player.getEntityReach();
    }
}
