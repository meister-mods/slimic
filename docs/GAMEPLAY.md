# Gameplay Guide

English | [한국어](GAMEPLAY.kr.md) · [Project overview](../README.md)

Slimic provides friendly companions with upgradeable stats, mining, transport, herald, combat, cleaning, riding, farming, lumberjack, guardian, and soggy specializations.

## Installation

Use Minecraft 1.20.1, Forge 47.x (developed against 47.4.10), and Java 17. Place `slimic-1.20.1-*.jar` from the build or release into the Minecraft `mods` folder, then launch the Forge profile. Do not install the `-sources.jar` development artifact.

## Crafting a Friendly Slime

Place eight slimeballs around one soul sand or soul soil in a crafting table to make one **Friendly Slime** item:

| Slimeball | Slimeball | Slimeball |
| --- | --- | --- |
| Slimeball | Soul sand / soul soil | Slimeball |
| Slimeball | Slimeball | Slimeball |

The item is also available in the Slimic creative tab.

## Summoning and controls

Use the item on a block or water source, like a spawn egg. A newly crafted item summons a slime already tamed to the player using it. A successful Survival use consumes one item; a newly crafted item is reusable in Creative mode.

An anvil name on the item becomes the slime's custom name.

| Action | Result |
| --- | --- |
| Owner uses a Slime Tuner on the slime | Open the management screen for stats, upgrades, naming, equipment, inventory, and orders. |
| Owner Shift-right-clicks | Recover the slime into an inventory item, even while holding another item. |
| Use a recovered item on a block or water source | Release the same companion with its saved traits. |

All newly created work specializations start in **Wait**, including Creative defaults and specialization of a stored Friendly Slime. Start Mine, Haul, Watch, Fight, Clean, Farm, or Chop explicitly in management. Select Follow or Free roam for Soggy Slime trails, or Follow for guardian movement. Recovered companions retain their saved commands; miners retain the documented relocation behavior. Missing job data defaults to Wait.

Ordinary use of a saddled Saddle Slime mounts it, including while holding an item. Use a Slime Tuner for management. Ordinary Herald Slime use launches its saved firework.

Only the owner can recover the slime or change its follow/wait order. Releasing a recovered item preserves the original owner, even if another player uses the item.

## Recovery and preserved traits

Recovery saves the slime's complete persistent state. Its current health is preserved rather than refilled. Saved traits include:

- Maximum health, other attributes, and permanent attribute modifiers.
- Active effects and their remaining durations.
- Custom name, owner, and follow/wait order.
- Identity, age, equipment, and other saved entity properties.
- Other persistent companion data.

Save data carries a schema version. Existing unversioned saves and items remain compatible; a stored item with an unsupported newer version cannot be released and remains intact.

Recovered slimes occupy separate, unstackable items. Their saved state survives inventory, chest, and world saves. Effect durations and other saved timers stay paused while the slime is an item.

The main inventory needs an empty slot. If it is full, recovery fails and the slime stays in the world with its state intact. A dead or dying slime cannot be recovered.

Releasing a recovered item consumes that item in both Survival and Creative mode. A failed release, such as placement inside solid blocks, leaves the stored item unchanged.

The slime appears at the selected destination. Old motion and fall distance are reset so recovery does not carry a previous fall into the new placement. Leashes are returned on recovery, and riding relationships are detached.

## Companion behavior

Friendly Slimes hop after their owner. Work specializations initially wait and follow after receiving Follow. Ownership and orders persist across saves. They persist in Peaceful difficulty and do not split on death. Combat slimes attack enabled target types while assigned to Fight; Guardian Angel Slimes retaliate against attackers of their owner or themselves.

Companions retain the original slime texture with a dye color filter. Summon icons use 16×16 pixel textures with slime-ball silhouettes, a shared Friendly Slime base, and framed profession symbols. See [CREDITS.txt](../CREDITS.txt).

All thirteen summoning items share the Friendly Slime base and carry a small framed profession crest: a plain gold plate for Friendly Slimes, then a pickaxe, bundle, firework, sword, brush, saddle, hoe, axe, nether star, Slime Slag, golden carrot, or chest minecart. The base stays consistent while the crests distinguish each specialization.

Permanent profession details remain visible without optional equipment: Cleaning Slimes wear blue work caps, Combat Slimes wear metal headbands with red crests, Saddle Slimes have brown bridles with gold buckles, and Soggy Slimes carry three slime droplets. Armor, saddles, and wings remain visible with these details.

## Shared stats and growth

Use a **Slime Tuner** on an owned slime to open its management screen. The name field sets a visible name above the slime, like a name tag. Follow and Wait buttons change its order. Mining slimes have a Mine button, separate pickaxe and shovel slots, and a bag. Transport slimes have a Haul button and a larger bag, with no equipment slot.

