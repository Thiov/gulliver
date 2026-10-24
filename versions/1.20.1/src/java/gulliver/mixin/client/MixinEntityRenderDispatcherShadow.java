package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.client.VisualSize;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1: shadows use the renderer's fixed radius (1.21 multiplies it by
 * the render-state scale), so scale it with the entity's drawn size here
 * (capped at 32 blocks, like 1.21), and let a giant's shadow fade out
 * proportionally farther away (vanilla: 16 blocks), as the 1.21 twin does
 * through EntityRenderDispatcher.distanceToSqr.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcherShadow {

    @Unique private float gulliver$shadowScale = 1.0F;
    @Unique private float gulliver$shadowFade = 1.0F;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void gulliver$rememberSize(Entity entity, double x, double y, double z, float yaw, float partialTick,
                                        PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        gulliver$shadowScale = VisualSize.renderScale(entity, partialTick);
        gulliver$shadowFade = VisualSize.visibilityScale(entity);
    }

    @ModifyConstant(method = "render", constant = @Constant(doubleValue = 256.0D), require = 0)
    private double gulliver$shadowFadeDistance(double distanceSq) {
        return distanceSq * gulliver$shadowFade * gulliver$shadowFade;
    }

    @ModifyArg(method = "render",
               at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;FFLnet/minecraft/world/level/LevelReader;F)V"),
               index = 6, require = 0)
    private float gulliver$scaleShadow(float radius) {
        return Math.min(32.0F, radius * gulliver$shadowScale);
    }
}
