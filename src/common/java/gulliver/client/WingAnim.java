package gulliver.client;

import gulliver.Gulliver;
import gulliver.access.IGulliverWings;
import gulliver.api.IResizeableEntity;
import gulliver.common.SyncedSettings;
import gulliver.common.WingType;
import gulliver.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * How every winged player's wings move and sound on this client
 * (TinyWings): opening and folding, the beat, the body leaning into its
 * flight, the metamorphosis, the buzz and flutter, and fairy sparkles.
 * Ticked for each player in view; WingRender draws from the result.
 */
public final class WingAnim {
    private WingAnim() {}

    // The wings ship without sounds. A resource pack (or the mod's own assets) that defines these
    // events in sounds.json is played as is; until then nothing is played and nothing is logged.
    static final SoundEvent BUZZ_FLY = sound("wings.buzz_fly");
    static final SoundEvent BUZZ_BEE = sound("wings.buzz_bee");
    static final SoundEvent FAIRY = sound("wings.fairy");
    static final SoundEvent FLUTTER = sound("wings.flutter");
    static final SoundEvent SPROUT = sound("wings.sprout");

    private static SoundEvent sound(String name) {
        return SoundEvent.createVariableRangeEvent(Gulliver.id(name));
    }

    /** Whether a sound for this event is installed. */
    private static boolean installed(SoundEvent event) {
        //#if MC >= 1.21.2
        return Minecraft.getInstance().getSoundManager().getSoundEvent(event.location()) != null;
        //#else
        //$$ return Minecraft.getInstance().getSoundManager().getSoundEvent(event.getLocation()) != null;
        //#endif
    }

    /** Length of the metamorphosis, in ticks. */
    public static final int SPROUT_TICKS = 60;

    public static final class State {
        /** Wingbeat phase, in beats. */
        float phase, prevPhase;
        /** 0 folded .. 1 open. */
        float spread, prevSpread;
        /** How far the wings sweep through their beat, 0 (still) .. 1 (full strokes). */
        float stroke, prevStroke;
        /** Body lean, degrees forward. */
        float tilt, prevTilt;
        /** 0 just sprouting .. 1 grown. */
        float growth = 1.0F, prevGrowth = 1.0F;
        int sproutTicks = -1;
        WingLoop loop;
        /** Ticks until a resting butterfly opens and closes its wings again. */
        int basking = 60;
        float bask, prevBask;

        /** How far open the wings are this tick (no partial tick: for the legacy model pose). */
        public float spread() {
            return spread;
        }
    }

    public static State of(Entity e) {
        if (!(e instanceof IGulliverWings w)) return null;
        Object o = w.gulliver$wingAnim();
        if (o instanceof State s) return s;
        State s = new State();
        w.gulliver$setWingAnim(s);
        return s;
    }

    /** Whether this player's wings are out at all: it has some and is small enough to wear them. */
    public static boolean visible(Entity e) {
        return e instanceof IGulliverWings w && w.gulliver$wingType() != WingType.NONE
                && ((IResizeableEntity) e).getSizeMultiplier() <= SyncedSettings.wingsMaxSize() + 1.0E-4F;
    }

    // ---- network --------------------------------------------------------------

    public static void onWings(Payloads.Wings p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        if (!(e instanceof IGulliverWings w)) return;
        WingType type = WingType.byId(p.wingType());
        w.gulliver$setWings(type, p.color());
        if (e != mc.player || p.flight() == IGulliverWings.FOLDED) w.gulliver$setWingFlight(p.flight());
        if (e == mc.player && p.flight() == IGulliverWings.FOLDED) WingFlight.forceFolded();
        if (p.sprout() && type != WingType.NONE) {
            State s = of(e);
            s.sproutTicks = 0;
            s.growth = s.prevGrowth = 0.0F;
            if (installed(SPROUT)) mc.getSoundManager().play(new SimpleSoundInstance(SPROUT, SoundSource.PLAYERS, 0.9F, pitchFor(e),
                    RandomSource.create(), e.getX(), e.getY(), e.getZ()));
        }
    }

    static float pitchFor(Entity e) {
        float size = Math.max(0.03F, ((IResizeableEntity) e).getSizeMultiplier());
        return Mth.clamp((float) Math.sqrt(0.25F / size), 0.85F, 1.35F);
    }

