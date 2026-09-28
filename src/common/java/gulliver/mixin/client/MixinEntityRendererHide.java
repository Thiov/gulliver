package gulliver.mixin.client;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverEnvoy;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.6.4 hide-in-flower: an extra-tiny standing inside a flower, sapling
 * or flower pot isn't drawn at all. (shouldRender lives on
 * EntityRenderer; the old hook on LivingEntityRenderer never matched.)
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererHide {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void gulliver$hideInFlower(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LivingEntity
                && ((IResizeableEntity) entity).isExtraTiny()
                && GulliverEnvoy.isEntityIntersectingPlant(entity)) {
            cir.setReturnValue(false);
        }
    }
}
