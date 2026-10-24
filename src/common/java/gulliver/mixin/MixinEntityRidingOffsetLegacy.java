//#if MC < 1.20.5
//$$ package gulliver.mixin;
//$$
//$$ import gulliver.api.IResizeableEntity;
//$$ import net.minecraft.world.entity.Entity;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Redirect;
//$$
//$$ /**
//$$  * Before 1.20.5 a rider sits at a fixed offset (a player's is -0.35)
//$$  * that doesn't know about size, so a tiny on a chicken sank into it
//$$  * up to its waist. Newer versions scale the attachment points with the
//$$  * entity's dimensions; here the passenger's own offset is scaled.
//$$  */
//$$ @Mixin(Entity.class)
//$$ public abstract class MixinEntityRidingOffsetLegacy {
//$$
//$$     @Redirect(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
//$$               at = @At(value = "INVOKE",
//$$                        target = "Lnet/minecraft/world/entity/Entity;getMyRidingOffset()D"))
//$$     private double gulliver$scaleRiderOffset(Entity passenger) {
//$$         return passenger.getMyRidingOffset() * ((IResizeableEntity) passenger).getSizeMultiplier();
//$$     }
//$$ }
//#endif
