package gulliver.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gulliver.client.GliderPaperWorldRenderer;
import gulliver.client.LilyRaftWorldRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person world-space props (glide paper, lily-pad raft/umbrella).
 * The local player isn't drawn in first person, so these are submitted
 * right after the level's entities, in camera-relative world space.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRendererSubmit {

    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void gulliver$submitProps(PoseStack pose, LevelRenderState state, SubmitNodeCollector buf,
                                      CallbackInfo ci) {
        GliderPaperWorldRenderer.submit(pose, buf);
        LilyRaftWorldRenderer.submit(pose, buf);
    }
}
