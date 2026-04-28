package gulliver.mixin;

import gulliver.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla DyeItem.use() and MushroomBlockItem.use() return PASS when
 * there's no block target (air RMB). PASS means client never sends the
 * use-item packet to the server, so Fabric's UseItemCallback (which
 * fires server-side from packet handling) never runs for these items.
 *
 * Fix: client-side mixin into MultiPlayerGameMode.useItem at HEAD —
 * when the player presses RMB with cyan/purple dye OR red/brown
 * mushroom in hand, send our custom ConsumeResizingItem packet to
 * the server (which applies the size effect) and short-circuit with
 * SUCCESS so vanilla doesn't try (and fail) to route through the
 * stock item.use chain.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameModeUseItem {

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void gulliver$consumeResizing(Player player, InteractionHand hand,
                                           CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.CYAN_DYE) || stack.is(Items.PURPLE_DYE)
         || stack.is(Items.RED_MUSHROOM) || stack.is(Items.BROWN_MUSHROOM)) {
            ClientPlayNetworking.send(new Payloads.ConsumeResizingItem(hand == InteractionHand.MAIN_HAND));
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
