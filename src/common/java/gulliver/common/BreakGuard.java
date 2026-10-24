package gulliver.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The checks vanilla itself makes before a player may break a block, that
 * are the same on every loader. Each loader's
 * {@code Platform.mayBreakBlock} runs this first and then fires its own
 * break event, so claim and protection mods get their say.
 */
public final class BreakGuard {
    private BreakGuard() {}

    public static boolean allowed(ServerLevel level, BlockPos pos, ServerPlayer player) {
        // Adventure mode (without the right tool tags) and spectators.
        if (player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) return false;
        // Spawn protection and the world border.
        return level.mayInteract(player, pos);
    }
}
