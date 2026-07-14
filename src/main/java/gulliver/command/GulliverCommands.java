package gulliver.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import gulliver.api.IResizeableEntity;
import gulliver.api.IResizeableLiving;
import gulliver.common.GulliverConfig;
import gulliver.common.GulliverEnvoy;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Predicate;

/**
 * Brigadier port of the 1.6.4 mod's 14 commands. Names, args, and
 * permission levels mirror the originals exactly:
 *
 *   /basesize <size> [player]                  perm 2
 *   /basesizeadjust <factor> [player]          perm 2
 *   /halfsize [player]                         perm 2
 *   /doublesize [player]                       perm 2
 *   /showsize [player]                         perm 2
 *   /showmysize                                perm 0  (player only)
 *   /entitybasesize <id> <size>                perm 2
 *   /entitybasesizeadjust <id> <factor>        perm 2
 *   /entityhalfsize <id>                       perm 2
 *   /entitydoublesize <id>                     perm 2
 *   /entityshowsize <id>                       perm 2
 *   /instantkarma [player]                     perm 2
 *   /shoulderentity                            perm 0  (player only) — full
 *                                              behavior wired in Phase 12
 *   /reloadgullivercfg                         perm 4  (op-only reload)
 */
public final class GulliverCommands {
    private GulliverCommands() {}

    private static final SimpleCommandExceptionType INVALID_SIZE =
            new SimpleCommandExceptionType(Component.literal("Invalid size"));

    /** Vanilla op-level 2 equivalent in 26.x. */
    private static final Predicate<CommandSourceStack> OP2 = perm(Permissions.COMMANDS_MODERATOR);
    /** Vanilla op-level 4 equivalent (full admin / config reload). */
    private static final Predicate<CommandSourceStack> OP4 = perm(Permissions.COMMANDS_ADMIN);

    private static Predicate<CommandSourceStack> perm(Permission p) {
        return src -> src.permissions().hasPermission(p);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /basesize <size> [player]
        dispatcher.register(Commands.literal("basesize").requires(OP2)
                .then(Commands.argument("size", StringArgumentType.string())
                        .executes(ctx -> baseSize(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> baseSize(ctx, EntityArgument.getPlayer(ctx, "player"))))));

        // /basesizeadjust <factor> [player]
        dispatcher.register(Commands.literal("basesizeadjust").requires(OP2)
                .then(Commands.argument("factor", FloatArgumentType.floatArg())
                        .executes(ctx -> baseSizeAdjust(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> baseSizeAdjust(ctx, EntityArgument.getPlayer(ctx, "player"))))));

        // /halfsize [player]
        dispatcher.register(Commands.literal("halfsize").requires(OP2)
                .executes(ctx -> halfSize(ctx, ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> halfSize(ctx, EntityArgument.getPlayer(ctx, "player")))));

        // /doublesize [player]
        dispatcher.register(Commands.literal("doublesize").requires(OP2)
                .executes(ctx -> doubleSize(ctx, ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> doubleSize(ctx, EntityArgument.getPlayer(ctx, "player")))));

        // /showsize [player]
        dispatcher.register(Commands.literal("showsize").requires(OP2)
                .executes(ctx -> showSize(ctx, ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> showSize(ctx, EntityArgument.getPlayer(ctx, "player")))));

        // /showmysize  (perm 0, player-only)
        dispatcher.register(Commands.literal("showmysize")
                .requires(s -> s.getEntity() instanceof ServerPlayer)
                .executes(ctx -> showSize(ctx, ctx.getSource().getPlayerOrException())));

        // /entitybasesize <id> <size>
        dispatcher.register(Commands.literal("entitybasesize").requires(OP2)
                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .then(Commands.argument("size", StringArgumentType.string())
                                .executes(GulliverCommands::entityBaseSize))));

