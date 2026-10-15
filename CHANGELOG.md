# Changelog

This project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.3] - 2026-10-15

### Added

- Shared Health, Attack, Defense, Movement Speed, Work Speed, Capacity, Stamina, Reach, and Detection Range stats with activity-earned upgrades.
- An owner management screen for naming, visible nameplates, stat upgrades, orders, pickaxe equipment, and bag access.
- Mining slimes crafted from a Friendly Slime and any pickaxe, preserving both companion traits and tool data.
- Chunk-bound mining with tool-tier checks, break progress, enchantment-aware drops, durability, stamina, and bag-full pausing.
- A mining helmet, lamp, and equipped-pickaxe rendering.
- Optional JEI recipes and information pages.
- Persistent stats, progression, equipment, and bag contents across saves and recovery.

## [0.1.2] - 2026-10-15

### Added

- Owners can recover a friendly slime into an item with Shift-right-click, including while holding another item.
- Recovery preserves the slime's saved state, including health, attributes, effects, name, owner, waiting order, identity, and custom data.
- Recovered slimes remain separate items and are consumed when released, including in Creative mode.
- Full inventories leave the slime in the world, and failed releases preserve the stored item.

### Changed

- Standardized Java source formatting with Google Java Format.

### Documentation

- Kept the bilingual READMEs focused on the project overview and moved gameplay and development details into bilingual documents under `docs`.

## [0.1.1] - 2026-10-15

### Added

- A Friendly Slime item crafted with eight slime blocks surrounding either soul sand or soul soil.
- Using the item on a block or water summons a persistent slime already tamed to the player.
- Friendly slimes hop after their owner, remain in Peaceful difficulty, and never attack or split on death.
- Owners can use an empty hand to toggle between following and waiting.
- Minecraft slime and slime-ball artwork, with English and Korean names and tooltips.

## [0.1.0] - 2026-10-15

### Added

- An empty Minecraft Forge 1.20.1 mod template with a minimal Slimic entry point.
- Gradle wrapper, development configurations, and a GitHub Actions build workflow based on Siliconic.
- English and Korean project documentation, the MIT license, and Forge credits.
