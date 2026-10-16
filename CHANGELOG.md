# Changelog

This project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.15] - 2026-10-16

### Changed

- Herald signals repeat every ten seconds while a worker remains blocked.
- Activity growth includes defense, escape movement, and ridden travel.
- Ground movement speed changes hop frequency while hop propulsion stays fixed.
- Companion summoning items share a Friendly Slime base with distinct framed profession crests.
- Winged mounts have paired animated side wings and stamina-powered Ctrl ground and flight sprint. Normal flight also consumes stamina; exhaustion produces a slow controlled landing before grounded recovery.

### Added

- Hostile mobs can target companions; ordinary companions flee after attacks. Creepers receive no additional targeting behavior.
- Fallen companions become Broken Slime Blocks, preserving their identity, stats, equipment, cargo, and orders. Their owner can revive them with a slimeball.

### Fixed

- Combat slimes face threats while shielding and maintain a usable blocking interval between attacks.

### Validation

- Added native regressions for all-specialization revival, payload-preserving mining and placement, blocked/foreign revival, Creative recovery, hostile targeting, escape, shield timing and cancellation, repeated signals, hop distance, and stamina costs.
- Client coverage includes shared summoning bases and distinct profession crests, the recovery block model, paired wings, ground and air sprint, and controlled descent after stamina depletion.

## [0.1.14] - 2026-10-16

### Changed

- All companions require 32 activity for the first stat point, with the existing 64-unit increase and 4,096-unit cap. Existing saved activity grants earned points and retains its remainder without repeated awards.
- The Creative tab groups companion items, storage, armor by material, and the tuner.

### Fixed

- Transport packs and straps clear both slime body surfaces, eliminating overlapping faces and flicker.
- Starting mining after following into another chunk establishes a quarry in the current chunk while same-chunk commands retain progress.
- Slowly advancing mining approaches no longer fail the travel timeout because of the previous half-block movement threshold.
- Changing the target block state during mining clears previous damage; the current block takes its own work interval and yields its own drops.

### Validation

- Added all-specialization growth and saved-activity coverage plus native regressions for relocated work commands, low-speed approaches, and block replacement. Client validation includes multiple pack views and the actual Creative ordering.

## [0.1.13] - 2026-10-16

### Added

- Saddle Slimes crafted from a Friendly Slime and saddle, preserving companion traits and the exact saddle.
- Owner-only riding, view-relative ground steering and jumping, and saddle/elytra equipment slots with direct application and Shift transfers.
- Free flight with usable elytra: view-directed steering, ascent, configurable descent, hovering, and native dismounting for Survival riders without rockets.
- Visible leather saddle, straps, stirrups, and vanilla-textured elytra wings with a mounted pose; dedicated 16px summon artwork, translations, Creative entry, and optional JEI guidance.

### Compatibility

- Network protocol 6 adds riding controls. Use compatible client and server builds. Existing companion save fields remain compatible; equipment and current vitals survive recovery without retaining transient flight gravity.

### Validation

- Added server coverage for specialization and release, ownership, equipment transfers, flight prerequisites, and mounted recovery, plus native client riding/flight checks with and without JEI.

## [0.1.12] - 2026-10-16

### Fixed

- Slow combat pursuit and cleaning returns no longer report unreachable routes solely because 400 travel ticks have elapsed. Stationary companions still report blocked work, and resumed movement clears the state before arrival.
- Combat pursuit and cleaning returns preserve valid paths during hops. Pursuit refreshes its route when the target moves, and each cleaning return starts with a fresh stall timer.
- Transport slimes recheck the current work radius before deposits and tool pickups, cancelling selected storage that becomes out of range after a Detection Range change.

### Validation

- Added server regressions for advancing and stationary combat/cleaning slimes, recovery of movement, moving combat targets, exact cleaning returns, and range changes during both storage operations.

## [0.1.11] - 2026-10-15

### Fixed

- Herald warning history retains every recorded worker episode across recovery. New warnings no longer evict older records and cause repeated signals in large groups.
- Combat slimes respect the server's PVP setting and their owner's player-damage permissions when player targets are enabled.
- Management and tuner screens preserve complete integer values over native container packets, including large attributes and multiplied detection ranges. Initial editor values wait for the complete snapshot.

### Compatibility

- Network protocol 5 requires compatible client and server builds because menu-data layouts have changed. Existing worlds and recovered items keep their save format.

### Validation

- Added tests for 200 saved warning records, warning insertion without eviction, PVP and team rules, native packet round-trips in both menus, and clearing high bits after values shrink.
- Client checks include an attribute value exceeding a single native data packet's range.

## [0.1.10] - 2026-10-15

### Added

- A shared work status separates orders, execution states, blocking reasons, and cargo/tool support requests. Transport and herald behavior consume the common contract.
- Moving with an owner and monitoring active workers provide growth opportunities for companions without collection or combat actions. Delivery growth rewards and following/monitoring intervals are configurable.
- Companion saves include `SlimeDataVersion`; unversioned data remains compatible, and stored companions from unsupported future schemas cannot be released or consumed.
- Management upgrade tooltips identify stats used after specialization.

### Changed

- Newly crafted and Creative mining items summon waiting companions. The owner explicitly starts the quarry with Mine. Recovered miners retain same-area orders; relocation to another chunk or dimension still resumes a new quarry.
- Work orders can start after waiting even when the owner is not loaded; the native sitting goal no longer blocks the assigned task.
- Stamina recovery scales at 2% of maximum per second, keeping the recovery period to 25% similar across upgrade levels.
- Completed cargo deliveries and supplied tools award four activity units by default to account for transport travel.
- Transport slimes share loaded-chunk candidate snapshots for up to 20 ticks and stagger idle searches. Requests, ownership, distance, locks, and transfer capacity are rechecked before use.

