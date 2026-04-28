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
 * — multiplies the volume argument by sizeMultiplierRoot before the call
 * forwards to the level. Applies to every entity (1.6.4 only attached to
 * the player class because that's where their resize lived; we have full
 * resize on every entity, so footsteps for a giant zombie are loud and
 * for a chicken sized down are nearly silent — same shape as 1.6.4 just
 * universal).
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
        float root = ((IResizeableEntity) this).getSizeMultiplierRoot();
        return root == 1.0F ? vol : vol * root;
    }
}
