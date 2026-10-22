# Civilizations Expansion

A seven-civilization expansion framework for **Minecraft 1.20.1 / Forge 47.4.x**, planned to work alongside Reign of the Nether and Valarian Conquest.

## Planned civilizations

- Valarians
- Bohemians
- Caladrians
- Visigothians
- Orcs
- Dwarves
- Jade Warriors

## Current development status

**Early framework / not yet a playable faction expansion.**

The initial source milestone adds the Forge mod entry point and a central catalog of all seven civilization IDs. It does not yet add faction units, buildings, builders, diplomacy, trade, war AI, or autonomous kingdoms. Those systems will be implemented in stages and tested before being described as playable.

## Build

Requirements:
- Java 17
- Gradle 8.8
- Minecraft Forge 1.20.1 development dependencies (downloaded by ForgeGradle)

Run:

```sh
gradle build
```

The packaged JAR will be under `build/libs/`.

GitHub Actions attempts this build on pushes and pull requests. Check the Actions tab for the actual result; a successful workflow is required before treating the artifact as build-verified.

## Compatibility

This first milestone deliberately does not compile against or bundle code/assets from Reign of the Nether or Valarian Conquest. Compatibility hooks and integration should be added only after their APIs and licenses have been checked. Keep both original mods installed when testing their existing compatibility, but do not treat this early framework as implementing integration yet.

## Roadmap

1. Reliable Forge loading and automated build.
2. Stable definitions for all seven civilizations.
3. Optional compatibility layer for the two target mods.
4. Faction-specific builder rules, units, and structures.
5. Diplomacy, trade, peace, war, and autonomous kingdom behavior.

## Credits and permissions

Reign of the Nether and Valarian Conquest are separate projects. They are not included in this repository. Please consult their official project pages and licenses before redistributing, adapting, or using their code or assets. This project is an independent fan-made add-on and is not endorsed by the original authors unless they explicitly say otherwise.
