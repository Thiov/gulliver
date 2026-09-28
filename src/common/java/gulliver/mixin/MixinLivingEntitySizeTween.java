package gulliver.mixin;

import gulliver.access.IGulliverEntityInternal;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smooth resize: each tick, lerp sizeBaseMultiplier toward
 * sizeBaseDestMultiplier so a /doublesize or potion drink grows the
 * entity over ~half a second instead of snapping instantly.
 *
 * Lerp formula: linear, 0.05 per tick of the destination-vs-current
 * delta. That's ~20 ticks (1 second) regardless of step magnitude — the
 * 1.6.4 mod's exact rate isn't visible from the JDCore source but a
 * one-second feel matches the "Alice growing" tone of the source mod.
 *
 * Snap to dest when |diff| < 0.001 to avoid endless tiny lerps. Each
 * tick the tween updates the base, refreshes dimensions, but does NOT
 * re-broadcast — the destination is broadcast once at setBaseSize time
 * and clients run their own lerp using the same formula starting from
 * their own stored base.
 *
 * Runs on both server and client (LivingEntity.tick fires on both).
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntitySizeTween {

    @Inject(method = "tick", at = @At("RETURN"))
    private void gulliver$tweenSize(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        IGulliverEntityInternal access = (IGulliverEntityInternal) self;
        float base = access.gulliver$getSizeBaseMultiplier();
        float dest = access.gulliver$getSizeBaseDestMultiplier();

        // Tween was removed — setBaseSize now snaps directly. This branch
        // only triggers from external code paths (NBT load, COPY_FROM,
        // potion/item resize) that may set base/dest separately. Snap
        // base to dest in one tick so we never enter a long-tween state.
        if (base != dest) {
            access.gulliver$setSizeBaseMultiplier(dest);
            self.refreshDimensions();
        }

        // Re-apply size-based attribute modifiers each tick. Cheap if
        // size hasn't changed (modifier replace is a no-op if amount
        // matches). Catches edge cases where attributes are reset
        // mid-game (re-equip armor, dimension change, etc.).
        float liveSize = ((gulliver.api.IResizeableEntity) self).getSizeMultiplier();
        gulliver.common.SizeAttributes.applyForSize(self, liveSize);
    }
}
