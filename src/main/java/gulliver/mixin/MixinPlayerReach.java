package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scales the player's block- and entity-interaction range by sizeMultiplier
 * so chests, furnaces, and entity-pickups match a resized player's arm
 * length proportionally.
 *
 * 1.6.4 nn.java line 304 getRangeMultiplier:
 *   size >= 1 -> linear size; else sqrt(size).
 * Tinies get a softer reach penalty than full linear scaling — a 0.25x
 * player reaches half-vanilla rather than quarter-vanilla, which the
 * 1.6.4 mod arrived at empirically as the "feels right" curve.
 *
 * The 1.6.4 LittleBlocks worldscale=8 branch is dropped per scope.
 */
@Mixin(Player.class)
public abstract class MixinPlayerReach {

    @Inject(method = "blockInteractionRange", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleBlockReach(CallbackInfoReturnable<Double> cir) {
        float m = ((IResizeableEntity) this).getSizeMultiplier();
        if (m == 1.0F) return;
        float r = ((IResizeableEntity) this).getRangeMultiplier();
        cir.setReturnValue(cir.getReturnValue() * r);
    }

    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleEntityReach(CallbackInfoReturnable<Double> cir) {
        float m = ((IResizeableEntity) this).getSizeMultiplier();
        if (m == 1.0F) return;
        float r = ((IResizeableEntity) this).getRangeMultiplier();
        cir.setReturnValue(cir.getReturnValue() * r);
    }
}
