package gulliver.neoforge;

import gulliver.common.BreakGuard;
import gulliver.common.CommonEvents;
import gulliver.network.GulliverPayload;
import gulliver.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
//#if MC >= 26.1
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
//#else
//$$ import net.neoforged.neoforge.event.level.BlockEvent;
//#endif
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

    @Override
    public boolean mayBreakBlock(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (!BreakGuard.allowed(level, pos, player)) return false;
        //#if MC >= 26.1
        return !NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, pos, state, player)).isCanceled();
        //#else
        //$$ return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled();
        //#endif
    }

    @Override
    public boolean mayInteractWithEntity(ServerPlayer player, Entity target, InteractionHand hand) {
        // Both stages of the click, as vanilla fires them, so a mod that guards either one is heard.
        return CommonEvents.probe(() -> {
            //#if MC >= 26.2
            return !NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(
                    player, hand, target, bodyCentre(target))).isCanceled();
            //#else
            //$$ if (NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteractSpecific(
            //$$         player, hand, target, bodyCentre(target))).isCanceled()) {
            //$$     return false;
            //$$ }
            //$$ return !NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, hand, target)).isCanceled();
            //#endif
        });
    }

    /** Interaction point, relative to the entity's feet: the middle of its body. */
    private static net.minecraft.world.phys.Vec3 bodyCentre(Entity target) {
        return new net.minecraft.world.phys.Vec3(0.0D, target.getBbHeight() * 0.5D, 0.0D);
    }
}
