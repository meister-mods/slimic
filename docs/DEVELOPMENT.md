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

Format all Java sources with Google Java Format before completing a change. Version 1.37 requires Java 21 to run; the mod still compiles and runs on Java 17.

Run `build` and `runGameTestServer` after gameplay changes. Coverage includes crafting, normal spawning, recovery and release, health and other traits, ownership, full inventories, blocked placement, Creative use, persistence, following, and death behavior.

Tests use a structure loaded one block above its marker, so the fixture floor is at relative Y=1. Keep world fixtures separate from production assets.

## Stats, mining, and integrations

Shared stat levels are synchronized as entity data. Server-side menu checks enforce ownership, distance, available points, and upgrade limits. Rename packets apply only to the sender's active Slime menu. Health upgrades change the maximum without healing the companion.

Menu opening data includes the companion type so a missing client entity can retain the exact slot layout until the screen closes. Network protocol 12 requires compatible client and server builds. Hidden bag pages reject transfers, Capacity changes clamp the current page, and in-place slot mutations notify the bag so partially unloaded miners resume.

Mining state, bag contents, stamina, and activity progress use the standard entity save/load hooks. The equipped pickaxe and shovel use the native main-hand and off-hand equipment slots, keeping client rendering and item damage synchronized. Ordinary mining is bounded by one completed solid block per tick, searches process a limited number of chunk positions, and no neighbouring chunk is loaded for target discovery.

The crafting serializer copies the Friendly Slime's stored traits and the exact pickaxe stack into the result. JEI is a compile-only API dependency with optional development runtime support. Use `./gradlew runClient -PenableJei=false` or `./gradlew runGameTestServer -PenableJei=false` to validate operation without JEI. Common game logic does not reference JEI or client classes.

## Transport and storage

`BagSlime` shares capacity-aware storage, collapse preservation, save/load, and recovery between worker types. Mining retains the `MiningBag` NBT key for existing saves; transport uses `TransportBag` with up to 54 slots. Transport capacity multiplies the common base and every upgrade by two. Its menu has no equipment slot and checks the active page on the server.

Transport tasks query loaded chunks for Slime Chest block entities and same-owner workers inside the release-centered detection area. Transfers simulate available capacity before moving items on the server thread. Work requests are revalidated before transfer so concurrent carriers cannot duplicate tools or replace a tool already supplied. Travel targets are transient; cargo and orders are persistent, so interrupted jobs can be selected again after load or release.

Slime Chests use a standard 27-slot container menu and Forge item capability, with normal inventory persistence, locks, comparator updates, and separate block/content drops. Regular chests are excluded from logistics.

Validation covers all Capacity levels, both bag pages, ownership, full and partial destinations, concurrent tool supply, tool data and breakage, mining resumption, chest persistence/automation/drops, and transport recovery. Client checks cover both specializations, page synchronization, chest menus, model resources, and optional JEI recipes.

## Quarry, tuning, and herald persistence

Mining uses native main-hand pickaxe and off-hand shovel equipment. `MiningQuarry` retains the chunk's entry elevation and current layer; same-chunk release resumes it, while another chunk creates a new plan and resumes work. The perimeter plan reserves two-block-wide full-block treads with flat corners. Repair consumes items from `slimic:quarry_supports` and honors placement events. Target scans are bounded to 256 positions per layer and a limited number of empty layers per update.

Dye IDs and custom work stats use synchronized entity data. Native attribute tuning edits base values and preserves modifiers. The creative-only tuner validates the active menu, held tool, dimension, distance, finite values, ranges, and occupied bag slots on the server before applying any changes.

Herald recipes store the exact rocket stack. Signals are vanilla firework entities marked for damage suppression. Workers save their continuous blocked duration and episode identifier; heralds save warning history and cooldowns so recovery cannot repeat an existing warning. Server configuration supplies the default delay.

Client rendering applies a brightness-preserving dye shader to the original slime texture while leaving equipment unchanged. Optional JEI previews include every specialization. Validate client shader loading, translated screens, tuner packets, creative defaults, two tool slots, and JEI enabled/disabled alongside dedicated-server tests.

Quarry navigation uses a bounded 4,096-node search with a 128-block route budget inside the already-loaded work chunk. The route budget is independent of Detection Range; tool actions retain their actual range and sight checks. Candidate standing positions are searched together using the actual body footprint for support. Valid paths survive hops, and tight waypoint tolerance prevents clipping staircase corners. Travel timers reset on movement, and failed support routes retry after a short cooldown. Regression coverage includes default-stat staircase navigation, wall detours, blocked repairs, and resumption after clearance.

