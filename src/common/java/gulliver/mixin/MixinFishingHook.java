package gulliver.mixin;

import gulliver.access.IGulliverHookInternal;
import gulliver.api.IResizeableEntity;
import gulliver.common.Grapple;
import gulliver.network.Payloads;
import gulliver.platform.Services;
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
 * Grappling hook (see Grapple). When a tiny angler's bobber touches a
 * block out of water the server anchors it there and tells the clients
 * (HookAnchor), which pin their copy too; reeling in an anchored bobber
 * pulls the angler to it. A hooked creature bigger than a tiny angler
 * pulls the angler onto it, and pullEntity's tug is scaled by size.
 */
@Mixin(FishingHook.class)
public abstract class MixinFishingHook implements IGulliverHookInternal {

    @Shadow private Entity hookedIn;

    @Shadow public abstract Player getPlayerOwner();

    @Unique private Vec3 gulliver$anchor;
    @Unique private Vec3 gulliver$prePull;

    @Override @Unique public Vec3 gulliver$getAnchor() { return gulliver$anchor; }
    @Override @Unique public void gulliver$setAnchor(Vec3 anchor) { gulliver$anchor = anchor; }

    // RETURN, not TAIL: tick returns early once something is hooked.
    @Inject(method = "tick", at = @At("RETURN"))
    private void gulliver$anchor(CallbackInfo ci) {
        FishingHook self = (FishingHook) (Object) this;
        if (self.isRemoved()) return;
        if (hookedIn != null) {
            // Something walked into the pinned hook: it follows that now.
            gulliver$anchor = null;
            return;
        }
        if (gulliver$anchor != null) {
            // Pinned: stay exactly where the hook bit in.
            self.setPos(gulliver$anchor.x, gulliver$anchor.y, gulliver$anchor.z);
            self.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (self.level().isClientSide()) return;
        Player owner = getPlayerOwner();
        if (owner == null || !Grapple.canGrapple(owner)) return;
        boolean touching = self.onGround() || self.horizontalCollision || self.verticalCollision;
        if (touching && !self.level().getFluidState(self.blockPosition()).is(FluidTags.WATER)) {
            gulliver$anchor = self.position();
            self.setDeltaMovement(Vec3.ZERO);
            Services.platform().sendToTrackingAndSelf(self, new Payloads.HookAnchor(
                    self.getId(), gulliver$anchor.x, gulliver$anchor.y, gulliver$anchor.z));
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

    @Inject(method = "pullEntity", at = @At("HEAD"))
    private void gulliver$beforePull(Entity target, CallbackInfo ci) {
        gulliver$prePull = target.getDeltaMovement();
    }

    /** Scale only the tug pullEntity added, not the target's own momentum. */
    @Inject(method = "pullEntity", at = @At("RETURN"))
    private void gulliver$scalePull(Entity target, CallbackInfo ci) {
        Vec3 before = gulliver$prePull;
        gulliver$prePull = null;
        Player owner = getPlayerOwner();
        if (owner == null || before == null) return;
        float scale = Grapple.pullScale(owner, target);
        if (scale != 1.0F) {
            Vec3 tug = target.getDeltaMovement().subtract(before);
            target.setDeltaMovement(before.add(tug.scale(scale)));
            target.hurtMarked = true;
        }
    }
}
