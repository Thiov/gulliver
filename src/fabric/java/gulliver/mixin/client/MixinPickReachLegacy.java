//#if MC < 1.20.5
//$$ package gulliver.mixin.client;
//$$
//$$ import gulliver.api.IResizeableEntity;
//$$ import gulliver.common.SizeAttributes;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.renderer.GameRenderer;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.Constant;
//$$ import org.spongepowered.asm.mixin.injection.ModifyConstant;
//$$
//$$ /**
//$$  * Entity reach in GameRenderer.pick (Fabric before 1.20.5): the 3-block
//$$  * survival limit (9 squared) and creative's 6 blocks scale with size.
//$$  */
//$$ @Mixin(GameRenderer.class)
//$$ public abstract class MixinPickReachLegacy {
//$$
//$$     private static float gulliver$factor() {
//$$         var player = Minecraft.getInstance().player;
//$$         return player == null ? 1.0F
//$$                 : SizeAttributes.reachFactor(player, ((IResizeableEntity) player).getSizeMultiplier());
//$$     }
//$$
//$$     @ModifyConstant(method = "pick", constant = @Constant(doubleValue = 3.0D))
//$$     private double gulliver$entityReach(double c) {
//$$         return c * gulliver$factor();
//$$     }
//$$
//$$     @ModifyConstant(method = "pick", constant = @Constant(doubleValue = 9.0D))
//$$     private double gulliver$entityReachSq(double c) {
//$$         float f = gulliver$factor();
//$$         return c * f * f;
//$$     }
//$$
//$$     @ModifyConstant(method = "pick", constant = @Constant(doubleValue = 6.0D))
//$$     private double gulliver$creativeReach(double c) {
//$$         return c * gulliver$factor();
//$$     }
//$$ }
//#endif