## Combat and cleaning

`CombatSlime` keeps sword, shield, and armor in native equipment slots and shares the normal capacity-aware bag. Its synchronized target mask has six independent bits; owner menu checks protect all changes, and current targets are revalidated after a filter or order change. Native melee, armor, shield timing, enchantments, durability, and Forge events provide combat behavior. Serialized settings and cooldowns survive recovery.

Each standard armor material has its own registered item, preserving native repairs and netherite fire resistance. `SlimeArmorItem` stores an original chestplate in `SlimeArmorBase`. Outer item damage, enchantments, display, and attribute data remain authoritative after conversion. Material attributes and durability are delegated to that chestplate. Recipes accept registered chestplates and swords, and JEI builds corresponding previews dynamically.

`CleaningSlime` multiplies effective Detection Range by five while leaving the common base and tuner values unchanged. Target selection is limited to loaded entities around a fixed release origin. Transfers simulate bag space, preserve partial stacks, honor pickup delays/ownership/events, and run on the server thread. A 3,600-tick attempt window selects an alternative or retries; saved timers pause in item storage. Working goals reserve movement while idle to prevent wandering. Transport uses the shared full-bag and replacement-tool interfaces.

Validation covers target defaults and toggles, ownership, native combat and defenses, material/NBT conversion, cargo and retry persistence, collection bounds, idle behavior, actual pickups and returns, full bags, tool delivery, optional JEI, and translated client controls. Combat tests run after the Peaceful-difficulty test batch.

## Visual asset conventions

Summoning items share the `slime_summon_base` model and Friendly Slime texture. Child models select a profession crest from native item artwork; the common model places it in a small frame over the base. Other new items include a dedicated Minecraft-style 16×16 pixel texture with a limited palette and clear silhouette. Use nearest-neighbor scaling when preparing pixel art. Leather armor keeps its tintable base separate from the untinted slime badge. Block items inherit their textured block model so inventory and placed appearances stay consistent. Companion accessories use separate textures and retain their colors when the slime is dyed.

## Work-state restoration

`WorkDimension` records the dimension associated with a companion's work state. A successful Forge teleporter transfer runs the release hook at the destination. The first server tick also checks the saved dimension before AI runs, covering native dimension-restored copies. Mining uses dimension identity alongside chunk coordinates; transport and cleaning rebuild their fixed origins while retaining orders and cargo. Same-world loads keep their original work areas. Saves without this field adopt the dimension in which they are loaded. Specializations that override `onReleased` must call its base implementation so the recorded dimension follows the new work area.

`TransportCooldown` persists the remaining Work Speed interval, bounded to the supported 0–200 ticks. Transient paths and targets are reselected after load. Transport only replaces paths while grounded and measures missing horizontal or grounded vertical progress for its travel timeout, so hopping alone does not mask a stalled delivery.

Combat pursuit and cleaning returns also measure 400 ticks without horizontal or grounded vertical progress, excluding in-place hops. Progress clears a resolved blocked state. Valid paths survive hops, moving combat targets refresh the destination, and new return trips reset their own stall counter. Regression tests cover slow travel, stationary companions, recovery of movement before arrival, and exact cleaning returns.

Regression coverage includes cross-dimension quarry restoration at matching coordinates, portal and command-style restoration, cancelled travel, legacy saves, same-dimension origins, slow advancing deliveries, stationary carriers, and cooldown preservation after recovery.

World-scale worker tests use independently loaded, cleared full chunks. This keeps quarry targets and cargo-transfer assertions independent of small template placement and neighboring terrain.

## Shared work contract and data compatibility

`WorkStatus` separates the assigned `Order`, execution `State`, blocking `Reason`, and support `Request` set. Existing specialization states remain the presentation and persistence format; each type maps them to the common contract. Follow/Wait suppress requests. The native sitting goal cannot claim movement while a work order is active, including when the owner is unavailable. Logistics consumes cargo/tool requests, and heralds consume `BLOCKED` rather than specialization enums. Equipment and ownership are revalidated at the transfer boundary.

