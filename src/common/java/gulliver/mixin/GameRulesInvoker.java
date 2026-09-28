//#if MC < 1.21.11
//$$ package gulliver.mixin;
//$$
//$$ import net.minecraft.world.level.GameRules;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.gen.Invoker;
//$$
//$$ /** GameRules.register is private before 1.21.11's registry-based rules. */
//$$ @Mixin(GameRules.class)
//$$ public interface GameRulesInvoker {
//$$     @Invoker("register")
//$$     static <T extends GameRules.Value<T>> GameRules.Key<T> gulliver$register(String name, GameRules.Category category,
//$$                                                                          GameRules.Type<T> type) {
//$$         throw new AssertionError();
//$$     }
//$$ }
//#endif
