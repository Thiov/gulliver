//#if MC >= 26.3
package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.api.IResizeableLiving;
import gulliver.client.VisualSize;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person hands and held items from 26.3 on (the renderer was
 * ItemInHandRenderer before: see MixinItemInHandRenderer). The renderer
 * now works from a render state, so the local player is looked up from
 * the client; every item it draws goes through submitArmWithItem, which
 * ends in ItemStackRenderState.submit inside vanilla's own pushPose /
 * popPose around the arm, so a scale applied just before that call is
 * dropped again with that scope.
 *
 * For 1st-person glide the paper is drawn in the world instead
 * (GliderPaperWorldRenderer), above the player's head, and the hand-held
 * copy is cancelled here.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class MixinFirstPersonHandsAndItemsRenderer {

    /**
     * Hide both hands (and held items) in 1st person while the local
     * player is rafting. The lily-pad becomes the raft, so the hands
     * shouldn't appear cradling air or the un-rendered lily-pad.
     */
    @Inject(method = "submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void gulliver$hideHandsWhileRafting(float partialTicks, PoseStack pose,
                                                  SubmitNodeCollector buf, PlayerRenderState player,
                                                  FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
        LocalPlayer local = Minecraft.getInstance().player;
        if (local != null && ((IResizeableLiving) local).isRafting()) {
            ci.cancel();
        }
    }

    @Redirect(method = "submitArmWithItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"),
            require = 0)
    private void gulliver$firstPersonItem(ItemStackRenderState item, PoseStack pose,
                                           SubmitNodeCollector buf, int light, int overlay, int seed) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            IResizeableLiving sized = (IResizeableLiving) player;
            // Rafting: lily-pad is the raft (rendered separately) and the
            // hand is occupied "using" it — hide both items in 1st person.
            // Gliding / umbrella: no paper in 1st person, but 3rd person
            // bone-attached rendering still works correctly.
            if (sized.isRafting() || sized.isGliding() || sized.doesUmbrella()) return;
            // 1st-person item scale: tiny → BIG, giant → small, 1/sqrt(size)
            // kept to the 1/8..8 range of factors (VisualSize.heldItemScale).
            float size = VisualSize.of(player);
            if (size != 1.0F && !gulliver$proportional()) {
                float scale = VisualSize.heldItemScale(size);
                pose.scale(scale, scale, scale);
            }
        }
        item.submit(pose, buf, light, overlay, seed);
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
