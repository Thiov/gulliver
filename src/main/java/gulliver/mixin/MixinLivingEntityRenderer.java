package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverEnvoy;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scale the rendered model by sizeMultiplier. Vanilla's pose-stack scale
 * comes from LivingEntityRenderState.scale which is populated each frame
 * by extractRenderState. Multiplying that field at @Inject RETURN means
 * every layer + the base model picks up the new scale uniformly without
 * needing to mixin the scale() method itself.
 *
 * The 1.6.4 mod's RenderPlayer override scaled the model by
 * getSizeMultiplier() at the start of doRender; the modern equivalent is
 * exactly this state.scale multiply.
 *
 * Also: 1.6.4 hide-in-flower behaviour. When an extra-tiny entity is
 * fully inside a flower bbox (per GulliverEnvoy.isEntityIntersectingPlant)
 * we skip the entire render. Mirrors the 1.6.4 RenderPlayer
 * `if (entity.isHidingInPlant()) return;` short-circuit.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("RETURN"),
            require = 0)
    private void gulliver$applyScale(LivingEntity entity, LivingEntityRenderState state,
                                      float partialTick, CallbackInfo ci) {
        float m = ((IResizeableEntity) entity).getSizeMultiplier();
        if (m != 1.0F) {
            state.scale *= m;
            // DO NOT scale state.ageScale — vanilla setupAttackAnimation
            // multiplies the punch arm's .x/.z offset by ageScale (5 *
            // ageScale, see HumanoidModel.setupAttackAnimation +85/+108).
            // The bone .x/.z is then scaled again at render time by
            // state.scale, so multiplying ageScale double-scales the
            // punch offset — tinies' arms compress into the body, giants'
            // arms fly outward. Leaving ageScale at 1.0 means the punch
            // offset stays at vanilla 5 units in bone-space, which then
            // scales correctly with state.scale at render.
        }
        // Carry glide / umbrella flags onto the state for HumanoidModel
        // setupAnim to read (state pattern hides entity from model path).
        if (state instanceof gulliver.access.IGlideRenderState g) {
            gulliver.api.IResizeableLiving sized = (gulliver.api.IResizeableLiving) entity;
            g.gulliver$setGliding(sized.isGliding());
            g.gulliver$setDoesUmbrella(sized.doesUmbrella());
            g.gulliver$setRafting(sized.isRafting());
            g.gulliver$setSizeMultiplier(sized.getSizeMultiplier());
            g.gulliver$setHandPassenger(((gulliver.access.IGulliverShoulderInternal) entity)
                    .gulliver$getHandEntity() != null);
        }
        // 1.6.4 verbatim: NO walkAnimationPos compensation. The anim runs
        // at vanilla cycle frequency. Tiny moves slower in world units so
        // anim slows; huge moves faster so anim speeds up. The model
        // itself is scaled by state.scale, so visually the swing
        // amplitude is body-proportional. This is exactly how 1.6.4
        // bbj.java handled it (no per-size adjustment in the model anim).
    }

    /**
     * Skip rendering the entity entirely when it's an extra-tiny inside a
     * plant. shouldRender is the canonical "should we draw this?" gate
     * that all LivingEntityRenderer subclasses inherit; setting return
     * value to false visually hides the model AND its layers, matching
     * the 1.6.4 hide-in-flower visual.
     */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void gulliver$hideInFlower(LivingEntity entity,
                                        net.minecraft.client.renderer.culling.Frustum frustum,
                                        double cameraX, double cameraY, double cameraZ,
                                        CallbackInfoReturnable<Boolean> cir) {
        IResizeableEntity sized = (IResizeableEntity) entity;
        if (!sized.isExtraTiny()) return;
        if (GulliverEnvoy.isEntityIntersectingPlant(entity)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Render a flat lily-pad raft under the player in 3rd-person view.
     * 1st person uses LilyRaftWorldRenderer (different code path because
     * the local player isn't passed through entity-render in 1st-person
     * camera mode).
     *
     * Geometry: ItemDisplayContext.NONE strips display transforms so we
     * get the bare 2D item quad. Rotate +90° around X to lay it flat
     * (the item is vertical by default). Uniform 1.5× scale — no Y
     * squish; previous 0.1 Y scale collapsed the already-flat item to
     * a single line.
     *
     * Pose state at @RETURN: LivingEntityRenderer.submit's pushPose /
     * popPose pair is balanced before super.submit fires, and we're
     * after super.submit too. So the pose is at the entity's WORLD
     * origin (set by EntityRenderDispatcher.submit's translate) with
     * NO body rotation applied. We must apply yaw ourselves —
     * otherwise the disc lies flat in a fixed world direction
     * regardless of player rotation, which read as "static, only
     * points one direction".
     */
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"),
            require = 0)
    private void gulliver$drawRaftLilypad(LivingEntityRenderState state,
                                           com.mojang.blaze3d.vertex.PoseStack pose,
                                           net.minecraft.client.renderer.SubmitNodeCollector buf,
                                           net.minecraft.client.renderer.state.level.CameraRenderState cam,
                                           CallbackInfo ci) {
        if (!(state instanceof gulliver.access.IGlideRenderState g)) return;
        boolean raft = g.gulliver$isRafting();
        boolean umbrella = !raft && g.gulliver$doesUmbrella();
        if (!raft && !umbrella) return;
        pose.pushPose();
        // Pose at this point: entity world origin (feet at y=0).
        // Raft: player is snap-positioned 0.4×size below the water
        // line, so lifting the disc by 0.4×size puts its center exactly
        // at the water surface — top breaks the surface the way a real
        // lily-pad floats.
        // Umbrella: disc hugs the top of the head (head top is
        // 1.8×scale) — user-tuned: 2.2 floated too high.
        pose.translate(0.0F, (raft ? 0.4F : 2.0F) * state.scale, 0.0F);
        // Body yaw — disc rotates with body, matching the 1st-person
        // path's `180 - yBodyRot` convention (same one
        // setupRotations applies to the model itself).
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - state.bodyRot));
        // Lay the item quad flat (it's vertical by default in NONE).
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));
        // Uniform scale tracking entity scale — the raft reads as a
        // vehicle (1.5×), the umbrella as a hand-held canopy (1.1×).
        float disc = (raft ? 1.5F : 1.1F) * state.scale;
        pose.scale(disc, disc, disc);
        // No manual recenter — ItemTransform.apply for the
        // NO_TRANSFORM branch (which is what NONE display context
        // resolves to) itself applies a pose.translate(-0.5, -0.5,
        // -0.5) inside the item submit. Adding our own -0.5 stacks
        // a second shift, which read in 3rd person as the disc
        // sitting 0.5×disc body-front-left of the feet. Without it,
        // the model is naturally centered on the pose origin.
        net.minecraft.world.item.ItemStack stack =
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LILY_PAD);
        net.minecraft.client.renderer.item.ItemStackRenderState itemState =
                new net.minecraft.client.renderer.item.ItemStackRenderState();
        net.minecraft.client.Minecraft.getInstance().getItemModelResolver().updateForTopItem(
                itemState, stack, net.minecraft.world.item.ItemDisplayContext.NONE,
                null, null, 0);
        itemState.submit(pose, buf, 15728880,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
