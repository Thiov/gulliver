package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.Grapple;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Grappling hook (see Grapple). Server-side: when a tiny angler's bobber
 * touches a block out of water it anchors there; reeling in an anchored
 * bobber pulls the angler to it. A hooked creature bigger than a tiny
 * angler pulls the angler onto it, and pullEntity is scaled by size.
 */
@Mixin(FishingHook.class)
public abstract class MixinFishingHook {

    @Shadow private Entity hookedIn;

    @Shadow public abstract Player getPlayerOwner();

    @Unique private Vec3 gulliver$anchor;

    @Inject(method = "tick", at = @At("TAIL"))
    private void gulliver$anchor(CallbackInfo ci) {
        FishingHook self = (FishingHook) (Object) this;
        if (self.level().isClientSide() || self.isRemoved()) return;
        if (gulliver$anchor != null) {
            // Pinned: stay exactly where the hook bit in.
            self.setPos(gulliver$anchor.x, gulliver$anchor.y, gulliver$anchor.z);
            self.setDeltaMovement(Vec3.ZERO);
            return;
        }
        Player owner = getPlayerOwner();
        if (owner == null || hookedIn != null || !Grapple.canGrapple(owner)) return;
        boolean touching = self.onGround() || self.horizontalCollision || self.verticalCollision;
        if (touching && !self.level().getFluidState(self.blockPosition()).is(FluidTags.WATER)) {
            gulliver$anchor = self.position();
            self.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Inject(method = "retrieve", at = @At("HEAD"), cancellable = true)
    private void gulliver$reelIn(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
        FishingHook self = (FishingHook) (Object) this;
        Player owner = getPlayerOwner();
        if (owner == null || self.level().isClientSide()) return;
        if (gulliver$anchor != null) {
            Grapple.pullToward(owner, gulliver$anchor);
            self.discard();
            cir.setReturnValue(1);
            return;
        }
        // Hooked a creature at least twice our size: climb it instead of
        // dragging it (vanilla still gives it its tug).
        if (hookedIn != null && Grapple.canGrapple(owner)
                && ((IResizeableEntity) hookedIn).getSizeMultiplier()
                    >= ((IResizeableEntity) owner).getSizeMultiplier() * 2.0F) {
            Grapple.pullToward(owner, hookedIn.position().add(0.0D, hookedIn.getBbHeight() * 0.8D, 0.0D));
        }
    }

    @Inject(method = "pullEntity", at = @At("RETURN"))
    private void gulliver$scalePull(Entity target, CallbackInfo ci) {
        Player owner = getPlayerOwner();
        if (owner == null) return;
        float scale = Grapple.pullScale(owner, target);
        if (scale != 1.0F) {
            target.setDeltaMovement(target.getDeltaMovement().scale(scale));
            target.hurtMarked = true;
        }
    }
}
