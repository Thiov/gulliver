# Gulliver

Shrink down to explore the world from an ant's-eye view, or grow into a world-crushing giant. Resize yourself and other entities anywhere from **0.125× to 8×** (configurable from 1/32× up to 32×), and everything scales to match: how far you reach, how hard you hit, how mobs react to you, how the world sounds, and how it *feels* when something enormous walks past.

Inspired by UncleMion's classic Gulliver mod for 1.6.4; on CurseForge and Modrinth it's **Gulliver Unbound**. No scaling library is needed: on Fabric the only dependency is the Fabric API, on NeoForge and Forge there are none.

## Downloads

| Minecraft | Fabric | NeoForge | Forge |
|-----------|:------:|:--------:|:-----:|
| 26.3      | ✓ | ✓ | ✓ |
| 26.2      | ✓ | ✓ | ✓ |
| 26.1.x    | ✓ | ✓ | ✓ |
| 1.21.11   | ✓ | ✓ | ✓ |
| 1.21.10   | ✓ | ✓ | ✓ |
| 1.20.1    | ✓ | ✓ | ✓ |

Download **Gulliver Unbound** from [CurseForge](https://www.curseforge.com/projects/1539752) or [Modrinth](https://modrinth.com/mod/gulliver-unbound), or get the jars from the [releases page](https://github.com/Thiov/gulliver/releases) and pick the one whose name matches your loader and Minecraft version, e.g. `gulliver-1.3.0+neoforge-26.3.jar`. Fabric needs the Fabric API; Mod Menu is optional (settings button). Install it on the server and on every client.

## Features

**Everything scales with your size**
- **Movement**: walk speed, jump height, step height, and fall damage.
- **Falling**: the smaller you are, the lower your terminal velocity, so long falls become a leaf-light drift while jumps stay snappy. Giants fall normally, but landing from a real height sets off a ground-shock that booms, kicks up dust, and knocks smaller creatures off their feet.
- **Reach**: interaction range scales with size. Tinies get a bump when holding a tool. Better Combat's weapon swings reach as far as you do.
- **Combat**: damage and knockback scale with the size gap. A much bigger mob often swings right over a tiny. Drawing a bow, cranking a crossbow, or winding up a trident takes longer the smaller you are.
- **Trampling**: anything walking over a creature well under half its size tramples it, so a normal zombie is a real danger to a quarter-size player. Giants also trample crops, trip pressure plates, and crack brittle floors. Sneak or crawl (vanilla, ParCool or Crawl) to step carefully and trample nothing; how hard a step hits is a setting.
- **Mob AI**: mobs ignore prey far smaller than themselves, though spiders, silverfish, endermites, and bees will always hunt tinies.

**Presence: big things are seen, heard, and felt**
- Every footfall of a creature much larger than you **thumps your screen**, stronger the closer and bigger it is.
- Big bodies are **loud**: a giant's footsteps carry far beyond the usual 16 blocks.
- Entity sounds are **pitched relative to your own size**: giants rumble when you're small, and you squeak to a giant.

**Giant powers**
- **Fists that span blocks**: breaking a block shatters a fist-shaped crater around it, up to a 7×7 disc at size 8. Collateral blocks only drop what your tool can harvest, and wear the tool.
- **Sweeping blows**: melee hits splash damage onto creatures around your target.
- **Appetite**: anything can eat a creature a quarter of its size or smaller. Hold a creature in your hand and hold sneak + right-click: you lift it to your mouth (you see your arm come up in first and third person) and bite it. Hostile giants (2× and up) hunt anything they can eat and grab it off the ground at any health; smaller hostile mobs only snatch prey that is down to a third of its health. Either way they lift it to their mouth over a second and a half and bite it four times, half a second apart. Every bite is real damage that armor, Protection and Resistance reduce, so a well-armored tiny takes more bites, and one that survives them all is spat out, hurt but alive. Hurting the monster makes it set you down at its feet, and it can't grab you again for three seconds; sneaking doesn't get you out. Eaten creatures leave no loot and feed the eater.
- **Breath gust**: hold **G** to blow a cone of wind, four of your own lengths long. It only moves what's lighter than you, harder the lighter it is: a giant blows normal mobs clean away, a normal player blows a tiny away, and nobody can shove something their own size. It turns arrows and fireballs around and blows out candles, campfires and fire. Wisps of air leave your mouth and fly on by themselves, drawn to your size: they swirl, slow down and drift up, trail behind when you turn your head, and spread out over walls, the ground and water instead of going through them. A whisper for a tiny, a gale that kicks up the ground and whips up spray for a giant, rolling rings of wind for a colossus. Water stops the wind, so nothing under the surface is blown; under water your breath goes up as a stream of bubbles. A full breath lasts three seconds, so you can't blow forever.
- **Precision mode**: hold sneak for a single block and a single target.
- **Growing pains**: grow indoors and your body bursts through weak blocks above you.

**Tiny survival tricks**
- **Grappling hook**: cast a fishing rod while tiny and the bobber bites into whatever it hits: wall, ceiling, or floor. Reel in to be pulled up to it. Hook a creature at least twice your size and you're pulled onto it instead.
- Glide slowly and ride heat updrafts with paper in hand (not in the rain).
- Float on water with a lily pad.
- Climb soft blocks while sneaking, or any wall with a slimeball or string in hand.
- Carry a pointy item (sword, tool, or stick) to use functional blocks. You're too light to trip pressure plates and tripwires.
- **Rain slowly drowns the smallest sizes.** Hold a lily pad overhead as an umbrella, get under cover, or sneak to huddle.
- Hide inside flowers: others can't see you, and an eye icon tells you you're hidden.
- **Ride small animals**: right-click a chicken, rabbit, cat, ocelot, wolf or fox at least three times your size with an empty hand and steer it like a horse. Chickens flap down gently, rabbits leap.
- Wind charges and breezes fling tinies across the room; giants barely notice them.
- Paper, lily pads, slimeballs, and string all work from **either hand**, and their tooltips tell you what they do while you're tiny.

**Carry**
- Sneak + right-click (empty hand) a creature under 0.4× your size to scoop it into your hand.
- Press **V** to move carried creatures between hand and shoulders, up to three at once.
- Right-click to set one down, left-click to throw it. The bigger you are, the further it flies. Hold sneak + right-click to eat it instead (if it's a quarter of your size or smaller).
- Other players can only be carried where PvP is allowed, and can always break free by sneaking. Bosses can't be carried.

**Polish**: resizing animates smoothly with a puff and a rising or falling sound, and the action bar shows your new size. Held items, name tags, shadows, view-bobbing, the third-person camera distance, water splashes and eating speed all scale with size. Giant mobs drop more loot, tiny ones less, and can optionally show a boss bar. A Gulliver advancement tab tracks the milestones.

**Giants and mounts**: a giant can't squeeze into a boat or onto a mount much smaller than itself.

## Resizing

- **Potions**: brew Ensmallening from an awkward potion + red mushroom, Embiggening with a brown mushroom. Redstone extends, glowstone strengthens.
- **Drink Me / Eat Me**: right-click cyan dye or a red mushroom to shrink, purple dye or a brown mushroom to grow.
- **Commands** (operators): `/gulliver size <targets> <size>`, `/gulliver scale <targets> <factor>`, `/gulliver get <target>` and `/gulliver reload` take normal selectors (`@e[type=cow,distance=..10]`, player names). The classic `/basesize`, `/halfsize`, `/doublesize`, `/showsize`, `/instantkarma` and their `entity…` variants (by entity id) still work. `/showmysize` and `/shoulderentity` work for everyone.
- **Keybinds** (creative): **U** grow, **I** shrink. Hold a **stick** and they resize the creature under your crosshair instead. For everyone: **V** moves carried creatures between hand and shoulders, **G** (hold) blows a breath gust. All of them can be rebound in Controls.

## Configuration

In game: **Mods → Gulliver → Config** on NeoForge and Forge, or the settings button in [Mod Menu](https://modrinth.com/mod/modmenu) on Fabric. The settings screen covers the gameplay switches, spawn sizes, size limits and held-item style. On a multiplayer server the server's own config decides gameplay.

Everything is stored in `config/gulliver.json` (reload with `/gulliver reload`):

- `general`: size limits (`minEntitySize`/`maxEntitySize`, default 0.125 and 8, allowed 1/32 to 32; outside 1/8–8 is experimental), `enableDyeResizing`, `enableKarmaMode` (reset size on death), `fishingRodGrapple` and `grappleMaxSize` (default 0.3, i.e. tiny), `trampleSizeRatio` (default 0.4, 0 disables trampling), `trampleDamage` (0 to 10, default 1), `breathGust`, `giantAppetite`.
- `spawnSize`: starting sizes for players, animals, monsters, villagers, and per-entity overrides. Accepts plain sizes (`"0.5"`), ranges (`"0.5-2"`), sets (`"0.5,1,2"`) and heights (`"5'9\""`, `"120cm"`).
- `sizeLimit`: minimum and maximum sizes per class and per entity.
- `client.screenShake` (0 to 1, also follows vanilla's Damage Tilt and Distortion Effects), `client.smoothResize`, `client.sizeReadout`.
- `general.lootScaling`, `general.giantBossBars` (off by default), `general.tinyMounts`.
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
- `scripts/selftest.sh <mc> <loader>` boots a dedicated server and then a client that joins it, with `-Dgulliver.selftest=true`: every mixin is force-applied and resizing, combat, trampling, carrying, spawn sizes, effects, brewing and the grappling hook are exercised before the game quits. `scripts/prodtest.sh <mc>` does the same with the release jar on a real Fabric server. `scripts/check_mixins.py` checks every mixin target and handler signature against a Minecraft jar without launching. With a file named `gulliver-showcase` in a target's `run` folder, the self-test client plays a scripted tour of the breath gust (on land, over water and under it), carrying and eating instead and saves screenshots (`run/screenshots`).

## Credits and license

Inspired by UncleMion's [Gulliver Forged (2013)](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/1282337-mc-forge-1-6-4-gulliver-the-resizing-mod-v0-14-3) for Minecraft 1.6.4. This is an independent mod written for modern Minecraft, sharing that mod's spirit rather than its code. All rights reserved.
