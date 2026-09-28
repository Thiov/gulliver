package gulliver.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.api.IResizeableLiving;
import gulliver.common.GulliverEnvoy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 1.20.1 renderer helpers: which arm holds the active prop, and the flat
 * lily-pad disc drawn as a raft under the feet or an umbrella over the
 * head (shared by the third-person entity renderer and the first-person
 * world pass).
 */
public final class PropRender {
    private PropRender() {}

    public static HumanoidArm propArm(LivingEntity entity) {
        IResizeableLiving sized = (IResizeableLiving) entity;
        if (sized.isRafting() || sized.doesUmbrella()) {
            return GulliverEnvoy.armHolding(entity, GulliverEnvoy::isItemUmbrella);
        }
        if (sized.isGliding()) return GulliverEnvoy.armHolding(entity, GulliverEnvoy::isGlideableItem);
        return null;
    }

    /** Pose at the entity's feet (world-aligned, no body rotation applied). */
    public static void lilyDisc(PoseStack pose, MultiBufferSource buffers, LivingEntity entity,
                                float bodyYaw, boolean raft, int light) {
        float scale = ((IResizeableLiving) entity).getSizeMultiplier();
        pose.pushPose();
        pose.translate(0.0F, (raft ? 0.4F : 2.0F) * scale, 0.0F);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - bodyYaw));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));
        float disc = (raft ? 1.5F : 1.1F) * scale;
        pose.scale(disc, disc, disc);
        Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(Items.LILY_PAD),
                ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), 0);
        pose.popPose();
    }

    public static float bodyYaw(LivingEntity entity, float partialTick) {
        return Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
    }
}
