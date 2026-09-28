package gulliver.mixin;

import gulliver.effect.ResizingEffect;
import gulliver.access.IGulliverEntityInternal;
import gulliver.network.SizeSync;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * When a Gulliver resizing effect ends (duration runs out, /effect clear,
 * milk bucket, etc.), reset the entity's sizePotionMultiplier to 1.0 so
 * the size goes back to base × items only. Mirrors the 1.6.4
 * PotionResizing.finishEffect override (sizePotionMultiplier = 1.0F).
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityEffects {

    @Inject(method = "onEffectsRemoved", at = @At("HEAD"))
    private void gulliver$resetOnRemove(Collection<MobEffectInstance> effects, CallbackInfo ci) {
        boolean changed = false;
        for (MobEffectInstance inst : effects) {
            MobEffect e = inst.getEffect().value();
            if (e instanceof ResizingEffect) {
                changed = true;
                break;
            }
        }
        if (!changed) return;

        IGulliverEntityInternal sized = (IGulliverEntityInternal) this;
        if (sized.gulliver$getSizePotionMultiplier() == 1.0F) return;
        sized.gulliver$setSizePotionMultiplier(1.0F);
        LivingEntity self = (LivingEntity) (Object) this;
        self.refreshDimensions();
        SizeSync.broadcast(self);
    }
}
