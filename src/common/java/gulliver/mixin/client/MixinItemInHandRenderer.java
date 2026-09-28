package gulliver.mixin.client;

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
     * One bit per first-person renderItem call in flight: did HEAD push?
     * RETURN pops exactly what HEAD pushed, even if the size or the config
     * (reloaded on the server thread) changed in between.
     */
    @org.spongepowered.asm.mixin.Unique private int gulliver$pushedBits;

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
    @Inject(method = "submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V",
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
            // 1st-person item scale: tiny → BIG, giant → small (matches
            // 3rd-person where the item looks oversized relative to a
            // tiny body). User wants this exact relative direction in
            // 1st person too. Use 1/sqrt(size):
            //   tiny  0.125 → 2.83× (item appears big in tiny POV)
            //   vanilla     → 1.0×
            //   giant 8     → 0.354× (item appears small in giant POV)
            float size = sized.getSizeMultiplier();
            boolean push = size != 1.0F && !gulliver$proportional();
            gulliver$pushedBits = (gulliver$pushedBits << 1) | (push ? 1 : 0);
            if (!push) return;
            pose.pushPose();
            float invRoot = 1.0F / (float) Math.sqrt(size);
            pose.scale(invRoot, invRoot, invRoot);
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

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("RETURN"))
    private void gulliver$popFirstPerson(LivingEntity entity, ItemStack stack,
                                          ItemDisplayContext ctx, PoseStack pose,
                                          SubmitNodeCollector buf, int light, CallbackInfo ci) {
        // Only calls HEAD let through reach here (cancelled ones return early).
        if (!ctx.firstPerson()) return;
        boolean pushed = (gulliver$pushedBits & 1) != 0;
        gulliver$pushedBits >>>= 1;
        if (pushed) pose.popPose();
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
