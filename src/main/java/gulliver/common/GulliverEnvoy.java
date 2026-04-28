package gulliver.common;

import gulliver.api.IResizeableEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.IllegalFormatException;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Runtime helper port of the 1.6.4 GulliverEnvoy. Phase 3(2) covers only
 * the size-resolution subset (parsing, range strings, height notation,
 * entity-class checks, per-entity min/max/base lookups). Game-mechanic
 * helpers (canOpenSingleBlock, leaveHugeFootprints, getRisingUpdraft, …)
 * land in later phases as their consuming code paths come online.
 *
 * Translations from the 1.6.4 source (see reference/.../GulliverEnvoy.java):
 *   q   = entity.level()
 *   q.I = level.isClientSide()
 *   nt.b(entity)    = EntityType.getKey(entity.getType()).getPath()
 *   nt.a(entity)    = (legacy numeric ID — not directly portable)
 *   ua = Villager;  th = Monster;  uf = Player
 *   oj.c (CreatureAttribute.ARTHROPOD) = EntityTypeTags.ARTHROPOD
 *
 * All numeric formulas, branching, and special-case ordering preserved
 * exactly as the original — this is the source of truth for the port's
 * sizing behavior.
 */
public final class GulliverEnvoy {
    private static final Random RAND = new Random();

    private GulliverEnvoy() {}

    public static Random getRand() { return RAND; }

    // ---- entity classification (1.6.4 isNPC/isMonster/isAnimal/isArthropod) ----

    public static boolean isNPC(Entity entity) {
        return entity instanceof Villager;
    }

    public static boolean isMonster(Entity entity) {
        return entity instanceof Monster;
    }

    public static boolean isAnimal(Entity entity) {
        return !isNPC(entity) && !isMonster(entity) && !(entity instanceof Player);
    }

    public static boolean isArthropod(Entity entity) {
        if (!isMonster(entity)) return false;
        try {
            return entity.getType().builtInRegistryHolder().is(EntityTypeTags.ARTHROPOD);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isDragonEntity(Entity entity) {
        // 1.6.4: instanceof EntityDragon || EntityDragonPart
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String path = key.getPath();
        return "ender_dragon".equals(path) || "ender_dragon_part".equals(path);
    }

    private static String entityName(Entity entity) {
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key.getPath().toLowerCase();
    }

    // ---- size resolution: per-entity max / min / new-base ----

    public static double getMaxSizeForEntity(Entity entity) {
        Level level = entity.level();
        GulliverConfig.General g = GulliverConfig.INSTANCE.general;
        if (level == null || level.isClientSide()) {
            return g.maxEntitySize;
        }
        GulliverConfig.SizeLimit sl = GulliverConfig.INSTANCE.sizeLimit;

        String name = entityName(entity);
        if (name != null) {
            Double override = sl.maxOverrides.get(name);
            if (override != null && override != 0.0D) return override;
        }
        if (entity instanceof Player) {
            if (sl.maxPlayerSize != 0.0D) return sl.maxPlayerSize;
        } else if (isNPC(entity)) {
            if (sl.maxNpcSize != 0.0D) return sl.maxNpcSize;
        } else if (isMonster(entity)) {
            if (sl.maxMonsterSize != 0.0D) return sl.maxMonsterSize;
        } else {
            if (sl.maxAnimalSize != 0.0D) return sl.maxAnimalSize;
        }
        return g.maxEntitySize;
    }

    public static double getMinSizeForEntity(Entity entity) {
        Level level = entity.level();
        GulliverConfig.General g = GulliverConfig.INSTANCE.general;
        if (level == null || level.isClientSide()) {
            return g.minEntitySize;
        }
        GulliverConfig.SizeLimit sl = GulliverConfig.INSTANCE.sizeLimit;

        String name = entityName(entity);
        if (name != null) {
            Double override = sl.minOverrides.get(name);
            if (override != null && override != 0.0D) return override;
        }
        if (entity instanceof Player) {
            if (sl.minPlayerSize != 0.0D) return sl.minPlayerSize;
        } else if (isNPC(entity)) {
            if (sl.minNpcSize != 0.0D) return sl.minNpcSize;
        } else if (isMonster(entity)) {
            if (sl.minMonsterSize != 0.0D) return sl.minMonsterSize;
        } else {
            if (sl.minAnimalSize != 0.0D) return sl.minAnimalSize;
        }
        return g.minEntitySize;
    }

    public static float getNewBasePlayerSize() {
        return getSizeFromRangeString(GulliverConfig.INSTANCE.spawnSize.basePlayerSize, true);
    }

    /**
     * 1.6.4 lookup order: per-entity 'entity{id}' override → per-entity-name
     * override → class default. Modern MC has no numeric entity IDs, but
     * legacy 'entity{id}' override keys remain honoured if present in the
     * config (string-equality lookup only).
     */
    public static float getNewBaseEntitySize(Entity entity) {
        Level level = entity.level();
        if (level == null || level.isClientSide()) {
            return 1.0F;
        }
        GulliverConfig.SpawnSize ss = GulliverConfig.INSTANCE.spawnSize;
        String name = entityName(entity);
        if (name != null) {
            String byName = ss.overrides.get(name);
            if (byName != null && !byName.isEmpty()) {
                return getSizeFromRangeString(byName, false);
            }
        }
        if (isNPC(entity)) {
            return getSizeFromRangeString(ss.baseNpcSize, false);
        }
        if (isMonster(entity)) {
            return getSizeFromRangeString(ss.baseMonsterSize, false);
        }
        return getSizeFromRangeString(ss.baseAnimalSize, false);
    }

    // ---- string parsing (verbatim from 1.6.4) ----

    public static float parseFloatString(String s, float defv) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException ex) {
            return defv;
        }
    }

