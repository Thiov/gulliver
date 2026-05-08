package gulliver.mixin;

import gulliver.api.IResizeableEntity;
import gulliver.common.GulliverEnvoy;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.6.4 ka.java line 528-530: when a tiny player holds a pointy item
 * (sword/stick/tool), their interaction reach is boosted via
 * {@code rmult = Math.cbrt(getSizeMultiplier())} — i.e., reach lifted
 * back up well above the linear size-scaled value.
 *
 * Per the user's stated intent, the bump should give a tiny holding
 * stick/sword "the reach of a normal size-0.5 player" — so we promote
 * the result to 0.5 * base (independent of the player's actual size,
 * as long as they're below 0.5). This matches the mental model better
 * than cbrt at very small sizes (e.g., size 0.0625 + pointy: cbrt
 * gives 0.397, the 0.5 floor gives 0.5 — the user wants the floor).
 *
 * Applies to BOTH block and entity reach so right-clicking a chest and
 * attacking a mob both feel the same.
 *
 * Below the 0.5 threshold the player's attribute-scaled reach (linear,
 * size-driven, set in SizeAttributes) is too short to ever exceed
 * base * 0.5, so we replace the return value outright. No max() check
 * needed.
 */
@Mixin(Player.class)
public abstract class MixinPlayerPointyReach {

    @Inject(method = "blockInteractionRange", at = @At("RETURN"), cancellable = true)
    private void gulliver$pointyBlockReach(CallbackInfoReturnable<Double> cir) {
        Player self = (Player) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        if (sized.getSizeMultiplier() >= 0.5F) return;
        if (!GulliverEnvoy.holdingPointyItem(self)) return;
        AttributeInstance attr = self.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        double base = attr != null ? attr.getBaseValue() : 4.5D;
        cir.setReturnValue(base * 0.5D);
    }

    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void gulliver$pointyEntityReach(CallbackInfoReturnable<Double> cir) {
        Player self = (Player) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        if (sized.getSizeMultiplier() >= 0.5F) return;
        if (!GulliverEnvoy.holdingPointyItem(self)) return;
        AttributeInstance attr = self.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        double base = attr != null ? attr.getBaseValue() : 3.0D;
        cir.setReturnValue(base * 0.5D);
    }
}
