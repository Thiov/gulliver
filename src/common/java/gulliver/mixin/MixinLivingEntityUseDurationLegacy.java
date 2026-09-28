//#if MC < 1.20.5
//$$ package gulliver.mixin;
//$$
//$$ import gulliver.api.IResizeableEntity;
//$$ import net.minecraft.world.InteractionHand;
//$$ import net.minecraft.world.entity.LivingEntity;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.item.UseAnim;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ /**
//$$  * Eating and drinking speed by size (1.20.1 has no per-entity use
//$$  * duration): scale the countdown when the eating starts. Tinies finish
//$$  * a meal quickly, giants take their time.
//$$  */
//$$ @Mixin(LivingEntity.class)
//$$ public abstract class MixinLivingEntityUseDurationLegacy {
//$$
//$$     @Shadow protected int useItemRemaining;
//$$
//$$     @Shadow public abstract ItemStack getUseItem();
//$$
//$$     @Inject(method = "startUsingItem", at = @At("TAIL"))
//$$     private void gulliver$scaleEating(InteractionHand hand, CallbackInfo ci) {
//$$         ItemStack stack = getUseItem();
//$$         if (stack.isEmpty() || useItemRemaining <= 0) return;
//$$         UseAnim anim = stack.getUseAnimation();
//$$         if (anim != UseAnim.EAT && anim != UseAnim.DRINK) return;
//$$         float size = ((IResizeableEntity) this).getSizeMultiplier();
//$$         if (size != 1.0F) useItemRemaining = Math.max(1, Math.round(useItemRemaining / size));
//$$     }
//$$ }
//#endif
