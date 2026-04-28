package gulliver.common;

import gulliver.init.GulliverEffects;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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
 * 1.6.4 dye-resizing: drinking cyan dye applies the Tiny effect, drinking
 * purple dye applies Huge. Gated by GulliverConfig.general.enableDyeResizing
 * (defaults true). Mirrors GulliverConfigHelper enable-dye-resizing key.
 *
 * Implementation: UseItemCallback (right-click with item, no block/entity
 * target) — if held stack is cyan or purple dye and the config flag is
 * on, apply the effect, decrement stack, fire CONSUME_ITEM trigger so
 * the eat_me advancement awards.
 *
 * Effect duration 200 ticks (10 seconds) at amplifier 0 — same shape as
 * a typical short potion. The 1.6.4 mod's exact dye-resize duration
 * isn't visible from the JDCore decompile (the Drink/Eat handler bodies
 * lived in the bytecode-patched Item class) but a 10-second window
 * gives the player a brief 'shrink to fit' / 'grow to reach' moment
 * matching the Alice motif.
 */
public final class DyeResizing {
    private DyeResizing() {}

    public static void registerCommon() {
        UseItemCallback.EVENT.register((player, level, hand) -> tryDrink(player, level, hand));
        // Also catch the block-RMB path: when the player right-clicks on
        // a block (very common — even grass/dirt under feet), Fabric's
        // UseBlockCallback fires INSTEAD of UseItemCallback. If we don't
        // register here too, dyes do nothing on most surfaces.
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> tryDrink(player, level, hand));
    }

    private static InteractionResult tryDrink(Player player, Level level, InteractionHand hand) {
        if (!GulliverConfig.INSTANCE.general.enableDyeResizing) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.CYAN_DYE)) {
            apply(player, stack, level, hand, GulliverEffects.TINY);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (stack.is(Items.PURPLE_DYE)) {
            apply(player, stack, level, hand, GulliverEffects.HUGE);
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
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