`WorkSearch` shares immutable loaded-chunk candidate snapshots for 20 ticks between carriers. Candidate membership is cached; requests, range, ownership, lock state, and capacity remain live checks. Expired regions are removed during refresh and world unload clears the cache. Missing chunks return empty results without acquiring tickets. Idle carrier searches use entity-dependent phases. Selected storage is checked against the current work radius before both deposit and tool pickup, including range reductions during travel. Regression coverage includes 100 repeated snapshot queries and 32 simultaneous deliveries; these are correctness/load scenarios, not hardware TPS benchmarks.

Recovery uses 2% of maximum stamina at existing recovery opportunities. Delivery activity is configurable (default 4); actual owner-following movement and active herald monitoring provide additional growth paths (default 100 eligible ticks per unit). Their partial timers are serialized and never advance in item storage. Failed actions and idle/wait orders grant no growth. All work specializations initially select Wait, including when saved order data is absent. Recovered companions retain their explicit orders, subject to the documented mining relocation rules.

`SlimeDataVersion` currently equals 1 in companion world and item payloads. The migration entry point adopts unversioned/zero-version payloads without removing existing fields. Unsupported future or malformed stored-item versions fail release without mutation. Existing specialization NBT keys and accumulated points remain unchanged. Add explicit migration steps before changing stored field meanings or layouts.

## Reliable alerts and menu synchronization

Herald warning history keeps the latest signaled episode for each worker UUID without truncating large groups. World saves and recovered items preserve the same records; malformed UUIDs and invalid episode values are ignored on load. A new warning does not remove another worker's history.

Player target selection checks the server PVP flag and the loaded owner's player-damage permissions before both target acquisition and melee damage. Native team/alliance exclusions remain in effect. Tests restore server PVP and scoreboard fixtures after checking each rule.

Native container-data packets carry signed 16-bit values. `IntContainerData` encodes each logical integer as two halves and appends an initial-snapshot marker. Management and tuner menus share this transport, retaining large effective attributes and clearing old high bits after a smaller update. Editors wait for the marker before loading initial values. Protocol 12 includes this layout, riding controls and the current management types. Validation covers real native packet serialization, both menus, large cleaning range modifiers, value reductions, and native clients with and without JEI.

## Saddle equipment and riding

`SaddleSlime` uses native main-hand saddle and chest-slot elytra stacks for equipment synchronization, saving, recovery, and collapse preservation. The equipment-only menu contributes two companion slots before the player's inventory. Native crafting copies stored Friendly Slime data and the exact initial saddle; recovery emits the specialized item without reapplying initial equipment.

Only the owning player can occupy and control the single seat. A priority movement goal suspends companion AI during a ride. Native ridden-input/speed hooks provide steering and ground jumps. Winged travel uses collision-aware entity movement and the rider's view, with normalized combined directional input, hover, and Movement Speed scaling. Flight is available to Survival riders and uses normal vehicle movement packets. The client sends ascent/jump and boost intent; the server requires the sender to be the current owner rider of that exact entity. Input expires after 30 ticks without refresh and clears on dismount.

Winged riding supplies transient no-gravity for movement and native vehicle validation. Saving removes that computed flag while preserving explicit native gravity overrides, so recovered mounts do not hover unattended. The saddle has a dedicated model and leather atlas; paired side-wing geometry uses a dedicated opaque feather texture and animates independently while flying.

Protocol 12 carries ascent and boost in the riding input message. Validation includes crafting and actual release, owner checks, equipment Shift transfers, mounted recovery, unusable wings, explicit versus transient gravity, and native Survival client steering, jumping, ascent, hover, boost, pitch, server vehicle synchronization, and Shift dismount with and without JEI.

## Growth thresholds and quarry consistency

The shared point cost starts at 32, increases by 64 for each earned point, and caps at 4,096. Activity loading retains the supported remainder range and settles earned thresholds before saving the remainder, rather than clamping old activity to the smaller new cost. The same settlement handles live work awards. Existing points, investments, earned-tier counts, and companion traits remain authoritative; normalized saves do not repeat awards.

An explicit Mine command rebases a previously anchored companion when its current chunk differs from the work chunk. Same-chunk commands retain the saved plan. Travel progress uses 0.05 horizontal blocks or 0.5 grounded vertical blocks, preventing slow but advancing approaches from timing out solely because they cannot cover half a block within 400 ticks. Mining tracks the current block state and clears accumulated damage and the crack overlay when it changes.