| Stat | Initial value | Per point | Purpose |
| --- | --- | --- | --- |
| Health | 16 | +4 | Maximum health; upgrading does not refill current health. |
| Attack | 2 | +1 | Base melee damage for Combat Slimes and beam damage for Guardian Angel Slimes. |
| Defense | 0 | +1 | Minecraft's standard armor attribute and damage calculation. |
| Movement Speed | 0.30 | +0.015 | Shortens the pause between ground hops; launch speed and hop distance stay fixed. Also scales mounted flight speed. |
| Work Speed | 1.0× | +0.1× | Mining progress and action intervals for transport, combat, cleaning, farming, logging, healing, warehouse logistics, beams, and trails. |
| Capacity | 9 slots / transport: 18 / warehouse: 72 | +3 / +6 / +24 slots | Usable bag slots, up to 27 / 54 / 216 respectively. |
| Stamina | 100 | +20 | Maximum stamina for continuous work and proportional recovery. |
| Reach | 3 blocks | +0.25 blocks | Maximum distance for mining, healing, and item transfers. |
| Detection Range | 8 blocks | +1 block | Worker target/work-area range and herald monitoring radius. |

Capacity supports six upgrades; the other stats support twenty. Only the owner can spend points, rename the companion, or use its management inventory. All stats, unused points, activity progress, and current stamina survive world saves and recovery.

The first stat point requires 32 activity units. Each earned point increases the next requirement by 64 units, capped at 4,096. Successful fluid removal, slime-trail placement, mining, combat hits, cleaning pickups, crop harvesting/planting, soil restoration, log harvesting, material collection, and replanting each grant one unit. Successful healing or revival grants one unit. Transport grants four units per cargo-stack delivery into a warehouse or Slime Chest, or supplies delivered to a worker; warehouse cargo collection also earns this reward. Ordinary transport collection and fetching supplies grant none. This higher delivery reward compensates for travel, while actual progress rates still depend on the job and terrain.

All companions can earn one unit per 100 ticks (five seconds) of actual movement while following a nearby owner or carrying their rider. Escaping an attacker uses the same movement interval. Actual damage from a non-allied attacker or a successful shield block also earns one unit, limited to one defensive award per second; owner damage and environmental damage grant none. Waiting, standing still, teleporting, and changing orders grant none. Heralds also earn one unit per 100 ticks of monitoring an active or blocked same-owner worker in range; idle, completed, or manually waiting workers grant none. Partial following and monitoring timers survive saves and recovery and pause in item storage. Signals do not grant extra activity.

The world's `serverconfig/slimic-server.toml` configures `transportActivityPerDelivery` (default 4, range 1–64), `followingActivityIntervalTicks`, and `monitoringActivityIntervalTicks` (both default 100, range 20–1,200). Existing points and activity are retained.

Stats have different roles. Health, Defense, and Movement Speed benefit every companion. Attack affects combat and guardian slimes; Work Speed, Capacity, Stamina, and Reach serve mining, transport, combat, cleaning, farming, logging, healing, and warehouse slimes. Guardian Angel Slimes also use Work Speed, Stamina, and Reach for their abilities. Soggy slimes use Work Speed and Stamina for trail production. Stamina also powers normal mounted flight and ground or air sprinting. Detection Range serves those workers, herald monitoring, guardian beams, and the Soggy Slime roaming area. Friendly slimes retain all investments when specialized. Upgrade tooltips identify stats outside the current specialization.

Stamina recovers at **2% of maximum per second** during recovery: 2 at 100 maximum, 4 at 200, and 10 at 500. Mining, transport, combat, cleaning, farming, and lumberjack workers resume at 25%, taking about 13 seconds from zero regardless of maximum, subject to tick timing. Larger stamina pools support longer work bursts and proportionally higher recovery. Recovery opportunities still depend on the job.

## Work orders and support requests

An order describes the assigned task: Follow, Wait, Mine, Haul, Monitor, Fight, Clean, Farm, Log, Free roam, Search, or Store. Execution is idle, active, blocked, or complete. A blocked task carries a reason such as a full bag, missing tool, unsuitable tool tier, exhaustion, missing storage, full storage, unreachable terrain, missing supports, or disabled work.

A full working bag issues a cargo-pickup request. A worker missing its required tool issues a tool-supply request. A miner encountering water or lava without an empty bucket issues a separate bucket-supply request; a full bag also requests cargo pickup. Transport acts on these requests and rechecks them before every transfer. Heralds use the shared blocked state and its continuous duration. Manual Follow/Wait orders and completed quarries are not blocked work.

Existing activity is recalculated against the current thresholds when a companion is loaded. Newly earned points are granted and remaining activity is retained; existing points and stat investments remain intact. For example, 200 activity at the first tier becomes two points and 72 remaining activity. Subsequent recovery does not grant those points again.

The Slimic Creative tab groups all thirteen companion items first, followed by Slime Trail, Slime Slag, the Slime Chest and Broken Slime Block, six armor materials from leather through netherite, the Survival Slime Tuner, and the Creative Slime Tuner.

## Mining slimes

Craft a Friendly Slime item with any pickaxe, without a fixed shape, to make a **Mining Slime**. The equipped tool retains its type, damage, name, and enchantments. A recovered friendly slime can also specialize without losing its saved traits.

Mining slimes wear a yellow mining helmet and display both equipped tools beside the body, with their grips inward and tool heads upright. The recipe equips the exact input pickaxe, including its damage and enchantments. A Creative-tab default miner starts with both equipment slots empty.

