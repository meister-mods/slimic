# Gameplay Guide

English | [한국어](GAMEPLAY.kr.md) · [Project overview](../README.md)

Slimic provides friendly companions with upgradeable stats, mining, transport, herald, combat, cleaning, and riding specializations.

## Installation

Use Minecraft 1.20.1, Forge 47.x (developed against 47.4.10), and Java 17. Place `slimic-1.20.1-*.jar` from the build or release into the Minecraft `mods` folder, then launch the Forge profile. Do not install the `-sources.jar` development artifact.

## Crafting a Friendly Slime

Place eight slime balls around one soul sand or soul soil in a crafting table to make one **Friendly Slime** item:

| Slime ball | Slime ball | Slime ball |
| --- | --- | --- |
| Slime ball | Soul sand / soul soil | Slime ball |
| Slime ball | Slime ball | Slime ball |

The item is also available in the Slimic creative tab.

## Summoning and controls

Use the item on a block or water source, like a spawn egg. A newly crafted item summons a slime already tamed to the player using it. A successful Survival use consumes one item; a newly crafted item is reusable in Creative mode.

An anvil name on the item becomes the slime's custom name.

| Action | Result |
| --- | --- |
| Owner right-clicks | Open the management screen for stats, upgrades, naming, equipment, inventory, and orders. |
| Owner Shift-right-clicks | Recover the slime into an inventory item, even while holding another item. |
| Use a recovered item on a block or water source | Release the same companion with its saved traits. |

Empty-hand use of a saddled Saddle Slime mounts it; hold another item to open management.

Only the owner can recover the slime or change its follow/wait order. Releasing a recovered item preserves the original owner, even if someone else uses the item.

## Recovery and preserved traits

Recovery saves the slime's complete persistent state. Its current health is preserved rather than refilled. Saved traits include:

- Maximum health, other attributes, and permanent attribute modifiers.
- Active effects and their remaining durations.
- Custom name, owner, and follow/wait order.
- Identity, age, equipment, and other saved entity properties.
- Custom persistent data used for future slime traits.

Save data carries a schema version. Existing unversioned saves and items remain compatible; a stored item with an unsupported newer version cannot be released and remains intact.

Recovered slimes occupy separate, unstackable items. Their saved state survives inventory, chest, and world saves. Effect durations and other saved timers stay paused while the slime is an item.

The main inventory needs an empty slot. If it is full, recovery fails and the slime stays in the world with its state intact. A dead or dying slime cannot be recovered.

Releasing a recovered item consumes that item in both Survival and Creative mode. A failed release, such as placement inside solid blocks, leaves the stored item unchanged.

The slime appears at the selected destination. Old movement and fall distance are reset so recovery does not carry a previous fall into the new placement. Leashes are returned on recovery, and riding relationships are detached.

## Companion behavior

Friendly slimes hop after their owner and retain ownership and the wait order across saves. They persist in Peaceful difficulty and do not split on death. Combat slimes attack enabled target types while assigned to Fight; other specializations do not attack.

Companions retain the original slime texture with a dye color filter. Summon icons use 16×16 pixel textures with slime-ball silhouettes, specialization colors, and symbols. See [CREDITS.txt](../CREDITS.txt).

## Shared stats and growth

Right-click an owned slime to open its management screen. The name field sets a visible name above the slime, like a name tag. Follow and Wait buttons change its order. Mining slimes have a Mine button, separate pickaxe and shovel slots, and a bag. Transport slimes have a Haul button and a larger bag, with no equipment slot.

| Stat | Initial value | Per point | Purpose |
| --- | --- | --- | --- |
| Health | 16 | +4 | Maximum health; upgrading does not refill current health. |
| Attack | 2 | +1 | Base melee damage for combat slimes; retained when a friendly slime specializes. |
| Defense | 0 | +1 | Minecraft's standard armor attribute and damage calculation. |
| Movement Speed | 0.30 | +0.015 | Shortens the pause between ground hops; launch speed and hop distance stay fixed. Also scales mounted flight speed. |
| Work Speed | 1.0× | +0.1× | Mining progress and transport, combat, and cleaning action frequency. |
| Capacity | 9 slots / transport: 18 | +3 slots / transport: +6 | Usable bag slots, up to 27 or 54 for transport. |
| Stamina | 100 | +20 | Maximum energy for continuous work and proportional recovery. |
| Reach | 3 blocks | +0.25 blocks | Maximum distance for mining and item transfers. |
| Detection Range | 8 blocks | +1 block | Worker target/work-area range and herald monitoring radius. |

