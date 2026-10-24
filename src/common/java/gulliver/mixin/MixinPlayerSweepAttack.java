//#if MC >= 1.21.11
package gulliver.mixin;

import gulliver.common.AttackContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The sweep of a sword hits the creatures next to the target and knocks
 * them back from a separate method that runs after hurtServer has
 * returned. On 1.21.11 and 26.1.2 that knockback carries no damage source
 * (26.2 passes one), so without this the sweeper's size was ignored and
 * a giant's sweep shoved like a vanilla player. The frame names the
 * attacker for any victim; each victim's own hurtServer pushes a more
 * specific frame on top while it is being hurt.
 */
@Mixin(Player.class)
public abstract class MixinPlayerSweepAttack {

    @Inject(method = "doSweepAttack", at = @At("HEAD"))
    private void gulliver$pushSweep(Entity target, float baseDamage, DamageSource source, float strength,
                                    CallbackInfo ci) {
        AttackContext.push((Entity) (Object) this, null);
    }

    @Inject(method = "doSweepAttack", at = @At("RETURN"))
    private void gulliver$popSweep(Entity target, float baseDamage, DamageSource source, float strength,
                                   CallbackInfo ci) {
        AttackContext.pop(null);
    }
}
//#endif