A newly crafted or Creative miner waits at its release point. Open its management screen and select **Mine** to explicitly begin work in that chunk. Specializing a recovered friendly slime into a miner also starts in Wait. It clears successive horizontal layers from the release elevation downward, including dirt, stone, and ores. Releasing a recovered miner in the **same chunk** retains its current layer and saved order. Releasing it in a **different chunk or dimension** establishes a new quarry at the new elevation and automatically resumes mining. Tools, cargo, health, stats, and other traits remain intact.

The quarry reserves a two-block-wide staircase around the chunk perimeter, with flat corner landings and full-block steps. Existing solid floor blocks are retained. Missing steps are repaired using common full blocks from the bag, such as cobblestone, dirt, stone, or deepslate; each placed block consumes one item. A miner pauses if supports are unavailable or placement is blocked. Supply building blocks through its bag to continue.

Mining stays within the chunk and does not load neighbouring chunks. Air, block entities, and unbreakable blocks are skipped as mining targets. Water and lava are handled separately before ordinary blocks in the current layer. An unbreakable floor sealed by the reserved staircase, or the bottom of the dimension completes the quarry. Protected containers or other obstructions can prevent access and may require manual clearance. Block breaking and support placement respect Forge events and `mobGriefing`.

When the current quarry layer contains water or lava, a miner without an **empty bucket in its own bag** stops and requests one. A bucket in a nearby chest or the player's inventory does not directly enable removal. Transport slimes can fetch one from a Slime Chest and place it in the miner's bag; mining resumes automatically. If the bag is full, cargo is unloaded first. Each successful removal costs one stamina and grants one activity unit. The bucket is neither consumed nor filled, including for lava. Waterlogged blocks retain their block and inventory while their water is removed. Removal respects Reach, sight, Forge protection events, and `mobGriefing`.

Water and lava are removed in horizontal patches of up to **3×3 blocks**, starting with exposed source corners where possible. Each cell must be within Reach and line of sight. While a quarry layer is being drained, source regeneration is suppressed in that layer so an infinite-water pool cannot continually refill the same spot. The restriction ends when work stops or advances to another layer. Drainage removes water inside the quarry chunk; side inlets are sealed under the wall-reinforcement rules. Global source-conversion settings remain unchanged. Each removed cell has its own stamina cost and protection checks.

The miner approaches targets and works within Reach, bounded by Detection Range. It uses the shovel for shovel-suitable terrain when available and the pickaxe otherwise, provided the tool meets the block's drop requirements. Actual tools determine break speed, enchantment-aware drops, durability, Fortune, Silk Touch, and Unbreaking. Work Speed boosts breaking only when the equipped tool is effective on that block. For example, a pickaxe used on dirt without a shovel retains its ordinary slow breaking rate even at maximum Work Speed; required ore tiers still cannot be bypassed. Changing a block, tool, or its enchantments restarts that block’s partial breaking progress. When a tool breaks or an equipment slot is empty, a compatible spare in the bag is equipped automatically with its data intact. Without a suitable tool or tier, work pauses.

Drops go directly into the bag. If all drops cannot fit, the block stays intact, the tool is not damaged, and the miner pauses. Removing items or upgrading Capacity lets work resume. Bag items can be moved to the player's inventory, including with Shift-click.

Each completed block consumes one stamina. At exhaustion the miner rests, recovering 2% of maximum stamina per second until reaching 25%. Waiting, following, and other non-mining time also restore stamina. The mobGriefing game rule and Forge block-break permissions are respected.

A miner can navigate long stairs and detours inside its work chunk at default Detection Range. Actual mining and support placement still require Reach, Detection Range, and sight. Stalled repair routes report an unreachable state and retry periodically; clearing the route lets work resume. Long routes are not abandoned solely because of elapsed time while the miner is still moving.

Following preserves the saved quarry until mining is started again. Starting Mine in another chunk establishes a quarry at the current position; starting in the same chunk retains its progress. Slow approaches continue while making horizontal or grounded vertical progress. If a target's block state changes during mining, its work progress restarts and only the current block's drops are produced.

### Wall reinforcement

An active Mining Slime uses cobblestone from its own usable bag to seal side water or lava inlets and underground openings at least 2×2 blocks wide and tall. It checks the current work layer and the two blocks above it. Ordinary open air above the quarry entrance does not require a wall. The wall occupies a one-block lining immediately outside the work chunk, leaving the two-block-wide staircase usable. Only already-loaded neighboring chunks are inspected; outside terrain is never excavated. Cells behind intact quarry blocks wait until the work face opens.

Each placed block costs one cobblestone and one stamina and grants one activity unit. Wall placement does not need a bucket; removing remaining fluid still requires an empty bucket in the miner's bag. Missing cobblestone pauses work and issues a supply request. Transport brings **up to 64 cobblestone per request**, limited by available stock and usable bag space. A partly stocked chest is sufficient. A full bag must be unloaded first. Follow or Wait cancels support requests. Placement respects protection, occupied blocks, entities, the world border, and `mobGriefing`.

Unfinished wall cells survive saves and same-chunk recovery, including the remaining cells after a large opening is partly filled. A new quarry plan discards the old wall job. If a cell cannot be reached or placement is denied, work reports an obstruction without spending material.

## Slime Chests

