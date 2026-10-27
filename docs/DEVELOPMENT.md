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
| `./gradlew verifyGuidebook` | Check language keys, arguments, and guide resources, links, recipes, and optional conditions in all four languages. |

GameTests run in `build/game-test-server`, separate from the normal development world under `run`. Their Java sources and fixtures live in `src/gameTest` and are excluded from release artifacts. The build compiles the GameTests; `runGameTestServer` executes them.

## Source layout

- `src/main/java`: mod entry point, registries, slime behavior, item interactions, and client rendering.
- `src/main/resources`: metadata, textures, translations, recipes, and loot tables.
- `src/generated/resources`: generated production resources when needed.
- `src/gameTest`: development-only test mod, tests, and structures.
- `docs`: detailed gameplay and development guides in English and Korean.

Keep each README focused on the project overview and links. Maintain detailed guides as English/Korean pairs.

Package only runtime assets used by the mod in `src/main/resources`; keep reference material and verification artifacts outside the release resources.

## Slime names

The dimension name is **The Slimic** in every locale. Material names retain the Latin **Slimic** prefix; Korean material terms follow it. Localized display-name corrections do not rename registry IDs or saved-data keys.

Working slime types are named for their activity. Use action names such as Mining, Farming, Logging, Signaling, and Command rather than names for the people performing those jobs. Keep established trait and species names, including Friendly, Guardian Angel, and Royal Guard.

| English type | Korean type |
| --- | --- |
| Mining Slime | 채굴 슬라임 |
| Farming Slime | 농사 슬라임 |
| Logging Slime | 벌목 슬라임 |
| Signaling Slime | 파발 슬라임 |
| Command Slime | 지휘 슬라임 |

Use the same full type name for the summon item, entity, guide entry, and documentation. Modifiers precede the activity: Ancient Mining Slime and Next-Generation Mining Slime. Wrap long guide headings instead of abbreviating the type. Individual companion names and story titles are distinct from type names. `verifyGuidebook` checks the item, entity, and entry labels in all four supported languages.

Java implementation names follow the same convention: `FarmingSlime`, `LoggingSlime`, `SignalingSlime`, and `CommandSlime`. Registry constants, menus, packets, and recipe kinds use the corresponding action names. Persisted registry IDs, resource paths, NBT keys, and server configuration keys remain stable so existing worlds, stored companions, equipment, and recipes continue to load. Ancient core roles use explicit saved names through `Role.savedName()` and `Role.fromSavedName()`; Java enum names do not define the save format.

## Versions and changes

Early development releases use the `0.5.x` series, with a patch increment for each release. Update `mod_version` in `gradle.properties` and [CHANGELOG.md](../CHANGELOG.md) together; the build verifies the changelog entry.

Keep the development test-mod version in sync when changing the release version.

## Sanctum and synthesis data

The current release uses protocol **27**, preserves companion schema **1**, and has a separately versioned growth record. The implementation of [sanctum exploration](FALLEN_SANCTUM.md) and [synthesis](SYNTHESIS.md) is summarized below.

`FallenSanctumStructure` and five `FallenSanctumPiece` instances save `ArchitectureVersion`. New version-4 pieces use `SanctumLabyrinthArchitecture`: a 112×112 footprint, three main floors at relative walking heights 0, 10 and 20, a lower vault, and a height bound of 48. The entrance and rear pieces cover 32-block bands; the three middle pieces cover 16-block bands. Saved versions 1–3 continue using their original `SanctumArchitecture` plans, while version 0 retains the original generator. Loaded pieces keep their serialized bounding boxes. New plans are clipped to the rotated chunk and piece bounds, and the complete clipped plan passes terrain, construction and block-entity checks before any write. Terrain sampling, world-border checks and nearby-feature reservations cover the larger footprint. Map searches reuse the actual anchor of an existing native structure rather than replacing it with the new terrain prediction. Relay, loot, corpse and watch sockets and site-reward identities remain stable.

`RoyalSynthesis.check` derives a preview from stored parents. `SlimeSynthesisEvent` is a cancellable pre-commit hook with detached item snapshots. Confirmation revalidates the actual block and inputs. `RoyalSynthesisData` checkpoints consumed UUIDs and before/after inventory images before applying the mutation; results have a persistent recipient mailbox. `DurableWorldData` writes native NBT envelopes with atomic replacement and file synchronization. Previously saved delivery receipts remain valid during retries.

Sanctum rewards checkpoint a player receipt with the delivered items before acknowledging the world ledger. Sanctum, assembly reward and colony recovery receipts are copied during player respawn; moving or using an item cannot remove proof of delivery.

Reward delivery checkpoints new stacks before acknowledging the recipient. If the player write fails, only the exact provisional stacks in previously empty inventory slots are withdrawn; existing contents and saved receipts remain intact. The site or synthesis ledger retains the pending delivery for retry.

`GrowthSchema=1`, `GrowthEarned`, and `GrowthTrusted` distinguish verified growth from Creative edits and unverified old history. `StatPoints` is a nonnegative long; older numeric NBT remains readable. Inheritance returns invested plus unused points and accounts for the inherited earned total in future costs. `SynthesisFamily` reserves mapping identifiers while enabling Mining and Tunnel. `NextGenerationMiningSlime` preserves policy ordinals and ore-mask keys, inherits the ancient half-cost stamina hook, and uses a 0.78-block physical body, rendering at 75% of ordinary companion size. `PrecisionProspecting` handles ordinal 2 with saved shaft/front/return-trail data, 2×2 passages and physical stepped routes. Surveys inspect at most 1,024 positions per slice in a radius-32 loaded window; route search expands at most 96 nodes per tick and 2,048 nodes per search. Every action revalidates floors, falling-floor support, fluids, hot magma blocks, roofs, stair protection, tool tier, bag capacity and Forge break callbacks. Only the precision policy selects `WorkNavigation`; ordinary policies retain the unchanged quarry navigator. Native breaking, durability, loot, experience and growth apply to access rock as well as ore. Legacy selective saves pause before excavation; current plans survive ordinary reloads and recovered deployment waits. Scanner snapshots describe the shaft and next face without surveying or revealing ore.

## Slime recovery data

`FriendlySlimeInteractions` handles golden-apple feeding through Forge's entity-interaction event. Only living, unremoved, size-one `minecraft:slime` entities qualify. The server creates one owned, waiting companion and preserves the custom name. Only a successful entity insertion removes the wild slime and consumes a Survival apple. Canceled interactions and spawn vetoes preserve both inputs; normal companion recovery applies afterward.

A recovered item stores the slime's complete serialized state in `SlimeData`, including Forge persistent data and capabilities. New persistent slime traits should use the entity's standard save/load hooks so they automatically survive recovery.

Deploying a recovered item loads the saved state and retains the original UUID and owner. Item release and revival select Wait; ordinary world loads retain saved commands. It replaces placement, motion, and fall distance for the new destination and does not restore leash or passenger relationships. A loaded entity with the same UUID prevents another release of that stored identity.

Entity admission is provisional until insertion finishes. After spawn callbacks, a candidate must be alive and registered in the expected level with its original UUID and owner before the summon item is consumed. Pinned Ender capacity is rechecked after callbacks so nested restorations cannot exceed the owner's limit. Provisional candidates do not update the companion registry or leave a Broken Slime block or recovery item on death. Rejected candidates are unregistered, including those removed during admission; the exact item data and previous registry record remain available for retry. Regression checks cover fresh and stored items, removal, death, ownership or UUID changes, death combined with cancellation, and competing Ender restorations.

Recovered items have a stack limit of one. Their release consumes the stored item even through Creative mode's normal item-count restoration. Newly crafted items retain the usual Creative reuse behavior.

## Validation

The [companion interaction matrix](INTERACTIONS.md) covers cooperation and ownership, quantity and equipment invariants.

Format all Java sources with Google Java Format before completing a change. Version 1.37 requires Java 21 to run; the mod still compiles and runs on Java 17.

Run `build` and `runGameTestServer` after gameplay changes. Coverage includes crafting, normal spawning, recovery and release, health and other traits, ownership, full inventories, blocked placement, Creative use, persistence, following, and death behavior.

Tests use a structure loaded one block above its marker, so the fixture floor is at relative Y=1. Keep world fixtures separate from production assets. Work-area preparation processes native chunk tasks until all forced chunks support entity ticks. Preparation has a 30-second real-time bound; action tick deadlines remain unchanged.

Development GameTest servers start at midday with natural mob spawning, daylight progression, and weather cycling disabled. Cases create their own targets and explicitly establish any time or weather conditions they need.

## Stats, mining, and integrations

Shared stat levels are synchronized as entity data. Server-side menu checks enforce ownership, distance, available points, and upgrade limits. Rename packets apply only to the sender's active Slime menu. Health upgrades change the maximum without healing the companion.

Menu opening data includes the companion type so a missing client entity can retain the exact slot layout until the screen closes. Network protocol 29 requires compatible client and server builds. Hidden bag pages reject transfers, Capacity changes clamp the current page, and in-place slot mutations notify the bag so partially unloaded miners resume. Management slots share the layout constants in `SlimeMenu`: equipment, a nine-column by three-row bag page, player inventory, and hotbar use the same column origin and spacing. The screen draws inactive bag cells as locked placeholders without exposing them to item transfers. Single-page bags hide paging controls; combat target settings hide the bag grid.

Bag sorting uses the current management menu’s server authorization. `SlimeBag.sortContents` visits occupied slots, orders cargo by item identifier and NBT, and merges only matching stacks. `ColonyBag` plans both canonical member stores before updating either one. Cargo retained above a reduced Capacity stays within existing extraction-only positions; sorting does not grant additional insertion slots. After a successful explicit colony sort, empty retained member spans are compacted and the current spans are saved. Ordinary transfers keep their stable slot indices.