        // /entitybasesizeadjust <id> <factor>
        dispatcher.register(Commands.literal("entitybasesizeadjust").requires(OP2)
                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .then(Commands.argument("factor", FloatArgumentType.floatArg())
                                .executes(GulliverCommands::entityBaseSizeAdjust))));

        // /entityhalfsize <id>
        dispatcher.register(Commands.literal("entityhalfsize").requires(OP2)
                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .executes(ctx -> entityScale(ctx, true))));

        // /entitydoublesize <id>
        dispatcher.register(Commands.literal("entitydoublesize").requires(OP2)
                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .executes(ctx -> entityScale(ctx, false))));

        // /entityshowsize <id>
        dispatcher.register(Commands.literal("entityshowsize").requires(OP2)
                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .executes(GulliverCommands::entityShowSize)));

        // /instantkarma [player]
        dispatcher.register(Commands.literal("instantkarma").requires(OP2)
                .executes(ctx -> instantKarma(ctx, ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> instantKarma(ctx, EntityArgument.getPlayer(ctx, "player")))));

        // /shoulderentity  (V key) — cycle hand <-> shoulders
        // /shoulderentity drop  — drop everything carried
        // /shoulderentity throw — fling hand-held in look direction
        dispatcher.register(Commands.literal("shoulderentity")
                .requires(s -> s.getEntity() instanceof ServerPlayer)
                .executes(GulliverCommands::shoulderEntity)
                .then(Commands.literal("throw").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    return gulliver.common.ShoulderHelper.throwHeld(p) ? 1 : 0;
                }))
                .then(Commands.literal("drop").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    return gulliver.common.ShoulderHelper.drop(p) ? 1 : 0;
                })));

        // /reloadgullivercfg  (perm 4, the 1.6.4 mod's reload)
        dispatcher.register(Commands.literal("reloadgullivercfg").requires(OP4)
                .executes(ctx -> {
                    GulliverConfig.load();
                    ctx.getSource().sendSuccess(() -> Component.literal("Reloading Gulliver configuration"), true);
                    return 1;
                }));
    }

    // ---- player-targeted handlers ----

    private static int baseSize(CommandContext<CommandSourceStack> ctx, ServerPlayer target) throws CommandSyntaxException {
        String sizeStr = StringArgumentType.getString(ctx, "size");
        // Strict parse: a typo like "/basesize abc" must error, not
        // silently reset the target to 1.0.
        float size = GulliverEnvoy.getSizeFromRangeStringStrict(sizeStr, true);
        if (GulliverEnvoy.isInvalidSize(size)) throw INVALID_SIZE.create();
        ((IResizeableLiving) target).setBaseSize(size);
        successSize(ctx, target);
        return 1;
    }

    private static int baseSizeAdjust(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        float factor = FloatArgumentType.getFloat(ctx, "factor");
        if (GulliverEnvoy.isInvalidSize(factor)) {
            ctx.getSource().sendFailure(Component.literal("Invalid factor: " + factor));
            return 0;
        }
        ((IResizeableLiving) target).adjustBaseSize(factor);
        successSize(ctx, target);
        return 1;
    }

    private static int halfSize(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        // MixinLivingEntity overrides halveSize to route through
        // setBaseSize, which already clamps, refreshes dimensions, and
        // broadcasts the size sync — no extra plumbing needed here.
        ((IResizeableEntity) target).halveSize();
        successSize(ctx, target);
        return 1;
    }

    private static int doubleSize(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        ((IResizeableEntity) target).doubleSize();
        successSize(ctx, target);
        return 1;
    }

    private static int showSize(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        IResizeableLiving sized = (IResizeableLiving) target;
        float base = ((gulliver.access.IGulliverEntityInternal) target).gulliver$getSizeBaseMultiplier();
        float full = sized.getSizeMultiplier();
        String hs = GulliverEnvoy.getPlayerHeightStringFromSizeMult(full);
        if (!hs.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    target.getName().getString() + " base " + fmt(base) + " current " + fmt(full) + " (" + hs + ")"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    target.getName().getString() + " base " + fmt(base) + " current " + fmt(full)), false);
        }
        return 1;
    }

    /**
     * /shoulderentity (V keybind): cycle the carry slots — hand <-> shoulders.
     * If nothing is carried AND the player is looking at a carryable
     * LivingEntity in reach, pick that up. No fallback to nearest-anything
     * (that picked up dropped items by accident).
     */
    private static int shoulderEntity(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        // If carrying anything, V only cycles slots — does NOT pick up new.
        if (((gulliver.access.IGulliverShoulderInternal) player).gulliver$hasAnyCarry()) {
            gulliver.common.ShoulderHelper.toggleHandShoulder(player);
            return 1;
        }
        // Empty: pick up the LivingEntity the player is looking at, if any.
        double reach = player.blockInteractionRange();
        Entity targeted = null;
        double bestDistSq = reach * reach;
        net.minecraft.world.phys.Vec3 eye  = player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        net.minecraft.world.phys.Vec3 end  = eye.add(look.x * reach, look.y * reach, look.z * reach);
        // No extra inflate — the bbox.clip ray-trace below is the
        // strict gate. Tinies have small reach, so they need to be
        // close to / overlapping the target.
        net.minecraft.world.phys.AABB scan =
                player.getBoundingBox().expandTowards(look.scale(reach));
        for (Entity candidate : player.level().getEntities(player, scan)) {
            if (!(candidate instanceof net.minecraft.world.entity.LivingEntity)) continue;
            if (!gulliver.common.ShoulderHelper.canCarry(player, candidate)) continue;
            net.minecraft.world.phys.AABB cb = candidate.getBoundingBox().inflate(0.3D);
            java.util.Optional<net.minecraft.world.phys.Vec3> hit = cb.clip(eye, end);
            if (hit.isEmpty()) continue;
            double dsq = eye.distanceToSqr(hit.get());
            if (dsq < bestDistSq) {
                bestDistSq = dsq;
                targeted = candidate;
            }
        }
        if (targeted == null) return 0;
        gulliver.common.ShoulderHelper.pickUp(player, targeted);
        return 1;
    }

    private static int instantKarma(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        // Manual one-off reset — always works regardless of enableKarmaMode
        // (the config flag only controls AUTOMATIC reset on death, not
        // this command).
        ((IResizeableLiving) target).setBaseSize(GulliverEnvoy.getNewBasePlayerSize());
        successSize(ctx, target);
        return 1;
    }

    // ---- entity-id-targeted handlers (1.6.4 used numeric IDs; preserved here) ----

    private static int entityBaseSize(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Entity ent = entityById(ctx);
        if (ent == null) return 0;
        if (!(ent instanceof LivingEntity living)) {
            ctx.getSource().sendFailure(Component.literal("Entity " + ent.getId() + " is not a living entity"));
            return 0;
        }
        if (GulliverEnvoy.isDragonEntity(ent)) {
            ctx.getSource().sendFailure(Component.literal("Cannot resize dragon entities"));
            return 0;
        }
        String sizeStr = StringArgumentType.getString(ctx, "size");
        float size = GulliverEnvoy.getSizeFromRangeStringStrict(sizeStr, false);
        if (GulliverEnvoy.isInvalidSize(size)) throw INVALID_SIZE.create();
        ((IResizeableLiving) living).setBaseSize(size);
        successEntity(ctx, living);
        return 1;
    }

    private static int entityBaseSizeAdjust(CommandContext<CommandSourceStack> ctx) {
        Entity ent = entityById(ctx);
        if (ent == null) return 0;
        if (!(ent instanceof LivingEntity living)) {
            ctx.getSource().sendFailure(Component.literal("Entity " + ent.getId() + " is not a living entity"));
            return 0;
        }
        if (GulliverEnvoy.isDragonEntity(ent)) {
            ctx.getSource().sendFailure(Component.literal("Cannot resize dragon entities"));
            return 0;
        }
        float factor = FloatArgumentType.getFloat(ctx, "factor");
        if (GulliverEnvoy.isInvalidSize(factor)) {
            ctx.getSource().sendFailure(Component.literal("Invalid factor: " + factor));
            return 0;
        }
        ((IResizeableLiving) living).adjustBaseSize(factor);
        successEntity(ctx, living);
        return 1;
    }

    private static int entityScale(CommandContext<CommandSourceStack> ctx, boolean halve) {
        Entity ent = entityById(ctx);
        if (ent == null) return 0;
        if (!(ent instanceof LivingEntity living)) {
            ctx.getSource().sendFailure(Component.literal("Entity " + ent.getId() + " is not a living entity"));
            return 0;
        }
        if (GulliverEnvoy.isDragonEntity(ent)) {
            ctx.getSource().sendFailure(Component.literal("Cannot resize dragon entities"));
            return 0;
        }
        // LivingEntity's halveSize/doubleSize route through setBaseSize
        // (clamp + refresh + broadcast) — see MixinLivingEntity.
        if (halve) ((IResizeableEntity) living).halveSize();
        else ((IResizeableEntity) living).doubleSize();
        successEntity(ctx, living);
        return 1;
    }

    private static int entityShowSize(CommandContext<CommandSourceStack> ctx) {
        Entity ent = entityById(ctx);
        if (ent == null) return 0;
        float full = ((IResizeableEntity) ent).getSizeMultiplier();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Entity " + ent.getId() + " size " + fmt(full)), false);
        return 1;
    }

    // ---- helpers ----

    /**
     * Human-friendly size formatting for chat feedback: max 3 decimals,
     * trailing zeros stripped — "0.5" instead of "0.5000001".
     */
    private static String fmt(float v) {
        String s = String.format(java.util.Locale.ROOT, "%.3f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static Entity entityById(CommandContext<CommandSourceStack> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        // 1.6.4 looked up the ID in the sender's world (or in the stored world
        // for command-block senders). Fabric: scan all server levels.
        for (ServerLevel lvl : ctx.getSource().getServer().getAllLevels()) {
            Entity e = lvl.getEntity(id);
            if (e != null) return e;
        }
        ctx.getSource().sendFailure(Component.literal("No entity with id " + id));
        return null;
    }

    private static void successSize(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        // Read the DESTINATION (the value the entity is tweening toward),
        // not the live mid-lerp base — otherwise users see ugly intermediate
        // floats like 0.508 right after pressing /halfsize.
        gulliver.access.IGulliverEntityInternal access =
                (gulliver.access.IGulliverEntityInternal) target;
        float base = access.gulliver$getSizeBaseDestMultiplier();
        float full = base * access.gulliver$getSizePotionMultiplier()
                * access.gulliver$getSizeItemMultiplier();
        ctx.getSource().sendSuccess(() -> Component.literal(
                target.getName().getString() + " base " + fmt(base) + " current " + fmt(full)), true);
    }

    private static void successEntity(CommandContext<CommandSourceStack> ctx, LivingEntity living) {
        gulliver.access.IGulliverEntityInternal access =
                (gulliver.access.IGulliverEntityInternal) living;
        float base = access.gulliver$getSizeBaseDestMultiplier();
        float full = base * access.gulliver$getSizePotionMultiplier()
                * access.gulliver$getSizeItemMultiplier();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Entity " + living.getId() + " base " + fmt(base) + " current " + fmt(full)), true);
    }
}