The transport pack is placed behind the outer slime cube; side and rear straps also clear both body surfaces. This removes the coplanar inner-body/pack face and overlapping outer surfaces while sharing the companion's pose and squish transform. The Creative tab emits companion items together, then storage, armor in material order, and the tuner.

Regressions cover the initial growth thresholds for all companion types, conversion of 200 saved activity without lost or repeated awards, starting a new quarry after following into another chunk, an advancing low-speed miner, and block replacement with exact drops and tool wear. Client checks inspect the pack from multiple angles and record the actual Creative item order.

## Threats, collapse, and active growth

The entity-join hook adds a companion target goal to hostile `Enemy` mobs, excluding creepers. Their native attack goals retain damage and projectile behavior. A higher-priority escape goal responds to a real attacker for non-combat companions and yields to rider control. Hop propulsion stays at a common base while the movement attribute sets the grounded delay; ridden ground travel uses the same principle.

A lowest-priority uncancelled `LivingDeathEvent` preserves the companion before native equipment, bag, or experience drops. `BrokenSlimeBlockEntity` stores its specialized recovery stack under `Slime`. A replaceable death cell becomes a block; an occupied cell produces its block item without overwriting terrain. Native block loot copies this payload to `BlockEntityTag.Slime`, retaining it through mining and placement. Revival uses the normal spawn validation and UUID checks, sets current health to 1, clears death/fire/motion state, and consumes the block and ball only on success. Failed spawning restores the block entity.

Real non-allied damage and shield blocks grant at most one defense activity per 20 ticks. Owner and environmental damage grant none. Escape and ridden movement use the configured following interval. Combat shields face the target using entity yaw and retain at least a ten-tick attack interval so the native activation delay can complete.

Heralds retain per-worker repeat cooldowns in `HeraldWarningCooldowns`, separate from the episode history. They repeat every 200 active monitoring ticks after the first signal, pause in storage, and stop signaling resolved or out-of-range workers. Legacy `HeraldCooldown` loads into existing warning records.

Riding input carries ascent and boost. Boost state is synchronized; the server spends 0.1 stamina per airborne tick, 0.5 per ground-sprint tick, and 0.75 per boosted-flight tick. Empty stamina enables synchronized controlled descent at 0.12 blocks per tick, with ascent suppressed, no airborne recovery, and a grounded 25% recovery latch before flight or sprinting resumes. Equipment and stored companion schema remain compatible; `SlimeDataVersion` stays 1.

## Surface workers

`FieldSlime` shares a single native main-hand tool, normal bag, stamina recovery, loaded-area checks, protected harvesting/placement, and FARM/LOG work orders. Full inventories request CARGO_PICKUP; an empty broken-tool slot requests TOOL_SUPPLY. Missing seeds use SEEDS_MISSING without creating a tool request. Successful actions award activity only after the world edit succeeds. Networking uses protocol 12.

`BlockSurvey` copies palettes of matching loaded chunk sections, then inspects at most 512 cells per worker tick. Farming activation freezes the existing farmland selection; serialization finishes any pending snapshot so recovery never loses recognized plots. Plot coordinates and crop identities are persistent. Tilling uses the equipped hoe's Forge tool action and native placement cancellation; block removal uses owner-attributed survival break events and simulates all drops before changing terrain.

Lumberjack discovery inspects at most 64 connected candidates per tick, limits tree plans to 512 harvest blocks and 4,096 visited cells, and never loads chunks. Stump-ground and natural-leaf checks exclude decorative structures. Matching saplings are reserved before cutting and serialized separately from transportable cargo. Log queues and replantings resume after world reload; release establishes a new origin and returns unused reservations. Both roles multiply effective detection by two while tuner fields retain the common base.

Mangrove discovery follows raised trunks through central roots and air or water gaps to valid planting soil. Only the planned central root blocks join the harvest queue. The optional `LoggingRootColumns` coordinate array identifies those root entries separately from logs, so replacing a planned log with a root does not authorize harvesting that replacement. Loading accepts at most 512 entries that still belong to the saved queue; absent data means an empty set and retains schema 1 compatibility. Replanting uses native planted propagule age and waterlogs only existing source water.

Native regressions cover exact specialization tools, frozen plots, same-crop replanting, soil restoration/exclusion, farmland trampling, tall-tree felling and replanting, decorative leaves, seed requirements, full inventories, cancelled edits, tool requests, and recovery. Client checks cover hats, held tools, management layouts, both JEI recipe families, and all thirteen profession crests with JEI enabled and disabled.