Capacity supports six upgrades; the other stats support twenty. Only the owner can spend points, rename the companion, or use its management inventory. All stats, unused points, activity progress, and current stamina survive world saves and recovery.

The first stat point requires 32 activity units. Each earned point increases the next requirement by 64 units, capped at 4,096. Successful mining, combat hits, and cleaning pickups each grant one unit. Transport grants four units per cargo-stack delivery into a Slime Chest or replacement tool supplied to a worker; collecting cargo and fetching tools grant none. This higher delivery reward compensates for travel, while actual progress rates still depend on the job and terrain.

All companions can earn one unit per 100 ticks (five seconds) of actual movement while following a nearby owner or carrying their rider. Escaping an attacker uses the same movement interval. Actual damage from a non-allied attacker or a successful shield block also earns one unit, limited to one defensive award per second; owner damage and environmental damage grant none. Waiting, standing still, teleporting, and changing orders grant none. Heralds also earn one unit per 100 ticks of monitoring an active or blocked same-owner worker in range; idle, completed, or manually waiting workers grant none. Partial following and monitoring timers survive saves and recovery and pause in item storage. Signals do not grant extra activity.

The world's `serverconfig/slimic-server.toml` configures `transportActivityPerDelivery` (default 4, range 1–64), `followingActivityIntervalTicks`, and `monitoringActivityIntervalTicks` (both default 100, range 20–1,200). Existing points and activity are retained.

Stats have different roles. Health, Defense, and Movement Speed benefit every companion. Attack affects combat slimes; Work Speed, Capacity, Stamina, and Reach serve mining, transport, combat, and cleaning. Stamina powers normal mounted flight and ground or air sprinting. Detection Range serves those workers and herald monitoring. Friendly slimes retain all investments when specialized. Upgrade tooltips identify stats outside the current specialization.

Stamina recovers at **2% of maximum per second** during recovery: 2 at 100 maximum, 4 at 200, and 10 at 500. Exhausted workers resume at 25%, taking about 13 seconds from zero regardless of maximum, subject to tick timing. Larger stamina pools support longer work bursts and proportionally higher recovery. Recovery opportunities still depend on the job.

## Work orders and support requests

An order describes the assigned task: Follow, Wait, Mine, Haul, Monitor, Fight, or Clean. Execution is idle, active, blocked, or complete. A blocked task carries a reason such as a full bag, missing tool, unsuitable tool tier, exhaustion, missing storage, full storage, unreachable terrain, missing supports, or disabled work.

A full working bag issues a cargo-pickup request. A worker missing its required tool issues a tool-supply request. Transport acts on these requests and rechecks them before every transfer. Heralds use the shared blocked state and its continuous duration. Manual Follow/Wait orders and completed quarries are not blocked work.

Existing activity is recalculated against the current thresholds when a companion is loaded. Newly earned points are granted and remaining activity is retained; existing points and stat investments remain intact. For example, 200 activity at the first tier becomes two points and 72 remaining activity. Subsequent recovery does not grant those points again.

The Slimic Creative tab groups all seven companion items first, followed by the Slime Chest and Broken Slime Block, six armor materials from leather through netherite, and the Creative-only tuner.

## Mining slimes

Craft a Friendly Slime item with any pickaxe, without a fixed shape, to make a **Mining Slime**. The equipped tool retains its type, damage, name, and enchantments. A recovered friendly slime can also specialize without losing its saved traits.

Mining slimes wear a yellow mining helmet and display both equipped tools beside the body, with their grips inward and tool heads upright. The recipe equips the exact input pickaxe, including its damage and enchantments. A Creative-tab default miner starts with both equipment slots empty.

A newly crafted or Creative miner waits at its release point. Open its management screen and select **Mine** to explicitly begin work in that chunk. Specializing a recovered friendly slime into a miner also starts in Wait. It clears successive horizontal layers from the release elevation downward, including dirt, stone, and ores. Releasing a recovered miner in the **same chunk** retains its current layer and saved order. Releasing it in a **different chunk or dimension** establishes a new quarry at the new elevation and automatically resumes mining. Tools, cargo, health, stats, and other traits remain intact.

