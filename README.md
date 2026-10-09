# Minecraft Beta Multiverse Indev Terrain Generator

A Project Poseidon/Bukkit Beta 1.7.3 generator that runs Minecraft Indev's
2010-02-23 terrain generator and exposes all of its presets to Multiverse.
The companion `IndevMobRules` plugin reproduces that version's finite-level
mob spawning and prevents Beta climate and Nether-provider rules from changing
the result.

## Features

- Original Indev inland, island, floating, and flat terrain algorithms
- Original normal, hell, paradise, and woods themes
- Configurable finite level dimensions
- Original block metadata, including starter-house wall torch orientation
- Native Beta skylight and block-light initialization after chunk population
- Original surrounding ground/fluid planes represented as blocks for 2,048
  blocks beyond each finite edge
- True void beyond floating levels
- Indev mob selection, grouped attempts, light checks, caps, and player distance
- Nether PigZombie and Monster replacement suppression in Indev worlds
- Ice and snow formation suppression so Beta biomes cannot alter Indev climates
- Explicit isolation from `SkylandsOverworldSpawns`

## Requirements

- Project Poseidon / CraftBukkit for Minecraft Beta 1.7.3
- Multiverse-Core compatible with that Bukkit build
- Java 8 or newer for building
- A legally obtained `in-20100223.jar`

The original Minecraft jar is not distributed by this repository or its
releases.

## Installation

1. Put `IndevWorldGen.jar` and `IndevMobRules.jar` in `plugins/`.
2. Start and stop the server once, or create `plugins/IndevWorldGen/` manually.
3. Put `IndevGeneratorBridge.jar` and `in-20100223.jar` in
   `plugins/IndevWorldGen/`.
4. Create or import worlds with Multiverse.

Examples:

```text
/mv create indevnormal normal -g IndevWorldGen:inland,normal
/mv create indevhell nether -g IndevWorldGen:island,hell
/mv create indevparadise normal -g IndevWorldGen:island,paradise
/mv create indevfloating normal -g IndevWorldGen:floating,normal
/mv create indevhellfloating nether -g IndevWorldGen:floating,hell
/mv create indevforest normal -g IndevWorldGen:flat,woods
```

Generator syntax:

```text
IndevWorldGen:<type>,<theme>[,<width>,<height>,<length>]
```

Valid types are `inland`, `island`, `floating`, and `flat`. `classic` is an
alias for `inland`. Valid themes are `normal`, `hell`, `paradise`, and `woods`.
`forest` is an alias for `woods`. The default dimensions are `256,64,256`.

Use a Nether environment for hell themes so the client renders the red fog and
dark sky. Use Skylands for normal floating worlds. Terrain still comes entirely
from Indev; the Bukkit environment controls client presentation.

## Mob Rules

For each loaded world whose name begins with `indev`, the companion plugin:

- replaces provider-driven natural spawning with the 2010-02-23 finite-level
  loop;
- selects Skeleton, Creeper, Spider, or Zombie, with the original empty hostile
  selection, and Pig or Sheep for passives;
- uses the original four passes, two clusters, three attempts, horizontal
  random walk, 32-block player exclusion, and randomized hostile light test;
- uses the original default 256x64x256 caps: 160 hostile and 16 passive mobs;
- counts multiplayer proximity against every player in that world;
- blocks PigZombie and generic Monster spawns, including replacements attempted
  by Nether-provider plugins;
- prevents ice and snow from being formed by Beta biome ticks.

`SkylandsOverworldSpawns` must exclude world names beginning with `indev`. The
version used at Vio's Arcade has this guard in its shared world predicate, so
neither its event listener nor its periodic task can add mobs to Indev worlds.

## Borders

The original Indev renderer draws its surrounding ground and liquid horizon for
2,048 blocks beyond the finite level. Non-floating presets reproduce those
planes blockwise and generate them on demand as chunks load. Floating presets
remain void outside and below the generated level. Beyond the 2,048-block
horizon, all presets generate void.

## Building

```powershell
.\build.ps1 -ServerJar 'C:\path\to\server.jar'
```

Artifacts are written to `dist/`. The bridge has no Bukkit dependency. The two
plugins compile against the supplied Poseidon server jar.

## Regeneration

Generator caches live in `plugins/IndevWorldGen/cache`. Back up a world before
regenerating it. To regenerate, stop the server, archive the world directory and
its matching cache file, then restart. If Dynmap is installed, archive its tile
directory for that world as well so stale tiles are not reused.
