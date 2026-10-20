# Gameplay Guide

English | [한국어](GAMEPLAY.kr.md) · [Project overview](../README.md)

Slimic provides twenty-three types of slime companions with shared growth, specialized jobs, support abilities, and travel controls.

[Forgotten Orders](FORGOTTEN_ORDERS.md) covers ancient hostile slimes, core restoration, two acquisition paths, and the Royal Guard Slime's defensive abilities.

## Installation

Use Minecraft 1.20.1, Forge 47.x (developed against 47.4.10), and Java 17. Place `slimic-1.20.1-*.jar` from the build or release into the Minecraft `mods` folder, then launch the Forge profile. Do not install the `-sources.jar` development artifact.

## In-game guidebook

With [Patchouli](https://vazkiimods.github.io/Patchouli/) installed, craft one **book** and one **slimeball** without a fixed arrangement to make the **Slimic Companion Guide**. A 2×2 inventory grid is sufficient. The guide also appears in the Slimic Creative tab.

For multiplayer guidebook use, install the Minecraft 1.20.1 Forge version of Patchouli on both client and server.

The guide contains English and Korean instructions for all twenty-three companions, orders, stats, equipment, cooperation, work sites, and recovery. Other languages fall back to English. All entries are available immediately. Patchouli is optional; Slimic and these web guides remain usable without it. JEI is independent of the guidebook.

## Crafting a Friendly Slime

Place eight slimeballs around one soul sand or soul soil in a crafting table to make one **Friendly Slime** item:

| Slimeball | Slimeball | Slimeball |
| --- | --- | --- |
| Slimeball | Soul sand / soul soil | Slimeball |
| Slimeball | Slimeball | Slimeball |

The item is also available in the Slimic creative tab.

You can also right-click a **wild Slime of the smallest size** with a **golden apple** to turn it into one Friendly Slime. It belongs to the player who feeds it, retains its custom name, and starts in **Wait**. Survival consumes one apple; Creative does not. Larger Slimes, Magma Cubes, and enchanted golden apples do not use this interaction. **Shift-right-click** to recover the new companion into an item with its traits intact.

## Summoning and controls

Use the item on a block or water source, like a spawn egg. A newly crafted item summons a slime already tamed to the player using it. A successful Survival use consumes one item; a newly crafted item is reusable in Creative mode.

An anvil name on the item becomes the slime's custom name.

The player who first summons a fresh item is its primary owner. In this guide, an owner can also mean a player granted shared access through a [Commander Slime](#shared-owners-and-enemies); shared access does not replace the primary owner.

| Action | Result |
| --- | --- |
| Owner uses a Slime Tuner on the slime | Open the management screen for stats, upgrades, naming, equipment, inventory, and orders. |
| Owner right-clicks with a Slime Scanner | Preview the current or planned work area for 15 seconds. |
| Owner Shift-right-clicks | Recover the slime into an inventory item. |
| Use a recovered item on a block or water source | Release the same companion in Wait with its saved traits. |

All newly created companions start in **Wait**, including Friendly and Saddle Slimes, Creative defaults, and first specialization of a stored companion. Start Mine, Haul, Watch, Fight, Clean, Farm, Chop, Search, Store, Fish, Nurture, or Tunnel explicitly in management. Select Follow or Roam for Soggy Slime trails, or Follow for guardian movement. Recovered companions also start in Wait; choose a command explicitly to resume movement or work. Missing job data defaults to Wait.

Right-click a saddled Saddle Slime with an empty hand or an item without a dedicated interaction to mount it. Tuners, scanners, slimeballs, dyes, and water buckets keep their management or care functions; gear fills its matching empty equipment slot first. On a Herald Slime, ordinary right-click launches the saved firework, while holding a firework rocket replaces the saved design.

Primary and shared owners can recover the slime or change its follow/wait order. Releasing a recovered item preserves the original owner, even if another player uses the item.

## Recovery and preserved traits

Recovery saves the slime's complete persistent state. Its current health is preserved rather than refilled. Saved traits include:

- Maximum health, other attributes, and permanent attribute modifiers.
- Active effects and their remaining durations.
- Custom name and primary owner.
- Applicable work plans and job progress.
- Identity, age, equipment, and other saved entity properties.
- Other persistent companion data.

Item release always selects Wait. Ordinary world reloads retain the saved command.

Save data carries a schema version. Companion payloads without a version field can still be loaded; a stored item with an unsupported newer version cannot be released and remains intact.

Recovered slimes occupy separate, unstackable items. Their saved state survives inventory, chest, and world saves. Effect durations and other saved timers stay paused while the slime is an item.

The main inventory needs an empty slot. If it is full, recovery fails and the slime stays in the world with its state intact. A dead or dying slime cannot be recovered.

Releasing a recovered item consumes that item in both Survival and Creative mode. A failed release, such as placement inside solid blocks, leaves the stored item unchanged.

The slime appears at the selected destination. Old motion and fall distance are reset so recovery does not carry a previous fall into the new placement. Leashes are returned on recovery, and riding relationships are detached.

## Companion behavior

All twenty-three companion types start in Wait, including Friendly and Saddle Slimes. Choose Follow or a profession command in the management screen to begin movement or work; mounting a Saddle Slime enables riding. First specialization also starts in Wait. Recovered companions also start in Wait. Ownership and orders persist across saves. They persist in Peaceful difficulty and do not split on death. Combat slimes attack enabled target types while assigned to Fight; Guardian Angel Slimes retaliate against attackers of their owner or themselves.

Companions retain the original slime texture with a dye color filter. Summon icons use 16×16 pixel textures with slimeball silhouettes, a shared Friendly Slime base, and framed profession symbols. See [CREDITS.txt](../CREDITS.txt).

All twenty-three summoning items share the Friendly Slime base. Friendly Slimes have a flat badge showing the full gold-block texture without a profession symbol; specialized companions use a framed profession crest, such as a pickaxe, bundle, firework, sword, brush, saddle, hoe, axe, nether star, Slime Slag, golden carrot, or chest minecart. Fishing rods, bone meal, minecarts, dragon breath, and raw fish distinguish Fishing, Abundance, Tunnel, Dragon, and Meow Slimes. Glowstone dust, a book, an ender pearl, and redstone distinguish Glow, Commander, Ender, and Attention Slimes. Royal Guards use a golden helmet crest. The base stays consistent while the crests distinguish each specialization.

Permanent profession details remain visible without optional equipment: Cleaning Slimes wear blue work caps, Combat Slimes wear metal headbands with red crests, Saddle Slimes have brown bridles with gold buckles, and Soggy Slimes carry three slime droplets. Armor, saddles, and wings remain visible with these details.

Profession accessories have their own texture files, keeping their colors when the body is dyed. Resource packs can replace the accessory and management-screen artwork; see [Texture editing](TEXTURES.md).

Every summoning item shows its own profession summary, followed by shared release, recovery, and management instructions. Fresh items explain the initial Wait order; recovered items describe restoring the saved companion.

## Shared stats and growth

Use a **Slime Tuner** on an owned slime to open its management screen. The name field sets a visible name above the slime, like a name tag. Follow and Wait buttons change its order. Mining slimes have a Mine button, separate pickaxe and shovel slots, and a bag. Transport slimes have a Haul button and a larger bag, with no equipment slot.

| Stat | Initial value | Per point | Purpose |
| --- | --- | --- | --- |
| Health | 16 | +4 | Maximum health; upgrading does not refill current health. |
| Attack | 2 | +1 | Melee damage for Combat and Dragon Slimes, Dragon flame breath, and Guardian Angel beams. |
| Defense | 0 | +1 | Minecraft's standard armor attribute and damage calculation. |
| Movement Speed | 0.30 | +0.015 | Shortens the pause between ground hops; launch speed and hop distance stay fixed. Also scales mounted flight speed. |
| Work Speed | 1.0× | +0.1× | Mining progress and action intervals for transport, combat, cleaning, farming, logging, healing, warehouse logistics, fishing, plant growth, dragon breath, beams, and trails. |
| Capacity | 9 slots / transport: 18 / warehouse: 72 | +3 / +6 / +24 slots | Usable bag slots, up to 27 / 54 / 216 respectively. |
| Stamina | 100 | +20 | Maximum stamina for continuous work and proportional recovery. |
| Reach | 3 blocks | +0.25 blocks | Maximum distance for job actions, attacks, healing, and item transfers. |
| Detection Range | 8 blocks | +1 block | Worker target/work-area range and herald monitoring radius. Cleaning uses 5× the common base; farming/logging/healing/abundance use 2×. |

These are common base values. Guardian Angel Health is ten times the corresponding common value, and Attention Slime effective Movement Speed is 1.5 times its base value.

Capacity supports six upgrades; the other stats support twenty. Primary and shared owners can spend points, rename the companion, and use its management inventory. All stats, unused points, activity progress, and current stamina survive world saves and recovery.

Firefly bursts, each successfully gathered companion, endpoint linking, and completed teleporter arrivals award one activity unit to the acting slime.

The first stat point requires 32 activity units. Each earned point increases the next requirement by 64 units, capped at 4,096. Successful fluid removal, slime-trail placement, mining, combat hits, cleaning pickups, crop harvesting/planting, soil restoration, log harvesting, material collection, and replanting each grant one unit. Successful healing, revival, fishing catches, and plant-growth applications each grant one unit. Transport grants four units per cargo-stack delivery into a warehouse or Slime Chest, or supplies delivered to a worker; warehouse cargo collection also earns this reward. Ordinary transport collection and fetching supplies grant none. This higher delivery reward compensates for travel, while actual progress rates still depend on the job and terrain.

All companions can earn one unit per 100 ticks (five seconds) of actual horizontal movement while following a nearby owner or carrying their rider. Attention Slime roaming and escaping an attacker use the same movement interval. Actual damage from a non-allied attacker or a successful shield block also earns one unit, limited to one defensive award per second; owner damage and environmental damage grant none. Passive waiting, standing still, being transported by teleport, and changing orders grant none. Heralds also earn one unit per 100 ticks of monitoring an active or blocked worker with same-owner or mutual sharing authorization in range; idle, completed, or manually waiting workers grant none. Partial following and monitoring timers survive saves and recovery and pause in item storage. Signals do not grant extra activity.

The world's `serverconfig/slimic-server.toml` configures `transportActivityPerDelivery` (default 4, range 1–64), `followingActivityIntervalTicks`, and `monitoringActivityIntervalTicks` (both default 100, range 20–1,200). Existing points and activity are retained.

Stats have different roles. Health, Defense, and Movement Speed benefit every companion. Attack affects Combat, Dragon, and Guardian Angel Slimes; Work Speed, Capacity, Stamina, and Reach serve mining, transport, combat, cleaning, farming, logging, healing, warehouse, fishing, abundance, tunnel, and dragon slimes. Guardian Angel Slimes also use Work Speed, Stamina, and Reach for their abilities. Soggy slimes use Work Speed and Stamina for trail production. Stamina also powers normal mounted flight, ground or air sprinting, firefly bursts, gathering, and Ender abilities. Detection Range serves those workers, herald monitoring, guardian beams, and the Soggy Slime roaming area. Friendly slimes retain all investments when specialized. Upgrade tooltips identify stats outside the current specialization.

Stamina recovers at **2% of maximum per second** during recovery: 2 at 100 maximum, 4 at 200, and 10 at 500. Workers using exhaustion rest resume at 25%, taking about 13 seconds from zero regardless of maximum, subject to tick timing. Larger stamina pools support longer work bursts and proportionally higher recovery. Recovery opportunities still depend on the job.

## Work orders and support requests

An order describes the assigned task: Follow, Wait, Mine, Haul, Watch, Fight, Clean, Farm, Chop, Roam, Search, Store, Fish, Nurture, or Tunnel. Execution is idle, active, blocked, or complete. A blocked task carries a reason such as a full bag, missing tool, unsuitable tool tier, exhaustion, missing storage, full storage, unreachable terrain, missing supports, or disabled work.

A full working bag issues a cargo-pickup request. A worker missing its required tool issues a tool-supply request. A miner encountering water or lava without an empty bucket issues a separate bucket-supply request; a full bag also requests cargo pickup. Transport acts on these requests and rechecks them before every transfer. Heralds use the shared blocked state and its continuous duration. Manual Follow/Wait orders and completed quarries are not blocked work.

Existing activity is recalculated against the current thresholds when a companion is loaded. Newly earned points are granted and remaining activity is retained; existing points and stat investments remain intact. For example, 200 activity at the first tier becomes two points and 72 remaining activity. Subsequent recovery does not grant those points again.

The Slimic Creative tab groups all twenty-three companion items first, followed by Slime Trail, Slime Slag, the Ancient Command Seal, the Slime Chest and Broken Slime Block, six armor materials from leather through netherite, the Survival Slime Tuner, the Slime Scanner, and the Creative Slime Tuner. Unbound Slime Teleporters do not appear in the tab; obtain a bound teleporter by linking an Ender Slime.

## Mining slimes

Craft a Friendly Slime item with any pickaxe, without a fixed shape, to make a **Mining Slime**. The equipped tool retains its type, damage, name, and enchantments. A recovered friendly slime can also specialize without losing its saved traits.

Mining slimes wear a yellow mining helmet and display both equipped tools beside the body, with their grips inward and tool heads upright. The recipe equips the exact input pickaxe, including its damage and enchantments. A Creative-tab default miner starts with both equipment slots empty.

A newly crafted or Creative miner waits at its release point. Open its management screen and select **Mine** to explicitly begin work in that chunk. Specializing a recovered friendly slime into a miner also starts in Wait. It clears successive horizontal layers from the release elevation downward, including dirt, stone, and ores. Releasing a recovered miner in the **same chunk** retains its current layer but starts in Wait. Releasing it in a **different chunk or dimension** establishes a new quarry at the new elevation and starts in Wait. Select Mine to begin. Tools, cargo, health, stats, and other traits remain intact.

The quarry reserves a two-block-wide staircase around the chunk perimeter, with flat corner landings and full-block steps. Existing solid floor blocks are retained. Missing steps use supported repair blocks from the bag; each placement consumes one item. Default repair materials are cobblestone, cobbled deepslate, dirt, stone, deepslate, netherrack, end stone, tuff, andesite, diorite, granite, blackstone, basalt, and calcite. A miner pauses if supports are unavailable or placement is blocked. Supply supported repair materials through its bag to continue.

Mining Slimes in the same chunk share the staircase layout, including new workers starting at different heights. Registered staircase protection remains in world data after builders wait, are recovered, unload, or leave the world; miners with different owners also preserve it. Existing saved work records retain their earlier staircase layouts alongside the shared layout. A repair that would fill another staircase's headroom pauses as unreachable.

Mining stays within the chunk and does not load neighbouring chunks. Air, logs, leaves, axe- or hoe-mineable blocks, block entities, and unbreakable blocks are skipped as ordinary mining targets. Water and lava are handled separately before ordinary blocks in the current layer. An unbreakable floor sealed by the reserved staircase, or the bottom of the dimension completes the quarry. Protected containers or other obstructions can prevent access and may require manual clearance. Block breaking and support placement respect Forge events and `mobGriefing`.

When the current quarry layer contains water or lava, a miner without an **empty bucket in its own bag** stops and requests one. A bucket in a nearby chest or the player's inventory does not directly enable removal. Transport slimes can fetch one from a warehouse or Slime Chest and place it in the miner's bag; mining resumes automatically. If the bag is full, cargo is unloaded first. Each successful removal costs one stamina and grants one activity unit. The bucket is neither consumed nor filled, including for lava. Waterlogged blocks retain their block and inventory while their water is removed. Removal respects Reach, sight, Forge protection events, and `mobGriefing`.

Water and lava are removed in horizontal patches of up to **3×3 blocks**, starting with exposed source corners where possible. Each cell must be within Reach and line of sight. While a quarry layer is being drained, source regeneration is suppressed in that layer so an infinite-water pool cannot continually refill the same spot. The restriction ends when Follow or Wait stops the mining order, or the quarry advances to another layer. Drainage removes water inside the quarry chunk; side inlets are sealed under the wall-reinforcement rules. Global source-conversion settings remain unchanged. Each removed cell has its own stamina cost and protection checks.

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

Craft eight wooden planks around a slimeball to make a **Slime Chest**. Any plank type, including mixed types, works. Right-click to open its 27-slot inventory. It stores both resources and spare tools, retains its inventory across world saves, and supports standard item automation and comparator output. Breaking it drops the chest and its contents separately. A custom name stays on the chest item and is restored when it is placed again.

Slime Chests are shared logistics containers for nearby transport slimes. Ordinary chests are used to craft transport slimes and are not automatic pickup or delivery endpoints. Locked Slime Chests are not used by transport slimes.

Slime Chests use dedicated wooden face textures with green slime trim. The inventory item displays the same textured block model. The lid animates with opening and closing sounds while players use the chest.

## Transport slimes

Craft a Friendly Slime item with a **regular chest**, without a fixed shape, to make a **Transport Slime**. Stored companion traits survive specialization. A transport slime has no equipment slot: even tools travel in its normal bag.

Its Capacity is always exactly twice the common bag capacity at the same upgrade level: 18 slots initially, six additional slots per Capacity upgrade, and 54 slots at the maximum. The management screen shows up to 27 usable slots per page. Arrow buttons open the second page once Capacity exceeds 27 slots. Shift-click moves cargo between the visible bag page and the player's inventory.

New transport slimes wait when summoned. Select **Haul** in management to start logistics. Their release position is the center of a work area whose radius is their Detection Range. They find loaded Slime Chests and same-owner or mutually shared warehouses and workers inside this area, walk between them, and transfer items within Reach and line of sight. The area remains centered on the release position while they travel; recovery and release establish a new center. They do not load neighbouring chunks to find work. Changes to Detection Range also apply to previously selected chests before deposits or tool pickups.

When a worker requests unloading, for example because its bag is full, a transport slime approaches, picks up cargo that fits in its own bag, and delivers it to available warehouses or Slime Chests. A worker paused by full cargo resumes as space becomes available. Partial stacks are merged, and another storage destination is used when needed. If no suitable destination has room, existing cargo stays in the transport bag and no items are discarded. It retries as space becomes available.

When a worker stops because its tool is missing or broken, the transport slime fetches one compatible spare tool from a warehouse or Slime Chest and brings it to the worker. For mining slimes this means the requested pickaxe or shovel; combat slimes request swords. For a miner paused at water or lava, the same logistics delivers one empty bucket into its bag. Full bags request unloading before bucket supply. Item counts and custom data are preserved along with tools' damage, enchantments, and names. If another carrier already supplied the worker, remaining tools stay in cargo and are reused or returned to storage. Manually waiting or following workers are not treated as stopped work requests.

Each cargo-stack pickup or delivery and each supply pickup or delivery consumes one stamina. Exhausted carriers rest until 25% stamina, recovering 2% of maximum per second. Work Speed shortens the interval between transfer actions. Follow and Wait suspend logistics. All bag contents, stats, current stamina, and growth survive saves and recovery. World loads retain commands; item release starts in Wait.

Transport travel failures are based on stalled movement. A delivery that keeps advancing at a low movement speed continues beyond 400 elapsed ticks. A carrier that cannot move reports an unreachable route and retries other routes or targets. The remaining interval between transfer actions survives saves and recovery, and pauses while stored as an item.

## JEI

JEI is optional. When installed, it shows crafting recipes and information for all twenty-three companion types, Slime Chests, Slime Slag recycling, and Slime Armor. Tool-based specialization previews include registered pickaxes, swords, hoes, axes, and fishing rods and retain the matching tool. Armor previews show each supported registered chestplate conversion. With Patchouli also installed, JEI shows the guidebook recipe. Slimic also runs without JEI.

## Dye colors

Right-click an owned companion with a dye to apply that color to its original slime texture. This works for all companion types. Survival consumes one dye; Creative does not. Equipment keeps its own colors. The color survives world saves, specialization, recovery, and release. Shift-right-click still recovers the companion.

## Herald slimes

Craft a Friendly Slime item with any firework rocket, without a fixed shape, to make a **Herald Slime**. The companion retains the exact rocket design, including explosion shapes, colors, fades, trail, flicker, and flight duration. The crafting ingredients are consumed normally; signaling requires no further rockets. The Creative default uses a white small-ball explosion.

A herald watches working companions with the same primary owner or mutual sharing authorization within its Detection Range. When a worker has been continuously unable to work for the configured number of minutes, it launches a visible vanilla firework. These signal fireworks do not damage living entities. Missing tools, insufficient tool tier, full cargo, exhausted stamina, disabled work, inaccessible targets, and unavailable logistics can block work. Manual Follow/Wait orders and completed quarries do not count as stalled work.

The management screen offers Watch, Follow, Wait, and a delay from 1 to 60 minutes. The default is five minutes, configured by `heraldDelayMinutes` in the world's `serverconfig/slimic-server.toml`. After the first signal for a stalled episode, the herald repeats it every 200 ticks (ten seconds) while the same worker remains blocked in range. Each worker has its own repeat timer. Resolution, manual pause, or leaving monitoring range stops further signals. Warning history stays intact for large groups and through recovery. Resuming useful work and becoming stalled again permits a new warning. Rocket design, delay, warning history, and per-worker repeat timers survive recovery; time does not advance inside an item. Releasing the herald starts in Wait; select Watch to resume monitoring.

Herald slimes wear a red-and-cream striped conical hat. Dyeing the slime preserves the hat colors.

Ordinary right-click launches a manual signal without consuming rockets. Right-click with a firework rocket to consume one in Survival and replace the saved design. Management also offers **Fire signal**; manual signals have a short cooldown and do not award activity. Healing, washing, dyeing and tuner interactions take priority over manual firing.

## Slime Tuners

Craft one **slimeball** with one **iron ingot**, without a fixed shape, to make a **Slime Tuner**. Use it directly on an owned companion within normal interaction reach to inspect its status, spend earned points, rename it, manage gear and cargo, or change orders. While using management, stay within eight blocks and keep the tuner in either hand. The ordinary tuner does not edit base stats. Companion bags use nine columns and up to three rows per page, aligned with the player inventory and hotbar. Dark cells are locked by Capacity and cannot hold items. Paging controls appear only when the bag spans multiple pages. The management panel is 368×240 GUI units; lower Minecraft's GUI Scale if it extends beyond the window.

Place one **Slime Tuner** in the center of a crafting table, with **glass blocks above, below, left, and right**, to make a **Slime Scanner**. Ordinary right-click with the scanner previews work areas. Shift-right-click recovers a companion regardless of the held tool.

The **Creative Slime Tuner** is available in the Slimic creative tab and has no Survival recipe. Hold it and right-click to open a separate stat editor for the companion under the crosshair within 16 blocks, falling back to the nearest companion in that range. Right-clicking a companion directly also opens it. The editor requires Creative mode and a held tuner throughout use.

The Creative Slime Tuner edits base stats, current health, current stamina, and unused stat points. Permanent attribute modifiers remain applied. Guardian Health is ten times its base, and Attention Movement Speed is 1.5 times its base. Transport Capacity is twice the entered common base, and Warehouse Capacity is eight times that base; Cleaning Detection Range is five times its entered base; Farming, Lumberjack, Healing, and Abundance Detection Range is twice its entered base. Apply saves valid changes together; Refresh reloads the current values. Reducing Capacity requires emptying every bag slot that would become unavailable. Changing maximum health does not automatically refill current health.

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

Portal travel rebases fixed work areas at the arrival point. Companions restored into another dimension by a command also update their area before their first work tick. Mining slimes establish a new quarry while retaining their active or paused command; transport and cleaning slimes retain their follow, wait, or work order. Matching X/Z chunk coordinates in different dimensions identify separate work areas. Health, stamina, growth, ownership, equipment, and cargo remain intact.

Ordinary reloads within the same dimension retain the saved work area. Cancelled dimension transfers leave the original area unchanged.

## Combat slimes

Craft a Friendly Slime item with any sword, without a fixed shape, to make a **Combat Slime**. The recipe equips the exact input sword, including its wear and enchantments. Stored companion traits survive specialization. Creative defaults have empty equipment.

Combat slimes share the common stats and have a normal-capacity bag plus three equipment slots: sword, shield, and Slime Armor. Fight searches within Detection Range and approaches visible permitted targets; Follow and Wait suspend combat. Sword damage, enchantments, knockback, fire effects, and durability use Minecraft's melee behavior. Reach bounds attack distance and Work Speed shortens the interval between attacks. Each successful hit costs one stamina and earns one activity unit. Exhausted companions rest until 25% stamina. Missing equipment can be filled from compatible bag spares, and transport slimes can fetch replacement swords from warehouses or Slime Chests.

Pursuit reports an unreachable route after 400 ticks without horizontal or grounded vertical progress. Slow movement keeps pursuit active, and advancing again clears the blocked state. Hopping in place does not count as travel.

An equipped shield blocks frontal attacks while approaching and between strikes, using normal blocking timing, durability, and shield disabling by compatible weapons. The body turns toward its target so frontal coverage follows the threat. With a shield, attack intervals are at least ten ticks to allow the normal five-tick activation delay even at high Work Speed. Slime Armor applies its original chestplate's defense, toughness, knockback resistance, enchantments, and durability. The Defense stat adds the common base armor attribute separately.

Open **Targets** in the management screen to switch between the bag and six independent attack filters:

| Target type | Default | Classification |
| --- | --- | --- |
| Hostile | On | Hostile mobs other than neutral mobs and creepers; explicitly assigned enemy players and Endermen targeting living companions also use this filter. |
| Neutral | Off | Neutral mobs, including those capable of retaliation. |
| Passive | Off | Other mobs, such as livestock and villagers. |
| Players | Off | Other players permitted by server PVP and the owner's team rules. |
| Tamed | Off | Other owners' tamed companions and horses. |
| Creepers | Off | Creepers, controlled separately from Hostile. |

The owner, the owner's other tamed companions, allies, spectators, and Creative players remain excluded. Players, tamed mobs, and creepers normally use their own switches. A player explicitly assigned as an enemy through shared-ownership management uses Hostile instead; PvP and ally protections still apply. Disabling a current target's category stops that attack. Filters, current health, stamina, cooldowns, stats, equipment, and cargo survive recovery and release. The released companion starts in Wait.

## Slime Armor

Craft any chestplate with **one slimeball**, without a fixed shape, to make **Slime Armor**. Leather, chainmail, iron, gold, diamond, and netherite variants appear in the Slimic creative tab; other registered chestplates can also be converted. Helmets, leggings, boots, and already converted armor do not qualify.

The result stores the original chestplate and retains its material attributes, maximum and current durability, enchantments, custom name, leather color, and other item data. Wear and enchantment changes remain authoritative after conversion. Place it in a Combat, Dragon, or Saddle Slime's armor slot. Ordinary chestplates are crafting ingredients and do not fit that slot directly. All six variants share the same icon silhouette and green slime badge; their colors distinguish materials, and leather armor displays its dye color.

## Cleaning slimes

Craft a Friendly Slime item with **rotten flesh**, without a fixed shape, to make a **Cleaning Slime**. Its Detection Range is always five times the common base: 40 blocks initially, with five additional blocks for each normal range upgrade. Its bag uses normal Capacity, starting at nine slots.

Clean begins work around the release position. It waits there, selects a dropped item inside the fixed spherical collection area, approaches it, collects within Reach and line of sight, and returns to the release position after each successful pickup. It does not wander while assigned to Clean, and walking toward an item does not move the collection area's center. Recovery and release establish a new center while preserving other traits.

Returns also measure stalled movement rather than total travel time. A cleaner that keeps advancing can take more than 400 ticks to reach home; a stalled return clears its warning state when travel resumes.

Pickup respects item pickup delays, reserved ownership, Forge pickup cancellation, stack limits, and available bag space. Partial pickups leave the remainder in the world. A full bag pauses collection, and a transport slime can unload it into a nearby warehouse or Slime Chest. Removing cargo also resumes work. Each successful pickup action costs one stamina and earns one activity unit; Work Speed shortens the search interval. Exhaustion pauses collection until 25% stamina, with 2% of maximum restored per second.

After **three minutes** of attempting to acquire the same item without success, the cleaner searches for another eligible item with available bag space. If one exists, it changes target and temporarily avoids the failed item. Otherwise it resets the attempt timer and retries the same item. A removed or unavailable target is replaced promptly. The retry timer advances during active collection and pauses while following, waiting, or stored as an item. Retry state, cargo, dye, stats, current health, and stamina survive saves and recovery.

## Saddle slimes

Craft a Friendly Slime item with **one saddle**, without a fixed shape, to make a **Saddle Slime**. Stored companion traits and the exact input saddle are retained; the saddle is equipped on release. The Creative entry starts with empty equipment. The management screen has three equipment slots: saddle, elytra, and Slime Armor, with no cargo bag. All three can be equipped together. Equipment also accepts Shift-click transfers.

The owner can apply a saddle, elytra, or Slime Armor directly by right-clicking with it when the corresponding slot is empty. Survival consumes the applied item. An equipped saddle appears as a leather seat with side straps and stirrups. Equipped elytra show a white feathered wing texture independently of the slime's dye color.

Ordinary right-click rides a saddled companion while holding any item that has no dedicated companion interaction. Use a **Slime Tuner** for management or its **Ride** button. Primary and shared owners may equip, manage, or ride it, and it carries one player. Riding takes priority over Follow/Wait and releases a waiting order. Without a rider, it follows its normal companion order.

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

Hostile mobs with native target-goal AI, including zombies, skeletons, and spiders, can select every companion type as prey. Creepers receive no added companion targeting or swelling. Endermen retain their native behavior and receive no added companion-targeting goal. Vanilla Slimes only select a companion after it attacks them; this exception does not apply to Magma Cubes. Combat and Dragon Slimes can still attack vanilla Slimes first when Hostile targeting is enabled. An Enderman actively targeting a living companion is classified as Hostile for Combat Slimes; once it disengages, it returns to Neutral. The corresponding target filter still applies. Companions other than Combat, Dragon, and Guardian Angel Slimes flee for up to ten seconds after an attack, temporarily overriding work and waiting while retaining the assigned order. A pinned Ender Slime stays anchored, and a mounted slime follows its rider's controls.

A fallen companion leaves a **Broken Slime Block** at its position. The block stores the same UUID, owner, specialization, name, dye, stat investments, points, activity, remaining stamina, equipment, cargo, and applicable work plans. The owner can right-click it with one slimeball to revive it in **Wait** with **1 health**. Survival consumes one ball only after successful revival; Creative consumes none. Clear enough space for the companion before reviving. Blocked placement, an existing matching UUID, or invalid stored data leaves the block and ball intact.

Breaking the block yields a single recovery block item carrying its stored companion; place it again to revive. A collapsed companion does not separately drop its retained equipment or cargo. Existing solid blocks and containers are preserved: if the death position cannot accept the block, a recovery block item is left there instead. The recovery item resists fire, and the placed block resists explosions. Breaking a stored recovery block in Creative also returns its companion item.

## Farming slimes

Craft a Friendly Slime summoning item with **any hoe**, without a fixed shape, to create a **Farming Slime**. The exact hoe is equipped, including its durability, enchantments, name, and other data. A straw hat and the held hoe identify it; its summoning crest is a hoe.

New farming summons wait. Give the **Farm** order in management to survey loaded farmland around its current position. Detection Range is always twice the common base: 16 blocks initially and two more per ordinary upgrade. After registration, the plot list stays fixed while working. To include later farmland, select **Wait**, then **Farm** again. Follow or Wait stops work.

The slime harvests mature wheat, carrots, potatoes, beetroot, and compatible crop blocks, stores the drops in its ordinary nine-slot starting bag, and replants the same crop using seeds or produce from that bag. Empty plots accept suitable bag seeds. Planting consumes matching bag stock, which harvest drops can replenish. Use management to supply any shortage. Successful harvesting, planting, and soil restoration each award one activity unit and consume one stamina. Work Speed changes the interval between actions.

Registered farmland that reverts to dirt, grass, a dirt path, coarse dirt, or rooted dirt is restored with the equipped hoe when the space above is clear. Soil requiring two tilling stages takes two actions. A registered block observed as air, stone, or any other non-soil block is removed from the plot list; select Wait, then Farm to register it again. Unregistered dirt is left alone. Farming slimes cannot trample farmland, including while following or waiting.

The hoe has one equipment slot; bag spares and transport tool deliveries replace broken hoes. Full bags pause harvesting and request unloading. Protection events and `mobGriefing` apply. Snapshot plots, crop choices, cargo, equipment, health, stamina, and growth survive saves and recovery. Releasing away from the farm center block clears the old plot selection and waits for a new Farm order.

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

A Guardian Angel Slime follows its owner in flight. When its owner or the guardian itself takes damage from a nearby living attacker, it retaliates with a visible beam while the target is in range and line of sight. It avoids the owner and allied or same-owner tame companions. Attack controls damage and Work Speed controls the firing interval. Beam distance is the smaller of four times Reach and Detection Range (eight blocks initially). Each successful hit costs one stamina and grants one activity unit. Wait pauses following; the guardian can still protect a nearby owner and defend itself. It can defend itself while the owner is offline. It does not scan or force-load distant chunks.

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

Both Follow and Roam leave trails with the existing stamina cost, growth, and recovery rules. Wait stops autonomous movement and trail placement. Selecting Follow or Wait replaces Roam. The order and area survive world saves. Releasing a recovered roaming slime preserves its other traits, starts in Wait, and centers its next roaming area on the new release position.

## Healing slimes

Craft a **Friendly Slime + golden carrot**, without a fixed shape. New Healing Slimes wait; select **Search** in management to begin. Follow and Wait suspend treatment. The release point anchors an area bounded by Detection Range, independent of the owner's presence. Searches use loaded terrain only. The healer approaches injured companions and Broken Slime Blocks with the same primary owner or mutual sharing authorization, prioritizing revival, and acts within Reach and line of sight. It can also heal itself.

Each successful treatment consumes **one slimeball from its own usable bag and one stamina**. Living companions recover up to **4 health**; revival restores the preserved companion at **1 health**, retaining identity, equipment, cargo, stats, and orders. Later treatment can restore more health. No ball or stamina is spent on a full-health target, cancelled healing, protected block, obstructed revival, invalid data, or duplicate UUID. One successful treatment earns one activity unit. Work Speed shortens the treatment interval. Exhaustion pauses treatment until 25% stamina, with 2% of maximum restored each second while idle or resting.

Missing slimeballs request transport supply of up to one stack. A bag with no room for a ball requests unloading first. Saved health, bag, stamina, and growth survive recovery; release anchors a new search area and starts in Wait.

## Warehouse slimes and storage priority

Craft a **Transport Slime + eight regular chests** in a crafting table, without a fixed arrangement. Specialization preserves the stored companion and its cargo, and begins in Wait. A Warehouse Slime has **four times a Transport Slime's Capacity at identical stats**, or eight times the common bag capacity: **72 slots initially, 216 at the normal Capacity limit**. The ratio also applies to creative tuning. It has no equipment slot; management pages show 27 bag slots at a time.

Select **Store** to collect requested cargo from same-owner or mutually shared workers and deliver requested tools or materials. Collected cargo stays in the warehouse. It never travels to a Slime Chest to empty itself; when full, collection pauses until space is made available. A successful cargo pickup or supply delivery earns the transport activity reward and spends one stamina per moved stack. Idle and resting warehouses recover stamina like transport slimes.

Other carriers use same-owner or mutually shared Warehouse Slimes as storage endpoints, including during Wait or Follow, and prefer them over Slime Chests. Warehouses also serve as supply sources. Each transfer rechecks ownership, detection range, reach, visibility, current inventory and available space. A full or unsuitable destination is skipped in favor of another warehouse or Slime Chest; partial transfers keep the remainder in cargo. Failed paths are temporarily excluded so a different destination can be tried. No cargo is discarded when every destination is full. Warehouses without same-owner or mutual sharing authorization and the carrier itself are excluded, preventing self-transfers and automatic warehouse-to-warehouse cargo loops.

## Support reliability

A full Healing Slime bag reports that cargo must be unloaded before slimeball delivery. Once slimeballs arrive, the supply request clears immediately. Each delivery accepts up to 64 balls and preserves the supplied stack's item data. Cancelled treatment or a protected revival keeps the job blocked while the healer searches for another companion; resolving or removing that patient clears the obsolete obstacle.

Warehouses fetch only supplies that fit their current bag. Partly filled matching cargo slots remain usable even when no empty slot can hold a different supply. Work routes use loaded terrain, including detours around obstacles, and do not load unknown chunks.

Manual revival in Adventure mode uses the permissions on the slimeball actually held in the interacting hand and the clicked block face. Ordinary ownership and successful-revival consumption rules still apply.

Repeated unsuccessful approaches also retain the blocked duration, so retrying cannot postpone herald alerts indefinitely.

Follow and Wait take effect before the next job action, including when the previous AI goal is still being stopped. They prevent a final unwanted pickup, treatment, world edit, or automatic signal.

## Fishing slimes

Combine a Friendly Slime with a fishing rod in any arrangement. The original rod retains its enchantments, wear, name, and item data, and is equipped when released. A fresh Creative-default Fishing Slime also receives one rod. It starts in **Wait**; select **Fish** to begin.

The slime finds a dry shore beside source water inside its release-centered Detection Range. Catches enter its normal bag. After five minutes of active work time without a catch it seeks another spot. Native fishing loot tables and open-water treasure conditions apply. **Lure** reduces waiting, **Luck of the Sea** changes loot chances, **Unbreaking** reduces rod wear, and **Mending** repairs the rod with catch experience before remaining experience appears. Work Speed shortens waiting.

Each successful catch costs one stamina. A full bag pauses fishing and requests cargo pickup; a missing or broken rod requests a replacement from transport or warehouse support. Pending catches and the original rod survive recovery. The slime wears blue overalls with low straps and gold buttons below its eyes.

## Abundance slimes

Combine a Healing Slime, one bone meal, and one nether star without a fixed arrangement. The original companion's identity, health, growth, equipment, and bag remain intact. It starts waiting; select **Nurture** to work.

Abundance retains the Healing Slime's healing and revival and gives those patients priority. It then prioritizes crops and saplings over grass while applying native bonemeal effects to nearby growable plants, using **two stamina per application** and no bone meal item. Native random growth chances remain; an allowed application spends stamina and earns activity even without immediate plant growth. Work Speed controls the growth interval. Its fixed Detection Range is always twice the common base, initially 16 blocks. Plant growth requires the surrounding terrain to be loaded.

Healing still requires slimeballs, with normal transport/warehouse supply. Plant growth respects `mobGriefing`, world borders, owner permissions, and Forge bonemeal protection events. It carries a golden lyre beside its body, with matching strings and a laurel.

## Tunnel slimes

Combine a Mining Slime and a nether star. Its tools, bag, health, identity, and growth remain intact; the new specialization starts waiting. It wears a minecart-shaped shell.

Management provides a horizontal direction selector and one **lining template** slot beside the pickaxe and shovel. The initial direction is north and the initial lining is cobblestone. Click the template with a suitable solid block on the cursor to copy its type, or with an empty cursor to clear it. The held stack is unchanged. The displayed sample is never consumed, extracted, or used as real inventory. Falling blocks, explosives, and blocks with inventories are unsuitable. Changing the direction or template pauses work; select **Tunnel** to resume.

The worker clears a **3×3 horizontal passage** in the chosen direction and fills replaceable holes or fluids in its floor, ceiling, and sides using real matching blocks from its own bag. It preserves existing solid walls. Missing material stops work and requests up to 64 matching blocks from nearby transport or warehouse supply. If storage has none, it remains stopped. A full bag requests unloading.

Mining uses the original pickaxe/shovel, native breaking time, tool tiers, enchantments and protection events. Fluid removal requires one empty bucket in its own bag without consuming or filling it. Protected shared quarry stairs, containers, logs, leaves, and unbreakable blocks stop the bore. Work never excavates a whole chunk or acquires unloaded chunks. Moving the recovered slime away from its route reanchors the bore.

In flooded passages, the slime holds the tunnel height instead of floating away. It avoids repeatedly draining the same returning flow while clearing the current face. Resting and waiting for supplies suspend forward movement. If it cannot get closer to a work position for 20 seconds, it reports an unreachable route so a Herald Slime can signal the blockage.

## Dragon slimes

Combine a Combat Slime and a dragon head. The original sword, shield, Slime Armor, filters, cargo, health, and growth remain. It starts waiting; equip a sword and select **Fight**.

The slime flies with bat-shaped wings, uses its sword in melee, and periodically sends a visible flame breath at an enabled target in Detection Range. The breath costs **four stamina**, uses Attack, and can ignite the target; Work Speed shortens its interval. It applies the same owner, ally, PvP, and target-filter checks as ordinary combat. The targeted flame does not ignite terrain or damage an allied bystander. Its equipment and loot logistics follow Combat Slime behavior.

## Meow slimes

Combine a Friendly Slime with one raw cod, salmon, tropical fish, or pufferfish. Cooked fish does not work. The slime wears a cat head and starts **Active + Wait**.

Active companions make nearby creepers use the same **six-block avoidance goal** used for cats. **Active/Inactive** and **Wait/Roam** are independent: disabling the aura does not change movement, and a movement command does not reactivate it. Waiting companions still repel creepers while active. Native avoidance does not guarantee protection from an already ignited creeper.

It retains ordinary companion ownership, stats, naming, dyeing, healing, and recovery, with no bag or equipment slots. World saves retain activation and movement orders. Recovery preserves activation, but re-release starts in Wait; select Roam again to move freely.

## Combat loot and healing range

Combat and Dragon Slimes collect item drops from their own kills into their bags. A full inventory stops item pickup while combat continues. While Fight is active, any collected cargo can request unloading, even before the bag fills. Nearby transport and warehouse companions can unload it without cancelling Fight. Pickup respects item ownership, pickup delay, and Forge pickup protection.

Healing and Abundance Slimes keep Detection Range at **twice the common value**, including upgrades and direct tuning: initially 16 blocks, increasing by two per normal point to 56. The Healing Slime's gold cross consists of separate surfaces to keep it stable when viewed at different angles.

## Tool enchantments

Workers use the actual equipped tool and its complete item data. Efficiency and Work Speed accelerate blocks on which that tool is effective; an unrelated tool retains native slow breaking time. Fortune and Silk Touch follow the target block's native loot rules, while Unbreaking changes ordinary durability use.

Mining, Tunnel, and Fishing Slimes spend experience earned by that action on a damaged working tool with Mending before spawning any remaining experience orbs. A broken tool cannot be repaired after it disappears, and work without experience cannot power Mending. Combat swords retain native damage enchantments, Knockback, Fire Aspect, and kill-loot rules. Native equipment and protection callbacks remain in effect.

## Glow slimes

Combine a Friendly Slime with **glowstone dust**. It starts in Wait with its light enabled. The independent Light control turns torch-strength illumination on or off without changing its order. Ordinary light costs no stamina. Its temporary light appears only in empty air and does not obstruct building. Other blocks and fluids are preserved, and overlapping companion lights remain independent.

Right-click the companion to spend **10 stamina** on fireflies in nearby open air for **six seconds**. Bursts have a one-second interval. Temporary light cannot be collected. Management, dyeing, healing, and recovery retain their ordinary interactions.

## Commander slimes

Combine a Friendly Slime with a **book**. It initially waits and has a mustache and baton. Ordinary right-click opens a paged roster; the tuner opens stats and provides a roster button. The companion roster and Gather cover companions belonging to the commander’s primary owner; shared owners may operate those commands. Companion rows include last-known health, order, work state, storage/life status, dimension, coordinates, and tag. Hover a row to read its location. Observations are discovered when entities, stored items, or broken blocks load; previously unrecorded companions become visible when they or their stored data are observed. Stored-item locations are last known, not continuous chest tracking.

Only **one living released Commander Slime per connected shared-owner group** may be summoned across the entire save, including all dimensions and unloaded chunks. One-way grants, shared co-owners, and indirect shared-access links connect players into the same group for this limit; tags do not define groups. This grouping does not grant additional management or logistics permissions. Stored, broken, and removed commanders do not occupy the slot. Fresh releases, stored releases, and revivals all check the limit; failures preserve the item, broken block, and revival materials. Stored releases use the original owner’s group. Recover one commander before joining two groups that each have one. Removing the last connecting shared-access link separates the groups. Existing extra commanders from older saves are preserved, but block additional releases.

Select a companion, enter a tag, and choose **Save tag**. Tags contain at most 24 characters and ignore letter case. **Gather** uses the typed tag; a blank input selects untagged companions. Starting Gather spends **25 stamina**. It has a five-second cooldown and processes matching living released companions sequentially. Recorded unloaded chunks and other dimensions are included. Only one temporary source chunk is held at a time, for at most five seconds per companion. Stored, broken, removed, mounted, and passenger companions are excluded. Unavailable sources and unsafe landing spaces are skipped. Arrival pauses all work in Wait, preventing accidental excavation at the commander. A gathered commander cancels its own unfinished Gather and releases its temporary source ticket. It can receive a new explicit Gather command after the usual cooldown. Identity, ownership, health, gear, bags, growth, and other saved traits survive. Recovering the commander or restarting cancels unfinished gathering.

### Shared owners and enemies

The Players tab lists online players and previously assigned offline relationships. Only the commander's **primary owner** may add or remove shared owners and enemies. Shared owners may manage, name, dye, heal, recover, revive, equip, ride, link, and command that primary owner's companions. They may use the commander and assign tags, but cannot change player relationships. Follow targets the player who issued it, falling back to an available owner if necessary. Removing shared access is checked immediately by server actions and open menus. Giving someone a stored item alone never transfers ownership.

Shared companions remain allied in combat, and guardians may protect shared owners. Automatic logistics, herald monitoring, healing, and marked-item collection between different primary owners require each primary owner to grant shared access to the other. A one-sided grant authorizes the invited player to manage the granting player’s companions; it never authorizes the inviter to withdraw the invited player’s stores. World edits continue to use the primary owner's simulated player. Enemies are classified as Hostile by fighting companions; PvP settings, Creative immunity, teams, and target filters still apply. Shared owners cannot be enemies; granting shared ownership removes an enemy assignment. Primary owner UUIDs remain unchanged in worlds and items.

## Ender slimes

Combine a Friendly Slime with an **ender pearl**. It initially waits without a fixed anchor. With **full stamina** and an empty inventory slot, right-click it with an empty hand to pin it and receive a **Slime Teleporter**. Linking spends all its stamina. A pinned companion stays at its anchor and keeps its destination chunk ticking. The server configuration `enderAnchorsPerOwner` defaults to **8** per primary owner, with a range of 1–128.

Hold the bound teleporter and right-click in the air to reach a safe space beside that living, pinned companion, including across dimensions. Prepare a dry, unobstructed landing space on a solid floor near the anchor. The destination must again have full stamina; successful travel consumes it all. Teleporting dismounts the player and leaves the mount behind. There is no pearl consumption or teleport damage. Missing, unsafe, unpinned, or unauthorized destinations fail without charging stamina. Teleport events and dimension travel protection remain effective. Primary and shared owners may use the token. It records companion identity, so moving that same companion updates its destination.

Follow releases the anchor. Recovery and collapse release its chunk; ordinary save/restart retains valid pinned chunk tickets. A restored pinned companion anchors at its new release location. If the owner’s other pinned endpoints already fill the limit, release or revival waits for a free slot and preserves the stored item or broken block. Stored or broken companions cannot serve as destinations. At the common 2%-per-second recovery rate, full recharge takes about **50 seconds**; increasing maximum stamina also increases recovery and does not shorten the full-charge interval.

## Attention slimes

Combine a Friendly Slime with **redstone dust**. It initially waits and has no bag or equipment. Its effective Movement Speed is **1.5 times** that of a companion with the same base value, including upgrades, stored specialization, tuning, and recovery.

Roam keeps it bustling around an available owner, brushing past and lightly nudging players without attacking. It uses nearby loaded ground when the owner is unavailable, more than 24 blocks away, or has no reachable ground nearby. It does not load chunks or trample farmland. Follow and Wait stop roaming. Actual roaming movement earns activity at the configured following interval; stationary or waiting companions gain no activity. Partial activity survives recovery.

An Attention Slime item may replace the Friendly Slime in all sixteen basic specialization recipes. This preserves its invested common stats and ordinary companion data, including identity, owner, name, health, and growth. The new profession starts in Wait and loses Attention roaming, its partial roaming timer, and the profession’s +50% Movement Speed multiplier. Movement Speed upgrades remain. JEI displays both base ingredients. Attention cannot be specialized into itself, and Abundance, Tunnel, Dragon, and Warehouse still require their existing profession-specific bases.

## Work area previews and herald messages

Hold a **Slime Scanner** and right-click an owned or shared companion. Normal entity-interaction reach applies, with an eight-block server limit. The preview is visible only to the requester for 15 seconds and clears when the scanner is put away, the companion disappears, or the dimension changes. Cyan outlines show the current or planned range; gold outlines show the next quarry layer or tunnel face, registered farmland bounds, or the selected work position. Tunnel previews show the next eight sections. Search spheres use each profession’s effective Detection Range. Previews do not start jobs, inspect unloaded terrain, or load chunks. Companions without a work area show a short notice instead.

When a Herald Slime launches an automatic blocked-work signal, the worker’s online primary and shared owners also receive a chat message containing its type, custom name if present, and concrete cause. Missing pickaxes, shovels, hoes, axes, rods, swords, seeds, saplings, buckets, cobblestone, slimeballs, and lining materials are distinguished from full bags, storage problems, exhaustion, unreachable destinations, and disabled world changes. Messages follow the existing signal delay and ten-second repeat interval. Manual signals and ordinary Wait do not issue blocked-work messages.

## The Slimic

Explore a separate world of damp plains, gel-covered terrain, and abandoned workshops. A Nether-shaped slime-block frame activated with a slimeball connects it to the Overworld. Building materials and decorations have separate Creative tabs. See [The Slimic](THE_SLIMIC.md) for portal rules, materials, ruins, wildlife, and companion travel.
