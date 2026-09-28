package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.api.IResizeableLiving;
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
 * classic 1/sqrt(size) item scale unless "proportional" is chosen.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer {

    /** One bit per first-person renderItem call in flight: did HEAD push? */
    @org.spongepowered.asm.mixin.Unique private int gulliver$pushedBits;

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void gulliver$hideHandsWhileRafting(float partialTicks, PoseStack pose, MultiBufferSource.BufferSource buffers,
                                                  LocalPlayer player, int light, CallbackInfo ci) {
        if (((IResizeableLiving) player).isRafting()) ci.cancel();
    }

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void gulliver$scaleFirstPerson(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, boolean leftHand,
                                            PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!ctx.firstPerson()) return;
        IResizeableLiving sized = (IResizeableLiving) entity;
        if (sized.isRafting() || sized.isGliding() || sized.doesUmbrella()) {
            ci.cancel();
            return;
        }
        float size = sized.getSizeMultiplier();
        boolean push = size != 1.0F && !gulliver$proportional();
        gulliver$pushedBits = (gulliver$pushedBits << 1) | (push ? 1 : 0);
        if (!push) return;
        pose.pushPose();
        float invRoot = 1.0F / (float) Math.sqrt(size);
        pose.scale(invRoot, invRoot, invRoot);
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void gulliver$popFirstPerson(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, boolean leftHand,
                                          PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        // Only calls HEAD let through reach here (cancelled ones return early).
        if (!ctx.firstPerson()) return;
        boolean pushed = (gulliver$pushedBits & 1) != 0;
        gulliver$pushedBits >>>= 1;
        if (pushed) pose.popPose();
    }

    private static boolean gulliver$proportional() {
        return gulliver.common.GulliverConfig.INSTANCE.client.proportionalHeldItems();
    }
}
