package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverConfig;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A creeper's blast follows its size (#16): the radius is multiplied by it,
 * so a size-2 creeper blows a crater twice as wide and a size-1/8 one makes
 * a pop that barely scorches what touches it. Explosion damage already
 * grows with the radius (vanilla: 7 x diameter at the centre, falling off
 * with distance and cover), so a bigger creeper also hits harder, and the
 * attacker-size damage factor is left out for creeper blasts
 * (MixinLivingEntityDamage) so the size doesn't count twice. The target's
 * own size still does.
 *
 * The radius is held to 48 (a size-16 creeper's), which already levels a
 * hill; past that an explosion costs more than it shows. Charged creepers
 * double it as in vanilla, before the cap. general.creeperExplosionScaling
 * off restores vanilla blasts.
 */
@Mixin(Creeper.class)
public abstract class MixinCreeperExplosion {

    @Unique private static final float GULLIVER$MAX_RADIUS = 48.0F;

    @ModifyArg(method = "explodeCreeper",
               at = @At(value = "INVOKE",
                        //#if MC >= 1.21.2
                        target = "Lnet/minecraft/server/level/ServerLevel;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)V"),
                        //#else
                        //$$ target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"),
                        //#endif
               index = 4, require = 0)
    private float gulliver$scaleRadius(float radius) {
        if (!GulliverConfig.INSTANCE.general.creeperExplosionScaling) return radius;
        float size = ((IResizeableEntity) this).getSizeMultiplier();
        if (size == 1.0F || !(size > 0.0F)) return radius;
        return Math.min(radius * size, Math.max(radius, GULLIVER$MAX_RADIUS));
    }
}
