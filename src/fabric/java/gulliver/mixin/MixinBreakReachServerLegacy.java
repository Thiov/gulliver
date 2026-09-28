//#if MC < 1.20.5
//$$ package gulliver.mixin;
//$$
//$$ import gulliver.api.IResizeableEntity;
//$$ import gulliver.common.SizeAttributes;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.level.ServerPlayerGameMode;
//$$ import net.minecraft.server.network.ServerGamePacketListenerImpl;
//$$ import org.objectweb.asm.Opcodes;
//$$ import org.spongepowered.asm.mixin.Final;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Redirect;
//$$
//$$ /** Block breaking reach check, widened for giants (Fabric before 1.20.5). */
//$$ @Mixin(ServerPlayerGameMode.class)
//$$ public abstract class MixinBreakReachServerLegacy {
//$$
//$$     @Shadow @Final protected ServerPlayer player;
//$$
//$$     @Redirect(method = "handleBlockBreakAction",
//$$               at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
//$$                        target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;MAX_INTERACTION_DISTANCE:D"))
//$$     private double gulliver$maxDistance() {
//$$         float f = Math.max(1.0F, SizeAttributes.reachFactor(player, ((IResizeableEntity) player).getSizeMultiplier()));
//$$         return ServerGamePacketListenerImpl.MAX_INTERACTION_DISTANCE * f * f;
//$$     }
//$$ }
//#endif
