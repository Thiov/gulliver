package gulliver.mixin;

import gulliver.access.IGulliverEntityInternal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Persist size multiplier fields across world save/load. Without this,
 * a player at /basesize 4 reverts to 1.0 every time they reload the
 * world (the @Unique fields on the new ServerPlayer entity default to
 * 1.0 since they're not in vanilla NBT).
 *
 * Stores into the entity's value-output under gulliver-namespaced keys.
 * Reads back at load time. Doesn't broadcast SizeSync here — the
 * entity's later refreshDimensions / start-tracking handler covers
 * client sync.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntitySaveSize {

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
        i.gulliver$setSizeBaseMultiplier(in.getFloatOr("gulliver.sizeBase", 1.0F));
        i.gulliver$setSizeBaseDestMultiplier(in.getFloatOr("gulliver.sizeBaseDest", 1.0F));
        i.gulliver$setSizePotionMultiplier(in.getFloatOr("gulliver.sizePotion", 1.0F));
        i.gulliver$setSizeItemMultiplier(in.getFloatOr("gulliver.sizeItem", 1.0F));
        ((LivingEntity) (Object) this).refreshDimensions();
    }
}
