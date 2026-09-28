//#if MC < 1.20.5
//$$ package gulliver.mixin;
//$$
//$$ import gulliver.api.IResizeableEntity;
//$$ import gulliver.common.SizeAttributes;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.network.ServerGamePacketListenerImpl;
//$$ import org.objectweb.asm.Opcodes;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Constant;
//$$ import org.spongepowered.asm.mixin.injection.ModifyConstant;
//$$ import org.spongepowered.asm.mixin.injection.Redirect;
//$$
//$$ /**
//$$  * Server reach checks (Fabric before 1.20.5): a giant's reach must not be
//$$  * rejected by the fixed 6-block limit. Only ever widened, never narrowed.
//$$  */
//$$ @Mixin(ServerGamePacketListenerImpl.class)
//$$ public abstract class MixinReachServerLegacy {
//$$
//$$     @Shadow public ServerPlayer player;
//$$
//$$     private double gulliver$sq() {
//$$         float f = Math.max(1.0F, SizeAttributes.reachFactor(player, ((IResizeableEntity) player).getSizeMultiplier()));
//$$         return f * f;
//$$     }
//$$
//$$     @Redirect(method = {"handleUseItemOn", "handleInteract"},
//$$               at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
//$$                        target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;MAX_INTERACTION_DISTANCE:D"))
//$$     private double gulliver$maxDistance() {
//$$         return ServerGamePacketListenerImpl.MAX_INTERACTION_DISTANCE * gulliver$sq();
//$$     }
//$$
//$$     @ModifyConstant(method = "handleUseItemOn", constant = @Constant(doubleValue = 64.0D))
//$$     private double gulliver$useDistance(double c) {
//$$         return c * gulliver$sq();
//$$     }
//$$ }
//#endif
