# Gulliver (Fabric)

A faithful port of [Gulliver Forged 0.14.3 for Minecraft 1.6.4](https://www.curseforge.com/minecraft/mc-mods/gulliver-forged) to **Minecraft 26.1.2 / Fabric**.

The original mod (UncleMion, 2013) is the only resizing mod with hand-tuned formulas for every system that touches body size — movement, jumping, fall damage, knockback, reach, climbing, gliding, container interaction, mob targeting, footstep crushing, particle scaling, and more. This port reimplements those formulas field-for-field; it is not a generic "scale attribute" wrapper.

## What it does

Resize yourself or any entity from 0.125× to 8× normal size. Every interaction with the world scales accordingly:

- **Movement** — walk speed, jump power, step height, and fall damage all follow the original 1.6.4 formulas.
- **Reach** — block / entity interaction range scales linearly with size. Tinies holding a sword, stick, pickaxe, axe, hoe, shovel, or shears get a reach bump (size-0.5 player equivalent).
- **Container interaction** — tinies need a pointy item (sword/stick/tool) to right-click any block with a function (chests, furnaces, crafting tables, buttons, levers, doors, etc.). With one in hand, full interaction is restored.
- **Pressure plates / tripwires** — tinies are too light to trigger them.
- **Mob AI** — mobs ignore targets less than 0.3× their own size (a size-0.125 player hides from a size-1 zombie the same way a size-1 player hides from a size-8 zombie). Spider, cave spider, silverfish, endermite, and bee always notice tinies and actively pursue them, regardless of light level.
- **Combat** — damage scales as sqrt of the size ratio. A size-1 zombie still hits a tiny hard, but doesn't one-shot. Mobs swinging at a much-smaller target have a high miss chance. Knockback scales as `attackerSize / targetSize` for both bare-hand and weapon hits.
- **Crushing** — huge entities trample crops, set off pressure plates, break brittle blocks (glass / wool) when standing only on flimsy support, and damage smaller entities at their feet. Damage is reduced (~3 dmg per step at 4× vs 0.125×) so smaller entities can flee.
- **Gliding** — paper held in hand triggers a slow descent + heat-source updraft (lava, fire, sunny grass during day).
- **Lily-pad raft** — holding a lily-pad lets a tiny stand on water. First-person renders the raft under you.
- **Tiny climbing** — soft blocks (dirt, grass, wool, leaves, sand) are scalable by tinies while shifted. Slime-ball in hand grants automatic climbing on any solid wall.
- **Body-shoulder passenger** — small mobs/players can ride on your shoulder via `/shoulderentity`.
- **Sleep** — bed dimensions scale with size.
- **Sound volume** — entity sounds scale with `sqrt(size)`.
- **Held items** — render at the correct relative size; nameplates above the head shrink/grow with the body.

## Resize methods

- **Drink-Me potion** (cyan dye) → Tiny status effect (200 ticks).
- **Eat-Me potion** (purple dye) → Huge status effect (200 ticks).
- **Mushroom variants** — red mushroom → Tiny, brown → Huge.
- **Brewing** — `awkward + red mushroom` → Tiny potion (long / strong variants via redstone / glowstone).
- **Commands** (OP-gated) — `/basesize`, `/halfsize`, `/doublesize`, `/showsize`, plus `entity*` variants targeting another entity.
- **Keybinds** (creative only for self-resize) — `U` upsize, `I` downsize, `V` shoulder. Holding a feather + targeting an entity dispatches the entity-targeted variant in any game mode.
- **Karma mode** — game rule that resets size on death respawn.
- **Size-griefing gate** — `gulliver:size_griefing` game rule controls whether resized entities can break / squish / trample (defaults true).

## Install

1. Drop the jar into `mods/`.
2. Requires **Fabric Loader 0.19.2+**, **Fabric API 0.146.1+**, **Minecraft 26.1.2**, **Java 25**.

## Build

```sh
./build.sh
```

The build script invokes `javac --release 25` directly against a widened-public Minecraft jar — there is no Loom step (26.1.2 has no Loom named namespace yet). Output: `build/libs/gulliver-0.14.3-fabric.jar`.

Required local paths (configurable in `build.sh`):
- JDK 25 at `${JAVA_HOME}`
- Widened-public MC jar at `${MC_JAR}`
- Fabric Loader / API libs at `${LIBS}`

## Source layout

```
src/main/java/gulliver/
  api/          IResizeable{Entity,Living,Player} interfaces (1.6.4 verbatim)
  client/       Keybinds, client packet receivers, lily-pad world renderer
  command/      14 Brigadier commands (1.6.4 names + permission levels preserved)
  common/       GulliverEnvoy (the bulk of the size math), config, event handlers
  init/         Damage types, effects, game rules, potions
  mixin/        ~50 Mixins for entity/block/render hooks
  network/      EntitySize + AttachEntitySpecial CustomPacketPayload records
src/main/resources/
  data/gulliver/        advancements, damage_type/passive
  assets/gulliver/      lang
  gulliver.mixins.json  mixin manifest
  fabric.mod.json       loader manifest
reference/
  gulliver_jdcore/      JDCore decompile of the 1.6.4 jar — the only reference
docs/
  gulliver-survey.md    mod-survey notes from the porting work
```

## Scope

This port targets the **exact 0.14.3-MC1.6.4 behavior**. Later "Gulliver-like" reimplementations (Lilliputian, ProjectS) are explicitly out of scope — the original is the canonical reference.

A handful of behaviors deviate from the literal 1.6.4 source where playtest feedback overruled it:

- Damage scaling uses `sqrt(size)` instead of linear (the 1.6.4 8× multiplier at extreme size disparity was instant-kill).
- Tiny soft-block climbing requires holding shift (1.6.4 climbed automatically — felt aggressive in modern movement).
- Resize keybinds are creative-only for self-resize.

Out of scope: Optifine glue, TMI, LittleBlocks, ThornyFlower (1.6.4-only block, no 26.x analog), the launch-wrapper coremod, and the AIR-HUD overlay (that was a 1.6.4 anti-Optifine workaround; modern MC renders correctly without it).

## Credits

- **Original mod:** [UncleMion, Gulliver Forged 0.14.3 (MC1.6.4, 2013)](https://www.curseforge.com/minecraft/mc-mods/gulliver-forged).
- **JDCore decompile** of the original jar — the only source-of-truth reference used for this port.

## License

All rights reserved (matches the upstream license).