Craft eight wooden planks around a slimeball to make a **Slime Chest**. Any plank type, including mixed types, works. Right-click to open its 27-slot inventory. It stores both resources and spare tools, retains its inventory across world saves, and supports standard item automation and comparator output. Breaking it drops the chest and its contents separately.

Slime Chests are shared logistics containers for nearby transport slimes. Ordinary chests are used to craft transport slimes and are not automatic pickup or delivery endpoints. Locked Slime Chests are not used by transport slimes.

Slime Chests use dedicated wooden face textures with green slime trim. The inventory item displays the same textured block model. The lid animates with opening and closing sounds while players use the chest.

## Transport slimes

Craft a Friendly Slime item with a **regular chest**, without a fixed shape, to make a **Transport Slime**. Stored companion traits survive specialization. A transport slime has no equipment slot: even tools travel in its normal bag.

Its Capacity is always exactly twice the common bag capacity at the same upgrade level: 18 slots initially, six additional slots per Capacity upgrade, and 54 slots at the maximum. Both bag pages can be accessed with the arrow buttons in the management screen. Shift-click moves cargo between the visible bag page and the player's inventory.

New transport slimes wait when summoned. Select **Haul** in management to start logistics. Their release position is the center of a work area whose radius is their Detection Range. They find loaded Slime Chests and same-owner workers inside this area, walk between them, and transfer items within Reach and line of sight. The area remains centered on the release position while they travel; recovery and release establish a new center. They do not load neighbouring chunks to find work. Changes to Detection Range also apply to previously selected chests before deposits or tool pickups.

When a worker stops because its bag is full, a transport slime approaches, picks up cargo that fits in its own bag, and delivers it to available warehouses or Slime Chests. The worker resumes as space becomes available. Partial stacks are merged, and another chest is used when needed. If no suitable chest has room, existing cargo stays in the transport bag and no items are discarded. It retries as space becomes available.

When a worker stops because its tool is missing or broken, the transport slime fetches one compatible spare tool from a warehouse or Slime Chest and brings it to the worker. For mining slimes this means the requested pickaxe or shovel; combat slimes request swords. For a miner paused at water or lava, the same logistics delivers one empty bucket into its bag. Full bags request unloading before bucket supply. Item counts and custom data are preserved along with tools' damage, enchantments, and names. If another carrier already supplied the worker, remaining tools stay in cargo and are reused or returned to a Slime Chest. Manually waiting or following workers are not treated as stopped work requests.

Each cargo-stack pickup or delivery and each supply pickup or delivery consumes one stamina. Exhausted carriers rest until 25% stamina, recovering 2% of maximum per second. Work Speed shortens the interval between transfer actions. Follow and Wait suspend logistics, and all bag contents, stats, current stamina, growth, and orders survive saves and recovery.

Transport travel failures are based on stalled movement. A delivery that keeps advancing at a low movement speed continues beyond 400 elapsed ticks. A carrier that cannot move reports an unreachable route and retries other routes or targets. The remaining interval between transfer actions survives saves and recovery, and pauses while stored as an item.

## JEI

JEI is optional. When installed, it shows the Friendly Slime and Slime Chest recipes, Mining Slime combinations for registered pickaxes, the Transport Slime, Herald Slime, Combat Slime, Cleaning Slime, Saddle Slime, Farming Slime, Lumberjack Slime, Guardian Angel Slime, Soggy Slime, Slime Slag recycling, and Slime Armor combinations, and companion information pages. Mining and combat recipe previews retain the corresponding tool. Armor previews show each registered chestplate conversion. Slimic also runs without JEI.

## Dye colors

Right-click an owned companion with a dye to apply that color to its original slime texture. This works for all companion types. Survival consumes one dye; Creative does not. Equipment keeps its own colors. The color survives world saves, specialization, recovery, and release. Shift-right-click still recovers the companion.

## Herald slimes

Craft a Friendly Slime item with any firework rocket, without a fixed shape, to make a **Herald Slime**. The companion retains the exact rocket design, including explosion shapes, colors, fades, trail, flicker, and flight duration. The crafting ingredients are consumed normally; signaling requires no further rockets. The Creative default uses a white small-ball explosion.

A herald watches working companions belonging to the same owner within its Detection Range. When a worker has been continuously unable to work for the configured number of minutes, it launches a visible vanilla firework. These signal fireworks do not damage living entities. Missing tools, insufficient tool tier, full cargo, exhausted stamina, disabled work, inaccessible targets, and unavailable logistics can block work. Manual Follow/Wait orders and completed quarries do not count as stalled work.

The management screen offers Watch, Follow, Wait, and a delay from 1 to 60 minutes. The default is five minutes, configured by `heraldDelayMinutes` in the world's `serverconfig/slimic-server.toml`. After the first signal for a stalled episode, the herald repeats it every 200 ticks (ten seconds) while the same worker remains blocked in range. Each worker has its own repeat timer. Resolution, manual pause, or leaving monitoring range stops further signals. Warning history stays intact for large groups and through recovery. Resuming useful work and becoming stalled again permits a new warning. Rocket design, delay, order, warning history, per-worker repeat timers, and blocked-work timers survive recovery; time does not advance inside an item.

Herald slimes wear a red-and-cream striped conical hat. Dyeing the slime preserves the hat colors.

