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

    /**
     * 1st-person glide paper rendering. Cancels vanilla renderItem and
     * submits the item ourselves with ItemDisplayContext.FIXED — that
     * context lays the item flat (item-frame transform), avoiding the
     * upright FIRST_PERSON_RIGHT_HAND display rotation that was making
     * the paper "stand up to the right" in 1st person.
     */
    /**
     * Hide both hands (and held items) in 1st person while the local
     * player is rafting. The lily-pad becomes the raft, so the hands
     * shouldn't appear cradling air or the un-rendered lily-pad.
     */
    @Inject(method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V",
            at = @At("HEAD"), cancellable = true)
    private void gulliver$hideHandsWhileRafting(float partialTicks, PoseStack pose,
                                                  SubmitNodeCollector buf,
                                                  net.minecraft.client.player.LocalPlayer player,
                                                  int light, CallbackInfo ci) {
        if (((IResizeableLiving) player).isRafting()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"), cancellable = true)
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
            // Non-glide first-person: don't apply any scaling. The
            // previous 1/sqrt(size) for tinies inflated the held-item /
            // hand model by 2.83× at size 0.125, filling the screen and
            // completely blocking the player's view. In 1st person the
            // hand IS the player's hand from their POV — vanilla scale
            // looks correct (everything is relative). Just pass-through.
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

    // RETURN handler removed — HEAD branch no longer pushes the pose,
    // so there's nothing to pop.
}
