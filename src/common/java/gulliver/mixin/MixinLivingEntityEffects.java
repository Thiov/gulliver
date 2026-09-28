package gulliver.mixin;

import gulliver.effect.ResizingEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//#if MC >= 1.20.5
import java.util.Collection;
//#else
//$$ import org.spongepowered.asm.mixin.Unique;
//#endif

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
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) return;
        ResizingEffect.refreshPotionMultiplier(self, null);
    }
    //#else
    //$$ @Unique private boolean gulliver$potionDirty;
    //$$
    //$$ // removeAllEffects() (milk) and Forge's curePotionEffects call this
    //$$ // before taking the effect out of the map, one effect at a time, so
    //$$ // recompute on the next effect tick, once the removal is complete.
    //$$ @Inject(method = "onEffectRemoved", at = @At("RETURN"))
    //$$ private void gulliver$recomputeOnRemove(MobEffectInstance effect, CallbackInfo ci) {
    //$$     if (!((LivingEntity) (Object) this).level().isClientSide()) gulliver$potionDirty = true;
    //$$ }
    //$$
    //$$ @Inject(method = "tickEffects", at = @At("HEAD"))
    //$$ private void gulliver$recomputeWhenDirty(CallbackInfo ci) {
    //$$     if (!gulliver$potionDirty) return;
    //$$     gulliver$potionDirty = false;
    //$$     ResizingEffect.refreshPotionMultiplier((LivingEntity) (Object) this, null);
    //$$ }
    //#endif
}
