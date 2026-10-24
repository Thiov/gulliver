//#if MC < 1.21.2
//$$ package gulliver.mixin;
//$$
//$$ import gulliver.common.Movement;
//$$ import net.minecraft.world.entity.animal.Rabbit;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//$$
//$$ /**
//$$  * A rabbit's hop before 1.21.2: Rabbit overrides the no-argument
//$$  * getJumpPower with its own fixed values instead of going through
//$$  * LivingEntity's (which is where MixinLivingEntityMovement scales jumps),
//$$  * so a resized rabbit would hop at vanilla height. From 1.21.2 the rabbit
//$$  * asks LivingEntity.getJumpPower(float) and is covered there.
//$$  */
//$$ @Mixin(Rabbit.class)
//$$ public abstract class MixinRabbitJumpLegacy {
//$$
//$$     @Inject(method = "getJumpPower", at = @At("RETURN"), cancellable = true)
//$$     private void gulliver$scaleHop(CallbackInfoReturnable<Float> cir) {
//$$         Rabbit self = (Rabbit) (Object) this;
//$$         float vanilla = cir.getReturnValueF();
//$$         // The rabbit's own numbers don't include Jump Boost.
//$$         float scaled = Movement.scaledJump(self, vanilla, 0.0F);
//$$         if (scaled != vanilla) cir.setReturnValue(scaled);
//$$     }
//$$ }
//#endif
