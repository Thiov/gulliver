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
 * After playtest feedback (4(338)): 0.5*base wasn't enough for a
 * size-0.125 tiny to actually hit a size-1 mob standing next to them.
 * The user's intent — "with a stick/sword, a tiny should be able to
 * hit a normal mob, and a size-1 should be able to hit a size-8" —
 * argues for restoring full vanilla reach when pointy. We now return
 * the un-modified base value, ignoring the size scaling entirely.
 *
 * Applies to BOTH block and entity reach so right-clicking a chest and
 * attacking a mob both feel the same.
 *
 * Below the 0.5 threshold the player's attribute-scaled reach (linear,
 * size-driven, set in SizeAttributes) is too short to ever exceed
 * base, so we replace the return value outright. No max() check needed.
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
        cir.setReturnValue(base);
    }

    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void gulliver$pointyEntityReach(CallbackInfoReturnable<Double> cir) {
        Player self = (Player) (Object) this;
        IResizeableEntity sized = (IResizeableEntity) self;
        if (sized.getSizeMultiplier() >= 0.5F) return;
        if (!GulliverEnvoy.holdingPointyItem(self)) return;
        AttributeInstance attr = self.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        double base = attr != null ? attr.getBaseValue() : 3.0D;
        cir.setReturnValue(base);
    }
}
