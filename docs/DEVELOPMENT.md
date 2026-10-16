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

Menu opening data includes the companion type so a missing client entity can retain the exact slot layout until the screen closes. Network protocol 8 requires compatible client and server builds. Hidden bag pages reject transfers, Capacity changes clamp the current page, and in-place slot mutations notify the bag so partially unloaded miners resume.

Mining state, bag contents, stamina, and activity progress use the standard entity save/load hooks. The equipped pickaxe and shovel use the native main-hand and off-hand equipment slots, keeping client rendering and item damage synchronized. Mining progress is bounded by one completed block per tick, searches process a limited number of chunk positions, and no neighbouring chunk is loaded for target discovery.

The crafting serializer copies the Friendly Slime's stored traits and the exact pickaxe stack into the result. JEI is a compile-only API dependency with optional development runtime support. Use `./gradlew runClient -PenableJei=false` or `./gradlew runGameTestServer -PenableJei=false` to validate operation without JEI. Common game logic does not reference JEI or client classes.

## Transport and storage

`BagSlime` shares capacity-aware storage, collapse preservation, save/load, and recovery between worker types. Mining retains the `MiningBag` NBT key for existing saves; transport uses `TransportBag` with up to 54 slots. Transport capacity multiplies the common base and every upgrade by two. Its menu has no equipment slot and checks the active page on the server.

Transport tasks query loaded chunks for Slime Chest block entities and same-owner workers inside the release-centered detection area. Transfers simulate available capacity before moving items on the server thread. Work requests are revalidated before transfer so concurrent carriers cannot duplicate tools or replace a tool already supplied. Travel targets are transient; cargo and orders are persistent, so interrupted jobs can be selected again after load or release.

Slime Chests use a standard 27-slot container menu and Forge item capability, with normal inventory persistence, locks, comparator updates, and separate block/content drops. Regular chests are excluded from logistics.

Validation covers all Capacity levels, both bag pages, ownership, full and partial destinations, concurrent tool supply, tool data and breakage, mining resumption, chest persistence/automation/drops, and transport recovery. Client checks cover both specializations, page synchronization, chest menus, model resources, and optional JEI recipes.

## Quarry, tuning, and herald persistence

Mining uses native main-hand pickaxe and off-hand shovel equipment. `MiningQuarry` retains the chunk's entry elevation and current layer; same-chunk release resumes it, while another chunk creates a new plan and resumes work. The perimeter plan reserves two-wide full-block treads with flat corners. Repair consumes items from `slimic:quarry_supports` and honors placement events. Target scans are bounded to 256 positions per layer and a limited number of empty layers per update.

Dye IDs and custom work stats use synchronized entity data. Native attribute tuning edits base values and preserves modifiers. The creative-only tuner validates the active menu, held tool, dimension, distance, finite values, ranges, and occupied bag slots on the server before applying any changes.

Herald recipes store the exact rocket stack. Signals are vanilla firework entities marked for damage suppression. Workers save their continuous blocked duration and episode identifier; heralds save warning history and cooldowns so recovery cannot repeat an existing warning. Server configuration supplies the default delay.

Client rendering applies a brightness-preserving dye shader to the original slime texture while leaving equipment unchanged. Optional JEI previews include every specialization. Validate client shader loading, translated screens, tuner packets, creative defaults, two tool slots, and JEI enabled/disabled alongside dedicated-server tests.

## Combat and cleaning

`CombatSlime` keeps sword, shield, and armor in native equipment slots and shares the normal capacity-aware bag. Its synchronized target mask has six independent bits; owner menu checks protect all changes, and current targets are revalidated after a filter or order change. Native melee, armor, shield timing, enchantments, durability, and Forge events provide combat behavior. Serialized settings and cooldowns survive recovery.

Each standard armor material has its own registered item, preserving native repairs and netherite fire resistance. `SlimeArmorItem` stores an original chestplate in `SlimeArmorBase`. Outer item damage, enchantments, display, and attribute data remain authoritative after conversion. Material attributes and durability are delegated to that chestplate. Recipes accept registered chestplates and swords, and JEI builds corresponding previews dynamically.

