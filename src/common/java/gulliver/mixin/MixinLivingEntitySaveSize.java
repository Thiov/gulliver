package gulliver.mixin;

import gulliver.access.IGulliverEntityInternal;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC >= 1.21.6
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//#else
//$$ import net.minecraft.nbt.CompoundTag;
//#endif

/**
 * Persists the size multipliers with the entity (players included:
 * Player's save/load call up into LivingEntity's). An entity loaded with
 * Gulliver data counts as size-initialised, so configured spawn sizes are
 * only ever rolled for brand-new entities.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntitySaveSize {

    //#if MC >= 1.21.6
    @Inject(method = "addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V", at = @At("RETURN"))
    private void gulliver$saveSize(ValueOutput out, CallbackInfo ci) {
        IGulliverEntityInternal i = (IGulliverEntityInternal) this;
        out.putFloat("gulliver.sizeBase", i.gulliver$getSizeBaseMultiplier());
        out.putFloat("gulliver.sizeBaseDest", i.gulliver$getSizeBaseDestMultiplier());
        out.putFloat("gulliver.sizePotion", i.gulliver$getSizePotionMultiplier());
        out.putFloat("gulliver.sizeItem", i.gulliver$getSizeItemMultiplier());
    }

    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueInput;)V", at = @At("RETURN"))
    private void gulliver$loadSize(ValueInput in, CallbackInfo ci) {
        float base = in.getFloatOr("gulliver.sizeBase", Float.NaN);
        if (Float.isNaN(base)) return; // no Gulliver data: a fresh or vanilla-saved entity
        gulliver$apply(base, in.getFloatOr("gulliver.sizeBaseDest", base),
                in.getFloatOr("gulliver.sizePotion", 1.0F), in.getFloatOr("gulliver.sizeItem", 1.0F));
    }
    //#else
    //$$ @Inject(method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    //$$ private void gulliver$saveSize(CompoundTag out, CallbackInfo ci) {
    //$$     IGulliverEntityInternal i = (IGulliverEntityInternal) this;
    //$$     out.putFloat("gulliver.sizeBase", i.gulliver$getSizeBaseMultiplier());
    //$$     out.putFloat("gulliver.sizeBaseDest", i.gulliver$getSizeBaseDestMultiplier());
    //$$     out.putFloat("gulliver.sizePotion", i.gulliver$getSizePotionMultiplier());
    //$$     out.putFloat("gulliver.sizeItem", i.gulliver$getSizeItemMultiplier());
    //$$ }
    //$$
    //$$ @Inject(method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    //$$ private void gulliver$loadSize(CompoundTag in, CallbackInfo ci) {
    //$$     if (!in.contains("gulliver.sizeBase")) return;
    //$$     float base = in.getFloat("gulliver.sizeBase");
    //$$     gulliver$apply(base, in.contains("gulliver.sizeBaseDest") ? in.getFloat("gulliver.sizeBaseDest") : base,
    //$$             in.contains("gulliver.sizePotion") ? in.getFloat("gulliver.sizePotion") : 1.0F,
    //$$             in.contains("gulliver.sizeItem") ? in.getFloat("gulliver.sizeItem") : 1.0F);
    //$$ }
    //#endif

    private void gulliver$apply(float base, float dest, float potion, float item) {
        IGulliverEntityInternal i = (IGulliverEntityInternal) this;
        // The live size starts at its destination: nothing to tween after a load.
        i.gulliver$setSizeBaseMultiplier(dest);
        i.gulliver$setSizeBaseDestMultiplier(dest);
        i.gulliver$setSizePotionMultiplier(potion);
        i.gulliver$setSizeItemMultiplier(item);
        i.gulliver$setSizeInitialized(true);
        ((LivingEntity) (Object) this).refreshDimensions();
    }
}
