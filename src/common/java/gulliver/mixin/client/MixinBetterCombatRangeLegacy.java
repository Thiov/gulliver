//#if MC < 1.20.5
//$$ package gulliver.mixin.client;
//$$
//$$ import gulliver.api.IResizeableEntity;
//$$ import gulliver.common.SizeAttributes;
//$$ import net.minecraft.client.Minecraft;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Pseudo;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.ModifyVariable;
//$$
//$$ /**
//$$  * Better Combat (issue #11): before 1.20.5 it swings with each weapon's
//$$  * fixed attack range and never reads reach, so a giant's sword hit no
//$$  * further than a normal player's (and a tiny's much further). All of its
//$$  * targeting goes through TargetFinder.findAttackTargetResult - the entity
//$$  * search box, the weapon hit box and the swing radius all come from that
//$$  * one range - so it is scaled there, like Gulliver's own reach. Better
//$$  * Combat targets for the local player only; newer builds read the
//$$  * interaction-range attribute, which already scales with size.
//$$  * Pseudo: does nothing unless Better Combat is installed.
//$$  */
//$$ @Pseudo
//$$ @Mixin(targets = "net.bettercombat.client.collision.TargetFinder", remap = false)
//$$ public abstract class MixinBetterCombatRangeLegacy {
//$$
//$$     @ModifyVariable(method = "findAttackTargetResult", at = @At("HEAD"), argsOnly = true, require = 0)
//$$     private static double gulliver$scaleAttackRange(double attackRange) {
//$$         var player = Minecraft.getInstance().player;
//$$         if (player == null) return attackRange;
//$$         return attackRange * SizeAttributes.reachFactor(player, ((IResizeableEntity) player).getSizeMultiplier());
//$$     }
//$$ }
//#endif