Mining state, bag contents, stamina, and activity progress use the standard entity save/load hooks. The equipped pickaxe and shovel use the native main-hand and off-hand equipment slots, keeping client rendering and item damage synchronized. Ordinary mining is bounded by one completed solid block per tick, searches process a limited number of chunk positions, and no neighbouring chunk is loaded for target discovery.

The crafting serializer copies the Friendly Slime's stored traits and the exact pickaxe stack into the result. JEI is a compile-only API dependency with optional development runtime support. Use `./gradlew runClient -PenableJei=false` or `./gradlew runGameTestServer -PenableJei=false` to validate operation without JEI. Common game logic does not reference JEI or client classes.

## Transport and storage

`BagSlime` shares capacity-aware storage, collapse preservation, save/load, and recovery between worker types. Mining retains the `MiningBag` NBT key for existing saves; transport uses `TransportBag` with a normal upgrade maximum of 54 slots. Transport capacity multiplies the common base and every upgrade by two. Its menu has no equipment slot and checks the active page on the server.

`SlimeBag` keeps only occupied slots while preserving the existing `Size`/`Items`/`Slot` NBT layout. `BagView` pages expanded capacity through a fixed-size native menu; the client caches visible slots separately so packet ordering cannot write into another page. Internal simulation, logistics, drops, and save paths traverse occupied slots instead of allocating or scanning the configured capacity. Creative editing has no gameplay ceilings; recommendation warnings do not prevent Apply. Finite/minimum checks, representable integer slot addresses, and occupied-slot shrink protection remain.

Transport tasks query loaded chunks for Slime Chest block entities and same-owner or mutually shared workers inside the release-centered detection area. Transfers simulate available capacity before moving items on the server thread. Work requests are revalidated before transfer so concurrent carriers cannot duplicate tools or replace a tool already supplied. Travel targets are transient; cargo and orders persist in world data. Item release selects Wait; an explicit command can select a job again.

Slime Chests use a standard 27-slot container menu and Forge item capability, with normal inventory persistence, locks, comparator updates, and separate block/content drops. Regular chests are excluded from logistics.

Validation covers all Capacity levels, normal and expanded bag pages, ownership, full and partial destinations, concurrent tool supply, tool data and breakage, mining resumption, chest persistence/automation/drops, and transport recovery. Client checks cover both specializations, page synchronization, chest menus, model resources, and optional JEI recipes.

## Quarry, tuning, and signaling slime persistence

Mining uses native main-hand pickaxe and off-hand shovel equipment. `MiningQuarry` retains the chunk's entry elevation and current layer. Same-chunk item release keeps the plan; another chunk creates a new plan. Both start in Wait. The generic non-item dimension-transfer hook retains the existing command. Slime portals explicitly select Wait before and after that hook. The perimeter plan reserves two-block-wide full-block treads with flat corners. Repair consumes items from `slimic:quarry_supports` and honors placement events. Target scans are bounded to 256 positions per layer and a limited number of empty layers per update.

Dye IDs and custom work stats use synchronized entity data. Native attribute tuning edits base values and preserves modifiers. The creative-only tuner validates the active menu, held tool, dimension, distance, finite values, minimums, integral Capacity, and occupied bag slots on the server before applying any changes.

Signaling recipes store the exact rocket stack. Signals are vanilla firework entities marked for damage suppression. Workers save their continuous blocked duration and episode identifier; signaling slimes save warning history and cooldowns so an explicit Watch command after recovery retains warning history and the remaining repeat timer. Server configuration supplies the default delay.

Client rendering applies a brightness-preserving dye shader to the original slime texture while leaving equipment unchanged. Optional JEI previews include crafting specializations and all six station restorations. Validate client shader loading, translated screens, tuner packets, creative defaults, two tool slots, and JEI enabled/disabled alongside dedicated-server tests.

Quarry navigation uses a bounded 4,096-node search through loaded terrain. Route allowance accounts for target distance and vertical staircase detours independently of Detection Range; tool actions retain their actual range and sight checks. Candidate standing positions are searched together using the actual body footprint for support. Valid paths survive hops, and tight waypoint tolerance prevents clipping staircase corners. Travel timers reset on movement, and failed support routes retry after a short cooldown. Regression coverage includes default-stat staircase navigation, wall detours, blocked repairs, and resumption after clearance.

`QuarryStaircaseData` stores dimension-local, persistent staircase profiles in `slimic_quarry_staircases.dat`. Each worked chunk retains one shared origin and at most 28 top-height bounds; protection lookup is constant time and does not acquire chunks. Unstarted plans adopt the shared origin while keeping their own starting height and layer. Started legacy plans retain their original phase, and their profiles are merged before work. Fresh waiting summons do not register a worksite. `MiningQuarry.RampOrigin` is optional in older entity data; absent values derive from `TopFloor`. Registered protection outlives entities. Join callbacks skip stale work anchors from another dimension until release initializes the destination. Final breaking checks revalidate protection after Forge callbacks. Repair checks preserve two blocks of headroom above other protected floors. The common companion schema remains 1 and the network protocol is 27.

Large Creative search ranges use loaded chunk positions and accessible entities instead of iterating empty coordinate windows. Chunk load/unload and level unload maintain the index. Normal searches retain small local windows; work routes and extreme Creative follow routes use bounded loaded-terrain navigation without overflowing the radius. Stats remain unchanged.

Creative Health and Attack are converted through `CreativeStats.asFloat` at native attribute reads. Input bases remain doubles. This prevents infinite outgoing damage from producing NaN in vanilla absorption subtraction; the same attribute path covers melee, Guardian beams, and Dragon breath. Compatible payload migration clears older nonfinite absorption to zero without changing valid absorption, current health, or the source item.

## Patchouli guidebook

Ordinary guide entries use neutral explanations and direct instructions. Slime records are distinct narrative passages: use short, simple recollections of what their slime authors saw and experienced, retaining references to `XXX`. Keep controls and numeric rules in the guide rather than presenting them as a record author’s knowledge. Introductions to records use the ordinary guide voice.

Development runs use optional runtime dependency Patchouli `1.20.1-84.1-FORGE`. Disable it with `-PenablePatchouli=false`, independently of `-PenableJei=false`. Common game logic has no Patchouli Java API dependency, and the release JAR does not bundle the library.