`CleaningSlime` multiplies effective Detection Range by five while leaving the common base and tuner values unchanged. Target selection is limited to loaded entities around a fixed release origin. Transfers simulate bag space, preserve partial stacks, honor pickup delays/ownership/events, and run on the server thread. A 3,600-tick attempt window selects an alternative or retries; saved timers pause in item storage. Working goals reserve movement while idle to prevent wandering. Transport uses the shared full-bag and replacement-tool interfaces.

Validation covers target defaults and toggles, ownership, native combat and defenses, material/NBT conversion, cargo and retry persistence, collection bounds, idle behavior, actual pickups and returns, full bags, tool delivery, optional JEI, and translated client controls. Combat tests run after the Peaceful-difficulty test batch.

## Visual asset conventions

Summoning items share the `slime_summon_base` model and Friendly Slime texture. Child models select a profession crest from native item artwork; the common model places it in a small frame over the base. Other new items include a dedicated Minecraft-style 16×16 pixel texture with a limited palette and clear silhouette. Use nearest-neighbor scaling when preparing pixel art. Leather armor keeps its tintable base separate from the untinted slime badge. Block items inherit their textured block model so inventory and placed appearances stay consistent. Companion accessories use separate textures and retain their colors when the slime is dyed.

Quarry navigation uses a bounded 4,096-node search with a 128-block route budget inside the already-loaded work chunk. The route budget is independent of Detection Range; tool actions retain their actual range and sight checks. Candidate standing positions are searched together using the actual body footprint for support. Valid paths survive hops, and tight waypoint tolerance prevents clipping staircase corners. Travel timers reset on movement, and failed support routes retry after a short cooldown. Regression coverage includes default-stat staircase navigation, wall detours, blocked repairs, and resumption after clearance.

## Work-state restoration

`WorkDimension` records the dimension associated with a companion's work state. A successful Forge teleporter transfer runs the release hook at the destination. The first server tick also checks the saved dimension before AI runs, covering native dimension-restored copies. Mining uses dimension identity alongside chunk coordinates; transport and cleaning rebuild their fixed origins while retaining orders and cargo. Same-world loads keep their original work areas. Saves without this field adopt the dimension in which they are loaded. Specializations that override `onReleased` must call its base implementation so the recorded dimension follows the new work area.

`TransportCooldown` persists the remaining Work Speed interval, bounded to the supported 0–200 ticks. Transient paths and targets are reselected after load. Transport only replaces paths while grounded and measures missing horizontal or grounded vertical progress for its travel timeout, so hopping alone does not mask a stalled delivery.

Combat pursuit and cleaning returns also measure 400 ticks without horizontal or grounded vertical progress, excluding in-place hops. Progress clears a resolved blocked state. Valid paths survive hops, moving combat targets refresh the destination, and new return trips reset their own stall counter. Regression tests cover slow travel, stationary companions, recovery of movement before arrival, and exact cleaning returns.

Regression coverage includes cross-dimension quarry restoration at matching coordinates, portal and command-style restoration, cancelled travel, legacy saves, same-dimension origins, slow advancing deliveries, stationary carriers, and cooldown preservation after recovery.

World-scale worker tests use independently loaded, cleared full chunks. This keeps quarry targets and cargo-transfer assertions independent of small template placement and neighboring terrain.

## Shared work contract and data compatibility

`WorkStatus` separates the assigned `Order`, execution `State`, blocking `Reason`, and support `Request` set. Existing specialization states remain the presentation and persistence format; each type maps them to the common contract. Follow/Wait suppress requests. The native sitting goal cannot claim movement while a work order is active, including when the owner is unavailable. Logistics consumes cargo/tool requests, and heralds consume `BLOCKED` rather than specialization enums. Equipment and ownership are revalidated at the transfer boundary.

`WorkSearch` shares immutable loaded-chunk candidate snapshots for 20 ticks between carriers. Candidate membership is cached; requests, range, ownership, lock state, and capacity remain live checks. Expired regions are removed during refresh and world unload clears the cache. Missing chunks return empty results without acquiring tickets. Idle carrier searches use entity-dependent phases. Selected storage is checked against the current work radius before both deposit and tool pickup, including range reductions during travel. Regression coverage includes 100 repeated snapshot queries and 32 simultaneous deliveries; these are correctness/load scenarios, not hardware TPS benchmarks.

