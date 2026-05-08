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

    public static void applyForSize(LivingEntity entity, float size) {
        applyMultiplier(entity, Attributes.MAX_HEALTH, MAX_HEALTH_ID, size);
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
