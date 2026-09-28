package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.api.IResizeableEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1: shadows use the renderer's fixed radius (1.21 multiplies it by
 * the render-state scale), so scale it with the entity's size here.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcherShadow {

    @Unique private float gulliver$shadowScale = 1.0F;

    @Inject(method = "render", at = @At("HEAD"))
    private void gulliver$rememberSize(Entity entity, double x, double y, double z, float yaw, float partialTick,
                                        PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        gulliver$shadowScale = ((IResizeableEntity) entity).getSizeMultiplier();
    }

    @ModifyArg(method = "render",
               at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;FFLnet/minecraft/world/level/LevelReader;F)V"),
               index = 6)
    private float gulliver$scaleShadow(float radius) {
        return Math.min(32.0F, radius * gulliver$shadowScale);
    }
}