Ordinary right-click launches a manual signal without consuming rockets. Right-click with a firework rocket to consume one in Survival and replace the saved design. Management also offers **Fire signal**; manual signals have a short cooldown and do not award activity. Healing, washing, dyeing and tuner interactions take priority over manual firing.

## Slime Tuners

Craft one **slimeball** with one **iron ingot**, without a fixed shape, to make a **Slime Tuner**. Use it directly on an owned companion within eight blocks to inspect its status, spend earned points, rename it, manage gear and cargo, or change orders. Keep it in either hand while using management. The ordinary tuner does not edit base stats.

The **Creative Slime Tuner** is available in the Slimic creative tab and has no Survival recipe. Hold it and right-click to open a separate stat editor for the companion under the crosshair within 16 blocks, falling back to the nearest companion in that range. Right-clicking a companion directly also opens it. The editor requires Creative mode and a held tuner throughout use.

The Creative Slime Tuner edits base stats, current health, current stamina, and unused stat points. Permanent attribute modifiers remain applied. Transport Capacity is twice the entered common base, and Warehouse Capacity is eight times that base; Cleaning Detection Range is five times its entered base; farming and lumberjack range is twice its entered base. Apply saves valid changes together; Refresh reloads the current values. Reducing Capacity requires emptying every bag slot that would become unavailable. Changing maximum health does not automatically refill current health.

| Field | Range |
| --- | --- |
| Health | 1–1,024; guardian base: 1–102.4 |
| Attack | 0–2,048 |
| Defense | 0–30 |
| Movement Speed | 0–10 |
| Work Speed | 0.1–32 |
| Capacity | 1–27 whole slots; transport receives 2–54; warehouses receive 8–216 |
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

Combat slimes share the common stats and have a normal-capacity bag plus three equipment slots: sword, shield, and Slime Armor. Fight searches within Detection Range and approaches visible permitted targets; Follow and Wait suspend combat. Sword damage, enchantments, knockback, fire effects, and durability use Minecraft's melee behavior. Reach bounds attack distance and Work Speed shortens the interval between attacks. Each successful hit costs one stamina and earns one activity unit. Exhausted companions rest until 25% stamina. Missing equipment can be filled from compatible bag spares, and transport slimes can fetch replacement swords from Slime Chests.

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

Craft any chestplate with **one slimeball**, without a fixed shape, to make **Slime Armor**. Leather, chainmail, iron, gold, diamond, and netherite variants appear in the Slimic creative tab; other registered chestplates can also be converted. Helmets, leggings, boots, and already converted armor do not qualify.

The result stores the original chestplate and retains its material attributes, maximum and current durability, enchantments, custom name, leather color, and other item data. Wear and enchantment changes remain authoritative after conversion. Place it in a Combat or Saddle Slime's armor slot. Ordinary chestplates are crafting ingredients and do not fit that slot directly. Material-specific models and the green slime badge distinguish the item variants.

## Cleaning slimes

Craft a Friendly Slime item with **rotten flesh**, without a fixed shape, to make a **Cleaning Slime**. Its Detection Range is always five times the common base: 40 blocks initially, with five additional blocks for each normal range upgrade. Its bag uses normal Capacity, starting at nine slots.

Clean begins work around the release position. It waits there, selects a dropped item inside the fixed spherical collection area, approaches it, collects within Reach and line of sight, and returns to the release position after each successful pickup. It does not wander while assigned to Clean, and walking toward an item does not move the collection area's center. Recovery and release establish a new center while preserving other traits.

Returns also measure stalled movement rather than total travel time. A cleaner that keeps advancing can take more than 400 ticks to reach home; a stalled return clears its warning state when travel resumes.

Pickup respects item pickup delays, reserved ownership, Forge pickup cancellation, stack limits, and available bag space. Partial pickups leave the remainder in the world. A full bag pauses collection, and a transport slime can unload it into a nearby Slime Chest. Removing cargo also resumes work. Each successful pickup action costs one stamina and earns one activity unit; Work Speed shortens the search interval. Exhaustion pauses collection until 25% stamina, with 2% of maximum restored per second.

After **three minutes** of attempting to acquire the same item without success, the cleaner searches for another eligible item with available bag space. If one exists, it changes target and temporarily avoids the failed item. Otherwise it resets the attempt timer and retries the same item. A removed or unavailable target is replaced promptly. The retry timer advances during active collection and pauses while following, waiting, or stored as an item. Retry state, cargo, dye, stats, current health, and stamina survive saves and recovery.

## Saddle slimes

Craft a Friendly Slime item with **one saddle**, without a fixed shape, to make a **Saddle Slime**. Stored companion traits and the exact input saddle are retained; the saddle is equipped on release. The Creative entry starts with empty equipment. The management screen has three equipment slots: saddle, elytra, and Slime Armor, with no cargo bag. All three can be equipped together. Equipment also accepts Shift-click transfers.

The owner can apply a saddle, elytra, or Slime Armor directly by right-clicking with it when the corresponding slot is empty. Survival consumes the applied item. An equipped saddle appears as a leather seat with side straps and stirrups. Equipped elytra show a white feathered wing texture independently of the slime's dye color.

