# Changelog

This project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.25] - 2026-10-17

### Changed

- Saddle flight uses diminishing Movement Speed gains and explicit cruise and boost limits. Ctrl retains its faster movement and higher stamina cost; exhaustion retains controlled descent.
- Saddle Slimes accept Slime Armor alongside the saddle and elytra. Armor supplies native defense, enchantments and durability; legacy chest-slot elytra move intact to the independent wing slot.
- Cleaning Slimes wear blue work caps, Combat Slimes wear metal bands with red crests, Saddle Slimes have bridles, and Soggy Slimes carry slime droplets even without optional equipment.
- Friendly summon items show a plain gold plate; Soggy summon crests use Slime Slag. Every profession retains the shared summon base.
- Network protocol is 13; saved-data schema remains 1 with an explicit saddle equipment layout marker.

### Validation

- All 424 native regressions pass both with and without JEI, including bounded flight, armor damage, equipment migration, exact slot transfers and companion cooperation.
- Korean with JEI and English without JEI pass all seven client stages, including actual Survival riding and simultaneous armor, saddle and wing rendering.
- A world saved by 0.1.24 loads with all thirteen professions retaining identities, owners, health, all nine stats, orders, names, colors, cargo, reserves and equipment.
- Twenty-nine independent interaction scenarios are repeatedly validated for over two real hours. A separate continuous movement run completes 288 logistics and treatment cycles in 7,212 seconds, preserving cargo and all three mount equipment items through stamina recovery and partial unloading.

## [0.1.24] - 2026-10-17

### Fixed

- Work goals recheck the current order before every action, preventing an already-running goal from collecting cargo, healing, changing terrain, or signaling after Follow or Wait.
- Work navigation and sight checks stay within loaded terrain while allowing routes around obstacles. Farming and logging use the same boundary checks; ordinary following retains native navigation. Route caches avoid repeated temporary coordinate objects.
- Adventure-mode revival retains the actual held slimeball's permissions, interaction hand, and clicked face.
- Stored identities are reserved during restoration, preventing spawn callbacks from releasing the same companion in another dimension before the original spawn finishes. Cancellation releases the reservation for a safe retry.
- Healing supply accepts at most one stack and clears fulfilled requests immediately. Full bags show a distinct unloading status.
- Warehouses skip supplies that cannot fit, allowing compatible cargo collection to continue.
- Cancelled treatment, protected revival, and repeated failed approaches retain continuous blocked time for herald monitoring. Successful treatment or absence of eligible patients clears the obstruction.
- Command changes inside healing and protection events no longer invalidate an in-progress treatment target. Completed healing is charged once; interrupted revival preserves the block and its supplies.
- Mining, farming, logging and collection revalidate the current command and owner after protection or pickup callbacks. Interrupted placements restore the original block without consuming materials.
- Herald scans stop additional signals when a signal callback changes the command. Soggy trails roll back when their placement callback switches to Wait, in both Follow and Roam.
- Guardian Angel flight routes, direct-flight checks and beam sight treat unavailable chunks as blocked terrain without loading them.
- Guardian totem protection revalidates ownership, life, distance and the exact reserve after the totem event. Interrupted or recursive protection cannot consume unrelated totems or provide an uncharged rescue.
- Tuned Saddle Slime flight supplies the native dedicated-server movement validator with the expected velocity, preventing valid fast flight from being rolled back. Prediction is cleared before physics, dismounting and wing removal, and is never saved as momentum.
- A displaced miner clears its retained route when its original quarry chunk unloads, so old path nodes cannot load that chunk again. Completed quarry progress, equipment, cargo and the work order remain intact.
- Accepted placement events can adjust crops, soil or sapling growth without bypassing seed, reserved-sapling or tool costs. Converted quarry barriers consume cobblestone when they seal the inlet; ineffective conversions restore the original block without costs.
- Mangrove logging recognizes raised trunks, central root columns and natural gaps, preserves outer roots, and restores propagules with native age and source-water waterlogging. Saved work distinguishes planned root blocks from logs.
- Surface workers use actual collision-floor heights, allowing ordinary logging and mangrove work on mud. Stable stat persistence keys are initialized once to reduce repeated string allocation.

### Validation

