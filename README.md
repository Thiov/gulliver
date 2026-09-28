# Gulliver

Shrink down to explore the world from an ant's-eye view, or grow into a world-crushing giant. Resize yourself and other entities anywhere from **0.125× to 8×**, and everything scales to match: how far you reach, how hard you hit, how mobs react to you, how the world sounds, and how it *feels* when something enormous walks past.

Inspired by UncleMion's classic Gulliver mod for 1.6.4. No scaling library is needed: on Fabric the only dependency is the Fabric API, on NeoForge and Forge there are none.

## Downloads

| Minecraft | Fabric | NeoForge | Forge |
|-----------|:------:|:--------:|:-----:|
| 26.2      | ✓ | ✓ | ✓ |
| 26.1.x    | ✓ | ✓ | ✓ |
| 1.21.11   | ✓ | ✓ | ✓ |
| 1.21.10   | ✓ | ✓ | ✓ |
| 1.20.1    | ✓ | ✓ | ✓ |

Pick the jar whose name matches your loader and Minecraft version, e.g. `gulliver-1.1.0+neoforge-26.1.2.jar`. Fabric needs the Fabric API.

## Features

**Everything scales with your size**
- **Movement**: walk speed, jump height, step height, and fall damage.
- **Falling**: the smaller you are, the lower your terminal velocity, so long falls become a leaf-light drift while jumps stay snappy. Giants fall normally, but landing from a real height sets off a ground-shock that booms, kicks up dust, and knocks smaller creatures off their feet.
- **Reach**: interaction range scales with size. Tinies get a bump when holding a tool.
- **Combat**: damage and knockback scale with the size gap. A much bigger mob often swings right over a tiny. Drawing a bow, cranking a crossbow, or winding up a trident takes longer the smaller you are.
- **Trampling**: anything walking over a creature well under half its size tramples it, so a normal zombie is a real danger to a quarter-size player. Giants also trample crops, trip pressure plates, and crack brittle floors.
- **Mob AI**: mobs ignore prey far smaller than themselves, though spiders, silverfish, endermites, and bees will always hunt tinies.

**Presence: big things are seen, heard, and felt**
- Every footfall of a creature much larger than you **thumps your screen**, stronger the closer and bigger it is.
- Big bodies are **loud**: a giant's footsteps carry far beyond the usual 16 blocks.
- Entity sounds are **pitched relative to your own size**: giants rumble when you're small, and you squeak to a giant.

**Giant powers**
- **Fists that span blocks**: breaking a block shatters a fist-shaped crater around it, up to a 7×7 disc at size 8. Collateral blocks only drop what your tool can harvest, and wear the tool.
- **Sweeping blows**: melee hits splash damage onto creatures around your target.
- **Precision mode**: hold sneak for a single block and a single target.
- **Growing pains**: grow indoors and your body bursts through weak blocks above you.

**Tiny survival tricks**
- **Grappling hook**: cast a fishing rod while tiny and the bobber bites into whatever it hits: wall, ceiling, or floor. Reel in to be pulled up to it. Hook a creature at least twice your size and you're pulled onto it instead.
- Glide slowly and ride heat updrafts with paper in hand (not in the rain).
- Float on water with a lily pad.
- Climb soft blocks while sneaking, or any wall with a slimeball or string in hand.
- Carry a pointy item (sword, tool, or stick) to use functional blocks. You're too light to trip pressure plates and tripwires.
- **Rain slowly drowns the smallest sizes.** Hold a lily pad overhead as an umbrella, get under cover, or sneak to huddle.
- Hide inside flowers.
- Paper, lily pads, slimeballs, and string all work from **either hand**.

**Carry**
- Sneak + right-click (empty hand) a creature under 0.4× your size to scoop it into your hand.
- Press **V** to move carried creatures between hand and shoulders, up to three at once.
- Right-click to set one down, left-click to throw it. The bigger you are, the further it flies.
- Other players can only be carried where PvP is allowed, and can always break free by sneaking. Bosses can't be carried.

**Polish**: held items, name tags, view-bobbing, the third-person camera distance, and eating speed all scale with size.

## Resizing

- **Potions**: brew Ensmallening from an awkward potion + red mushroom, Embiggening with a brown mushroom. Redstone extends, glowstone strengthens.
- **Drink Me / Eat Me**: right-click cyan dye or a red mushroom to shrink, purple dye or a brown mushroom to grow.
- **Commands** (operators): `/basesize`, `/halfsize`, `/doublesize`, `/showsize`, `/instantkarma`, plus `entity…` variants that take an entity id. `/showmysize` and `/shoulderentity` work for everyone.
- **Keybinds** (creative): **U** grow, **I** shrink. Hold a **stick** and they resize the creature under your crosshair instead.

## Configuration

`config/gulliver.json` (reload with `/reloadgullivercfg`):

- `general`: size limits, `enableDyeResizing`, `enableKarmaMode` (reset size on death), `fishingRodGrapple` and `grappleMaxSize` (default 0.3, i.e. tiny), `trampleSizeRatio` (default 0.4, 0 disables trampling).
- `spawnSize`: starting sizes for players, animals, monsters, villagers, and per-entity overrides. Accepts plain sizes (`"0.5"`), ranges (`"0.5-2"`), sets (`"0.5,1,2"`) and heights (`"5'9\""`, `"120cm"`).
- `sizeLimit`: minimum and maximum sizes per class and per entity.
- `client.heldItemScaling`: `"classic"` (items look bigger when you're tiny, as in the original) or `"proportional"` (items stay in proportion to your body).

Game rule `gulliver:size_griefing` (`gulliverSizeGriefing` before 1.21.11) turns off every size-based block breaking.

## Building

All targets share one source tree; each `versions/<minecraft>/<loader>` folder is a small standalone Gradle build.

```sh
./gradlew -p versions/26.1.2/neoforge build     # one target
scripts/build-all.sh                            # every target, jars collected in dist/
```

Gradle must run on **JDK 25** (older targets are compiled for Java 17 or 21 through toolchains).

- `src/common` holds the mod itself, `src/fabric`, `src/neoforge` and `src/forge` the thin loader glue.
- Version differences are handled at build time by `gradle/gulliver.gradle`: `//#if MC >= 1.21.2 && !FORGE … //#else … //#endif` blocks, whole-file conditions, and `versions/<mc>/remap.txt` for plain renames. Sources are written against the newest Minecraft names.
- `scripts/selftest.sh <mc> <loader>` boots a dedicated server and then a client that joins it, with `-Dgulliver.selftest=true`: every mixin is force-applied and resizing, combat, trampling, carrying, spawn sizes, effects, brewing and the grappling hook are exercised before the game quits. `scripts/check_mixins.py` checks every mixin target against a Minecraft jar without launching.

## Credits and license

Inspired by UncleMion's [Gulliver Forged (2013)](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/1282337-mc-forge-1-6-4-gulliver-the-resizing-mod-v0-14-3) for Minecraft 1.6.4. This is an independent mod written for modern Minecraft, sharing that mod's spirit rather than its code. All rights reserved.
