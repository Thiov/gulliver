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
 * or flower pot isn't drawn for anyone else (the camera's own entity
 * stays visible, so a third-person tiny doesn't vanish from itself). (shouldRender lives on
 * EntityRenderer; the old hook on LivingEntityRenderer never matched.)
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererHide {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void gulliver$hideInFlower(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                        //#if MC >= 26.3
                                        float partialTick,
                                        //#endif
                                        CallbackInfoReturnable<Boolean> cir) {
        // Hidden from everyone else; your own body stays drawn in third person.
        if (entity instanceof LivingEntity
                && entity != net.minecraft.client.Minecraft.getInstance().getCameraEntity()
                && ((IResizeableEntity) entity).isExtraTiny()
                && GulliverEnvoy.isEntityIntersectingPlant(entity)) {
            cir.setReturnValue(false);
        }
    }
}
