package gulliver.network;

import gulliver.common.GulliverConfig;
import gulliver.init.GulliverEffects;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PacketHandlers {
    private PacketHandlers() {}

    public static void registerCommon() {
        var s2c = PayloadTypeRegistry.clientboundPlay();
        s2c.register(Payloads.EntitySize.TYPE, Payloads.EntitySize.CODEC.cast());
        s2c.register(Payloads.AttachEntitySpecial.TYPE, Payloads.AttachEntitySpecial.CODEC.cast());
        s2c.register(Payloads.GroundShock.TYPE, Payloads.GroundShock.CODEC.cast());

        var c2s = PayloadTypeRegistry.serverboundPlay();
        c2s.register(Payloads.ConsumeResizingItem.TYPE, Payloads.ConsumeResizingItem.CODEC.cast());

        // Server-side handler for ConsumeResizingItem.
        ServerPlayNetworking.registerGlobalReceiver(Payloads.ConsumeResizingItem.TYPE,
                (payload, ctx) -> {
                    var player = ctx.player();
                    if (!GulliverConfig.INSTANCE.general.enableDyeResizing) return;
                    InteractionHand hand = payload.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                    ItemStack stack = player.getItemInHand(hand);
                    var effect = stack.is(Items.CYAN_DYE) || stack.is(Items.RED_MUSHROOM) ? GulliverEffects.TINY
                               : stack.is(Items.PURPLE_DYE) || stack.is(Items.BROWN_MUSHROOM) ? GulliverEffects.HUGE
                               : null;
                    if (effect == null) return;
                    player.addEffect(new MobEffectInstance(effect, 200, 0));
                    CriteriaTriggers.CONSUME_ITEM.trigger(player, stack);
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    player.swing(hand, true);
                });
    }
}
