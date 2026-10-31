package gulliver.network;

import gulliver.common.GulliverConfig;
import gulliver.common.ShoulderHelper;
import gulliver.init.GulliverEffects;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-side handlers for Gulliver's serverbound packets. */
public final class PacketHandlers {
    private PacketHandlers() {}

    static void onConsumeResizingItem(ServerPlayer player, Payloads.ConsumeResizingItem payload) {
        if (!GulliverConfig.INSTANCE.general.enableDyeResizing) return;
        // Spectators neither hold nor eat anything; this can only be a forged packet.
        if (player.isSpectator()) return;
        InteractionHand hand = payload.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = player.getItemInHand(hand);
        boolean tiny = stack.is(Items.DYE.cyan()) || stack.is(Items.RED_MUSHROOM);
        boolean huge = stack.is(Items.DYE.purple()) || stack.is(Items.BROWN_MUSHROOM);
        if (!tiny && !huge) return;
        player.addEffect(new MobEffectInstance(tiny ? GulliverEffects.tiny() : GulliverEffects.huge(), 200, 0));
        CriteriaTriggers.CONSUME_ITEM.trigger(player, stack);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        //#if MC >= 26.3
        player.swing(hand, stack.getInteractAnimation(), true);
        //#else
        //$$ player.swing(hand, true);
        //#endif
    }

    static void onCarryAction(ServerPlayer player, Payloads.CarryAction payload) {
        switch (payload.action()) {
            case Payloads.CarryAction.CYCLE -> ShoulderHelper.cycleOrPickUp(player);
            case Payloads.CarryAction.THROW -> ShoulderHelper.throwHeld(player);
            case Payloads.CarryAction.DROP -> ShoulderHelper.drop(player);
            case Payloads.CarryAction.EAT -> {
                if (!player.isSpectator() && !gulliver.common.Appetite.bite(player)) setDown(player);
            }
            case Payloads.CarryAction.SET_DOWN -> {
                if (!player.isSpectator()) setDown(player);
            }
            case Payloads.CarryAction.BREATH -> gulliver.common.BreathGust.start(player);
            case Payloads.CarryAction.BREATH_STOP -> gulliver.common.BreathGust.stop(player);
            default -> { }
        }
    }

    private static void setDown(ServerPlayer player) {
        //#if MC >= 26.3
        // 26.3 clients no longer send a swing packet: show the set-down to onlookers from here.
        if (ShoulderHelper.detachHand(player) != null) {
            player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        }
        //#else
        //$$ ShoulderHelper.detachHand(player);
        //#endif
    }
}
