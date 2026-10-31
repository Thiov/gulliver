//#if MC < 1.20.5
//$$ package gulliver.mixin;
//$$
//$$ import gulliver.api.IResizeableEntity;
//$$ import net.minecraft.world.entity.Entity;
//$$ import net.minecraft.world.entity.animal.Chicken;
//$$ import net.minecraft.world.entity.animal.camel.Camel;
//$$ import net.minecraft.world.entity.animal.horse.AbstractHorse;
//$$ import net.minecraft.world.entity.animal.horse.Llama;
//$$ import net.minecraft.world.entity.vehicle.Boat;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Unique;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Redirect;
//$$
//$$ /**
//$$  * Before 1.20.5 a rider sits at a fixed offset below the vehicle's seat
//$$  * point (a player's is -0.35, a zombie's -0.45) that doesn't know about
//$$  * size. A scaled rider keeps the part it sits on - a quarter block above
//$$  * that offset, 0.6 for a player, like the seat attachment newer versions
//$$  * use - on the same seat, so a tiny no longer sinks into a chicken or
//$$  * under a boat's hull, where it drowned (issue #12); a standing rider
//$$  * (an animal) keeps its height above the seat in proportion. Boats, chickens,
//$$  * camels, horses and llamas place their riders themselves and get the
//$$  * same correction.
//$$  */
//$$ @Mixin({ Entity.class, Boat.class, Chicken.class, Camel.class, AbstractHorse.class, Llama.class })
//$$ public abstract class MixinEntityRidingOffsetLegacy {
//$$
//$$     @Redirect(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
//$$               at = @At(value = "INVOKE",
//$$                        target = "Lnet/minecraft/world/entity/Entity;getMyRidingOffset()D"))
//$$     private double gulliver$scaleRiderOffset(Entity passenger) {
//$$         return gulliver$riderOffset(passenger.getMyRidingOffset(), ((IResizeableEntity) passenger).getSizeMultiplier(),
//$$                 (Object) this instanceof Boat);
//$$     }
//$$
//$$     /** The vanilla offset moved so a scaled rider sits (or stands) where a normal one would. */
//$$     @Unique
//$$     private static double gulliver$riderOffset(double offset, float size, boolean boat) {
//$$         if (size == 1.0F) return offset;
//$$         // Sitting riders: the part that rests on the seat, a quarter block
//$$         // above the offset, stays on the seat.
//$$         if (offset < 0.0D) return offset + (1.0D - size) * (0.25D - offset);
//$$         // Standing riders (animals): their height above the seat scales. A
//$$         // boat's seat point is under its hull, so small ones stand up into it.
//$$         return offset * size + (boat && size < 1.0F ? (1.0D - size) * 0.25D : 0.0D);
//$$     }
//$$ }
//#endif
