//#if MC < 1.21.2
//$$ package gulliver.mixin;
//$$
//$$ import gulliver.common.Movement;
//$$ import net.minecraft.world.entity.animal.horse.AbstractHorse;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//$$
//$$ /**
//$$  * A horse's rider-charged jump before 1.21.2: the jump height comes
//$$  * straight from the jump_strength attribute (getCustomJump), not from
//$$  * LivingEntity.getJumpPower where MixinLivingEntityMovement scales jumps,
//$$  * so a resized horse would jump at vanilla height. From 1.21.2 the horse
//$$  * asks LivingEntity.getJumpPower(float) and is covered there.
//$$  */
//$$ @Mixin(AbstractHorse.class)
//$$ public abstract class MixinHorseJumpLegacy {
//$$
//$$     @Inject(method = "getCustomJump", at = @At("RETURN"), cancellable = true)
//$$     private void gulliver$scaleJump(CallbackInfoReturnable<Double> cir) {
//$$         AbstractHorse self = (AbstractHorse) (Object) this;
//$$         double vanilla = cir.getReturnValueD();
//$$         // Jump Boost is added on top by the horse, so only the strength is scaled.
//$$         float scaled = Movement.scaledJump(self, (float) vanilla, 0.0F);
//$$         if (scaled != (float) vanilla) cir.setReturnValue((double) scaled);
//$$     }
//$$ }
//#endif
