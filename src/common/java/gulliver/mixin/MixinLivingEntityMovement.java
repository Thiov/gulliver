package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.api.IResizeableLiving;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Verbatim port of the 1.6.4 walk-speed + jump-power scaling.
 *
 * Walk speed (getSpeed) — 1.6.4 of.java line 2638 multiplies the friction-
 * adjusted forward speed by getSizeMovementMultiplier(): sqrt(size) when
 * !isWeighted, else linear size. Sqrt scaling preserves "stride per body
 * length per second" — a 0.25x player crosses 0.5 vanilla-blocks per
 * second instead of 0.25, which feels like the same relative gait.
 *
 * Jump (getJumpPower) — 1.6.4 of.java lines 2451-2489. Five branches:
 *
 *   if size == 1:                 y = 0.42 (vanilla, untouched)
 *   else:
 *     y = 0.375 + (size <= 1 ? size : sqrt(size)) * 0.045
 *     if isWeighted || (isSticky && isTiny):
 *                                 y *= sqrt(size)
 *     else if isJumping (ah() && reasons): if size > 1: y *= sqrt(size)
 *     else if size > 1:           y *= sqrt(sqrt(size))   [size^0.25]
 *     else if isTiny && !sprinting && (isPlayer || ...):
 *                                 y *= cbrt(sqrt(size))   [size^(1/6)]
 *
 *   + jump-boost: y += (amp+1) * 0.1 * sqrt(sqrt(size))
 *
 * The 1.6.4 isJumping / sprinting branches gate on flags we can't
 * directly read at the getJumpPower call site (the decision happens in
 * the bytecode-injected jumpFromGround). We use isSprinting directly and
 * always treat "jumping" as true when applicable (the only effect would
 * be using sqrt instead of size^0.25 when size > 1 — both lift huge
 * entities, the difference is just whether the giant goes higher when
 * spamming jump).
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityMovement {

    @Inject(method = "getJumpPower()F", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleJump(CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) this;
        float size = sized.getSizeMultiplier();
        if (size == 1.0F) return;

        float root = sized.getSizeMultiplierRoot();
        float y;
        // 1.6.4 of.java line 2458 — non-vanilla branch always; vanilla
        // baseline 0.42 corresponds to the size==1 short-circuit above.
        y = 0.375F + (size <= 1.0F ? size : root) * 0.045F;

        boolean weighted = sized.isWeighted();
        boolean sticky = sized.isSticky();
        boolean tiny = sized.isTiny();
        boolean huge = size > 1.0F;
        boolean sprinting = self.isSprinting();

        if (weighted || (sticky && tiny)) {
            y *= root;
        } else if (huge) {
            // Subsumes both the "isJumping && size > 1 -> *= root" and the
            // "size > 1 -> *= sqrt(root)" branches; we use sqrt(root) =
            // size^0.25 as the conservative choice (matches 1.6.4 fall-
            // through path when not actively jumping-up).
            y *= (float) Math.sqrt(root);
        } else if (tiny && !sprinting && self instanceof Player) {
            y *= (float) Math.cbrt(root);
        }

        // The formula above is the 1.6.4 size curve for a plain jump (0.42
        // at size 1). Apply it as a factor on vanilla's own value, so honey
        // blocks, the jump_strength attribute and other mods' changes still
        // count; the jump-boost part is scaled separately by size^0.25
        // (1.6.4 of.java 2487-2489).
        float boost = self.getJumpBoostPower();
        float plain = cir.getReturnValueF() - boost;
        y = plain * (y / 0.42F) + boost * (float) Math.sqrt(root);

        // Reconstructed sneak-jump (1.6.4 ASM patch invisible in JDCore):
        // shift+space lifts higher than plain jump. Only from the ground, so
        // it doesn't compound during the same airborne arc.
        if (self.isShiftKeyDown() && self.onGround()) {
            y *= 1.5F;
        }

        cir.setReturnValue(y);
    }
}
