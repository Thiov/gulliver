package gulliver.common;

import gulliver.init.GulliverEffects;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * 1.6.4 had a complementary "eat mushroom" path alongside the brewing
 * recipe path: a player who right-clicks a red mushroom directly applies
 * the Tiny effect (mirroring the recipe's mushroom-as-modifier). Brown
 * mushroom mirrors as Huge.
 *
 * Shares the dye-resizing machinery: 200-tick (10 second) effect at amp 0,
 * stack shrink, eat_me CONSUME_ITEM trigger.
 *
 * Gated by GulliverConfig.general.enableDyeResizing — the same config
 * flag covers all "consume to resize" item paths, since the 1.6.4 mod
 * treated them as a single switch.
 */
public final class MushroomResizing {
    private MushroomResizing() {}

    public static void registerCommon() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!GulliverConfig.INSTANCE.general.enableDyeResizing) {
                return InteractionResult.PASS;
            }
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(Items.RED_MUSHROOM)) {
                apply(player, stack, level, hand, GulliverEffects.TINY);
                return InteractionResult.SUCCESS_SERVER;
            }
            if (stack.is(Items.BROWN_MUSHROOM)) {
                apply(player, stack, level, hand, GulliverEffects.HUGE);
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.PASS;
        });
    }

    private static void apply(Player player, ItemStack stack, Level level,
                              InteractionHand hand,
                              net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        if (level.isClientSide()) return;
        player.addEffect(new MobEffectInstance(effect, 200, 0));
        if (player instanceof ServerPlayer sp) {
            CriteriaTriggers.CONSUME_ITEM.trigger(sp, stack);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.swing(hand, true);
    }
}