## Companion care, tools, guardians, and animated storage

`SlimeTunerItem` is the Survival management tool; its shapeless recipe uses a slimeball and an iron ingot. `CreativeSlimeTunerItem`, `CreativeSlimeTunerMenu`, `CreativeSlimeTunerScreen`, `creative_slime_tuner`, and `creative_tuner` identify the Creative editor. Protocol 12 carries its renamed packet and adds the guardian menu layout and herald manual-fire command. Management validates ownership, alive state, dimension, distance and a held ordinary tuner on the server. Healing and washing consume items only on an actual change; capture retains priority.

`GuardianAngelSlimeRecipe` preserves the input companion payload and exact totem in `InitialGuardianTotem`. The guardian stores `GuardianTotems` and its attack cooldown. A stable permanent maximum-health multiplier applies tenfold scaling without compounding on reload. The native health limit bounds Creative base input to 102.4. Flying navigation and move control use a dedicated flying-speed attribute derived from common Movement Speed. Retaliation targets and beams are transient. A lowest-priority damage event selects the owner's attacker; sight, alliance, range and resource checks gate beams. A lowest-priority uncancelled death event selects one nearby owned guardian, invokes Forge totem-use permission, consumes one reserve and restores native-style effects. Held native totems resolve before death. Beam ribbons emit both windings for the native lightning render type.

The reserve slot accepts up to 64 matching totems. Manual withdrawal takes one; Shift-click distributes into native one-item inventory slots and retains overflow. Container mutations mark synchronized reserve data dirty. Native menu clicks, item transfer, saves, owner damage, flying retaliation and lethal protection are covered by GameTests and client checks.

Surface workers plan collision-free standing cells within action reach instead of targeting occupied logs or crops. Candidate stands and path endpoints use native `WalkNodeEvaluator.getFloorLevel`, including reduced-height collision surfaces such as mud. Lumberjacks cache unavailable roots briefly, keep the specific missing sapling and stump count in synchronized data, continue searching other species, and invalidate discovery caches after bag changes. Farming and lumberjack have separate localization namespaces. Miners exclude wood/axe/hoe targets, revalidate path endpoints, retain native step approaches, and measure real progress toward the target so stationary hopping cannot postpone retries indefinitely. Escape navigation keeps its route during airborne hops and replaces it only with a grounded path whose endpoint increases distance from the attacker.

Slime Chest keeps its block entity inventory, item capabilities and block drops. A native opener counter drives chest sounds and block events; a client lid controller interpolates the angle. The block entity renderer uses cropped original base/lid models around a rear hinge. The closed item remains a baked 3D block model.

## Boundary regression coverage

Management rechecks Spectator status on every command and inventory interaction, including menus opened before a game-mode change. Slime-ball healing compares health before and after the Forge healing hook; canceled or zero healing produces no consumption or healing effects.

Lumberjacks defer unreachable or pickup-protected material entities for 200 ticks and continue other work. Missing-sapling indicators are cleared when their root or natural-leaf witness disappears, after cargo changes, or when the work origin changes. These transient search caches are rebuilt after loading. Guardian visibility checks include the beam's bounding box as well as the companion body.

`BroadAuditGameTests` adds independent cases for save sanitization, unsupported schemas, immutable recipe inputs, healing hooks, permission transitions, invalid tuning payloads, native slot limits, capacity/recovery scaling, explicit mining activation, loaded-only searches, relocated farmland, obsolete sapling requests, unreachable material pickup, simultaneous transport, pickup cancellation, signal-spawn cancellation, target revalidation, totem data, riding equipment removal, and multiple chest viewers. The full profession suites remain separate. Client checks exercise beam visibility when the body is culled, synchronized menus, equipment, animated storage, and operation with and without JEI.

Recovery fixtures wait for the destination chunk to become entity-ticking before interaction, observe the restored UUID at the join event, and verify identity, health, order, and schema through the server's entity lookup. Verification and fixture cleanup run in separate sequence steps.

Work arenas clear complete columns above their floor and remove existing non-player entities. This prevents overhanging mountain terrain and falling gravel from changing the test inputs. Asynchronous worker tests wait for the required work state before changing resources or terrain instead of assuming discovery finishes after a fixed delay.