The quarry reserves a two-block-wide staircase around the chunk perimeter, with flat corner landings and full-block steps. Existing solid floor blocks are retained. Missing steps are repaired using common full blocks from the bag, such as cobblestone, dirt, stone, or deepslate; each placed block consumes one item. A miner pauses if supports are unavailable or placement is blocked. Supply building blocks through its bag to continue.

Mining stays within the chunk and does not load neighbouring chunks. Air, liquids, block entities, and unbreakable blocks are skipped. An unbreakable floor sealed by the reserved staircase, or the bottom of the dimension completes the quarry. Protected containers, fluids, or other obstructions can prevent access and may require manual clearance. Block breaking and support placement respect Forge events and `mobGriefing`.

The miner approaches targets and works within Reach, bounded by Detection Range. It uses the shovel for shovel-suitable terrain when available and the pickaxe otherwise, provided the tool meets the block's drop requirements. Actual tools determine break speed, enchantment-aware drops, durability, Fortune, Silk Touch, and Unbreaking. When a tool breaks or its equipment slot is empty, a compatible spare in the bag is equipped automatically with its data intact. Without a suitable tool or tier, work pauses.

Drops go directly into the bag. If all drops cannot fit, the block stays intact, the tool is not damaged, and the miner pauses. Removing items or upgrading Capacity lets work resume. Bag items can be moved to the player's inventory, including with Shift-click.

Each completed block consumes one stamina. At exhaustion the miner rests, recovering 2% of maximum stamina per second until reaching 25%. Waiting, following, and other non-mining time also restore stamina. The mobGriefing game rule and Forge block-break permissions are respected.

A miner can navigate long stairs and detours inside its work chunk at default Detection Range. Actual mining and support placement still require Reach, Detection Range, and sight. Stalled repair routes report an unreachable state and retry periodically; clearing the route lets work resume. Long routes are not abandoned solely because of elapsed time while the miner is still moving.

## Slime Chests

Craft eight wooden planks around a slime ball to make a **Slime Chest**. Any plank type, including mixed types, works. Right-click to open its 27-slot inventory. It stores both resources and spare tools, retains its inventory across world saves, and supports standard item automation and comparator output. Breaking it drops the chest and its contents separately.

Slime Chests are shared logistics containers for nearby transport slimes. Ordinary chests are used to craft transport slimes and are not automatic pickup or delivery endpoints. Locked Slime Chests are not used by transport slimes.

Slime Chests use dedicated wooden face textures with green slime trim. The inventory item displays the same textured block model.


Following preserves the saved quarry until mining is started again. Starting Mine in another chunk establishes a quarry at the current position; starting in the same chunk retains its progress. Slow approaches continue while making horizontal or grounded vertical progress. If a target's block state changes during mining, its work progress restarts and only the current block's drops are produced.

## Transport slimes

Craft a Friendly Slime item with a **regular chest**, without a fixed shape, to make a **Transport Slime**. Stored companion traits survive specialization. A transport slime has no equipment slot: even tools travel in its normal bag.

Its Capacity is always exactly twice that of another slime at the same upgrade level: 18 slots initially, six additional slots per Capacity upgrade, and 54 slots at the maximum. Both bag pages can be accessed with the arrow buttons in the management screen. Shift-click moves cargo between the visible bag page and the player's inventory.

New transport slimes start hauling when summoned. Their release position is the center of a work area whose radius is their Detection Range. They find loaded Slime Chests and same-owner workers inside this area, walk between them, and transfer items within Reach and line of sight. The area remains centered on the release position while they travel; recovery and release establish a new center. They do not load neighbouring chunks to find work. Changes to Detection Range also apply to previously selected chests before deposits or tool pickups.

When a worker stops because its bag is full, a transport slime approaches, picks up cargo that fits in its own bag, and delivers it to available Slime Chests. The worker resumes as space becomes available. Partial stacks are merged, and another chest is used when needed. If no suitable chest has room, existing cargo stays in the transport bag and no items are discarded. It retries as space becomes available.

