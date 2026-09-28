//#if MC < 1.21.11
//$$ package gulliver.mixin;
//$$
//$$ import gulliver.common.AttackContext;
//$$ import net.minecraft.world.entity.Entity;
//$$ import net.minecraft.world.entity.player.Player;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ /**
//$$  * Before 1.21.11 the sprint/weapon extra knockback happens inline in
//$$  * Player.attack, after hurtServer has returned: keep the attacker on the
//$$  * AttackContext stack for the whole attack.
//$$  */
//$$ @Mixin(Player.class)
//$$ public abstract class MixinPlayerAttackContextLegacy {
//$$
//$$     @Inject(method = "attack", at = @At("HEAD"))
//$$     private void gulliver$pushAttack(Entity target, CallbackInfo ci) {
//$$         AttackContext.push((Entity) (Object) this);
//$$     }
//$$
//$$     @Inject(method = "attack", at = @At("RETURN"))
//$$     private void gulliver$popAttack(Entity target, CallbackInfo ci) {
//$$         AttackContext.pop();
//$$     }
//$$ }
//#endif
