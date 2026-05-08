package gulliver.common;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Apply size-based attribute modifiers for the attributes whose effect
 * SHOULD scale with body size. We deliberately do NOT scale MAX_HEALTH
 * or ARMOR — per user feedback, hearts should stay at vanilla 10
 * regardless of size; the "tiny is fragile, giant is tough" feel
 * comes purely from MixinLivingEntityDamage's per-hit damage scaling
 * (smaller target → take more damage, bigger target → take less).
 *
 * What we DO scale:
 *  - ENTITY_INTERACTION_RANGE: AttackRange.defaultFor reads this
 *    attribute directly (bypassing Player.entityInteractionRange()
 *    mixin), so without this modifier a size-8 player physically
 *    can't reach the mobs at their feet.
 *  - BLOCK_INTERACTION_RANGE: same idea for block targeting.
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
        // Reach: 1.6.4 nn.java getRangeMultiplier — sqrt(size) for tinies,
        // linear for giants. Linear-for-tinies gave size 0.125 a reach of
        // 4.5 * 0.125 = 0.56 blocks (you couldn't break a block in front
        // of you). Sqrt is the soft penalty curve: 0.125 → 0.354 → reach
        // 1.59 / 1.06 (block / entity), usable.
        float reachMul = size >= 1.0F ? size : (float) Math.sqrt(size);
        applyMultiplierAmount(entity, Attributes.ENTITY_INTERACTION_RANGE,
                ENTITY_REACH_ID, reachMul - 1.0F);
        applyMultiplierAmount(entity, Attributes.BLOCK_INTERACTION_RANGE,
                BLOCK_REACH_ID, reachMul - 1.0F);

        // Max HP and Armor: giants only. Tinies keep vanilla hearts and
        // armor — their "fragility" comes from the per-hit damage divide
        // in MixinLivingEntityDamage (target/size = 8x damage for tinies).
        if (size > 1.0F) {
            applyMultiplier(entity, Attributes.MAX_HEALTH, MAX_HEALTH_ID, size);
            float bonus = Math.min(30.0F, (size - 1.0F) * 2.0F);
            applyAdditive(entity, Attributes.ARMOR, ARMOR_ID, bonus);
        } else {
            removeModifier(entity, Attributes.MAX_HEALTH, MAX_HEALTH_ID);
            removeModifier(entity, Attributes.ARMOR, ARMOR_ID);
        }
    }

    private static void applyMultiplierAmount(LivingEntity entity,
                                                Holder<Attribute> attr,
                                                Identifier id,
                                                float amount) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) return;
        if (amount == 0.0F) {
            if (inst.getModifier(id) != null) inst.removeModifier(id);
            return;
        }
        AttributeModifier mod = new AttributeModifier(
                id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        inst.addOrReplacePermanentModifier(mod);
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