Ordinary right-click rides a saddled companion while holding any item that has no dedicated companion interaction. Use a **Slime Tuner** for management or its **Ride** button. Only the owner may equip, manage, or ride it, and it carries one player. Riding takes priority over Follow/Wait and releases a waiting order. Without a rider, it follows its normal companion order.

| Control | Ground without elytra | With usable elytra |
| --- | --- | --- |
| WASD | Steer relative to the rider's view | Fly forward/backward and strafe; forward flight follows view pitch |
| Space | Jump | Ascend |
| Left Ctrl | Double-speed sprint | 1.75× flight boost; rebind **Slime Sprint** in Minecraft's controls |
| No movement input | Stop | Hover |
| Shift | Dismount | Dismount |

Movement Speed shortens ground-hop intervals while retaining fixed launch speed. Flight gains diminish as Movement Speed rises: normal flight starts at about 4.8 blocks per second and reaches about 6.8 after all twenty ordinary Movement Speed upgrades. Normal flight is capped at 9 blocks per second; Ctrl raises the cap to 15.75. Sprinting increases launch speed separately from the Movement Speed stat. Normal airborne flight spends two stamina per second. Ctrl doubles ground-hop speed without elytra and multiplies winged movement speed by 1.75: ground sprint spends ten stamina per second, and boosted flight spends fifteen. Airborne flight and sprinting prevent recovery. At zero stamina the wings remain open, ascent is disabled, and the companion slowly descends at about 2.4 blocks per second with limited horizontal steering and no fall damage. It recovers after landing; flight and sprinting resume at 25% stamina. Aim downward and move forward for an ordinary controlled descent. Flight requires an equipped saddle, usable elytra, and the owner as rider; rockets and Creative flight are unnecessary. Removing the saddle dismounts the rider. Removing the wings, wearing unusable wings, or dismounting restores normal gravity. Equipment, current health, stats, dye, and ownership survive saves and Shift-right-click recovery. Passengers and transient flight controls are not stored.

Equipped wings form two side-mounted wings, fold down while grounded, and flap during mounted flight, faster while boosting.

## Hostile encounters and revival

Hostile mobs with native target-goal AI, including zombies, skeletons, and spiders, can select every companion type as prey. Creepers receive no added companion targeting or swelling. Endermen retain their native behavior and receive no added companion-targeting goal. An Enderman actively targeting a living companion is classified as Hostile for Combat Slimes; once it disengages, it returns to Neutral. The corresponding target filter still applies. Non-combat companions flee for up to ten seconds after an attack, temporarily overriding work and waiting while retaining the assigned order. A mounted slime follows its rider's controls.

A fallen companion leaves a **Broken Slime Block** at its position. The block stores the same UUID, owner, specialization, name, dye, stat investments, points, activity, remaining stamina, equipment, cargo, and orders. The owner can right-click it with one slimeball to revive it with **1 health**. Survival consumes one ball only after successful revival; Creative consumes none. Clear enough space for the companion before reviving. Blocked placement, an existing matching UUID, or invalid stored data leaves the block and ball intact.

Breaking the block yields a single recovery block item carrying its stored companion; place it again to revive. A collapsed companion does not separately drop its retained equipment or cargo. Existing solid blocks and containers are preserved: if the death position cannot accept the block, a recovery block item is left there instead. The recovery item resists fire, and the placed block resists explosions. Breaking a stored recovery block in Creative also returns its companion item.

## Farming slimes

Craft a Friendly Slime summoning item with **any hoe**, without a fixed shape, to create a **Farming Slime**. The exact hoe is equipped, including its durability, enchantments, name, and other data. A straw hat and the held hoe identify it; its summoning crest is a hoe.

New farming summons wait. Give the **Farm** order in management to register the farmland present in the loaded area at that moment. Detection Range is always twice the common base: 16 blocks initially and two more per ordinary upgrade. The registered plots remain fixed while working; farmland added later requires another Farm activation. Follow or Wait stops work.

The slime harvests mature wheat, carrots, potatoes, beetroot, and compatible crop blocks, stores the drops in its ordinary nine-slot starting bag, and replants the same crop using seeds or produce from that bag. Empty plots accept suitable bag seeds. Supply starting seeds through management; this worker does not create seeds. Successful harvesting, planting, and soil restoration each award one activity unit and consume one stamina. Work Speed changes the interval between actions.

Registered farmland that reverts to dirt, grass, a dirt path, coarse dirt, or rooted dirt is restored with the equipped hoe when the space above is clear. Soil requiring two tilling stages takes two actions. A registered block observed as air, stone, or any other non-soil block is permanently removed from the plot list until another Farm activation. Unregistered dirt is left alone. Farming slimes cannot trample farmland, including while following or waiting.

The hoe has one equipment slot; bag spares and transport tool deliveries replace broken hoes. Full bags pause harvesting and request unloading. Protection events and `mobGriefing` apply. Snapshot plots, crop choices, cargo, equipment, health, stamina, and growth survive saves and recovery. Releasing at a different block clears the old plot selection and waits for a new Farm order.

## Lumberjack slimes

