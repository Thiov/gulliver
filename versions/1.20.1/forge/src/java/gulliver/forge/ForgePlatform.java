package gulliver.forge;

import gulliver.common.BreakGuard;
import gulliver.common.CommonEvents;
import gulliver.network.GulliverPayload;
import gulliver.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
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
    public boolean mayBreakBlock(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (!BreakGuard.allowed(level, pos, player)) return false;
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player));
    }

    @Override
    public boolean mayInteractWithEntity(ServerPlayer player, Entity target, InteractionHand hand) {
        // Both stages of the click, as vanilla fires them, so a mod that guards either one is heard.
        // The interaction point is relative to the entity's feet: the middle of its body.
        Vec3 local = new Vec3(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        return CommonEvents.probe(() ->
                !MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteractSpecific(player, hand, target, local))
                        && !MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, hand, target)));
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
