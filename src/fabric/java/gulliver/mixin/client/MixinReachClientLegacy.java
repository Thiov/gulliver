//#if MC < 1.20.5
//$$ package gulliver.mixin.client;
//$$
//$$ import gulliver.api.IResizeableEntity;
//$$ import gulliver.common.SizeAttributes;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.multiplayer.MultiPlayerGameMode;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//$$
//$$ /** Fabric before 1.20.5 has no reach attribute: scale the block pick range directly. */
//$$ @Mixin(MultiPlayerGameMode.class)
//$$ public abstract class MixinReachClientLegacy {
//$$
//$$     @Inject(method = "getPickRange", at = @At("RETURN"), cancellable = true)
//$$     private void gulliver$scaleReach(CallbackInfoReturnable<Float> cir) {
//$$         var player = Minecraft.getInstance().player;
//$$         if (player == null) return;
//$$         float f = SizeAttributes.reachFactor(player, ((IResizeableEntity) player).getSizeMultiplier());
//$$         if (f != 1.0F) cir.setReturnValue(cir.getReturnValueF() * f);
//$$     }
//$$ }
//#endif