Craft a Friendly Slime summoning item with **any axe** to create a **Lumberjack Slime**, equipped with that exact axe. It wears a brown leather-style cap and carries its axe; its summoning crest is an axe. It initially waits. Give **Chop** in management to start work around its release position, which stays fixed as it moves. Detection Range is always twice the common base, including upgrades and tuner changes. Follow and Wait pause work; Chop resumes it.

Supply matching saplings in its bag. The slime identifies connected, unstripped overworld logs with natural leaves and stumps rooted on soil. It supports oak, spruce, birch, jungle, acacia, dark oak, mangrove, and cherry. Decorative persistent leaves, unsupported logs, and trees crossing the loaded work-area boundary are excluded. Tree inspection is bounded to 512 logs and planned central roots in total; unusually large or obstructed trees are left for manual work.

Mangroves can have raised trunks above roots and natural gaps. The slime traces the central column down to planting soil, harvests the planned central roots with the logs, and leaves outer roots and soil intact. Replanting places a mature propagule and preserves shallow source water through waterlogging.

Before cutting, it reserves one matching sapling per rooted stump from its bag. It approaches the stump and fells the connected crown into its bag, then plants each reserved sapling at its original stump. A two-by-two tree reserves and restores all four stumps. Reserved saplings are saved with the slime and cannot be taken by a transport slime. If a stump is replaced or its soil removed, its unused sapling returns to cargo. It needs space and a clear route to the stump; felling does not extend the work area. Low-hanging leaves can block a short tree's stump: clear a ground approach or increase Reach to work from farther away.

Successful log harvests and replantings each grant one activity unit and consume one stamina. The axe uses native durability; the ordinary bag starts with nine slots. A full bag or broken axe creates the same cargo/tool support requests as other workers. Block protections and `mobGriefing` apply. Origin, queued logs, reserved saplings, gear, cargo, growth, and current resources survive world saves. Nearby dropped logs and saplings are collected with normal pickup delays and ownership checks. Releasing elsewhere establishes a new origin and returns old reserved saplings to cargo, dropping only any overflow beside the slime.

The equipped axe, its enchantments, and log hardness determine cutting time. Wooden axes cut more slowly than diamond axes at the same Work Speed. Missing or incompatible axes stop logging; replacement tools start fresh breaking progress. Farming similarly requires a hoe, while naturally instant-breaking crops retain their native harvest time.

## Companion care

Right-click an injured owned companion with a **slimeball** to restore four health, capped at its maximum. Survival consumes one ball only when health is restored. Right-click with a **water bucket** to remove its dye; Survival returns an empty bucket only when a color was present. Creative preserves the held item. Shift-right-click still recovers the companion first.

If another mod cancels healing or reduces it to zero, the slimeball is retained. Switching to Spectator also ends permission to operate an already open companion management screen.

## Guardian Angel slimes

Craft a **Friendly Slime**, **nether star**, and **totem of undying** without a fixed shape. The exact crafting totem becomes the initial reserve, including its custom data. The Creative default has an empty reserve. The summoning item uses the common base with a nether-star crest. A golden halo and feathered wings identify the flying companion.

A Guardian Angel Slime follows its owner in flight. When its owner or the guardian itself takes damage from a nearby living attacker, it retaliates with a visible beam while the target is in range and line of sight. It avoids the owner and allied or same-owner tame companions. Attack controls damage, Work Speed controls the firing interval, Reach extends beam distance up to Detection Range, and each successful hit costs one stamina and grants one activity unit. Wait pauses following; it can still protect its nearby owner or defend itself, including while its owner is offline. It does not scan or force-load distant chunks.

Management has one **totem reserve** slot holding up to 64 matching totems. Custom data must match to stack. Shift-click deposits totems; withdrawal respects the ordinary one-item inventory limit and leaves overflow in reserve. The exact reserve survives recovery and world saves.

When the owner would take lethal damage within 16 blocks, one available guardian consumes one reserve totem and applies native totem-style protection: one health, regeneration, absorption, and fire resistance. This includes lethal falls, lava, drowning, and fire, as well as attacks. Normal held totems act first. Totem-use cancellation and damage that bypasses invulnerability remain respected. A guardian removed, transferred, moved out of range, or given a different reserve during the protection event cannot finish that rescue. Multiple guardians consume only one totem for the same rescue. A successful rescue grants four activity units.

Maximum health is always ten times the corresponding common value: initially **160**, with **40** added by each Health upgrade. Current health is preserved when specializing a stored companion and during recovery. Native maximum-health limits still apply; the Creative editor limits guardian base Health to 102.4.

## Worker movement and saplings

Mining skips logs, leaves, and blocks assigned to axe or hoe mining. Wooden structures can remain as obstacles; mining chooses reachable work positions around them instead of clearing them. Surface workers likewise navigate to collision-free standing positions within reach.

Farming and lumberjack status messages are separate. A lumberjack missing replanting stock displays the required sapling type and total stump count. Supply that type in its bag; a bag change immediately refreshes the tree search. Trees missing their matching saplings are skipped temporarily so another supplied tree species can be worked. Surface workers can follow progressive route segments to reach the edge of their doubled work range.

Removing a tree clears its obsolete sapling requirement. Unreachable or pickup-protected dropped materials are skipped for ten seconds so nearby tree work can continue; they can be retried afterward.

## Soggy slimes

