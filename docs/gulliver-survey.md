# Gulliver Forged 0.14.3-MC1.6.4 — Subsystem Survey

This is a complete inventory of the original 1.6.4 mod's behavior, derived from
the JDCore-decompiled universal jar at
`reference/gulliver_jdcore/.../gulliver/`. Every feature documented below comes
from that source. Other "Gulliver-like" reimplementations (1.12, 1.16, etc.)
are explicitly out of scope.

This doc is the contract for the port. When implementing each phase, refer
back here to confirm "what did the original do?" and translate that — and
nothing else — to Fabric 26.1.2.

## 1. Data model

### `IResizeableEntity` — base contract on every entity
- `getSizeMultiplier()` → final scale = base × potion × items
- `getSizeMultiplierRoot()` → sqrt of multiplier (used for damage/sound scaling)
- `halveSize()`, `doubleSize()` → one-shot multiplier change
- `isTiny()` → multiplier < 1.0
- `isExtraTiny()` → multiplier < some threshold (read further; ~0.25)
- `isHuge()` → multiplier > 1.0
- `getStepHeight()` → scaled step height

### `IResizeableLiving extends IResizeableEntity`
- `getSizePotionMultiplier()` → multiplicative factor from active potion
- `getSizeItemMultiplier()` → multiplicative factor from worn/held items (e.g. mushrooms)
- `setBaseSize(float)`, `adjustBaseSize(float)` → base-size mutators

### `IResizeablePlayer extends IResizeableLiving`
- Marker only; no new methods.

### Bounds
- Hard min 0.125 (1/8), hard max 8.0 — bedrock, not configurable past these.
- Configurable per-class (player, animal, monster, npc) and per-entity-name.

### Categories (heuristic)
- NPC: instanceof `EntityVillager` (`ua` in obf).
- Monster: instanceof `IMob` (`th`).
- Animal: not NPC, not monster, not player.
- Arthropod: monster with `creatureType == ARTHROPOD` (spider, silverfish, cave-spider). Used for special handling.

## 2. Player class extensions

### `EntityResizeablePlayerMP` (server-side)
Wraps `EntityPlayerMP`. Overrides:
- `getEyeHeight()` = `1.62F * sizeMultiplier`
- `setPositionAndUpdate(x,y,z)` — drops shoulder-held entity first, sends `Packet172AttachEntitySpecial` (type=0 = drop)
- `sleepInBedAt(x,y,z)` → calls `sleepInSizedBedAt(world,x,y,z)` — bed must accommodate the player's current size; uses Gulliver custom logic
- After successful sleep: sends `Packet17Sleep` for client visual + repositions

### `EntityResizeableClientPlayerMP` (client-side)
Wraps `EntityClientPlayerMP`. Overrides:
- `onUpdate()` — saves `ySizeOld = X` each tick before super
- `pushOutOfBlocks(x,y,z)` — custom push-out so that tinies who fit in a 1-block tunnel are NOT pushed back out. Inspects block bounding box and only pushes if the bbox actually intersects the tiny player.
- `playSound(name, vol, pitch)` — volume scaled by `sizeMultiplierRoot` (small player makes quieter footsteps; huge player makes louder ones)

## 3. Subsystem: `GulliverEnvoy` (the runtime helper, 1404 lines)

The cross-cutting helper. Public statics drive nearly every game-mechanic
hook. **This is the bulk of the port.**

### Configuration plumbing
- `setBasePlayerSize(String)`, `getBasePlayerSize()` etc.
- Karma mode toggle (default off): on death, reset base size to spawn default.
- Dye-resizing toggle (default on): cyan/purple dye drinks.
- Min/max entity size + min/max base size (defaults 0.125/8.0).

### Achievements
- `drinkMe` (id 42780, icon = potion, ref `ko.bv` in obf — needs MCP decode) — Alice in Wonderland reference.
- `eatMe` (id 42781, icon = melon, ref `yc.bb`).
- Both call `.c()` for "make independent" achievement registration.
- *26.x port:* convert to JSON advancements.