### Validation

- Added coverage for initial mining orders, common requests, proportional recovery, growth and paused timers, legacy/future data, cache expiration without chunk loading, and 32 concurrent carriers sharing storage.

## [0.1.9] - 2026-10-15

### Fixed

- Partial Shift-click unloading wakes full miners even when the original bag stack remains occupied.
- Combat target settings reject Shift-click merges into hidden bag slots.
- Open transport menus return to an existing page after Capacity is reduced.
- Slime menus retain their slot layout and close safely if the companion disappears during opening. Network protocol 4 requires matching client and server versions.

### Validation

- Added regression tests for partial unloading, hidden-slot transfers, capacity changes, missing entities, and stale menu actions.

## [0.1.8] - 2026-10-15

### Fixed

- Mining in another dimension establishes a new quarry even when its X/Z chunk coordinates match the previous world.
- Fixed work areas move to the arrival point after portal transfers and dimension-restored entity copies, while ordinary same-world reloads retain their saved origins.
- Transport travel timeouts measure stalled movement, allowing slow deliveries to continue beyond 400 elapsed ticks.
- Carriers preserve valid paths during hops and refresh paths when destinations move or a route ends.
- Transport action cooldowns survive world saves and recovery, preserving Work Speed intervals.

### Validation

- Added regression coverage for dimension changes, legacy saves, cancelled transfers, work-area restoration, slow and stalled deliveries, and persisted transfer cooldowns.
- Isolated cargo-transfer integration tests in a fully prepared work chunk, independent of surrounding natural terrain.

## [0.1.7] - 2026-10-15

### Changed

- Coarser 16×16 summon and tuner icons, with dedicated textures for all six Slime Armor materials.
- Dedicated Slime Chest face textures shared by the placed block and its block-model inventory item.
- A striped conical hat for herald slimes, with the nameplate raised above it.

### Fixed

- Quarry routes now cover long perimeter detours at default stats while restricting navigation reads and the slime footprint to the work chunk.
- Valid quarry paths survive hops, and tighter waypoint arrival prevents wide slimes from cutting into staircase corners.
- Mining approaches search valid visible standing positions together rather than repeatedly pathfinding toward individual positions or solid targets.
- Support repairs check sight and reach, report stalled routes, and retry after obstructions are cleared.
- Sealed bedrock floors complete correctly when their only breakable blocks are preserved staircase supports.
- Travel timeouts measure lack of movement so long routes can finish without being abandoned solely for taking time.

## [0.1.6] - 2026-10-15

### Added

- Combat slimes specialized with a sword, with sword, shield, and Slime Armor equipment slots, native melee damage and defenses, stamina, and growth.
- Independent hostile, neutral, passive, player, tamed-mob, and creeper target switches; defaults enable only hostile mobs other than creepers.
- Slime Armor crafted from a chestplate and a slime ball, preserving the original material, durability, enchantments, color, name, and attributes, with six standard material variants.
- Cleaning slimes specialized with rotten flesh, collecting dropped items within a fixed area with five times the shared Detection Range and returning after each pickup.
- Three-minute pickup retries that prefer another available item when the current target remains inaccessible.
- Persistent combat filters, equipment, cleaning origins and retry state; transport support for combat swords and full cleaning bags.
- Visible combat armor, swords, and shields, simultaneous pickaxe/shovel display, and shared held-tool poses with upright tool heads and inward grips.
- New summon icons, armor material models, target controls, JEI previews, and bilingual guides.

## [0.1.5] - 2026-10-15

### Added

- A Creative-only Slime Tuner with a separate screen for base stats, current health, stamina, and unused points.
- Herald slimes crafted with a Friendly Slime and any firework rocket, preserving its design and signaling stalled same-owner workers without consuming more rockets or dealing damage.
- Configurable herald delays, persistent warning state, and a white small-ball firework for Creative defaults.
- A dedicated Slimic creative tab, six distinct item textures, and dye colors for every companion type.
- Whole-chunk excavation with a persistent, two-block-wide perimeter staircase and repair using common building blocks from the bag.
- Separate pickaxe and shovel equipment slots, automatic bag-spare replacement, and native held-tool orientation.

### Changed

- Friendly Slime crafting now uses eight slime balls around soul sand or soul soil.
- Releasing a miner in another chunk starts a fresh quarry automatically; releasing in the same chunk preserves its progress and order.
- Mining recipe results equip the exact input pickaxe; Creative default items have empty equipment.
- Recovery preserves dye, tuning, firework designs, alert settings, blocked-work timers, tools, cargo, and quarry progress.

## [0.1.4] - 2026-10-15

### Added

- Transport slimes crafted from a Friendly Slime and a regular chest, preserving saved companion traits.
- A transport bag with exactly twice the capacity at every shared Capacity level, supporting 18–54 slots without an equipment slot.
- Slime Chests crafted from eight wooden planks and a slime ball, with 27 persistent slots, standard inventory access, and automation support.
- Automatic unloading of same-owner workers stopped by full bags and delivery to nearby Slime Chests.
- Replacement-tool delivery from Slime Chests to workers stopped by missing or broken tools.
- Cargo retention when destinations are full or unavailable, stamina-based work, and activity earned on completed deliveries.
- A transport backpack, paged bag controls, and JEI crafting and information support.

### Changed

- Shared bag persistence and recovery across worker specializations while preserving existing mining-slime saves.

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
