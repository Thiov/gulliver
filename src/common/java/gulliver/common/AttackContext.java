package gulliver.common;

import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * The attacker behind the knockback that is about to happen. hurtServer
 * and causeExtraKnockback push a frame at HEAD and pop it at RETURN;
 * knockback reads the innermost frame that concerns its victim. Frames
 * nest (thorns inside a hit, Player.causeExtraKnockback calling up to
 * LivingEntity's). Vanilla applies knockback before it records
 * lastDamageSource, so this is the only reliable way to know who hit.
 *
 * A RETURN hook does not run when another mod cancels the method at HEAD
 * after ours, so a frame can be left behind. Three things keep that
 * harmless: frames are matched to their victim (a stale frame for one
 * entity never speaks for another), a pop also discards every frame above
 * the one it closes (leaks from inner calls vanish with the outer call),
 * and the stack is cleared at both ends of every level tick and capped in
 * depth.
 */
public final class AttackContext {
    private AttackContext() {}

    private static final int MAX_DEPTH = 16;

    /** attacker may be null (environmental damage); victim null = any victim (sweep attacks). */
    private record Frame(Entity attacker, Entity victim) {}

    private static final ThreadLocal<ArrayDeque<Frame>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    public static void push(Entity attacker, Entity victim) {
        ArrayDeque<Frame> s = STACK.get();
        if (s.size() >= MAX_DEPTH) s.removeLast();
        s.push(new Frame(attacker, victim));
    }

    /** Closes the innermost frame opened for this victim, together with any left open above it. */
    public static void pop(Entity victim) {
        ArrayDeque<Frame> s = STACK.get();
        boolean found = false;
        for (Frame f : s) {
            if (f.victim == victim) {
                found = true;
                break;
            }
        }
        if (!found) return;
        while (!s.isEmpty()) {
            if (s.pop().victim == victim) return;
        }
    }

    /** The attacker of the knockback now happening to {@code victim}, or null if none is known. */
    public static Entity get(Entity victim) {
        for (Iterator<Frame> it = STACK.get().iterator(); it.hasNext(); ) {
            Frame f = it.next();
            if (f.victim == null || f.victim == victim) return f.attacker;
        }
        return null;
    }

    /** Forgets everything; called between level ticks so nothing outlives a tick. */
    public static void reset() {
        ArrayDeque<Frame> s = STACK.get();
        if (!s.isEmpty()) s.clear();
    }
}
