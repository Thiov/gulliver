# Gulliver

Shrink down to explore the world from an ant's-eye view, or grow into a world-crushing giant. Resize yourself and other entities anywhere from **0.125× to 8×**, and everything scales to match: how far you reach, how hard you hit, how mobs react to you, how the world sounds, and how it *feels* when something enormous walks past.

Built from the ground up for **Minecraft 26.1.2** on Fabric, inspired by UncleMion's classic Gulliver mod for 1.6.4. The only dependency is the Fabric API, with no external scaling library.

## Features

**Everything scales with your size**
- **Movement**: walk speed, jump height, step height, and fall damage.
- **Falling**: the square-cube law applies. The smaller you are, the lower your terminal velocity, so long falls become a leaf-light drift while jumps stay snappy. Giants fall normally, but the *world* answers. Landing from a real height sets off a ground-shock that booms, kicks up a ring of dust, and knocks smaller creatures off their feet.
- **Reach**: interaction range scales with size. Tinies get a bump when holding a tool.
- **Combat**: damage and knockback scale with the size gap, and big attackers often miss much smaller targets. Drawing a bow, cranking a crossbow, or winding up a trident takes longer the smaller you are, and is nearly instant for a giant.
- **Crushing**: large entities trample crops, trip pressure plates, break fragile blocks, and hurt whatever's underfoot.
- **Mob AI**: mobs ignore prey far smaller than themselves, though spiders, silverfish, endermites, and bees will always hunt tinies.

**Presence: big things are seen, heard, and felt**
- Every footfall of a creature much larger than you (roughly 6× and up) **thumps your screen** in rhythm with its stride, stronger the closer and bigger it is. A giant's landing quakes everyone much smaller nearby.
- Big bodies are **loud**. A giant's footsteps and impacts carry far beyond the usual 16 blocks, so you'll hear it coming long before it appears.
- All entity sounds are **pitched relative to your own size**. A giant's voice and footsteps rumble deep when you're small, while the giant hears you as a squeak. The relationship is identical at every scale pair, and your own sounds always stay normal, so the world changes around you rather than your ears.

**Giant powers**
- **Fists that span blocks**: breaking a block shatters a fist-shaped crater around it, from 3×3 at the huge threshold up to a 7×7 disc at size 8. Harder neighbours, containers, and unbreakables survive.
- **Sweeping blows**: melee hits splash reduced damage to creatures around your target, scaled by attack charge.
- **Precision mode**: hold sneak for a single block and a single target.
- **Growing pains**: grow indoors and your expanding body bursts through the blocks above you if your new bulk beats their hardness. Glass and dirt pop easily, a size-4 giant splinters stone, and obsidian just leaves you cramped. Shrinking never breaks anything.

**Tiny survival tricks**
- Glide slowly and ride heat updrafts with paper in hand, though wet paper doesn't fly, so no gliding in the rain.
- Stand on water with a lily-pad, rendered in both first and third person.
- Climb soft blocks while sneaking, or any wall with a slimeball or string in hand.
- Carry a pointy item (sword, tool, or stick) to use functional blocks. You're also too light to trip pressure plates and tripwires.
- **Rain slowly drowns the smallest sizes.** At that scale a raindrop is a body-sized mass of water, and your air drains as if you were submerged. Hold a lily-pad overhead as an umbrella (it renders above your head in both views), duck under cover, or sneak to huddle through the storm.
- Hide inside flowers.

**Carry**
- Sneak + right-click a smaller mob or player to scoop it into your hand.
- Press **V** (or `/shoulderentity`) to shuffle carried entities between hand and shoulders, up to three at once.
- Right-click to set one down, or `/shoulderentity throw` to toss it. The bigger you are compared to what you're throwing, the further it flies.

**Polish**: held items, nameplates, view-bobbing, third-person camera distance, and eating speed all scale with size.

## Resizing

- **Potions and mushrooms**: Drink-Me (cyan dye) shrinks, Eat-Me (purple dye) grows, and red and brown mushrooms do the same. Brew a Tiny potion from an awkward potion + red mushroom.
- **Commands** (OP): `/basesize`, `/halfsize`, `/doublesize`, `/showsize`, plus `entity…` variants that target another entity.
- **Keybinds** (creative): **U** grow, **I** shrink, **V** carry. Hold a **stick** and the grow/shrink keys retarget the entity under your crosshair instead of yourself.
- **Game rules**: `gulliver:size_griefing` toggles size-based griefing, and karma mode resets size on respawn.

Per-entity spawn sizes and limits live in `config/gulliver.json` (reload with `/reloadgullivercfg`).

## Install

Requires **Minecraft 26.1.2**, **Fabric Loader 0.19.2+**, **Fabric API**, and **Java 25**. Drop the jar into `mods/`.

## Build

```sh
./build.sh
```

There is no Loom step. 26.1.2 ships unobfuscated, so the script compiles the sources directly with `javac --release 25` against an access-widened Minecraft jar. Output lands in `build/libs/`. Adjust the JDK and library paths at the top of the script for your machine.

## Credits and license

Inspired by UncleMion's [Gulliver Forged (2013)](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/1282337-mc-forge-1-6-4-gulliver-the-resizing-mod-v0-14-3) for Minecraft 1.6.4. This is an independent mod written for modern Minecraft, sharing that mod's spirit rather than its code. All rights reserved.
