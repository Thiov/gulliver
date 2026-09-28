//#if MC >= 1.21.11
package gulliver.mixin;

import gulliver.common.AttackContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 * Fix: push `this` as the attacker at HEAD of causeExtraKnockback and
 * pop it at RETURN. Hooked on LivingEntity too, so giant MOBS' extra
 * knockback scales as well (Player's override never calls super). Narrow window — covers only the extra-knockback
 * path without touching the in-hurtServer scaling.
 *
 * Bare-hand attacks don't trigger causeExtraKnockback (the sprint-bonus
 * branch in Player.attack gates on the weapon-knockback flag), which
 * matches the user's observation that bare-hand knockback DID scale
 * correctly while pointy attacks did not.
 */
@Mixin({LivingEntity.class, Player.class})
public abstract class MixinLivingEntityExtraKnockback {

    @Inject(method = "causeExtraKnockback", at = @At("HEAD"))
    private void gulliver$setAttackContext(Entity target, float strength, Vec3 vec,
                                             //#if MC >= 26.2
                                             net.minecraft.world.damagesource.DamageSource source, float f, boolean b,
                                             //#endif
                                             CallbackInfo ci) {
        AttackContext.push((Entity) (Object) this);
    }

    @Inject(method = "causeExtraKnockback", at = @At("RETURN"))
    private void gulliver$clearAttackContext(Entity target, float strength, Vec3 vec,
                                               //#if MC >= 26.2
                                               net.minecraft.world.damagesource.DamageSource source, float f, boolean b,
                                               //#endif
                                               CallbackInfo ci) {
        AttackContext.pop();
    }
}
//#endif
