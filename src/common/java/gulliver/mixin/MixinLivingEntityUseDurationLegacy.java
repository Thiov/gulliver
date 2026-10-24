//#if MC < 1.20.5
//$$ package gulliver.mixin;
//$$
//$$ import gulliver.api.IResizeableEntity;
//$$ import gulliver.common.SizeScaling;
//$$ import net.minecraft.world.InteractionHand;
//$$ import net.minecraft.world.entity.LivingEntity;
//$$ import net.minecraft.world.food.FoodProperties;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.item.UseAnim;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//$$
//$$ /**
//$$  * Eating and drinking speed by size (1.20.1 has no per-entity use
//$$  * duration): scale the countdown when the eating starts. Tinies eat
//$$  * slower and giants quicker (1/size, as on the newer versions; never
//$$  * under 4 ticks or over 8x vanilla).
//$$  *
//$$  * The chewing sounds and crumbs would stay unscaled, which left a slow
//$$  * tiny's meal silent until the last stretch, so the "start making
//$$  * noise" check is scaled the same way (vanilla starts after 7 of the 32
//$$  * ticks; here after the same share of the scaled time). The first-person
//$$  * animation is scaled by MixinItemInHandRendererEatLegacy.
//$$  */
//$$ @Mixin(LivingEntity.class)
//$$ public abstract class MixinLivingEntityUseDurationLegacy {
//$$
//$$     @Shadow protected int useItemRemaining;
//$$
//$$     @Shadow public abstract ItemStack getUseItem();
//$$
//$$     @Shadow public abstract int getUseItemRemainingTicks();
//$$
//$$     @Inject(method = "startUsingItem", at = @At("TAIL"))
//$$     private void gulliver$scaleEating(InteractionHand hand, CallbackInfo ci) {
//$$         ItemStack stack = getUseItem();
//$$         if (stack.isEmpty() || useItemRemaining <= 0) return;
//$$         UseAnim anim = stack.getUseAnimation();
//$$         if (anim != UseAnim.EAT && anim != UseAnim.DRINK) return;
//$$         float size = ((IResizeableEntity) this).getSizeMultiplier();
//$$         if (size != 1.0F) useItemRemaining = SizeScaling.scaledTicks(useItemRemaining, size);
//$$     }
//$$
//$$     @Inject(method = "shouldTriggerItemUseEffects", at = @At("HEAD"), cancellable = true)
//$$     private void gulliver$scaleEatingEffects(CallbackInfoReturnable<Boolean> cir) {
//$$         ItemStack stack = getUseItem();
//$$         UseAnim anim = stack.getUseAnimation();
//$$         if (anim != UseAnim.EAT && anim != UseAnim.DRINK) return;
//$$         float size = ((IResizeableEntity) this).getSizeMultiplier();
//$$         if (size == 1.0F) return;
//$$         int total = SizeScaling.scaledTicks(stack.getUseDuration(), size);
//$$         int remaining = getUseItemRemainingTicks();
//$$         FoodProperties food = stack.getItem().getFoodProperties();
//$$         boolean fast = food != null && food.isFastFood();
//$$         boolean started = remaining <= total - (int) (total * 0.21875F);
//$$         cir.setReturnValue((fast || started) && remaining % 4 == 0);
//$$     }
//$$ }
//#endif