When a worker stops because its tool is missing or broken, the transport slime fetches one compatible spare tool from a Slime Chest and brings it to the worker. For mining slimes this means the requested pickaxe or shovel; combat slimes request swords. The tool's damage, enchantments, and name are preserved. If another carrier already supplied the worker, remaining tools stay in cargo and are reused or returned to a Slime Chest. Manually waiting or following workers are not treated as stopped work requests.

Each cargo-stack pickup or delivery, and each tool pickup or supply, consumes one stamina. Exhausted carriers rest until 25% stamina, recovering 2% of maximum per second. Work Speed shortens the interval between transfer actions. Follow and Wait suspend logistics, and all bag contents, stats, current stamina, growth, and orders survive saves and recovery.

Transport travel failures are based on stalled movement. A delivery that keeps advancing at a low movement speed continues beyond 400 elapsed ticks. A carrier that cannot move reports an unreachable route and retries other routes or targets. The remaining interval between transfer actions survives saves and recovery, and pauses while stored as an item.

## JEI

JEI is optional. When installed, it shows the Friendly Slime and Slime Chest recipes, Mining Slime combinations for registered pickaxes, the Transport Slime, Herald Slime, Combat Slime, Cleaning Slime, Saddle Slime, and Slime Armor combinations, and companion information pages. Mining and combat recipe previews retain the corresponding tool. Armor previews show each registered chestplate conversion. Slimic also runs without JEI.

## Dye colors

Right-click an owned companion with a dye to apply that color to its original slime texture. This works for all companion types. Survival consumes one dye; Creative does not. Equipment keeps its own colors. The color survives world saves, specialization, recovery, and release. Shift-right-click still recovers the companion.

## Herald slimes

Craft a Friendly Slime item with any firework rocket, without a fixed shape, to make a **Herald Slime**. The companion retains the exact rocket design, including explosion shapes, colors, fades, trail, flicker, and flight duration. The crafting ingredients are consumed normally; signaling requires no further rockets. The Creative default uses a white small-ball explosion.

A herald watches working companions belonging to the same owner within its Detection Range. When a worker has been continuously unable to work for the configured number of minutes, it launches a visible vanilla firework. These signal fireworks do not damage living entities. Missing tools, insufficient tool tier, full cargo, exhausted stamina, disabled work, inaccessible targets, and unavailable logistics can block work. Manual Follow/Wait orders and completed quarries do not count as stalled work.

The management screen offers Watch, Follow, Wait, and a delay from 1 to 60 minutes. The default is five minutes, configured by `heraldDelayMinutes` in the world's `serverconfig/slimic-server.toml`. After the first signal for a stalled episode, the herald repeats it every 200 ticks (ten seconds) while the same worker remains blocked in range. Each worker has its own repeat timer. Resolution, manual pause, or leaving monitoring range stops further signals. Warning history stays intact for large groups and through recovery. Resuming useful work and becoming stalled again permits a new warning. Rocket design, delay, order, warning history, per-worker repeat timers, and blocked-work timers survive recovery; time does not advance inside an item.

Herald slimes wear a red-and-cream striped conical hat. Dyeing the slime preserves the hat colors.

## Slime Tuner

The **Slime Tuner** is available in the Slimic creative tab and has no Survival recipe. Hold it and right-click to open a separate stat editor for the companion under the crosshair within 16 blocks, falling back to the nearest companion in that range. Right-clicking a companion directly also opens it. The editor requires Creative mode and a held tuner throughout use.

The tuner edits base stats, current health, current stamina, and unused stat points. Permanent attribute modifiers remain applied. Transport Capacity is twice the entered common base; Cleaning Detection Range is five times its entered base. Apply saves valid changes together; Refresh reloads the current values. Reducing Capacity requires emptying every bag slot that would become unavailable. Changing maximum health does not automatically refill current health.

| Field | Range |
| --- | --- |
| Health | 1–1,024 |
| Attack | 0–2,048 |
| Defense | 0–30 |
| Movement Speed | 0–10 |
| Work Speed | 0.1–32 |
| Capacity | 1–27 whole slots; transport receives 2–54 |
| Stamina | 1–2,000 |
| Reach | 0–32 |
| Detection Range | 0–64 |
| Current health / stamina | 0 to the resulting maximum |
| Unused points | 0–32,767 whole points |

Tuned stats and current values survive recovery and release. Normal upgrades remain available within their level limits.

## Dimension travel and work areas