The definition is `data/slimic/patchouli_books/guide/book.json`; contents live in `assets/slimic/patchouli_books/guide/{en_us,ko_kr,ja_jp,es_es}`. The guide uses the [Patchouli resource book format](https://vazkiimods.github.io/Patchouli/docs/reference/book-json/). Locale folders translate the body; `i18n` stays disabled so literal percentages are not interpreted as translation format strings. The standard language files translate the book name, landing text, and subtitle. Book `version` is `0` so the localized subtitle appears instead of an edition number.

The one-book, one-slimeball recipe and its recipe-book unlock share a `forge:mod_loaded` condition. Dynamic companion recipes retain their normal ingredients and saved data; the guide displays them through shared item diagrams. Ordinary recipes use native crafting pages.

`verifyGuidebook` checks matching language keys, group order, formatting arguments, locale paths, and page structures in all four languages, as well as categories, templates, internal links, recipe references, and optional conditions; `check` and `build` include it.

For native validation, create a test world named `slimic-smoke` in the normal development client, save and quit, then copy `run/saves/slimic-smoke` to `build/guidebook-client/saves/slimic-smoke`. Run `./gradlew runClient -PclientSmoke=true -PguidebookSmoke=true`. The separate world checks Survival 2×2 crafting, consumption, unlocks, Creative placement, language/resource reloads, every page, text bounds, item lookups, links, and localized search. It changes the copied player inventory and game mode.

## Combat and cleaning

`CombatSlime` keeps sword, shield, and armor in native equipment slots and shares the normal capacity-aware bag. Its synchronized target mask has six independent bits; owner menu checks protect all changes, and current targets are revalidated after a filter or order change. Native melee, armor, shield timing, enchantments, durability, and Forge events provide combat behavior. Serialized settings and cooldowns survive recovery.

Each standard armor material has its own registered item, preserving native repairs and netherite fire resistance. `SlimeArmorItem` stores an original chestplate in `SlimeArmorBase`. Outer item damage, enchantments, display, and attribute data remain authoritative after conversion. Material attributes and durability are delegated to that chestplate. Recipes accept registered chestplates and swords, and JEI builds corresponding previews dynamically.

`CleaningSlime` multiplies effective Detection Range by five while leaving the common base and tuner values unchanged. Target selection is limited to loaded entities around a fixed release origin. Transfers simulate bag space, preserve partial stacks, honor pickup delays/ownership/events, and run on the server thread. A 3,600-tick attempt window selects an alternative or retries; saved timers pause in item storage. Working goals reserve movement while idle to prevent wandering. Transport uses the shared full-bag and replacement-tool interfaces.

Validation covers target defaults and toggles, ownership, native combat and defenses, material/NBT conversion, cargo and retry persistence, collection bounds, idle behavior, actual pickups and returns, full bags, tool delivery, optional JEI, and translated client controls. Combat tests run after the Peaceful-difficulty test batch.

## Visual asset conventions

Summoning items share the `slime_summon_base` model and Friendly Slime texture. Child models select profession artwork; Friendly uses a flat plate showing the complete gold-block face without a profession crest and Soggy uses the registered Slime Slag texture. Specialized crests use the common frame; Friendly overrides the badge elements to keep a single flat face. Other new items include a dedicated Minecraft-style 16×16 pixel texture with a limited palette and clear silhouette. Use nearest-neighbor scaling when preparing pixel art. All six Slime Armor models inherit `slime_armor_base`, sharing one grayscale silhouette and an untinted slime badge. Item colors supply each material palette and preserve the original leather dye. Block items inherit their textured block model so inventory and placed appearances stay consistent. Companion accessories use separate textures and retain their colors when the slime is dyed.


Profession accessories use dedicated `textures/entity/accessories/*.png` atlases; management, tuner, roster, and slot surfaces use `textures/gui/*.png`. Shapes and animations stay in the client models. See [Texture editing](TEXTURES.md) for file assignments, UV coordinates, resource-pack replacement, and native appearance checks.


`FriendlySlimeItem` adds one profession description using the concrete item's `<description_id>.tooltip` key for every specialization. Shared hints distinguish fresh summoning from stored restoration. Subclasses add only item-specific state such as an initial pickaxe; they do not repeat the profession summary. English, Korean, Japanese, and Spanish provide descriptions for every ordinary and colony companion. The native `slimic.companionTooltipsOnly` client check covers fresh/stored stacks, normal/advanced tooltips, both fonts, all four supported languages, language fallback, native screen bounds, and immutable saved data. JEI information retains translatable components and resolves the current language when opened, including after a language reload. New Royal Synthesis results also retain parent-name components: default names follow the current language, while custom names remain literal. Missing or malformed optional components fall back to the previously stored name strings.

## Work-state restoration

`WorkDimension` records the dimension associated with a companion's work state. A successful Forge teleporter transfer runs the release hook at the destination. The first server tick also checks the saved dimension before AI runs, covering native dimension-restored copies. Mining uses dimension identity alongside chunk coordinates; transport and cleaning rebuild their fixed origins while retaining orders and cargo. Same-world loads keep their original work areas. Saves without this field adopt the dimension in which they are loaded. Specializations that override `onReleased` must call its base implementation so the recorded dimension follows the new work area.

`TransportCooldown` persists the remaining Work Speed interval, bounded to the supported 0–200 ticks. Transient paths and targets are reselected after load. Transport only replaces paths while grounded and measures missing horizontal or grounded vertical progress for its travel timeout, so hopping alone does not mask a stalled delivery.

Combat pursuit and cleaning returns also measure 400 ticks without horizontal or grounded vertical progress, excluding in-place hops. Progress clears a resolved blocked state. Valid paths survive hops, moving combat targets refresh the destination, and new return trips reset their own stall counter. Regression tests cover slow travel, stationary companions, recovery of movement before arrival, and exact cleaning returns.

Regression coverage includes cross-dimension quarry restoration at matching coordinates, portal and command-style restoration, cancelled travel, legacy saves, same-dimension origins, slow advancing deliveries, stationary carriers, and cooldown preservation after recovery.

World-scale worker tests use independently loaded, cleared full chunks. This keeps quarry targets and cargo-transfer assertions independent of small template placement and neighboring terrain.

## Shared work contract and data compatibility

`WorkStatus` separates the assigned `Order`, execution `State`, blocking `Reason`, and support `Request` set. Existing specialization states remain the presentation and persistence format; each type maps them to the common contract. Follow/Wait suppress requests. The native sitting goal cannot claim movement while a work order is active, including when the owner is unavailable. Logistics consumes cargo/tool requests, and signaling slimes consume `BLOCKED` rather than specialization enums. Equipment and ownership are revalidated at the transfer boundary.

`WorkSearch` shares immutable loaded-chunk candidate snapshots for 20 ticks between carriers. Candidate membership is cached; requests, range, ownership, lock state, and capacity remain live checks. Expired regions are removed during refresh and world unload clears the cache. Missing chunks return empty results without acquiring tickets. Idle carrier searches use entity-dependent phases. Selected storage is checked against the current work radius before both deposit and tool pickup, including range reductions during travel. Regression coverage includes 100 repeated snapshot queries and 32 simultaneous deliveries; these are correctness/load scenarios, not hardware TPS benchmarks.

Stamina regeneration uses 2% of maximum stamina at existing recovery opportunities. Delivery activity is configurable (default 4); actual owner-following movement and active signaling slime monitoring provide additional growth paths (default 100 eligible ticks per unit). Their partial timers are serialized and never advance in item storage. Failed actions and idle/wait orders grant no growth. All companion types initially select Wait, including when saved order data is absent. All item releases and revivals enforce Wait after stored data and equipment are restored and after the relocation hook. Ordinary world loads preserve saved commands.

`SlimeDataVersion` currently equals 1 in companion world and item payloads. The migration entry point adopts unversioned/zero-version payloads without removing existing fields. Unsupported future or malformed stored-item versions fail release without mutation. Existing specialization NBT keys and accumulated points remain unchanged. Add explicit migration steps before changing stored field meanings or layouts.

## Reliable alerts and menu synchronization

Signaling warning history keeps the latest signaled episode for each worker UUID without truncating large groups. World saves and recovered items preserve the same records; malformed UUIDs and invalid episode values are ignored on load. A new warning does not remove another worker's history.

Player target selection checks the server PVP flag and the loaded owner's player-damage permissions before both target acquisition and melee damage. Native team/alliance exclusions remain in effect. Tests restore server PVP and scoreboard fixtures after checking each rule.

Native container-data packets carry signed 16-bit values. `NumericContainerData` sends each double through four 16-bit segments and an initial-snapshot marker. Both menus preserve exact stats, current health/stamina, and points without scaled-integer truncation. Editors wait for the marker before loading initial values. Protocol 29 carries points as four raw 16-bit segments of a signed 64-bit integer, alongside the unchanged double fields. Native packet tests cover high values and reductions; client checks cover both menus. Command roster components format health above the signed-integer range in scientific notation while retaining the recorded float values.

`CreativeSlimeTunerMenu` applies only stat fields whose submitted values differ from the current values. Editing only the unused-point balance preserves constituent stat differences and odd combined capacities. An explicit colony Capacity edit divides storage in the members’ multiplier units and gives the extra unit to the first member. Before any edit is applied, the combined storage span, including occupied slots, must fit the supported integer range. An unrepresentable total is rejected without changing the colony. This storage check is independent of gameplay recommendations; exceeding a recommended value alone remains allowed.

## Saddle equipment and riding

`SaddleSlime` uses the native main hand for its saddle, off hand for elytra, and chest slot for Slime Armor. The equipment-only menu contributes three companion slots before the player's inventory. Native armor attributes, enchantments and durability remain separate from the wings. `SaddleEquipmentVersion = 1` marks this layout: legacy chest-slot elytra move to the off hand on load, preserving their full data. Any nonstandard old offhand item is retained in the chest slot for retrieval. Saving the marker prevents repeated migration; the shared companion schema stays 1. Native crafting copies stored Friendly Slime data and the exact initial saddle; recovery emits the specialized item without reapplying initial equipment.

Normal flight velocity in blocks per tick is `min(0.45, 0.24 × sqrt(Movement Speed / 0.3))`, multiplied by 1.75 while boosting. Non-finite or non-positive movement attributes produce zero thrust. Ground sprint without elytra retains its existing twofold speed. Client travel and server vehicle prediction share this calculation, with normalized input and the existing stamina/descent rules.

Only a primary or shared owner can occupy and control the single seat. A priority movement goal suspends companion AI during a ride. Native ridden-input/speed hooks provide steering and ground jumps. Winged travel uses collision-aware entity movement and the rider's view, with normalized combined directional input, hover, and Movement Speed scaling. Flight is available to Survival riders and uses normal vehicle movement packets. The client sends ascent/jump and boost intent; the server requires the sender to be the current rider of that exact entity with primary or shared-owner access. Input expires after 30 ticks without refresh and clears on dismount.

Winged riding supplies transient no-gravity for movement and native vehicle validation. Saving removes that computed flag while preserving explicit native gravity overrides, so recovered mounts do not hover unattended. The saddle has a dedicated model and leather atlas; paired side-wing geometry uses a dedicated opaque feather texture and animates independently while flying.

Protocol 29 carries ascent and boost in the riding input message. Validation includes crafting and actual release, owner checks, equipment Shift transfers, mounted recovery, unusable wings, explicit versus transient gravity, and native Survival client steering, jumping, ascent, hover, boost, pitch, server vehicle synchronization, and Shift dismount with and without JEI.

## Growth thresholds and quarry consistency

The shared point cost starts at 32, increases by 64 for each earned point, and caps at 4,096. Activity loading retains the supported remainder range and settles earned thresholds before saving the remainder, rather than clamping old activity to the smaller new cost. The same settlement handles live work awards. Existing points, investments, earned-tier counts, and companion traits remain authoritative; normalized saves do not repeat awards.

An explicit Mine command rebases a previously anchored companion when its current chunk differs from the work chunk. Same-chunk commands retain the saved plan. Travel progress uses 0.05 horizontal blocks or 0.5 grounded vertical blocks, preventing slow but advancing approaches from timing out solely because they cannot cover half a block within 400 ticks. Mining tracks the current block state and clears accumulated damage and the crack overlay when it changes.

The transport pack is placed behind the outer slime cube; side and rear straps also clear both body surfaces. This removes the coplanar inner-body/pack face and overlapping outer surfaces while sharing the companion's pose and squish transform. The main Creative tab groups companion roles and upgrade lines, management tools, armor variants, functional stations, exploration and restoration supplies, records, and hostile spawn eggs. Building variants follow their base material; decorations follow their use. Registry identifiers and variant data are unchanged.

Regressions cover the initial growth thresholds for individual companion types, conversion of 200 saved activity without lost or repeated awards, starting a new quarry after following into another chunk, an advancing low-speed miner, and block replacement with exact drops and tool wear. Client checks inspect the pack from multiple angles and record the actual Creative item order.

## Threats, collapse, and active growth

The entity-join hook adds a companion target goal to hostile `Enemy` mobs, excluding creepers and Endermen. For the exact vanilla `EntityType.SLIME`, the goal only acquires the companion recorded by `getLastHurtByMob()`. Magma Cubes and other registered slime types retain normal targeting. Combat target classification and filters remain unchanged, allowing Combat and Dragon Slimes to initiate attacks with Hostile enabled. Native attack goals retain damage and projectile behavior. A higher-priority escape goal responds to a real attacker for non-combat companions and yields to rider control. Hop propulsion stays at a common base while the movement attribute sets the grounded delay; ridden ground travel uses the same principle.

A lowest-priority uncancelled `LivingDeathEvent` preserves the companion before native equipment, bag, or experience drops. `BrokenSlimeBlockEntity` stores its specialized recovery stack under `Slime`. A replaceable death cell becomes a block; an occupied cell produces its block item without overwriting terrain. Native block loot copies this payload to `BlockEntityTag.Slime`, retaining it through mining and placement. Revival uses the normal spawn validation and UUID checks, sets current health to 1, clears death/fire/motion state, and consumes the block and ball only on success. Failed spawning restores the block entity.

Real non-allied damage and shield blocks grant at most one defense activity per 20 ticks. Owner and environmental damage grant none. Escape and ridden movement use the configured following interval. Combat shields face the target using entity yaw and retain at least a ten-tick attack interval so the native activation delay can complete.

Signaling Slimes retain per-worker repeat cooldowns in `HeraldWarningCooldowns`, separate from the episode history. They repeat every 200 active monitoring ticks after the first signal, pause in storage, and stop signaling resolved or out-of-range workers. Legacy `HeraldCooldown` loads into existing warning records.

Riding input carries ascent and boost. Boost state is synchronized; the server spends 0.1 stamina per airborne tick, 0.5 per ground-sprint tick, and 0.75 per boosted-flight tick. Empty stamina enables synchronized controlled descent at 0.12 blocks per tick, with ascent suppressed, no airborne recovery, and a grounded 25% recovery latch before flight or sprinting resumes. Equipment and stored companion schema remain compatible; `SlimeDataVersion` stays 1.

## Surface workers

`FieldSlime` shares a single native main-hand tool, normal bag, stamina recovery, loaded-area checks, protected harvesting/placement, and FARM/LOG work orders. Harvesting and placement require the target and its immediate horizontal neighbors to be loaded, preventing native block notifications from acquiring a new chunk. Unavailable terrain pauses the action without consuming drops or materials and uses the normal retry path. Full inventories request CARGO_PICKUP; an empty broken-tool slot requests TOOL_SUPPLY. Missing seeds use SEEDS_MISSING without creating a tool request. Successful actions award activity only after the world edit succeeds. Networking uses protocol 29.

`BlockSurvey` copies palettes of matching loaded chunk sections, then inspects at most 512 cells per worker tick. Farming activation freezes the existing farmland selection; serialization finishes any pending snapshot so recovery never loses recognized plots. Plot coordinates and crop identities are persistent. Tilling uses the equipped hoe's Forge tool action and native placement cancellation; block removal uses owner-attributed survival break events and simulates all drops before changing terrain.

Logging discovery inspects at most 64 connected candidates per tick, limits tree plans to 512 harvest blocks and 4,096 visited cells, and never loads chunks. Stump-ground and natural-leaf checks exclude decorative structures. Matching saplings are reserved before cutting and serialized separately from transportable cargo. Log queues and replantings resume after world reload; release establishes a new origin and returns unused reservations. Both roles multiply effective detection by two while tuner fields retain the common base.

Mangrove discovery follows raised trunks through central roots and air or water gaps to valid planting soil. Only the planned central root blocks join the harvest queue. The optional `LoggingRootColumns` coordinate array identifies those root entries separately from logs, so replacing a planned log with a root does not authorize harvesting that replacement. Loading accepts at most 512 entries that still belong to the saved queue; absent data means an empty set and retains schema 1 compatibility. Replanting uses native planted propagule age and waterlogs only existing source water.

Native regressions cover exact specialization tools, frozen plots, same-crop replanting, soil restoration/exclusion, farmland trampling, tall-tree felling and replanting, decorative leaves, seed requirements, full inventories, cancelled edits, tool requests, and recovery. Client checks cover hats, held tools, management layouts, both JEI recipe families, and all ordinary and colony companion crests with JEI enabled and disabled.

## Companion care, tools, guardians, and animated storage

`SlimeTunerItem` is the Survival management tool; its shaped 1×2 recipe places a slimeball directly above an iron ingot. `CreativeSlimeTunerItem`, `CreativeSlimeTunerMenu`, `CreativeSlimeTunerScreen`, `creative_slime_tuner`, and `creative_tuner` identify the Creative editor. Protocol 29 carries its renamed packet and adds the guardian menu layout and signaling slime manual-fire command. Management validates ownership, alive state, dimension, distance and a held ordinary tuner on the server. Healing and washing consume items only on an actual change; capture retains priority.

`GuardianAngelSlimeRecipe` requires a Healing Slime with an empty bag and preserves the input companion payload and exact totem in `InitialGuardianTotem`. The guardian stores `GuardianTotems` and its attack cooldown. `ProfessionHealth` maintains stable permanent fourfold maximum-health modifiers for both Guardian Angel Slimes and Royal Guard Slimes: base 64, with 16 per ordinary Health upgrade. `SlimeData` normalizes earlier modifiers before loading and clamps current health only to the resulting maximum, without healing. Creative native attributes use saved `TunedStats` markers and finite modifier arithmetic without changing global attribute limits. Flying navigation and move control use a dedicated flying-speed attribute derived from common Movement Speed. Retaliation targets and beams are transient. A lowest-priority damage event selects the owner's attacker; sight, alliance, range and resource checks gate beams. A lowest-priority uncancelled death event selects one nearby owned guardian, invokes Forge totem-use permission, consumes one reserve and restores native-style effects. Held native totems resolve before death. Beam ribbons emit both windings for the native lightning render type.

The reserve slot accepts up to 64 matching totems. Manual withdrawal takes one; Shift-click distributes into native one-item inventory slots and retains overflow. Container mutations mark synchronized reserve data dirty. Native menu clicks, item transfer, saves, owner damage, flying retaliation and lethal protection are covered by GameTests and client checks.

Surface workers plan collision-free standing cells within action reach instead of targeting occupied logs or crops. Candidate stands and path endpoints use native `WalkNodeEvaluator.getFloorLevel`, including reduced-height collision surfaces such as mud. Logging Slimes cache unavailable roots briefly, keep the specific missing sapling and stump count in synchronized data, continue searching other species, and invalidate discovery caches after bag changes. Farming and logging have separate localization namespaces. Miners exclude wood/axe/hoe targets, revalidate path endpoints, retain native step approaches, and measure real progress toward the target so stationary hopping cannot postpone retries indefinitely. Escape navigation keeps its route during airborne hops and replaces it only with a grounded path whose endpoint increases distance from the attacker.

Slime Chest keeps its block entity inventory, item capabilities and block drops. A native opener counter drives chest sounds and block events; a client lid controller interpolates the angle. The block entity renderer uses the native single-chest body, lid, and latch proportions and 64×64 UV layout around a rear hinge. The latch moves with the lid; the closed item uses the same atlas and geometry in a baked 3D block model.

## Boundary regression coverage

Management rechecks Spectator status on every command and inventory interaction, including menus opened before a game-mode change. Slimeball healing compares health before and after the Forge healing hook; canceled or zero healing produces no consumption or healing effects.

Logging Slimes defer unreachable or pickup-protected material entities for 200 ticks and continue other work. Missing-sapling indicators are cleared when their root or natural-leaf witness disappears, after cargo changes, or when the work origin changes. These transient search caches are rebuilt after loading. Guardian visibility checks include the beam's bounding box as well as the companion body.

`BroadAuditGameTests` adds independent cases for save sanitization, unsupported schemas, immutable recipe inputs, healing hooks, permission transitions, invalid tuning payloads, native slot limits, capacity/recovery scaling, explicit mining activation, loaded-only searches, relocated farmland, obsolete sapling requests, unreachable material pickup, simultaneous transport, pickup cancellation, signal-spawn cancellation, target revalidation, totem data, riding equipment removal, and multiple chest viewers. The full profession suites remain separate. Client checks exercise beam visibility when the body is culled, synchronized menus, equipment, animated storage, and operation with and without JEI.

Recovery fixtures wait for the destination chunk to become entity-ticking before interaction, observe the restored UUID at the join event, and verify identity, health, order, and schema through the server's entity lookup. Verification and fixture cleanup run in separate sequence steps.

Work arenas clear complete columns above their floor and remove non-player entities before and after terrain preparation, including drops from cleared vegetation. This prevents natural terrain and preparation drops from changing the test inputs. Placement-accounting fixtures register only their prepared plot and wait for valid native planting conditions; loot checks remain inside the cleared arena. Asynchronous worker tests wait for the required work state before changing resources or terrain instead of assuming discovery finishes after a fixed delay.

## Fluid work, trails, and step movement

Mining scans the current quarry layer for water or lava before ordinary block work. `BUCKET_MISSING` and `BUCKET_SUPPLY` distinguish the blocked reason and support request. Only an empty bucket in the miner's usable bag enables removal; a full bag also requests cargo pickup. Transport revalidates the request, available capacity, and accepted stack before transferring exactly one original bucket. Removal neither consumes nor fills it. Fluid actions check Forge break and placement permissions, roll back cancelled replacements, preserve waterlogged block entities, and apply stamina/growth only after success. The persisted fluid cooldown bounds action frequency.

Fluid target selection prefers reachable exposed sources and removes at most a horizontal 3×3 patch per action. Source batches leave flow cleanup until the remaining sources are gone. Each cell independently checks reach, sight, Forge permissions, bucket presence, and available stamina. `MiningDrainedWaterLayer` preserves the active drainage layer; the Forge fluid-source event prevents water in that working layer from contributing to source regeneration. Active drainage is indexed by loaded level and chunk, so fluid updates check registered miners without scanning surrounding entities or loading chunks. Stopping work, changing quarry layers, or removing the miner releases the guard. No global game rule is changed.

`SoggySlime` uses ordinary companion state plus saved trail/recovery timers. Server-side placement requires air, a loaded solid supporting face, and permission; cancelled placements restore the snapshot without charging stamina or growth. `SlimeTrailBlock` uses carpet geometry, a shovel tag, and one-slag loot. The slimeball recycling recipe consumes four slag. Its summon model uses the shared base with a separate crest; slag has a dedicated transparent 16-pixel texture.

Ground hop control caps propulsion throughout native `JUMPING` as well as movement requests. Entering water allows a new swimming destination to replace the previous ground-jump heading, with full swimming input against currents. Quarry waypoints accept the height difference between the water surface and submerged path nodes. The Movement Speed attribute adjusts hop timing without reapplying unlimited launch speed while stepping up. Guardian self-defense excludes owners and allies and remains available during Wait or an owner disconnect. Totem protection uses the shared lethal-damage event, including environmental sources subject to native bypass exclusions.

Regression coverage includes all ground roles in the jumping state, actual fall/lava/drowning/fire deaths, offline guardian retaliation, trail costs/loot/cancellation, stored soggy traits, fluid protection rollback, unchanged bucket data, waterlogged inventories, and transport unloading followed by bucket delivery. Native clients verify the new models, trail rendering, management screen, and optional JEI recipes.

The common FriendlySlime constructor initializes every companion in Wait without invoking subclass order setters during construction. Fresh summon items enforce Wait after equipment initialization. Entity loading defaults to Wait when Sitting is absent; missing profession order data and first specialization also select Wait. Explicit saved orders remain authoritative during ordinary world loading; item-based release and revival select Wait. Native regressions cover all individual companion types through construction, commandless entity NBT, world-data round trips, fresh item use, and recovery into Wait with preserved identity and resources. Saddle crafting verifies that a stored Follow order becomes Wait while identity, resources, and the exact saddle are retained. Job tests issue their starting commands explicitly.

Guardian retaliation also recognizes damage absorbed by temporary absorption health. Untamed guardians safely reject owner-dependent target checks; native regressions cover both boundaries.

## Documentation and translation

Use present-tense factual explanations for behavior and imperative verbs for player procedures. UI labels use short nouns or actions; status messages name the current state and, when blocked, its cause. Tooltips state the item's use and essential conditions. Guides explain controls and mechanics in a neutral voice. Slime records use simple first-person recollections of concrete events and clues, with `XXX` retained; their authors do not describe hidden game systems.

Keep meanings parallel across the four supported in-game languages and the English/Korean documentation and use official item and companion names. The dimension is **The Slimic** in every language. In Korean, **회수** returns a companion to an item, **수거** collects cargo, **복원** restores an ancient core to a companion, and **결속** joins two companions into a colony. Describe a map as recording or displaying a destination. Technical recovery of saved data and repair of terrain must identify what is being restored.

Preserve translation keys, format placeholders and their argument mappings, links, and the distinction between labels and complete sentences. Check text in the actual screen or book page: a correct translation must also fit its buttons, tooltips and available line width. Historical changelog entries retain the behavior of their release.

Keep `en_us.json`, `ko_kr.json`, `ja_jp.json`, and `es_es.json` as one file per language. Group keys by their first dot-separated component in this order: `itemGroup, key, dimension, biome, structure, block, item, entity, stat, direction, ore, target, equipment, policy, screen, roster, status, message, synthesis, bonding, jei, book, lore, record, name`. Within each group, sort the complete keys lexicographically. Separate groups with a blank line and keep identical key order in all four languages. Reordering must preserve every key, value and format placeholder.

## UI text and terminology

Screens and gameplay guides share the stat names Health, Attack, Defense, Movement Speed, Work Speed, Capacity, Stamina, Reach, and Detection Range. Stamina defines the maximum resource; current stamina is the amount available to spend or recover. Growth uses activity units and upgrades consume stat points. The Korean interface distinguishes the Stamina stat, 지구력, from current stamina, 기력.

Follow and Wait are owner-selected orders. Resource shortages and other impediments are described as blocked work. Farming and logging messages identify their own tasks. Keep status text brief, action guidance direct, and button labels short. Use the game's localized names for vanilla ingredients.

Copy changes retain translation keys and persistent data identifiers. Check matching keys across all supported languages, formatting placeholders, and document links, then inspect wrapping, button widths, and help panels in the client.

## Quarry lining and free roaming

`QuarryWalls` scans a bounded 192-cell window on the four exterior faces of the work chunk, at most once per 20 ticks unless the layer changes. Loaded side fluids and underground 2×2 air openings create a retained job; the inside face must be exposed before a cell is actionable. The exterior lining preserves both staircase columns. Pending cells are bounded, validated against the current plan, and saved in `MiningWallPending`; changing quarry coordinates or dimension creates a new wall job. Discovery and navigation never acquire adjacent chunk tickets.

`COBBLESTONE_MISSING` with `COBBLESTONE_SUPPLY` pauses mining; lack of bag space also requests `CARGO_PICKUP`. `BagSlime.workSupplyLimit` and `receiveWorkSupply` retain one-item defaults for tools and buckets. A miner requesting wall material accepts up to 64, with live request/ownership/capacity validation before transfer; transport extracts only the accepted count. Successfully placed cobblestone costs one material and stamina and grants one activity unit. Forge placement cancellation restores the snapshot and spends nothing. Unreplaceable and occupied cells are preserved.

`SoggyRoaming` and `SoggyRoamHome` store the optional ROAM order and its origin without changing schema version 1. Missing fields retain previous Follow/Wait behavior. Roaming reserves MOVE/LOOK, chooses bounded random supported destinations, and uses a path region only after every intersecting chunk is confirmed loaded. It remains independent of the owner's presence and yields to fleeing. Follow and Wait clear the roaming flag; release reanchors it. Terrain changes release roaming paths for local reselection, avoiding reuse of an earlier Follow destination. Its recovery override permits trail stamina to recover while ROAM is active. The menu synchronizes ROAM through the existing mode field; protocol 29 requires matching clients.

Native regressions cover inlet replacement, cave lining across recovery, exact placement costs, protected placements, material shortages, stack-limited and partial-stock delivery, menu permissions, actual owner-independent roaming, trail production, command changes, saved traits, and exhausted-roamer recovery.

## Enderman targeting

Endermen are excluded from the added companion-target goal. Combat classification checks their live target before the generic NeutralMob rule: an Enderman targeting a living FriendlySlime uses HOSTILE; otherwise NEUTRAL. Target selection and attacks revalidate this classification and existing filters. Native tests cover unchanged goal sets, calm/hostile transitions, unrelated targets, and actual combat followed by disengagement.

## Tool effectiveness and block-breaking time

`SlimeBlockBreaking` shares native `getDestroyProgress` timing between quarry mining and `FieldSlime.harvest`. Work Speed multiplies progress only when the held tool's native destroy speed exceeds 1, matching the effectiveness requirement used by native Efficiency. An ineffective fallback tool retains its ordinary native progress. Missing, exhausted, or tier-inadequate equipment cannot advance breaking. Crops with zero native hardness still harvest immediately when the profession's required hoe is equipped. Logging now includes the actual axe's native breaking time before its existing action cooldown.

The transient tracker identifies the block position, state, and an exact snapshot of the tool, including enchantments and damage. Changing any of them, interrupting work, changing orders or areas, or removing the entity clears partial progress and crack overlays. Reloaded and recovered companions restart partial block damage while retaining saved work areas and completed activity. Durability, stamina, cargo, and growth are charged only after successful protected removal.

Native regressions measure actual break-attempt ticks at maximum Work Speed with missing, unsuitable, downgraded, and modified tools. They compare wooden and diamond axe intervals, confirm effective-tool bonuses, and cover logging/farming equipment requirements and resumption. Tunnel excavation also uses the same block-breaking tracker. Fluid removal, wall placement, tilling, planting, and player-operated revival remain separate actions with their own resource and permission checks.


## Healing and mobile storage

`HealingSlime` persists its bag, work command, rest state, cooldown and search origin. Searches inspect loaded entities and chunk block entities on a staggered interval. Live treatment uses Minecraft's healing event path; broken companions use the same transactional revival helper as player interactions, with owner permissions and Forge block protection checked before removal. Costs and activity commit only after success. Failed targets receive a short retry exclusion. Revival retains the common one-health rule.

`WarehouseSlime` extends transport behavior with a capacity multiplier of 8 and a normal 216-slot layout. Creative editing can expand this sparse inventory further. Shared `TransportBag` data preserves existing cargo during specialization; the recipe clears the prior profession command so the warehouse initially waits. `WorkStorage` resolves loaded same-owner or mutually shared warehouse entities and accessible Slime Chests, sorting warehouses first. UUID/block-position targets are revalidated on every transfer; full endpoints are skipped and unreachable endpoints are temporarily excluded. Warehouses collect and supply without the normal deposit phase, avoiding cargo circulation. Storage endpoints remain accessible while following or waiting. The shared work model adds Heal/Store orders and a slimeball supply request without reordering existing enum values. The saved-data schema remains 1; network protocol is **27**.

## Loaded routes and support boundaries

`WorkNavigation` supplies bounded ground paths for working companions. Its region reads chunks through `getChunkNow` and treats unavailable terrain as a solid boundary. Region construction touches only the already-loaded origin chunk; route search can use loaded detours without eagerly acquiring a rectangular set of chunks. Search effort is bounded at 4,096 nodes. Ground companions retain native navigation while following. Guardian flights use `LoadedFlyingNavigation` with the same `LoadedPathRegion`; path targets, route cells and direct-flight rays cannot acquire unavailable chunks.

Healing status distinguishes a full bag from missing materials and rechecks accepted supplies synchronously. Supply APIs cap each operation at 64 slimeballs without mutating the caller's offer. Transport simulates its own remaining space before choosing a supply source. Treatment exclusions retain blocked status while failed targets remain eligible and clear when another companion resolves the need. Revival receives the real item, hand-derived stack and face from player interaction, preserving Adventure permission predicates.

Independent regression fixtures cover chunk boundaries, loaded detours, native healing cancellation, protected revival, competing carriers and exact item metadata. Logging arenas remove unrelated natural trees from loaded neighboring chunks so generated forests cannot substitute a different work target. Long-running actions use observable state transitions rather than fixed discovery deadlines.

`HealingTreatmentBlocked` preserves an unresolved treatment episode across failed approach retries and saves; productive treatment or an empty eligible-target set clears it. This allows the signaling slime delay to elapse even when path attempts restart.

Stored-item restoration reserves its UUID for the synchronous spawn transaction, including Forge spawn callbacks. A reentrant restoration in another dimension fails without consuming its item. The reservation is released in `finally`, including cancellation and exceptions; the existing cross-world entity check still prevents a second release after registration.

Chunk cache keys use primitive longs to avoid allocating boxed coordinates for each inspected path cell. Each `SlimeStat` also initializes its stable lowercase persistence key once instead of allocating it during repeated stat reads.

Every work goal revalidates its pure eligibility predicate at the start of `tick`, since the native goal selector can retain a running goal until a later eligibility refresh. A cancelled goal stops without mutating resources or terrain. Independent native tests tick already-running transport, warehouse and healing goals immediately after command cancellation.

`LoadedBlockView` provides the same loaded-only chunk access for route regions and work sight rays. Missing chunks behave as opaque blocks, so visibility checks cannot load them before a reach check. Each query has its own view: newly loaded terrain is available to the next query. Ordinary ground following retains native sight behavior.

Treatment captures its target before invoking Forge callbacks, since event handlers can change the healer's command and clear transient targets synchronously. A completed heal still commits one matching cost; a cancelled heal commits none. Revival rechecks the working command and the healer's live state after block protection callbacks, before changing terrain.

Protection and pickup callbacks use the same post-event live-command, owner, dimension and lifetime checks before committing further work. Interrupted fluid removal, wall reinforcement, tilling and planting restore their block snapshots without material costs. Collection also stops its route immediately when a callback replaces the work order. Quarry and collection sight rays share the loaded-only view, including diagonal chunk corners within ordinary reach.

Signaling scans recheck the monitoring command between signal emissions. Soggy trail placement rechecks Wait, ownership and live state after its protection callback; it retains normal trail behavior in both Follow and Roam.

Loaded work arenas register completion listeners that release their forced chunks on both success and failure. This keeps a failed fixture from leaving distant chunks active for later tests.

Guardian protection snapshots its exact totem reserve before the native totem-use callback and revalidates live ownership, distance and reserve data afterward. Removal, transfer or reserve replacement cancels the rescue before costs or player effects. A per-guardian guard prevents recursive protection and is released in `finally`.

Mounted flight shares one movement calculation between client travel and server velocity prediction. The server retains that prediction only for native vehicle-packet validation; it clears it before the next physics step, dismounting or wing removal. Both world saves and recovered items store zero predicted motion. Native packet regressions use a registered Survival `ServerPlayer`, include the dedicated-server speed check, and retain rejection of impossible movement.

When a miner remains active outside an unloaded quarry, its work tick clears the retained route before native navigation can inspect retained path nodes. The quarry plan and inventory are retained. A native regression unloads the actual source chunk, verifies no re-acquisition, and rebuilds a route after terrain becomes available.

Accepted placement-event adjustments remain completed field actions, including altered crop age, farmland moisture and immediate sapling growth. Costs commit once after the event, while cancellation retains snapshot rollback. Quarry reinforcement accepts a converted block only when it forms a solid, non-fluid barrier; ineffective conversions restore the inlet and use the ordinary blocked retry delay.

## Additional companion professions

`FishingSlime`, `AbundanceSlime`, `TunnelSlime`, `DragonSlime`, and `MeowSlime` add five professions. All new summon items reuse the shared summon model and have a profession crest. Entity layers render overalls, lyre and laurel, a minecart shell, bat-shaped wings, and a native cat head. The Healing cross has nonoverlapping front faces to avoid depth fighting.

`AdvancedCompanionRecipe` validates shapeless recipes, consumes every ingredient once, and retains stored companion data. Conversion pauses the new profession without discarding shared health, identity, inventory, or equipment. `FriendlySlimeItem.fromSlime` uses the entity's pick result so subclasses recover into their own registered item. Schema version remains 1; protocol 29 requires matching client and server builds.

Fishing generates loot through the native fishing table and a hook context, including native open-water treasure checks, Luck of the Sea, Lure, the Forge fishing event, normal rod wear, Unbreaking, and catch-experience Mending. Survey work is bounded across ticks and reads loaded terrain. Pending catches are serialized separately until usable bag space is available. Five minutes of active work time without a catch triggers another shore search. Pending cargo, a missing rod, and exhaustion rest pause that timer.

Abundance inherits healing and revival and uses a secondary growth action after patient search. Growth uses native bonemeal target and chance checks and the Forge bonemeal hook, revalidating command and ownership before changing terrain. It needs no bone meal inventory. Both healing types return detection multiplier 2. Growth requires surrounding tree-feature terrain to be loaded.

Tunnel inherits mining equipment and the bag but disables the quarry goal, quarry navigation, chunk motion clamp, and quarry fluid-source registration. It clears horizontal 3×3 planes and fills replaceable perimeter cells from the bag. The lining template is a synchronized one-item sample; menu clicks copy the cursor type without transferring items. Extraction, Shift transfers, number-key swaps, cloning, and throwing do not turn the sample into inventory. Template or direction changes pause work. `MATERIAL_SUPPLY` requests use the normal same-owner or mutually shared logistics transaction and a maximum stack-sized delivery. Shared quarry-stair protection is checked before breaking.

Dragon inherits sword, shield, armor, filters, cargo and logistics from combat and uses loaded flying navigation. Its targeted flame follows the same permissions as melee and never creates burning terrain. Work Speed changes its interval. Kill-drop markers let combat workers collect their own loot and request unloading while their execution state remains active; full cargo does not become a combat blocker.

Meow Slime keeps independently synchronized and saved activation and roaming flags. Creepers receive the native `AvoidEntityGoal` with the same six-block radius and speeds as cat avoidance, restricted to active tamed Meow Slimes. Wait remains compatible with the active deterrent. Roaming reserves movement independently of owner following and uses loaded paths.

`SlimeToolExperience` applies work-earned Mending before remaining experience orbs spawn. The working stack retains normal Unbreaking wear and uses Forge's repair ratio; spent XP follows native integer durability-to-XP conversion. Experience-free work and already broken tools cannot repair. Abundance finishes its bounded palette scan before committing a target, prioritizing crops and saplings before grass so early survey sections cannot starve a nearby crop.

Tunnel swimming holds the bore's working height and suspends horizontal travel while clearing, resting, or awaiting supplies. A saved nine-bit `TunnelDrainedFluidMask` tracks cleared cells on the current face, avoiding repeated removal of returning flowing water; new source blocks still require removal. Face advancement and reanchoring clear the mask. Fluid changes use block snapshots and the native placement-protection event, with live command and bucket revalidation before commit. Failed approaches count once per game tick and report `UNREACHABLE` after 400 ticks without distance improvement; this blocked state survives recovery.

Default random strolling respects the waiting order for flying companions as well as ground companions. Dragon movement control clears its target and motion when ordered to wait. Client-side Meow roaming uses the synchronized sitting pose rather than the server-only ordered-to-sit field. The lining template returns copies and denies ordinary slot extraction, including pickup-all initiated from another slot.

Profession regressions cover shapeless recipes, original equipment and identity, waiting defaults, fishing loot and open water, Mending, pending catches, five-minute relocation, growth priority and cancellation, tunnel geometry and protected placement, bucket and lining delivery, flooded travel at default stamina, dragon flight and filtered breath, native creeper avoidance, and combat cargo. Tool-tier and break-cancellation fixtures use isolated quarry chunks; cancellation starts directly above its target and requires an actual denied native event as well as unchanged terrain, equipment, and activity. Client checks exercise profession models, crests, synchronized slot layouts, direction and template changes, and independent Meow controls.

`MeowSlime` uses `meow_slime` for its entity, summon item, and recipe serializer, and `MeowActive`/`MeowRoaming` for saved controls. Only these names are supported; no previous-name registry aliases or field conversions are provided. The save schema remains 1 and the network protocol is 27.

## Lighting, coordination, and teleportation

`GlowSlime` uses expiring, invisible `SlimeLightBlock` nodes with torch-level emission. Each block entity tracks independent entity leases and a firefly deadline. Moving or disabling one source releases only its lease; expired fireflies cannot leave particles on a surviving ordinary light. Nodes occupy air, have no collision, are replaceable and noncollectible, and persist only until their chunk-local deadline. They never replace fluids or existing solid blocks. The custom firefly particle uses the native glow sprite and full-bright rendering.

`CompanionRegistry` is overworld `SavedData`. It stores last-known UUID, primary owner, type, name, dimension, position, health, order/state/reason, location state, and normalized tag. Loaded entities refresh on UUID-dependent phases. Join/leave, inventory observations, collapse, and broken-block discovery distinguish world, item, broken, and removed records. Observations never replace a known live entity record with an item copy. Unobserved historical entities are discovered on load; stored-item locations are last-known observations.

Primary-owner relationship sets provide bounded shared-owner and enemy lists. Delegated management uses a common server predicate; entity metadata mirrors access for client interaction and stores the chosen Follow owner. Relationships do not rewrite primary UUIDs or bypass simulated-player world protection. Follow chooses a living, nonspectating available owner; primary-owner team alliances remain independent of the chosen Follow target. Combat treats designated enemy players as Hostile while preserving PvP, team, and Creative checks. Delegated access applies to management, protection, and riding; cross-primary automatic logistics, monitoring, treatment, and marked-item collection require mutual primary-owner grants. This prevents an inviter from manufacturing access to another owner’s stores.

`CommandMenu` has paged, owner-scoped snapshots with six rows and bounded strings. The client cannot choose an arbitrary primary owner. Actions revalidate menu identity, distance, life, delegation, and primary-only relation changes. Gather spends 25 stamina and queues matching world records; a command slime holds at most one temporary source chunk, with a 100-tick deadline. Safe loaded landing spaces are checked before teleportation. Stored, dead, mounted, and passenger entities are skipped, and arriving workers are paused before and after the relocation release hook, since mining may otherwise resume when its chunk changes. Temporary rally tickets are invalidated on restart; recovery cancels the queue.

Command population checks use undirected connected components of the saved shared-owner graph, separately from management and mutual-cooperation authorization. World records include unloaded chunks and other dimensions; item, broken, and removed records do not consume capacity. `FriendlySlimeItem` reserves group capacity before spawn callbacks for fresh releases, restoration, and revival, rechecks the candidate before insertion, and releases the reservation on every exit. Stored payloads use their original owner. Relationship changes reject merges of two occupied groups, including pending spawn reservations. These transient reservations are not serialized; existing saved records retain schema 1. Older saves with duplicate command slimes retain their entities and reject additional releases. `CommandSlime.setWaiting(true)` also cancels an unfinished rally and releases its source ticket; failed teleports leave the original command unchanged.

Roster presentation resolves profession-specific labels from the saved type: Abundance displays Nurture for HEAL, and Logging displays saplings for SEEDS_MISSING. The stored order and reason identifiers remain unchanged.

`EnderSlime` persists two synchronized equipment stacks: `EnderPearls` (up to 16) and `EnderDimensionalSeal` (one). Only a pinned, living anchor with pearls holds its UUID-based ticking Forge ticket. `CompanionRegistry.ChunkLoading` validates persisted tickets before entities load; missing legacy flags revoke implicit loading without deleting companions or links. Slot changes, the last pearl, recovery, collapse, and dimension transfer release obsolete tickets. Native dimension transfer also releases tickets in `removeAfterChangingDimensions`, which bypasses the ordinary `remove` hook. `EnderChunkLoading` checks tracked players, Overworld spawn coverage, vanilla forced chunks, and serialized Forge block/entity ticket ownership, excluding exactly the destination anchor. Pearl cost is decided and revalidated before arrival and committed only after successful travel; cancellation retains stamina and pearls. Cross-dimension teleporter use, native entity travel, and companion relocation require the equipped reusable seal. Same-dimension use remains available at externally loaded anchors without pearls. Player travel fixes its departure dimension before callbacks; a changed departure cancels the request without charging the anchor. Live entity NBT reloads reconcile pinning, equipment, gravity and chunk tickets immediately; unadmitted spawn and transfer copies remain free of registry and ticket side effects. Anchor limits and existing owner, safety, and event checks remain effective.

`AttentionSlime` applies one permanent +50% total Movement Speed modifier with a fixed UUID, restoring it after specialization loads without stacking it on recovery. Roam retries destination selection at most every ten ticks and falls back to local ground if the owner is distant or has no reachable nearby ground. It prevents farmland trampling, and earns growth only from real movement. Its partial activity timer is serialized. All ordinary and colony companion types share the common initial Wait state. Protocol 29 retains bounded roster actions and client snapshots and adds server-authorized tunnel-size controls; payload schema 1 remains compatible with absent new fields.

`CompanionBase` centralizes Friendly/Attention ingredient alternatives and specialization data copying. Attention-only NBT fields and its fixed-UUID speed modifier are removed from the copied payload; attribute base values, invested levels, identity, and common growth data remain. The input stack is never modified by recipe previews. All fifteen direct specializations share this path; Guardian Angel requires Healing, and higher professions keep their original inputs, and Attention does not accept itself. JEI uses the same alternatives as the native recipes.

## Work preview snapshots and warning delivery

Large Creative ranges are bounded only when constructing display geometry, with a small floating-point margin below the packet’s 65,536-block extent limit. Oversized farm outlines are intersected with that display region. Stats, registered plots and job progress remain unchanged.

`SlimeWorkArea` builds a read-only snapshot from profession getters without serializing entities or advancing field surveys. At most eight validated boxes or radial bounds travel in the server-to-client `WorkAreaPacket`; the server requires a held Slime Scanner, management permission, same dimension, life, nonspectator state, and eight-block range. The client associates the snapshot with both entity ID and UUID, clears it on tool/identity/dimension changes or expiry, and draws bounded outlines without depth writes. Coordinates are made relative to the camera before float conversion. Requests do not mutate work orders or load chunks. Quarry previews share the starting-floor clamp used by quarry initialization, including minimum and maximum build heights. Active farming and logging use their registered home after relocation, so worker travel does not shift the preview; a waiting farming slime previews the position of its next survey.

`SlimeWorkWarning` localizes type, custom name, and every common blocking reason. Tool requirements retain the miner’s exact pickaxe/shovel distinction, and logging saplings remain separate from farming seeds. Fishing warnings distinguish a missing suitable spot from a failed approach; storage warnings include both chests and warehouses. A successfully spawned automatic signaling slime signal revalidates the worker’s blocked state and cooperation before messaging authorized online owners. Existing per-worker signal cooldowns also throttle chat. World saves retain commands; item release and revival override only the command to Wait. Network protocol 29 requires matching client and server builds; data schema 1 is unchanged.

## The Slimic

`ModWorldBlocks` maintains a shared catalog for 54 building entries, 31 decorations, and one functional station. Building and decoration entries have separate Creative tabs; functional stations appear in the main Slimic tab. Models use Forge render types and tint indices; soil and construction shapes retain native tool and placement behavior.

`slimic:the_slimic` uses a fixed `slimic:damp_plains` biome with an Overworld noise router, local surface rules, native ores and caves, and three bounded terrain/vegetation/ruin features. Spawn-placement additions apply only to native Slimes in this dimension and biome. Ruin loot contains a localized written record.

`SlimicPortalShape` validates vanilla Nether frame dimensions using vanilla slime blocks. `SlimicPortalTravel` handles activation, transactional Forge placement events, safe exits, native dimension events, cooldowns, and waiting arrivals. Per-dimension `slimic_portals` saved data indexes known exits; every candidate is checked against the live frame. It neither creates portal items nor modifies vanilla Nether or End portal behavior. Companion data continues to use the ordinary entity save/load format.

Cross-dimension Gather retains its source until the pending destination copy has joined and passed identity, ownership, authorization, landing and unchanged-source checks. Admission failure discards only the provisional copy and restores the source registry observation. Pending copies cannot be recovered. Normal and bonded recovery remove the original before lead-drop callbacks, preserving stored-item receipts and identity reservations. Shared teleport landing checks reject damaging blocks touching the body or feet.

If a collapsed companion cannot place its broken block and its recovery item is rejected, an ordinary companion remains alive at one health. A controlled colony may be removed only when its current damaged fallback is already recoverable from the bonding station.

Companion portal travel inserts and validates the destination copy before removing the original. Spawn vetoes or changed source state discard the copy and preserve the original; removal hooks release source lights, rally tickets, and anchor tickets. Player travel resolves a safe landing before native transfer. A temporary listener runs at the lowest Forge event priority and rechecks permissions and landing safety before the native API sends a respawn packet or removes the player. Slime portals and Ender travel share this player guard.

World GameTests cover geometry, protection rollback, item use, saved exits, all companion professions, tools, loot, and waterlogging. Client checks include models, tinting, the separate Creative tabs, dimension travel, translated records, and guidebook rendering in all four supported languages and English fallback. See [The Slimic](THE_SLIMIC.md).

## Forgotten Orders

Forgotten Orders uses separate `AncientSlime` hostile entities and a `RoyalGuardSlime` combat companion. Ancient cores store a versioned identity, role, name, and bounded veterancy, never a `SlimeData` snapshot. `ForgottenOrders` persists redeemed core identities across dimensions and reserves in-flight restorations against callback re-entry. Pedestal restoration validates materials and held state again after constructing the result, then replaces the held core and consumes one catalyst and one slimeball. Ruin cores use seals; mine cores use Old Command Fragments and require the Overworld. Normal companion summon admission remains responsible for the resulting item's release and population limits.

Network protocol 29 requires matching clients and servers for the new entity types and synchronized ancient-origin appearance. Existing saved companions default to ordinary appearance when the added field is absent; their payload schema remains version 1.

Pedestal restoration respects both Forge block-use and item-use vetoes. Ancient carriers may route up to twelve blocks from their ruin origin; the guards' smaller idle patrol radius must not interrupt a valid delivery route.

Ruin generation confines structures and native entity insertion to The Slimic's current chunk. Patrol navigation and sight checks use loaded terrain with bounded path effort. Carriers require two block entities with their own ruin UUID; opening a depot claims it. Royal interception runs on final living damage, admits one eligible guard, prevents recursion, respects canceled guard damage, and reuses combat ownership and target checks. `ModEntities.companionTypes()` excludes hostile remnants from companion item, menu, and recovery contracts. See [Forgotten Orders](FORGOTTEN_ORDERS.md) for player-visible behavior.

Optional JEI integration requires JEI 15.62 or newer and registers `slimic:slime_restoration` as a display-only category for the six station interactions, with explicit ingredients, companion outputs, and an intact-station catalyst. Core subtypes depend only on the validated role, so natural identity, name, and veterancy do not prevent recipe lookup. Each role also keeps its information page. No workbench recipe or common-side JEI dependency is introduced.

## Collapsed mines

`CollapsedMineFeature` plans chunk-contained tunnel or quarry sites, validates the entire dry terrain volume, and only then writes blocks. Quarry geometry uses `QuarryPlan`; its profile is registered through the server executor because feature placement can run on a generation worker. Mine Broken Slime Blocks use a mineralized state and an `AncientCore` payload separate from owned `Slime` snapshots. Core loot replaces block-item loot; each persisted ancient identity is restored once through `ForgottenOrders`. Earlier empty remains stay empty.

Tunnel height follows the actual rotated surface entrance. A surface remnant keeps that entrance within the existing ruin feature's block-entity collision checks, preventing later surface ruins from paving over a covered tunnel. Quarry treads use stable cobblestone rather than unsupported falling terrain.

Loaded work navigation includes the horizontal detours of perimeter stairs when estimating routes to a different height. Its search remains limited to 4,096 nodes and loaded chunks. This allows carriers to collect from deeper excavation layers and return to storage without changing terrain or bypassing normal reach checks.

Ancient work companions subclass the existing quarry and tunnel workers. Ore selection changes only active-layer priority; a stamina override halves actual work expenditure. `blockWorkMultiplier` applies only to effective tools, with ancient tunnel masonry configured by `slimic:ancient_masonry`. Mine-remnant work is bounded to one saved 3×3 face and nine blocks, and revalidates terrain, protections, entity state, and griefing after Forge callbacks. See [mine recovery](MINE_RECOVERY.md).

`NextGenerationTunnelSlime` reuses Tunnel work, safety, supply, loot, and growth contracts. Its separate phase/cursor pipeline checks at most 128 cells per tick across the lining ring, n×n face, and final recheck. A bounded BitSet preserves drained-fluid cells beyond the old nine-bit mask; ordinary Tunnel saves retain their original keys. `ModNetwork.TunnelSize` validates the live tuner menu and ownership before changing a positive, dimension-height-bounded size. Size and direction changes wait; native world saves preserve partial progress.

The complete Next-Generation Tunnel face and lining must fit build height and the world border before any face work begins. Lining positions are calculated by cursor rather than allocating the full perimeter per tick. A newly obstructed destination returns movement to bounded face inspection; obstructions outside the active face retain the normal unreachable-approach reporting. Multiple workers sharing terrain still validate the actual block before collecting drops or applying tool wear.

Precision surveys inspect at most 32 loaded section palettes and 1,024 candidate blocks per tick. Ore-free sections are skipped without visiting every block or acquiring chunks; palette checks honor the live selected ore tags. A movement timeout interrupts its route while preserving the pending cell, so a partially opened descent can retry. Shared Wait, Follow, and stamina-recovery states use the same localized wording across professions. The Next-Generation Tunnel bag page renders its own status without falling through to base specialization help. Version-1 precision saves that lost their pending descent recover only an adjacent, already cleared, safe next shaft level; unfinished or unsafe levels remain blocked.

## Assembly and colony persistence

`AssemblyChamberStructure` and `AssemblyChamberPiece` register a native underground structure. `AssemblyPlacement` shares bounded placement prediction and disk-only metadata inspection with kingdom maps, using its own destination identity and loaded reference cache. Sanctum placement has priority; mines and small ruins reserve assembly footprints. Generation never rewrites old full chunks.

`AssemblySites` owns one encounter, body snapshot, `ColonyCohesion` allocation pool, participants and personal receipt deliveries per site. Free, allocated and lost cohesion sum to 100. Native entity reloads validate site/encounter/sequence identities, and a missing loaded body is recovered only after all arena entity chunks have naturally loaded. No chunk tickets are acquired. Reward and payment receipts are checkpointed with player state before final world acknowledgement.

`AimlessColony` appends attack identifiers without reordering older values. Protocol 29 also synchronizes the swing direction and recovery motion. A sweep retains its 32-tick warning, then crosses the fixed sector over ten ticks; passed ground no longer deals damage. Each target receives at most one damage attempt per attack, including canceled attempts. Charges test the swept body footprint after each actual movement step, including the final one, and clear horizontal velocity before ordinary travel. Charges and sweeps settle for twelve harmless ticks before any fully warned follow-up. Client poses use the committed heading, while the moving sweep edge and particles follow the same angular progress as damage. Eruption retains its fixed warning geometry. Attack selection considers distance, health phase and the persisted `LastAttack`; old saves without that field remain valid. Reload discards unfinished warnings and momentum and preserves a minimum 20-tick action delay. Movement, fragment landing and forced movement check the full collision box, supporting floor, world border and loaded chunks; sight checks use `LoadedBlockView`. A saved `ReturningHome` latch keeps retreat active from beyond radius 38 until the boss returns within radius 8. Already announced attacks finish, while new attacks and unannounced follow-ups wait.

The native `slimic.colonyMotionOnly` client check captures charge, both sweep directions, recovery, and other preparation poses with minimal particles. It verifies tracked state, finite model transforms, frame resets, and alignment of eyes and cores with the shell. Active sweep samples pass through the renderer’s heading and scale to check the gel arm against the server’s sweep direction and eight-block radius. The extended arm also remains visible when the torso is outside the viewport. Model-space sweep rotation accounts for Minecraft’s mirrored X axis; charge and recovery poses interpolate compression, extension and rebound without changing combat timing.

`ColonyMembers.Schema=3` contains two unspawned native companion records and an `IndividualGrowthLevels` integer array with each member’s own cost tier. Schema 1 and 2 records remain readable. Older records without that ledger start from their currently saved tiers; a lower tier already overwritten by Schema 2 is no longer available to reconstruct. `BoundTransportSlime` and `BoundCombatSlime` reuse profession behavior; `ColonyBag` delegates to the real sparse member inventories. Mutable vitals, equipment and invested upgrades update those records.

Unused points use the arithmetic mean of both member balances, rounded down. While bound, the native `EarnedPoints` cost uses the higher tier. Each point actually earned through colony activity advances both individual counters once; spending points or editing the unused balance does not advance them. The shared balance, progress and current colony cost remain mirrored into the member records, while `IndividualGrowthLevels` preserves their separate costs for unbinding.

Partial activity is stored in half units as `2 × ActivityProgress + ActivityHalf`. Binding averages the members’ progress fractions toward their respective next points, then scales the result to the colony’s higher activity requirement. Unbinding scales the colony’s remaining progress fraction to each member’s own requirement. Conversions round down to half-activity units; differences between activity requirements do not award refund points.

One shared point upgrades each member below the selected stat’s cap; a capped member stays unchanged, and two capped members reject the upgrade without spending. Activity adds one half unit per ordinary unit while any stat used by the role can still upgrade both members. Otherwise it adds two half units, the normal activity rate; fully capped colonies can continue banking points. Reaching one cap alone cannot restore full growth while another stat remains jointly upgradeable.

Unbinding copies the remaining shared point balance to both members, restores each saved individual cost tier, and converts the remaining progress proportionally. Saved colony data retains both individual tiers and fractional activity across recovery and reload. The ordinary point-cost formula and direct Creative stat aggregation remain unchanged.

`ColonyBondingData` uses a separate journal and release epochs. Both originals are provisional during unbinding; identity, ownership, admission, landing and station contents are checked before committing. Native world checkpoints and recovery records cover interrupted transitions. `CompanionRegistry.Location.BOUND` reserves the originals without presenting them as active world companions. Central restoration, admission, capture and broken-companion hooks validate the same authority.

Transport colonies keep one saved `BoundTransportSlime` as authority and reconstruct a temporary `TransportColonyBody` for the second moving body. The helper is not a separate companion record and never serializes another inventory. Both task runners use the same `ColonyBag`, vitals, order and growth accounting. Worker reservations prevent duplicate assignments; supply reservations keep another body from unloading or using an item promised to an active delivery. Each transfer revalidates the live request and available space. The link range is twice Detection Range, bounded to 16–64 blocks. Reuniting the bodies checks safe loaded space without requesting chunks. Wait, capture, removal and dimension transfer stop helper work; world reload reconstructs it from the authoritative colony.


See [The Aimless Colony](AIMLESS_COLONY.md) for bounded configuration and [Colony Bonding](COLONY_BONDING.md) for shared training, equipment and point averaging and copying rules. JEI exposes four display-only bonding/unbinding recipes. The new native menu uses exact numeric synchronization for previews; optional integrations do not control gameplay.

Combat colonies reconstruct a non-saving `CombatColonyBody` under the same `LinkedColonyBody` authority checks. Each body attacks and defends with its own member equipment; `SlimeMenu` exposes six equipment slots. Damage to the second body commits through the saved representative, using the struck body’s armor, shield facing and knockback. Helper attacks retain their physical damage source and attribute loot and encounter contribution to the colony owner. `ColonyAssistCharge` schedules one six-tick wind-up and collision-checked joining leap with a 100-tick cooldown. It revalidates commands, ownership, target, weapon, stamina and loaded terrain every step. The attack multiplier is temporary and always removed; active leaps are discarded on reload while their cooldown persists. Protocol 29 covers the helper type, synchronized motion and six-slot menu. Existing colony identities and both equipment records remain readable.
