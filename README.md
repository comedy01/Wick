# Wick

Dynamic lights for Minecraft. Hold a torch, lantern or lava bucket and the world lights up around you as you walk. A
dropped torch keeps shining on the ground, burning mobs light their surroundings, and blazes glow in the dark.

Wick is a small client-side mod for Minecraft on Fabric, NeoForge and Forge, compatible with Mod Menu on Fabric.

Supported versions: Fabric 1.18.2 and 1.19 to 26.3, NeoForge 1.20.1 and 1.21 to 26.3, Forge 1.12.2, 1.16.5, 1.18.2,
1.19.2 and 1.20.1. Works with Sodium, Embeddium and Iris, and with OptiFine on 1.12.2.

## Features

- **Held and worn items** - torches, lanterns, glowstone, sea lanterns, lava buckets, blaze rods and any other glowing
  item, in either hand or on your head (a jack o'lantern helmet works). Endermen carrying a glowing block glow too.
- **Accessory slots** - a lantern on your belt or a glowing charm in a [Curios](https://modrinth.com/mod/curios) or
  [Trinkets](https://modrinth.com/mod/trinkets-updated) slot (or [Baubles](https://www.curseforge.com/minecraft/mc-mods/baubles)
  on 1.12.2) shines like a held one. Neither mod is required.
- **Other players and mobs** - a zombie carrying a torch lights its way too. Turn this off to keep only your own light.
- **Dropped and displayed items** - a torch on the ground, in an item frame or on an item or block display keeps
  glowing.
- **Burning entities** - anything on fire lights its surroundings, flaming arrows included.
- **Glowing mobs and things** - blazes, magma cubes, glow squid, fireballs, wither skulls, spectral arrows, lit TNT,
  lightning, end crystals, furnace minecarts with fuel, falling glowing blocks, and creepers about to explode. Glowing
  mobs from other mods light up too.
- **Works with other mods' lights** - glowing blocks from any mod light up when held or dropped, and Wick reads
  LambDynamicLights' resource pack light files (`assets/<namespace>/dynamiclights/item` and `.../entity`), so items and
  mobs a mod or resource pack already set up for LambDynamicLights glow with Wick as well.
- **Water puts out flames** - torches and campfires go dark underwater; lanterns and glowstone keep shining.
- **Enchanted glow** (optional, off by default) - enchanted weapons, tools, armor and books give off a faint glow, and
  so does anything else with the enchantment shimmer, modded gear included.
- **Walls block light** - light stops at solid blocks, so your torch does not shine through into the next room or the
  cave below, but it still wraps around corners and through doorways, dimmer for the longer way. Glass, leaves, slabs
  and doors let it through. Can be turned off.
- **Smooth light** - light fades evenly in a circle instead of in blocky steps.
- **Your own light levels** - set any item's brightness in `config/wick.json`, for example
  `"items": { "minecraft:stick": 12 }` (`"stick"` works too).
- **Built to stay fast** - only the chunk sections a light can reach are rebuilt, and only when a light moves to another
  block. Update speed, range and a cap on the number of lights are all adjustable.
- **Client-side only** - nothing to install on a server.

## Install

**Fabric**

1. Install [Fabric Loader](https://fabricmc.net/use/) for your version of Minecraft.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and the Fabric Wick jar for your version in your `mods` folder.
3. Start the game.

Optional: add [Mod Menu](https://modrinth.com/mod/modmenu) to get a settings screen (*Mods > Wick > Configure*).

**NeoForge**

1. Install NeoForge for your version of Minecraft.
2. Put the NeoForge Wick jar in your `mods` folder (on 1.20.1, the Forge 1.20.1 jar).
3. Start the game. Settings are under *Mods > Wick > Config*.

**Forge (1.12.2, 1.16.5, 1.18.2, 1.19.2, 1.20.1)**

1. Install Forge for your version of Minecraft.
2. Put the Forge Wick jar in your `mods` folder.
3. Start the game. Settings are under *Mods > Wick > Config*.

## Building

`./gradlew build -Pminecraft_version=26.3` builds the Fabric jar; add `-Ploader=neoforge` or `-Ploader=forge` for the other loaders (version configs in `versions/`).
Forge 1.16.5 builds from `legacy-forge/`: `cd legacy-forge && ./gradlew build`. Forge 1.12.2 builds from `retro-forge/`:
`cd retro-forge && ./gradlew build`.
`./gradlew runSelftest` launches the game and runs the in-world checks.
