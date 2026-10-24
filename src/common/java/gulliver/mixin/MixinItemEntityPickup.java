package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
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
 * Implementation: at HEAD of playerTouch, cancel pickup unless the two
 * bounding boxes are within half a body width of each other, measured as
 * the real gap between the boxes in all three dimensions. That makes the
 * gate "you must be touching it", with a little forgiveness, whether the
 * tiny walks over the item, jumps onto it or stands on a slab beside it
 * (the old test compared feet positions, so any height difference ate the
 * whole allowance).
 *
 * Only for tinies (below 0.3, the same line as everything else tiny).
 * Anything larger picks up like vanilla: the old rule applied to every
 * size under 1.0 and cut a 0.99 player's range from 1.4 blocks to 0.4.
 */
@Mixin(ItemEntity.class)
public abstract class MixinItemEntityPickup {

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void gulliver$strictTouch(Player player, CallbackInfo ci) {
        if (!((IResizeableEntity) player).isTiny()) return;
        ItemEntity self = (ItemEntity) (Object) this;
        AABB body = player.getBoundingBox();
        AABB item = self.getBoundingBox();
        double gx = gulliver$gap(body.minX, body.maxX, item.minX, item.maxX);
        double gy = gulliver$gap(body.minY, body.maxY, item.minY, item.maxY);
        double gz = gulliver$gap(body.minZ, body.maxZ, item.minZ, item.maxZ);
        double allowed = player.getBbWidth() * 0.5D;
        if (gx * gx + gy * gy + gz * gz > allowed * allowed) ci.cancel();
    }

    /** Distance between two intervals on one axis; 0 when they overlap. */
    private static double gulliver$gap(double minA, double maxA, double minB, double maxB) {
        return Math.max(0.0D, Math.max(minA - maxB, minB - maxA));
    }
}
