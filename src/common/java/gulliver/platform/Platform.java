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

    /** Client -> server. Only called on the client. */
    void sendToServer(GulliverPayload payload);

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
