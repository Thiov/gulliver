package gulliver.mixin;

import gulliver.effect.ResizingEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * A resizing effect ended (ran out, milk, /effect clear): recompute the
 * potion multiplier from whatever resizing effects remain, instead of
 * snapping to 1.0 while another one is still running.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityEffects {

    //#if MC >= 1.20.5
    @Inject(method = "onEffectsRemoved", at = @At("RETURN"))
    private void gulliver$recomputeOnRemove(Collection<MobEffectInstance> effects, CallbackInfo ci) {
    //#else
    //$$ @Inject(method = "onEffectRemoved", at = @At("RETURN"))
    //$$ private void gulliver$recomputeOnRemove(MobEffectInstance effect, CallbackInfo ci) {
    //#endif
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) return;
        ResizingEffect.refreshPotionMultiplier(self, null);
    }
}