Craft a **Friendly Slime** item with a **slimeball**, without a fixed shape, to make a **Soggy Slime**. Stored companion traits are retained. Its summon item shares the common base with a Slime Slag crest; the companion carries three slime droplets on its head and sheds water droplets. It has no bag or equipment slots.

While following or roaming and standing on solid ground, it leaves a one-sixteenth-block-high **Slime Trail** in empty space beneath itself. Wait suspends trail placement. Each placed trail costs one stamina and grants one activity unit; existing blocks and trails are preserved. Work Speed shortens the placement interval from ten ticks, while Stamina increases the available stamina. Stamina recovers at the common rate when trail production pauses. Placement respects Forge permissions and `mobGriefing`, without loading neighbouring chunks.

Break a trail to obtain **one Slime Slag**. Shovels break it faster. Craft **four Slime Slag** without a fixed shape into **one slimeball**. Removing the supporting ground also drops the trail's slag. Health, stats, owner, dye, growth, and remaining trail timers survive saving and recovery.

### Free roaming

The management screen offers **Follow**, **Wait**, and **Roam**. New Soggy Slimes still start waiting. Roam chooses nearby walkable destinations around the position where that command was selected, within Detection Range, and does not require the owner to be nearby or online. Movement uses loaded terrain and does not load new chunks for pathfinding. An attacker can temporarily cause the slime to flee; afterward it resumes roaming toward its assigned area.

Both Follow and Roam leave trails with the existing stamina cost, growth, and recovery rules. Wait stops autonomous movement and trail placement. Selecting Follow or Wait replaces Roam. The order and area survive world saves; releasing a recovered roaming slime preserves its traits and order and centers the roaming area on the new release position.


## Healing slimes

Craft a **Friendly Slime + golden carrot**, without a fixed shape. New Healing Slimes wait; select **Search** in management to begin. Follow and Wait suspend treatment. The release point anchors an area bounded by Detection Range, independent of the owner's presence. Searches use loaded terrain only. The healer approaches same-owner injured companions and Broken Slime Blocks, prioritizing revival, and acts within Reach and line of sight. It can also heal itself.

Each successful treatment consumes **one slimeball from its own usable bag and one stamina**. Living companions recover up to **4 health**; revival restores the preserved companion at **1 health**, retaining identity, equipment, cargo, stats, and orders. Later treatment can restore more health. No ball or stamina is spent on a full-health target, cancelled healing, protected block, obstructed revival, invalid data, or duplicate UUID. One successful treatment earns one activity unit. Work Speed shortens the treatment interval. Exhaustion pauses treatment until 25% stamina, with 2% of maximum restored each second while idle or resting.

Missing slimeballs request transport supply of up to one stack. A bag with no room for a ball requests unloading first. Saved health, bag, stamina, growth, and explicit orders survive recovery; release anchors a new search area.

## Warehouse slimes and storage priority

Craft a **Transport Slime + eight regular chests** in a crafting table, without a fixed arrangement. Specialization preserves the stored companion and its cargo, and begins in Wait. A Warehouse Slime has **four times a Transport Slime's Capacity at identical stats**, or eight times the common bag capacity: **72 slots initially, 216 at the normal Capacity limit**. The ratio also applies to creative tuning. It has no equipment slot; management pages show 27 bag slots at a time.

Select **Store** to collect cargo from same-owner blocked workers and deliver requested tools or materials. Collected cargo stays in the warehouse. It never travels to a Slime Chest to empty itself; when full, collection pauses until space is made available. A successful cargo pickup or supply delivery earns the transport activity reward and spends one stamina per moved stack. Idle and resting warehouses recover stamina like transport slimes.

Other carriers use same-owner Warehouse Slimes as storage endpoints, including during Wait or Follow, and prefer them over Slime Chests. Warehouses also serve as supply sources. Each transfer rechecks ownership, detection range, reach, visibility, current inventory and available space. A full or unsuitable destination is skipped in favor of another warehouse or Slime Chest; partial transfers keep the remainder in cargo. Failed paths are temporarily excluded so a different destination can be tried. No cargo is discarded when every destination is full. Different owners' warehouses and the carrier itself are excluded, preventing self-transfers and automatic warehouse-to-warehouse cargo loops.

## Support reliability

A full Healing Slime bag reports that cargo must be unloaded before slimeball delivery. Once slimeballs arrive, the supply request clears immediately. Each delivery accepts up to 64 balls and preserves the supplied stack's item data. Cancelled treatment or a protected revival keeps the job blocked while the healer searches for another companion; resolving or removing that patient clears the obsolete obstacle.

Warehouses fetch only supplies that fit their current bag. Partly filled matching cargo slots remain usable even when no empty slot can hold a different supply. Work routes use loaded terrain, including detours around obstacles, and do not load unknown chunks.

Manual revival in Adventure mode uses the permissions on the slimeball actually held in the interacting hand and the clicked block face. Ordinary ownership and successful-revival consumption rules still apply.

Repeated unsuccessful approaches also retain the blocked duration, so retrying cannot postpone herald alerts indefinitely.

Follow and Wait take effect before the next job action, including when the previous AI goal is still being stopped. They prevent a final unwanted pickup, treatment, world edit, or automatic signal.
