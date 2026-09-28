//#if MC < 1.21.11
//$$ package gulliver.mixin;
//$$
//$$ import net.minecraft.world.level.GameRules;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.gen.Invoker;
//$$
//$$ /** BooleanValue.create(boolean) is package-private on some versions. */
//$$ @Mixin(GameRules.BooleanValue.class)
//$$ public interface GameRulesBooleanValueInvoker {
//$$     @Invoker("create")
//$$     static GameRules.Type<GameRules.BooleanValue> gulliver$create(boolean defaultValue) {
//$$         throw new AssertionError();
//$$     }
//$$ }
//#endif
