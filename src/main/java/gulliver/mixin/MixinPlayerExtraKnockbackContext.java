package gulliver.mixin;

import gulliver.common.AttackContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player.attack invokes target.knockback() in two stages:
 *   1. Inside hurtOrSimulate → hurtServer: vanilla's hit-knockback. Our
 *      MixinLivingEntityDamage HEAD inject sets AttackContext for this
 *      window, so MixinLivingEntityKnockback's @ModifyVariable scales
 *      strength by attackerSize/targetSize correctly.
 *   2. AFTER hurtServer returns: Player.causeExtraKnockback() — the
 *      sprint/sweep extra knockback. AttackContext was cleared by
 *      hurtServer's RETURN inject, so the knockback ModifyVariable
 *      falls into the "no attacker" branch (strength / targetSize),
 *      which doesn't apply attacker-size scaling. Result: a size-0.125
 *      tiny with a sword still produces full sprint-attack knockback.
 *
 * Fix: re-set AttackContext to `this` at HEAD of causeExtraKnockback,
 * clear at RETURN. Narrow window — covers only the extra-knockback
 * path without touching the in-hurtServer scaling.
 *
 * Bare-hand attacks don't trigger causeExtraKnockback (the sprint-bonus
 * branch in Player.attack gates on the weapon-knockback flag), which
 * matches the user's observation that bare-hand knockback DID scale
 * correctly while pointy attacks did not.
 */
@Mixin(Player.class)
public abstract class MixinPlayerExtraKnockbackContext {

    @Inject(method = "causeExtraKnockback", at = @At("HEAD"))
    private void gulliver$setAttackContext(Entity target, float strength, Vec3 vec,
                                             CallbackInfo ci) {
        AttackContext.set((Entity) (Object) this);
    }

    @Inject(method = "causeExtraKnockback", at = @At("RETURN"))
    private void gulliver$clearAttackContext(Entity target, float strength, Vec3 vec,
                                               CallbackInfo ci) {
        AttackContext.clear();
    }
}
