//#if MC >= 1.21.10
package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Wind charges, breezes and the mace's wind burst: the push of the burst
 * scales with 1 / size, between 0.25x and 3x, so tinies are launched and
 * giants barely stir. Only these bursts, not TNT or creepers: they are the
 * explosions that only trigger blocks (BlockInteraction.TRIGGER_BLOCK),
 * and nothing else in the game uses that.
 *
 * The factor goes into the knockback multiplier the explosion asks its
 * damage calculator for, before the push vector is built. That vector is
 * what the entity is pushed with and, for players, what the explosion
 * packet carries to the client, so the player is flung by the scaled
 * value too, not just the server's copy.
 */
@Mixin(ServerExplosion.class)
public abstract class MixinServerExplosionWind {

    // NeoForge and Forge add an overload, hurtEntities(List), that holds the body and leave the
    // no-argument one as a stub; a bare name is ambiguous between the two there. Vanilla has
    // only the first. Mixin takes whichever of the listed methods exist.
    @Redirect(method = {"hurtEntities()V", "hurtEntities(Ljava/util/List;)V"},
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/level/ExplosionDamageCalculator;getKnockbackMultiplier(Lnet/minecraft/world/entity/Entity;)F"))
    private float gulliver$scaleWindBurst(ExplosionDamageCalculator calculator, Entity entity) {
        float multiplier = calculator.getKnockbackMultiplier(entity);
        if (((ServerExplosion) (Object) this).getBlockInteraction() != Explosion.BlockInteraction.TRIGGER_BLOCK) {
            return multiplier;
        }
        float size = ((IResizeableEntity) entity).getSizeMultiplier();
        if (!(size > 0.0F)) return multiplier;
        return multiplier * Mth.clamp(1.0F / size, 0.25F, 3.0F);
    }
}
//#endif