    // ---- per tick -------------------------------------------------------------

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.isPaused()) return;
        for (Player p : mc.level.players()) {
            if (p instanceof IGulliverWings w && (w.gulliver$wingType() != WingType.NONE || w.gulliver$wingAnim() != null)) {
                tickOne(mc, p, w);
            }
        }
    }

    private static void tickOne(Minecraft mc, Player p, IGulliverWings w) {
        State s = of(p);
        s.prevPhase = s.phase;
        s.prevSpread = s.spread;
        s.prevStroke = s.stroke;
        s.prevTilt = s.tilt;
        s.prevGrowth = s.growth;
        s.prevBask = s.bask;
        WingType type = w.gulliver$wingType();
        boolean shown = visible(p);
        byte flight = shown ? w.gulliver$wingFlight() : IGulliverWings.FOLDED;
        boolean flying = IGulliverWings.flying(flight);

        // Metamorphosis: the wings push out of the back, crumpled, and unfold.
        if (s.sproutTicks >= 0) {
            s.sproutTicks++;
            s.growth = Math.min(1.0F, s.sproutTicks / (float) SPROUT_TICKS);
            sproutParticles(mc, p, type, s);
            if (s.sproutTicks >= SPROUT_TICKS) s.sproutTicks = -1;
        }

        float spreadTarget = flying ? 1.0F : 0.0F;
        if (s.sproutTicks >= 0) spreadTarget = Mth.clamp((s.growth - 0.55F) / 0.3F, 0.0F, 1.0F);
        s.spread += (spreadTarget - s.spread) * (flying ? 0.45F : 0.25F);

        float strokeTarget = switch (flight) {
            case IGulliverWings.HOVER, IGulliverWings.CRUISE, IGulliverWings.CLIMB, IGulliverWings.DASH -> 1.0F;
            case IGulliverWings.DIVE -> 0.35F;
            default -> 0.0F;
        };
        // The new wings' first beats as the metamorphosis ends.
        if (s.sproutTicks >= 0 && s.growth > 0.85F) strokeTarget = 0.6F;
        s.stroke += (strokeTarget - s.stroke) * 0.3F;

        float rate = switch (flight) {
            case IGulliverWings.CLIMB -> 1.3F;
            case IGulliverWings.DASH -> 1.25F;
            case IGulliverWings.DIVE -> 0.6F;
            default -> 1.0F;
        };
        float beatHz = type.beatHz;
        if (s.sproutTicks >= 0) beatHz = Math.min(beatHz, 4.0F);
        s.phase += beatHz * rate / 20.0F * Math.max(0.15F, s.stroke);
        if (s.phase > 1000.0F) {
            s.phase -= 1000.0F;
            s.prevPhase -= 1000.0F;
        }

        // A butterfly at rest opens and closes its wings now and then.
        if (type == WingType.BUTTERFLY && !flying && shown && s.sproutTicks < 0) {
            if (--s.basking <= 0) s.basking = 80 + p.getRandom().nextInt(120);
            float t = s.basking < 40 ? Mth.sin(s.basking / 40.0F * Mth.PI) : 0.0F;
            s.bask = t;
        } else {
            s.bask *= 0.8F;
        }

        // Lean into the flight: forward with speed, back a little when climbing or hovering.
        float tiltTarget = 0.0F;
        if (flying) {
            double dx = p.getX() - p.xo;
            double dz = p.getZ() - p.zo;
            float speed = (float) Math.sqrt(dx * dx + dz * dz);
            float rel = Mth.clamp(speed / Math.max(0.05F, type.cruise), 0.0F, 1.8F);
            tiltTarget = rel * 38.0F;
            if (flight == IGulliverWings.HOVER) tiltTarget -= 6.0F;
            if (flight == IGulliverWings.CLIMB) tiltTarget -= 12.0F;
            if (flight == IGulliverWings.DIVE) tiltTarget += 18.0F;
            tiltTarget = Mth.clamp(tiltTarget, -15.0F, 70.0F);
        }
        s.tilt += (tiltTarget - s.tilt) * 0.2F;

        // Fairy dust behind a flying fairy.
        if (type == WingType.FAIRY && shown && s.spread > 0.5F && p.getRandom().nextInt(3) == 0) {
            float size = ((IResizeableEntity) p).getSizeMultiplier();
            double y = p.getY() + p.getBbHeight() * 0.7D;
            ParticleScale.pushScale(size * 0.6F);
            try {
                p.level().addParticle(ParticleTypes.END_ROD, p.getX() + (p.getRandom().nextDouble() - 0.5D) * 0.4D * size,
                        y, p.getZ() + (p.getRandom().nextDouble() - 0.5D) * 0.4D * size, 0.0D, -0.01D, 0.0D);
            } finally {
                ParticleScale.pop();
            }
        }

        sounds(mc, p, type, flight, s);
    }

    private static void sproutParticles(Minecraft mc, Player p, WingType type, State s) {
        if (s.sproutTicks % 4 != 0) return;
        float size = ((IResizeableEntity) p).getSizeMultiplier();
        double y = p.getY() + p.getBbHeight() * 0.7D;
        ParticleScale.pushScale(Math.max(0.05F, size * 0.5F));
        try {
            for (int i = 0; i < 2; i++) {
                double ox = (p.getRandom().nextDouble() - 0.5D) * 0.8D * size;
                double oz = (p.getRandom().nextDouble() - 0.5D) * 0.8D * size;
                p.level().addParticle(s.growth < 0.6F ? ParticleTypes.WAX_ON : ParticleTypes.END_ROD,
                        p.getX() + ox, y, p.getZ() + oz, ox * 0.5D, 0.02D, oz * 0.5D);
            }
        } finally {
            ParticleScale.pop();
        }
    }

    private static void sounds(Minecraft mc, Player p, WingType type, byte flight, State s) {
        boolean flying = IGulliverWings.flying(flight) && visible(p);
        if (type == WingType.BUTTERFLY) {
            // A soft flutter on each downstroke.
            if (flying && s.stroke > 0.5F && Mth.floor(s.phase) != Mth.floor(s.prevPhase) && installed(FLUTTER)) {
                mc.getSoundManager().play(new SimpleSoundInstance(FLUTTER, SoundSource.PLAYERS,
                        0.45F, pitchFor(p) * (0.9F + p.getRandom().nextFloat() * 0.2F), RandomSource.create(),
                        p.getX(), p.getY() + p.getBbHeight() * 0.7D, p.getZ()));
            }
            return;
        }
        if (flying && (s.loop == null || s.loop.isStopped())) {
            SoundEvent event = type == WingType.BEE ? BUZZ_BEE : type == WingType.FAIRY ? FAIRY : BUZZ_FLY;
            if (!installed(event)) return;
            s.loop = new WingLoop(p, event);
            mc.getSoundManager().play(s.loop);
        }
    }

    /** A wing's buzz, following its player and swelling and fading with what the wings do. */
    static final class WingLoop extends AbstractTickableSoundInstance {
        private final Player player;
        private float fade;

        WingLoop(Player player, SoundEvent event) {
            super(event, SoundSource.PLAYERS, RandomSource.create());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
        }

        @Override
        public void tick() {
            if (player.isRemoved()) {
                stop();
                return;
            }
            IGulliverWings w = (IGulliverWings) player;
            byte flight = visible(player) ? w.gulliver$wingFlight() : IGulliverWings.FOLDED;
            float want = switch (flight) {
                case IGulliverWings.HOVER -> 0.55F;
                case IGulliverWings.CRUISE -> 0.6F;
                case IGulliverWings.CLIMB -> 0.8F;
                case IGulliverWings.DASH -> 0.85F;
                case IGulliverWings.DIVE -> 0.3F;
                case IGulliverWings.GLIDE -> 0.08F;
                default -> 0.0F;
            };
            fade += (want - fade) * 0.25F;
            if (want == 0.0F && fade < 0.02F) {
                stop();
                return;
            }
            float rise = switch (flight) {
                case IGulliverWings.CLIMB -> 1.12F;
                case IGulliverWings.DASH -> 1.18F;
                case IGulliverWings.DIVE -> 0.88F;
                default -> 1.0F;
            };
            volume = Math.max(0.001F, fade);
            pitch = pitchFor(player) * rise;
            x = player.getX();
            y = player.getY() + player.getBbHeight() * 0.6D;
            z = player.getZ();
        }
    }
}
