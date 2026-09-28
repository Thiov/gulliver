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
 * Player's save/load call up into LivingEntity's), and whether its spawn
 * size has been rolled, so configured spawn sizes are rolled exactly once.
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
        out.putBoolean("gulliver.sizeInit", i.gulliver$isSizeInitialized());
    }

    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueInput;)V", at = @At("RETURN"))
    private void gulliver$loadSize(ValueInput in, CallbackInfo ci) {
        float base = in.getFloatOr("gulliver.sizeBase", Float.NaN);
        if (Float.isNaN(base)) return; // no Gulliver data: a fresh or vanilla-saved entity
        gulliver$apply(base, in.getFloatOr("gulliver.sizeBaseDest", base),
                in.getFloatOr("gulliver.sizePotion", 1.0F), in.getFloatOr("gulliver.sizeItem", 1.0F),
                in.getBooleanOr("gulliver.sizeInit", true));
    }
    //#else
    //$$ @Inject(method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    //$$ private void gulliver$saveSize(CompoundTag out, CallbackInfo ci) {
    //$$     IGulliverEntityInternal i = (IGulliverEntityInternal) this;
    //$$     out.putFloat("gulliver.sizeBase", i.gulliver$getSizeBaseMultiplier());
    //$$     out.putFloat("gulliver.sizeBaseDest", i.gulliver$getSizeBaseDestMultiplier());
    //$$     out.putFloat("gulliver.sizePotion", i.gulliver$getSizePotionMultiplier());
    //$$     out.putFloat("gulliver.sizeItem", i.gulliver$getSizeItemMultiplier());
    //$$     out.putBoolean("gulliver.sizeInit", i.gulliver$isSizeInitialized());
    //$$ }
    //$$
    //$$ @Inject(method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    //$$ private void gulliver$loadSize(CompoundTag in, CallbackInfo ci) {
    //$$     if (!in.contains("gulliver.sizeBase")) return;
    //$$     float base = in.getFloat("gulliver.sizeBase");
    //$$     gulliver$apply(base, in.contains("gulliver.sizeBaseDest") ? in.getFloat("gulliver.sizeBaseDest") : base,
    //$$             in.contains("gulliver.sizePotion") ? in.getFloat("gulliver.sizePotion") : 1.0F,
    //$$             in.contains("gulliver.sizeItem") ? in.getFloat("gulliver.sizeItem") : 1.0F,
    //$$             !in.contains("gulliver.sizeInit") || in.getBoolean("gulliver.sizeInit"));
    //$$ }
    //#endif

    /**
     * {@code initialized} is false for mobs saved before their first tick
     * (world generation writes new mobs straight into the chunk), so their
     * spawn size is still rolled; saves from before the flag count as done.
     */
    private void gulliver$apply(float base, float dest, float potion, float item, boolean initialized) {
        IGulliverEntityInternal i = (IGulliverEntityInternal) this;
        // The live size starts at its destination: nothing to tween after a load.
        i.gulliver$setSizeBaseMultiplier(dest);
        i.gulliver$setSizeBaseDestMultiplier(dest);
        i.gulliver$setSizePotionMultiplier(potion);
        i.gulliver$setSizeItemMultiplier(item);
        i.gulliver$setSizeInitialized(initialized);
        ((LivingEntity) (Object) this).refreshDimensions();
    }
}
