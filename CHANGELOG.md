# Changelog

## Unreleased

### New

- **Breath gust** (#10): hold **G** to blow a cone of wind out of your mouth. Creatures smaller than you are pushed away (the wind's strength is your size, their weight is theirs), arrows and fireballs are turned around and become yours, and lit candles, campfires and fire straight ahead go out. Walls stop the wind, and your pets, teammates and carried creatures are left alone. It costs air: full lungs last about three seconds, and once they are empty you have to let go and catch your breath. Not underwater; creative players never run out. Can be turned off (`general.breathGust`).
- **Trample damage setting** (#8): `general.trampleDamage` (0 to 10×, default 1) scales how hard trampling hits, and its cap. At 10× a giant's step flattens what it crushes; 0 turns the damage off.

### Fixed

- **Careful steps** (#9): sneaking or crawling never tramples anything. Crawling from vanilla, ParCool and Crawl all count.
- **Reach on Forge and NeoForge 1.20.1** (#11): reach didn't scale with size at all there, so giants couldn't hit or mine what was in front of them. Gulliver now uses Forge's reach attributes.
- **Better Combat on 1.20.1** (#11): weapon swings used each weapon's own range and ignored size; they now scale like reach. (Newer Better Combat versions already follow reach.)
- **Tinies in boats on 1.20.1** (#12): a tiny sat under the boat's hull and drowned. Riders of any size now sit in boats, on chickens, camels, horses and llamas where a normal rider would.

## 1.2.1

### Fixed

- **Stepping up on Forge and NeoForge 1.20.1** (#7): players and mobs could not step up anything, not even slabs, and step-height mods such as Accessible Step did nothing. Gulliver no longer replaces Forge's step height.
- Step height on 1.20.1 is scaled with size once instead of twice (tinies stepped too little, giants far too much).
- The first-person glider paper on 1.20.1 sits over a tiny's head again instead of sinking to eye level.

## 1.2.0

Gulliver now also runs on **Minecraft 26.3** (Fabric, NeoForge and Forge), next to 26.2, 26.1.x, 1.21.11, 1.21.10 and 1.20.1.

### New

- **Wider size range** (#3): the limits are now settings, from **1/32× up to 32×**. The default stays 0.125× to 8×; anything outside 1/8–8 is marked experimental in the settings screen. Servers tell clients their limits, so everyone sees the same sizes.
- **Ride small animals**: while tiny, right-click a chicken, rabbit, cat, ocelot, wolf or fox at least three times your size (empty hand, not sneaking) and steer it like a horse. Chickens flap down gently, rabbits leap. Tamed animals only carry their owner.
- **Smooth resizing**: bodies, the camera and the view distance grow and shrink smoothly over half a second, with a puff of particles and a rising or falling sound. Your new size shows on the action bar.
- **Loot scales with size**: a giant cow drops up to four times the beef, a tiny chicken may drop nothing. Experience follows the same rule. Bosses are left alone.
- **Wind charges and breezes** fling tinies much further; giants barely move.
- **Giant boss bars** (off by default): mobs at size 4 and up show their health to players nearby.
- **Giants and mounts**: a giant can't squeeze into a boat or minecart or onto a mount far smaller than itself.
- **Tooltips** on paper, lily pads, slimeballs, string, fishing rods and pointy items explain what they do while you're tiny.
- **Advancements**: a Gulliver tab with Ant's-Eye View, Head in the Clouds, Paper Plane, Up We Go, Tiny Rider, Pocket Friend and Watch Your Step, next to Drink Me and Eat Me.
- **Settings**: screen shake strength (it also follows vanilla's Damage Tilt and Distortion Effects), smooth resizing, size readout, loot scaling, giant boss bars and tiny mounts.
- **Hidden icon**: while an extra-tiny hides in a flower, sapling or flower pot, a closed-eye effect icon shows you that others can't see you (you still see yourself in third person). The Tiny and Huge effects got proper icons too.
- Easier to click small creatures: the crosshair is more forgiving with creatures far smaller than you.
- Giant name tags and shadows stay visible from further away; splashes scale with the body entering the water.

### Fixed

- **Slimeball climbing** (#6): half slabs can be climbed, standing next to a wall no longer makes you bounce, and walls topped with snow, carpet or slabs can be climbed all the way over. Soul sand and mud no longer count as walls.
- Holding a lead no longer turns every wall into a ladder for normal-size players (no more free fall-damage immunity); string and lead climbing only works while you push into the wall.
- Carried players are no longer kicked for flying on servers, can't be pushed through walls, and carried creatures are released properly when the carrier changes dimension.
- Giants no longer crack End portal frames, ice or lanterns; "brittle" floors are now a block tag (`gulliver:brittle`). Crater, growth and floor breaking respect claim mods, spawn protection and adventure mode, and drop with your real tool (Silk Touch, Fortune).
- Potions and dyes respect the configured size limits.
- Giants respawning or arriving through a portal are placed where they fit instead of suffocating.
- Arrows and tridents hit a giant's head and chest, not only its legs.
- Mobs keep their size when they convert (drowned, cured villagers, witches, frogs).
- Big giants no longer crush the floor and shake the ground on every normal jump; slime blocks, hay and honey cushion the landing.
- Rain no longer drowns tinies with Water Breathing, Conduit Power or Respiration, or undead and water mobs; slabs and beds no longer count as shelter.
- Screen shake no longer stops working after about half an hour on 1.20.1 and 1.21.10, and stops while paused or dead.
- The inventory player preview shows you at normal size; the third-person camera accounts for big mounts.
- Item pickup is only strict for tinies, not just below size 1.
- Gliding no longer stops when you fly over flowers or saplings.
- Sneak-jumps, sprint-jumps, horse and rabbit jumps and the giant walk animation scale properly.
- Bees really attack tinies, spiders need to see their prey, creepers still flee tiny cats, ender chests need a pointy item, and trampling spares your own pets and teammates.
- A broken `gulliver.json` is backed up instead of overwritten, invalid values are rejected, and `/gulliver reload` reports errors. `/gulliver get` is operator-only, as documented.
- 1.20.1: giant saturation, tiny eating sounds and animation, and crossbow charge times are fixed.
- Many smaller fixes; the full audit is in `docs/AUDIT-2026-10.md`.
