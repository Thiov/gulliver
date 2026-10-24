package gulliver.mixin.client;

import gulliver.client.VisualSize;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Name tags and shadows fade out at fixed distances (64 and 16 blocks,
 * half of that sneaking), measured from the camera to the entity with this
 * one method. A giant's name tag would vanish while the giant fills the
 * sky, and its shadow while it still darkens the ground around you, so for
 * entities bigger than normal the distance is divided by their size
 * (capped at 8: VisualSize.visibilityScale) — they stay readable from
 * proportionally farther away.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcherDistance {

    @Inject(method = "distanceToSqr(Lnet/minecraft/world/entity/Entity;)D", at = @At("RETURN"), cancellable = true,
            require = 0)
    private void gulliver$giantsStayVisible(Entity entity, CallbackInfoReturnable<Double> cir) {
        float scale = VisualSize.visibilityScale(entity);
        if (scale > 1.0F) cir.setReturnValue(cir.getReturnValueD() / ((double) scale * scale));
    }
}
