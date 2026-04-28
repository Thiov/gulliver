package gulliver.init;

import gulliver.GulliverFabric;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * 1.6.4 GulliverForged.registerCommandsAndRules registered a 'sizeGriefing'
 * boolean gamerule (default true) and consulted it from canSizeGrief.
 *
 * Modern Fabric translation: register through GameRuleBuilder.forBoolean.
 * Reading: level.getGameRules().get(GulliverGameRules.SIZE_GRIEFING).
 */
public final class GulliverGameRules {
    private GulliverGameRules() {}

    public static final GameRule<Boolean> SIZE_GRIEFING =
            GameRuleBuilder.forBoolean(true)
                    .category(GameRuleCategory.MOBS)
                    .buildAndRegister(GulliverFabric.id("size_griefing"));

    public static void init() {}
}
