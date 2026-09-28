//#if MC < 1.20.5
//$$ package gulliver.mixin;
//$$
//$$ import net.minecraft.world.item.Item;
//$$ import net.minecraft.world.item.alchemy.Potion;
//$$ import net.minecraft.world.item.alchemy.PotionBrewing;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.gen.Invoker;
//$$
//$$ /** Before 1.20.5 brewing mixes live in a static list filled by addMix. */
//$$ @Mixin(PotionBrewing.class)
//$$ public interface PotionBrewingInvoker {
//$$     @Invoker("addMix")
//$$     static void gulliver$addMix(Potion from, Item ingredient, Potion to) {
//$$         throw new AssertionError();
//$$     }
//$$ }
//#endif