Recovery uses 2% of maximum stamina at existing recovery opportunities. Delivery activity is configurable (default 4); actual owner-following movement and active herald monitoring provide additional growth paths (default 100 eligible ticks per unit). Their partial timers are serialized and never advance in item storage. Failed actions and idle/wait orders grant no growth. A newly specialized mining item explicitly selects Wait before release; recovered mining data retains its order, except established relocation behavior.

`SlimeDataVersion` currently equals 1 in companion world and item payloads. The migration entry point adopts unversioned/zero-version payloads without removing existing fields. Unsupported future or malformed stored-item versions fail release without mutation. Existing specialization NBT keys and accumulated points remain unchanged. Add explicit migration steps before changing stored field meanings or layouts.

## Reliable alerts and menu synchronization

Herald warning history keeps the latest signaled episode for each worker UUID without truncating large groups. World saves and recovered items preserve the same records; malformed UUIDs and invalid episode values are ignored on load. A new warning does not remove another worker's history.

Player target selection checks the server PVP flag and the loaded owner's player-damage permissions before both target acquisition and melee damage. Native team/alliance exclusions remain in effect. Tests restore server PVP and scoreboard fixtures after checking each rule.

Native container-data packets carry signed 16-bit values. `IntContainerData` encodes each logical integer as two halves and appends an initial-snapshot marker. Management and tuner menus share this transport, retaining large effective attributes and clearing old high bits after a smaller update. Editors wait for the marker before loading initial values. Protocol 7 includes this layout and riding controls. Validation covers real native packet serialization, both menus, large cleaning range modifiers, value reductions, and native clients with and without JEI.

## Saddle equipment and riding

`SaddleSlime` uses native main-hand saddle and chest-slot elytra stacks for equipment synchronization, saving, recovery, and collapse preservation. The equipment-only menu contributes two companion slots before the player's inventory. Native crafting copies stored Friendly Slime data and the exact initial saddle; recovery emits the specialized item without reapplying initial equipment.

Only the owning player can occupy and control the single seat. A priority movement goal suspends companion AI during a ride. Native ridden-input/speed hooks provide steering and ground jumps. Winged travel uses collision-aware entity movement and the rider's view, with normalized combined directional input, hover, and Movement Speed scaling. Flight is available to Survival riders and uses normal vehicle movement packets. The client sends ascent/jump and boost intent; the server requires the sender to be the current owner rider of that exact entity. Input expires after 30 ticks without refresh and clears on dismount.

Winged riding supplies transient no-gravity for movement and native vehicle validation. Saving removes that computed flag while preserving explicit native gravity overrides, so recovered mounts do not hover unattended. The saddle has a dedicated model and leather atlas; paired side-wing geometry uses a dedicated opaque feather texture and animates independently while flying.

Protocol 7 carries ascent and boost in the riding input message. Validation includes crafting and actual release, owner checks, equipment Shift transfers, mounted recovery, unusable wings, explicit versus transient gravity, and native Survival client steering, jumping, ascent, hover, boost, pitch, server vehicle synchronization, and Shift dismount with and without JEI.

## Growth thresholds and quarry consistency

The shared point cost starts at 32, increases by 64 for each earned point, and caps at 4,096. Activity loading retains the supported remainder range and settles earned thresholds before saving the remainder, rather than clamping old activity to the smaller new cost. The same settlement handles live work awards. Existing points, investments, earned-tier counts, and companion traits remain authoritative; normalized saves do not repeat awards.

An explicit Mine command rebases a previously anchored companion when its current chunk differs from the work chunk. Same-chunk commands retain the saved plan. Travel progress uses 0.05 horizontal blocks or 0.5 grounded vertical blocks, preventing slow but advancing approaches from timing out solely because they cannot cover half a block within 400 ticks. Mining tracks the current block state and clears accumulated damage and the crack overlay when it changes.

