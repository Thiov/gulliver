//#if MC < 1.21.2
//$$ package gulliver.mixin;
//$$
//$$ import gulliver.common.EatContext;
//$$ import net.minecraft.world.entity.player.Player;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.level.Level;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//$$
//$$ /**
//$$  * Remembers who is eating, so FoodData can scale nutrition by their size.
//$$  * Player.eat fills FoodData before calling up, so hook the Player override.
//$$  */
//$$ @Mixin(Player.class)
//$$ public abstract class MixinPlayerEatLegacy {
//$$
//$$     @Inject(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"))
//$$     private void gulliver$captureEater(Level level, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
//$$         EatContext.set((Player) (Object) this);
//$$     }
//$$
//$$     @Inject(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
//$$     private void gulliver$clearEater(Level level, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
//$$         EatContext.clear();
//$$     }
//$$ }
//#endif
