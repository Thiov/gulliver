//#if MC < 1.20.5
//$$ package gulliver.mixin;
//$$
//$$ import gulliver.api.IResizeableEntity;
//$$ import net.minecraft.world.entity.EntityDimensions;
//$$ import net.minecraft.world.entity.LivingEntity;
//$$ import net.minecraft.world.entity.Pose;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.ModifyVariable;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//$$
//$$ /**
//$$  * Eye height before 1.20.5: EntityDimensions has no eye height yet, and
//$$  * most mobs (and players) return fixed numbers from getStandingEyeHeight.
//$$  * The mob's own formula runs on its unscaled dimensions and the result is
//$$  * scaled once, so constant and dimension-based eye heights both follow
//$$  * the body.
//$$  */
//$$ @Mixin(LivingEntity.class)
//$$ public abstract class MixinLivingEntityDimensionsLegacy {
//$$
//$$     @ModifyVariable(method = "getEyeHeight(Lnet/minecraft/world/entity/Pose;Lnet/minecraft/world/entity/EntityDimensions;)F",
//$$                     at = @At("HEAD"), argsOnly = true)
//$$     private EntityDimensions gulliver$unscaledDimensions(EntityDimensions dims) {
//$$         float m = ((IResizeableEntity) this).getSizeMultiplier();
//$$         return m == 1.0F || dims == null ? dims : dims.scale(1.0F / m);
//$$     }
//$$
//$$     @Inject(method = "getEyeHeight(Lnet/minecraft/world/entity/Pose;Lnet/minecraft/world/entity/EntityDimensions;)F",
//$$             at = @At("RETURN"), cancellable = true)
//$$     private void gulliver$scaleEyeHeight(Pose pose, EntityDimensions dims, CallbackInfoReturnable<Float> cir) {
//$$         float m = ((IResizeableEntity) this).getSizeMultiplier();
//$$         if (m != 1.0F) cir.setReturnValue(cir.getReturnValueF() * m);
//$$     }
//$$ }
//#endif
