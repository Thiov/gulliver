//#if MC >= 26.2
package gulliver.mixin.client;

import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The breath gust's ribbons go into the after-terrain phase with the
 * particles instead of the translucent custom geometry drawn before the
 * water, which covered them (BreathRibbonRenderer).
 */
@Mixin(SubmitNodeCollection.class)
public abstract class MixinSubmitNodeCollectionAfterTerrain {

    @Shadow @Final public SimpleFeatureRenderPhase afterTerrain;

    @Redirect(method = "submitCustomGeometry",
              at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                       target = "Lnet/minecraft/client/renderer/SubmitNodeCollection;translucentCustomGeometry:Lnet/minecraft/client/renderer/feature/phase/SimpleFeatureRenderPhase;"),
              require = 0)
    private SimpleFeatureRenderPhase gulliver$windAfterWater(SubmitNodeCollection self) {
        return gulliver.client.BreathRibbonRenderer.afterTerrain ? afterTerrain : self.translucentCustomGeometry;
    }
}
//#endif