### Size resolution
- `getNewBasePlayerSize()` / `getNewBaseEntitySize(entity)` — reads config:
  per-entity name override → per-entity ID override → per-class default
  (`base-animal-size`, `base-monster-size`, `base-npc-size`).
- `getMaxSizeForEntity(entity)` / `getMinSizeForEntity(entity)` — same lookup
  pattern across `size-limit` config category.
- `getSizeFromRangeString(str, allowHeights)` — parses sizes:
  - Comma-separated set: `"0.5,1.0,2.0"` → random pick
  - Range: `"0.5-2.0"` → uniform-quantized random in 9 steps
  - Single: `"1.5"` → literal
- `parsePlayerHeight(str)` — when `allowHeights=true`, also parses
  `"5'9""`, `"175cm"`, `"1.8m"`, `"6ft"`, `"70in"`. Divides result by 1.8m
  baseline → multiplier.
- `getPlayerHeightStringFromSizeMult(sm)` — formats back: `"1.80m (5ft11.02in)"`.
- `isInvalidSize(f)` → ≤0, infinite, or NaN.

### Game-mechanic hooks (each invoked from the patched MC code)
- `canOpenSingleBlock(entity)` / `canOpenDoubleBlock(entity)` →
  uses `smallBlockOpeningStrength`:
  - Start at 3.
  - If multiplier ≥ 0.6, return 3 (full strength).
  - Otherwise: while < 0.6, halve doubles → strength--; (so 0.5x = 2, 0.25x = 1, 0.125x = 0)
  - Holding pointy item: +1
  - Slowness effect: −(amplifier+1)
  - Haste effect: +(amplifier+1)
  - `canOpenSingleBlock` = strength ≥ 1, `canOpenDoubleBlock` = strength ≥ 2.
  - Used by InteractEventHandler to deny door/lever/button/gate/hatch/cabinet/safe interactions, and by chest single/double opening.
- `canPressPlateLikeButton(entity)` → entity is huge; huge entities trigger
  buttons by stepping on them.
- `canSizeGrief(entity)` → checks `sizeGriefing` gamerule (defaults true if
  unset). For players, also requires `bG.d == false` (some other flag,
  likely `disableDamage`/peaceful?). For mobs, also requires `mobGriefing`
  gamerule.
- `isHoldingStringOrLeash(entity)` → tiny holding string OR (medium/huge holding
  leash). Used somewhere — likely climbing rope mechanic.
- `isItemPointy(stack)` → swords, pickaxes, axes, shears, snowball,
  scissors, plus a list of block-IDs that count as pointy. Used by
  smallBlockOpeningStrength bonus.

### Huge entity ground effects
- `checkSupportingBlocksForHuge(entity, rand)` — examines blocks under huge
  entity's bbox at floor level. If only "brittle"/"flimsy" blocks are
  supporting it (glass, leaves), they break randomly: 1/10 chance for
  blocks under center, 1/(10×count) for edge blocks.
- `leaveHugeFootprints(entity)` — when huge entity steps, calls
  `block.onEntityWalking(...)` on the four corner blocks under its hitbox.
  Causes farmland to be trampled etc. Uses `entity.getStepSide()` (custom
  field) to alternate left/right.
- `stepOnSmallerEntities(entity)` — collects entities in expanded bbox
  (+0.2 × +0.2). For each that this entity can squish (per
  `entity.canSquish(target)`) AND collides with the bottom 1/16 of bbox:
  damages target with `EntityDamageSourcePassive("step")` for
  `2 × stepperRoot / targetRoot`.
- `breakBlocksViaGrowth(entity, oldBB, diffH, diffW, rand)` — when entity
  grows, breaks soft blocks now in its bbox (hardness < newSizeMult, OR
  cobweb-material if huge).

### Tiny entity helpers
- `resizeCollision(entity, oldWidth, newWidth)` — when a tiny grows or
  shrinks, push out of blocks if needed (uses 0.03125 tolerance × sizeMult).