- Independent native cases cover unloaded destinations and gaps, wide wall detours, revival permissions, cancelled treatment, concurrent healing, final-slot warehouse recovery, and competing logistics.
- Forty companions preserve all 4,608 cargo items and their source tags; twelve carriers share one supply request without duplicate transfers.
- All 402 native regressions pass. Separate 90-minute runs complete 1,038 logistics cycles with live save/reload and 480 real-movement logistics cycles; Korean with JEI and English without JEI pass the client checks.
- Native sapling generation covers all eight supported species, including two-by-two spruce and jungle trees; low-canopy spruce resumes after a Reach increase.
- Network protocol is 12; saved-data schema remains 1.

## [0.1.23] - 2026-10-16

### Added

- Healing Slimes craft from a Friendly Slime and golden carrot. Explicit Search heals same-owner companions and revives Broken Slime Blocks using bag slimeballs and stamina; transport can replenish their supplies.
- Warehouse Slimes craft from a Transport Slime and eight chests, preserving companion data and cargo. Their capacity is always four times the equivalent transport capacity; Store collects cargo without automatically emptying into chests.
- Shared-base summon crests, visible healing bands and larger warehouse packs, bilingual management messages and guides, and JEI recipes for both specializations.

### Changed

- Transport prefers same-owner warehouses over Slime Chests for deposits and supplies, skips full storage, and retries alternative destinations. Full warehouses pause collection without requesting their own unloading.
- New specializations initially wait. Saved orders and the one-health revival rule remain consistent. Network protocol is 11; saved-data schema remains 1.

### Validation

- Native regressions cover treatment costs, healing cancellation, protected and obstructed revival, duplicate identities, initial orders, capacity scaling, paged recovery, storage ownership changes, partial deposits, alternative destinations, and warehouse supply delivery.
- Korean and English client checks cover both optional-JEI configurations, shared profession crests, visible equipment, synchronized orders and the final warehouse bag page.

## [0.1.22] - 2026-10-16

### Fixed

- Ineffective mining tools no longer receive the Work Speed breaking bonus. Missing or tier-inadequate tools cannot bypass equipment requirements.
- Mining and surface workers share native block-breaking progress. Tool changes, enchantment changes, interruptions, and block replacement reset partial damage and crack overlays.
- Logging respects log hardness and the equipped axe's speed and enchantments before its action cooldown. Farming retains native instant crop harvesting and requires its hoe.

### Validation

- Native regressions measure break-attempt ticks for effective and ineffective tools, maximum Work Speed, tool downgrades, enchantment removal, missing equipment, and logging/farming resumption.

## [0.1.21] - 2026-10-16

### Added

- Mining Slimes reinforce exposed side fluid inlets and large underground openings with their own cobblestone, preserving the quarry staircase and unfinished wall jobs across recovery.
- Missing wall material pauses mining and requests transport delivery of up to one stack; partial stock and available bag space bound the transfer.
- Soggy Slimes support an explicit Roam order, independent of the owner's presence, with a saved local origin, loaded-terrain navigation, and normal trail production and stamina recovery.

### Changed

- Revival from a Broken Slime Block starts every specialization at 1 health while preserving stored traits, equipment, cargo, and stamina.
- Endermen retain native targeting. Combat Slimes classify an Enderman targeting a living companion as Hostile and return it to Neutral when it disengages.
- Updated bilingual management messages, JEI guidance, and gameplay/development guides for wall reinforcement and free roaming. Network protocol is 10; saved-data schema remains 1.

### Validation

- Native server regressions cover wall geometry, protection and costs, recovery, material delivery, roaming, command permissions, stamina recovery, Enderman target transitions, and all eleven specializations revived at one health.

## [0.1.20] - 2026-10-16

### Changed

- Standardized Korean stat names, current stamina, activity, equipment, and work-order terminology across screens and guides; aligned vanilla ingredient names with Minecraft translations.
- Revised English and Korean tooltips, JEI descriptions, status messages, and help text for consistent tone and current behavior. Combat filter states are localized.
- Simplified the bilingual project overviews, corrected misplaced gameplay explanations, and updated development terminology and protocol references.

### Fixed

- Mining Slimes drain exposed source corners in horizontal patches of up to 3×3 cells. Active quarry layers suppress water-source regeneration, preventing repeated removal of the same infinite-water source. Each cell retains range, permission, bucket, and stamina checks.

- Mining paths accept submerged waypoints at the water surface, and swimming control can change heading and move against currents.

### Validation

