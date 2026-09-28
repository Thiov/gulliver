//#if MC < 1.20.5
//$$ package gulliver.mixin;
//$$
//$$ import gulliver.api.IResizeableEntity;
//$$ import net.minecraft.world.entity.EntityDimensions;
//$$ import net.minecraft.world.entity.Pose;
//$$ import net.minecraft.world.entity.player.Player;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//$$
//$$ /**
//$$  * Before 1.20.5 Player.getDimensions answers from its own pose table
//$$  * without calling up, so the Entity-level scaling never reaches players.
//$$  */
//$$ @Mixin(Player.class)
//$$ public abstract class MixinPlayerDimensionsLegacy {
//$$
//$$     @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
//$$     private void gulliver$scaleDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
//$$         float m = ((IResizeableEntity) this).getSizeMultiplier();
//$$         EntityDimensions base = cir.getReturnValue();
//$$         if (m != 1.0F && base != null) cir.setReturnValue(base.scale(m));
//$$     }
//$$ }
//#endif
