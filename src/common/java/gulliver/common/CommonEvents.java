package gulliver.common;

import com.mojang.brigadier.CommandDispatcher;
import gulliver.api.IResizeableEntity;
import gulliver.command.GulliverCommands;
import gulliver.network.SizeSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Every loader event Gulliver listens to, in loader-neutral form. The
 * Fabric/NeoForge/Forge glue translates its own events into these calls;
 * a non-PASS result means "cancel the vanilla action with this result".
 */
public final class CommonEvents {
    private CommonEvents() {}

    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        // Setting a carried entity down wins over the tiny interaction gate.
        InteractionResult r = ShoulderInteractHandler.onUseBlock(player, level, hand, hit);
        if (r != InteractionResult.PASS) return r;
        return InteractEventHandler.onUseBlock(player, level, hand, hit);
    }

    public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity target) {
        return ShoulderInteractHandler.onUseEntity(player, level, hand, target);
    }

    public static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
        return ShoulderInteractHandler.onUseItem(player, level, hand);
    }

    /** Player melee attack on an entity; never cancels. */
    public static void onAttackEntity(Player player, Level level, Entity target) {
        GiantAoe.onAttack(player, level, target);
    }

    public static void onPlayerClone(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        KarmaMode.onClone(oldPlayer, newPlayer, alive);
    }

    public static void onPlayerRespawn(ServerPlayer player, boolean alive) {
        KarmaMode.onRespawn(player, alive);
    }

    public static void onPlayerJoin(ServerPlayer player) {
        SizeSync.broadcast(player);
    }

    public static void onPlayerLeave(ServerPlayer player) {
        ShoulderInteractHandler.onDisconnect(player);
    }

    public static void onPlayerChangedDimension(ServerPlayer player) {
        ShoulderInteractHandler.onChangeDimension(player);
        KarmaMode.onChangedDimension(player);
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        GulliverCommands.register(dispatcher);
    }

    /**
     * Mining speed scales linearly with size (giants mine fast, tinies
     * slow). Fabric applies this through MixinPlayerBreakSpeed; NeoForge
     * and Forge through their break-speed event.
     */
    public static float scaleBreakSpeed(Player player, float speed) {
        float size = ((IResizeableEntity) player).getSizeMultiplier();
        return size == 1.0F ? speed : speed * size;
    }
}
