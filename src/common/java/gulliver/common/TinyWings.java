package gulliver.common;

import gulliver.access.IGulliverWings;
import gulliver.api.IResizeableEntity;
import gulliver.network.Payloads;
import gulliver.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Wings for tinies: the metamorphosis and the server's side of flying.
 *
 * A player at or below general.wingsMaxSize (tiny, 0.3, by default) who
 * sneak-right-clicks the air with a phantom membrane, honeycomb, flower or
 * amethyst shard eats it and grows fly, bee, butterfly or fairy wings
 * (WingType); eating another one swaps them. The wings stay with the player
 * (saved, and kept on death unless general.keepWingsOnDeath is off), but
 * only carry, and only show on, a body that is small enough.
 *
 * Flying itself happens on the player's own client (gulliver.client.WingFlight),
 * like any player movement. The client tells the server what its wings are
 * doing (Payloads.WingFlight); the server checks it may fly, passes it on to
 * everyone who can see the player, keeps the fall from hurting, keeps it from
 * being kicked for flying (MixinServerGamePacketWings) and makes flying cost a
 * little food.
 */
public final class TinyWings {
    private TinyWings() {}

    public static boolean enabled() {
        return GulliverConfig.INSTANCE.general.tinyWings;
    }

    /** Whether this body is small enough for wings to carry it. */
    public static boolean smallEnough(Entity entity) {
        return ((IResizeableEntity) entity).getSizeMultiplier() <= GulliverConfig.INSTANCE.general.wingsMaxSize + 1.0E-4D;
    }

    public static WingType typeOf(Entity entity) {
        return entity instanceof IGulliverWings w ? w.gulliver$wingType() : WingType.NONE;
    }

    // ---- metamorphosis --------------------------------------------------------

    public static void onMetamorphosis(ServerPlayer player, boolean mainHand) {
        if (!enabled() || player.isSpectator() || !player.isAlive()) return;
        InteractionHand hand = mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = player.getItemInHand(hand);
        WingType type = WingType.fromCatalyst(stack);
        if (type == WingType.NONE) return;
        if (!smallEnough(player)) {
            tell(player, Component.translatable("gulliver.wings.tooBig"));
            return;
        }
        int color = WingType.colorOf(type, stack);
        IGulliverWings wings = (IGulliverWings) player;
        if (wings.gulliver$wingType() == type && wings.gulliver$wingColor() == color) {
            tell(player, Component.translatable("gulliver.wings.already"));
            return;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        set(player, type, color, true);
        //#if MC >= 26.3
        player.swing(hand, stack.getInteractAnimation(), true);
        //#else
        //$$ player.swing(hand, true);
        //#endif
        GulliverAdvancements.award(player, GulliverAdvancements.METAMORPHOSIS);
    }

    /** Gives (or with NONE takes away) a player's wings and tells everyone who can see them. */
    public static void set(ServerPlayer player, WingType type, int color, boolean sprout) {
        IGulliverWings wings = (IGulliverWings) player;
        wings.gulliver$setWings(type, color);
        wings.gulliver$setWingFlight(IGulliverWings.FOLDED);
        broadcast(player, sprout);
    }

    private static void broadcast(ServerPlayer player, boolean sprout) {
        Services.platform().sendToTrackingAndSelf(player, packet(player, sprout));
    }

    private static Payloads.Wings packet(Entity entity, boolean sprout) {
        IGulliverWings w = (IGulliverWings) entity;
        return new Payloads.Wings(entity.getId(), w.gulliver$wingType().id, w.gulliver$wingColor(),
                w.gulliver$wingFlight(), sprout);
    }

    // ---- flight ---------------------------------------------------------------

    /** The player's client says what its wings are doing now. */
    public static void onFlight(ServerPlayer player, byte state) {
        IGulliverWings wings = (IGulliverWings) player;
        if (state < IGulliverWings.FOLDED || state > IGulliverWings.DASH) return;
        if (IGulliverWings.flying(state) && !mayFly(player)) state = IGulliverWings.FOLDED;
        if (wings.gulliver$wingFlight() == state) return;
        wings.gulliver$setWingFlight(state);
        if (IGulliverWings.flying(state)) player.resetFallDistance();
        // Everyone else sees the wings change; the flyer's own client already knows.
        Payloads.Wings p = packet(player, false);
        for (ServerPlayer other : ((ServerLevel) player.level()).players()) {
            if (other != player && other.distanceToSqr(player) < 160.0D * 160.0D) {
                Services.platform().sendToPlayer(other, p);
            }
        }
    }

    public static boolean mayFly(Player player) {
        return enabled() && typeOf(player) != WingType.NONE && smallEnough(player)
                && !player.isPassenger() && !player.isSpectator() && player.isAlive();
    }

    /** Every server tick for every player. */
    public static void tick(ServerPlayer player) {
        IGulliverWings wings = (IGulliverWings) player;
        if (!IGulliverWings.flying(wings.gulliver$wingFlight())) return;
        if (!mayFly(player)) {
            // Grown out of them, wings switched off, mounted: down you go (and the client is told).
            wings.gulliver$setWingFlight(IGulliverWings.FOLDED);
            broadcast(player, false);
            return;
        }
        player.resetFallDistance();
        // Beating wings make you hungry, more when climbing or darting.
        byte s = wings.gulliver$wingFlight();
        float cost = s == IGulliverWings.CLIMB || s == IGulliverWings.DASH ? 0.012F
                : s == IGulliverWings.GLIDE ? 0.002F : 0.006F;
        if (!player.getAbilities().instabuild) player.causeFoodExhaustion(cost);
        if (player.tickCount % 20 == 0) GulliverAdvancements.award(player, GulliverAdvancements.METAMORPHOSIS);
    }

    // ---- lifecycle ------------------------------------------------------------

    public static void onStartTracking(Entity entity, ServerPlayer tracker) {
        if (entity instanceof IGulliverWings w && w.gulliver$wingType() != WingType.NONE) {
            Services.platform().sendToPlayer(tracker, packet(entity, false));
        }
    }

    public static void onJoin(ServerPlayer player) {
        IGulliverWings w = (IGulliverWings) player;
        w.gulliver$setWingFlight(IGulliverWings.FOLDED);
        if (w.gulliver$wingType() != WingType.NONE) Services.platform().sendToPlayer(player, packet(player, false));
    }

    public static void onClone(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        IGulliverWings from = (IGulliverWings) oldPlayer;
        if (from.gulliver$wingType() == WingType.NONE) return;
        if (!alive && !GulliverConfig.INSTANCE.general.keepWingsOnDeath) return;
        ((IGulliverWings) newPlayer).gulliver$setWings(from.gulliver$wingType(), from.gulliver$wingColor());
    }

    /** After a respawn or a change of dimension: the new player entity's clients need the wings again. */
    public static void resend(ServerPlayer player) {
        IGulliverWings w = (IGulliverWings) player;
        w.gulliver$setWingFlight(IGulliverWings.FOLDED);
        if (w.gulliver$wingType() != WingType.NONE) broadcast(player, false);
    }

    private static void tell(ServerPlayer player, Component text) {
        //#if MC >= 26.1
        player.sendOverlayMessage(text);
        //#else
        //$$ player.displayClientMessage(text, true);
        //#endif
    }
}
