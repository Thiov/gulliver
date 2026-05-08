package gulliver.common;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Apply size-based attribute modifiers to MAX_HEALTH (and any other
 * attributes the HUD/AI reads directly). This is necessary because
 * vanilla's HUD reads `player.getAttributeValue(MAX_HEALTH)` directly,
 * BYPASSING our `getMaxHealth()` mixin override — so without an actual
 * attribute modifier, the heart count visually stays at vanilla 10
 * even when our scaled max is 80.
 *
 * Use ADD_MULTIPLIED_BASE so amount = (size - 1) gives result = base * size:
 *   size 0.125: amount = -0.875 → result = 20 + 20*(-0.875) = 2.5
 *   size 1:     no modifier (no-op)
 *   size 4:     amount = 3 → result = 20 + 20*3 = 80
 *
 * Modifier IDs are stable per attribute so addOrReplacePermanentModifier
 * just updates in place — no leak across resizes.
 */
public final class SizeAttributes {
    private SizeAttributes() {}

    private static final Identifier MAX_HEALTH_ID =
            Identifier.fromNamespaceAndPath("gulliver", "size_max_health");
    private static final Identifier ARMOR_ID =
            Identifier.fromNamespaceAndPath("gulliver", "size_armor");
    private static final Identifier ENTITY_REACH_ID =
            Identifier.fromNamespaceAndPath("gulliver", "size_entity_reach");
    private static final Identifier BLOCK_REACH_ID =
            Identifier.fromNamespaceAndPath("gulliver", "size_block_reach");

    public static void applyForSize(LivingEntity entity, float size) {
        applyMultiplier(entity, Attributes.MAX_HEALTH, MAX_HEALTH_ID, size);
        // Reach attributes scale linearly with size — needed because
        // vanilla's AttackRange.defaultFor and various range checks read
        // these attributes DIRECTLY (bypassing Player.entityInteractionRange
        // mixin). Without these modifiers, a size-8 player has 8x the
        // arm length but the game still uses vanilla 4-block reach,
        // making them unable to actually attack mobs at the foot of
        // their giant body.
        applyMultiplier(entity, Attributes.ENTITY_INTERACTION_RANGE, ENTITY_REACH_ID, size);
        applyMultiplier(entity, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, size);
        // Armor: bonus for giants only, additive (capped). Tinies don't
        // lose armor, so for size <= 1 we remove the modifier entirely.
        if (size > 1.0F) {
            float bonus = Math.min(30.0F, (size - 1.0F) * 2.0F);
            applyAdditive(entity, Attributes.ARMOR, ARMOR_ID, bonus);
        } else {
            removeModifier(entity, Attributes.ARMOR, ARMOR_ID);
        }
    }

    private static void applyMultiplier(LivingEntity entity,
                                          Holder<Attribute> attr,
                                          Identifier id,
                                          float size) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) return;
        if (size == 1.0F) {
            if (inst.getModifier(id) != null) inst.removeModifier(id);
            return;
        }
        AttributeModifier mod = new AttributeModifier(
                id, size - 1.0F, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        inst.addOrReplacePermanentModifier(mod);
    }

    private static void applyAdditive(LivingEntity entity,
                                        Holder<Attribute> attr,
                                        Identifier id,
                                        float amount) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier mod = new AttributeModifier(
                id, amount, AttributeModifier.Operation.ADD_VALUE);
        inst.addOrReplacePermanentModifier(mod);
    }

    private static void removeModifier(LivingEntity entity,
                                         Holder<Attribute> attr,
                                         Identifier id) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null && inst.getModifier(id) != null) {
            inst.removeModifier(id);
        }
    }
}
