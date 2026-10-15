# Development Guide

English | [한국어](DEVELOPMENT.kr.md) · [Project overview](../README.md)

## Build environment

Use JDK 17. The project targets Minecraft 1.20.1 and Forge 47.4.10, using the Gradle 8.8 wrapper.

```bash
./gradlew build
```

On Windows, use `.\gradlew.bat` in place of `./gradlew`.

The runnable artifact is `build/libs/slimic-1.20.1-<version>.jar`. The `-sources.jar` artifact is for development.

## Development tasks

| Command | Purpose |
| --- | --- |
| `./gradlew runClient` | Launch the development client. |
| `./gradlew runServer` | Launch a local development server. |
| `./gradlew runGameTestServer` | Run the dedicated Forge GameTest server. |
| `./gradlew runData` | Run configured data generators. |

GameTests run in `build/game-test-server`, separate from the normal development world under `run`. Their Java sources and fixtures live in `src/gameTest` and are excluded from release artifacts. The build compiles the GameTests; `runGameTestServer` executes them.

## Source layout

- `src/main/java`: mod entry point, registries, slime behavior, item interactions, and client rendering.
- `src/main/resources`: metadata, textures, translations, recipes, and loot tables.
- `src/generated/resources`: generated production resources when needed.
- `src/gameTest`: development-only test mod, tests, and structures.
- `docs`: detailed gameplay and development guides in English and Korean.

Keep each README focused on the project overview and links. Maintain detailed guides as English/Korean pairs.

Package only runtime assets used by the mod in `src/main/resources`; keep reference material and verification artifacts outside the release resources.

## Versions and changes

Early development releases use the `0.1.x` series, with a patch increment for each release. Update `mod_version` in `gradle.properties` and [CHANGELOG.md](../CHANGELOG.md) together; the build verifies the changelog entry.

Keep the development test-mod version in sync when changing the release version.

## Slime recovery data

A recovered item stores the slime's complete serialized state in `SlimeData`, including Forge persistent data and capabilities. New persistent slime traits should use the entity's standard save/load hooks so they automatically survive recovery.

Restoration loads the saved state and retains the original UUID and owner. It replaces placement, motion, and fall distance for the new destination and does not restore leash or passenger relationships. A loaded entity with the same UUID prevents another release of that stored identity.

Recovered items have a stack limit of one. Their release consumes the stored item even through Creative mode's normal item-count restoration. Newly crafted items retain the usual Creative reuse behavior.

## Validation

Format all Java sources with Google Java Format before completing a change.

Run `build` and `runGameTestServer` after gameplay changes. Coverage includes crafting, normal spawning, recovery and release, health and other traits, ownership, full inventories, blocked placement, Creative use, persistence, following, and death behavior.

Tests use a structure loaded one block above its marker, so the fixture floor is at relative Y=1. Keep world fixtures separate from production assets.

## Stats, mining, and integrations

Shared stat levels are synchronized as entity data. Server-side menu checks enforce ownership, distance, available points, and upgrade limits. Rename packets apply only to the sender's active Slime menu. Health upgrades change the maximum without healing the companion.

Mining state, bag contents, stamina, and activity progress use the standard entity save/load hooks. The equipped pickaxe uses the native main-hand equipment slot, keeping client rendering and item damage synchronized. Mining progress is bounded by one completed block per tick, searches process a limited number of nearby positions, and no neighbouring chunk is loaded for target discovery.

The crafting serializer copies the Friendly Slime's stored traits and the exact pickaxe stack into the result. JEI is a compile-only API dependency with optional development runtime support. Use `./gradlew runClient -PenableJei=false` or `./gradlew runGameTestServer -PenableJei=false` to validate operation without JEI. Common game logic does not reference JEI or client classes.