- `blockClimbingRateForTiny(world, x, y, z)` — material-typed climb rate:
  - leaves (`material == LEAVES`) → 0.3 (or moss-stone same)
  - dirt/sand/grass-soft → 0.7
  - cloth/cotton (wool) → 0.6
  - bed/cake/cloth/etc. → 0.5
  - cactus → 0.4
  - ice → 0.3
  - tripwire → 0.5
  - else 0.0 (can't climb)
  - **Vines (the existing climb block) handled separately by MC.**
- `isEntityIntersectingPlant(entity)` — true if tiny is fully inside a
  flower (`PLANTS` material) or flower-pot bbox. Hides them visually.
- `tinyCaughtInRain(entity)` → extra-tiny + raining + no umbrella + not
  sheltered → "caught in rain".
- `couldBeRainedOn(entity)` / `isShelteredFromRain(entity)` — searches up
  to ~16 blocks above for a roof or umbrella-bearing entity overhead.
- `alongStickySurface(entity)` — tiny against ladder/wall-sign sides allows
  spider-walk along the side, not just up/down.
- `getRisingUpdraft(entity)` → tinies get lifted by heat:
  - Lava/fire: 1.0
  - Fire-on-grass: 1.5
  - Torch: 0.75
  - Sunny grass-block top in daytime: 0.4
  - Decays −0.04 per Y from heat source up to 8 blocks.
  - Plus 4 cardinal-neighbour columns each (0.25× contribution unless
    magma/torch faces toward player → 0.75×).
  - Final result × `entity.sizeMultiplierRoot` and slight Gaussian noise.
- `getBlockHeatValue(world,x,y,z)` — the source-of-truth table above.

### Helpers consumed by command code
- `pruneSmallerEntities(minSize, list)` / `pruneLargerEntities(maxSize, list)`
  — strip targets outside size bracket from a list.
- `isNPC/isMonster/isAnimal/isArthropod` (above).
- `isDragonEntity(entity)` → ender-dragon or its parts (size-immune).

## 4. Subsystem: `GulliverConfigHelper` (54 lines)

Forge `Configuration` file at `config/Gulliver.cfg`. Categories and keys:

### `[potion]`
- `potion-tiny-id` (default 26) — legacy 1.6.4 numeric potion-effect ID
- `potion-huge-id` (default 27)

### `[general]`
- `enable-dye-resizing` (true) — Cyan & Purple dye as drink/eat triggers
- `enable-karma-mode` (false) — reset base size on death
- `min-entity-size` (0.125)
- `max-entity-size` (8.0)
- `min-base-size` (0.125)
- `max-base-size` (8.0)

### `[spawn-size]`
- `base-player-size` (string, "1.0") — accepts ranges and height notation
- `base-animal-size` (1.0)
- `base-monster-size` (1.0)
- `base-npc-size` (1.0)
- `base-size-<entityname>` — per-entity-name override (eg. `base-size-spider`)
- `base-size-entity<id>` — per-entity-id override (eg. `base-size-entity90`)

### `[size-limit]`
- `min-/max-player-size`, `min-/max-animal-size`, `min-/max-monster-size`,
  `min-/max-npc-size` (all 0.0 = ignored)
- `min-size-<entityname>`, `max-size-<entityname>` (per-entity overrides)

**Port style:** plain Gson POJOs at `config/gulliver.json`, mirroring the
witherstorm port pattern (see auto-memory `feedback_witherstorm_porting_strategy.md`).
Persist exactly the same key shapes (kebab-case strings) for compat with
existing config files.

## 5. Subsystem: `GulliverBlockReplacer` (152 lines, reflective)

At pre-init, replaces 14 vanilla `Block.X` static fields with Gulliver
subclasses, using `Field.set(null, replacement)` after clearing the
`final` modifier. The replacement keeps the block ID and texture name.

Replacements:

| Vanilla block (obf field)   | Replacement class             | Why |
| --- | --- | --- |
| `Block.anvil` (cm)          | `BlockAnvilGulliver`          | Custom multi-AABB so tinies can walk under |
| `Block.cactus` (ba)         | `BlockCactusGulliver`         | Don't damage tinies wading at the base |
| `Block.flowerPot` (ch)      | `BlockFlowerPotGulliver`      | Tiny can hide inside |
| `Block.lever` (aO)          | `BlockLeverGulliver`          | Block tinies from flipping |
| `Block.flowerThorny` (aj)   | `BlockFlowerThorny`           | Damage tinies wading through |
| `Block.portal` (bj)         | `BlockPortalGulliver`         | Custom dim transit for huge |
| `Block.snow` (aX)           | `BlockSnowGulliver`           | Layer behavior for tiny/huge |
| `Block.soulSand` (bh)       | `BlockSoulSandGulliver`       | Sink rate scales with size |
| `Block.farmland` (aF)       | `BlockFarmlandGulliver`       | Huge tramples; tiny doesn't |
| `Block.tnt` (ar)            | `BlockTNTGulliver`            | Huge can prime by right-click |
| `Block.tripWire` (bZ)       | `BlockTripWireGulliver`       | Tinies don't trigger |
| `Block.tripWireSource` (bY) | `BlockTripWireSourceGulliver` | (companion) |
| `Block.web` (ab)            | `BlockWebGulliver`            | Huge breaks web walking through |
| `Block.carpet` (cC)         | `BlockCarpetGulliver`         | Layer behavior for tiny/huge |

**Port strategy on Fabric:** use Mixin to inject into the relevant block
behaviors (`onEntityCollision`, `onEntityWalk`, `getCollisionShape`, etc.)
rather than replacing block instances. Registry replacement is fragile in
modern MC; per-method Mixin is cleaner.

### Per-block snapshot

- **BlockSnowGulliver**: `addCollisionBoxesToList` raises height by 1 layer
  for extra-tiny (so they stand on snow flush) and lowers by `1.25 × size` for huge
  (sink in). `onEntityWalking` reduces snow-layer count when huge stomps
  through (1/50 ticks).
- **BlockTNTGulliver**: `onBlockActivated` — if huge bare-handed, spawn
  primed TNT entity and remove block (auto-prime).
- **BlockWebGulliver**: `onEntityCollidedWithBlock` — if huge, breaks web
  (1/50 ticks) AND respects `canSizeGrief`.
- **BlockAnvilGulliver**: separate method-private collision boxes for the
  base (0.125–0.875 × 0.0–0.25), middle stem (0.375–0.625 × 0.3125–0.625),
  and top (0.1875–0.8125 × 0.625–1.0) — so tinies walk under the head.
- (read full implementations as each block is ported in its substage.)

## 6. Subsystem: `PotionResizing` (78 lines)

`extends Potion`. Two singletons:
- `tiny` — id 26, color 0x7CFEC0
- `huge` — id 27, color 0x9362DB

`performEffect(entity, amplifier)` sets a custom field
`entity.sizePotionMultiplier`:
- Huge, amp clamped 0..8: `4.0 × 2^amp` (4×, 8×, 16×, ..., 1024×)
- Tiny, amp clamped 0..8: `0.25 × 2^(-amp)` (0.25×, 0.125×, ..., 0.001×)
- `finishEffect`: reset to 1.0.

Patched into `PotionHelper.potionRequirements`/`potionAmplifiers` maps with
custom brewing requirement strings (`"0 & 1 & 2 & !3 & 2+6"` for huge,
inverse for tiny). Sets up brewing stand recipes:
- Mushroom red potion modifier → tiny (`"+0+1+2-3&4-4+13"`)
- Mushroom brown potion modifier → huge (`"+0+1+2+3&4-4+13"`)

**Port on 26.x:** `StatusEffect` subclass + brewing recipe registration via
`BrewingRecipeRegistry` (Fabric API).

## 7. Subsystem: Networking (2 packets)

Both extend `Packet` (the legacy 1.6.4 base) and use channel IDs 171/172
(reserved by `NetworkRegistry` in the original mod's preInit — unseen in
this source, likely added via `@NetworkMod` annotation runtime).

### `Packet171EntitySize` — server→client size sync
- Fields: `int entityId`, `float sizeMult`
- Wire: 4 + 4 = 8 bytes payload (header reports `a()` returns 6 — possibly excludes int header? legacy quirk)
- Sent when an entity's size changes; client applies on receive via
  `netHandler.handleEntitySize(this)` (added via Forge ASM patch to
  `NetClientHandler`).

### `Packet172AttachEntitySpecial` — server→client special-attach (shoulder)
- Fields: `int entityId`, `int vehicleEntityId`, `byte attachmentType`
- `attachmentType == 0` → drop / detach
- `attachmentType == 1` → attach (likely)
- Used by `EntityResizeablePlayerMP.setPositionAndUpdate` (drop-on-tp) and
  by `CommandShoulderEntity` (pickup/putdown).

**Port on 26.x:** `CustomPacketPayload` records:
```java
public record EntitySizePayload(int entityId, float sizeMult) implements CustomPacketPayload { ... }
public record AttachSpecialPayload(int entityId, int vehicleEntityId, byte type) implements CustomPacketPayload { ... }
```
Plus `StreamCodec.composite(...)` for each. Registered on
`PayloadTypeRegistry.playS2C().register(...)`.

## 8. Subsystem: Commands (14)

All extend `CommandBase`. Permission level 2 (op) for size-change ones, 0
for `/shoulderentity`, 0 for some show ones.

| Command | Args | Effect |
| --- | --- | --- |
| `/basesize <size> [player]` | size string + optional target | Sets target's base size. Accepts numeric, range, height notation. |
| `/basesizeadjust <factor> [player]` | mult factor + target | Multiplies current base size. |
| `/halfsize [player]` | optional target | One-shot ×0.5 base size. |
| `/doublesize [player]` | optional target | One-shot ×2 base size. |
| `/showsize [player]` | optional target | Print target size info. |
| `/showmysize` | none | Print own size info (perm 0). |
| `/entitybasesize <size> [selector]` | size + entity match | Same as basesize but on nearest matching entity. |
| `/entitybasesizeadjust <factor> [selector]` | | |
| `/entityhalfsize [selector]` | | |
| `/entitydoublesize [selector]` | | |
| `/entityshowsize [selector]` | | |
| `/instantkarma [player]` | optional target | If karma mode ON: reset base to default. Else error. |
| `/shoulderentity` | none | Pick up rider as shoulder pet, or put it down. Sends `dj` packet (Animation entity-action 1) + `Packet172AttachEntitySpecial`. |
| `/serverreloadgulliver` | none | Reload config. |

**Port on 26.x:** `CommandManager.register` with `Brigadier` builders. Argument types:
- size string → `StringArgumentType.string()` then `GulliverEnvoy.getSizeFromRangeString` parse.
- entity selector → `EntityArgumentType.entity()`.
- player target → `EntityArgumentType.player()` (optional via separate overload).

## 9. Subsystem: `InteractEventHandler` (232 lines)

Subscribes:
- `PlayerInteractEvent.RIGHT_CLICK_BLOCK` → if size != 1.0:
  - For huge clicking `BlockLittleChunk` (LittleBlocks compat) without pointy item or sneak → cancel.
  - For tinies failing `canOpenSingleBlock` → cancel for door/lever/button/gate/hatch/cabinet/safe (matched by class-name substring).
- `PlayerOpenContainerEvent` → if size != 1.0 OR LittleBlocks present:
  - Reflective walks the open container's fields to find TileEntity / world+coords / inventory-bound entity / inner inventory.
  - Replaces vanilla 64-block-distance check with `64 × player.getRangeMultiplier()` for normal-scale, or `16 × sizeMult` for tiny.
  - For chest-style containers, rejects opening if can't open single/double.

**Port on 26.x:**
- `UseBlockCallback.EVENT.register` for the right-click cancel.
- For container distance: Mixin into `Container.canUse(player)` / specific
  containers (`ChestBlockEntity#canPlayerUse`).
- The reflective field-walk is unportable; replace with explicit per-container
  Mixin handlers. Drop LittleBlocks branch entirely.

## 10. Subsystem: Client integration (`ClientProxy`, ~80% Optifine glue)

### `ClientProxy.load()`
- Registers `ClientEventHandler` (HUD overlay) on Forge event bus.
- Registers `InteractEventHandler` (also on client for prediction).
- Registers `KeyInputHandler` keybinds.

### `ClientProxy.checkOtherMods()` — mostly drop on Fabric
- Optifine detection (drop — not on Fabric).
- TooManyItems detection + injects resizing potions into TMI menu (drop — not on Fabric).
- LittleBlocks detection (drop).

### Bridge methods (CommonProxy → ClientProxy)
- `isClientPlayerEntity`, `isClientsideEntity`, `clearClientMouseover`,
  `isClientAttacking`, `getClientPlayerMovementSneak`, `getClientPlayerMovementJump`
  → simple side-routed accessors. **On Fabric these become static helper calls or remain dist-routed via `EnvType.CLIENT`-only initializer.**

### `ClientEventHandler.handleAirGUI`
- Cancels vanilla AIR overlay and re-renders custom-positioned bubbles.
  Likely because the player's eye-Y changes with size, and the air bubble
  needs repositioning. The custom version ALSO uses a different number-of-bubbles
  formula (full = `floor((air-2) × 10 / 300)`, partial = same for `air × 10/300` minus full).
- **Port on 26.x:** `HudRenderCallback` overlay that calls
  `RenderGameOverlayEvent.ElementType.AIR` equivalent (overrides
  `InGameHud.renderStatusBars` air section via Mixin or hook).

### `KeyInputHandler` — three keybinds (LWJGL2 keycodes, decode to GLFW for 26.x)
- `key.UPSIZE` = 19 (R) → if holding feather + targeting entity:
  `/entitydoublesize <entityId>`; else `/doublesize`.
- `key.DOWNSIZE` = 33 (F) → if holding feather + targeting entity:
  `/entityhalfsize <entityId>`; else `/halfsize`.
- `key.SHOULDER` = 47 (V) → `/shoulderentity`.

(LWJGL2 → GLFW mapping: 19→GLFW_KEY_R=82, 33→GLFW_KEY_F=70, 47→GLFW_KEY_V=86.)

## 11. Damage source — `EntityDamageSourcePassive`

`extends EntityDamageSource`. Constructor takes a string type (`"step"`,
likely others) and the source entity. Overrides `isDifficultyScaled()` →
false (not scaled by difficulty). Used by `stepOnSmallerEntities` and
likely several block replacements.

**Port on 26.x:** `DamageSource` is a record-based registry now. Register a
custom damage type via data (`data/gulliver/damage_type/step.json`) and
construct via `DamageSources.create(...)` from the DataPack registry.

## 12. Subsystem: `GulliverOMHelper` (131 lines)

100% Optifine + LittleBlocks bridge stubs. **Drop the Optifine half
entirely on Fabric.** The LittleBlocks half (`isLittleBlocksWorld`,
`hasLittleBlocks`) gates worldscale=8 in `InteractEventHandler` — also drop.

## 13. Subsystem: `GulliverForgedServerLaunchWrapper` (12 lines)

```java
System.setProperty("fml.ignorePatchDiscrepancies", "true");
ServerLaunchWrapper.main(args);
```
A standalone launch wrapper for headless servers, telling FML to ignore
patch mismatches when running the modded server.

**Port on 26.x:** N/A. Fabric uses its own launcher; no equivalent needed.

## 14. Bytecode patches required (the invisible part)

The mod's classes call methods like `entity.getSizeMultiplier()`,
`entity.isHuge()`, `entity.holdingPointyItem()`, `entity.canSquish(other)`,
`world.handleEntitySize(packet)` etc. — **none of these exist on vanilla
classes**. Forge 1.6.4 had Forge-style "ASM coremod" / FMP patches + a
custom `mcmod.info` `coremod` declaration. The patches inject:

- New methods on `Entity`: `getSizeMultiplier`, `getSizeMultiplierRoot`,
  `getStepHeight`, `setSizeMultiplier`, `holdingEntity`, `heldEntity`,
  `getStepSide`, `collideableRideEntity`, `canSquish`, `pickUpEntity`,
  `dropHeldEntity`, `maxHeldWidth`, `holdingPointyItem`, `doesUmbrella`,
  `isTiny`/`isExtraTiny`/`isHuge`, etc.
- New methods on `EntityLivingBase`: `setBaseSize`, `adjustBaseSize`,
  `getNewSizeDestMultiplier`, `getRangeMultiplier`, `sizeBaseMultiplier`,
  `sizePotionMultiplier`, `sizeItemMultiplier`.
- New methods on `EntityPlayer`: shoulder mechanics, sleep-in-sized-bed.
- New methods on `World`: `sleepInSizedBedAt`, plus probably
  `b(player, x, y, z)` shadow.
- New methods on `NetClientHandler` / `NetHandlerPlayClient`:
  `handleEntitySize`, `handleAttachEntitySpecial`.
- Per-tick callouts to `GulliverEnvoy.stepOnSmallerEntities`,
  `GulliverEnvoy.leaveHugeFootprints`, `GulliverEnvoy.checkSupportingBlocksForHuge`,
  `GulliverEnvoy.tinyCaughtInRain`, `GulliverEnvoy.getRisingUpdraft`,
  `GulliverEnvoy.alongStickySurface`, etc. inserted into
  `Entity.onUpdate` / `Entity.moveEntity`.

**Port on 26.x — reimplement the size system field-for-field. NO vanilla shortcuts.**

This is binding: do NOT substitute `Attributes.SCALE` or
`Attributes.STEP_HEIGHT` for Gulliver's own multipliers. Vanilla SCALE
(added in 1.20.5) only does uniform bbox/eye-height/step-height/reach
multiplication. Gulliver does substantially more — custom walking-animation
timing, sound volume scaling by `sizeMultiplierRoot`, custom
`pushOutOfBlocks` that lets tinies fit in tunnels, `smallBlockOpeningStrength`,
material-typed `blockClimbingRateForTiny`, heat-source `getRisingUpdraft`
physics, `alongStickySurface`, `tinyCaughtInRain`, brittle-block stomping,
huge-footprint trampling, `breakBlocksViaGrowth`, `resizeCollision` push-out
— and these are the *feel* of the mod, not implementation details. They
go in verbatim.

The patch surface becomes:
- Mixin-injected `@Unique` fields on `Entity` for `sizeBaseMultiplier`,
  `sizePotionMultiplier`, `sizeItemMultiplier`. `getSizeMultiplier()`
  composes them as the original did. **Not** `getAttributeValue(SCALE)`.
- Mixins into `Entity#tick`, `Entity#move`, `LivingEntity#tickMovement`,
  `PlayerEntity#travel`, `Entity#playSound`, `Entity#getEyeHeight`,
  `Entity#getStepHeight`, `Entity#pushOutOfBlocks`, etc., to inject
  callouts at the same code points the 1.6.4 ASM patches did.
- Eye height = `1.62F * getSizeMultiplier()` for players (and the
  1.6.4 corresponding default for other living entities). Mixin into
  `Entity#getEyeHeight`/`LivingEntity#getActiveEyeHeight` to return the
  Gulliver-computed value, **not** SCALE attribute output.
- Step height = port the original `getStepHeight()` formula. Mixin into
  `Entity#getStepHeight` (or its modern equivalent) to override.
- Reach = port `getRangeMultiplier()` and Mixin into the reach checks
  where Gulliver applied them (chest distance, container interaction).
- Sound volume = Mixin into `Entity#playSound(SoundEvent, vol, pitch)` to
  multiply `vol *= getSizeMultiplierRoot()` exactly as the 1.6.4
  `EntityResizeableClientPlayerMP.a(String, float, float)` did.
- Push-out = Mixin into `Entity#pushOutOfBlocks` mirroring
  `EntityResizeableClientPlayerMP.i(double, double, double)` so tinies
  who fit in 1-block tunnels are NOT pushed out.
- Shoulder entity = Mixin into the vanilla parrot-shoulder logic to widen
  the type filter and width check, OR add a parallel shoulder slot. Drive
  the full mechanic from Gulliver's own `pickUpEntity`/`dropHeldEntity`/
  `maxHeldWidth`/`heldEntity`/`holdingEntity` fields, ported as
  `@Unique` Mixin fields on `PlayerEntity`.

Vanilla classes that the original ALREADY used (`EntityDamageSource`,
`Potion`, `Block` subclasses) can still be subclassed — that's not the
shortcut rule. The rule is about scaling/movement/feel: those reproduce
the original's formulas exactly.

## 15. What does NOT get ported

These are 1.6.4-era ecosystem hooks with no modern parallel and no
behavioral value to the original mod's identity:

- All Optifine integration (`GulliverOMHelper`, `ClientProxy.checkOptifineSettings`,
  `ClientProxy.getOptifineWorldServer`, `ClientProxy.getOptifineCustomColor` etc.)
  — Optifine doesn't run on Fabric. Drop entirely.
- TooManyItems integration — Gulliver poked at TMI's potion list. Drop.
- LittleBlocks compat (`hasLittleBlocks`, `isLittleBlocksWorld`,
  worldScale=8 path in `InteractEventHandler`). LittleBlocks doesn't exist
  on 26.x. Drop.
- `GulliverForgedServerLaunchWrapper`. Fabric uses its own launcher.

Everything else from the original mod ports. The mod's identity is the
~5,400 lines of resizing mechanics in `GulliverEnvoy`, the 14 block
replacements, the 14 commands, the 2 packets, the potion, the achievements,
and the player-class extensions.

## 16. Phase plan (binding)

1. **Phase 1 — skeleton.** Empty jar builds. **(DONE — commit `1388a55`).**
2. **Phase 2 — size data model & API.** Create `IResizeableEntity` /
   `IResizeableLiving` / `IResizeablePlayer` Mixin interfaces. Inject
   `@Unique` fields `sizeBaseMultiplier`, `sizePotionMultiplier`,
   `sizeItemMultiplier` onto `Entity` (or `LivingEntity` where it
   makes sense). `getSizeMultiplier()` returns their product as the
   1.6.4 mod did. **No `Attributes.SCALE`.** Implement
   `setBaseSize`/`adjustBaseSize` writing `sizeBaseMultiplier` directly.
   Mixin into `Entity#getEyeHeight` returning `defaultEyeHeight *
   sizeMultiplier`; Mixin into `Entity#getStepHeight` returning the
   ported `getStepHeight()` formula. Persist size multipliers in
   `writeNbt`/`readNbt` (Mixin) so they round-trip across save/load.
3. **Phase 3 — config (Gson).** Port the 4 categories with all keys.
4. **Phase 4 — packets.** `EntitySizePayload`, `AttachSpecialPayload`. Wire
   server→client size sync.
5. **Phase 5 — commands.** All 14 in Brigadier.
6. **Phase 6 — potion + brewing.** `tiny`/`huge` status effects + brewing
   recipes (red mushroom → tiny, brown → huge).
7. **Phase 7 — InteractEventHandler.** Door/lever/button block + chest
   distance scaling.
8. **Phase 8 — block replacements.** Each of the 14 as its own substage,
   via Mixin per behavior.
9. **Phase 9 — passive damage source.**
10. **Phase 10 — huge-entity ground effects** (footprints, brittle-block
    breaking, step-on-smaller).
11. **Phase 11 — tiny-entity helpers** (climb rate, sticky surface,
    updraft, rain shelter, intersecting plant, push-out-of-blocks).
12. **Phase 12 — shoulder entity.** Pick up / drop including the packet.
13. **Phase 13 — sleep in sized bed.**
14. **Phase 14 — keybinds + custom AIR overlay** (client).
15. **Phase 15 — achievements as advancements** (drinkMe, eatMe).
16. **Phase 16 — sound/volume scaling, eye height, step height.**
17. **Phase 17 — dye-resizing** (cyan/purple dye drink/eat).
18. **Phase 18 — karma mode** (reset on death).
19. **Phase 19 — `/serverreloadgulliver` + final config polish.**

Each phase is broken into substages (numbered like `2(1)`, `2(2)`) where
each substage builds green and is committed independently, mirroring the
witherstorm port's rhythm.