    public static boolean isInvalidSize(float f) {
        return f <= 0.0F || Float.isInfinite(f) || Float.isNaN(f);
    }

    /**
     * 1.6.4 getSizeFromRangeString:
     *   - comma-separated set: pick uniformly random
     *   - "a-b" range: f1 + (f2-f1) * random(0..8)/8  (9-step uniform quantization)
     *   - single value: parse directly
     *   - if allowHeights, parsePlayerHeight is used for parsing
     */
    public static float getSizeFromRangeString(String sizes, boolean allowHeights) {
        float f1;
        float f2 = 1.0F;
        String[] sets = sizes.split(",");
        int r = RAND.nextInt(sets.length);
        String[] range = sets[r].split("-");
        String s1 = range[0];
        String s2 = range.length > 1 ? range[1] : "";

        try {
            f1 = allowHeights ? parsePlayerHeight(s1) : Float.parseFloat(s1);
        } catch (NumberFormatException ex) {
            return 1.0F;
        }
        if (range.length == 1) return f1;
        try {
            f2 = allowHeights ? parsePlayerHeight(s2) : Float.parseFloat(s2);
        } catch (NumberFormatException ex) {
            return f1;
        }
        if (f1 > f2) return f1;

        f1 += (f2 - f1) * RAND.nextInt(9) / 8.0F;
        return f1;
    }

    /**
     * 1.6.4 parsePlayerHeight — supports raw multipliers and human height
     * notation: "5'9\"", "5ft9in", "5'", "9\"", "175cm", "1.8m", "70in".
     *
     * Player height baseline for division: 1.8m (matches the 1.6.4 mod).
     *   inches    → ×0.0254 m
     *   feet      → ×0.3048 m
     *   centimetres → ×0.01 m
     *   metres    → ×1.0
     * Then divide by 1.8m to get a multiplier.
     */
    public static float parsePlayerHeight(String s) {
        float sm;
        boolean isInches = s.endsWith("in");
        boolean isFeet = s.endsWith("ft");
        boolean isMetric = s.endsWith("m");
        boolean isCentimeters = s.endsWith("cm");

        if ((isInches || isFeet || isCentimeters) && s.length() > 2) {
            s = s.substring(0, s.length() - 2);
        } else if (isMetric && s.length() > 1) {
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("\"") && s.length() > 1) {
            s = s.substring(0, s.length() - 1);
            isInches = true;
        } else if (s.endsWith("'") && s.length() > 1) {
            s = s.substring(0, s.length() - 1);
            isFeet = true;
        }

        if (isInches) {
            String[] parts = s.split("'|ft");
            if (parts.length == 2) {
                sm = Float.parseFloat(parts[0]) * 12.0F + Float.parseFloat(parts[parts.length - 1]);
            } else {
                sm = Float.parseFloat(s);
            }
        } else {
            sm = Float.parseFloat(s);
        }

        if (isInches || isFeet || isMetric || isCentimeters) {
            sm /= 1.8F;
            if (isCentimeters) {
                sm *= 0.01F;
            } else if (isInches) {
                sm *= 0.0254F;
            } else if (isFeet) {
                sm *= 0.3048F;
            }
            // metres: ×1.0, no change
        }

        return sm;
    }

