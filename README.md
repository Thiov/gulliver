# Gulliver (Fabric)

A Fabric port of [Gulliver Forged 0.14.3](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/1282337-mc-forge-1-6-4-gulliver-the-resizing-mod-v0-14-3) — UncleMion's resizing mod for Minecraft 1.6.4 — brought to **Minecraft 26.1.2**.

Resize yourself or other entities anywhere from **0.125× to 8×**, with movement, combat, reach, and world interaction scaling to match. The only dependency is the Fabric API — no external scaling library.

## Features

**Size-scaled mechanics**
- **Movement** — walk speed, jump height, step height, and fall damage scale with size.
- **Reach** — interaction range scales with size; tinies get a reach bump when holding a tool.
- **Combat** — damage and knockback scale with the size difference, and large attackers often miss much smaller targets.
- **Crushing** — big entities trample crops, trigger pressure plates, break fragile blocks, and hurt entities underfoot.
- **Mob AI** — mobs ignore targets far smaller than themselves (spiders, silverfish, endermites, and bees still hunt tinies).

**Tiny abilities**
- Glide slowly and ride heat updrafts with paper in hand.
- Stand on water with a lily-pad (rendered in both first and third person).
- Climb soft blocks while sneaking; climb any wall with a slimeball or string in hand.
- Need a pointy item (sword, tool, or stick) to use functional blocks; too light to trip pressure plates and tripwires.
- Shelter from rain — which hurts the smallest sizes — under paper or a lily-pad, and hide inside flowers.

**Carry**
- Sneak + right-click a smaller mob or player to pick it up into your hand.
- Press **V** (or `/shoulderentity`) to shuffle carried entities between hand and shoulders — up to three at once.
- Right-click to set one down, or `/shoulderentity throw` to toss it.

**Polish** — held items, nameplates, view-bobbing, third-person camera distance, and eating speed all scale with size.

## Resizing

- **Potions / mushrooms** — Drink-Me (cyan dye) shrinks, Eat-Me (purple dye) grows; red and brown mushrooms do the same. Brew a Tiny potion from an awkward potion + red mushroom.
- **Commands** (OP) — `/basesize`, `/halfsize`, `/doublesize`, `/showsize`, plus `entity…` variants that target another entity.
- **Keybinds** (creative) — **U** grow, **I** shrink, **V** carry.
- **Game rules** — `gulliver:size_griefing` toggles size-based griefing; karma mode resets size on respawn.

Per-entity spawn sizes and limits live in `config/gulliver.json` (reload with `/reloadgullivercfg`).

## Install

Requires **Minecraft 26.1.2**, **Fabric Loader 0.19.2+**, **Fabric API**, and **Java 25**. Drop the jar into `mods/`.

## Build

```sh
./build.sh
```

There is no Loom step — 26.1.2 ships unobfuscated, so the script compiles the sources directly with `javac --release 25` against an access-widened Minecraft jar. Output lands in `build/libs/`. Adjust the JDK and library paths at the top of the script for your machine.

## Credits & license

Original mod by [UncleMion — Gulliver Forged 0.14.3 (2013)](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/1282337-mc-forge-1-6-4-gulliver-the-resizing-mod-v0-14-3). All rights reserved, matching the upstream license.