Portal travel rebases fixed work areas at the arrival point. Companions restored into another dimension by a command also update their area before their first work tick. Mining slimes start a new quarry automatically; transport and cleaning slimes retain their follow, wait, or work order. Matching X/Z chunk coordinates in different dimensions identify separate work areas. Health, stamina, growth, ownership, equipment, and cargo remain intact.

Ordinary reloads within the same dimension retain the saved work area. Cancelled dimension transfers leave the original area unchanged.

## Combat slimes

Craft a Friendly Slime item with any sword, without a fixed shape, to make a **Combat Slime**. The recipe equips the exact input sword, including its wear and enchantments. Stored companion traits survive specialization. Creative defaults have empty equipment.

Combat slimes share the common stats and have a normal-capacity bag plus three equipment slots: sword, shield, and Slime Armor. Fight searches within Detection Range and approaches visible permitted targets; Follow and Wait suspend combat. Sword damage, enchantments, knockback, fire effects, and durability use Minecraft's melee behavior. Reach bounds attack distance and Work Speed shortens the interval between attacks. Each successful hit costs one stamina and earns one work unit. Exhausted companions rest until 25% stamina. Missing equipment can be filled from compatible bag spares, and transport slimes can fetch replacement swords from Slime Chests.

Pursuit reports an unreachable route after 400 ticks without horizontal or grounded vertical progress. Slow movement keeps pursuit active, and advancing again clears the blocked state. Hopping in place does not count as travel.

An equipped shield blocks frontal attacks while approaching and between strikes, using normal blocking timing, durability, and shield disabling by compatible weapons. The body turns toward its target so frontal coverage follows the threat. With a shield, attack intervals are at least ten ticks to allow the normal five-tick activation delay even at high Work Speed. Slime Armor applies its original chestplate's defense, toughness, knockback resistance, enchantments, and durability. The Defense stat adds the common base armor attribute separately.

Open **Targets** in the management screen to switch between the bag and six independent attack filters:

| Target type | Default | Classification |
| --- | --- | --- |
| Hostile | On | Hostile mobs other than neutral mobs and creepers. |
| Neutral | Off | Neutral mobs, including those capable of retaliation. |
| Passive | Off | Other mobs, such as livestock and villagers. |
| Players | Off | Other players permitted by server PVP and the owner's team rules. |
| Tamed | Off | Other owners' tamed companions and horses. |
| Creepers | Off | Creepers, controlled separately from Hostile. |

The owner, the owner's other tamed companions, allies, spectators, and Creative players remain excluded. Players, tamed mobs, and creepers use their own switches rather than falling back to a general category. Disabling a current target's category stops that attack. Filters, orders, current health, stamina, cooldowns, stats, equipment, and cargo survive recovery and release.

## Slime Armor

Craft any chestplate with **one slime ball**, without a fixed shape, to make **Slime Armor**. Leather, chainmail, iron, gold, diamond, and netherite variants appear in the Slimic creative tab; other registered chestplates can also be converted. Helmets, leggings, boots, and already converted armor do not qualify.

The result stores the original chestplate and retains its material attributes, maximum and current durability, enchantments, custom name, leather color, and other item data. Wear and enchantment changes remain authoritative after conversion. Place it in a combat slime's armor slot. Ordinary chestplates are crafting ingredients and do not fit that slot directly. Material-specific models and the green slime badge distinguish the item variants.

## Cleaning slimes

Craft a Friendly Slime item with **rotten flesh**, without a fixed shape, to make a **Cleaning Slime**. Its Detection Range is always five times the common base: 40 blocks initially, with five additional blocks for each normal range upgrade. Its bag uses normal Capacity, starting at nine slots.

Clean begins work around the release position. It waits there, selects a dropped item inside the fixed spherical collection area, approaches it, collects within Reach and line of sight, and returns to the release position after each successful pickup. It does not wander while assigned to Clean, and walking toward an item does not move the collection area's center. Recovery and release establish a new center while preserving other traits.

Returns also measure stalled movement rather than total travel time. A cleaner that keeps advancing can take more than 400 ticks to reach home; a stalled return clears its warning state when travel resumes.

Pickup respects item pickup delays, reserved ownership, Forge pickup cancellation, stack limits, and available bag space. Partial pickups leave the remainder in the world. A full bag pauses collection, and a transport slime can unload it into a nearby Slime Chest. Removing cargo also resumes work. Each successful pickup action costs one stamina and earns one work unit; Work Speed shortens the search interval. Exhaustion pauses collection until 25% stamina, with 2% of maximum restored per second.