    public static String getPlayerHeightStringFromSizeMult(float sm) {
        try {
            float h = sm * 1.8F;
            float hf = h / 0.3048F;
            boolean useMeters = h >= 1.0F;
            boolean useFeet = hf >= 1.0F;
            String s;
            if (useMeters) {
                s = "%.2fm";
            } else {
                s = "%.2fcm";
                h *= 100.0F;
            }
            if (useFeet) {
                return String.format(s + " (%dft%.2fin)", h, (int) hf, hf * 12.0F % 12.0F);
            }
            return String.format(s + " (%.2fin)", h, hf * 12.0F);
        } catch (IllegalFormatException ignored) {
        }
        return "";
    }

    // ---- list helpers ----

    public static void pruneSmallerEntities(float minSize, List<? extends Entity> list) {
        if (minSize == 0.0F) return;
        Iterator<? extends Entity> it = list.iterator();
        while (it.hasNext()) {
            Entity e = it.next();
            if (((gulliver.api.IResizeableEntity) e).getSizeMultiplier() < minSize) {
                it.remove();
            }
        }
    }

    public static void pruneLargerEntities(float maxSize, List<? extends Entity> list) {
        if (maxSize == 0.0F) return;
        Iterator<? extends Entity> it = list.iterator();
        while (it.hasNext()) {
            Entity e = it.next();
            if (((gulliver.api.IResizeableEntity) e).getSizeMultiplier() > maxSize) {
                it.remove();
            }
        }
    }

    /**
     * Indirection point for any code that wants the LivingEntity-typed
     * size multiplier without an explicit cast.
     */
    public static float getSizeMultiplier(LivingEntity living) {
        return ((gulliver.api.IResizeableEntity) living).getSizeMultiplier();
    }

    // ---- canOpen* / smallBlockOpeningStrength (1.6.4 GulliverEnvoy) ----

    /**
     * Ports 1.6.4 GulliverEnvoy.smallBlockOpeningStrength verbatim:
     *   start at 3
     *   while mult < 0.6F: strengthadj--; mult *= 2.0F
     *   if mult >= 0.6F at start: return 3 (no descent)
     *   if holdingPointyItem: +1
     *   if hasEffect(SLOWNESS): -(amplifier+1)
     *   if hasEffect(HASTE):    +(amplifier+1)
     *
     * The original source short-circuited "mult >= 0.6F" before the loop —
     * this preserves that ordering exactly.
     */
    public static int smallBlockOpeningStrength(LivingEntity living) {
        float mult = ((IResizeableEntity) living).getSizeMultiplier();
        int strengthadj = 3;
        if (mult >= 0.6F) {
            return strengthadj;
        }
        while (mult < 0.6F) {
            strengthadj--;
            mult *= 2.0F;
        }
        if (holdingPointyItem(living)) {
            strengthadj++;
        }
        MobEffectInstance slowness = living.getEffect(MobEffects.SLOWNESS);
        if (slowness != null) {
            strengthadj -= slowness.getAmplifier() + 1;
        }
        MobEffectInstance haste = living.getEffect(MobEffects.HASTE);
        if (haste != null) {
            strengthadj += haste.getAmplifier() + 1;
        }
        return strengthadj;
    }

    public static boolean canOpenSingleBlock(LivingEntity living) {
        return smallBlockOpeningStrength(living) >= 1;
    }

