package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.client.VisualSize;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1 name tags scale with their entity, exactly as much as its body
 * (#22), pivoting on the top of the head. Vanilla puts the tag half a block
 * above the head (getNameTagOffsetY = height + 0.5); pivoting on the head
 * rather than on the tag scales that gap too, so a tiny's tag sits right
 * above it instead of floating many body heights higher. The head itself
 * comes from the hitbox (which has already changed size), so it is carried
 * along with the drawn body while a resize glides.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererNameTag {

    /** Whether HEAD pushed, so RETURN pops exactly what was pushed. */
    @Unique private boolean gulliver$pushed;

    @Unique
    private static float gulliver$scale(Entity entity) {
        if (!(entity instanceof LivingEntity)) return 1.0F;
        return VisualSize.nameTagScale(VisualSize.of(entity));
    }

    @Inject(method = "renderNameTag", at = @At("HEAD"), require = 0)
    private void gulliver$push(Entity entity, Component name, PoseStack pose, MultiBufferSource buffers, int light,
                                CallbackInfo ci) {
        float s = gulliver$scale(entity);
        float follow = entity instanceof LivingEntity ? VisualSize.of(entity) / VisualSize.logical(entity) : 1.0F;
        gulliver$pushed = s != 1.0F || follow != 1.0F;
        if (!gulliver$pushed) return;
        float y = entity.getBbHeight();
        pose.pushPose();
        // Scale about the top of the head, and move it with the drawn body.
        pose.translate(0.0F, y * follow, 0.0F);
        pose.scale(s, s, s);
        pose.translate(0.0F, -y, 0.0F);
    }

    @Inject(method = "renderNameTag", at = @At("RETURN"), require = 0)
    private void gulliver$pop(Entity entity, Component name, PoseStack pose, MultiBufferSource buffers, int light,
                               CallbackInfo ci) {
        if (!gulliver$pushed) return;
        gulliver$pushed = false;
        pose.popPose();
    }
}