After **three minutes** of attempting to acquire the same item without success, the cleaner searches for another eligible item with available bag space. If one exists, it changes target and temporarily avoids the failed item. Otherwise it resets the attempt timer and retries the same item. A removed or unavailable target is replaced promptly. The retry timer advances during active collection and pauses while following, waiting, or stored as an item. Retry state, cargo, dye, stats, current health, and stamina survive saves and recovery.

## Saddle slimes

Craft a Friendly Slime item with **one saddle**, without a fixed shape, to make a **Saddle Slime**. Stored companion traits and the exact input saddle are retained; the saddle is equipped on release. The Creative entry starts with empty equipment. The management screen has two equipment slots: saddle and elytra, with no cargo bag. Equipment also accepts Shift-click transfers.

The owner can apply a saddle or elytra directly by right-clicking with it when the corresponding slot is empty. Survival consumes the applied item. An equipped saddle appears as a leather seat with side straps and stirrups. Equipped elytra show the vanilla wing texture independently of the slime's dye color.

Empty-hand right-click rides a saddled companion. Hold another item to open management, or use its **Ride** button. Only the owner may equip, manage, or ride it, and it carries one player. Riding takes priority over Follow/Wait and releases a waiting order. Without a rider, it follows its normal companion order.

| Control | Ground | With usable elytra |
| --- | --- | --- |
| WASD | Steer relative to the rider's view | Fly forward/backward and strafe; forward flight follows view pitch |
| Space | Jump | Ascend |
| Left Ctrl | Double-speed sprint | Double-speed flight boost; rebind **Slime Sprint** in Minecraft's controls |
| No movement input | Stop | Hover |
| Shift | Dismount | Dismount |

Movement Speed shortens ground-hop intervals while retaining fixed launch speed, and scales normal flight speed. Sprinting increases launch speed separately from the Movement Speed stat. Normal airborne flight spends two stamina per second. Ctrl doubles movement speed on both the ground and in the air: ground sprint spends ten stamina per second, and boosted flight spends fifteen. Airborne flight and sprinting prevent recovery. At zero stamina the wings remain open, ascent is disabled, and the companion slowly descends at about 2.4 blocks per second with limited horizontal steering and no fall damage. It recovers after landing; flight and sprinting resume at 25% stamina. Aim downward and move forward for an ordinary controlled descent. Flight requires an equipped saddle, usable elytra, and the owner as rider; rockets and Creative flight are unnecessary. Removing the saddle dismounts the rider. Removing the wings, wearing unusable wings, or dismounting restores normal gravity. Equipment, current health, stats, dye, and ownership survive saves and Shift-right-click recovery. Passengers and transient flight controls are not stored.

## Hostile encounters and revival

Hostile mobs with native target-goal AI, including zombies, skeletons, and spiders, can select every companion type as prey. Creepers receive no added companion targeting or swelling. Non-combat companions flee for up to ten seconds after an attack, temporarily overriding work and waiting while retaining the assigned order. A mounted slime follows its rider's controls.

A fallen companion leaves a **Broken Slime Block** at its position. The block stores the same UUID, owner, specialization, name, dye, stat investments, points, activity, remaining stamina, equipment, cargo, and orders. The owner can right-click it with one slimeball to revive it at full health. Survival consumes one ball only after successful revival; Creative consumes none. Clear enough space for the companion before reviving. Blocked placement, an existing matching UUID, or invalid stored data leaves the block and ball intact.

Breaking the block yields a single recovery block item carrying its stored companion; place it again to revive. A collapsed companion does not separately drop its retained equipment or cargo. Existing solid blocks and containers are preserved: if the death position cannot accept the block, a recovery block item is left there instead. The recovery item resists fire, and the placed block resists explosions. Breaking a stored recovery block in Creative also returns its companion item.

All seven summoning items share the Friendly Slime base and carry a small framed profession crest: a blue heart, pickaxe, bundle, firework, sword, brush, or saddle. The base stays consistent while the crests distinguish each specialization. Equipped wings form two side-mounted wings, fold down while grounded, and flap during mounted flight, faster while boosting.