The transport pack is placed behind the outer slime cube; side and rear straps also clear both body surfaces. This removes the coplanar inner-body/pack face and overlapping outer surfaces while sharing the companion's pose and squish transform. The Creative tab emits companion items together, then storage, armor in material order, and the tuner.

Regressions cover all nine initial growth thresholds, conversion of 200 saved activity without lost or repeated awards, starting a new quarry after following into another chunk, an advancing low-speed miner, and block replacement with exact drops and tool wear. Client checks inspect the pack from multiple angles and record the actual Creative item order.

## Threats, collapse, and active growth

The entity-join hook adds a companion target goal to hostile `Enemy` mobs, excluding creepers. Their native attack goals retain damage and projectile behavior. A higher-priority escape goal responds to a real attacker for non-combat companions and yields to rider control. Hop propulsion stays at a common base while the movement attribute sets the grounded delay; ridden ground travel uses the same principle.

A lowest-priority uncancelled `LivingDeathEvent` preserves the companion before native equipment, bag, or experience drops. `BrokenSlimeBlockEntity` stores its specialized recovery stack under `Slime`. A replaceable death cell becomes a block; an occupied cell produces its block item without overwriting terrain. Native block loot copies this payload to `BlockEntityTag.Slime`, retaining it through mining and placement. Revival uses the normal spawn validation and UUID checks, restores full health, clears death/fire/motion state, and consumes the block and ball only on success. Failed spawning restores the block entity.

Real non-allied damage and shield blocks grant at most one defense activity per 20 ticks. Owner and environmental damage grant none. Escape and ridden movement use the configured following interval. Combat shields face the target using entity yaw and retain at least a ten-tick attack interval so the native activation delay can complete.

Heralds retain per-worker repeat cooldowns in `HeraldWarningCooldowns`, separate from the episode history. They repeat every 200 active monitoring ticks after the first signal, pause in storage, and stop signaling resolved or out-of-range workers. Legacy `HeraldCooldown` loads into existing warning records.

Protocol 7 changes riding input to ascent and boost. Boost state is synchronized; the server spends 0.1 stamina per airborne tick, 0.5 per ground-sprint tick, and 0.75 per boosted-flight tick. Empty stamina enables synchronized controlled descent at 0.12 blocks per tick, with ascent suppressed, no airborne recovery, and a grounded 25% recovery latch before flight or sprinting resumes. Equipment and stored companion schema remain compatible; `SlimeDataVersion` stays 1.


## Surface workers

`FieldSlime` shares a single native main-hand tool, normal bag, stamina recovery, loaded-area checks, protected harvesting/placement, and FARM/LOG work orders. Full inventories request CARGO_PICKUP; an empty broken-tool slot requests TOOL_SUPPLY. Missing seeds use SEEDS_MISSING without creating a tool request. Successful actions award activity only after the world edit succeeds. Networking uses protocol 8.

`BlockSurvey` copies palettes of matching loaded chunk sections, then inspects at most 512 cells per worker tick. Farming activation freezes the existing farmland selection; serialization finishes any pending snapshot so recovery never loses recognized plots. Plot coordinates and crop identities are persistent. Tilling uses the equipped hoe's Forge tool action and native placement cancellation; block removal uses owner-attributed survival break events and simulates all drops before changing terrain.

Lumberjack discovery inspects at most 64 connected candidates per tick, limits trees to 512 logs and 4,096 visited cells, and never loads chunks. Stump-ground and natural-leaf checks exclude decorative structures. Matching saplings are reserved before cutting and serialized separately from transportable cargo. Log queues and replantings resume after world reload; release establishes a new origin and returns unused reservations. Both roles multiply effective detection by two while tuner fields retain the common base.

Native regressions cover exact specialization tools, frozen plots, same-crop replanting, soil restoration/exclusion, farmland trampling, tall-tree felling and replanting, decorative leaves, seed requirements, full inventories, cancelled edits, tool requests, and recovery. Client checks cover hats, held tools, management layouts, both new JEI recipe families, and all nine baked profession crests with JEI enabled and disabled.
