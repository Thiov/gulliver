package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.api.IResizeableLiving;
import gulliver.client.VisualSize;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1 first-person items: no hands while rafting, no hand item while
 * gliding or under the umbrella (the prop is drawn in the world), and the
 * classic 1/sqrt(size) item scale unless "proportional" is chosen. The
 * scale is applied without a push/pop pair of its own: vanilla wraps every
 * renderItem call in its own pushPose / popPose, so the stack cannot end
 * up unbalanced if another mod cancels the draw.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true, require = 0)
    private void gulliver$hideHandsWhileRafting(float partialTicks, PoseStack pose, MultiBufferSource.BufferSource buffers,
                                                  LocalPlayer player, int light, CallbackInfo ci) {
        if (((IResizeableLiving) player).isRafting()) ci.cancel();
    }

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void gulliver$scaleFirstPerson(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, boolean leftHand,
                                            PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!ctx.firstPerson()) return;
        IResizeableLiving sized = (IResizeableLiving) entity;
        if (sized.isRafting() || sized.isGliding() || sized.doesUmbrella()) {
            ci.cancel();
            return;
        }
        float size = VisualSize.of(entity);
        if (size == 1.0F || gulliver$proportional()) return;
        float scale = VisualSize.heldItemScale(size);
        pose.scale(scale, scale, scale);
    }

    private static boolean gulliver$proportional() {
        return gulliver.common.GulliverConfig.INSTANCE.client.proportionalHeldItems();
    }
}