## Fluid work, trails, and step movement

Mining scans the current quarry layer for water or lava before ordinary block work. `BUCKET_MISSING` and `BUCKET_SUPPLY` distinguish the blocked reason and support request. Only an empty bucket in the miner's usable bag enables removal; a full bag also requests cargo pickup. Transport revalidates the request, available capacity, and accepted stack before transferring exactly one original bucket. Removal neither consumes nor fills it. Fluid actions check Forge break and placement permissions, roll back cancelled replacements, preserve waterlogged block entities, and apply stamina/growth only after success. The persisted fluid cooldown bounds action frequency.

Fluid target selection prefers reachable exposed sources and removes at most a horizontal 3×3 patch per action. Source batches leave flow cleanup until the remaining sources are gone. Each cell independently checks reach, sight, Forge permissions, bucket presence, and available stamina. `MiningDrainedWaterLayer` preserves the active drainage layer; the Forge fluid-source event prevents water in that working layer from contributing to source regeneration. Active drainage is indexed by loaded level and chunk, so fluid updates check registered miners without scanning surrounding entities or loading chunks. Stopping work, changing quarry layers, or removing the miner releases the guard. No global game rule is changed.

`SoggySlime` uses ordinary companion state plus saved trail/recovery timers. Server-side placement requires air, a loaded solid supporting face, and permission; cancelled placements restore the snapshot without charging stamina or growth. `SlimeTrailBlock` uses carpet geometry, a shovel tag, and one-slag loot. The slime-ball recycling recipe consumes four slag. Its summon model uses the shared base with a separate crest; slag has a dedicated transparent 16-pixel texture.

Ground hop control caps propulsion throughout native `JUMPING` as well as movement requests. Entering water allows a new swimming destination to replace the previous ground-jump heading, with full swimming input against currents. Quarry waypoints accept the height difference between the water surface and submerged path nodes. The Movement Speed attribute adjusts hop timing without reapplying unlimited launch speed while stepping up. Guardian self-defense excludes owners and allies and remains available during Wait or an owner disconnect. Totem protection uses the shared lethal-damage event, including environmental sources subject to native bypass exclusions.

Regression coverage includes all ground roles in the jumping state, actual fall/lava/drowning/fire deaths, offline guardian retaliation, trail costs/loot/cancellation, stored soggy traits, fluid protection rollback, unchanged bucket data, waterlogged inventories, and transport unloading followed by bucket delivery. Native clients verify the new models, trail rendering, management screen, and optional JEI recipes.

All work-specialization constructors start waiting. Missing profession order data also selects Wait, including Friendly Slime specialization, while explicit saved orders remain authoritative. Job tests issue their starting commands explicitly. Native item-use tests cover defaults, specialization, and command-preserving recovery for all nine work roles.

Guardian retaliation also recognizes damage absorbed by temporary absorption health. Untamed guardians safely reject owner-dependent target checks; native regressions cover both boundaries.

## UI text and terminology

Screens and gameplay guides share the stat names Health, Attack, Defense, Movement Speed, Work Speed, Capacity, Stamina, Reach, and Detection Range. Stamina defines the maximum resource; current stamina is the amount available to spend or recover. Growth uses activity units and upgrades consume stat points. The Korean interface distinguishes the Stamina stat, 지구력, from current stamina, 기력.

Follow and Wait are owner-selected orders. Resource shortages and other impediments are described as blocked work. Farming and logging messages identify their own tasks. Keep status text brief, action guidance direct, and button labels short. Use the game's localized names for vanilla ingredients.

Copy changes retain translation keys and persistent data identifiers. Check matching English/Korean keys, formatting placeholders, and document links, then inspect wrapping, button widths, and help panels in the client.

## Quarry lining and free roaming

`QuarryWalls` scans a bounded 192-cell window on the four exterior faces of the work chunk, at most once per 20 ticks unless the layer changes. Loaded side fluids and underground 2×2 air openings create a retained job; the inside face must be exposed before a cell is actionable. The exterior lining preserves both staircase columns. Pending cells are bounded, validated against the current plan, and saved in `MiningWallPending`; changing quarry coordinates or dimension creates a new wall job. Discovery and navigation never acquire adjacent chunk tickets.

