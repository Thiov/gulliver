package gulliver.common;

import net.minecraft.world.entity.Entity;

/**
 * Thread-local capture of the current hurtServer attacker, set at HEAD
 * of hurtServer and cleared at RETURN. Mixins that need attacker context
 * (knockback ratio, damage scaling) read from here.
 *
 * Vanilla calls knockback() and computes damage BEFORE setting
 * lastDamageSource (offsets 460/557 in the disasm), so getLastDamageSource()
 * is unreliable at those points — this thread-local is the workaround.
 */
public final class AttackContext {
    private AttackContext() {}

    private static final ThreadLocal<Entity> ATTACKER = new ThreadLocal<>();

    public static void set(Entity attacker) { ATTACKER.set(attacker); }
    public static Entity get() { return ATTACKER.get(); }
    public static void clear() { ATTACKER.remove(); }
}
