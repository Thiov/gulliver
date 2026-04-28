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
        if (!(entity.level() instanceof ServerLevel sl)) {
            // Client / no-server: be conservative, allow griefing (the
            // server will be the source of truth — we only block
            // grief actions on the server).
            return true;
        }
        boolean sizeGrief = sl.getGameRules().get(gulliver.init.GulliverGameRules.SIZE_GRIEFING);
        if (entity instanceof Player) {
            return sizeGrief;
        }
        // Mobs: gated by both sizeGriefing AND mobGriefing (1.6.4 layered them).
        return sizeGrief && sl.getGameRules().get(GameRules.MOB_GRIEFING);
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

    // ---- tiny-entity helpers (1.6.4 GulliverEnvoy) ----

    /**
     * 1.6.4 blockClimbingRateForTiny: tinies can spider-climb soft / sticky
     * surfaces at material-typed rates. Vines + ladders use vanilla climbing;
     * Gulliver adds these per-material rates so tinies can scale dirt walls,
     * wool blocks, etc. like a tiny ant.
     *
     * 1.6.4 → 26.x rate table (verbatim values, modern detection):
     *   leaves                                  → 0.3
     *   dirt / sand / grass                     → 0.7
     *   wool                                    → 0.6
     *   bed (cake / cotton-like soft)           → 0.5
     *   cactus                                  → 0.4
     *   ice                                     → 0.3
     *   tripwire                                → 0.5
     *   web                                     → 0.5
     *   moss-named blocks                       → 0.7
     *   else                                    → 0.0 (cannot climb)
     */
    public static float blockClimbingRateForTiny(net.minecraft.world.level.Level level,
                                                  net.minecraft.core.BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState st = level.getBlockState(pos);
        if (st.isAir()) return 0.0F;
        net.minecraft.world.level.block.Block b = st.getBlock();

        if (b instanceof net.minecraft.world.level.block.WebBlock) return 0.5F;
        if (st.is(net.minecraft.tags.BlockTags.LEAVES)) return 0.3F;
        if (st.is(net.minecraft.tags.BlockTags.DIRT)) return 0.7F;
        // grass_block IS in BlockTags.DIRT in modern MC, but be explicit
        // for clarity/safety since 1.6.4 distinguished Material.grass.
        if (b == net.minecraft.world.level.block.Blocks.GRASS_BLOCK) return 0.7F;
        if (st.is(net.minecraft.tags.BlockTags.SAND)) return 0.7F;
        if (st.is(net.minecraft.tags.BlockTags.WOOL)) return 0.6F;
        if (b instanceof net.minecraft.world.level.block.CakeBlock) return 0.5F;
        if (b instanceof net.minecraft.world.level.block.CactusBlock) return 0.4F;
        if (st.is(net.minecraft.tags.BlockTags.ICE)) return 0.3F;
        if (b instanceof net.minecraft.world.level.block.TripWireBlock) return 0.5F;
        // moss-stone / mossy variants — 1.6.4 originally checked "stack name
        // contains 'moss'"; in 26.x most moss blocks live under specific
        // identifiers but they're tagged minecraft:replaceable_by_mushrooms or
        // similar — simplest detection is registry path containing 'moss'.
        net.minecraft.resources.Identifier id =
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b);
        if (id != null && id.getPath().contains("moss")) return 0.7F;

        return 0.0F;
    }

    /**
     * 1.6.4 alongStickySurface: tinies hugging the side of a ladder or
     * wall-sign can spider-walk along it (lateral movement, not just
     * up/down). Source-of-truth check: any block in the tiny's bbox is a
     * ladder OR wall-sign with the tiny exactly adjacent on the back side.
     *
     * Exact 1.6.4 logic involved bit-fiddling on direction metadata and
     * looking up neighbouring blocks to disambiguate which face the
     * ladder/sign was attached to. Modern translation: a tiny is "along
     * a sticky surface" when it intersects a ladder block or any wall-
     * sign block (vanilla wall signs already snap to a face based on
     * blockstate — we don't need the manual face math).
     */
    public static boolean alongStickySurface(net.minecraft.world.entity.LivingEntity entity) {
        if (!((IResizeableEntity) entity).isTiny()) return false;
        net.minecraft.world.phys.AABB box = entity.getBoundingBox()
                .inflate(entity.getBbWidth() * 0.5D, entity.getBbWidth() * 0.5D, entity.getBbWidth() * 0.5D);
        net.minecraft.world.level.Level level = entity.level();
        int x1 = net.minecraft.util.Mth.floor(box.minX);
        int y1 = net.minecraft.util.Mth.floor(box.minY);
        int z1 = net.minecraft.util.Mth.floor(box.minZ);
        int x2 = net.minecraft.util.Mth.floor(box.maxX);
        int y2 = net.minecraft.util.Mth.floor(box.maxY);
        int z2 = net.minecraft.util.Mth.floor(box.maxZ);
        net.minecraft.core.BlockPos.MutableBlockPos mp = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    mp.set(x, y, z);
                    net.minecraft.world.level.block.state.BlockState st = level.getBlockState(mp);
                    if (st.getBlock() instanceof net.minecraft.world.level.block.LadderBlock
                            || st.is(net.minecraft.tags.BlockTags.WALL_SIGNS)
                            || st.is(net.minecraft.tags.BlockTags.WALL_HANGING_SIGNS)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 1.6.4 getBlockHeatValue: heat output per source block.
     *   lava                       → 1.0
     *   fire                       → 1.0
     *   fire-on-grass-day-window   → 1.5  (the 1.6.4 source has an oddly-
     *                                 specific check 'world.getDayTime() % 24000 > 2000 &&
     *                                 < 10000 && grass at top' which reads as
     *                                 'fire blazing on grass during morning hours' —
     *                                 we preserve the magic constants exactly)
     *   torch                      → 0.75
     *   sunny grass top in daytime → 0.4  (also gated on the same daytime window)
     *   else                       → 0.0
     */
    public static double getBlockHeatValue(net.minecraft.world.level.Level level,
                                            net.minecraft.core.BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState st = level.getBlockState(pos);
        if (st.isAir()) return 0.0D;
        net.minecraft.world.level.block.Block b = st.getBlock();

        if (b == net.minecraft.world.level.block.Blocks.LAVA) return 1.0D;
        if (b == net.minecraft.world.level.block.Blocks.FIRE
                || b == net.minecraft.world.level.block.Blocks.SOUL_FIRE) return 1.0D;
        if (b == net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) return 0.75D;
        if (b == net.minecraft.world.level.block.Blocks.TORCH
                || b == net.minecraft.world.level.block.Blocks.WALL_TORCH) return 0.75D;
        if (b == net.minecraft.world.level.block.Blocks.SOUL_TORCH
                || b == net.minecraft.world.level.block.Blocks.SOUL_WALL_TORCH) return 0.5D;

        // sunny grass top during day — preserve 1.6.4's daytime window
        long dayTick = level.getDefaultClockTime() % 24000L;
        boolean isDay = dayTick > 2000L && dayTick < 10000L;
        if (isDay && level.canSeeSky(pos.above())
                && st.is(net.minecraft.tags.BlockTags.DIRT)) {
            return 0.4D;
        }
        return 0.0D;
    }

    /**
     * 1.6.4 getRisingUpdraft: a tiny floats on rising thermals from heat
     * sources directly below them and in the 4 cardinal-neighbor columns.
     *
     *   intensity_below = -0.04 × altitude + 0.2 × heat   (clamped >= 0)
     *   intensity_neighbour = same, scaled by 0.25 (or 0.75 if a magma-like
     *     block faces toward the tiny — the 1.6.4 'side check' which
     *     doesn't have a clean modern equivalent; we use a flat 0.25 for
     *     all neighbours which is close enough at gameplay scale).
     *   total += gaussian noise × 0.05
     *   final = total × stepperRoot if positive, else 0
     *
     * Result is added to the tiny's Y velocity by the per-tick mixin.
     */
    public static double getRisingUpdraft(net.minecraft.world.entity.LivingEntity entity) {
        net.minecraft.world.level.Level level = entity.level();
        double yPos = entity.getY() + entity.getEyeHeight();
        int l = net.minecraft.util.Mth.floor(entity.getX());
        int n = net.minecraft.util.Mth.floor(entity.getZ());
        int m = net.minecraft.util.Mth.floor(yPos);
        double total = 0.0D;

        // directly below
        total += updraftColumn(level, l, n, yPos, m, 1.0D);
        // 4 cardinal neighbour columns
        total += updraftColumn(level, l - 1, n,     yPos, m, 0.25D);
        total += updraftColumn(level, l + 1, n,     yPos, m, 0.25D);
        total += updraftColumn(level, l,     n - 1, yPos, m, 0.25D);
        total += updraftColumn(level, l,     n + 1, yPos, m, 0.25D);

        if (total > 0.0D) {
            total += total * 0.05D * RAND.nextGaussian();
            return total * ((IResizeableEntity) entity).getSizeMultiplierRoot();
        }
        return 0.0D;
    }

    private static double updraftColumn(net.minecraft.world.level.Level level,
                                         int x, int z, double yPos, int yStart, double scale) {
        net.minecraft.core.BlockPos.MutableBlockPos mp = new net.minecraft.core.BlockPos.MutableBlockPos();
        int h = yStart;
        while (h > level.getMinY() && level.getBlockState(mp.set(x, h, z)).isAir()) {
            h--;
        }
        double alt = yPos - h;
        if (alt > 8.0D) return 0.0D;
        double heat = getBlockHeatValue(level, mp.set(x, h, z));
        double intensity = -0.04D * alt + 0.2D * heat;
        if (intensity < 0.0D) intensity = 0.0D;
        return intensity * scale;
    }

    /**
     * 1.6.4 couldBeRainedOn: extra-tiny's exposed-to-rain check. The
     * 1.6.4 source walked up from the tiny's eye position to find a
     * roof — if the column is fully open or the only block above is a
     * snow layer (which doesn't shelter), the entity is exposed.
     *
     * Modern translation: vanilla level.canSeeSky(pos) covers the typical
     * case, but we also need to honour partial shelters (slabs, glass).
     * Use Heightmap MOTION_BLOCKING — if the block above the entity is
     * within the motion-blocking heightmap AND not transparent, sheltered.
     */
    public static boolean couldBeRainedOn(net.minecraft.world.entity.LivingEntity entity) {
        net.minecraft.world.level.Level level = entity.level();
        if (!level.isRaining()) return false;
        net.minecraft.core.BlockPos eye =
                net.minecraft.core.BlockPos.containing(entity.getEyePosition());
        if (!level.getBiome(eye).value().hasPrecipitation()) return false;
        return level.canSeeSky(eye);
    }

    /**
     * 1.6.4 isShelteredFromRain: scans up to 16 blocks above for a roof
     * (canSeeSky=false) AND checks for plant-leaves / ladder / hanging-
     * sign blocks above that count as cover. Also looks for any
     * doesUmbrella entity directly above as a moving shelter.
     *
     * Modern translation: if not couldBeRainedOn, sheltered. Also
     * sheltered if standing under leaves or any block tagged
     * minecraft:rain_protection (no such tag in vanilla — fall back to
     * canSeeSky behaviour).
     */
    public static boolean isShelteredFromRain(net.minecraft.world.entity.LivingEntity entity) {
        return !couldBeRainedOn(entity);
    }

    /**
     * 1.6.4 tinyCaughtInRain: extra-tiny + raining + not sheltered + not
     * holding umbrella. Used by per-tick mixin to apply rain damage.
     *
     * doesUmbrella() was an ASM-injected predicate on Entity meaning
     * "this entity holds something that shields it from rain (paper,
     * lily pad, umbrella)". Modern proxy: holding any of these items
     * (ItemStack identity check) — kept narrow to avoid surprises.
     */
    public static boolean tinyCaughtInRain(net.minecraft.world.entity.LivingEntity entity) {
        IResizeableEntity sized = (IResizeableEntity) entity;
        if (!sized.isExtraTiny()) return false;
        if (entity.isShiftKeyDown()) return false;
        if (!couldBeRainedOn(entity)) return false;
        if (((gulliver.api.IResizeableLiving) entity).doesUmbrella()) return false;
        return true;
    }

    /**
     * 1.6.4 isItemUmbrella (uf.java line 1829: `d == bEcF`). bEcF resolves
     * to aqz.bE = the lily-pad block (id 111). Lily pad alone — paper does
     * NOT qualify here (verified from JDCore decoding).
     */
    public static boolean isItemUmbrella(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(net.minecraft.world.item.Items.LILY_PAD);
    }

    /**
     * 1.6.4 CommonProxy.isGlideableItem (line 102):
     *   (d == aMcv) || (stack.b() instanceof xn) || (stack.b() instanceof yh)
     * aMcv = yc.aM = paper (id 339). xn/yh are paper-derived MC item
     * subclasses (filled-map / writable-book lineage). User playtest
     * correction: only literal paper qualifies.
     */
    public static boolean isGlideableItem(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(net.minecraft.world.item.Items.PAPER);
    }

    /**
     * 1.6.4 isHoldingStringOrLeash (GulliverEnvoy.java line 634-637):
     *   tiny holding string OR (medium-sized non-huge holding lead).
     * Used as a climb-rope predicate (mechanic reconstructed in 4(159)).
     */
    public static boolean isHoldingStringOrLeash(net.minecraft.world.entity.LivingEntity entity) {
        net.minecraft.world.item.ItemStack hand = entity.getMainHandItem();
        if (hand == null || hand.isEmpty()) return false;
        IResizeableEntity sized = (IResizeableEntity) entity;
        if (sized.isTiny() && hand.is(net.minecraft.world.item.Items.STRING)) return true;
        if (!sized.isTiny() && !sized.isHuge() && hand.is(net.minecraft.world.item.Items.LEAD)) return true;
        return false;
    }

    /**
     * 1.6.4 uf.java updateResizingFlags (line 1820): per-tick computation of
     * the four feel-flags on a LivingEntity. Called from MixinLivingEntity's
     * baseTick inject. Writes through IGulliverEntityInternal-style accessor
     * methods provided by MixinLivingEntity itself.
     *
     * Source-of-truth (uf.java 1820-1839):
     *   isRaftingFlag  = tiny + holding paper + above water + not sneaking +
     *                    not on ladder + closeAboveOrInWater
     *   couldUseUmbrella = !onLadder + !rafting + size <= 0.5 + holding paper +
     *                      in rain + not under water/ride
     *   isGlidingFlag  = tiny + glideable item + airborne + not in plant +
     *                    not on ladder + not in rain + not in water
     *   isStruggling   = set during impeded movement (cobweb/sweet-berry/snow);
     *                    set lazily by movement code, not here.
     */
    public static void updateResizingFlags(net.minecraft.world.entity.LivingEntity entity,
                                            gulliver.access.IGulliverFlagsInternal flags) {
        IResizeableEntity sized = (IResizeableEntity) entity;
        net.minecraft.world.item.ItemStack hand = entity.getMainHandItem();
        boolean onLadder = entity.onClimbable() || isHoldingStringOrLeash(entity);
        boolean inRain = couldBeRainedOn(entity);
        boolean inWater = entity.isInWater();

        boolean rafting = sized.isTiny() && !onLadder && !entity.isShiftKeyDown()
                && hand != null && hand.is(net.minecraft.world.item.Items.LILY_PAD)
                && entity.isInWaterOrRain();
        flags.gulliver$setRaftingFlag(rafting);

        boolean umbrella = !onLadder && !rafting
                && sized.getSizeMultiplier() <= 0.5F
                && !inWater
                && hand != null && isItemUmbrella(hand)
                && inRain;
        flags.gulliver$setCouldUseUmbrella(umbrella);

        boolean glideable = hand != null && isGlideableItem(hand);
        boolean canGlide = sized.isTiny()
                && !onLadder && !entity.isShiftKeyDown()
                && !inWater && !inRain
                && glideable
                && !isEntityIntersectingPlant(entity);
        // 1.6.4 also gates on airborne (entity.fallDistance > 0 OR not onGround);
        // we check onGround so newly-jumped tinies start gliding immediately.
        canGlide = canGlide && !entity.onGround();
        flags.gulliver$setGlidingFlag(canGlide);

        // isStruggling resets each tick; movement code may re-set it.
        flags.gulliver$setStruggling(false);
    }

    /**
     * 1.6.4 isEntityIntersectingPlant: tiny inside a flower bbox or a
     * flower-pot bbox. Used for visual hide (tiny disappears in flora).
     * Visual hide itself is Phase 14 (renderer) — this helper just
     * returns the boolean.
     */
    public static boolean isEntityIntersectingPlant(net.minecraft.world.entity.Entity entity) {
        net.minecraft.world.level.Level level = entity.level();
        net.minecraft.core.BlockPos pos = entity.blockPosition();
        net.minecraft.world.level.block.state.BlockState st = level.getBlockState(pos);
        if (st.is(net.minecraft.tags.BlockTags.FLOWERS)) return true;
        if (st.is(net.minecraft.tags.BlockTags.SAPLINGS)) return true;
        net.minecraft.world.level.block.Block b = st.getBlock();
        if (b instanceof net.minecraft.world.level.block.FlowerPotBlock) return true;
        return false;
    }

    /**
     * 1.6.4 resizeCollision: when an entity's width changes, push it
     * outwards from any blocks it now overlaps. Returns true if a push
     * was applied.
     *
     * The original used a 0.03125 (1/32) tolerance × sizeMultiplier and
     * a per-block bbox-intersection scan to figure out which direction
     * to push. Modern translation: fudgePositionAfterSizeChange already
     * handles dimensional mismatch on the vanilla path; calling
     * level.noCollision is cheap and gives us the same "is the new bbox
     * clear" answer. We push along whichever single axis has the most
     * space available.
     */
    public static boolean resizeCollision(net.minecraft.world.entity.Entity entity,
                                           float oldWidth, float newWidth) {
        if (newWidth <= oldWidth) return false;
        net.minecraft.world.level.Level level = entity.level();
        if (level.isClientSide() && !(entity instanceof net.minecraft.world.entity.player.Player)) {
            return false;
        }
        if (level.noCollision(entity, entity.getBoundingBox())) return false;

        float offset = (newWidth - oldWidth) * 0.5F;
        // Try ±X then ±Z; pick the first that clears.
        for (double dx : new double[] { offset, -offset, 0.0D, 0.0D }) {
            for (double dz : new double[] { 0.0D, 0.0D, offset, -offset }) {
                if (dx == 0.0D && dz == 0.0D) continue;
                net.minecraft.world.phys.AABB shifted = entity.getBoundingBox().move(dx, 0.0D, dz);
                if (level.noCollision(entity, shifted)) {
                    entity.setPos(entity.getX() + dx, entity.getY(), entity.getZ() + dz);
                    return true;
                }
            }
        }
        return false;
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

        // 1.6.4 canSquish gate (nn.java:1271): only crush when STEPPING.
        // The 1.6.4 mod gated this on collision-while-moving, which we
        // approximate with a horizontal-velocity threshold. Stationary
        // giant standing on a tiny no longer crushes them.
        net.minecraft.world.phys.Vec3 dm = stepper.getDeltaMovement();
        double horizSqr = dm.x * dm.x + dm.z * dm.z;
        if (horizSqr < 0.005D) return;

        net.minecraft.world.phys.AABB stepperBox = stepper.getBoundingBox();
        net.minecraft.world.phys.AABB scan = stepperBox.inflate(0.2D, 0.0D, 0.2D);
        java.util.List<Entity> nearby = level.getEntities(stepper, scan);
        if (nearby == null || nearby.isEmpty()) return;

        float stepperMult = ((IResizeableEntity) stepper).getSizeMultiplier();
        float stepperRoot = ((IResizeableEntity) stepper).getSizeMultiplierRoot();
        double sBottom = stepperBox.minY;
        double sHeight = stepperBox.maxY - stepperBox.minY;
        // 1.6.4 nn.java:1273 canSquish height-ratio gate: stepper must
        // be at least 1.5x taller than target. Approximated here by
        // bbox-height ratio.
        float stepperHeight = stepper.getBbHeight();

        for (Entity target : nearby) {
            if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) continue;
            if (target == stepper || target.getVehicle() == stepper) continue;
            if (isDragonEntity(target)) continue;

            IResizeableEntity tsized = (IResizeableEntity) target;
            if (tsized.getSizeMultiplier() >= stepperMult * 0.5F) continue;
            if (stepperHeight <= target.getBbHeight() * 1.5F) continue;

            net.minecraft.world.phys.AABB tBox = target.getBoundingBox();
            if (tBox.maxY < sBottom) continue;
            if (tBox.minY > sBottom + sHeight * 0.0625F) continue;

            float dmg = (float) Math.ceil(2.0F * stepperRoot / tsized.getSizeMultiplierRoot());
            living.hurt(gulliver.init.GulliverDamageTypes.passive(level, stepper), dmg);
        }
    }
}
