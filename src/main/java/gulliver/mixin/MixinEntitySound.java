package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 1.6.4 EntityResizeableClientPlayerMP override:
 *   public void playSound(String name, float vol, float pitch) {
 *     super.playSound(name, vol * getSizeMultiplierRoot(), pitch);
 *   }
 *
 * Modern translation: ModifyArg on Entity#playSound(SoundEvent, float, float)
 * — scales the volume argument before the call forwards to the level.
 * Applies to every entity (1.6.4 only attached to the player class
 * because that's where their resize lived; we have full resize on every
 * entity).
 *
 * Scaling is asymmetric on purpose:
 *   size < 1 → × sqrt(size)   (1.6.4 verbatim — tinies are quieter)
 *   size > 1 → × size^1.5, capped at 5.0
 *
 * The steeper up-slope exists because AUDIBLE RANGE only grows once
 * the final volume exceeds 1.0 (range = 16 × volume, both for the
 * server's packet-broadcast radius and the client's attenuation), and
 * most entity sound bases are small: a cow's moo is 0.4, footsteps
 * 0.15. With the old sqrt both ways, a size-8 cow mooed at 1.13 —
 * barely 18 blocks. With size^1.5: size-8 moo → 5.0 (80 blocks),
 * size-8 footsteps → 3.4 (54 blocks, matching the ~29-block tremor
 * reach with margin), size-4 moo → 3.2 (51 blocks). You hear the
 * giant long before you see it.
 *
 * playSound(SoundEvent) without volume/pitch defaults internally and
 * doesn't go through the (SoundEvent, float, float) overload — vanilla
 * uses 1.0/1.0 for both directly. Skipping a separate hook for that
 * overload is intentional: silent ambient sounds on tinies would be
 * surprising. The 1.6.4 mod also only hooked the volume-bearing path.
 */
@Mixin(Entity.class)
public abstract class MixinEntitySound {

    @ModifyArg(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V",
               at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"),
               index = 6,
               require = 0)
    private float gulliver$scaleVolume(float vol) {
        IResizeableEntity sized = (IResizeableEntity) this;
        float size = sized.getSizeMultiplier();
        if (size == 1.0F) return vol;
        if (size > 1.0F) {
            // size^1.5 = size * root — see class doc for why the
            // up-slope is steeper than the 1.6.4 sqrt.
            return Math.min(vol * size * sized.getSizeMultiplierRoot(), 5.0F);
        }
        return vol * sized.getSizeMultiplierRoot();
    }
}