`COBBLESTONE_MISSING` with `COBBLESTONE_SUPPLY` pauses mining; lack of bag space also requests `CARGO_PICKUP`. `BagSlime.workSupplyLimit` and `receiveWorkSupply` retain one-item defaults for tools and buckets. A miner requesting wall material accepts up to 64, with live request/ownership/capacity validation before transfer; transport extracts only the accepted count. Successfully placed cobblestone costs one material and stamina and grants one activity unit. Forge placement cancellation restores the snapshot and spends nothing. Unreplaceable and occupied cells are preserved.

`SoggyRoaming` and `SoggyRoamHome` store the optional ROAM order and its origin without changing schema version 1. Missing fields retain previous Follow/Wait behavior. Roaming reserves MOVE/LOOK, chooses bounded random supported destinations, and uses a path region only after every intersecting chunk is confirmed loaded. It remains independent of the owner's presence and yields to fleeing. Follow and Wait clear the roaming flag; release reanchors it. Terrain changes release roaming paths for local reselection, avoiding reuse of an earlier Follow destination. Its recovery override permits trail stamina to recover while ROAM is active. The menu synchronizes ROAM through the existing mode field; protocol 12 requires matching clients.

Native regressions cover inlet replacement, cave lining across recovery, exact placement costs, protected placements, material shortages, stack-limited and partial-stock delivery, menu permissions, actual owner-independent roaming, trail production, command changes, saved traits, and exhausted-roamer recovery.

## Enderman targeting

Endermen are excluded from the added companion-target goal. Combat classification checks their live target before the generic NeutralMob rule: an Enderman targeting a living FriendlySlime uses HOSTILE; otherwise NEUTRAL. Target selection and attacks revalidate this classification and existing filters. Native tests cover unchanged goal sets, calm/hostile transitions, unrelated targets, and actual combat followed by disengagement.

## Tool effectiveness and block-breaking time

`SlimeBlockBreaking` shares native `getDestroyProgress` timing between quarry mining and `FieldSlime.harvest`. Work Speed multiplies progress only when the held tool's native destroy speed exceeds 1, matching the effectiveness requirement used by native Efficiency. An ineffective fallback tool retains its ordinary native progress. Missing, exhausted, or tier-inadequate equipment cannot advance breaking. Crops with zero native hardness still harvest immediately when the profession's required hoe is equipped. Logging now includes the actual axe's native breaking time before its existing action cooldown.

The transient tracker identifies the block position, state, and an exact snapshot of the tool, including enchantments and damage. Changing any of them, interrupting work, changing orders or areas, or removing the entity clears partial progress and crack overlays. Reloaded and recovered companions restart partial block damage while retaining saved work areas and completed activity. Durability, stamina, cargo, and growth are charged only after successful protected removal.

Native regressions measure actual break-attempt ticks at maximum Work Speed with missing, unsuitable, downgraded, and modified tools. They compare wooden and diamond axe intervals, confirm effective-tool bonuses, and cover logging/farming equipment requirements and resumption. The other companion roles perform no tool-based block-breaking actions. Fluid removal, wall placement, tilling, planting, and player-operated revival remain separate actions with their own resource and permission checks.


## Healing and mobile storage

`HealingSlime` persists its bag, work command, rest state, cooldown and search origin. Searches inspect loaded entities and chunk block entities on a staggered interval. Live treatment uses Minecraft's healing event path; broken companions use the same transactional revival helper as player interactions, with owner permissions and Forge block protection checked before removal. Costs and activity commit only after success. Failed targets receive a short retry exclusion. Revival retains the common one-health rule.

`WarehouseSlime` extends transport behavior with a capacity multiplier of 8 and a 216-slot backing inventory. Shared `TransportBag` data preserves existing cargo during specialization; the recipe clears the prior profession command so the warehouse initially waits. `WorkStorage` resolves loaded same-owner warehouse entities and accessible Slime Chests, sorting warehouses first. UUID/block-position targets are revalidated on every transfer; full endpoints are skipped and unreachable endpoints are temporarily excluded. Warehouses collect and supply without the normal deposit phase, avoiding cargo circulation. Storage endpoints remain accessible while following or waiting. The shared work model adds Heal/Store orders and a slimeball supply request without reordering existing enum values. The saved-data schema remains 1; network protocol is **12**.

## Loaded routes and support boundaries

