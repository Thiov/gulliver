//#if MC >= 1.21.10
package gulliver.mixin.client;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Models are posed once more right before they are drawn; a monster
 * eating gets its arms (EatPose.applyMob) after that setupAnim too, or the
 * zombie's own arm pose would win at draw time.
 */
@Mixin(ModelFeatureRenderer.class)
public abstract class MixinModelFeatureRendererEatPose {

    //#if MC >= 26.2
    @Redirect(method = "prepareModel",
    //#else
    //$$ @Redirect(method = "renderModel",
    //#endif
              at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V"),
              require = 0)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void gulliver$poseMobEater(Model model, Object state) {
        model.setupAnim(state);
        gulliver.client.EatPose.afterSetupAnim(model, state);
    }
}
//#endif
