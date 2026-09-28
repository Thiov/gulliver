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
 * should scale with body size.
 *
 * Always scaled (all sizes):
 *  - ENTITY_INTERACTION_RANGE: AttackRange.defaultFor reads this
 *    attribute directly (bypassing Player.entityInteractionRange()
 *    mixin), so without this modifier a size-8 player physically
 *    can't reach the mobs at their feet. Linear by size — the user
 *    explicitly wanted "tight" reach for tinies (4(221..222)); at
 *    size 0.125 that's entity reach 0.375 / block reach 0.56, just
 *    enough to hit what your face is touching.
 *  - BLOCK_INTERACTION_RANGE: same idea for block targeting.
 *
 * Giants only (size > 1):
 *  - MAX_HEALTH: linear by size, so a size-8 giant has 160 hp.
 *  - ARMOR: +2 per size step above 1, capped at +30.
 *
 * Tinies deliberately keep vanilla hearts and armor — their fragility
 * comes from the per-hit damage divide in MixinLivingEntityDamage
 * (smaller target → takes more damage per hit).
 *
 * Modifier IDs are stable per attribute so re-applying updates in
 * place — no leak across resizes. This runs from the per-tick
 * safety-net in MixinLivingEntitySizeTween, so every path SKIPS the
 * attribute write when the already-applied amount matches: rewriting
 * a permanent modifier dirties the instance and re-syncs it to
 * clients, which would otherwise happen every tick for every entity.
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
        applyMultiplier(entity, Attributes.ENTITY_INTERACTION_RANGE, ENTITY_REACH_ID, size);
        applyMultiplier(entity, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, size);

        if (size > 1.0F) {
            applyMultiplier(entity, Attributes.MAX_HEALTH, MAX_HEALTH_ID, size);
            float bonus = Math.min(30.0F, (size - 1.0F) * 2.0F);
            applyModifier(entity, Attributes.ARMOR, ARMOR_ID, bonus,
                    AttributeModifier.Operation.ADD_VALUE);
        } else {
            removeModifier(entity, Attributes.MAX_HEALTH, MAX_HEALTH_ID);
            removeModifier(entity, Attributes.ARMOR, ARMOR_ID);
        }
    }

    private static void applyMultiplier(LivingEntity entity,
                                          Holder<Attribute> attr,
                                          Identifier id,
                                          float size) {
        if (size == 1.0F) {
            removeModifier(entity, attr, id);
            return;
        }
        applyModifier(entity, attr, id, size - 1.0F,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static void applyModifier(LivingEntity entity,
                                        Holder<Attribute> attr,
                                        Identifier id,
                                        float amount,
                                        AttributeModifier.Operation op) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(id);
        if (existing != null && existing.amount() == (double) amount
                && existing.operation() == op) {
            return; // already applied — avoid dirtying the instance
        }
        inst.addOrReplacePermanentModifier(new AttributeModifier(id, amount, op));
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
