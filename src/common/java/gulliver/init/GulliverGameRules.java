package gulliver.init;

import gulliver.Gulliver;
//#if MC >= 1.21.11
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import java.util.function.BiConsumer;
//#else
//$$ import net.minecraft.world.level.GameRules;
//#endif
import net.minecraft.server.level.ServerLevel;

/**
 * The 1.6.4 'sizeGriefing' boolean game rule (default true), consulted by
 * GulliverEnvoy.canSizeGrief, and 'starter_repick' (default true): whether
 * players may pick their starter size again with /gulliver starter, where
 * the config allows it at all (starterSize.allowRepick, #32). From 1.21.11
 * game rules are registry entries ("gulliver:size_griefing"); before that
 * they are named keys ("gulliverSizeGriefing").
 */
public final class GulliverGameRules {
    private GulliverGameRules() {}

    //#if MC >= 1.21.11
    public static final GameRule<Boolean> SIZE_GRIEFING = new GameRule<>(
            GameRuleCategory.MOBS, GameRuleType.BOOL, BoolArgumentType.bool(),
            GameRuleTypeVisitor::visitBoolean, Codec.BOOL, b -> b ? 1 : 0, true, FeatureFlagSet.of());
    public static final GameRule<Boolean> STARTER_REPICK = new GameRule<>(
            GameRuleCategory.PLAYER, GameRuleType.BOOL, BoolArgumentType.bool(),
            GameRuleTypeVisitor::visitBoolean, Codec.BOOL, b -> b ? 1 : 0, true, FeatureFlagSet.of());

    public static void register(BiConsumer<net.minecraft.resources.Identifier, GameRule<?>> sink) {
        sink.accept(Gulliver.id("size_griefing"), SIZE_GRIEFING);
        sink.accept(Gulliver.id("starter_repick"), STARTER_REPICK);
    }

    public static boolean starterRepick(ServerLevel level) {
        return level.getGameRules().get(STARTER_REPICK);
    }

    public static boolean sizeGriefing(ServerLevel level) {
        return level.getGameRules().get(SIZE_GRIEFING);
    }

    public static boolean mobGriefing(ServerLevel level) {
        return level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING);
    }
    //#else
    //$$ public static GameRules.Key<GameRules.BooleanValue> SIZE_GRIEFING;
    //$$ public static GameRules.Key<GameRules.BooleanValue> STARTER_REPICK;
    //$$
    //$$ /** Called once during common setup, before any world loads. */
    //$$ public static void register() {
    //$$     if (SIZE_GRIEFING == null) {
    //$$         SIZE_GRIEFING = gulliver.mixin.GameRulesInvoker.gulliver$register("gulliverSizeGriefing", GameRules.Category.MOBS,
    //$$                 gulliver.mixin.GameRulesBooleanValueInvoker.gulliver$create(true));
    //$$     }
    //$$     if (STARTER_REPICK == null) {
    //$$         STARTER_REPICK = gulliver.mixin.GameRulesInvoker.gulliver$register("gulliverStarterRepick", GameRules.Category.PLAYER,
    //$$                 gulliver.mixin.GameRulesBooleanValueInvoker.gulliver$create(true));
    //$$     }
    //$$ }
    //$$
    //$$ public static boolean starterRepick(ServerLevel level) {
    //$$     return STARTER_REPICK == null || level.getGameRules().getBoolean(STARTER_REPICK);
    //$$ }
    //$$
    //$$ public static boolean sizeGriefing(ServerLevel level) {
    //$$     return SIZE_GRIEFING == null || level.getGameRules().getBoolean(SIZE_GRIEFING);
    //$$ }
    //$$
    //$$ public static boolean mobGriefing(ServerLevel level) {
    //$$     return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    //$$ }
    //#endif
}
