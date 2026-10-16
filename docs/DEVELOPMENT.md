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

Menu opening data includes the companion type so a missing client entity can retain the exact slot layout until the screen closes. Network protocol 5 requires compatible client and server builds. Hidden bag pages reject transfers, Capacity changes clamp the current page, and in-place slot mutations notify the bag so partially unloaded miners resume.

Mining state, bag contents, stamina, and activity progress use the standard entity save/load hooks. The equipped pickaxe and shovel use the native main-hand and off-hand equipment slots, keeping client rendering and item damage synchronized. Mining progress is bounded by one completed block per tick, searches process a limited number of chunk positions, and no neighbouring chunk is loaded for target discovery.

The crafting serializer copies the Friendly Slime's stored traits and the exact pickaxe stack into the result. JEI is a compile-only API dependency with optional development runtime support. Use `./gradlew runClient -PenableJei=false` or `./gradlew runGameTestServer -PenableJei=false` to validate operation without JEI. Common game logic does not reference JEI or client classes.

## Transport and storage

`BagSlime` shares capacity-aware storage, death drops, save/load, and recovery between worker types. Mining retains the `MiningBag` NBT key for existing saves; transport uses `TransportBag` with up to 54 slots. Transport capacity multiplies the common base and every upgrade by two. Its menu has no equipment slot and checks the active page on the server.

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

Every new item includes a dedicated Minecraft-style 16×16 pixel texture with a limited palette and clear silhouette. Use nearest-neighbor scaling when preparing pixel art. Leather armor keeps its tintable base separate from the untinted slime badge. Block items inherit their textured block model so inventory and placed appearances stay consistent. Companion accessories use separate textures and retain their colors when the slime is dyed.

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

Native container-data packets carry signed 16-bit values. `IntContainerData` encodes each logical integer as two halves and appends an initial-snapshot marker. Management and tuner menus share this transport, retaining large effective attributes and clearing old high bits after a smaller update. Editors wait for the marker before loading initial values. Protocol 5 identifies the new layout. Validation covers real native packet serialization, both menus, large cleaning range modifiers, value reductions, and native clients with and without JEI.
