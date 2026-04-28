package gulliver.mixin;

import gulliver.access.IGulliverEntityInternal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Belt-and-suspenders: also persist size fields directly on Player's
 * own addAdditionalSaveData / readAdditionalSaveData. Player calls
 * Avatar.addAdditionalSaveData via invokespecial which inherits to
 * LivingEntity's, so MixinLivingEntitySaveSize SHOULD fire — but if
 * that injection fails to attach, this redundant Player-level hook
 * guarantees persistence.
 */
@Mixin(Player.class)
public abstract class MixinPlayerSaveSize {

    @Inject(method = "addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V",
            at = @At("RETURN"))
    private void gulliver$saveSize(ValueOutput out, CallbackInfo ci) {
        IGulliverEntityInternal i = (IGulliverEntityInternal) this;
        out.putFloat("gulliver.sizeBase", i.gulliver$getSizeBaseMultiplier());
        out.putFloat("gulliver.sizeBaseDest", i.gulliver$getSizeBaseDestMultiplier());
        out.putFloat("gulliver.sizePotion", i.gulliver$getSizePotionMultiplier());
        out.putFloat("gulliver.sizeItem", i.gulliver$getSizeItemMultiplier());
    }

    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueInput;)V",
            at = @At("RETURN"))
    private void gulliver$loadSize(ValueInput in, CallbackInfo ci) {
        IGulliverEntityInternal i = (IGulliverEntityInternal) this;
        float base = in.getFloatOr("gulliver.sizeBase", 1.0F);
        // If the keys aren't present, getFloatOr returns the default 1.0F,
        // so we don't accidentally clobber any value already set.
        if (base != 1.0F || in.getFloatOr("gulliver.sizeBaseDest", 1.0F) != 1.0F
                         || in.getFloatOr("gulliver.sizePotion", 1.0F) != 1.0F
                         || in.getFloatOr("gulliver.sizeItem", 1.0F) != 1.0F) {
            i.gulliver$setSizeBaseMultiplier(base);
            i.gulliver$setSizeBaseDestMultiplier(in.getFloatOr("gulliver.sizeBaseDest", 1.0F));
            i.gulliver$setSizePotionMultiplier(in.getFloatOr("gulliver.sizePotion", 1.0F));
            i.gulliver$setSizeItemMultiplier(in.getFloatOr("gulliver.sizeItem", 1.0F));
            ((Player) (Object) this).refreshDimensions();
        }
    }
}
