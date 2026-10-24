//#if MC < 1.21
//$$ package gulliver.mixin;
//$$
//$$ import gulliver.api.IResizeableEntity;
//$$ import gulliver.common.SizeScaling;
//$$ import net.minecraft.world.entity.LivingEntity;
//$$ import net.minecraft.world.item.CrossbowItem;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.level.Level;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Redirect;
//$$
//$$ /**
//$$  * Size-scaled crossbow loading on 1.20.1, where getChargeDuration has no
//$$  * entity to ask about. Same shape as the bow and trident: the time held
//$$  * is multiplied by a size factor wherever the loading progress is
//$$  * measured (the release check and the loading sounds), so a size-0.5
//$$  * body needs twice as long, a size-2 body half. The factor keeps the
//$$  * loading between 4 ticks and 8x vanilla (SizeScaling.chargeFactor).
//$$  *
//$$  * The crossbow's drawn-back look in first person still follows the
//$$  * unscaled time; only the mechanics and sounds are scaled.
//$$  */
//$$ @Mixin(CrossbowItem.class)
//$$ public abstract class MixinCrossbowItemLegacy {
//$$
//$$     @Redirect(method = "releaseUsing",
//$$               at = @At(value = "INVOKE",
//$$                        target = "Lnet/minecraft/world/item/CrossbowItem;getUseDuration(Lnet/minecraft/world/item/ItemStack;)I"))
//$$     private int gulliver$scaleReleaseCharge(CrossbowItem item, ItemStack stack, ItemStack stack2, Level level,
//$$                                              LivingEntity user, int remaining) {
//$$         int duration = item.getUseDuration(stack);
//$$         float size = ((IResizeableEntity) user).getSizeMultiplier();
//$$         if (size == 1.0F) return duration;
//$$         float factor = SizeScaling.chargeFactor(size, CrossbowItem.getChargeDuration(stack));
//$$         return remaining + Math.round((duration - remaining) * factor);
//$$     }
//$$
//$$     @Redirect(method = "onUseTick",
//$$               at = @At(value = "INVOKE",
//$$                        target = "Lnet/minecraft/world/item/ItemStack;getUseDuration()I"))
//$$     private int gulliver$scaleLoadingProgress(ItemStack stack, Level level, LivingEntity user, ItemStack stack2,
//$$                                                int remaining) {
//$$         int duration = stack.getUseDuration();
//$$         float size = ((IResizeableEntity) user).getSizeMultiplier();
//$$         if (size == 1.0F) return duration;
//$$         float factor = SizeScaling.chargeFactor(size, CrossbowItem.getChargeDuration(stack));
//$$         return remaining + Math.round((duration - remaining) * factor);
//$$     }
//$$ }
//#endif
