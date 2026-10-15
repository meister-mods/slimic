# Gameplay Guide

English | [한국어](GAMEPLAY.kr.md) · [Project overview](../README.md)

Slimic provides friendly companions with upgradeable stats, mining, and transport specializations.

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

Only the owner can recover the slime or change its follow/wait order. Releasing a recovered item preserves the original owner, even if someone else uses the item.

## Recovery and preserved traits

Recovery saves the slime's complete persistent state. Its current health is preserved rather than refilled. Saved traits include:

- Maximum health, other attributes, and permanent attribute modifiers.
- Active effects and their remaining durations.
- Custom name, owner, and follow/wait order.
- Identity, age, equipment, and other saved entity properties.
- Custom persistent data used for future slime traits.

Recovered slimes occupy separate, unstackable items. Their saved state survives inventory, chest, and world saves. Effect durations and other saved timers stay paused while the slime is an item.

The main inventory needs an empty slot. If it is full, recovery fails and the slime stays in the world with its state intact. A dead or dying slime cannot be recovered.

Releasing a recovered item consumes that item in both Survival and Creative mode. A failed release, such as placement inside solid blocks, leaves the stored item unchanged.

The slime appears at the selected destination. Old movement and fall distance are reset so recovery does not carry a previous fall into the new placement. Leashes are returned on recovery, and riding relationships are detached.

## Companion behavior

Friendly slimes hop after their owner and retain ownership and the wait order across saves. They persist in Peaceful difficulty, never attack, and do not split on death.

Companions retain the original slime texture with a dye color filter. Summon icons use slime-ball silhouettes with specialization colors and symbols. See [CREDITS.txt](../CREDITS.txt).

## Shared stats and growth

Right-click an owned slime to open its management screen. The name field sets a visible name above the slime, like a name tag. Follow and Wait buttons change its order. Mining slimes have a Mine button, separate pickaxe and shovel slots, and a bag. Transport slimes have a Haul button and a larger bag, with no equipment slot.

| Stat | Initial value | Per point | Purpose |
| --- | --- | --- | --- |
| Health | 16 | +4 | Maximum health; upgrading does not refill current health. |
| Attack | 2 | +1 | Base attack damage; mining companions do not automatically fight. |
| Defense | 0 | +1 | Minecraft's standard armor attribute and damage calculation. |
| Movement Speed | 0.30 | +0.015 | Minecraft movement-speed attribute. |
| Work Speed | 1.0× | +0.1× | Mining progress and the frequency of transport actions. |
| Capacity | 9 slots / transport: 18 | +3 slots / transport: +6 | Usable bag slots, up to 27 or 54 for transport. |
| Stamina | 100 | +20 | Maximum energy for continuous work. |
| Reach | 3 blocks | +0.25 blocks | Maximum distance for mining and item transfers. |
| Detection Range | 8 blocks | +1 block | Mining target range and transport work-area radius. |

Capacity supports six upgrades; the other stats support twenty. Only the owner can spend points, rename the companion, or use its management inventory. All stats, unused points, activity progress, and current stamina survive world saves and recovery.

The first stat point requires 256 completed work units. Each earned point increases the next requirement by 64 units, capped at 4,096. Mining earns one unit per successfully mined block. Transport earns one unit per cargo-stack transfer into a Slime Chest or replacement tool delivered to a worker; picking up cargo or fetching a tool grants none. Standing idle, following, changing orders, and recovering or summoning a slime grant no activity. Failed actions grant none.

## Mining slimes

Craft a Friendly Slime item with any pickaxe, without a fixed shape, to make a **Mining Slime**. The equipped tool retains its type, damage, name, and enchantments. A recovered friendly slime can also specialize without losing its saved traits.

Mining slimes wear a yellow mining helmet and display their active tool in a held-item orientation. The recipe equips the exact input pickaxe, including its damage and enchantments. A Creative-tab default miner starts with both equipment slots empty.

A newly summoned miner starts work in its release chunk. It clears successive horizontal layers from the release elevation downward, including dirt, stone, and ores. Releasing a recovered miner in the **same chunk** retains its current layer and saved order. Releasing it in a **different chunk** establishes a new quarry at the new elevation and automatically resumes mining. Tools, cargo, health, stats, and other traits remain intact.

The quarry reserves a two-block-wide staircase around the chunk perimeter, with flat corner landings and full-block steps. Existing solid floor blocks are retained. Missing steps are repaired using common full blocks from the bag, such as cobblestone, dirt, stone, or deepslate; each placed block consumes one item. A miner pauses if supports are unavailable or placement is blocked. Supply building blocks through its bag to continue.

Mining stays within the chunk and does not load neighbouring chunks. Air, liquids, block entities, and unbreakable blocks are skipped. A full unbreakable floor or the bottom of the dimension completes the quarry. Protected containers, fluids, or other obstructions can prevent access and may require manual clearance. Block breaking and support placement respect Forge events and `mobGriefing`.

The miner approaches targets and works within Reach, bounded by Detection Range. It uses the shovel for shovel-suitable terrain when available and the pickaxe otherwise, provided the tool meets the block's drop requirements. Actual tools determine break speed, enchantment-aware drops, durability, Fortune, Silk Touch, and Unbreaking. When a tool breaks or its equipment slot is empty, a compatible spare in the bag is equipped automatically with its data intact. Without a suitable tool or tier, work pauses.