    public static boolean canOpenDoubleBlock(LivingEntity living) {
        return smallBlockOpeningStrength(living) >= 2;
    }

    /**
     * Huge entities trigger buttons by stepping on them, like pressure plates.
     * Mirrors 1.6.4 GulliverEnvoy.canPressPlateLikeButton.
     */
    public static boolean canPressPlateLikeButton(LivingEntity living) {
        return ((IResizeableEntity) living).isHuge();
    }

    // ---- isItemPointy / holdingPointyItem (1.6.4 GulliverEnvoy) ----

    /**
     * Ports 1.6.4 GulliverEnvoy.isItemPointy. The 1.6.4 version checked:
     *   - block-form items where the block was Cactus, ThornyFlower, or
     *     specific block IDs (snowball, scissors-block, etc.)
     *   - or items: ItemSword, ItemPickaxe, ItemAxe, ItemShears, ItemHoe,
     *     plus snowball, dye-stick, scissors, magma-cream-style.
     *
     * 26.x replaces the per-class instance check with item tags
     * (minecraft:swords, :pickaxes, :axes, :shovels, :hoes). Snowball,
     * shears, and the cactus/thorny-flower block-items map to their
     * Items.* equivalents by identity. Behavior matches the 1.6.4
     * "pointy = yes" set as closely as the new item taxonomy allows.
     */
    public static boolean isItemPointy(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.typeHolder().is(ItemTags.SWORDS)) return true;
        if (stack.typeHolder().is(ItemTags.PICKAXES)) return true;
        if (stack.typeHolder().is(ItemTags.AXES)) return true;
        if (stack.typeHolder().is(ItemTags.HOES)) return true;
        if (stack.typeHolder().is(ItemTags.SHOVELS)) return true;
        if (stack.is(Items.SHEARS)) return true;
        if (stack.is(Items.SNOWBALL)) return true;
        if (stack.is(Items.CACTUS)) return true;
        // Thorny-flower / scissors / magma-cream had no direct 26.x
        // equivalents in the 1.6.4 vanilla set the original referenced.
        return false;
    }

    public static boolean holdingPointyItem(LivingEntity living) {
        return isItemPointy(living.getMainHandItem()) || isItemPointy(living.getOffhandItem());
    }

    /**
     * 1.6.4 GulliverEnvoy.canSizeGrief: players are gated by an
     * unmapped 'bG.d' flag (likely peaceful/disable-damage) AND the
     * 'sizeGriefing' gamerule (defaulting on if unset). Mobs are also
     * gated by mobGriefing.
     *
     * Modern translation: the 1.6.4 mod registered 'sizeGriefing' as a
     * world gamerule on server start. We don't replicate that custom
     * gamerule here yet (Phase 19 / config polish), so this falls back
     * to: players → always allowed; mobs → mobGriefing gamerule.
     */
    public static boolean canSizeGrief(Entity entity) {
        if (entity instanceof Player) {
            return true;
        }
        if (entity.level() instanceof ServerLevel sl) {
            return sl.getGameRules().get(GameRules.MOB_GRIEFING);
        }
        return false;
    }

    // ---- huge-entity ground effects (1.6.4 GulliverEnvoy) ----

    /**
     * 1.6.4 stepOnSmallerEntities verbatim. A huge entity walking in the
     * world looks for small entities at its foot level and crushes them
     * with PASSIVE damage equal to ceil(2 × stepperRoot / targetRoot).
     *
     * Original predicates:
     *   - collideableRideEntity(other) — target is collidable, not the
     *     stepper, not its passenger.
     *   - canSquish(other) — stepper much larger than target. Modern
     *     proxy: target's sizeMultiplier < stepper's / 2 (so a 4× giant
     *     squishes only sub-2× targets, a normal-size player can't
     *     squish another normal player just by walking near them).
     *   - foot-level test: target's top ≥ stepper's bottom AND target's
     *     bottom ≤ stepper's bottom + (1/16 × stepper's height). Target
     *     is genuinely under the stepper's foot.
     */
    /**
     * 1.6.4 checkSupportingBlocksForHuge: when a huge entity stands on
     * brittle materials (glass, wool, leaves), those blocks
     * probabilistically break under the weight.
     *
     * Logic:
     *   - Scan blocks under the entity's hitbox at foot level.
     *   - Categorize each: solid (stone, dirt) → entity is "supported",
     *     no breaking.
     *     Brittle (glass, wool) → counted; only break if no solid support.
     *     Flimsy (leaves, snow layer) → not counted but doesn't support.
     *   - If unsupported (no solid blocks), break brittle blocks: blocks
     *     toward the center break at 1/10, edge blocks at 1/(10×count).
     *
     * Modern translation:
     *   - "brittle" = BlockTags.WOOL OR class == GlassBlock /
     *     StainedGlassBlock / TintedGlassBlock.
     *   - "flimsy" = BlockTags.LEAVES OR !state.isFaceSturdy(level, pos, UP)
     *     (the modern test for "supports an entity standing on it").
     *   - Edge width uses bbox-width / 3, same as 1.6.4 (Mth.ceil(O / 3)).
     */
    public static void checkSupportingBlocksForHuge(net.minecraft.world.entity.LivingEntity stepper,
                                                     java.util.Random rand) {
        net.minecraft.world.level.Level level = stepper.level();
        if (level.isClientSide()) return;
        if (!canSizeGrief(stepper)) return;

        net.minecraft.world.phys.AABB box = stepper.getBoundingBox();
        int y = net.minecraft.util.Mth.floor(box.minY - 0.2D - 0.001D);
        int sx1 = net.minecraft.util.Mth.floor(box.minX + 0.001D);
        int sz1 = net.minecraft.util.Mth.floor(box.minZ + 0.001D);
        int sx2 = net.minecraft.util.Mth.floor(box.maxX - 0.001D);
        int sz2 = net.minecraft.util.Mth.floor(box.maxZ - 0.001D);

        boolean supported = false;
        int brittleCount = 0;

        // first pass — categorize
        for (int x = sx1; x <= sx2 && !supported; x++) {
            for (int z = sz1; z <= sz2 && !supported; z++) {
                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
                net.minecraft.world.level.block.state.BlockState st = level.getBlockState(pos);
                if (st.isAir()) continue;
                boolean brittle = isBrittleSupport(st);
                boolean flimsy = isFlimsySupport(st, level, pos);
                if (!brittle && !flimsy) {
                    supported = true;
                    break;
                }
                if (brittle && !flimsy) brittleCount++;
            }
        }
        if (supported || brittleCount == 0) return;

        // second pass — break brittle blocks per probability table
        float bboxWidth = (float) (box.maxX - box.minX);
        int edgew = (int) Math.ceil(bboxWidth / 3.0F);

        for (int x = sx1; x <= sx2; x++) {
            for (int z = sz1; z <= sz2; z++) {
                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
                net.minecraft.world.level.block.state.BlockState st = level.getBlockState(pos);
                if (st.isAir() || !isBrittleSupport(st)) continue;

                boolean center = (x >= sx1 + edgew) && (x <= sx2 - edgew)
                              && (z >= sz1 + edgew) && (z <= sz2 - edgew);
                int p = center ? 10 : (10 * brittleCount);
                if (rand.nextInt(p) != 0) continue;

                level.destroyBlock(pos, true);
            }
        }
    }

    private static boolean isBrittleSupport(net.minecraft.world.level.block.state.BlockState st) {
        if (st.is(net.minecraft.tags.BlockTags.WOOL)) return true;
        net.minecraft.world.level.block.Block b = st.getBlock();
        if (b instanceof net.minecraft.world.level.block.StainedGlassBlock
                || b instanceof net.minecraft.world.level.block.TintedGlassBlock) {
            return true;
        }
        // Plain glass uses the vanilla Block class (no specialized GlassBlock
        // class in 26.x). Detect via sound type — it's the unifying signal
        // across plain / stained / tinted glass and any modded glass.
        return st.getSoundType() == net.minecraft.world.level.block.SoundType.GLASS;
    }

    private static boolean isFlimsySupport(net.minecraft.world.level.block.state.BlockState st,
                                            net.minecraft.world.level.Level level,
                                            net.minecraft.core.BlockPos pos) {
        if (st.is(net.minecraft.tags.BlockTags.LEAVES)) return true;
        if (st.is(net.minecraft.tags.BlockTags.WOOL_CARPETS)) return true;
        return !st.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP);
    }

    /**
     * 1.6.4 leaveHugeFootprints: when a huge entity steps, calls
     * Block.onEntityWalking on the 4 corner blocks under its hitbox at
     * foot level, alternating left/right corners by getStepSide()'s sign
     * each stride. This is what makes farmland trample under a giant's
     * footsteps, pressure plates trigger, sound blocks play, etc.
     *
     * Modern translation: Block.stepOn replaces onEntityWalking.
     * getStepSide() (the 1.6.4 ASM-injected sign field that flipped each
     * stride) is approximated by tickCount-mod-2 alternation — close
     * enough to preserve the "diagonal corners alternating" feel.
     *
     * Only fires when the entity actually moved meaningful horizontal
     * distance this tick, to avoid trampling under a stationary giant.
     */
    public static void leaveHugeFootprints(net.minecraft.world.entity.LivingEntity stepper) {
        net.minecraft.world.level.Level level = stepper.level();
        if (level.isClientSide()) return;
        net.minecraft.world.phys.Vec3 dm = stepper.getDeltaMovement();
        double horiz = dm.x * dm.x + dm.z * dm.z;
        if (horiz < 1.0E-4D) return;

        net.minecraft.world.phys.AABB box = stepper.getBoundingBox();
        int y = net.minecraft.util.Mth.floor(box.minY - 0.2D - 0.001D);
        int sx1 = net.minecraft.util.Mth.floor(box.minX + 0.001D);
        int sz1 = net.minecraft.util.Mth.floor(box.minZ + 0.001D);
        int sx2 = net.minecraft.util.Mth.floor(box.maxX - 0.001D);
        int sz2 = net.minecraft.util.Mth.floor(box.maxZ - 0.001D);

        // Alternate corner pair per stride — left/right.
        int[][] pairs = (stepper.tickCount & 1) == 0
                ? new int[][] { { sx1, sz1 }, { sx2, sz2 } }
                : new int[][] { { sx1, sz2 }, { sx2, sz1 } };

        for (int[] xz : pairs) {
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(xz[0], y, xz[1]);
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            state.getBlock().stepOn(level, pos, state, stepper);
        }
    }

    public static void stepOnSmallerEntities(net.minecraft.world.entity.LivingEntity stepper) {
        net.minecraft.world.level.Level level = stepper.level();
        if (level.isClientSide()) return;
        net.minecraft.world.phys.AABB stepperBox = stepper.getBoundingBox();
        net.minecraft.world.phys.AABB scan = stepperBox.inflate(0.2D, 0.0D, 0.2D);
        java.util.List<Entity> nearby = level.getEntities(stepper, scan);
        if (nearby == null || nearby.isEmpty()) return;

        float stepperMult = ((IResizeableEntity) stepper).getSizeMultiplier();
        float stepperRoot = ((IResizeableEntity) stepper).getSizeMultiplierRoot();
        double sBottom = stepperBox.minY;
        double sHeight = stepperBox.maxY - stepperBox.minY;

        for (Entity target : nearby) {
            if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) continue;
            if (target == stepper || target.getVehicle() == stepper) continue;
            if (isDragonEntity(target)) continue;

            IResizeableEntity tsized = (IResizeableEntity) target;
            if (tsized.getSizeMultiplier() >= stepperMult * 0.5F) continue;

            net.minecraft.world.phys.AABB tBox = target.getBoundingBox();
            if (tBox.maxY < sBottom) continue;
            if (tBox.minY > sBottom + sHeight * 0.0625F) continue;

            float dmg = (float) Math.ceil(2.0F * stepperRoot / tsized.getSizeMultiplierRoot());
            living.hurt(gulliver.init.GulliverDamageTypes.passive(level, stepper), dmg);
        }
    }
}
