# glungus

so this is the superpowers mod idk

you get like 8 powers and you just press stuff on the keypad to use them lol

## what it does

- ice, air, fire, water, ghost, lightning, nature, or god powers
- each one has its own handler and does dumb op stuff
- single-player only btw, doesnt run on dedicated servers lol i tried

## powers n stuff

- **ice** - freeze stuff
- **air** - tornado thing that yeets mobs, uses that tornado math from GlungFastMath lol
- **fire** - burn lol
- **water** - idk water stuff
- **ghost** - fly and be spooky
- **lightning** - zap, big lightning entity goes brrr
- **nature** - vines and earthquake jolt thing
- **god** - yeah major feature update for godmode lol, has like smite / laser / nova / giant / bless / banish / annihilate / telekinesis / levitate / noclip n whatever, i just kept adding stuff 1.1.0 style

## how to run

needs:
- minecraft 1.21.11
- fabric loader 0.19.0
- fabric api 0.141.6+1.21.11
- java 21

just do

```
./gradlew build
```

and grab the jar from `build/libs/` lol

if gradle complains just try again one more try lol

## controls

keypad to use powers, hud shows whats ready, check the json in `src/main/resources/assets/glungus/hud/` if you wanna tweak hud stuff

## math lib

1.1.2 math library speed boost lol
- FastTrig table for sin/cos (16384 entries)
- GlungFastMath does all the vector/quat/matrix stuff
- FastNoise for perlin/simplex/worley
- easing curves for hud animations n tornado growth n shake falloff
- FastRandom splitmix64/xorshift stuff

tweaked it for performance but idk its still kinda jank

## dev notes

- mod id is `glungus`
- entrypoints in `fabric.mod.json`
- mixins in `glungus.mixins.json` / `glungus.client.mixins.json`
- version is in `gradle.properties` dont forget to update it lol (forgot to update gradle.prop some commits back lol)
- `Glungus.java` registers payloads n powers on init

## license

all rights reserved lol

## credits

made by xiaojian999

i just fixed too many bugs earlier in development so if its broken idk man open an issue lol

## do not use glungus with these mods
1. WorldEdit GUI - **BANNED** - https://modrinth.com/mod/wegui
   - reason: depends on litematica. that mod's banned
   - origin: china
   - **if detected, glungus will instantly crash the game with "listen to the readme, don't use that one chinese worldedit mod"**
2. Litematica - **BANNED** - https://modrinth.com/mod/litematica
   - reason: blocks pressing the KP- button, holding it works tho
   - **if detected, glungus will instantly crash the game with "listen to the readme, don't use litematica"**
3. other superpower mods/datapacks (e.g. svm powers)
   - reason: idk about interop

## wip stuff
- [ ] shadow powerset (will be added in 2.0)