Drops go directly into the bag. If all drops cannot fit, the block stays intact, the tool is not damaged, and the miner pauses. Removing items or upgrading Capacity lets work resume. Bag items can be moved to the player's inventory, including with Shift-click.

Each completed block consumes one stamina. At exhaustion the miner rests, recovering two stamina per second until reaching 25% of maximum. Waiting, following, and other non-mining time also restore stamina. The mobGriefing game rule and Forge block-break permissions are respected.

## Slime Chests

Craft eight wooden planks around a slime ball to make a **Slime Chest**. Any plank type, including mixed types, works. Right-click to open its 27-slot inventory. It stores both resources and spare tools, retains its inventory across world saves, and supports standard item automation and comparator output. Breaking it drops the chest and its contents separately.

Slime Chests are shared logistics containers for nearby transport slimes. Ordinary chests are used to craft transport slimes and are not automatic pickup or delivery endpoints. Locked Slime Chests are not used by transport slimes.

## Transport slimes

Craft a Friendly Slime item with a **regular chest**, without a fixed shape, to make a **Transport Slime**. Stored companion traits survive specialization. A transport slime has no equipment slot: even tools travel in its normal bag.

Its Capacity is always exactly twice that of another slime at the same upgrade level: 18 slots initially, six additional slots per Capacity upgrade, and 54 slots at the maximum. Both bag pages can be accessed with the arrow buttons in the management screen. Shift-click moves cargo between the visible bag page and the player's inventory.

New transport slimes start hauling when summoned. Their release position is the center of a work area whose radius is their Detection Range. They find loaded Slime Chests and same-owner workers inside this area, walk between them, and transfer items within Reach and line of sight. The area remains centered on the release position while they travel; recovery and release establish a new center. They do not load neighbouring chunks to find work.

When a worker stops because its bag is full, a transport slime approaches, picks up cargo that fits in its own bag, and delivers it to available Slime Chests. The worker resumes as space becomes available. Partial stacks are merged, and another chest is used when needed. If no suitable chest has room, existing cargo stays in the transport bag and no items are discarded. It retries as space becomes available.

When a worker stops because its tool is missing or broken, the transport slime fetches one compatible spare tool from a Slime Chest and brings it to the worker. For mining slimes this means the requested pickaxe or shovel. The tool's damage, enchantments, and name are preserved. If another carrier already supplied the worker, remaining tools stay in cargo and are reused or returned to a Slime Chest. Manually waiting or following workers are not treated as stopped work requests.

Each cargo-stack pickup or delivery, and each tool pickup or supply, consumes one stamina. Exhausted carriers rest until 25% stamina, recovering two per second. Work Speed shortens the interval between transfer actions. Follow and Wait suspend logistics, and all bag contents, stats, current stamina, growth, and orders survive saves and recovery.

## JEI

JEI is optional. When installed, it shows the Friendly Slime and Slime Chest recipes, Mining Slime combinations for registered pickaxes, the Transport Slime and Herald Slime combinations, and companion information pages. Mining recipe previews show the corresponding equipped tool. Slimic also runs without JEI.

## Dye colors

Right-click an owned companion with a dye to apply that color to its original slime texture. This works for friendly, mining, transport, and herald slimes. Survival consumes one dye; Creative does not. Equipment keeps its own colors. The color survives world saves, specialization, recovery, and release. Shift-right-click still recovers the companion.

## Herald slimes

Craft a Friendly Slime item with any firework rocket, without a fixed shape, to make a **Herald Slime**. The companion retains the exact rocket design, including explosion shapes, colors, fades, trail, flicker, and flight duration. The crafting ingredients are consumed normally; signaling requires no further rockets. The Creative default uses a white small-ball explosion.

A herald watches working companions belonging to the same owner within its Detection Range. When a worker has been continuously unable to work for the configured number of minutes, it launches a visible vanilla firework. These signal fireworks do not damage living entities. Missing tools, insufficient tool tier, full cargo, exhausted stamina, disabled work, inaccessible targets, and unavailable logistics can block work. Manual Follow/Wait orders and completed quarries do not count as stalled work.

The management screen offers Watch, Follow, Wait, and a delay from 1 to 60 minutes. The default is five minutes, configured by `heraldDelayMinutes` in the world's `serverconfig/slimic-server.toml`. Each stalled episode is signaled once, with a short cooldown between signals. Resuming useful work and becoming stalled again permits a new warning. Rocket design, delay, order, warning history, and blocked-work timers survive recovery; time does not advance inside an item.

## Slime Tuner

The **Slime Tuner** is available in the Slimic creative tab and has no Survival recipe. Hold it and right-click to open a separate stat editor for the companion under the crosshair within 16 blocks, falling back to the nearest companion in that range. Right-clicking a companion directly also opens it. The editor requires Creative mode and a held tuner throughout use.

The tuner edits base stats, current health, current stamina, and unused stat points. Permanent attribute modifiers remain applied. Transport Capacity is twice the entered common base. Apply saves valid changes together; Refresh reloads the current values. Reducing Capacity requires emptying every bag slot that would become unavailable. Changing maximum health does not automatically refill current health.

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
