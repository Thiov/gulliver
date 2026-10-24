//#if MC < 26.3
package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.api.IResizeableLiving;
import gulliver.client.VisualSize;
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
 * after the camera-attached arm pose has been set up, at the end of
 * vanilla's own pushPose / popPose around the arm, so a scale applied at
 * its HEAD reaches the item draw and is dropped again with that scope.
 *
 * For 1st-person glide we don't try to mimic the 1.6.4 GL11 rotations
 * (those were authored against a pre-rotation OpenGL state that doesn't
 * map cleanly to PoseStack pre-multiply order). The paper is drawn in
 * the world instead (GliderPaperWorldRenderer), above the player's head,
 * and the hand-held copy is cancelled here.
 *
 * 26.3 split the renderer into extract and submit steps and renamed it;
 * MixinFirstPersonHandsAndItemsRenderer is the same hook for those versions.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer {

    /**
     * Hide both hands (and held items) in 1st person while the local
     * player is rafting. The lily-pad becomes the raft, so the hands
     * shouldn't appear cradling air or the un-rendered lily-pad.
     */
    @Inject(method = "submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void gulliver$hideHandsWhileRafting(float partialTicks, PoseStack pose,
                                                  SubmitNodeCollector buf,
                                                  net.minecraft.client.player.LocalPlayer player,
                                                  int light, CallbackInfo ci) {
        if (((IResizeableLiving) player).isRafting()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void gulliver$replaceFirstPersonGlide(LivingEntity entity, ItemStack stack,
                                                    ItemDisplayContext ctx, PoseStack pose,
                                                    SubmitNodeCollector buf, int light, CallbackInfo ci) {
        if (!ctx.firstPerson()) return;
        IResizeableLiving sized = (IResizeableLiving) entity;
        // Rafting: lily-pad is the raft (rendered separately) and the
        // hand is occupied "using" it — hide both items in 1st person.
        if (sized.isRafting()) {
            ci.cancel();
            return;
        }
        boolean gliding = sized.isGliding();
        boolean umbrella = sized.doesUmbrella();
        if (!gliding && !umbrella) {
            // 1st-person item scale: tiny → BIG, giant → small (matches
            // 3rd-person where the item looks oversized relative to a
            // tiny body). User wants this exact relative direction in
            // 1st person too. Use 1/sqrt(size), kept to the 1/8..8 range
            // of factors (VisualSize.heldItemScale):
            //   tiny  0.125 → 2.83× (item appears big in tiny POV)
            //   vanilla     → 1.0×
            //   giant 8     → 0.354× (item appears small in giant POV)
            // The scale is not pushed and popped around the draw: vanilla
            // already wraps every renderItem call in its own pushPose /
            // popPose, so nothing here can leave the stack unbalanced when
            // another mod cancels the draw after this point.
            float size = VisualSize.of(entity);
            if (size != 1.0F && !gulliver$proportional()) {
                float scale = VisualSize.heldItemScale(size);
                pose.scale(scale, scale, scale);
            }
            return;
        }
        // 1st person paper: rendering matrix conventions in modern MC
        // are too unreliable for the camera-relative custom positioning
        // we want (every approach either followed head pitch or
        // disappeared). Cancel vanilla rendering so the held item
        // doesn't appear — no paper visible in 1st person, but 3rd
        // person bone-attached rendering still works correctly.
        // Press F5 to see the parachute pose visually.
        ci.cancel();
    }

    /**
     * "proportional" held items (issue #5): the first-person view already
     * sees the world from the scaled eye, so items drawn at their normal
     * size are exactly in proportion to the body.
     */
    private static boolean gulliver$proportional() {
        return gulliver.common.GulliverConfig.INSTANCE.client.proportionalHeldItems();
    }
}
//#endif
