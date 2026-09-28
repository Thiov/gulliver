package gulliver.common;

import gulliver.Gulliver;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
//#if MC >= 1.20.5
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
//#else
//$$ import java.util.UUID;
//#endif

/**
 * Size-based attribute modifiers.
 *
 * Always (every size):
 *  - reach (entity and block interaction range): linear with size, so a
 *    size-8 giant can touch what's at its feet and a 0.125 tiny only
 *    what its face is against. A tiny under half size holding something
 *    pointy (sword, tool, stick) reaches like a half-size body instead.
 *    Before 1.20.5 vanilla has no reach attributes: Forge's own are used,
 *    and on Fabric small hooks read {@link #reachFactor} directly.
 *
 * Giants only (size > 1):
 *  - max health: linear with size (size 8 = 160 hp);
 *  - armor: +2 per size step above 1, capped at +30.
 *
 * Tinies keep vanilla hearts and armor — their fragility comes from the
 * per-hit damage scaling. Modifier ids are stable per attribute so a
 * re-apply replaces in place, and nothing is written when the value is
 * already right (writes dirty the instance and re-sync it to clients).
 */
public final class SizeAttributes {
    private SizeAttributes() {}

    //#if MC >= 1.20.5
    private static final Identifier MAX_HEALTH_ID = Gulliver.id("size_max_health");
    private static final Identifier ARMOR_ID = Gulliver.id("size_armor");
    private static final Identifier ENTITY_REACH_ID = Gulliver.id("size_entity_reach");
    private static final Identifier BLOCK_REACH_ID = Gulliver.id("size_block_reach");
    //#else
    //$$ private static final UUID MAX_HEALTH_ID = uuid("size_max_health");
    //$$ private static final UUID ARMOR_ID = uuid("size_armor");
    //$$ private static final UUID ENTITY_REACH_ID = uuid("size_entity_reach");
    //$$ private static final UUID BLOCK_REACH_ID = uuid("size_block_reach");
    //$$
    //$$ private static UUID uuid(String name) {
    //$$     return UUID.nameUUIDFromBytes((Gulliver.MOD_ID + ":" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    //$$ }
    //#endif

    /** Reach multiplier for this body: its size, or 0.5 for a tiny holding something pointy. */
    public static float reachFactor(LivingEntity entity, float size) {
        if (size < 0.5F && entity instanceof Player && GulliverEnvoy.holdingPointyItem(entity)) return 0.5F;
        return size;
    }

    public static void applyForSize(LivingEntity entity, float size) {
        float reach = reachFactor(entity, size);
        //#if MC >= 1.20.5
        applyMultiplier(entity, Attributes.ENTITY_INTERACTION_RANGE, ENTITY_REACH_ID, reach);
        applyMultiplier(entity, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, reach);
        //#else
        //$$ var reachAttrs = gulliver.platform.Services.platform().reachAttributes();
        //$$ if (reachAttrs != null) {
        //$$     applyMultiplier(entity, reachAttrs[0], BLOCK_REACH_ID, reach);
        //$$     applyMultiplier(entity, reachAttrs[1], ENTITY_REACH_ID, reach);
        //$$ }
        //#endif

        if (size > 1.0F) {
            applyMultiplier(entity, Attributes.MAX_HEALTH, MAX_HEALTH_ID, size);
            applyModifier(entity, Attributes.ARMOR, ARMOR_ID, Math.min(30.0F, (size - 1.0F) * 2.0F), false);
        } else {
            removeModifier(entity, Attributes.MAX_HEALTH, MAX_HEALTH_ID);
            removeModifier(entity, Attributes.ARMOR, ARMOR_ID);
        }
    }

    //#if MC >= 1.20.5
    private static void applyMultiplier(LivingEntity entity, Holder<Attribute> attr, Identifier id, float factor) {
        if (factor == 1.0F) {
            removeModifier(entity, attr, id);
            return;
        }
        applyModifier(entity, attr, id, factor - 1.0F, true);
    }

    private static void applyModifier(LivingEntity entity, Holder<Attribute> attr, Identifier id,
                                      float amount, boolean multiplyBase) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier.Operation op = multiplyBase
                ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                : AttributeModifier.Operation.ADD_VALUE;
        AttributeModifier existing = inst.getModifier(id);
        if (existing != null && existing.amount() == (double) amount && existing.operation() == op) return;
        inst.addOrReplacePermanentModifier(new AttributeModifier(id, amount, op));
    }

    private static void removeModifier(LivingEntity entity, Holder<Attribute> attr, Identifier id) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null && inst.getModifier(id) != null) inst.removeModifier(id);
    }
    //#else
    //$$ private static void applyMultiplier(LivingEntity entity, Attribute attr, UUID id, float factor) {
    //$$     if (factor == 1.0F) {
    //$$         removeModifier(entity, attr, id);
    //$$         return;
    //$$     }
    //$$     applyModifier(entity, attr, id, factor - 1.0F, true);
    //$$ }
    //$$
    //$$ private static void applyModifier(LivingEntity entity, Attribute attr, UUID id, float amount, boolean multiplyBase) {
    //$$     AttributeInstance inst = entity.getAttribute(attr);
    //$$     if (inst == null) return;
    //$$     AttributeModifier.Operation op = multiplyBase
    //$$             ? AttributeModifier.Operation.MULTIPLY_BASE
    //$$             : AttributeModifier.Operation.ADDITION;
    //$$     AttributeModifier existing = inst.getModifier(id);
    //$$     if (existing != null && existing.getAmount() == (double) amount && existing.getOperation() == op) return;
    //$$     if (existing != null) inst.removeModifier(id);
    //$$     inst.addPermanentModifier(new AttributeModifier(id, "gulliver size", amount, op));
    //$$ }
    //$$
    //$$ private static void removeModifier(LivingEntity entity, Attribute attr, UUID id) {
    //$$     AttributeInstance inst = entity.getAttribute(attr);
    //$$     if (inst != null && inst.getModifier(id) != null) inst.removeModifier(id);
    //$$ }
    //#endif
}