- Native fluid regressions cover renewable pools, batch limits and costs, protected cells, recovery, and neighboring chunks. Client checks cover localized screens, help panels, and optional JEI integration.

## [0.1.19] - 2026-10-16

### Added

- All work specializations start in Wait, including Creative defaults and new specializations; saved explicit orders survive recovery.
- Soggy Slimes specialize from a Friendly Slime and slime ball, spend stamina on thin slime trails, and retain their traits through recovery. Trails break faster with shovels and drop one Slime Slag; four slag craft into a slime ball.
- Mining Slimes remove water and lava while an empty bucket is present in their own bag. Buckets remain unchanged. Missing buckets pause mining and request transport delivery; full bags can request unloading before supply.

### Fixed

- Guardian Angel Slimes retaliate after taking damage themselves, including absorbed damage and when their owner is unavailable. Untamed guardians safely handle owner-dependent target checks. Environmental lethal damage follows the same nearby guardian-totem protection as attacks.
- Ground slimes retain bounded hop propulsion throughout the native jumping state, preventing high Movement Speed from launching them across one-block steps.

### Validation

- Native checks cover slime trails, slag drops and recipes, stored identity, ownership and world-edit cancellation, guardian self-defense and environmental totems, jumping-state propulsion, fluid removal, retained buckets, and transport resupply.
- Client checks cover the new summoning crest, slag sprite, trail model, management text, JEI, equipment and riding.

## [0.1.18] - 2026-10-16

### Fixed

- Canceled or zero-value healing keeps the slime ball and suppresses healing effects.
- Switching to Spectator invalidates an already open companion management screen.
- Lumberjacks skip temporarily unreachable or protected dropped materials so reachable tree work can continue, and clear obsolete sapling requests when the tree disappears or cargo changes.
- Guardian beams remain visible when the beam crosses the view but the slime body is outside it.

### Validation

- Twenty independent boundary checks cover saved data, summoning, recipes, interactions, permissions, tuning, inventories, growth, mining, search limits, farming, forestry, logistics, cleaning, heralds, combat, guardians, riding, and storage.
- Full native regression and client checks cover profession behavior, equipment, animated storage, optional JEI, resource consistency, and release packaging.

## [0.1.17] - 2026-10-16

### Added

- Guardian Angel Slimes fly beside their owners, retaliate with beams, store up to 64 totems and protect against lethal damage. Maximum health stays ten times the common value.
- A craftable Survival Slime Tuner opens companion management. The former editor is now the Creative Slime Tuner throughout code, registries and UI.
- Slime balls heal four health; water buckets remove dye. Heralds support manual signals and direct rocket-design replacement.
- Slime Chests animate their lids with native opening and closing sounds.

### Fixed

- Lumberjacks refresh tree searches when supplied saplings change, skip temporarily unsupplied species, and show the exact sapling type and stump count. Farming and lumberjack status messages are distinct.
- Surface workers choose reachable standing positions and follow progressive routes across their doubled work range. Miners avoid harvesting wood and revalidate close approach paths. Escape movement retains valid routes through hops and only replaces them with reachable grounded paths.
- Saddle Slimes accept ordinary mounting interactions while items are held; management uses the Survival tuner.

### Validation

- Native regression coverage includes companion care, management permissions, stored firework designs, guardian crafting and reserve transfers, health scaling, flight, retaliation, lethal protection, sapling resupply, mixed tree species, mining around wood, and chest lids.
- Client checks cover the guardian halo and wings, reserve synchronization and clicks, animated chests, worker menus and equipment, profession crests, and optional JEI.

## [0.1.16] - 2026-10-16

### Added

- Farming Slimes equip the exact crafting hoe, wear straw hats, register farmland on the Farm order, harvest and replant crops, restore registered soil, and avoid trampling farmland.
- Lumberjack Slimes equip the exact crafting axe, wear leather-style caps, fell natural trees around their release origin, and reserve matching saplings to replant the original stumps.
- Both workers have twice the common Detection Range, ordinary cargo bags, work growth, stamina costs, transport support requests, distinct summoning crests, and optional JEI recipes.

### Validation

- Native tests cover crop and tree work, exact equipment, plot snapshots, replanting, soil exclusion, protections, full bags, tool requests, saved state, and all-nine companion revival.
- Client validation covers the new hats, held equipment, management screens, recipe families and profession crests with and without JEI.

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
