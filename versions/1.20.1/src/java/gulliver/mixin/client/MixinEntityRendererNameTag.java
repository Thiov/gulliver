package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.api.IResizeableEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.20.1 name tags scale with their entity, pivoting on the tag anchor. */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererNameTag {

    private static float gulliver$scale(Entity entity) {
        if (!(entity instanceof LivingEntity)) return 1.0F;
        float m = ((IResizeableEntity) entity).getSizeMultiplier();
        return m == 1.0F ? 1.0F : Math.max(0.35F, Math.min(3.0F, (float) Math.sqrt(m)));
    }

    @Inject(method = "renderNameTag", at = @At("HEAD"))
    private void gulliver$push(Entity entity, Component name, PoseStack pose, MultiBufferSource buffers, int light,
                                CallbackInfo ci) {
        float s = gulliver$scale(entity);
        if (s == 1.0F) return;
        float y = entity.getNameTagOffsetY();
        pose.pushPose();
        pose.translate(0.0F, y, 0.0F);
        pose.scale(s, s, s);
        pose.translate(0.0F, -y, 0.0F);
    }

    @Inject(method = "renderNameTag", at = @At("RETURN"))
    private void gulliver$pop(Entity entity, Component name, PoseStack pose, MultiBufferSource buffers, int light,
                               CallbackInfo ci) {
        if (gulliver$scale(entity) != 1.0F) pose.popPose();
    }
}
