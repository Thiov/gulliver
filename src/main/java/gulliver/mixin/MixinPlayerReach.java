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
 * 1.6.4 GulliverEnvoy.canInteractWithLocatedContainer used
 * 'distSq <= 64.0 * player.getRangeMultiplier()'. The 64 was the squared
 * vanilla 8-block reach. getRangeMultiplier() was an ASM-injected method
 * we don't have a body for, but a faithful interpretation is sizeMultiplier^2
 * — which is identical to scaling the LINEAR reach by sizeMultiplier (since
 * 26.x's blockInteractionRange returns linear distance, not squared).
 *
 * The 1.6.4 LittleBlocks worldscale=8 branch is dropped per scope.
 */
@Mixin(Player.class)
public abstract class MixinPlayerReach {

    @Inject(method = "blockInteractionRange", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleBlockReach(CallbackInfoReturnable<Double> cir) {
        float m = ((IResizeableEntity) this).getSizeMultiplier();
        if (m == 1.0F) return;
        cir.setReturnValue(cir.getReturnValue() * m);
    }

    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void gulliver$scaleEntityReach(CallbackInfoReturnable<Double> cir) {
        float m = ((IResizeableEntity) this).getSizeMultiplier();
        if (m == 1.0F) return;
        cir.setReturnValue(cir.getReturnValue() * m);
    }
}