`WorkNavigation` supplies bounded ground paths for working companions. Its region reads chunks through `getChunkNow` and treats unavailable terrain as a solid boundary. Region construction touches only the already-loaded origin chunk; route search can use loaded detours without eagerly acquiring a rectangular set of chunks. Search effort is bounded at 4,096 nodes. Ground companions retain native navigation while following. Guardian flights use `LoadedFlyingNavigation` with the same `LoadedPathRegion`; path targets, route cells and direct-flight rays cannot acquire unavailable chunks.

Healing status distinguishes a full bag from missing materials and rechecks accepted supplies synchronously. Supply APIs cap each operation at 64 slimeballs without mutating the caller's offer. Transport simulates its own remaining space before choosing a supply source. Treatment exclusions retain blocked status while failed targets remain eligible and clear when another companion resolves the need. Revival receives the real item, hand-derived stack and face from player interaction, preserving Adventure permission predicates.

Independent regression fixtures cover chunk boundaries, loaded detours, native healing cancellation, protected revival, competing carriers and exact item metadata. Logging arenas remove unrelated natural trees from loaded neighboring chunks so generated forests cannot substitute a different work target. Long-running actions use observable state transitions rather than fixed discovery deadlines.

`HealingTreatmentBlocked` preserves an unresolved treatment episode across failed approach retries and saves; productive treatment or an empty eligible-target set clears it. This allows the herald delay to elapse even when path attempts restart.

Stored-item restoration reserves its UUID for the synchronous spawn transaction, including Forge spawn callbacks. A reentrant restoration in another dimension fails without consuming its item. The reservation is released in `finally`, including cancellation and exceptions; the existing cross-world entity check still prevents a second release after registration.

Chunk cache keys use primitive longs to avoid allocating boxed coordinates for each inspected path cell. Each `SlimeStat` also initializes its stable lowercase persistence key once instead of allocating it during repeated stat reads.

Every work goal revalidates its pure eligibility predicate at the start of `tick`, since the native goal selector can retain a running goal until a later eligibility refresh. A cancelled goal stops without mutating resources or terrain. Independent native tests tick already-running transport, warehouse and healing goals immediately after command cancellation.

`LoadedBlockView` provides the same loaded-only chunk access for route regions and work sight rays. Missing chunks behave as opaque blocks, so visibility checks cannot load them before a reach check. Each query has its own view: newly loaded terrain is available to the next query. Ordinary ground following retains native sight behavior.

Treatment captures its target before invoking Forge callbacks, since event handlers can change the healer's command and clear transient targets synchronously. A completed heal still commits one matching cost; a cancelled heal commits none. Revival rechecks the working command and the healer's live state after block protection callbacks, before changing terrain.

Protection and pickup callbacks use the same post-event live-command, owner, dimension and lifetime checks before committing further work. Interrupted fluid removal, wall reinforcement, tilling and planting restore their block snapshots without material costs. Collection also stops its route immediately when a callback replaces the work order. Quarry and collection sight rays share the loaded-only view, including diagonal chunk corners within ordinary reach.

Herald scans recheck the monitoring command between signal emissions. Soggy trail placement rechecks Wait, ownership and live state after its protection callback; it retains normal trail behavior in both Follow and Roam.

Loaded work arenas register completion listeners that release their forced chunks on both success and failure. This keeps a failed fixture from leaving distant chunks active for later tests.

Guardian protection snapshots its exact totem reserve before the native totem-use callback and revalidates live ownership, distance and reserve data afterward. Removal, transfer or reserve replacement cancels the rescue before costs or player effects. A per-guardian guard prevents recursive protection and is released in `finally`.

Mounted flight shares one movement calculation between client travel and server velocity prediction. The server retains that prediction only for native vehicle-packet validation; it clears it before the next physics step, dismounting or wing removal. Both world saves and recovered items store zero predicted motion. Native packet regressions use a registered Survival `ServerPlayer`, include the dedicated-server speed check, and retain rejection of impossible movement.

When a miner remains active outside an unloaded quarry, its work tick clears the retained route before native navigation can inspect retained path nodes. The quarry plan and inventory are retained. A native regression unloads the actual source chunk, verifies no re-acquisition, and rebuilds a route after terrain becomes available.

Accepted placement-event adjustments remain completed field actions, including altered crop age, farmland moisture and immediate sapling growth. Costs commit once after the event, while cancellation retains snapshot rollback. Quarry reinforcement accepts a converted block only when it forms a solid, non-fluid barrier; ineffective conversions restore the inlet and use the ordinary blocked retry delay.
