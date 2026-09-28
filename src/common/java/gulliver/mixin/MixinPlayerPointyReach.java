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
 * back up above the linear size-scaled value.
 *
 * Per user intent — "the reach of a normal size-0.5 player" — we
 * promote pointy reach to base*0.5. The earlier 4(339) experiment of
 * full base*1.0 was too long: a size-0.125 tiny could hit blocks
 * across the room. The size-0.125 + sword case still needs to be able
 * to damage a size-1 mob, but THAT is gated by the damage-side fix in
 * MixinLivingEntityDamage (immunity-bypass on pointy + cbrt scaling),
 * not by reach. With base*0.5 the tiny reach reaches a size-1 mob's
 * close hitbox, and the damage path then lands a hit.
 *
 * Applies to BOTH block and entity reach so right-clicking a chest and
 * attacking a mob both feel the same.
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
