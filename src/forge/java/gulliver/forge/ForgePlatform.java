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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
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

    @Override
    public boolean mayBreakBlock(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (!BreakGuard.allowed(level, pos, player)) return false;
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player, Result.DEFAULT);
        return !BlockEvent.BreakEvent.BUS.post(event) && !event.getResult().isDenied();
    }

    //#if MC < 1.20.5
    //$$ /** Forge's own reach attributes: before 1.20.5 vanilla has none, and every Forge reach check reads these. */
    //$$ @Override
    //$$ public net.minecraft.world.entity.ai.attributes.Attribute[] reachAttributes() {
    //$$     return new net.minecraft.world.entity.ai.attributes.Attribute[] {
    //$$             net.minecraftforge.common.ForgeMod.BLOCK_REACH.get(),
    //$$             net.minecraftforge.common.ForgeMod.ENTITY_REACH.get() };
    //$$ }
    //$$
    //$$ @Override
    //$$ public double blockReach(net.minecraft.world.entity.player.Player player) {
    //$$     return player.getBlockReach();
    //$$ }
    //$$
    //$$ @Override
    //$$ public double entityReach(net.minecraft.world.entity.player.Player player) {
    //$$     return player.getEntityReach();
    //$$ }
    //#endif

    @Override
    public boolean mayInteractWithEntity(ServerPlayer player, Entity target, InteractionHand hand) {
        // Interaction point relative to the entity's feet: the middle of its body.
        Vec3 local = new Vec3(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        return CommonEvents.probe(() ->
                !PlayerInteractEvent.EntityInteractSpecific.BUS.post(
                        new PlayerInteractEvent.EntityInteractSpecific(player, hand, target, local)));
    }
}
