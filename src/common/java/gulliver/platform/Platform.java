package gulliver.platform;

import gulliver.network.GulliverPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.nio.file.Path;

/**
 * The few things common code needs from the mod loader. Each loader
 * (Fabric, NeoForge, Forge) supplies one implementation and installs it
 * through {@link Services#set} before anything else runs.
 */
public interface Platform {
    /** "Fabric", "NeoForge" or "Forge". */
    String loaderName();

    Path configDir();

    boolean isModLoaded(String modId);

    /** Server -> one client. */
    void sendToPlayer(ServerPlayer player, GulliverPayload payload);

    /** Server -> every client tracking the entity, plus the entity itself when it is a player. */
    void sendToTrackingAndSelf(Entity entity, GulliverPayload payload);

    /**
     * Client -> server. Only called on the client. Returns false (and sends
     * nothing) when the server doesn't have Gulliver.
     */
    boolean sendToServer(GulliverPayload payload);

    /**
     * Whether a player may break this block as collateral of a size power
     * (giant fist crater, growth burst, floor cracking). Implementations
     * fire the loader's own break event so claim and protection mods can
     * veto it.
     */
    default boolean mayBreakBlock(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
                                  net.minecraft.world.level.block.state.BlockState state, ServerPlayer player) {
        return true;
    }

    /**
     * Whether a player may grab, carry or mount this entity through
     * Gulliver's own input (V key, carry, tiny mounts). Implementations
     * fire the loader's entity-interaction event so claim mods can veto it.
     */
    default boolean mayInteractWithEntity(ServerPlayer player, Entity target,
                                          net.minecraft.world.InteractionHand hand) {
        return true;
    }

    //#if MC < 1.20.5
    //$$ /** {block reach, entity reach} attributes where the loader has them (Forge), else null. */
    //$$ default net.minecraft.world.entity.ai.attributes.Attribute[] reachAttributes() {
    //$$     return null;
    //$$ }
    //$$
    //$$ /** Survival block reach (4.5, 5 in creative before 1.20.5) scaled by size. */
    //$$ default double blockReach(net.minecraft.world.entity.player.Player player) {
    //$$     return (player.isCreative() ? 5.0D : 4.5D) * gulliver.common.SizeAttributes.reachFactor(player,
    //$$             ((gulliver.api.IResizeableEntity) player).getSizeMultiplier());
    //$$ }
    //$$
    //$$ default double entityReach(net.minecraft.world.entity.player.Player player) {
    //$$     return (player.isCreative() ? 5.0D : 3.0D) * gulliver.common.SizeAttributes.reachFactor(player,
    //$$             ((gulliver.api.IResizeableEntity) player).getSizeMultiplier());
    //$$ }
    //#endif
}
