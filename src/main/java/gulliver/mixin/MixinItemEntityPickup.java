package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tighten item pickup for tinies. Vanilla picks up an item the moment
 * the player's bbox overlaps the item's bbox — but item bbox is fixed
 * (~0.25), so even a tiny player can scoop items as long as their
 * outer bbox edge clips the item bbox edge. 1.6.4 felt stricter:
 * tinies had to be ON the item.
 *
 * Implementation: at HEAD of playerTouch, cancel pickup unless the
 * player's center is within their own bbox-radius of the item center.
 * That makes the gate "you must overlap with your CENTER", not "any
 * edge contact".
 *
 * For sizeMultiplier >= 1 (vanilla and huge), no change.
 */
@Mixin(ItemEntity.class)
public abstract class MixinItemEntityPickup {

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void gulliver$strictTouch(Player player, CallbackInfo ci) {
        IResizeableEntity sized = (IResizeableEntity) player;
        if (sized.getSizeMultiplier() >= 1.0F) return;
        ItemEntity self = (ItemEntity) (Object) this;
        // Distance from player CENTER to item CENTER.
        double dist = player.distanceTo(self);
        // Allowed distance scales with player's body width (which is
        // already size-scaled via Phase 2 dimensions). Half-bbWidth ≈
        // half a body radius — so tinies need their center within a
        // fraction of a block of the item.
        double allowed = player.getBbWidth() * 0.5D;
        if (dist > allowed) ci.cancel();
    }
}
