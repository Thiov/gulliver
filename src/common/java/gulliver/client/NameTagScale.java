//#if MC >= 1.21.2
package gulliver.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * Name tags grow and shrink with their entity (square root of the size,
 * so a tiny's tag stays readable and a titan's doesn't fill the sky). The
 * tag's anchor already moves with the resized hitbox, so the scale pivots
 * on that anchor rather than on the entity's feet.
 */
public final class NameTagScale {
    private NameTagScale() {}

    public static float of(EntityRenderState state) {
        if (!(state instanceof LivingEntityRenderState ls) || ls.scale == 1.0F || state.nameTagAttachment == null) {
            return 1.0F;
        }
        return Math.max(0.35F, Math.min(3.0F, (float) Math.sqrt(ls.scale)));
    }

    public static void push(EntityRenderState state, PoseStack pose) {
        float s = of(state);
        if (s == 1.0F) return;
        Vec3 a = state.nameTagAttachment;
        pose.pushPose();
        pose.translate((float) a.x, (float) a.y, (float) a.z);
        pose.scale(s, s, s);
        pose.translate((float) -a.x, (float) -a.y, (float) -a.z);
    }

    public static void pop(EntityRenderState state, PoseStack pose) {
        if (of(state) != 1.0F) pose.popPose();
    }
}
//#endif
