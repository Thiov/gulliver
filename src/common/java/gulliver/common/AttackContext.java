package gulliver.common;

import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;

/**
 * The attacker behind the knockback that is about to happen. hurtServer
 * and causeExtraKnockback push their attacker at HEAD and pop at RETURN;
 * knockback reads the top. A stack, because those calls nest (thorns
 * inside a hit, Player.causeExtraKnockback calling up to LivingEntity's).
 * Vanilla applies knockback before it records lastDamageSource, so this
 * is the only reliable way to know who hit.
 */
public final class AttackContext {
    private AttackContext() {}

    private static final ThreadLocal<ArrayDeque<Object>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Object NONE = new Object();

    public static void push(Entity attacker) {
        STACK.get().push(attacker == null ? NONE : attacker);
    }

    public static void pop() {
        ArrayDeque<Object> s = STACK.get();
        if (!s.isEmpty()) s.pop();
    }

    public static Entity get() {
        Object top = STACK.get().peek();
        return top instanceof Entity e ? e : null;
    }
}
