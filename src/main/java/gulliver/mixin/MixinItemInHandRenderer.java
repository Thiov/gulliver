package gulliver.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.api.IResizeableLiving;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person held-item path. ItemInHandRenderer.renderItem is called
 * after the camera-attached arm pose has been set up. We hook RETURN of
 * renderItem and apply scale/glide-pose modifications BEFORE the actual
 * draw — actually wait: we hook HEAD because renderItem itself calls
 * the item-model submission. Easier path: hook HEAD, push pose, scale
 * and glide-pose translate, the inner submission then runs scaled.
 *
 * For 1st-person glide we don't try to mimic the 1.6.4 GL11 rotations
 * (those were authored against a pre-rotation OpenGL state that doesn't
 * map cleanly to PoseStack pre-multiply order). Instead we translate
 * the item to a fixed overhead-and-forward position (above and slightly
 * in front of the camera origin), which visually places the paper as
 * if held above the player's head — same effective look as the original.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer {

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"))
    private void gulliver$pushFirstPerson(LivingEntity entity, ItemStack stack,
                                           ItemDisplayContext ctx, PoseStack pose,
                                           SubmitNodeCollector buf, int light, CallbackInfo ci) {
        if (!ctx.firstPerson()) return;
        IResizeableLiving sized = (IResizeableLiving) entity;
        boolean gliding = sized.isGliding();
        boolean umbrella = sized.doesUmbrella();
        float size = sized.getSizeMultiplier();
        if (!gliding && !umbrella && size == 1.0F) return;

        pose.pushPose();
        if (gliding || umbrella) {
            // The pose-stack at this point has the vanilla hand-position
            // baked in (translate to right-of-screen + tilt). To place the
            // paper at a clean OVERHEAD-AND-FORWARD position independent of
            // hand-pose, reset the local matrix to identity and translate
            // in pure camera-relative coords: +X right, +Y up, -Z forward.
            pose.last().pose().identity();
            pose.last().normal().identity();
            // Slightly above eye level, in front of player.
            pose.translate(0.0F, 0.4F, -0.6F);
            // Tilt 45° forward so the flat paper is visible from below
            // (otherwise we'd see only its edge from the player's POV).
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(45.0F));
            pose.scale(0.8F, 0.8F, 0.8F);
        }
        if (size != 1.0F) {
            float invRoot = 1.0F / (float) Math.sqrt(size);
            pose.scale(invRoot, invRoot, invRoot);
        }
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("RETURN"))
    private void gulliver$popFirstPerson(LivingEntity entity, ItemStack stack,
                                          ItemDisplayContext ctx, PoseStack pose,
                                          SubmitNodeCollector buf, int light, CallbackInfo ci) {
        if (!ctx.firstPerson()) return;
        IResizeableLiving sized = (IResizeableLiving) entity;
        boolean gliding = sized.isGliding();
        boolean umbrella = sized.doesUmbrella();
        float size = sized.getSizeMultiplier();
        if (!gliding && !umbrella && size == 1.0F) return;
        pose.popPose();
    }
}
